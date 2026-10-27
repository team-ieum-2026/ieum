// 주인: BE-B
package com.ieum.profile;

import com.fasterxml.jackson.databind.JsonNode;

/** 직접 확인한 기술 태그만 저장하고 AI 요약 문장은 서류에 남긴다. */
final class ProfileSkillsWriter {

    private final ProfileMapper mapper;

    ProfileSkillsWriter(ProfileMapper mapper) {
        this.mapper = mapper;
    }

    // 호출 서비스가 먼저 수료생 잠금을 잡고 동일 트랜잭션에서 실행한다.
    boolean update(Long studentId, Long itemId, JsonNode item, boolean allowDocumentSummaryOnly) {
        if (!studentId.equals(itemId)) throw new ProfileItemNotFoundException();
        if (allowDocumentSummaryOnly && item != null && item.isObject() && !item.has("tags")
                && (item.has("summary") || item.has("source"))) {
            return false;
        }
        mapper.updateSkills(studentId, ProfileItemValues.skills(item));
        return true;
    }
}
