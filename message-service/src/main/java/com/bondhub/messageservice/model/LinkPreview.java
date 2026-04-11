package com.bondhub.messageservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkPreview {
    String url;
    String token;
    String groupName;
    String groupAvatar;
    int memberCount;
    List<MemberSnapshot> memberPreviews;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberSnapshot {
        String name;
        String avatar;
    }
}
