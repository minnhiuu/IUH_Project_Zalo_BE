package com.leafy.socialfeedservice.service.post;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.utils.SecurityUtil;
import com.leafy.socialfeedservice.dto.request.post.CreatePostRequest;
import com.leafy.socialfeedservice.dto.request.post.UpdatePostRequest;
import com.leafy.socialfeedservice.dto.response.post.PostResponse;
import com.leafy.socialfeedservice.mapper.PostMapper;
import com.leafy.socialfeedservice.model.Hashtag;
import com.leafy.socialfeedservice.model.Post;
import com.leafy.socialfeedservice.model.embedded.PostStats;
import com.leafy.socialfeedservice.model.enums.PostType;
import com.leafy.socialfeedservice.publisher.PostEventPublisher;
import com.leafy.socialfeedservice.repository.HashtagRepository;
import com.leafy.socialfeedservice.repository.PostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostServiceImpl implements PostService {

    PostRepository postRepository;
    HashtagRepository hashtagRepository;
    PostMapper postMapper;
    SecurityUtil securityUtil;
    PostEventPublisher postEventPublisher;

    @Override
    @Transactional
    public PostResponse createPost(CreatePostRequest request) {
        String currentUserId = securityUtil.getCurrentUserId();
        log.info("Creating post for userId={}", currentUserId);

        Post post = postMapper.toPost(request);
        LocalDateTime now = LocalDateTime.now();

        post.setAuthorId(currentUserId);
        post.setUploadedAt(now);
        post.setUpdatedAt(now);

        applyTypeSpecificRulesOnCreate(post, now);

        Post savedPost = postRepository.save(post);
        persistMissingHashtags(extractHashtagsForPersistence(savedPost));
        postEventPublisher.publishPostCreated(savedPost);
        return postMapper.toPostResponse(savedPost);
    }

    @Override
    public PostResponse getPostById(String postId) {
        Post post = getActivePost(postId);
        return postMapper.toPostResponse(post);
    }

    @Override
    public PageResponse<List<PostResponse>> getMyPosts(int page, int size) {
        String currentUserId = securityUtil.getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "uploadedAt"));

        Page<Post> posts = postRepository.findByAuthorIdAndActiveTrueAndIsCurrentTrueOrderByUploadedAtDesc(
                currentUserId,
                pageable);

        return PageResponse.fromPage(posts, postMapper::toPostResponse);
    }

    @Override
    public PageResponse<List<PostResponse>> getFeedAndSharePosts(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "uploadedAt"));

        Page<Post> posts = postRepository.findByPostTypeInAndActiveTrueAndIsCurrentTrueOrderByUploadedAtDesc(
                List.of(PostType.FEED, PostType.SHARE),
                pageable);

        return PageResponse.fromPage(posts, postMapper::toPostResponse);
    }

    @Override
    public PageResponse<List<PostResponse>> getStoryPosts(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "uploadedAt"));

        Page<Post> posts = postRepository.findByPostTypeAndActiveTrueAndIsCurrentTrueOrderByUploadedAtDesc(
                PostType.STORY,
                pageable);

        return PageResponse.fromPage(posts, postMapper::toPostResponse);
    }

    @Override
    public PageResponse<List<PostResponse>> getReelPosts(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "uploadedAt"));

        Page<Post> posts = postRepository.findByPostTypeAndActiveTrueAndIsCurrentTrueOrderByUploadedAtDesc(
                PostType.REEL,
                pageable);

        return PageResponse.fromPage(posts, postMapper::toPostResponse);
    }

    @Override
    @Transactional
    public PostResponse updatePost(String postId, UpdatePostRequest request) {
        String currentUserId = securityUtil.getCurrentUserId();
        Post post = getActivePost(postId);

        validateOwner(post, currentUserId);

        postMapper.updatePost(post, request);
        applyTypeSpecificRulesOnUpdate(post);
        post.setEdited(true);
        post.setUpdatedAt(LocalDateTime.now());
        post.setVersion(post.getVersion() + 1);

        Post updatedPost = postRepository.save(post);
        postEventPublisher.publishPostUpdated(updatedPost);
        return postMapper.toPostResponse(updatedPost);
    }

    @Override
    @Transactional
    public void deletePost(String postId) {
        String currentUserId = securityUtil.getCurrentUserId();
        Post post = getActivePost(postId);

        validateOwner(post, currentUserId);

        post.setActive(false);
        post.setCurrent(false);
        post.setUpdatedAt(LocalDateTime.now());

        Post deletedPost = postRepository.save(post);
        postEventPublisher.publishPostDeleted(deletedPost);
    }

    private Post getActivePost(String postId) {
        return postRepository.findByIdAndActiveTrueAndIsCurrentTrue(postId)
                .orElseThrow(() -> new AppException(ErrorCode.POST_NOT_FOUND));
    }

    private void validateOwner(Post post, String currentUserId) {
        if (!post.getAuthorId().equals(currentUserId)) {
            throw new AppException(ErrorCode.AUTH_UNAUTHORIZED);
        }
    }

    private void persistMissingHashtags(List<String> hashtags) {
        if (hashtags == null || hashtags.isEmpty()) {
            return;
        }

        hashtags.stream()
                .map(this::normalizeHashtag)
                .filter(normalized -> normalized != null && !normalized.isBlank())
                .distinct()
                .forEach(normalized -> {
                    if (hashtagRepository.existsByNormalizedValue(normalized)) {
                        return;
                    }

                    try {
                        hashtagRepository.save(Hashtag.builder()
                                .value("#" + normalized)
                                .normalizedValue(normalized)
                                .build());
                    } catch (DuplicateKeyException ignored) {
                        // Concurrent requests can race to insert the same hashtag.
                    }
                });
    }

    private String normalizeHashtag(String hashtag) {
        if (hashtag == null) {
            return null;
        }

        String normalized = hashtag.trim().replaceFirst("^#+", "");
        if (normalized.isBlank()) {
            return null;
        }

        return normalized.toLowerCase(Locale.ROOT);
    }

    private List<String> extractHashtagsForPersistence(Post post) {
        if (post.getContent() != null && post.getContent().getHashtags() != null) {
            return post.getContent().getHashtags();
        }

        if (post.getSharedCaption() != null) {
            return post.getSharedCaption().getHashtags();
        }

        return List.of();
    }

    private void applyTypeSpecificRulesOnCreate(Post post, LocalDateTime now) {
        if (post.getPostType() == PostType.SHARE) {
            Post sharedPost = resolveSharedPost(post.getSharedPostId());

            if (post.getSharedCaption() == null && post.getContent() != null) {
                post.setSharedCaption(post.getContent());
            }

            post.setContent(null);
            post.setOriginalAuthorId(sharedPost.getOriginalAuthorId() != null
                    ? sharedPost.getOriginalAuthorId()
                    : sharedPost.getAuthorId());
            post.setRootPostId(sharedPost.getRootPostId() != null
                    ? sharedPost.getRootPostId()
                    : sharedPost.getId());

            incrementShareCount(sharedPost);
        } else {
            post.setSharedPostId(null);
            post.setOriginalAuthorId(null);
            post.setSharedCaption(null);
            post.setRootPostId(null);
        }

        if (post.getPostType() == PostType.STORY) {
            if (post.getExpiresAt() == null) {
                post.setExpiresAt(now.plusHours(24));
            }
        } else {
            post.setExpiresAt(null);
            post.setViewerIds(null);
            post.setElements(null);
        }

        if (post.getPostType() != PostType.REEL) {
            post.setMusicId(null);
        }

        validateSingleMediaForStoryOrReel(post);
        validateReelMediaType(post);
    }

    private void applyTypeSpecificRulesOnUpdate(Post post) {
        if (post.getPostType() == PostType.SHARE) {
            if (post.getSharedCaption() == null && post.getContent() != null) {
                post.setSharedCaption(post.getContent());
            }
            post.setContent(null);
        }

        if (post.getPostType() != PostType.STORY) {
            post.setExpiresAt(null);
            post.setViewerIds(null);
            post.setElements(null);
        }

        if (post.getPostType() != PostType.REEL) {
            post.setMusicId(null);
        }

        validateSingleMediaForStoryOrReel(post);
        validateReelMediaType(post);
    }

    private void validateSingleMediaForStoryOrReel(Post post) {
        if (post.getPostType() != PostType.STORY && post.getPostType() != PostType.REEL) {
            return;
        }

        int mediaCount = post.getMedia() == null ? 0 : post.getMedia().size();
        if (mediaCount > 1) {
            throw new AppException(ErrorCode.INVALID_OPERATION);
        }
    }

    private void validateReelMediaType(Post post) {
        if (post.getPostType() != PostType.REEL) {
            return;
        }

        int mediaCount = post.getMedia() == null ? 0 : post.getMedia().size();
        if (mediaCount != 1) {
            throw new AppException(ErrorCode.INVALID_OPERATION);
        }

        String mediaType = post.getMedia().get(0).getType();
        if (mediaType == null || !"VIDEO".equalsIgnoreCase(mediaType.trim())) {
            throw new AppException(ErrorCode.INVALID_OPERATION);
        }
    }

    private Post resolveSharedPost(String sharedPostId) {
        if (sharedPostId == null || sharedPostId.isBlank()) {
            throw new AppException(ErrorCode.POST_NOT_FOUND);
        }

        return getActivePost(sharedPostId);
    }

    private void incrementShareCount(Post sharedPost) {
        PostStats stats = sharedPost.getStats() == null ? PostStats.builder().build() : sharedPost.getStats();
        stats.setShareCount(stats.getShareCount() + 1);
        sharedPost.setStats(stats);
        sharedPost.setUpdatedAt(LocalDateTime.now());

        Post updatedSharedPost = postRepository.save(sharedPost);
        postEventPublisher.publishPostUpdated(updatedSharedPost);
    }
}
