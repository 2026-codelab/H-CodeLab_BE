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

/**
 * 힌트 요청 전체 흐름(설계 문서 2장)을 조합하는 오케스트레이터.
 * "테스트하기"는 Submission을 DB에 저장하지 않으므로, 힌트는 problemId + 학생이 선택한
 * 테스트케이스 정보(HintRequestDTO)를 받아 바로 분류·생성·검증한다.
 * ①분류 → ②생성(이력 반영) → ③검증 게이트(최대 3회 재시도) → 표시+로깅+사용횟수 차감.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HintService {

    private static final int MAX_HINT_USES_PER_PROBLEM = 3;
    private static final int MAX_GATE_RETRIES = 3;

    private final UserRepository userRepository;
    private final ProblemRepository problemRepository;
    private final ErrorClassifier errorClassifier;
    private final HintGenerator hintGenerator;
    private final HintValidator hintValidator;
    private final HintUsageRepository hintUsageRepository;
    private final MistakeCategoryCountRepository mistakeCategoryCountRepository;
    private final HintLogRepository hintLogRepository;

    /** 힌트를 소모하지 않고 현재 남은 사용 횟수만 조회 (버튼 초기 표시/새로고침용). */
    public HintUsageResponseDTO getRemainingUses(Long userId, Long problemId) {
        int usedCount = hintUsageRepository
                .findByUser_IdAndProblem_Id(userId, problemId)
                .map(HintUsage::getUsedCount)
                .orElse(0);

        return HintUsageResponseDTO.builder()
                .remainingUses(MAX_HINT_USES_PER_PROBLEM - usedCount)
                .maxUses(MAX_HINT_USES_PER_PROBLEM)
                .build();
    }

    public HintResponseDTO requestHint(Long userId, Long problemId, HintRequestDTO request) {
        if ("correct".equals(request.getResult())) {
            throw new CustomException("정답 테스트케이스에는 힌트가 필요 없습니다.", "ALREADY_CORRECT");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다.", "USER_NOT_FOUND"));
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new CustomException("문제를 찾을 수 없습니다.", "PROBLEM_NOT_FOUND"));

        HintUsage usage = hintUsageRepository
                .findByUser_IdAndProblem_Id(userId, problemId)
                .orElseGet(() -> HintUsage.builder().user(user).problem(problem).usedCount(0).build());

        if (usage.getUsedCount() >= MAX_HINT_USES_PER_PROBLEM) {
            throw new CustomException("이 문제에서 힌트를 모두 사용했습니다(최대 " + MAX_HINT_USES_PER_PROBLEM + "회).", "HINT_LIMIT_EXCEEDED");
        }

        ClassificationResult classification = errorClassifier.classify(request);
        int repeatCount = incrementMistakeCount(user, classification);
        HintTier tier = HintTier.fromRepeatCount(repeatCount);

        GenerationOutcome outcome = generateWithGate(problem, request.getTestcaseRank(), classification, tier, repeatCount);

        usage.setUsedCount(usage.getUsedCount() + 1);
        hintUsageRepository.save(usage);

        return HintResponseDTO.builder()
                .hint(outcome.hint)
                .category(classification.getCategory().name())
                .tier(tier.name())
                .remainingUses(MAX_HINT_USES_PER_PROBLEM - usage.getUsedCount())
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

    private GenerationOutcome generateWithGate(Problem problem, Integer testcaseRank, ClassificationResult classification,
                                                HintTier tier, int repeatCount) {
        for (int attempt = 1; attempt <= MAX_GATE_RETRIES; attempt++) {
            String candidate = hintGenerator.generate(problem.getDescription(), classification, tier, repeatCount);
            GateResult gate = hintValidator.validate(candidate, classification);
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
