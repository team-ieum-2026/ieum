import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve, sep } from 'node:path';
import { pathToFileURL } from 'node:url';

const origin = process.env.IEUM_QA_ORIGIN || 'http://localhost:3000';
const port = Number(process.env.IEUM_CDP_PORT || 9241);
const output = process.env.IEUM_QA_OUTPUT ? pathToFileURL(resolve(process.env.IEUM_QA_OUTPUT) + sep) : new URL('./screenshots/document-reading/', import.meta.url);
await mkdir(output, { recursive: true });
const browserProfile = await mkdtemp(join(tmpdir(), 'ieum-document-reading-'));
const fixture = async name => JSON.parse(await readFile(new URL(`../../../../public/mock/${name}`, import.meta.url), 'utf8'));
const original = await fixture('M6_application_document.json');
const profile = await fixture('M8_student_profile.json');
const recommendations = await fixture('M1_recommendations_current.json');
const progress = await fixture('M5_application_progress_generating.json');
let detail = structuredClone(original.data);
let failSave = false;
let patchCount = 0;
let lastPatch;
const failures = [];
const checks = [];
const pending = new Map();
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
const chrome = spawn(process.env.IEUM_CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe', ['--headless=new', '--disable-gpu', '--no-first-run', `--remote-debugging-port=${port}`, `--user-data-dir=${browserProfile}`, 'about:blank'], { windowsHide: true, stdio: 'ignore' });
let ws;
let sequence = 0;

function cdp(method, params = {}) {
  const id = ++sequence;
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => { pending.delete(id); reject(Error(`CDP timeout: ${method}`)); }, 30000);
    pending.set(id, { resolve: result => { clearTimeout(timer); resolve(result); }, reject });
    ws.send(JSON.stringify({ id, method, params }));
  });
}
async function evaluate(expression) {
  const result = await cdp('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
  if (result.exceptionDetails) throw Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text);
  return result.result?.value;
}
async function until(expression) {
  const started = Date.now();
  while (Date.now() - started < 45000) {
    try { if (await evaluate(expression)) return; } catch { /* Navigation creates a new context. */ }
    await pause(100);
  }
  throw Error(`Timed out: ${expression}`);
}
async function click(selector) {
  await until(`document.querySelector(${JSON.stringify(selector)}) !== null`);
  await evaluate(`document.querySelector(${JSON.stringify(selector)}).click()`);
}
async function snapshot(name) {
  await evaluate('document.fonts.ready.then(() => true)');
  const result = await cdp('Page.captureScreenshot', { format: 'png' });
  await writeFile(new URL(name, output), Buffer.from(result.data, 'base64'));
}
function pass(name) { checks.push(name); console.log('PASS ' + name); }
async function navigate(path) {
  await cdp('Page.navigate', { url: origin + path + '?studentId=1' });
  await until("document.querySelector('.s-student-document-heading') !== null");
}
async function respond({ requestId, request }) {
  const path = new URL(request.url).pathname;
  let status = 200;
  let body;
  if (request.method === 'OPTIONS') { status = 204; }
  else if (request.method === 'PATCH' && path === '/api/applications/7/document') {
    patchCount++;
    lastPatch = JSON.parse(request.postData);
    await pause(200);
    if (failSave) { status = 503; body = { error: { code: 'QA_FAILURE', message: '저장을 다시 시도해주세요.' } }; }
    else {
      const sentence = detail.document.sections.find(section => section.section === 'COVER_LETTER').sentences.find(sentence => sentence.sentenceId === lastPatch.sentenceId);
      assert.equal(lastPatch.op, 'UPDATE_SENTENCE');
      sentence.text = lastPatch.text;
      sentence.source = 'USER';
      sentence.evidence = [];
      body = { data: { document: detail.document } };
    }
  } else if (request.method !== 'GET') {
    failures.push(`Unexpected write blocked: ${request.method} ${path}`);
    status = 503;
    body = { error: { code: 'QA_BLOCKED', message: 'QA blocks server writes.' } };
  } else if (path === '/api/students/1/profile') body = profile;
  else if (path === '/api/applications/7') body = { data: detail };
  else if (path === '/api/applications/7/progress') body = progress;
  else if (path === '/api/students/1/recommendations/current') body = recommendations;
  else {
    failures.push(`Unexpected API: ${path}`);
    status = 404;
    body = { error: { code: 'NOT_FOUND', message: 'QA route missing.' } };
  }
  await cdp('Fetch.fulfillRequest', { requestId, responseCode: status, responseHeaders: [
    { name: 'Content-Type', value: 'application/json' },
    { name: 'Access-Control-Allow-Origin', value: origin },
    { name: 'Access-Control-Allow-Methods', value: 'GET,PATCH,OPTIONS' },
    { name: 'Access-Control-Allow-Headers', value: 'Content-Type' },
  ], body: Buffer.from(body ? JSON.stringify(body) : '').toString('base64') });
}

try {
  let target;
  for (let i = 0; i < 100; i++) {
    try { target = (await (await fetch(`http://127.0.0.1:${port}/json/list`)).json()).find(target => target.type === 'page'); if (target) break; } catch {}
    await pause(100);
  }
  assert.ok(target, 'Chrome started');
  ws = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { ws.addEventListener('open', resolve, { once: true }); ws.addEventListener('error', reject, { once: true }); });
  ws.addEventListener('message', ({ data }) => {
    const message = JSON.parse(data);
    if (message.id) {
      const task = pending.get(message.id);
      if (!task) return;
      pending.delete(message.id);
      if (message.error) task.reject(Error(message.error.message)); else task.resolve(message.result);
    }
    if (message.method === 'Fetch.requestPaused') void respond(message.params).catch(error => failures.push(error.stack));
    if (message.method === 'Runtime.exceptionThrown') failures.push(message.params.exceptionDetails.exception?.description || message.params.exceptionDetails.text);
  });
  await cdp('Page.enable');
  await cdp('Runtime.enable');
  await cdp('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false });
  await cdp('Fetch.enable', { patterns: [{ urlPattern: '*://*/api/*' }] });
  await navigate('/student/applications/7/cover-letter');
  await until("document.querySelectorAll('.s-document-sentence').length === 4");
  assert.equal(await evaluate("document.querySelectorAll('.s-document-sentence[aria-pressed=true]').length"), 0);
  assert.equal(await evaluate("getComputedStyle(document.querySelector('.s-document-sentence-text')).backgroundColor"), 'rgba(0, 0, 0, 0)');
  assert.equal(await evaluate("getComputedStyle(document.querySelector('.s-document-sentence-text')).outlineStyle"), 'none');
  assert.equal(await evaluate("document.querySelectorAll('.s-letter-sentence').length"), 0);
  assert.equal(await evaluate("document.querySelector('.s-student-document-heading h1').textContent"), '자기소개서');
  assert.equal(await evaluate("document.querySelectorAll('.s-cover-page .s-page-heading, .s-student-document-company, .s-student-document-job').length"), 0);
  assert.equal(await evaluate("document.querySelectorAll('.s-letter-sentences > p').length"), 1);
  assert.equal(await evaluate("document.querySelector('.s-letter-source-toggle').getAttribute('aria-checked')"), 'false');
  assert.equal(await evaluate("document.querySelector('.s-cover-toolbar a').getAttribute('href')"), '/student/applications/7?studentId=1');
  assert.match(await evaluate("document.querySelector('.s-document-sentence[data-source=USER] .sr-only').textContent"), /직접 작성·수정/);
  assert.equal(await evaluate("document.querySelectorAll('.s-document-sentence-dot').length"), 0);
  assert.equal(await evaluate("document.querySelectorAll('.s-document-sentence-source').length"), 0);
  const initialSentence = detail.document.sections.find(section => section.section === 'COVER_LETTER').sentences[0];
  assert.equal(await evaluate("document.querySelector('.s-letter-evidence textarea').value"), initialSentence.text);
  assert.equal(await evaluate("document.querySelector('.s-letter-selected-summary blockquote').textContent"), initialSentence.text);
  assert.ok(await evaluate("[...document.querySelectorAll('.s-letter-evidence button')].some(button => button.textContent === '취소')"));
  assert.equal(await evaluate("document.querySelector('.s-letter-evidence .s-button-primary').disabled"), true);
  await snapshot('cover-initial.png');
  pass('The document has only its title, continuous prose, source counts, and a return link; source highlighting starts off');
  pass('The first sentence evidence and editor are visible immediately without adding default body highlights');

  await evaluate("document.querySelector('.s-letter-evidence textarea').focus(); document.querySelector('.s-letter-evidence textarea').select()");
  await cdp('Input.insertText', { text: '취소하면 저장된 문장으로 돌아가야 해요.' });
  await click('.s-letter-evidence button:not(.s-button-primary)');
  assert.equal(await evaluate("document.querySelector('.s-letter-evidence textarea').value"), initialSentence.text);
  assert.equal(patchCount, 0);
  pass('Cancel restores the original sentence without saving or hiding the editor');

  await click('.s-letter-source-toggle');
  assert.equal(await evaluate("document.querySelector('.s-letter-source-toggle').getAttribute('aria-checked')"), 'true');
  assert.ok(await evaluate("[...document.querySelectorAll('.s-document-sentence-text')].every(text => getComputedStyle(text).outlineStyle === 'solid')"));
  assert.equal(await evaluate("getComputedStyle(document.querySelector('[data-source=AI] .s-document-sentence-text')).outlineColor"), 'rgb(128, 87, 245)');
  assert.equal(await evaluate("getComputedStyle(document.querySelector('[data-source=USER] .s-document-sentence-text')).outlineColor"), 'rgb(49, 130, 246)');
  await snapshot('cover-sources-on.png');
  await click('.s-letter-source-toggle');
  assert.ok(await evaluate("[...document.querySelectorAll('.s-document-sentence-text')].every(text => getComputedStyle(text).outlineStyle === 'none')"));
  pass('The source switch shows purple AI borders and blue USER borders, then restores plain prose');

  const hoverPoint = await evaluate("(() => {const rect=document.querySelector('[data-source=AI] .s-document-sentence-text').getClientRects()[0];return {x:rect.x+4,y:rect.y+rect.height/2};})()");
  await cdp('Input.dispatchMouseEvent', { type: 'mouseMoved', ...hoverPoint });
  assert.equal(await evaluate("getComputedStyle(document.querySelector('[data-source=AI] .s-document-sentence-text')).outlineStyle"), 'solid');
  assert.equal(await evaluate("getComputedStyle(document.querySelector('[data-source=USER] .s-document-sentence-text')).outlineStyle"), 'none');
  await cdp('Input.dispatchMouseEvent', { type: 'mouseMoved', x: 1, y: 1 });
  assert.equal(await evaluate("getComputedStyle(document.querySelector('[data-source=AI] .s-document-sentence-text')).outlineStyle"), 'none');
  pass('With the switch off, hovering reveals only the hovered sentence source border');

  const firstAI = detail.document.sections.find(section => section.section === 'COVER_LETTER').sentences.find(sentence => sentence.source === 'AI');
  const sentenceSelector = `[data-sentence-id="${firstAI.sentenceId}"]`;
  await click(sentenceSelector);
  await until("document.querySelector('.s-letter-evidence textarea') !== null");
  assert.equal(await evaluate(`document.querySelector(${JSON.stringify(sentenceSelector)}).getAttribute('aria-pressed')`), 'true');
  assert.equal(await evaluate(`getComputedStyle(document.querySelector(${JSON.stringify(sentenceSelector)} + ' .s-document-sentence-text')).outlineStyle`), 'solid');
  assert.equal(await evaluate("document.querySelector('.s-letter-selected-summary blockquote').textContent"), firstAI.text);
  assert.ok((await evaluate("document.querySelector('.s-letter-evidence').textContent")).includes(firstAI.evidence[0].quote));
  await snapshot('cover-selected.png');
  pass('Selecting a sentence highlights only its text and shows exact evidence and origin');

  await evaluate(`document.querySelector(${JSON.stringify(sentenceSelector)}).focus()`);
  await cdp('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Enter', code: 'Enter', windowsVirtualKeyCode: 13 });
  await cdp('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Enter', code: 'Enter', windowsVirtualKeyCode: 13 });
  assert.equal(await evaluate('document.activeElement.dataset.sentenceId'), firstAI.sentenceId);
  pass('Keyboard selection retains focus');

  await evaluate("document.querySelector('.s-letter-evidence textarea').focus(); document.querySelector('.s-letter-evidence textarea').select()");
  const revised = firstAI.text + ' 직접 고친 문장이에요.';
  await cdp('Input.insertText', { text: revised });
  await evaluate("document.querySelector('.s-letter-evidence .s-button-primary').scrollIntoView({block:'nearest'})");
  const applyPoint = await evaluate("(() => {const button=document.querySelector('.s-letter-evidence .s-button-primary');const rect=button.getBoundingClientRect();const x=rect.x+rect.width/2,y=rect.y+rect.height/2;return {x,y,unobstructed:button.contains(document.elementFromPoint(x,y))};})()");
  assert.ok(applyPoint.unobstructed, 'The floating help button must not cover Apply');
  await cdp('Input.dispatchMouseEvent', { type: 'mousePressed', button: 'left', clickCount: 1, x: applyPoint.x, y: applyPoint.y });
  await cdp('Input.dispatchMouseEvent', { type: 'mouseReleased', button: 'left', clickCount: 1, x: applyPoint.x, y: applyPoint.y });
  await until(`document.querySelector(${JSON.stringify(sentenceSelector)}).dataset.source === 'USER'`);
  assert.equal(lastPatch.text, revised);
  assert.equal(lastPatch.sentenceId, firstAI.sentenceId);
  assert.equal(await evaluate(`document.querySelector(${JSON.stringify(sentenceSelector)} + ' .s-document-sentence-text').textContent`), revised);
  assert.ok((await evaluate("document.querySelector('.s-letter-evidence').textContent")).includes('직접 작성·수정'));
  pass('Applying edits preserves the API contract and updates source to USER');

  await click('.s-cover-toolbar a');
  await until("document.querySelector('.s-resume-document-heading') !== null");
  assert.equal(await evaluate('location.pathname + location.search'), '/student/applications/7?studentId=1');
  assert.ok((await evaluate("document.querySelector('.s-cover-preview').textContent")).includes(revised));
  await navigate('/student/applications/7/cover-letter');
  await until("document.querySelectorAll('.s-document-sentence').length === 4");
  assert.equal(await evaluate(`document.querySelector(${JSON.stringify(sentenceSelector)} + ' .s-document-sentence-text').textContent`), revised);
  pass('The return button opens the same application and preserves saved sentence edits');

  const nextAI = detail.document.sections.find(section => section.section === 'COVER_LETTER').sentences.find(sentence => sentence.source === 'AI');
  await click(`[data-sentence-id="${nextAI.sentenceId}"]`);
  await evaluate("document.querySelector('.s-letter-evidence textarea').focus(); document.querySelector('.s-letter-evidence textarea').select()");
  await cdp('Input.insertText', { text: '저장 실패 시 유지할 입력' });
  failSave = true;
  await click('.s-letter-evidence .s-button-primary');
  await until("document.querySelector('.s-letter-evidence [role=alert]') !== null");
  assert.equal(await evaluate("document.querySelector('.s-letter-evidence textarea').value"), '저장 실패 시 유지할 입력');
  assert.equal(await evaluate(`document.querySelector('[data-sentence-id="${nextAI.sentenceId}"]').dataset.source`), 'AI');
  failSave = false;
  pass('Save failures retain unsaved input and the previous sentence source');

  detail.status = 'SUBMITTED';
  await navigate('/student/applications/7/cover-letter');
  await click('.s-document-sentence');
  assert.equal(await evaluate("document.querySelector('.s-letter-evidence textarea').disabled"), true);
  assert.equal(await evaluate("document.querySelector('.s-letter-evidence .s-button-primary').disabled"), true);
  assert.ok((await evaluate("document.querySelector('.s-letter-evidence').textContent")).includes('매니저에게 검토를 요청한 서류는 수정할 수 없어요.'));
  assert.equal(await evaluate("document.querySelector('.s-contextbar > span').textContent"), '매니저 검토 중');
  pass('Submitted documents show the editor with a clear explanation and disabled controls');

  detail = structuredClone(original.data);
  const cover = detail.document.sections.find(section => section.section === 'COVER_LETTER');
  cover.sentences = Array.from({ length: 40 }, (_, index) => ({ ...structuredClone(cover.sentences[index % 4]), sentenceId: `long-${index}` }));
  await navigate('/student/applications/7/cover-letter');
  await until("document.querySelectorAll('.s-document-sentence').length === 40");
  await evaluate("document.querySelector('[data-sentence-id=long-20]').scrollIntoView({block:'center'})");
  const before = await evaluate('scrollY');
  await click('[data-sentence-id=long-20]');
  await pause(250);
  assert.ok(Math.abs((await evaluate('scrollY')) - before) < 2, 'Desktop selection must not jump to the side panel');
  const panel = await evaluate("(() => { const panel=document.querySelector('.s-letter-evidence'); const bounds=panel.getBoundingClientRect(); return {top:bounds.top,bottom:bounds.bottom,position:getComputedStyle(panel).position}; })()");
  assert.equal(panel.position, 'sticky');
  assert.ok(panel.top >= 80 && panel.top <= 120, JSON.stringify(panel));
  assert.ok(panel.bottom <= 1000, JSON.stringify(panel));
  await snapshot('cover-long-scroll.png');
  pass('Long documents keep the evidence panel in view without moving the desktop reading position');

  await cdp('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: true });
  await cdp('Page.navigate', { url: origin + '/student/applications/7/cover-letter?studentId=1' });
  await until("document.querySelectorAll('.s-document-sentence').length === 40");
  assert.equal(await evaluate("getComputedStyle(document.querySelector('.s-letter-evidence')).position"), 'static');
  assert.ok(await evaluate('document.documentElement.scrollWidth <= innerWidth + 1'));
  await snapshot('cover-mobile.png');
  pass('Mobile stacks the panels without horizontal overflow');

  detail = structuredClone(original.data);
  await cdp('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false });
  await navigate('/student/applications/7');
  await until("document.querySelectorAll('.student-section-card').length === 9");
  assert.ok(await evaluate("Math.abs(document.querySelector('.s-cover-preview').getBoundingClientRect().top - document.querySelector('.s-resume-layout > .s-action-panel').getBoundingClientRect().top) < 1"), 'Resume document and action panel start on the same line');
  assert.equal(await evaluate("document.querySelectorAll('.s-cover-preview .border-b-2').length"), 0);
  assert.equal(await evaluate("document.querySelector('.s-resume-document-heading').textContent"), '이력서');
  await click('.student-section-card[data-section=SKILLS]');
  await until("document.querySelector('.s-resume-sentence-row') !== null");
  assert.equal(await evaluate("getComputedStyle(document.querySelector('.s-resume-sentence-row')).borderTopWidth"), '0px');
  assert.equal(await evaluate("document.querySelectorAll('.s-resume-sentence-row button').length > 0"), true);
  pass('Resume keeps all nine section editors; narrative rows and cover preview have no automatic boxes or highlighting');

  await cdp('Page.navigate', { url: origin + '/student/applications/7/progress?studentId=1' });
  await until("document.querySelector('.s-progress-frame')?.getBoundingClientRect().width > 0");
  const frame = await evaluate("(() => { const frame=document.querySelector('.s-progress-frame'); const rect=frame.getBoundingClientRect(); return {centerX:rect.left+rect.width/2, centerY:rect.top+rect.height/2, viewportWidth:document.documentElement.clientWidth, viewportHeight:innerHeight, border:getComputedStyle(frame).borderTopWidth, innerBorder:getComputedStyle(frame.querySelector('.s-progress-card')).borderTopWidth, links:document.querySelectorAll('.s-progress .s-back-link').length}; })()");
  assert.ok(Math.abs(frame.centerX - frame.viewportWidth / 2) < 1, JSON.stringify(frame));
  assert.ok(Math.abs(frame.centerY - frame.viewportHeight / 2) < 2, JSON.stringify(frame));
  assert.equal(frame.border, '1px');
  assert.equal(frame.innerBorder, '0px');
  assert.equal(frame.links, 0);
  await snapshot('progress-centered.png');
  pass('Generation container is centered in the viewport with one border and no back link');

  assert.equal(patchCount, 2);
  assert.deepEqual(failures, []);
  await writeFile(new URL('results.json', output), JSON.stringify({ checks, patchCount, realDatabaseWrites: 0, failures }, null, 2));
  console.log(`${checks.length} browser checks passed. Real database writes: 0.`);
} finally {
  if (ws?.readyState === WebSocket.OPEN) { try { await cdp('Browser.close'); } catch {} ws.close(); }
  chrome.kill();
  if (resolve(browserProfile).startsWith(resolve(tmpdir()) + sep) && browserProfile.split(sep).at(-1).startsWith('ieum-document-reading-')) {
    await rm(browserProfile, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 }).catch(() => {});
  }
}
