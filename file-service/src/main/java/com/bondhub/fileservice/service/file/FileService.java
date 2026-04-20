package com.bondhub.fileservice.service.file;

import com.bondhub.common.dto.client.fileservice.FileUploadResponse;
import com.bondhub.fileservice.dto.IngestUploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface FileService {
    FileUploadResponse uploadFile(MultipartFile file, String folder) throws IOException;

    IngestUploadResponse uploadForIngest(MultipartFile file, String conversationId, String folder) throws IOException;

    byte[] downloadFile(String key);

    void deleteFile(String key);
}
