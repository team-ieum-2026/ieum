// 주인: BE-A
package com.ieum.job;

/** 서울 API 호출 실패(ERROR-500 · 시간 초과 · 응답 형식 오류). 그날 배치는 건너뛴다. */
public class SeoulApiException extends RuntimeException {

    public SeoulApiException(String message) {
        super(message);
    }

    public SeoulApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
