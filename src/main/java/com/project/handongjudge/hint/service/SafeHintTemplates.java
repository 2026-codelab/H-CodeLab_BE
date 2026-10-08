package com.project.handongjudge.hint.service;

import com.project.handongjudge.hint.entity.ErrorCategory;

import java.util.EnumMap;
import java.util.Map;

/**
 * 검증 게이트가 반복 실패했을 때 쓰는 안전 템플릿 (설계 문서 5장 "안전 템플릿 설계").
 * 카테고리마다 사람이 미리 작성·검수한 문구 하나씩 — LLM이 새로 생성하지 않고 그대로 꺼내 쓴다.
 */
public final class SafeHintTemplates {

    private static final Map<ErrorCategory, String> TEMPLATES = new EnumMap<>(ErrorCategory.class);

    static {
        TEMPLATES.put(ErrorCategory.UNDECLARED_VARIABLE, "선언되지 않았거나 이름이 잘못된 변수/함수가 있는지 컴파일러 메시지에 나온 줄을 다시 확인해보세요.");
        TEMPLATES.put(ErrorCategory.TYPE_MISMATCH, "연산이나 대입 과정에서 타입이 서로 맞지 않는 부분이 있는지 확인해보세요.");
        TEMPLATES.put(ErrorCategory.MISSING_RETURN, "모든 실행 경로(특히 조건문의 각 분기)에서 값을 return하고 있는지 확인해보세요.");
        TEMPLATES.put(ErrorCategory.SYNTAX_ERROR, "컴파일러가 가리키는 줄 주변의 괄호, 세미콜론이 짝이 맞는지 확인해보세요.");

        TEMPLATES.put(ErrorCategory.OFF_BY_ONE, "반복문의 시작·끝 조건과 배열의 유효 인덱스 범위를 다시 확인해보세요.");
        TEMPLATES.put(ErrorCategory.EDGE_CASE_MISSING, "입력이 비어있거나 최솟값·최댓값인 경우도 코드가 처리하고 있는지 확인해보세요.");
        TEMPLATES.put(ErrorCategory.OUTPUT_FORMAT_MISMATCH, "값 자체는 맞을 수 있습니다. 공백, 줄바꿈, 소수점 자릿수 등 출력 형식을 문제 설명과 다시 비교해보세요.");
        TEMPLATES.put(ErrorCategory.PARTIAL_LOGIC_ERROR, "일부 테스트케이스에서만 실패하고 있습니다. 실패하는 입력들에 공통된 조건이 있는지 다시 살펴보세요.");
        TEMPLATES.put(ErrorCategory.WRONG_APPROACH, "현재 접근 방식이 문제의 요구 조건을 만족하는지부터 다시 검토해보세요.");
        TEMPLATES.put(ErrorCategory.INEFFICIENT_ALGORITHM, "현재 알고리즘의 시간 복잡도를 계산해보고, 더 효율적인 방법이 있는지 검토해보세요.");

        TEMPLATES.put(ErrorCategory.INVALID_MEMORY_ACCESS, "배열이나 포인터가 유효한 범위 밖을 가리키고 있는지 확인해보세요.");
        TEMPLATES.put(ErrorCategory.DIVISION_BY_ZERO, "나누는 값이 0이 될 수 있는 경우가 있는지 입력 범위를 다시 확인해보세요.");
        TEMPLATES.put(ErrorCategory.STACK_OVERFLOW, "재귀 함수에 종료 조건이 있는지, 그 조건에 실제로 도달하는지 확인해보세요.");

        String escalate = "현재 자동 분석으로는 정확한 원인을 특정하기 어렵습니다. 실패한 테스트케이스의 입출력을 직접 비교해보거나, 조교/교수님께 질문해보세요.";
        TEMPLATES.put(ErrorCategory.UNCATEGORIZED_RUNTIME_ERROR, escalate);
        TEMPLATES.put(ErrorCategory.UNCERTAIN, escalate);
    }

    private SafeHintTemplates() {
    }

    public static String get(ErrorCategory category) {
        return TEMPLATES.getOrDefault(category, TEMPLATES.get(ErrorCategory.UNCERTAIN));
    }
}
