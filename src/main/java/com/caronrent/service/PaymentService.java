package com.caronrent.service;

import com.caronrent.dto.CreatePaymentRequest;
import com.caronrent.dto.PaymentResponse;
import com.caronrent.dto.PaymentVerificationRequest;
import com.caronrent.entity.Booking;
import com.caronrent.repo.BookingRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class PaymentService {

    private final RazorpayClient razorpayClient;
    private final BookingRepository bookingRepository;
    private final IdEncryptionService idEncryptionService;
    private final EmailService emailService;

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    public PaymentService(RazorpayClient razorpayClient, BookingRepository bookingRepository,
                          IdEncryptionService idEncryptionService, EmailService emailService) {
        this.razorpayClient = razorpayClient;
        this.bookingRepository = bookingRepository;
        this.idEncryptionService = idEncryptionService;
        this.emailService = emailService;
    }

    @Transactional
    public PaymentResponse createPaymentOrder(CreatePaymentRequest request) throws RazorpayException {
        Long bookingId = idEncryptionService.decryptId(request.getBookingId());
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        // Validate booking can accept payment
        if (!"PAYMENT_PENDING".equals(booking.getStatus())) {
            throw new RuntimeException("Booking is not in payment pending state");
        }

        // Validate amount matches booking total
        if (!request.getAmount().equals(booking.getTotalAmount())) {
            throw new RuntimeException("Payment amount does not match booking total");
        }

        // Create Razorpay order
        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", (int) (request.getAmount() * 100)); // Amount in paise
        orderRequest.put("currency", request.getCurrency());
        orderRequest.put("receipt", "booking_" + booking.getId());
        orderRequest.put("payment_capture", 1); // Auto-capture payment
        orderRequest.put("notes", new JSONObject()
                .put("booking_id", booking.getId())
                .put("user_email", booking.getUser().getEmail()));

        Order order = razorpayClient.orders.create(orderRequest);

        // Update booking with order ID
        booking.setOrderId(order.get("id"));
        bookingRepository.save(booking);

        PaymentResponse response = new PaymentResponse();
        response.setOrderId(order.get("id"));
        response.setRazorpayKeyId(razorpayKeyId);
        response.setAmount(request.getAmount());
        response.setCurrency(request.getCurrency());
        response.setStatus("created");

        return response;
    }

    @Transactional
    public Booking verifyPayment(PaymentVerificationRequest request) {
        Long bookingId = idEncryptionService.decryptId(request.getBookingId());
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        try {
            // Verify payment signature
            String data = request.getRazorpayOrderId() + "|" + request.getRazorpayPaymentId();
            String generatedSignature = calculateHMAC(data, razorpayKeySecret);

            if (generatedSignature.equals(request.getRazorpaySignature())) {
                // Payment successful - Update booking status
                booking.setPaymentStatus("PAID");
                booking.setPaymentId(request.getRazorpayPaymentId());
                booking.setAmountPaid(booking.getTotalAmount());
                booking.setStatus("PAYMENT_CONFIRMED");
                booking.setUpdatedAt(LocalDateTime.now());

                // Log payment success
                System.out.println("✅ Payment verified for booking ID: " + booking.getId());
                System.out.println("💰 Amount paid: " + booking.getAmountPaid());
                System.out.println("📧 User: " + booking.getUser().getEmail());
                System.out.println("🚗 Car owner: " + booking.getCar().getOwner().getEmail());

                Booking savedBooking = bookingRepository.save(booking);

                // Send payment success emails
                sendPaymentSuccessEmails(savedBooking);

                return savedBooking;
            } else {
                // Signature mismatch
                booking.setPaymentStatus("FAILED");
                bookingRepository.save(booking);
                throw new RuntimeException("Invalid payment signature");
            }
        } catch (Exception e) {
            // Payment verification failed
            booking.setPaymentStatus("FAILED");
            bookingRepository.save(booking);
            throw new RuntimeException("Payment verification failed: " + e.getMessage());
        }
    }

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
            System.out.println("✅ Payment success email sent to user: " + booking.getUser().getEmail());

            // Send email to car owner
            emailService.sendPaymentSuccessToOwnerEmail(
                    booking.getCar().getOwner().getEmail(),
                    encryptedBookingId,
                    String.valueOf(booking.getAmountPaid()),
                    booking.getUser().getEmail()
            );
            System.out.println("✅ Payment success email sent to owner: " + booking.getCar().getOwner().getEmail());

        } catch (Exception e) {
            System.err.println("⚠️ Failed to send payment success emails: " + e.getMessage());
            // Don't throw exception, just log it
        }
    }

    // Helper method to calculate HMAC signature
    private String calculateHMAC(String data, String secret) throws Exception {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256_HMAC.init(secretKey);
        byte[] hash = sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    // Helper method to convert bytes to hex
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    @Transactional
    public Booking initiateRefund(Long bookingId) throws RazorpayException {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (!"PAID".equals(booking.getPaymentStatus())) {
            throw new RuntimeException("Cannot refund unpaid booking");
        }

        if ("CANCELLED".equals(booking.getStatus())) {
            throw new RuntimeException("Booking is already cancelled");
        }

        // Check if booking can be refunded (within cancellation period)
        if (LocalDateTime.now().isAfter(booking.getStartDate().minusHours(24))) {
            throw new RuntimeException("Cannot refund booking within 24 hours of start date");
        }

        // Create Razorpay refund
        JSONObject refundRequest = new JSONObject();
        refundRequest.put("payment_id", booking.getPaymentId());
        refundRequest.put("amount", (int) (booking.getAmountPaid() * 100)); // Amount in paise
        refundRequest.put("speed", "normal");
        refundRequest.put("notes", new JSONObject()
                .put("booking_id", booking.getId())
                .put("reason", "Booking cancellation"));

        com.razorpay.Refund refund = razorpayClient.payments.refund(booking.getPaymentId(), refundRequest);

        // Update booking
        booking.setPaymentStatus("REFUNDED");
        booking.setStatus("CANCELLED");
        booking.setCancelledAt(LocalDateTime.now());
        booking.setUpdatedAt(LocalDateTime.now());

        // Make car available again
        booking.getCar().setIsAvailable(true);

        System.out.println("💸 Refund initiated for booking ID: " + booking.getId());
        System.out.println("🔄 Refund ID: " + refund.get("id"));
        System.out.println("💰 Refund amount: " + booking.getAmountPaid());

        Booking savedBooking = bookingRepository.save(booking);

        // Send cancellation email with refund info
        sendCancellationEmailWithRefund(savedBooking, "Booking cancelled with refund",
                "Refund ID: " + refund.get("id") + ", Amount: ₹" + booking.getAmountPaid());

        return savedBooking;
    }

    private void sendCancellationEmailWithRefund(Booking booking, String reason, String refundInfo) {
        try {
            String encryptedBookingId = idEncryptionService.encryptId(booking.getId());

            // Send email to user
            emailService.sendBookingCancelledEmail(
                    booking.getUser().getEmail(),
                    encryptedBookingId,
                    reason,
                    refundInfo
            );
            System.out.println("✅ Cancellation email with refund sent to user: " + booking.getUser().getEmail());

            // Send email to car owner
            emailService.sendBookingCancelledEmail(
                    booking.getCar().getOwner().getEmail(),
                    encryptedBookingId,
                    reason,
                    null
            );
            System.out.println("✅ Cancellation email sent to owner: " + booking.getCar().getOwner().getEmail());

        } catch (Exception e) {
            System.err.println("⚠️ Failed to send cancellation emails: " + e.getMessage());
            // Don't throw exception, just log it
        }
    }

    // New method to send booking completion emails
    @Transactional
    public void markBookingAsCompleted(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (!"CONFIRMED".equals(booking.getStatus())) {
            throw new RuntimeException("Only confirmed bookings can be marked as completed");
        }

        if (LocalDateTime.now().isBefore(booking.getEndDate())) {
            throw new RuntimeException("Cannot mark booking as completed before end date");
        }

        booking.setStatus("COMPLETED");
        booking.setUpdatedAt(LocalDateTime.now());

        // Make car available again
        booking.getCar().setIsAvailable(true);

        Booking savedBooking = bookingRepository.save(booking);

        // Send booking completion emails
        sendBookingCompletionEmails(savedBooking);
    }

    private void sendBookingCompletionEmails(Booking booking) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
            String bookingDates = booking.getStartDate().format(formatter) + " to " +
                    booking.getEndDate().format(formatter);
            String carDetails = booking.getCar().getBrand() + " " + booking.getCar().getModel() +
                    " (" + booking.getCar().getRegistrationNumber() + ")";

            // Send email to user
            emailService.sendBookingCompletedEmail(
                    booking.getUser().getEmail(),
                    idEncryptionService.encryptId(booking.getId()),
                    carDetails,
                    null, // No need for customer email here
                    String.valueOf(booking.getAmountPaid())
            );
            System.out.println("✅ Booking completion email sent to user: " + booking.getUser().getEmail());

            // Send email to car owner
            emailService.sendBookingCompletedEmail(
                    booking.getCar().getOwner().getEmail(),
                    idEncryptionService.encryptId(booking.getId()),
                    carDetails,
                    booking.getUser().getEmail(),
                    String.valueOf(booking.getAmountPaid())
            );
            System.out.println("✅ Booking completion email sent to owner: " + booking.getCar().getOwner().getEmail());

        } catch (Exception e) {
            System.err.println("⚠️ Failed to send booking completion emails: " + e.getMessage());
            // Don't throw exception, just log it
        }
    }

    // New method to get payment details
    public JSONObject getPaymentDetails(String paymentId) throws RazorpayException {
        com.razorpay.Payment payment = razorpayClient.payments.fetch(paymentId);
        return new JSONObject(payment.toString());
    }
}