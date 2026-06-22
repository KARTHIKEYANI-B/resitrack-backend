package com.resitrack.dto;

import com.resitrack.entity.Vehicle;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class VehicleDTO {

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Response {
        private Long          id;
        private Long          residentId;
        private String        vehicleNumber;
        private String        vehicleType;
        private String        insuranceDocumentUrl;
        private String        insuranceDocumentName;
        private String        insuranceNumber;
        private String        insuranceProvider;
        private LocalDate     insuranceExpiryDate;
        private boolean       active;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static Response from(Vehicle v, String insuranceDocumentUrl) {
            return Response.builder()
                    .id(v.getId())
                    .residentId(v.getResident().getId())
                    .vehicleNumber(v.getVehicleNumber())
                    .vehicleType(v.getVehicleType())
                    .insuranceDocumentUrl(insuranceDocumentUrl)
                    .insuranceDocumentName(v.getInsuranceDocumentName())
                    .insuranceNumber(v.getInsuranceNumber())
                    .insuranceProvider(v.getInsuranceProvider())
                    .insuranceExpiryDate(v.getInsuranceExpiryDate())
                    .active(v.isActive())
                    .createdAt(v.getCreatedAt())
                    .updatedAt(v.getUpdatedAt())
                    .build();
        }
    }

    /** Used for add / update of vehicle metadata (JSON body, no file). */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Request {
        private String    vehicleNumber;
        private String    vehicleType;
        private String    insuranceNumber;
        private String    insuranceProvider;
        private LocalDate insuranceExpiryDate;
    }
}
