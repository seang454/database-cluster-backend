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
import com.example.demo.cluster.model.HelmReleaseResult;
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
	private final OperatorInstallerService operatorInstallerService;
	private final DeploymentReadinessService deploymentReadinessService;
	private final HelmReleaseService helmReleaseService;

	public ClusterService(
		ClusterRepository clusterRepository,
		DeploymentRecordRepository deploymentRecordRepository,
		ClusterPersistenceMapper persistenceMapper,
		ClusterResponseMapper responseMapper,
		DeploymentNamingService namingService,
		OperatorInstallerService operatorInstallerService,
		DeploymentReadinessService deploymentReadinessService,
		HelmReleaseService helmReleaseService
	) {
		this.clusterRepository = clusterRepository;
		this.deploymentRecordRepository = deploymentRecordRepository;
		this.persistenceMapper = persistenceMapper;
		this.responseMapper = responseMapper;
		this.namingService = namingService;
		this.operatorInstallerService = operatorInstallerService;
		this.deploymentReadinessService = deploymentReadinessService;
		this.helmReleaseService = helmReleaseService;
	}

	@Transactional
	public ClusterConfigResponse saveClusterConfig(ClusterDeploymentRequest request) {
		Cluster cluster = persistenceMapper.toCluster(request);
		Cluster saved = clusterRepository.save(cluster);
		DatabaseInstance database = saved.getDatabaseInstances().stream().findFirst()
			.orElseThrow(() -> new ClusterDeploymentException("Database configuration is required"));
		return responseMapper.toConfigResponse(saved, database, namingService.resolve(request));
	}

	public HelmReleaseResult saveAndDeploy(ClusterDeploymentRequest request) {
		Cluster cluster = persistenceMapper.toCluster(request);
		DeploymentTarget target = namingService.resolve(request);
		cluster.setHelmReleaseName(target.releaseName());
		Cluster saved = clusterRepository.save(cluster);

		DeploymentRecord record = createRecord(saved, request.database(), target);
		deploymentRecordRepository.save(record);
		record.setStatus(DeploymentStatus.INSTALLING);
		deploymentRecordRepository.save(record);

		try {
			operatorInstallerService.prepareForDeployment(request, target);
			HelmReleaseResult result = helmReleaseService.deploy(request);
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

	@Transactional(readOnly = true)
	public List<ClusterConfigResponse> listClusters() {
		return clusterRepository.findAll().stream()
			.map(cluster -> responseMapper.toConfigResponse(
				cluster,
				cluster.getDatabaseInstances().stream().findFirst().orElse(null),
				new DeploymentTarget(cluster.getHelmReleaseName(), responseMapper.defaultNamespace(cluster))))
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
			new DeploymentTarget(cluster.getHelmReleaseName(), responseMapper.defaultNamespace(cluster))
		);
	}

	@Transactional(readOnly = true)
	public List<DeploymentRecordResponse> listDeploymentRecords(UUID clusterId) {
		return deploymentRecordRepository.findByClusterIdOrderByCreatedAtDesc(clusterId).stream()
			.map(responseMapper::toRecordResponse)
			.toList();
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
