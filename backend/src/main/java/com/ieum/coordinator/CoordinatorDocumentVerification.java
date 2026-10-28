// 주인: BE-B
package com.ieum.coordinator;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;

/** 저장된 문서만 읽는다. 원문·근거·제외 수를 변경하거나 현재 프로필로 다시 검증하지 않는다. */
final class CoordinatorDocumentVerification {
    private static final Set<String> COMPANY_STATUSES = Set.of("FOUND", "PARTIAL", "NOT_FOUND");

    private CoordinatorDocumentVerification() {}

    static CoordinatorApplicationResponse.Verification summarize(JsonNode document) {
        if (document == null || document.isNull()) {
            return new CoordinatorApplicationResponse.Verification(0, 0, 0, 0, "NOT_FOUND");
        }
        int[] sentences = new int[2];
        JsonNode sections = document.path("sections");
        if (sections.isArray()) {
            for (JsonNode section : sections) {
                switch (section.path("section").asText("")) {
                    case "SKILLS" -> countSentences(section.path("summary"), sentences);
                    case "COVER_LETTER" -> countSentences(section.path("sentences"), sentences);
                    case "PROJECTS" -> {
                        if (section.path("items").isArray()) {
                            for (JsonNode project : section.path("items")) {
                                countSentences(project.path("bullets"), sentences);
                            }
                        }
                    }
                    default -> { }
                }
            }
        }
        JsonNode excluded = document.path("excludedSentenceCount");
        int excludedCount = excluded.isIntegralNumber() && excluded.canConvertToInt()
                ? Math.max(0, excluded.intValue()) : 0;
        String companyStatus = document.path("companyInfoStatus").asText("");
        if (!COMPANY_STATUSES.contains(companyStatus)) companyStatus = "NOT_FOUND";
        return new CoordinatorApplicationResponse.Verification(sentences[0], sentences[1], excludedCount,
                countUserSources(document), companyStatus);
    }

    private static void countSentences(JsonNode nodes, int[] counts) {
        if (!nodes.isArray()) return;
        for (JsonNode sentence : nodes) {
            if (!sentence.isObject() || !"AI".equals(sentence.path("source").asText(null))) continue;
            counts[0]++;
            JsonNode evidence = sentence.path("evidence");
            if (evidence.isArray() && !evidence.isEmpty()) counts[1]++;
        }
    }

    // M7과 같은 기준으로 USER 문장·항목·섹션을 센다. evidence 아래는 세지 않는다.
    private static int countUserSources(JsonNode node) {
        int count = 0;
        if (node.isObject()) {
            if ("USER".equals(node.path("source").asText(null))) count++;
            for (var field : node.properties()) {
                if (!"evidence".equals(field.getKey())) count += countUserSources(field.getValue());
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) count += countUserSources(child);
        }
        return count;
    }
}
