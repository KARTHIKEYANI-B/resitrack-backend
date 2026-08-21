package com.resitrack.dto;

import com.resitrack.entity.VitalReading;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class VitalReadingDTO {

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Response {
        private Long          id;
        private String        readingType;
        private LocalDate     readingDate;
        private LocalTime     readingTime;
        private BigDecimal    sugarValue;
        private String        sugarContext;
        private Integer       systolic;
        private Integer       diastolic;
        private Integer       pulse;
        private String        notes;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static Response from(VitalReading e) {
            return Response.builder()
                    .id(e.getId())
                    .readingType(e.getReadingType())
                    .readingDate(e.getReadingDate())
                    .readingTime(e.getReadingTime())
                    .sugarValue(e.getSugarValue())
                    .sugarContext(e.getSugarContext())
                    .systolic(e.getSystolic())
                    .diastolic(e.getDiastolic())
                    .pulse(e.getPulse())
                    .notes(e.getNotes())
                    .createdAt(e.getCreatedAt())
                    .updatedAt(e.getUpdatedAt())
                    .build();
        }
    }

    /** Used for add / update (JSON body). */
    @Data
    public static class Request {

        @NotBlank(message = "Reading type is required")
        @Pattern(regexp = "SUGAR|BP", message = "Reading type must be SUGAR or BP")
        private String readingType;

        @NotNull(message = "Reading date is required")
        private LocalDate readingDate;

        private LocalTime readingTime;

        @DecimalMin(value = "0.0", message = "Sugar value must not be negative")
        @Digits(integer = 4, fraction = 2, message = "Sugar value is invalid")
        private BigDecimal sugarValue;

        @Size(max = 30, message = "Sugar context is too long")
        private String sugarContext;

        @Min(value = 0, message = "Systolic must not be negative")
        private Integer systolic;

        @Min(value = 0, message = "Diastolic must not be negative")
        private Integer diastolic;

        @Min(value = 0, message = "Pulse must not be negative")
        private Integer pulse;

        @Size(max = 500, message = "Notes are too long")
        private String notes;
    }
}
