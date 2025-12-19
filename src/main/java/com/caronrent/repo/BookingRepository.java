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

    // Better overlap checking query
    @Query("SELECT b FROM Booking b WHERE b.car = :car " +
            "AND b.status IN ('PENDING', 'CONFIRMED') " +
            "AND ((b.startDate <= :endDate AND b.endDate >= :startDate))")
    List<Booking> findOverlappingBookings(
            @Param("car") Car car,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    // Find all confirmed bookings for a car in date range
    @Query("SELECT b FROM Booking b WHERE b.car = :car " +
            "AND b.status = 'CONFIRMED' " +
            "AND b.startDate <= :endDate " +
            "AND b.endDate >= :startDate")
    List<Booking> findConfirmedOverlappingBookings(
            @Param("car") Car car,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    List<Booking> findByStatus(String status);
}