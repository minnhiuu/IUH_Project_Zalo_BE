package com.bondhub.fileservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.client.fileservice.FileUploadResponse;
import com.bondhub.fileservice.service.file.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<FileUploadResponse>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "misc") String folder) throws IOException {
        FileUploadResponse fileUploadResponse = fileService.uploadFile(file, folder);
        return ResponseEntity.ok(ApiResponse.success(fileUploadResponse));
    }

    @GetMapping("/download/{filename}")
    public ResponseEntity<byte[]> download(@PathVariable String filename) {
        byte[] data = fileService.downloadFile(filename);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .body(data);
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteByQuery(@RequestParam("key") String key) {
        fileService.deleteFile(key);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

    @DeleteMapping("/**")
    public ResponseEntity<ApiResponse<Void>> deleteLegacy(HttpServletRequest request) {
        String rawPath = request.getRequestURI();
        String marker = "/files/";
        int index = rawPath.indexOf(marker);

        String encodedKey = index >= 0 ? rawPath.substring(index + marker.length()) : "";
        String key = URLDecoder.decode(encodedKey, StandardCharsets.UTF_8);

        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("File key must not be blank");
        }

        fileService.deleteFile(key);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }
}
