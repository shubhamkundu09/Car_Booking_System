package com.caronrent.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendOtpEmail(String to, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject("Your OTP Code");
        message.setText("Your OTP code is: " + otp + "\n\nThis OTP will expire in 10 minutes.");
        mailSender.send(message);
    }

    public void sendRegistrationSuccessEmail(String to, String role) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject("Registration Successful - CarOnRent");
        message.setText("Dear User,\n\n" +
                "Congratulations! You have successfully registered with CarOnRent as a " + role + ".\n\n" +
                "You can now login to your account and start using our services.\n\n" +
                "Thank you for choosing CarOnRent!\n\n" +
                "Best Regards,\n" +
                "CarOnRent Team");
        mailSender.send(message);
    }

    public void sendBookingCreatedEmail(String to, String bookingId, String carDetails,
                                        String bookingDates, String amount) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject("Booking Created Successfully");
        message.setText("Dear User,\n\n" +
                "Your booking has been created successfully!\n\n" +
                "Booking Details:\n" +
                "Booking ID: " + bookingId + "\n" +
                "Car: " + carDetails + "\n" +
                "Dates: " + bookingDates + "\n" +
                "Total Amount: ₹" + amount + "\n\n" +
                "Please complete the payment to confirm your booking.\n\n" +
                "Thank you,\n" +
                "CarOnRent Team");
        mailSender.send(message);
    }

    public void sendBookingCreatedToOwnerEmail(String to, String bookingId, String carDetails,
                                               String userEmail, String bookingDates, String amount) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject("New Booking Request for Your Car");
        message.setText("Dear Car Owner,\n\n" +
                "You have received a new booking request for your car!\n\n" +
                "Booking Details:\n" +
                "Booking ID: " + bookingId + "\n" +
                "Car: " + carDetails + "\n" +
                "Customer Email: " + userEmail + "\n" +
                "Dates: " + bookingDates + "\n" +
                "Total Amount: ₹" + amount + "\n\n" +
                "Please wait for payment confirmation from the customer.\n\n" +
                "Thank you,\n" +
                "CarOnRent Team");
        mailSender.send(message);
    }

    public void sendPaymentSuccessEmail(String to, String bookingId, String amount,
                                        String paymentId) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject("Payment Successful - CarOnRent");
        message.setText("Dear User,\n\n" +
                "Your payment has been processed successfully!\n\n" +
                "Payment Details:\n" +
                "Booking ID: " + bookingId + "\n" +
                "Amount Paid: ₹" + amount + "\n" +
                "Payment ID: " + paymentId + "\n\n" +
                "Your booking is now confirmed. Please wait for car owner's confirmation.\n\n" +
                "Thank you,\n" +
                "CarOnRent Team");
        mailSender.send(message);
    }

    public void sendPaymentSuccessToOwnerEmail(String to, String bookingId, String amount,
                                               String userEmail) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject("Payment Received for Your Car Booking");
        message.setText("Dear Car Owner,\n\n" +
                "Payment has been received for booking ID: " + bookingId + "\n\n" +
                "Details:\n" +
                "Customer: " + userEmail + "\n" +
                "Amount: ₹" + amount + "\n\n" +
                "Please confirm the booking from your dashboard to finalize it.\n\n" +
                "Thank you,\n" +
                "CarOnRent Team");
        mailSender.send(message);
    }

    public void sendBookingConfirmedEmail(String to, String bookingId, String carDetails,
                                          String bookingDates) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject("Booking Confirmed!");
        message.setText("Dear Customer,\n\n" +
                "Great news! Your booking has been confirmed by the car owner.\n\n" +
                "Booking Details:\n" +
                "Booking ID: " + bookingId + "\n" +
                "Car: " + carDetails + "\n" +
                "Dates: " + bookingDates + "\n\n" +
                "Please prepare your documents and be ready for pickup.\n\n" +
                "Thank you,\n" +
                "CarOnRent Team");
        mailSender.send(message);
    }

    public void sendBookingCompletedEmail(String to, String bookingId, String carDetails,
                                          String userEmail, String amount) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject("Booking Completed Successfully");
        message.setText("Dear User,\n\n" +
                "Your booking has been completed successfully!\n\n" +
                "Booking Details:\n" +
                "Booking ID: " + bookingId + "\n" +
                "Car: " + carDetails + "\n" +
                (userEmail != null ? "Customer: " + userEmail + "\n" : "") +
                "Amount: ₹" + amount + "\n\n" +
                "Thank you for using CarOnRent. We hope to serve you again soon!\n\n" +
                "Best Regards,\n" +
                "CarOnRent Team");
        mailSender.send(message);
    }

    public void sendBookingCancelledEmail(String to, String bookingId, String reason,
                                          String refundInfo) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject("Booking Cancelled");
        message.setText("Dear User,\n\n" +
                "Your booking has been cancelled.\n\n" +
                "Booking ID: " + bookingId + "\n" +
                "Reason: " + reason + "\n" +
                (refundInfo != null ? "Refund Info: " + refundInfo + "\n" : "") +
                "\nIf you have any questions, please contact our support team.\n\n" +
                "Thank you,\n" +
                "CarOnRent Team");
        mailSender.send(message);
    }
}