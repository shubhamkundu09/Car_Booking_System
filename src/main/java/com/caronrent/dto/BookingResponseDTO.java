package com.caronrent.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class BookingResponseDTO {
    private String id;
    private String carId;
    private String carBrand;
    private String carModel;
    private String userId;
    private String userEmail;
    private String ownerEmail;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Integer totalDays;
    private Double totalAmount;
    private String status;
    private String paymentStatus;
    private String paymentId;
    private String orderId;
    private Double amountPaid;
    private String specialRequests;
    // Add these three document fields to response
    private String drivingLicenseUrl;
    private String aadharCardUrl;
    private String policeVerificationUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime cancelledAt;
}