package com.example.demo.cluster.exception;

public class ClusterDeploymentException extends RuntimeException {

	public ClusterDeploymentException(String message) {
		super(message);
	}

	public ClusterDeploymentException(String message, Throwable cause) {
		super(message, cause);
	}
}
