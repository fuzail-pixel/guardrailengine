package com.virality.guardrailengine.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisService {

    private final RedisTemplate<String, String> redisTemplate;

    // ── Virality Score ──────────────────────────────────────────
    public void incrementViralityScore(Long postId, int points) {
        String key = "post:" + postId + ":virality_score";
        redisTemplate.opsForValue().increment(key, points);
    }

    public String getViralityScore(Long postId) {
        String key = "post:" + postId + ":virality_score";
        return redisTemplate.opsForValue().get(key);
    }

    // ── Bot Count (Horizontal Cap) ───────────────────────────────
    public Long incrementBotCount(Long postId) {
        String key = "post:" + postId + ":bot_count";
        return redisTemplate.opsForValue().increment(key);
    }

    public String getBotCount(Long postId) {
        String key = "post:" + postId + ":bot_count";
        return redisTemplate.opsForValue().get(key);
    }

    // ── Cooldown Cap ─────────────────────────────────────────────
    public boolean isCooldownActive(Long botId, Long userId) {
        String key = "cooldown:bot_" + botId + ":human_" + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void setCooldown(Long botId, Long userId) {
        String key = "cooldown:bot_" + botId + ":human_" + userId;
        redisTemplate.opsForValue().set(key, "1", 10, TimeUnit.MINUTES);
    }

    // ── Notification Throttler ───────────────────────────────────
    public boolean isNotificationCooldownActive(Long userId) {
        String key = "notif_cooldown:user_" + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void setNotificationCooldown(Long userId) {
        String key = "notif_cooldown:user_" + userId;
        redisTemplate.opsForValue().set(key, "1", 15, TimeUnit.MINUTES);
    }

    public void pushPendingNotification(Long userId, String message) {
        String key = "user:" + userId + ":pending_notifs";
        redisTemplate.opsForList().rightPush(key, message);
    }

    public java.util.List<String> popAllPendingNotifications(Long userId) {
        String key = "user:" + userId + ":pending_notifs";
        Long size = redisTemplate.opsForList().size(key);
        if (size == null || size == 0) return java.util.Collections.emptyList();
        return redisTemplate.opsForList().leftPop(key, size);
    }

    public void clearPendingNotifications(Long userId) {
        String key = "user:" + userId + ":pending_notifs";
        redisTemplate.delete(key);
    }

    public void decrementBotCount(Long postId) {
    String key = "post:" + postId + ":bot_count";
    redisTemplate.opsForValue().decrement(key);
}

}