package com.caronrent.service;

import com.caronrent.dto.BookingRequestDTO;
import com.caronrent.dto.BookingResponseDTO;
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
import java.util.stream.Collectors;

@Service
public class BookingService {
    private final BookingRepository bookingRepository;
    private final CarRepository carRepository;
    private final UserRepository userRepository;
    private final PaymentService paymentService;
    private final IdEncryptionService idEncryptionService;

    public BookingService(BookingRepository bookingRepository, CarRepository carRepository,
                          UserRepository userRepository, PaymentService paymentService,
                          IdEncryptionService idEncryptionService) {
        this.bookingRepository = bookingRepository;
        this.carRepository = carRepository;
        this.userRepository = userRepository;
        this.paymentService = paymentService;
        this.idEncryptionService = idEncryptionService;
    }

    @Transactional
    public BookingResponseDTO createBooking(String userEmail, BookingRequestDTO bookingRequest) {
        Long carId = idEncryptionService.decryptId(bookingRequest.getCarId());
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new RuntimeException("Car not found"));

        // Check if car is available
        if (!car.getIsAvailable() || !car.getIsActive()) {
            throw new RuntimeException("Car is not available for booking");
        }

        // Check overlapping bookings
        List<Booking> overlappingBookings = bookingRepository.findOverlappingBookings(
                carId,
                bookingRequest.getStartDate(),
                bookingRequest.getEndDate()
        );

        if (!overlappingBookings.isEmpty()) {
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

        if (days > 30) {
            throw new RuntimeException("Maximum booking duration is 30 days");
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
        booking.setStatus("PAYMENT_PENDING");
        booking.setPaymentStatus("PENDING");
        booking.setAmountPaid(0.0);

        // Temporarily mark car as unavailable until payment is confirmed
        car.setIsAvailable(false);
        carRepository.save(car);

        Booking savedBooking = bookingRepository.save(booking);

        System.out.println("📝 Booking created with ID: " + savedBooking.getId());
        System.out.println("💰 Payment required: " + totalAmount);
        System.out.println("🚗 Car: " + car.getBrand() + " " + car.getModel());
        System.out.println("👤 User: " + userEmail);
        System.out.println("👑 Owner: " + car.getOwner().getEmail());

        return convertToResponseDTO(savedBooking);
    }

    public List<BookingResponseDTO> getUserBookings(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return bookingRepository.findByUser(user).stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    public List<BookingResponseDTO> getOwnerBookings(String ownerEmail) {
        User owner = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<BookingResponseDTO> bookings = bookingRepository.findByCarOwner(owner).stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());

        // Log for debugging
        System.out.println("👑 Owner bookings for: " + ownerEmail);
        bookings.forEach(b -> {
            System.out.println("   Booking ID: " + b.getId());
            System.out.println("   Status: " + b.getStatus());
            System.out.println("   Payment Status: " + b.getPaymentStatus());
            System.out.println("   Amount Paid: " + b.getAmountPaid());
        });

        return bookings;
    }

    @Transactional
    public BookingResponseDTO confirmBooking(String encryptedBookingId, String ownerEmail) {
        Long bookingId = idEncryptionService.decryptId(encryptedBookingId);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        // Verify ownership
        if (!booking.getCar().getOwner().getEmail().equals(ownerEmail)) {
            throw new RuntimeException("You can only confirm bookings for your own cars");
        }

        // Check if booking can be confirmed
        if (!"PAYMENT_CONFIRMED".equals(booking.getStatus())) {
            throw new RuntimeException("Booking cannot be confirmed. Payment must be completed first.");
        }

        if (!"PAID".equals(booking.getPaymentStatus())) {
            throw new RuntimeException("Payment not completed. Cannot confirm booking.");
        }

        // Update booking status
        booking.setStatus("CONFIRMED");
        booking.setConfirmedAt(LocalDateTime.now());

        // Car remains unavailable
        booking.getCar().setIsAvailable(false);

        Booking updatedBooking = bookingRepository.save(booking);

        System.out.println("✅ Booking confirmed: " + bookingId);
        System.out.println("   Payment: " + booking.getPaymentStatus());
        System.out.println("   Amount: " + booking.getAmountPaid());

        return convertToResponseDTO(updatedBooking);
    }

    @Transactional
    public BookingResponseDTO cancelBooking(String encryptedBookingId, String userEmail) {
        Long bookingId = idEncryptionService.decryptId(encryptedBookingId);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        // Verify user is the one who booked
        if (!booking.getUser().getEmail().equals(userEmail)) {
            throw new RuntimeException("You can only cancel your own bookings");
        }

        // Check if booking can be cancelled
        if ("CANCELLED".equals(booking.getStatus()) || "COMPLETED".equals(booking.getStatus())) {
            throw new RuntimeException("Booking cannot be cancelled");
        }

        // Different handling based on status
        if ("PAYMENT_PENDING".equals(booking.getStatus())) {
            // If payment not made yet, just cancel
            booking.setStatus("CANCELLED");
            booking.setPaymentStatus("CANCELLED");
            booking.setCancelledAt(LocalDateTime.now());

            // Make car available again
            booking.getCar().setIsAvailable(true);

            System.out.println("❌ Booking cancelled (payment pending): " + bookingId);

        } else if ("PAYMENT_CONFIRMED".equals(booking.getStatus()) || "CONFIRMED".equals(booking.getStatus())) {
            // Check if within cancellation window (24 hours before start)
            if (LocalDateTime.now().isAfter(booking.getStartDate().minusHours(24))) {
                throw new RuntimeException("Cannot cancel booking less than 24 hours before start");
            }

            booking.setStatus("CANCELLED");
            booking.setCancelledAt(LocalDateTime.now());

            // Make car available again
            booking.getCar().setIsAvailable(true);

            // Initiate refund if paid
            if ("PAID".equals(booking.getPaymentStatus())) {
                try {
                    paymentService.initiateRefund(bookingId);
                    booking.setPaymentStatus("REFUNDED");
                    System.out.println("💸 Refund initiated for booking: " + bookingId);
                } catch (Exception e) {
                    throw new RuntimeException("Booking cancelled but refund failed: " + e.getMessage());
                }
            }
        }

        Booking updatedBooking = bookingRepository.save(booking);
        return convertToResponseDTO(updatedBooking);
    }

    @Transactional
    public BookingResponseDTO cancelBookingByOwner(String encryptedBookingId, String ownerEmail) {
        Long bookingId = idEncryptionService.decryptId(encryptedBookingId);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        // Verify ownership
        if (!booking.getCar().getOwner().getEmail().equals(ownerEmail)) {
            throw new RuntimeException("You can only cancel bookings for your own cars");
        }

        // Check if booking can be cancelled by owner
        if ("CANCELLED".equals(booking.getStatus()) || "COMPLETED".equals(booking.getStatus())) {
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
                booking.setPaymentStatus("REFUNDED");
            } catch (Exception e) {
                throw new RuntimeException("Booking cancelled but refund failed: " + e.getMessage());
            }
        } else if ("PENDING".equals(booking.getPaymentStatus())) {
            booking.setPaymentStatus("CANCELLED");
        }

        Booking updatedBooking = bookingRepository.save(booking);
        return convertToResponseDTO(updatedBooking);
    }

    public BookingResponseDTO updatePaymentStatus(String encryptedBookingId, String status) {
        Long bookingId = idEncryptionService.decryptId(encryptedBookingId);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        booking.setPaymentStatus(status);

        // Update booking status based on payment
        if ("PAID".equals(status)) {
            booking.setStatus("PAYMENT_CONFIRMED");
            booking.setAmountPaid(booking.getTotalAmount());
        } else if ("FAILED".equals(status)) {
            booking.setStatus("CANCELLED");
            booking.getCar().setIsAvailable(true);
        } else if ("REFUNDED".equals(status)) {
            booking.setStatus("CANCELLED");
            booking.getCar().setIsAvailable(true);
        }

        Booking updatedBooking = bookingRepository.save(booking);
        return convertToResponseDTO(updatedBooking);
    }

    public BookingResponseDTO getBookingById(String encryptedBookingId) {
        Long bookingId = idEncryptionService.decryptId(encryptedBookingId);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));
        return convertToResponseDTO(booking);
    }

    private BookingResponseDTO convertToResponseDTO(Booking booking) {
        BookingResponseDTO dto = new BookingResponseDTO();
        dto.setId(idEncryptionService.encryptId(booking.getId()));
        dto.setCarId(idEncryptionService.encryptId(booking.getCar().getId()));
        dto.setCarBrand(booking.getCar().getBrand());
        dto.setCarModel(booking.getCar().getModel());
        dto.setUserId(idEncryptionService.encryptId(booking.getUser().getId()));
        dto.setUserEmail(booking.getUser().getEmail());
        dto.setOwnerEmail(booking.getCar().getOwner().getEmail());
        dto.setStartDate(booking.getStartDate());
        dto.setEndDate(booking.getEndDate());
        dto.setTotalDays(booking.getTotalDays());
        dto.setTotalAmount(booking.getTotalAmount());
        dto.setStatus(booking.getStatus());
        dto.setPaymentStatus(booking.getPaymentStatus());
        dto.setPaymentId(booking.getPaymentId());
        dto.setOrderId(booking.getOrderId());
        dto.setAmountPaid(booking.getAmountPaid());
        dto.setSpecialRequests(booking.getSpecialRequests());
        dto.setCreatedAt(booking.getCreatedAt());
        dto.setUpdatedAt(booking.getUpdatedAt());
        dto.setConfirmedAt(booking.getConfirmedAt());
        dto.setCancelledAt(booking.getCancelledAt());
        return dto;
    }
}