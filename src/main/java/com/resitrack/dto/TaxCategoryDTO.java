package com.resitrack.dto;

import com.resitrack.entity.TaxCategory;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class TaxCategoryDTO {

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Response {
        private Long          id;
        private Long          residentId;
        private String        taxName;
        private String        taxType;
        private String        description;
        private LocalDate     dueDate;
        private LocalDate     reminderDate;
        private boolean       active;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static Response from(TaxCategory t) {
            return Response.builder()
                    .id(t.getId())
                    .residentId(t.getResident().getId())
                    .taxName(t.getTaxName())
                    .taxType(t.getTaxType())
                    .description(t.getDescription())
                    .dueDate(t.getDueDate())
                    .reminderDate(t.getReminderDate())
                    .active(t.isActive())
                    .createdAt(t.getCreatedAt())
                    .updatedAt(t.getUpdatedAt())
                    .build();
        }
    }

    /** Used for add / update of a tax category (JSON body). */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Request {
        private String    taxName;
        private String    taxType;
        private String    description;
        private LocalDate dueDate;
        private LocalDate reminderDate;
    }
}
