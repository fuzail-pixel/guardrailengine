package com.virality.guardrailengine.service;

import com.virality.guardrailengine.dto.CreateCommentRequest;
import com.virality.guardrailengine.dto.CreatePostRequest;
import com.virality.guardrailengine.entity.Bot;
import com.virality.guardrailengine.entity.Comment;
import com.virality.guardrailengine.entity.Post;
import com.virality.guardrailengine.entity.User;
import com.virality.guardrailengine.exception.BadRequestException;
import com.virality.guardrailengine.exception.ResourceNotFoundException;
import com.virality.guardrailengine.exception.TooManyRequestsException;
import com.virality.guardrailengine.repository.BotRepository;
import com.virality.guardrailengine.repository.CommentRepository;
import com.virality.guardrailengine.repository.PostRepository;
import com.virality.guardrailengine.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final BotRepository botRepository;
    private final RedisService redisService;

    // ── Create Post ──────────────────────────────────────────────
    @Transactional
    public Post createPost(CreatePostRequest request) {

        validateAuthor(request.getUserId(), request.getBotId());

        Post post = new Post();
        post.setContent(request.getContent());

        if (request.getUserId() != null) {
            User user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "User not found with id: " + request.getUserId()));
            post.setUser(user);
        }

        if (request.getBotId() != null) {
            Bot bot = botRepository.findById(request.getBotId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Bot not found with id: " + request.getBotId()));
            post.setBot(bot);
        }

        return postRepository.save(post);
    }

    // ── Add Comment ──────────────────────────────────────────────
    @Transactional
    public Comment addComment(Long postId, CreateCommentRequest request) {

        validateAuthor(request.getUserId(), request.getBotId());

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Post not found with id: " + postId));

        // Vertical Cap check
        if (request.getDepthLevel() > 20) {
            throw new BadRequestException("Comment thread cannot go deeper than 20 levels");
        }

        // Bot specific guardrails
        if (request.getBotId() != null) {
            Bot bot = botRepository.findById(request.getBotId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Bot not found with id: " + request.getBotId()));

            // Horizontal Cap check
            Long botCount = redisService.incrementBotCount(postId);
            if (botCount > 100) {
                // rollback the increment
                redisService.decrementBotCount(postId);
                throw new TooManyRequestsException(
                        "Post has reached the maximum of 100 bot replies");
            }

            // Cooldown Cap check
            Long humanId = getPostOwnerId(post);
            if (humanId != null && redisService.isCooldownActive(request.getBotId(), humanId)) {
                throw new TooManyRequestsException(
                        "Bot " + request.getBotId() + " is on cooldown for this user");
            }

            Comment comment = buildComment(post, null, bot, request);
            Comment saved = commentRepository.save(comment);

            // Set cooldown after successful save
            if (humanId != null) {
                redisService.setCooldown(request.getBotId(), humanId);
            }

            // Update virality score
            redisService.incrementViralityScore(postId, 1);

            // Handle notification
            if (humanId != null) {
                handleBotNotification(humanId, bot.getName(), postId);
            }

            return saved;
        }

        // Human comment
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id: " + request.getUserId()));

        Comment comment = buildComment(post, user, null, request);
        Comment saved = commentRepository.save(comment);

        // Update virality score
        redisService.incrementViralityScore(postId, 50);

        return saved;
    }

    // ── Like Post ────────────────────────────────────────────────
    @Transactional
    public Post likePost(Long postId, Long userId) {

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Post not found with id: " + postId));

        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id: " + userId));

        post.setLikeCount(post.getLikeCount() + 1);
        Post saved = postRepository.save(post);

        // Update virality score
        redisService.incrementViralityScore(postId, 20);

        return saved;
    }

    // ── Helpers ──────────────────────────────────────────────────
    private void validateAuthor(Long userId, Long botId) {
        if (userId == null && botId == null) {
            throw new BadRequestException("Either userId or botId must be provided");
        }
        if (userId != null && botId != null) {
            throw new BadRequestException("Only one of userId or botId can be provided");
        }
    }

    private Long getPostOwnerId(Post post) {
        if (post.getUser() != null) {
            return post.getUser().getId();
        }
        return null;
    }

    private Comment buildComment(Post post, User user, Bot bot, CreateCommentRequest request) {
        Comment comment = new Comment();
        comment.setPost(post);
        comment.setUser(user);
        comment.setBot(bot);
        comment.setContent(request.getContent());
        comment.setDepthLevel(request.getDepthLevel());
        return comment;
    }

    private void handleBotNotification(Long userId, String botName, Long postId) {
        if (redisService.isNotificationCooldownActive(userId)) {
            redisService.pushPendingNotification(userId,
                    "Bot " + botName + " replied to your post " + postId);
        } else {
            System.out.println("Push Notification Sent to User: " + userId
                    + " - Bot " + botName + " replied to your post");
            redisService.setNotificationCooldown(userId);
        }
    }
    public List<Post> getAllPosts() {
        return postRepository.findAll();
    }

    public Post getPost(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Post not found with id: " + postId));
    }

    public List<Comment> getComments(Long postId) {
        postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Post not found with id: " + postId));
        return commentRepository.findByPostId(postId);
    }
    
}