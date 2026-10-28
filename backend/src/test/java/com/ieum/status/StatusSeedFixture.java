// 주인: BE-B
package com.ieum.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Checked-in 시드를 실행하지 않고 상태 판정에 필요한 원천 필드만 읽는다. */
final class StatusSeedFixture {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    static final LocalDate BASE_DATE = LocalDate.of(2026, 10, 28);
    static final Map<String, Long> STATUS_COUNTS = Map.of(
            "EMPLOYED", 10L, "NEED_HELP", 4L, "CHECK_REQUIRED", 7L, "SEEKING", 29L);
    static final Map<String, Long> SEEKING_COUNTS = Map.of("WAITING", 22L, "APPLYING", 4L, "OTHER", 3L);

    private static final Pattern RELATIVE_TIME = Pattern.compile(
            "DATE_SUB\\(DATE_ADD\\(CURDATE\\(\\), INTERVAL (\\d+) HOUR\\), INTERVAL (\\d+) DAY\\)");

    record Student(long id, LocalDate completedOn, LocalDateTime lastResponseAt) {}
    record Recommendation(long id, long studentId, String triggerType, LocalDateTime createdAt) {}
    record Item(long id, long recommendationId, int rank, String response,
                String passReason, LocalDateTime respondedAt) {}
    record Help(long studentId, Long recommendationItemId, String status, LocalDateTime createdAt) {}
    record Application(long studentId, long recommendationItemId, String status,
                       LocalDateTime submittedAt, LocalDateTime reviewedAt, LocalDateTime deliveredAt) {}
    record Expected(String status, String subStatus) {}

    final List<Student> students;
    final List<Recommendation> recommendations;
    final List<Item> items;
    final List<Help> helpRequests;
    final List<Application> applications;
    final Map<Long, Expected> expected;
    final LocalDateTime now;

    private StatusSeedFixture(String sql, LocalDate baseDate) {
        this.now = baseDate.atTime(12, 0);
        students = rows(sql, "student").stream().map(row -> new Student(
                number(row, "id"), LocalDate.parse(text(row, "completed_on")), time(row, "last_response_at")))
                .toList();
        recommendations = rows(sql, "recommendation").stream().map(row -> new Recommendation(
                number(row, "id"), number(row, "student_id"), text(row, "trigger_type"), time(row, "created_at")))
                .toList();
        items = rows(sql, "recommendation_item").stream().map(row -> new Item(
                number(row, "id"), number(row, "recommendation_id"), Math.toIntExact(number(row, "rank_no")),
                text(row, "response"), text(row, "pass_reason"), time(row, "responded_at")))
                .toList();
        helpRequests = rows(sql, "help_request").stream().map(row -> new Help(
                number(row, "student_id"), nullableNumber(row, "recommendation_item_id"),
                row.containsKey("status") ? text(row, "status") : "OPEN", time(row, "created_at")))
                .toList();
        applications = rows(sql, "application").stream().map(row -> new Application(
                number(row, "student_id"), number(row, "recommendation_item_id"), text(row, "status"),
                time(row, "submitted_at"), time(row, "reviewed_at"), time(row, "delivered_at")))
                .toList();
        Map<Long, Expected> statuses = new LinkedHashMap<>();
        for (Map<String, String> row : rows(sql, "student_status")) {
            if (statuses.put(number(row, "student_id"),
                    new Expected(text(row, "status"), text(row, "sub_status"))) != null) {
                throw new IllegalArgumentException("Duplicate student_status seed ID");
            }
        }
        expected = Map.copyOf(statuses);
    }

    static StatusSeedFixture load() throws IOException {
        return load(BASE_DATE);
    }

    static StatusSeedFixture load(LocalDate baseDate) throws IOException {
        Path path = List.of(Path.of("../db/seed.sql"), Path.of("db/seed.sql")).stream()
                .filter(Files::isRegularFile).findFirst()
                .orElseThrow(() -> new IOException("Checked-in db/seed.sql was not found"));
        return new StatusSeedFixture(Files.readString(path, StandardCharsets.UTF_8), baseDate);
    }

    Clock clock() {
        return Clock.fixed(now.atZone(SEOUL).toInstant(), SEOUL);
    }

    static Map<String, Long> statusCounts(Map<Long, Expected> values) {
        return counts(values.values().stream().map(Expected::status).toList());
    }

    static Map<String, Long> seekingCounts(Map<Long, Expected> values) {
        return counts(values.values().stream().filter(value -> "SEEKING".equals(value.status()))
                .map(Expected::subStatus).toList());
    }

    private static Map<String, Long> counts(List<String> values) {
        return values.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

    private LocalDateTime time(Map<String, String> row, String field) {
        String expression = token(row, field);
        if ("NULL".equals(expression)) return null;
        var relative = RELATIVE_TIME.matcher(expression);
        if (!relative.matches()) {
            // Unexpected seed expressions fail explicitly rather than consulting the machine's clock.
            throw new IllegalArgumentException("Unsupported seed timestamp expression in " + field);
        }
        return now.toLocalDate().atStartOfDay().plusHours(Long.parseLong(relative.group(1)))
                .minusDays(Long.parseLong(relative.group(2)));
    }

    private static String text(Map<String, String> row, String field) {
        String value = token(row, field);
        if ("NULL".equals(value)) return null;
        if (value.length() < 2 || !value.startsWith("'") || !value.endsWith("'")) {
            throw new IllegalArgumentException("Expected quoted seed value in " + field);
        }
        return value.substring(1, value.length() - 1).replace("''", "'");
    }

    private static long number(Map<String, String> row, String field) {
        return Long.parseLong(token(row, field));
    }

    private static Long nullableNumber(Map<String, String> row, String field) {
        return "NULL".equals(token(row, field)) ? null : number(row, field);
    }

    private static String token(Map<String, String> row, String field) {
        String value = row.get(field);
        if (value == null) throw new IllegalArgumentException("Missing seed column " + field);
        return value;
    }

    private static List<Map<String, String>> rows(String sql, String table) {
        var insert = Pattern.compile("INSERT\\s+INTO\\s+" + Pattern.quote(table)
                + "\\s*\\(([^)]*)\\)\\s*VALUES\\s*", Pattern.CASE_INSENSITIVE).matcher(sql);
        if (!insert.find()) throw new IllegalArgumentException("Missing seed INSERT for " + table);
        List<String> columns = split(insert.group(1));
        String values = sql.substring(insert.end(), statementEnd(sql, insert.end()));
        List<Map<String, String>> result = new ArrayList<>();
        for (String tuple : split(values)) {
            if (!tuple.startsWith("(") || !tuple.endsWith(")")) {
                throw new IllegalArgumentException("Malformed seed tuple for " + table);
            }
            List<String> fields = split(tuple.substring(1, tuple.length() - 1));
            if (fields.size() != columns.size()) {
                throw new IllegalArgumentException("Seed column/value count mismatch for " + table);
            }
            Map<String, String> row = new LinkedHashMap<>();
            for (int index = 0; index < columns.size(); index++) row.put(columns.get(index), fields.get(index));
            result.add(row);
        }
        return List.copyOf(result);
    }

    /** SQL strings may contain commas, parentheses, semicolons and doubled apostrophes. */
    private static int statementEnd(String sql, int start) {
        boolean quoted = false;
        for (int index = start; index < sql.length(); index++) {
            char character = sql.charAt(index);
            if (quoted && character == '\\') {
                index++;
            } else if (character == '\'') {
                if (quoted && index + 1 < sql.length() && sql.charAt(index + 1) == '\'') index++;
                else quoted = !quoted;
            } else if (!quoted && character == ';') {
                return index;
            }
        }
        throw new IllegalArgumentException("Unterminated seed INSERT");
    }

    private static List<String> split(String values) {
        List<String> result = new ArrayList<>();
        int start = 0;
        int depth = 0;
        boolean quoted = false;
        for (int index = 0; index < values.length(); index++) {
            char character = values.charAt(index);
            if (quoted && character == '\\') {
                index++;
            } else if (character == '\'') {
                if (quoted && index + 1 < values.length() && values.charAt(index + 1) == '\'') index++;
                else quoted = !quoted;
            } else if (!quoted) {
                if (character == '(') depth++;
                else if (character == ')') depth--;
                else if (character == ',' && depth == 0) {
                    result.add(values.substring(start, index).trim());
                    start = index + 1;
                }
                if (depth < 0) throw new IllegalArgumentException("Unbalanced seed tuple");
            }
        }
        if (quoted || depth != 0) throw new IllegalArgumentException("Unbalanced seed tuple");
        result.add(values.substring(start).trim());
        return List.copyOf(result);
    }
}
