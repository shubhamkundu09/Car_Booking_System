package com.caronrent.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;

@Data
public class BookingRequestDTO {
    private String carId;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String specialRequests;
    // Change these to MultipartFile for file upload
    private MultipartFile drivingLicense;
    private MultipartFile aadharCard;
    private MultipartFile policeVerification;
}