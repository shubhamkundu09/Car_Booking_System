package com.caronrent.repo;

import com.caronrent.entity.Car;
import com.caronrent.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CarRepository extends JpaRepository<Car, Long> {
    List<Car> findByOwnerAndIsActiveTrue(User owner);
    List<Car> findByOwner(User owner);
    List<Car> findByIsAvailableTrueAndIsActiveTrue();
    List<Car> findByLocationContainingIgnoreCaseAndIsAvailableTrueAndIsActiveTrue(String location);
    List<Car> findByBrandContainingIgnoreCaseAndIsAvailableTrueAndIsActiveTrue(String brand);
    List<Car> findByDailyRateBetweenAndIsAvailableTrueAndIsActiveTrue(Double minRate, Double maxRate);
}