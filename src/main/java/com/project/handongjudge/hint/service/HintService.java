package com.project.handongjudge.hint.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.project.handongjudge.common.exception.CustomException;
import com.project.handongjudge.hint.dto.HintResponseDTO;
import com.project.handongjudge.hint.entity.HintLog;
import com.project.handongjudge.hint.entity.HintTier;
import com.project.handongjudge.hint.entity.HintUsage;
import com.project.handongjudge.hint.entity.MistakeCategoryCount;
import com.project.handongjudge.hint.repository.HintLogRepository;
import com.project.handongjudge.hint.repository.HintUsageRepository;
import com.project.handongjudge.hint.repository.MistakeCategoryCountRepository;
import com.project.handongjudge.domjudge.service.DomjudgeService;
import com.project.handongjudge.submission.dto.SubmissionOutputResponseDTO;
import com.project.handongjudge.submission.entity.Submission;
import com.project.handongjudge.submission.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 힌트 요청 전체 흐름(설계 문서 2장)을 조합하는 오케스트레이터.
 * ①분류 → ②생성(이력 반영) → ③검증 게이트(최대 3회 재시도) → 표시+로깅+사용횟수 차감.
 * 외부 호출(DOMjudge, LLM)이 섞여 있어 전체를 한 DB 트랜잭션으로 묶지 않고,
 * 각 저장은 그때그때 커밋한다(OSIV가 활성화되어 있어 지연 로딩은 요청 범위 내에서 안전).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HintService {

    private static final int MAX_HINT_USES_PER_PROBLEM = 3;
    private static final int MAX_GATE_RETRIES = 3;

    private final SubmissionRepository submissionRepository;
    private final DomjudgeService domjudgeService;
    private final ErrorClassifier errorClassifier;
    private final HintGenerator hintGenerator;
    private final HintValidator hintValidator;
    private final HintUsageRepository hintUsageRepository;
    private final MistakeCategoryCountRepository mistakeCategoryCountRepository;
    private final HintLogRepository hintLogRepository;

    public HintResponseDTO requestHint(Long userId, Long submissionDbId) {
        Submission submission = submissionRepository.findById(submissionDbId)
                .orElseThrow(() -> new CustomException("제출을 찾을 수 없습니다.", "SUBMISSION_NOT_FOUND"));

        if (submission.getUser() == null || !submission.getUser().getId().equals(userId)) {
            throw new CustomException("본인의 제출에만 힌트를 요청할 수 있습니다.", "FORBIDDEN");
        }
        if ("AC".equals(submission.getResult())) {
            throw new CustomException("이미 정답으로 채점된 제출입니다.", "ALREADY_ACCEPTED");
        }

        HintUsage usage = hintUsageRepository
                .findByUser_IdAndProblem_Id(userId, submission.getProblem().getId())
                .orElseGet(() -> HintUsage.builder()
                        .user(submission.getUser())
                        .problem(submission.getProblem())
                        .usedCount(0)
                        .build());

        if (usage.getUsedCount() >= MAX_HINT_USES_PER_PROBLEM) {
            throw new CustomException("이 문제에서 힌트를 모두 사용했습니다(최대 " + MAX_HINT_USES_PER_PROBLEM + "회).", "HINT_LIMIT_EXCEEDED");
        }

        SubmissionOutputResponseDTO output = fetchOutput(submission);
        if (output == null) {
            throw new CustomException("아직 채점이 끝나지 않았습니다. 잠시 후 다시 시도해주세요.", "JUDGEMENT_NOT_READY");
        }

        ClassificationResult classification = errorClassifier.classify(submission.getResult(), output);
        int repeatCount = incrementMistakeCount(submission, classification);
        HintTier tier = HintTier.fromRepeatCount(repeatCount);

        GenerationOutcome outcome = generateWithGate(submission, classification, tier, repeatCount);

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

    private SubmissionOutputResponseDTO fetchOutput(Submission submission) {
        try {
            return domjudgeService.getResultOutput(
                    submission.getSection().getId().toString(), submission.getSubmissionId());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("채점 결과를 가져오는 중 오류가 발생했습니다.", e);
        }
    }

    private int incrementMistakeCount(Submission submission, ClassificationResult classification) {
        MistakeCategoryCount mistakeCount = mistakeCategoryCountRepository
                .findByUser_IdAndCategory(submission.getUser().getId(), classification.getCategory())
                .orElseGet(() -> MistakeCategoryCount.builder()
                        .user(submission.getUser())
                        .category(classification.getCategory())
                        .count(0)
                        .build());
        mistakeCount.setCount(mistakeCount.getCount() + 1);
        mistakeCategoryCountRepository.save(mistakeCount);
        return mistakeCount.getCount();
    }

    private GenerationOutcome generateWithGate(Submission submission, ClassificationResult classification,
                                                HintTier tier, int repeatCount) {
        for (int attempt = 1; attempt <= MAX_GATE_RETRIES; attempt++) {
            String candidate = hintGenerator.generate(
                    submission.getProblem().getDescription(), classification, tier, repeatCount);
            GateResult gate = hintValidator.validate(candidate, classification);
            boolean isLastAttempt = attempt == MAX_GATE_RETRIES;

            hintLogRepository.save(HintLog.builder()
                    .submission(submission)
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

        log.warn("검증 게이트 {}회 연속 실패, 안전 템플릿으로 대체: submissionId={}, category={}",
                MAX_GATE_RETRIES, submission.getId(), classification.getCategory());
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
