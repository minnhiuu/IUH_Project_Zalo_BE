package com.bondhub.messageservice.repository;

import com.bondhub.common.enums.MessageType;
import com.bondhub.messageservice.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface MessageRepository extends MongoRepository<Message, String> {

    @Query("{ 'conversationId': ?0, 'deletedBy': { $ne: ?1 }, "
         + "$and: [ "
         + "  { $or: [ { 'visibleTo': { $exists: false } }, { 'visibleTo': null }, { 'visibleTo': { $size: 0 } }, { 'visibleTo': ?1 } ] }, "
         + "  { $or: [ { 'type': { $ne: 'SYSTEM' } }, { 'createdAt': { $gte: ?2 } } ] } "
         + "] }")
    Page<Message> findByConversationIdAndNotDeleted(String conversationId, String userId, LocalDateTime memberJoinedAt, Pageable pageable);

    @Query("{ 'conversationId': ?0, 'deletedBy': { $ne: ?1 }, 'type': ?2, $or: [ { 'visibleTo': { $exists: false } }, { 'visibleTo': null }, { 'visibleTo': { $size: 0 } }, { 'visibleTo': ?1 } ] }")
    Page<Message> findByConversationIdAndTypeAndNotDeleted(
            String conversationId,
            String userId,
            MessageType type,
            Pageable pageable
    );

    Page<Message> findByConversationId(String conversationId, Pageable pageable);
}
