package com.project.handongjudge.hint.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.handongjudge.hint.entity.ErrorCategory;
import com.project.handongjudge.hint.llm.LlmClient;
import com.project.handongjudge.submission.entity.Output;
import com.project.handongjudge.submission.dto.SubmissionOutputResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * ① 오류 유형 분류. 설계 문서 3장의 규칙을 그대로 구현.
 * 규칙으로 깔끔히 맞는 경우 LLM 호출 없이 즉시 분류하고, 애매한 경우(PARTIAL_LOGIC_ERROR,
 * UNCATEGORIZED_RUNTIME_ERROR)만 보조 LLM 호출로 세분화한다.
 * 분류 자체가 어떻게 끝나든, 힌트 생성(②)은 항상 별도로 LLM을 호출한다 — 여기서 결정되는 건
 * "카테고리 + localize된 실패 지점 요약"뿐이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErrorClassifier {

    private static final int SUMMARY_FIELD_MAX_LEN = 300;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public ClassificationResult classify(String topLevelResult, SubmissionOutputResponseDTO output) {
        switch (topLevelResult) {
            case "CE":
                return classifyCompileError(output.getOutputCompile());
            case "TLE":
                return new ClassificationResult(
                        ErrorCategory.INEFFICIENT_ALGORITHM,
                        "시간 제한 내에 결과를 출력하지 못해 시간 초과로 채점이 종료되었습니다.",
                        true
                );
            case "RE":
                return classifyRuntimeError(output.getOutputList());
            case "WA":
                return classifyWrongAnswer(output.getOutputList());
            default:
                return new ClassificationResult(
                        ErrorCategory.UNCERTAIN,
                        "자동으로 원인을 분류할 수 없는 채점 결과입니다(" + topLevelResult + ").",
                        true
                );
        }
    }

    // ---------------------------------------------------------------- CE

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

    // ---------------------------------------------------------------- RE

    private ClassificationResult classifyRuntimeError(List<Output> outputs) {
        Output failing = findFirstFailing(outputs);
        String signal = (failing == null) ? "" : nullToEmpty(failing.getOutputError()) + " " + nullToEmpty(failing.getResult());

        if (containsAny(signal, "segmentation", "sigsegv")) {
            return new ClassificationResult(ErrorCategory.INVALID_MEMORY_ACCESS, describeFailure(failing), true);
        }
        if (containsAny(signal, "floating point exception", "sigfpe", "divide by zero", "division by zero")) {
            return new ClassificationResult(ErrorCategory.DIVISION_BY_ZERO, describeFailure(failing), true);
        }
        if (containsAny(signal, "stack overflow", "sigabrt", "aborted")) {
            return new ClassificationResult(ErrorCategory.STACK_OVERFLOW, describeFailure(failing), true);
        }

        return askLlmToClassify(
                "런타임 에러 정보:\n" + describeFailure(failing),
                List.of(ErrorCategory.INVALID_MEMORY_ACCESS, ErrorCategory.DIVISION_BY_ZERO,
                        ErrorCategory.STACK_OVERFLOW, ErrorCategory.UNCATEGORIZED_RUNTIME_ERROR, ErrorCategory.UNCERTAIN)
        );
    }

    // ---------------------------------------------------------------- WA

    private ClassificationResult classifyWrongAnswer(List<Output> outputs) {
        List<Output> failing = outputs.stream()
                .filter(o -> !isPassing(o))
                .collect(Collectors.toList());

        if (failing.isEmpty()) {
            return new ClassificationResult(ErrorCategory.WRONG_APPROACH, "실패한 테스트케이스를 찾지 못했습니다.", true);
        }

        Output representative = failing.get(0);

        if (isOutputFormatMismatch(representative)) {
            return new ClassificationResult(ErrorCategory.OUTPUT_FORMAT_MISMATCH, describeFailure(representative), true);
        }

        Integer constantDiff = findConstantNumericDiff(failing);
        if (constantDiff != null && constantDiff != 0) {
            return new ClassificationResult(ErrorCategory.OFF_BY_ONE, describeFailure(representative), true);
        }

        boolean allFail = failing.size() == outputs.size();
        if (allFail) {
            return new ClassificationResult(ErrorCategory.WRONG_APPROACH, describeFailure(representative), true);
        }

        boolean onlyOneEdgeLikeFailure = failing.size() == 1 && looksLikeEdgeCase(representative);
        if (onlyOneEdgeLikeFailure) {
            return new ClassificationResult(ErrorCategory.EDGE_CASE_MISSING, describeFailure(representative), true);
        }

        // 일부만 간헐적 실패 — 규칙으로 깔끔히 안 맞음, 보조 LLM 분류
        return askLlmToClassify(
                "일부 테스트케이스만 실패했습니다.\n" + describeFailure(representative),
                List.of(ErrorCategory.OFF_BY_ONE, ErrorCategory.EDGE_CASE_MISSING, ErrorCategory.OUTPUT_FORMAT_MISMATCH,
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

    private static boolean isPassing(Output output) {
        String result = nullToEmpty(output.getResult());
        return result.equalsIgnoreCase("AC") || result.equalsIgnoreCase("correct");
    }

    private static Output findFirstFailing(List<Output> outputs) {
        if (outputs == null) {
            return null;
        }
        return outputs.stream().filter(o -> !isPassing(o)).findFirst().orElse(null);
    }

    private static boolean isOutputFormatMismatch(Output output) {
        String expected = normalizeWhitespace(output.getTestcase_output());
        String actual = normalizeWhitespace(output.getOutput());
        if (expected == null || actual == null) {
            return false;
        }
        return expected.equals(actual) && !expected.equals(nullToEmpty(output.getTestcase_output()).trim());
    }

    /** 실패한 케이스들의 (실제-기대) 수치 차이가 전부 같은 0이 아닌 상수면 off-by-one으로 간주. */
    private static Integer findConstantNumericDiff(List<Output> failing) {
        Integer diff = null;
        for (Output o : failing) {
            Integer expected = parseSingleInt(o.getTestcase_output());
            Integer actual = parseSingleInt(o.getOutput());
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
    private static boolean looksLikeEdgeCase(Output output) {
        String input = nullToEmpty(output.getTestcase_input()).trim();
        return input.isEmpty() || input.length() <= 2;
    }

    private static String describeFailure(Output output) {
        if (output == null) {
            return "실패한 테스트케이스 정보를 가져오지 못했습니다.";
        }
        return String.format(
                "테스트케이스 %s: 입력 `%s` → 기대 출력 `%s`, 실제 출력 `%s`%s",
                nullToEmpty(output.getTestcase()),
                truncate(output.getTestcase_input()),
                truncate(output.getTestcase_output()),
                truncate(output.getOutput()),
                output.getOutputError() == null || output.getOutputError().isEmpty()
                        ? "" : (", 에러 출력: `" + truncate(output.getOutputError()) + "`")
        );
    }

    private static String normalizeWhitespace(String text) {
        return text == null ? null : text.trim().replaceAll("\\s+", " ");
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
