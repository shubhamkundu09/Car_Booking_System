package com.caronrent.controller;

import com.caronrent.service.IdEncryptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/test")
public class TestController {

    private final IdEncryptionService idEncryptionService;

    public TestController(IdEncryptionService idEncryptionService) {
        this.idEncryptionService = idEncryptionService;
    }

    @GetMapping("/public")
    public ResponseEntity<String> publicEndpoint() {
        return ResponseEntity.ok("This is a public endpoint");
    }

    @GetMapping("/user")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<String> userEndpoint() {
        return ResponseEntity.ok("This is a user endpoint");
    }

    @GetMapping("/carowner")
    @PreAuthorize("hasAnyRole('CAROWNER', 'ADMIN')")
    public ResponseEntity<String> carOwnerEndpoint() {
        return ResponseEntity.ok("This is a car owner endpoint");
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> adminEndpoint() {
        return ResponseEntity.ok("This is an admin endpoint");
    }

    @GetMapping("/my-info")
    public ResponseEntity<Map<String, Object>> getMyInfo(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        if (authentication != null) {
            response.put("username", authentication.getName());
            response.put("authorities", authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList()));
            response.put("isAuthenticated", authentication.isAuthenticated());
        } else {
            response.put("message", "Not authenticated");
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/encrypt/{id}")
    public ResponseEntity<String> encryptId(@PathVariable Long id) {
        String encrypted = idEncryptionService.encryptId(id);
        return ResponseEntity.ok(encrypted);
    }

    @GetMapping("/decrypt/{encryptedId}")
    public ResponseEntity<Long> decryptId(@PathVariable String encryptedId) {
        Long decrypted = idEncryptionService.decryptId(encryptedId);
        return ResponseEntity.ok(decrypted);
    }
}