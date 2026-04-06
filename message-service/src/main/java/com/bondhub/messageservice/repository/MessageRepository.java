package com.bondhub.messageservice.repository;

import com.bondhub.messageservice.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageRepository extends MongoRepository<Message, String> {

    @Query("{ 'conversationId': ?0, 'deletedBy': { $ne: ?1 } }")
    Page<Message> findByConversationIdAndNotDeleted(String conversationId, String userId, Pageable pageable);

    Page<Message> findByConversationId(String conversationId, Pageable pageable);
}
