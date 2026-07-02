package com.resitrack.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TestEmailResponse {

    private boolean success;
    private String  message;

    public static TestEmailResponse success(String message) {
        return new TestEmailResponse(true, message);
    }

    public static TestEmailResponse failure(String message) {
        return new TestEmailResponse(false, message);
    }
}