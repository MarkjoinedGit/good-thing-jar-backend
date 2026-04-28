package com.goodthingjar.exception;

import com.goodthingjar.dto.ApiError;
import com.goodthingjar.dto.ApiResponse;
import com.goodthingjar.dto.ValidationErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final String REQUEST_ID_HEADER = "X-Request-Id";

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
		MethodArgumentNotValidException ex,
		HttpServletRequest request
	) {
		List<ValidationErrorDetail> details = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(this::toFieldValidationDetail)
			.toList();

		return buildErrorResponse(
			HttpStatus.BAD_REQUEST,
			"VALIDATION_ERROR",
			"Request validation failed",
			details,
			request
		);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
		ConstraintViolationException ex,
		HttpServletRequest request
	) {
		List<String> details = ex.getConstraintViolations()
			.stream()
			.map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
			.toList();

		return buildErrorResponse(
			HttpStatus.BAD_REQUEST,
			"VALIDATION_ERROR",
			"Request validation failed",
			details,
			request
		);
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ApiResponse<Void>> handleHandlerMethodValidation(
		HandlerMethodValidationException ex,
		HttpServletRequest request
	) {
		List<String> details = ex.getAllErrors()
			.stream()
			.map(error -> error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage())
			.toList();

		return buildErrorResponse(
			HttpStatus.BAD_REQUEST,
			"VALIDATION_ERROR",
			"Request validation failed",
			details,
			request
		);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(
		IllegalArgumentException ex,
		HttpServletRequest request
	) {
		return buildErrorResponse(
			HttpStatus.BAD_REQUEST,
			"VALIDATION_ERROR",
			ex.getMessage(),
			null,
			request
		);
	}

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ApiResponse<Void>> handleAuthentication(
		AuthenticationException ex,
		HttpServletRequest request
	) {
		return buildErrorResponse(
			HttpStatus.UNAUTHORIZED,
			"UNAUTHORIZED",
			"Authentication is required to access this resource",
			null,
			request
		);
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiResponse<Void>> handleAccessDenied(
		AccessDeniedException ex,
		HttpServletRequest request
	) {
		return buildErrorResponse(
			HttpStatus.FORBIDDEN,
			"FORBIDDEN",
			"You are not allowed to access this resource",
			null,
			request
		);
	}

	@ExceptionHandler(PairingBusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handlePairingBusinessException(
		PairingBusinessException ex,
		HttpServletRequest request
	) {
		return buildErrorResponse(
			ex.getStatus(),
			ex.getErrorCode(),
			ex.getMessage(),
			null,
			request
		);
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ApiResponse<Void>> handleResponseStatus(
		ResponseStatusException ex,
		HttpServletRequest request
	) {
		HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
		String errorCode = status == HttpStatus.NOT_FOUND ? "NOT_FOUND" : status.name();
		String message = ex.getReason() != null && !ex.getReason().isBlank()
			? ex.getReason()
			: "Request failed";

		return buildErrorResponse(status, errorCode, message, null, request);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleGenericException(
		Exception ex,
		HttpServletRequest request
	) {
		return buildErrorResponse(
			HttpStatus.INTERNAL_SERVER_ERROR,
			"INTERNAL_SERVER_ERROR",
			"An unexpected error occurred",
			null,
			request
		);
	}

	private ResponseEntity<ApiResponse<Void>> buildErrorResponse(
		HttpStatus status,
		String code,
		String message,
		Object details,
		HttpServletRequest request
	) {
		ApiError error = new ApiError(code, message, details);
		ApiResponse<Void> response = new ApiResponse<>(
			false,
			null,
			error,
			Instant.now(),
			resolveRequestId(request)
		);
		return ResponseEntity.status(status).body(response);
	}

	private ValidationErrorDetail toFieldValidationDetail(FieldError fieldError) {
		String message = fieldError.getDefaultMessage() != null
			? fieldError.getDefaultMessage()
			: "Invalid value";
		return new ValidationErrorDetail(fieldError.getField(), message);
	}

	private String resolveRequestId(HttpServletRequest request) {
		String requestId = request.getHeader(REQUEST_ID_HEADER);
		if (requestId == null || requestId.isBlank()) {
			return UUID.randomUUID().toString();
		}
		return requestId;
	}
}
