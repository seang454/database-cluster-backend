package com.example.demo.cluster.service;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import com.example.demo.cluster.config.ClusterDeploymentProperties;
import com.example.demo.cluster.dto.ClusterDeploymentRequest;
import com.example.demo.cluster.dto.DatabaseInstanceRequest;
import com.example.demo.cluster.dto.DeploymentSecretsRequest;
import com.example.demo.cluster.domain.DatabaseBackup;
import com.example.demo.cluster.domain.enumtype.DatabaseEngine;
import com.example.demo.cluster.exception.ClusterDeploymentException;
import com.example.demo.cluster.model.CommandResult;
import com.example.demo.cluster.model.DeploymentTarget;
import com.example.demo.cluster.model.KubernetesDeploymentResult;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class KubernetesDeploymentService {

	private static final Logger log = LoggerFactory.getLogger(KubernetesDeploymentService.class);

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
	private final DeploymentNamingService namingService;
	private final HelmValuesService helmValuesService;
	private final HelmCommandService helmCommandService;
	private final KubectlCommandService kubectlCommandService;
	private final DeploymentReadinessService deploymentReadinessService;
	private final MinioBucketService minioBucketService;
	private final Executor readinessExecutor;
	private final ClusterDeploymentProperties properties;

	public KubernetesDeploymentService(
		KubernetesClient client,
		DeploymentNamingService namingService,
		HelmValuesService helmValuesService,
		HelmCommandService helmCommandService,
		KubectlCommandService kubectlCommandService,
		DeploymentReadinessService deploymentReadinessService,
		MinioBucketService minioBucketService,
		Executor readinessExecutor,
		ClusterDeploymentProperties properties
	) {
		this.client = client;
		this.namingService = namingService;
		this.helmValuesService = helmValuesService;
		this.helmCommandService = helmCommandService;
		this.kubectlCommandService = kubectlCommandService;
		this.deploymentReadinessService = deploymentReadinessService;
		this.minioBucketService = minioBucketService;
		this.readinessExecutor = readinessExecutor;
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
			overrideValuesFile = helmValuesService.renderOverrideValues(request);
			CommandResult commandResult = helmCommandService.upgradeInstall(target.releaseName(), target.namespace(), overrideValuesFile);
			Instant finishedAt = Instant.now();
			if (!commandResult.successful()) {
				return failedResult(target, commandResult, overrideValuesFile, startedAt, finishedAt);
			}
			minioBucketService.ensureNamespaceBucket(target.namespace(), target.releaseName(), database.engine());
			scheduleReadinessCheck(target, database.engine());
			finishedAt = Instant.now();
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

	public KubernetesDeploymentResult updateBackupSettings(DeploymentTarget target, DatabaseEngine engine, DatabaseBackup backup) {
		Instant startedAt = Instant.now();
		try {
			if (engine == DatabaseEngine.POSTGRESQL) {
				patchCloudNativePgScheduledBackup(target, backup);
			}
			else {
				log.info("Backup settings persistence updated for {} in {}, but no live operator patch is defined for {}",
					target.releaseName(), target.namespace(), engine);
			}
			Instant finishedAt = Instant.now();
			return new KubernetesDeploymentResult(
				target.releaseName(),
				target.namespace(),
				List.of("kubectl", "apply", "scheduledbackup"),
				0,
				true,
				null,
				"Backup settings patched in the live operator resource",
				"",
				startedAt,
				finishedAt
			);
		}
		catch (Exception exception) {
			throw new ClusterDeploymentException("Failed to update backup settings: " + rootCauseMessage(exception), exception);
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

	private void patchCloudNativePgScheduledBackup(DeploymentTarget target, DatabaseBackup backup) {
		String clusterName = target.releaseName() + "-postgresql";
		List<io.fabric8.kubernetes.api.model.GenericKubernetesResource> scheduledBackups = client.genericKubernetesResources(
				"postgresql.cnpg.io/v1",
				"ScheduledBackup")
			.inNamespace(target.namespace())
			.list()
			.getItems();
		boolean patched = false;
		for (io.fabric8.kubernetes.api.model.GenericKubernetesResource resource : scheduledBackups) {
			if (!isScheduledBackupForCluster(resource, clusterName)) {
				continue;
			}
			String patchJson = buildScheduledBackupPatchJson(backup);
			CommandResult patchResult = kubectlCommandService.patchMerge(
				"scheduledbackup",
				resource.getMetadata().getName(),
				target.namespace(),
				patchJson
			);
			if (!patchResult.successful()) {
				throw new ClusterDeploymentException(
					"kubectl patch failed for ScheduledBackup " + resource.getMetadata().getName() + ": " + patchResult.stderr()
				);
			}
			patched = true;
		}
		if (!patched && backup != null && Boolean.TRUE.equals(backup.getEnabled())) {
			log.warn("No CloudNativePG ScheduledBackup resource was found for {} in {}", target.releaseName(), target.namespace());
		}
	}

	private String buildScheduledBackupPatchJson(DatabaseBackup backup) {
		boolean suspended = backup == null || !Boolean.TRUE.equals(backup.getEnabled());
		StringBuilder json = new StringBuilder();
		json.append("{\"spec\":{");
		json.append("\"suspend\":").append(suspended);
		if (backup != null && StringUtils.hasText(backup.getSchedule())) {
			json.append(",\"schedule\":\"").append(escapeJson(backup.getSchedule())).append("\"");
		}
		json.append("}}");
		return json.toString();
	}

	private String escapeJson(String value) {
		if (value == null) {
			return "";
		}
		StringBuilder escaped = new StringBuilder(value.length());
		for (char ch : value.toCharArray()) {
			switch (ch) {
				case '\\' -> escaped.append("\\\\");
				case '"' -> escaped.append("\\\"");
				case '\b' -> escaped.append("\\b");
				case '\f' -> escaped.append("\\f");
				case '\n' -> escaped.append("\\n");
				case '\r' -> escaped.append("\\r");
				case '\t' -> escaped.append("\\t");
				default -> escaped.append(ch);
			}
		}
		return escaped.toString();
	}

	private boolean isScheduledBackupForCluster(io.fabric8.kubernetes.api.model.GenericKubernetesResource resource, String clusterName) {
		Object specObject = resource.getAdditionalProperties() != null ? resource.getAdditionalProperties().get("spec") : null;
		if (!(specObject instanceof Map<?, ?> spec)) {
			return false;
		}
		Object clusterObject = spec.get("cluster");
		if (!(clusterObject instanceof Map<?, ?> cluster)) {
			return false;
		}
		Object nameObject = cluster.get("name");
		return clusterName.equals(String.valueOf(nameObject));
	}

	private Object readNestedValue(io.fabric8.kubernetes.api.model.GenericKubernetesResource resource, String parentKey, String childKey) {
		if (resource.getAdditionalProperties() == null) {
			return null;
		}
		Object parent = resource.getAdditionalProperties().get(parentKey);
		if (!(parent instanceof Map<?, ?> map)) {
			return null;
		}
		return map.get(childKey);
	}

	private void scheduleReadinessCheck(DeploymentTarget target, DatabaseEngine engine) {
		readinessExecutor.execute(() -> {
			try {
				deploymentReadinessService.verifyDeployment(target, engine);
				org.slf4j.LoggerFactory.getLogger(KubernetesDeploymentService.class)
					.info("Deployment became ready for {} in {}", target.releaseName(), target.namespace());
			}
			catch (RuntimeException exception) {
				org.slf4j.LoggerFactory.getLogger(KubernetesDeploymentService.class)
					.warn("Background readiness check failed for {} in {}: {}", target.releaseName(), target.namespace(), exception.getMessage());
			}
		});
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

	private KubernetesDeploymentResult failedResult(
		DeploymentTarget target,
		CommandResult commandResult,
		Path overrideValuesFile,
		Instant startedAt,
		Instant finishedAt
	) {
		return failedResult(target, commandResult, overrideValuesFile, startedAt, finishedAt, commandResult.stderr());
	}

	private KubernetesDeploymentResult failedResult(
		DeploymentTarget target,
		CommandResult commandResult,
		Path overrideValuesFile,
		Instant startedAt,
		Instant finishedAt,
		String stderr
	) {
		return new KubernetesDeploymentResult(
			target.releaseName(),
			target.namespace(),
			helmCommandSummary(target.releaseName(), target.namespace()),
			commandResult.exitCode(),
			false,
			valuesFileSummary(overrideValuesFile),
			commandResult.stdout(),
			stderr,
			startedAt,
			finishedAt
		);
	}

	private record KubernetesResourceDescriptor(
		String apiVersion,
		String kind,
		String resourceName
	) {
	}
}
