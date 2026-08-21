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
 * Handles storage of Personal Management documents (identity proof, address
 * proof, insurance/license copies, etc.).
 *
 * Mirrors the existing PhotoUploadService / VehicleDocumentUploadService
 * pattern exactly (same upload root, same UUID naming convention) but is
 * kept as its own service so those existing upload flows are never touched.
 * Unlike photos/vehicle documents (served via the public /uploads/** static
 * mapping), personal documents are sensitive, so retrieval always goes
 * through the authenticated, ownership-checked PersonalDocumentController
 * download endpoint rather than a public URL — the file is still written to
 * the same local-disk root as everything else.
 */
@Slf4j
@Service
public class DocumentUploadService {

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    private static final long MAX_BYTES = 10 * 1024 * 1024; // 10 MB

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "application/pdf"
    );

    private static final Set<String> ALLOWED_EXTS = Set.of(
            ".jpg", ".jpeg", ".png", ".pdf"
    );

    public String saveDocument(MultipartFile file) {
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
            Path docsDir = Paths.get(uploadDir, "personal-documents");
            Files.createDirectories(docsDir);

            String uniqueName = UUID.randomUUID().toString().replace("-", "") + ext;
            Path destination   = docsDir.resolve(uniqueName);
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

            log.info("Personal document saved: {}", destination.toAbsolutePath());
            return "personal-documents/" + uniqueName;

        } catch (IOException e) {
            log.error("Failed to save personal document", e);
            throw new CustomException("Failed to save document. Please try again.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public byte[] readDocument(String relativePath) {
        try {
            Path target = Paths.get(uploadDir, relativePath);
            if (!Files.exists(target)) {
                throw new CustomException("Document file not found", HttpStatus.NOT_FOUND);
            }
            return Files.readAllBytes(target);
        } catch (IOException e) {
            log.error("Failed to read personal document", e);
            throw new CustomException("Failed to read document. Please try again.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public void deleteDocument(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return;
        try {
            Path target = Paths.get(uploadDir, relativePath);
            Files.deleteIfExists(target);
            log.info("Personal document deleted: {}", target.toAbsolutePath());
        } catch (IOException e) {
            log.warn("Could not delete document at {}: {}", relativePath, e.getMessage());
        }
    }
}
