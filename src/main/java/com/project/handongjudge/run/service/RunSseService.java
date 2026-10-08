package com.project.handongjudge.run.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "코딩 실습"(코드 실행) 전용 SSE emitter 생명주기 관리.
 * key = DOMjudge submissionId (String). DB에 저장하지 않는 일회성 실행이라 TestOutputSseService와 별도로 둔다
 * (테스트케이스 채점 개념이 없어 이벤트가 훨씬 단순함).
 */
@Service
public class RunSseService {

    private static final Logger log = LoggerFactory.getLogger(RunSseService.class);
    private static final long SSE_EMITTER_TIMEOUT_MS = 70_000L;

    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SseEmitter register(String sessionKey) {
        SseEmitter emitter = new SseEmitter(SSE_EMITTER_TIMEOUT_MS);

        emitter.onTimeout(() -> {
            log.debug("[RUN-SSE] timeout sessionKey={}", sessionKey);
            emitters.remove(sessionKey);
        });
        emitter.onCompletion(() -> {
            log.debug("[RUN-SSE] completed sessionKey={}", sessionKey);
            emitters.remove(sessionKey);
        });
        emitter.onError(e -> {
            log.debug("[RUN-SSE] error sessionKey={}: {}", sessionKey, e.getMessage());
            emitters.remove(sessionKey);
        });

        emitters.put(sessionKey, emitter);
        log.debug("[RUN-SSE] registered sessionKey={} total={}", sessionKey, emitters.size());
        return emitter;
    }

    public void complete(String sessionKey) {
        SseEmitter emitter = emitters.remove(sessionKey);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.debug("[RUN-SSE] complete() failed sessionKey={}: {}", sessionKey, e.getMessage());
            }
        }
    }

    /** output: { output, outputError } — 실행 완료(AC/WA 여부는 의미 없으므로 무시) */
    public void sendOutput(String sessionKey, String output, String outputError) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("output", output);
        data.put("outputError", outputError);
        send(sessionKey, "output", data);
    }

    /** ce: { output_compile } — 컴파일 에러 */
    public void sendCe(String sessionKey, String outputCompile) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        if (outputCompile != null && !outputCompile.isEmpty()) {
            data.put("output_compile", outputCompile);
        }
        send(sessionKey, "ce", data);
    }

    /** error: { message } */
    public void sendError(String sessionKey, String message) {
        send(sessionKey, "error", Map.of("message", message));
    }

    private void send(String sessionKey, String eventName, Object data) {
        SseEmitter emitter = emitters.get(sessionKey);
        if (emitter == null) {
            log.debug("[RUN-SSE] no emitter sessionKey={} event={}", sessionKey, eventName);
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(data);
            emitter.send(SseEmitter.event().name(eventName).data(json, MediaType.APPLICATION_JSON));
            log.debug("[RUN-SSE] sent event={} sessionKey={}", eventName, sessionKey);
        } catch (Exception e) {
            log.warn("[RUN-SSE] send failed event={} sessionKey={}: {}", eventName, sessionKey, e.getMessage());
            emitters.remove(sessionKey);
        }
    }
}
