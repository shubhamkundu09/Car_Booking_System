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

import java.security.MessageDigest;
import java.time.LocalDateTime;

@Service
public class PaymentService {

    private final RazorpayClient razorpayClient;
    private final BookingRepository bookingRepository;

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    public PaymentService(RazorpayClient razorpayClient, BookingRepository bookingRepository) {
        this.razorpayClient = razorpayClient;
        this.bookingRepository = bookingRepository;
    }

    @Transactional
    public PaymentResponse createPaymentOrder(CreatePaymentRequest request) throws RazorpayException {
        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        // Validate booking can accept payment
        if (!"PAYMENT_PENDING".equals(booking.getStatus())) {
            throw new RuntimeException("Booking is not in payment pending state");
        }

        // Create Razorpay order
        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", request.getAmount() * 100); // Amount in paise
        orderRequest.put("currency", request.getCurrency());
        orderRequest.put("receipt", "booking_" + booking.getId());
        orderRequest.put("payment_capture", 1);

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
        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        try {
            // Verify payment signature
            String generatedSignature = generateSignature(
                    request.getRazorpayOrderId() + "|" + request.getRazorpayPaymentId(),
                    razorpayKeySecret
            );

            if (generatedSignature.equals(request.getRazorpaySignature())) {
                // Payment successful
                booking.setPaymentStatus("PAID");
                booking.setPaymentId(request.getRazorpayPaymentId());
                booking.setAmountPaid(booking.getTotalAmount());
                booking.setStatus("PAYMENT_PENDING"); // Now ready for confirmation
                booking.setUpdatedAt(LocalDateTime.now());

                return bookingRepository.save(booking);
            } else {
                throw new RuntimeException("Invalid payment signature");
            }
        } catch (Exception e) {
            booking.setPaymentStatus("FAILED");
            bookingRepository.save(booking);
            throw new RuntimeException("Payment verification failed: " + e.getMessage());
        }
    }

    private String generateSignature(String data, String secret) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(data.getBytes());

        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
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

        // Create Razorpay refund
        JSONObject refundRequest = new JSONObject();
        refundRequest.put("payment_id", booking.getPaymentId());
        refundRequest.put("amount", booking.getAmountPaid() * 100); // Amount in paise

        com.razorpay.Refund refund = razorpayClient.payments.refund(booking.getPaymentId(), refundRequest);

        // Update booking
        booking.setPaymentStatus("REFUNDED");
        booking.setStatus("CANCELLED");
        booking.setCancelledAt(LocalDateTime.now());
        booking.setUpdatedAt(LocalDateTime.now());

        // Make car available again
        booking.getCar().setIsAvailable(true);

        return bookingRepository.save(booking);
    }
}