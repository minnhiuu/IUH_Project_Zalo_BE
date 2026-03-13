package com.bondhub.friendservice.repository;

import com.bondhub.friendservice.model.FriendShip;
import com.bondhub.friendservice.model.enums.FriendStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendShipRepository extends MongoRepository<FriendShip, String> {
    
    // Paginated queries
    Page<FriendShip> findByRequestedAndFriendStatusOrderByCreatedAtDesc(String userId, FriendStatus status, Pageable pageable);
    
    Page<FriendShip> findByReceivedAndFriendStatusOrderByCreatedAtDesc(String userId, FriendStatus status, Pageable pageable);
    
    // Non-paginated queries (for backward compatibility)
    List<FriendShip> findByRequestedAndFriendStatusOrderByCreatedAtDesc(String userId, FriendStatus status);
    
    List<FriendShip> findByReceivedAndFriendStatusOrderByCreatedAtDesc(String userId, FriendStatus status);
    
    @Query("{ $and: [ " +
           "{ $or: [ " +
           "  { 'requested': ?0, 'received': ?1 }, " +
           "  { 'requested': ?1, 'received': ?0 } " +
           "] }, " +
           "{ 'friendStatus': { $in: ['PENDING', 'ACCEPTED'] } } " +
           "] }")
    Optional<FriendShip> findFriendshipBetweenUsers(String userId1, String userId2);
    
    @Query("{ $and: [ " +
           "{ $or: [ { 'requested': ?0 }, { 'received': ?0 } ] }, " +
           "{ 'friendStatus': 'ACCEPTED' } " +
           "] }")
    Page<FriendShip> findAllFriendsByUserId(String userId, Pageable pageable);
    
    @Query("{ $and: [ " +
           "{ $or: [ { 'requested': ?0 }, { 'received': ?0 } ] }, " +
           "{ 'friendStatus': 'ACCEPTED' } " +
           "] }")
    List<FriendShip> findAllFriendsByUserId(String userId);
    
    @Query("{ $and: [ " +
           "{ $or: [ " +
           "  { 'requested': ?0, 'received': ?1 }, " +
           "  { 'requested': ?1, 'received': ?0 } " +
           "] }, " +
           "{ 'friendStatus': 'ACCEPTED' } " +
           "] }")
    Optional<FriendShip> findAcceptedFriendship(String userId1, String userId2);
    
    @Query("{ $or: [ { 'requested': ?0 }, { 'received': ?0 } ] }")
    List<FriendShip> findAllFriendshipsByUserId(String userId);
}
