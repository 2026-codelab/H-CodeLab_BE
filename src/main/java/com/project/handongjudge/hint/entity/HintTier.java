package com.project.handongjudge.hint.entity;

/** 설계 문서 4장: 같은 카테고리의 반복 횟수에 따라 유도강도가 바뀜. */
public enum HintTier {
    SOCRATIC,
    MEDIUM,
    DIRECT;

    /** 1회=소크라테스식, 2회=중간, 3회 이상=직접형 (반복 횟수는 이번 힌트 요청까지 포함한 값). */
    public static HintTier fromRepeatCount(int repeatCount) {
        if (repeatCount <= 1) {
            return SOCRATIC;
        } else if (repeatCount == 2) {
            return MEDIUM;
        }
        return DIRECT;
    }
}
