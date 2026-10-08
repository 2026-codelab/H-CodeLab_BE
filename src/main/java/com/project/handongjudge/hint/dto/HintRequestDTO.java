package com.project.handongjudge.hint.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * "테스트하기"는 Submission을 DB에 저장하지 않으므로, 힌트에 필요한 정보를
 * FE가 이미 들고 있는 SSE 결과에서 그대로 실어 보낸다.
 * outputCompile이 있으면 컴파일 에러 경로(힌트 횟수 차감 없음), 없으면
 * testcases(실패한 케이스 전체)를 기반으로 통합 힌트를 생성하는 일반 경로다.
 */
@Getter
@Setter
public class HintRequestDTO {
    /** 학생이 제출한 코드 — 힌트 생성·분류 모두에 사용 */
    private String code;

    /** 컴파일 에러일 때만 설정됨. 설정되어 있으면 컴파일 에러 경로로 처리. */
    private String outputCompile;

    /** 컴파일 에러가 아닐 때, 실패한(correct가 아닌) 테스트케이스 전체 */
    private List<TestcaseResultDTO> testcases;
}
