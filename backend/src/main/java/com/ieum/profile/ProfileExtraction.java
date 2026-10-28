// 주인: BE-B
package com.ieum.profile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ieum.ai.AiClient;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 등록 자료를 AI 요청으로 변환한다. 읽기·AI 호출·결과 저장의 경계는 호출자가 관리한다. */
final class ProfileExtraction {

    private final ProfileMapper mapper;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    ProfileExtraction(ProfileMapper mapper, AiClient aiClient, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
    }

    void refresh(Long studentId, StudentProfileResponse registered) {
        refresh(studentId, registered, false);
    }

    void refresh(Long studentId, StudentProfileResponse registered, boolean currentRead) {
        JsonNode sources = sources(studentId, registered, currentRead);
        mapper.upsertCompetencyProfile(studentId, extract(sources), sourceHash(sources));
    }

    JsonNode sources(Long studentId, StudentProfileResponse registered, boolean currentRead) {
        ObjectNode sources = objectMapper.valueToTree(registered);
        sources.remove("competencyProfile");
        sources.remove("profileUpdatedAt");
        sources.set("projects", objectMapper.valueToTree(currentRead
                ? mapper.findCurrentProjects(studentId) : mapper.findProjects(studentId)));
        // itemId는 원본 테이블의 PK다. 프로젝트 원문은 M8 표시용 응답에서 빠지므로 다시 포함한다.
        return sources;
    }

    JsonNode extract(JsonNode sources) {
        JsonNode result = aiClient.extractProfile(sources);
        JsonNode profile = result == null ? null : result.get("profile");
        if (profile == null || !profile.isObject()) throw new BusinessException(ErrorCode.AI_SERVER_ERROR);
        return profile;
    }

    String sourceHash(JsonNode sources) {
        try {
            byte[] content = objectMapper.writeValueAsBytes(sources);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("프로필 원문 해시를 만들지 못했어요.", exception);
        }
    }
}
