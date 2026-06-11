package com.resitrack.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class ComplaintRequest {
    @JsonAlias("subject")
    private String title;

    @JsonAlias("message")
    private String description;
}