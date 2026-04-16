package com.example.demo.cluster.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;

import io.fabric8.kubernetes.client.KubernetesClient;
import com.example.demo.cluster.config.ClusterDeploymentProperties;
import com.example.demo.cluster.domain.enumtype.DatabaseEngine;
import com.example.demo.cluster.dto.ClusterDeploymentRequest;
import com.example.demo.cluster.dto.ClusterRequest;
import com.example.demo.cluster.dto.DatabaseBackupRequest;
import com.example.demo.cluster.dto.DatabaseInstanceRequest;
import com.example.demo.cluster.dto.DatabaseResourceRequest;
import com.example.demo.cluster.dto.DeploymentSecretsRequest;
import com.example.demo.cluster.dto.PostgresqlConfigRequest;
import com.example.demo.cluster.model.CommandResult;
import com.example.demo.cluster.model.DeploymentTarget;
import com.example.demo.cluster.model.KubernetesDeploymentResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KubernetesDeploymentServiceTest {

	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	private KubernetesClient kubernetesClient;

	@Mock
	private DeploymentNamingService deploymentNamingService;

	@Mock
	private HelmValuesService helmValuesService;

	@Mock
	private HelmCommandService helmCommandService;

	@Mock
	private DeploymentReadinessService deploymentReadinessService;

	@Mock
	private MinioBucketService minioBucketService;

	private ClusterDeploymentProperties properties;
	private KubernetesDeploymentService kubernetesDeploymentService;

	@BeforeEach
	void setUp() {
		properties = new ClusterDeploymentProperties();
		properties.setChartPath("oci://ghcr.io/seang454/db-cluster");
		properties.setChartVersion("4.0.1");
		properties.setHelmExecutable("helm");
		kubernetesDeploymentService = new KubernetesDeploymentService(
			kubernetesClient,
			deploymentNamingService,
			helmValuesService,
			helmCommandService,
			deploymentReadinessService,
			minioBucketService,
			properties
		);
	}

	@Test
	void deployReturnsFailedResultWhenHelmInstallFails() {
		ClusterDeploymentRequest request = new ClusterDeploymentRequest(
			"db-my-db",
			"ns-my-db",
			new ClusterRequest("my-db", null, null, null),
			new DatabaseInstanceRequest(
				DatabaseEngine.POSTGRESQL,
				true,
				(short) 3,
				"10Gi",
				"longhorn",
				false,
				null,
				null,
				true,
				null,
				null,
				null,
				false,
				null,
				new DatabaseResourceRequest("250m", "512Mi", "1500m", "2Gi", null),
				new DatabaseBackupRequest(true, null, "minio-credentials", "7d", "0 * * * * *"),
				new PostgresqlConfigRequest(true, "2Gi", "appdb", "appuser"),
				null,
				null,
				null,
				null
			),
			new DeploymentSecretsRequest("secret", null, null, null, null, null)
		);

		when(deploymentNamingService.resolve(request)).thenReturn(new DeploymentTarget("db-my-db", "ns-my-db"));
		when(helmValuesService.renderOverrideValues(request)).thenReturn(Path.of("D:/temp/overrides.yaml"));
		when(helmCommandService.upgradeInstall("db-my-db", "ns-my-db", Path.of("D:/temp/overrides.yaml")))
			.thenReturn(new CommandResult(1, "", "job failed"));

		KubernetesDeploymentResult result = kubernetesDeploymentService.deploy(request);

		assertThat(result.successful()).isFalse();
		assertThat(result.exitCode()).isEqualTo(1);
		assertThat(result.stderr()).contains("job failed");
		verify(deploymentReadinessService, never()).verifyDeployment(any(), any());
		verify(minioBucketService, never()).ensureNamespaceBucket(any(), any());
	}

	@Test
	void deployCreatesMinioBucketWhenBackupIsEnabled() {
		ClusterDeploymentRequest request = new ClusterDeploymentRequest(
			"db-my-db",
			"ns-my-db",
			new ClusterRequest("my-db", null, null, null),
			new DatabaseInstanceRequest(
				DatabaseEngine.POSTGRESQL,
				true,
				(short) 3,
				"10Gi",
				"longhorn",
				false,
				null,
				null,
				true,
				null,
				null,
				null,
				false,
				null,
				new DatabaseResourceRequest("250m", "512Mi", "1500m", "2Gi", null),
				new DatabaseBackupRequest(true, null, "minio-credentials", "7d", "0 * * * * *"),
				new PostgresqlConfigRequest(true, "2Gi", "appdb", "appuser"),
				null,
				null,
				null,
				null
			),
			new DeploymentSecretsRequest("secret", null, null, null, null, null)
		);

		when(deploymentNamingService.resolve(request)).thenReturn(new DeploymentTarget("db-my-db", "ns-my-db"));
		when(helmValuesService.renderOverrideValues(request)).thenReturn(Path.of("D:/temp/overrides.yaml"));
		when(helmCommandService.upgradeInstall("db-my-db", "ns-my-db", Path.of("D:/temp/overrides.yaml")))
			.thenReturn(new CommandResult(0, "installed", ""));

		KubernetesDeploymentResult result = kubernetesDeploymentService.deploy(request);

		assertThat(result.successful()).isTrue();
		verify(minioBucketService).ensureNamespaceBucket("ns-my-db", "db-my-db");
		verify(deploymentReadinessService).verifyDeployment(new DeploymentTarget("db-my-db", "ns-my-db"), DatabaseEngine.POSTGRESQL);
	}
}
