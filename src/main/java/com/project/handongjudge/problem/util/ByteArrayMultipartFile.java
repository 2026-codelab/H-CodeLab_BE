package com.project.handongjudge.problem.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * 메모리에서 생성한 byte[]를 DOMjudge 업로드 API(MultipartFile 파라미터)에 그대로 넘기기 위한 래퍼.
 * 실제 HTTP 업로드 없이(zip을 서버에서 직접 생성) MultipartFile 인터페이스가 필요한 곳에 사용한다.
 */
public class ByteArrayMultipartFile implements MultipartFile {

    private final byte[] content;
    private final String filename;
    private final String contentType;

    public ByteArrayMultipartFile(byte[] content, String filename) {
        this(content, filename, "application/zip");
    }

    public ByteArrayMultipartFile(byte[] content, String filename, String contentType) {
        this.content = content;
        this.filename = filename;
        this.contentType = contentType;
    }

    @Override
    public String getName() { return "file"; }

    @Override
    public String getOriginalFilename() { return filename; }

    @Override
    public String getContentType() { return contentType; }

    @Override
    public boolean isEmpty() { return content.length == 0; }

    @Override
    public long getSize() { return content.length; }

    @Override
    public byte[] getBytes() { return content; }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(content);
    }

    @Override
    public void transferTo(File dest) throws IOException {
        Files.write(dest.toPath(), content);
    }
}
