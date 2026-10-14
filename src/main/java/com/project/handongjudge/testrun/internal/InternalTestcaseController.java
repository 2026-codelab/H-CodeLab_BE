package com.project.handongjudge.testrun.internal;

import com.project.handongjudge.problem.entity.Problem;
import com.project.handongjudge.problem.repository.ProblemRepository;
import com.project.handongjudge.testrun.JudgingTestcases;
import com.project.handongjudge.testrun.dto.TestcaseBundleResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 경량 실행기(runner)가 처음 보는 문제의 테스트케이스를 받아 가는 내부 API.
 * runner는 응답의 hash로 캐시하므로 문제당 한 번만 호출된다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class InternalTestcaseController {

    private final ProblemRepository problemRepository;

    @GetMapping("/internal/testrun/problems/{problemId}/testcases")
    public ResponseEntity<TestcaseBundleResponse> testcases(@PathVariable Long problemId) throws IOException {
        Problem problem = problemRepository.findById(problemId).orElse(null);
        if (problem == null || problem.getZipFileData() == null || problem.getZipFileData().length == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        TestcaseBundleResponse bundle = JudgingTestcases.extract(problem.getZipFileData());
        log.info("[TESTRUN] served testcases problem={} hash={} sample={} secret={}",
                problemId, bundle.getHash(), bundle.getSample().size(), bundle.getSecret().size());
        return ResponseEntity.ok().eTag(bundle.getHash()).body(bundle);
    }
}
