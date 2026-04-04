package com.example.demo.cluster.exception;

import java.time.Instant;

import io.fabric8.kubernetes.client.KubernetesClientException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler({ ClusterDeploymentException.class, IllegalArgumentException.class })
	public ResponseEntity<ApiErrorResponse> handleBadRequest(RuntimeException exception) {
		HttpStatus status = exception instanceof ClusterDeploymentException ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_REQUEST;
		return ResponseEntity.status(status)
			.body(new ApiErrorResponse(status.value(), exception.getMessage(), Instant.now()));
	}

	@ExceptionHandler(KubernetesClientException.class)
	public ResponseEntity<ApiErrorResponse> handleKubernetesClientException(KubernetesClientException exception) {
		String message = exception.getMessage();
		Throwable root = exception.getCause();
		if (root != null && root.getMessage() != null && !root.getMessage().isBlank()) {
			message = root.getClass().getSimpleName() + ": " + root.getMessage();
		}
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
			.body(new ApiErrorResponse(HttpStatus.SERVICE_UNAVAILABLE.value(), message, Instant.now()));
	}

	record ApiErrorResponse(int status, String message, Instant timestamp) {
	}
}
