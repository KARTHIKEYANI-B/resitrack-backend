package com.resitrack.dto;

import lombok.*;
import java.time.LocalDateTime;

public class SecurityGuardDTO {

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Request {
        private String name;
        private String phone;
        private String email;
        private String password;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Response {
        private Long          id;
        private String        name;
        private String        email;
        private String        phone;
        private boolean       active;
        private Long          createdByAdminId;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class UpdateRequest {
        private String  name;
        private String  phone;
        private String  email;
        private String  password;   // optional — applied only when non-blank
        private Boolean active;
    }
}