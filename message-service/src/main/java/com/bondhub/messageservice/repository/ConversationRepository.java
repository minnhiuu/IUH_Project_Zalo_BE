package com.bondhub.messageservice.repository;

import com.bondhub.messageservice.model.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConversationRepository extends MongoRepository<Conversation, String> {

    /**
     * Tìm cuộc trò chuyện 1-1 giữa 2 user bằng $all operator.
     * Không phụ thuộc vào thứ tự userA/userB.
     */
    @Query("{ 'isGroup': false, 'members': { '$size': 2 }, 'members.userId': { '$all': [?0, ?1] } }")
    Optional<Conversation> findDirectConversation(String userA, String userB);

    /**
     * Lấy tất cả các phòng chat mà user là thành viên,
     * sắp xếp theo lastMessage.timestamp DESC (xử lý bởi Pageable).
     */
    @Query("{ 'members.userId': ?0 }")
    Page<Conversation> findAllByMembersUserId(String userId, Pageable pageable);
}
