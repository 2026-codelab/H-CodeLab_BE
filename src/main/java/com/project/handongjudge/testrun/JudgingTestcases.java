package com.project.handongjudge.testrun;

import com.project.handongjudge.testrun.dto.TestcaseBundleResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;

/**
 * 문제 zip에서 채점용 테스트케이스를 DOMjudge와 같은 규칙으로 꺼낸다.
 * (Domjudge/webapp/src/Service/ImportProblemService.php:340-362)
 * <ul>
 *   <li>data/sample/, data/secret/ 아래의 .in 중 같은 이름의 .ans가 있는 것만</li>
 *   <li>각 묶음 안에서 파일 경로 문자열 순, 순번은 sample부터 1로 시작해 secret으로 이어짐</li>
 *   <li>입력이 빈 테스트케이스도 포함 (ProblemFileUtil.extractTestCasesFromZip은 빈 입력을 버리므로 쓰지 않는다)</li>
 * </ul>
 * runner가 공식 채점과 같은 테스트케이스로 실행해야 결과가 일치한다.
 */
public final class JudgingTestcases {

    private JudgingTestcases() {
    }

    /** runner 캐시 키. zip 바이트가 같으면 같은 값. */
    public static String hash(byte[] zip) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(zip);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static TestcaseBundleResponse extract(byte[] zip) throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        try (ZipArchiveInputStream in = new ZipArchiveInputStream(new ByteArrayInputStream(zip),
                StandardCharsets.UTF_8.name())) {
            ZipArchiveEntry entry;
            while ((entry = in.getNextZipEntry()) != null) {
                if (!entry.isDirectory()) {
                    files.put(entry.getName(), in.readAllBytes());
                }
            }
        }
        int[] rank = {0};
        List<TestcaseBundleResponse.Testcase> sample = collect(files, "sample", rank);
        List<TestcaseBundleResponse.Testcase> secret = collect(files, "secret", rank);
        return new TestcaseBundleResponse(hash(zip), sample, secret);
    }

    private static List<TestcaseBundleResponse.Testcase> collect(Map<String, byte[]> files, String type, int[] rank) {
        String prefix = "data/" + type + "/";
        // TreeMap: 경로 문자열 순 (DOMjudge asort SORT_STRING)
        TreeMap<String, String> names = new TreeMap<>();
        for (String path : files.keySet()) {
            if (path.startsWith(prefix) && path.endsWith(".in")) {
                String base = path.substring(0, path.length() - 3);
                if (files.containsKey(base + ".ans")) {
                    names.put(base.substring(prefix.length()), base);
                }
            }
        }
        List<TestcaseBundleResponse.Testcase> result = new ArrayList<>();
        for (Map.Entry<String, String> e : names.entrySet()) {
            rank[0]++;
            result.add(new TestcaseBundleResponse.Testcase(rank[0], e.getKey(),
                    new String(files.get(e.getValue() + ".in"), StandardCharsets.UTF_8),
                    new String(files.get(e.getValue() + ".ans"), StandardCharsets.UTF_8)));
        }
        return result;
    }
}
