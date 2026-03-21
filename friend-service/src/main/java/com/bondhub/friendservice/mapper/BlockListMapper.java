package com.bondhub.friendservice.mapper;

import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.friendservice.dto.request.BlockUserRequest;
import com.bondhub.friendservice.dto.response.BlockPreferenceResponse;
import com.bondhub.friendservice.dto.response.BlockedUserDetailResponse;
import com.bondhub.friendservice.dto.response.BlockedUserResponse;
import com.bondhub.friendservice.model.BlockList;
import com.bondhub.friendservice.model.BlockPreference;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for converting between {@link BlockList} entities and their DTOs.
 */
@Mapper(componentModel = "spring")
public interface BlockListMapper {

    /**
     * Convert a {@link BlockList} entity to a {@link BlockedUserResponse}.
     *
     * @param blockList the block entity
     * @return the response DTO
     */
    BlockedUserResponse toBlockedUserResponse(BlockList blockList);

    /**
     * Convert a {@link BlockPreference} embedded document to a {@link BlockPreferenceResponse}.
     *
     * @param preference the block preference
     * @return the response DTO
     */
    BlockPreferenceResponse toBlockPreferenceResponse(BlockPreference preference);

    /**
     * Convert a {@link BlockList} entity and a {@link UserSummaryResponse} to a
     * {@link BlockedUserDetailResponse} enriched with user profile fields.
     *
     * @param blockList the block entity
     * @param user      the blocked user's profile summary
     * @return the detailed response DTO
     */
    @Mapping(target = "id", source = "blockList.id")
    @Mapping(target = "blockedUserId", source = "user.id")
    @Mapping(target = "fullName", source = "user.fullName")
    @Mapping(target = "avatar", source = "user.avatar")
    @Mapping(target = "bio", ignore = true)
    @Mapping(target = "gender", ignore = true)
    @Mapping(target = "dob", ignore = true)
    @Mapping(target = "preference", source = "blockList.preference")
    @Mapping(target = "blockedAt", source = "blockList.createdAt")
    BlockedUserDetailResponse toBlockedUserDetailResponse(BlockList blockList, UserSummaryResponse user);

    /**
     * Convert a {@link BlockUserRequest} and the blocker's user ID to a {@link BlockList} entity.
     *
     * @param request   the block request
     * @param blockerId the ID of the user performing the block
     * @return the block entity ready to be persisted
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "blockerId", source = "blockerId")
    @Mapping(target = "blockedUserId", source = "request.blockedUserId")
    @Mapping(target = "preference", expression = "java(createBlockPreference(request))")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "lastModifiedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "active", ignore = true)
    BlockList toBlockList(BlockUserRequest request, String blockerId);

    /**
     * Build a {@link BlockPreference} from the optional preference flags in the request.
     * Defaults to {@code true} (blocked) when a flag is not provided.
     *
     * @param request the block request
     * @return the constructed {@link BlockPreference}
     */
    default BlockPreference createBlockPreference(BlockUserRequest request) {
        return BlockPreference.builder()
            .message(request.blockMessage() != null ? request.blockMessage() : true)
            .call(request.blockCall() != null ? request.blockCall() : true)
            .story(request.blockStory() != null ? request.blockStory() : true)
            .build();
    }
}
