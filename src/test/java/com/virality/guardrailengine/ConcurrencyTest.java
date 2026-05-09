package com.virality.guardrailengine;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ConcurrencyTest {

    private static final int START_BOT_ID = 404;

    @Test
    public void testHorizontalCapWith200ConcurrentBotReplies() throws Exception {
        int totalRequests = 200;
        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(totalRequests);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        HttpClient client = HttpClient.newHttpClient();

        for (int i = 0; i < totalRequests; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    long botId = START_BOT_ID + index;
                    String body = String.format("""
                            {
                                "content": "Bot reply %d",
                                "botId": %d,
                                "depthLevel": 0
                            }
                            """, index, botId);

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:8080/api/posts/1/comments"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();

                    HttpResponse<String> response = client.send(request,
                            HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 201) {
                        successCount.incrementAndGet();
                    } else {
                        rejectedCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    rejectedCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        System.out.println("=== CONCURRENCY TEST RESULTS ===");
        System.out.println("Total Requests: " + totalRequests);
        System.out.println("Successful (saved to DB): " + successCount.get());
        System.out.println("Rejected (429/other): " + rejectedCount.get());
        System.out.println("================================");

        Assertions.assertTrue(successCount.get() <= 100,
                "FAILED! " + successCount.get() + " comments saved, expected max 100!");
        Assertions.assertTrue(successCount.get() > 0,
                "FAILED! No comments were saved at all!");

        System.out.println("PASSED! Horizontal cap held at: " + successCount.get());
    }
}