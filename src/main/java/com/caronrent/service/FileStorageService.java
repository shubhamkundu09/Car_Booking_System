package com.caronrent.service;

import com.caronrent.config.FileStorageProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path carImageStorageLocation;
    private final Path documentStorageLocation;
    private final FileStorageProperties fileStorageProperties;

    public FileStorageService(FileStorageProperties fileStorageProperties) {
        this.fileStorageProperties = fileStorageProperties;
        this.carImageStorageLocation = Paths.get(fileStorageProperties.getCarImages()).toAbsolutePath().normalize();
        this.documentStorageLocation = Paths.get(fileStorageProperties.getDocuments()).toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.carImageStorageLocation);
            Files.createDirectories(this.documentStorageLocation);
            System.out.println("✅ File storage directories created at: " + carImageStorageLocation);
            System.out.println("✅ Document storage directories created at: " + documentStorageLocation);
        } catch (IOException ex) {
            throw new RuntimeException("Could not create upload directories", ex);
        }
    }

    /**
     * Store a car image file and return URL
     */
    public String storeCarImage(MultipartFile file) {
        String fileName = storeFile(file, carImageStorageLocation, "car");
        return generateFileUrl(fileName, false);
    }

    /**
     * Store multiple car images and return URLs
     */
    public List<String> storeCarImages(List<MultipartFile> files) {
        List<String> fileUrls = new ArrayList<>();
        if (files != null) {
            for (MultipartFile file : files) {
                if (!file.isEmpty()) {
                    fileUrls.add(storeCarImage(file));
                }
            }
        }
        return fileUrls;
    }

    /**
     * Store a document file and return URL
     */
    public String storeDocument(MultipartFile file, String documentType) {
        String fileName = storeFile(file, documentStorageLocation, documentType);
        return generateFileUrl(fileName, true);
    }



    /**
     * Generate URL for file access
     */
    private String generateFileUrl(String fileName, boolean isDocument) {
        String endpoint = isDocument ? "/api/files/document/" : "/api/files/car/";
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(endpoint)
                .path(fileName)
                .toUriString();
    }

    /**
     * Validate file before storing
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("File is empty");
        }

        if (file.getSize() > fileStorageProperties.getMaxSize()) {
            throw new RuntimeException("File size exceeds maximum limit of " +
                    (fileStorageProperties.getMaxSize() / 1048576) + "MB");
        }

        String fileName = StringUtils.cleanPath(file.getOriginalFilename());
        String fileExtension = getFileExtension(fileName).toLowerCase();

        String allowedExtensions = fileStorageProperties.getAllowedExtensions();
        if (!allowedExtensions.contains(fileExtension)) {
            throw new RuntimeException("File type not allowed. Allowed types: " + allowedExtensions);
        }
    }

    /**
     * Get file extension
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf(".") == -1) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf("."));
    }

    /**
     * Delete a file by filename and type (NEW METHOD)
     */
    public boolean deleteFile(String fileName, boolean isDocument) {
        try {
            Path storageLocation = isDocument ? documentStorageLocation : carImageStorageLocation;
            Path filePath = storageLocation.resolve(fileName);
            return Files.deleteIfExists(filePath);
        } catch (IOException ex) {
            System.err.println("⚠️ Could not delete file: " + fileName + " - " + ex.getMessage());
            return false;
        }
    }

    /**
     * Delete a file by full path (BACKWARD COMPATIBLE METHOD)
     */
    public boolean deleteFile(String filePath) {
        try {
            if (filePath == null || filePath.isEmpty()) {
                return false;
            }
            Path path = Paths.get(filePath);
            return Files.deleteIfExists(path);
        } catch (IOException ex) {
            System.err.println("⚠️ Could not delete file: " + filePath + " - " + ex.getMessage());
            return false;
        }
    }

    /**
     * Delete a file by URL (NEW HELPER METHOD)
     */
    public boolean deleteFileByUrl(String fileUrl) {
        try {
            String fileName = extractFilenameFromUrl(fileUrl);
            if (fileName == null) {
                return false;
            }

            // Determine if it's a document or car image based on URL path
            boolean isDocument = fileUrl.contains("/api/files/document/");
            return deleteFile(fileName, isDocument);

        } catch (Exception ex) {
            System.err.println("⚠️ Could not delete file from URL: " + fileUrl + " - " + ex.getMessage());
            return false;
        }
    }

    /**
     * Get file as resource
     */
    public Path getFilePath(String fileName, boolean isDocument) {
        if (isDocument) {
            return documentStorageLocation.resolve(fileName);
        } else {
            return carImageStorageLocation.resolve(fileName);
        }
    }

    /**
     * Extract filename from URL
     */
    public String extractFilenameFromUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return null;
        }
        return fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
    }



    // Add this method to ensure file is readable
    private void ensureFilePermissions(Path filePath) {
        try {
            File file = filePath.toFile();
            file.setReadable(true, false);  // readable by所有人
            file.setWritable(true, true);    // writable only by owner
        } catch (Exception e) {
            System.err.println("Could not set file permissions: " + e.getMessage());
        }
    }

    // Update the storeFile method to set permissions after saving
    private String storeFile(MultipartFile file, Path storageLocation, String prefix) {
        validateFile(file);

        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
        String fileExtension = getFileExtension(originalFileName);
        String fileName = prefix + "_" + UUID.randomUUID().toString() + fileExtension;

        try {
            if (fileName.contains("..")) {
                throw new RuntimeException("Filename contains invalid path sequence: " + fileName);
            }

            Path targetLocation = storageLocation.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            // Ensure file is readable
            ensureFilePermissions(targetLocation);

            return fileName;
        } catch (IOException ex) {
            throw new RuntimeException("Could not store file " + fileName, ex);
        }
    }
}