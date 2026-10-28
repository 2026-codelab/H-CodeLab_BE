package com.project.handongjudge.hint.llm;

/**
 * 힌트 파이프라인이 실제 모델 구현을 모르게 하기 위한 경계.
 * 지금은 연구실 로컬 LLM(vLLM)을 OpenAI 호환 API 형식으로 호출한다(OpenAiLlmClient). 모델이나 서버를 바꿀 때도
 * 이 인터페이스를 쓰는 분류/생성/게이트 코드는 전혀 수정하지 않아도 된다 — base-url/key/model 설정만 바뀜.
 */
public interface LlmClient {

    /** 기본 temperature로 호출. */
    String complete(String systemPrompt, String userPrompt);

    /** temperature를 직접 지정 — 게이트 판정처럼 결정적인 응답이 필요할 땐 낮게, 힌트 생성엔 약간 높게. */
    String complete(String systemPrompt, String userPrompt, double temperature);
}
