package com.example.demo.cluster.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import com.example.demo.cluster.config.ClusterDeploymentProperties;
import com.example.demo.cluster.domain.enumtype.DatabaseEngine;
import com.example.demo.cluster.dto.ClusterDeploymentRequest;
import com.example.demo.cluster.dto.DatabaseInstanceRequest;
import com.example.demo.cluster.dto.DeploymentSecretsRequest;
import com.example.demo.cluster.exception.ClusterDeploymentException;
import com.example.demo.cluster.model.CommandResult;
import com.example.demo.cluster.model.DeploymentTarget;
import com.example.demo.cluster.model.KubernetesDeploymentResult;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class KubernetesDeploymentService {

	private static final String CNPG_API_VERSION = "postgresql.cnpg.io/v1";
	private static final String CNPG_KIND = "Cluster";
	private static final String PSMDB_API_VERSION = "psmdb.percona.com/v1";
	private static final String PSMDB_KIND = "PerconaServerMongoDB";
	private static final String PXC_API_VERSION = "pxc.percona.com/v1";
	private static final String PXC_KIND = "PerconaXtraDBCluster";
	private static final String REDIS_API_VERSION = "redis.redis.opstreelabs.in/v1beta1";
	private static final String REDIS_KIND = "RedisCluster";
	private static final String K8SSANDRA_API_VERSION = "k8ssandra.io/v1alpha1";
	private static final String K8SSANDRA_KIND = "K8ssandraCluster";

	private final KubernetesClient client;
	private final KubernetesSecretService secretService;
	private final DeploymentNamingService namingService;
	private final HelmValuesService helmValuesService;
	private final HelmCommandService helmCommandService;
	private final DeploymentReadinessService deploymentReadinessService;
	private final ClusterDeploymentProperties properties;

	public KubernetesDeploymentService(
		KubernetesClient client,
		KubernetesSecretService secretService,
		DeploymentNamingService namingService,
		HelmValuesService helmValuesService,
		HelmCommandService helmCommandService,
		DeploymentReadinessService deploymentReadinessService,
		ClusterDeploymentProperties properties
	) {
		this.client = client;
		this.secretService = secretService;
		this.namingService = namingService;
		this.helmValuesService = helmValuesService;
		this.helmCommandService = helmCommandService;
		this.deploymentReadinessService = deploymentReadinessService;
		this.properties = properties;
	}

	public KubernetesDeploymentResult deploy(ClusterDeploymentRequest request) {
		DatabaseInstanceRequest database = requireDatabase(request);
		DeploymentTarget target = namingService.resolve(request);
		Instant startedAt = Instant.now();
		Path overrideValuesFile = null;
		try {
			ensureNamespace(target.namespace());
			validateSecrets(database.engine(), request.secrets());
			secretService.ensureCloudflareSecretIfPresent(target, request.secrets().cloudflareApiToken());
			overrideValuesFile = helmValuesService.renderOverrideValues(request);
			CommandResult commandResult = helmCommandService.upgradeInstall(target.releaseName(), target.namespace(), overrideValuesFile);
			if (!commandResult.successful()) {
				throw new ClusterDeploymentException(
					"Helm install failed for " + target.releaseName() + " in namespace " + target.namespace() + ": "
						+ (StringUtils.hasText(commandResult.stderr()) ? commandResult.stderr() : commandResult.stdout())
				);
			}
			deploymentReadinessService.verifyDeployment(target, database.engine());
			Instant finishedAt = Instant.now();
			return new KubernetesDeploymentResult(
				target.releaseName(),
				target.namespace(),
				helmCommandSummary(target.releaseName(), target.namespace()),
				commandResult.exitCode(),
				commandResult.successful(),
				valuesFileSummary(overrideValuesFile),
				commandResult.stdout(),
				commandResult.stderr(),
				startedAt,
				finishedAt
			);
		}
		catch (RuntimeException exception) {
			throw exception instanceof ClusterDeploymentException clusterException
				? clusterException
				: new ClusterDeploymentException("Failed to apply Kubernetes resources: " + rootCauseMessage(exception), exception);
		}
		finally {
			cleanupTempFile(overrideValuesFile);
		}
	}

	public KubernetesDeploymentResult status(String releaseName, String namespace) {
		DeploymentTarget target = new DeploymentTarget(releaseName, namespace);
		Instant startedAt = Instant.now();
		for (KubernetesResourceDescriptor descriptor : descriptorsForRelease(target)) {
			GenericKubernetesResource resource = getResource(descriptor, target.namespace());
			if (resource == null) {
				continue;
			}
			boolean ready = isReady(resource);
			Instant finishedAt = Instant.now();
			return new KubernetesDeploymentResult(
				target.releaseName(),
				target.namespace(),
				List.of("fabric8", "get", descriptor.kind(), descriptor.resourceName()),
				ready ? 0 : 1,
				ready,
				null,
				describeResource(descriptor, resource),
				"",
				startedAt,
				finishedAt
			);
		}
		throw new ClusterDeploymentException("No Kubernetes deployment resource found for release " + releaseName);
	}

	public KubernetesDeploymentResult uninstall(String releaseName, String namespace) {
		DeploymentTarget target = new DeploymentTarget(releaseName, namespace);
		Instant startedAt = Instant.now();
		try {
			CommandResult commandResult = helmCommandService.uninstall(target.releaseName(), target.namespace());
			Instant finishedAt = Instant.now();
			return new KubernetesDeploymentResult(
				target.releaseName(),
				target.namespace(),
				List.of("helm", "uninstall", target.releaseName(), "-n", target.namespace()),
				commandResult.exitCode(),
				commandResult.successful(),
				null,
				commandResult.stdout(),
				commandResult.stderr(),
				startedAt,
				finishedAt
			);
		}
		catch (RuntimeException exception) {
			throw new ClusterDeploymentException("Failed to delete Kubernetes resources: " + exception.getMessage(), exception);
		}
	}

	private DatabaseInstanceRequest requireDatabase(ClusterDeploymentRequest request) {
		if (request == null || request.database() == null || request.database().engine() == null) {
			throw new ClusterDeploymentException("A single database configuration is required for deployment");
		}
		return request.database();
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
			throw new ClusterDeploymentException("Failed to create namespace " + namespace + ": " + rootCauseMessage(exception), exception);
		}
	}

	private List<KubernetesResourceDescriptor> descriptorsForRelease(DeploymentTarget target) {
		return List.of(
			new KubernetesResourceDescriptor(CNPG_API_VERSION, CNPG_KIND, target.releaseName() + "-postgresql"),
			new KubernetesResourceDescriptor(PSMDB_API_VERSION, PSMDB_KIND, target.releaseName() + "-mongodb"),
			new KubernetesResourceDescriptor(PXC_API_VERSION, PXC_KIND, target.releaseName() + "-mysql"),
			new KubernetesResourceDescriptor(REDIS_API_VERSION, REDIS_KIND, target.releaseName() + "-redis"),
			new KubernetesResourceDescriptor(K8SSANDRA_API_VERSION, K8SSANDRA_KIND, target.releaseName() + "-cassandra")
		);
	}

	private GenericKubernetesResource getResource(KubernetesResourceDescriptor descriptor, String namespace) {
		try {
			return client.genericKubernetesResources(descriptor.apiVersion(), descriptor.kind())
				.inNamespace(namespace)
				.withName(descriptor.resourceName())
				.get();
		}
		catch (RuntimeException exception) {
			return null;
		}
	}

	private boolean isReady(GenericKubernetesResource resource) {
		if (resource == null || resource.getAdditionalProperties() == null) {
			return false;
		}
		Object statusObject = resource.getAdditionalProperties().get("status");
		if (!(statusObject instanceof Map<?, ?> status)) {
			return false;
		}
		Object conditionsObject = status.get("conditions");
		if (!(conditionsObject instanceof List<?> conditions)) {
			return false;
		}
		for (Object conditionObject : conditions) {
			if (!(conditionObject instanceof Map<?, ?> condition)) {
				continue;
			}
			String type = String.valueOf(condition.get("type"));
			String state = String.valueOf(condition.get("status"));
			if ("Ready".equalsIgnoreCase(type) && "True".equalsIgnoreCase(state)) {
				return true;
			}
		}
		return false;
	}

	private String describeResource(KubernetesResourceDescriptor descriptor, GenericKubernetesResource resource) {
		return descriptor.kind() + " " + descriptor.resourceName()
			+ " status="
			+ (isReady(resource) ? "Ready" : "NotReady")
			+ " apiVersion="
			+ descriptor.apiVersion();
	}

	private String rootCauseMessage(Throwable exception) {
		Throwable current = exception;
		String message = null;
		while (current != null) {
			if (StringUtils.hasText(current.getMessage())) {
				message = current.getClass().getSimpleName() + ": " + current.getMessage();
			}
			current = current.getCause();
		}
		return StringUtils.hasText(message) ? message : exception.getClass().getSimpleName();
	}

	private List<String> helmCommandSummary(String releaseName, String namespace) {
		return List.of(
			properties.getHelmExecutable() != null ? properties.getHelmExecutable() : "helm",
			"upgrade",
			"--install",
			releaseName,
			properties.getChartPath(),
			"-n",
			namespace
		);
	}

	private String valuesFileSummary(Path overrideValuesFile) {
		String defaults = properties.getDefaultsFile();
		if (StringUtils.hasText(defaults) && overrideValuesFile != null) {
			return defaults + " + " + overrideValuesFile;
		}
		if (StringUtils.hasText(defaults)) {
			return defaults;
		}
		return overrideValuesFile != null ? overrideValuesFile.toString() : null;
	}

	private void cleanupTempFile(Path overrideValuesFile) {
		if (overrideValuesFile == null) {
			return;
		}
		try {
			Files.deleteIfExists(overrideValuesFile);
		}
		catch (Exception ignored) {
		}
	}

	private void validateSecrets(DatabaseEngine engine, DeploymentSecretsRequest secrets) {
		if (secrets == null) {
			throw new ClusterDeploymentException("Deployment secrets are required");
		}
		switch (engine) {
			case POSTGRESQL -> requireText(secrets.pgPassword(), "pgPassword");
			case MONGODB -> requireText(secrets.mongoPassword(), "mongoPassword");
			case MYSQL -> requireText(secrets.mysqlPassword(), "mysqlPassword");
			case REDIS -> requireText(secrets.redisPassword(), "redisPassword");
			case CASSANDRA -> requireText(secrets.cassandraPassword(), "cassandraPassword");
		}
	}

	private void requireText(String value, String field) {
		if (!StringUtils.hasText(value)) {
			throw new ClusterDeploymentException("Missing required secret: " + field);
		}
	}

	private record KubernetesResourceDescriptor(
		String apiVersion,
		String kind,
		String resourceName
	) {
	}
}
