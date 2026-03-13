package com.bondhub.userservice.repository;

import com.bondhub.userservice.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByAccountId(String accountId);

    List<User> findAllByOrderByIdAsc(Pageable pageable);

    List<User> findByIdGreaterThanOrderByIdAsc(String id, Pageable pageable);

    List<User> findByLastModifiedAtAfter(LocalDateTime lastModifiedAt);

    boolean existsById(String id);

    Page<User> findByFullNameContainingIgnoreCase(String fullName, Pageable pageable);

    Page<User> findByActive(Boolean active, Pageable pageable);

    @Query("{ 'active': { $ne: false } }")
    Page<User> findActiveUsers(Pageable pageable);

    @Query("{ 'active': { $ne: false }, 'fullName': { $regex: ?0, $options: 'i' } }")
    Page<User> findActiveUsersByKeyword(String keyword, Pageable pageable);

    Page<User> findByActiveAndFullNameContainingIgnoreCase(Boolean active, String fullName, Pageable pageable);
}