package com.project.handongjudge.run.controller;

import com.project.handongjudge.run.dto.RunRequestDTO;
import com.project.handongjudge.run.dto.RunSubmitResponseDTO;
import com.project.handongjudge.run.service.RunService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * "코딩 실습" — 과제/문제와 무관하게 코드만 작성해서 실행 결과(표준출력/표준에러/컴파일에러)를 확인하는 기능.
 * 정답 채점(AC/WA)은 하지 않는다.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/run")
public class RunController {

    private final RunService runService;

    /**
     * 실행 요청: DOMjudge에 제출만 하고 sessionKey를 즉시 반환.
     * 반환된 sessionKey(+domjudgeProblemId)로 GET /stream/{sessionKey} SSE 연결 후 결과 수신.
     */
    @PostMapping("/submit")
    public ResponseEntity<RunSubmitResponseDTO> submit(
            Authentication authentication,
            @RequestBody RunRequestDTO request) {
        return ResponseEntity.ok(runService.submitRun(authentication, request));
    }

    /**
     * 실행 결과 SSE 스트리밍.
     * 이벤트 타입:
     *   output — { output, outputError }
     *   ce     — { output_compile? }
     *   error  — { message }
     */
    @GetMapping(value = "/stream/{sessionKey}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            Authentication authentication,
            @PathVariable String sessionKey,
            @RequestParam String domjudgeProblemId) {
        return runService.createRunStream(authentication, sessionKey, domjudgeProblemId);
    }
}
