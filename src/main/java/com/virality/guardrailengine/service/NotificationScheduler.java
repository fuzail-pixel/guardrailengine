package com.virality.guardrailengine.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final RedisTemplate<String, String> redisTemplate;

    // Runs every 5 minutes
    @Scheduled(fixedRate = 300000)
    public void sweepPendingNotifications() {
        System.out.println(">>> CRON Sweeper running...");

        // Find all pending notification keys
        Set<String> keys = redisTemplate.keys("user:*:pending_notifs");

        if (keys == null || keys.isEmpty()) {
            System.out.println(">>> No pending notifications found.");
            return;
        }

        for (String key : keys) {
            // Extract userId from key e.g. "user:1:pending_notifs"
            String userId = key.split(":")[1];

            Long size = redisTemplate.opsForList().size(key);
            if (size == null || size == 0) continue;

            // Pop all messages
            List<String> notifications = redisTemplate.opsForList().leftPop(key, size);
            if (notifications == null || notifications.isEmpty()) continue;

            // Build summarized message
            String first = notifications.get(0);
            int others = notifications.size() - 1;

            if (others == 0) {
                System.out.println("Summarized Push Notification for User " + userId
                        + ": " + first);
            } else {
                System.out.println("Summarized Push Notification for User " + userId
                        + ": " + first + " and [" + others + "] others interacted with your posts.");
            }

            // Clear the list
            redisTemplate.delete(key);
        }
    }
}