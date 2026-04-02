package com.example.demo.cluster.service;

import java.util.List;

import com.example.demo.cluster.config.ClusterOperationsProperties;
import com.example.demo.cluster.domain.enumtype.DatabaseEngine;
import com.example.demo.cluster.exception.ClusterDeploymentException;
import com.example.demo.cluster.model.CommandResult;
import com.example.demo.cluster.model.DeploymentTarget;

import org.springframework.stereotype.Service;

@Service
public class DeploymentReadinessService {

	private final ClusterOperationsProperties properties;
	private final CommandRunnerService commandRunnerService;

	public DeploymentReadinessService(
		ClusterOperationsProperties properties,
		CommandRunnerService commandRunnerService
	) {
		this.properties = properties;
		this.commandRunnerService = commandRunnerService;
	}

	public void verifyDeployment(DeploymentTarget target, DatabaseEngine engine) {
		waitForClusterSecretStore();
		switch (engine) {
			case POSTGRESQL -> verifyPostgresql(target);
			case MONGODB -> verifyMongodb(target);
			case MYSQL -> verifyMysql(target);
			case REDIS -> verifyRedis(target);
			case CASSANDRA -> verifyCassandra(target);
		}
	}

	private void waitForClusterSecretStore() {
		runRequired(List.of(
			properties.getKubectlExecutable(),
			"wait",
			"--for=condition=Ready",
			"clustersecretstore/vault-backend",
			"--timeout=180s"
		), "Vault ClusterSecretStore did not become Ready");
	}

	private void verifyPostgresql(DeploymentTarget target) {
		waitExternalSecret(target.namespace(), target.releaseName() + "-postgresql-credentials", "PostgreSQL superuser ExternalSecret did not become Ready");
		waitExternalSecret(target.namespace(), target.releaseName() + "-postgresql-app", "PostgreSQL app ExternalSecret did not become Ready");
		waitForSecret(target.namespace(), target.releaseName() + "-postgresql-credentials", "PostgreSQL superuser secret was not created");
		waitForSecret(target.namespace(), target.releaseName() + "-postgresql-app", "PostgreSQL app secret was not created");
		waitForPodsReady(target.namespace(), "cnpg.io/cluster=" + target.releaseName() + "-postgresql", "PostgreSQL pods");
	}

	private void verifyMongodb(DeploymentTarget target) {
		waitExternalSecret(target.namespace(), target.releaseName() + "-mongodb-credentials", "MongoDB ExternalSecret did not become Ready");
		waitForSecret(target.namespace(), target.releaseName() + "-mongodb-credentials", "MongoDB credentials secret was not created");
		waitForPodsReady(target.namespace(), "app.kubernetes.io/instance=" + target.releaseName() + "-mongodb,app.kubernetes.io/component=mongod", "MongoDB pods");
	}

	private void verifyMysql(DeploymentTarget target) {
		waitExternalSecret(target.namespace(), target.releaseName() + "-mysql-credentials", "MySQL ExternalSecret did not become Ready");
		waitForSecret(target.namespace(), target.releaseName() + "-mysql-credentials", "MySQL credentials secret was not created");
	}

	private void verifyRedis(DeploymentTarget target) {
		waitExternalSecret(target.namespace(), target.releaseName() + "-redis-credentials", "Redis ExternalSecret did not become Ready");
		waitForSecret(target.namespace(), target.releaseName() + "-redis-credentials", "Redis credentials secret was not created");
	}

	private void verifyCassandra(DeploymentTarget target) {
		waitExternalSecret(target.namespace(), target.releaseName() + "-cassandra-credentials", "Cassandra ExternalSecret did not become Ready");
		waitForSecret(target.namespace(), target.releaseName() + "-cassandra-credentials", "Cassandra credentials secret was not created");
	}

	private void waitExternalSecret(String namespace, String name, String failureMessage) {
		runRequired(List.of(
			properties.getKubectlExecutable(),
			"wait",
			"--for=condition=Ready",
			"externalsecret/" + name,
			"-n",
			namespace,
			"--timeout=180s"
		), failureMessage);
	}

	private void waitForSecret(String namespace, String name, String failureMessage) {
		for (int attempt = 0; attempt < 18; attempt++) {
			CommandResult result = commandRunnerService.run(List.of(
				properties.getKubectlExecutable(),
				"get",
				"secret",
				name,
				"-n",
				namespace
			));
			if (result.successful()) {
				return;
			}
			sleepSeconds(10);
		}
		throw new ClusterDeploymentException(failureMessage);
	}

	private void waitForPodsReady(String namespace, String selector, String label) {
		for (int attempt = 0; attempt < 60; attempt++) {
			CommandResult total = commandRunnerService.run(List.of(
				properties.getKubectlExecutable(),
				"get",
				"pods",
				"-n",
				namespace,
				"-l",
				selector,
				"--no-headers"
			));
			if (!total.successful()) {
				sleepSeconds(10);
				continue;
			}
			String[] lines = total.stdout().lines().filter(line -> !line.isBlank()).toArray(String[]::new);
			if (lines.length == 0) {
				sleepSeconds(10);
				continue;
			}
			boolean ready = true;
			for (String line : lines) {
				String[] parts = line.trim().split("\\s+");
				if (parts.length < 2) {
					ready = false;
					break;
				}
				String[] ratio = parts[1].split("/");
				if (ratio.length != 2 || !ratio[0].equals(ratio[1])) {
					ready = false;
					break;
				}
			}
			if (ready) {
				return;
			}
			sleepSeconds(10);
		}
		throw new ClusterDeploymentException(label + " did not become Ready");
	}

	private void runRequired(List<String> command, String failureMessage) {
		CommandResult result = commandRunnerService.run(command);
		if (!result.successful()) {
			throw new ClusterDeploymentException(failureMessage + System.lineSeparator() + result.stderr());
		}
	}

	private void sleepSeconds(long seconds) {
		try {
			Thread.sleep(seconds * 1000L);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new ClusterDeploymentException("Readiness check was interrupted", exception);
		}
	}
}
