package com.bondhub.friendservice.service.blocklist;

import com.bondhub.common.exception.AppException;
import com.bondhub.friendservice.model.enums.BlockType;

public interface BlockCheckService {

    /**
     * Check if recipient has blocked sender for specific communication type
     *
     * @param senderId ID of the user trying to communicate
     * @param recipientId ID of the user receiving the communication
     * @param blockType Type of communication (MESSAGE, CALL, STORY)
     * @throws AppException if communication is blocked
     */
    void checkAndThrowIfBlocked(String senderId, String recipientId, BlockType blockType);

    /**
     * Check if either user has blocked the other (bidirectional check)
     *
     * @param userId1 First user ID
     * @param userId2 Second user ID
     * @param blockType Type of communication to check
     * @throws AppException if either user has blocked the other
     */
    void checkBidirectionalBlock(String userId1, String userId2, BlockType blockType);

    /**
     * Check if recipient has blocked sender (returns boolean instead of throwing)
     *
     * @param senderId ID of the user trying to communicate
     * @param recipientId ID of the user receiving the communication
     * @param blockType Type of communication
     * @return true if blocked, false otherwise
     */
    boolean isBlocked(String senderId, String recipientId, BlockType blockType);
}
