package com.resitrack.dto;

import com.resitrack.entity.Complaint;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ComplaintResponseDTO {
    private Long          id;
    private String        title;
    private String        description;
    private Long          residentId;
    private String        residentName;
    private String        flatNumber;
    private String        flatType;
    private String        status;
    private String        adminReply;
    private Long          notificationId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ComplaintResponseDTO from(Complaint c) {
        return ComplaintResponseDTO.builder()
                .id(c.getId())
                .title(c.getTitle())
                .description(c.getDescription())
                .residentId(c.getResidentId())
                .residentName(c.getResidentName())
                .flatNumber(c.getFlatNumber())
                .flatType(c.getFlatType())
                .status(c.getStatus().name())
                .adminReply(c.getAdminReply())
                .notificationId(c.getNotificationId())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}