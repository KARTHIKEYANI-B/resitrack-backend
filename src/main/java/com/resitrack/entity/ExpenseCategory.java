package com.resitrack.entity;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public enum ExpenseCategory {

    BUILDING_MAINTENANCE("Building Maintenance"),
    CCTV_EXPENSES("CCTV Expenses"),
    CULTURAL_OR_COMMUNITY_EVENTS("Cultural or Community Events"),
    ELECTRICITY_EXPENSES("Electricity Expenses"),
    EXPENSES("Expenses"),
    FILING_LEGAL_CONSULTATION_FEES("Filing & Legal Consultation Fees"),
    FREIGHT_AND_CARRIAGE("Freight and Carriage"),
    GENSET_DIESEL_EXPENSES("Genset Diesel Expenses"),
    GIFT_EXPENSES("Gift Expenses"),
    HARDWARE_SPARES_EXPENSES("Hardware & Spares Expenses"),
    LIFT_MAINTENANCE("Lift Maintenance"),
    MINOR_EXPENSES("Minor Expenses"),
    OFFICE_SUPPLIES_AND_PRINTING("Office Supplies and Printing"),
    PETROL_EXPENSES("Petrol Expenses"),
    PLUMBING_MAINTENANCE("Plumbing Maintenance"),
    POOJA_EXPENSES("Pooja Expenses"),
    PRINTING_STATIONERY_EXPENSES("Printing & Stationery Expenses"),
    RO_WATER("RO Water"),
    RO_WATER_MAINTENANCE_EXPENSES("RO Water Maintenance Expenses"),
    SECURITY_MAINTENANCE_SALARY("Security Maintenance Salary"),
    SECURITY_SYSTEM_CCTV("Security System CCTV"),
    SECURITY_SALARY("Security Salary"),
    STAFF_SALARY("Staff Salary"),
    STAFF_WELFARE("Staff Welfare"),
    STP_MAINTENANCE("STP Maintenance"),
    TELEPHONE_EXPENSES("Telephone Expenses"),
    WASTAGE_DISPOSAL_EXPENSES("Wastage Disposal Expenses"),
    BANK_CHARGES("Bank Charges"),
    COMPUTER_EXPENSES("Computer Expenses");

    private final String label;

    ExpenseCategory(String label) { this.label = label; }

    public String getLabel() { return label; }

    public static List<String> labels() {
        return java.util.Arrays.stream(values())
                .map(ExpenseCategory::getLabel)
                .toList();
    }

    public static Set<String> allowedLabels() {
        Set<String> s = new LinkedHashSet<>();
        for (ExpenseCategory c : values()) s.add(c.getLabel());
        return s;
    }

    public static boolean isValid(String label) {
        return label != null && allowedLabels().contains(label.trim());
    }
}