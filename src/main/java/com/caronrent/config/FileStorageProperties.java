package com.caronrent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "file.upload")
public class FileStorageProperties {
    private String uploadDir = "uploads/";
    private String carImages = "uploads/cars/";
    private String documents = "uploads/documents/";
    private Long maxSize = 10485760L; // 10MB default
    private String allowedExtensions = ".jpg,.jpeg,.png,.gif,.pdf";

    // Getters and setters
    public String getUploadDir() { return uploadDir; }
    public void setUploadDir(String uploadDir) { this.uploadDir = uploadDir; }

    public String getCarImages() { return carImages; }
    public void setCarImages(String carImages) { this.carImages = carImages; }

    public String getDocuments() { return documents; }
    public void setDocuments(String documents) { this.documents = documents; }

    public Long getMaxSize() { return maxSize; }
    public void setMaxSize(Long maxSize) { this.maxSize = maxSize; }

    public String getAllowedExtensions() { return allowedExtensions; }
    public void setAllowedExtensions(String allowedExtensions) { this.allowedExtensions = allowedExtensions; }
}