package com.virality.guardrailengine.controller;

import com.virality.guardrailengine.dto.CreateCommentRequest;
import com.virality.guardrailengine.dto.CreatePostRequest;
import com.virality.guardrailengine.dto.LikeRequest;
import com.virality.guardrailengine.entity.Comment;
import com.virality.guardrailengine.entity.Post;
import com.virality.guardrailengine.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    // Create Post
    @PostMapping
    public ResponseEntity<Post> createPost(@Valid @RequestBody CreatePostRequest request) {
        Post post = postService.createPost(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(post);
    }

    // Add Comment
    @PostMapping("/{postId}/comments")
    public ResponseEntity<Comment> addComment(
            @PathVariable Long postId,
            @Valid @RequestBody CreateCommentRequest request) {
        Comment comment = postService.addComment(postId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(comment);
    }

    // Like Post
    @PostMapping("/{postId}/like")
    public ResponseEntity<Post> likePost(
            @PathVariable Long postId,
            @Valid @RequestBody LikeRequest request) {
        Post post = postService.likePost(postId, request.getUserId());
        return ResponseEntity.ok(post);
    }
    @GetMapping
public ResponseEntity<List<Post>> getAllPosts() {
    return ResponseEntity.ok(postService.getAllPosts());
}

@GetMapping("/{postId}")
public ResponseEntity<Post> getPost(@PathVariable Long postId) {
    return ResponseEntity.ok(postService.getPost(postId));
}

@GetMapping("/{postId}/comments")
public ResponseEntity<List<Comment>> getComments(@PathVariable Long postId) {
    return ResponseEntity.ok(postService.getComments(postId));
}
    
}