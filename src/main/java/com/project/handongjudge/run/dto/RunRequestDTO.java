package com.project.handongjudge.run.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * "코딩 실습" 실행 요청. 과제/문제와 무관하게 코드 + (선택) stdin만 받아서 그대로 실행한다.
 */
@Getter
@Setter
public class RunRequestDTO {
    /** 학생의 DOMjudge 팀을 찾기 위한 용도로만 사용 (실행 결과는 어떤 과제와도 연결되지 않음). */
    private Long sectionId;
    private String language;
    private String code;
    /** 프로그램 표준 입력. 없으면 빈 입력으로 실행. */
    private String stdin;
}
