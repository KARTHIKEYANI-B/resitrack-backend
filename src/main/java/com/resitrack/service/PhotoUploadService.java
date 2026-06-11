package com.resitrack.service;

import com.resitrack.exception.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class PhotoUploadService {

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    private static final long MAX_BYTES = 5 * 1024 * 1024; // 5 MB

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp"
    );

    private static final Set<String> ALLOWED_EXTS = Set.of(
            ".jpg", ".jpeg", ".png", ".webp"
    );

    public String saveProfilePhoto(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException("No file provided", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > MAX_BYTES) {
            throw new CustomException("File too large. Maximum 5 MB.", HttpStatus.BAD_REQUEST);
        }

        String contentType = file.getContentType() != null
                ? file.getContentType().toLowerCase() : "";
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new CustomException(
                    "Invalid file type. Allowed: JPG, PNG, WEBP", HttpStatus.BAD_REQUEST);
        }

        String originalName = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase() : "";
        String ext = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.'))
                : "";
        if (!ALLOWED_EXTS.contains(ext)) {
            throw new CustomException(
                    "Invalid extension. Allowed: .jpg .jpeg .png .webp", HttpStatus.BAD_REQUEST);
        }

        try {
            Path profilesDir = Paths.get(uploadDir, "profiles");
            Files.createDirectories(profilesDir);

            String uniqueName = UUID.randomUUID().toString().replace("-", "") + ext;
            Path destination  = profilesDir.resolve(uniqueName);
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

            log.info("Profile photo saved: {}", destination.toAbsolutePath());
            return "profiles/" + uniqueName;

        } catch (IOException e) {
            log.error("Failed to save profile photo", e);
            throw new CustomException("Failed to save photo. Please try again.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public void deletePhoto(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return;
        try {
            Path target = Paths.get(uploadDir, relativePath);
            Files.deleteIfExists(target);
            log.info("Profile photo deleted: {}", target.toAbsolutePath());
        } catch (IOException e) {
            log.warn("Could not delete photo at {}: {}", relativePath, e.getMessage());
        }
    }

    public String toPublicUrl(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return null;
        return "/api/uploads/" + relativePath;
    }
}