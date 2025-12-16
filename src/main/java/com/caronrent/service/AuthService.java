package com.caronrent.service;

import com.caronrent.config.CustomUserDetails;
import com.caronrent.dto.LoginRequest;
import com.caronrent.dto.SignupRequest;
import com.caronrent.dto.VerifyOTPRequest;
import com.caronrent.entity.User;

import com.caronrent.repo.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OTPService otpService;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       OTPService otpService, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.otpService = otpService;
        this.jwtService = jwtService;
    }

    public String signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        // Generate and send OTP
        otpService.generateAndSendOTP(request.getEmail());

        return "OTP sent to your email. Please verify to complete registration.";
    }



    // Updated method that accepts password in OTP verification
    public String verifyOtpAndRegisterWithPassword(VerifyOTPRequest request) {
        // Verify OTP
        if (!otpService.verifyOTP(request.getEmail(), request.getOtp())) {
            throw new RuntimeException("Invalid or expired OTP");
        }

        // Check if user already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        // Create user with actual password
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword())); // Use actual password
        user.setEnabled(true);
        user.setRoles(List.of(request.getRole())); 

        userRepository.save(user);

        return "Registration successful!";
    }

    public String login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Debug: Print passwords to see what's happening
        System.out.println("User entered password: " + request.getPassword());
        System.out.println("Stored hashed password: " + user.getPassword());
        System.out.println("Password matches: " + passwordEncoder.matches(request.getPassword(), user.getPassword()));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        if (!user.isEnabled()) {
            throw new RuntimeException("Account is disabled");
        }

        // Create UserDetails from User entity
        CustomUserDetails userDetails = new CustomUserDetails(user);

        return jwtService.generateToken(userDetails);
    }


}