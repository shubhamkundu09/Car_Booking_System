package com.caronrent.repo;

import com.caronrent.entity.CarImage;
import com.caronrent.entity.Car;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CarImageRepository extends JpaRepository<CarImage, Long> {
    List<CarImage> findByCar(Car car);
    void deleteByCar(Car car);
}