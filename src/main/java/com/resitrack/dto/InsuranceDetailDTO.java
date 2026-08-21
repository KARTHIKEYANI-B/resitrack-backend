package com.resitrack.dto;

import com.resitrack.entity.InsuranceDetail;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class InsuranceDetailDTO {

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Response {
        private Long          id;
        private String        insuranceType;
        private String        provider;
        private String        policyNumber;
        private String        policyHolderName;
        private String        insuredPersonName;
        private LocalDate     startDate;
        private LocalDate     expiryDate;
        private BigDecimal    premiumAmount;
        private String        premiumFrequency;
        private BigDecimal    sumInsured;
        private String        nomineeName;
        private String        nomineeRelationship;
        private String        nomineeContact;
        private String        status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static Response from(InsuranceDetail e) {
            return Response.builder()
                    .id(e.getId())
                    .insuranceType(e.getInsuranceType())
                    .provider(e.getProvider())
                    .policyNumber(e.getPolicyNumber())
                    .policyHolderName(e.getPolicyHolderName())
                    .insuredPersonName(e.getInsuredPersonName())
                    .startDate(e.getStartDate())
                    .expiryDate(e.getExpiryDate())
                    .premiumAmount(e.getPremiumAmount())
                    .premiumFrequency(e.getPremiumFrequency())
                    .sumInsured(e.getSumInsured())
                    .nomineeName(e.getNomineeName())
                    .nomineeRelationship(e.getNomineeRelationship())
                    .nomineeContact(e.getNomineeContact())
                    .status(e.getStatus())
                    .createdAt(e.getCreatedAt())
                    .updatedAt(e.getUpdatedAt())
                    .build();
        }
    }

    /** Used for add / update (JSON body). */
    @Data
    public static class Request {

        @NotBlank(message = "Insurance type is required")
        @Size(max = 30, message = "Insurance type is too long")
        private String insuranceType;

        @Size(max = 200, message = "Provider is too long")
        private String provider;

        @NotBlank(message = "Policy number is required")
        @Size(max = 100, message = "Policy number is too long")
        private String policyNumber;

        @Size(max = 150, message = "Policy holder name is too long")
        private String policyHolderName;

        @Size(max = 150, message = "Insured person name is too long")
        private String insuredPersonName;

        private LocalDate startDate;
        private LocalDate expiryDate;

        @DecimalMin(value = "0.0", message = "Premium amount must not be negative")
        @Digits(integer = 10, fraction = 2, message = "Premium amount is invalid")
        private BigDecimal premiumAmount;

        @Size(max = 20, message = "Premium frequency is too long")
        private String premiumFrequency;

        @DecimalMin(value = "0.0", message = "Sum insured must not be negative")
        @Digits(integer = 12, fraction = 2, message = "Sum insured is invalid")
        private BigDecimal sumInsured;

        @Size(max = 150, message = "Nominee name is too long")
        private String nomineeName;

        @Size(max = 100, message = "Nominee relationship is too long")
        private String nomineeRelationship;

        @Pattern(regexp = "^$|^[+]?[0-9]{10,13}$", message = "Invalid nominee contact number")
        private String nomineeContact;

        @Size(max = 20, message = "Status is too long")
        private String status;
    }
}
