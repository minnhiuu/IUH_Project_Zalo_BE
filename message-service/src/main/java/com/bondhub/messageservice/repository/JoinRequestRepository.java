package com.bondhub.messageservice.repository;

import com.bondhub.messageservice.model.JoinRequest;
import com.bondhub.messageservice.model.enums.JoinRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JoinRequestRepository extends MongoRepository<JoinRequest, String> {

    Page<JoinRequest> findByConversationIdAndStatus(String conversationId, JoinRequestStatus status, Pageable pageable);

    Optional<JoinRequest> findByConversationIdAndUserIdAndStatus(String conversationId, String userId, JoinRequestStatus status);

    boolean existsByConversationIdAndUserIdAndStatus(String conversationId, String userId, JoinRequestStatus status);

    long countByConversationIdAndStatus(String conversationId, JoinRequestStatus status);
}
