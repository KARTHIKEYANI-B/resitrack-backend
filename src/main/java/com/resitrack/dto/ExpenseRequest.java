package com.resitrack.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class ExpenseRequest {
    private String    expenseName;
    private String    category;
    private Double    amount;
    private LocalDate expenseDate;
    private String    paymentMethod;
    private String    vendorStatus;
    private String    description;
}
