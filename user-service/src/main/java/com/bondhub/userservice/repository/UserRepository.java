package com.bondhub.userservice.repository;

import com.bondhub.userservice.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByAccountId(String accountId);

    List<User> findAllByOrderByIdAsc(Pageable pageable);

    List<User> findByIdGreaterThanOrderByIdAsc(String id, Pageable pageable);
    
    List<User> findByLastModifiedAtAfter(LocalDateTime lastModifiedAt);
}
