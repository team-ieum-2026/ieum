// 주인: BE-A
package com.ieum.ai;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * AI 서버(FastAPI, /ai) 호출 인터페이스. 엔드포인트는 docs/API.md 4장.
 * app.ai.mock=true 면 MockAiClient, false 면 RestAiClient 가 빈으로 등록된다.
 * 요청 · 응답은 AI-A가 형식을 확정하기 전까지 JsonNode 로 다룬다. 응답은 { "data": ... } 의 data 부분이다.
 */
public interface AiClient {

    /** POST /ai/profile/extract — 등록 자료 → 역량 프로필 JSON (호출: BE-B, S2) */
    JsonNode extractProfile(JsonNode request);

    /** POST /ai/matching/index — 공고 임베딩 색인 갱신 (호출: BE-A, 일 배치 후, S3) */
    JsonNode indexJobs(JsonNode request);

    /** POST /ai/matching/recommend — 프로필 → Top-3 공고 + 추천 근거 (호출: BE-A, S3 · S4) */
    JsonNode recommend(JsonNode request);

    /** POST /ai/company/collect — 기업 정보 수집 → company_info 형식 (호출: BE-A, 시연 전 1회, S7) */
    JsonNode collectCompanyInfo(JsonNode request);

    /** POST /ai/document/draft — 서류 JSON 생성, 20~40초 걸릴 수 있음 (호출: BE-A, 비동기, S7) */
    JsonNode draftDocument(JsonNode request);

    /** GET /ai/health — { "status": "OK" } */
    JsonNode health();
}
