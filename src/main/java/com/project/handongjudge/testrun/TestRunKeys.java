package com.project.handongjudge.testrun;

import java.util.regex.Pattern;

/**
 * 테스트하기 실행기와 공유하는 Redis 키·필드 이름.
 * H-CodeLab_TestRun common-contract의 Streams와 같은 값이다 (패키지 배포 전까지 복사해 둔다).
 */
public final class TestRunKeys {

    /** 실행기 작업 ID(UUID). 기존 DOMjudge 경로의 sessionKey(숫자 제출 번호)와 구분하는 데 쓴다. */
    private static final Pattern JOB_ID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    public static final String FIELD_EVENT = "event";
    public static final String FIELD_DATA = "data";

    private TestRunKeys() {
    }

    public static boolean isJobId(String sessionKey) {
        return sessionKey != null && JOB_ID.matcher(sessionKey).matches();
    }

    /** 작업별 이벤트 스트림. */
    public static String events(String jobId) {
        return "testrun:events:" + jobId;
    }

    /** 작업 소유자 메타 해시 (userId, sectionId, problemId). */
    public static String jobMeta(String jobId) {
        return "testrun:job:" + jobId;
    }

    public static boolean isTerminal(String event) {
        return "complete".equals(event) || "ce".equals(event) || "error".equals(event);
    }
}
