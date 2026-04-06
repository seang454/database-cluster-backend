package com.example.demo.cluster.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.demo.cluster.config.ClusterDeploymentProperties;
import com.example.demo.cluster.exception.ClusterDeploymentException;
import com.example.demo.cluster.model.CommandResult;
import com.example.demo.cluster.model.HelmConnectionResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HelmConnectivityServiceTest {

	@Mock
	private HelmCommandService helmCommandService;

	private ClusterDeploymentProperties properties;
	private HelmConnectivityService helmConnectivityService;

	@BeforeEach
	void setUp() {
		properties = new ClusterDeploymentProperties();
		properties.setHelmExecutable("helm");
		properties.setChartPath("oci://ghcr.io/seang454/db-cluster");
		properties.setChartVersion("4.0.1");
		helmConnectivityService = new HelmConnectivityService(helmCommandService, properties);
	}

	@Test
	void probeChartSourceReturnsCommandDetailsOnSuccess() {
		when(helmCommandService.showChart()).thenReturn(new CommandResult(0, "apiVersion: v2", ""));

		HelmConnectionResponse response = helmConnectivityService.probeChartSource();

		assertThat(response.success()).isTrue();
		assertThat(response.chartPath()).isEqualTo("oci://ghcr.io/seang454/db-cluster");
		assertThat(response.chartVersion()).isEqualTo("4.0.1");
		assertThat(response.command()).containsExactly("helm", "show", "chart", "oci://ghcr.io/seang454/db-cluster", "--version", "4.0.1");
	}

	@Test
	void probeChartSourceThrowsWhenHelmCannotReachRegistry() {
		when(helmCommandService.showChart()).thenReturn(new CommandResult(1, "", "timed out"));

		assertThatThrownBy(() -> helmConnectivityService.probeChartSource())
			.isInstanceOf(ClusterDeploymentException.class)
			.hasMessageContaining("Helm chart test failed: timed out");
	}
}
