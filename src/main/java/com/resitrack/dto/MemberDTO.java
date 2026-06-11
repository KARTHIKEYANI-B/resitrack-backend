package com.resitrack.dto;

import com.resitrack.entity.Member;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class MemberDTO {

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Response {
        private Long        id;
        private Long        residentId;
        private String      position;
        private String      positionDisplayName;
        private String      name;
        private String      photoUrl;
        private String      phoneNumber;
        private String      email;
        private LocalDate   joinedDate;
        private boolean     active;
        private boolean     placeholder;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static Response from(Member m) {
            return Response.builder()
                    .id(m.getId())
                    .residentId(m.getResident() != null ? m.getResident().getId() : null)
                    .position(m.getPosition().name())
                    .positionDisplayName(m.getPosition().getDisplayName())
                    .name(m.getName())
                    .photoUrl(m.getPhotoUrl())
                    .phoneNumber(m.getPhoneNumber())
                    .email(m.getEmail())
                    .joinedDate(m.getJoinedDate())
                    .active(m.isActive())
                    .placeholder(m.isPlaceholder())
                    .createdAt(m.getCreatedAt())
                    .updatedAt(m.getUpdatedAt())
                    .build();
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Request {
        private Long        residentId;   
        private String      position;     
        private String      name;
        private String      photoUrl;
        private String      phoneNumber;
        private String      email;
        private LocalDate   joinedDate;
        private boolean     active = true;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TransferPresidencyRequest {
        private Long newPresidentResidentId;  
        private Long newPresidentMemberId;    
    }
}