package com.example.demo.cluster.service;

import java.time.OffsetDateTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.example.demo.cluster.domain.Cluster;
import com.example.demo.cluster.domain.DatabaseBackup;
import com.example.demo.cluster.domain.DatabaseInstance;
import com.example.demo.cluster.domain.DeploymentRecord;
import com.example.demo.cluster.domain.enumtype.ClusterEnvironment;
import com.example.demo.cluster.domain.enumtype.DatabaseEngine;
import com.example.demo.cluster.domain.enumtype.DeploymentStatus;
import com.example.demo.cluster.dto.ClusterDeploymentRequest;
import com.example.demo.cluster.dto.DatabaseBackupSettingsRequest;
import com.example.demo.cluster.exception.ClusterDeploymentException;
import com.example.demo.cluster.mapper.ClusterPersistenceMapper;
import com.example.demo.cluster.mapper.ClusterResponseMapper;
import com.example.demo.cluster.model.ClusterConfigResponse;
import com.example.demo.cluster.model.DeploymentRecordResponse;
import com.example.demo.cluster.model.DeploymentTarget;
import com.example.demo.cluster.model.KubernetesDeploymentResult;
import com.example.demo.cluster.repository.ClusterRepository;
import com.example.demo.cluster.repository.DeploymentRecordRepository;
import com.example.demo.cluster.config.ClusterDeploymentProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClusterService {

	private static final Logger log = LoggerFactory.getLogger(ClusterService.class);

	private final ClusterRepository clusterRepository;
	private final DeploymentRecordRepository deploymentRecordRepository;
	private final ClusterPersistenceMapper persistenceMapper;
	private final ClusterResponseMapper responseMapper;
	private final DeploymentNamingService namingService;
	private final DeploymentReadinessService deploymentReadinessService;
	private final KubernetesDeploymentService kubernetesDeploymentService;
	private final ClusterChangeRoutingService changeRoutingService;
	private final CloudflareDnsService cloudflareDnsService;
	private final ClusterDeploymentProperties properties;

	public ClusterService(
		ClusterRepository clusterRepository,
		DeploymentRecordRepository deploymentRecordRepository,
		ClusterPersistenceMapper persistenceMapper,
		ClusterResponseMapper responseMapper,
		DeploymentNamingService namingService,
		DeploymentReadinessService deploymentReadinessService,
		KubernetesDeploymentService kubernetesDeploymentService,
		ClusterChangeRoutingService changeRoutingService,
		CloudflareDnsService cloudflareDnsService,
		ClusterDeploymentProperties properties
	) {
		this.clusterRepository = clusterRepository;
		this.deploymentRecordRepository = deploymentRecordRepository;
		this.persistenceMapper = persistenceMapper;
		this.responseMapper = responseMapper;
		this.namingService = namingService;
		this.deploymentReadinessService = deploymentReadinessService;
		this.kubernetesDeploymentService = kubernetesDeploymentService;
		this.changeRoutingService = changeRoutingService;
		this.cloudflareDnsService = cloudflareDnsService;
		this.properties = properties;
	}

	public KubernetesDeploymentResult saveAndDeploy(ClusterDeploymentRequest request) {
		// Keep persistence outside a single long transaction so external deploy steps
		// do not roll back the cluster/record rows if Kubernetes or MinIO later fails.
		validateRequest(request);
		Cluster cluster = persistenceMapper.toCluster(request);
		applySpringDefaults(cluster);
		DeploymentTarget target = namingService.resolve(request);
		cluster.setDeploymentName(target.releaseName());
		cluster.setDeploymentNamespace(target.namespace());
		Cluster saved = clusterRepository.findByDeploymentNameAndDeploymentNamespace(target.releaseName(), target.namespace())
			.map(existing -> mergeCluster(existing, cluster))
			.orElse(cluster);
		saved = clusterRepository.save(saved);

		DeploymentRecord record = createRecord(saved, request.database(), target);
		deploymentRecordRepository.save(record);
		DatabaseInstance targetDatabase = resolveDatabaseInstance(saved, request.database().engine());
		if (request.database() != null && Boolean.FALSE.equals(request.database().enabled())) {
			KubernetesDeploymentResult result = uninstallFromDisabledRequest(target, record);
			deleteDnsBestEffort(saved, saved.getDatabaseInstances(), request.secrets());
			return result;
		}

		record.setStatus(DeploymentStatus.INSTALLING);
		deploymentRecordRepository.save(record);

		try {
			cloudflareDnsService.upsertClusterRecords(saved, targetDatabase, request.secrets());
			KubernetesDeploymentResult result = kubernetesDeploymentService.deploy(request);
			record.setExitCode(result.exitCode());
			record.setValuesFile(result.valuesFile());
			record.setCommandText(String.join(" ", result.command()));
			record.setStdout(result.stdout());
			record.setStderr(result.stderr());
			if (!result.successful()) {
				record.setFinishedAt(OffsetDateTime.ofInstant(result.finishedAt(), ZoneOffset.UTC));
				record.setStatus(DeploymentStatus.FAILED);
				deploymentRecordRepository.save(record);
				return result;
			}

			record.setStatus(DeploymentStatus.INSTALLING);
			record.setFinishedAt(OffsetDateTime.now(ZoneOffset.UTC));
			targetDatabase.setLastDeployedAt(record.getFinishedAt());
			clusterRepository.save(saved);
			deploymentRecordRepository.save(record);
			return result;
		}
		catch (RuntimeException exception) {
			record.setStatus(DeploymentStatus.FAILED);
			record.setFinishedAt(OffsetDateTime.now(ZoneOffset.UTC));
			record.setStderr(exception.getMessage());
			deploymentRecordRepository.save(record);
			throw exception;
		}
	}

	private void validateRequest(ClusterDeploymentRequest request) {
		if (request == null) {
			throw new ClusterDeploymentException("Deployment request is required");
		}
		if (request.database() == null || request.database().engine() == null) {
			throw new ClusterDeploymentException("A single database configuration is required");
		}
		boolean hasClusterName = request.cluster() != null
			&& org.springframework.util.StringUtils.hasText(request.cluster().name());
		boolean hasExplicitTarget = org.springframework.util.StringUtils.hasText(request.releaseName())
			&& org.springframework.util.StringUtils.hasText(request.namespace());
		if (!hasClusterName && !hasExplicitTarget) {
			throw new ClusterDeploymentException("Cluster name is required when release name or namespace is missing");
		}
	}

	private KubernetesDeploymentResult uninstallFromDisabledRequest(DeploymentTarget target, DeploymentRecord record) {
		try {
			KubernetesDeploymentResult result = kubernetesDeploymentService.uninstall(target.releaseName(), target.namespace());
			record.setExitCode(result.exitCode());
			record.setCommandText(String.join(" ", result.command()));
			record.setStdout(result.stdout());
			record.setStderr(result.stderr());
			record.setFinishedAt(OffsetDateTime.ofInstant(result.finishedAt(), ZoneOffset.UTC));
			record.setStatus(result.successful() ? DeploymentStatus.UNINSTALLED : DeploymentStatus.FAILED);
			deploymentRecordRepository.save(record);
			return result;
		}
		catch (RuntimeException exception) {
			record.setStatus(DeploymentStatus.FAILED);
			record.setFinishedAt(OffsetDateTime.now(ZoneOffset.UTC));
			record.setStderr(exception.getMessage());
			deploymentRecordRepository.save(record);
			throw exception;
		}
	}

	@Transactional(readOnly = true)
	public List<ClusterConfigResponse> listClusters(String namespace) {
		List<Cluster> clusters = clusterRepository.findAll().stream()
			.filter(cluster -> matchesNamespace(cluster, namespace))
			.toList();
		if (clusters.isEmpty() && org.springframework.util.StringUtils.hasText(namespace)) {
			clusters = deploymentRecordRepository.findByNamespaceOrderByCreatedAtDesc(namespace).stream()
				.map(DeploymentRecord::getCluster)
				.filter(cluster -> cluster != null)
				.distinct()
				.toList();
		}
		String resolvedNamespace = org.springframework.util.StringUtils.hasText(namespace) ? namespace : null;
		return clusters.stream()
			.map(cluster -> responseMapper.toConfigResponse(
				cluster,
				cluster.getDatabaseInstances().stream().findFirst().orElse(null),
				new DeploymentTarget(cluster.getDeploymentName(), resolveNamespace(cluster, resolvedNamespace))))
			.toList();
	}

	@Transactional(readOnly = true)
	public ClusterConfigResponse getCluster(UUID id, String namespace) {
		Cluster cluster = clusterRepository.findById(id)
			.orElseThrow(() -> new ClusterDeploymentException("Cluster not found: " + id));
		assertNamespace(cluster, namespace);
		DatabaseInstance database = cluster.getDatabaseInstances().stream().findFirst().orElse(null);
		return responseMapper.toConfigResponse(
			cluster,
			database,
			new DeploymentTarget(cluster.getDeploymentName(), responseMapper.defaultNamespace(cluster))
		);
	}

	@Transactional(readOnly = true)
	public List<DeploymentRecordResponse> listDeploymentRecords(UUID clusterId, String namespace) {
		Cluster cluster = clusterRepository.findById(clusterId)
			.orElseThrow(() -> new ClusterDeploymentException("Cluster not found: " + clusterId));
		assertNamespace(cluster, namespace);
		return deploymentRecordRepository.findByClusterIdOrderByCreatedAtDesc(clusterId).stream()
			.map(responseMapper::toRecordResponse)
			.toList();
	}

	@Transactional
	public KubernetesDeploymentResult uninstallDeployment(String releaseName, String namespace) {
		KubernetesDeploymentResult result = kubernetesDeploymentService.uninstall(releaseName, namespace);
		deploymentRecordRepository.findByReleaseNameAndNamespaceOrderByCreatedAtDesc(releaseName, namespace).stream()
			.findFirst()
			.ifPresent(record -> {
				record.setExitCode(result.exitCode());
				record.setCommandText(String.join(" ", result.command()));
				record.setStdout(result.stdout());
				record.setStderr(result.stderr());
				record.setFinishedAt(OffsetDateTime.ofInstant(result.finishedAt(), ZoneOffset.UTC));
				record.setStatus(result.successful() ? DeploymentStatus.UNINSTALLED : DeploymentStatus.FAILED);
				deploymentRecordRepository.save(record);
		});
		return result;
	}

	@Transactional
	public KubernetesDeploymentResult uninstallCluster(UUID clusterId, String namespace) {
		Cluster cluster = clusterRepository.findById(clusterId)
			.orElseThrow(() -> new ClusterDeploymentException("Cluster not found: " + clusterId));
		assertNamespace(cluster, namespace);
		String releaseName = cluster.getDeploymentName();
		String targetNamespace = responseMapper.defaultNamespace(cluster);
		if (!org.springframework.util.StringUtils.hasText(releaseName) || !org.springframework.util.StringUtils.hasText(targetNamespace)) {
			throw new ClusterDeploymentException("Cluster is missing deployment name or namespace");
		}
		KubernetesDeploymentResult result = kubernetesDeploymentService.uninstall(releaseName, targetNamespace);
		deleteDnsBestEffort(cluster, cluster.getDatabaseInstances(), null);
		if (result.successful()) {
			deploymentRecordRepository.deleteByClusterId(clusterId);
			clusterRepository.delete(cluster);
		}
		deploymentRecordRepository.findByReleaseNameAndNamespaceOrderByCreatedAtDesc(releaseName, targetNamespace).stream()
			.findFirst()
			.ifPresent(record -> {
				record.setExitCode(result.exitCode());
				record.setCommandText(String.join(" ", result.command()));
				record.setStdout(result.stdout());
				record.setStderr(result.stderr());
				record.setFinishedAt(OffsetDateTime.ofInstant(result.finishedAt(), ZoneOffset.UTC));
				record.setStatus(result.successful() ? DeploymentStatus.UNINSTALLED : DeploymentStatus.FAILED);
				deploymentRecordRepository.save(record);
			});
		return result;
	}

	@Transactional
	public KubernetesDeploymentResult updateBackupSettings(UUID clusterId, String namespace, DatabaseBackupSettingsRequest request) {
		if (request == null) {
			throw new ClusterDeploymentException("Backup settings request is required");
		}
		Cluster cluster = clusterRepository.findById(clusterId)
			.orElseThrow(() -> new ClusterDeploymentException("Cluster not found: " + clusterId));
		assertNamespace(cluster, namespace);
		DatabaseInstance database = resolveDatabaseInstance(cluster, null);
		DatabaseBackup backup = database.getDatabaseBackup();
		if (backup == null) {
			backup = new DatabaseBackup();
			backup.setDatabaseInstance(database);
			backup.setEnabled(Boolean.TRUE);
			database.setDatabaseBackup(backup);
		}
		if (request.enabled() != null) {
			backup.setEnabled(request.enabled());
		}
		else if (backup.getEnabled() == null) {
			backup.setEnabled(Boolean.TRUE);
		}
		if (request.destinationPath() != null) {
			backup.setDestinationPath(request.destinationPath());
		}
		if (request.credentialSecret() != null) {
			backup.setCredentialSecret(request.credentialSecret());
		}
		if (request.retentionPolicy() != null) {
			backup.setRetentionPolicy(request.retentionPolicy());
		}
		if (request.schedule() != null) {
			backup.setSchedule(request.schedule());
		}
		clusterRepository.save(cluster);

		String releaseName = cluster.getDeploymentName();
		if (!org.springframework.util.StringUtils.hasText(releaseName)) {
			throw new ClusterDeploymentException("Cluster has no deployment name");
		}
		DeploymentTarget target = new DeploymentTarget(releaseName, namespace);
		ChangeDestination destination = changeRoutingService.routeBackupSettings(request);
		log.info(
			"Backup settings for {} in {} routed to {} ({})",
			releaseName,
			namespace,
			destination,
			changeRoutingService.describeBackupRoute(request)
		);
		if (destination == ChangeDestination.OPERATOR_PATCH) {
			return kubernetesDeploymentService.updateBackupSettings(target, database.getEngine(), backup);
		}
		String message = destination == ChangeDestination.HELM
			? "Backup settings saved in the application database; Helm re-apply is required for chart-managed fields"
			: "Backup settings saved in the application database; no live operator patch was required";
		Instant now = OffsetDateTime.now(ZoneOffset.UTC).toInstant();
		return new KubernetesDeploymentResult(
			releaseName,
			namespace,
			List.of("db", "backup", "save"),
			0,
			true,
			null,
			message,
			"",
			now,
			now
		);
	}

	@Transactional
	public KubernetesDeploymentResult updateBackupSettings(String releaseName, String namespace, DatabaseBackupSettingsRequest request) {
		Cluster cluster = clusterRepository.findByDeploymentNameAndDeploymentNamespace(releaseName, namespace)
			.orElseThrow(() -> new ClusterDeploymentException("Cluster not found for release: " + releaseName));
		return updateBackupSettings(cluster.getId(), namespace, request);
	}

	private DeploymentRecord createRecord(
		Cluster cluster,
		com.example.demo.cluster.dto.DatabaseInstanceRequest database,
		DeploymentTarget target
	) {
		if (database == null || database.engine() == null) {
			throw new ClusterDeploymentException("A single database configuration is required");
		}
		DeploymentRecord record = new DeploymentRecord();
		record.setCluster(cluster);
		record.setDatabaseEngine(database.engine());
		record.setReleaseName(target.releaseName());
		record.setNamespace(target.namespace());
		record.setStatus(DeploymentStatus.PENDING);
		record.setStartedAt(OffsetDateTime.now(ZoneOffset.UTC));
		return record;
	}

	private void applySpringDefaults(Cluster cluster) {
		if (cluster == null) {
			return;
		}
		if (cluster.getEnvironment() == null) {
			cluster.setEnvironment(ClusterEnvironment.PRODUCTION);
		}
		if (cluster.getPlatformConfig() != null) {
			cluster.getPlatformConfig().setCloudflareEnabled(properties.getDefaultCloudflareEnabled());
			cluster.getPlatformConfig().setCloudflareZoneName(properties.getDefaultCloudflareZoneName());
			cluster.getPlatformConfig().setCloudflareZoneId(properties.getDefaultCloudflareZoneId());
		}
		cluster.setDomain(properties.getDefaultClusterDomain());
		cluster.setExternalIp(properties.getDefaultExternalIp());
	}

	private Cluster mergeCluster(Cluster existing, Cluster incoming) {
		existing.setName(incoming.getName());
		existing.setEnvironment(incoming.getEnvironment());
		existing.setDomain(incoming.getDomain());
		existing.setExternalIp(incoming.getExternalIp());
		existing.setDeploymentName(incoming.getDeploymentName());
		existing.setDeploymentNamespace(incoming.getDeploymentNamespace());
		existing.setPlatformConfig(incoming.getPlatformConfig());
		existing.setNotes(incoming.getNotes());
		if (incoming.getDatabaseInstances() != null && !incoming.getDatabaseInstances().isEmpty()) {
			if (existing.getDatabaseInstances() == null) {
				existing.setDatabaseInstances(new ArrayList<>());
			}
			for (DatabaseInstance database : incoming.getDatabaseInstances()) {
				database.setCluster(existing);
				existing.getDatabaseInstances().removeIf(current -> current.getEngine() == database.getEngine());
				existing.getDatabaseInstances().add(database);
			}
		}
		return existing;
	}

	private DatabaseInstance resolveDatabaseInstance(Cluster cluster, DatabaseEngine engine) {
		if (cluster == null || cluster.getDatabaseInstances() == null || cluster.getDatabaseInstances().isEmpty()) {
			throw new ClusterDeploymentException("Cluster has no database instance");
		}
		if (engine == null) {
			return cluster.getDatabaseInstances().stream()
				.findFirst()
				.orElseThrow(() -> new ClusterDeploymentException("Cluster has no database instance"));
		}
		return cluster.getDatabaseInstances().stream()
			.filter(database -> engine.equals(database.getEngine()))
			.findFirst()
			.orElseThrow(() -> new ClusterDeploymentException("Cluster has no database instance for engine " + engine));
	}

	private void deleteDnsBestEffort(Cluster cluster, List<DatabaseInstance> databases, com.example.demo.cluster.dto.DeploymentSecretsRequest secrets) {
		try {
			if (cluster != null && databases != null) {
				for (DatabaseInstance database : databases) {
					cloudflareDnsService.deleteClusterRecords(cluster, database, secrets);
				}
			}
		}
		catch (RuntimeException exception) {
			log.warn("Cloudflare DNS cleanup failed: {}", exception.getMessage());
		}
	}

	private void assertNamespace(Cluster cluster, String namespace) {
		if (!isKnownNamespace(cluster, namespace)) {
			throw new ClusterDeploymentException("Cluster not found in namespace: " + namespace);
		}
	}

	private boolean isKnownNamespace(Cluster cluster, String namespace) {
		if (cluster == null || !org.springframework.util.StringUtils.hasText(namespace)) {
			return false;
		}
		if (namespace.equals(cluster.getDeploymentNamespace())) {
			return true;
		}
		if (namespace.equals(responseMapper.defaultNamespace(cluster))) {
			return true;
		}
		if (cluster.getId() == null) {
			return false;
		}
		return deploymentRecordRepository.findByClusterIdOrderByCreatedAtDesc(cluster.getId()).stream()
			.anyMatch(record -> namespace.equals(record.getNamespace()));
	}

	private boolean matchesNamespace(Cluster cluster, String namespace) {
		if (cluster == null) {
			return false;
		}
		if (!org.springframework.util.StringUtils.hasText(namespace)) {
			return true;
		}
		String storedNamespace = cluster.getDeploymentNamespace();
		if (org.springframework.util.StringUtils.hasText(storedNamespace) && namespace.equals(storedNamespace)) {
			return true;
		}
		String expectedNamespace = responseMapper.defaultNamespace(cluster);
		return namespace.equals(expectedNamespace);
	}

	private String resolveNamespace(Cluster cluster, String fallbackNamespace) {
		if (org.springframework.util.StringUtils.hasText(fallbackNamespace)) {
			return fallbackNamespace;
		}
		if (cluster != null && org.springframework.util.StringUtils.hasText(cluster.getDeploymentNamespace())) {
			return cluster.getDeploymentNamespace();
		}
		return responseMapper.defaultNamespace(cluster);
	}
}
