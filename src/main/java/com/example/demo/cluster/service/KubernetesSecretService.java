package com.example.demo.cluster.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import com.example.demo.cluster.exception.ClusterDeploymentException;
import com.example.demo.cluster.model.DeploymentTarget;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class KubernetesSecretService {

	private static final String CLOUDFLARE_SECRET_NAME = "cloudflare-api-token";

	private final KubernetesClient client;

	public KubernetesSecretService(KubernetesClient client) {
		this.client = client;
	}

	public void ensureCloudflareSecretIfPresent(DeploymentTarget target, String apiToken) {
		if (!StringUtils.hasText(apiToken)) {
			return;
		}
		ensureNamespace(target.namespace());
		Secret secret = new SecretBuilder()
			.withNewMetadata()
			.withName(CLOUDFLARE_SECRET_NAME)
			.withNamespace(target.namespace())
			.endMetadata()
			.withType("Opaque")
			.withData(Map.of("token", Base64.getEncoder().encodeToString(apiToken.getBytes(StandardCharsets.UTF_8))))
			.build();
		try {
			client.secrets().inNamespace(target.namespace()).resource(secret).serverSideApply();
		}
		catch (RuntimeException exception) {
			throw new ClusterDeploymentException("Failed to apply Cloudflare secret", exception);
		}
	}

	private void ensureNamespace(String namespace) {
		try {
			client.resource(new NamespaceBuilder()
				.withNewMetadata()
				.withName(namespace)
				.endMetadata()
				.build()).serverSideApply();
		}
		catch (RuntimeException exception) {
			throw new ClusterDeploymentException("Failed to create namespace " + namespace, exception);
		}
	}
}
