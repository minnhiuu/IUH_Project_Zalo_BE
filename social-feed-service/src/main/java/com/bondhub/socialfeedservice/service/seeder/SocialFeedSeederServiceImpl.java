package com.bondhub.socialfeedservice.service.seeder;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.common.event.socialfeed.InteractionType;
import com.bondhub.socialfeedservice.client.AuthServiceClient;
import com.bondhub.socialfeedservice.client.PostRecommendationClient;
import com.bondhub.socialfeedservice.client.UserServiceClient;
import com.bondhub.socialfeedservice.dto.request.user.UserInterestSeedUpdateRequest;
import com.bondhub.socialfeedservice.dto.response.account.AccountSummaryResponse;
import com.bondhub.socialfeedservice.model.Comment;
import com.bondhub.socialfeedservice.model.Post;
import com.bondhub.socialfeedservice.model.Reaction;
import com.bondhub.socialfeedservice.model.UserInteraction;
import com.bondhub.socialfeedservice.model.embedded.PostContent;
import com.bondhub.socialfeedservice.model.embedded.PostMedia;
import com.bondhub.socialfeedservice.model.embedded.PostMusic;
import com.bondhub.socialfeedservice.model.embedded.PostStats;
import com.bondhub.socialfeedservice.model.enums.PostType;
import com.bondhub.socialfeedservice.model.enums.ReactionTargetType;
import com.bondhub.socialfeedservice.model.enums.ReactionType;
import com.bondhub.socialfeedservice.model.enums.Visibility;
import com.bondhub.socialfeedservice.publisher.PostEventPublisher;
import com.bondhub.socialfeedservice.repository.CommentRepository;
import com.bondhub.socialfeedservice.repository.PostRepository;
import com.bondhub.socialfeedservice.repository.ReactionRepository;
import com.bondhub.socialfeedservice.repository.UserInteractionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class SocialFeedSeederServiceImpl implements SocialFeedSeederService {

    static final int DEFAULT_MAX_USERS = 20;
    static final int POSTS_PER_TYPE = 60;
    static final int COMMENTS_PER_POST = 3;
    static final int REACTIONS_PER_POST = 5;
    static final int INTERACTIONS_PER_POST = 8;
    static final int INTERESTS_PER_USER = 4; // how many interests to pick per user
    static final int MIN_IMAGES_PER_POST = 1;
    static final int MAX_IMAGES_PER_POST = 3;

    static final String[] INTERESTS = {
            // Lifestyle
            "travel", "photography", "cooking", "fitness", "fashion", "music", "movies", "reading", "gaming", "art",
            // Tech
            "technology", "artificial intelligence", "mobile apps", "cybersecurity", "web development",
            // Outdoors & Sports
            "hiking", "cycling", "yoga", "football", "basketball",
            // Food & Drink
            "street food", "coffee culture", "baking", "veganism", "wine tasting",
            // Other
            "pets", "interior design", "mental health", "entrepreneurship", "sustainable living"};

    static final String[] POST_CAPTIONS = {"Just had the most amazing bowl of pho — food coma incoming 🍜 #foodie", "Golden hour was absolutely unreal today 🌅 #photography #travel", "Finally finished my home gym setup. No more excuses! 💪 #fitness", "Tried a new coffee shop downtown — 10/10 would recommend ☕ #coffeelover", "Binge-watched the entire series in one weekend. No regrets. 📺", "New personal best on the 5k run this morning 🏃‍♂️ #running #health", "That feeling when your code finally compiles on the first try 🎉 #dev", "Weekend hike with the squad — views were worth every step 🏔️ #outdoors", "Made homemade ramen from scratch tonight — proud of myself 🍥 #cooking", "Unplugged from socials for 3 days. Came back feeling refreshed ✨", "Pet tax: my cat judge me 24/7 and I love it 🐱 #petlife", "Finally booked that trip I've been planning for months ✈️ #wanderlust", "Current mood: lofi beats + rainy window + hot tea 🎵☕", "Art exhibition today was so inspiring — highly recommend going 🎨", "Just discovered this hidden gem of a bookstore in the old quarter 📚"};

    static final String[] COMMENT_TEXTS = {"This is exactly the content I needed today! 🙌", "Wow, where is this? It looks stunning! 😍", "You always make this look so effortless 👏", "I need to try this ASAP!", "Literally laughed out loud 😂", "So relatable, I felt this in my soul.", "The vibe is immaculate 🔥", "Drop the recipe please!! 🙏", "Goals. Absolute goals.", "This just made my day 😊", "You inspire me every single time 💫", "Not me saving this for later 👀", "Okay but how?? Spill the secrets!", "This brought me so much joy ❤️", "Same energy every single day 💯"};

    static final String[] PLACEHOLDER_IMAGE_URLS = {"https://picsum.photos/seed/bondhub-1/1200/800", "https://picsum.photos/seed/bondhub-2/1200/800", "https://picsum.photos/seed/bondhub-3/1200/800", "https://picsum.photos/seed/bondhub-4/1200/800", "https://picsum.photos/seed/bondhub-5/1200/800", "https://picsum.photos/seed/bondhub-6/1200/800", "https://picsum.photos/seed/bondhub-7/1200/800", "https://picsum.photos/seed/bondhub-8/1200/800", "https://picsum.photos/seed/bondhub-9/1200/800", "https://picsum.photos/seed/bondhub-10/1200/800", "https://picsum.photos/seed/bondhub-11/1200/800", "https://picsum.photos/seed/bondhub-12/1200/800"};

    static final String[] PLACEHOLDER_VIDEO_URLS = {
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerMeltdowns.mp4",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/VolkswagenGTIReview.mp4"
    };


    AuthServiceClient authServiceClient;
    PostRecommendationClient postRecommendationClient;
    UserServiceClient userServiceClient;
    PostRepository postRepository;
    PostEventPublisher postEventPublisher;
    CommentRepository commentRepository;
    ReactionRepository reactionRepository;
    UserInteractionRepository userInteractionRepository;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    @Override
    public Map<String, Object> seedEverything() {
        log.info("🚀 Starting full seed pipeline: users → interests → posts/comments/reactions");

        List<AccountSummaryResponse> accounts = fetchAccounts(DEFAULT_MAX_USERS);
        if (accounts.isEmpty()) {
            log.warn("⚠️  No accounts found in auth-service — full seeding skipped");
            return buildSummary(0, 0, 0, 0, 0, "No accounts available in auth-service");
        }

        Map<String, String> accountIdToUserId = resolveUserIds(accounts);
        List<String> userIds = accountIdToUserId.values().stream().toList();
        if (userIds.isEmpty()) {
            log.warn("⚠️  No user IDs could be resolved from user-service — full seeding skipped");
            return buildSummary(0, 0, 0, 0, 0, "No user IDs available in user-service");
        }

        if (accountIdToUserId.size() < accounts.size()) {
            log.warn("⚠️  Resolved userId for {}/{} accounts; unresolved accounts will be skipped", accountIdToUserId.size(), accounts.size());
        }

        Random random = new Random();
        ReactionType[] reactionTypes = ReactionType.values();

        // ── Step 1: Seed interests through user-service internal endpoint ─────
        log.info("📡 Step 1/3 — Seeding interests for {} users", userIds.size());
        int interestsPublished = 0;
        for (AccountSummaryResponse account : accounts) {
            try {
                Set<String> interests = pickRandomInterests(random);

                userServiceClient.updateUserInterestsForSeed(account.id(), new UserInterestSeedUpdateRequest(interests));

                interestsPublished++;

                String userId = accountIdToUserId.get(account.id());
                if (userId == null || userId.isBlank()) {
                    log.warn("⚠️  Skipping re-vectorization because userId could not be resolved for accountId={}", account.id());
                    continue;
                }

                // Trigger re-vectorization immediately so the recommendation
                // service builds the dynamic user vector in Qdrant right away.
                // Failures are non-fatal.
                try {
                    postRecommendationClient.revectorizeUser(userId);
                    log.debug("🔄 Re-vectorization triggered for userId={}", userId);
                } catch (Exception revecEx) {
                    log.warn("⚠️  Re-vectorization failed for userId={} — will retry later: {}", userId, revecEx.getMessage());
                }
            } catch (Exception e) {
                log.error("❌ Failed to seed interests for accountId={}", account.id(), e);
            }
        }
        log.info("✅ Step 1/3 done — Seeded interests for {} users", interestsPublished);

        // ── Step 2: Build and save Posts ──────────────────────────────────────
        log.info("📝 Step 2/3 — Creating posts");
        List<Post> basePosts = buildBasePosts(userIds, random);
        List<Post> savedBasePosts = postRepository.saveAll(basePosts);
        List<Post> sharePosts = buildSharePosts(savedBasePosts, userIds, random);
        List<Post> savedSharePosts = postRepository.saveAll(sharePosts);
        List<Post> savedPosts = new ArrayList<>(savedBasePosts);
        savedPosts.addAll(savedSharePosts);
        log.info("✅ Step 2/3 done — Saved {} posts", savedPosts.size());
        int publishedPostEvents = publishPostCreatedEvents(savedPosts);
        log.info("✅ Step 2/3 done — Published {} POST_CREATED events", publishedPostEvents);

        // ── Step 3: Build and save Comments & Reactions ───────────────────────
        log.info("💬 Step 3/3 — Creating comments and reactions");
        List<Comment> comments = new ArrayList<>();
        List<Reaction> reactions = new ArrayList<>();
        List<UserInteraction> interactions = new ArrayList<>();
        for (Post savedPost : savedPosts) {
            for (int c = 0; c < COMMENTS_PER_POST; c++) {
                comments.add(Comment.builder()
                        .postId(savedPost.getId())
                        .authorId(userIds.get(random.nextInt(userIds.size())))
                        .content(COMMENT_TEXTS[random.nextInt(COMMENT_TEXTS.length)])
                        .build());
            }
            List<String> shuffled = new ArrayList<>(userIds);
            Collections.shuffle(shuffled, random);
            for (String reactorId : shuffled.subList(0, Math.min(REACTIONS_PER_POST, shuffled.size()))) {
                reactions.add(Reaction.builder()
                        .authorId(reactorId)
                        .targetId(savedPost.getId())
                        .targetType(ReactionTargetType.POST)
                        .type(reactionTypes[random.nextInt(reactionTypes.length)])
                        .build());
            }

            for (int i = 0; i < INTERACTIONS_PER_POST; i++) {
                String interactionUserId = userIds.get(random.nextInt(userIds.size()));
                InteractionType interactionType = pickRandomInteractionType(random);
                Instant createdAt = Instant.now().minusSeconds(random.nextInt(30 * 24 * 60 * 60));

                interactions.add(UserInteraction.builder()
                        .userId(interactionUserId)
                        .postId(savedPost.getId())
                        .interactionType(interactionType)
                        .weight(interactionType.getWeight())
                        .createdAt(createdAt)
                        .ingestedAt(Instant.now())
                        .build());
            }
        }

        List<Comment> savedRootComments = commentRepository.saveAll(comments);

        Map<String, List<Comment>> rootCommentsByPost = new HashMap<>();
        for (Comment comment : savedRootComments) {
            rootCommentsByPost.computeIfAbsent(comment.getPostId(), ignored -> new ArrayList<>()).add(comment);
        }

        List<Comment> replyComments = new ArrayList<>();
        for (Post savedPost : savedPosts) {
            List<Comment> postRootComments = rootCommentsByPost.getOrDefault(savedPost.getId(), List.of());
            if (postRootComments.isEmpty()) {
                continue;
            }

            int repliesForPost = 1 + random.nextInt(5); // random 1-5 replies per post
            for (int i = 0; i < repliesForPost; i++) {
                Comment parentComment = postRootComments.get(random.nextInt(postRootComments.size()));
                parentComment.setReplyCount(parentComment.getReplyCount() + 1);

                replyComments.add(Comment.builder()
                        .postId(savedPost.getId())
                        .authorId(userIds.get(random.nextInt(userIds.size())))
                        .parentId(parentComment.getId())
                        .replyDepth(parentComment.getReplyDepth() + 1)
                        .content(COMMENT_TEXTS[random.nextInt(COMMENT_TEXTS.length)])
                        .build());
            }
        }

        if (!replyComments.isEmpty()) {
            commentRepository.saveAll(savedRootComments);
        }
        List<Comment> savedReplyComments = commentRepository.saveAll(replyComments);

        List<Comment> savedComments = new ArrayList<>(savedRootComments);
        savedComments.addAll(savedReplyComments);

        List<Reaction> savedReactions = reactionRepository.saveAll(reactions);
        applySeedReactionStats(savedPosts, savedReactions);
        List<UserInteraction> savedInteractions = userInteractionRepository.saveAll(interactions);
        log.info("✅ Step 3/3 done — Saved {} comments, {} reactions, {} interactions", savedComments.size(), savedReactions.size(), savedInteractions.size());

        log.info("🏁 Full seed pipeline completed!");
        return buildSummary(savedPosts.size(), savedComments.size(), savedReactions.size(), savedInteractions.size(), interestsPublished, String.format("Full seed done! Users: %d, Interests: %d, Posts: %d, Comments: %d, Reactions: %d, Interactions: %d", userIds.size(), interestsPublished, savedPosts.size(), savedComments.size(), savedReactions.size(), savedInteractions.size()));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private List<AccountSummaryResponse> fetchAccounts(int limit) {
        ApiResponse<List<AccountSummaryResponse>> response = authServiceClient.getAllAccounts();
        if (response == null || response.data() == null) {
            return List.of();
        }
        List<AccountSummaryResponse> all = response.data();
        return all.size() > limit ? all.subList(0, limit) : all;
    }

    private Map<String, String> resolveUserIds(List<AccountSummaryResponse> accounts) {
        Map<String, String> accountIdToUserId = new HashMap<>();
        for (AccountSummaryResponse account : accounts) {
            try {
                ApiResponse<UserSummaryResponse> response = userServiceClient.getUserSummaryByAccountId(account.id());
                String userId = response != null && response.data() != null ? response.data().id() : null;
                if (userId == null || userId.isBlank()) {
                    log.warn("⚠️  Missing userId for accountId={} from user-service summary", account.id());
                    continue;
                }
                accountIdToUserId.put(account.id(), userId);
            } catch (Exception ex) {
                log.warn("⚠️  Failed to resolve userId for accountId={}: {}", account.id(), ex.getMessage());
            }
        }
        return accountIdToUserId;
    }

    /**
     * Picks {@code count} distinct random interests from {@link #INTERESTS} as a {@link List}
     * (used for post hashtags).
     */
    private List<String> pickRandomInterestList(Random random, int count) {
        List<String> pool = new ArrayList<>(List.of(INTERESTS));
        Collections.shuffle(pool, random);
        return pool.subList(0, Math.min(count, pool.size()));
    }

    /**
     * Picks {@value #INTERESTS_PER_USER} distinct random interests from
     * {@link #INTERESTS} as a {@link java.util.Set}.
     */
    private Set<String> pickRandomInterests(Random random) {
        List<String> pool = new ArrayList<>(List.of(INTERESTS));
        Collections.shuffle(pool, random);
        return new HashSet<>(pool.subList(0, INTERESTS_PER_USER));
    }

    private InteractionType pickRandomInteractionType(Random random) {
        InteractionType[] interactionTypes = {InteractionType.VIEW, InteractionType.LIKE, InteractionType.COMMENT, InteractionType.SHARE, InteractionType.DISLIKE};
        return interactionTypes[random.nextInt(interactionTypes.length)];
    }

    private List<Post> buildBasePosts(List<String> userIds, Random random) {
        List<Post> seededPosts = new ArrayList<>();

        for (PostType type : List.of(PostType.FEED, PostType.STORY, PostType.REEL)) {
            for (int i = 0; i < POSTS_PER_TYPE; i++) {
                seededPosts.add(buildPostByType(type, userIds, random));
            }
        }

        return seededPosts;
    }

    private List<Post> buildSharePosts(List<Post> shareablePosts, List<String> userIds, Random random) {
        List<Post> seededSharePosts = new ArrayList<>();
        // SHARE posts must reference an existing source post.
        for (int i = 0; i < POSTS_PER_TYPE; i++) {
            Post sourcePost = shareablePosts.get(random.nextInt(shareablePosts.size()));
            seededSharePosts.add(buildSharePost(sourcePost, userIds, random));
        }

        return seededSharePosts;
    }

    private Post buildPostByType(PostType postType, List<String> userIds, Random random) {
        String authorId = userIds.get(random.nextInt(userIds.size()));
        String caption = POST_CAPTIONS[random.nextInt(POST_CAPTIONS.length)];
        LocalDateTime uploadedAt = LocalDateTime.now().minusDays(random.nextInt(30));

        Post.PostBuilder<?, ?> builder = Post.builder()
                .authorId(authorId)
                .postType(postType)
                .visibility(Visibility.ALL)
                .content(PostContent.builder()
                        .caption(caption)
                        .hashtags(pickRandomInterestList(random, 2))
                        .build())
            .media(postType == PostType.REEL
                ? buildSingleSeedVideoMedia(random)
                : (postType == PostType.STORY ? buildSingleSeedMedia(random) : buildSeedMedia(random)))
                .stats(PostStats.builder()
                        .reactionCount(REACTIONS_PER_POST)
                        .commentCount(COMMENTS_PER_POST)
                        .shareCount(0)
                        .build())
                .uploadedAt(uploadedAt)
                .updatedAt(uploadedAt);

        if (postType == PostType.STORY) {
            builder.expiresAt(uploadedAt.plusHours(24));
            if (random.nextBoolean()) {
                int idx = 1 + random.nextInt(50);
                builder.music(PostMusic.builder()
                        .jamendoId("seed-" + idx)
                        .title("Seed Track " + idx)
                        .artistName("Seed Artist")
                        .audioUrl("https://example.com/audio/" + idx + ".mp3")
                        .coverUrl("https://picsum.photos/seed/music" + idx + "/100/100")
                        .duration(180 + random.nextInt(120))
                        .albumName("Seed Album")
                        .build());
            }
        }

        if (postType == PostType.REEL) {
            int idx = 1 + random.nextInt(50);
            builder.music(PostMusic.builder()
                    .jamendoId("seed-" + idx)
                    .title("Seed Track " + idx)
                    .artistName("Seed Artist")
                    .audioUrl("https://example.com/audio/" + idx + ".mp3")
                    .coverUrl("https://picsum.photos/seed/music" + idx + "/100/100")
                    .duration(180 + random.nextInt(120))
                    .albumName("Seed Album")
                    .build());
        }

        return builder.build();
    }

    private Post buildSharePost(Post sourcePost, List<String> userIds, Random random) {
        String shareAuthorId = userIds.get(random.nextInt(userIds.size()));
        LocalDateTime uploadedAt = LocalDateTime.now().minusDays(random.nextInt(30));

        return Post.builder()
                .authorId(shareAuthorId)
                .postType(PostType.SHARE)
                .visibility(Visibility.ALL)
                .sharedPostId(sourcePost.getId())
                .originalAuthorId(sourcePost.getOriginalAuthorId() != null ? sourcePost.getOriginalAuthorId() : sourcePost.getAuthorId())
                .rootPostId(sourcePost.getRootPostId() != null ? sourcePost.getRootPostId() : sourcePost.getId())
                .sharedCaption(PostContent.builder()
                        .caption("Sharing this: " + POST_CAPTIONS[random.nextInt(POST_CAPTIONS.length)])
                        .hashtags(pickRandomInterestList(random, 2))
                        .build())
                .stats(PostStats.builder()
                        .reactionCount(REACTIONS_PER_POST)
                        .commentCount(COMMENTS_PER_POST)
                        .shareCount(0)
                        .build())
                .uploadedAt(uploadedAt)
                .updatedAt(uploadedAt)
                .build();
    }

    private List<PostMedia> buildSeedMedia(Random random) {
        List<PostMedia> media = new ArrayList<>();

        int imageCount = MIN_IMAGES_PER_POST + random.nextInt(MAX_IMAGES_PER_POST - MIN_IMAGES_PER_POST + 1);
        List<String> imagePool = new ArrayList<>(List.of(PLACEHOLDER_IMAGE_URLS));
        Collections.shuffle(imagePool, random);

        for (int i = 0; i < Math.min(imageCount, imagePool.size()); i++) {
            media.add(PostMedia.builder().url(imagePool.get(i)).type("IMAGE").build());
        }

        if (random.nextBoolean()) {
            media.add(PostMedia.builder().url(PLACEHOLDER_VIDEO_URLS[random.nextInt(PLACEHOLDER_VIDEO_URLS.length)]).type("VIDEO").build());
        }

        return media;
    }

    private List<PostMedia> buildSingleSeedMedia(Random random) {
        List<PostMedia> media = new ArrayList<>();
        if (random.nextBoolean()) {
            media.add(PostMedia.builder()
                    .url(PLACEHOLDER_VIDEO_URLS[random.nextInt(PLACEHOLDER_VIDEO_URLS.length)])
                    .type("VIDEO")
                    .build());
            return media;
        }

        media.add(PostMedia.builder()
                .url(PLACEHOLDER_IMAGE_URLS[random.nextInt(PLACEHOLDER_IMAGE_URLS.length)])
                .type("IMAGE")
                .build());
        return media;
    }

    private List<PostMedia> buildSingleSeedVideoMedia(Random random) {
        return List.of(
                PostMedia.builder()
                        .url(PLACEHOLDER_VIDEO_URLS[random.nextInt(PLACEHOLDER_VIDEO_URLS.length)])
                        .type("VIDEO")
                        .build()
        );
    }

    private int publishPostCreatedEvents(List<Post> posts) {
        int published = 0;
        for (Post post : posts) {
            try {
                postEventPublisher.publishPostCreated(post);
                published++;
            } catch (Exception e) {
                log.warn("⚠️ Failed to publish POST_CREATED for postId={} during seeding", post.getId(), e);
            }
        }
        return published;
    }

    private void applySeedReactionStats(List<Post> posts, List<Reaction> reactions) {
        Map<String, Map<ReactionType, Long>> reactionCountsByPost = new HashMap<>();

        for (Reaction reaction : reactions) {
            if (reaction.getTargetType() != ReactionTargetType.POST || !reaction.isActive()) {
                continue;
            }

            reactionCountsByPost
                    .computeIfAbsent(reaction.getTargetId(), ignored -> new EnumMap<>(ReactionType.class))
                    .merge(reaction.getType(), 1L, Long::sum);
        }

        for (Post post : posts) {
            Map<ReactionType, Long> counts = reactionCountsByPost.getOrDefault(post.getId(), Collections.emptyMap());
            int totalReactions = counts.values().stream().mapToInt(Long::intValue).sum();

            List<ReactionType> topReactions = counts.entrySet().stream()
                    .sorted(Comparator
                            .comparing(Map.Entry<ReactionType, Long>::getValue, Comparator.reverseOrder())
                            .thenComparing(entry -> entry.getKey().name()))
                    .limit(3)
                    .map(Map.Entry::getKey)
                    .toList();

            PostStats stats = post.getStats() == null ? PostStats.builder().build() : post.getStats();
            stats.setReactionCount(totalReactions);
            stats.setTopReactions(topReactions);
            post.setStats(stats);
            post.setUpdatedAt(LocalDateTime.now());
        }

        postRepository.saveAll(posts);
    }

    private Map<String, Object> buildSummary(int posts, int comments, int reactions, int interactions, int interestsPublished, String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("posts", posts);
        result.put("comments", comments);
        result.put("reactions", reactions);
        result.put("interactions", interactions);
        result.put("interests_published", interestsPublished);
        result.put("message", message);
        return result;
    }
}
