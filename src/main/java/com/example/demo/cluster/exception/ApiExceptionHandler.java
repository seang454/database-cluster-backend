package com.example.demo.cluster.exception;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler({ ClusterDeploymentException.class, IllegalArgumentException.class })
	public ResponseEntity<ApiErrorResponse> handleBadRequest(RuntimeException exception) {
		return ResponseEntity.badRequest()
			.body(new ApiErrorResponse(HttpStatus.BAD_REQUEST.value(), exception.getMessage(), Instant.now()));
	}

	record ApiErrorResponse(int status, String message, Instant timestamp) {
	}
}
