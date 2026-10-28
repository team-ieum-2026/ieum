// 주인: BE-B
package com.ieum.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieum.ai.AiClient;
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
        JsonNode sources = reads.execute(status ->
                extraction.sources(studentId, profiles.getProfile(studentId), false));
        String hash = extraction.sourceHash(sources);
        JsonNode profile = extraction.extract(sources);

        writes.executeWithoutResult(status -> {
            if (mapper.lockStudent(studentId) == null) return;
            JsonNode current = extraction.sources(studentId, profiles.getCurrentProfile(studentId), true);
            // AI 응답을 기다리는 사이 변경된 원문에는 이전 결과를 덮어쓰지 않는다.
            if (hash.equals(extraction.sourceHash(current))) {
                mapper.upsertCompetencyProfile(studentId, profile, hash);
            }
        });
    }
}
