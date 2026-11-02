// 주인: BE-B
package com.ieum.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** DB 연결 없이 실제 XML의 필터 분기와 학생별 요청 집계 계약을 확인한다. */
class CoordinatorStudentMapperTest {
    private static final String NAMESPACE = "com.ieum.coordinator.CoordinatorStudentMapper.";
    private static final String RESOURCE = "mapper/coordinator/CoordinatorStudentMapper.xml";
    private static Configuration configuration;

    @BeforeAll
    static void parseActualMapperXml() throws Exception {
        configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        try (InputStream input = Resources.getResourceAsStream(RESOURCE)) {
            new XMLMapperBuilder(input, configuration, RESOURCE, configuration.getSqlFragments()).parse();
        }
    }

    @Test
    void legacyHelpFilterUsesOpenRequestsIndependentlyOfRepresentativeStatus() {
        for (String statement : new String[]{"countStudents", "findStudents"}) {
            BoundSql bound = bound(statement, "NEED_HELP");
            String sql = sql(bound);
            assertTrue(sql.contains("coalesce(open_help.open_help_request_count, 0) > 0"));
            assertTrue(bound.getParameterMappings().stream().noneMatch(value -> value.getProperty().equals("status")),
                    "이전 도움 필터는 대표 상태를 NEED_HELP와 비교하지 않는다");
            assertTrue(sql.contains("where status = 'open' group by student_id"));
        }
    }

    @Test
    void restingFilterAndDetailKeepOneRowPerStudentWithAnIndependentRequestCount() {
        for (String statement : new String[]{"findStudents", "findStudent"}) {
            String sql = sql(bound(statement, "RESTING"));
            assertTrue(sql.contains("coalesce(open_help.open_help_request_count, 0) as open_help_request_count"));
            assertTrue(sql.contains("select student_id, count(*) as open_help_request_count"));
            assertTrue(sql.contains("group by student_id ) open_help on open_help.student_id = s.id"));
            assertTrue(sql.contains("('employed', 'seeking', 'resting')"));
            assertTrue(sql.contains("= 'need_help' then 'seeking'"));
            assertTrue(sql.contains("= 'need_help' then 'waiting'"));
        }
        assertEquals(1, bound("findStudents", "RESTING").getParameterMappings().stream()
                .filter(value -> value.getProperty().equals("status")).count());
    }

    private static BoundSql bound(String statement, String status) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("status", status);
        parameters.put("subStatus", null);
        parameters.put("namePattern", null);
        parameters.put("studentId", 7L);
        parameters.put("offset", 0L);
        parameters.put("size", 20);
        return configuration.getMappedStatement(NAMESPACE + statement).getBoundSql(parameters);
    }

    private static String sql(BoundSql bound) {
        return bound.getSql().replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }
}
