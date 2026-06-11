package com.resitrack.dto;

import lombok.*;

@Data @AllArgsConstructor @NoArgsConstructor @Builder
public class MonthlyChartDTO {
    private String month;
    private Double income;
    private Double expense;
    private Double balance;
}
