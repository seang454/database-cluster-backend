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
import com.example.demo.cluster.model.ClusterConfigResponse;
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
	private ClusterChangeRoutingService changeRoutingService;

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
		when(clusterRepository.findByDeploymentNameAndDeploymentNamespace("db-my-db", "ns-my-db")).thenReturn(Optional.of(existing));
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
	void saveAndDeployDoesNotMergeAcrossNamespaces() {
		ClusterDeploymentRequest request = new ClusterDeploymentRequest(
			"db-my-db",
			"ns-new-person",
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
		existing.setDeploymentNamespace("ns-old-person");
		DatabaseInstance mysql = new DatabaseInstance();
		mysql.setEngine(DatabaseEngine.MYSQL);
		existing.getDatabaseInstances().add(mysql);

		KubernetesDeploymentResult deployResult = new KubernetesDeploymentResult(
			"db-my-db",
			"ns-new-person",
			List.of("helm", "upgrade", "--install", "db-my-db", "oci://ghcr.io/seang454/db-cluster", "-n", "ns-new-person"),
			0,
			true,
			null,
			"deployed",
			"",
			Instant.now(),
			Instant.now()
		);

		when(persistenceMapper.toCluster(request)).thenReturn(incoming);
		when(namingService.resolve(request)).thenReturn(new DeploymentTarget("db-my-db", "ns-new-person"));
		when(clusterRepository.findByDeploymentNameAndDeploymentNamespace("db-my-db", "ns-new-person")).thenReturn(Optional.empty());
		when(clusterRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(kubernetesDeploymentService.deploy(request)).thenReturn(deployResult);

		KubernetesDeploymentResult result = clusterService.saveAndDeploy(request);

		assertThat(result).isEqualTo(deployResult);
		ArgumentCaptor<Cluster> clusterCaptor = ArgumentCaptor.forClass(Cluster.class);
		verify(clusterRepository, times(2)).save(clusterCaptor.capture());
		Cluster savedCluster = clusterCaptor.getAllValues().get(clusterCaptor.getAllValues().size() - 1);
		assertThat(savedCluster.getDeploymentNamespace()).isEqualTo("ns-new-person");
		assertThat(savedCluster.getDatabaseInstances()).hasSize(1);
		assertThat(savedCluster.getDatabaseInstances()).extracting(DatabaseInstance::getEngine)
			.containsExactly(DatabaseEngine.POSTGRESQL);
	}

	@Test
	void listClustersFallsBackToDeploymentRecordsWhenClusterNamespaceIsMissing() {
		Cluster cluster = new Cluster();
		cluster.setName("my-db");
		cluster.setDeploymentName("db-my-db");
		DatabaseInstance database = new DatabaseInstance();
		database.setEngine(DatabaseEngine.POSTGRESQL);
		cluster.getDatabaseInstances().add(database);

		DeploymentRecord record = new DeploymentRecord();
		record.setCluster(cluster);

		when(clusterRepository.findAll()).thenReturn(List.of(cluster));
		when(deploymentRecordRepository.findByNamespaceOrderByCreatedAtDesc("seang")).thenReturn(List.of(record));
		when(responseMapper.toConfigResponse(any(), any(), any())).thenAnswer(invocation -> {
			Cluster mappedCluster = invocation.getArgument(0);
			DatabaseInstance mappedDatabase = invocation.getArgument(1);
			DeploymentTarget target = invocation.getArgument(2);
			return new ClusterConfigResponse(
				mappedCluster.getId(),
				mappedCluster.getName(),
				mappedCluster.getEnvironment(),
				mappedDatabase != null ? mappedDatabase.getEngine() : null,
				target.releaseName(),
				target.namespace()
			);
		});

		List<ClusterConfigResponse> result = clusterService.listClusters("seang");

		assertThat(result).hasSize(1);
		assertThat(result.getFirst().namespace()).isEqualTo("seang");
		assertThat(result.getFirst().name()).isEqualTo("my-db");
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

		when(clusterRepository.findById(clusterId)).thenReturn(Optional.of(cluster));
		when(responseMapper.defaultNamespace(cluster)).thenReturn("ns-my-db");
		when(clusterRepository.save(cluster)).thenReturn(cluster);
		when(changeRoutingService.routeBackupSettings(any())).thenReturn(ChangeDestination.OPERATOR_PATCH);
		when(changeRoutingService.describeBackupRoute(any())).thenReturn("enabled/schedule are operator-managed and can be patched live");
		when(kubernetesDeploymentService.updateBackupSettings(any(), any(), any())).thenReturn(new KubernetesDeploymentResult(
			"db-my-db",
			"ns-my-db",
			List.of("kubectl", "apply", "scheduledbackup"),
			0,
			true,
			null,
			"Backup settings patched in the live operator resource",
			"",
			Instant.now(),
			Instant.now()
		));

		KubernetesDeploymentResult result = clusterService.updateBackupSettings(
			clusterId,
			"ns-my-db",
			new DatabaseBackupSettingsRequest(true, null, "minio-backup-credentials", "7d", "0 * * * * *")
		);

		assertThat(result.successful()).isTrue();
		verify(clusterRepository).findById(clusterId);
		verify(clusterRepository).save(cluster);
		verify(kubernetesDeploymentService).updateBackupSettings(any(), any(), any());
	}

	@Test
	void updateBackupSettingsAcceptsNamespaceFromDeploymentHistory() {
		UUID clusterId = UUID.randomUUID();
		Cluster cluster = new Cluster();
		cluster.setId(clusterId);
		cluster.setName("my-db");
		cluster.setDeploymentName("db-my-db");
		DatabaseInstance databaseInstance = new DatabaseInstance();
		databaseInstance.setEngine(DatabaseEngine.POSTGRESQL);
		cluster.getDatabaseInstances().add(databaseInstance);

		DeploymentRecord record = new DeploymentRecord();
		record.setCluster(cluster);
		record.setNamespace("seang");

		when(clusterRepository.findById(clusterId)).thenReturn(Optional.of(cluster));
		when(deploymentRecordRepository.findByClusterIdOrderByCreatedAtDesc(clusterId)).thenReturn(List.of(record));
		when(clusterRepository.save(cluster)).thenReturn(cluster);
		when(changeRoutingService.routeBackupSettings(any())).thenReturn(ChangeDestination.OPERATOR_PATCH);
		when(changeRoutingService.describeBackupRoute(any())).thenReturn("enabled/schedule are operator-managed and can be patched live");
		when(kubernetesDeploymentService.updateBackupSettings(any(), any(), any())).thenReturn(new KubernetesDeploymentResult(
			"db-my-db",
			"seang",
			List.of("kubectl", "apply", "scheduledbackup"),
			0,
			true,
			null,
			"Backup settings patched in the live operator resource",
			"",
			Instant.now(),
			Instant.now()
		));

		KubernetesDeploymentResult result = clusterService.updateBackupSettings(
			clusterId,
			"seang",
			new DatabaseBackupSettingsRequest(false, null, null, null, "0 * * * * *")
		);

		assertThat(result.successful()).isTrue();
		verify(kubernetesDeploymentService).updateBackupSettings(any(), any(), any());
	}

	@Test
	void updateBackupSettingsStoresDbOnlyFieldsWithoutLivePatch() {
		UUID clusterId = UUID.randomUUID();
		Cluster cluster = new Cluster();
		cluster.setId(clusterId);
		cluster.setName("my-db");
		cluster.setDeploymentName("db-my-db");
		DatabaseInstance databaseInstance = new DatabaseInstance();
		databaseInstance.setEngine(DatabaseEngine.POSTGRESQL);
		cluster.getDatabaseInstances().add(databaseInstance);

		when(clusterRepository.findById(clusterId)).thenReturn(Optional.of(cluster));
		when(responseMapper.defaultNamespace(cluster)).thenReturn("ns-my-db");
		when(clusterRepository.save(cluster)).thenReturn(cluster);
		when(changeRoutingService.routeBackupSettings(any())).thenReturn(ChangeDestination.DB_ONLY);
		when(changeRoutingService.describeBackupRoute(any())).thenReturn("no live backup field changed; DB persistence only");

		KubernetesDeploymentResult result = clusterService.updateBackupSettings(
			clusterId,
			"ns-my-db",
			new DatabaseBackupSettingsRequest(null, "s3://backup-bucket/my-db", null, "7d", null)
		);

		assertThat(result.successful()).isTrue();
		assertThat(result.stdout()).contains("application database");
		verify(kubernetesDeploymentService, never()).updateBackupSettings(any(), any(), any());
		verify(clusterRepository).save(cluster);
	}
}
