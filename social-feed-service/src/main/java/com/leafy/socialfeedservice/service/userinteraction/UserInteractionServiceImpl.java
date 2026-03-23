package com.leafy.socialfeedservice.service.userinteraction;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.utils.SecurityUtil;
import com.leafy.socialfeedservice.dto.response.interaction.UserInteractionResponse;
import com.leafy.socialfeedservice.model.UserInteraction;
import com.leafy.socialfeedservice.repository.UserInteractionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserInteractionServiceImpl implements UserInteractionService {

    UserInteractionRepository userInteractionRepository;
    SecurityUtil securityUtil;

    @Override
    public PageResponse<List<UserInteractionResponse>> getMyInteractions(int page, int size) {
        String currentUserId = securityUtil.getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size);
        Page<UserInteraction> interactions =
                userInteractionRepository.findByUserIdOrderByCreatedAtDesc(currentUserId, pageable);
        return PageResponse.fromPage(interactions, this::toResponse);
    }

    @Override
    public PageResponse<List<UserInteractionResponse>> getInteractionsByPost(String postId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<UserInteraction> interactions =
                userInteractionRepository.findByPostIdOrderByCreatedAtDesc(postId, pageable);
        return PageResponse.fromPage(interactions, this::toResponse);
    }

    @Override
    public List<UserInteractionResponse> getNewestInteractionsByUser(String userId, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return userInteractionRepository.findTopByUserIdOrderByCreatedAtDesc(userId, pageable)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private UserInteractionResponse toResponse(UserInteraction interaction) {
        return UserInteractionResponse.builder()
                .id(interaction.getId())
                .userId(interaction.getUserId())
                .postId(interaction.getPostId())
                .groupId(interaction.getGroupId())
                .interactionType(interaction.getInteractionType())
                .weight(interaction.getWeight())
                .createdAt(interaction.getCreatedAt())
                .ingestedAt(interaction.getIngestedAt())
                .build();
    }
}
