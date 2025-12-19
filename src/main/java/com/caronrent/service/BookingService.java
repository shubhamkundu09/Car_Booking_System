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

        // Check for overlapping bookings (only CONFIRMED or PENDING bookings matter)
        List<Booking> overlappingBookings = bookingRepository.findByCarAndStartDateBetweenOrEndDateBetween(
                car,
                bookingRequest.getStartDate(), bookingRequest.getEndDate(),
                bookingRequest.getStartDate(), bookingRequest.getEndDate()
        );

        boolean hasOverlap = overlappingBookings.stream()
                .anyMatch(b ->
                        (b.getStatus().equals("CONFIRMED") || b.getStatus().equals("PENDING")) &&
                                !b.getStatus().equals("CANCELLED") &&
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

    @Transactional
    public Booking confirmBooking(Long bookingId, String ownerEmail) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        // Verify ownership
        if (!booking.getCar().getOwner().getEmail().equals(ownerEmail)) {
            throw new RuntimeException("You can only confirm bookings for your own cars");
        }

        // Check if booking is already cancelled
        if (booking.getStatus().equals("CANCELLED")) {
            throw new RuntimeException("Cannot confirm a cancelled booking");
        }

        // Check if booking is already completed
        if (booking.getStatus().equals("COMPLETED")) {
            throw new RuntimeException("Cannot confirm a completed booking");
        }

        // Check if booking is already confirmed
        if (booking.getStatus().equals("CONFIRMED")) {
            throw new RuntimeException("Booking is already confirmed");
        }

        // Check if car is still available and active
        Car car = booking.getCar();
        if (!car.getIsActive()) {
            throw new RuntimeException("Car is no longer active");
        }

        // Check if car is available for the booking dates
        List<Booking> overlappingBookings = bookingRepository.findByCarAndStartDateBetweenOrEndDateBetween(
                car,
                booking.getStartDate(), booking.getEndDate(),
                booking.getStartDate(), booking.getEndDate()
        );

        boolean hasOverlap = overlappingBookings.stream()
                .anyMatch(b ->
                        b.getId().equals(bookingId) ? false : // Skip current booking
                                (b.getStatus().equals("CONFIRMED") || b.getStatus().equals("PENDING")) &&
                                        !b.getStatus().equals("CANCELLED") &&
                                        !(booking.getEndDate().isBefore(b.getStartDate()) ||
                                                booking.getStartDate().isAfter(b.getEndDate()))
                );

        if (hasOverlap) {
            throw new RuntimeException("Car is already booked for these dates by another confirmed booking");
        }

        // Check if booking dates are still valid
        if (booking.getStartDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Cannot confirm a booking with past start date");
        }

        // Update booking status
        booking.setStatus("CONFIRMED");

        // IMPORTANT: Make car unavailable during the booking period
        // Note: We don't set car.setIsAvailable(false) globally because the car
        // should still be available for other time periods. Availability is
        // managed at the booking level, not at the car level.

        return bookingRepository.save(booking);
    }

    @Transactional
    public Booking cancelBooking(Long bookingId, String userEmail) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        // Verify user is the one who booked or the car owner
        boolean isUser = booking.getUser().getEmail().equals(userEmail);
        boolean isOwner = booking.getCar().getOwner().getEmail().equals(userEmail);

        if (!isUser && !isOwner) {
            throw new RuntimeException("You can only cancel your own bookings or bookings for your cars");
        }

        // Check if booking is already cancelled
        if (booking.getStatus().equals("CANCELLED")) {
            throw new RuntimeException("Booking is already cancelled");
        }

        // Check if booking is already completed
        if (booking.getStatus().equals("COMPLETED")) {
            throw new RuntimeException("Cannot cancel a completed booking");
        }

        // Additional checks for confirmed bookings
        if (booking.getStatus().equals("CONFIRMED")) {
            LocalDateTime now = LocalDateTime.now();

            // User cancellation policy (different from owner cancellation)
            if (isUser) {
                // Users must cancel at least 24 hours before start
                long hoursBeforeStart = ChronoUnit.HOURS.between(now, booking.getStartDate());

                if (hoursBeforeStart < 24) {
                    throw new RuntimeException("Confirmed bookings must be cancelled at least 24 hours before start time");
                }
            }

            // Owner cancellation - can cancel anytime but notify user
            if (isOwner) {
                // Owner can cancel but should provide reason (could be added to method)
                System.out.println("Warning: Car owner is cancelling a confirmed booking");
            }
        }

        // Update booking status
        String oldStatus = booking.getStatus();
        booking.setStatus("CANCELLED");

        Booking savedBooking = bookingRepository.save(booking);

        // IMPORTANT: If a confirmed booking is cancelled, the car becomes available again
        // for those dates. Other pending bookings can now be confirmed.
        if (oldStatus.equals("CONFIRMED")) {
            System.out.println("Confirmed booking cancelled. Car is now available for dates: " +
                    booking.getStartDate() + " to " + booking.getEndDate());
        }

        return savedBooking;
    }

    @Transactional
    public Booking updatePaymentStatus(Long bookingId, String status) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        // Don't allow payment status updates for cancelled bookings
        if (booking.getStatus().equals("CANCELLED")) {
            throw new RuntimeException("Cannot update payment status for cancelled booking");
        }

        // Don't allow payment status updates for completed bookings
        if (booking.getStatus().equals("COMPLETED")) {
            throw new RuntimeException("Cannot update payment status for completed booking");
        }

        booking.setPaymentStatus(status);
        return bookingRepository.save(booking);
    }

    public Booking getBookingById(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));
    }

    // Helper method to check if car is available for specific dates
    public boolean isCarAvailableForDates(Long carId, LocalDateTime startDate, LocalDateTime endDate) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new RuntimeException("Car not found"));

        if (!car.getIsAvailable() || !car.getIsActive()) {
            return false;
        }

        List<Booking> overlappingBookings = bookingRepository.findByCarAndStartDateBetweenOrEndDateBetween(
                car,
                startDate, endDate,
                startDate, endDate
        );

        return overlappingBookings.stream()
                .noneMatch(b ->
                        (b.getStatus().equals("CONFIRMED") || b.getStatus().equals("PENDING")) &&
                                !b.getStatus().equals("CANCELLED") &&
                                !(endDate.isBefore(b.getStartDate()) || startDate.isAfter(b.getEndDate()))
                );
    }

    // Method to automatically mark completed bookings
    @Transactional
    public void processCompletedBookings() {
        LocalDateTime now = LocalDateTime.now();
        List<Booking> activeBookings = bookingRepository.findByStatus("CONFIRMED");

        for (Booking booking : activeBookings) {
            if (booking.getEndDate().isBefore(now)) {
                booking.setStatus("COMPLETED");
                bookingRepository.save(booking);
                System.out.println("Booking " + booking.getId() + " marked as completed");
            }
        }
    }
}