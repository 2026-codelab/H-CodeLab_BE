package com.project.handongjudge.testrun;

import com.project.handongjudge.problem.entity.Problem;
import com.project.handongjudge.testrun.dto.TestRunJobRequest;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 테스트하기를 경량 실행기로 보낼지 정하고, 보낸다.
 * 비어 있는 결과를 돌려주면 호출한 쪽은 기존 DOMjudge 경로로 처리한다 (폴백).
 */
@Slf4j
@Component
public class TestRunRouter {

    private static final double DEFAULT_TIME_LIMIT_SEC = 1.0;
    private static final int DEFAULT_MEMORY_LIMIT_MB = 256;

    private final TestRunProperties properties;
    private final TestRunServiceClient client;

    public TestRunRouter(TestRunProperties properties, TestRunServiceClient client) {
        this.properties = properties;
        this.client = client;
    }

    /** 테스트하기 문맥 (통계 구분용). */
    public static class Context {
        public final String type;
        public final Long id;
        public final boolean manager;

        public Context(String type, Long id, boolean manager) {
            this.type = type;
            this.id = id;
            this.manager = manager;
        }
    }

    /**
     * @return 접수된 작업 ID. 비어 있으면 DOMjudge 경로로 처리할 것.
     * @throws RuntimeException 학생에게 보여줄 오류 (이전 테스트 진행 중, 코드 크기 초과)
     */
    public Optional<String> submit(Long userId, Long sectionId, Problem problem, String langId, String code,
                                   Context context) {
        if (!properties.useRunner(sectionId)) {
            return Optional.empty();
        }
        byte[] zip = problem.getZipFileData();
        if (zip == null || zip.length == 0) {
            log.warn("[TESTRUN] problem {} has no zip; falling back to DOMjudge", problem.getId());
            return Optional.empty();
        }

        TestRunJobRequest request = TestRunJobRequest.builder()
                .userId(userId)
                .sectionId(sectionId)
                .problemId(problem.getId())
                .contextType(context.type)
                .contextId(context.id)
                .manager(context.manager)
                .langId(langId)
                .code(code)
                .timeLimitSec(problem.getTimeLimit() != null ? problem.getTimeLimit() : DEFAULT_TIME_LIMIT_SEC)
                .memoryLimitMb(problem.getMemoryLimit() != null ? problem.getMemoryLimit() : DEFAULT_MEMORY_LIMIT_MB)
                // DOMjudge용 zip의 validator_flags와 같은 규칙 (ProblemFileToDomjudgeConverter.createProblemYaml)
                .compareArgs(Boolean.TRUE.equals(problem.getStrictWhitespaceGrading()) ? "space_change_sensitive" : "")
                .problemHash(JudgingTestcases.hash(zip))
                .build();

        TestRunServiceClient.Result result = client.create(request);
        switch (result.getStatus()) {
            case ACCEPTED:
                log.info("[TESTRUN] job {} accepted user={} section={} problem={}",
                        result.getJobId(), userId, sectionId, problem.getId());
                return Optional.of(result.getJobId());
            case CONFLICT:
                throw new RuntimeException("이전 테스트가 아직 실행 중입니다. 끝난 뒤 다시 시도해 주세요.");
            case REJECTED:
                if ("CODE_TOO_LARGE".equals(result.getReason())) {
                    throw new RuntimeException("코드가 너무 깁니다. 64KB 이하로 줄여 주세요.");
                }
                log.warn("[TESTRUN] request rejected ({}); falling back to DOMjudge", result.getReason());
                return Optional.empty();
            default:
                log.warn("[TESTRUN] executor unavailable ({}); falling back to DOMjudge", result.getReason());
                return Optional.empty();
        }
    }
}
