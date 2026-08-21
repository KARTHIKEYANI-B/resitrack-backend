package com.resitrack.dto;

import com.resitrack.entity.DietChartEntry;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;

public class DietChartEntryDTO {

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Response {
        private Long          id;
        private String        title;
        private String        mealType;
        private String        description;
        private String        notes;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static Response from(DietChartEntry e) {
            return Response.builder()
                    .id(e.getId())
                    .title(e.getTitle())
                    .mealType(e.getMealType())
                    .description(e.getDescription())
                    .notes(e.getNotes())
                    .createdAt(e.getCreatedAt())
                    .updatedAt(e.getUpdatedAt())
                    .build();
        }
    }

    /** Used for add / update (JSON body). */
    @Data
    public static class Request {

        @Size(max = 100, message = "Title is too long")
        private String title;

        @Size(max = 50, message = "Meal type is too long")
        private String mealType;

        @NotBlank(message = "Description is required")
        @Size(max = 1000, message = "Description is too long")
        private String description;

        @Size(max = 500, message = "Notes are too long")
        private String notes;
    }
}
