package com.bondhub.socialfeedservice.service.userinteraction;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.event.socialfeed.InteractionType;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.socialfeedservice.dto.response.interaction.UserInteractionResponse;
import com.bondhub.socialfeedservice.model.UserInteraction;
import com.bondhub.socialfeedservice.publisher.PostViewEventPublisher;
import com.bondhub.socialfeedservice.repository.UserInteractionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserInteractionServiceImpl implements UserInteractionService {

    UserInteractionRepository userInteractionRepository;
    PostViewEventPublisher postViewEventPublisher;
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

    @Override
    public void recordView(String postId) {
        String currentUserId = securityUtil.getCurrentUserId();

        boolean alreadyViewed = userInteractionRepository.existsByUserIdAndPostIdAndInteractionType(
                currentUserId, postId, InteractionType.VIEW);

        if (alreadyViewed) {
            log.debug("View already recorded, skipping: userId={}, postId={}", currentUserId, postId);
            return;
        }

        UserInteraction interaction = UserInteraction.builder()
                .userId(currentUserId)
                .postId(postId)
                .interactionType(InteractionType.VIEW)
                .weight(InteractionType.VIEW.getWeight())
                .createdAt(Instant.now())
                .build();

        userInteractionRepository.save(interaction);
        log.info("Recorded VIEW interaction: userId={}, postId={}", currentUserId, postId);

        // Publish async event so the listener increments PostStats.viewCount
        postViewEventPublisher.publishPostViewed(postId, currentUserId);
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
