package com.example.demo.cluster.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.example.demo.cluster.config.ClusterDeploymentProperties;
import com.example.demo.cluster.dto.ClusterDeploymentRequest;
import com.example.demo.cluster.dto.DeploymentSecretsRequest;
import com.example.demo.cluster.domain.enumtype.DatabaseEngine;
import com.example.demo.cluster.exception.ClusterDeploymentException;
import com.example.demo.cluster.model.DeploymentTarget;
import com.example.demo.cluster.model.HelmReleaseResult;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class HelmReleaseService {

	private final ClusterDeploymentProperties properties;
	private final DeploymentNamingService namingService;
	private final HelmValuesService valuesService;

	public HelmReleaseService(
		ClusterDeploymentProperties properties,
		DeploymentNamingService namingService,
		HelmValuesService valuesService
	) {
		this.properties = properties;
		this.namingService = namingService;
		this.valuesService = valuesService;
	}

	public HelmReleaseResult deploy(ClusterDeploymentRequest request) {
		if (request == null || request.database() == null || request.database().engine() == null) {
			throw new ClusterDeploymentException("A single database configuration is required for deployment");
		}
		DeploymentTarget target = namingService.resolve(request);
		Path valuesFile = valuesService.writeValuesFile(request, target);
		DatabaseEngine engine = request.database().engine();
		try {
			return runHelm(buildDeployCommand(target, engine, request.secrets(), valuesFile), target, valuesFile);
		}
		finally {
			deleteQuietly(valuesFile);
		}
	}

	public HelmReleaseResult status(String releaseName, String namespace) {
		DeploymentTarget target = new DeploymentTarget(releaseName, namespace);
		return runHelm(List.of(
			properties.getHelmExecutable(),
			"status",
			target.releaseName(),
			"-n",
			target.namespace()
		), target, null);
	}

	public HelmReleaseResult uninstall(String releaseName, String namespace) {
		DeploymentTarget target = new DeploymentTarget(releaseName, namespace);
		return runHelm(List.of(
			properties.getHelmExecutable(),
			"uninstall",
			target.releaseName(),
			"-n",
			target.namespace()
		), target, null);
	}

	private List<String> buildDeployCommand(
		DeploymentTarget target,
		DatabaseEngine engine,
		DeploymentSecretsRequest secrets,
		Path valuesFile
	) {
		validateConfig();
		validateCommandContext(target, valuesFile);
		validateSecrets(secrets);

		List<String> command = new ArrayList<>();
		command.add(properties.getHelmExecutable());
		command.add("upgrade");
		command.add("--install");
		command.add(target.releaseName());
		command.add(properties.getChartReference());
		if (StringUtils.hasText(properties.getChartVersion())) {
			command.add("--version");
			command.add(properties.getChartVersion());
		}
		command.add("--namespace");
		command.add(target.namespace());
		command.add("--create-namespace");
		command.add("-f");
		command.add(properties.getDefaultValuesFile().toString());
		command.add("-f");
		command.add(valuesFile.toString());
		command.add("--set");
		command.add("externalSecrets.enabled=false");
		command.add("--set");
		command.add("postgresql.operator.enabled=false");
		command.add("--set");
		command.add("mongodb.operator.enabled=false");
		command.add("--set");
		command.add("mysql.operator.enabled=false");
		command.add("--set");
		command.add("redis.operator.enabled=false");
		command.add("--set");
		command.add("cassandra.operator.enabled=false");
		command.add("--set");
		command.add("vaultTransit.enabled=false");

		addDatabaseSecrets(command, engine, secrets);
		addOptionalCloudflareSecret(command, secrets);
		command.add("--timeout");
		command.add("10m");
		return command;
	}

	private HelmReleaseResult runHelm(List<String> command, DeploymentTarget target, Path valuesFile) {
		ProcessBuilder builder = new ProcessBuilder(command);
		builder.redirectErrorStream(true);
		if (properties.getDefaultValuesFile() != null && properties.getDefaultValuesFile().getParent() != null) {
			builder.directory(properties.getDefaultValuesFile().getParent().toFile());
		}

		Instant startedAt = Instant.now();
		try {
			Process process = builder.start();
			String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			int exitCode = process.waitFor();
			Instant finishedAt = Instant.now();
			return new HelmReleaseResult(
				target.releaseName(),
				target.namespace(),
				List.copyOf(command),
				exitCode,
				exitCode == 0,
				valuesFile != null ? valuesFile.toString() : null,
				stdout,
				"",
				startedAt,
				finishedAt
			);
		}
		catch (IOException exception) {
			throw new ClusterDeploymentException("Failed to execute helm command", exception);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new ClusterDeploymentException("Helm command execution was interrupted", exception);
		}
	}

	private void validateConfig() {
		if (!StringUtils.hasText(properties.getChartReference())) {
			throw new ClusterDeploymentException("cluster.deployment.chart-reference is missing or invalid");
		}
		if (properties.getDefaultValuesFile() == null || !Files.exists(properties.getDefaultValuesFile())) {
			throw new ClusterDeploymentException("cluster.deployment.default-values-file is missing or invalid");
		}
	}

	private void validateSecrets(DeploymentSecretsRequest secrets) {
		if (secrets == null) {
			throw new ClusterDeploymentException("Deployment secrets are required");
		}
	}

	private void validateSecret(String value, String fieldName, DatabaseEngine engine) {
		if (!StringUtils.hasText(value)) {
			throw new ClusterDeploymentException("Missing required secret '" + fieldName + "' for " + engine.name().toLowerCase());
		}
	}

	private void addDatabaseSecrets(List<String> command, DatabaseEngine engine, DeploymentSecretsRequest secrets) {
		switch (engine) {
			case POSTGRESQL -> {
				validateSecret(secrets.pgPassword(), "pgPassword", engine);
				addSecret(command, "vault.postgresql.superuserPassword", secrets.pgPassword());
				addSecret(command, "vault.postgresql.appPassword", secrets.pgPassword());
			}
			case MONGODB -> {
				validateSecret(secrets.mongoPassword(), "mongoPassword", engine);
				addSecret(command, "vault.mongodb.clusterAdminPassword", secrets.mongoPassword());
				addSecret(command, "vault.mongodb.userAdminPassword", secrets.mongoPassword());
				addSecret(command, "vault.mongodb.clusterMonitorPassword", secrets.mongoPassword());
				addSecret(command, "vault.mongodb.databaseAdminPassword", secrets.mongoPassword());
				addSecret(command, "vault.mongodb.backupPassword", secrets.mongoPassword());
				addSecret(command, "vault.mongodb.replicationKey", secrets.mongoPassword());
			}
			case MYSQL -> {
				validateSecret(secrets.mysqlPassword(), "mysqlPassword", engine);
				addSecret(command, "vault.mysql.rootPassword", secrets.mysqlPassword());
				addSecret(command, "vault.mysql.appPassword", secrets.mysqlPassword());
				addSecret(command, "vault.mysql.replicationPassword", secrets.mysqlPassword());
				addSecret(command, "vault.mysql.monitorPassword", secrets.mysqlPassword());
				addSecret(command, "vault.mysql.clusterCheckPassword", secrets.mysqlPassword());
			}
			case REDIS -> {
				validateSecret(secrets.redisPassword(), "redisPassword", engine);
				addSecret(command, "vault.redis.password", secrets.redisPassword());
				addSecret(command, "redis.auth.password", secrets.redisPassword());
			}
			case CASSANDRA -> {
				validateSecret(secrets.cassandraPassword(), "cassandraPassword", engine);
				addSecret(command, "vault.cassandra.password", secrets.cassandraPassword());
			}
		}
	}

	private void addOptionalCloudflareSecret(List<String> command, DeploymentSecretsRequest secrets) {
		if (!StringUtils.hasText(secrets.cloudflareApiToken())) {
			return;
		}
		addSecret(command, "cloudflare.apiToken", secrets.cloudflareApiToken());
	}

	private void validateCommandContext(DeploymentTarget target, Path valuesFile) {
		if (!StringUtils.hasText(target.releaseName()) || !StringUtils.hasText(target.namespace()) || valuesFile == null) {
			throw new ClusterDeploymentException("Invalid deployment request");
		}
	}

	private void addSecret(List<String> command, String key, String value) {
		command.add("--set");
		command.add(key + "=" + value);
	}

	private void deleteQuietly(Path path) {
		if (path == null) {
			return;
		}
		try {
			Files.deleteIfExists(path);
		}
		catch (IOException ignored) {
		}
	}
}
