package com.virality.guardrailengine.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateCommentRequest {

    @NotBlank(message = "Content cannot be empty")
    private String content;

    // either userId or botId must be provided, not both
    private Long userId;
    private Long botId;

    private int depthLevel = 0;
}