// 주인: BE-A
package com.ieum.job;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 공고 배치 설정: app.seoul.* 바인딩 + 일 배치 스케줄 활성화 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(SeoulJobProperties.class)
public class JobConfig {
}
