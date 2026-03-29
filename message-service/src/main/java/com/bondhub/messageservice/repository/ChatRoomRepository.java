package com.bondhub.messageservice.repository;

import com.bondhub.messageservice.model.Conversation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

@Repository
public interface ChatRoomRepository extends MongoRepository<Conversation, String> {
    Optional<Conversation> findBySenderIdAndRecipientId(String senderId, String recipientId);

    Optional<Conversation> findByConversationId(String conversationId);

    @Query(value = "{ '$or': [ { 'senderId': ?0 }, { 'recipientId': ?0 } ] }")
    Page<Conversation> findAllRoomsByUserId(String userId, Pageable pageable);
}
