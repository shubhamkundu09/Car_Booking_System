package com.caronrent.service;


import com.caronrent.entity.OTP;
import com.caronrent.repo.OTPRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Random;

@Service
public class OTPService {
    private final OTPRepository otpRepository;
    private final EmailService emailService;

    @Value("${app.otp.expiration-minutes}")
    private int otpExpirationMinutes;

    public OTPService(OTPRepository otpRepository, EmailService emailService) {
        this.otpRepository = otpRepository;
        this.emailService = emailService;
    }

    public String generateAndSendOTP(String email) {
        // Delete old OTPs for this email
        otpRepository.deleteByEmail(email);

        // Generate new OTP
        String otpCode = generateOTP();

        // Save OTP to database
        OTP otp = new OTP();
        otp.setEmail(email);
        otp.setOtpCode(otpCode);
        otp.setExpiryTime(LocalDateTime.now().plusMinutes(otpExpirationMinutes));
        otp.setUsed(false);
        otpRepository.save(otp);

        // Send OTP via email
        emailService.sendOtpEmail(email, otpCode);

        return otpCode;
    }

    public boolean verifyOTP(String email, String otpCode) {
        return otpRepository.findByEmailAndOtpCodeAndUsedFalse(email, otpCode)
                .map(otp -> {
                    if (LocalDateTime.now().isAfter(otp.getExpiryTime())) {
                        return false;
                    }
                    otp.setUsed(true);
                    otpRepository.save(otp);
                    return true;
                })
                .orElse(false);
    }

    private String generateOTP() {
        Random random = new Random();
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }
}