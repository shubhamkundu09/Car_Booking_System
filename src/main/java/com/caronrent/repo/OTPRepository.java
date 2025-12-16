package com.caronrent.repo;


import com.caronrent.entity.OTP;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OTPRepository extends JpaRepository<OTP, Long> {
    Optional<OTP> findByEmailAndOtpCodeAndUsedFalse(String email, String otpCode);
    void deleteByEmail(String email);
}
