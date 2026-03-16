package com.bondhub.friendservice.repository;

import com.bondhub.friendservice.model.BlockList;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BlockListRepository extends MongoRepository<BlockList, String> {

    /**
     * Find a block relationship between two users
     */
    Optional<BlockList> findByBlockerIdAndBlockedUserId(String blockerId, String blockedUserId);

    /**
     * Find all users blocked by a specific user
     */
    List<BlockList> findByBlockerId(String blockerId);

    /**
     * Find all users who blocked a specific user
     */
    List<BlockList> findByBlockedUserId(String blockedUserId);

    /**
     * Check if a user is blocked by another user
     */
    boolean existsByBlockerIdAndBlockedUserId(String blockerId, String blockedUserId);

    /**
     * Delete a block relationship
     */
    void deleteByBlockerIdAndBlockedUserId(String blockerId, String blockedUserId);
}
