package com.example.demo.cluster.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import com.example.demo.cluster.domain.Cluster;
import com.example.demo.cluster.domain.DatabaseInstance;
import com.example.demo.cluster.domain.DeploymentRecord;
import com.example.demo.cluster.domain.enumtype.DatabaseEngine;
import com.example.demo.cluster.domain.enumtype.DeploymentStatus;
import com.example.demo.cluster.dto.ClusterDeploymentRequest;
import com.example.demo.cluster.dto.DatabaseInstanceRequest;
import com.example.demo.cluster.mapper.ClusterPersistenceMapper;
import com.example.demo.cluster.mapper.ClusterResponseMapper;
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

		KubernetesDeploymentResult result = clusterService.saveAndDeploy(request);

		assertThat(result).isEqualTo(uninstallResult);
		verify(kubernetesDeploymentService).uninstall("db-my-db", "ns-my-db");
		verify(kubernetesDeploymentService, never()).deploy(any());
		verify(deploymentReadinessService, never()).verifyDeployment(any(), any());

		ArgumentCaptor<DeploymentRecord> recordCaptor = ArgumentCaptor.forClass(DeploymentRecord.class);
		verify(deploymentRecordRepository, times(2)).save(recordCaptor.capture());
		DeploymentRecord finalRecord = recordCaptor.getAllValues().get(recordCaptor.getAllValues().size() - 1);
		assertThat(finalRecord.getStatus()).isEqualTo(DeploymentStatus.UNINSTALLED);
		assertThat(finalRecord.getReleaseName()).isEqualTo("db-my-db");
		assertThat(finalRecord.getNamespace()).isEqualTo("ns-my-db");
	}
}
