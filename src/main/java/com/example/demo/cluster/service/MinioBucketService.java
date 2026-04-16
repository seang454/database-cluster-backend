package com.example.demo.cluster.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.example.demo.cluster.config.ClusterDeploymentProperties;
import com.example.demo.cluster.exception.ClusterDeploymentException;

import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobStatus;
import io.fabric8.kubernetes.client.KubernetesClient;

import org.springframework.boot.convert.DurationStyle;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MinioBucketService {

	private static final String MINIO_BUCKET_PREFIX = "minio-bucket";
	private static final String MINIO_ALIAS_NAME = "target";

	private final KubernetesClient client;
	private final ClusterDeploymentProperties properties;

	public MinioBucketService(KubernetesClient client, ClusterDeploymentProperties properties) {
		this.client = client;
		this.properties = properties;
	}

	public void ensureNamespaceBucket(String namespace, String releaseName) {
		if (!StringUtils.hasText(namespace)) {
			return;
		}
		String credentialsSecretName = resolveCredentialsSecretName(namespace, releaseName);
		ResolvedMinioCredentials credentials = resolveMinioCredentials(namespace, credentialsSecretName);
		String bucketName = namespace;
		String jobName = bucketJobName(releaseName);
		deleteExistingJob(namespace, jobName);
		createBucketJob(namespace, jobName, bucketName, releaseName, credentials.rootUser(), credentials.rootPassword());
		waitForCompletion(namespace, jobName);
		deleteExistingJob(namespace, jobName);
	}

	private String resolveCredentialsSecretName(String namespace, String releaseName) {
		Duration timeout = StringUtils.hasText(properties.getMinioBucketTimeout())
			? DurationStyle.detectAndParse(properties.getMinioBucketTimeout())
			: Duration.ofMinutes(2);
		Instant deadline = Instant.now().plus(timeout);
		List<String> candidates = List.of(
			scopedSecretName("minio-credentials", releaseName),
			scopedSecretName(properties.getMinioCredentialsSecretPrefix(), releaseName),
			"minio-credentials",
			properties.getMinioCredentialsSecretPrefix()
		);
		while (Instant.now().isBefore(deadline)) {
			for (String candidate : candidates) {
				if (StringUtils.hasText(candidate) && secretExists(namespace, candidate)) {
					return candidate;
				}
			}
			sleepQuietly(2000L);
		}
		throw new ClusterDeploymentException("Unable to find MinIO credentials secret in namespace " + namespace);
	}

	private boolean secretExists(String namespace, String name) {
		Secret secret = client.secrets().inNamespace(namespace).withName(name).get();
		return secret != null;
	}

	private ResolvedMinioCredentials resolveMinioCredentials(String namespace, String secretName) {
		Secret secret = client.secrets().inNamespace(namespace).withName(secretName).get();
		if (secret == null || secret.getData() == null || secret.getData().isEmpty()) {
			throw new ClusterDeploymentException("MinIO credentials secret is empty in namespace " + namespace);
		}
		String rootUser = decodeSecretValue(secret.getData(), properties.getMinioCredentialsAccessKey());
		String rootPassword = decodeSecretValue(secret.getData(), properties.getMinioCredentialsSecretKey());
		if (!StringUtils.hasText(rootUser) || !StringUtils.hasText(rootPassword)) {
			String credentials = decodeSecretValue(secret.getData(), "credentials");
			if (StringUtils.hasText(credentials)) {
				rootUser = parseCredentialsValue(credentials, "aws_access_key_id");
				rootPassword = parseCredentialsValue(credentials, "aws_secret_access_key");
			}
		}
		if (!StringUtils.hasText(rootUser) || !StringUtils.hasText(rootPassword)) {
			throw new ClusterDeploymentException("MinIO credentials are missing in secret " + secretName + " in namespace " + namespace);
		}
		return new ResolvedMinioCredentials(rootUser, rootPassword);
	}

	private void createBucketJob(String namespace, String jobName, String bucketName, String releaseName, String rootUser, String rootPassword) {
		String endpoint = properties.getMinioEndpointUrl();
		if (!StringUtils.hasText(endpoint)) {
			throw new ClusterDeploymentException("MinIO endpoint is not configured");
		}
		String mcImage = "minio/mc:latest";
		String script = String.join("\n",
			"if [ -z \"$MINIO_ROOT_USER\" ] || [ -z \"$MINIO_ROOT_PASSWORD\" ]; then",
			"  echo 'MinIO credentials are missing'",
			"  exit 1",
			"fi",
			"mc alias set " + MINIO_ALIAS_NAME + " \"$MINIO_ENDPOINT\" \"$MINIO_ROOT_USER\" \"$MINIO_ROOT_PASSWORD\"",
			"mc mb --ignore-existing \"" + MINIO_ALIAS_NAME + "/$MINIO_BUCKET\"",
			"if [ -n \"$MINIO_RELEASE_NAME\" ]; then",
			"  MARKER_FILE=/tmp/minio-release-marker",
			"  : > \"$MARKER_FILE\"",
			"  mc cp \"$MARKER_FILE\" \"" + MINIO_ALIAS_NAME + "/$MINIO_BUCKET/$MINIO_RELEASE_NAME/.keep\"",
			"  if [ -n \"$MINIO_DATABASE_FOLDER\" ]; then",
			"    mc cp \"$MARKER_FILE\" \"" + MINIO_ALIAS_NAME + "/$MINIO_BUCKET/$MINIO_RELEASE_NAME/$MINIO_DATABASE_FOLDER/.keep\"",
			"  fi",
			"fi"
		);
		Job job = new JobBuilder()
			.withMetadata(new ObjectMetaBuilder()
				.withName(jobName)
				.withNamespace(namespace)
				.addToLabels("app.kubernetes.io/name", MINIO_BUCKET_PREFIX)
				.addToLabels("app.kubernetes.io/part-of", "db-cluster")
				.build())
			.withNewSpec()
				.withBackoffLimit(0)
				.withNewTemplate()
					.withMetadata(new ObjectMetaBuilder()
						.addToLabels("app.kubernetes.io/name", MINIO_BUCKET_PREFIX)
						.addToLabels("app.kubernetes.io/part-of", "db-cluster")
						.build())
					.withSpec(new PodSpecBuilder()
						.withRestartPolicy("Never")
						.withContainers(List.of(
							new io.fabric8.kubernetes.api.model.ContainerBuilder()
								.withName(MINIO_BUCKET_PREFIX)
								.withImage(mcImage)
								.withCommand("/bin/sh", "-ec")
								.withArgs(script)
								.withEnv(List.of(
									new EnvVarBuilder().withName("MINIO_ENDPOINT").withValue(endpoint).build(),
									new EnvVarBuilder().withName("MINIO_BUCKET").withValue(bucketName).build(),
									new EnvVarBuilder().withName("MINIO_RELEASE_NAME").withValue(StringUtils.hasText(releaseName) ? releaseName : "").build(),
									new EnvVarBuilder().withName("MINIO_DATABASE_FOLDER").withValue(chartFolderName(releaseName)).build(),
									new EnvVarBuilder().withName("MINIO_ROOT_USER").withValue(rootUser).build(),
									new EnvVarBuilder().withName("MINIO_ROOT_PASSWORD").withValue(rootPassword).build()
								))
								.build()
						))
						.build())
					.endTemplate()
				.endSpec()
			.build();
		client.batch().v1().jobs().inNamespace(namespace).resource(job).create();
	}

	private String decodeSecretValue(Map<String, String> data, String key) {
		if (data == null || !StringUtils.hasText(key)) {
			return null;
		}
		String value = data.get(key);
		if (!StringUtils.hasText(value)) {
			return null;
		}
		try {
			return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8).trim();
		}
		catch (IllegalArgumentException exception) {
			return value.trim();
		}
	}

	private String parseCredentialsValue(String credentials, String propertyName) {
		if (!StringUtils.hasText(credentials) || !StringUtils.hasText(propertyName)) {
			return null;
		}
		for (String line : credentials.lines().toList()) {
			String trimmed = line.trim();
			if (trimmed.startsWith(propertyName + " = ")) {
				return trimmed.substring((propertyName + " = ").length()).trim();
			}
		}
		return null;
	}

	private void waitForCompletion(String namespace, String jobName) {
		Duration timeout = StringUtils.hasText(properties.getMinioBucketTimeout())
			? DurationStyle.detectAndParse(properties.getMinioBucketTimeout())
			: Duration.ofMinutes(2);
		Instant deadline = Instant.now().plus(timeout);
		while (Instant.now().isBefore(deadline)) {
			Job job = client.batch().v1().jobs().inNamespace(namespace).withName(jobName).get();
			if (job != null) {
				JobStatus status = job.getStatus();
				Integer succeeded = status != null ? status.getSucceeded() : null;
				if (succeeded != null && succeeded > 0) {
					return;
				}
				Integer failed = status != null ? status.getFailed() : null;
				if (failed != null && failed > 0) {
					throw new ClusterDeploymentException("MinIO bucket creation job failed for namespace " + namespace);
				}
			}
			sleepQuietly(2000L);
		}
		throw new ClusterDeploymentException("Timed out waiting for MinIO bucket creation in namespace " + namespace);
	}

	private void deleteExistingJob(String namespace, String jobName) {
		try {
			client.batch().v1().jobs().inNamespace(namespace).withName(jobName).delete();
		}
		catch (RuntimeException ignored) {
		}
	}

	private String bucketJobName(String releaseName) {
		String base = MINIO_BUCKET_PREFIX + "-" + (StringUtils.hasText(releaseName) ? releaseName : "release");
		String sanitized = base.replaceAll("[^a-z0-9-]+", "-").replaceAll("-{2,}", "-").replaceAll("^-+", "").replaceAll("-+$", "");
		if (sanitized.length() > 52) {
			sanitized = sanitized.substring(0, 52).replaceAll("-+$", "");
		}
		return sanitized;
	}

	private String scopedSecretName(String baseName, String releaseName) {
		if (!StringUtils.hasText(baseName)) {
			return StringUtils.hasText(releaseName) ? releaseName : "";
		}
		return StringUtils.hasText(releaseName) ? baseName + "-" + releaseName : baseName;
	}

	private String chartFolderName(String releaseName) {
		if (!StringUtils.hasText(releaseName)) {
			return "";
		}
		return releaseName + "-postgresql";
	}

	private void sleepQuietly(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new ClusterDeploymentException("Interrupted while waiting for MinIO credentials", exception);
		}
	}

	private record ResolvedMinioCredentials(String rootUser, String rootPassword) {
	}
}
