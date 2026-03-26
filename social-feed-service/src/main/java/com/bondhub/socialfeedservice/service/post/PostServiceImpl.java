package com.bondhub.socialfeedservice.service.post;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.utils.S3Util;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.socialfeedservice.dto.request.post.CreatePostRequest;
import com.bondhub.socialfeedservice.dto.request.post.UpdatePostRequest;
import com.bondhub.socialfeedservice.dto.response.post.AuthorInfo;
import com.bondhub.socialfeedservice.dto.response.post.PostResponse;
import com.bondhub.socialfeedservice.dto.response.post.SharedPostPreview;
import com.bondhub.socialfeedservice.dto.response.post.StoryGroupResponse;
import com.bondhub.socialfeedservice.mapper.PostMapper;
import com.bondhub.socialfeedservice.model.embedded.PostMedia;
import com.bondhub.socialfeedservice.model.Hashtag;
import com.bondhub.socialfeedservice.model.Post;
import com.bondhub.socialfeedservice.model.UserSummary;
import com.bondhub.socialfeedservice.model.embedded.PostStats;
import com.bondhub.socialfeedservice.model.enums.PostType;
import com.bondhub.socialfeedservice.model.enums.ReactionTargetType;
import com.bondhub.socialfeedservice.model.enums.ReactionType;
import com.bondhub.socialfeedservice.publisher.PostEventPublisher;
import com.bondhub.socialfeedservice.repository.HashtagRepository;
import com.bondhub.socialfeedservice.repository.PostRepository;
import com.bondhub.socialfeedservice.repository.ReactionRepository;
import com.bondhub.socialfeedservice.repository.UserSummaryRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PostServiceImpl implements PostService {

    final PostRepository postRepository;
    final HashtagRepository hashtagRepository;
    final PostMapper postMapper;
    final SecurityUtil securityUtil;
    final PostEventPublisher postEventPublisher;
    final ReactionRepository reactionRepository;
    final UserSummaryRepository userSummaryRepository;

    @Value("${aws.s3.bucket.name}")
    String bucketName;

    @Value("${cloud.aws.region.static}")
    String region;

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
        UserSummary author = userSummaryRepository.findById(currentUserId).orElse(null);
        return postMapper.toPostResponse(savedPost, getS3BaseUrl(), null, author);
    }

    @Override
    public PostResponse getPostById(String postId) {
        Post post = getActivePost(postId);
        String currentUserId = securityUtil.getCurrentUserId();
        UserSummary author = userSummaryRepository.findById(post.getAuthorId()).orElse(null);
        return postMapper.toPostResponse(post, getS3BaseUrl(), getCurrentUserReaction(currentUserId, postId), author);
    }

    @Override
    public PageResponse<List<PostResponse>> getMyPosts(int page, int size) {
        String currentUserId = securityUtil.getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "uploadedAt"));

        Page<Post> posts = postRepository.findByAuthorIdAndActiveTrueAndIsCurrentTrueOrderByUploadedAtDesc(
                currentUserId,
                pageable);

        String s3BaseUrl = getS3BaseUrl();
        Map<String, UserSummary> authorMap = buildAuthorMap(posts);
        return PageResponse.fromPage(posts, post -> postMapper.toPostResponse(post, s3BaseUrl,
                getCurrentUserReaction(currentUserId, post.getId()), authorMap.get(post.getAuthorId())));
    }

    @Override
    public PageResponse<List<PostResponse>> getFeedAndSharePosts(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "uploadedAt"));

        Page<Post> posts = postRepository.findByPostTypeInAndActiveTrueAndIsCurrentTrueOrderByUploadedAtDesc(
                List.of(PostType.FEED, PostType.SHARE),
                pageable);

        String s3BaseUrl = getS3BaseUrl();
        String currentUserId = securityUtil.getCurrentUserId();
        Map<String, UserSummary> authorMap = buildAuthorMap(posts);

        // Bulk-fetch all original posts referenced by SHARE posts in a single DB query
        Set<String> sharedPostIds = posts.stream()
                .filter(p -> p.getPostType() == PostType.SHARE && p.getSharedPostId() != null)
                .map(Post::getSharedPostId)
                .collect(Collectors.toSet());

        Map<String, Post> sharedPostMap = sharedPostIds.isEmpty()
                ? Map.of()
                : postRepository.findAllById(sharedPostIds).stream()
                        .collect(Collectors.toMap(Post::getId, p -> p));

        return PageResponse.fromPage(posts, post -> {
            SharedPostPreview preview = buildSharedPostPreview(post, sharedPostMap, authorMap, s3BaseUrl);
            return postMapper.toPostResponse(post, s3BaseUrl,
                    getCurrentUserReaction(currentUserId, post.getId()),
                    authorMap.get(post.getAuthorId()),
                    preview);
        });
    }

    @Override
    public List<StoryGroupResponse> getStoryPosts(int page, int size) {
        // Fetch a large recent window; grouping happens in-memory.
        Pageable pageable = PageRequest.of(page, size * 10, Sort.by(Sort.Direction.DESC, "uploadedAt"));

        Page<Post> posts = postRepository.findByPostTypeAndActiveTrueAndIsCurrentTrueOrderByUploadedAtDesc(
                PostType.STORY,
                pageable);

        String s3BaseUrl = getS3BaseUrl();
        String currentUserId = securityUtil.getCurrentUserId();
        Map<String, UserSummary> authorMap = buildAuthorMap(posts);

        // Group stories by authorId, preserving insertion order (most-recent story first per group).
        Map<String, List<PostResponse>> grouped = new LinkedHashMap<>();
        for (Post post : posts) {
            PostResponse response = postMapper.toPostResponse(
                    post, s3BaseUrl,
                    getCurrentUserReaction(currentUserId, post.getId()),
                    authorMap.get(post.getAuthorId()));
            grouped.computeIfAbsent(post.getAuthorId(), k -> new ArrayList<>()).add(response);
        }

        return grouped.entrySet().stream()
                .map(entry -> {
                    UserSummary user = authorMap.get(entry.getKey());
                    AuthorInfo authorInfo = AuthorInfo.builder()
                            .id(entry.getKey())
                            .fullName(user != null ? user.getFullName() : null)
                            .avatar(user != null && user.getAvatar() != null
                                    ? postMapper.resolveMediaUrl(user.getAvatar(), s3BaseUrl)
                                    : null)
                            .build();
                    return StoryGroupResponse.builder()
                            .authorInfo(authorInfo)
                            .stories(entry.getValue())
                            .build();
                })
                .toList();
    }

    @Override
    public PageResponse<List<PostResponse>> getReelPosts(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "uploadedAt"));

        Page<Post> posts = postRepository.findByPostTypeAndActiveTrueAndIsCurrentTrueOrderByUploadedAtDesc(
                PostType.REEL,
                pageable);

        String s3BaseUrl = getS3BaseUrl();
        String currentUserId = securityUtil.getCurrentUserId();
        Map<String, UserSummary> authorMap = buildAuthorMap(posts);
        return PageResponse.fromPage(posts, post -> postMapper.toPostResponse(post, s3BaseUrl,
                getCurrentUserReaction(currentUserId, post.getId()), authorMap.get(post.getAuthorId())));
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
        UserSummary author = userSummaryRepository.findById(currentUserId).orElse(null);
        return postMapper.toPostResponse(updatedPost, getS3BaseUrl(), null, author);
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

    private String getS3BaseUrl() {
        return S3Util.getS3BaseUrl(bucketName, region);
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

        if (post.getPostType() != PostType.REEL && post.getPostType() != PostType.STORY) {
            post.setMusic(null);
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

        if (post.getPostType() != PostType.REEL && post.getPostType() != PostType.STORY) {
            post.setMusic(null);
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

    private ReactionType getCurrentUserReaction(String userId, String postId) {
        return reactionRepository
                .findByAuthorIdAndTargetIdAndTargetType(userId, postId, ReactionTargetType.POST)
                .filter(r -> r.isActive())
                .map(r -> r.getType())
                .orElse(null);
    }

    private SharedPostPreview buildSharedPostPreview(
            Post post,
            Map<String, Post> sharedPostMap,
            Map<String, UserSummary> authorMap,
            String s3BaseUrl) {
        if (post.getPostType() != PostType.SHARE || post.getSharedPostId() == null) {
            return null;
        }

        Post original = sharedPostMap.get(post.getSharedPostId());
        if (original == null) {
            return null;
        }

        UserSummary originalAuthor = authorMap.computeIfAbsent(
                original.getAuthorId(),
                id -> userSummaryRepository.findById(id).orElse(null));

        AuthorInfo authorInfo = AuthorInfo.builder()
                .id(original.getAuthorId())
                .fullName(originalAuthor != null ? originalAuthor.getFullName() : null)
                .avatar(originalAuthor != null && originalAuthor.getAvatar() != null
                        ? postMapper.resolveMediaUrl(originalAuthor.getAvatar(), s3BaseUrl)
                        : null)
                .build();

        List<PostMedia> resolvedMedia = original.getMedia() == null ? new ArrayList<>() :
                original.getMedia().stream()
                        .filter(m -> m.getUrl() != null)
                        .map(m -> PostMedia.builder()
                                .url(postMapper.resolveMediaUrl(m.getUrl(), s3BaseUrl))
                                .type(m.getType())
                                .build())
                        .toList();

        return SharedPostPreview.builder()
                .postId(original.getId())
                .authorInfo(authorInfo)
                .content(original.getContent())
                .media(resolvedMedia)
                .build();
    }

    private Map<String, UserSummary> buildAuthorMap(Page<Post> posts) {
        List<String> authorIds = posts.stream()
                .map(Post::getAuthorId)
                .distinct()
                .toList();
        return userSummaryRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(UserSummary::getId, s -> s));
    }
}
