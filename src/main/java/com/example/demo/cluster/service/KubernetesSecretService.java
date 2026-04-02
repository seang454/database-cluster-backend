package com.example.demo.cluster.service;

import java.util.List;

import com.example.demo.cluster.config.ClusterOperationsProperties;
import com.example.demo.cluster.exception.ClusterDeploymentException;
import com.example.demo.cluster.model.CommandResult;
import com.example.demo.cluster.model.DeploymentTarget;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class KubernetesSecretService {

	private final ClusterOperationsProperties properties;
	private final CommandRunnerService commandRunnerService;

	public KubernetesSecretService(
		ClusterOperationsProperties properties,
		CommandRunnerService commandRunnerService
	) {
		this.properties = properties;
		this.commandRunnerService = commandRunnerService;
	}

	public void ensureCloudflareSecretIfPresent(DeploymentTarget target, String apiToken) {
		if (!StringUtils.hasText(apiToken)) {
			return;
		}
		runRequired(List.of(
			properties.getKubectlExecutable(),
			"create",
			"namespace",
			target.namespace()
		), "Failed to create namespace " + target.namespace());

		CommandResult manifest = commandRunnerService.run(List.of(
			properties.getKubectlExecutable(),
			"create",
			"secret",
			"generic",
			"cloudflare-api-token",
			"--from-literal=token=" + apiToken,
			"--namespace",
			target.namespace(),
			"--dry-run=client",
			"-o",
			"yaml"
		));
		if (!manifest.successful()) {
			throw new ClusterDeploymentException("Failed to build Cloudflare secret manifest: " + manifest.stderr());
		}

		CommandResult apply = commandRunnerService.run(
			List.of(properties.getKubectlExecutable(), "apply", "-f", "-"),
			null,
			manifest.stdout()
		);
		if (!apply.successful()) {
			throw new ClusterDeploymentException("Failed to apply Cloudflare secret: " + apply.stderr());
		}
	}

	private void runRequired(List<String> command, String message) {
		CommandResult result = commandRunnerService.run(command);
		if (!result.successful() && !alreadyExists(result.stderr())) {
			throw new ClusterDeploymentException(message + ": " + result.stderr());
		}
	}

	private boolean alreadyExists(String stderr) {
		return stderr != null && stderr.contains("AlreadyExists");
	}
}
