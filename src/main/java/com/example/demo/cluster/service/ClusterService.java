package com.example.demo.cluster.service;

import java.time.OffsetDateTime;
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
		this.cloudflareDnsService = cloudflareDnsService;
		this.properties = properties;
	}

	@Transactional
	public KubernetesDeploymentResult saveAndDeploy(ClusterDeploymentRequest request) {
		validateRequest(request);
		Cluster cluster = persistenceMapper.toCluster(request);
		applySpringDefaults(cluster);
		DeploymentTarget target = namingService.resolve(request);
		cluster.setDeploymentName(target.releaseName());
		Cluster saved = clusterRepository.findByDeploymentName(target.releaseName())
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

			deploymentReadinessService.verifyDeployment(target, request.database().engine());
			record.setStatus(DeploymentStatus.DEPLOYED);
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
		return clusterRepository.findAll().stream()
			.filter(cluster -> !org.springframework.util.StringUtils.hasText(namespace)
				|| namespace.equals(responseMapper.defaultNamespace(cluster)))
			.map(cluster -> responseMapper.toConfigResponse(
				cluster,
				cluster.getDatabaseInstances().stream().findFirst().orElse(null),
				new DeploymentTarget(cluster.getDeploymentName(), responseMapper.defaultNamespace(cluster))))
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
		return kubernetesDeploymentService.updateBackupSettings(target, database.getEngine(), backup);
	}

	@Transactional
	public KubernetesDeploymentResult updateBackupSettings(String releaseName, String namespace, DatabaseBackupSettingsRequest request) {
		Cluster cluster = clusterRepository.findByDeploymentName(releaseName)
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
		String expectedNamespace = responseMapper.defaultNamespace(cluster);
		if (!org.springframework.util.StringUtils.hasText(namespace) || !namespace.equals(expectedNamespace)) {
			throw new ClusterDeploymentException("Cluster not found in namespace: " + namespace);
		}
	}
}
