package com.project.handongjudge.hint.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.handongjudge.hint.entity.ErrorCategory;
import com.project.handongjudge.hint.dto.TestcaseResultDTO;
import com.project.handongjudge.hint.llm.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * ① 오류 유형 분류 (설계 문서 3장). "테스트하기" 한 번의 실행에서 실패한 테스트케이스 전체를
 * 입력으로 받아, 통합 힌트를 만들기 위한 대표 카테고리 + localize된 요약을 만든다.
 * DOMjudge가 이미 테스트케이스별로 세분화된 판정을 주기 때문에, presentation-error/timelimit/
 * memory-limit은 추측 없이 바로 분류된다. 애매한 경우(wrong-answer 세부 원인, run-error 신호
 * 종류, 컴파일 메시지)만 학생 코드를 함께 주고 보조 LLM으로 분류한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErrorClassifier {

    private static final int SUMMARY_FIELD_MAX_LEN = 300;
    private static final int COMPILE_SUMMARY_MAX_LEN = 1000;
    private static final int CODE_MAX_LEN = 4000;

    /** 여러 결과가 섞여 있을 때(흔하지 않지만 가능) 대표로 삼을 우선순위. */
    private static final List<String> RESULT_PRIORITY = List.of(
            "run-error", "timelimit", "memory-limit", "presentation-error", "wrong-answer", "no-output"
    );

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public ClassificationResult classifyCompileError(String code, String outputCompile) {
        String text = outputCompile == null ? "" : outputCompile;

        if (containsAny(text, "cannot find symbol", "undefined reference")) {
            return new ClassificationResult(ErrorCategory.UNDECLARED_VARIABLE, truncateCompile(text), true);
        }
        if (containsAny(text, "incompatible types", "cannot convert")) {
            return new ClassificationResult(ErrorCategory.TYPE_MISMATCH, truncateCompile(text), true);
        }
        if (text.contains("missing return statement")) {
            return new ClassificationResult(ErrorCategory.MISSING_RETURN, truncateCompile(text), true);
        }
        if (containsAny(text, "' expected", "\" expected")) {
            return new ClassificationResult(ErrorCategory.SYNTAX_ERROR, truncateCompile(text), true);
        }

        return askLlmToClassify(
                code,
                "컴파일러 메시지:\n" + truncateCompile(text),
                List.of(ErrorCategory.UNDECLARED_VARIABLE, ErrorCategory.TYPE_MISMATCH,
                        ErrorCategory.MISSING_RETURN, ErrorCategory.SYNTAX_ERROR, ErrorCategory.UNCERTAIN)
        );
    }

    public ClassificationResult classify(String code, List<TestcaseResultDTO> testcases) {
        if (testcases == null || testcases.isEmpty()) {
            return new ClassificationResult(ErrorCategory.UNCERTAIN, "실패한 테스트케이스 정보가 없습니다.", true);
        }

        TestcaseResultDTO representative = pickRepresentative(testcases);
        String result = nullToEmpty(representative.getResult());

        switch (result) {
            case "presentation-error":
                return new ClassificationResult(ErrorCategory.OUTPUT_FORMAT_MISMATCH, describeFailures(testcases), true);
            case "timelimit":
                return new ClassificationResult(ErrorCategory.INEFFICIENT_ALGORITHM,
                        "시간 제한 내에 결과를 출력하지 못해 시간 초과로 채점이 종료되었습니다.", true);
            case "memory-limit":
                return new ClassificationResult(ErrorCategory.MEMORY_LIMIT_EXCEEDED, "메모리 제한을 초과했습니다.", true);
            case "run-error":
                return classifyRuntimeError(code, representative, testcases);
            case "wrong-answer":
                return classifyWrongAnswer(code, testcases);
            case "no-output":
                return askLlmToClassify(
                        code,
                        "프로그램이 아무 출력도 내지 않았습니다.\n" + describeFailures(testcases),
                        List.of(ErrorCategory.WRONG_APPROACH, ErrorCategory.UNCATEGORIZED_RUNTIME_ERROR, ErrorCategory.UNCERTAIN)
                );
            default:
                return new ClassificationResult(ErrorCategory.UNCERTAIN,
                        "자동으로 원인을 분류할 수 없는 채점 결과입니다(" + result + ").", true);
        }
    }

    private TestcaseResultDTO pickRepresentative(List<TestcaseResultDTO> testcases) {
        for (String priority : RESULT_PRIORITY) {
            Optional<TestcaseResultDTO> match = testcases.stream()
                    .filter(t -> priority.equals(t.getResult()))
                    .findFirst();
            if (match.isPresent()) {
                return match.get();
            }
        }
        return testcases.get(0);
    }

    // ---------------------------------------------------------------- run-error

    private ClassificationResult classifyRuntimeError(String code, TestcaseResultDTO representative, List<TestcaseResultDTO> testcases) {
        String signal = nullToEmpty(representative.getOutputError());

        if (containsAny(signal, "segmentation", "sigsegv")) {
            return new ClassificationResult(ErrorCategory.INVALID_MEMORY_ACCESS, describeFailures(testcases), true);
        }
        if (containsAny(signal, "floating point exception", "sigfpe", "divide by zero", "division by zero")) {
            return new ClassificationResult(ErrorCategory.DIVISION_BY_ZERO, describeFailures(testcases), true);
        }
        if (containsAny(signal, "stack overflow", "sigabrt", "aborted")) {
            return new ClassificationResult(ErrorCategory.STACK_OVERFLOW, describeFailures(testcases), true);
        }

        return askLlmToClassify(
                code,
                "런타임 에러 정보:\n" + describeFailures(testcases),
                List.of(ErrorCategory.INVALID_MEMORY_ACCESS, ErrorCategory.DIVISION_BY_ZERO,
                        ErrorCategory.STACK_OVERFLOW, ErrorCategory.UNCATEGORIZED_RUNTIME_ERROR, ErrorCategory.UNCERTAIN)
        );
    }

    // ---------------------------------------------------------------- wrong-answer

    private ClassificationResult classifyWrongAnswer(String code, List<TestcaseResultDTO> testcases) {
        Integer constantDiff = findConstantNumericDiff(testcases);
        if (constantDiff != null && constantDiff != 0) {
            return new ClassificationResult(ErrorCategory.OFF_BY_ONE, describeFailures(testcases), true);
        }

        List<TestcaseResultDTO> wrongAnswerCases = testcases.stream()
                .filter(t -> "wrong-answer".equals(t.getResult()))
                .collect(Collectors.toList());
        if (wrongAnswerCases.size() == 1 && looksLikeEdgeCase(wrongAnswerCases.get(0).getTestcaseInput())) {
            return new ClassificationResult(ErrorCategory.EDGE_CASE_MISSING, describeFailures(testcases), true);
        }

        // presentation-error는 DOMjudge가 이미 별도로 구분해주므로 후보에서 제외
        return askLlmToClassify(
                code,
                "오답(wrong-answer) 테스트케이스입니다.\n" + describeFailures(testcases),
                List.of(ErrorCategory.OFF_BY_ONE, ErrorCategory.EDGE_CASE_MISSING,
                        ErrorCategory.WRONG_APPROACH, ErrorCategory.PARTIAL_LOGIC_ERROR, ErrorCategory.UNCERTAIN)
        );
    }

    /** wrong-answer 케이스들의 (실제-기대) 수치 차이가 전부 같은 0이 아닌 상수면 off-by-one으로 간주. */
    private static Integer findConstantNumericDiff(List<TestcaseResultDTO> testcases) {
        Integer diff = null;
        for (TestcaseResultDTO t : testcases) {
            if (!"wrong-answer".equals(t.getResult())) {
                continue;
            }
            Integer expected = parseSingleInt(t.getExpectedOutput());
            Integer actual = parseSingleInt(t.getOutput());
            if (expected == null || actual == null) {
                return null;
            }
            int d = actual - expected;
            if (diff == null) {
                diff = d;
            } else if (!diff.equals(d)) {
                return null;
            }
        }
        return diff;
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

    // ---------------------------------------------------------------- 공통 보조 LLM 분류

    private ClassificationResult askLlmToClassify(String code, String context, List<ErrorCategory> candidates) {
        String categoryNames = candidates.stream().map(Enum::name).collect(Collectors.joining(", "));
        String system = "너는 프로그래밍 과제 채점 결과를 분석해 오류 유형을 분류하는 보조 시스템이다. "
                + "반드시 다음 카테고리 중 하나만 선택해라: " + categoryNames + ". "
                + "확신이 없으면 UNCERTAIN을 선택해라. "
                + "다른 설명 없이 JSON 한 줄로만 답해라: {\"category\": \"<카테고리>\", \"summary\": \"<한국어 한두 문장으로 이 실패 원인을 요약>\"}";

        String user = withCode(code, context);
        String raw = llmClient.complete(system, user, 0.0);
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

    private static String describeFailures(List<TestcaseResultDTO> testcases) {
        String ranks = testcases.stream()
                .map(t -> "#" + (t.getTestcaseRank() == null ? "?" : t.getTestcaseRank()))
                .collect(Collectors.joining(", "));
        TestcaseResultDTO first = testcases.get(0);
        return String.format(
                "실패한 테스트케이스: %s (총 %d개)\n예시(#%s): 입력 `%s` → 기대 출력 `%s`, 실제 출력 `%s`%s",
                ranks,
                testcases.size(),
                first.getTestcaseRank() == null ? "?" : first.getTestcaseRank(),
                truncate(first.getTestcaseInput()),
                truncate(first.getExpectedOutput()),
                truncate(first.getOutput()),
                first.getOutputError() == null || first.getOutputError().isEmpty()
                        ? "" : (", 에러 출력: `" + truncate(first.getOutputError()) + "`")
        );
    }

    private static String withCode(String code, String context) {
        if (code == null || code.isBlank()) {
            return context;
        }
        return "학생 코드:\n```\n" + truncateCode(code) + "\n```\n\n" + context;
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

    /** 컴파일러 메시지는 줄 번호가 뒤쪽 오류에 있을 수 있어 일반 요약보다 더 넉넉하게 자른다. */
    private static String truncateCompile(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= COMPILE_SUMMARY_MAX_LEN ? text : text.substring(0, COMPILE_SUMMARY_MAX_LEN) + "...";
    }

    private static String truncateCode(String code) {
        return code.length() <= CODE_MAX_LEN ? code : code.substring(0, CODE_MAX_LEN) + "\n... (생략)";
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
