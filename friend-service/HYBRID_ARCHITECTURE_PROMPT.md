# Friend Service — Hybrid MongoDB + Neo4j Architecture Refactoring

## System Prompt

You are a senior backend architect specializing in Java Spring Boot microservices, MongoDB, Neo4j, and event-driven systems.

I have an existing microservice called `friend-service` built with Spring Boot 3.5.9, Java 21, Spring Cloud 2025.0.1. It currently uses MongoDB to manage friendships and is part of the **BondHub** microservices platform.

---

## Existing Codebase Context

### Current Architecture
- **Parent**: `com.bondhub:bond-hub-api:0.0.1-SNAPSHOT`
- **Service**: `friend-service` (port 8086)
- **Package**: `com.bondhub.friendservice`
- **Infrastructure**: Eureka discovery, Config Server, API Gateway (8080), Kafka (KRaft mode), MongoDB 7.0, Redis 7
- **Security**: JWT-based via `SecurityContextFilter` extracting `X-User-Id`, `X-Account-Id` headers from API Gateway
- **Current user context**: `SecurityUtil.getCurrentUserId()` returns the authenticated user's ID

### Current FriendShip Model (MongoDB — `friendships` collection)
```java
@Document("friendships")
public class FriendShip extends BaseModel {
    @Id String id;
    String requested;        // userId who sent request
    String received;         // userId who received request
    String content;          // optional message
    FriendStatus friendStatus; // PENDING, ACCEPTED, DECLINED, CANCELLED
    // inherited: createdAt, lastModifiedAt, createdBy, lastModifiedBy, active
}
```

### Current Repository (FriendShipRepository)
```java
extends MongoRepository<FriendShip, String>
- findByRequestedAndFriendStatusOrderByCreatedAtDesc(userId, status, pageable)
- findByReceivedAndFriendStatusOrderByCreatedAtDesc(userId, status, pageable)
- findFriendshipBetweenUsers(userId1, userId2)          // @Query: pending or accepted
- findAllFriendsByUserId(userId) / (userId, pageable)   // @Query: accepted friendships
- findAcceptedFriendship(userId1, userId2)
- findAllFriendshipsByUserId(userId)                     // any status
- findFriendshipsBetweenUserAndTargets(currentUserId, targetIds) // batch
```

### Current Service Interface (FriendshipService)
```java
ApiResponse<FriendRequestResponse> sendFriendRequest(FriendRequestSendRequest request);
ApiResponse<FriendRequestResponse> acceptFriendRequest(String friendshipId);
ApiResponse<FriendRequestResponse> declineFriendRequest(String friendshipId);
ApiResponse<FriendRequestResponse> cancelFriendRequest(String friendshipId);
ApiResponse<Void> unfriend(String friendId);
ApiResponse<PageResponse<List<FriendRequestResponse>>> getReceivedFriendRequests(Pageable pageable);
ApiResponse<PageResponse<List<FriendRequestResponse>>> getSentFriendRequests(Pageable pageable);
ApiResponse<PageResponse<List<FriendResponse>>> getMyFriends(Pageable pageable);
ApiResponse<FriendshipStatusResponse> checkFriendshipStatus(String userId);
ApiResponse<MutualFriendsResponse> getMutualFriends(String userId);
ApiResponse<Integer> getMutualFriendsCount(String userId);
ApiResponse<List<String>> getFriendIds(String userId);
ApiResponse<Map<String, FriendshipStatusResponse>> batchCheckFriendshipStatus(List<String> targetUserIds);
```

### Current DTOs
```java
// Requests
record FriendRequestSendRequest(String receiverId, String message) {}
record BlockUserRequest(String blockedUserId, Boolean blockMessage, Boolean blockCall, Boolean blockStory) {}

// Responses
record FriendRequestResponse(id, requestedUserId, requestedUserName, requestedUserAvatar,
    receivedUserId, receivedUserName, receivedUserAvatar, message, status, createdAt, updatedAt) {}
record FriendResponse(userId, userName, userAvatar, userEmail, userPhone,
    friendsSince: LocalDateTime, mutualFriendsCount: Integer) {}
record FriendshipStatusResponse(areFriends: Boolean, status: FriendStatus, friendshipId, requestedBy) {}
record MutualFriendsResponse(count: Integer, mutualFriends: List<FriendResponse>) {}
```

### Current Kafka Integration
- **Outbox Pattern**: Uses `OutboxEventPublisher.saveAndPublish()` from common module
- **Event**: `FriendshipChangedEvent(userA, userB, friendshipId, action: FriendshipAction, timestamp)`
- **Actions**: `ADDED, REMOVED, REQUESTED, DECLINED, CANCELLED`
- **Topic**: `friend.friendship.changed`
- **Notification**: Uses `RawNotificationEventPublisher` for FRIEND_REQUEST/FRIEND_ACCEPT notifications
- **Consumer**: Existing `UserDeletedListener` listens to `user.deleted` topic

### Current Feign Client
```java
@FeignClient(name = "user-service", path = "/users")
public interface UserServiceClient {
    @GetMapping("/summary/{id}")
    ApiResponse<UserSummaryResponse> getUserSummary(@PathVariable String id);
    
    @PostMapping("/summary/batch")
    ApiResponse<Map<String, UserSummaryResponse>> getUsersByIds(@RequestBody List<String> ids);
    
    @GetMapping("/account/{accountId}")
    ApiResponse<UserSummaryResponse> getUserByAccountId(@PathVariable String accountId);
}
// UserSummaryResponse(id, fullName, avatar, phoneNumber)
```

### Current Controller Endpoints
```
POST   /friendships/requests                    — Send friend request
PUT    /friendships/requests/{id}/accept        — Accept
PUT    /friendships/requests/{id}/decline       — Decline
PUT    /friendships/requests/{id}/cancel        — Cancel
DELETE /friendships/friends/{friendId}           — Unfriend
GET    /friendships/requests/received            — Received requests (paginated)
GET    /friendships/requests/sent                — Sent requests (paginated)
GET    /friendships/friends                      — My friends (paginated)
GET    /friendships/status/{userId}              — Check friendship status
GET    /friendships/mutual/{userId}              — Mutual friends
GET    /friendships/mutual/{userId}/count        — Mutual friends count
POST   /friendships/batch-status                 — Batch check status

POST   /blocks                                   — Block user
DELETE /blocks/{blockedUserId}                    — Unblock
PATCH  /blocks/{blockedUserId}/preferences        — Update block preferences
GET    /blocks                                    — My blocked users
GET    /blocks/details                            — Blocked users with profiles
GET    /blocks/{userId}/check                     — Check if blocked
GET    /blocks/{blockedUserId}                    — Block details

GET    /internal/friends/user/{userId}/friend-ids — Internal: get friend IDs
```

### Common Module Shared Classes
```java
// ApiResponse<T> — standard wrapper: success(data), successWithoutData(), error(code, msg, errors)
// PageResponse<T> — pagination wrapper: fromPage(page, mapper)
// BaseModel — createdAt, lastModifiedAt, createdBy, lastModifiedBy, active
// ErrorCode — enum with ranges: 1xxx=auth, 2xxx=user, 3xxx=friendship, 23xx=block, 9xxx=system
// AppException(ErrorCode)
// SecurityUtil — getCurrentUserId(), getCurrentAccountId(), etc.
// OutboxEventPublisher — saveAndPublish(aggregateId, aggregateType, eventType, payload)
// RawNotificationEventPublisher — publish(RawNotificationEvent)
// FriendshipChangedEvent, FriendshipAction, UserDeletedEvent
```

### Current Docker Infrastructure (docker-compose.yml)
- MongoDB 7.0 (port 27018:27017)
- Redis 7-alpine (port 6379)
- Kafka KRaft mode (confluent 7.6.0, port 9092)
- Networks: `bondhub-network`
- Volumes: `mongodb_data`, `mongodb_config`, `redis_data`, `kafka_data`

### Current Config (friend-service.yml via Config Server)
```yaml
server.port: 8086
spring.data.mongodb.uri: ${MONGODB_URI_FRIENDSHIPS}
spring.kafka.producer.client-id: friend-service-producer
spring.kafka.consumer.group-id: friend-service-group
spring.kafka.listener.ack-mode: manual
bondhub.security.public-endpoints: [/friendships]
```

### Build System
```xml
<!-- Current dependencies: spring-boot-starter-data-mongodb, spring-kafka,
     spring-cloud-starter-openfeign, spring-boot-starter-security,
     spring-boot-starter-aop, springdoc-openapi, lombok, mapstruct, common module -->
<!-- Compiler: lombok + mapstruct annotation processors -->
```

---

# 🎯 GOAL

Refactor the system into a **HYBRID architecture**:

## MongoDB (source of truth)
- Keep FriendShip collection as-is
- Handle: friend request lifecycle (PENDING → ACCEPTED → DECLINED → CANCELLED), validation & business rules
- APIs that stay on Mongo: `sendFriendRequest`, `acceptFriendRequest`, `declineFriendRequest`, `cancelFriendRequest`, `checkFriendshipStatus`, `getReceivedFriendRequests`, `getSentFriendRequests`, `batchCheckFriendshipStatus`

## Neo4j (graph engine)
- Store ONLY:
  - `(:User {id})` — synced from events, NOT duplicating user-service data
  - `[:FRIEND]` relationships — only when ACCEPTED
  - `[:IN_CONTACT]` relationships — for phone/contact suggestion
- Use Neo4j for:
  - `getMyFriends` → replaces current Mongo query
  - `getMutualFriends` / `getMutualFriendsCount` → replaces current Mongo logic
  - Friend suggestions (friends-of-friends)
  - Contact-based suggestions
  - `getFriendIds` → replaces current Mongo query

---

# 📱 CONTACT DATA (FROM EXPO-CONTACTS)

The mobile app uses expo-contacts and sends:

```json
{
  "contacts": [
    {
      "name": "Nguyen Van A",
      "phones": ["+84901234567", "0901234567"],
      "emails": ["a@gmail.com"]
    }
  ]
}
```

## Backend requirements:

### 1. Normalize contact data
- Normalize phone numbers to E.164 format (e.g., `+84` prefix, strip leading `0`)
- Remove duplicates, ignore invalid numbers
- Default country code: `+84` (Vietnam)

### 2. Match with existing users
- Call `user-service` to find users by phone/email (may need new Feign endpoint)
- Return matched users

### 3. Store in Neo4j
```
(:User {id: $currentUserId})-[:IN_CONTACT {score, source, createdAt}]->(:User {id: $matchedUserId})
```

### 4. Scoring logic
- Same phone → score = 1.0
- Same email → score = 0.8
- Multiple matches → sum scores

### 5. Deduplicate with MERGE in Cypher

---

# 📦 IMPLEMENTATION REQUIREMENTS

## 1. Add Neo4j Integration

### pom.xml — Add dependency:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-neo4j</artifactId>
</dependency>
```

### friend-service.yml — Add Neo4j config:
```yaml
spring:
  neo4j:
    uri: ${NEO4J_URI:bolt://localhost:7687}
    authentication:
      username: ${NEO4J_USERNAME:neo4j}
      password: ${NEO4J_PASSWORD:password}
```

### New package structure:
```
com.bondhub.friendservice/
├── client/
│   └── UserServiceClient.java          (existing — may need new endpoints)
├── config/
│   ├── SecurityConfig.java             (existing)
│   ├── OpenApiConfig.java              (existing)
│   ├── KafkaConsumerConfig.java        (existing)
│   └── Neo4jConfig.java               ⭐ NEW
├── controller/
│   ├── FriendshipController.java       (existing — modify some endpoints)
│   ├── BlockListController.java        (existing)
│   ├── FriendInternalController.java   (existing — modify getFriendIds)
│   └── ContactController.java         ⭐ NEW
├── dto/
│   ├── ... (existing DTOs)
│   ├── contact/
│   │   ├── request/
│   │   │   └── ContactImportRequest.java    ⭐ NEW
│   │   └── response/
│   │       └── ContactImportResponse.java   ⭐ NEW
│   └── suggestion/
│       └── response/
│           └── FriendSuggestionResponse.java ⭐ NEW
├── graph/                              ⭐ NEW PACKAGE
│   ├── node/
│   │   └── UserNode.java
│   ├── relationship/
│   │   ├── FriendRelationship.java
│   │   └── InContactRelationship.java
│   ├── repository/
│   │   └── UserNodeRepository.java
│   └── service/
│       ├── GraphFriendService.java      (interface)
│       └── GraphFriendServiceImpl.java  (implementation)
├── listener/
│   ├── UserDeletedListener.java        (existing)
│   └── FriendshipChangedListener.java  ⭐ NEW — Kafka consumer for graph sync
├── mapper/
│   ├── FriendShipMapper.java           (existing)
│   └── BlockListMapper.java            (existing)
├── model/
│   ├── FriendShip.java                 (existing)
│   ├── BlockList.java                  (existing)
│   ├── BlockPreference.java            (existing)
│   ├── FriendStatus.java               (existing)
│   └── BlockType.java                  (existing)
├── repository/
│   ├── FriendShipRepository.java       (existing)
│   └── BlockListRepository.java        (existing)
├── service/
│   ├── friendship/
│   │   ├── FriendshipService.java      (existing interface)
│   │   └── FriendshipServiceImpl.java  (existing — modify graph-delegated methods)
│   ├── block/
│   │   ├── BlockListService.java       (existing)
│   │   ├── BlockListServiceImpl.java   (existing)
│   │   ├── BlockCheckService.java      (existing)
│   │   └── BlockCheckServiceImpl.java  (existing)
│   └── contact/                        ⭐ NEW
│       ├── ContactService.java
│       └── ContactServiceImpl.java
└── FriendServiceApplication.java       (existing)
```

---

## 2. Neo4j Entities

### UserNode
```java
@Node("User")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UserNode {
    @Id
    private String id;  // Same as userId from user-service

    @Relationship(type = "FRIEND", direction = Relationship.Direction.UNDIRECTED)
    private Set<UserNode> friends;
}
```

### FriendRelationship — modeled via Cypher (no separate entity needed since FRIEND is a simple undirected edge with no properties)

### InContactRelationship
```java
@RelationshipProperties
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class InContactRelationship {
    @RelationshipId
    private Long id;

    @TargetNode
    private UserNode targetUser;

    private Double score;
    private String source;  // "PHONE" or "EMAIL"
    private Long createdAt;
}
```

---

## 3. Graph Repository (Spring Data Neo4j + Custom Cypher)

```java
public interface UserNodeRepository extends Neo4jRepository<UserNode, String> {

    // Get friends list
    @Query("MATCH (u:User {id: $userId})-[:FRIEND]-(f:User) RETURN f.id")
    List<String> findFriendIds(@Param("userId") String userId);

    // Get friends list (paginated)
    @Query(value = "MATCH (u:User {id: $userId})-[:FRIEND]-(f:User) RETURN f.id SKIP $skip LIMIT $limit",
           countQuery = "MATCH (u:User {id: $userId})-[:FRIEND]-(f:User) RETURN count(f)")
    Page<String> findFriendIdsPaginated(@Param("userId") String userId, Pageable pageable);

    // Mutual friends
    @Query("MATCH (a:User {id: $userA})-[:FRIEND]-(m:User)-[:FRIEND]-(b:User {id: $userB}) " +
           "WHERE a <> b RETURN m.id")
    List<String> findMutualFriendIds(@Param("userA") String userA, @Param("userB") String userB);

    // Mutual friends count
    @Query("MATCH (a:User {id: $userA})-[:FRIEND]-(m:User)-[:FRIEND]-(b:User {id: $userB}) " +
           "WHERE a <> b RETURN count(m)")
    int countMutualFriends(@Param("userA") String userA, @Param("userB") String userB);

    // Friend suggestions (friends-of-friends)
    @Query("MATCH (u:User {id: $userId})-[:FRIEND]-(f:User)-[:FRIEND]-(suggest:User) " +
           "WHERE NOT (u)-[:FRIEND]-(suggest) AND u <> suggest " +
           "RETURN suggest.id AS userId, count(f) AS mutualCount " +
           "ORDER BY mutualCount DESC LIMIT $limit")
    List<Map<String, Object>> findFriendSuggestions(@Param("userId") String userId, @Param("limit") int limit);

    // Contact-based suggestions
    @Query("MATCH (u:User {id: $userId})-[c:IN_CONTACT]->(suggest:User) " +
           "WHERE NOT (u)-[:FRIEND]-(suggest) " +
           "RETURN suggest.id AS userId, c.score AS score " +
           "ORDER BY c.score DESC LIMIT $limit")
    List<Map<String, Object>> findContactSuggestions(@Param("userId") String userId, @Param("limit") int limit);

    // MERGE user node (idempotent)
    @Query("MERGE (u:User {id: $userId}) RETURN u")
    UserNode mergeUser(@Param("userId") String userId);

    // Create FRIEND relationship (idempotent)
    @Query("MERGE (a:User {id: $userA}) MERGE (b:User {id: $userB}) MERGE (a)-[:FRIEND]-(b)")
    void createFriendRelationship(@Param("userA") String userA, @Param("userB") String userB);

    // Remove FRIEND relationship
    @Query("MATCH (a:User {id: $userA})-[r:FRIEND]-(b:User {id: $userB}) DELETE r")
    void removeFriendRelationship(@Param("userA") String userA, @Param("userB") String userB);

    // Create/update IN_CONTACT relationship (idempotent with MERGE)
    @Query("MERGE (a:User {id: $fromUserId}) " +
           "MERGE (b:User {id: $toUserId}) " +
           "MERGE (a)-[c:IN_CONTACT]->(b) " +
           "ON CREATE SET c.score = $score, c.source = $source, c.createdAt = timestamp() " +
           "ON MATCH SET c.score = c.score + $score")
    void mergeInContactRelationship(@Param("fromUserId") String fromUserId,
                                     @Param("toUserId") String toUserId,
                                     @Param("score") double score,
                                     @Param("source") String source);

    // Delete all relationships for a user (cleanup on user delete)
    @Query("MATCH (u:User {id: $userId})-[r]-() DELETE r")
    void deleteAllRelationships(@Param("userId") String userId);

    // Delete user node
    @Query("MATCH (u:User {id: $userId}) DETACH DELETE u")
    void deleteUserNode(@Param("userId") String userId);
}
```

---

## 4. Kafka Consumer for Graph Sync

### FriendshipChangedListener
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class FriendshipChangedListener {

    private final UserNodeRepository userNodeRepository;

    @KafkaListener(
        topics = "${kafka.topics.friendship-changed:friend.friendship.changed}",
        groupId = "friend-service-graph-sync",
        containerFactory = "friendshipChangedListenerFactory"  // or use type mapping
    )
    public void handleFriendshipChanged(FriendshipChangedEvent event, Acknowledgment ack) {
        try {
            log.info("Received FriendshipChangedEvent: action={}, userA={}, userB={}",
                event.action(), event.userA(), event.userB());

            switch (event.action()) {
                case ADDED -> {
                    userNodeRepository.createFriendRelationship(event.userA(), event.userB());
                    log.info("Created FRIEND relationship: {} <-> {}", event.userA(), event.userB());
                }
                case REMOVED -> {
                    userNodeRepository.removeFriendRelationship(event.userA(), event.userB());
                    log.info("Removed FRIEND relationship: {} <-> {}", event.userA(), event.userB());
                }
                default -> log.debug("Ignoring action: {}", event.action());
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process FriendshipChangedEvent: {}", event, e);
            // Don't ack — will be retried
        }
    }
}
```

### Update UserDeletedListener to also clean up Neo4j:
```java
// Add to existing UserDeletedListener:
userNodeRepository.deleteUserNode(event.getUserId());
log.info("Deleted Neo4j user node and relationships for userId: {}", event.getUserId());
```

---

## 5. Service Modifications

### GraphFriendService (interface)
```java
public interface GraphFriendService {
    List<String> getFriendIds(String userId);
    Page<String> getFriendIdsPaginated(String userId, Pageable pageable);
    List<String> getMutualFriendIds(String userA, String userB);
    int getMutualFriendsCount(String userA, String userB);
    List<FriendSuggestionResponse> getFriendSuggestionsFromGraph(String userId, int limit);
    List<FriendSuggestionResponse> getContactSuggestions(String userId, int limit);
    void ensureUserNode(String userId);
    void createFriendRelationship(String userA, String userB);
    void removeFriendRelationship(String userA, String userB);
    void mergeInContact(String fromUserId, String toUserId, double score, String source);
}
```

### Modify FriendshipServiceImpl — delegate graph queries:
```java
// BEFORE (Mongo):
// List<FriendShip> friendships = friendShipRepository.findAllFriendsByUserId(currentUserId, pageable);

// AFTER (Neo4j):
// Page<String> friendIds = graphFriendService.getFriendIdsPaginated(currentUserId, pageable);
// Map<String, UserSummaryResponse> users = userServiceClient.getUsersByIds(friendIds.getContent());
// ... map to FriendResponse with mutual counts from Neo4j
```

Same for `getMutualFriends`, `getMutualFriendsCount`, `getFriendIds`.

---

## 6. Contact Import Service

### ContactController
```java
@RestController
@RequestMapping("/contacts")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ContactController {

    ContactService contactService;

    @PostMapping("/import")
    public ResponseEntity<ApiResponse<ContactImportResponse>> importContacts(
            @Valid @RequestBody ContactImportRequest request) {
        return ResponseEntity.ok(contactService.importContacts(request));
    }
}
```

### New endpoints on FriendshipController:
```java
@GetMapping("/suggestions/contacts")
public ResponseEntity<ApiResponse<List<FriendSuggestionResponse>>> getContactSuggestions(
        @RequestParam(defaultValue = "20") int limit) {
    return ResponseEntity.ok(friendshipService.getContactSuggestions(limit));
}

@GetMapping("/suggestions/graph")
public ResponseEntity<ApiResponse<List<FriendSuggestionResponse>>> getGraphSuggestions(
        @RequestParam(defaultValue = "20") int limit) {
    return ResponseEntity.ok(friendshipService.getGraphSuggestions(limit));
}
```

### ContactImportRequest
```java
public record ContactImportRequest(
    @NotNull @Size(min = 1, max = 500)
    List<ContactEntry> contacts
) {
    public record ContactEntry(
        String name,
        List<String> phones,
        List<String> emails
    ) {}
}
```

### ContactImportResponse
```java
public record ContactImportResponse(
    int totalContacts,
    int matchedUsers,
    int contactRelationsCreated,
    List<String> matchedUserIds
) {}
```

### FriendSuggestionResponse
```java
public record FriendSuggestionResponse(
    String userId,
    String fullName,
    String avatar,
    String phoneNumber,
    Integer mutualFriendsCount,  // for graph suggestions
    Double contactScore          // for contact suggestions
) {}
```

### ContactServiceImpl — Key Logic:
```java
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ContactServiceImpl implements ContactService {

    UserServiceClient userServiceClient;
    GraphFriendService graphFriendService;
    SecurityUtil securityUtil;

    @Override
    public ApiResponse<ContactImportResponse> importContacts(ContactImportRequest request) {
        String currentUserId = securityUtil.getCurrentUserId();
        log.info("Importing {} contacts for user: {}", request.contacts().size(), currentUserId);

        // 1. Normalize phone numbers
        Set<String> normalizedPhones = new HashSet<>();
        Set<String> emails = new HashSet<>();
        for (var contact : request.contacts()) {
            if (contact.phones() != null) {
                contact.phones().stream()
                    .map(this::normalizePhoneNumber)
                    .filter(Objects::nonNull)
                    .forEach(normalizedPhones::add);
            }
            if (contact.emails() != null) {
                emails.addAll(contact.emails().stream()
                    .filter(e -> e != null && !e.isBlank())
                    .map(String::toLowerCase)
                    .toList());
            }
        }

        // 2. Match with user-service (needs new Feign endpoints)
        // ApiResponse<List<UserMatchResponse>> phoneMatches = userServiceClient.findUsersByPhones(normalizedPhones);
        // ApiResponse<List<UserMatchResponse>> emailMatches = userServiceClient.findUsersByEmails(emails);

        // 3. Ensure current user node exists in Neo4j
        graphFriendService.ensureUserNode(currentUserId);

        // 4. Create IN_CONTACT relationships with scoring
        int relationsCreated = 0;
        // For phone matches: score = 1.0, source = "PHONE"
        // For email matches: score = 0.8, source = "EMAIL"
        // graphFriendService.mergeInContact(currentUserId, matchedUserId, score, source);

        return ApiResponse.success(new ContactImportResponse(...));
    }

    private String normalizePhoneNumber(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String digits = phone.replaceAll("[^0-9+]", "");
        if (digits.startsWith("+84")) return digits;
        if (digits.startsWith("84") && digits.length() >= 11) return "+" + digits;
        if (digits.startsWith("0") && digits.length() >= 10) return "+84" + digits.substring(1);
        return null; // invalid
    }
}
```

### New Feign Endpoints Needed on user-service:
```java
// Add to UserServiceClient:
@PostMapping("/search/by-phones")
ApiResponse<List<UserSummaryResponse>> findUsersByPhones(@RequestBody Set<String> phones);

@PostMapping("/search/by-emails")
ApiResponse<List<UserSummaryResponse>> findUsersByEmails(@RequestBody Set<String> emails);
```

---

## 7. Docker Configuration

### Add to existing docker-compose.yml:
```yaml
  # Neo4j Graph Database
  neo4j:
    image: neo4j:5.18-community
    container_name: bondhub-neo4j
    restart: unless-stopped
    environment:
      NEO4J_AUTH: ${NEO4J_USERNAME:-neo4j}/${NEO4J_PASSWORD:-bondhub123}
      NEO4J_PLUGINS: '["apoc"]'
      NEO4J_dbms_memory_heap_initial__size: 256m
      NEO4J_dbms_memory_heap_max__size: 512m
    ports:
      - "${NEO4J_HTTP_PORT:-7474}:7474"
      - "${NEO4J_BOLT_PORT:-7687}:7687"
    volumes:
      - neo4j_data:/data
      - neo4j_logs:/logs
    networks:
      - bondhub-network
    healthcheck:
      test: ["CMD", "cypher-shell", "-u", "neo4j", "-p", "${NEO4J_PASSWORD:-bondhub123}", "RETURN 1"]
      interval: 10s
      timeout: 5s
      retries: 5

# Add to volumes:
volumes:
  neo4j_data:
  neo4j_logs:
```

### Add to .env:
```env
NEO4J_URI=bolt://localhost:7687
NEO4J_USERNAME=neo4j
NEO4J_PASSWORD=bondhub123
NEO4J_HTTP_PORT=7474
NEO4J_BOLT_PORT=7687
```

---

## 8. Dockerfile for friend-service
```dockerfile
# Stage 1: Build
FROM maven:3.9.8-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY common/pom.xml common/
COPY friend-service/pom.xml friend-service/
RUN mvn dependency:go-offline -pl friend-service -am -B
COPY common/src common/src
COPY friend-service/src friend-service/src
RUN mvn clean package -pl friend-service -am -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S bondhub && adduser -S bondhub -G bondhub
COPY --from=build /app/friend-service/target/*.jar app.jar
USER bondhub
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## 9. Updated friend-service.yml Configuration
```yaml
# Add Neo4j config alongside existing MongoDB:
spring:
  neo4j:
    uri: ${NEO4J_URI:bolt://localhost:7687}
    authentication:
      username: ${NEO4J_USERNAME:neo4j}
      password: ${NEO4J_PASSWORD:bondhub123}

# Add Kafka consumer config for graph sync:
kafka:
  topics:
    friendship-changed: friend.friendship.changed
    user-deleted: user.deleted
```

---

## 10. Architecture Diagram (Updated)
```
┌──────────────────────────────────────────────────────────┐
│                      API Gateway (8080)                   │
└────────────┬─────────────────┬───────────────────────────┘
             │                 │
    ┌────────▼──────┐  ┌──────▼────────┐
    │ friend-service│  │ user-service   │
    │   (8086)      │  │   (8082)       │
    └──┬─────┬──────┘  └───────────────┘
       │     │              ▲
       │     │    OpenFeign │
       │     └──────────────┘
       │
  ┌────┴──────────────────────────────────┐
  │           friend-service              │
  │                                        │
  │  ┌─────────────┐  ┌─────────────────┐ │
  │  │  MongoDB     │  │    Neo4j        │ │
  │  │ (friendships)│  │ (graph engine)  │ │
  │  │              │  │                 │ │
  │  │ • FriendShip │  │ • (:User)       │ │
  │  │ • BlockList  │  │ • [:FRIEND]     │ │
  │  │ • OutboxEvent│  │ • [:IN_CONTACT] │ │
  │  └──────────────┘  └─────────────────┘ │
  │                                        │
  │  ┌──────────────────────────────────┐  │
  │  │         Kafka                     │  │
  │  │  • friend.friendship.changed     │  │
  │  │    → Graph sync (ADDED/REMOVED)  │  │
  │  │  • user.deleted                  │  │
  │  │    → Cleanup Mongo + Neo4j       │  │
  │  └──────────────────────────────────┘  │
  └────────────────────────────────────────┘
```

### Data Flow:
```
1. Send Friend Request → MongoDB (PENDING)
2. Accept Friend Request → MongoDB (ACCEPTED) → Kafka → Neo4j [:FRIEND]
3. Unfriend → MongoDB (delete) → Kafka → Neo4j (delete [:FRIEND])
4. Get Friends List → Neo4j → user-service (enrich)
5. Mutual Friends → Neo4j Cypher
6. Import Contacts → Normalize → user-service match → Neo4j [:IN_CONTACT]
7. Friend Suggestions → Neo4j (friends-of-friends / contacts)
```

---

## 11. Error Codes to Add (in common ErrorCode enum)

```java
// Contact errors (34xx)
CONTACT_IMPORT_FAILED(3401, "contact.import.failed", HttpStatus.INTERNAL_SERVER_ERROR),
CONTACT_NO_VALID_PHONES(3402, "contact.no.valid.phones", HttpStatus.BAD_REQUEST),
CONTACT_TOO_MANY(3403, "contact.too.many", HttpStatus.BAD_REQUEST),

// Graph errors (35xx)
GRAPH_SYNC_FAILED(3501, "graph.sync.failed", HttpStatus.INTERNAL_SERVER_ERROR),
GRAPH_QUERY_FAILED(3502, "graph.query.failed", HttpStatus.INTERNAL_SERVER_ERROR),
```

---

## 12. Best Practices Checklist

- [x] Idempotent MERGE for all Neo4j writes
- [x] No duplicate edges (MERGE prevents this)
- [x] User data NOT duplicated (only `id` stored in Neo4j)
- [x] Missing users handled gracefully (MERGE creates node if absent)
- [x] MongoDB remains source of truth for friendship lifecycle
- [x] Neo4j is eventually consistent via Kafka events
- [x] Manual Kafka acknowledgment for reliability
- [x] Phone number normalization to E.164
- [x] Existing block-check logic unchanged
- [x] Constructor injection with `@RequiredArgsConstructor` throughout
- [x] All responses wrapped in `ApiResponse<T>`
- [x] Java Records for new DTOs
