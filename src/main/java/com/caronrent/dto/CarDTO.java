package com.caronrent.dto;

import lombok.Data;
import java.util.List;

@Data
public class CarDTO {
    private String brand;
    private String model;
    private Integer year;
    private String registrationNumber;
    private String color;
    private Double dailyRate;
    private String location;
    private String description;
    private List<String> imageUrls;
}