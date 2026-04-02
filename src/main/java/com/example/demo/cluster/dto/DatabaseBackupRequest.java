package com.example.demo.cluster.dto;

public record DatabaseBackupRequest(
	Boolean enabled,
	String destinationPath,
	String credentialSecret,
	String retentionPolicy,
	String schedule
) {
}
