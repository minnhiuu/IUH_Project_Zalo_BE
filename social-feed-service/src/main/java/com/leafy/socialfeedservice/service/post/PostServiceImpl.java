package com.leafy.socialfeedservice.service.post;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.utils.SecurityUtil;
import com.leafy.socialfeedservice.dto.request.post.CreatePostRequest;
import com.leafy.socialfeedservice.dto.request.post.UpdatePostRequest;
import com.leafy.socialfeedservice.dto.response.post.PostResponse;
import com.leafy.socialfeedservice.mapper.PostMapper;
import com.leafy.socialfeedservice.model.Post;
import com.leafy.socialfeedservice.repository.PostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostServiceImpl implements PostService {

    PostRepository postRepository;
    PostMapper postMapper;
    SecurityUtil securityUtil;

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

        Post savedPost = postRepository.save(post);
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
    @Transactional
    public PostResponse updatePost(String postId, UpdatePostRequest request) {
        String currentUserId = securityUtil.getCurrentUserId();
        Post post = getActivePost(postId);

        validateOwner(post, currentUserId);

        postMapper.updatePost(post, request);
        post.setEdited(true);
        post.setUpdatedAt(LocalDateTime.now());
        post.setVersion(post.getVersion() + 1);

        Post updatedPost = postRepository.save(post);
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

        postRepository.save(post);
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
}
