import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import vm from "node:vm";
import ts from "typescript";

const compile = (url) => ts.transpileModule(readFileSync(url, "utf8"), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText;
const apiSource = compile(new URL("../../lib/api/student.ts", import.meta.url));
const cacheSource = compile(new URL("./mockRecommendationResponses.ts", import.meta.url));
const fixture = (name) => JSON.parse(readFileSync(new URL(`../../../public/mock/${name}`, import.meta.url), "utf8")).data;
const applicationKey = "ieum:mock-applications:v1";
const passesKey = "ieum:mock-passes:v1";

class ApiError extends Error {
  constructor(code, message, status) { super(message); this.code = code; this.status = status; }
}

function storage() {
  const items = new Map();
  return { getItem: (key) => items.get(key) ?? null, setItem: (key, value) => items.set(key, String(value)), removeItem: (key) => items.delete(key) };
}

function runtime({ mock = true, deleteResponse, deleteError } = {}) {
  const localStorage = storage(), sessionStorage = storage(), events = [], calls = [];
  const client = {
    ApiError, DEMO_STUDENT_ID: 1,
    apiGet: async (_path, name) => fixture(name),
    apiPost: async (_path, name) => fixture(name),
    apiDelete: async (...args) => { calls.push(args); if (deleteError) throw deleteError; return deleteResponse; },
  };
  const api = {};
  const env = { process: { env: { NEXT_PUBLIC_USE_MOCK: mock ? "true" : "false" } }, localStorage, sessionStorage, window: { dispatchEvent: (event) => events.push(event.type) }, Event };
  vm.runInNewContext(apiSource, { ...env, exports: api, require: (path) => path.endsWith("/client") ? client : {} });
  const cache = {};
  vm.runInNewContext(cacheSource, { ...env, exports: cache, require: () => api });
  return { api, cache, localStorage, calls, events };
}

test("APPLY 취소는 해당 학생·공고만 정리하고 원본 응답을 다시 살리지 않는다", async () => {
  const { api, cache, localStorage } = runtime();
  localStorage.setItem(applicationKey, JSON.stringify([
    { studentId: 1, itemId: 91, applicationId: 7, status: "DRAFT" },
    { studentId: 2, itemId: 91, applicationId: 8, status: "DRAFT" },
    { studentId: 1, itemId: 92, applicationId: 9, status: "DRAFT" },
  ]));
  cache.rememberResponse(1, 91, "APPLY");
  await api.cancelItemResponse(91, "APPLY", 1);
  cache.clearRememberedResponse(1, 91);
  assert.equal(cache.restoreResponse(1, 91, "APPLY"), "NONE");
  assert.deepEqual(JSON.parse(localStorage.getItem(applicationKey)).map((item) => item.applicationId), [8, 9]);
  assert.equal(api.getMockRecommendationResponse(2, 91, "NONE"), "APPLY");
});

test("검토 요청 이후 지원서는 취소하지 않고 다른 응답 유형도 지우지 않는다", async () => {
  for (const status of ["SUBMITTED", "APPROVED", "REJECTED", "DELIVERED"]) {
    const { api, localStorage } = runtime();
    const saved = JSON.stringify([{ studentId: 1, itemId: 91, applicationId: 7, status }]);
    localStorage.setItem(applicationKey, saved);
    await assert.rejects(api.cancelItemResponse(91, "APPLY", 1), (error) => error.code === "INVALID_STATUS");
    assert.equal(localStorage.getItem(applicationKey), saved);
  }
  const { api, localStorage } = runtime();
  localStorage.setItem(passesKey, JSON.stringify([{ studentId: 1, itemId: 91, passReason: "OTHER" }]));
  await assert.rejects(api.cancelItemResponse(91, "HELP", 1), (error) => error.code === "INVALID_STATUS");
  await assert.rejects(api.cancelItemResponse(91, "NONE", 1), (error) => error.code === "INVALID_REQUEST");
});

test("PASS·HELP 취소와 HELP 재요청은 학생별 캐시를 일관되게 복원한다", async () => {
  const { api, cache, localStorage } = runtime();
  localStorage.setItem(passesKey, JSON.stringify([
    { studentId: 1, itemId: 91, passReason: "OTHER" },
    { studentId: 2, itemId: 91, passReason: "OTHER" },
  ]));
  await api.cancelItemResponse(91, "PASS", 1);
  assert.equal(cache.restoreResponse(1, 91, "PASS"), "NONE");
  assert.equal(JSON.parse(localStorage.getItem(passesKey)).length, 1);
  cache.rememberResponse(1, 92, "HELP");
  await api.cancelItemResponse(92, "HELP", 1);
  cache.clearRememberedResponse(1, 92);
  assert.equal(cache.restoreResponse(1, 92, "HELP"), "NONE");
  cache.rememberResponse(1, 92, "HELP");
  assert.equal(cache.restoreResponse(1, 92, "NONE"), "HELP");
});

test("취소 후 재지원하면 이전 지원서 번호를 재사용하지 않는다", async () => {
  const { api } = runtime();
  const itemId = fixture("M1_recommendations_current.json").items[0].itemId;
  const first = await api.respondToItem(itemId, { response: "APPLY" }, 1);
  await api.cancelItemResponse(itemId, "APPLY", 1);
  const second = await api.respondToItem(itemId, { response: "APPLY" }, 1);
  assert.ok(second.applicationId > first.applicationId);
  assert.equal(api.getMockRecommendationResponse(1, itemId, "NONE"), "APPLY");
});

test("실제 API 모드에서는 제안 DELETE를 사용하고 성공 응답을 검증한다", async () => {
  for (const response of ["APPLY", "PASS", "HELP"]) {
    const { api, calls, events, localStorage } = runtime({ mock: false, deleteResponse: { itemId: 91, response: "NONE", applicationId: null } });
    await api.cancelItemResponse(91, response, 1);
    assert.deepEqual(calls, [[`/api/recommendation-items/91/response?expectedResponse=${response}`, ""]]);
    assert.equal(events.length, 1);
    assert.equal(localStorage.getItem(applicationKey), null);
  }
  const invalid = runtime({ mock: false, deleteResponse: { itemId: 91, response: "APPLY", applicationId: 7 } });
  await assert.rejects(invalid.api.cancelItemResponse(91, "APPLY", 1), (error) => error.code === "INVALID_RESPONSE");
  assert.deepEqual(invalid.events, []);
  const failed = runtime({ mock: false, deleteError: new ApiError("INVALID_STATUS", "거절", 409) });
  await assert.rejects(failed.api.cancelItemResponse(91, "HELP", 1), (error) => error.status === 409);
  assert.deepEqual(failed.events, []);
});
