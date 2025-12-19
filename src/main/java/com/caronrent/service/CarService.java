package com.caronrent.service;

import com.caronrent.dto.CarDTO;
import com.caronrent.dto.CarStatusDTO;
import com.caronrent.entity.Car;
import com.caronrent.entity.CarImage;
import com.caronrent.entity.User;
import com.caronrent.repo.CarRepository;
import com.caronrent.repo.CarImageRepository;
import com.caronrent.repo.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CarService {
    private final CarRepository carRepository;
    private final UserRepository userRepository;
    private final CarImageRepository carImageRepository;

    public CarService(CarRepository carRepository, UserRepository userRepository,
                      CarImageRepository carImageRepository) {
        this.carRepository = carRepository;
        this.userRepository = userRepository;
        this.carImageRepository = carImageRepository;
    }

    @Transactional
    public Car addCar(String ownerEmail, CarDTO carDTO) {
        User owner = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Check if user is a car owner
        if (!owner.getRoles().contains("ROLE_CAROWNER") && !owner.getRoles().contains("ROLE_ADMIN")) {
            throw new AccessDeniedException("Only car owners can add cars");
        }

        Car car = new Car();
        car.setOwner(owner);
        car.setBrand(carDTO.getBrand());
        car.setModel(carDTO.getModel());
        car.setYear(carDTO.getYear());
        car.setRegistrationNumber(carDTO.getRegistrationNumber());
        car.setColor(carDTO.getColor());
        car.setDailyRate(carDTO.getDailyRate());
        car.setLocation(carDTO.getLocation());
        car.setDescription(carDTO.getDescription());
        car.setIsAvailable(true);
        car.setIsActive(true);

        Car savedCar = carRepository.save(car);

        // Save images if provided
        if (carDTO.getImageUrls() != null && !carDTO.getImageUrls().isEmpty()) {
            for (int i = 0; i < carDTO.getImageUrls().size(); i++) {
                CarImage image = new CarImage();
                image.setCar(savedCar);
                image.setImageUrl(carDTO.getImageUrls().get(i));
                image.setIsPrimary(i == 0); // First image is primary
                carImageRepository.save(image);
            }
        }

        return savedCar;
    }

    public List<Car> getMyCars(String ownerEmail) {
        User owner = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return carRepository.findByOwner(owner);
    }

    public Car updateCarStatus(Long carId, String ownerEmail, CarStatusDTO statusDTO) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new RuntimeException("Car not found"));

        // Verify ownership
        if (!car.getOwner().getEmail().equals(ownerEmail)) {
            throw new AccessDeniedException("You can only update your own cars");
        }

        if (statusDTO.getIsActive() != null) {
            car.setIsActive(statusDTO.getIsActive());
        }

        if (statusDTO.getIsAvailable() != null) {
            car.setIsAvailable(statusDTO.getIsAvailable());
        }

        return carRepository.save(car);
    }

    public List<Car> getAllAvailableCars() {
        return carRepository.findByIsAvailableTrueAndIsActiveTrue();
    }

    public List<Car> searchCarsByLocation(String location) {
        return carRepository.findByLocationContainingIgnoreCaseAndIsAvailableTrueAndIsActiveTrue(location);
    }

    public List<Car> searchCarsByBrand(String brand) {
        return carRepository.findByBrandContainingIgnoreCaseAndIsAvailableTrueAndIsActiveTrue(brand);
    }

    public List<Car> searchCarsByPriceRange(Double minPrice, Double maxPrice) {
        return carRepository.findByDailyRateBetweenAndIsAvailableTrueAndIsActiveTrue(minPrice, maxPrice);
    }

    public Car getCarById(Long carId) {
        return carRepository.findById(carId)
                .orElseThrow(() -> new RuntimeException("Car not found"));
    }

    @Transactional
    public void deleteCar(Long carId, String ownerEmail) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new RuntimeException("Car not found"));

        // Verify ownership
        if (!car.getOwner().getEmail().equals(ownerEmail)) {
            throw new AccessDeniedException("You can only delete your own cars");
        }

        // Check if there are any active bookings
        if (!car.getBookings().isEmpty()) {
            boolean hasActiveBookings = car.getBookings().stream()
                    .anyMatch(b -> b.getStatus().equals("PENDING") || b.getStatus().equals("CONFIRMED"));

            if (hasActiveBookings) {
                throw new RuntimeException("Cannot delete car with active bookings");
            }
        }

        // Delete associated images first
        carImageRepository.deleteByCar(car);

        // Then delete the car
        carRepository.delete(car);
    }
}