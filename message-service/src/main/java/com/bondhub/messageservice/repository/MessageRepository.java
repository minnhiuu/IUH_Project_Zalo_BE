package com.bondhub.messageservice.repository;

import com.bondhub.messageservice.model.Message;
import com.bondhub.common.enums.MessageType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MessageRepository extends MongoRepository<Message, String> {

    @Query("{ 'conversationId': ?0, 'deletedBy': { $ne: ?1 } }")
    Page<Message> findByConversationIdAndNotDeleted(String conversationId, String userId, Pageable pageable);

    Page<Message> findByConversationId(String conversationId, Pageable pageable);

    boolean existsByConversationIdAndType(String conversationId, MessageType type);

    Optional<Message> findTopByConversationIdAndTypeOrderByCreatedAtDesc(String conversationId, MessageType type);
}
