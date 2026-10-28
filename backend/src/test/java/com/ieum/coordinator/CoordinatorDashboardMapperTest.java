// 주인: BE-B
package com.ieum.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** DB 연결 없이 실제 MyBatis XML 파싱·결과 타입·조회 범위 계약을 확인한다. */
class CoordinatorDashboardMapperTest {

    private static final String NAMESPACE = "com.ieum.coordinator.CoordinatorDashboardMapper.";
    private static final String RESOURCE = "mapper/coordinator/CoordinatorDashboardMapper.xml";
    private static final Map<String, Class<?>> RESULT_TYPES = Map.of(
            "findStudentStates", CoordinatorDashboardData.StudentState.class,
            "findOpenHelpRequests", CoordinatorDashboardData.HelpRequest.class,
            "findReviewQueue", CoordinatorDashboardData.ReviewQueueItem.class,
            "findLatestPassNotes", CoordinatorDashboardData.NoteToCheck.class);
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
    void allFourQueriesAreUnboundedReadsWithTheirOwnResultTypes() {
        Set<String> statementNames = configuration.getMappedStatementNames().stream()
                .filter(name -> name.startsWith(NAMESPACE))
                .map(name -> name.substring(NAMESPACE.length()))
                .collect(Collectors.toSet());
        assertEquals(RESULT_TYPES.keySet(), statementNames);
        RESULT_TYPES.forEach((name, expectedType) -> {
            MappedStatement statement = configuration.getMappedStatement(NAMESPACE + name);
            assertEquals(SqlCommandType.SELECT, statement.getSqlCommandType());
            assertEquals(expectedType, statement.getResultMaps().get(0).getType());
            assertTrue(statement.getBoundSql(null).getParameterMappings().isEmpty());
            String sql = sql(name);
            assertFalse(sql.matches(".*\\b(insert|update|delete|replace|call|limit)\\b.*"),
                    "대시보드 조회가 데이터를 바꾸거나 목록을 임의로 잘라서는 안 된다");
        });
    }

    private static String sql(String name) {
        return configuration.getMappedStatement(NAMESPACE + name).getBoundSql(null).getSql()
                .replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }
}
