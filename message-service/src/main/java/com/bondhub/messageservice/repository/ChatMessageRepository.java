package com.bondhub.messageservice.repository;

import com.bondhub.messageservice.model.Message;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface ChatMessageRepository extends MongoRepository<Message, String> {
    Page<Message> findByChatId(String chatId, Pageable pageable);
}
