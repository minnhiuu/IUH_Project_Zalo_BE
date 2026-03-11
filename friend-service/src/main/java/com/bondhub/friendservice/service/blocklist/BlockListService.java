package com.bondhub.friendservice.service.blocklist;

import com.bondhub.friendservice.dto.request.BlockUserRequest;
import com.bondhub.friendservice.dto.request.UpdateBlockPreferenceRequest;
import com.bondhub.friendservice.dto.response.BlockedUserDetailResponse;
import com.bondhub.friendservice.dto.response.BlockedUserResponse;

import java.util.List;

/**
 * Service interface for managing the block list between users.
 * Provides operations to block/unblock users and manage block preferences.
 */
public interface BlockListService {

    /**
     * Block a user based on the given request.
     *
     * @param request the request containing the target user ID and optional block preferences
     * @return the created {@link BlockedUserResponse} representing the block relationship
     */
    BlockedUserResponse blockUser(BlockUserRequest request);

    /**
     * Unblock a previously blocked user.
     *
     * @param blockedUserId the ID of the user to unblock
     */
    void unblockUser(String blockedUserId);

    /**
     * Update blocking preferences (message, call, story) for a blocked user.
     *
     * @param blockedUserId the ID of the blocked user
     * @param request       the request containing fields to update
     * @return the updated {@link BlockedUserResponse}
     */
    BlockedUserResponse updateBlockPreference(String blockedUserId, UpdateBlockPreferenceRequest request);

    /**
     * Get all users blocked by the currently authenticated user.
     *
     * @return list of {@link BlockedUserResponse}
     */
    List<BlockedUserResponse> getMyBlockedUsers();

    /**
     * Get all users blocked by the currently authenticated user with their profile details.
     *
     * @return list of {@link BlockedUserDetailResponse} enriched with user profile information
     */
    List<BlockedUserDetailResponse> getMyBlockedUsersWithDetails();

    /**
     * Check whether the currently authenticated user has blocked the given user.
     *
     * @param userId the ID of the user to check
     * @return {@code true} if blocked, {@code false} otherwise
     */
    boolean isUserBlocked(String userId);

    /**
     * Get the block details for a specific blocked user.
     *
     * @param blockedUserId the ID of the blocked user
     * @return the {@link BlockedUserResponse} or {@code null} if not blocked
     */
    BlockedUserResponse getBlockDetails(String blockedUserId);
}
