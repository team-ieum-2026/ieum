// 실제 API 주소의 응답을 이 브라우저 세션 안에서만 대체한다. 실제 DB에 쓰지 않는다.
// 성공·오류·경쟁 상태를 검증하며, 실제 DB 연동 검증 결과와 구분해 실행한다.
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve, sep } from 'node:path';

const origin = process.env.IEUM_QA_ORIGIN || 'http://localhost:3000';
const port = Number(process.env.IEUM_CDP_PORT || 9228);
const fixture = async name => JSON.parse(await readFile(new URL(`../../../../public/mock/${name}`, import.meta.url), 'utf8')).data;
const profileData = await fixture('M8_student_profile.json');
const recommendationData = await fixture('M1_recommendations_current.json');
const detailData = await fixture('M2_recommendation_item_detail.json');
const items = recommendationData.items.map(item => ({ ...item, response: 'NONE' }));
const stamp = '2026-11-03T09:00:00+09:00';
const makeRequest = (id, status = 'OPEN', studentId = 1) => ({ helpRequestId: id, studentId, recommendationItemId: null, helpType: null, status, contactMethod: null, memo: null, createdAt: stamp, closedAt: status === 'CLOSED' ? stamp : null, closeReason: status === 'CLOSED' ? 'OTHER' : null, jobTitle: null });
let helpRows = [makeRequest(101), makeRequest(102, 'CONTACTED'), makeRequest(103, 'CLOSED'), makeRequest(201, 'OPEN', 2)];
const statuses = new Map([[1, { studentId: 1, status: 'SEEKING', subStatus: 'WAITING', updatedAt: stamp }], [2, { studentId: 2, status: 'CHECK_REQUIRED', subStatus: null, updatedAt: null }]]);
let recMode = 'normal', listFailure = false, closeMode = 'ok', createMode = 'ok', patchFailure = false, statusFailure = false;
let nextId = 300;
const requests = [], unexpected = [], exceptions = [];
let checks = 0;
const profile = await mkdtemp(join(tmpdir(), 'ieum-help-qa-'));
const chrome = spawn(process.env.IEUM_CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe', ['--headless=new', '--disable-gpu', '--no-first-run', `--remote-debugging-port=${port}`, `--user-data-dir=${profile}`, 'about:blank'], { windowsHide: true, stdio: 'ignore' });
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
const pending = new Map();
let ws, sequence = 0;
function cdp(method, params = {}) {
  return new Promise((resolve, reject) => {
    const id = ++sequence;
    const timer = setTimeout(() => { pending.delete(id); reject(Error('CDP timeout: ' + method)); }, 30000);
    pending.set(id, { resolve: result => { clearTimeout(timer); resolve(result); }, reject: error => { clearTimeout(timer); reject(error); } });
    ws.send(JSON.stringify({ id, method, params }));
  });
}
async function evaluate(expression) {
  const result = await cdp('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
  if (result.exceptionDetails) throw Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text);
  return result.result?.value;
}
async function until(expression) {
  for (let i = 0; i < 250; i++) {
    try { if (await evaluate(expression)) return; } catch { /* navigation */ }
    await pause(100);
  }
  throw Error('Timeout: ' + expression);
}
async function click(selector) {
  await until(`document.querySelector(${JSON.stringify(selector)}) !== null`);
  const point = await evaluate(`(() => { const e=document.querySelector(${JSON.stringify(selector)}); if(!e) throw Error('Missing '+${JSON.stringify(selector)}); e.scrollIntoView({block:'center'}); const r=e.getBoundingClientRect(); return {x:r.x+r.width/2,y:r.y+r.height/2}; })()`);
  await cdp('Input.dispatchMouseEvent', { type: 'mousePressed', button: 'left', clickCount: 1, ...point });
  await cdp('Input.dispatchMouseEvent', { type: 'mouseReleased', button: 'left', clickCount: 1, ...point });
}
async function navigate(path, ready) {
  await cdp('Page.navigate', { url: origin + path });
  await until(ready);
}
async function openProfile(id = 1) {
  await navigate(`/student/profile?studentId=${id}`, "document.querySelector('.s-my-help') && document.querySelector('.s-profile-status-value')?.dataset.status !== 'UNSET'");
  await until("!document.querySelector('.s-my-help > [role=status]:not(.s-my-help-message)')");
}
async function composer() {
  await click('.s-header-help');
  await until("document.querySelector('.student-ui-sheet[open] .s-help-composer') !== null");
  await until("document.querySelector('.s-help-target-options > [role=status]') === null");
}
async function dismiss(selector) {
  await click(selector + ' .s-cancel-dialog-actions button[type=button]');
  await until("document.querySelector('.student-ui-sheet[open]') === null");
}
async function closeDialog(id = 101) {
  await click(`[data-help-request-id="${id}"] .s-text-link`);
  await until("document.querySelector('.s-help-close-form') !== null");
}
const mutations = () => requests.filter(r => r.method !== 'GET');
function pass(name) { checks++; console.log('PASS ' + name); }
async function snapshot(name) {
  const image = await cdp('Page.captureScreenshot', { format: 'png' });
  const directory = new URL('./screenshots/', import.meta.url);
  await mkdir(directory, { recursive: true });
  await writeFile(new URL(name, directory), Buffer.from(image.data, 'base64'));
}

async function respond({ requestId, request }) {
  const url = new URL(request.url), path = url.pathname, method = request.method;
  const body = request.postData ? JSON.parse(request.postData) : null;
  let data, status = 200, error;
  if (method === 'OPTIONS') data = null;
  else {
    requests.push({ method, path, query: url.search, body });
    const studentId = Number(path.match(/students\/(\d+)/)?.[1] || url.searchParams.get('studentId') || 1);
    if (/\/students\/\d+\/profile$/.test(path)) data = { ...profileData, studentId, basic: { ...profileData.basic, name: '시연 수료생 ' + studentId } };
    else if (/\/students\/\d+\/status$/.test(path)) {
      if (method === 'PUT') {
        if (statusFailure) { status = 500; error = '상태 저장 실패'; }
        else statuses.set(studentId, { studentId, status: body.status, subStatus: null, updatedAt: stamp });
      }
      data = statuses.get(studentId);
    } else if (/\/recommendations\/current$/.test(path)) {
      if (recMode === 'error') { status = 500; error = '추천 조회 실패'; }
      data = { ...recommendationData, studentId, items: recMode === 'empty' ? [] : items.map(item => recMode === 'all' ? { ...item, response: 'PASS' } : item) };
    } else if (/\/students\/\d+\/applications$/.test(path)) data = { applications: [], otherItems: [] };
    else if (/^\/api\/recommendation-items\/\d+$/.test(path)) {
      const itemId = Number(path.split('/').at(-1));
      data = { ...detailData, itemId, studentId, response: items.find(item => item.itemId === itemId)?.response || 'NONE' };
    } else if (path === '/api/help-requests' && method === 'GET') {
      if (listFailure) { status = 500; error = '도움 요청 조회 실패'; }
      const filter = url.searchParams.get('status') || 'OPEN';
      data = helpRows.filter(row => row.studentId === studentId && (filter === 'ALL' || row.status === filter));
    } else if (path === '/api/help-requests' && method === 'POST') {
      await pause(150);
      if (createMode === '409') { status = 409; error = '이미 응답한 공고예요.'; }
      else {
        const row = { ...makeRequest(++nextId, 'OPEN', body.studentId), recommendationItemId: body.recommendationItemId, helpType: body.helpType };
        helpRows.push(row);
        if (body.recommendationItemId !== null) items.find(item => item.itemId === body.recommendationItemId).response = 'HELP';
        data = { helpRequestId: row.helpRequestId, status: 'OPEN' };
      }
    } else if (/\/help-requests\/\d+\/close$/.test(path) && method === 'POST') {
      await pause(150);
      const id = Number(path.split('/').at(-2));
      const row = helpRows.find(row => row.helpRequestId === id);
      if (closeMode === 'network') { await cdp('Fetch.failRequest', { requestId, errorReason: 'ConnectionFailed' }); return; }
      if (closeMode === '409') { row.status = 'CONTACTED'; status = 409; error = '요청 상태가 바뀌었어요.'; }
      else {
        if (row.status !== 'CLOSED') Object.assign(row, { status: 'CLOSED', closedAt: stamp, closeReason: body.closeReason });
        data = { helpRequestId: id, status: 'CLOSED', closedAt: row.closedAt, closeReason: row.closeReason };
      }
    } else if (/\/help-requests\/\d+$/.test(path) && method === 'PATCH') {
      if (patchFailure) { status = 500; error = '연락 방법 저장 실패'; }
      else { const row = helpRows.find(row => row.helpRequestId === Number(path.split('/').at(-1))); Object.assign(row, body); data = row; }
    } else { unexpected.push(method + ' ' + path); status = 404; error = 'QA route missing'; }
  }
  await cdp('Fetch.fulfillRequest', { requestId, responseCode: status, responseHeaders: [
    { name: 'Content-Type', value: 'application/json' }, { name: 'Access-Control-Allow-Origin', value: origin },
    { name: 'Access-Control-Allow-Methods', value: 'GET,POST,PATCH,PUT,DELETE,OPTIONS' }, { name: 'Access-Control-Allow-Headers', value: 'Content-Type' },
  ], body: Buffer.from(JSON.stringify(error ? { error: { code: status === 409 ? 'INVALID_STATUS' : 'INTERNAL_ERROR', message: error } } : { data })).toString('base64') });
}

try {
  let target;
  for (let i = 0; i < 80; i++) { try { target = (await (await fetch(`http://127.0.0.1:${port}/json/list`)).json()).find(t => t.type === 'page'); if (target) break; } catch {} await pause(100); }
  assert.ok(target, 'Chrome ready');
  ws = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { ws.addEventListener('open', resolve, { once: true }); ws.addEventListener('error', reject, { once: true }); });
  ws.addEventListener('message', ({ data }) => {
    const message = JSON.parse(data);
    if (message.id) { const task = pending.get(message.id); if (!task) return; pending.delete(message.id); if (message.error) task.reject(Error(message.error.message)); else task.resolve(message.result); }
    if (message.method === 'Fetch.requestPaused') void respond(message.params).catch(error => exceptions.push(error.stack));
    if (message.method === 'Runtime.exceptionThrown') exceptions.push(message.params.exceptionDetails.exception?.description || message.params.exceptionDetails.text);
  });
  await cdp('Page.enable'); await cdp('Runtime.enable'); await cdp('Network.enable');
  await cdp('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false });
  await cdp('Fetch.enable', { patterns: [{ urlPattern: 'http://localhost:8080/api/*' }] });

  await openProfile();
  assert.equal(await evaluate("document.querySelectorAll('.s-my-help-list > li').length"), 1);
  assert.match(await evaluate("document.querySelector('.s-my-help-list').textContent"), /일반 도움 요청.*미분류|미분류.*일반 도움 요청/s);
  await click('.s-my-help-filters button:nth-child(2)');
  await until("document.querySelectorAll('.s-my-help-list > li').length === 3");
  assert.equal(await evaluate("document.querySelectorAll('.s-my-help-list .s-text-link').length"), 1);
  await snapshot('help-list-desktop.png');
  pass('OPEN default, ALL array, null fields, CONTACTED/CLOSED have no close action');

  for (const reason of ['EMPLOYED', 'NO_LONGER_NEEDED', 'OTHER']) {
    helpRows.find(row => row.helpRequestId === 101).status = 'OPEN';
    await openProfile(); await closeDialog();
    assert.equal(await evaluate("document.querySelector('.s-help-close-form button[type=submit]').disabled"), true);
    await click(`.s-help-close-form input[value="${reason}"]`);
    const before = mutations().length;
    await evaluate("document.querySelector('.s-help-close-form').requestSubmit(); document.querySelector('.s-help-close-form').requestSubmit()");
    await until("document.querySelector('.s-help-close-form') === null");
    await until("document.querySelector('[data-help-request-id=\"101\"]') === null");
    assert.equal(mutations().length, before + 1);
    assert.deepEqual(mutations().at(-1).body, { studentId: 1, closeReason: reason });
    assert.equal(statuses.get(1).status, 'SEEKING');
  }
  pass('all three close reasons, single submit, no status PUT or recommendation DELETE');

  helpRows.find(row => row.helpRequestId === 101).status = 'OPEN';
  await openProfile(); await closeDialog(); await click('.s-help-close-form input[value=OTHER]');
  closeMode = 'network'; await click('.s-help-close-form button[type=submit]');
  await until("document.querySelector('.s-help-close-form [role=alert]') !== null");
  assert.equal(await evaluate("document.querySelector('.s-help-close-form input[value=OTHER]').checked"), true);
  assert.equal(helpRows.find(row => row.helpRequestId === 101).status, 'OPEN');
  closeMode = 'ok'; await click('.s-help-close-form button[type=submit]');
  await until("document.querySelector('.s-help-close-form') === null");
  pass('close network failure retains request and reason; retry succeeds');

  helpRows.find(row => row.helpRequestId === 101).status = 'OPEN';
  await openProfile(); await closeDialog(); await click('.s-help-close-form input[value=OTHER]');
  closeMode = '409'; await click('.s-help-close-form button[type=submit]');
  await until("document.querySelector('.s-help-close-form [role=alert]') !== null");
  await until("document.querySelector('[data-help-request-id=\"101\"]') === null");
  assert.equal(await evaluate("document.querySelector('.s-help-close-form button[type=submit]').disabled"), true);
  await dismiss('.s-help-close-form'); closeMode = 'ok';
  pass('close 409 refreshes list and prevents invalid repeat submission');

  helpRows.find(row => row.helpRequestId === 101).status = 'OPEN';
  await openProfile(); await closeDialog(); await click('.s-help-close-form input[value=OTHER]');
  Object.assign(helpRows.find(row => row.helpRequestId === 101), { status: 'CLOSED', closeReason: 'NO_LONGER_NEEDED', closedAt: stamp });
  await click('.s-help-close-form button[type=submit]');
  await until("document.querySelector('.s-help-close-form') === null");
  pass('already CLOSED 200 is successful and preserves server reason');

  listFailure = true; await openProfile();
  assert.ok(await evaluate("document.querySelector('.s-my-help [role=alert]') !== null"));
  assert.equal(await evaluate("document.querySelector('.s-my-help-empty') !== null"), false);
  listFailure = false; await click('.s-my-help [role=alert] button');
  await until("document.querySelector('.s-my-help-empty') !== null");
  pass('list error is distinct from empty state and retry works');

  await composer();
  const beforeCancel = mutations().length;
  await click('.s-help-type-options input[value=OTHER]');
  await click('.s-help-target-options input:not([value=""])');
  assert.equal(mutations().length, beforeCancel);
  await dismiss('.s-help-composer');
  assert.equal(mutations().length, beforeCancel);
  pass('opening, type selection, target selection and cancel do not POST');

  await click('.s-sidebar-toggle');
  await until("document.querySelector('.s-utility-dialog[open]') !== null");
  await click('.s-support button');
  await until("document.querySelector('.s-help-composer') !== null && document.querySelector('.s-utility-dialog[open]') === null");
  await dismiss('.s-help-composer');
  assert.equal(mutations().length, beforeCancel);
  pass('sidebar opens the shared composer without nested modal or implicit creation');

  for (const [mode, type] of [['empty', 'RESUME_COVER_LETTER'], ['all', 'INTERVIEW_PREPARATION'], ['error', 'CAREER_COUNSELING'], ['normal', 'JOB_APPLICATION'], ['normal', 'OTHER']]) {
    recMode = mode; await openProfile(); await composer();
    await click(`.s-help-type-options input[value="${type}"]`);
    if (mode === 'error') {
      await until("document.querySelector('.s-help-load-error') !== null");
      const beforeRetry = mutations().length;
      await click('.s-help-load-error button'); await pause(200);
      assert.equal(mutations().length, beforeRetry, 'recommendation retry must not submit the form');
    }
    const before = mutations().length;
    await evaluate("document.querySelector('.s-help-composer').requestSubmit(); document.querySelector('.s-help-composer').requestSubmit()");
    await until("location.pathname.startsWith('/student/help/') && document.querySelector('.s-help-page form') !== null");
    assert.equal(mutations().length, before + 1);
    assert.deepEqual(mutations().at(-1).body, { studentId: 1, recommendationItemId: null, helpType: type });
    assert.equal(await evaluate("new URLSearchParams(location.search).get('studentId')"), '1');
  }
  pass('five types, general creation with zero/all-responded/error recommendations, one POST only');

  recMode = 'normal'; await openProfile(); await composer();
  await click('.s-help-type-options input[value=INTERVIEW_PREPARATION]');
  await click(`.s-help-target-options input[value="${items[1].itemId}"]`);
  await click('.s-help-composer button[type=submit]');
  await until("location.pathname.startsWith('/student/help/') && document.querySelector('.s-help-page form') !== null");
  assert.deepEqual(mutations().at(-1).body, { studentId: 1, recommendationItemId: items[1].itemId, helpType: 'INTERVIEW_PREPARATION' });
  items[1].response = 'NONE';
  pass('header creates a linked request only after explicit confirmation');

  recMode = 'normal';
  const linkedId = items[0].itemId;
  await navigate(`/student/jobs/${linkedId}?studentId=1`, "document.querySelector('.s-action-panel .s-action-stack button:last-child') !== null");
  await click('.s-action-panel .s-action-stack button:last-child');
  await until("document.querySelector('.s-help-job-context') !== null");
  assert.equal(await evaluate("document.querySelector('.s-help-composer button[type=submit]').disabled"), true);
  await click('.s-help-type-options input[value=JOB_APPLICATION]');
  await click('.s-help-composer button[type=submit]');
  await until("location.pathname.startsWith('/student/help/') && document.querySelector('.s-help-page form') !== null");
  assert.deepEqual(mutations().at(-1).body, { studentId: 1, recommendationItemId: linkedId, helpType: 'JOB_APPLICATION' });
  assert.equal(items[0].response, 'HELP');
  const postCount = requests.filter(r => r.method === 'POST' && r.path === '/api/help-requests').length;
  patchFailure = true;
  await click('.s-help-page input[name=contactMethod]');
  await click('.s-help-page textarea');
  await cdp('Input.insertText', { text: '지원 준비 상담' });
  await click('.s-help-page form button[type=submit]');
  await until("document.querySelector('.s-help-page [role=alert]') !== null");
  patchFailure = false; await click('.s-help-page form button[type=submit]');
  await until("location.pathname === '/student' && document.querySelector('.s-job-grid') !== null");
  assert.equal(requests.filter(r => r.method === 'POST' && r.path === '/api/help-requests').length, postCount);
  assert.deepEqual(mutations().at(-1).body, { contactMethod: 'SMS', memo: '지원 준비 상담' });
  assert.ok(await evaluate("document.querySelector('.s-response-HELP') !== null"));
  pass('M2 linked creation, HELP badge, M4 PATCH failure/retry never creates twice');

  await composer(); await click('.s-help-type-options input[value=OTHER]');
  await click(`.s-help-target-options input[value="${items[1].itemId}"]`);
  assert.equal(await evaluate("document.querySelector('.s-help-type-options input[value=OTHER]').checked"), true, 'type selected before conflict test');
  assert.equal(await evaluate(`document.querySelector('.s-help-target-options input[value="${items[1].itemId}"]').checked`), true, 'target selected before conflict test');
  createMode = '409'; await click('.s-help-composer button[type=submit]');
  await until("document.querySelector('.s-help-composer [role=alert]') !== null");
  assert.equal(await evaluate("document.querySelector('.s-help-composer button[type=submit]').disabled"), true);
  await click('.s-help-target-options input[value=""]');
  assert.equal(await evaluate("document.querySelector('.s-help-composer button[type=submit]').disabled"), false);
  await dismiss('.s-help-composer'); createMode = 'ok';
  pass('create 409 preserves type and allows changing to a general request');

  await openProfile();
  const rowSnapshot = JSON.stringify(helpRows);
  for (const value of ['EMPLOYED', 'RESTING', 'SEEKING']) {
    await click('.s-profile-status-heading button');
    await click(`.s-profile-status-option input[value="${value}"]`);
    await click('.s-cancel-dialog-actions .s-button-primary');
    await until(`document.querySelector('.s-profile-status-value').dataset.status === '${value}' && !document.querySelector('.student-ui-sheet[open]')`);
    await openProfile();
    assert.equal(await evaluate("document.querySelector('.s-profile-status-value').dataset.status"), value);
    assert.equal(JSON.stringify(helpRows), rowSnapshot);
  }
  statusFailure = true; await click('.s-profile-status-heading button');
  await click('.s-profile-status-option input[value=RESTING]'); await click('.s-cancel-dialog-actions .s-button-primary');
  await until("document.querySelector('.student-ui-sheet[open] [role=alert]') !== null");
  assert.equal(await evaluate("document.querySelector('.s-profile-status-value').dataset.status"), 'SEEKING');
  assert.equal(await evaluate("document.querySelector('.s-profile-status-option input[value=RESTING]').checked"), true);
  await click('.s-cancel-dialog-actions .s-button:not(.s-button-primary)'); statusFailure = false;
  pass('existing status choices save/reload, preserve help requests and retain selection on failure');

  await openProfile(2);
  assert.equal(await evaluate("document.querySelector('.s-profile-status-value').dataset.status"), 'CHECK_REQUIRED');
  await until("document.querySelector('[data-help-request-id=\"201\"]') !== null");
  assert.equal(await evaluate("document.querySelector('[data-help-request-id=\"101\"]') !== null"), false);
  await composer(); await click('.s-help-type-options input[value=OTHER]');
  await click('.s-help-composer button[type=submit]');
  await until("location.pathname.startsWith('/student/help/') && document.querySelector('.s-help-page form') !== null");
  assert.equal(mutations().at(-1).body.studentId, 2);
  assert.equal(await evaluate("new URLSearchParams(location.search).get('studentId')"), '2');
  pass('student switch isolates list, CHECK_REQUIRED display and creation routing');

  await openProfile(2); await composer(); await snapshot('help-composer-desktop.png');
  await dismiss('.s-help-composer');
  assert.deepEqual(unexpected, []);
  assert.deepEqual(exceptions, []);
  assert.equal(requests.some(r => r.method === 'DELETE'), false);
  console.log(`${checks} browser checks passed; transport responses are isolated, no real DB writes.`);
} catch (failure) {
  console.log(JSON.stringify({ lastRequests: requests.slice(-5), page: await evaluate("({url:location.href,form:document.querySelector('.s-help-composer')?.textContent,selected:Array.from(document.querySelectorAll('.s-help-composer input:checked')).map(e=>e.value),submitDisabled:document.querySelector('.s-help-composer button[type=submit]')?.disabled})").catch(() => null) }));
  await snapshot('help-qa-failure.png').catch(() => {});
  throw failure;
} finally {
  if (ws?.readyState === WebSocket.OPEN) { try { await cdp('Browser.close'); } catch {} ws.close(); }
  chrome.kill();
  if (resolve(profile).startsWith(resolve(tmpdir()) + sep) && profile.split(sep).at(-1).startsWith('ieum-help-qa-')) await rm(profile, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 }).catch(() => {});
}
