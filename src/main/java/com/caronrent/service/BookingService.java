package com.caronrent.service;

import com.caronrent.dto.BookingRequestDTO;
import com.caronrent.entity.Booking;
import com.caronrent.entity.Car;
import com.caronrent.entity.User;
import com.caronrent.repo.BookingRepository;
import com.caronrent.repo.CarRepository;
import com.caronrent.repo.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class BookingService {
    private final BookingRepository bookingRepository;
    private final CarRepository carRepository;
    private final UserRepository userRepository;

    public BookingService(BookingRepository bookingRepository, CarRepository carRepository,
                          UserRepository userRepository) {
        this.bookingRepository = bookingRepository;
        this.carRepository = carRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Booking createBooking(String userEmail, BookingRequestDTO bookingRequest) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Car car = carRepository.findById(bookingRequest.getCarId())
                .orElseThrow(() -> new RuntimeException("Car not found"));

        // Check if car is available
        if (!car.getIsAvailable() || !car.getIsActive()) {
            throw new RuntimeException("Car is not available for booking");
        }

        // Check for overlapping bookings
        List<Booking> overlappingBookings = bookingRepository.findByCarAndStartDateBetweenOrEndDateBetween(
                car,
                bookingRequest.getStartDate(), bookingRequest.getEndDate(),
                bookingRequest.getStartDate(), bookingRequest.getEndDate()
        );

        boolean hasOverlap = overlappingBookings.stream()
                .anyMatch(b ->
                        b.getStatus().equals("CONFIRMED") ||
                                b.getStatus().equals("PENDING") &&
                                        !(bookingRequest.getEndDate().isBefore(b.getStartDate()) ||
                                                bookingRequest.getStartDate().isAfter(b.getEndDate()))
                );

        if (hasOverlap) {
            throw new RuntimeException("Car is already booked for the selected dates");
        }

        // Calculate total days and amount
        long days = ChronoUnit.DAYS.between(
                bookingRequest.getStartDate(),
                bookingRequest.getEndDate()
        );

        if (days <= 0) {
            throw new RuntimeException("End date must be after start date");
        }

        double totalAmount = days * car.getDailyRate();

        // Create booking
        Booking booking = new Booking();
        booking.setCar(car);
        booking.setUser(user);
        booking.setStartDate(bookingRequest.getStartDate());
        booking.setEndDate(bookingRequest.getEndDate());
        booking.setTotalDays((int) days);
        booking.setTotalAmount(totalAmount);
        booking.setSpecialRequests(bookingRequest.getSpecialRequests());
        booking.setStatus("PENDING");
        booking.setPaymentStatus("PENDING");

        return bookingRepository.save(booking);
    }

    public List<Booking> getUserBookings(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return bookingRepository.findByUser(user);
    }

    public List<Booking> getOwnerBookings(String ownerEmail) {
        User owner = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return bookingRepository.findByCarOwner(owner);
    }

    public Booking confirmBooking(Long bookingId, String ownerEmail) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        // Verify ownership
        if (!booking.getCar().getOwner().getEmail().equals(ownerEmail)) {
            throw new RuntimeException("You can only confirm bookings for your own cars");
        }

        // Check if car is still available
        if (!booking.getCar().getIsAvailable() || !booking.getCar().getIsActive()) {
            throw new RuntimeException("Car is no longer available");
        }

        booking.setStatus("CONFIRMED");
        return bookingRepository.save(booking);
    }

    public Booking cancelBooking(Long bookingId, String userEmail) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        // Verify user is the one who booked or the car owner
        if (!booking.getUser().getEmail().equals(userEmail) &&
                !booking.getCar().getOwner().getEmail().equals(userEmail)) {
            throw new RuntimeException("You can only cancel your own bookings or bookings for your cars");
        }

        booking.setStatus("CANCELLED");
        return bookingRepository.save(booking);
    }

    public Booking updatePaymentStatus(Long bookingId, String status) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        booking.setPaymentStatus(status);
        return bookingRepository.save(booking);
    }

    public Booking getBookingById(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));
    }
}