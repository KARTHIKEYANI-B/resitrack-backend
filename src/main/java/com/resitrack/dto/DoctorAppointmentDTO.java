package com.resitrack.dto;

import com.resitrack.entity.DoctorAppointment;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class DoctorAppointmentDTO {

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Response {
        private Long          id;
        private String        doctorName;
        private String        specialization;
        private String        hospitalClinic;
        private LocalDate     appointmentDate;
        private LocalTime     appointmentTime;
        private String        reason;
        private String        notes;
        private String        status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static Response from(DoctorAppointment e) {
            return Response.builder()
                    .id(e.getId())
                    .doctorName(e.getDoctorName())
                    .specialization(e.getSpecialization())
                    .hospitalClinic(e.getHospitalClinic())
                    .appointmentDate(e.getAppointmentDate())
                    .appointmentTime(e.getAppointmentTime())
                    .reason(e.getReason())
                    .notes(e.getNotes())
                    .status(e.getStatus())
                    .createdAt(e.getCreatedAt())
                    .updatedAt(e.getUpdatedAt())
                    .build();
        }
    }

    /** Used for add / update (JSON body). */
    @Data
    public static class Request {

        @NotBlank(message = "Doctor name is required")
        @Size(max = 150, message = "Doctor name is too long")
        private String doctorName;

        @Size(max = 150, message = "Specialization is too long")
        private String specialization;

        @Size(max = 200, message = "Hospital/Clinic is too long")
        private String hospitalClinic;

        @NotNull(message = "Appointment date is required")
        private LocalDate appointmentDate;

        private LocalTime appointmentTime;

        @Size(max = 500, message = "Reason is too long")
        private String reason;

        @Size(max = 1000, message = "Notes are too long")
        private String notes;

        @Size(max = 20, message = "Status is too long")
        private String status;
    }
}
