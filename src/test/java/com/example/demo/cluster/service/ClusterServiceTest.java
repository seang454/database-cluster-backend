package com.example.demo.cluster.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import com.example.demo.cluster.domain.Cluster;
import com.example.demo.cluster.domain.DatabaseInstance;
import com.example.demo.cluster.domain.DeploymentRecord;
import com.example.demo.cluster.domain.enumtype.DatabaseEngine;
import com.example.demo.cluster.domain.enumtype.DeploymentStatus;
import com.example.demo.cluster.dto.ClusterDeploymentRequest;
import com.example.demo.cluster.dto.ClusterRequest;
import com.example.demo.cluster.dto.DatabaseBackupSettingsRequest;
import com.example.demo.cluster.dto.DatabaseInstanceRequest;
import com.example.demo.cluster.mapper.ClusterPersistenceMapper;
import com.example.demo.cluster.mapper.ClusterResponseMapper;
import com.example.demo.cluster.config.ClusterDeploymentProperties;
import com.example.demo.cluster.model.DeploymentTarget;
import com.example.demo.cluster.model.KubernetesDeploymentResult;
import com.example.demo.cluster.repository.ClusterRepository;
import com.example.demo.cluster.repository.DeploymentRecordRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClusterServiceTest {

	@Mock
	private ClusterRepository clusterRepository;

	@Mock
	private DeploymentRecordRepository deploymentRecordRepository;

	@Mock
	private ClusterPersistenceMapper persistenceMapper;

	@Mock
	private ClusterResponseMapper responseMapper;

	@Mock
	private DeploymentNamingService namingService;

	@Mock
	private DeploymentReadinessService deploymentReadinessService;

	@Mock
	private KubernetesDeploymentService kubernetesDeploymentService;

	@Mock
	private CloudflareDnsService cloudflareDnsService;

	@Mock
	private ClusterDeploymentProperties properties;

	@InjectMocks
	private ClusterService clusterService;

	@Test
	void saveAndDeployUninstallsWhenDatabaseIsDisabled() {
		ClusterDeploymentRequest request = new ClusterDeploymentRequest(
			"db-my-db",
			"ns-my-db",
			null,
			new DatabaseInstanceRequest(
				DatabaseEngine.POSTGRESQL,
				false,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null
			),
			null
		);

		Cluster cluster = new Cluster();
		DatabaseInstance databaseInstance = new DatabaseInstance();
		databaseInstance.setEngine(DatabaseEngine.POSTGRESQL);
		cluster.getDatabaseInstances().add(databaseInstance);
		DeploymentTarget target = new DeploymentTarget("db-my-db", "ns-my-db");
		KubernetesDeploymentResult uninstallResult = new KubernetesDeploymentResult(
			"db-my-db",
			"ns-my-db",
			List.of("helm", "uninstall", "db-my-db", "-n", "ns-my-db"),
			0,
			true,
			null,
			"deleted",
			"",
			Instant.now(),
			Instant.now()
		);

		when(persistenceMapper.toCluster(request)).thenReturn(cluster);
		when(namingService.resolve(request)).thenReturn(target);
		when(clusterRepository.save(cluster)).thenReturn(cluster);
		when(kubernetesDeploymentService.uninstall("db-my-db", "ns-my-db")).thenReturn(uninstallResult);
		when(properties.getDefaultClusterDomain()).thenReturn("seang.shop");
		when(properties.getDefaultExternalIp()).thenReturn("35.194.146.154");
		when(properties.getDefaultCloudflareEnabled()).thenReturn(Boolean.TRUE);
		when(properties.getDefaultCloudflareZoneName()).thenReturn("seang.shop");
		when(properties.getDefaultCloudflareZoneId()).thenReturn("25794f1056c9f59652052d977bd73acb");

		KubernetesDeploymentResult result = clusterService.saveAndDeploy(request);

		assertThat(result).isEqualTo(uninstallResult);
		verify(kubernetesDeploymentService).uninstall("db-my-db", "ns-my-db");
		verify(kubernetesDeploymentService, never()).deploy(any());
		verify(deploymentReadinessService, never()).verifyDeployment(any(), any());
		verify(cloudflareDnsService).deleteClusterRecords(any(), any(), any());

		ArgumentCaptor<DeploymentRecord> recordCaptor = ArgumentCaptor.forClass(DeploymentRecord.class);
		verify(deploymentRecordRepository, times(2)).save(recordCaptor.capture());
		DeploymentRecord finalRecord = recordCaptor.getAllValues().get(recordCaptor.getAllValues().size() - 1);
		assertThat(finalRecord.getStatus()).isEqualTo(DeploymentStatus.UNINSTALLED);
		assertThat(finalRecord.getReleaseName()).isEqualTo("db-my-db");
		assertThat(finalRecord.getNamespace()).isEqualTo("ns-my-db");
	}

	@Test
	void saveAndDeployPreservesOtherDatabaseEnginesInTheSameRelease() {
		ClusterDeploymentRequest request = new ClusterDeploymentRequest(
			"db-my-db",
			"ns-my-db",
			new ClusterRequest("my-db", null, null, null),
			new DatabaseInstanceRequest(
				DatabaseEngine.POSTGRESQL,
				true,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null
			),
			null
		);

		Cluster incoming = new Cluster();
		incoming.setName("my-db");
		DatabaseInstance postgres = new DatabaseInstance();
		postgres.setEngine(DatabaseEngine.POSTGRESQL);
		incoming.getDatabaseInstances().add(postgres);

		Cluster existing = new Cluster();
		existing.setName("my-db");
		existing.setDeploymentName("db-my-db");
		DatabaseInstance mysql = new DatabaseInstance();
		mysql.setEngine(DatabaseEngine.MYSQL);
		existing.getDatabaseInstances().add(mysql);

		KubernetesDeploymentResult deployResult = new KubernetesDeploymentResult(
			"db-my-db",
			"ns-my-db",
			List.of("helm", "upgrade", "--install", "db-my-db", "oci://ghcr.io/seang454/db-cluster", "-n", "ns-my-db"),
			0,
			true,
			null,
			"deployed",
			"",
			Instant.now(),
			Instant.now()
		);

		when(persistenceMapper.toCluster(request)).thenReturn(incoming);
		when(namingService.resolve(request)).thenReturn(new DeploymentTarget("db-my-db", "ns-my-db"));
		when(clusterRepository.findByDeploymentName("db-my-db")).thenReturn(Optional.of(existing));
		when(clusterRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(kubernetesDeploymentService.deploy(request)).thenReturn(deployResult);

		KubernetesDeploymentResult result = clusterService.saveAndDeploy(request);

		assertThat(result).isEqualTo(deployResult);
		ArgumentCaptor<Cluster> clusterCaptor = ArgumentCaptor.forClass(Cluster.class);
		verify(clusterRepository, times(2)).save(clusterCaptor.capture());
		Cluster savedCluster = clusterCaptor.getAllValues().get(clusterCaptor.getAllValues().size() - 1);
		assertThat(savedCluster.getDatabaseInstances()).hasSize(2);
		assertThat(savedCluster.getDatabaseInstances()).extracting(DatabaseInstance::getEngine)
			.containsExactlyInAnyOrder(DatabaseEngine.MYSQL, DatabaseEngine.POSTGRESQL);
	}

	@Test
	void uninstallClusterDeletesClusterAndDeploymentHistoryOnSuccess() {
		UUID clusterId = UUID.randomUUID();
		Cluster cluster = new Cluster();
		cluster.setDeploymentName("db-my-db");
		DatabaseInstance databaseInstance = new DatabaseInstance();
		databaseInstance.setEngine(DatabaseEngine.POSTGRESQL);
		cluster.getDatabaseInstances().add(databaseInstance);

		when(clusterRepository.findById(clusterId)).thenReturn(Optional.of(cluster));
		when(responseMapper.defaultNamespace(cluster)).thenReturn("ns-my-db");
		when(kubernetesDeploymentService.uninstall("db-my-db", "ns-my-db")).thenReturn(new KubernetesDeploymentResult(
			"db-my-db",
			"ns-my-db",
			List.of("helm", "uninstall", "db-my-db", "-n", "ns-my-db"),
			0,
			true,
			null,
			"deleted",
			"",
			Instant.now(),
			Instant.now()
		));

		KubernetesDeploymentResult result = clusterService.uninstallCluster(clusterId, "ns-my-db");

		assertThat(result.successful()).isTrue();
		verify(deploymentRecordRepository).deleteByClusterId(clusterId);
		verify(clusterRepository).delete(cluster);
		verify(kubernetesDeploymentService).uninstall("db-my-db", "ns-my-db");
	}

	@Test
	void updateBackupSettingsUsesStoredClusterId() {
		UUID clusterId = UUID.randomUUID();
		Cluster cluster = new Cluster();
		cluster.setId(clusterId);
		cluster.setName("my-db");
		cluster.setDeploymentName("db-my-db");
		DatabaseInstance databaseInstance = new DatabaseInstance();
		databaseInstance.setEngine(DatabaseEngine.POSTGRESQL);
		cluster.getDatabaseInstances().add(databaseInstance);

		KubernetesDeploymentResult updateResult = new KubernetesDeploymentResult(
			"db-my-db",
			"ns-my-db",
			List.of("helm", "upgrade", "--install"),
			0,
			true,
			null,
			"updated",
			"",
			Instant.now(),
			Instant.now()
		);

		when(clusterRepository.findById(clusterId)).thenReturn(Optional.of(cluster));
		when(responseMapper.defaultNamespace(cluster)).thenReturn("ns-my-db");
		when(clusterRepository.save(cluster)).thenReturn(cluster);
		when(kubernetesDeploymentService.updateBackupSettings(any(), any(), any())).thenReturn(updateResult);

		KubernetesDeploymentResult result = clusterService.updateBackupSettings(
			clusterId,
			"ns-my-db",
			new DatabaseBackupSettingsRequest(true, null, "minio-backup-credentials", "7d", "0 * * * * *")
		);

		assertThat(result).isEqualTo(updateResult);
		verify(clusterRepository).findById(clusterId);
		verify(clusterRepository).save(cluster);
		verify(kubernetesDeploymentService).updateBackupSettings(any(), any(), any());
	}
}
