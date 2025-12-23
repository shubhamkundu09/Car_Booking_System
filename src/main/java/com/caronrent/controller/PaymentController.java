package com.caronrent.controller;

import com.caronrent.dto.CreatePaymentRequest;
import com.caronrent.dto.PaymentResponse;
import com.caronrent.dto.PaymentVerificationRequest;
import com.caronrent.entity.Booking;
import com.caronrent.service.PaymentService;
import com.razorpay.RazorpayException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/create-order")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> createPaymentOrder(
            @RequestBody CreatePaymentRequest request,
            Authentication authentication) {
        try {
            String userEmail = authentication.getName();
            System.out.println("💰 Creating payment order for user: " + userEmail);
            System.out.println("📦 Booking ID: " + request.getBookingId());
            System.out.println("💵 Amount: " + request.getAmount());

            PaymentResponse response = paymentService.createPaymentOrder(request);

            System.out.println("✅ Payment order created: " + response.getOrderId());

            // Return response with additional info for frontend
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("order", response);
            responseData.put("message", "Payment order created successfully");
            responseData.put("nextStep", "Complete payment using Razorpay checkout");

            return ResponseEntity.ok(responseData);
        } catch (RazorpayException e) {
            System.err.println("❌ Razorpay error: " + e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Payment order creation failed",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            System.err.println("❌ Error creating payment order: " + e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Payment order creation failed",
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/verify")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> verifyPayment(
            @RequestBody PaymentVerificationRequest request,
            Authentication authentication) {
        try {
            String userEmail = authentication.getName();
            System.out.println("🔍 Verifying payment for user: " + userEmail);
            System.out.println("📦 Booking ID: " + request.getBookingId());
            System.out.println("💳 Payment ID: " + request.getRazorpayPaymentId());
            System.out.println("📝 Order ID: " + request.getRazorpayOrderId());
            System.out.println("✍️  Signature: " + request.getRazorpaySignature());

            Booking booking = paymentService.verifyPayment(request);

            System.out.println("✅ Payment verified successfully!");
            System.out.println("   Booking Status: " + booking.getStatus());
            System.out.println("   Payment Status: " + booking.getPaymentStatus());
            System.out.println("   Amount Paid: " + booking.getAmountPaid());

            // Return detailed response
            Map<String, Object> response = new HashMap<>();
            response.put("booking", booking);
            response.put("message", "Payment verified successfully");
            response.put("status", "success");
            response.put("paymentDetails", Map.of(
                    "paymentId", booking.getPaymentId(),
                    "orderId", booking.getOrderId(),
                    "amountPaid", booking.getAmountPaid(),
                    "paymentStatus", booking.getPaymentStatus()
            ));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("❌ Payment verification failed: " + e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Payment verification failed",
                    "message", e.getMessage(),
                    "status", "failed"
            ));
        }
    }

    @GetMapping("/status/{encryptedBookingId}")
    @PreAuthorize("hasAnyRole('USER', 'CAROWNER', 'ADMIN')")
    public ResponseEntity<?> getPaymentStatus(
            @PathVariable String encryptedBookingId,
            Authentication authentication) {
        try {
            // You'll need to add a method in BookingService to get payment status
            // For now, we'll return a placeholder response

            Map<String, Object> response = new HashMap<>();
            response.put("bookingId", encryptedBookingId);
            response.put("message", "Payment status endpoint - implement this method");
            response.put("suggestedActions", Map.of(
                    "user", "Complete payment to confirm booking",
                    "owner", "Check payment status before confirming booking"
            ));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to get payment status",
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/generate-signature")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> generateSignature(
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        try {
            String orderId = request.get("orderId");
            String paymentId = request.get("paymentId");

            if (orderId == null || orderId.isEmpty() || paymentId == null || paymentId.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Missing parameters",
                        "message", "Both orderId and paymentId are required"
                ));
            }

            // Generate signature using the same logic as Razorpay
            String data = orderId + "|" + paymentId;
            String generatedSignature = calculateHMAC(data, razorpayKeySecret);

            Map<String, Object> response = new HashMap<>();
            response.put("orderId", orderId);
            response.put("paymentId", paymentId);
            response.put("signature", generatedSignature);
            response.put("data", data);
            response.put("message", "Signature generated successfully");

            System.out.println("📝 Generated signature details:");
            System.out.println("   Order ID: " + orderId);
            System.out.println("   Payment ID: " + paymentId);
            System.out.println("   Data: " + data);
            System.out.println("   Signature: " + generatedSignature);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("❌ Error generating signature: " + e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to generate signature",
                    "message", e.getMessage()
            ));
        }
    }

    // Helper method to calculate HMAC signature (same as in PaymentService)
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
}