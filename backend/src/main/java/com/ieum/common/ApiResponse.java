// 주인: BE-A
package com.ieum.common;

/** 성공 응답 공통 형식: { "data": ... } (docs/API.md 1장) */
public record ApiResponse<T>(T data) {

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data);
    }
}
