// 주인: BE-A
package com.ieum.job;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** recMntList 한 건 → job_posting. 규칙은 docs/JOB_API.md "필드 매핑" · "매칭용 본문" · "IT 직종 필터". */
public final class SeoulJobParser {

    public static final String SOURCE = "SEOUL_API";

    /** it-job-codes 가 비어 있을 때만 쓰는 JOBS_NM 단어 필터 */
    static final List<String> IT_KEYWORDS = List.of(
            "소프트웨어", "웹", "앱", "프로그래머", "개발자", "데이터",
            "정보시스템", "네트워크", "보안", "인공지능", "IT", "시스템 운영");

    private static final Pattern YYMMDD = Pattern.compile("(\\d{2})-(\\d{2})-(\\d{2})");
    private static final Pattern TRAILING_CODE = Pattern.compile("\\s*\\(\\d+\\)\\s*$");
    private static final Pattern LEADING_ZIP = Pattern.compile("^\\s*\\(\\d+\\)\\s*");

    private SeoulJobParser() {
    }

    public static boolean isItJob(JsonNode row, List<String> itJobCodes) {
        if (!itJobCodes.isEmpty()) {
            return itJobCodes.contains(text(row, "JOBS_CD"));
        }
        String jobsNm = text(row, "JOBS_NM");
        return IT_KEYWORDS.stream().anyMatch(jobsNm::contains);
    }

    public static JobPosting parse(JsonNode row, LocalDate today) {
        JobPosting p = new JobPosting();
        String company = text(row, "COMPANY");
        String title = text(row, "TITLE");
        String regDt = text(row, "REG_DT");
        String closeDt = text(row, "CLOSE_DT");

        p.setSource(SOURCE);
        p.setExternalId(sha1Hex(company + "|" + title + "|" + regDt + "|" + text(row, "STRTNM_CD")));
        p.setCompanyName(cut(company, 150));
        p.setTitle(cut(title, 300));
        p.setJobCode(cut(text(row, "JOBS_CD"), 10));
        p.setJobCategory(cut(TRAILING_CODE.matcher(text(row, "JOBS_NM")).replaceAll(""), 100));
        p.setLocation(cut(text(row, "REGION"), 100));
        p.setWorkAddress(cut(LEADING_ZIP.matcher(text(row, "WORK_REGION")).replaceAll(""), 300));
        p.setEmploymentType(cut(employmentType(text(row, "EMP_TP_NM")), 50));
        p.setCareer(cut(text(row, "CAREER"), 50));
        p.setEducation(cut(text(row, "MIN_EDUBG"), 50));
        p.setSalaryText(cut(text(row, "SAL_TP_NM").replaceAll(",\\s*$", ""), 100));
        p.setPostedOn(lastDate(regDt));
        p.setDeadlineOn(lastDate(closeDt));
        p.setUntilFilled(closeDt.startsWith("채용시까지"));
        p.setDescription(description(row, p.getJobCategory()));
        p.setContentHash(sha256Hex(p.getDescription()));
        p.setActive(p.getDeadlineOn() == null || !p.getDeadlineOn().isBefore(today));
        p.setRaw(row.toString());
        return p;
    }

    /** "기간의 정함이 없는" → 정규직, "있는" → 계약직, 시간제면 " · 시간제". 둘 다 아니면 첫 '/' 앞부분 */
    static String employmentType(String empTpNm) {
        if (empTpNm.isEmpty()) {
            return null;
        }
        String base;
        if (empTpNm.contains("기간의 정함이 없는")) {
            base = "정규직";
        } else if (empTpNm.contains("기간의 정함이 있는")) {
            base = "계약직";
        } else {
            base = empTpNm.split("/")[0].trim();
        }
        return empTpNm.contains("시간(선택)제") ? base + " · 시간제" : base;
    }

    static String description(JsonNode row, String jobCategory) {
        StringBuilder sb = new StringBuilder();
        line(sb, "직무", jobCategory);
        line(sb, "내용", text(row, "JOB_CONT"));
        line(sb, "필요 역량", text(row, "COMP_ABL"));
        line(sb, "자격증", text(row, "CERTIFICATE"));
        line(sb, "전공", text(row, "MAJOR"));
        line(sb, "우대 조건", text(row, "PF_COND"));
        line(sb, "근무 시간", text(row, "WORKDAY_WORKHR_CONT"));
        line(sb, "전형", text(row, "SEL_MTHD"));
        return sb.toString().trim();
    }

    /** 문자열 안의 마지막 YY-MM-DD 를 20YY-MM-DD 로. 없으면 null */
    static LocalDate lastDate(String s) {
        Matcher m = YYMMDD.matcher(s);
        LocalDate found = null;
        while (m.find()) {
            try {
                found = LocalDate.of(2000 + Integer.parseInt(m.group(1)),
                        Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
            } catch (RuntimeException ignored) {
                // 잘못된 날짜는 건너뜀
            }
        }
        return found;
    }

    private static void line(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append('[').append(label).append("] ").append(value.trim()).append('\n');
        }
    }

    private static String text(JsonNode row, String field) {
        JsonNode v = row.get(field);
        return v == null || v.isNull() ? "" : v.asText().trim();
    }

    private static String cut(String s, int max) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    static String sha1Hex(String s) {
        return hash("SHA-1", s);
    }

    static String sha256Hex(String s) {
        return hash("SHA-256", s);
    }

    private static String hash(String algorithm, String s) {
        try {
            byte[] digest = MessageDigest.getInstance(algorithm).digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
