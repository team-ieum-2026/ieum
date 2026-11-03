// M1 카드의 브라우저 회귀 검사. API 응답은 이 Chrome 세션 안에서만 대체하고 실제 DB에는 요청하지 않는다.
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve, sep } from 'node:path';

const origin = process.env.IEUM_QA_ORIGIN || 'http://localhost:3000';
const port = Number(process.env.IEUM_CDP_PORT || 9226);
const fixture = async name => JSON.parse(await readFile(new URL(`../../../../public/mock/${name}`, import.meta.url), 'utf8'));
const recommendations = await fixture('M1_recommendations_current.json');
const profileData = await fixture('M8_student_profile.json');
const applications = await fixture('M7_student_applications.json');
const detail = await fixture('M2_recommendation_item_detail.json');
const sourceItems = recommendations.data.items.map(item => ({ ...item, response: 'NONE' }));
let items = structuredClone(sourceItems);
let refreshRequests = 0;
let cancelRequests = 0;
const unexpected = [];
const exceptions = [];
const checks = [];
const directory = new URL('./screenshots/', import.meta.url);
const profile = await mkdtemp(join(tmpdir(), 'ieum-job-card-qa-'));
const chrome = spawn(process.env.IEUM_CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe', ['--headless=new', '--disable-gpu', '--no-first-run', `--remote-debugging-port=${port}`, `--user-data-dir=${profile}`, 'about:blank'], { windowsHide: true, stdio: 'ignore' });
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const pending = new Map();
let ws;
let sequence = 0;

async function cdp(method, params = {}) {
  const id = ++sequence;
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => { pending.delete(id); reject(Error(`CDP timeout: ${method}`)); }, 30000);
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
  for (let i = 0; i < 200; i++) {
    try { if (await evaluate(expression)) return; } catch { /* 새 문서의 컨텍스트 대기 */ }
    await sleep(100);
  }
  throw Error('Timeout: ' + expression);
}
async function viewport(width, touch = false) {
  await cdp('Emulation.setDeviceMetricsOverride', { width, height: 1000, deviceScaleFactor: 1, mobile: touch });
  await cdp('Emulation.setTouchEmulationEnabled', { enabled: touch, maxTouchPoints: 1 });
}
async function move(x, y) { await cdp('Input.dispatchMouseEvent', { type: 'mouseMoved', x, y }); }
async function click(selector) {
  const point = await evaluate(`(() => { const e = document.querySelector(${JSON.stringify(selector)}); e.scrollIntoView({block:'center'}); const r=e.getBoundingClientRect(); return {x:r.x+r.width/2,y:r.y+r.height/2}; })()`);
  await cdp('Input.dispatchMouseEvent', { type: 'mousePressed', button: 'left', clickCount: 1, ...point });
  await cdp('Input.dispatchMouseEvent', { type: 'mouseReleased', button: 'left', clickCount: 1, ...point });
}
async function home() {
  await move(1, 1);
  await cdp('Page.navigate', { url: `${origin}/student?studentId=1` });
  await until(items.length ? `document.querySelectorAll('.s-job-card').length === ${items.length}` : "document.querySelector('.s-empty') !== null");
  await evaluate('document.fonts.ready.then(() => true)');
}
async function metrics() {
  return evaluate(`(() => { const cards=[...document.querySelectorAll('.s-job-card')]; const card=cards[0]; const r=e=>{const b=e.getBoundingClientRect();return {x:b.x,y:b.y,width:b.width,height:b.height,bottom:b.bottom}}; return {cards:cards.map(r),image:r(card.querySelector('.s-career-image')),panel:r(card.querySelector('.s-card-expansion')),panelVisibility:getComputedStyle(card.querySelector('.s-card-expansion')).visibility,activity:r(document.querySelector('.s-activity'))}; })()`);
}
async function snapshot(name, selector = '.s-job-grid') {
  const clip = await evaluate(`(() => {const r=document.querySelector(${JSON.stringify(selector)}).getBoundingClientRect();return {x:r.x+scrollX,y:r.y+scrollY,width:r.width,height:r.height,scale:1}})()`);
  const result = await cdp('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true, clip });
  await mkdir(directory, { recursive: true });
  await writeFile(new URL(name + '.png', directory), Buffer.from(result.data, 'base64'));
}
function pass(message) { checks.push(message); console.log('PASS ' + message); }
async function respond({ requestId, request }) {
  const path = new URL(request.url).pathname;
  let status = 200;
  let body;
  if (request.method === 'OPTIONS') status = 204;
  else if (path.endsWith('/recommendations/current')) body = { data: { ...recommendations.data, items } };
  else if (path.endsWith('/recommendations/refresh')) {
    refreshRequests++;
    items = structuredClone(sourceItems.slice(0, 2));
    body = { data: { ...recommendations.data, items } };
  } else if (path === '/api/students/1/profile') body = profileData;
  else if (path === '/api/students/1/applications') body = applications;
  else if (/^\/api\/recommendation-items\/\d+$/.test(path)) body = { data: { ...detail.data, itemId: Number(path.split('/').at(-1)) } };
  else if (request.method === 'DELETE' && path.endsWith('/response')) {
    cancelRequests++; status = 503; body = { error: { code: 'QA_UNAVAILABLE', message: '취소 요청을 처리하지 못했어요.' } };
  } else { unexpected.push(request.method + ' ' + path); status = 404; body = { error: { code: 'NOT_FOUND', message: 'QA route not configured' } }; }
  await cdp('Fetch.fulfillRequest', { requestId, responseCode: status, responseHeaders: [
    { name: 'Content-Type', value: 'application/json' }, { name: 'Access-Control-Allow-Origin', value: origin },
    { name: 'Access-Control-Allow-Methods', value: 'GET,POST,DELETE,OPTIONS' }, { name: 'Access-Control-Allow-Headers', value: 'Content-Type' },
  ], body: Buffer.from(body ? JSON.stringify(body) : '').toString('base64') });
}

try {
  let target;
  for (let i = 0; i < 80; i++) {
    try { target = (await (await fetch(`http://127.0.0.1:${port}/json/list`)).json()).find(t => t.type === 'page'); if (target) break; } catch {}
    await sleep(100);
  }
  assert.ok(target, 'Chrome CDP ready');
  ws = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { ws.addEventListener('open', resolve, { once: true }); ws.addEventListener('error', reject, { once: true }); });
  ws.addEventListener('message', ({ data }) => {
    const message = JSON.parse(data);
    if (message.id) {
      const task = pending.get(message.id); if (!task) return;
      pending.delete(message.id);
      if (message.error) task.reject(Error(message.error.message)); else task.resolve(message.result);
    }
    if (message.method === 'Fetch.requestPaused') void respond(message.params).catch(error => exceptions.push(error.message));
    if (message.method === 'Runtime.exceptionThrown') exceptions.push(message.params.exceptionDetails.text);
  });
  await cdp('Page.enable'); await cdp('Runtime.enable');
  await cdp('Fetch.enable', { patterns: [{ urlPattern: 'http://localhost:8080/api/*', requestStage: 'Request' }] });
  await viewport(1440); await home(); await sleep(300);
  const initial = await metrics();
  assert.equal(initial.panel.height, 0);
  assert.equal(initial.panelVisibility, 'hidden');
  assert.equal(await evaluate("getComputedStyle(document.querySelector('.s-card-reason-toggle')).display"), 'none');
  assert.equal(await evaluate("document.querySelectorAll('.s-recommend-rank').length"), 3);
  await snapshot('job-card-default');
  pass('Default: full image, core information, rank/response/deadline visible; reasons/CTA hidden');

  await move(initial.cards[0].x + 32, initial.cards[0].y + 20);
  await sleep(30);
  assert.equal((await metrics()).panel.height, 0);
  assert.equal((await metrics()).panelVisibility, 'hidden');
  await move(1, 1); await sleep(250);
  assert.equal((await metrics()).panel.height, 0);
  pass('Brief pointer pass does not expand the card or trigger a delayed opening');
  await move(initial.cards[0].x + 32, initial.cards[0].y + 20);
  await sleep(40);
  assert.equal((await metrics()).panel.height, 0);
  await sleep(160); const middle = await metrics();
  await sleep(250); const expanded = await metrics();
  assert.equal(expanded.panelVisibility, 'visible');
  assert.ok(expanded.panel.height > 100);
  assert.ok(expanded.image.height < middle.image.height && middle.image.height < initial.image.height, 'Image height changes gradually');
  for (const index of [0, 1, 2]) for (const key of ['x', 'y', 'height', 'width']) assert.ok(Math.abs(initial.cards[index][key] - expanded.cards[index][key]) < 1, `Stable card ${index} ${key}`);
  assert.ok(Math.abs(initial.activity.y - expanded.activity.y) < 1, 'Following section does not move');
  assert.ok(expanded.panel.bottom <= expanded.cards[0].bottom + 1);
  await snapshot('job-card-hover');
  pass('Hover: smooth image shrink and reasons/CTA reveal without card, neighbour or page shift');
  await move(1, 1); await sleep(260);
  assert.equal((await metrics()).panel.height, 0);
  pass('Pointer leave restores collapsed card');

  await evaluate("document.querySelector('.s-job-card-link').focus()"); await sleep(300);
  assert.equal((await metrics()).panelVisibility, 'visible');
  assert.equal(await evaluate("getComputedStyle(document.querySelector('.s-card-expansion')).transitionDelay"), '0s');
  await cdp('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Tab', code: 'Tab', windowsVirtualKeyCode: 9 });
  await cdp('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Tab', code: 'Tab', windowsVirtualKeyCode: 9 });
  assert.ok(await evaluate("document.activeElement.classList.contains('s-card-cta')"));
  await click('.s-card-cta');
  await until(`location.pathname === '/student/jobs/${items[0].itemId}'`);
  assert.equal(await evaluate('location.search'), '?studentId=1');
  pass('Keyboard focus expands card; CTA navigates to M2 with studentId');

  items = structuredClone(sourceItems); items[0].response = 'PASS';
  await home(); await click('.s-job-card .s-response-cancel');
  await until("document.querySelector('.student-ui-sheet[open]') !== null");
  assert.equal(await evaluate('location.pathname'), '/student');
  await click('.s-cancel-dialog-actions .s-button-primary');
  await until("document.querySelector('.student-ui-sheet[open] [role=alert]') !== null");
  assert.equal(cancelRequests, 1);
  assert.ok(await evaluate("!!document.querySelector('.s-response-PASS')"));
  await click('.s-cancel-dialog-actions .s-button:not(.s-button-primary)');
  pass('Cancel confirmation is independent of card navigation; one request, failed response preserved');

  items = structuredClone(sourceItems); await viewport(390, true); await home();
  assert.equal((await metrics()).panel.height, 0);
  await click('.s-card-reason-toggle');
  await until("document.querySelector('.s-card-reason-toggle').getAttribute('aria-expanded') === 'true'");
  await sleep(300);
  assert.equal((await metrics()).panelVisibility, 'visible');
  assert.equal(await evaluate('location.pathname'), '/student');
  await snapshot('job-card-mobile-expanded', '.s-job-card');
  await click('.s-card-reason-toggle'); await sleep(260);
  assert.equal((await metrics()).panel.height, 0);
  await click('.s-job-card-link');
  await until(`location.pathname === '/student/jobs/${items[0].itemId}'`);
  pass('Mobile: explicit expand/collapse, no navigation on toggle, direct M2 navigation on first tap');

  for (const width of [320, 640, 768, 1024, 1440]) {
    await viewport(width, width < 1024); await home();
    assert.ok(await evaluate('document.documentElement.scrollWidth <= innerWidth + 1'), `No overflow at ${width}`);
    if (width < 1024) assert.notEqual(await evaluate("getComputedStyle(document.querySelector('.s-card-reason-toggle')).display"), 'none');
  }
  pass('Desktop/tablet/mobile layout: 320, 640, 768, 1024, 1440px without overflow');

  for (const size of [2, 1, 0]) { items = structuredClone(sourceItems.slice(0, size)); await home(); assert.equal(await evaluate("document.querySelectorAll('.s-job-card').length"), size); }
  assert.ok(await evaluate("!!document.querySelector('.s-empty a[href*=profile]')"));
  pass('0/1/2 recommendation results preserve layout and empty-state profile link');

  items = structuredClone(sourceItems); await viewport(1440); await home();
  await click('.s-refresh');
  await until("document.querySelectorAll('.s-job-card').length === 2");
  assert.equal(refreshRequests, 1);
  assert.equal(await evaluate("document.querySelector('.s-recommend-count').textContent"), '2');
  pass('Refresh replaces cards and count through existing endpoint');

  items = structuredClone(sourceItems);
  items[0].job.dDay = 0; items[1].job.dDay = -1; items[2].job.dDay = null; items[2].job.untilFilled = true;
  items[0].response = 'APPLY'; items[1].response = 'HELP'; items[2].response = 'PASS';
  items[0].job.title = items[0].job.title.repeat(5);
  items[0].reasonSummary = items[0].reasonSummary.map(reason => reason.repeat(5));
  await home();
  assert.equal(await evaluate("document.querySelectorAll('.s-job-card .is-urgent').length"), 2);
  for (const response of ['APPLY', 'HELP', 'PASS']) assert.ok(await evaluate(`!!document.querySelector('.s-response-${response}')`));
  const long = await metrics(); await move(long.cards[0].x + 20, long.cards[0].y + 20); await sleep(300);
  const longOpen = await metrics();
  assert.ok(longOpen.panel.bottom <= longOpen.cards[0].bottom + 1);
  assert.ok(longOpen.image.height > 40);
  pass('Long content, apply/help/pass badges and today/closed/unknown/until-filled deadlines remain accessible');

  await move(1, 1);
  await cdp('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: 'reduce' }] });
  assert.equal(await evaluate("getComputedStyle(document.querySelector('.s-card-expansion')).transitionDuration"), '0s');
  pass('Reduced-motion disables transitions');
  assert.deepEqual(unexpected, []);
  assert.deepEqual(exceptions, []);
  console.log(JSON.stringify({ passed: checks.length, checks, screenshots: directory.pathname }, null, 2));
} catch (error) {
  try { await snapshot('job-card-failure'); console.error(JSON.stringify(await metrics())); } catch {}
  throw error;
} finally {
  if (ws?.readyState === WebSocket.OPEN) { try { await cdp('Browser.close'); } catch {} ws.close(); }
  chrome.kill();
  if (resolve(profile).startsWith(resolve(tmpdir()) + sep) && profile.split(sep).at(-1).startsWith('ieum-job-card-qa-')) await rm(profile, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 }).catch(() => {});
}
