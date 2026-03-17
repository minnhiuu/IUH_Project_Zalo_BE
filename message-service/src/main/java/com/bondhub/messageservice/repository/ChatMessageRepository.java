package com.bondhub.messageservice.repository;

import com.bondhub.messageservice.model.Message;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.mongodb.repository.Query;

@Repository
public interface ChatMessageRepository extends MongoRepository<Message, String> {
    @Query("{ 'chatId': ?0, 'deletedBy': { $ne: ?1 } }")
    Page<Message> findByChatIdAndNotDeleted(String chatId, String userId, Pageable pageable);

    Page<Message> findByChatId(String chatId, Pageable pageable);
}
