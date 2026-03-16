package com.bondhub.fileservice.service.file;

import com.bondhub.common.dto.client.fileservice.FileUploadResponse;
import com.bondhub.common.utils.S3Util;
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
        public FileUploadResponse uploadFile(MultipartFile file)
                        throws IOException {
                String fileName = file.getOriginalFilename();
                String key = UUID.randomUUID() + "_" + fileName;
                s3Client.putObject(PutObjectRequest.builder()
                                .bucket(bucketName)
                                .key(key)
                                .contentType(file.getContentType())
                                .build(),
                                RequestBody.fromBytes(file.getBytes()));

                return FileUploadResponse.builder()
                                .fileName(fileName)
                                .key(key)
                                .build();
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
