package com.example.demo.cluster.dto;

public record PostgresqlConfigRequest(
	Boolean walEnabled,
	String walSize,
	String bootstrapDatabase,
	String bootstrapOwner
) {
}
