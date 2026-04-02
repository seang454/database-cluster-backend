package com.example.demo.cluster.dto;

public record ClusterPlatformConfigRequest(
	Boolean cloudflareEnabled,
	String cloudflareZoneName,
	Boolean vaultEnabled
) {
}
