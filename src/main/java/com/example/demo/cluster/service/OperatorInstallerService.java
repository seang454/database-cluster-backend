package com.example.demo.cluster.service;

import java.util.List;

import com.example.demo.cluster.config.ClusterDeploymentProperties;
import com.example.demo.cluster.config.ClusterOperationsProperties;
import com.example.demo.cluster.domain.enumtype.DatabaseEngine;
import com.example.demo.cluster.dto.ClusterDeploymentRequest;
import com.example.demo.cluster.exception.ClusterDeploymentException;
import com.example.demo.cluster.model.CommandResult;
import com.example.demo.cluster.model.DeploymentTarget;

import org.springframework.stereotype.Service;

@Service
public class OperatorInstallerService {

	private final ClusterOperationsProperties properties;
	private final ClusterDeploymentProperties deploymentProperties;
	private final CommandRunnerService commandRunnerService;
	private final KubernetesSecretService kubernetesSecretService;

	public OperatorInstallerService(
		ClusterOperationsProperties properties,
		ClusterDeploymentProperties deploymentProperties,
		CommandRunnerService commandRunnerService,
		KubernetesSecretService kubernetesSecretService
	) {
		this.properties = properties;
		this.deploymentProperties = deploymentProperties;
		this.commandRunnerService = commandRunnerService;
		this.kubernetesSecretService = kubernetesSecretService;
	}

	public void prepareForDeployment(ClusterDeploymentRequest request, DeploymentTarget target) {
		if (request == null || request.database() == null || request.database().engine() == null) {
			throw new ClusterDeploymentException("A single database configuration is required");
		}

		ensureHelmRepos();
		kubernetesSecretService.ensureCloudflareSecretIfPresent(target, request.secrets() != null ? request.secrets().cloudflareApiToken() : null);
		cleanupStaleMongodbBootstrapIfNeeded(target, request.database().engine());
		installExternalSecretsOperator();
		installOperatorForEngine(request.database().engine(), target.namespace());
	}

	private void ensureHelmRepos() {
		runRequired(List.of(deploymentProperties.getHelmExecutable(), "repo", "add", "cnpg", "https://cloudnative-pg.github.io/charts"), true);
		runRequired(List.of(deploymentProperties.getHelmExecutable(), "repo", "add", "percona", "https://percona.github.io/percona-helm-charts/"), true);
		runRequired(List.of(deploymentProperties.getHelmExecutable(), "repo", "add", "ot-helm", "https://ot-container-kit.github.io/helm-charts/"), true);
		runRequired(List.of(deploymentProperties.getHelmExecutable(), "repo", "add", "k8ssandra", "https://helm.k8ssandra.io/stable"), true);
		runRequired(List.of(deploymentProperties.getHelmExecutable(), "repo", "add", "jetstack", "https://charts.jetstack.io"), true);
		runRequired(List.of(deploymentProperties.getHelmExecutable(), "repo", "add", "ext-secrets", "https://charts.external-secrets.io"), true);
		runRequired(List.of(deploymentProperties.getHelmExecutable(), "repo", "update"), false);
	}

	private void installExternalSecretsOperator() {
		runRequired(List.of(
			deploymentProperties.getHelmExecutable(),
			"upgrade",
			"--install",
			properties.getExternalSecretsReleaseName(),
			properties.getExternalSecretsChart(),
			"--namespace",
			properties.getExternalSecretsNamespace(),
			"--create-namespace",
			"--set",
			"installCRDs=true",
			"--wait",
			"--timeout",
			"5m"
		), false);
	}

	private void installOperatorForEngine(DatabaseEngine engine, String namespace) {
		switch (engine) {
			case POSTGRESQL -> runRequired(List.of(
				deploymentProperties.getHelmExecutable(),
				"upgrade",
				"--install",
				properties.getCnpgReleaseName(),
				properties.getCnpgChart(),
				"--namespace",
				properties.getCnpgNamespace(),
				"--create-namespace",
				"--version",
				properties.getCnpgVersion(),
				"--wait",
				"--timeout",
				"5m"
			), false);
			case MONGODB -> runRequired(List.of(
				deploymentProperties.getHelmExecutable(),
				"upgrade",
				"--install",
				properties.getPsmdbReleaseName(),
				properties.getPsmdbChart(),
				"--namespace",
				namespace,
				"--create-namespace",
				"--version",
				properties.getPsmdbVersion(),
				"--wait",
				"--timeout",
				"5m"
			), false);
			case MYSQL -> runRequired(List.of(
				deploymentProperties.getHelmExecutable(),
				"upgrade",
				"--install",
				properties.getPxcReleaseName(),
				properties.getPxcChart(),
				"--namespace",
				namespace,
				"--create-namespace",
				"--version",
				properties.getPxcVersion(),
				"--wait",
				"--timeout",
				"5m"
			), false);
			case REDIS -> {
				cleanupPendingRelease(properties.getRedisOperatorReleaseName(), namespace);
				runRequired(List.of(
					deploymentProperties.getHelmExecutable(),
					"upgrade",
					"--install",
					properties.getRedisOperatorReleaseName(),
					properties.getRedisOperatorChart(),
					"--namespace",
					namespace,
					"--create-namespace",
					"--version",
					properties.getRedisOperatorVersion(),
					"--set",
					"featureGates.GenerateConfigInInitContainer=true",
					"--wait",
					"--timeout",
					properties.getRedisOperatorTimeout()
				), false);
			}
			case CASSANDRA -> {
				ensureCertManager();
				runRequired(List.of(
					deploymentProperties.getHelmExecutable(),
					"upgrade",
					"--install",
					properties.getK8ssandraReleaseName(),
					properties.getK8ssandraChart(),
					"--namespace",
					namespace,
					"--create-namespace",
					"--version",
					properties.getK8ssandraVersion(),
					"--wait",
					"--timeout",
					"5m"
				), false);
			}
		}
	}

	private void ensureCertManager() {
		CommandResult crdCheck = commandRunnerService.run(List.of(
			properties.getKubectlExecutable(),
			"get",
			"crd",
			"certificates.cert-manager.io"
		));
		if (crdCheck.successful()) {
			return;
		}
		runRequired(List.of(
			deploymentProperties.getHelmExecutable(),
			"upgrade",
			"--install",
			properties.getCertManagerReleaseName(),
			properties.getCertManagerChart(),
			"--namespace",
			properties.getCertManagerNamespace(),
			"--create-namespace",
			"--version",
			properties.getCertManagerVersion(),
			"--set",
			"crds.enabled=true",
			"--wait",
			"--timeout",
			"10m"
		), false);
	}

	private void cleanupPendingRelease(String releaseName, String namespace) {
		CommandResult status = commandRunnerService.run(List.of(
			deploymentProperties.getHelmExecutable(),
			"status",
			releaseName,
			"-n",
			namespace
		));
		String text = status.stdout() + status.stderr();
		if (text.contains("pending-install") || text.contains("pending-upgrade") || text.contains("pending-rollback")) {
			commandRunnerService.run(List.of(deploymentProperties.getHelmExecutable(), "uninstall", releaseName, "-n", namespace));
		}
	}

	private void cleanupStaleMongodbBootstrapIfNeeded(DeploymentTarget target, DatabaseEngine engine) {
		if (engine != DatabaseEngine.MONGODB) {
			return;
		}
		String release = target.releaseName();
		String namespace = target.namespace();
		CommandResult clusterCheck = commandRunnerService.run(List.of(
			properties.getKubectlExecutable(),
			"get",
			"psmdb",
			release + "-mongodb",
			"-n",
			namespace
		));
		if (clusterCheck.successful()) {
			return;
		}
		deleteIfPresent("secret", "internal-" + release + "-mongodb-users", namespace);
		deleteIfPresent("secret", release + "-mongodb-credentials", namespace);
	}

	private void deleteIfPresent(String kind, String name, String namespace) {
		commandRunnerService.run(List.of(
			properties.getKubectlExecutable(),
			"delete",
			kind,
			name,
			"-n",
			namespace,
			"--ignore-not-found"
		));
	}

	private void runRequired(List<String> command, boolean tolerateExists) {
		CommandResult result = commandRunnerService.run(command);
		if (result.successful()) {
			return;
		}
		if (tolerateExists && (result.stderr().contains("already exists") || result.stderr().contains("exists"))) {
			return;
		}
		throw new ClusterDeploymentException("Command failed: " + String.join(" ", command) + System.lineSeparator() + result.stderr());
	}
}
