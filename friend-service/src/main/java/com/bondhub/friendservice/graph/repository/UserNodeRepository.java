package com.bondhub.friendservice.graph.repository;

import com.bondhub.friendservice.graph.node.UserNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public interface UserNodeRepository extends Neo4jRepository<UserNode, String> {

    // ===== USER NODE MANAGEMENT =====

    @Query("MERGE (u:User {id: $userId}) RETURN u")
    UserNode mergeUser(@Param("userId") String userId);

    @Query("MATCH (u:User {id: $userId}) DETACH DELETE u")
    void deleteUserNode(@Param("userId") String userId);

    // ===== FRIEND RELATIONSHIP =====

    @Query("MERGE (a:User {id: $userA}) " +
           "MERGE (b:User {id: $userB}) " +
           "MERGE (a)-[:FRIEND]-(b)")
    void createFriendRelationship(@Param("userA") String userA, @Param("userB") String userB);

    @Query("MATCH (a:User {id: $userA})-[r:FRIEND]-(b:User {id: $userB}) DELETE r")
    void removeFriendRelationship(@Param("userA") String userA, @Param("userB") String userB);

    // ===== FRIEND QUERIES =====

    @Query("MATCH (u:User {id: $userId})-[:FRIEND]-(f:User) RETURN f.id")
    List<String> findFriendIds(@Param("userId") String userId);

    @Query("MATCH (u:User {id: $userId})-[:FRIEND]-(f:User) " +
           "RETURN f.id ORDER BY f.id SKIP $skip LIMIT $limit")
    List<String> findFriendIdsPaginated(@Param("userId") String userId,
                                         @Param("skip") long skip,
                                         @Param("limit") int limit);

    @Query("MATCH (u:User {id: $userId})-[:FRIEND]-(f:User) RETURN count(f)")
    long countFriends(@Param("userId") String userId);

    // ===== MUTUAL FRIENDS =====

    @Query("MATCH (a:User {id: $userA})-[:FRIEND]-(m:User)-[:FRIEND]-(b:User {id: $userB}) " +
           "WHERE a <> b " +
           "RETURN DISTINCT m.id")
    List<String> findMutualFriendIds(@Param("userA") String userA, @Param("userB") String userB);

    @Query("MATCH (a:User {id: $userA})-[:FRIEND]-(m:User)-[:FRIEND]-(b:User {id: $userB}) " +
           "WHERE a <> b " +
           "RETURN count(DISTINCT m)")
    int countMutualFriends(@Param("userA") String userA, @Param("userB") String userB);

    // ===== FRIEND SUGGESTIONS (friends-of-friends) =====

    @Query("MATCH (u:User {id: $userId})-[:FRIEND]-(f:User)-[:FRIEND]-(suggest:User) " +
           "WHERE NOT (u)-[:FRIEND]-(suggest) AND u <> suggest " +
           "RETURN {userId: suggest.id, mutualCount: count(f)} AS row " +
           "ORDER BY mutualCount DESC " +
           "SKIP $skip LIMIT $limit")
    List<Map<String, Object>> findFriendSuggestions(@Param("userId") String userId,
                                                     @Param("skip") long skip,
                                                     @Param("limit") int limit);

    @Query("MATCH (u:User {id: $userId})-[:FRIEND]-(f:User)-[:FRIEND]-(suggest:User) " +
           "WHERE NOT (u)-[:FRIEND]-(suggest) AND u <> suggest " +
           "RETURN count(DISTINCT suggest)")
    long countFriendSuggestions(@Param("userId") String userId);

    // ===== CONTACT-BASED SUGGESTIONS =====

    @Query("MATCH (u:User {id: $userId})-[c:IN_CONTACT]->(suggest:User) " +
           "WHERE NOT (u)-[:FRIEND]-(suggest) " +
           "RETURN {userId: suggest.id, score: c.score} AS row " +
           "ORDER BY c.score DESC " +
           "SKIP $skip LIMIT $limit")
    List<Map<String, Object>> findContactSuggestions(@Param("userId") String userId,
                                                      @Param("skip") long skip,
                                                      @Param("limit") int limit);

    @Query("MATCH (u:User {id: $userId})-[c:IN_CONTACT]->(suggest:User) " +
           "WHERE NOT (u)-[:FRIEND]-(suggest) " +
           "RETURN count(suggest)")
    long countContactSuggestions(@Param("userId") String userId);

    // ===== IN_CONTACT RELATIONSHIP =====

    @Query("MERGE (a:User {id: $fromUserId}) " +
           "MERGE (b:User {id: $toUserId}) " +
           "MERGE (a)-[c:IN_CONTACT]->(b) " +
           "ON CREATE SET c.score = $score, c.source = $source, c.createdAt = timestamp() " +
           "ON MATCH SET c.score = c.score + $score")
    void mergeInContactRelationship(@Param("fromUserId") String fromUserId,
                                     @Param("toUserId") String toUserId,
                                     @Param("score") double score,
                                     @Param("source") String source);

    // ===== IN_GROUP RELATIONSHIP =====

    @Query("MERGE (u:User {id: $userId}) " +
           "MERGE (g:Group {id: $groupId}) " +
           "MERGE (u)-[:IN_GROUP]->(g)")
    void mergeInGroupRelationship(@Param("userId") String userId,
                                   @Param("groupId") String groupId);

    @Query("MATCH (u:User {id: $userId})-[r:IN_GROUP]->(g:Group {id: $groupId}) DELETE r")
    void removeInGroupRelationship(@Param("userId") String userId,
                                    @Param("groupId") String groupId);

    // ===== UNIFIED SUGGESTIONS =====

    @Query("MATCH (u:User {id: $userId}) " +
           "MATCH (suggest:User) WHERE suggest <> u AND NOT (u)-[:FRIEND]-(suggest) " +
           "OPTIONAL MATCH (u)-[:FRIEND]-(mutual:User)-[:FRIEND]-(suggest) " +
           "WITH u, suggest, count(DISTINCT mutual) AS mutualFriendsCount " +
           "OPTIONAL MATCH (u)-[:IN_GROUP]->(g:Group)<-[:IN_GROUP]-(suggest) " +
           "WITH u, suggest, mutualFriendsCount, count(DISTINCT g) AS sharedGroupsCount " +
           "OPTIONAL MATCH (u)-[c:IN_CONTACT]->(suggest) " +
           "WITH suggest, mutualFriendsCount, sharedGroupsCount, coalesce(c.score, 0.0) AS contactScore, " +
           "(mutualFriendsCount * 10 + sharedGroupsCount * 3 + coalesce(c.score, 0.0) * 5) AS totalScore " +
           "WHERE mutualFriendsCount > 0 OR sharedGroupsCount > 0 OR contactScore > 0 " +
           "RETURN {userId: suggest.id, mutualFriendsCount: mutualFriendsCount, sharedGroupsCount: sharedGroupsCount, contactScore: contactScore, totalScore: totalScore} AS row " +
           "ORDER BY totalScore DESC " +
           "SKIP $skip LIMIT $limit")
    List<Map<String, Object>> findUnifiedSuggestions(@Param("userId") String userId,
                                                      @Param("skip") long skip,
                                                      @Param("limit") int limit);

    @Query("MATCH (u:User {id: $userId}) " +
           "MATCH (suggest:User) WHERE suggest <> u AND NOT (u)-[:FRIEND]-(suggest) " +
           "OPTIONAL MATCH (u)-[:FRIEND]-(mutual:User)-[:FRIEND]-(suggest) " +
           "WITH u, suggest, count(DISTINCT mutual) AS mutualFriendsCount " +
           "OPTIONAL MATCH (u)-[:IN_GROUP]->(g:Group)<-[:IN_GROUP]-(suggest) " +
           "WITH u, suggest, mutualFriendsCount, count(DISTINCT g) AS sharedGroupsCount " +
           "OPTIONAL MATCH (u)-[c:IN_CONTACT]->(suggest) " +
           "WITH suggest, mutualFriendsCount, sharedGroupsCount, coalesce(c.score, 0.0) AS contactScore " +
           "WHERE mutualFriendsCount > 0 OR sharedGroupsCount > 0 OR contactScore > 0 " +
           "RETURN count(suggest)")
    long countUnifiedSuggestions(@Param("userId") String userId);
}
