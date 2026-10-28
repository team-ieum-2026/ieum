// 주인: BE-B
package com.ieum.coordinator;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;

/** C4 서류 조회와 승인·반려·전달 상태 확인에 사용하는 DB 행. */
public final class CoordinatorApplicationData {

    private CoordinatorApplicationData() {}

    @Getter
    @Setter
    public static class Detail {
        private Long applicationId;
        private Long studentId;
        private Long jobPostingId;
        private String studentName;
        private String status;
        private String companyName;
        private String title;
        private JsonNode document;
    }

    @Getter
    @Setter
    public static class ReviewTarget {
        private Long applicationId;
        private Long studentId;
        private String status;
    }
}
