package com.caronrent.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
@Data
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "car_id", nullable = false)
    private Car car;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime startDate;

    @Column(nullable = false)
    private LocalDateTime endDate;

    @Column(nullable = false)
    private Integer totalDays;

    @Column(nullable = false)
    private Double totalAmount;

    @Column(nullable = false)
    private String status; // PENDING, PAYMENT_PENDING, CONFIRMED, CANCELLED, COMPLETED

    @Column(nullable = false)
    private String paymentStatus; // PENDING, PAID, FAILED, REFUNDED

    @Column(unique = true)
    private String paymentId; // Razorpay payment ID

    @Column(unique = true)
    private String orderId; // Razorpay order ID

    @Column(nullable = false)
    private Double amountPaid;

    private String specialRequests;

    // Add these three mandatory document fields
    @Column(name = "driving_license_url", nullable = false)
    private String drivingLicenseUrl;

    @Column(name = "aadhar_card_url", nullable = false)
    private String aadharCardUrl;

    @Column(name = "police_verification_url", nullable = false)
    private String policeVerificationUrl;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime cancelledAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        status = "PAYMENT_PENDING";
        paymentStatus = "PENDING";
        amountPaid = 0.0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean canBeConfirmed() {
        return "PAYMENT_PENDING".equals(status) && "PAID".equals(paymentStatus);
    }

    public boolean canBeCancelled() {
        return !"CANCELLED".equals(status) && !"COMPLETED".equals(status);
    }

    public boolean overlapsWith(LocalDateTime start, LocalDateTime end) {
        return !(end.isBefore(this.startDate) || start.isAfter(this.endDate));
    }
}