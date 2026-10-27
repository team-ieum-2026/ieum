// 주인: BE-A
package com.ieum.job;

import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** job_posting 테이블 한 줄 (db/schema.sql). BE-A 소유 테이블이라 job 패키지에 둔다. */
@Getter
@Setter
@NoArgsConstructor
public class JobPosting {

    private Long id;
    private String source;
    private String externalId;
    private String companyName;
    private String title;
    private String jobCode;
    private String jobCategory;
    private String location;
    private String workAddress;
    private String employmentType;
    private String career;
    private String education;
    private String salaryText;
    private String description;
    private String homepageUrl;
    private LocalDate deadlineOn;
    private boolean untilFilled;
    private LocalDate postedOn;
    private boolean active;
    private String contentHash;
    /** API 원본 한 건 (JSON 문자열). CONTACT_TELNO 는 여기에만 남고 화면 · API로 내보내지 않는다. */
    private String raw;
}
