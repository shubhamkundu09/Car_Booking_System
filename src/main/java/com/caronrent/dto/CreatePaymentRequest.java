package com.caronrent.dto;

import lombok.Data;

@Data
public class CreatePaymentRequest {
    private String bookingId;  // Changed from Long
    private Double amount;
    private String currency = "INR";
}