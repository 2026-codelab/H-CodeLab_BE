package com.project.handongjudge.hint.service;

import com.project.handongjudge.hint.llm.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * ③ 검증 게이트. 독립된 2개의 LLM 호출로 정확성/유출을 각각 판정한다 (설계 문서 5장).
 * 둘 다 "PASS"/"FAIL"만 답하도록 강제해 파싱을 단순하게 유지한다.
 *
 * 유출의 기준은 "학생 코드를 그대로 보여주는 것"이 아니라 "정확히 어떻게 고치라고 알려주는 것"이다
 * (학생이 이미 작성한 코드를 인용하는 건 괜찮음). 사람 재검수(sample.csv id=35)에서 실제로 유출된
 * 패턴 — 괄호/세미콜론 같은 토큰 하나를 지목해 "~가 필요합니다/추가하세요"라고 지시하는 것 — 은
 * LLM 판정과 별도로 규칙 기반으로도 한 번 더 걸러낸다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HintValidator {

    private static final double GATE_TEMPERATURE = 0.0;

    private static final Pattern PRECISE_EDIT_PATTERN = Pattern.compile(
            "[`\"']([^`\"'\\s]{1,3})[`\"'][^.!?\\n]{0,15}(추가|삽입|삭제|제거|필요합니다|필요해요|넣으세요|넣어야)"
            + "|(추가|삽입|삭제|제거|바꾸|교체)[^.!?\\n]{0,15}[`\"']([^`\"'\\s]{1,3})[`\"']"
    );

    private final LlmClient llmClient;

    public GateResult validate(String hintText, ClassificationResult classification, String code) {
        boolean accuracyPass = checkAccuracy(hintText, classification);
        boolean leakPass = !containsPreciseEditInstruction(hintText) && checkLeak(hintText, code);
        return new GateResult(accuracyPass, leakPass);
    }

    /** "`)`가 필요합니다", "대신 `)`를 추가하세요"처럼 토큰 단위 수정 지시를 정규식으로 선제 차단. */
    private boolean containsPreciseEditInstruction(String hintText) {
        if (hintText == null) {
            return false;
        }
        boolean matched = PRECISE_EDIT_PATTERN.matcher(hintText).find();
        if (matched) {
            log.debug("규칙 기반 유출 필터 FAIL 판정: {}", hintText);
        }
        return matched;
    }

    private boolean checkAccuracy(String hintText, ClassificationResult classification) {
        String system = "너는 힌트 검증 시스템이다. 아래 힌트가 가리키는 원인이 실제 오류 원인과 일치하면 "
                + "'PASS', 그렇지 않으면 'FAIL'이라고만 답해라. 다른 말은 절대 하지 마라.";
        String user = "오류 유형: " + classification.getCategory() + "\n"
                + "실패 지점 요약: " + classification.getLocalizedSummary() + "\n"
                + "힌트: " + hintText;
        return isPass(llmClient.complete(system, user, GATE_TEMPERATURE));
    }

    private boolean checkLeak(String hintText, String code) {
        String system = "너는 힌트 검증 시스템이다. 학생 코드와 힌트를 비교해서 판정해라. "
                + "힌트가 학생이 이미 작성한 코드를 그대로 인용하는 것은 괜찮다(이미 학생이 쓴 내용이므로 유출이 아니다). "
                + "하지만 힌트가 정답 코드, 수정된 코드 줄, 완전한 해결 방법을 직접 알려주거나, "
                + "'여기에 X를 추가/삭제/교체하세요', '~대신 ~를 넣으세요'처럼 정확히 어떤 문자나 토큰을 어떻게 바꿔야 "
                + "하는지 지시하면 'FAIL', 문제의 종류나 확인할 범위만 제시하면 'PASS'라고만 답해라. 다른 말은 절대 하지 마라.";
        String user = "학생 코드:\n```\n" + code + "\n```\n\n힌트: " + hintText;
        return isPass(llmClient.complete(system, user, GATE_TEMPERATURE));
    }

    private boolean isPass(String raw) {
        if (raw == null) {
            return false;
        }
        boolean pass = raw.trim().toUpperCase().startsWith("PASS");
        if (!pass) {
            log.debug("게이트 FAIL 판정: {}", raw);
        }
        return pass;
    }
}
