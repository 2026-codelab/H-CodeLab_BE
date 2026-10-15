package com.project.handongjudge.testrun.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 내부 API {@code GET /internal/testrun/problems/{id}/testcases} 응답. runner가 problemHash로 캐시한다. */
@Getter
@AllArgsConstructor
public class TestcaseBundleResponse {
    private String hash;
    private List<Testcase> sample;
    private List<Testcase> secret;

    @Getter
    @AllArgsConstructor
    public static class Testcase {
        /** DOMjudge rank와 같다 (샘플부터 1, 이어서 secret). */
        private int rank;
        private String name;
        private String input;
        private String answer;
    }
}
