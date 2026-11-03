// 최신 dev 통합 후 R0 진입, M6 공유 편집 시트, C4 화면 회귀 확인. 실제 API에는 전송하지 않는다.
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve, sep } from 'node:path';

const origin = process.env.IEUM_QA_ORIGIN || 'http://localhost:3000';
const port = Number(process.env.IEUM_CDP_PORT || 9232);
const fixture = async name => JSON.parse(await readFile(new URL(`../../../../public/mock/${name}`, import.meta.url), 'utf8'));
const fixtures = Object.fromEntries(await Promise.all(['M8_student_profile.json', 'M1_recommendations_current.json', 'M7_student_applications.json', 'M6_application_document.json', 'C4_coordinator_application.json', 'C1_coordinator_dashboard.json'].map(async name => [name, await fixture(name)])));
const profile = await mkdtemp(join(tmpdir(), 'ieum-navigation-qa-'));
const chrome = spawn(process.env.IEUM_CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe', ['--headless=new', '--disable-gpu', '--no-first-run', `--remote-debugging-port=${port}`, `--user-data-dir=${profile}`, 'about:blank'], { windowsHide: true, stdio: 'ignore' });
const pending = new Map(), unexpected = [], exceptions = [], mutations = [];
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
let ws, sequence = 0, checks = 0;
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
  for (let i = 0; i < 200; i++) { try { if (await evaluate(expression)) return; } catch {} await pause(100); }
  throw Error('Timeout: ' + expression);
}
async function click(selector) {
  await until(`document.querySelector(${JSON.stringify(selector)}) !== null`);
  const point = await evaluate(`(() => { const e=document.querySelector(${JSON.stringify(selector)}); e.scrollIntoView({block:'center'}); const r=e.getBoundingClientRect(); return {x:r.x+r.width/2,y:r.y+r.height/2}; })()`);
  await cdp('Input.dispatchMouseEvent', { type: 'mousePressed', button: 'left', clickCount: 1, ...point });
  await cdp('Input.dispatchMouseEvent', { type: 'mouseReleased', button: 'left', clickCount: 1, ...point });
}
async function buttonText(text) {
  await evaluate(`(() => {const e=Array.from(document.querySelectorAll('button')).find(e=>e.textContent.trim()===${JSON.stringify(text)}); if(!e)throw Error('Missing button'); e.click()})()`);
}
async function key(name, code, keyCode, modifiers = 0) {
  await cdp('Input.dispatchKeyEvent', { type: 'keyDown', key: name, code, windowsVirtualKeyCode: keyCode, modifiers });
  await cdp('Input.dispatchKeyEvent', { type: 'keyUp', key: name, code, windowsVirtualKeyCode: keyCode, modifiers });
}
async function snapshot(name) {
  await evaluate('document.fonts.ready.then(()=>true)');
  const directory = new URL('./screenshots/', import.meta.url);
  await mkdir(directory, { recursive: true });
  const result = await cdp('Page.captureScreenshot', { format: 'png' });
  await writeFile(new URL(name, directory), Buffer.from(result.data, 'base64'));
}
function pass(name) { checks++; console.log('PASS ' + name); }
async function respond({ request, requestId }) {
  const path = new URL(request.url).pathname;
  let body, status = 200;
  if (request.method === 'OPTIONS') body = {};
  else if (request.method !== 'GET') { mutations.push(request.method + ' ' + path); body = { error: { code: 'QA_BLOCKED', message: 'QA blocks writes' } }; status = 503; }
  else if (path === '/api/students/1/profile') body = fixtures['M8_student_profile.json'];
  else if (path === '/api/students/1/recommendations/current') body = fixtures['M1_recommendations_current.json'];
  else if (path === '/api/students/1/applications') body = fixtures['M7_student_applications.json'];
  else if (path === '/api/applications/7') body = fixtures['M6_application_document.json'];
  else if (path === '/api/coordinator/applications/7') body = fixtures['C4_coordinator_application.json'];
  else if (path === '/api/coordinator/dashboard') body = fixtures['C1_coordinator_dashboard.json'];
  else { unexpected.push(path); status = 404; body = { error: { code: 'NOT_FOUND', message: 'QA route missing' } }; }
  await cdp('Fetch.fulfillRequest', { requestId, responseCode: status, responseHeaders: [{ name: 'Content-Type', value: 'application/json' }, { name: 'Access-Control-Allow-Origin', value: origin }, { name: 'Access-Control-Allow-Methods', value: 'GET,POST,PATCH,PUT,DELETE,OPTIONS' }, { name: 'Access-Control-Allow-Headers', value: 'Content-Type' }], body: Buffer.from(JSON.stringify(body)).toString('base64') });
}

try {
  let target;
  for (let i = 0; i < 80; i++) { try { target = (await (await fetch(`http://127.0.0.1:${port}/json/list`)).json()).find(t => t.type === 'page'); if (target) break; } catch {} await pause(100); }
  assert.ok(target);
  ws = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { ws.addEventListener('open', resolve, { once: true }); ws.addEventListener('error', reject, { once: true }); });
  ws.addEventListener('message', ({ data }) => {
    const message = JSON.parse(data);
    if (message.id) { const task = pending.get(message.id); if (!task) return; pending.delete(message.id); if (message.error) task.reject(Error(message.error.message)); else task.resolve(message.result); }
    if (message.method === 'Fetch.requestPaused') void respond(message.params).catch(error => exceptions.push(error.message));
    if (message.method === 'Runtime.exceptionThrown') exceptions.push(message.params.exceptionDetails.exception?.description || message.params.exceptionDetails.text);
  });
  await cdp('Page.enable'); await cdp('Runtime.enable');
  await cdp('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false });
  await cdp('Fetch.enable', { patterns: [{ urlPattern: 'http://localhost:8080/api/*' }] });
  await cdp('Page.navigate', { url: origin + '/' });
  await until("document.querySelector('.student-card') !== null"); await pause(2000);
  assert.equal(await evaluate("document.querySelectorAll('.entry-heading-character').length"), 16);
  assert.ok(await evaluate('document.documentElement.scrollWidth <= innerWidth + 1'));
  await snapshot('entry-desktop.png');
  await click('.student-card');
  await until("document.querySelector('#student-entry-dialog[open]') !== null");
  assert.equal(await evaluate('document.activeElement.id'), 'student-entry-id');
  assert.equal(await evaluate("getComputedStyle(document.querySelector('#student-entry-id')).outlineStyle"), 'none');
  await click('#student-entry-form button[type=submit]');
  await until("document.querySelector('#student-entry-error') !== null");
  await click('#student-entry-id'); await cdp('Input.insertText', { text: '0' });
  await click('#student-entry-form button[type=submit]');
  assert.match(await evaluate("document.querySelector('#student-entry-error').textContent"), /1 이상/);
  await click('#student-entry-id'); await evaluate("document.querySelector('#student-entry-id').select()"); await cdp('Input.insertText', { text: '1' });
  await snapshot('entry-id-desktop.png');
  await click('#student-entry-form button[type=submit]');
  await until("location.pathname === '/student' && document.querySelector('.s-job-grid') !== null");
  assert.equal(await evaluate("new URLSearchParams(location.search).get('studentId')"), '1');
  assert.ok(await evaluate("Array.from(document.querySelectorAll('.s-header-nav a')).every(a=>a.href.includes('studentId=1'))"));
  await click('.s-home-link'); await until("location.pathname === '/' && document.querySelector('.student-card') !== null");
  pass('R0 title/ID focus/validation, studentId navigation and home return');

  await cdp('Page.navigate', { url: origin + '/student/applications/7?studentId=1' });
  await until("document.querySelectorAll('.student-section-card').length === 9");
  await click('.student-section-card[data-section=BASIC]');
  await until("document.querySelector('.document-sheet input[type=text]') !== null");
  await click('.document-sheet input[type=text]');
  const inputId = await evaluate('document.activeElement.id');
  await evaluate('document.activeElement.select()');
  for (const text of ['시', '연', ' ', '입', '력']) { await cdp('Input.insertText', { text }); assert.equal(await evaluate('document.activeElement.id'), inputId); }
  assert.equal(await evaluate('document.activeElement.value'), '시연 입력');
  await evaluate("(() => { const buttons=Array.from(document.querySelectorAll('.document-sheet button:not(:disabled)')); buttons.at(-1).focus(); })()");
  await key('Tab', 'Tab', 9);
  assert.ok(await evaluate("document.querySelector('.document-sheet').contains(document.activeElement)"));
  await key('Tab', 'Tab', 9, 8);
  assert.ok(await evaluate("document.querySelector('.document-sheet').contains(document.activeElement)"));
  await snapshot('resume-editor-desktop.png');
  await key('Escape', 'Escape', 27);
  await until("document.querySelector('.document-sheet') === null");
  assert.equal(await evaluate('document.activeElement.dataset.section'), 'BASIC');
  assert.equal(await evaluate('document.body.style.overflow'), '');
  assert.deepEqual(mutations, []);
  pass('M6 nine cards, per-character input focus, Tab trap, Escape/focus/scroll restore without autosave');

  await cdp('Page.navigate', { url: origin + '/coordinator/applications/7' });
  await until("document.querySelector('h1')?.textContent.includes('지원서류')");
  assert.equal(await evaluate("document.querySelector('.student-app') !== null"), false);
  await buttonText('수정 요청 기록');
  await until("document.querySelector('[role=dialog] textarea') !== null");
  await click('[role=dialog] textarea');
  for (const text of ['검', '토', ' ', '메', '모']) { await cdp('Input.insertText', { text }); assert.equal(await evaluate('document.activeElement.tagName'), 'TEXTAREA'); }
  assert.equal(await evaluate('document.activeElement.value'), '검토 메모');
  await key('Escape', 'Escape', 27);
  await until("document.querySelector('[role=dialog]') === null");
  await snapshot('coordinator-review-desktop.png');
  assert.deepEqual(mutations, []); assert.deepEqual(unexpected, []); assert.deepEqual(exceptions, []);
  pass('latest dev C4 renders with coordinator theme; review note focus and cancel preserved');
  console.log(`${checks} integration checks passed; no real DB writes.`);
} finally {
  if (ws?.readyState === WebSocket.OPEN) { try { await cdp('Browser.close'); } catch {} ws.close(); }
  chrome.kill();
  if (resolve(profile).startsWith(resolve(tmpdir()) + sep) && profile.split(sep).at(-1).startsWith('ieum-navigation-qa-')) await rm(profile, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 }).catch(() => {});
}
