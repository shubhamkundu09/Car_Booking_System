package com.caronrent.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class IdEncryptionService {

    @Value("${id.encryption.key}")
    private String encryptionKey;

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";

    public String encryptId(Long id) {
        if (id == null) {
            return null;
        }
        try {
            // Ensure key is 32 bytes for AES-256
            byte[] keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
            byte[] paddedKey = new byte[32];
            System.arraycopy(keyBytes, 0, paddedKey, 0, Math.min(keyBytes.length, 32));

            SecretKeySpec secretKey = new SecretKeySpec(paddedKey, ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);

            byte[] encrypted = cipher.doFinal(String.valueOf(id).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted);
        } catch (Exception e) {
            // Fallback: return string representation if encryption fails
            System.err.println("Encryption failed for ID " + id + ": " + e.getMessage());
            return String.valueOf(id);
        }
    }

    public Long decryptId(String encryptedId) {
        if (encryptedId == null || encryptedId.isEmpty()) {
            return null;
        }

        try {
            // Try to decrypt as Base64 URL encoded
            byte[] keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
            byte[] paddedKey = new byte[32];
            System.arraycopy(keyBytes, 0, paddedKey, 0, Math.min(keyBytes.length, 32));

            SecretKeySpec secretKey = new SecretKeySpec(paddedKey, ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);

            byte[] decoded = Base64.getUrlDecoder().decode(encryptedId);
            byte[] decrypted = cipher.doFinal(decoded);
            return Long.parseLong(new String(decrypted, StandardCharsets.UTF_8));
        } catch (Exception e) {
            // If decryption fails, try to parse as plain Long
            try {
                return Long.parseLong(encryptedId);
            } catch (NumberFormatException ex) {
                throw new RuntimeException("Failed to decrypt or parse ID: " + encryptedId, e);
            }
        }
    }
}