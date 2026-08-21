package com.resitrack.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.resitrack.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Generic Cloudinary upload service.
 *
 * Any feature that previously wrote to the local filesystem (payment
 * screenshots, profile photos, vehicle documents, expense receipts, ...)
 * should go through this service instead. It never touches disk — the
 * MultipartFile bytes are streamed straight to Cloudinary and only the
 * resulting secure_url / public_id come back.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private final Cloudinary cloudinary;

    private static final long MAX_BYTES = 10L * 1024 * 1024; // 10 MB

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "application/pdf");

    /**
     * Result of a successful upload — only what callers actually need.
     */
    public record UploadResult(String secureUrl, String publicId) {
    }

    /**
     * Uploads a MultipartFile to Cloudinary under the given folder.
     *
     * @param file   the file from the incoming request
     * @param folder Cloudinary folder, e.g. "resitrack/payments"
     */
    public UploadResult upload(MultipartFile file, String folder) {
        validate(file);

        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1) : "jpg";
        // Cloudinary derives resource_type from content automatically when we pass "auto",
        // but PDFs must be uploaded as "raw"/"image" depending on delivery needs — "auto" covers both.
        String publicId = UUID.randomUUID().toString();

        try {
            Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", folder,
                    "public_id", publicId,
                    "resource_type", "auto",
                    "overwrite", false,
                    "use_filename", false,
                    "unique_filename", true
            ));

            String secureUrl = (String) result.get("secure_url");
            String returnedPublicId = (String) result.get("public_id");

            if (secureUrl == null) {
                throw new CustomException("Cloudinary upload did not return a URL", HttpStatus.INTERNAL_SERVER_ERROR);
            }

            log.info("Uploaded file to Cloudinary: folder={}, publicId={}", folder, returnedPublicId);
            return new UploadResult(secureUrl, returnedPublicId);

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            // Catches more than IOException deliberately — the Cloudinary SDK's
            // bundled (old) Apache HttpClient can also throw unchecked exceptions
            // (e.g. a stale pooled connection being reused, timeouts), and any of
            // those should surface as a clean, expected upload failure rather than
            // an unhandled 500.
            log.error("Cloudinary upload failed for folder={}", folder, e);
            throw new CustomException("Failed to upload file to cloud storage", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Deletes an asset from Cloudinary by its public_id. Safe to call with
     * null/blank — becomes a no-op (useful when a DB record had no file).
     */
    public void delete(String publicId) {
        if (publicId == null || publicId.isBlank()) return;
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", "auto"));
        } catch (Exception e) {
            // Non-fatal — log and move on. An orphaned Cloudinary asset is not worth
            // failing the caller's transaction over. Catches more than IOException
            // deliberately: the Cloudinary SDK can also throw unchecked exceptions
            // (e.g. on an unexpected API error response), and a failed best-effort
            // cleanup of the OLD asset must never fail the caller's actual request
            // (e.g. AdminSignatureController's re-upload, which has already saved
            // the new signature by the time this runs).
            log.warn("Failed to delete Cloudinary asset publicId={}", publicId, e);
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException("File is empty", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > MAX_BYTES) {
            throw new CustomException("File too large. Maximum 10 MB.", HttpStatus.BAD_REQUEST);
        }
        String ct = file.getContentType() != null ? file.getContentType().toLowerCase() : "";
        if (!ALLOWED_CONTENT_TYPES.contains(ct)) {
            throw new CustomException(
                    "Invalid file type. Allowed: JPG, PNG, WEBP, PDF", HttpStatus.BAD_REQUEST);
        }
    }
}
