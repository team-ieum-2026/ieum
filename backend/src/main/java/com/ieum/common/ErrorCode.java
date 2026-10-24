// 주인: BE-A
package com.ieum.common;

import org.springframework.http.HttpStatus;

/** 에러 코드와 화면에 그대로 띄울 수 있는 한국어 메시지 (docs/API.md 1장) */
public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않아요."),
    PASS_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "패스 사유를 골라 주세요."),
    STUDENT_NOT_FOUND(HttpStatus.NOT_FOUND, "수료생 정보를 찾을 수 없어요."),
    APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "지원서를 찾을 수 없어요."),
    RECOMMENDATION_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "추천 공고를 찾을 수 없어요."),
    ALREADY_RESPONDED(HttpStatus.CONFLICT, "이미 응답한 공고예요."),
    INVALID_STATUS(HttpStatus.CONFLICT, "지금 상태에서는 할 수 없는 작업이에요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 생겼어요. 잠시 후 다시 시도해 주세요."),
    AI_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "AI 서버 응답에 실패했어요. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
