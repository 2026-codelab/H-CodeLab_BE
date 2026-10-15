package com.project.handongjudge.testrun;

/** 경량 실행기 작업이 없거나 만료됨 (작업 메타 TTL 10분). */
public class TestRunStreamNotFoundException extends RuntimeException {

    public TestRunStreamNotFoundException(String jobId) {
        super("테스트 작업을 찾을 수 없습니다: " + jobId);
    }
}
