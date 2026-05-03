package com.bondhub.messageservice.repository;

import com.bondhub.messageservice.model.ChatUser;
import com.bondhub.common.enums.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface ChatUserRepository extends MongoRepository<ChatUser, String> {
    List<ChatUser> findAllByStatus(Status status);

    List<ChatUser> findByIdInAndStatus(Iterable<String> ids, Status status);

    Page<ChatUser> findByIdInAndFullNameContainingIgnoreCase(Set<String> ids, String fullName, Pageable pageable);

    Optional<ChatUser> findByPhoneNumber(String phoneNumber);
}
