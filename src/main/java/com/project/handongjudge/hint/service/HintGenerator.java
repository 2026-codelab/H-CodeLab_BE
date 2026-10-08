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
    private static final int CODE_MAX_LEN = 4000;

    private final LlmClient llmClient;

    public String generate(String code, String problemDescription, ClassificationResult classification,
                            HintTier tier, int repeatCount) {
        String system = buildSystemPrompt(tier, repeatCount);
        String user = buildUserPrompt(code, problemDescription, classification);
        return llmClient.complete(system, user, GENERATION_TEMPERATURE);
    }

    private String buildSystemPrompt(HintTier tier, int repeatCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("너는 프로그래밍 수업에서 학생에게 힌트를 주는 조교다. ")
          .append("학생 코드를 참고해서 구체적으로 짚어주되, 절대 정답 코드나 수정된 코드 줄을 그대로 적지 마라. ");

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
        sb.append(" 여러 테스트케이스가 실패했다면 개별 케이스를 나열하지 말고 공통 원인 하나로 통합해서 설명해라.");
        sb.append(" 한국어로, 2~4문장 이내로 답해라.");
        return sb.toString();
    }

    private String buildUserPrompt(String code, String problemDescription, ClassificationResult classification) {
        StringBuilder sb = new StringBuilder();
        if (code != null && !code.isBlank()) {
            sb.append("학생 코드:\n```\n").append(truncateCode(code)).append("\n```\n\n");
        }
        sb.append("문제 설명:\n").append(problemDescription).append("\n\n")
          .append("오류 유형: ").append(classification.getCategory()).append("\n")
          .append("실패 지점 요약: ").append(classification.getLocalizedSummary());
        return sb.toString();
    }

    private static String truncateCode(String code) {
        return code.length() <= CODE_MAX_LEN ? code : code.substring(0, CODE_MAX_LEN) + "\n... (생략)";
    }
}
