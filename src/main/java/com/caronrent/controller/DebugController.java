package com.caronrent.controller;

import com.caronrent.service.IdEncryptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/debug")
public class DebugController {

    private final IdEncryptionService idEncryptionService;

    public DebugController(IdEncryptionService idEncryptionService) {
        this.idEncryptionService = idEncryptionService;
    }

    @GetMapping("/encrypt/{id}")
    public ResponseEntity<Map<String, Object>> encryptId(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            String encrypted = idEncryptionService.encryptId(id);
            response.put("original", id);
            response.put("encrypted", encrypted);
            response.put("success", true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", e.getMessage());
            response.put("success", false);
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/decrypt/{encryptedId}")
    public ResponseEntity<Map<String, Object>> decryptId(@PathVariable String encryptedId) {
        Map<String, Object> response = new HashMap<>();
        try {
            Long decrypted = idEncryptionService.decryptId(encryptedId);
            response.put("encrypted", encryptedId);
            response.put("decrypted", decrypted);
            response.put("success", true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", e.getMessage());
            response.put("success", false);
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/test-encryption")
    public ResponseEntity<Map<String, Object>> testEncryption() {
        Map<String, Object> response = new HashMap<>();
        try {
            Long testId = 123L;
            String encrypted = idEncryptionService.encryptId(testId);
            Long decrypted = idEncryptionService.decryptId(encrypted);

            response.put("testId", testId);
            response.put("encrypted", encrypted);
            response.put("decrypted", decrypted);
            response.put("matches", testId.equals(decrypted));
            response.put("success", true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", e.getMessage());
            response.put("success", false);
            return ResponseEntity.badRequest().body(response);
        }
    }
}