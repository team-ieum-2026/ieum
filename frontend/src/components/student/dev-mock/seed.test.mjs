import assert from "node:assert/strict";
import { once } from "node:events";
import test from "node:test";
import { MockModel } from "./model.mjs";
import { createMockServer } from "./server.mjs";
import { readSeed } from "./seed.mjs";

const now = Date.parse("2026-10-27T12:00:00+09:00");
const url = (path) => new URL(path, "http://localhost");

test("원본 시드 전체의 ID, 원문, 관계와 상태를 보존한다", () => {
  const db = readSeed(now); const model = new MockModel({ now: () => now });
  assert.equal(model.state.students.size, 50);
  assert.equal(model.seedJobs.size, 15);
  assert.equal(model.state.items.size, 93);
  assert.equal(model.state.recommendations.size, 31);
  assert.equal(model.state.applications.size, 4);
  assert.equal(model.state.helps.size, 4);
  for (const student of db.student) {
    const p = model.student(student.id).profile;
    assert.equal(p.basic.name, student.name);
    assert.equal(p.basic.githubUrl, student.github_url);
    assert.deepEqual(p.skills, JSON.parse(student.skills));
    assert.deepEqual(p.projects.map((p) => p.itemId), db.student_project.filter((p) => p.student_id === student.id).map((p) => p.id));
    assert.equal(p.coverLetter?.content ?? null, db.student_cover_letter.find((p) => p.student_id === student.id)?.content ?? null);
    assert.deepEqual(p.competencyProfile, JSON.parse(db.competency_profile.find((p) => p.student_id === student.id)?.profile ?? "null"));
    const expected = db.student_status.find((s) => s.student_id === student.id);
    assert.equal(model.student(student.id).row.status, expected.status);
    assert.equal(model.student(student.id).row.subStatus, expected.sub_status);
    model.coordinatorStudent(student.id); // 전체 이력의 지원서 참조도 검증
  }
  for (const app of db.application) {
    assert.equal(model.applicationDetail(app.id).status, app.status);
    assert.deepEqual(model.applicationDetail(app.id).document, JSON.parse(db.application_document.find((d) => d.application_id === app.id).document));
    assert.equal(model.item(app.recommendation_item_id).applicationId, app.id);
  }
  assert.equal(model.student(21).row.lastResponseAt, "2026-10-17T10:00:00.000+09:00");
  assert.deepEqual([...model.state.students.values()].flatMap((s) => s.logs).map((l) => l.contactLogId).sort(), [1, 2, 3]);
});

test("기본 HTTP 서버는 시드 50명과 명세의 응답 구조를 반환한다", async (t) => {
  const server = createMockServer({ now: () => now });
  server.listen(0, "127.0.0.1"); await once(server, "listening");
  t.after(() => new Promise((resolve) => { server.closeAllConnections(); server.close(resolve); }));
  async function request(path, method = "GET", body) {
    const response = await fetch(`http://127.0.0.1:${server.address().port}${path}`, { method,
      headers: body ? { "Content-Type": "application/json" } : {}, body: body ? JSON.stringify(body) : undefined });
    const result = await response.json(); assert.equal(response.status, 200, JSON.stringify(result));
    assert.deepEqual(Object.keys(result), ["data"]); return result.data;
  }
  const first = await request("/api/coordinator/students");
  assert.equal(first.total, 50); assert.equal(first.items.length, 20);
  assert.equal((await request("/api/coordinator/students?page=3")).items.length, 10);
  const dashboard = await request("/api/coordinator/dashboard");
  assert.equal(dashboard.totalStudents, 50); assert.equal(dashboard.helpRequests.length, 4);
  assert.deepEqual(dashboard.reviewQueue.map((a) => a.applicationId), [101, 102]);
  const batch = await request("/api/students/1/recommendations/current");
  assert.deepEqual(batch.items.map((i) => i.job.jobPostingId), [501, 502, 503]);
  const detail = await request(`/api/recommendation-items/${batch.items[0].itemId}`);
  assert.equal(detail.job.workAddress, null); assert.equal(detail.job.homepageUrl, null);
  assert.equal(detail.job.dDay, 7); assert.equal(detail.job.deadlineOn, "2026-11-03");
  assert.equal(detail.job.source, "DEV_SEED");
  assert.ok(detail.job.description.includes("LLM"));
  // 활성 공고만 새 추천에 사용하고, 이미 응답한 공고는 제외한다.
  for (const item of batch.items) await request(`/api/recommendation-items/${item.itemId}/response`, "POST", { response: "PASS", passReason: "JOB_MISMATCH", passNote: null });
  assert.deepEqual((await request("/api/students/1/recommendations/refresh", "POST")).items, []);
  await request("/api/coordinator/help-requests/104", "PATCH", { status: "CLOSED" });
  assert.equal((await request("/api/coordinator/dashboard")).helpRequests.length, 3);
});

test("시드 기반 새 지원서 생성·편집·제출은 ID 충돌 없이 동작한다", () => {
  let clock = now; const model = new MockModel({ now: () => clock });
  const dispatch = (path, method = "GET", body = {}) => model.dispatch(method, url(path), body);
  const batch = dispatch("/api/students/1/recommendations/current");
  const applied = dispatch(`/api/recommendation-items/${batch.items[0].itemId}/response`, "POST", { response: "APPLY" });
  assert.equal(applied.applicationId, 105); clock += 2300;
  const app = dispatch("/api/applications/105"); assert.equal(app.status, "DRAFT");
  assert.equal(app.document.sections.find((s) => s.section === "PROJECTS").items.length, 2);
  dispatch("/api/applications/105/document", "PATCH", { op: "ADD_ITEM", section: "CERTIFICATES", item: { name: "모의 자격증" }, saveToProfile: true });
  const added = dispatch("/api/students/1/profile").certificates.at(-1);
  assert.equal(added.issuer, null); assert.equal(added.acquiredOn, null);
  assert.ok(added.itemId > 126);
  dispatch("/api/applications/105/submit", "POST");
  assert.equal(dispatch("/api/coordinator/applications/105").status, "SUBMITTED");
  assert.equal(dispatch("/api/applications/101").status, "SUBMITTED");
});
