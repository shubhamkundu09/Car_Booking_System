package com.caronrent.config;

import com.caronrent.entity.User;
import com.caronrent.repo.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class AdminDataLoader implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:admin@caronrent.com}")
    private String adminEmail;

    @Value("${app.admin.password:Admin@123456}")
    private String adminPassword;

    public AdminDataLoader(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        // Create admin ONLY if no user exists at all
        long userCount = userRepository.count();

        if (userCount == 0) {
            User admin = new User();
            admin.setEmail(adminEmail);
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setEnabled(true);
            admin.setRoles(Arrays.asList("ROLE_USER","ROLE_CAROWNER", "ROLE_ADMIN")); // Both roles

            userRepository.save(admin);

            System.out.println("=========================================");
            System.out.println("🚀 SUPER ADMIN CREATED SUCCESSFULLY!");
            System.out.println("📧 Email: " + adminEmail);
            System.out.println("🔑 Password: " + adminPassword);
            System.out.println("⭐ This is the ONLY admin for the system");
            System.out.println("=========================================");
        } else {
            // Check if our admin exists
            userRepository.findByEmail(adminEmail).ifPresentOrElse(
                    user -> {
                        System.out.println("✅ Admin user already exists: " + adminEmail);
                    },
                    () -> {
                        System.out.println("⚠️  Users exist but admin account not found");
                        System.out.println("ℹ️  Existing admin email: " + adminEmail);
                    }
            );
        }
    }
}