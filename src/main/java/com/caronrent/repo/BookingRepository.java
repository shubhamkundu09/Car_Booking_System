package com.caronrent.repo;

import com.caronrent.entity.Booking;
import com.caronrent.entity.Car;
import com.caronrent.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByUser(User user);
    List<Booking> findByCarOwner(User owner);
    List<Booking> findByCar(Car car);
    List<Booking> findByCarAndStartDateBetweenOrEndDateBetween(Car car,
                                                               LocalDateTime start1, LocalDateTime end1, LocalDateTime start2, LocalDateTime end2);
    List<Booking> findByStatus(String status);
}