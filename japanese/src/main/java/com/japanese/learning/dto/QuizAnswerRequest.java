package com.japanese.learning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record QuizAnswerRequest(@NotNull Long itemId, @NotBlank String answer) { }
