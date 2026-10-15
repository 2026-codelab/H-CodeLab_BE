package com.project.handongjudge.submission.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 테스트하기 전용 SSE emitter 생명주기 관리.
 * key = DOMjudge submissionId (String) — DB에 저장하지 않는 일회성 테스트 제출에 사용.
 */
@Service
public class TestOutputSseService {

    private static final Logger log = LoggerFactory.getLogger(TestOutputSseService.class);
    private static final long SSE_EMITTER_TIMEOUT_MS = 70_000L;

    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // -------------------------------------------------------------------------
    // Emitter 관리
    // -------------------------------------------------------------------------

    public SseEmitter register(String sessionKey) {
        return register(sessionKey, SSE_EMITTER_TIMEOUT_MS);
    }

    public SseEmitter register(String sessionKey, long timeoutMs) {
        SseEmitter emitter = new SseEmitter(timeoutMs);

        // 콜백은 자기 자신일 때만 지운다. 같은 키로 다시 연결했을 때 이전 연결의 종료가 새 연결을 지우지 않게.
        emitter.onTimeout(() -> {
            log.debug("[TEST-SSE] timeout sessionKey={}", sessionKey);
            emitters.remove(sessionKey, emitter);
        });
        emitter.onCompletion(() -> {
            log.debug("[TEST-SSE] completed sessionKey={}", sessionKey);
            emitters.remove(sessionKey, emitter);
        });
        emitter.onError(e -> {
            log.debug("[TEST-SSE] error sessionKey={}: {}", sessionKey, e.getMessage());
            emitters.remove(sessionKey, emitter);
        });

        SseEmitter previous = emitters.put(sessionKey, emitter);
        if (previous != null) {
            // 같은 키로 다시 연결하면 이전 연결은 닫는다.
            try {
                previous.complete();
            } catch (Exception e) {
                log.debug("[TEST-SSE] previous emitter complete failed sessionKey={}: {}", sessionKey, e.getMessage());
            }
        }
        log.debug("[TEST-SSE] registered sessionKey={} total={}", sessionKey, emitters.size());
        return emitter;
    }

    /** 이 키로 열린 연결이 있는지. 테스트하기 실행기 이벤트 중계에서 끊긴 연결을 정리할 때 쓴다. */
    public boolean isActive(String sessionKey) {
        return emitters.containsKey(sessionKey);
    }

    public void complete(String sessionKey) {
        SseEmitter emitter = emitters.remove(sessionKey);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.debug("[TEST-SSE] complete() failed sessionKey={}: {}", sessionKey, e.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // 이벤트 전송 (output 상세 포함)
    // -------------------------------------------------------------------------

    /**
     * testcase: { index, result, output, outputError, outputDiff, testcaseInput, expectedOutput, runtime, memoryUsed }
     */
    public void sendTestcase(String sessionKey, int index, String result,
                             String output, String outputError, String outputDiff,
                             String testcaseInput, String expectedOutput,
                             int runtime, int memoryUsed) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("index", index);
        data.put("result", result);
        data.put("output", output);
        data.put("outputError", outputError);
        data.put("outputDiff", outputDiff);
        data.put("testcaseInput", testcaseInput);
        data.put("expectedOutput", expectedOutput);
        data.put("runtime", runtime);
        data.put("memoryUsed", memoryUsed);
        send(sessionKey, "testcase", data);
    }

    /** total: { count } — 채점 시작 전 전체 테스트케이스 수 알림 */
    public void sendTotal(String sessionKey, int count) {
        send(sessionKey, "total", Map.of("count", count));
    }

    /** complete: { result } */
    public void sendComplete(String sessionKey, String result) {
        send(sessionKey, "complete", Map.of("result", result));
    }

    /** ce: { result: "CE", output_compile?: string } */
    public void sendCe(String sessionKey, String outputCompile) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("result", "CE");
        if (outputCompile != null && !outputCompile.isEmpty()) {
            data.put("output_compile", outputCompile);
        }
        send(sessionKey, "ce", data);
    }

    /** error: { message } */
    public void sendError(String sessionKey, String message) {
        send(sessionKey, "error", Map.of("message", message));
    }

    /**
     * 테스트하기 실행기(testrun-runner)가 Redis에 기록한 이벤트를 변환 없이 그대로 보낸다.
     * 이벤트 이름과 data(JSON)는 위 send* 메서드와 같은 계약이다.
     */
    public void sendRaw(String sessionKey, String eventName, String jsonData) {
        SseEmitter emitter = emitters.get(sessionKey);
        if (emitter == null) {
            log.debug("[TEST-SSE] no emitter sessionKey={} event={}", sessionKey, eventName);
            return;
        }
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(jsonData, MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            log.warn("[TEST-SSE] send failed event={} sessionKey={}: {}", eventName, sessionKey, e.getMessage());
            emitters.remove(sessionKey, emitter);
        }
    }

    // -------------------------------------------------------------------------
    // 내부 유틸
    // -------------------------------------------------------------------------

    private void send(String sessionKey, String eventName, Object data) {
        SseEmitter emitter = emitters.get(sessionKey);
        if (emitter == null) {
            log.debug("[TEST-SSE] no emitter sessionKey={} event={}", sessionKey, eventName);
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(data);
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(json, MediaType.APPLICATION_JSON));
            log.debug("[TEST-SSE] sent event={} sessionKey={}", eventName, sessionKey);
        } catch (Exception e) {
            log.warn("[TEST-SSE] send failed event={} sessionKey={}: {}", eventName, sessionKey, e.getMessage());
            emitters.remove(sessionKey, emitter);
        }
    }
}
