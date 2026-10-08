package com.project.handongjudge.hint.entity;

/** 설계 문서 3장(오류 유형 분류)에서 정의한 카테고리. CE/WA/RE 각각의 세부 분류 + 공통 폴백(UNCERTAIN). */
public enum ErrorCategory {
    // CE
    UNDECLARED_VARIABLE,
    TYPE_MISMATCH,
    MISSING_RETURN,
    SYNTAX_ERROR,

    // WA
    OFF_BY_ONE,
    EDGE_CASE_MISSING,
    OUTPUT_FORMAT_MISMATCH,
    PARTIAL_LOGIC_ERROR,
    WRONG_APPROACH,
    INEFFICIENT_ALGORITHM,

    // RE
    INVALID_MEMORY_ACCESS,
    DIVISION_BY_ZERO,
    STACK_OVERFLOW,
    UNCATEGORIZED_RUNTIME_ERROR,

    // 규칙·LLM 모두로도 분류 신뢰도가 낮을 때
    UNCERTAIN
}
