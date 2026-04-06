package com.example.demo.cluster.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.example.demo.cluster.domain.Cluster;
import com.example.demo.cluster.domain.DatabaseInstance;
import com.example.demo.cluster.domain.DeploymentRecord;
import com.example.demo.cluster.domain.enumtype.DeploymentStatus;
import com.example.demo.cluster.dto.ClusterDeploymentRequest;
import com.example.demo.cluster.exception.ClusterDeploymentException;
import com.example.demo.cluster.mapper.ClusterPersistenceMapper;
import com.example.demo.cluster.mapper.ClusterResponseMapper;
import com.example.demo.cluster.model.ClusterConfigResponse;
import com.example.demo.cluster.model.DeploymentRecordResponse;
import com.example.demo.cluster.model.DeploymentTarget;
import com.example.demo.cluster.model.KubernetesDeploymentResult;
import com.example.demo.cluster.repository.ClusterRepository;
import com.example.demo.cluster.repository.DeploymentRecordRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClusterService {

	private final ClusterRepository clusterRepository;
	private final DeploymentRecordRepository deploymentRecordRepository;
	private final ClusterPersistenceMapper persistenceMapper;
	private final ClusterResponseMapper responseMapper;
	private final DeploymentNamingService namingService;
	private final DeploymentReadinessService deploymentReadinessService;
	private final KubernetesDeploymentService kubernetesDeploymentService;

	public ClusterService(
		ClusterRepository clusterRepository,
		DeploymentRecordRepository deploymentRecordRepository,
		ClusterPersistenceMapper persistenceMapper,
		ClusterResponseMapper responseMapper,
		DeploymentNamingService namingService,
		DeploymentReadinessService deploymentReadinessService,
		KubernetesDeploymentService kubernetesDeploymentService
	) {
		this.clusterRepository = clusterRepository;
		this.deploymentRecordRepository = deploymentRecordRepository;
		this.persistenceMapper = persistenceMapper;
		this.responseMapper = responseMapper;
		this.namingService = namingService;
		this.deploymentReadinessService = deploymentReadinessService;
		this.kubernetesDeploymentService = kubernetesDeploymentService;
	}

	public KubernetesDeploymentResult saveAndDeploy(ClusterDeploymentRequest request) {
		validateRequest(request);
		Cluster cluster = persistenceMapper.toCluster(request);
		DeploymentTarget target = namingService.resolve(request);
		cluster.setDeploymentName(target.releaseName());
		Cluster saved = clusterRepository.save(cluster);

		DeploymentRecord record = createRecord(saved, request.database(), target);
		deploymentRecordRepository.save(record);
		if (request.database() != null && Boolean.FALSE.equals(request.database().enabled())) {
			return uninstallFromDisabledRequest(target, record);
		}

		record.setStatus(DeploymentStatus.INSTALLING);
		deploymentRecordRepository.save(record);

		try {
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
			DatabaseInstance database = saved.getDatabaseInstances().stream().findFirst()
				.orElseThrow(() -> new ClusterDeploymentException("Saved cluster has no database instance"));
			database.setLastDeployedAt(record.getFinishedAt());
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
	public List<ClusterConfigResponse> listClusters() {
		return clusterRepository.findAll().stream()
			.map(cluster -> responseMapper.toConfigResponse(
				cluster,
				cluster.getDatabaseInstances().stream().findFirst().orElse(null),
				new DeploymentTarget(cluster.getDeploymentName(), responseMapper.defaultNamespace(cluster))))
			.toList();
	}

	@Transactional(readOnly = true)
	public ClusterConfigResponse getCluster(UUID id) {
		Cluster cluster = clusterRepository.findById(id)
			.orElseThrow(() -> new ClusterDeploymentException("Cluster not found: " + id));
		DatabaseInstance database = cluster.getDatabaseInstances().stream().findFirst().orElse(null);
		return responseMapper.toConfigResponse(
			cluster,
			database,
			new DeploymentTarget(cluster.getDeploymentName(), responseMapper.defaultNamespace(cluster))
		);
	}

	@Transactional(readOnly = true)
	public List<DeploymentRecordResponse> listDeploymentRecords(UUID clusterId) {
		return deploymentRecordRepository.findByClusterIdOrderByCreatedAtDesc(clusterId).stream()
			.map(responseMapper::toRecordResponse)
			.toList();
	}

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
}
