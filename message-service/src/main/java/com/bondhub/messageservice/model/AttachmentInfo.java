package com.bondhub.messageservice.model;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentInfo {
    private String key;
    private String url;
    private String fileName;
    private String originalFileName;
    private String extension;
    private String contentType;
    private Long size;
}
