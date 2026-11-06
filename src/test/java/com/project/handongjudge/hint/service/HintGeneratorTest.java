package com.project.handongjudge.hint.service;

import com.project.handongjudge.hint.entity.ErrorCategory;
import com.project.handongjudge.hint.entity.HintTier;
import com.project.handongjudge.hint.llm.LlmClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 컴파일 에러 힌트가 (1) 줄 번호를 짚어달라고 요청하지 않고, (2) LLM이 한자를 섞어 응답해도
 * 최종 결과에서 모두 제거되는지 검증 (이슈 #41).
 */
class HintGeneratorTest {

    @Test
    void compileErrorPrompt_doesNotAskForLineNumber() {
        LlmClient llmClient = mock(LlmClient.class);
        when(llmClient.complete(anyString(), anyString(), anyDouble())).thenReturn("세미콜론이 빠진 것 같아요.");
        HintGenerator generator = new HintGenerator(llmClient);
        ClassificationResult classification = new ClassificationResult(ErrorCategory.SYNTAX_ERROR, "요약", true);

        generator.generate("int a = 3", "문제 설명", classification, HintTier.DIRECT, 1, true);

        ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(llmClient).complete(systemPrompt.capture(), anyString(), anyDouble());
        assertThat(systemPrompt.getValue()).doesNotContain("줄 번호(line number)가 있다면 몇 번째 줄에서 문제가 발생했는지 먼저 짚어주고");
        assertThat(systemPrompt.getValue()).contains("언급하지 마라");
    }

    @Test
    void generate_stripsHanjaFromLlmResponse() {
        LlmClient llmClient = mock(LlmClient.class);
        when(llmClient.complete(anyString(), anyString(), anyDouble()))
                .thenReturn("세미콜론(;) 누락 가능성이 높아요. 漢字 섞인 應答 文字입니다.");
        HintGenerator generator = new HintGenerator(llmClient);
        ClassificationResult classification = new ClassificationResult(ErrorCategory.SYNTAX_ERROR, "요약", true);

        String hint = generator.generate("int a = 3", "문제 설명", classification, HintTier.DIRECT, 1, true);

        assertThat(hint).doesNotMatch(".*[一-鿿].*");
        assertThat(hint).contains("세미콜론(;) 누락 가능성이 높아요");
    }
}
