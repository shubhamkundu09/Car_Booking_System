package com.caronrent.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class BookingRequestDTO {
    private String carId;  // Changed from Long
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String specialRequests;
    // Add these three mandatory document fields
    private String drivingLicenseUrl;
    private String aadharCardUrl;
    private String policeVerificationUrl;
}