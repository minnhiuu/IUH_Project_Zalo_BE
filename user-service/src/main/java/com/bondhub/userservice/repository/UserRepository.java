package com.bondhub.userservice.repository;

import com.bondhub.userservice.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByAccountId(String accountId);

    boolean existsById(String id);

    // ---- Admin search / filter ----

    /** Keyword search on fullName (case-insensitive) */
    Page<User> findByFullNameContainingIgnoreCase(String fullName, Pageable pageable);

    /** Filter by active=false (BANNED) */
    Page<User> findByActive(Boolean active, Pageable pageable);

    /** Filter ACTIVE: active=true */
    @Query("{ 'active': { $ne: false } }")
    Page<User> findActiveUsers(Pageable pageable);

    /** ACTIVE + keyword combined */
    @Query("{ 'active': { $ne: false }, 'fullName': { $regex: ?0, $options: 'i' } }")
    Page<User> findActiveUsersByKeyword(String keyword, Pageable pageable);

    /** Keyword + BANNED combined */
    Page<User> findByActiveAndFullNameContainingIgnoreCase(Boolean active, String fullName, Pageable pageable);
}
