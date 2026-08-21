package com.resitrack.dto;

import com.resitrack.entity.LicenseDetail;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class LicenseDetailDTO {

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Response {
        private Long          id;
        private String        licenseType;
        private String        licenseNumber;
        private String        holderName;
        private LocalDate     issueDate;
        private LocalDate     expiryDate;
        private String        vehicleClasses;
        private String        issuingAuthority;
        private String        state;
        private String        status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static Response from(LicenseDetail e) {
            return Response.builder()
                    .id(e.getId())
                    .licenseType(e.getLicenseType())
                    .licenseNumber(e.getLicenseNumber())
                    .holderName(e.getHolderName())
                    .issueDate(e.getIssueDate())
                    .expiryDate(e.getExpiryDate())
                    .vehicleClasses(e.getVehicleClasses())
                    .issuingAuthority(e.getIssuingAuthority())
                    .state(e.getState())
                    .status(e.getStatus())
                    .createdAt(e.getCreatedAt())
                    .updatedAt(e.getUpdatedAt())
                    .build();
        }
    }

    /** Used for add / update (JSON body). */
    @Data
    public static class Request {

        @NotBlank(message = "License type is required")
        @Size(max = 30, message = "License type is too long")
        private String licenseType;

        @NotBlank(message = "License number is required")
        @Size(max = 100, message = "License number is too long")
        private String licenseNumber;

        @Size(max = 150, message = "Holder name is too long")
        private String holderName;

        private LocalDate issueDate;
        private LocalDate expiryDate;

        @Size(max = 200, message = "Vehicle classes is too long")
        private String vehicleClasses;

        @Size(max = 150, message = "Issuing authority is too long")
        private String issuingAuthority;

        @Size(max = 100, message = "State is too long")
        private String state;

        @Size(max = 20, message = "Status is too long")
        private String status;
    }
}
