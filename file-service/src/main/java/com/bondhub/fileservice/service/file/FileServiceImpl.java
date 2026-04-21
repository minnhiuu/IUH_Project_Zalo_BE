package com.bondhub.fileservice.service.file;

import com.bondhub.common.dto.client.fileservice.FileUploadResponse;
import com.bondhub.fileservice.dto.IngestUploadResponse;
import com.bondhub.fileservice.dto.PresignFileRequest;
import com.bondhub.fileservice.dto.PresignedUploadResponse;
import com.bondhub.common.utils.S3UtilV2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FileServiceImpl implements FileService {

        @Autowired
        private S3Client s3Client;

        @Autowired
        private S3Presigner s3Presigner;

        @Autowired
        private S3UtilV2 s3UtilV2;

        @Override
        public FileUploadResponse uploadFile(MultipartFile file, String folder)
                        throws IOException {
                String fileName = file.getOriginalFilename();
                String key = folder + "/" + UUID.randomUUID() + "_" + fileName;
                s3Client.putObject(PutObjectRequest.builder()
                                .bucket(s3UtilV2.getBucketName())
                                .key(key)
                                .contentType(file.getContentType())
                                .build(),
                                RequestBody.fromBytes(file.getBytes()));

                String url = s3UtilV2.getS3BaseUrl() + key;

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
                                .bucket(s3UtilV2.getBucketName())
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
                                file.getSize());
        }

        @Override
        public void deleteFile(String key) {
                s3Client.deleteObject(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.builder()
                                .bucket(s3UtilV2.getBucketName())
                                .key(key)
                                .build());
        }

        @Override
        public byte[] downloadFile(String key) {
                ResponseBytes<GetObjectResponse> objectAsBytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                                .bucket(s3UtilV2.getBucketName())
                                .key(key)
                                .build());
                return objectAsBytes.asByteArray();
        }

        @Override
        public List<PresignedUploadResponse> generatePresignedUrls(List<PresignFileRequest> requests) {
                return requests.stream().map(req -> {
                        String key = req.folder() + "/" + UUID.randomUUID() + "_" + req.fileName();

                        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                                        .bucket(s3UtilV2.getBucketName())
                                        .key(key)
                                        .contentType(req.contentType())
                                        .build();

                        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                                        .signatureDuration(Duration.ofMinutes(15))
                                        .putObjectRequest(putObjectRequest)
                                        .build();

                        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

                        return PresignedUploadResponse.builder()
                                        .key(key)
                                        .presignedUrl(presignedRequest.url().toString())
                                        .publicUrl(s3UtilV2.getS3BaseUrl() + key)
                                        .contentType(req.contentType())
                                        .originalFileName(req.fileName())
                                        .size(req.size())
                                        .expiresAt(presignedRequest.expiration())
                                        .build();
                }).collect(Collectors.toList());
        }
}
