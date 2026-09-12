package com.japanese.learning.dto;
import com.japanese.content.entity.ContentType;
public record WeakContent(String slug, ContentType type, String title, long attempts, long incorrectAnswers) { }
