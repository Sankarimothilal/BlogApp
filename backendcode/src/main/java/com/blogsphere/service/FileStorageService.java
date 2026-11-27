package com.blogsphere.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final long AVATAR_MAX_SIZE = 5 * 1024 * 1024;

    @Value("${blogsphere.upload.dir:uploads}")
    private String uploadDir;

    public String storeFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > AVATAR_MAX_SIZE) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Image must be between 1 byte and 5 MB");
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        String extension;
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex <= 0) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Unsupported image type");
        }
        extension = originalFilename.substring(dotIndex).toLowerCase(java.util.Locale.ROOT);
        String contentType = file.getContentType();
        boolean supportedType = switch (extension) {
            case ".jpg", ".jpeg" -> "image/jpeg".equalsIgnoreCase(contentType);
            case ".png" -> "image/png".equalsIgnoreCase(contentType);
            case ".webp" -> "image/webp".equalsIgnoreCase(contentType);
            default -> false;
        };
        if (!supportedType) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Only JPG, JPEG, PNG, and WEBP images are supported");
        }

        try {
            byte[] bytes = file.getBytes();
            boolean validImage = ".webp".equals(extension)
                    ? isWebpImage(bytes)
                    : ImageIO.read(new ByteArrayInputStream(bytes)) != null;
            if (!validImage) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, "Uploaded file is not a valid image");
            }
        } catch (IOException ex) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Could not read uploaded image", ex);
        }

        String fileName = UUID.randomUUID().toString() + extension;

        try {
            Path targetLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(targetLocation);
            Path destination = targetLocation.resolve(fileName);
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

            return "/uploads/" + fileName;
        } catch (IOException ex) {
            throw new RuntimeException("Could not store file " + fileName + ". Please try again!", ex);
        }
    }

    private boolean isWebpImage(byte[] bytes) {
        if (bytes.length < 20
                || !"RIFF".equals(new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII))
                || !"WEBP".equals(new String(bytes, 8, 4, java.nio.charset.StandardCharsets.US_ASCII))) {
            return false;
        }
        long riffSize = (bytes[4] & 0xffL) | ((bytes[5] & 0xffL) << 8)
                | ((bytes[6] & 0xffL) << 16) | ((bytes[7] & 0xffL) << 24);
        String chunkType = new String(bytes, 12, 4, java.nio.charset.StandardCharsets.US_ASCII);
        long chunkSize = (bytes[16] & 0xffL) | ((bytes[17] & 0xffL) << 8)
                | ((bytes[18] & 0xffL) << 16) | ((bytes[19] & 0xffL) << 24);
        long minimumChunkSize = switch (chunkType) {
            case "VP8 " -> 10;
            case "VP8L" -> 5;
            case "VP8X" -> 10;
            default -> Long.MAX_VALUE;
        };
        return riffSize == bytes.length - 8L && chunkSize >= minimumChunkSize
                && chunkSize <= bytes.length - 20L;
    }

    public String storeAvatar(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > AVATAR_MAX_SIZE) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Profile image must be between 1 byte and 5 MB");
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex <= 0) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Only JPG, JPEG, PNG, and WEBP images are supported");
        }
        String extension = originalFilename.substring(dotIndex).toLowerCase(java.util.Locale.ROOT);
        String contentType = file.getContentType();
        boolean supported = switch (extension) {
            case ".jpg", ".jpeg" -> "image/jpeg".equalsIgnoreCase(contentType);
            case ".png" -> "image/png".equalsIgnoreCase(contentType);
            case ".webp" -> "image/webp".equalsIgnoreCase(contentType);
            default -> false;
        };
        if (!supported) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Only JPG, JPEG, PNG, and WEBP images are supported");
        }

        try {
            byte[] bytes = file.getBytes();
            boolean validImage = switch (extension) {
                case ".jpg", ".jpeg" -> bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
                case ".png" -> bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
                case ".webp" -> bytes.length >= 12 && new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
                        && new String(bytes, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP");
                default -> false;
            };
            if (!validImage) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, "Uploaded file is not a valid image");
            }
            String fileName = UUID.randomUUID() + extension;
            Path targetLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(targetLocation);
            Files.copy(new ByteArrayInputStream(bytes), targetLocation.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            return "/uploads/" + fileName;
        } catch (IOException ex) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Could not read uploaded image", ex);
        }
    }
}
