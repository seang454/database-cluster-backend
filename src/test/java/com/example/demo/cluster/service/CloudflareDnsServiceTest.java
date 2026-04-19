package com.example.demo.cluster.service;

import static org.mockito.Mockito.when;

import java.util.List;

import com.example.demo.cluster.config.ClusterDeploymentProperties;
import com.example.demo.cluster.domain.Cluster;
import com.example.demo.cluster.domain.ClusterPlatformConfig;
import com.example.demo.cluster.domain.DatabaseInstance;
import com.example.demo.cluster.domain.enumtype.DatabaseEngine;
import com.example.demo.cluster.dto.DeploymentSecretsRequest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CloudflareDnsServiceTest {

	@Mock
	private ClusterDeploymentProperties properties;

	@Test
	void upsertClusterRecordsSkipsWhenCloudflareConfigurationIsIncomplete() {
		when(properties.getDefaultCloudflareApiToken()).thenReturn("");

		CloudflareDnsService service = new CloudflareDnsService(properties);

		Cluster cluster = new Cluster();
		cluster.setDomain("seang.shop");
		cluster.setExternalIp("35.194.146.154");
		ClusterPlatformConfig platformConfig = new ClusterPlatformConfig();
		platformConfig.setCloudflareEnabled(Boolean.TRUE);
		platformConfig.setCloudflareZoneName("seang.shop");
		cluster.setPlatformConfig(platformConfig);

		DatabaseInstance database = new DatabaseInstance();
		database.setEngine(DatabaseEngine.POSTGRESQL);
		database.setPublicHostnames(List.of("postgres-db.seang.shop"));

		service.upsertClusterRecords(cluster, database, new DeploymentSecretsRequest(null, null, null, null, null, null));
	}

	@Test
	void upsertClusterRecordsSkipsUnresolvedTemplateTokens() {
		when(properties.getDefaultCloudflareApiToken()).thenReturn("");

		CloudflareDnsService service = new CloudflareDnsService(properties);

		Cluster cluster = new Cluster();
		cluster.setDomain("seang.shop");
		cluster.setExternalIp("35.194.146.154");
		ClusterPlatformConfig platformConfig = new ClusterPlatformConfig();
		platformConfig.setCloudflareEnabled(Boolean.TRUE);
		platformConfig.setCloudflareZoneName("seang.shop");
		cluster.setPlatformConfig(platformConfig);

		DatabaseInstance database = new DatabaseInstance();
		database.setEngine(DatabaseEngine.POSTGRESQL);
		database.setPublicHostnames(List.of("postgres-db.seang.shop"));

		service.upsertClusterRecords(
			cluster,
			database,
			new DeploymentSecretsRequest(null, null, null, null, null, "{{cloudflareApiToken}}")
		);
	}
}
