package com.bondhub.searchservice.repository;

import com.bondhub.searchservice.model.mongodb.FailedEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FailedEventRepository extends MongoRepository<FailedEvent, String> {

    Page<FailedEvent> findAllByResolved(boolean resolved, Pageable pageable);
    
    @Query("{ $and: [ { 'resolved': ?0 }, { $or: [ { 'eventId': { $regex: ?1, $options: 'i' } }, { 'errorMessage': { $regex: ?1, $options: 'i' } } ] } ] }")
    Page<FailedEvent> searchWithResolved(boolean resolved, String keyword, Pageable pageable);

    @Query("{ $or: [ { 'eventId': { $regex: ?0, $options: 'i' } }, { 'errorMessage': { $regex: ?0, $options: 'i' } } ] }")
    Page<FailedEvent> searchWithoutResolved(String keyword, Pageable pageable);

    long countByResolved(boolean resolved);

    List<FailedEvent> findAllByResolved(boolean resolved);

    List<FailedEvent> findAllByResolvedAndCreatedAtAfter(boolean resolved, LocalDateTime createdAt);
}
