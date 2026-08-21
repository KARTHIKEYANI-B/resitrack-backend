package com.resitrack.dto;

import com.resitrack.entity.PersonalDocument;
import com.resitrack.util.ExpiryStatusUtil;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class PersonalDocumentDTO {

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Response {
        private Long          id;
        private String        documentName;
        private String        documentType;
        private String        relatedRecordType;
        private Long           relatedRecordId;
        private String        relatedRecordLabel;
        private String        originalFileName;
        private String        mimeType;
        private Long          fileSizeBytes;
        private LocalDate     expiryDate;
        private String        status;
        private String        effectiveStatus;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static Response from(PersonalDocument e, String relatedRecordLabel) {
            return Response.builder()
                    .id(e.getId())
                    .documentName(e.getDocumentName())
                    .documentType(e.getDocumentType())
                    .relatedRecordType(e.getRelatedRecordType())
                    .relatedRecordId(e.getRelatedRecordId())
                    .relatedRecordLabel(relatedRecordLabel)
                    .originalFileName(e.getOriginalFileName())
                    .mimeType(e.getMimeType())
                    .fileSizeBytes(e.getFileSizeBytes())
                    .expiryDate(e.getExpiryDate())
                    .status(e.getStatus())
                    .effectiveStatus(ExpiryStatusUtil.computeEffectiveStatus(e.getStatus(), e.getExpiryDate()))
                    .createdAt(e.getCreatedAt())
                    .updatedAt(e.getUpdatedAt())
                    .build();
        }
    }

    /** Used for add (multipart form fields alongside the file) / update (JSON body). */
    @Data
    public static class Request {

        @Size(max = 200, message = "Document name is too long")
        private String documentName;

        @NotBlank(message = "Document type is required")
        @Size(max = 30, message = "Document type is too long")
        private String documentType;

        @Size(max = 20, message = "Related record type is too long")
        private String relatedRecordType;

        private Long relatedRecordId;

        private LocalDate expiryDate;

        @Size(max = 20, message = "Status is too long")
        private String status;
    }
}
