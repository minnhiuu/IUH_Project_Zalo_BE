package com.bondhub.aiservice.client.friendservice;

/**
 * DTO tối giản để AI đọc danh sách bạn bè.
 * Chỉ giữ các field cần thiết cho Tool response — tránh bloat token.
 */
public record FriendSummary(
        String userId,
        String displayName,
        String avatarUrl
) {}
