package com.project.handongjudge.hint.service;

import com.project.handongjudge.common.exception.CustomException;
import com.project.handongjudge.hint.dto.HintRequestDTO;
import com.project.handongjudge.hint.dto.HintResponseDTO;
import com.project.handongjudge.hint.dto.HintUsageResponseDTO;
import com.project.handongjudge.hint.entity.HintLog;
import com.project.handongjudge.hint.entity.HintTier;
import com.project.handongjudge.hint.entity.HintUsage;
import com.project.handongjudge.hint.entity.MistakeCategoryCount;
import com.project.handongjudge.hint.repository.HintLogRepository;
import com.project.handongjudge.hint.repository.HintUsageRepository;
import com.project.handongjudge.hint.repository.MistakeCategoryCountRepository;
import com.project.handongjudge.problem.entity.Problem;
import com.project.handongjudge.problem.repository.ProblemRepository;
import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 힌트 요청 전체 흐름(설계 문서 2장)을 조합하는 오케스트레이터.
 * "테스트하기"는 Submission을 DB에 저장하지 않으므로, problemId + HintRequestDTO(학생 코드 +
 * 컴파일 메시지 또는 실패한 테스트케이스 전체)를 받아 바로 분류·생성·검증한다.
 *
 * 힌트 사용 횟수는 제한하지 않는다(사용자 피드백 반영) — HintUsage.usedCount는 통계/이력 용도로만
 * 계속 기록한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HintService {

    private static final int MAX_GATE_RETRIES = 3;

    private final UserRepository userRepository;
    private final ProblemRepository problemRepository;
    private final ErrorClassifier errorClassifier;
    private final HintGenerator hintGenerator;
    private final HintValidator hintValidator;
    private final HintUsageRepository hintUsageRepository;
    private final MistakeCategoryCountRepository mistakeCategoryCountRepository;
    private final HintLogRepository hintLogRepository;

    /** 힌트를 소모하지 않고 지금까지 사용한 횟수만 조회 (버튼 초기 표시/새로고침용). */
    public HintUsageResponseDTO getRemainingUses(Long userId, Long problemId) {
        int usedCount = hintUsageRepository
                .findByUser_IdAndProblem_Id(userId, problemId)
                .map(HintUsage::getUsedCount)
                .orElse(0);

        return HintUsageResponseDTO.builder()
                .usedCount(usedCount)
                .build();
    }

    public HintResponseDTO requestHint(Long userId, Long problemId, HintRequestDTO request) {
        boolean isCompileError = StringUtils.hasText(request.getOutputCompile());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다.", "USER_NOT_FOUND"));
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new CustomException("문제를 찾을 수 없습니다.", "PROBLEM_NOT_FOUND"));

        HintUsage usage = null;
        if (!isCompileError) {
            if (request.getTestcases() == null || request.getTestcases().isEmpty()) {
                throw new CustomException("힌트를 생성할 실패한 테스트케이스 정보가 없습니다.", "NO_FAILING_TESTCASES");
            }
            usage = hintUsageRepository
                    .findByUser_IdAndProblem_Id(userId, problemId)
                    .orElseGet(() -> HintUsage.builder().user(user).problem(problem).usedCount(0).build());
        }

        ClassificationResult classification = isCompileError
                ? errorClassifier.classifyCompileError(request.getCode(), request.getOutputCompile())
                : errorClassifier.classify(request.getCode(), request.getTestcases());

        int repeatCount = incrementMistakeCount(user, classification);
        HintTier tier = HintTier.fromRepeatCount(repeatCount);

        Integer representativeTestcaseRank = (!isCompileError)
                ? request.getTestcases().get(0).getTestcaseRank()
                : null;

        GenerationOutcome outcome = generateWithGate(
                request.getCode(), problem, representativeTestcaseRank, classification, tier, repeatCount, isCompileError);

        int usedCount;
        if (isCompileError) {
            usedCount = hintUsageRepository
                    .findByUser_IdAndProblem_Id(userId, problemId)
                    .map(HintUsage::getUsedCount)
                    .orElse(0);
        } else {
            usage.setUsedCount(usage.getUsedCount() + 1);
            hintUsageRepository.save(usage);
            usedCount = usage.getUsedCount();
        }

        return HintResponseDTO.builder()
                .hint(outcome.hint)
                .category(classification.getCategory().name())
                .tier(tier.name())
                .usedCount(usedCount)
                .usedFallback(outcome.usedFallback)
                .build();
    }

    private int incrementMistakeCount(User user, ClassificationResult classification) {
        MistakeCategoryCount mistakeCount = mistakeCategoryCountRepository
                .findByUser_IdAndCategory(user.getId(), classification.getCategory())
                .orElseGet(() -> MistakeCategoryCount.builder()
                        .user(user)
                        .category(classification.getCategory())
                        .count(0)
                        .build());
        mistakeCount.setCount(mistakeCount.getCount() + 1);
        mistakeCategoryCountRepository.save(mistakeCount);
        return mistakeCount.getCount();
    }

    private GenerationOutcome generateWithGate(String code, Problem problem, Integer testcaseRank,
                                                ClassificationResult classification, HintTier tier, int repeatCount,
                                                boolean isCompileError) {
        for (int attempt = 1; attempt <= MAX_GATE_RETRIES; attempt++) {
            String candidate = hintGenerator.generate(code, problem.getDescription(), classification, tier, repeatCount, isCompileError);
            GateResult gate = hintValidator.validate(candidate, classification, code);
            boolean isLastAttempt = attempt == MAX_GATE_RETRIES;

            hintLogRepository.save(HintLog.builder()
                    .problem(problem)
                    .testcaseRank(testcaseRank)
                    .category(classification.getCategory())
                    .attemptNumber(attempt)
                    .accuracyGateResult(gate.isAccuracyPass() ? HintLog.GateVerdict.PASS : HintLog.GateVerdict.FAIL)
                    .leakGateResult(gate.isLeakPass() ? HintLog.GateVerdict.PASS : HintLog.GateVerdict.FAIL)
                    .finalResult(gate.allPass() ? HintLog.FinalResult.EXPOSED
                            : (isLastAttempt ? HintLog.FinalResult.FALLBACK : null))
                    .hintText(candidate)
                    .build());

            if (gate.allPass()) {
                return new GenerationOutcome(candidate, false);
            }
        }

        log.warn("검증 게이트 {}회 연속 실패, 안전 템플릿으로 대체: problemId={}, category={}",
                MAX_GATE_RETRIES, problem.getId(), classification.getCategory());
        return new GenerationOutcome(SafeHintTemplates.get(classification.getCategory()), true);
    }

    private static class GenerationOutcome {
        final String hint;
        final boolean usedFallback;

        GenerationOutcome(String hint, boolean usedFallback) {
            this.hint = hint;
            this.usedFallback = usedFallback;
        }
    }
}
