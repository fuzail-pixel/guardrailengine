package com.virality.guardrailengine.controller;

import com.virality.guardrailengine.entity.Bot;
import com.virality.guardrailengine.repository.BotRepository;
import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bots")
@RequiredArgsConstructor
public class BotController {

    private final BotRepository botRepository;

    @PostMapping
    public ResponseEntity<Bot> createBot(@RequestBody Bot bot) {
        return ResponseEntity.status(HttpStatus.CREATED).body(botRepository.save(bot));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Bot> getBot(@PathVariable Long id) {
        return botRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    @GetMapping
public ResponseEntity<List<Bot>> getAllBots() {
    return ResponseEntity.ok(botRepository.findAll());
}
}