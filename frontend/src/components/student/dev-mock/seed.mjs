import { readFileSync } from "node:fs";

// 저장소의 개발용 INSERT 문만 해석한다. SQL 실행이나 DB 연결은 하지 않는다.
export function readSeed(now) {
  const sql = readFileSync(new URL("../../../../../db/seed.sql", import.meta.url), "utf8");
  let pos = 0;
  const skip = () => { while (/\s/.test(sql[pos] ?? "") && pos < sql.length) pos++; };
  const expect = (token) => { skip(); if (!sql.startsWith(token, pos)) throw new Error(`시드 문법 확인 필요: ${pos}`); pos += token.length; };
  const stamp = (ms) => new Date(ms + 9 * 3600000).toISOString().replace("Z", "+09:00");
  const midnight = Date.parse(stamp(now).slice(0, 10) + "T00:00:00+09:00");
  function value() {
    skip();
    if (sql[pos] === "'") {
      pos++; let result = "";
      while (pos < sql.length) {
        const c = sql[pos++];
        if (c === "'") { if (sql[pos] === "'") { result += "'"; pos++; } else return result; }
        else if (c === "\\") {
          const escaped = sql[pos++];
          result += ({ n: "\n", r: "\r", t: "\t", "0": "\0" })[escaped] ?? escaped;
        } else result += c;
      }
      throw new Error("시드 문자열이 닫히지 않았어요.");
    }
    const word = /^[A-Z_]+|^-?\d+/.exec(sql.slice(pos))?.[0];
    if (!word) throw new Error(`지원하지 않는 시드 값: ${pos}`);
    pos += word.length;
    if (word === "NULL") return null;
    if (/^-?\d+$/.test(word)) return Number(word);
    expect("(");
    if (word === "CURDATE") { expect(")"); return midnight; }
    if (word === "JSON_ARRAY") {
      const entries = []; skip();
      if (sql[pos] !== ")") { entries.push(value()); skip(); while (sql[pos] === ",") { pos++; entries.push(value()); skip(); } }
      expect(")"); return JSON.stringify(entries);
    }
    if (word === "DATE_ADD" || word === "DATE_SUB") {
      const base = value(); expect(","); expect("INTERVAL"); const amount = value(); skip();
      const unit = /^(DAY|HOUR)/.exec(sql.slice(pos))?.[0];
      if (!unit) throw new Error("지원하지 않는 시드 날짜 단위");
      pos += unit.length; expect(")");
      return Number(base) + (word === "DATE_ADD" ? 1 : -1) * amount * (unit === "DAY" ? 86400000 : 3600000);
    }
    throw new Error(`지원하지 않는 시드 함수: ${word}`);
  }
  const tables = {};
  const insert = /INSERT INTO (\w+)\s*\(([^)]+)\)\s*VALUES/g;
  for (const match of sql.matchAll(insert)) {
    pos = match.index + match[0].length;
    const columns = match[2].split(",").map((s) => s.trim());
    const rows = tables[match[1]] ??= [];
    do {
      expect("("); const row = {};
      for (let index = 0; index < columns.length; index++) {
        if (index) expect(",");
        let entry = value();
        if (typeof entry === "number" && columns[index].endsWith("_at")) entry = stamp(entry);
        if (typeof entry === "number" && columns[index].endsWith("_on")) entry = stamp(entry).slice(0, 10);
        row[columns[index]] = entry;
      }
      expect(")"); rows.push(row); skip();
      if (sql[pos] !== ",") break;
      pos++;
    } while (true);
    expect(";");
  }
  return tables;
}

export function loadSeed(model) {
  const db = readSeed(model.now());
  const s = model.state;
  for (const key of ["students", "items", "recommendations", "current", "applications", "helps"]) s[key].clear();
  const json = (v, fallback = null) => v == null ? fallback : JSON.parse(v);
  const time = model.timestamp();
  const byStudent = (table, id) => (db[table] ?? []).filter((row) => row.student_id === id);
  const fields = (row, names) => Object.fromEntries(names.map((name) => [name.replace(/_([a-z])/g, (_, c) => c.toUpperCase()), row[name] ?? null]));
  const records = (table, id, names) => byStudent(table, id).map((row) => ({ itemId: row.id ?? db[table].indexOf(row) + 1, source: row.source ?? "REGISTERED", ...fields(row, names) }));
  for (const row of db.student) {
    const course = db.course.find((c) => c.id === row.course_id);
    const cover = byStudent("student_cover_letter", row.id)[0];
    const profile = { studentId: row.id, profileUpdatedAt: null,
      basic: fields(row, ["name", "email", "github_url", "desired_job", "preferred_region", "military_status", "completed_on"]),
      course: { courseId: course.id, ...fields(course, ["name", "cohort", "campus", "started_on", "ended_on"]) },
      education: records("student_education", row.id, ["school_name", "major", "degree", "status", "graduated_on"]),
      careers: records("student_career", row.id, ["company_name", "position", "started_on", "ended_on", "description"]),
      certificates: records("student_certificate", row.id, ["name", "issuer", "acquired_on"]),
      skills: json(row.skills, []), projects: byStudent("student_project", row.id).map((p) => ({ itemId: p.id,
        ...fields(p, ["title", "period_text", "team_size"]), techStack: json(p.tech_stack, []), portfolioFileName: p.portfolio_file_url, includeInApply: !!p.include_in_apply })),
      coverLetter: cover ? { content: cover.content, updatedAt: time } : null,
      competencyProfile: json(byStudent("competency_profile", row.id)[0]?.profile),
    };
    const status = byStudent("student_status", row.id)[0];
    s.students.set(row.id, { profile, row: { studentId: row.id, name: row.name,
      courseLabel: `${course.name.replace("청년취업사관학교 ", "").replace(" 과정", "")} ${course.cohort}`,
      completedOn: row.completed_on, status: status.status, subStatus: status.sub_status, lastResponseAt: row.last_response_at, hasNote: false },
      reason: status.reason, logs: byStudent("contact_log", row.id).map((log) => ({ contactLogId: db.contact_log.indexOf(log) + 1, ...fields(log, ["contact_type", "note", "created_at"]) })).reverse() });
  }
  model.seedJobs = new Map(db.job_posting.map((job) => [job.id, job]));
  model.jobCard = (job) => ({ jobPostingId: job.id, ...fields(job, ["company_name", "title", "location", "employment_type", "career", "deadline_on"]),
    untilFilled: !!job.until_filled, dDay: job.deadline_on === null ? null : Math.ceil((Date.parse(job.deadline_on + "T00:00:00+09:00") - Date.parse(model.timestamp().slice(0, 10) + "T00:00:00+09:00")) / 86400000) });
  model.catalog = db.job_posting.filter((job) => job.is_active !== 0).map((job) => ({ job: model.jobCard(job), reasonSummary: ["개발용 시드 공고입니다."] }));
  for (const rec of db.recommendation) {
    const entries = db.recommendation_item.filter((item) => item.recommendation_id === rec.id).sort((a, b) => a.rank_no - b.rank_no);
    model.addRecommendation(rec.student_id, { recommendationId: rec.id, createdAt: rec.created_at, basis: "PROFILE_LATEST", items: entries.map((item) => ({
      itemId: item.id, rank: item.rank_no, response: item.response, job: model.jobCard(model.seedJobs.get(item.job_posting_id)), reasonSummary: json(item.reasons, []).map((r) => r.text),
    })) });
    for (const entry of entries) Object.assign(s.items.get(entry.id), { passReason: entry.pass_reason, passNote: entry.pass_note, respondedAt: entry.responded_at, reasons: json(entry.reasons, []) });
  }
  for (const app of db.application) {
    s.applications.set(app.id, { applicationId: app.id, studentId: app.student_id, itemId: app.recommendation_item_id, status: app.status,
      job: model.brief(model.jobCard(model.seedJobs.get(app.job_posting_id))), document: json(db.application_document.find((d) => d.application_id === app.id)?.document),
      submittedAt: app.submitted_at, createdAt: model.now(), profileLinks: new Map() });
    s.items.get(app.recommendation_item_id).applicationId = app.id;
  }
  for (const help of db.help_request) {
    const student = s.students.get(help.student_id);
    const item = s.items.get(help.recommendation_item_id);
    s.helps.set(help.id, { helpRequestId: help.id, studentId: help.student_id, recommendationItemId: help.recommendation_item_id,
      studentName: student.row.name, courseLabel: student.row.courseLabel, jobTitle: item.job.title, status: help.status ?? "OPEN", ...fields(help, ["contact_method", "memo", "created_at"]) });
  }
  s.nextItem = Math.max(...s.items.keys()) + 1;
  s.nextRecommendation = Math.max(...s.recommendations.keys()) + 1;
  s.nextApplication = Math.max(...s.applications.keys()) + 1;
  s.nextHelp = Math.max(...s.helps.keys()) + 1;
  s.nextContact = (db.contact_log?.length ?? 0) + 1;
  s.nextRecord = Math.max(0, ...["student_education", "student_career", "student_certificate", "student_project"].flatMap((table) => (db[table] ?? []).map((r, i) => r.id ?? i + 1))) + 1;
  for (const id of s.students.keys()) model.recalculate(id);
}
