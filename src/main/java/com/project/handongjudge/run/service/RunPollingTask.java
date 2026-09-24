package com.project.handongjudge.run.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.project.handongjudge.domjudge.service.DomjudgeService;
import com.project.handongjudge.submission.dto.SubmissionOutputResponseDTO;
import com.project.handongjudge.submission.entity.Output;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;

/**
 * "코딩 실습" 전용 비동기 폴링 태스크.
 * 테스트케이스 채점(AC/WA) 개념이 없는 단발성 실행이라, 결과가 오면(또는 CE면) 즉시 이벤트를 보내고
 * 뒤처리로 DOMjudge에 만들어둔 스크래치 문제를 삭제한다.
 */
@Component
@RequiredArgsConstructor
public class RunPollingTask {

    private static final Logger log = LoggerFactory.getLogger(RunPollingTask.class);
    private static final String SCRATCH_CID = "scratch";
    private static final long POLL_INITIAL_BACKOFF_MS = 500L;
    private static final long POLL_MAX_BACKOFF_MS = 3_000L;
    private static final long POLL_TIMEOUT_MS = 30_000L;

    private final DomjudgeService domjudgeService;
    private final RunSseService runSseService;

    @Async("sseExecutor")
    public void pollAndStream(String sessionKey, String domjudgeProblemId) {
        log.info("[RUN-SSE] polling started sessionKey={} problemId={}", sessionKey, domjudgeProblemId);

        long deadlineMs = System.currentTimeMillis() + POLL_TIMEOUT_MS;
        long backoffMs = POLL_INITIAL_BACKOFF_MS;

        while (System.currentTimeMillis() < deadlineMs) {
            try {
                SubmissionOutputResponseDTO result = domjudgeService.getResultOutput(SCRATCH_CID, sessionKey);

                if (result != null && result.getResult() != null && !result.getResult().isEmpty()) {
                    if ("CE".equals(result.getResult())) {
                        runSseService.sendCe(sessionKey, result.getOutputCompile());
                        finish(sessionKey, domjudgeProblemId);
                        return;
                    }

                    List<Output> outputList = result.getOutputList();
                    if (outputList != null && !outputList.isEmpty()) {
                        Output output = outputList.get(0);
                        if (output.getResult() != null && !output.getResult().isEmpty()) {
                            runSseService.sendOutput(sessionKey, output.getOutput(), output.getOutputError());
                            finish(sessionKey, domjudgeProblemId);
                            return;
                        }
                    }
                }
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() != 404) {
                    log.warn("[RUN-SSE] DOMjudge client error status={} sessionKey={}", e.getStatusCode(), sessionKey);
                }
            } catch (ResourceAccessException e) {
                log.warn("[RUN-SSE] DOMjudge network error sessionKey={}: {}", sessionKey, e.getMessage());
            } catch (JsonProcessingException e) {
                log.warn("[RUN-SSE] JSON parse error sessionKey={}: {}", sessionKey, e.getMessage());
                runSseService.sendError(sessionKey, "실행 결과를 해석하는 중 오류가 발생했습니다.");
                finish(sessionKey, domjudgeProblemId);
                return;
            } catch (Exception e) {
                log.warn("[RUN-SSE] unexpected error sessionKey={}: {}", sessionKey, e.getMessage());
            }

            long remaining = deadlineMs - System.currentTimeMillis();
            if (remaining <= 0) break;

            try {
                Thread.sleep(Math.min(backoffMs, remaining));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            backoffMs = Math.min(backoffMs * 2, POLL_MAX_BACKOFF_MS);
        }

        log.warn("[RUN-SSE] timeout sessionKey={}", sessionKey);
        runSseService.sendError(sessionKey, "실행 결과를 가져오는 데 시간이 초과되었습니다. 다시 시도해 주세요.");
        finish(sessionKey, domjudgeProblemId);
    }

    /**
     * SSE 종료 + 스크래치 문제 정리.
     * DOMjudge API에는 문제를 완전히 삭제하는 엔드포인트가 없어서, contest에서 unlink하는 것으로
     * 채점 대상에서 제외한다(다음 실행 요청이 이 contest의 누적된 다른 테스트케이스까지 같이 채점되는 걸 방지).
     * 실패해도 학생에게 영향은 없으므로 로그만 남긴다.
     */
    private void finish(String sessionKey, String domjudgeProblemId) {
        runSseService.complete(sessionKey);
        try {
            domjudgeService.removeProblemFromContest(SCRATCH_CID, domjudgeProblemId);
        } catch (Exception e) {
            log.warn("[RUN-SSE] 스크래치 문제 정리 실패 problemId={}: {}", domjudgeProblemId, e.getMessage());
        }
    }
}
