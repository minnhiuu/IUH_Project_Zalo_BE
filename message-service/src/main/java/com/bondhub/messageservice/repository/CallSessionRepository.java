package com.bondhub.messageservice.repository;

import com.bondhub.messageservice.model.CallSession;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface CallSessionRepository extends MongoRepository<CallSession, String> {

    Optional<CallSession> findByRoomId(String roomId);

    boolean existsByCallerIdAndStatus(String callerId, CallSession.CallStatus status);

    boolean existsByReceiverIdAndStatus(String receiverId, CallSession.CallStatus status);
}
