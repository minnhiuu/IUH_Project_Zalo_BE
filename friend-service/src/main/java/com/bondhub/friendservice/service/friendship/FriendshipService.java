package com.bondhub.friendservice.service.friendship;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.friendservice.dto.request.FriendRequestSendRequest;
import com.bondhub.friendservice.dto.response.*;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface FriendshipService {
    
    FriendRequestResponse sendFriendRequest(FriendRequestSendRequest request); 

    FriendRequestResponse acceptFriendRequest(String friendshipId);

    void declineFriendRequest(String friendshipId);

    void cancelFriendRequest(String friendshipId);
    
    void unfriend(String friendId);
    
    PageResponse<List<FriendRequestResponse>> getReceivedFriendRequests(Pageable pageable);
   
    PageResponse<List<FriendRequestResponse>> getSentFriendRequests(Pageable pageable);
    
    PageResponse<List<FriendResponse>> getMyFriends(Pageable pageable);
    
    FriendshipStatusResponse checkFriendshipStatus(String userId);
    
    MutualFriendsResponse getMutualFriends(String userId);
    
    Integer getMutualFriendsCount(String userId);
}
