package com.project.handongjudge.submission.util;

import org.springframework.stereotype.Component;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException; 
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Getter
@Setter
public class CodeExtenstion {

    private static final Logger log = LoggerFactory.getLogger(CodeExtenstion.class);
    private static final String TEMP_DIR = "./tmp/submissions";

    public static String getCodeExtension(String language) {
        switch (language) {
            case "python":
                return ".py";
            case "java":
                return ".java";
            case "cpp":
                return ".cpp";
            case "c":
                return ".c";
            case "javascript":
                return ".js";
            default:
                return ".txt";
        }
    }
    
    private static final Pattern JAVA_PUBLIC_CLASS_PATTERN =
            Pattern.compile("public\\s+(?:final\\s+|abstract\\s+)?class\\s+(\\w+)");

    public static File StringToFile(String language, String code){
        // use getCodeExtenstion  get C
        // make file name
        // make file
        // return file
        String extension = getCodeExtension(language);
        Path tempFile;
        if ("java".equals(language)) {
            // Java는 public class 이름과 파일명이 반드시 같아야 javac가 컴파일함
            // (안 맞으면 "class X is public, should be declared in a file named X.java" 에러).
            // 동시에 여러 요청이 같은 public class 이름(예: 기본 템플릿의 Solution)을 쓸 수 있으므로,
            // 파일명 충돌이 안 나게 요청마다 별도 하위 폴더에 만든다.
            Matcher m = JAVA_PUBLIC_CLASS_PATTERN.matcher(code);
            String fileName = (m.find() ? m.group(1) : "Main") + extension;
            tempFile = Paths.get(TEMP_DIR, UUID.randomUUID().toString(), fileName);
        } else {
            String fileName = UUID.randomUUID().toString() + extension;
            tempFile = Paths.get(TEMP_DIR, fileName);
        }
        log.debug("파일 이름: {}", tempFile.getFileName());

        try {
            Files.createDirectories(tempFile.getParent());
            Files.write(tempFile, code.getBytes(StandardCharsets.UTF_8));
            System.out.println("파일 저장 성공: " + tempFile);
        } catch (IOException e) {
            throw new RuntimeException("파일 저장 실패", e);
        }


        return tempFile.toFile();
    }

    public static File multipartToFile(MultipartFile multipartFile, String language) {
        try {
            File tempFile = File.createTempFile("submission_", "." + language);
            multipartFile.transferTo(tempFile);
            return tempFile;
        } catch (IOException e) {
            throw new RuntimeException("파일 저장 실패", e);
        }
    }
}
