package com.caronrent.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class BookingRequestDTO {
    private Long carId;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String specialRequests;
}