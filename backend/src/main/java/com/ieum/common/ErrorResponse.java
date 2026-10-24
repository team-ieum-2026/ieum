// 주인: BE-A
package com.ieum.common;

/** 실패 응답 공통 형식: { "error": { "code", "message" } } (docs/API.md 1장) */
public record ErrorResponse(ErrorBody error) {

    public record ErrorBody(String code, String message) {
    }

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(new ErrorBody(errorCode.name(), errorCode.getMessage()));
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(new ErrorBody(errorCode.name(), message));
    }
}
