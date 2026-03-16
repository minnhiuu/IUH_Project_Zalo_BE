package com.bondhub.friendservice.service.blocklist;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.friendservice.client.UserServiceClient;
import com.bondhub.friendservice.dto.request.BlockUserRequest;
import com.bondhub.friendservice.dto.request.UpdateBlockPreferenceRequest;
import com.bondhub.friendservice.dto.response.BlockedUserDetailResponse;
import com.bondhub.friendservice.dto.response.BlockedUserResponse;
import com.bondhub.friendservice.mapper.BlockListMapper;
import com.bondhub.friendservice.model.BlockList;
import com.bondhub.friendservice.model.BlockPreference;
import com.bondhub.friendservice.repository.BlockListRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of {@link BlockListService} for managing user block relationships.
 * Handles blocking/unblocking users, updating block preferences, and querying block status.
 */
@Service
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class BlockListServiceImpl implements BlockListService {

    BlockListRepository blockListRepository;
    BlockListMapper blockListMapper;
    UserServiceClient userServiceClient;
    SecurityUtil securityUtil;

    @Override
    @Transactional
    public BlockedUserResponse blockUser(BlockUserRequest request) {
        String currentUserId = securityUtil.getCurrentUserId();
        log.info("User {} attempting to block user {}", currentUserId, request.blockedUserId());

        // Validate blocked user exists
        validateUserExists(request.blockedUserId());

        // Cannot block yourself
        if (currentUserId.equals(request.blockedUserId())) {
            throw new AppException(ErrorCode.CANNOT_BLOCK_YOURSELF);
        }

        // Check if already blocked
        if (blockListRepository.existsByBlockerIdAndBlockedUserId(currentUserId, request.blockedUserId())) {
            throw new AppException(ErrorCode.USER_ALREADY_BLOCKED);
        }

        BlockList blockList = blockListMapper.toBlockList(request, currentUserId);
        blockList = blockListRepository.save(blockList);

        log.info("User {} successfully blocked user {}", currentUserId, request.blockedUserId());
        return blockListMapper.toBlockedUserResponse(blockList);
    }

    @Override
    @Transactional
    public void unblockUser(String blockedUserId) {
        String currentUserId = securityUtil.getCurrentUserId();
        log.info("User {} attempting to unblock user {}", currentUserId, blockedUserId);

        // Validate blocked user exists
        validateUserExists(blockedUserId);

        BlockList blockList = blockListRepository.findByBlockerIdAndBlockedUserId(
                currentUserId, blockedUserId)
                .orElseThrow(() -> new AppException(ErrorCode.BLOCK_NOT_FOUND));

        blockListRepository.delete(blockList);

        log.info("User {} successfully unblocked user {}", currentUserId, blockedUserId);
    }

    @Override
    @Transactional
    public BlockedUserResponse updateBlockPreference(String blockedUserId, UpdateBlockPreferenceRequest request) {
        String currentUserId = securityUtil.getCurrentUserId();
        log.info("User {} updating block preferences for user {}", currentUserId, blockedUserId);

        BlockList blockList = blockListRepository.findByBlockerIdAndBlockedUserId(
                currentUserId, blockedUserId)
                .orElseThrow(() -> new AppException(ErrorCode.BLOCK_NOT_FOUND));

        BlockPreference preference = blockList.getPreference();
        if (preference == null) {
            preference = new BlockPreference();
        }

        if (request.blockMessage() != null) {
            preference.setMessage(request.blockMessage());
        }
        if (request.blockCall() != null) {
            preference.setCall(request.blockCall());
        }
        if (request.blockStory() != null) {
            preference.setStory(request.blockStory());
        }

        blockList.setPreference(preference);
        blockList = blockListRepository.save(blockList);

        log.info("Block preferences updated successfully for user {}", blockedUserId);
        return blockListMapper.toBlockedUserResponse(blockList);
    }

    @Override
    public List<BlockedUserResponse> getMyBlockedUsers() {
        String currentUserId = securityUtil.getCurrentUserId();
        log.info("Fetching blocked users for user {}", currentUserId);

        List<BlockList> blockList = blockListRepository.findByBlockerId(currentUserId);

        log.info("Found {} blocked users for user {}", blockList.size(), currentUserId);
        return blockList.stream()
                .map(blockListMapper::toBlockedUserResponse)
                .toList();
    }

    @Override
    public boolean isUserBlocked(String userId) {
        String currentUserId = securityUtil.getCurrentUserId();
        return blockListRepository.existsByBlockerIdAndBlockedUserId(currentUserId, userId);
    }

    @Override
    public BlockedUserResponse getBlockDetails(String blockedUserId) {
        String currentUserId = securityUtil.getCurrentUserId();
        log.info("Fetching block details for user {} by {}", blockedUserId, currentUserId);

        return blockListRepository.findByBlockerIdAndBlockedUserId(currentUserId, blockedUserId)
                .map(blockListMapper::toBlockedUserResponse)
                .orElse(null);
    }

    @Override
    public List<BlockedUserDetailResponse> getMyBlockedUsersWithDetails() {
        String currentUserId = securityUtil.getCurrentUserId();
        log.info("Fetching blocked users with details for user {}", currentUserId);

        List<BlockList> blockLists = blockListRepository.findByBlockerId(currentUserId);

        List<BlockedUserDetailResponse> responses = new ArrayList<>();
        for (BlockList blockList : blockLists) {
            try {
                UserSummaryResponse blockedUser = getUserSummaryById(blockList.getBlockedUserId());
                if (blockedUser != null) {
                    responses.add(blockListMapper.toBlockedUserDetailResponse(blockList, blockedUser));
                } else {
                    log.warn("Blocked user not found: {}", blockList.getBlockedUserId());
                }
            } catch (Exception e) {
                log.error("Error fetching blocked user details: {}", blockList.getBlockedUserId(), e);
            }
        }

        log.info("Found {} blocked users with details for user {}", responses.size(), currentUserId);
        return responses;
    }

    private UserSummaryResponse getUserSummaryById(String userId) {
        try {
            ApiResponse<UserSummaryResponse> response = userServiceClient.getUserSummary(userId);
            if (response != null && response.data() != null) {
                return response.data();
            }
        } catch (Exception e) {
            log.error("Failed to fetch user summary for userId: {}", userId, e);
        }
        return null;
    }

    private void validateUserExists(String userId) {
        try {
            ApiResponse<UserSummaryResponse> response = userServiceClient.getUserSummary(userId);
            if (response == null || response.data() == null) {
                throw new AppException(ErrorCode.USER_NOT_FOUND);
            }
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to validate user: {}", userId, e);
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
    }
}
