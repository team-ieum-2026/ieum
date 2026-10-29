// 주인: BE-B
package com.ieum.profile;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.managed.ManagedTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * 같은 연결의 임시 테이블 17개에만 전체 seed를 적재하고 실제 M8 매퍼·서비스로 평가 자료를 읽는다.
 * 다른 연결에서 기존 DB의 전후 행·시각 지문을 비교하며, 실제 테이블을 초기화하거나 수정하지 않는다.
 */
@EnabledIfEnvironmentVariable(named = "IEUM_PROFILE_DB_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.config.import=optional:file:.env[.properties],optional:file:backend/.env[.properties]",
        "spring.sql.init.mode=never", "app.ai.mock=true", "debug=false",
        "app.seoul.sync-cron=-", "app.status.recalculate-cron=-",
        "logging.level.org.springframework.jdbc.datasource.init=OFF"
})
class EvaluationSeedDatabaseTest {

    private static final String PROFILE_RESOURCE = "mapper/profile/ProfileMapper.xml";
    private static final List<Table> TABLES = List.of(
            new Table("course", "id"), new Table("student", "id"),
            new Table("student_education", "id"), new Table("student_career", "id"),
            new Table("student_certificate", "id"), new Table("student_project", "id"),
            new Table("student_cover_letter", "student_id"), new Table("competency_profile", "student_id"),
            new Table("job_posting", "id"), new Table("company_info", "id"),
            new Table("recommendation", "id"), new Table("recommendation_item", "id"),
            new Table("help_request", "id"), new Table("application", "id"),
            new Table("application_document", "application_id"), new Table("student_status", "student_id"),
            new Table("contact_log", "id"));
    private static final Set<String> TABLE_NAMES = TABLES.stream().map(Table::name)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static final Pattern INSERT = Pattern.compile("(?is)^INSERT\\s+INTO\\s+([a-z_]+)\\s*\\(");

    @Autowired private DataSource dataSource;
    @Autowired private ObjectMapper json;

    @Test
    void evaluationMaterialsMatchAllTwentyM8ProfilesWithoutChangingAnyExistingDatabaseRows() throws Exception {
        Path root = projectRoot();
        Path seed = root.resolve("db/seed.sql");
        requireOnlyShadowedSeedStatements(Files.readString(seed, StandardCharsets.UTF_8));
        JsonNode materials = readMaterials(root.resolve("ai/eval/personas/out/student_materials.json"));

        try (Connection original = dataSource.getConnection()) {
            assertTrue(original.getAutoCommit(), "실제 DB 비교 연결은 새 SELECT 스냅샷을 읽어야 한다");
            Map<String, String> before = fingerprints(original);
            try {
                try (Connection sandbox = dataSource.getConnection()) {
                    assertTrue("ieum".equals(sandbox.getCatalog()), "seed의 USE 대상과 임시 테이블 DB가 같아야 한다");
                    assertTrue(sandbox.getAutoCommit(), "임시 적재는 독립 연결에서 실행해야 한다");
                    assertTrue(connectionId(original) != connectionId(sandbox), "실제 DB와 임시 적재 연결을 분리해야 한다");
                    verifyInTemporaryTables(sandbox, seed, materials);
                }
            } finally {
                assertTrue(before.equals(fingerprints(original)), "기존 17개 테이블의 모든 행과 저장 시각을 보존해야 한다");
            }
        }
    }

    private void verifyInTemporaryTables(Connection connection, Path seed, JsonNode materials) throws Exception {
        List<String> created = new ArrayList<>();
        try {
            for (Table table : TABLES) {
                try (Statement statement = connection.createStatement()) {
                    // MySQL은 CREATE 대상과 LIKE 원본의 이름이 같으면 중복 alias로 거절한다.
                    String template = "__ieum_seed_" + UUID.randomUUID().toString().replace("-", "");
                    statement.execute("CREATE TEMPORARY TABLE `" + template + "` LIKE `" + table.name() + "`");
                    created.add(template);
                    statement.execute("CREATE TEMPORARY TABLE `" + table.name() + "` LIKE `" + template + "`");
                    created.add(table.name());
                    statement.execute("DROP TEMPORARY TABLE `" + template + "`");
                    created.remove(template);
                }
            }
            assertTrue(created.size() == TABLES.size(), "전체 테이블을 가린 뒤에만 seed를 실행해야 한다");
            try {
                ScriptUtils.executeSqlScript(connection,
                        new EncodedResource(new FileSystemResource(seed), StandardCharsets.UTF_8));
            } catch (RuntimeException exception) {
                // ScriptException의 원인에는 SQL 원문이 있으므로 노출하지 않는다.
                throw new AssertionError("평가 seed의 임시 테이블 적재에 실패했다");
            }

            try (SqlSession session = profileSessionFactory().openSession(connection)) {
                ProfileService profiles = new ProfileService(session.getMapper(ProfileMapper.class), event -> {
                    throw new AssertionError("평가 자료 조회는 AI 재추출이나 변경 이벤트를 발행하면 안 된다");
                });
                assertTrue(count(connection, "SELECT COUNT(*) FROM student") == 50, "전체 시드 수료생을 적재해야 한다");
                Set<Long> seen = new HashSet<>();
                for (JsonNode expected : materials) {
                    long studentId = expected.path("studentId").asLong();
                    assertTrue(studentId >= 1 && studentId <= 20 && seen.add(studentId),
                            "평가 자료의 수료생 번호는 중복 없는 1~20이어야 한다");
                    StudentProfileResponse actual;
                    try {
                        actual = profiles.getProfile(studentId);
                    } catch (RuntimeException exception) {
                        throw new AssertionError("새 평가 seed의 실제 프로필 매퍼 조회에 실패했다");
                    }
                    assertTrue(normalize(expected).equals(normalize(serializedProfile(actual))),
                            "평가 자료의 기본정보·과정·기술·학력·경력·자격증·프로젝트·자소서가 M8 조회와 같아야 한다");
                    verifyProjectOriginals(connection, studentId, expected.path("projects"));
                }
                assertTrue(seen.size() == 20, "평가 수료생 20명 모두를 확인해야 한다");
                verifyProtectedProjectOwners(connection);
            }
            assertTrue(!connection.isClosed(), "MyBatis 세션 종료 후에도 임시 테이블 정리 연결을 유지해야 한다");
        } finally {
            dropTemporaryTables(connection, created);
        }
    }

    private SqlSessionFactory profileSessionFactory() throws Exception {
        ManagedTransactionFactory transactions = new ManagedTransactionFactory();
        Properties properties = new Properties();
        properties.setProperty("closeConnection", "false");
        transactions.setProperties(properties);
        Configuration configuration = new Configuration(new Environment("evaluation-seed", transactions, dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLogImpl(NoLoggingImpl.class);
        try (InputStream input = Resources.getResourceAsStream(PROFILE_RESOURCE)) {
            new XMLMapperBuilder(input, configuration, PROFILE_RESOURCE, configuration.getSqlFragments()).parse();
        }
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private JsonNode readMaterials(Path path) throws Exception {
        JsonNode materials;
        try (InputStream input = Files.newInputStream(path)) {
            materials = json.readTree(input);
        } catch (Exception exception) {
            throw new AssertionError("평가용 등록 자료 JSON을 읽지 못했다");
        }
        assertTrue(materials.isArray() && materials.size() == 20, "평가 자료에는 수료생 20명이 있어야 한다");
        return materials;
    }

    private JsonNode serializedProfile(StudentProfileResponse profile) {
        try {
            // HTTP JSON과 같은 직렬화 경계를 거쳐 작은 Long ID와 JSON의 Int 노드를 같은 표현으로 맞춘다.
            return json.readTree(json.writeValueAsBytes(profile));
        } catch (Exception exception) {
            throw new AssertionError("실제 M8 등록 자료의 JSON 직렬화에 실패했다");
        }
    }

    private ObjectNode normalize(JsonNode profile) {
        assertTrue(profile.isObject(), "등록 자료는 프로필 객체여야 한다");
        ObjectNode normalized = (ObjectNode) profile.deepCopy();
        normalized.remove(List.of("profileUpdatedAt", "competencyProfile"));
        if (normalized.path("coverLetter").isObject()) {
            ((ObjectNode) normalized.path("coverLetter")).remove("updatedAt");
        }
        for (String field : List.of("education", "careers", "certificates", "projects")) {
            assertTrue(normalized.path(field).isArray(), "등록 자료의 항목 목록은 배열이어야 한다");
            List<JsonNode> items = new ArrayList<>();
            normalized.path(field).forEach(items::add);
            items.sort(Comparator.comparingLong(item -> item.path("itemId").asLong()));
            ArrayNode ordered = json.createArrayNode();
            for (JsonNode item : items) {
                if ("projects".equals(field) && item.isObject()) {
                    ((ObjectNode) item).remove(List.of("readme", "roleNote", "source"));
                }
                ordered.add(item);
            }
            normalized.set(field, ordered);
        }
        return normalized;
    }

    private void verifyProjectOriginals(Connection connection, long studentId, JsonNode projects) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT role_note, readme, source FROM student_project WHERE id = ? AND student_id = ?
                """)) {
            for (JsonNode expected : projects) {
                statement.setLong(1, expected.path("itemId").asLong());
                statement.setLong(2, studentId);
                try (ResultSet rows = statement.executeQuery()) {
                    assertTrue(rows.next(), "평가 프로젝트 번호와 소유 수료생을 유지해야 한다");
                    assertTrue(Objects.equals(text(expected.path("roleNote")), rows.getString("role_note")),
                            "평가 프로젝트 역할 기록을 그대로 저장해야 한다");
                    assertTrue(Objects.equals(text(expected.path("readme")), rows.getString("readme")),
                            "평가 프로젝트 README 원문을 그대로 저장해야 한다");
                    assertTrue(Objects.equals(text(expected.path("source")), rows.getString("source")),
                            "평가 프로젝트 등록 출처를 유지해야 한다");
                    assertTrue(!rows.next(), "평가 프로젝트는 한 행으로 연결되어야 한다");
                }
            }
        }
    }

    private static void verifyProtectedProjectOwners(Connection connection) throws SQLException {
        Map<Long, Long> expected = Map.of(11L, 1L, 12L, 1L, 113L, 12L);
        Map<Long, Long> actual = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT id, student_id FROM student_project WHERE id IN (11,12,113)")) {
            while (rows.next()) actual.put(rows.getLong("id"), rows.getLong("student_id"));
        }
        assertTrue(expected.equals(actual), "보존 대상 프로젝트 3개의 번호와 소유 수료생을 유지해야 한다");
    }

    private Map<String, String> fingerprints(Connection connection) throws Exception {
        Map<String, String> signatures = new LinkedHashMap<>();
        for (Table table : TABLES) {
            List<Map<String, Object>> values = new ArrayList<>();
            try (Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery("SELECT * FROM `" + table.name() + "` ORDER BY `" + table.key() + "`")) {
                ResultSetMetaData columns = rows.getMetaData();
                while (rows.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int column = 1; column <= columns.getColumnCount(); column++) {
                        row.put(columns.getColumnLabel(column), rows.getObject(column));
                    }
                    values.add(row);
                }
            }
            byte[] content = json.writeValueAsBytes(values);
            signatures.put(table.name(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)));
        }
        return signatures;
    }

    private static void dropTemporaryTables(Connection connection, List<String> created) {
        boolean failed = false;
        for (int index = created.size() - 1; index >= 0; index--) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("DROP TEMPORARY TABLE IF EXISTS `" + created.get(index) + "`");
            } catch (SQLException exception) {
                failed = true;
            }
        }
        if (failed) {
            // 정리 실패한 연결이 풀에 재사용되지 않도록 종료한다. 실제 테이블에는 DROP을 실행하지 않는다.
            try {
                connection.abort(Runnable::run);
            } catch (SQLException ignored) {
                // 아래 고정 메시지로만 실패를 알린다.
            }
            throw new AssertionError("임시 테이블 정리에 실패하여 검증 연결을 종료했다");
        }
    }

    private static long connectionId(Connection connection) throws SQLException {
        return count(connection, "SELECT CONNECTION_ID()");
    }

    private static long count(Connection connection, String query) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(query)) {
            assertTrue(rows.next(), "검증 집계 결과가 있어야 한다");
            return rows.getLong(1);
        }
    }

    private static String text(JsonNode value) {
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static Path projectRoot() {
        return List.of(Path.of(".."), Path.of(".")).stream()
                .filter(path -> Files.isRegularFile(path.resolve("db/seed.sql")))
                .findFirst().orElseThrow(() -> new AssertionError("저장소의 평가용 seed 파일이 필요하다"));
    }

    private static void requireOnlyShadowedSeedStatements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (int index = 0; index < script.length(); index++) {
            char character = script.charAt(index);
            if (quote != 0) {
                if (character == '\\') index++;
                else if (character == quote) {
                    if (index + 1 < script.length() && script.charAt(index + 1) == quote) index++;
                    else quote = 0;
                }
                continue;
            }
            if (character == '\'' || character == '"' || character == '`') {
                quote = character;
                current.append(" ? ");
            } else if (character == '-' && index + 1 < script.length() && script.charAt(index + 1) == '-') {
                while (index < script.length() && script.charAt(index) != '\n') index++;
                current.append(' ');
            } else if (character == '/' && index + 1 < script.length() && script.charAt(index + 1) == '*') {
                throw new AssertionError("임시 적재 seed에는 실행 가능한 블록 주석을 허용하지 않는다");
            } else if (character == ';') {
                if (!current.toString().isBlank()) statements.add(current.toString().trim());
                current.setLength(0);
            } else current.append(character);
        }
        assertTrue(quote == 0 && current.toString().isBlank(), "평가 seed의 SQL 문장이 완결되어야 한다");
        Set<String> inserts = new HashSet<>();
        int uses = 0;
        int encodings = 0;
        for (String statement : statements) {
            String command = statement.replaceAll("\\s+", " ").trim();
            if ("USE ieum".equalsIgnoreCase(command)) uses++;
            else if ("SET NAMES utf8mb4".equalsIgnoreCase(command)) encodings++;
            else {
                Matcher insert = INSERT.matcher(statement);
                assertTrue(insert.find() && TABLE_NAMES.contains(insert.group(1)),
                        "평가 seed는 가려진 고정 테이블의 INSERT만 실행할 수 있다");
                inserts.add(insert.group(1));
            }
        }
        assertTrue(uses == 1 && encodings == 1 && inserts.containsAll(Set.of("course", "student",
                        "student_education", "student_career", "student_certificate", "student_project",
                        "student_cover_letter")),
                "평가 seed의 DB·문자셋과 필수 등록 자료 테이블을 먼저 확인해야 한다");
    }

    private record Table(String name, String key) {}
}
