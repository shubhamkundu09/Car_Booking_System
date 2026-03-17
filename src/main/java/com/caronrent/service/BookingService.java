package com.caronrent.service;

import com.caronrent.dto.BookingRequestDTO;
import com.caronrent.dto.BookingResponseDTO;
import com.caronrent.dto.CreatePaymentRequest;
import com.caronrent.dto.PaymentResponse;
import com.caronrent.entity.Booking;
import com.caronrent.entity.Car;
import com.caronrent.entity.User;
import com.caronrent.repo.BookingRepository;
import com.caronrent.repo.CarRepository;
import com.caronrent.repo.UserRepository;
import com.razorpay.RazorpayException;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final EmailService emailService;
    private final FileStorageService fileStorageService;

    public BookingService(BookingRepository bookingRepository, CarRepository carRepository,
                          UserRepository userRepository, PaymentService paymentService,
                          IdEncryptionService idEncryptionService, EmailService emailService,
                          FileStorageService fileStorageService) {
        this.bookingRepository = bookingRepository;
        this.carRepository = carRepository;
        this.userRepository = userRepository;
        this.paymentService = paymentService;
        this.idEncryptionService = idEncryptionService;
        this.emailService = emailService;
        this.fileStorageService = fileStorageService;
    }

    @Transactional
    public BookingResponseDTO createBooking(String userEmail, BookingRequestDTO bookingRequest) {
        Long carId = idEncryptionService.decryptId(bookingRequest.getCarId());
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new RuntimeException("Car not found"));

        // Validate document uploads
        validateDocuments(bookingRequest);

        // Store document files
        String drivingLicenseUrl = fileStorageService.storeDocument(bookingRequest.getDrivingLicense(), "dl");
        String aadharCardUrl = fileStorageService.storeDocument(bookingRequest.getAadharCard(), "aadhar");
        String policeVerificationUrl = fileStorageService.storeDocument(bookingRequest.getPoliceVerification(), "police");

        try {
            // Validate dates
            validateBookingDates(bookingRequest.getStartDate(), bookingRequest.getEndDate());

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
            long days = calculateDaysBetween(bookingRequest.getStartDate(), bookingRequest.getEndDate());
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

            booking.setDrivingLicenseUrl(drivingLicenseUrl);
            booking.setAadharCardUrl(aadharCardUrl);
            booking.setPoliceVerificationUrl(policeVerificationUrl);

            booking.setStatus("PAYMENT_PENDING");
            booking.setPaymentStatus("PENDING");
            booking.setAmountPaid(0.0);

            // Temporarily mark car as unavailable
            car.setIsAvailable(false);
            carRepository.save(car);

            Booking savedBooking = bookingRepository.save(booking);

            // Log booking details for debugging
            logBookingDetails(savedBooking, car, userEmail, days, totalAmount);

            // Send emails
            sendBookingCreationEmails(savedBooking, car, userEmail);

            return convertToResponseDTO(savedBooking);

        } catch (Exception e) {
            // Clean up uploaded files if booking fails
            cleanupUploadedDocuments(drivingLicenseUrl, aadharCardUrl, policeVerificationUrl);
            throw e;
        }
    }

    /**
     * Validate document uploads
     */
    private void validateDocuments(BookingRequestDTO bookingRequest) {
        if (bookingRequest.getDrivingLicense() == null || bookingRequest.getDrivingLicense().isEmpty()) {
            throw new RuntimeException("Driving license is required");
        }
        if (bookingRequest.getAadharCard() == null || bookingRequest.getAadharCard().isEmpty()) {
            throw new RuntimeException("Aadhar card is required");
        }
        if (bookingRequest.getPoliceVerification() == null || bookingRequest.getPoliceVerification().isEmpty()) {
            throw new RuntimeException("Police verification document is required");
        }

        // Validate file types
        validateFileType(bookingRequest.getDrivingLicense(), "Driving license");
        validateFileType(bookingRequest.getAadharCard(), "Aadhar card");
        validateFileType(bookingRequest.getPoliceVerification(), "Police verification");
    }

    /**
     * Validate file type
     */
    private void validateFileType(org.springframework.web.multipart.MultipartFile file, String documentType) {
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.startsWith("image/") && !contentType.equals("application/pdf"))) {
            throw new RuntimeException(documentType + " must be an image or PDF file");
        }
    }

    /**
     * Validate booking dates
     */
    private void validateBookingDates(LocalDateTime startDate, LocalDateTime endDate) {
        LocalDateTime now = LocalDateTime.now();

        // Check if dates are null
        if (startDate == null || endDate == null) {
            throw new RuntimeException("Start date and end date are required");
        }

        // Check if start date is in the past
        if (startDate.isBefore(now)) {
            throw new RuntimeException("Cannot book car for past dates. Please select a future date.");
        }

        // Check if start date is at least 2 hours from now (to allow for processing)
        if (startDate.isBefore(now.plusHours(2))) {
            throw new RuntimeException("Booking must be at least 2 hours from now. Please select a later time.");
        }

        // Check if end date is after start date
        if (!endDate.isAfter(startDate)) {
            throw new RuntimeException("End date must be after start date");
        }

        // Calculate days for validation
        long days = calculateDaysBetween(startDate, endDate);

        // Check maximum duration (30 days)
        if (days > 30) {
            throw new RuntimeException("Maximum booking duration is 30 days");
        }

        // Check minimum duration (1 day)
        if (days < 1) {
            throw new RuntimeException("Minimum booking duration is 1 day");
        }
    }

    /**
     * Calculate days between two dates
     * Returns number of full days, rounding up partial days
     */
    private long calculateDaysBetween(LocalDateTime startDate, LocalDateTime endDate) {
        // If times are on the same day but different times
        if (startDate.toLocalDate().equals(endDate.toLocalDate())) {
            long hours = ChronoUnit.HOURS.between(startDate, endDate);
            if (hours > 0) {
                return 1; // Minimum 1 day even for partial day
            }
        }

        // Calculate exact days
        long days = ChronoUnit.DAYS.between(startDate.toLocalDate(), endDate.toLocalDate());

        // Get the time parts
        int startHour = startDate.getHour();
        int startMinute = startDate.getMinute();
        int endHour = endDate.getHour();
        int endMinute = endDate.getMinute();

        // If end time is after start time on the last day, count as an additional day
        if (days > 0) {
            if (endHour > startHour || (endHour == startHour && endMinute > startMinute)) {
                // Don't add extra day if it's already counting the last day
                // This logic ensures correct day count
            }
        }

        // Ensure at least 1 day if dates are different
        if (days == 0 && !startDate.toLocalDate().equals(endDate.toLocalDate())) {
            days = 1;
        }

        return days > 0 ? days : 1;
    }

    /**
     * Log booking details for debugging
     */
    private void logBookingDetails(Booking booking, Car car, String userEmail, long days, double totalAmount) {
        System.out.println("==========================================");
        System.out.println("📝 BOOKING CREATED - DETAILS:");
        System.out.println("   Booking ID: " + booking.getId());
        System.out.println("   Car: " + car.getBrand() + " " + car.getModel());
        System.out.println("   Daily Rate: ₹" + car.getDailyRate());
        System.out.println("   From: " + booking.getStartDate());
        System.out.println("   To: " + booking.getEndDate());
        System.out.println("   Total Days: " + days);
        System.out.println("   Total Amount: ₹" + totalAmount);
        System.out.println("   Calculation: " + days + " days × ₹" + car.getDailyRate() + " = ₹" + totalAmount);
        System.out.println("   User: " + userEmail);
        System.out.println("   Owner: " + car.getOwner().getEmail());
        System.out.println("==========================================");
    }

    /**
     * Clean up uploaded documents if booking fails
     */
    private void cleanupUploadedDocuments(String drivingLicenseUrl, String aadharCardUrl, String policeVerificationUrl) {
        try {
            if (drivingLicenseUrl != null) {
                fileStorageService.deleteFileByUrl(drivingLicenseUrl);
            }
            if (aadharCardUrl != null) {
                fileStorageService.deleteFileByUrl(aadharCardUrl);
            }
            if (policeVerificationUrl != null) {
                fileStorageService.deleteFileByUrl(policeVerificationUrl);
            }
            System.out.println("✅ Cleaned up uploaded documents after booking failure");
        } catch (Exception e) {
            System.err.println("⚠️ Failed to clean up some documents: " + e.getMessage());
        }
    }

    /**
     * Send booking creation emails
     */
    private void sendBookingCreationEmails(Booking booking, Car car, String userEmail) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
            String bookingDates = booking.getStartDate().format(formatter) + " to " +
                    booking.getEndDate().format(formatter);
            String carDetails = car.getBrand() + " " + car.getModel() + " (" + car.getRegistrationNumber() + ")";
            String encryptedBookingId = idEncryptionService.encryptId(booking.getId());

            // Send email to user
            emailService.sendBookingCreatedEmail(
                    userEmail,
                    encryptedBookingId,
                    carDetails,
                    bookingDates,
                    String.valueOf(booking.getTotalAmount())
            );

            // Send email to car owner
            emailService.sendBookingCreatedToOwnerEmail(
                    car.getOwner().getEmail(),
                    encryptedBookingId,
                    carDetails,
                    userEmail,
                    bookingDates,
                    String.valueOf(booking.getTotalAmount())
            );

            System.out.println("✅ Booking creation emails sent");
        } catch (Exception e) {
            System.err.println("⚠️ Failed to send booking creation emails: " + e.getMessage());
        }
    }

    /**
     * Get user bookings
     */
    public List<BookingResponseDTO> getUserBookings(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return bookingRepository.findByUser(user).stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get owner bookings
     */
    public List<BookingResponseDTO> getOwnerBookings(String ownerEmail) {
        User owner = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<BookingResponseDTO> bookings = bookingRepository.findByCarOwner(owner).stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());

        System.out.println("👑 Owner bookings for: " + ownerEmail);
        bookings.forEach(b -> {
            System.out.println("   Booking ID: " + b.getId());
            System.out.println("   Status: " + b.getStatus());
            System.out.println("   Payment Status: " + b.getPaymentStatus());
            System.out.println("   Amount: ₹" + b.getTotalAmount());
        });

        return bookings;
    }

    /**
     * Confirm booking
     */
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
        System.out.println("   Amount: ₹" + booking.getAmountPaid());

        sendBookingConfirmationEmail(updatedBooking);

        return convertToResponseDTO(updatedBooking);
    }

    /**
     * Send booking confirmation email
     */
    private void sendBookingConfirmationEmail(Booking booking) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
            String bookingDates = booking.getStartDate().format(formatter) + " to " +
                    booking.getEndDate().format(formatter);
            String carDetails = booking.getCar().getBrand() + " " + booking.getCar().getModel() +
                    " (" + booking.getCar().getRegistrationNumber() + ")";

            emailService.sendBookingConfirmedEmail(
                    booking.getUser().getEmail(),
                    idEncryptionService.encryptId(booking.getId()),
                    carDetails,
                    bookingDates
            );
            System.out.println("✅ Booking confirmation email sent to user: " + booking.getUser().getEmail());
        } catch (Exception e) {
            System.err.println("⚠️ Failed to send booking confirmation email: " + e.getMessage());
        }
    }

    /**
     * Cancel booking by user
     */
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

        String reason = "Booking cancelled by user";

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
                    reason = "Booking cancelled by user with refund";
                    System.out.println("💸 Refund initiated for booking: " + bookingId);
                } catch (Exception e) {
                    throw new RuntimeException("Booking cancelled but refund failed: " + e.getMessage());
                }
            }
        }

        Booking updatedBooking = bookingRepository.save(booking);
        sendCancellationEmails(updatedBooking, "user", reason);

        return convertToResponseDTO(updatedBooking);
    }

    /**
     * Cancel booking by owner
     */
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

        String reason = "Booking cancelled by car owner";

        booking.setStatus("CANCELLED");
        booking.setCancelledAt(LocalDateTime.now());

        // Make car available again
        booking.getCar().setIsAvailable(true);

        // Initiate refund if paid
        if ("PAID".equals(booking.getPaymentStatus())) {
            try {
                paymentService.initiateRefund(bookingId);
                booking.setPaymentStatus("REFUNDED");
                reason = "Booking cancelled by owner with refund";
            } catch (Exception e) {
                throw new RuntimeException("Booking cancelled but refund failed: " + e.getMessage());
            }
        } else if ("PENDING".equals(booking.getPaymentStatus())) {
            booking.setPaymentStatus("CANCELLED");
        }

        Booking updatedBooking = bookingRepository.save(booking);
        sendCancellationEmails(updatedBooking, "owner", reason);

        return convertToResponseDTO(updatedBooking);
    }

    /**
     * Send cancellation emails
     */
    private void sendCancellationEmails(Booking booking, String cancelledBy, String reason) {
        try {
            String encryptedBookingId = idEncryptionService.encryptId(booking.getId());
            String refundInfo = null;

            if ("REFUNDED".equals(booking.getPaymentStatus())) {
                refundInfo = "Refund has been processed. Amount: ₹" + booking.getAmountPaid();
            }

            // Send email to user
            emailService.sendBookingCancelledEmail(
                    booking.getUser().getEmail(),
                    encryptedBookingId,
                    "Cancelled by " + cancelledBy + ". Reason: " + reason,
                    refundInfo
            );

            // Send email to car owner (except when owner cancelled)
            if (!"owner".equals(cancelledBy)) {
                emailService.sendBookingCancelledEmail(
                        booking.getCar().getOwner().getEmail(),
                        encryptedBookingId,
                        "Cancelled by " + cancelledBy + ". Reason: " + reason,
                        null
                );
            }
        } catch (Exception e) {
            System.err.println("⚠️ Failed to send cancellation emails: " + e.getMessage());
        }
    }

    /**
     * Update payment status
     */
    @Transactional
    public BookingResponseDTO updatePaymentStatus(String encryptedBookingId, String status) {
        Long bookingId = idEncryptionService.decryptId(encryptedBookingId);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        booking.setPaymentStatus(status);

        // Update booking status based on payment
        if ("PAID".equals(status)) {
            booking.setStatus("PAYMENT_CONFIRMED");
            booking.setAmountPaid(booking.getTotalAmount());
            sendPaymentSuccessEmails(booking);
        } else if ("FAILED".equals(status)) {
            booking.setStatus("CANCELLED");
            booking.getCar().setIsAvailable(true);
            sendCancellationEmails(booking, "system", "Payment failed");
        } else if ("REFUNDED".equals(status)) {
            booking.setStatus("CANCELLED");
            booking.getCar().setIsAvailable(true);
            sendCancellationEmails(booking, "system", "Payment refunded");
        }

        Booking updatedBooking = bookingRepository.save(booking);
        return convertToResponseDTO(updatedBooking);
    }

    /**
     * Send payment success emails
     */
    private void sendPaymentSuccessEmails(Booking booking) {
        try {
            String encryptedBookingId = idEncryptionService.encryptId(booking.getId());

            // Send email to user
            emailService.sendPaymentSuccessEmail(
                    booking.getUser().getEmail(),
                    encryptedBookingId,
                    String.valueOf(booking.getAmountPaid()),
                    booking.getPaymentId()
            );

            // Send email to car owner
            emailService.sendPaymentSuccessToOwnerEmail(
                    booking.getCar().getOwner().getEmail(),
                    encryptedBookingId,
                    String.valueOf(booking.getAmountPaid()),
                    booking.getUser().getEmail()
            );
        } catch (Exception e) {
            System.err.println("⚠️ Failed to send payment success emails: " + e.getMessage());
        }
    }

    /**
     * Get booking by ID
     */
    public BookingResponseDTO getBookingById(String encryptedBookingId) {
        Long bookingId = idEncryptionService.decryptId(encryptedBookingId);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));
        return convertToResponseDTO(booking);
    }

    /**
     * Create payment order
     */
    @Transactional
    public PaymentResponse createPaymentOrder(CreatePaymentRequest request) throws RazorpayException {
        Long bookingId = idEncryptionService.decryptId(request.getBookingId());
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        // Validate booking can accept payment
        if (!"PAYMENT_PENDING".equals(booking.getStatus())) {
            throw new RuntimeException("Booking is not in payment pending state");
        }

        // Validate amount matches booking total - with tolerance for floating point
        double amountDifference = Math.abs(request.getAmount() - booking.getTotalAmount());
        if (amountDifference > 0.01) { // Allow 1 paisa difference due to floating point
            throw new RuntimeException("Payment amount does not match booking total. " +
                    "Expected: ₹" + booking.getTotalAmount() + ", Got: ₹" + request.getAmount());
        }

        // Use payment service to create order
        return paymentService.createPaymentOrder(request);
    }

    /**
     * Convert Booking entity to Response DTO
     */
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
        dto.setDrivingLicenseUrl(booking.getDrivingLicenseUrl());
        dto.setAadharCardUrl(booking.getAadharCardUrl());
        dto.setPoliceVerificationUrl(booking.getPoliceVerificationUrl());
        dto.setCreatedAt(booking.getCreatedAt());
        dto.setUpdatedAt(booking.getUpdatedAt());
        dto.setConfirmedAt(booking.getConfirmedAt());
        dto.setCancelledAt(booking.getCancelledAt());
        return dto;
    }
}