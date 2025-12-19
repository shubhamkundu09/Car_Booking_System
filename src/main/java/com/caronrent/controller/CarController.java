package com.caronrent.controller;

import com.caronrent.dto.CarDTO;
import com.caronrent.dto.CarStatusDTO;
import com.caronrent.entity.Car;
import com.caronrent.service.CarService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
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
    public ResponseEntity<Car> addCar(@RequestBody CarDTO carDTO, Authentication authentication) {
        String email = authentication.getName();
        Car car = carService.addCar(email, carDTO);
        return ResponseEntity.ok(car);
    }

    @GetMapping("/owner/my-cars")
    @PreAuthorize("hasAnyRole('CAROWNER', 'ADMIN')")
    public ResponseEntity<List<Car>> getMyCars(Authentication authentication) {
        String email = authentication.getName();
        List<Car> cars = carService.getMyCars(email);
        return ResponseEntity.ok(cars);
    }

    @PutMapping("/owner/{carId}/status")
    @PreAuthorize("hasAnyRole('CAROWNER', 'ADMIN')")
    public ResponseEntity<Car> updateCarStatus(
            @PathVariable Long carId,
            @RequestBody CarStatusDTO statusDTO,
            Authentication authentication) {
        String email = authentication.getName();
        Car car = carService.updateCarStatus(carId, email, statusDTO);
        return ResponseEntity.ok(car);
    }

    @DeleteMapping("/owner/{carId}")
    @PreAuthorize("hasAnyRole('CAROWNER', 'ADMIN')")
    public ResponseEntity<String> deleteCar(
            @PathVariable Long carId,
            Authentication authentication) {
        String email = authentication.getName();
        carService.deleteCar(carId, email);
        return ResponseEntity.ok("Car deleted successfully");
    }

    // Public endpoints (for users to browse cars)
    @GetMapping("/public/all")
    public ResponseEntity<List<Car>> getAllAvailableCars() {
        List<Car> cars = carService.getAllAvailableCars();
        return ResponseEntity.ok(cars);
    }

    @GetMapping("/public/{carId}")
    public ResponseEntity<Car> getCarById(@PathVariable Long carId) {
        Car car = carService.getCarById(carId);
        return ResponseEntity.ok(car);
    }

    @GetMapping("/public/search/location")
    public ResponseEntity<List<Car>> searchByLocation(@RequestParam String location) {
        List<Car> cars = carService.searchCarsByLocation(location);
        return ResponseEntity.ok(cars);
    }

    @GetMapping("/public/search/brand")
    public ResponseEntity<List<Car>> searchByBrand(@RequestParam String brand) {
        List<Car> cars = carService.searchCarsByBrand(brand);
        return ResponseEntity.ok(cars);
    }

    @GetMapping("/public/search/price")
    public ResponseEntity<List<Car>> searchByPriceRange(
            @RequestParam Double minPrice,
            @RequestParam Double maxPrice) {
        List<Car> cars = carService.searchCarsByPriceRange(minPrice, maxPrice);
        return ResponseEntity.ok(cars);
    }
}