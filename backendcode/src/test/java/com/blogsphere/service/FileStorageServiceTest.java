package com.blogsphere.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileStorageServiceTest {

    @TempDir
    Path uploadDirectory;

    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        fileStorageService = new FileStorageService();
        ReflectionTestUtils.setField(fileStorageService, "uploadDir", uploadDirectory.toString());
    }

    @Test
    void storesValidWebpImageUsingUploadsConvention() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "content.webp", "image/webp", validWebpContainer());

        String url = fileStorageService.storeFile(file);

        assertTrue(url.matches("/uploads/[A-Za-z0-9-]+\\.webp"));
        assertTrue(Files.exists(uploadDirectory.resolve(Path.of(url).getFileName())));
    }

    @Test
    void rejectsWebpExtensionWithInvalidContent() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "content.webp", "image/webp", "not a webp".getBytes(StandardCharsets.UTF_8));

        assertThrows(ResponseStatusException.class, () -> fileStorageService.storeFile(file));
    }

    @Test
    void rejectsValidWebpLargerThanFiveMegabytes() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "content.webp", "image/webp", validWebpContainer(5 * 1024 * 1024 + 1));

        assertThrows(ResponseStatusException.class, () -> fileStorageService.storeFile(file));
    }

    private byte[] validWebpContainer() throws IOException {
        return validWebpContainer(30);
    }

    private byte[] validWebpContainer(int length) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write("RIFF".getBytes(StandardCharsets.US_ASCII));
        writeLittleEndian(bytes, length - 8); // RIFF length excludes the first eight bytes.
        bytes.write("WEBPVP8X".getBytes(StandardCharsets.US_ASCII));
        writeLittleEndian(bytes, length - 20);
        bytes.write(new byte[length - 20]);
        return bytes.toByteArray();
    }

    private void writeLittleEndian(ByteArrayOutputStream bytes, int value) {
        bytes.write(value & 0xff);
        bytes.write((value >>> 8) & 0xff);
        bytes.write((value >>> 16) & 0xff);
        bytes.write((value >>> 24) & 0xff);
    }
}
