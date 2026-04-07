package com.bondhub.fileservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.client.fileservice.FileUploadResponse;
import com.bondhub.fileservice.service.file.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @DeleteMapping("/{folder}/{fileName:.+}")
    public ResponseEntity<ApiResponse<Void>> deleteLegacyByFolder(
            @PathVariable String folder,
            @PathVariable String fileName) {
        fileService.deleteFile(folder + "/" + fileName);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

    @DeleteMapping("/{encodedKey:.+}")
    public ResponseEntity<ApiResponse<Void>> deleteLegacyEncoded(@PathVariable String encodedKey) {
        String key = URLDecoder.decode(encodedKey, StandardCharsets.UTF_8);
        fileService.deleteFile(key);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }
}
