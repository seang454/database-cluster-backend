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
import com.example.demo.cluster.dto.DatabaseInstanceRequest;
import com.example.demo.cluster.dto.DeploymentSecretsRequest;
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
	private KubernetesSecretService kubernetesSecretService;

	@Mock
	private DeploymentNamingService deploymentNamingService;

	@Mock
	private HelmValuesService helmValuesService;

	@Mock
	private HelmCommandService helmCommandService;

	@Mock
	private DeploymentReadinessService deploymentReadinessService;

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
			kubernetesSecretService,
			deploymentNamingService,
			helmValuesService,
			helmCommandService,
			deploymentReadinessService,
			properties
		);
	}

	@Test
	void deployReturnsFailedResultWhenHelmInstallFails() {
		ClusterDeploymentRequest request = new ClusterDeploymentRequest(
			"db-my-db",
			"ns-my-db",
			null,
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
	}
}
