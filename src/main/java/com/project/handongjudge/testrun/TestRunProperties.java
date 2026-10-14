package com.project.handongjudge.testrun;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 테스트하기 실행 경로 설정 (설계 문서 TESTRUN_EXECUTOR_DESIGN.md 3단계).
 * 모든 값에 기본값이 있어, 운영 .env에 새 환경변수가 없어도 서버가 뜨고 기존 DOMjudge 경로로 동작한다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "testrun")
public class TestRunProperties {

    /** domjudge: 모든 분반 기존 경로 / runner: 모든 분반 경량 실행기. */
    private String executor = "domjudge";
    /** executor=domjudge여도 이 분반들은 경량 실행기를 쓴다 (점진 전환). */
    private List<Long> runnerSections = new ArrayList<>();
    /** testrun-service 주소. */
    private String serviceUrl = "http://testrun-service:8090";
    /** testrun-service 호출용 토큰. 비어 있으면 경량 실행기를 쓰지 않는다. */
    private String serviceToken = "";
    /** runner가 내부 테스트케이스 API를 부를 때 쓰는 토큰. 비어 있으면 내부 API는 모두 거부한다. */
    private String internalToken = "";
    private int connectTimeoutMs = 1000;
    private int readTimeoutMs = 2000;
    /** SSE 연결을 기다리는 최대 시간. 큐 대기 상한(60초) + 실행 제한(30초)보다 짧으면 결과를 못 받을 수 있다. */
    private long streamTimeoutMs = 95_000L;

    public boolean useRunner(Long sectionId) {
        if (!StringUtils.hasText(serviceToken)) {
            return false;
        }
        return "runner".equalsIgnoreCase(executor) || (sectionId != null && runnerSections.contains(sectionId));
    }
}
