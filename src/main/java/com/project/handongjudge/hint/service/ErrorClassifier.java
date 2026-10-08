package com.project.handongjudge.hint.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.handongjudge.hint.dto.HintRequestDTO;
import com.project.handongjudge.hint.entity.ErrorCategory;
import com.project.handongjudge.hint.llm.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * ① 오류 유형 분류 (설계 문서 3장). "테스트하기"에서 학생이 고른 테스트케이스 하나를 대상으로 분류한다.
 * DOMjudge가 이미 테스트케이스별로 세분화된 판정(wrong-answer/presentation-error/run-error/...)을
 * 주기 때문에, 이전 버전처럼 WA 전체를 diff 패턴으로 다시 추측할 필요가 줄었다 — 특히
 * presentation-error/timelimit/memory-limit은 DOMjudge 판정 자체가 곧 카테고리다.
 * 규칙으로 깔끔히 안 맞는 경우(wrong-answer의 세부 원인, run-error의 신호 종류)만 보조 LLM을 쓴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErrorClassifier {

    private static final int SUMMARY_FIELD_MAX_LEN = 300;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public ClassificationResult classify(HintRequestDTO request) {
        String result = request.getResult() == null ? "" : request.getResult();

        switch (result) {
            case "compiler-error":
                return classifyCompileError(request.getOutputCompile());
            case "presentation-error":
                return new ClassificationResult(ErrorCategory.OUTPUT_FORMAT_MISMATCH, describeFailure(request), true);
            case "timelimit":
                return new ClassificationResult(
                        ErrorCategory.INEFFICIENT_ALGORITHM,
                        "시간 제한 내에 결과를 출력하지 못해 시간 초과로 채점이 종료되었습니다.",
                        true
                );
            case "memory-limit":
                return new ClassificationResult(
                        ErrorCategory.MEMORY_LIMIT_EXCEEDED,
                        "메모리 제한을 초과했습니다.",
                        true
                );
            case "run-error":
                return classifyRuntimeError(request);
            case "wrong-answer":
                return classifyWrongAnswer(request);
            case "no-output":
                return askLlmToClassify(
                        "프로그램이 아무 출력도 내지 않았습니다.\n" + describeFailure(request),
                        List.of(ErrorCategory.WRONG_APPROACH, ErrorCategory.UNCATEGORIZED_RUNTIME_ERROR, ErrorCategory.UNCERTAIN)
                );
            default:
                return new ClassificationResult(
                        ErrorCategory.UNCERTAIN,
                        "자동으로 원인을 분류할 수 없는 채점 결과입니다(" + result + ").",
                        true
                );
        }
    }

    // ---------------------------------------------------------------- compiler-error

    private ClassificationResult classifyCompileError(String compileOutput) {
        String text = compileOutput == null ? "" : compileOutput;

        if (containsAny(text, "cannot find symbol", "undefined reference")) {
            return new ClassificationResult(ErrorCategory.UNDECLARED_VARIABLE, truncate(text), true);
        }
        if (containsAny(text, "incompatible types", "cannot convert")) {
            return new ClassificationResult(ErrorCategory.TYPE_MISMATCH, truncate(text), true);
        }
        if (text.contains("missing return statement")) {
            return new ClassificationResult(ErrorCategory.MISSING_RETURN, truncate(text), true);
        }
        if (containsAny(text, "' expected", "\" expected")) {
            return new ClassificationResult(ErrorCategory.SYNTAX_ERROR, truncate(text), true);
        }

        return askLlmToClassify(
                "컴파일러 메시지:\n" + truncate(text),
                List.of(ErrorCategory.UNDECLARED_VARIABLE, ErrorCategory.TYPE_MISMATCH,
                        ErrorCategory.MISSING_RETURN, ErrorCategory.SYNTAX_ERROR, ErrorCategory.UNCERTAIN)
        );
    }

    // ---------------------------------------------------------------- run-error

    private ClassificationResult classifyRuntimeError(HintRequestDTO request) {
        String signal = nullToEmpty(request.getOutputError());

        if (containsAny(signal, "segmentation", "sigsegv")) {
            return new ClassificationResult(ErrorCategory.INVALID_MEMORY_ACCESS, describeFailure(request), true);
        }
        if (containsAny(signal, "floating point exception", "sigfpe", "divide by zero", "division by zero")) {
            return new ClassificationResult(ErrorCategory.DIVISION_BY_ZERO, describeFailure(request), true);
        }
        if (containsAny(signal, "stack overflow", "sigabrt", "aborted")) {
            return new ClassificationResult(ErrorCategory.STACK_OVERFLOW, describeFailure(request), true);
        }

        return askLlmToClassify(
                "런타임 에러 정보:\n" + describeFailure(request),
                List.of(ErrorCategory.INVALID_MEMORY_ACCESS, ErrorCategory.DIVISION_BY_ZERO,
                        ErrorCategory.STACK_OVERFLOW, ErrorCategory.UNCATEGORIZED_RUNTIME_ERROR, ErrorCategory.UNCERTAIN)
        );
    }

    // ---------------------------------------------------------------- wrong-answer

    private ClassificationResult classifyWrongAnswer(HintRequestDTO request) {
        Integer diff = numericDiff(request.getExpectedOutput(), request.getOutput());
        if (diff != null && diff != 0) {
            return new ClassificationResult(ErrorCategory.OFF_BY_ONE, describeFailure(request), true);
        }

        if (looksLikeEdgeCase(request.getTestcaseInput())) {
            return new ClassificationResult(ErrorCategory.EDGE_CASE_MISSING, describeFailure(request), true);
        }

        // 규칙으로 깔끔히 안 맞음 — 보조 LLM 분류 (presentation-error는 DOMjudge가 이미 별도로 구분해주므로 후보에서 제외)
        return askLlmToClassify(
                "오답(wrong-answer) 테스트케이스입니다.\n" + describeFailure(request),
                List.of(ErrorCategory.OFF_BY_ONE, ErrorCategory.EDGE_CASE_MISSING,
                        ErrorCategory.WRONG_APPROACH, ErrorCategory.PARTIAL_LOGIC_ERROR, ErrorCategory.UNCERTAIN)
        );
    }

    // ---------------------------------------------------------------- 공통 보조 LLM 분류

    private ClassificationResult askLlmToClassify(String context, List<ErrorCategory> candidates) {
        String categoryNames = candidates.stream().map(Enum::name).collect(Collectors.joining(", "));
        String system = "너는 프로그래밍 과제 채점 결과를 분석해 오류 유형을 분류하는 보조 시스템이다. "
                + "반드시 다음 카테고리 중 하나만 선택해라: " + categoryNames + ". "
                + "확신이 없으면 UNCERTAIN을 선택해라. "
                + "다른 설명 없이 JSON 한 줄로만 답해라: {\"category\": \"<카테고리>\", \"summary\": \"<한국어 한두 문장으로 이 실패 원인을 요약>\"}";

        String raw = llmClient.complete(system, context, 0.0);
        try {
            JsonNode node = objectMapper.readTree(extractJson(raw));
            String categoryStr = node.path("category").asText(ErrorCategory.UNCERTAIN.name());
            ErrorCategory category = candidates.stream()
                    .filter(c -> c.name().equalsIgnoreCase(categoryStr))
                    .findFirst()
                    .orElse(ErrorCategory.UNCERTAIN);
            String summary = node.path("summary").asText("원인을 명확히 판단하기 어렵습니다.");
            return new ClassificationResult(category, summary, false);
        } catch (Exception e) {
            log.warn("분류 보조 LLM 응답 파싱 실패, UNCERTAIN으로 폴백: {}", raw, e);
            return new ClassificationResult(ErrorCategory.UNCERTAIN, "원인을 명확히 판단하기 어렵습니다.", false);
        }
    }

    // ---------------------------------------------------------------- 헬퍼

    /** 기대 출력/실제 출력이 둘 다 정수 하나뿐이면 그 차이를, 아니면 null을 반환. */
    private static Integer numericDiff(String expected, String actual) {
        Integer e = parseSingleInt(expected);
        Integer a = parseSingleInt(actual);
        if (e == null || a == null) {
            return null;
        }
        return a - e;
    }

    private static Integer parseSingleInt(String text) {
        if (text == null) {
            return null;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 입력이 비어있거나 매우 짧으면(경계값으로 추정) edge-case-missing 후보로 간주. */
    private static boolean looksLikeEdgeCase(String testcaseInput) {
        String input = nullToEmpty(testcaseInput).trim();
        return input.isEmpty() || input.length() <= 2;
    }

    private static String describeFailure(HintRequestDTO request) {
        return String.format(
                "테스트케이스 %s: 입력 `%s` → 기대 출력 `%s`, 실제 출력 `%s`%s",
                request.getTestcaseRank() == null ? "?" : request.getTestcaseRank(),
                truncate(request.getTestcaseInput()),
                truncate(request.getExpectedOutput()),
                truncate(request.getOutput()),
                request.getOutputError() == null || request.getOutputError().isEmpty()
                        ? "" : (", 에러 출력: `" + truncate(request.getOutputError()) + "`")
        );
    }

    private static boolean containsAny(String text, String... needles) {
        String lower = text.toLowerCase();
        for (String needle : needles) {
            if (lower.contains(needle.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= SUMMARY_FIELD_MAX_LEN ? text : text.substring(0, SUMMARY_FIELD_MAX_LEN) + "...";
    }

    /** LLM이 ```json ... ``` 코드블록으로 감싸서 답하는 경우를 대비해 JSON 부분만 추출. */
    private static String extractJson(String raw) {
        if (raw == null) {
            return "{}";
        }
        java.util.regex.Matcher m = Pattern.compile("\\{.*\\}", Pattern.DOTALL).matcher(raw);
        return m.find() ? m.group() : raw;
    }
}
