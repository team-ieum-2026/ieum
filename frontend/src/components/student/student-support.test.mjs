import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import vm from "node:vm";
import ts from "typescript";

const compile = name => ts.transpileModule(readFileSync(new URL(`../../lib/api/${name}.ts`, import.meta.url), "utf8"), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText;
const sources = Object.fromEntries(["client", "profile", "student"].map(name => [name, compile(name)]));
const fixture = name => JSON.parse(readFileSync(new URL(`../../../public/mock/${name}`, import.meta.url), "utf8"));
const plain = value => JSON.parse(JSON.stringify(value));

function runtime({ mock = true, saved = new Map(), live, now = () => Date.now() } = {}) {
  const calls = [], events = [];
  const localStorage = { getItem: key => saved.get(key) ?? null, setItem: (key, value) => saved.set(key, String(value)), removeItem: key => saved.delete(key) };
  const window = new EventTarget();
  window.addEventListener("ieum:help-requests-changed", () => events.push("help"));
  const env = { Date: class extends Date { static now() { return now(); } },
    process: { env: { NEXT_PUBLIC_USE_MOCK: String(mock), NEXT_PUBLIC_API_BASE_URL: "http://backend.test" } },
    localStorage, window, Event, fetch: async (url, init) => {
      calls.push({ url, ...init });
      if (url.startsWith("/mock/")) return Response.json(fixture(url.slice(6)));
      assert.ok(live, `Unexpected backend call: ${url}`);
      return live(url, init);
    } };
  const modules = {};
  for (const name of ["client", "profile", "student"]) {
    modules[name] = {};
    vm.runInNewContext(sources[name], { ...env, exports: modules[name], require: path => modules[path.split("/").at(-1)] });
  }
  return { ...modules, saved, calls, events };
}

test("정적 목업의 초기 상태와 도움 요청을 학생별로 조회하며 NEED_HELP를 대표 상태로 쓰지 않는다", async () => {
  const { student, profile, calls } = runtime();
  assert.equal((await profile.getStudentStatus(12)).status, "SEEKING");
  assert.equal((await student.getStudentHelpRequests(12)).length, 1);
  assert.equal((await student.getStudentHelpRequests(1)).length, 0);
  assert.ok(calls.every(call => call.url.startsWith("/mock/") && call.method === "GET"));
});

test("일반 도움 요청 생성·수정·종료와 현재 상태 선택은 독립적으로 저장되고 재조회해도 유지된다", async () => {
  const { student, profile, saved, events } = runtime();
  const first = await student.createHelpRequest({ studentId: 1, recommendationItemId: null, helpType: "INTERVIEW_PREPARATION" });
  const second = await student.createHelpRequest({ studentId: 1, recommendationItemId: null, helpType: "CAREER_COUNSELING" });
  const other = await student.createHelpRequest({ studentId: 2, recommendationItemId: null, helpType: "OTHER" });
  assert.notEqual(first.helpRequestId, second.helpRequestId);
  await student.updateHelpRequest(first.helpRequestId, { contactMethod: "EMAIL", memo: "모의 면접 상담" });
  const current = await profile.selectStudentStatus(1, "EMPLOYED");
  assert.equal(current.subStatus, null);
  assert.ok(current.updatedAt);
  assert.equal((await student.getStudentHelpRequests(1)).length, 2);
  const closed = await student.closeHelpRequest(first.helpRequestId, { studentId: 1, closeReason: "EMPLOYED" });
  await assert.rejects(student.updateHelpRequest(first.helpRequestId, { contactMethod: "SMS", memo: null }), error => error.status === 409);
  assert.deepEqual(plain(await student.closeHelpRequest(first.helpRequestId, { studentId: 1, closeReason: "OTHER" })), plain(closed));
  assert.deepEqual(plain(await profile.getStudentStatus(1)), plain(current));
  assert.deepEqual(plain((await student.getStudentHelpRequests(1)).map(item => item.helpRequestId)), [second.helpRequestId]);
  const restored = runtime({ saved });
  const history = await restored.student.getStudentHelpRequests(1, "ALL");
  assert.equal(history.length, 2);
  assert.equal(history.find(item => item.helpRequestId === first.helpRequestId).memo, "모의 면접 상담");
  assert.equal((await restored.student.getStudentHelpRequests(1, "CLOSED"))[0].closeReason, "EMPLOYED");
  assert.equal((await restored.student.getStudentHelpRequests(2))[0].helpRequestId, other.helpRequestId);
  assert.equal((await restored.profile.getStudentStatus(1)).status, "EMPLOYED");
  await restored.profile.selectStudentStatus(1, "RESTING");
  assert.equal((await restored.profile.getStudentStatus(1)).status, "RESTING");
  assert.equal((await restored.student.getStudentHelpRequests(1)).length, 1);
  assert.ok(events.length >= 5);
});

test("공고 도움 요청은 추천 응답과 연결되고 취소·재요청할 때 새 요청 번호를 사용한다", async () => {
  const { student } = runtime();
  const body = { studentId: 1, recommendationItemId: 93, helpType: "JOB_APPLICATION" };
  const help = await student.createHelpRequest(body);
  const requests = await student.getStudentHelpRequests(1);
  assert.ok(requests[0].jobTitle);
  assert.equal(student.getMockRecommendationResponse(1, 93, "NONE"), "HELP");
  assert.equal((await student.getCurrentRecommendations(1)).items.find(item => item.itemId === 93).response, "HELP");
  await assert.rejects(student.createHelpRequest(body), error => error.code === "ALREADY_RESPONDED");
  await assert.rejects(student.respondToItem(93, { response: "APPLY" }, 1), error => error.code === "ALREADY_RESPONDED");
  await student.cancelItemResponse(93, "HELP", 1);
  await student.cancelItemResponse(93, "HELP", 1);
  assert.equal(student.getMockRecommendationResponse(1, 93, "HELP"), "NONE");
  assert.equal((await student.getStudentHelpRequests(1)).length, 0);
  assert.equal((await student.getStudentHelpRequests(1, "ALL"))[0].status, "CLOSED");
  const next = await student.createHelpRequest(body);
  assert.ok(next.helpRequestId > help.helpRequestId);
  assert.equal(student.getMockRecommendationResponse(1, 93, "NONE"), "HELP");
});

test("없는 요청·다른 학생·연락 완료 요청의 종료를 거절하고 현재 상태를 바꾸지 않는다", async () => {
  const { student, profile, saved } = runtime();
  const help = await student.createHelpRequest({ studentId: 1, recommendationItemId: null, helpType: "OTHER" });
  await assert.rejects(student.closeHelpRequest(help.helpRequestId, { studentId: 2, closeReason: "OTHER" }), error => error.status === 400);
  await assert.rejects(student.closeHelpRequest(99999, { studentId: 1, closeReason: "OTHER" }), error => error.status === 404);
  await assert.rejects(student.createHelpRequest({ studentId: 1, recommendationItemId: null }), error => error.status === 400);
  const key = "ieum:mock-help-requests:v1";
  const requests = JSON.parse(saved.get(key));
  requests.find(item => item.helpRequestId === help.helpRequestId).status = "CONTACTED";
  saved.set(key, JSON.stringify(requests));
  const before = saved.get(key);
  await assert.rejects(student.closeHelpRequest(help.helpRequestId, { studentId: 1, closeReason: "OTHER" }), error => error.status === 409);
  assert.equal(saved.get(key), before);
  assert.equal((await profile.getStudentStatus(1)).status, "SEEKING");
});

test("실제 모드는 기존 API 경로·메서드를 사용하고 브라우저에 목업 상태를 저장하지 않는다", async () => {
  const { student, profile, calls, saved } = runtime({ mock: false, live: () => Response.json({ data: {} }) });
  await student.getStudentHelpRequests(1, "ALL");
  await student.createHelpRequest({ studentId: 1, recommendationItemId: null, helpType: "OTHER" });
  await student.updateHelpRequest(5, { contactMethod: "SMS", memo: null });
  await student.closeHelpRequest(5, { studentId: 1, closeReason: "OTHER" });
  await profile.getStudentStatus(1);
  await profile.selectStudentStatus(1, "RESTING");
  assert.deepEqual(calls.map(call => [call.method, call.url.replace("http://backend.test", "")]), [
    ["GET", "/api/help-requests?studentId=1&status=ALL"], ["POST", "/api/help-requests"],
    ["PATCH", "/api/help-requests/5"], ["POST", "/api/help-requests/5/close"],
    ["GET", "/api/students/1/status"], ["PUT", "/api/students/1/status"],
  ]);
  assert.equal(calls.at(-1).body, JSON.stringify({ status: "RESTING" }));
  assert.equal(saved.size, 0);
});

test("실제 API 실패를 목업 성공으로 대체하지 않는다", async () => {
  const { student, profile, saved } = runtime({ mock: false, live: () => Response.json({ error: { code: "INVALID_STATUS", message: "모의 충돌" } }, { status: 409 }) });
  await assert.rejects(student.closeHelpRequest(5, { studentId: 1, closeReason: "OTHER" }), error => error.status === 409);
  await assert.rejects(profile.selectStudentStatus(1, "EMPLOYED"), error => error.status === 409);
  assert.equal(saved.size, 0);
});

test("목업 재생성은 해당 FAILED 지원서만 GENERATING으로 바꾸고 진행 조회 후 DRAFT가 된다", async () => {
  let clock = 10000;
  const { student, saved } = runtime({ now: () => clock });
  const key = "ieum:mock-applications:v1";
  saved.set(key, JSON.stringify([
    { applicationId: 7, studentId: 1, itemId: 91, status: "FAILED" },
    { applicationId: 8, studentId: 2, itemId: 92, status: "FAILED" },
  ]));
  assert.deepEqual(plain(await student.regenerateApplication(7)), { applicationId: 7, status: "GENERATING" });
  assert.equal((await student.getApplicationProgress(7)).status, "GENERATING");
  await assert.rejects(student.regenerateApplication(7), error => error.status === 409);
  clock += 2000;
  assert.equal((await student.getApplicationProgress(7)).status, "DRAFT");
  assert.deepEqual(JSON.parse(saved.get(key)).map(item => item.status), ["DRAFT", "FAILED"]);
  await assert.rejects(student.regenerateApplication(7), error => error.status === 409);
  await assert.rejects(student.regenerateApplication(99999), error => error.status === 404);
});

test("실제 재생성은 본문 없는 POST를 사용하고 시작 응답과 실패를 검증한다", async () => {
  const { student, calls, saved } = runtime({ mock: false,
    live: () => Response.json({ data: { applicationId: 7, status: "GENERATING" } }) });
  assert.deepEqual(plain(await student.regenerateApplication(7)), { applicationId: 7, status: "GENERATING" });
  assert.equal(calls[0].url, "http://backend.test/api/applications/7/regenerate");
  assert.equal(calls[0].method, "POST");
  assert.equal(calls[0].body, undefined);
  assert.equal(saved.size, 0);
  for (const data of [{ applicationId: 8, status: "GENERATING" }, { applicationId: 7, status: "DRAFT" }, null]) {
    const invalid = runtime({ mock: false, live: () => Response.json({ data }) });
    await assert.rejects(invalid.student.regenerateApplication(7), error => error.code === "INVALID_RESPONSE");
  }
  const conflict = runtime({ mock: false, live: () => Response.json({ error: { code: "INVALID_STATUS", message: "이미 생성 중이에요." } }, { status: 409 }) });
  await assert.rejects(conflict.student.regenerateApplication(7), error => error.status === 409);
});
