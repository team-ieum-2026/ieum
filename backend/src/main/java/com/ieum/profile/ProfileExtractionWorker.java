// 주인: BE-B
package com.ieum.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieum.ai.AiClient;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** AI를 기다리는 동안 DB 트랜잭션이나 학생 행 잠금을 유지하지 않는다. */
@Service
public class ProfileExtractionWorker {

    private final ProfileMapper mapper;
    private final ProfileService profiles;
    private final ProfileExtraction extraction;
    private final TransactionTemplate reads;
    private final TransactionTemplate writes;

    public ProfileExtractionWorker(ProfileMapper mapper, ProfileService profiles, AiClient aiClient,
                                   ObjectMapper objectMapper, PlatformTransactionManager transactions) {
        this.mapper = mapper;
        this.profiles = profiles;
        this.extraction = new ProfileExtraction(mapper, aiClient, objectMapper);
        this.reads = new TransactionTemplate(transactions);
        this.reads.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.reads.setReadOnly(true);
        this.writes = new TransactionTemplate(transactions);
        this.writes.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void refresh(Long studentId) {
        extract(studentId, false);
    }

    /** 기존 프로필은 건너뛰고, 새 프로필을 실제 저장한 경우에만 true를 반환한다. */
    public boolean extractIfMissing(Long studentId) {
        return extract(studentId, true);
    }

    private boolean extract(Long studentId, boolean onlyMissing) {
        JsonNode sources = reads.execute(status -> {
            StudentProfileResponse registered = profiles.getProfile(studentId);
            if (onlyMissing && registered.competencyProfile() != null) return null;
            return extraction.sources(studentId, registered, false);
        });
        if (sources == null) return false;
        String hash = extraction.sourceHash(sources);
        JsonNode profile = extraction.extract(sources);

        return Boolean.TRUE.equals(writes.execute(status -> {
            if (mapper.lockStudent(studentId) == null) {
                if (onlyMissing) throw new BusinessException(ErrorCode.STUDENT_NOT_FOUND);
                return false;
            }
            StudentProfileResponse registered = profiles.getCurrentProfile(studentId);
            // M8의 추출이 먼저 저장되었어도 관리용 API가 기존 프로필을 덮어쓰지 않는다.
            if (onlyMissing && registered.competencyProfile() != null) return false;
            JsonNode current = extraction.sources(studentId, registered, true);
            // AI 응답을 기다리는 사이 변경된 원문에는 이전 결과를 덮어쓰지 않는다.
            if (!hash.equals(extraction.sourceHash(current))) {
                if (onlyMissing) throw new IllegalStateException("추출 중 프로필 원문이 변경되었어요.");
                return false;
            }
            mapper.upsertCompetencyProfile(studentId, profile, hash);
            return true;
        }));
    }
}
