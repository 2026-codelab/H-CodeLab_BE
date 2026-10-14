package com.project.handongjudge.testrun;

import com.project.handongjudge.submission.service.TestOutputSseService;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 경량 실행기 이벤트(testrun:events:{jobId})를 학생 SSE로 중계한다.
 * <p>
 * 인스턴스당 스레드 1개가 전용 Redis 연결로 열려 있는 모든 작업의 스트림을 XREAD 한 번에 읽는다.
 * (연결마다 블로킹 읽기를 걸면 공유 연결이 막히고 스레드가 연결 수만큼 필요하다.)
 * 이벤트는 Redis에 남아 있으므로 처음(0)부터 읽으면 runner가 먼저 끝났거나 다른 인스턴스로 연결돼도 결과를 잃지 않는다.
 */
@Slf4j
@Component
public class TestRunEventRelay implements SmartLifecycle {

    private static final long IDLE_WAIT_MS = 200;
    private static final long BLOCK_MS = 100;

    private final RedisProperties redisProperties;
    private final TestOutputSseService sseService;
    private final ConcurrentHashMap<String, Watch> watches = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Object signal = new Object();
    private RedisClient client;
    private Thread thread;

    public TestRunEventRelay(RedisProperties redisProperties, TestOutputSseService sseService) {
        this.redisProperties = redisProperties;
        this.sseService = sseService;
    }

    private static class Watch {
        volatile String lastId = "0";
        final long deadline;

        Watch(long deadline) {
            this.deadline = deadline;
        }
    }

    /** 이 작업의 이벤트를 SSE로 보내기 시작한다. emitter는 미리 등록돼 있어야 한다. */
    public void watch(String jobId, long timeoutMs) {
        watches.put(jobId, new Watch(System.currentTimeMillis() + timeoutMs));
        synchronized (signal) {
            signal.notifyAll();
        }
    }

    @Override
    public void start() {
        RedisURI.Builder uri = RedisURI.builder()
                .withHost(redisProperties.getHost())
                .withPort(redisProperties.getPort())
                .withTimeout(Duration.ofSeconds(10));
        if (StringUtils.hasText(redisProperties.getPassword())) {
            uri.withPassword(redisProperties.getPassword().toCharArray());
        }
        client = RedisClient.create(uri.build());
        running.set(true);
        thread = new Thread(this::run, "testrun-event-relay");
        thread.setDaemon(true);
        thread.start();
    }

    private void run() {
        StatefulRedisConnection<String, String> connection = null;
        while (running.get()) {
            try {
                if (watches.isEmpty()) {
                    synchronized (signal) {
                        signal.wait(IDLE_WAIT_MS);
                    }
                    continue;
                }
                if (connection == null || !connection.isOpen()) {
                    // Redis가 늦게 뜨거나 끊겨도 다시 연결한다 (기동 실패로 만들지 않음)
                    connection = client.connect();
                }
                pump(connection.sync());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("[TESTRUN] event relay error: {}", e.getMessage());
                if (connection != null) {
                    connection.close();
                    connection = null;
                }
                sleep(1000);
            }
        }
        if (connection != null) {
            connection.close();
        }
    }

    @SuppressWarnings("unchecked")
    private void pump(RedisCommands<String, String> redis) {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Watch> e : watches.entrySet()) {
            String jobId = e.getKey();
            if (!sseService.isActive(jobId)) {
                watches.remove(jobId);          // 학생이 연결을 끊었다
            } else if (now > e.getValue().deadline) {
                sseService.sendError(jobId, "테스트 결과를 받지 못했습니다. 다시 시도해 주세요.");
                sseService.complete(jobId);
                watches.remove(jobId);
            }
        }
        if (watches.isEmpty()) {
            return;
        }

        XReadArgs.StreamOffset<String>[] offsets = watches.entrySet().stream()
                .map(e -> XReadArgs.StreamOffset.from(TestRunKeys.events(e.getKey()), e.getValue().lastId))
                .toArray(XReadArgs.StreamOffset[]::new);
        List<StreamMessage<String, String>> messages =
                redis.xread(XReadArgs.Builder.block(BLOCK_MS).count(100), offsets);

        for (StreamMessage<String, String> message : messages) {
            String jobId = message.getStream().substring("testrun:events:".length());
            Watch watch = watches.get(jobId);
            if (watch == null) {
                continue;
            }
            watch.lastId = message.getId();
            String event = message.getBody().get(TestRunKeys.FIELD_EVENT);
            String data = message.getBody().get(TestRunKeys.FIELD_DATA);
            if (event == null || data == null) {
                continue;
            }
            sseService.sendRaw(jobId, event, data);
            if (TestRunKeys.isTerminal(event)) {
                log.info("[TESTRUN] job {} finished with {}", jobId, event);
                sseService.complete(jobId);
                watches.remove(jobId);
            }
        }
    }

    @Override
    public void stop() {
        running.set(false);
        synchronized (signal) {
            signal.notifyAll();
        }
        if (thread != null) {
            try {
                thread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (client != null) {
            client.shutdown();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
