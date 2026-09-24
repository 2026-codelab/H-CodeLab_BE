package com.project.handongjudge.run.service;

import com.project.handongjudge.domjudge.service.DomjudgeService;
import com.project.handongjudge.problem.dto.ProblemFileParseResult;
import com.project.handongjudge.problem.dto.TestCaseDto;
import com.project.handongjudge.problem.util.ByteArrayMultipartFile;
import com.project.handongjudge.problem.util.ProblemFileToDomjudgeConverter;
import com.project.handongjudge.run.dto.RunRequestDTO;
import com.project.handongjudge.run.dto.RunSubmitResponseDTO;
import com.project.handongjudge.submission.util.CodeExtenstion;
import com.project.handongjudge.user.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

/**
 * "코딩 실습"(과제/문제와 무관하게 코드만 작성해 실행 결과를 보는 기능).
 * DOMjudge에는 여전히 "문제에 제출"하는 형태로만 채점을 시킬 수 있으므로, 요청마다 테스트케이스 1개짜리
 * 스크래치 문제를 만들었다 지운다. 여러 학생이 동시에 실행해도 서로 결과가 섞이지 않도록,
 * 문제를 공유하지 않고 매번 새로 만드는 방식을 쓴다 (자세한 이유는 팀 논의 참고).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RunService {

    /** DOMjudge에 1회성으로 만들어둔 스크래치 전용 contest의 externalid. */
    private static final String SCRATCH_CID = "scratch";
    private static final double TIME_LIMIT_SEC = 3.0;
    private static final int MEMORY_LIMIT = 256;

    private final DomjudgeService domjudgeService;
    private final EnrollmentRepository enrollmentRepository;
    private final RunSseService runSseService;
    private final RunPollingTask runPollingTask;

    public RunSubmitResponseDTO submitRun(Authentication authentication, RunRequestDTO request) {
        Long userId = Long.parseLong(authentication.getName());

        String teamId = enrollmentRepository.findTeamIdByUserIdAndSectionId(userId, request.getSectionId());
        if (teamId == null || teamId.isEmpty()) {
            throw new RuntimeException("현재 수업에 수강신청되어 있지 않습니다.");
        }

        String domjudgeProblemId = createScratchProblem(request.getStdin());
        domjudgeService.addProblemToContest(SCRATCH_CID, domjudgeProblemId);

        File codeFile = CodeExtenstion.StringToFile(request.getLanguage(), request.getCode());
        String domjudgeSubmissionId;
        try {
            domjudgeSubmissionId = domjudgeService.submitCode(
                    SCRATCH_CID, teamId, domjudgeProblemId, request.getLanguage(), codeFile);
        } finally {
            try {
                java.nio.file.Files.deleteIfExists(codeFile.toPath());
            } catch (IOException e) {
                log.warn("임시 코드 파일 삭제 실패: {}", e.getMessage());
            }
        }

        log.info("[RUN] submitted sessionKey={} problemId={} teamId={}",
                domjudgeSubmissionId, domjudgeProblemId, teamId);

        return RunSubmitResponseDTO.builder()
                .sessionKey(domjudgeSubmissionId)
                .domjudgeProblemId(domjudgeProblemId)
                .build();
    }

    public SseEmitter createRunStream(Authentication authentication, String sessionKey, String domjudgeProblemId) {
        Long.parseLong(authentication.getName()); // 인증 확인
        SseEmitter emitter = runSseService.register(sessionKey);
        runPollingTask.pollAndStream(sessionKey, domjudgeProblemId);
        return emitter;
    }

    /** stdin 하나만 들어있는 테스트케이스 1개짜리 DOMjudge 문제를 만들어 업로드한다. */
    private String createScratchProblem(String stdin) {
        TestCaseDto testcase = TestCaseDto.builder()
                .name("1")
                .input(stdin != null ? stdin : "")
                .output("") // 채점(정답 비교)을 안 하므로 기대 출력은 의미 없음
                .type("secret")
                .build();

        ProblemFileParseResult parseResult = ProblemFileParseResult.builder()
                .title("scratch")
                .description("")
                .timeLimit(TIME_LIMIT_SEC)
                .memoryLimit(MEMORY_LIMIT)
                .testcases(Collections.singletonList(testcase))
                .strictWhitespaceGrading(false)
                .build();

        try {
            byte[] zipBytes = ProblemFileToDomjudgeConverter.toDomjudgeZip(
                    parseResult, "scratch-" + UUID.randomUUID());
            MultipartFile zipFile = new ByteArrayMultipartFile(zipBytes, "scratch.zip");
            return domjudgeService.uploadProblemToDomjudge(zipFile);
        } catch (IOException e) {
            throw new RuntimeException("스크래치 문제 생성에 실패했습니다.", e);
        }
    }
}
