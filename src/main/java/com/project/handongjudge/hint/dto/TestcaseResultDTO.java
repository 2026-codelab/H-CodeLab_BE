package com.project.handongjudge.hint.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * "테스트하기" SSE로 FE가 이미 받은 테스트케이스 하나의 결과.
 * result는 DOMjudge 판정 문자열(wrong-answer/timelimit/memory-limit/run-error/presentation-error/no-output).
 */
@Getter
@Setter
public class TestcaseResultDTO {
    private Integer testcaseRank;
    private String result;
    private String testcaseInput;
    private String expectedOutput;
    private String output;
    private String outputError;
    private String outputDiff;
}
