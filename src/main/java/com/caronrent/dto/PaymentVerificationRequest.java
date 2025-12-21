package com.caronrent.dto;

import lombok.Data;

@Data
public class PaymentVerificationRequest {
    private String bookingId;  // Changed from Long
    private String razorpayPaymentId;
    private String razorpayOrderId;
    private String razorpaySignature;
}