// 실제 BE 조회만 허용하는 브라우저 검증. PUT은 전송 전에 차단해 DB와 선택 이력을 바꾸지 않는다.
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve, sep } from 'node:path';

const origin = process.env.IEUM_QA_ORIGIN || 'http://localhost:3000';
const api = process.env.IEUM_QA_API_ORIGIN || 'http://localhost:8080';
const port = Number(process.env.IEUM_CDP_PORT || 9224);
const profile = await mkdtemp(join(tmpdir(), 'ieum-real-db-qa-'));
const chrome = spawn(process.env.IEUM_CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe', ['--headless=new', '--disable-gpu', '--no-first-run', `--remote-debugging-port=${port}`, `--user-data-dir=${profile}`, 'about:blank'], { windowsHide: true, stdio: 'ignore' });
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
const pending = new Map();
const requests = [];
const exceptions = [];
let putBody;
let ws;
let sequence = 0;
async function cdp(method, params = {}) {
  return new Promise((resolve, reject) => {
    const id = ++sequence;
    const timer = setTimeout(() => { pending.delete(id); reject(Error('CDP timeout: ' + method)); }, 60000);
    pending.set(id, { resolve: result => { clearTimeout(timer); resolve(result); }, reject: error => { clearTimeout(timer); reject(error); } });
    ws.send(JSON.stringify({ id, method, params }));
  });
}
async function evaluate(expression) {
  const r = await cdp('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
  if (r.exceptionDetails) throw Error(r.exceptionDetails.exception?.description || r.exceptionDetails.text);
  return r.result?.value;
}
async function until(expression) {
  const started = Date.now();
  while (Date.now() - started < 45000) {
    try { if (await evaluate(expression)) return; } catch { /* 새 문서의 컨텍스트 대기 */ }
    await pause(150);
  }
  throw Error('Timeout: ' + expression);
}
async function click(selector) {
  const p = await evaluate(`(() => { const e=document.querySelector(${JSON.stringify(selector)}); e.scrollIntoView({block:'center'}); const r=e.getBoundingClientRect(); return {x:r.x+r.width/2,y:r.y+r.height/2}; })()`);
  await cdp('Input.dispatchMouseEvent', { type: 'mousePressed', button: 'left', clickCount: 1, ...p });
  await cdp('Input.dispatchMouseEvent', { type: 'mouseReleased', button: 'left', clickCount: 1, ...p });
}
async function readStatus(id) {
  const r = await fetch(`${api}/api/students/${id}/status`);
  assert.equal(r.status, 200);
  return (await r.json()).data;
}
async function openProfile(id) {
  await cdp('Page.navigate', { url: `${origin}/student/profile?studentId=${id}` });
  await until('document.querySelector(".s-profile-status-value")?.dataset.status !== undefined && document.querySelector(".s-profile-status-value").dataset.status !== "UNSET"');
}

try {
  let target;
  for (let n = 0; n < 80; n++) {
    try { target = (await (await fetch(`http://127.0.0.1:${port}/json/list`)).json()).find(t => t.type === 'page'); if (target) break; } catch {}
    await pause(150);
  }
  assert.ok(target);
  ws = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { ws.addEventListener('open', resolve, { once: true }); ws.addEventListener('error', reject, { once: true }); });
  ws.addEventListener('message', ({ data }) => {
    const message = JSON.parse(data);
    if (message.id) {
      const task = pending.get(message.id); if (!task) return;
      pending.delete(message.id);
      if (message.error) task.reject(Error(message.error.message)); else task.resolve(message.result);
    }
    if (message.method === 'Network.requestWillBeSent') requests.push(message.params.request.url);
    if (message.method === 'Runtime.exceptionThrown') exceptions.push(message.params.exceptionDetails.text);
    if (message.method === 'Fetch.requestPaused') {
      const { request, requestId } = message.params;
      if (request.method === 'PUT') {
        putBody = JSON.parse(request.postData);
        void cdp('Fetch.failRequest', { requestId, errorReason: 'ConnectionFailed' });
      } else void cdp('Fetch.continueRequest', { requestId });
    }
  });
  await cdp('Page.enable'); await cdp('Runtime.enable'); await cdp('Network.enable');
  await cdp('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false });
  const labels = { SEEKING: '구직 중', EMPLOYED: '취업 완료', RESTING: '잠시 쉬는 중', CHECK_REQUIRED: '추가 확인 필요' };
  for (const id of [1, 12, 24, 32, 34, 47]) {
    const stored = await readStatus(id);
    await openProfile(id);
    assert.equal(await evaluate('document.querySelector(".s-profile-status-value").dataset.status'), stored.status);
    assert.equal(await evaluate('document.querySelector(".s-profile-status-value").textContent'), labels[stored.status]);
    console.log(`PASS student ${id}: UI matches DB status ${stored.status}`);
  }
  await evaluate('localStorage.setItem("ieum:profile-status-preview:v1:47", JSON.stringify("PREPARING"))');
  await openProfile(47);
  const before = await readStatus(47);
  assert.equal(await evaluate('document.querySelector(".s-profile-status-value").dataset.status'), before.status);
  await click('.s-profile-status-heading button');
  await until('document.querySelector(".student-ui-sheet[open]") !== null');
  assert.deepEqual(await evaluate('Array.from(document.querySelectorAll(".s-profile-status-option input")).map(e=>e.value).sort()'), ['EMPLOYED', 'RESTING', 'SEEKING']);
  console.log('PASS legacy browser preview ignored; only server-supported choices');

  const helpResponse = await fetch(`${api}/api/help-requests?studentId=47&status=OPEN`);
  const helpBody = await helpResponse.json();
  if (helpResponse.ok) {
    await until(`document.querySelectorAll('.s-my-help-list > li').length === ${helpBody.data.length}`);
    assert.equal(await evaluate("document.querySelector('.s-my-help [role=alert]') !== null"), false);
    console.log('PASS real help list matches backend array');
  } else {
    await until("document.querySelector('.s-my-help [role=alert]') !== null");
    assert.ok((await evaluate("document.querySelector('.s-my-help [role=alert]').textContent")).includes(helpBody.error.message));
    assert.equal(await evaluate("document.querySelector('.s-my-help-empty') !== null"), false);
    console.log(`PASS real help list HTTP ${helpResponse.status} is visible; no false empty state`);
  }

  await cdp('Fetch.enable', { patterns: [{ urlPattern: '*/api/students/*/status', requestStage: 'Request' }] });
  const choice = before.status === 'RESTING' ? 'SEEKING' : 'RESTING';
  await click(`.s-profile-status-option input[value="${choice}"]`);
  await click('.s-cancel-dialog-actions .s-button-primary');
  await until('document.querySelector(".student-ui-sheet[open] [role=alert]") !== null');
  assert.deepEqual(putBody, { status: choice });
  assert.equal(await evaluate('document.querySelector(".s-profile-status-value").dataset.status'), before.status);
  assert.deepEqual(await readStatus(47), before);
  await click('.s-cancel-dialog-actions .s-button:not(.s-button-primary)');
  await cdp('Fetch.disable');
  console.log('PASS PUT payload, save failure feedback, prior state preserved; DB unchanged');

  assert.ok(requests.some(url => url.startsWith(api) && url.includes('/status')));
  assert.ok(requests.every(url => !url.includes(':4000/') && !url.includes('/mock/')));
  assert.deepEqual(exceptions, []);
  await evaluate('document.fonts.ready.then(()=>true)');
  await evaluate('window.scrollTo(0,0)');
  const image = await cdp('Page.captureScreenshot', { format: 'png' });
  const directory = new URL('./screenshots/', import.meta.url);
  await mkdir(directory, { recursive: true });
  await writeFile(new URL('real-db-profile.png', directory), Buffer.from(image.data, 'base64'));
  console.log('PASS only real backend requests; no browser exceptions');
} finally {
  if (ws?.readyState === WebSocket.OPEN) { try { await cdp('Browser.close'); } catch {} ws.close(); }
  chrome.kill();
  const root = resolve(tmpdir()) + sep;
  if (resolve(profile).startsWith(root) && profile.split(sep).at(-1).startsWith('ieum-real-db-qa-')) await rm(profile, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 }).catch(() => {});
}
