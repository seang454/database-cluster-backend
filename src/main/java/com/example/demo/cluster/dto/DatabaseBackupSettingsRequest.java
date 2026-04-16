package com.example.demo.cluster.dto;

public record DatabaseBackupSettingsRequest(
	Boolean enabled,
	String destinationPath,
	String credentialSecret,
	String retentionPolicy,
	String schedule
) {
}
