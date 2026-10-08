package com.project.handongjudge.hint.service;

import com.project.handongjudge.hint.llm.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ③ 검증 게이트. 독립된 2개의 LLM 호출로 정확성/유출을 각각 판정한다 (설계 문서 5장).
 * 둘 다 "PASS"/"FAIL"만 답하도록 강제해 파싱을 단순하게 유지한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HintValidator {

    private static final double GATE_TEMPERATURE = 0.0;

    private final LlmClient llmClient;

    public GateResult validate(String hintText, ClassificationResult classification) {
        boolean accuracyPass = checkAccuracy(hintText, classification);
        boolean leakPass = checkLeak(hintText);
        return new GateResult(accuracyPass, leakPass);
    }

    private boolean checkAccuracy(String hintText, ClassificationResult classification) {
        String system = "너는 힌트 검증 시스템이다. 아래 힌트가 가리키는 원인이 실제 오류 원인과 일치하면 "
                + "'PASS', 그렇지 않으면 'FAIL'이라고만 답해라. 다른 말은 절대 하지 마라.";
        String user = "오류 유형: " + classification.getCategory() + "\n"
                + "실패 지점 요약: " + classification.getLocalizedSummary() + "\n"
                + "힌트: " + hintText;
        return isPass(llmClient.complete(system, user, GATE_TEMPERATURE));
    }

    private boolean checkLeak(String hintText) {
        String system = "너는 힌트 검증 시스템이다. 아래 힌트가 정답 코드, 수정된 코드 줄, 또는 완전한 해결 방법을 "
                + "직접 알려주면 'FAIL', 방향이나 확인할 지점만 제시하면 'PASS'라고만 답해라. 다른 말은 절대 하지 마라.";
        String user = "힌트: " + hintText;
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
