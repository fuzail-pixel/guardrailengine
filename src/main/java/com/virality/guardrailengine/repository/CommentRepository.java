package com.virality.guardrailengine.repository;

import com.virality.guardrailengine.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    int countByPostId(Long postId);
    List<Comment> findByPostId(Long postId);
}