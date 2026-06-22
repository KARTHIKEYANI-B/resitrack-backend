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

/**
 * Handles storage of vehicle insurance documents (image or PDF).
 *
 * Mirrors the existing PhotoUploadService pattern (same upload root,
 * same UUID naming convention, same public URL scheme) but is kept as
 * a separate service so the existing profile-photo upload behavior is
 * never touched.
 */
@Slf4j
@Service
public class VehicleDocumentUploadService {

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    private static final long MAX_BYTES = 10 * 1024 * 1024; // 10 MB

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "application/pdf"
    );

    private static final Set<String> ALLOWED_EXTS = Set.of(
            ".jpg", ".jpeg", ".png", ".pdf"
    );

    public String saveInsuranceDocument(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException("No file provided", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > MAX_BYTES) {
            throw new CustomException("File too large. Maximum 10 MB.", HttpStatus.BAD_REQUEST);
        }

        String contentType = file.getContentType() != null
                ? file.getContentType().toLowerCase() : "";
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new CustomException(
                    "Invalid file type. Allowed: JPG, JPEG, PNG, PDF", HttpStatus.BAD_REQUEST);
        }

        String originalName = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase() : "";
        String ext = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.'))
                : "";
        if (!ALLOWED_EXTS.contains(ext)) {
            throw new CustomException(
                    "Invalid extension. Allowed: .jpg .jpeg .png .pdf", HttpStatus.BAD_REQUEST);
        }

        try {
            Path docsDir = Paths.get(uploadDir, "vehicle-insurance");
            Files.createDirectories(docsDir);

            String uniqueName = UUID.randomUUID().toString().replace("-", "") + ext;
            Path destination   = docsDir.resolve(uniqueName);
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

            log.info("Vehicle insurance document saved: {}", destination.toAbsolutePath());
            return "vehicle-insurance/" + uniqueName;

        } catch (IOException e) {
            log.error("Failed to save vehicle insurance document", e);
            throw new CustomException("Failed to save document. Please try again.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public void deleteDocument(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return;
        try {
            Path target = Paths.get(uploadDir, relativePath);
            Files.deleteIfExists(target);
            log.info("Vehicle insurance document deleted: {}", target.toAbsolutePath());
        } catch (IOException e) {
            log.warn("Could not delete document at {}: {}", relativePath, e.getMessage());
        }
    }

    public String toPublicUrl(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return null;
        return "/api/uploads/" + relativePath;
    }
}
