package com.bondhub.friendservice.service.blocklist;

import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.friendservice.model.BlockList;
import com.bondhub.friendservice.model.BlockPreference;
import com.bondhub.friendservice.model.enums.BlockType;
import com.bondhub.friendservice.repository.BlockListRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Implementation of {@link BlockCheckService} for evaluating block status between users.
 * Communicates directly with {@link com.bondhub.friendservice.repository.BlockListRepository}
 * to avoid circular dependency with {@link BlockListService}.
 */
@Service
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class BlockCheckServiceImpl implements BlockCheckService {

    BlockListRepository blockListRepository;

    @Override
    public void checkAndThrowIfBlocked(String senderId, String recipientId, BlockType blockType) {
        log.debug("Checking if user {} is blocked by user {} for {}", senderId, recipientId, blockType);

        // Check if recipient has blocked sender
        Optional<BlockList> blockOpt = blockListRepository.findByBlockerIdAndBlockedUserId(recipientId, senderId);

        if (blockOpt.isPresent()) {
            BlockList block = blockOpt.get();
            BlockPreference preference = block.getPreference();

            if (preference == null) {
                // No preference means all communication is blocked
                throwBlockedException(blockType);
            }

            // Check specific block type
            boolean isBlocked = switch (blockType) {
                case MESSAGE -> preference.isMessage();
                case CALL -> preference.isCall();
                case STORY -> preference.isStory();
                case ALL -> preference.isMessage() || preference.isCall() || preference.isStory();
            };

            if (isBlocked) {
                log.warn("Communication blocked: sender={}, recipient={}, type={}", senderId, recipientId, blockType);
                throwBlockedException(blockType);
            }
        }
    }

    @Override
    public void checkBidirectionalBlock(String userId1, String userId2, BlockType blockType) {
        log.debug("Checking bidirectional block between {} and {} for {}", userId1, userId2, blockType);

        // Check if user1 has blocked user2
        try {
            checkAndThrowIfBlocked(userId2, userId1, blockType);
        } catch (AppException e) {
            // User1 has blocked user2
            throw e;
        }

        // Check if user2 has blocked user1
        checkAndThrowIfBlocked(userId1, userId2, blockType);
    }

    @Override
    public boolean isBlocked(String senderId, String recipientId, BlockType blockType) {
        try {
            checkAndThrowIfBlocked(senderId, recipientId, blockType);
            return false;
        } catch (AppException e) {
            return true;
        }
    }

    /**
     * Throw appropriate exception based on block type
     */
    private void throwBlockedException(BlockType blockType) {
        ErrorCode errorCode = switch (blockType) {
            case MESSAGE -> ErrorCode.MESSAGE_BLOCKED;
            case CALL -> ErrorCode.CALL_BLOCKED;
            case STORY -> ErrorCode.STORY_BLOCKED;
            case ALL -> ErrorCode.COMMUNICATION_BLOCKED;
        };

        throw new AppException(errorCode);
    }
}
