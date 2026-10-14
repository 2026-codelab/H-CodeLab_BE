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
                            HintTier tier, int repeatCount, boolean isCompileError) {
        String system = isCompileError ? buildCompileErrorSystemPrompt() : buildSystemPrompt(tier, repeatCount);
        String user = buildUserPrompt(code, problemDescription, classification);
        return llmClient.complete(system, user, GENERATION_TEMPERATURE);
    }

    /**
     * 컴파일 에러는 단계별 유도(SOCRATIC/MEDIUM/DIRECT)가 아니라, 이미 학생에게 공개된 컴파일러
     * 메시지를 쉬운 말로 바로 풀어주는 설명이다. 줄 번호가 있으면 반드시 몇 번째 줄을 봐야 하는지
     * 짚어준다.
     */
    private String buildCompileErrorSystemPrompt() {
        return "너는 프로그래밍 수업에서 학생의 컴파일 에러 메시지를 쉬운 말로 설명해주는 조교다. "
                + "컴파일러 메시지는 학생에게 이미 전부 공개되어 있으니 원인을 숨기지 말고 명확하게 설명해라. "
                + "답변은 반드시 두 부분을 모두 포함해라: "
                + "(1) 컴파일러 메시지에 줄 번호(line number)가 있다면 몇 번째 줄에서 문제가 발생했는지 먼저 짚어주고, "
                + "(2) 그 줄에서 구체적으로 어떤 실수를 했을 가능성이 높은지(예: 세미콜론 누락, 괄호 짝 안 맞음, 변수명 오타, "
                + "타입 불일치 등) 컴파일러 메시지를 바탕으로 쉬운 말로 설명해라. 줄 번호만 알려주고 끝내지 마라, "
                + "반드시 그 줄에서 무엇을 확인해야 하는지까지 알려줘야 한다. 오류가 여러 개면 가장 먼저 발생한 오류를 "
                + "기준으로 설명해라. 학생이 이미 작성한 코드 줄을 그대로 보여주는 것은 괜찮다(이미 학생이 쓴 내용이므로). "
                + "하지만 정답 코드나 수정된 코드 줄 자체를 적거나, '여기에 `)`를 추가하세요', '세미콜론 대신 괄호를 넣으세요'처럼 "
                + "정확히 어떤 문자나 토큰을 추가·삭제·교체해야 하는지 짚어주는 정밀한 수정 지시는 하지 마라. 대신 "
                + "'괄호의 짝이 맞는지 확인해보세요'처럼 어떤 종류의 문제인지와 확인할 지점만 알려줘라. "
                + "한국어로, 3~5문장 이내로 답해라.";
    }

    private String buildSystemPrompt(HintTier tier, int repeatCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("너는 프로그래밍 수업에서 학생에게 힌트를 주는 조교다. ")
          .append("학생 코드를 참고해서 구체적으로 짚어주되, 절대 정답 코드나 수정된 코드 줄을 그대로 적지 마라. ")
          .append("'여기서 X를 Y로 바꾸세요'처럼 정확히 어떤 부분을 어떻게 고쳐야 하는지 문자·토큰 단위로 짚어주는 ")
          .append("정밀한 수정 지시도 하지 마라. ");

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
