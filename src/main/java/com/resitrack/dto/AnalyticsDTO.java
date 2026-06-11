package com.resitrack.dto;

import lombok.*;
import java.util.List;
import java.util.Map;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AnalyticsDTO {

    private Integer year;
    private Integer month;

    private Double  totalRevenue;
    private Double  totalExpenses;
    private Double  netBalance;
    private Double  pendingDues;
    private Double  collectionRate;

    private Double  bankCollection;
    private Double  cashCollection;

    private Integer paidCount;
    private Integer unpaidCount;
    private Integer totalResidents;
    private Integer occupiedFlats;

    private Double  revenueGrowth;
    private Double  expenseGrowth;

    private List<MonthlyChartDTO> monthlyChart;

    private List<Map<String, Object>> expenseCategories;
}