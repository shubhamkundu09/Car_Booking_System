package com.caronrent.service;

import com.caronrent.dto.CarDTO;
import com.caronrent.dto.CarResponseDTO;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CarService {
    private final CarRepository carRepository;
    private final UserRepository userRepository;
    private final CarImageRepository carImageRepository;
    private final IdEncryptionService idEncryptionService;

    public CarService(CarRepository carRepository, UserRepository userRepository,
                      CarImageRepository carImageRepository, IdEncryptionService idEncryptionService) {
        this.carRepository = carRepository;
        this.userRepository = userRepository;
        this.carImageRepository = carImageRepository;
        this.idEncryptionService = idEncryptionService;
    }

    @Transactional
    public CarResponseDTO addCar(String ownerEmail, CarDTO carDTO) {
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

        return convertToResponseDTO(savedCar);
    }

    public List<CarResponseDTO> getMyCars(String ownerEmail) {
        User owner = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return carRepository.findByOwner(owner).stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    public CarResponseDTO updateCarStatus(String encryptedCarId, String ownerEmail, CarStatusDTO statusDTO) {
        Long carId = idEncryptionService.decryptId(encryptedCarId);
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

        Car updatedCar = carRepository.save(car);
        return convertToResponseDTO(updatedCar);
    }

    public List<CarResponseDTO> getAllAvailableCars() {
        List<Car> cars = carRepository.findByIsAvailableTrueAndIsActiveTrue();

        return cars.stream()
                .filter(car -> car.getBookings().stream()
                        .noneMatch(booking ->
                                booking.overlapsWith(LocalDateTime.now(), LocalDateTime.now().plusDays(1)) &&
                                        !"CANCELLED".equals(booking.getStatus()) &&
                                        !"COMPLETED".equals(booking.getStatus())
                        ))
                .map(this::convertToResponseDTO)
                .toList();
    }

    public List<CarResponseDTO> getAvailableCarsByDate(LocalDateTime startDate, LocalDateTime endDate) {
        List<Car> cars = carRepository.findByIsAvailableTrueAndIsActiveTrue();

        return cars.stream()
                .filter(car -> isCarAvailableForDates(car, startDate, endDate))
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    private boolean isCarAvailableForDates(Car car, LocalDateTime startDate, LocalDateTime endDate) {
        return car.getBookings().stream()
                .noneMatch(booking ->
                        booking.overlapsWith(startDate, endDate) &&
                                !"CANCELLED".equals(booking.getStatus()) &&
                                !"COMPLETED".equals(booking.getStatus())
                );
    }

    public List<CarResponseDTO> searchCarsByLocation(String location) {
        return carRepository.findByLocationContainingIgnoreCaseAndIsAvailableTrueAndIsActiveTrue(location).stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    public List<CarResponseDTO> searchCarsByLocationAndDate(String location, LocalDateTime startDate, LocalDateTime endDate) {
        List<Car> cars = carRepository.findByLocationContainingIgnoreCaseAndIsAvailableTrueAndIsActiveTrue(location);

        return cars.stream()
                .filter(car -> isCarAvailableForDates(car, startDate, endDate))
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    public List<CarResponseDTO> searchCarsByBrand(String brand) {
        return carRepository.findByBrandContainingIgnoreCaseAndIsAvailableTrueAndIsActiveTrue(brand).stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    public List<CarResponseDTO> searchCarsByBrandAndDate(String brand, LocalDateTime startDate, LocalDateTime endDate) {
        List<Car> cars = carRepository.findByBrandContainingIgnoreCaseAndIsAvailableTrueAndIsActiveTrue(brand);

        return cars.stream()
                .filter(car -> isCarAvailableForDates(car, startDate, endDate))
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    public List<CarResponseDTO> searchCarsByPriceRange(Double minPrice, Double maxPrice) {
        return carRepository.findByDailyRateBetweenAndIsAvailableTrueAndIsActiveTrue(minPrice, maxPrice).stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    public List<CarResponseDTO> searchCarsByPriceRangeAndDate(Double minPrice, Double maxPrice, LocalDateTime startDate, LocalDateTime endDate) {
        List<Car> cars = carRepository.findByDailyRateBetweenAndIsAvailableTrueAndIsActiveTrue(minPrice, maxPrice);

        return cars.stream()
                .filter(car -> isCarAvailableForDates(car, startDate, endDate))
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    public CarResponseDTO getCarById(String encryptedCarId) {
        Long carId = idEncryptionService.decryptId(encryptedCarId);
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new RuntimeException("Car not found"));

        return convertToResponseDTO(car);
    }

    @Transactional
    public void deleteCar(String encryptedCarId, String ownerEmail) {
        Long carId = idEncryptionService.decryptId(encryptedCarId);
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new RuntimeException("Car not found"));

        // Verify ownership
        if (!car.getOwner().getEmail().equals(ownerEmail)) {
            throw new AccessDeniedException("You can only delete your own cars");
        }

        // Check if there are any active bookings
        if (!car.getBookings().isEmpty()) {
            boolean hasActiveBookings = car.getBookings().stream()
                    .anyMatch(b -> b.getStatus().equals("CONFIRMED"));

            if (hasActiveBookings) {
                throw new RuntimeException("Cannot delete car with confirmed bookings");
            }
        }

        // Delete associated images first
        carImageRepository.deleteByCar(car);

        // Then delete the car
        carRepository.delete(car);
    }

    private CarResponseDTO convertToResponseDTO(Car car) {
        CarResponseDTO dto = new CarResponseDTO();
        dto.setId(idEncryptionService.encryptId(car.getId()));
        dto.setBrand(car.getBrand());
        dto.setModel(car.getModel());
        dto.setYear(car.getYear());
        dto.setRegistrationNumber(car.getRegistrationNumber());
        dto.setColor(car.getColor());
        dto.setDailyRate(car.getDailyRate());
        dto.setLocation(car.getLocation());
        dto.setDescription(car.getDescription());
        dto.setIsAvailable(car.getIsAvailable());
        dto.setIsActive(car.getIsActive());
        dto.setOwnerEmail(car.getOwner().getEmail());
        dto.setCreatedAt(car.getCreatedAt());
        dto.setUpdatedAt(car.getUpdatedAt());

        // Set image URLs
        if (car.getImages() != null) {
            dto.setImageUrls(car.getImages().stream()
                    .map(CarImage::getImageUrl)
                    .collect(Collectors.toList()));
        }

        return dto;
    }
}