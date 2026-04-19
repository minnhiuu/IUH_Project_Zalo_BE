package com.bondhub.fileservice.service.file;

import com.bondhub.common.dto.client.fileservice.FileUploadResponse;
import com.bondhub.common.utils.S3Util;
import com.bondhub.fileservice.dto.IngestUploadResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Service
public class FileServiceImpl implements FileService {

    @Autowired
    private S3Client s3Client;

    @Value("${aws.s3.bucket.name}")
    private String bucketName;

    @Value("${cloud.aws.region.static}")
    private String region;

    @Override
    public FileUploadResponse uploadFile(MultipartFile file, String folder)
            throws IOException {
        String fileName = file.getOriginalFilename();
        String key = folder + "/" + UUID.randomUUID() + "_" + fileName;
        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .contentType(file.getContentType())
                        .build(),
                RequestBody.fromBytes(file.getBytes()));

        String url = S3Util.getS3BaseUrl(bucketName, region) + key;

        return FileUploadResponse.builder()
                .key(key)
                .url(url)
                .fileName(fileName)
                .originalFileName(file.getOriginalFilename())
                .contentType(file.getContentType())
                .size(file.getSize())
                .build();
    }

    @Override
    public IngestUploadResponse uploadForIngest(MultipartFile file, String conversationId, String folder)
            throws IOException {
        String docId = UUID.randomUUID().toString();
        String originalFileName = file.getOriginalFilename();
        String key = folder + "/" + conversationId + "/" + docId + "/" + originalFileName;

        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .contentType(file.getContentType())
                        .build(),
                RequestBody.fromBytes(file.getBytes()));

        return new IngestUploadResponse(
                docId,
                conversationId,
                key,
                originalFileName,
                originalFileName,
                file.getContentType(),
                file.getSize()
        );
    }

    @Override
    public void deleteFile(String key) {
        s3Client.deleteObject(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build());
    }

    @Override
    public byte[] downloadFile(String key) {
        ResponseBytes<GetObjectResponse> objectAsBytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build());
        return objectAsBytes.asByteArray();
    }
}
