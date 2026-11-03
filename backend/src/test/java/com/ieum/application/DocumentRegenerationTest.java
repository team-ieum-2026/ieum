// 주인: BE-A
package com.ieum.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import com.ieum.profile.ProfileService;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

/** 실패 서류 다시 생성 · 중복 실행 방지 · 중단된 생성 복구 */
class DocumentRegenerationTest {

    private ApplicationDocumentMapper documentMapper;
    private DocumentGenerationListener generation;
    private ApplicationDocumentService service;

    @BeforeEach
    void setUp() {
        documentMapper = mock(ApplicationDocumentMapper.class);
        generation = mock(DocumentGenerationListener.class);
        service = new ApplicationDocumentService(documentMapper, mock(ProfileService.class), new ObjectMapper(),
                mock(ApplicationEventPublisher.class), generation);
    }

    @Test
    void failedMovesToGeneratingAndStarts() {
        when(documentMapper.selectDetail(7L)).thenReturn(row("FAILED"));
        when(documentMapper.regenerate(7L)).thenReturn(1);

        ApplicationDetailResponse.Regenerating result = service.regenerate(7L);

        assertEquals(7L, result.applicationId());
        assertEquals("GENERATING", result.status());
        verify(generation).start(7L);
    }

    @Test
    void alreadyGeneratingIsConflictWithoutStarting() {
        when(documentMapper.selectDetail(7L)).thenReturn(row("GENERATING"));

        BusinessException e = assertThrows(BusinessException.class, () -> service.regenerate(7L));

        assertEquals(ErrorCode.INVALID_STATUS, e.getErrorCode());
        assertEquals("이미 서류를 만들고 있어요.", e.getMessage());
        verify(generation, never()).start(7L);
    }

    @Test
    void otherStatusIsConflict() {
        for (String status : List.of("DRAFT", "SUBMITTED", "APPROVED", "REJECTED", "DELIVERED")) {
            when(documentMapper.selectDetail(7L)).thenReturn(row(status));
            BusinessException e = assertThrows(BusinessException.class, () -> service.regenerate(7L), status);
            assertEquals(ErrorCode.INVALID_STATUS, e.getErrorCode());
        }
        verify(generation, never()).start(7L);
    }

    @Test
    void lostRaceIsConflict() {
        // 조회 때는 FAILED 였지만 그새 다른 요청이 먼저 GENERATING 으로 바꿨다
        when(documentMapper.selectDetail(7L)).thenReturn(row("FAILED"));
        when(documentMapper.regenerate(7L)).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.regenerate(7L));
        verify(generation, never()).start(7L);
    }

    @Test
    void missingApplicationIsNotFound() {
        BusinessException e = assertThrows(BusinessException.class, () -> service.regenerate(404L));
        assertEquals(ErrorCode.APPLICATION_NOT_FOUND, e.getErrorCode());
    }

    @Test
    void listenerDoesNotQueueSameApplicationTwice() throws Exception {
        DocumentGenerationService generationService = mock(DocumentGenerationService.class);
        CountDownLatch release = new CountDownLatch(1);
        Mockito.doAnswer(invocation -> {
            release.await(5, TimeUnit.SECONDS);
            return null;
        }).when(generationService).generate(7L);
        DocumentGenerationListener listener = new DocumentGenerationListener(generationService);
        try {
            assertTrue(listener.start(7L));
            assertFalse(listener.start(7L));
            assertTrue(listener.isRunning(7L));

            release.countDown();
            verify(generationService, timeout(2000).times(1)).generate(7L);
            for (int i = 0; i < 50 && listener.isRunning(7L); i++) {
                Thread.sleep(20);
            }
            assertFalse(listener.isRunning(7L));
            // 끝난 뒤에는 다시 시작할 수 있다
            assertTrue(listener.start(7L));
            verify(generationService, timeout(2000).times(2)).generate(7L);
        } finally {
            release.countDown();
            listener.shutdown();
        }
    }

    @Test
    void recoveryFailsOnlyStaleApplicationsNotRunningHere() {
        ApplicationMapper mapper = mock(ApplicationMapper.class);
        DocumentGenerationListener listener = mock(DocumentGenerationListener.class);
        when(mapper.selectStaleGenerating(DocumentGenerationRecovery.STALE_MINUTES)).thenReturn(List.of(7L, 8L, 9L));
        when(listener.isRunning(8L)).thenReturn(true);
        when(mapper.failStaleGenerating(7L, DocumentGenerationRecovery.STALE_MINUTES)).thenReturn(1);
        when(mapper.failStaleGenerating(9L, DocumentGenerationRecovery.STALE_MINUTES)).thenReturn(0);

        assertEquals(1, new DocumentGenerationRecovery(mapper, listener).recover());

        verify(mapper, times(1)).failStaleGenerating(7L, DocumentGenerationRecovery.STALE_MINUTES);
        verify(mapper, never()).failStaleGenerating(8L, DocumentGenerationRecovery.STALE_MINUTES);
        verify(mapper, times(1)).failStaleGenerating(9L, DocumentGenerationRecovery.STALE_MINUTES);
    }

    @Test
    void recoveryDoesNotThrow() {
        ApplicationMapper mapper = mock(ApplicationMapper.class);
        when(mapper.selectStaleGenerating(DocumentGenerationRecovery.STALE_MINUTES))
                .thenThrow(new IllegalStateException("DB 끊김"));

        assertEquals(0, new DocumentGenerationRecovery(mapper, mock(DocumentGenerationListener.class)).recover());
    }

    private static ApplicationDocumentMapper.Row row(String status) {
        ApplicationDocumentMapper.Row row = new ApplicationDocumentMapper.Row();
        row.setApplicationId(7L);
        row.setStudentId(1L);
        row.setStatus(status);
        return row;
    }
}
