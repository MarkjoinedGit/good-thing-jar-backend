package com.goodthingjar.dto;

public record ValidationErrorDetail(
    String field,
    String message
) {
}

