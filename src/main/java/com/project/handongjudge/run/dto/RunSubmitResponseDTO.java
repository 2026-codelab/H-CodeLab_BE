package com.project.handongjudge.run.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * sessionKey(= DOMjudge submissionId)로 SSE 스트림에 연결한다.
 * domjudgeProblemId는 실행이 끝난 뒤 스크래치 문제를 정리(삭제)하기 위해 스트림 요청 시 그대로 되돌려줘야 한다.
 */
@Getter
@Builder
public class RunSubmitResponseDTO {
    private String sessionKey;
    private String domjudgeProblemId;
}
