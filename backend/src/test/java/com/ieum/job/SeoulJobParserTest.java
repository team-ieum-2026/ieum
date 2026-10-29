package com.ieum.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SeoulJobParserTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);
    private static final List<String> CAPITAL = List.of("서울", "경기", "인천");

    private final ObjectMapper json = new ObjectMapper();

    private ObjectNode row() {
        ObjectNode row = json.createObjectNode();
        row.put("COMPANY", "모의소프트");
        row.put("TITLE", "백엔드 개발자 채용");
        row.put("JOBS_CD", "133301");
        row.put("JOBS_NM", "웹 개발자(133301)");
        row.put("REGION", "서울 구로구");
        row.put("REG_DT", "26-10-20");
        row.put("CLOSE_DT", "26-11-10");
        row.put("JOB_CONT", "Spring Boot API 개발");
        return row;
    }

    @Test
    void titleHtmlEntitiesAreUnescaped() {
        ObjectNode row = row();
        row.put("TITLE", "R&amp;D 소프트웨어 개발자 &lt;경력&gt;");

        JobPosting p = SeoulJobParser.parse(row, TODAY);

        assertEquals("R&D 소프트웨어 개발자 <경력>", p.getTitle());
    }

    @Test
    void externalIdUsesRawTitleSoExistingRowsKeepTheirId() {
        ObjectNode row = row();
        row.put("TITLE", "R&amp;D 개발자");

        JobPosting p = SeoulJobParser.parse(row, TODAY);

        assertEquals(SeoulJobParser.sha1Hex("모의소프트|R&amp;D 개발자|26-10-20|"), p.getExternalId());
    }

    @Test
    void phoneNumbersAndEmailsInDescriptionAreMasked() {
        ObjectNode row = row();
        row.put("JOB_CONT", "문의 02-2618-0000 / 070)4322-0000 / 010 1234 5678 / 1588-0000 / hr@example.com");

        JobPosting p = SeoulJobParser.parse(row, TODAY);

        String m = SeoulJobParser.CONTACT_MASK;
        assertEquals("[직무] 웹 개발자\n[내용] 문의 " + String.join(" / ", m, m, m, m, m), p.getDescription());
    }

    @Test
    void datesAndSalariesAreNotMaskedAsPhoneNumbers() {
        String text = "근무 2026-11-02부터 · 월 3,430,000원 · 09:00~18:00 · 인원 0명";

        assertEquals(text, SeoulJobParser.maskContacts(text));
    }

    @Test
    void contactPersonNamesAreMasked() {
        String n = SeoulJobParser.NAME_MASK;
        String c = SeoulJobParser.CONTACT_MASK;
        // 실제 공고 647번 형태 (이름은 모의)
        assertEquals("■ 지원문의 : 파주시일자리센터 " + n + " 상담사(☏" + c + ")",
                SeoulJobParser.maskContacts("■ 지원문의 : 파주시일자리센터 홍길동 상담사(☏031-940-0000)"));
        assertEquals("담당자: " + n + " (" + c + ")", SeoulJobParser.maskContacts("담당자: 김철수 (02-123-4567)"));
        assertEquals("문의 " + n + " 매니저, 메일 " + c,
                SeoulJobParser.maskContacts("문의 박영희 매니저, 메일 hr@example.com"));
        assertEquals("채용 담당 " + n, SeoulJobParser.maskContacts("채용 담당 이민수"));
    }

    @Test
    void jobTitlesWithHonorificWordsAreNotMasked() {
        String[] texts = {
            "하위 인사담당자에게 꼭 메일로 면접참석 여부를 알려주시고,",
            "- 고객사 담당자 및 개발·인프라 담당자와 협업",
            "동료 및 현업 담당자와 원활하게 소통하고 협업하실 수 있는 분",
            "든든한 운영 담당자를 모십니다.",
            "환경모니터링장비서비스를 위한 컴퓨터 담당자 1명 모집",
            "모집인원: 담당자급 1명 또는 팀장급 1명",
            "Jira 등록 및 담당자 배정·처리상태 관리",
            "시스템 개발 및 유지보수 (.NET 개발자/ 개발 매니저 / ERP전문가)"
        };
        for (String text : texts) {
            assertEquals(text, SeoulJobParser.maskContacts(text), text);
        }
    }

    @Test
    void longRegionNamesAreShortened() {
        assertEquals("경기 화성시 동탄구", SeoulJobParser.normalizeRegion("경기도 화성시 동탄구"));
        assertEquals("서울 중구", SeoulJobParser.normalizeRegion("서울특별시 중구"));
        assertEquals("인천", SeoulJobParser.normalizeRegion("인천광역시"));
        assertEquals("경기 군포시", SeoulJobParser.normalizeRegion("경기 군포시"));
    }

    @Test
    void onlyCapitalAreaIsAllowed() {
        assertTrue(SeoulJobParser.isAllowedRegion("서울 구로구", CAPITAL));
        assertTrue(SeoulJobParser.isAllowedRegion("경기 화성시", CAPITAL));
        assertTrue(SeoulJobParser.isAllowedRegion("인천 연수구", CAPITAL));
        assertFalse(SeoulJobParser.isAllowedRegion("부산 해운대구", CAPITAL));
        assertFalse(SeoulJobParser.isAllowedRegion(null, CAPITAL));
        assertTrue(SeoulJobParser.isAllowedRegion("부산 해운대구", List.of()));
    }

    @Test
    void parsedLocationFromLongRegionPassesFilter() {
        ObjectNode row = row();
        row.put("REGION", "경기도 화성시 효행구");

        JobPosting p = SeoulJobParser.parse(row, TODAY);

        assertEquals("경기 화성시 효행구", p.getLocation());
        assertTrue(SeoulJobParser.isAllowedRegion(p.getLocation(), CAPITAL));
    }
}
