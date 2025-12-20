package com.caronrent.dto;

import lombok.Data;

@Data
public class CreatePaymentRequest {
    private Long bookingId;
    private Double amount;
    private String currency = "INR";
}