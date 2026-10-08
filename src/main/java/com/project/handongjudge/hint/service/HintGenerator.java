package com.project.handongjudge.hint.service;

import com.project.handongjudge.hint.entity.HintTier;
import com.project.handongjudge.hint.llm.LlmClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** ② 힌트 생성. 설계 문서 4장: 반복 횟수에 따라 유도강도(소크라테스식/중간/직접형)를 바꾸고, 2회 이상이면 반복 사실을 힌트에 명시한다. */
@Component
@RequiredArgsConstructor
public class HintGenerator {

    private static final double GENERATION_TEMPERATURE = 0.5;

    private final LlmClient llmClient;

    public String generate(String problemDescription, ClassificationResult classification, HintTier tier, int repeatCount) {
        String system = buildSystemPrompt(tier, repeatCount);
        String user = buildUserPrompt(problemDescription, classification);
        return llmClient.complete(system, user, GENERATION_TEMPERATURE);
    }

    private String buildSystemPrompt(HintTier tier, int repeatCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("너는 프로그래밍 수업에서 학생에게 힌트를 주는 조교다. ")
          .append("절대 정답 코드나 수정된 코드 줄을 그대로 적지 마라. ");

        switch (tier) {
            case SOCRATIC:
                sb.append("원인을 직접 말하지 말고, 학생이 스스로 찾도록 질문 형태로만 유도해라.");
                break;
            case MEDIUM:
                sb.append("원인이 있는 범위를 좁혀서 알려주되 정확한 수정 방법까지는 알려주지 마라. ")
                  .append("이 학생이 같은 유형을 지난번에도 틀렸다는 사실을 문장 앞부분에 먼저 언급해라.");
                break;
            case DIRECT:
            default:
                sb.append("원인을 구체적으로 지적해라. 다만 수정된 코드 자체는 적지 마라. ")
                  .append("이 학생이 같은 유형의 실수를 이번까지 ").append(repeatCount).append("번째로 반복하고 있다는 사실을 ")
                  .append("문장 맨 앞에 명시해라.");
                break;
        }
        sb.append(" 한국어로, 2~3문장 이내로 답해라.");
        return sb.toString();
    }

    private String buildUserPrompt(String problemDescription, ClassificationResult classification) {
        return "문제 설명:\n" + problemDescription + "\n\n"
                + "오류 유형: " + classification.getCategory() + "\n"
                + "실패 지점 요약: " + classification.getLocalizedSummary();
    }
}
