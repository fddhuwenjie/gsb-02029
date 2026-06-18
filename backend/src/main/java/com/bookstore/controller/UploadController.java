package com.bookstore.controller;

import com.bookstore.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@RestController
@RequestMapping("/api/upload")
public class UploadController {
    
    @Value("${upload.path:/app/uploads}")
    private String uploadPath;
    
    @PostMapping("/image")
    public ApiResponse<?> uploadImage(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ApiResponse.error(400, "请选择文件");
        }
        
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".") 
            ? originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase()
            : ".jpg";
        
        String filename = UUID.randomUUID().toString() + extension;
        
        try {
            Path uploadDir = Paths.get(uploadPath);
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }
            
            Path filePath = uploadDir.resolve(filename);
            Files.write(filePath, file.getBytes());
            
            String url = "/api/upload/images/" + filename;
            return ApiResponse.success(url);
        } catch (IOException e) {
            return ApiResponse.error(500, "上传失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/images/{filename}")
    public ResponseEntity<byte[]> getImage(@PathVariable String filename) throws IOException {
        Path filePath = Paths.get(uploadPath).resolve(filename);
        if (Files.exists(filePath)) {
            byte[] data = Files.readAllBytes(filePath);
            MediaType mediaType = MediaType.IMAGE_JPEG;
            String lower = filename.toLowerCase();
            if (lower.endsWith(".png")) {
                mediaType = MediaType.IMAGE_PNG;
            } else if (lower.endsWith(".gif")) {
                mediaType = MediaType.IMAGE_GIF;
            } else if (lower.endsWith(".webp")) {
                mediaType = MediaType.parseMediaType("image/webp");
            }
            return ResponseEntity.ok().contentType(mediaType).body(data);
        }
        return ResponseEntity.notFound().build();
    }
}
