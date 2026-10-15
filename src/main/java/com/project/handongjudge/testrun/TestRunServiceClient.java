package com.project.handongjudge.testrun;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.handongjudge.testrun.dto.TestRunJobRequest;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

/**
 * testrun-service 호출. 테스트하기 요청 경로에 있으므로 타임아웃을 짧게 두고, 실패하면 호출한 쪽이 DOMjudge로 폴백한다.
 */
@Slf4j
@Component
public class TestRunServiceClient {

    private final TestRunProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public TestRunServiceClient(TestRunProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        this.restTemplate = new RestTemplate(factory);
    }

    public Result create(TestRunJobRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Service-Token", properties.getServiceToken());
        String url = properties.getServiceUrl().replaceAll("/+$", "") + "/v1/jobs";
        try {
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, new HttpEntity<>(request, headers), JsonNode.class);
            JsonNode body = response.getBody();
            if (body == null || !body.hasNonNull("jobId")) {
                return Result.unavailable("EMPTY_RESPONSE");
            }
            return Result.accepted(body.get("jobId").asText());
        } catch (HttpStatusCodeException e) {
            String code = errorCode(e.getResponseBodyAsString());
            int status = e.getRawStatusCode();
            if (status == 409) {
                return new Result(Status.CONFLICT, null, code);
            }
            if (status == 400 || status == 413) {
                return new Result(Status.REJECTED, null, code);
            }
            // 401(토큰 설정 오류), 503(NO_RUNNER, QUEUE_FULL, ENQUEUE_FAILED), 5xx → 폴백
            return Result.unavailable(status + " " + code);
        } catch (Exception e) {
            // 연결 실패, 타임아웃 → 폴백
            return Result.unavailable(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private String errorCode(String body) {
        try {
            return objectMapper.readTree(body).path("error").asText("");
        } catch (Exception e) {
            return "";
        }
    }

    public enum Status { ACCEPTED, CONFLICT, REJECTED, UNAVAILABLE }

    @Getter
    public static class Result {
        private final Status status;
        private final String jobId;
        private final String reason;

        Result(Status status, String jobId, String reason) {
            this.status = status;
            this.jobId = jobId;
            this.reason = reason;
        }

        static Result accepted(String jobId) {
            return new Result(Status.ACCEPTED, jobId, null);
        }

        static Result unavailable(String reason) {
            return new Result(Status.UNAVAILABLE, null, reason);
        }
    }
}
