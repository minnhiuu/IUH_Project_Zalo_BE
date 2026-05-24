package com.bondhub.messageservice.repository;

import com.bondhub.messageservice.model.Reminder;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ReminderRepository extends MongoRepository<Reminder, String> {
    List<Reminder> findByConversationId(String conversationId);
    List<Reminder> findByRemindAtBetweenAndStatus(Instant start, Instant end, com.bondhub.messageservice.model.enums.ReminderStatus status);
    List<Reminder> findByNextRemindAtBetweenAndStatus(Instant start, Instant end, com.bondhub.messageservice.model.enums.ReminderStatus status);
}