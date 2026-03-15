package com.bondhub.friendservice.mapper;

import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.friendservice.dto.response.FriendRequestResponse;
import com.bondhub.friendservice.dto.response.FriendResponse;
import com.bondhub.friendservice.model.FriendShip;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface FriendShipMapper {
    
    @Mapping(target = "id", source = "friendShip.id")
    @Mapping(target = "requestedUserId", source = "friendShip.requested")
    @Mapping(target = "requestedUserName", source = "requester.fullName")
    @Mapping(target = "requestedUserAvatar", source = "requester.avatar")
    @Mapping(target = "receivedUserId", source = "friendShip.received")
    @Mapping(target = "receivedUserName", source = "receiver.fullName")
    @Mapping(target = "receivedUserAvatar", source = "receiver.avatar")
    @Mapping(target = "message", source = "friendShip.content")
    @Mapping(target = "status", source = "friendShip.friendStatus")
    @Mapping(target = "createdAt", source = "friendShip.createdAt")
    @Mapping(target = "updatedAt", source = "friendShip.lastModifiedAt")
    FriendRequestResponse toFriendRequestResponse(
            FriendShip friendShip, 
            UserSummaryResponse requester, 
            UserSummaryResponse receiver
    );
    
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "userName", source = "user.fullName")
    @Mapping(target = "userAvatar", source = "user.avatar")
    @Mapping(target = "userEmail", ignore = true)
    @Mapping(target = "userPhone", ignore = true)
    @Mapping(target = "friendsSince", source = "friendShip.lastModifiedAt")
    @Mapping(target = "mutualFriendsCount", source = "mutualCount")
    FriendResponse toFriendResponse(
            UserSummaryResponse user, 
            FriendShip friendShip, 
            Integer mutualCount
    );
    
    @Mapping(target = "userId", source = "id")
    @Mapping(target = "userName", source = "fullName")
    @Mapping(target = "userAvatar", source = "avatar")
    @Mapping(target = "userEmail", ignore = true)
    @Mapping(target = "userPhone", ignore = true)
    @Mapping(target = "friendsSince", ignore = true)
    @Mapping(target = "mutualFriendsCount", ignore = true)
    FriendResponse toFriendResponseFromUser(UserSummaryResponse user);
}
