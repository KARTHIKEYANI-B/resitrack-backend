package com.resitrack.dto;

import com.resitrack.entity.PersonalReminder;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class PersonalReminderDTO {

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Response {
        private Long          id;
        private String        title;
        private String        category;
        private LocalDate     reminderDate;
        private String        notes;
        private boolean       completed;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static Response from(PersonalReminder e) {
            return Response.builder()
                    .id(e.getId())
                    .title(e.getTitle())
                    .category(e.getCategory())
                    .reminderDate(e.getReminderDate())
                    .notes(e.getNotes())
                    .completed(e.isCompleted())
                    .createdAt(e.getCreatedAt())
                    .updatedAt(e.getUpdatedAt())
                    .build();
        }
    }

    /** Used for add / update (JSON body). */
    @Data
    public static class Request {

        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title is too long")
        private String title;

        @Size(max = 50, message = "Category is too long")
        private String category;

        @NotNull(message = "Reminder date is required")
        private LocalDate reminderDate;

        @Size(max = 1000, message = "Notes are too long")
        private String notes;

        private Boolean completed;
    }
}
