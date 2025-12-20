package com.caronrent.dto;

import lombok.Data;

@Data
public class PaymentVerificationRequest {
    private Long bookingId;
    private String razorpayPaymentId;
    private String razorpayOrderId;
    private String razorpaySignature;
}