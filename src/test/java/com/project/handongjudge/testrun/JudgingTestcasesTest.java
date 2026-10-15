package com.project.handongjudge.testrun;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.project.handongjudge.testrun.dto.TestcaseBundleResponse;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class JudgingTestcasesTest {

    private static byte[] zip(String... pathAndContent) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (int i = 0; i < pathAndContent.length; i += 2) {
                out.putNextEntry(new ZipEntry(pathAndContent[i]));
                out.write(pathAndContent[i + 1].getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    @Test
    void followsDomjudgeRules() throws Exception {
        TestcaseBundleResponse bundle = JudgingTestcases.extract(zip(
                "problem.yaml", "name: x\n",
                "data/secret/2.in", "5 7\n", "data/secret/2.ans", "12\n",
                "data/secret/10.in", "1 1\n", "data/secret/10.ans", "2\n",
                "data/secret/orphan.in", "no answer\n",                     // .ans 없음 → 제외
                "data/sample/1.in", "", "data/sample/1.ans", "Hello\n",     // 빈 입력도 포함
                "other/3.in", "x", "other/3.ans", "y"));                    // data/ 밖 → 제외

        assertEquals(1, bundle.getSample().size());
        assertEquals("", bundle.getSample().get(0).getInput());
        assertEquals(1, bundle.getSample().get(0).getRank());

        // 문자열 순: "10" < "2", 순번은 sample 다음부터 이어짐
        assertEquals(2, bundle.getSecret().size());
        assertEquals("10", bundle.getSecret().get(0).getName());
        assertEquals(2, bundle.getSecret().get(0).getRank());
        assertEquals("2", bundle.getSecret().get(1).getName());
        assertEquals(3, bundle.getSecret().get(1).getRank());
    }

    @Test
    void hashIsStableSha256() throws Exception {
        byte[] z = zip("data/sample/1.in", "1\n", "data/sample/1.ans", "1\n");
        assertEquals(64, JudgingTestcases.hash(z).length());
        assertEquals(JudgingTestcases.hash(z), JudgingTestcases.extract(z).getHash());
    }
}
