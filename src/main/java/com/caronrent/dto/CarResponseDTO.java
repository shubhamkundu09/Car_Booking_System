package com.caronrent.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CarResponseDTO {
    private String id;  // Encrypted or string representation
    private String brand;
    private String model;
    private Integer year;
    private String registrationNumber;
    private String color;
    private Double dailyRate;
    private String location;
    private String description;
    private Boolean isAvailable;
    private Boolean isActive;
    private List<String> imageUrls;
    private String ownerEmail;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}