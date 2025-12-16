package com.caronrent.dto;


import lombok.Data;

@Data
public class VerifyOTPRequest {
    private String email;
    private String otp;
    private String password; // Add this field becuse i need password also
    private String role;
}
