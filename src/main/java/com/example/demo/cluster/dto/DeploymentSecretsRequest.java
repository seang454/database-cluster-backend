package com.example.demo.cluster.dto;

public record DeploymentSecretsRequest(
	String pgPassword,
	String mongoPassword,
	String mysqlPassword,
	String redisPassword,
	String cassandraPassword,
	String cloudflareApiToken
) {
}
