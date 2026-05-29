package com.bondhub.fileservice.service.file;

import com.bondhub.common.dto.client.fileservice.FileUploadResponse;
import com.bondhub.common.utils.S3UtilV2;
import com.bondhub.fileservice.dto.PresignFileRequest;
import com.bondhub.fileservice.dto.PresignedUploadResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceImplTest {

    @Mock
    private S3Client s3Client;
    @Mock
    private S3Presigner s3Presigner;
    @Mock
    private S3UtilV2 s3UtilV2;
    @Mock
    private MultipartFile multipartFile;

    @InjectMocks
    private FileServiceImpl fileService;

    @BeforeEach
    void setUp() {
        when(s3UtilV2.getBucketName()).thenReturn("test-bucket");
        when(s3UtilV2.getS3BaseUrl()).thenReturn("http://s3.local/");
    }

    @Test
    @DisplayName("UC19-UTCID01 - Upload file success")
    void uploadFile_Success() throws IOException {
        when(multipartFile.getOriginalFilename()).thenReturn("doc.pdf");
        when(multipartFile.getContentType()).thenReturn("application/pdf");
        when(multipartFile.getBytes()).thenReturn("data".getBytes(StandardCharsets.UTF_8));
        when(multipartFile.getSize()).thenReturn(4L);

        FileUploadResponse response = fileService.uploadFile(multipartFile, "docs");

        ArgumentCaptor<PutObjectRequest> putCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(putCaptor.capture(), any(RequestBody.class));

        PutObjectRequest putRequest = putCaptor.getValue();
        assertEquals("test-bucket", putRequest.bucket());
        assertEquals("application/pdf", putRequest.contentType());
        assertTrue(putRequest.key().startsWith("docs/"));
        assertTrue(putRequest.key().endsWith("_doc.pdf"));

        assertEquals("doc.pdf", response.fileName());
        assertEquals("doc.pdf", response.originalFileName());
        assertEquals("application/pdf", response.contentType());
        assertEquals(4L, response.size());
        assertTrue(response.url().startsWith("http://s3.local/"));
        assertTrue(response.key().startsWith("docs/"));
    }

    @Test
    @DisplayName("UC19-UTCID02 - Upload file throws IOException")
    void uploadFile_IOException() throws IOException {
        when(multipartFile.getOriginalFilename()).thenReturn("doc.pdf");
        when(multipartFile.getContentType()).thenReturn("application/pdf");
        when(multipartFile.getBytes()).thenThrow(new IOException("read failed"));

        assertThrows(IOException.class, () -> fileService.uploadFile(multipartFile, "docs"));

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("UC19-UTCID03 - Upload file with null filename")
    void uploadFile_NullFileName() throws IOException {
        when(multipartFile.getOriginalFilename()).thenReturn(null);
        when(multipartFile.getContentType()).thenReturn("application/pdf");
        when(multipartFile.getBytes()).thenReturn("data".getBytes(StandardCharsets.UTF_8));
        when(multipartFile.getSize()).thenReturn(4L);

        FileUploadResponse response = fileService.uploadFile(multipartFile, "docs");

        assertTrue(response.key().endsWith("_null"));
        assertEquals(null, response.fileName());
    }

    @Test
    @DisplayName("UC19-UTCID04 - Upload file with null content type")
    void uploadFile_NullContentType() throws IOException {
        when(multipartFile.getOriginalFilename()).thenReturn("doc.pdf");
        when(multipartFile.getContentType()).thenReturn(null);
        when(multipartFile.getBytes()).thenReturn("data".getBytes(StandardCharsets.UTF_8));
        when(multipartFile.getSize()).thenReturn(4L);

        FileUploadResponse response = fileService.uploadFile(multipartFile, "docs");

        assertEquals(null, response.contentType());
    }

    @Test
    @DisplayName("UC19-UTCID05 - Upload file with empty folder")
    void uploadFile_EmptyFolder() throws IOException {
        when(multipartFile.getOriginalFilename()).thenReturn("doc.pdf");
        when(multipartFile.getContentType()).thenReturn("application/pdf");
        when(multipartFile.getBytes()).thenReturn("data".getBytes(StandardCharsets.UTF_8));
        when(multipartFile.getSize()).thenReturn(4L);

        FileUploadResponse response = fileService.uploadFile(multipartFile, "");

        assertTrue(response.key().startsWith("/"));
    }

    @Test
    @DisplayName("UC19-UTCID06 - Upload file throws runtime exception")
    void uploadFile_RuntimeException() throws IOException {
        when(multipartFile.getOriginalFilename()).thenReturn("doc.pdf");
        when(multipartFile.getContentType()).thenReturn("application/pdf");
        when(multipartFile.getBytes()).thenReturn("data".getBytes(StandardCharsets.UTF_8));
        when(multipartFile.getSize()).thenReturn(4L);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(new RuntimeException("s3 error"));

        assertThrows(RuntimeException.class, () -> fileService.uploadFile(multipartFile, "docs"));
    }

    @Test
    @DisplayName("UC20-UTCID01 - Generate presigned URLs success")
    void generatePresignedUrls_Success() throws Exception {
        PresignFileRequest request = PresignFileRequest.builder()
                .fileName("report.pdf")
                .contentType("application/pdf")
                .size(123L)
                .folder("docs")
                .build();

        PresignedPutObjectRequest presigned = org.mockito.Mockito.mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("http://presigned.local/put"));
        when(presigned.expiration()).thenReturn(Instant.parse("2026-05-30T00:00:00Z"));
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);

        List<PresignedUploadResponse> responses = fileService.generatePresignedUrls(List.of(request));

        assertEquals(1, responses.size());
        PresignedUploadResponse response = responses.get(0);
        assertTrue(response.key().startsWith("docs/"));
        assertEquals("application/pdf", response.contentType());
        assertEquals("report.pdf", response.originalFileName());
        assertEquals(123L, response.size());
        assertNotNull(response.presignedUrl());
        assertTrue(response.publicUrl().startsWith("http://s3.local/"));
    }

    @Test
    @DisplayName("UC20-UTCID02 - Generate presigned URLs for multiple files")
    void generatePresignedUrls_MultipleRequests() throws Exception {
        PresignFileRequest request1 = PresignFileRequest.builder()
                .fileName("report.pdf")
                .contentType("application/pdf")
                .size(123L)
                .folder("docs")
                .build();
        PresignFileRequest request2 = PresignFileRequest.builder()
                .fileName("image.png")
                .contentType("image/png")
                .size(456L)
                .folder("images")
                .build();

        PresignedPutObjectRequest presigned = org.mockito.Mockito.mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("http://presigned.local/put"));
        when(presigned.expiration()).thenReturn(Instant.parse("2026-05-30T00:00:00Z"));
        when(s3Presigner.presignPutObject((PutObjectPresignRequest) any())).thenReturn(presigned);

        List<PresignedUploadResponse> responses = fileService.generatePresignedUrls(List.of(request1, request2));

        assertEquals(2, responses.size());
        assertTrue(responses.get(0).key().startsWith("docs/"));
        assertTrue(responses.get(1).key().startsWith("images/"));
    }

    @Test
    @DisplayName("UC20-UTCID03 - Generate presigned URLs with empty list")
    void generatePresignedUrls_EmptyList() {
        List<PresignedUploadResponse> responses = fileService.generatePresignedUrls(List.of());
        assertTrue(responses.isEmpty());
        verify(s3Presigner, never()).presignPutObject((PutObjectPresignRequest) any());
    }

    @Test
    @DisplayName("UC20-UTCID04 - Generate presigned URLs throws exception")
    void generatePresignedUrls_ThrowsException() throws Exception {
        PresignFileRequest request = PresignFileRequest.builder()
                .fileName("report.pdf")
                .contentType("application/pdf")
                .size(123L)
                .folder("docs")
                .build();
        when(s3Presigner.presignPutObject((PutObjectPresignRequest) any())).thenThrow(new RuntimeException("presign failed"));

        assertThrows(RuntimeException.class, () -> fileService.generatePresignedUrls(List.of(request)));
    }

    @Test
    @DisplayName("UC20-UTCID05 - Generate presigned URLs with null list")
    void generatePresignedUrls_NullList() {
        assertThrows(NullPointerException.class, () -> fileService.generatePresignedUrls(null));
    }

    @Test
    @DisplayName("UC20-UTCID06 - Generate presigned URLs with empty file name")
    void generatePresignedUrls_EmptyFileName() throws Exception {
        PresignFileRequest request = PresignFileRequest.builder()
                .fileName("")
                .contentType("application/pdf")
                .size(123L)
                .folder("docs")
                .build();

        PresignedPutObjectRequest presigned = org.mockito.Mockito.mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("http://presigned.local/put"));
        when(presigned.expiration()).thenReturn(Instant.parse("2026-05-30T00:00:00Z"));
        when(s3Presigner.presignPutObject((PutObjectPresignRequest) any())).thenReturn(presigned);

        List<PresignedUploadResponse> responses = fileService.generatePresignedUrls(List.of(request));

        assertEquals(1, responses.size());
        assertTrue(responses.get(0).key().startsWith("docs/"));
        assertEquals("", responses.get(0).originalFileName());
    }

    @Test
    @DisplayName("UC21-UTCID01 - Download file success")
    void downloadFile_Success() {
        byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
        ResponseBytes<GetObjectResponse> responseBytes = ResponseBytes.fromByteArray(
                GetObjectResponse.builder().build(), data);

        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes);

        byte[] result = fileService.downloadFile("docs/file.txt");

        assertEquals("hello", new String(result, StandardCharsets.UTF_8));
        verify(s3Client).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    @DisplayName("UC21-UTCID02 - Download file throws exception")
    void downloadFile_ThrowsException() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(new RuntimeException("not found"));

        assertThrows(RuntimeException.class, () -> fileService.downloadFile("docs/missing.txt"));
    }

    @Test
    @DisplayName("UC21-UTCID03 - Download file with empty key")
    void downloadFile_EmptyKey() {
        byte[] data = "x".getBytes(StandardCharsets.UTF_8);
        ResponseBytes<GetObjectResponse> responseBytes = ResponseBytes.fromByteArray(
                GetObjectResponse.builder().build(), data);
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes);

        byte[] result = fileService.downloadFile("");

        assertEquals("x", new String(result, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("UC21-UTCID04 - Download file with null key")
    void downloadFile_NullKey() {
        assertThrows(NullPointerException.class, () -> fileService.downloadFile(null));
    }

    @Test
    @DisplayName("UC21-UTCID05 - Download file empty bytes")
    void downloadFile_EmptyBytes() {
        byte[] data = new byte[0];
        ResponseBytes<GetObjectResponse> responseBytes = ResponseBytes.fromByteArray(
                GetObjectResponse.builder().build(), data);
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes);

        byte[] result = fileService.downloadFile("docs/empty.txt");

        assertEquals(0, result.length);
    }

    @Test
    @DisplayName("UC21-UTCID06 - Download file returns null response")
    void downloadFile_NullResponseBytes() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(null);

        assertThrows(NullPointerException.class, () -> fileService.downloadFile("docs/null.txt"));
    }

    @Test
    @DisplayName("UC22-UTCID01 - Delete file success")
    void deleteFile_Success() {
        fileService.deleteFile("docs/file.txt");

        ArgumentCaptor<DeleteObjectRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(deleteCaptor.capture());

        DeleteObjectRequest deleteRequest = deleteCaptor.getValue();
        assertEquals("test-bucket", deleteRequest.bucket());
        assertEquals("docs/file.txt", deleteRequest.key());
    }

    @Test
    @DisplayName("UC22-UTCID02 - Delete file with empty key")
    void deleteFile_EmptyKey() {
        fileService.deleteFile("");

        ArgumentCaptor<DeleteObjectRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(deleteCaptor.capture());

        DeleteObjectRequest deleteRequest = deleteCaptor.getValue();
        assertEquals("", deleteRequest.key());
    }

    @Test
    @DisplayName("UC22-UTCID03 - Delete file with null key")
    void deleteFile_NullKey() {
        assertThrows(NullPointerException.class, () -> fileService.deleteFile(null));
    }

    @Test
    @DisplayName("UC22-UTCID04 - Delete file throws exception")
    void deleteFile_ThrowsException() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(new RuntimeException("delete failed"));

        assertThrows(RuntimeException.class, () -> fileService.deleteFile("docs/file.txt"));
    }

    @Test
    @DisplayName("UC22-UTCID05 - Delete file uses bucket from config")
    void deleteFile_BucketFromConfig() {
        when(s3UtilV2.getBucketName()).thenReturn("bucket-2");

        fileService.deleteFile("docs/file.txt");

        ArgumentCaptor<DeleteObjectRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(deleteCaptor.capture());

        DeleteObjectRequest deleteRequest = deleteCaptor.getValue();
        assertEquals("bucket-2", deleteRequest.bucket());
    }

    @Test
    @DisplayName("UC22-UTCID06 - Delete file with nested path")
    void deleteFile_NestedPath() {
        fileService.deleteFile("docs/sub/file.txt");

        ArgumentCaptor<DeleteObjectRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(deleteCaptor.capture());

        DeleteObjectRequest deleteRequest = deleteCaptor.getValue();
        assertEquals("docs/sub/file.txt", deleteRequest.key());
    }
}
