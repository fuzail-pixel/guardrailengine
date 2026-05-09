# Guardrail Engine

A high-performance Spring Boot microservice built to handle real-time social interactions between human users and AI bots — with a Redis-powered guardrail system that enforces strict rate limits, cooldowns, and virality scoring under high concurrency.

---

## Overview

Guardrail Engine solves a real problem in AI-driven social platforms: **runaway bot activity**. Without guardrails, bots can spam posts, flood notification inboxes, and create deeply nested comment threads that degrade user experience. This service acts as both the API layer and the enforcement layer — using Redis atomic operations to gate every bot interaction before it ever touches the database.

---

## Architecture

```
Client Request
      │
      ▼
PostController (REST Layer)
      │
      ▼
PostService (Business Logic)
      │
      ├──► RedisService (Guardrail Checks)
      │         │
      │         ├── Horizontal Cap   → post:{id}:bot_count
      │         ├── Vertical Cap     → depth_level check
      │         ├── Cooldown Cap     → cooldown:bot_{id}:human_{id}
      │         ├── Virality Score   → post:{id}:virality_score
      │         └── Notifications    → user:{id}:pending_notifs
      │
      └──► PostgreSQL (Persist if Redis allows)
                │
                └── Users, Bots, Posts, Comments
```

**Redis is the gatekeeper. PostgreSQL is the source of truth.**
Database writes only happen if all Redis guardrails pass.

---

## Features

- **Real-time Virality Scoring** — every interaction atomically updates a post's virality score in Redis
- **Horizontal Cap** — hard limit of 100 bot replies per post, enforced atomically
- **Vertical Cap** — comment threads blocked beyond 20 levels deep
- **Cooldown Cap** — per-bot, per-human 10-minute interaction cooldown using Redis TTL
- **Smart Notification Batching** — prevents notification spam with a 15-minute throttle and summarized delivery
- **CRON Sweeper** — scheduled job that batches and summarizes pending notifications every 5 minutes
- **Race Condition Safe** — validated under 200 concurrent requests with exactly 100 DB writes
- **Fully Stateless** — zero in-memory state; all counters and cooldowns live in Redis

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.x |
| Primary Database | PostgreSQL 15 |
| Cache / State Store | Redis 7 |
| ORM | JPA / Hibernate |
| Containerization | Docker + Docker Compose |
| Testing | JUnit 5 |

---

## Getting Started

### Prerequisites
- Java 17+
- Docker Desktop
- Maven

### 1. Clone the repository
```bash
https://github.com/fuzail-pixel/guardrailengine.git
cd guardrailengine
```

### 2. Start PostgreSQL and Redis
```bash
docker-compose up -d
```

Spins up:
- PostgreSQL on port `5433`
- Redis on port `6379`

### 3. Run the application
```bash
./mvnw spring-boot:run
```

Server starts at `http://localhost:8080`

---

## API Reference

### Users
```http
POST /api/users
Content-Type: application/json

{
  "username": "alice",
  "isPremium": true
}
```

### Bots
```http
POST /api/bots
Content-Type: application/json

{
  "name": "BotAlpha",
  "personaDescription": "A helpful AI assistant"
}
```

### Posts
```http
POST /api/posts
Content-Type: application/json

# By a human user
{ "content": "Hello world", "userId": 1 }

# By a bot
{ "content": "Bot generated post", "botId": 1 }
```

### Comments
```http
POST /api/posts/{postId}/comments
Content-Type: application/json

# Human comment (+50 virality)
{ "content": "Great post!", "userId": 1, "depthLevel": 0 }

# Bot comment (+1 virality, subject to all guardrails)
{ "content": "Interesting!", "botId": 1, "depthLevel": 0 }
```

### Likes
```http
POST /api/posts/{postId}/like
Content-Type: application/json

{ "userId": 1 }
```

---

## Redis Key Schema

| Key | Type | Purpose |
|-----|------|---------|
| `post:{id}:virality_score` | String | Running virality score for a post |
| `post:{id}:bot_count` | String | Number of bot replies on a post |
| `cooldown:bot_{id}:human_{id}` | String (TTL 10min) | Bot-to-human interaction cooldown |
| `notif_cooldown:user_{id}` | String (TTL 15min) | Notification delivery cooldown |
| `user:{id}:pending_notifs` | List | Queued notifications for batched delivery |

---

## Guardrails Deep Dive

### Virality Score
Every interaction atomically increments a Redis counter:

```
Bot Reply        → +1  point
Human Like       → +20 points
Human Comment    → +50 points
```

### Horizontal Cap (100 Bot Replies)
Uses Redis INCR — an atomic single-step operation:
```java
Long botCount = redisService.incrementBotCount(postId);
if (botCount > 100) {
    redisService.decrementBotCount(postId); // rollback
    throw new TooManyRequestsException("...");
}
```
Returns `429 Too Many Requests` when exceeded.

### Vertical Cap (20 Levels)
Checked before any DB operation:
```java
if (request.getDepthLevel() > 20) {
    throw new BadRequestException("Comment thread cannot go deeper than 20 levels");
}
```
Returns `400 Bad Request` when exceeded.

### Cooldown Cap (10 Minutes)
Redis key with TTL set on first interaction:
```
cooldown:bot_1:human_3  →  TTL: 600 seconds
```
Returns `429 Too Many Requests` if key exists.

---

## Notification Engine

### How it works

When a bot interacts with a human's post:

```
Bot interaction
      │
      ▼
Is notif_cooldown:user_{id} set?
      │
      ├── NO  → Log "Push Notification Sent to User"
      │         Set 15-minute cooldown key
      │
      └── YES → Push to user:{id}:pending_notifs Redis List
```

### CRON Sweeper
Runs every 5 minutes via @Scheduled:
1. Scans all `user:*:pending_notifs` keys in Redis
2. Pops all pending messages per user
3. Logs summarized message:
   ```
   Summarized Push Notification for User 1: Bot BotAlpha replied to your post and [5] others interacted with your posts.
   ```
4. Clears the Redis list

---

## Concurrency & Thread Safety

### The Problem
Under high concurrency, naive counter checks fail. Two threads can both read `count = 99`, both pass the `> 100` check, and both write to the database — resulting in 101 or more comments.

### The Solution — Redis Atomic INCR
Redis INCR is a **single atomic operation** executed sequentially at the Redis level, regardless of how many concurrent application threads call it simultaneously.

```java
Long botCount = redisTemplate.opsForValue().increment(key);
```

Each call returns a **unique, monotonically increasing value**:
- Thread 1   → `1`   ✅ allowed
- Thread 2   → `2`   ✅ allowed
- ...
- Thread 100 → `100` ✅ allowed
- Thread 101 → `101` ❌ rolled back, 429 returned
- Thread 102-200 → all rolled back ❌

No Java `synchronized` blocks, no `ReentrantLock`, no `HashMap` — just Redis doing what it does best.

### Stress Test Results

200 concurrent requests fired using `ExecutorService` with 50 threads:

```
=== CONCURRENCY TEST RESULTS ===
Total Requests:           200
Successful (saved to DB): 100
Rejected (429/other):     100
================================
PASSED! Horizontal cap held at exactly 100.
```

PostgreSQL confirmed:
```sql
SELECT COUNT(*) FROM comments WHERE bot_id IS NOT NULL;
-- Result: 100
```

---

## Testing

Run the concurrency test:
```bash
./mvnw test -Dtest=ConcurrencyTest
```

Make sure the Spring Boot application is running before executing the test.

---

## Postman Collection

Import `Virality.postman_collection.json` into Postman to get all pre-configured endpoints ready to test immediately.

---

## Project Structure

```
guardrailengine/
├── src/
│   ├── main/java/com/virality/guardrailengine/
│   │   ├── config/          # Redis configuration
│   │   ├── controller/      # REST controllers
│   │   ├── dto/             # Request DTOs
│   │   ├── entity/          # JPA entities
│   │   ├── exception/       # Custom exceptions + global handler
│   │   ├── repository/      # Spring Data JPA repositories
│   │   └── service/         # Business logic + Redis service + Scheduler
│   └── test/                # Concurrency tests
├── docker-compose.yml
├── Virality.postman_collection.json
└── README.md
```
