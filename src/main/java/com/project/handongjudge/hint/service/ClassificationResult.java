package com.project.handongjudge.hint.service;

import com.project.handongjudge.hint.entity.ErrorCategory;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** ① 오류 유형 분류의 결과물. localizedSummary는 ②(힌트 생성)의 입력으로 그대로 전달됨. */
@Getter
@AllArgsConstructor
public class ClassificationResult {
    private final ErrorCategory category;
    private final String localizedSummary;
    /** true면 규칙 기반 즉시 분류(LLM 호출 없음), false면 보조 LLM 분류 사용 */
    private final boolean ruleBased;
}
