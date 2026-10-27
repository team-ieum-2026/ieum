import { readFileSync, readdirSync } from "node:fs";
import { ApiFault, choice, id, keys, object, passReasons, record, requireValue, text, validateItem } from "./validation.mjs";

const fixtureDirectory = new URL("../../../../public/mock/", import.meta.url);
const clone = (value) => structuredClone(value);
const DAY = 86_400_000;
const collectionNames = { EDUCATION: "education", CAREER: "careers", CERTIFICATES: "certificates", PROJECTS: "projects" };
const prefixes = { EDUCATION: "edu", CAREER: "car", CERTIFICATES: "cert", PROJECTS: "prj", PORTFOLIO: "pf" };

function sentences(value) {
  if (!value || typeof value !== "object") return [];
  if (Object.hasOwn(value, "sentenceId")) return [value];
  return Object.values(value).flatMap(sentences);
}

export class MockModel {
  constructor({ now = Date.now, generationMs = 2200 } = {}) {
    this.now = now;
    this.generationMs = generationMs;
    this.fixtures = Object.fromEntries(readdirSync(fixtureDirectory).filter((name) => name.endsWith(".json"))
      .map((name) => [name, JSON.parse(readFileSync(new URL(name, fixtureDirectory), "utf8"))]));
    const fixture = (name) => clone(this.fixtures[name].data);
    this.state = { students: new Map(), items: new Map(), recommendations: new Map(), current: new Map(),
      applications: new Map(), helps: new Map(), nextItem: 1000, nextRecommendation: 100, nextApplication: 8, nextHelp: 5, nextRecord: 1000, nextContact: 3 };
    const s = this.state;
    const profile = fixture("M8_student_profile.json");
    const detail = fixture("C3_coordinator_student_detail.json");
    for (const row of fixture("C2_coordinator_students.json").items) {
      const ownProfile = row.studentId === profile.studentId ? clone(profile) : {
        studentId: row.studentId, profileUpdatedAt: null,
        basic: { name: row.name, email: null, githubUrl: null, desiredJob: null, preferredRegion: null, militaryStatus: null, completedOn: row.completedOn },
        course: { courseId: row.studentId, name: row.courseLabel, cohort: "모의 과정", campus: "모의 캠퍼스", startedOn: "2026-03-02", endedOn: row.completedOn },
        education: [], careers: [], certificates: [], skills: [], projects: [], coverLetter: null, competencyProfile: null,
      };
      s.students.set(row.studentId, { row, profile: ownProfile, logs: row.studentId === 1 ? detail.contactLogs : [], reason: null });
    }
    this.catalog = [...new Map([fixture("M1_recommendations_current.json"), fixture("M1_recommendations_refresh.json")]
      .flatMap((batch) => batch.items).map((item) => [item.job.jobPostingId, item])).values()];
    for (const name of ["M1_recommendations_current.json", "M1_recommendations_refresh.json"]) {
      const batch = fixture(name);
      this.addRecommendation(1, batch, name.includes("current"));
    }
    // 사용자가 제출 전 흐름을 검증할 수 있도록 M6의 초안 상태로 통일한다.
    const application = fixture("M6_application_document.json");
    s.applications.set(7, { ...application, status: "DRAFT", studentId: 1, itemId: 91,
      submittedAt: null, createdAt: this.now(), profileLinks: new Map() });
    for (const history of detail.history) for (const saved of history.items) {
      const item = s.items.get(saved.itemId);
      if (item) Object.assign(item, { response: saved.response, passReason: saved.passReason, passNote: saved.passNote, respondedAt: saved.respondedAt, applicationId: saved.applicationId });
    }
    for (const help of fixture("C1_coordinator_dashboard.json").helpRequests) {
      s.helps.set(help.helpRequestId, { ...help, status: "OPEN", recommendationItemId: null });
    }
    // C2의 OTHER 상태를 뒷받침하는 비고를 별도 학생 이력에 연결한다.
    const note = fixture("C1_coordinator_dashboard.json").notesToCheck[0];
    this.createRecommendation(note.studentId, false);
    const noteItem = this.currentItems(note.studentId)[0];
    Object.assign(noteItem, { response: "PASS", passReason: "OTHER", passNote: note.passNote, respondedAt: s.students.get(note.studentId).row.lastResponseAt });
    // EMPLOYED 학생은 해당 상태의 근거인 패스 응답을 갖도록 초기화한다.
    this.createRecommendation(40, false);
    Object.assign(this.currentItems(40)[0], { response: "PASS", passReason: "ALREADY_EMPLOYED", respondedAt: s.students.get(40).row.lastResponseAt });
    for (const studentId of s.students.keys()) this.recalculate(studentId);
  }

  timestamp() { return new Date(this.now() + 9 * 3_600_000).toISOString().replace("Z", "+09:00"); }
  student(studentId) {
    const student = this.state.students.get(studentId);
    if (!student) throw new ApiFault(404, "STUDENT_NOT_FOUND", "수료생 정보를 찾을 수 없어요.");
    return student;
  }
  item(itemId) {
    const item = this.state.items.get(itemId);
    if (!item) throw new ApiFault(404, "NOT_FOUND", "추천 공고를 찾을 수 없어요.");
    return item;
  }
  application(applicationId) {
    const application = this.state.applications.get(applicationId);
    if (!application) throw new ApiFault(404, "APPLICATION_NOT_FOUND", "지원서를 찾을 수 없어요.");
    if (application.status === "GENERATING" && this.now() - application.createdAt >= this.generationMs) {
      application.status = "DRAFT";
      application.document = this.makeDocument(application.studentId);
    }
    return application;
  }
  help(helpId) {
    const help = this.state.helps.get(helpId);
    if (!help) throw new ApiFault(404, "NOT_FOUND", "도움 요청을 찾을 수 없어요.");
    return help;
  }
  ownItems(studentId) { return [...this.state.items.values()].filter((item) => item.studentId === studentId); }
  currentItems(studentId) { return this.state.recommendations.get(this.state.current.get(studentId))?.itemIds.map((itemId) => this.item(itemId)) ?? []; }
  addRecommendation(studentId, batch, current = true) {
    for (const item of batch.items) this.state.items.set(item.itemId, { ...item, studentId, recommendationId: batch.recommendationId,
      passReason: null, passNote: null, respondedAt: null, applicationId: null });
    this.state.recommendations.set(batch.recommendationId, { recommendationId: batch.recommendationId, studentId, createdAt: batch.createdAt, basis: batch.basis, itemIds: batch.items.map((item) => item.itemId) });
    if (current) this.state.current.set(studentId, batch.recommendationId);
  }
  createRecommendation(studentId, refresh) {
    this.student(studentId);
    const answered = new Set(this.ownItems(studentId).filter((item) => item.response !== "NONE").map((item) => item.job.jobPostingId));
    const candidates = refresh ? [...this.catalog.slice(3), ...this.catalog.slice(0, 3)] : this.catalog;
    const items = candidates.filter((item) => !answered.has(item.job.jobPostingId)).slice(0, 3).map((item, index) => ({ ...clone(item),
      itemId: this.state.nextItem++, rank: index + 1, response: "NONE",
      // 다른 수료생에게 학생 1의 경험을 근거로 제시하지 않는다.
      reasonSummary: studentId === 1 ? item.reasonSummary : ["모의 공고 목록입니다. 실제 AI 추천 결과가 아니에요."],
    }));
    const batch = { recommendationId: this.state.nextRecommendation++, createdAt: this.timestamp(), basis: "PROFILE_LATEST", items };
    this.addRecommendation(studentId, batch);
  }
  recommendations(studentId, refresh = false) {
    this.student(studentId);
    if (refresh || !this.state.current.has(studentId)) this.createRecommendation(studentId, refresh);
    const batch = this.state.recommendations.get(this.state.current.get(studentId));
    return { recommendationId: batch.recommendationId, createdAt: batch.createdAt, basis: batch.basis, items: this.currentItems(studentId).map((item) => ({
      itemId: item.itemId, rank: item.rank, response: this.jobResponse(item).response, job: item.job, reasonSummary: item.reasonSummary,
    })) };
  }
  jobResponse(item) {
    return this.ownItems(item.studentId).find((other) => other.job.jobPostingId === item.job.jobPostingId && other.response !== "NONE") ?? item;
  }
  detail(itemId) {
    const item = this.item(itemId);
    const template = this.fixtures["M2_recommendation_item_detail.json"].data;
    const full = item.studentId === 1 && item.job.jobPostingId === template.job.jobPostingId;
    return { itemId, rank: item.rank, response: this.jobResponse(item).response,
      job: full ? { ...clone(template.job), ...item.job } : { ...item.job, jobCategory: "", workAddress: item.job.location, education: "", salaryText: "", postedOn: "", description: "", homepageUrl: null, source: template.job.source, sourceLabel: template.job.sourceLabel },
      summary: full ? clone(template.summary) : item.reasonSummary,
      reasons: full ? clone(template.reasons) : item.reasonSummary.map((value) => ({ text: value, evidence: [] })),
    };
  }
  respond(itemId, body) {
    keys(body, ["response", "passReason", "passNote"], ["response"]);
    choice(body.response, ["APPLY", "PASS"]);
    if (body.response === "PASS") {
      requireValue(!!body.passReason, "패스 사유를 골라 주세요.", "PASS_REASON_REQUIRED");
      choice(body.passReason, passReasons);
      if (Object.hasOwn(body, "passNote")) requireValue(body.passNote === null || typeof body.passNote === "string");
    } else requireValue(!Object.hasOwn(body, "passReason") && !Object.hasOwn(body, "passNote"));
    const item = this.item(itemId);
    requireValue(this.jobResponse(item).response === "NONE", "이미 응답한 공고예요.", "ALREADY_RESPONDED", 409);
    Object.assign(item, { response: body.response, respondedAt: this.timestamp(), passReason: body.passReason ?? null, passNote: body.passNote ?? null });
    if (body.response === "APPLY") {
      item.applicationId = this.state.nextApplication++;
      this.state.applications.set(item.applicationId, { applicationId: item.applicationId, studentId: item.studentId, itemId,
        job: this.brief(item.job), document: null, status: "GENERATING", submittedAt: null, createdAt: this.now(), profileLinks: new Map() });
    }
    this.student(item.studentId).row.lastResponseAt = this.timestamp();
    this.recalculate(item.studentId);
    return { itemId, response: item.response, applicationId: item.applicationId };
  }
  brief(job) { return { jobPostingId: job.jobPostingId, companyName: job.companyName, title: job.title }; }
  progress(applicationId) {
    const app = this.application(applicationId);
    const elapsed = this.now() - app.createdAt;
    const template = clone(this.fixtures[app.status === "GENERATING" ? "M5_application_progress_generating.json" : "M5_application_progress_done.json"].data);
    if (app.status === "GENERATING") {
      const done = Math.min(2, Math.floor(elapsed / Math.max(1, this.generationMs / 3)));
      template.steps.forEach((step, index) => { step.state = index < done ? "DONE" : index === done ? "RUNNING" : "WAITING"; step.note = null; });
    } else template.steps.forEach((step) => { step.note = null; });
    return { ...template, applicationId, status: app.status };
  }
  applicationDetail(applicationId) {
    const app = this.application(applicationId);
    return { applicationId, status: app.status, job: app.job, document: app.document };
  }
  listApplications(studentId) {
    this.student(studentId);
    return { applications: [...this.state.applications.values()].filter((app) => app.studentId === studentId).map((entry) => {
      const app = this.application(entry.applicationId);
      return { applicationId: app.applicationId, companyName: app.job.companyName, title: app.job.title, status: app.status,
        submittedAt: app.submittedAt, userEditedCount: sentences(app.document).filter((s) => s.source === "USER").length };
    }), otherItems: this.currentItems(studentId).filter((item) => this.jobResponse(item).response !== "APPLY").map((item) => ({
      itemId: item.itemId, companyName: item.job.companyName, title: item.job.title, response: this.jobResponse(item).response, passReason: this.jobResponse(item).passReason,
    })) };
  }
  submit(applicationId) {
    const app = this.application(applicationId);
    this.expectStatus(app, "DRAFT");
    app.status = "SUBMITTED";
    app.submittedAt = this.timestamp();
    this.student(app.studentId).row.lastResponseAt = this.timestamp();
    this.recalculate(app.studentId);
    return { applicationId, status: app.status };
  }
  expectStatus(app, expected) { requireValue(app.status === expected, "현재 상태에서는 처리할 수 없어요.", "INVALID_STATUS", 409); }

  makeDocument(studentId) {
    const p = this.student(studentId).profile;
    const basic = Object.fromEntries(["name", "email", "githubUrl", "desiredJob"].map((key) => [key, p.basic[key]]));
    return { companyInfoStatus: "NOT_FOUND", companyInfoNote: "로컬 목업에서는 기업 정보를 수집하지 않아요.", excludedSentenceCount: 0, sections: [
      { section: "BASIC", source: "REGISTERED", fields: basic },
      { section: "EDUCATION", items: p.education.map((item) => ({ ...clone(item), itemId: `edu-${item.itemId}` })) },
      { section: "TRAINING", items: [{ itemId: `trn-${p.course.courseId}`, source: "REGISTERED", name: p.course.name, campus: p.course.campus, startedOn: p.course.startedOn, endedOn: p.course.endedOn }] },
      { section: "CAREER", items: p.careers.map((item) => ({ ...clone(item), itemId: `car-${item.itemId}` })) },
      { section: "MILITARY", value: p.basic.militaryStatus },
      { section: "CERTIFICATES", items: p.certificates.map((item) => ({ ...clone(item), itemId: `cert-${item.itemId}` })) },
      { section: "SKILLS", source: "REGISTERED", tags: clone(p.skills), summary: [] },
      { section: "PROJECTS", items: p.projects.map((item) => ({ itemId: `prj-${item.itemId}`, title: item.title, periodText: item.periodText, teamSize: item.teamSize, role: null, bullets: [] })) },
      { section: "PORTFOLIO", items: p.projects.filter((item) => item.portfolioFileName).map((item) => ({ itemId: `pf-${item.itemId}`, fileName: item.portfolioFileName, include: item.includeInApply })) },
      { section: "COVER_LETTER", sentences: p.coverLetter ? [{ sentenceId: "cl-1", text: p.coverLetter.content, source: "REGISTERED", evidence: [] }] : [] },
    ] };
  }

  patchDocument(applicationId, body) {
    object(body);
    const app = this.application(applicationId);
    this.expectStatus(app, "DRAFT");
    if (body.op === "UPDATE_SENTENCE") {
      keys(body, ["op", "sentenceId", "text"], ["op", "sentenceId", "text"]);
      text(body.sentenceId); text(body.text);
      const sentence = sentences(app.document).find((entry) => entry.sentenceId === body.sentenceId);
      requireValue(!!sentence, "문장을 찾을 수 없어요.", "NOT_FOUND", 404);
      Object.assign(sentence, { text: body.text, source: "USER", evidence: [] });
    } else {
      choice(body.op, ["ADD_ITEM", "UPDATE_ITEM", "DELETE_ITEM"]);
      const required = ["op", "section", "saveToProfile", ...(body.op === "ADD_ITEM" ? ["item"] : body.op === "UPDATE_ITEM" ? ["item", "itemId"] : ["itemId"])];
      keys(body, required, required);
      requireValue(typeof body.saveToProfile === "boolean");
      const section = app.document.sections.find((entry) => entry.section === body.section);
      requireValue(!!section && !["TRAINING", "COVER_LETTER"].includes(body.section), "이 섹션은 항목으로 수정할 수 없어요.");
      if (body.itemId !== undefined) text(body.itemId);
      if (!section.items) {
        requireValue(body.op === "UPDATE_ITEM", "단일 값 섹션은 수정만 할 수 있어요.");
        validateItem(body.section, body.item);
        if (body.section === "BASIC") { Object.assign(section.fields, body.item); section.source = "USER"; }
        if (body.section === "MILITARY") { requireValue(Object.hasOwn(body.item, "value")); section.value = body.item.value; }
        if (body.section === "SKILLS") { requireValue(Object.hasOwn(body.item, "tags")); section.tags = clone(body.item.tags); section.source = "USER"; }
      } else if (body.op === "ADD_ITEM") {
        const input = record(body.section, body.item);
        const itemId = `${prefixes[body.section]}-${this.state.nextRecord++}`;
        const item = this.documentItem(body.section, input, itemId);
        section.items.push(item);
        body = { ...body, itemId };
      } else {
        const index = section.items.findIndex((item) => item.itemId === body.itemId);
        requireValue(index >= 0, "항목을 찾을 수 없어요.", "NOT_FOUND", 404);
        if (body.op === "DELETE_ITEM") section.items.splice(index, 1);
        else {
          validateItem(body.section, body.item);
          if (body.section === "PORTFOLIO") Object.assign(section.items[index], body.item);
          else {
            const changes = { ...body.item }; delete changes.techStack;
            Object.assign(section.items[index], changes);
            if (body.section !== "PROJECTS") section.items[index].source = "USER";
          }
        }
      }
      if (body.saveToProfile) this.saveDocumentItem(app, body);
    }
    return { document: app.document };
  }
  documentItem(section, input, itemId) {
    if (section === "PROJECTS") return { itemId, title: input.title, periodText: input.periodText, teamSize: input.teamSize, role: input.role, bullets: [] };
    if (section === "PORTFOLIO") return { itemId, fileName: input.fileName, include: input.include ?? false };
    return { itemId, source: "USER", ...input };
  }

  refreshProfile(studentId) {
    const { profile: p, row } = this.student(studentId);
    const previous = p.profileUpdatedAt ? Date.parse(p.profileUpdatedAt) : 0;
    p.profileUpdatedAt = new Date(Math.max(this.now(), previous + 1) + 9 * 3_600_000).toISOString().replace("Z", "+09:00");
    const evidence = (refType, refId) => [{ refType, refId }];
    p.competencyProfile = {
      roles: p.projects.map((project) => `${project.title} 참여`),
      skills: [...new Set([...p.skills, ...p.projects.flatMap((project) => project.techStack)])].map((name) => ({ name,
        evidence: p.projects.filter((project) => project.techStack.includes(name)).map((project) => ({ refType: "PROJECT", refId: project.itemId })),
      })),
      achievements: p.projects.map((project) => ({ text: `${project.title} 등록 자료 보유`, evidence: evidence("PROJECT", project.itemId) })),
      certificates: p.certificates.map((entry) => entry.name), desiredJob: p.basic.desiredJob, preferredRegion: p.basic.preferredRegion,
    };
    row.name = p.basic.name;
  }
  profileMutation(studentId, method, itemId, section, item) {
    choice(section, ["BASIC", "EDUCATION", "CAREER", "CERTIFICATES", "MILITARY", "SKILLS", "PROJECTS", "PORTFOLIO"]);
    const p = this.student(studentId).profile;
    if (method === "DELETE") {
      const collection = p[collectionNames[section]];
      requireValue(Array.isArray(collection), "목록 섹션만 항목을 삭제할 수 있어요.");
      const index = collection.findIndex((entry) => entry.itemId === itemId);
      requireValue(index >= 0, "항목을 찾을 수 없어요.", "NOT_FOUND", 404);
      collection.splice(index, 1);
    } else {
      requireValue(!["TRAINING", "COVER_LETTER"].includes(section));
      if (!collectionNames[section]) {
        requireValue(method === "PATCH", "단일 값 섹션은 수정만 할 수 있어요.");
        validateItem(section, item);
        if (section === "BASIC") Object.assign(p.basic, item);
        else if (section === "MILITARY") { requireValue(Object.hasOwn(item, "value")); p.basic.militaryStatus = item.value; }
        else if (section === "SKILLS") { requireValue(Object.hasOwn(item, "tags")); p.skills = clone(item.tags); }
        else if (section === "PORTFOLIO") {
          const project = p.projects.find((entry) => entry.itemId === itemId);
          requireValue(!!project, "프로젝트를 찾을 수 없어요.", "NOT_FOUND", 404);
          if (item.fileName !== undefined) project.portfolioFileName = item.fileName;
          if (item.include !== undefined) project.includeInApply = item.include;
        } else throw new ApiFault(400, "INVALID_REQUEST", "지원하지 않는 섹션이에요.");
      } else if (method === "POST") {
        const fields = record(section, item);
        const entry = section === "PROJECTS" ? { itemId: this.state.nextRecord++, title: fields.title, periodText: fields.periodText,
          teamSize: fields.teamSize, techStack: fields.techStack ?? [], portfolioFileName: null, includeInApply: false }
          : { itemId: this.state.nextRecord++, source: "USER", ...fields };
        p[collectionNames[section]].push(entry);
      } else {
        const entry = p[collectionNames[section]].find((entry) => entry.itemId === itemId);
        requireValue(!!entry, "항목을 찾을 수 없어요.", "NOT_FOUND", 404);
        validateItem(section, item);
        const changes = { ...item }; delete changes.role;
        Object.assign(entry, changes);
        if (section !== "PROJECTS") entry.source = "USER";
      }
    }
    this.refreshProfile(studentId);
    return p;
  }
  saveDocumentItem(app, body) {
    const { studentId } = app;
    if (["BASIC", "MILITARY", "SKILLS"].includes(body.section)) {
      this.profileMutation(studentId, "PATCH", 1, body.section, body.item); return;
    }
    const key = `${body.section}:${body.itemId}`;
    const numericId = app.profileLinks.get(key) ?? Number(body.itemId.split("-").at(-1));
    if (body.op === "ADD_ITEM") {
      requireValue(body.section !== "PORTFOLIO", "포트폴리오는 등록된 프로젝트에서 수정해 주세요.");
      this.profileMutation(studentId, "POST", null, body.section, body.item);
      const collection = this.student(studentId).profile[collectionNames[body.section]];
      app.profileLinks.set(key, collection.at(-1).itemId);
    } else {
      requireValue(body.section !== "PORTFOLIO" || body.op !== "DELETE_ITEM", "포트폴리오는 포함 여부를 수정해 주세요.");
      this.profileMutation(studentId, body.op === "DELETE_ITEM" ? "DELETE" : "PATCH", numericId, body.section, body.item);
    }
  }

  createHelp(body) {
    keys(body, ["studentId", "recommendationItemId"], ["studentId", "recommendationItemId"]);
    const studentId = id(body.studentId); const item = this.item(id(body.recommendationItemId));
    requireValue(typeof body.studentId === "number" && typeof body.recommendationItemId === "number" && item.studentId === studentId);
    const student = this.student(studentId);
    requireValue(this.jobResponse(item).response === "NONE", "이미 응답한 공고예요.", "ALREADY_RESPONDED", 409);
    const helpRequestId = this.state.nextHelp++;
    this.state.helps.set(helpRequestId, { helpRequestId, studentId, recommendationItemId: item.itemId, status: "OPEN", createdAt: this.timestamp(),
      studentName: student.row.name, courseLabel: student.row.courseLabel, jobTitle: item.job.title, contactMethod: null, memo: null });
    Object.assign(item, { response: "HELP", respondedAt: this.timestamp() });
    student.row.lastResponseAt = this.timestamp(); this.recalculate(studentId);
    return { helpRequestId, status: "OPEN" };
  }
  updateHelp(helpRequestId, body, coordinator) {
    const help = this.help(helpRequestId);
    if (coordinator) {
      keys(body, ["status"], ["status"]); choice(body.status, ["OPEN", "CONTACTED"]); help.status = body.status;
    } else {
      keys(body, ["contactMethod", "memo"], ["contactMethod", "memo"]);
      choice(body.contactMethod, [null, "SMS", "PHONE", "EMAIL"]);
      requireValue(body.memo === null || typeof body.memo === "string");
      Object.assign(help, body);
    }
    this.recalculate(help.studentId);
    return { helpRequestId, status: help.status, ...(coordinator ? {} : { contactMethod: help.contactMethod, memo: help.memo }) };
  }

  recalculate(studentId) {
    const student = this.student(studentId); const { row } = student;
    const items = this.ownItems(studentId);
    const latestPass = items.filter((item) => item.response === "PASS").sort((a, b) => String(b.respondedAt).localeCompare(String(a.respondedAt)))[0];
    row.hasNote = items.some((item) => item.passNote) || student.logs.length > 0;
    let reason;
    if (items.some((item) => item.passReason === "ALREADY_EMPLOYED")) { row.status = "EMPLOYED"; reason = "패스 사유 ALREADY_EMPLOYED"; }
    else if ([...this.state.helps.values()].some((help) => help.studentId === studentId && help.status === "OPEN")) { row.status = "NEED_HELP"; reason = "열린 도움 요청 있음"; }
    else if (row.lastResponseAt === null || this.now() - Date.parse(row.lastResponseAt) >= 7 * DAY) { row.status = "CHECK_REQUIRED"; reason = "7일 이상 응답 없음"; }
    else { row.status = "SEEKING"; reason = "최근 응답 있음"; }
    row.subStatus = row.status === "SEEKING" ? [...this.state.applications.values()].some((app) => app.studentId === studentId && ["SUBMITTED", "APPROVED", "DELIVERED"].includes(app.status))
      ? "APPLYING" : latestPass?.passReason === "OTHER" ? "OTHER" : "WAITING" : null;
    student.reason = reason;
  }
  coordinatorStudents(query) {
    const { status, subStatus, q, page = "1", size = "20" } = Object.fromEntries(query);
    if (status) choice(status, ["EMPLOYED", "SEEKING", "NEED_HELP", "CHECK_REQUIRED"]);
    if (subStatus) choice(subStatus, ["APPLYING", "WAITING", "OTHER"]);
    const pageNumber = id(page); const pageSize = id(size); requireValue(pageSize <= 100);
    for (const studentId of this.state.students.keys()) this.recalculate(studentId);
    const rows = [...this.state.students.values()].map((student) => student.row).filter((row) => (!status || row.status === status) && (!subStatus || row.subStatus === subStatus) && (!q || row.name.includes(q)));
    return { items: rows.slice((pageNumber - 1) * pageSize, pageNumber * pageSize), total: rows.length, page: pageNumber, size: pageSize };
  }
  coordinatorStudent(studentId) {
    const { row, profile: p, logs } = this.student(studentId);
    this.recalculate(studentId);
    const history = [...this.state.recommendations.values()].filter((entry) => entry.studentId === studentId).map((entry) => ({ recommendationId: entry.recommendationId, createdAt: entry.createdAt,
      items: entry.itemIds.map((itemId) => { const item = this.item(itemId); return { itemId, companyName: item.job.companyName, title: item.job.title,
        response: item.response, passReason: item.passReason, passNote: item.passNote, respondedAt: item.respondedAt, applicationId: item.applicationId,
        applicationStatus: item.applicationId === null ? null : this.application(item.applicationId).status }; }),
    }));
    return { student: { ...row, email: p.basic.email, desiredJob: p.basic.desiredJob, preferredRegion: p.basic.preferredRegion, reason: this.student(studentId).reason },
      competencyProfile: p.competencyProfile, registered: { education: p.education.length, certificates: p.certificates.length, projects: p.projects.length, hasCoverLetter: p.coverLetter !== null }, history, contactLogs: logs };
  }
  addContact(studentId, body) {
    keys(body, ["contactType", "note"], ["contactType", "note"]);
    choice(body.contactType, ["PHONE", "MEETING", "MESSAGE"]); text(body.note);
    const entry = { contactLogId: this.state.nextContact++, contactType: body.contactType, note: body.note, createdAt: this.timestamp() };
    this.student(studentId).logs.unshift(entry); this.recalculate(studentId); return entry;
  }
  dashboard() {
    for (const studentId of this.state.students.keys()) this.recalculate(studentId);
    const students = [...this.state.students.values()].map((student) => student.row);
    const count = (status) => students.filter((row) => row.status === status).length;
    return { asOf: this.timestamp(), totalStudents: students.length,
      helpRequests: [...this.state.helps.values()].filter((help) => help.status === "OPEN").map((help) => ({ helpRequestId: help.helpRequestId, studentId: help.studentId,
        studentName: this.student(help.studentId).row.name, courseLabel: help.courseLabel, createdAt: help.createdAt, jobTitle: help.jobTitle, contactMethod: help.contactMethod, memo: help.memo })),
      statusSummary: { EMPLOYED: count("EMPLOYED"), SEEKING: { total: count("SEEKING"), ...Object.fromEntries(["APPLYING", "WAITING", "OTHER"].map((sub) => [sub, students.filter((row) => row.subStatus === sub).length])) },
        NEED_HELP: count("NEED_HELP"), CHECK_REQUIRED: count("CHECK_REQUIRED") },
      reviewQueue: [...this.state.applications.values()].filter((app) => app.status === "SUBMITTED").map((app) => ({ applicationId: app.applicationId,
        studentName: this.student(app.studentId).row.name, jobTitle: app.job.title, submittedAt: app.submittedAt })),
      callList: students.filter((row) => row.status === "CHECK_REQUIRED").sort((a, b) => String(a.lastResponseAt).localeCompare(String(b.lastResponseAt))).map((row) => ({ studentId: row.studentId,
        studentName: row.name, lastResponseAt: row.lastResponseAt, daysSinceResponse: row.lastResponseAt ? Math.floor((this.now() - Date.parse(row.lastResponseAt)) / DAY) : 0 })),
      notesToCheck: [...this.state.items.values()].filter((item) => item.passNote).map((item) => ({ studentId: item.studentId, studentName: this.student(item.studentId).row.name, passNote: item.passNote })),
    };
  }
  coordinatorApplication(applicationId) {
    const app = this.application(applicationId); const all = sentences(app.document);
    return { ...this.applicationDetail(applicationId), student: { studentId: app.studentId, name: this.student(app.studentId).row.name }, verification: {
      aiSentenceCount: all.filter((entry) => entry.source === "AI").length,
      aiSentenceWithEvidence: all.filter((entry) => entry.source === "AI" && entry.evidence.length > 0).length,
      excludedSentenceCount: app.document?.excludedSentenceCount ?? 0, userEditedCount: all.filter((entry) => entry.source === "USER").length,
      companyInfoStatus: app.document?.companyInfoStatus ?? "NOT_FOUND",
    } };
  }
  review(applicationId, action, body) {
    const app = this.application(applicationId);
    this.expectStatus(app, action === "deliver" ? "APPROVED" : "SUBMITTED");
    if (action === "reject") { keys(body, ["note"], ["note"]); text(body.note); this.addContact(app.studentId, { contactType: "MESSAGE", note: body.note }); }
    else keys(body, []);
    app.status = { approve: "APPROVED", reject: "REJECTED", deliver: "DELIVERED" }[action];
    this.recalculate(app.studentId); return { applicationId, status: app.status };
  }

  // 테스트/HTTP가 같은 계약을 사용한다. 실패한 변경은 메모리 전체를 원복한다.
  dispatch(method, url, body = {}) {
    const before = method === "GET" ? null : clone(this.state);
    try { return clone(this.route(method, url, body)); }
    catch (error) { if (before) this.state = before; throw error; }
  }
  route(method, url, body) {
    const p = url.pathname.replace(/\/$/, "").split("/").slice(1);
    if (p[0] !== "api") throw new ApiFault(404, "NOT_FOUND", "API 경로를 찾을 수 없어요.");
    if (p[1] === "students") {
      const studentId = id(p[2]); this.student(studentId);
      if (p.length === 5 && p[3] === "recommendations") {
        if (p[4] === "current" && method === "GET") return this.recommendations(studentId);
        if (p[4] === "refresh" && method === "POST") { keys(body, []); return this.recommendations(studentId, true); }
      }
      if (p.length === 4 && p[3] === "applications" && method === "GET") return this.listApplications(studentId);
      if (p.length === 4 && p[3] === "profile" && method === "GET") return this.student(studentId).profile;
      if (p[3] === "profile" && p[4] === "items" && [5, 6].includes(p.length)) {
        if ((p.length === 5 && method === "POST") || (p.length === 6 && method === "PATCH")) {
          keys(body, ["section", "item"], ["section", "item"]); return this.profileMutation(studentId, method, p.length === 6 ? id(p[5]) : null, body.section, body.item);
        }
        if (p.length === 6 && method === "DELETE") return this.profileMutation(studentId, method, id(p[5]), url.searchParams.get("section"));
      }
      if (p.length === 4 && p[3] === "cover-letter" && method === "PUT") {
        keys(body, ["content"], ["content"]); requireValue(typeof body.content === "string");
        this.student(studentId).profile.coverLetter = { content: body.content, updatedAt: this.timestamp() }; this.refreshProfile(studentId); return this.student(studentId).profile;
      }
    }
    if (p[1] === "recommendation-items" && [3, 4].includes(p.length)) {
      const itemId = id(p[2]);
      if (p.length === 3 && method === "GET") return this.detail(itemId);
      if (p[3] === "response" && method === "POST") return this.respond(itemId, body);
    }
    if (p[1] === "applications" && [3, 4].includes(p.length)) {
      const applicationId = id(p[2]);
      if (p.length === 3 && method === "GET") return this.applicationDetail(applicationId);
      if (p[3] === "progress" && method === "GET") return this.progress(applicationId);
      if (p[3] === "document" && method === "PATCH") return this.patchDocument(applicationId, body);
      if (p[3] === "submit" && method === "POST") { keys(body, []); return this.submit(applicationId); }
    }
    if (p[1] === "help-requests") {
      if (p.length === 2 && method === "POST") return this.createHelp(body);
      if (p.length === 3 && method === "PATCH") return this.updateHelp(id(p[2]), body, false);
    }
    if (p[1] === "coordinator") {
      if (p.length === 3 && p[2] === "dashboard" && method === "GET") return this.dashboard();
      if (p[2] === "students") {
        if (p.length === 3 && method === "GET") return this.coordinatorStudents(url.searchParams);
        if (p.length === 4 && method === "GET") return this.coordinatorStudent(id(p[3]));
        if (p.length === 5 && p[4] === "contact-logs" && method === "POST") return this.addContact(id(p[3]), body);
      }
      if (p.length === 4 && p[2] === "help-requests" && method === "PATCH") return this.updateHelp(id(p[3]), body, true);
      if (p[2] === "applications") {
        if (p.length === 4 && method === "GET") return this.coordinatorApplication(id(p[3]));
        if (p.length === 5 && ["approve", "reject", "deliver"].includes(p[4]) && method === "POST") return this.review(id(p[3]), p[4], body);
      }
    }
    throw new ApiFault(404, "NOT_FOUND", "API 경로를 찾을 수 없어요.");
  }
}
