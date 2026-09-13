package com.japanese.learning.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class QuizRequestException extends RuntimeException {
    public QuizRequestException(String message) { super(message); }
}
