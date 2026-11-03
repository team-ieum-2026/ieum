// M5 browser regression. All API traffic is intercepted; the real DB is never changed.
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join, resolve, sep } from "node:path";

const origin = process.env.IEUM_QA_ORIGIN || "http://localhost:3000";
const port = Number(process.env.IEUM_CDP_PORT || 9236);
const directory = new URL("./screenshots/", import.meta.url);
const fixture = async name => JSON.parse(await readFile(new URL(`../../../../public/mock/${name}`, import.meta.url), "utf8"));
const fixtures = Object.fromEntries(await Promise.all([
  "M5_application_progress_generating.json", "M5_application_progress_done.json", "M6_application_document.json",
  "M8_student_profile.json", "M1_recommendations_current.json", "M7_student_applications.json",
].map(async name => [name, await fixture(name)])));
const profile = await mkdtemp(join(tmpdir(), "ieum-progress-qa-"));
const chrome = spawn(process.env.IEUM_CHROME_PATH || "C:/Program Files/Google/Chrome/Application/chrome.exe", [
  "--headless=new", "--disable-gpu", "--no-first-run", `--remote-debugging-port=${port}`, `--user-data-dir=${profile}`, "about:blank",
], { windowsHide: true, stdio: "ignore" });
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
const pending = new Map(), exceptions = [], unexpected = [], requests = [], checks = [];
let ws, sequence = 0, pageLoads = 0, state = "FAILED", nextState = "DRAFT", progressError = false, postError = 0, holdPost = false, releasePost;

function cdp(method, params = {}) {
  return new Promise((resolve, reject) => {
    const id = ++sequence;
    const timer = setTimeout(() => { pending.delete(id); reject(Error(`CDP timeout: ${method}`)); }, 20000);
    pending.set(id, { resolve: value => { clearTimeout(timer); resolve(value); }, reject: error => { clearTimeout(timer); reject(error); } });
    ws.send(JSON.stringify({ id, method, params }));
  });
}
async function evaluate(expression) {
  const result = await cdp("Runtime.evaluate", { expression, returnByValue: true, awaitPromise: true });
  if (result.exceptionDetails) throw Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text);
  return result.result?.value;
}
async function until(expression) {
  for (let i = 0; i < 300; i++) {
    try { if (await evaluate(expression)) return; } catch { /* Navigation may replace the execution context. */ }
    await pause(100);
  }
  throw Error(`Timeout: ${expression}`);
}
const retryButton = "Array.from(document.querySelectorAll('.s-progress button')).find(e => e.textContent.includes('다시 시도'))";
async function openProgress() {
  const previous = pageLoads;
  await cdp("Page.navigate", { url: `${origin}/student/applications/7/progress?studentId=12` });
  for (let i = 0; i < 300 && pageLoads === previous; i++) await pause(100);
  assert.ok(pageLoads > previous, "Progress navigation must finish");
  await until(`${retryButton} !== undefined`);
}
async function retry() { await evaluate(`(${retryButton}).click()`); }
const posts = () => requests.filter(request => request.method === "POST");
const gets = () => requests.filter(request => request.path.endsWith("/progress"));
function pass(name) { checks.push(name); console.log("PASS", name); }
async function snapshot(name) {
  await mkdir(directory, { recursive: true });
  const result = await cdp("Page.captureScreenshot", { format: "png" });
  await writeFile(new URL(name, directory), Buffer.from(result.data, "base64"));
}

async function respond({ request, requestId }) {
  const path = new URL(request.url).pathname;
  let body, status = 200;
  if (request.method === "OPTIONS") body = {};
  else {
    requests.push({ path, method: request.method, body: request.postData });
    if (path === "/api/applications/7/regenerate" && request.method === "POST") {
      if (holdPost) await new Promise(resolve => { releasePost = resolve; });
      if (postError) {
        status = postError;
        body = { error: { code: "INVALID_STATUS", message: "모의 생성 시작 실패" } };
      } else {
        state = "GENERATING";
        body = { data: { applicationId: 7, status: "GENERATING" } };
      }
    } else if (request.method !== "GET") {
      unexpected.push(request.method + " " + path);
      status = 503; body = { error: { code: "QA_BLOCKED", message: "Unexpected write blocked" } };
    } else if (path === "/api/applications/7/progress") {
      if (progressError) {
        progressError = false;
        status = 503; body = { error: { code: "NETWORK_ERROR", message: "모의 진행 조회 실패" } };
      } else {
        body = structuredClone(fixtures[state === "GENERATING" ? "M5_application_progress_generating.json" : "M5_application_progress_done.json"]);
        body.data.applicationId = 7;
        body.data.status = state;
        if (state === "FAILED") body.data.steps.at(-1).state = "FAILED";
        if (state === "GENERATING") state = nextState;
      }
    } else if (path === "/api/applications/7") body = fixtures["M6_application_document.json"];
    else if (/^\/api\/students\/\d+\/profile$/.test(path)) body = fixtures["M8_student_profile.json"];
    else if (path.endsWith("/recommendations/current")) body = fixtures["M1_recommendations_current.json"];
    else if (/^\/api\/students\/\d+\/applications$/.test(path)) body = fixtures["M7_student_applications.json"];
    else if (path.endsWith("/status")) body = { data: { studentId: 12, status: "SEEKING", subStatus: "WAITING", updatedAt: null } };
    else if (path === "/api/help-requests") body = { data: [] };
    else {
      unexpected.push(path); status = 404; body = { error: { code: "NOT_FOUND", message: "QA route missing" } };
    }
  }
  await cdp("Fetch.fulfillRequest", { requestId, responseCode: status, responseHeaders: [
    { name: "Content-Type", value: "application/json" }, { name: "Access-Control-Allow-Origin", value: origin },
    { name: "Access-Control-Allow-Methods", value: "GET,POST,OPTIONS" }, { name: "Access-Control-Allow-Headers", value: "Content-Type" },
  ], body: Buffer.from(JSON.stringify(body)).toString("base64") });
}

try {
  let target;
  for (let i = 0; i < 80; i++) {
    try { target = (await (await fetch(`http://127.0.0.1:${port}/json/list`)).json()).find(item => item.type === "page"); if (target) break; } catch {}
    await pause(100);
  }
  assert.ok(target);
  ws = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise(resolve => ws.addEventListener("open", resolve, { once: true }));
  ws.addEventListener("message", event => {
    const message = JSON.parse(event.data);
    if (message.id) {
      const task = pending.get(message.id); if (!task) return; pending.delete(message.id);
      if (message.error) task.reject(Error(message.error.message)); else task.resolve(message.result);
    }
    if (message.method === "Fetch.requestPaused") void respond(message.params).catch(error => exceptions.push(error.message));
    if (message.method === "Page.loadEventFired") pageLoads++;
    if (message.method === "Runtime.exceptionThrown") exceptions.push(message.params.exceptionDetails.exception?.description || message.params.exceptionDetails.text);
  });
  await cdp("Page.enable"); await cdp("Runtime.enable");
  await cdp("Emulation.setDeviceMetricsOverride", { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false });
  await cdp("Fetch.enable", { patterns: [{ urlPattern: "*://*/api/*" }] });

  holdPost = true;
  await openProgress();
  const before = gets().length;
  await evaluate(`(() => { const button = ${retryButton}; button.click(); button.click(); button.click(); })()`);
  await until("document.querySelector('.s-progress button[aria-busy=true]')?.disabled === true");
  for (let i = 0; i < 30 && !releasePost; i++) await pause(100);
  assert.equal(posts().length, 1);
  assert.equal(posts()[0].body, undefined);
  assert.equal(gets().length, before, "Polling must wait for successful POST");
  await snapshot("progress-regenerate-pending.png");
  holdPost = false; releasePost(); releasePost = undefined;
  await until("location.pathname === '/student/applications/7'");
  assert.equal(await evaluate("new URLSearchParams(location.search).get('studentId')"), "12");
  assert.ok(gets().length >= before + 2);
  pass("FAILED → one POST despite repeated clicks → GENERATING polling → DRAFT → M6 with studentId preserved");

  for (const failure of [503, 409]) {
    state = "FAILED"; postError = failure;
    await openProgress();
    const count = gets().length;
    await retry();
    await until("document.querySelector('.s-progress [role=alert]')?.textContent.includes('모의 생성 시작 실패')");
    assert.equal(gets().length, count);
    assert.ok(await evaluate(`(${retryButton})?.disabled === false`));
    pass(`${failure} shows start failure and allows retry without pretending generation started`);
  }
  postError = 0; state = "FAILED"; nextState = "FAILED";
  await openProgress(); await retry();
  await until("document.querySelector('.s-progress .s-progress-card') !== null && document.body.textContent.includes('초안 생성이 잠시 멈췄어요.')");
  await until(`${retryButton} !== undefined`);
  assert.ok(await evaluate("location.pathname.endsWith('/progress')"));
  await snapshot("progress-regenerate-failed.png");
  pass("A second generation failure stays on M5 with failure guidance and another retry");

  nextState = "DRAFT"; state = "DRAFT"; progressError = true;
  await openProgress();
  const mutations = posts().length;
  await retry();
  await until("location.pathname === '/student/applications/7'");
  assert.equal(posts().length, mutations);
  pass("A progress GET error retries the GET without regenerating an existing draft");

  state = "FAILED"; holdPost = true;
  await openProgress(); await retry();
  for (let i = 0; i < 30 && !releasePost; i++) await pause(100);
  assert.ok(releasePost);
  await evaluate("Array.from(document.querySelectorAll('a')).find(link => new URL(link.href).pathname === '/student/profile').click()");
  await until("document.querySelector('.s-profile-current-status') !== null");
  holdPost = false; releasePost(); releasePost = undefined;
  await pause(500);
  assert.equal(await evaluate("location.pathname"), "/student/profile");
  pass("Leaving M5 during the POST ignores its late result");
  assert.deepEqual(unexpected, []);
  assert.deepEqual(exceptions, []);
  console.log(JSON.stringify({ passed: checks.length, checks, actualDatabaseWrites: 0 }, null, 2));
} finally {
  if (releasePost) releasePost();
  if (ws?.readyState === WebSocket.OPEN) { try { await cdp("Browser.close"); } catch {} ws.close(); }
  chrome.kill();
  if (resolve(profile).startsWith(resolve(tmpdir()) + sep) && profile.split(sep).at(-1).startsWith("ieum-progress-qa-")) {
    await rm(profile, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 }).catch(() => {});
  }
}
