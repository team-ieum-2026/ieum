import assert from "node:assert/strict";
import { once } from "node:events";
import { readFileSync } from "node:fs";
import test from "node:test";
import { createMockServer } from "./server.mjs";

async function fixture(t) {
  let clock = Date.parse("2026-10-27T12:00:00+09:00");
  const server = createMockServer({ now: () => clock, generationMs: 2200, dataSource: "fixtures" });
  server.listen(0, "127.0.0.1"); await once(server, "listening");
  t.after(() => new Promise((resolve) => { server.closeAllConnections(); server.close(resolve); }));
  const base = `http://127.0.0.1:${server.address().port}`;
  async function request(path, method = "GET", body, expected = 200) {
    const response = await fetch(base + path, { method, headers: body === undefined ? {} : { "Content-Type": "application/json" }, body: body === undefined ? undefined : JSON.stringify(body) });
    const payload = await response.json();
    assert.equal(response.status, expected, JSON.stringify(payload));
    if (expected === 200) { assert.deepEqual(Object.keys(payload), ["data"]); return payload.data; }
    assert.deepEqual(Object.keys(payload), ["error"]); assert.equal(typeof payload.error.message, "string"); return payload.error;
  }
  return { request, base, advance: (ms) => { clock += ms; } };
}

async function newDraft(f) {
  const rec = await f.request("/api/students/1/recommendations/refresh", "POST");
  const item = rec.items.find((entry) => entry.response === "NONE");
  assert.ok(item);
  const response = await f.request(`/api/recommendation-items/${item.itemId}/response`, "POST", { response: "APPLY" });
  f.advance(2201);
  const detail = await f.request(`/api/applications/${response.applicationId}`);
  assert.equal(detail.status, "DRAFT");
  return { ...response, job: item.job, document: detail.document };
}

test("M6-2 단일 섹션의 itemId null을 허용하고 프로필 동시 저장 선택을 지킨다", async (t) => {
  const cases = [
    { section: "BASIC", item: { desiredJob: "모의 수정 직무" }, documentValue: (s) => s.fields.desiredJob, profileValue: (p) => p.basic.desiredJob, expected: "모의 수정 직무" },
    { section: "MILITARY", item: { value: "EXEMPT" }, documentValue: (s) => s.value, profileValue: (p) => p.basic.militaryStatus, expected: "EXEMPT" },
    { section: "SKILLS", item: { tags: ["Java", "모의 기술"] }, documentValue: (s) => s.tags, profileValue: (p) => p.skills, expected: ["Java", "모의 기술"] },
  ];
  for (const entry of cases) for (const saveToProfile of [false, true]) {
    await t.test(`${entry.section}, saveToProfile=${saveToProfile}`, async (st) => {
      const f = await fixture(st);
      const draft = await newDraft(f);
      const app = `/api/applications/${draft.applicationId}`;
      const before = await f.request("/api/students/1/profile");
      const result = await f.request(`${app}/document`, "PATCH", {
        op: "UPDATE_ITEM", section: entry.section, itemId: null, item: entry.item, saveToProfile,
      });
      const section = result.document.sections.find((s) => s.section === entry.section);
      assert.deepEqual(entry.documentValue(section), entry.expected);
      assert.deepEqual((await f.request(app)).document, result.document);
      const profile = await f.request("/api/students/1/profile");
      if (saveToProfile) assert.deepEqual(entry.profileValue(profile), entry.expected);
      else assert.deepEqual(profile, before);
    });
  }
});

test("M6-2 목록의 null ID와 단일 섹션의 잘못된 ID·추가·삭제 요청은 거절한다", async (t) => {
  const f = await fixture(t);
  const draft = await newDraft(f);
  const app = `/api/applications/${draft.applicationId}`;
  const before = await f.request(app);
  for (const body of [
    { op: "UPDATE_ITEM", section: "CERTIFICATES", itemId: null, item: { name: "모의 자격증" }, saveToProfile: false },
    { op: "DELETE_ITEM", section: "CERTIFICATES", itemId: null, saveToProfile: false },
    { op: "UPDATE_ITEM", section: "BASIC", itemId: "basic-1", item: { desiredJob: "모의 직무" }, saveToProfile: false },
    { op: "ADD_ITEM", section: "BASIC", item: { name: "모의 이름" }, saveToProfile: false },
    { op: "DELETE_ITEM", section: "BASIC", itemId: null, saveToProfile: false },
  ]) {
    const error = await f.request(`${app}/document`, "PATCH", body, 400);
    assert.equal(error.code, "INVALID_REQUEST");
    assert.deepEqual(await f.request(app), before);
  }
});

test("추천 → 지원 → 시간에 따른 생성 → 제출 → 승인/전달이 모든 조회에 일치한다", async (t) => {
  const f = await fixture(t); const r = f.request;
  const recommendation = await r("/api/students/1/recommendations/refresh", "POST");
  assert.ok(recommendation.items.every((entry) => ![501, 502].includes(entry.job.jobPostingId)));
  const item = recommendation.items[0];
  const result = await r(`/api/recommendation-items/${item.itemId}/response`, "POST", { response: "APPLY" });
  const app = `/api/applications/${result.applicationId}`;
  assert.equal((await r(app)).status, "GENERATING");
  assert.equal((await r(app)).document, null);
  const progress = await r(app + "/progress");
  assert.equal(progress.status, "GENERATING"); assert.equal(progress.steps[0].state, "RUNNING");
  assert.equal((await r(app + "/submit", "POST", undefined, 409)).code, "INVALID_STATUS");
  f.advance(1000); const mid = await r(app + "/progress"); assert.equal(mid.steps[0].state, "DONE");
  f.advance(1500); assert.equal((await r(app)).status, "DRAFT");
  assert.ok((await r(app + "/progress")).steps.every((step) => step.state === "DONE"));
  const detail = await r(app); assert.equal(detail.job.jobPostingId, item.job.jobPostingId);
  assert.equal(detail.document.companyInfoStatus, "NOT_FOUND");
  assert.equal((await r(`/api/recommendation-items/${item.itemId}`)).response, "APPLY");
  assert.equal((await r(`/api/recommendation-items/${item.itemId}/response`, "POST", { response: "APPLY" }, 409)).code, "ALREADY_RESPONDED");
  assert.equal((await r(app + "/submit", "POST")).status, "SUBMITTED");
  assert.ok((await r("/api/coordinator/dashboard")).reviewQueue.some((entry) => entry.applicationId === result.applicationId));
  const review = `/api/coordinator/applications/${result.applicationId}`;
  assert.equal((await r(review)).status, "SUBMITTED");
  assert.equal((await r(review + "/deliver", "POST", undefined, 409)).code, "INVALID_STATUS");
  await r(review + "/approve", "POST"); await r(review + "/deliver", "POST");
  assert.equal((await r("/api/students/1/applications")).applications.find((entry) => entry.applicationId === result.applicationId).status, "DELIVERED");
  const history = (await r("/api/coordinator/students/1")).history.flatMap((entry) => entry.items);
  assert.equal(history.find((entry) => entry.applicationId === result.applicationId).applicationStatus, "DELIVERED");
  assert.ok(!(await r("/api/coordinator/dashboard")).reviewQueue.some((entry) => entry.applicationId === result.applicationId));
});

test("문장/항목 수정과 프로필 동시 저장, 실패 시 원복, 반려 후 오류를 검증한다", async (t) => {
  const f = await fixture(t); const r = f.request; const draft = await newDraft(f); const app = `/api/applications/${draft.applicationId}`;
  await r(app + "/document", "PATCH", { op: "UPDATE_SENTENCE", sentenceId: "cl-1", text: "모의 수정 문장" });
  const document = (await r(app)).document;
  const sentence = document.sections.find((entry) => entry.section === "COVER_LETTER").sentences[0];
  assert.equal(sentence.source, "USER"); assert.deepEqual(sentence.evidence, []);
  assert.equal((await r("/api/students/1/applications")).applications.find((entry) => entry.applicationId === draft.applicationId).userEditedCount, 1);
  await r(app + "/document", "PATCH", { op: "ADD_ITEM", section: "CERTIFICATES", item: { name: "모의 자격증", issuer: null, acquiredOn: null }, saveToProfile: true });
  let profile = await r("/api/students/1/profile");
  const certificate = profile.certificates.find((entry) => entry.name === "모의 자격증"); assert.ok(certificate);
  const saved = (await r(app)).document;
  const cert = saved.sections.find((entry) => entry.section === "CERTIFICATES").items.find((entry) => entry.name === "모의 자격증");
  await r(app + "/document", "PATCH", { op: "UPDATE_ITEM", section: "CERTIFICATES", itemId: cert.itemId, item: { name: "모의 변경 자격증" }, saveToProfile: true });
  profile = await r("/api/students/1/profile"); assert.equal(profile.certificates.find((entry) => entry.itemId === certificate.itemId).name, "모의 변경 자격증");
  const before = (await r(app)).document;
  // 서류 전용 포트폴리오 추가를 프로필의 새 프로젝트로 조용히 만들지 않는다.
  await r(app + "/document", "PATCH", { op: "ADD_ITEM", section: "PORTFOLIO", item: { fileName: "mock.pdf", include: false }, saveToProfile: true }, 400);
  assert.deepEqual((await r(app)).document, before);
  await r(app + "/document", "PATCH", { op: "DELETE_ITEM", section: "CERTIFICATES", itemId: cert.itemId, saveToProfile: true });
  assert.ok(!(await r("/api/students/1/profile")).certificates.some((entry) => entry.itemId === certificate.itemId));
  await r(app + "/submit", "POST");
  await r(`/api/coordinator/applications/${draft.applicationId}/reject`, "POST", { note: "모의 보완 사항" });
  assert.equal((await r(app)).status, "REJECTED");
  assert.equal((await r("/api/coordinator/students/1")).contactLogs[0].note, "모의 보완 사항");
  await r(app + "/document", "PATCH", { op: "UPDATE_SENTENCE", sentenceId: "cl-1", text: "다시 수정" }, 409);
});

test("패스 사유·도움 요청·연락 기록이 추천, 학생 상태와 대시보드에 반영된다", async (t) => {
  const f = await fixture(t); const r = f.request;
  await r("/api/recommendation-items/93/response", "POST", { response: "PASS" }, 400);
  await r("/api/recommendation-items/93/response", "POST", { response: "PASS", passReason: "NOT_AN_ENUM" }, 400);
  await r("/api/recommendation-items/93/response", "POST", { response: "PASS", passReason: "OTHER", passNote: "모의 비고" });
  assert.equal((await r("/api/students/1/applications")).otherItems.find((entry) => entry.itemId === 93).passReason, "OTHER");
  assert.ok((await r("/api/coordinator/dashboard")).notesToCheck.some((entry) => entry.passNote === "모의 비고"));
  const rec = await r("/api/students/1/recommendations/refresh", "POST");
  assert.deepEqual(rec.items.map((entry) => entry.job.jobPostingId), [504]);
  const help = await r("/api/help-requests", "POST", { studentId: 1, recommendationItemId: rec.items[0].itemId });
  await r(`/api/help-requests/${help.helpRequestId}`, "PATCH", { contactMethod: "SMS", memo: null });
  assert.equal((await r("/api/coordinator/students/1")).student.status, "NEED_HELP");
  assert.ok((await r("/api/coordinator/dashboard")).helpRequests.some((entry) => entry.helpRequestId === help.helpRequestId && entry.contactMethod === "SMS" && entry.memo === null));
  await r(`/api/coordinator/help-requests/${help.helpRequestId}`, "PATCH", { status: "CONTACTED" });
  assert.equal((await r("/api/coordinator/students/1")).student.status, "SEEKING");
  assert.ok(!(await r("/api/coordinator/dashboard")).helpRequests.some((entry) => entry.helpRequestId === help.helpRequestId));
  const log = await r("/api/coordinator/students/12/contact-logs", "POST", { contactType: "PHONE", note: "모의 통화" });
  assert.equal((await r("/api/coordinator/students/12")).contactLogs[0].contactLogId, log.contactLogId);
});

test("M8 수정은 전체 프로필을 반환하고 근거 참조 및 갱신 시각이 유지된다", async (t) => {
  const f = await fixture(t); const r = f.request;
  const initial = await r("/api/students/1/profile");
  const added = await r("/api/students/1/profile/items", "POST", { section: "CERTIFICATES", item: { name: "모의 인증", issuer: null, acquiredOn: null } });
  assert.ok(Date.parse(added.profileUpdatedAt) > Date.parse(initial.profileUpdatedAt));
  const certificate = added.certificates.at(-1); assert.equal(certificate.source, "USER"); assert.equal(certificate.issuer, null);
  const patched = await r(`/api/students/1/profile/items/${certificate.itemId}`, "PATCH", { section: "CERTIFICATES", item: { name: "모의 인증 수정" } });
  assert.ok(Date.parse(patched.profileUpdatedAt) > Date.parse(added.profileUpdatedAt));
  assert.ok(patched.competencyProfile.certificates.includes("모의 인증 수정"));
  await r(`/api/students/1/profile/items/${certificate.itemId}?section=CERTIFICATES`, "DELETE");
  assert.ok(!(await r("/api/students/1/profile")).certificates.some((entry) => entry.itemId === certificate.itemId));
  const project = await r("/api/students/1/profile/items", "POST", { section: "PROJECTS", item: { title: "모의 프로젝트", techStack: ["Node.js"] } });
  const projectId = project.projects.at(-1).itemId;
  const skill = project.competencyProfile.skills.find((entry) => entry.name === "Node.js");
  assert.deepEqual(skill.evidence, [{ refType: "PROJECT", refId: projectId }]);
  const changed = await r("/api/students/1/cover-letter", "PUT", { content: "모의 원문" });
  assert.equal(changed.coverLetter.content, "모의 원문"); assert.equal(changed.basic.name, initial.basic.name);
  assert.ok(!Object.hasOwn(changed, "profile")); assert.ok(Object.hasOwn(changed, "competencyProfile"));
  await r("/api/students/1/profile/items", "POST", { section: "TRAINING", item: { name: "수정 불가" } }, 400);
  await r("/api/students/1/profile/items", "POST", { section: "CERTIFICATES", item: { name: "잘못된 날짜", acquiredOn: "2026-02-30" } }, 400);
});

test("학생별 데이터 격리, 검색/페이지, 초기 데이터 정규화, 존재하지 않는 ID", async (t) => {
  const f = await fixture(t); const r = f.request;
  const list = await r("/api/coordinator/students?size=2&page=1"); assert.equal(list.total, 5); assert.equal(list.items.length, 2);
  const next = await r("/api/coordinator/students?size=2&page=2"); assert.equal(next.items.length, 2); assert.notEqual(list.items[0].studentId, next.items[0].studentId);
  const q = await r(`/api/coordinator/students?q=${encodeURIComponent(list.items[0].name)}`); assert.equal(q.total, 1);
  const filtered = await r("/api/coordinator/students?status=NEED_HELP"); assert.ok(filtered.items.every((entry) => entry.status === "NEED_HELP"));
  const a = await r("/api/students/1/recommendations/current"); const b = await r("/api/students/12/recommendations/current");
  assert.ok(!a.items.some((item) => b.items.some((other) => other.itemId === item.itemId)));
  await r(`/api/recommendation-items/${b.items[0].itemId}/response`, "POST", { response: "APPLY" });
  assert.equal((await r("/api/students/1/applications")).applications.length, 1);
  assert.equal((await r("/api/students/12/applications")).applications.length, 1);
  const profile = await r("/api/students/12/profile"); assert.equal(profile.studentId, 12); assert.deepEqual(profile.projects, []);
  assert.equal((await r("/api/applications/7")).status, "DRAFT");
  const initialApplication = (await r("/api/students/1/applications")).applications.find((entry) => entry.applicationId === 7);
  assert.equal(initialApplication.status, "DRAFT");
  assert.equal(initialApplication.submittedAt, null);
  assert.equal((await r("/api/coordinator/applications/7")).status, "DRAFT");
  assert.equal((await r("/api/coordinator/students/1")).history.flatMap((entry) => entry.items).find((entry) => entry.applicationId === 7).applicationStatus, "DRAFT");
  assert.ok(!(await r("/api/coordinator/dashboard")).reviewQueue.some((entry) => entry.applicationId === 7));
  assert.ok(!(await r("/api/coordinator/dashboard")).reviewQueue.some((entry) => entry.applicationId === 101));
  for (const [path, code] of [["/api/students/999/profile", "STUDENT_NOT_FOUND"], ["/api/applications/999", "APPLICATION_NOT_FOUND"], ["/api/recommendation-items/999", "NOT_FOUND"], ["/api/no-such-route", "NOT_FOUND"]]) {
    assert.equal((await r(path, "GET", undefined, 404)).code, code);
  }
  await r("/api/coordinator/students?page=0", "GET", undefined, 400);
  const bad = await r("/api/help-requests", "POST", { studentId: 1, recommendationItemId: b.items[1].itemId }, 400); assert.equal(bad.code, "INVALID_REQUEST");
});

test("모든 후보에 응답하면 빈 추천을 반환하고 다시 추천해도 응답한 공고를 넣지 않는다", async (t) => {
  const f = await fixture(t); const r = f.request;
  let batch = await r("/api/students/12/recommendations/current");
  const answeredJobs = new Set();
  for (let round = 0; round < 3 && batch.items.length; round++) {
    for (const item of batch.items) {
      assert.ok(!answeredJobs.has(item.job.jobPostingId));
      await r(`/api/recommendation-items/${item.itemId}/response`, "POST", { response: "PASS", passReason: "JOB_MISMATCH", passNote: null });
      answeredJobs.add(item.job.jobPostingId);
    }
    batch = await r("/api/students/12/recommendations/refresh", "POST");
  }
  assert.equal(answeredJobs.size, 4);
  assert.deepEqual(batch.items, []);
  assert.deepEqual((await r("/api/students/12/recommendations/current")).items, []);
  assert.deepEqual((await r("/api/students/12/recommendations/refresh", "POST")).items, []);
  assert.equal((await r("/api/students/12/profile")).studentId, 12);
  assert.equal((await r("/api/students/1/recommendations/current")).items.length, 3);
});

test("CORS/JSON 오류와 서버 재시작 초기화, 원본 목업 무변경", async (t) => {
  const file = new URL("../../../../public/mock/M8_student_profile.json", import.meta.url); const original = readFileSync(file, "utf8");
  const f = await fixture(t);
  const preflight = await fetch(f.base + "/api/students/1/profile", { method: "OPTIONS", headers: { Origin: "http://localhost:3000", "Access-Control-Request-Method": "PATCH" } });
  assert.equal(preflight.status, 204); assert.equal(preflight.headers.get("access-control-allow-origin"), "http://localhost:3000");
  const invalidOrigin = await fetch(f.base + "/api/students/1/profile", { headers: { Origin: "https://example.com" } }); assert.equal(invalidOrigin.status, 400);
  const invalidJson = await fetch(f.base + "/api/help-requests", { method: "POST", headers: { "Content-Type": "application/json" }, body: "{" });
  assert.equal(invalidJson.status, 400); assert.equal((await invalidJson.json()).error.code, "INVALID_REQUEST");
  await f.request("/api/students/1/cover-letter", "PUT", { content: "임시 수정" });
  const fresh = await fixture(t); assert.notEqual((await fresh.request("/api/students/1/profile")).coverLetter.content, "임시 수정");
  assert.equal(readFileSync(file, "utf8"), original);
});
