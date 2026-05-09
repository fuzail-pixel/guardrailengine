package com.virality.guardrailengine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreatePostRequest {

    @NotBlank(message = "Content cannot be empty")
    private String content;

    // either userId or botId must be provided, not both
    private Long userId;
    private Long botId;
}