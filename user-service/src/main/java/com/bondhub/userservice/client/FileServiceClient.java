package com.bondhub.userservice.client;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.client.fileservice.FileUploadResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@FeignClient(name = "file-service")
public interface FileServiceClient {

    @PostMapping(value = "/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<FileUploadResponse> uploadFile(@RequestPart("file") MultipartFile file);
}
