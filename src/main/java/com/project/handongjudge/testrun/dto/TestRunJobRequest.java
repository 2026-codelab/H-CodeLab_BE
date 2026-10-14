package com.project.handongjudge.testrun.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * testrun-service {@code POST /v1/jobs} 요청.
 * JSON 형식은 H-CodeLab_TestRun common-contract의 CreateJobRequest와 같다.
 */
@Getter
@Builder
public class TestRunJobRequest {
    private Long userId;
    private Long sectionId;
    private Long problemId;
    /** ASSIGNMENT 또는 QUIZ */
    private String contextType;
    private Long contextId;
    private boolean manager;
    /** DOMjudge 언어 ID (c, cpp, java, python3) */
    private String langId;
    private String code;
    private Double timeLimitSec;
    private Integer memoryLimitMb;
    private String compareArgs;
    private String problemHash;
}
