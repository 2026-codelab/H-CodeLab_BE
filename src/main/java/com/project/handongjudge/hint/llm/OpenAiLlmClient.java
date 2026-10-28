package com.project.handongjudge.hint.llm;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * OpenAI 호환 API(/chat/completions) 구현체. 연구실 로컬 LLM(vLLM)을 호출한다.
 * 서버나 모델을 바꿀 때는 application.yml의 llm.base-url/llm.api-key/llm.model만 바꾼다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiLlmClient implements LlmClient {

    private static final double DEFAULT_TEMPERATURE = 0.4;

    private final RestTemplate restTemplate;

    @Value("${llm.base-url}")
    private String baseUrl;

    @Value("${llm.api-key}")
    private String apiKey;

    @Value("${llm.model}")
    private String model;

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        return complete(systemPrompt, userPrompt, DEFAULT_TEMPERATURE);
    }

    @Override
    public String complete(String systemPrompt, String userPrompt, double temperature) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", temperature,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            JsonNode response = restTemplate.exchange(
                    baseUrl + "/chat/completions",
                    HttpMethod.POST,
                    request,
                    JsonNode.class
            ).getBody();

            if (response == null || !response.has("choices") || response.get("choices").isEmpty()) {
                throw new RuntimeException("LLM 응답에 choices가 없습니다: " + response);
            }

            return response.get("choices").get(0).path("message").path("content").asText("").trim();
        } catch (Exception e) {
            log.error("LLM 호출 실패 (model={}): {}", model, e.getMessage());
            throw new RuntimeException("LLM 호출에 실패했습니다.", e);
        }
    }
}
