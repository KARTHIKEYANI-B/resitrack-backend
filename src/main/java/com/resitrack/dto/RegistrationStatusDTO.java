package com.resitrack.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistrationStatusDTO {

    private String name;
    private String email;
    private String status;          
    private String registrationDate;
    private String rejectedReason;
    private String approvedAt;
    private String flatNumber;
    private String flatType;
}
