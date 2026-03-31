package com.bondhub.messageservice.repository;

import com.bondhub.messageservice.model.ChatUser;
import com.bondhub.common.enums.Status;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatUserRepository extends MongoRepository<ChatUser, String> {
    List<ChatUser> findAllByStatus(Status status);

    List<ChatUser> findByIdInAndStatus(Iterable<String> ids, Status status);
}
