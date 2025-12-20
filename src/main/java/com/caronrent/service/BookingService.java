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
    private final PaymentService paymentService;

    public BookingService(BookingRepository bookingRepository, CarRepository carRepository,
                          UserRepository userRepository, PaymentService paymentService) {
        this.bookingRepository = bookingRepository;
        this.carRepository = carRepository;
        this.userRepository = userRepository;
        this.paymentService = paymentService;
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

        // ========== UPDATED: Check overlapping bookings for THIS car only ==========
        // Previously was checking all cars from the owner, now only checks the specific car
        List<Booking> overlappingBookings = bookingRepository.findOverlappingBookings(
                car.getId(),  // Only check this specific car
                bookingRequest.getStartDate(),
                bookingRequest.getEndDate()
        );

        // Check if there are any overlapping bookings for THIS CAR
        if (!overlappingBookings.isEmpty()) {
            throw new RuntimeException("Car is already booked for the selected dates");
        }
        // ========== END UPDATE ==========

        // Calculate total days and amount
        long days = ChronoUnit.DAYS.between(
                bookingRequest.getStartDate(),
                bookingRequest.getEndDate()
        );

        if (days <= 0) {
            throw new RuntimeException("End date must be after start date");
        }

        if (days > 30) {
            throw new RuntimeException("Maximum booking duration is 30 days");
        }

        double totalAmount = days * car.getDailyRate();

        // Mark car as unavailable temporarily
        car.setIsAvailable(false);
        carRepository.save(car);

        // Create booking
        Booking booking = new Booking();
        booking.setCar(car);
        booking.setUser(user);
        booking.setStartDate(bookingRequest.getStartDate());
        booking.setEndDate(bookingRequest.getEndDate());
        booking.setTotalDays((int) days);
        booking.setTotalAmount(totalAmount);
        booking.setSpecialRequests(bookingRequest.getSpecialRequests());
        booking.setStatus("PAYMENT_PENDING");
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

    @Transactional
    public Booking confirmBooking(Long bookingId, String ownerEmail) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        // Verify ownership
        if (!booking.getCar().getOwner().getEmail().equals(ownerEmail)) {
            throw new RuntimeException("You can only confirm bookings for your own cars");
        }

        // Check if booking can be confirmed
        if (!booking.canBeConfirmed()) {
            throw new RuntimeException("Booking cannot be confirmed. Check payment status.");
        }

        // Update booking status
        booking.setStatus("CONFIRMED");
        booking.setConfirmedAt(LocalDateTime.now());

        // Car remains unavailable
        booking.getCar().setIsAvailable(false);

        return bookingRepository.save(booking);
    }

    @Transactional
    public Booking cancelBooking(Long bookingId, String userEmail) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        // Verify user is the one who booked
        if (!booking.getUser().getEmail().equals(userEmail)) {
            throw new RuntimeException("You can only cancel your own bookings");
        }

        // Check if booking can be cancelled
        if (!booking.canBeCancelled()) {
            throw new RuntimeException("Booking cannot be cancelled");
        }

        // Different handling based on status
        if ("PAYMENT_PENDING".equals(booking.getStatus())) {
            // If payment not made yet, just cancel
            booking.setStatus("CANCELLED");
            booking.setCancelledAt(LocalDateTime.now());

            // Make car available again
            booking.getCar().setIsAvailable(true);
        } else if ("CONFIRMED".equals(booking.getStatus())) {
            // If confirmed, check if within cancellation window (24 hours before start)
            if (LocalDateTime.now().isAfter(booking.getStartDate().minusHours(24))) {
                throw new RuntimeException("Cannot cancel confirmed booking less than 24 hours before start");
            }

            booking.setStatus("CANCELLED");
            booking.setCancelledAt(LocalDateTime.now());

            // Make car available again
            booking.getCar().setIsAvailable(true);

            // Initiate refund if paid
            if ("PAID".equals(booking.getPaymentStatus())) {
                try {
                    paymentService.initiateRefund(bookingId);
                } catch (Exception e) {
                    throw new RuntimeException("Booking cancelled but refund failed: " + e.getMessage());
                }
            }
        }

        return bookingRepository.save(booking);
    }

    @Transactional
    public Booking cancelBookingByOwner(Long bookingId, String ownerEmail) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        // Verify ownership
        if (!booking.getCar().getOwner().getEmail().equals(ownerEmail)) {
            throw new RuntimeException("You can only cancel bookings for your own cars");
        }

        // Check if booking can be cancelled by owner
        if (!booking.canBeCancelled() || "CANCELLED".equals(booking.getStatus())) {
            throw new RuntimeException("Booking cannot be cancelled");
        }

        // Owner can only cancel before confirmation
        if ("CONFIRMED".equals(booking.getStatus())) {
            throw new RuntimeException("Cannot cancel confirmed booking. Contact admin.");
        }

        booking.setStatus("CANCELLED");
        booking.setCancelledAt(LocalDateTime.now());

        // Make car available again
        booking.getCar().setIsAvailable(true);

        // Initiate refund if paid
        if ("PAID".equals(booking.getPaymentStatus())) {
            try {
                paymentService.initiateRefund(bookingId);
            } catch (Exception e) {
                throw new RuntimeException("Booking cancelled but refund failed: " + e.getMessage());
            }
        }

        return bookingRepository.save(booking);
    }

    public Booking updatePaymentStatus(Long bookingId, String status) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        booking.setPaymentStatus(status);

        // If payment fails, make car available again
        if ("FAILED".equals(status)) {
            booking.setStatus("CANCELLED");
            booking.getCar().setIsAvailable(true);
        }

        return bookingRepository.save(booking);
    }

    public Booking getBookingById(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));
    }

    public List<Booking> getActiveBookingsForCar(Long carId) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new RuntimeException("Car not found"));

        return bookingRepository.findByCar(car).stream()
                .filter(b -> !"CANCELLED".equals(b.getStatus()) && !"COMPLETED".equals(b.getStatus()))
                .toList();
    }
}