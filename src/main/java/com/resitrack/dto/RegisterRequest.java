package com.resitrack.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.resitrack.entity.PropertyType;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[+]?[0-9]{10,13}$", message = "Invalid phone number")
    private String phone;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank(message = "Flat/Villa number is required")
    private String flatNumber;

    private String flatType;

    @NotNull(message = "Property type (FLAT or VILLA) is required")
    private PropertyType propertyType;

    @JsonAlias({ "sqFt", "squareFeet" })
    private Double sqFt;

    private Integer familyMembers;
    private Integer age;
    private String  vehicleDetails;
    private String  address;

    private String registerNumber;
}