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

/** M8과 서류 저장이 같은 원문으로 AI 프로필을 재추출한다. 트랜잭션은 호출자가 관리한다. */
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
        ObjectNode sources = objectMapper.valueToTree(registered);
        sources.remove("competencyProfile");
        sources.remove("profileUpdatedAt");
        sources.set("projects", objectMapper.valueToTree(currentRead
                ? mapper.findCurrentProjects(studentId) : mapper.findProjects(studentId)));
        // TODO(AI-A/AI-B): 실제 extract 요청 형식 확정 시 입력 변환을 맞춘다.
        String sourceHash = sourceHash(sources);
        JsonNode result = aiClient.extractProfile(sources);
        JsonNode profile = result == null ? null : result.get("profile");
        if (profile == null || !profile.isObject()) throw new BusinessException(ErrorCode.AI_SERVER_ERROR);
        mapper.upsertCompetencyProfile(studentId, profile, sourceHash);
    }

    private String sourceHash(JsonNode sources) {
        try {
            byte[] content = objectMapper.writeValueAsBytes(sources);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("프로필 원문 해시를 만들지 못했어요.", exception);
        }
    }
}
