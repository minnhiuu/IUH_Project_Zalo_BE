package com.bondhub.fileservice.service.file;

import com.bondhub.common.dto.client.fileservice.FileUploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface FileService {
    FileUploadResponse uploadFile(MultipartFile file) throws IOException;

    byte[] downloadFile(String key);

    void deleteFile(String key);
}
