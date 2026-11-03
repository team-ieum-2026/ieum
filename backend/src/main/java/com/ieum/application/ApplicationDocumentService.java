// 주인: BE-A
package com.ieum.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import com.ieum.profile.ProfileService;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 서류 조회 · 수정 · 제출 (BE-A-08, M6 · M6-1 · M6-2, S8).
 * - 조회: GENERATING · FAILED 면 document 는 null
 * - 수정: DRAFT 일 때만. JSON 변경은 DocumentEditor, saveToProfile 이면 BE-B ProfileService 를 같은 트랜잭션에서 호출
 *   (프로필 저장이 실패하면 서류 수정도 되돌린다)
 * - 제출: DRAFT → SUBMITTED + last_response_at 갱신 + ApplicationSubmittedEvent (BE-B-06 상태 재계산용)
 * - 다시 생성: FAILED → GENERATING + 생성 재시작 (DocumentGenerationListener)
 */
@Service
public class ApplicationDocumentService {

    private static final Set<String> NO_DOCUMENT = Set.of("GENERATING", "FAILED");

    private final ApplicationDocumentMapper mapper;
    private final ProfileService profileService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;
    private final DocumentGenerationListener generation;

    public ApplicationDocumentService(ApplicationDocumentMapper mapper, ProfileService profileService,
                                      ObjectMapper objectMapper, ApplicationEventPublisher events,
                                      DocumentGenerationListener generation) {
        this.mapper = mapper;
        this.profileService = profileService;
        this.objectMapper = objectMapper;
        this.events = events;
        this.generation = generation;
    }

    public ApplicationDetailResponse getDetail(Long applicationId) {
        ApplicationDocumentMapper.Row row = require(mapper.selectDetail(applicationId));
        JsonNode document = NO_DOCUMENT.contains(row.getStatus()) || row.getDocument() == null
                ? null : read(row.getDocument());
        return new ApplicationDetailResponse(row.getApplicationId(), row.getStatus(),
                new ApplicationDetailResponse.Job(row.getJobPostingId(), row.getCompanyName(), row.getTitle()),
                document);
    }

    @Transactional
    public ApplicationDetailResponse.DocumentOnly patch(Long applicationId, DocumentPatchRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        ApplicationDocumentMapper.Row row = require(mapper.selectDetailForUpdate(applicationId));
        if (!"DRAFT".equals(row.getStatus()) || row.getDocument() == null) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }
        JsonNode parsed = read(row.getDocument());
        if (!parsed.isObject()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "서류 형식이 올바르지 않아요.");
        }
        ObjectNode document = (ObjectNode) parsed;
        DocumentEditor editor = new DocumentEditor(document, row.getStudentId());
        DocumentEditor.ProfileAction action = editor.apply(request);
        if (action != null) {
            saveToProfile(row.getStudentId(), action, editor);
        }
        mapper.updateDocument(applicationId, write(document));
        return new ApplicationDetailResponse.DocumentOnly(document);
    }

    @Transactional
    public ApplicationDetailResponse.Submitted submit(Long applicationId) {
        ApplicationDocumentMapper.Row row = require(mapper.selectDetail(applicationId));
        if (mapper.submit(applicationId) == 0) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }
        mapper.touchLastResponse(row.getStudentId());
        events.publishEvent(new ApplicationSubmittedEvent(row.getStudentId(), applicationId));
        return new ApplicationDetailResponse.Submitted(applicationId, "SUBMITTED");
    }

    /**
     * 실패한 서류 다시 생성: FAILED → GENERATING 후 생성을 줄 세운다. 트랜잭션 없이 상태부터 바꿔(바로 커밋)
     * 생성 스레드가 GENERATING 을 보게 한다. 조건부 UPDATE 라 동시에 여러 번 불러도 한 번만 시작한다.
     * 이미 생성 중이면 '이미 만들고 있어요' 409, 그 밖의 상태도 409.
     */
    public ApplicationDetailResponse.Regenerating regenerate(Long applicationId) {
        ApplicationDocumentMapper.Row row = require(mapper.selectDetail(applicationId));
        if (mapper.regenerate(applicationId) == 0) {
            if (DocumentGenerationService.GENERATING.equals(row.getStatus())) {
                throw new BusinessException(ErrorCode.INVALID_STATUS, "이미 서류를 만들고 있어요.");
            }
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }
        generation.start(applicationId);
        return new ApplicationDetailResponse.Regenerating(applicationId, DocumentGenerationService.GENERATING);
    }

    private void saveToProfile(Long studentId, DocumentEditor.ProfileAction action, DocumentEditor editor) {
        switch (action.kind()) {
            case ADD -> {
                Long newId = profileService.addItem(studentId, action.section(), action.item());
                editor.relink(action.section(), action.docItemId(), newId);
            }
            case UPDATE -> profileService.updateItem(studentId, action.section(), action.itemId(), action.item());
            case DELETE -> profileService.deleteItem(studentId, action.section(), action.itemId());
        }
    }

    private static ApplicationDocumentMapper.Row require(ApplicationDocumentMapper.Row row) {
        if (row == null) {
            throw new BusinessException(ErrorCode.APPLICATION_NOT_FOUND);
        }
        return row;
    }

    private JsonNode read(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "서류 형식이 올바르지 않아요.");
        }
    }

    private String write(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
