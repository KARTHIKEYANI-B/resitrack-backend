package com.resitrack.dto;

import com.resitrack.entity.AdminAssignment;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class AdminAssignmentDTO {

    @Data @AllArgsConstructor @NoArgsConstructor @Builder
    public static class Response {
        private Long          id;
        private Long          residentId;
        private String        residentName;
        private String        residentPersonalEmail;  
        private String        flatNumber;
        private Long          adminId;
        private String        adminPositionEmail;    
        private String        position;
        private String        positionDisplayName;
        private LocalDate     startDate;
        private LocalDate     endDate;
        private boolean       active;
        private String        notes;
        private LocalDateTime createdAt;

        public static Response from(AdminAssignment a) {
            return Response.builder()
                    .id(a.getId())
                    .residentId(a.getResident() != null ? a.getResident().getId() : null)
                    .residentName(a.getResident() != null ? a.getResident().getFullName() : null)
                    .residentPersonalEmail(a.getResident() != null ? a.getResident().getEmail() : null)
                    .flatNumber(a.getResident() != null ? a.getResident().getFlatNumber() : null)
                    .adminId(a.getAdmin() != null ? a.getAdmin().getId() : null)
                    .adminPositionEmail(a.getAdmin() != null ? a.getAdmin().getEmail() : null)
                    .position(a.getPosition() != null ? a.getPosition().name() : null)
                    .positionDisplayName(a.getPosition() != null ? a.getPosition().getDisplayName() : null)
                    .startDate(a.getStartDate())
                    .endDate(a.getEndDate())
                    .active(a.isActive())
                    .notes(a.getNotes())
                    .createdAt(a.getCreatedAt())
                    .build();
        }
    }

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class AppointRequest {
        private Long   residentId;
        private String position;
        private LocalDate startDate;
        private String notes;
        private boolean resetPassword;
    }

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class RevokeRequest {
        private Long      assignmentId;
        private LocalDate endDate;
        private String    notes;
    }

    @Data @AllArgsConstructor @NoArgsConstructor @Builder
    public static class AppointResponse {
        private Response   assignment;
        private String     positionEmail;
        private String     generatedPassword;
        private String     message;
    }
}