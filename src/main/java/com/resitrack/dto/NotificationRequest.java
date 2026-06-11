package com.resitrack.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class NotificationRequest {
    private String     title;
    private String     message;
    private String     type;           

    private String     audience;       
    private String     recipientRole;  

    private Long       residentId;     

    private String     transactionId;
    private Long       paymentId;
    private BigDecimal paymentAmount;
    private String     paymentMethod;
    private String     flatNumber;
}