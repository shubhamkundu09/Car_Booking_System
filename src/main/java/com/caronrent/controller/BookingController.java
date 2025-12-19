package com.caronrent.controller;

import com.caronrent.dto.BookingRequestDTO;
import com.caronrent.entity.Booking;
import com.caronrent.service.BookingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {
    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    // User endpoints
    @PostMapping("/user/create")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<Booking> createBooking(
            @RequestBody BookingRequestDTO bookingRequest,
            Authentication authentication) {
        String email = authentication.getName();
        Booking booking = bookingService.createBooking(email, bookingRequest);
        return ResponseEntity.ok(booking);
    }

    @GetMapping("/user/my-bookings")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<Booking>> getMyBookings(Authentication authentication) {
        String email = authentication.getName();
        List<Booking> bookings = bookingService.getUserBookings(email);
        return ResponseEntity.ok(bookings);
    }

    @PutMapping("/user/{bookingId}/cancel")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<Booking> cancelBooking(
            @PathVariable Long bookingId,
            Authentication authentication) {
        String email = authentication.getName();
        Booking booking = bookingService.cancelBooking(bookingId, email);
        return ResponseEntity.ok(booking);
    }

    // Car Owner endpoints
    @GetMapping("/owner/bookings")
    @PreAuthorize("hasAnyRole('CAROWNER', 'ADMIN')")
    public ResponseEntity<List<Booking>> getOwnerBookings(Authentication authentication) {
        String email = authentication.getName();
        List<Booking> bookings = bookingService.getOwnerBookings(email);
        return ResponseEntity.ok(bookings);
    }

    @PutMapping("/owner/{bookingId}/confirm")
    @PreAuthorize("hasAnyRole('CAROWNER', 'ADMIN')")
    public ResponseEntity<Booking> confirmBooking(
            @PathVariable Long bookingId,
            Authentication authentication) {
        String email = authentication.getName();
        Booking booking = bookingService.confirmBooking(bookingId, email);
        return ResponseEntity.ok(booking);
    }

    @PutMapping("/owner/{bookingId}/cancel")
    @PreAuthorize("hasAnyRole('CAROWNER', 'ADMIN')")
    public ResponseEntity<Booking> cancelBookingByOwner(
            @PathVariable Long bookingId,
            Authentication authentication) {
        String email = authentication.getName();
        Booking booking = bookingService.cancelBooking(bookingId, email);
        return ResponseEntity.ok(booking);
    }

    // Admin endpoints
    @PutMapping("/admin/{bookingId}/payment-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Booking> updatePaymentStatus(
            @PathVariable Long bookingId,
            @RequestParam String status) {
        Booking booking = bookingService.updatePaymentStatus(bookingId, status);
        return ResponseEntity.ok(booking);
    }

    @GetMapping("/{bookingId}")
    @PreAuthorize("hasAnyRole('USER', 'CAROWNER', 'ADMIN')")
    public ResponseEntity<Booking> getBookingById(@PathVariable Long bookingId) {
        Booking booking = bookingService.getBookingById(bookingId);
        return ResponseEntity.ok(booking);
    }
}