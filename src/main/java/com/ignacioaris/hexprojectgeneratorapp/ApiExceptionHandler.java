package com.ignacioaris.hexprojectgeneratorapp;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApiValidationException.class)
    public ResponseEntity<Map<String, Object>> validation(ApiValidationException exception) {
        return ResponseEntity.badRequest().body(Map.of(
                "message", "Validation failed",
                "errors", exception.errors()
        ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadableJson() {
        return ResponseEntity.badRequest().body(Map.of(
                "message", "Invalid JSON",
                "errors", List.of("Request body could not be parsed.")
        ));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, Object>> io(IOException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "message", "Generation failed",
                "errors", List.of(exception.getMessage())
        ));
    }
}
