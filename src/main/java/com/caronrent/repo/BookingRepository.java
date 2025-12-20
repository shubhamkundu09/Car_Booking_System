package com.caronrent.repo;

import com.caronrent.entity.Booking;
import com.caronrent.entity.Car;
import com.caronrent.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByUser(User user);
    List<Booking> findByCarOwner(User owner);
    List<Booking> findByCar(Car car);
    List<Booking> findByStatus(String status);
    List<Booking> findByUserEmail(String email);
    List<Booking> findByCarOwnerEmail(String email);

    // ========== UPDATED: Fixed overlapping bookings query ==========
    // Changed to check for specific car only, not all cars from owner
    @Query("SELECT b FROM Booking b WHERE b.car.id = :carId AND " +
            "((b.startDate <= :endDate AND b.endDate >= :startDate) OR " +
            "(b.startDate <= :endDate AND b.endDate >= :endDate) OR " +
            "(:startDate <= b.endDate AND :endDate >= b.startDate)) AND " +
            "b.status NOT IN ('CANCELLED', 'COMPLETED')")
    List<Booking> findOverlappingBookings(
            @Param("carId") Long carId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    // ========== END UPDATE ==========
}