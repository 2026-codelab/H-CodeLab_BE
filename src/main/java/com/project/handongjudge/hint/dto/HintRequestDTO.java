package com.project.handongjudge.hint.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * "테스트하기"는 Submission을 DB에 저장하지 않으므로, 힌트에 필요한 정보를
 * FE가 이미 들고 있는 SSE 테스트케이스 데이터에서 그대로 실어 보낸다.
 * result는 DOMjudge의 테스트케이스별 판정 문자열
 * (correct/wrong-answer/timelimit/memory-limit/run-error/presentation-error/no-output)
 * 이거나, 컴파일 실패 시 "compiler-error".
 */
@Getter
@Setter
public class HintRequestDTO {
    private String result;

    /** compiler-error일 때만 사용 */
    private String outputCompile;

    /** compiler-error가 아닐 때 사용되는 테스트케이스 상세 */
    private Integer testcaseRank;
    private String testcaseInput;
    private String expectedOutput;
    private String output;
    private String outputError;
    private String outputDiff;
}
