package com.virality.guardrailengine.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LikeRequest {

    @NotNull(message = "User ID cannot be null")
    private Long userId;
}