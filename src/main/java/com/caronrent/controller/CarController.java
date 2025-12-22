package com.caronrent.controller;

import com.caronrent.dto.CarDTO;
import com.caronrent.dto.CarResponseDTO;
import com.caronrent.dto.CarStatusDTO;
import com.caronrent.service.CarService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/cars")
public class CarController {
    private final CarService carService;

    public CarController(CarService carService) {
        this.carService = carService;
    }

    // Car Owner endpoints
    @PostMapping("/owner/add")
    @PreAuthorize("hasAnyRole('CAROWNER', 'ADMIN')")
    public ResponseEntity<CarResponseDTO> addCar(@RequestBody CarDTO carDTO, Authentication authentication) {
        String email = authentication.getName();
        CarResponseDTO car = carService.addCar(email, carDTO);
        return ResponseEntity.ok(car);
    }

    @GetMapping("/owner/my-cars")
    @PreAuthorize("hasAnyRole('CAROWNER', 'ADMIN')")
    public ResponseEntity<List<CarResponseDTO>> getMyCars(Authentication authentication) {
        String email = authentication.getName();
        List<CarResponseDTO> cars = carService.getMyCars(email);
        return ResponseEntity.ok(cars);
    }

    @PutMapping("/owner/{encryptedCarId}/status")
    @PreAuthorize("hasAnyRole('CAROWNER', 'ADMIN')")
    public ResponseEntity<CarResponseDTO> updateCarStatus(
            @PathVariable String encryptedCarId,
            @RequestBody CarStatusDTO statusDTO,
            Authentication authentication) {
        String email = authentication.getName();
        CarResponseDTO car = carService.updateCarStatus(encryptedCarId, email, statusDTO);
        return ResponseEntity.ok(car);
    }

    @DeleteMapping("/owner/{encryptedCarId}")
    @PreAuthorize("hasAnyRole('CAROWNER', 'ADMIN')")
    public ResponseEntity<String> deleteCar(
            @PathVariable String encryptedCarId,
            Authentication authentication) {
        String email = authentication.getName();
        carService.deleteCar(encryptedCarId, email);
        return ResponseEntity.ok("Car deleted successfully");
    }

    // Public endpoints (for users to browse cars)
    @GetMapping("/public/all")
    public ResponseEntity<List<CarResponseDTO>> getAllAvailableCars() {
        List<CarResponseDTO> cars = carService.getAllAvailableCars();
        return ResponseEntity.ok(cars);
    }

    // Get available cars by date range
    @GetMapping("/public/available")
    public ResponseEntity<List<CarResponseDTO>> getAvailableCarsByDate(
            @RequestParam String startDate,
            @RequestParam String endDate) {

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
            LocalDateTime start = LocalDateTime.parse(startDate, formatter);
            LocalDateTime end = LocalDateTime.parse(endDate, formatter);

            List<CarResponseDTO> cars = carService.getAvailableCarsByDate(start, end);
            return ResponseEntity.ok(cars);
        } catch (Exception e) {
            throw new RuntimeException("Invalid date format. Use ISO format: yyyy-MM-dd'T'HH:mm:ss");
        }
    }

    @GetMapping("/public/{encryptedCarId}")
    public ResponseEntity<CarResponseDTO> getCarById(@PathVariable String encryptedCarId) {
        CarResponseDTO car = carService.getCarById(encryptedCarId);
        return ResponseEntity.ok(car);
    }

    @GetMapping("/public/search/location")
    public ResponseEntity<List<CarResponseDTO>> searchByLocation(
            @RequestParam String location,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        List<CarResponseDTO> cars;

        if (startDate != null && endDate != null) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
                LocalDateTime start = LocalDateTime.parse(startDate, formatter);
                LocalDateTime end = LocalDateTime.parse(endDate, formatter);
                cars = carService.searchCarsByLocationAndDate(location, start, end);
            } catch (Exception e) {
                throw new RuntimeException("Invalid date format. Use ISO format: yyyy-MM-dd'T'HH:mm:ss");
            }
        } else {
            cars = carService.searchCarsByLocation(location);
        }
        return ResponseEntity.ok(cars);
    }

    @GetMapping("/public/search/brand")
    public ResponseEntity<List<CarResponseDTO>> searchByBrand(
            @RequestParam String brand,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        List<CarResponseDTO> cars;

        if (startDate != null && endDate != null) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
                LocalDateTime start = LocalDateTime.parse(startDate, formatter);
                LocalDateTime end = LocalDateTime.parse(endDate, formatter);
                cars = carService.searchCarsByBrandAndDate(brand, start, end);
            } catch (Exception e) {
                throw new RuntimeException("Invalid date format. Use ISO format: yyyy-MM-dd'T'HH:mm:ss");
            }
        } else {
            cars = carService.searchCarsByBrand(brand);
        }
        return ResponseEntity.ok(cars);
    }

    @GetMapping("/public/search/price")
    public ResponseEntity<List<CarResponseDTO>> searchByPriceRange(
            @RequestParam Double minPrice,
            @RequestParam Double maxPrice,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        List<CarResponseDTO> cars;

        if (startDate != null && endDate != null) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
                LocalDateTime start = LocalDateTime.parse(startDate, formatter);
                LocalDateTime end = LocalDateTime.parse(endDate, formatter);
                cars = carService.searchCarsByPriceRangeAndDate(minPrice, maxPrice, start, end);
            } catch (Exception e) {
                throw new RuntimeException("Invalid date format. Use ISO format: yyyy-MM-dd'T'HH:mm:ss");
            }
        } else {
            cars = carService.searchCarsByPriceRange(minPrice, maxPrice);
        }
        return ResponseEntity.ok(cars);
    }

}