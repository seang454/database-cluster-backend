package com.example.demo.cluster.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.example.demo.cluster.domain.enumtype.DatabaseEngine;
import com.example.demo.cluster.domain.enumtype.TlsMode;
import com.example.demo.cluster.dto.ClusterDeploymentRequest;
import com.example.demo.cluster.dto.ClusterPlatformConfigRequest;
import com.example.demo.cluster.dto.ClusterRequest;
import com.example.demo.cluster.dto.DatabaseInstanceRequest;
import com.example.demo.cluster.dto.DatabaseResourceRequest;
import com.example.demo.cluster.dto.DeploymentSecretsRequest;
import com.example.demo.cluster.dto.MongoConfigRequest;
import com.example.demo.cluster.dto.PostgresqlConfigRequest;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class HelmValuesServiceTest {

	private final HelmValuesService service = new HelmValuesService();
	private final Yaml yaml = new Yaml();

	@Test
	void rendersPostgresqlValuesUsingChartOwnedCredentialsAndStorageBlocks() {
		ClusterDeploymentRequest request = new ClusterDeploymentRequest(
			"db-my-db",
			"ns-my-db",
			new ClusterRequest(
				"my-db",
				null,
				null,
				"35.194.146.154",
				null,
				new ClusterPlatformConfigRequest(true, "seang.shop", false),
				null
			),
			new DatabaseInstanceRequest(
				DatabaseEngine.POSTGRESQL,
				true,
				(short) 3,
				"10Gi",
				"longhorn",
				false,
				null,
				List.of("postgres-db.seang.shop"),
				true,
				TlsMode.OPERATOR,
				null,
				null,
				false,
				null,
				new DatabaseResourceRequest("250m", "512Mi", "1500m", "2Gi", null),
				null,
				new PostgresqlConfigRequest(true, "2Gi", "appdb", "appuser"),
				null,
				null,
				null,
				null
			),
			new DeploymentSecretsRequest("secret", null, null, null, null, "cf-token")
		);

		Map<String, Object> values = yaml.load(service.render(request));
		Map<String, Object> postgresql = map(values.get("postgresql"));

		assertThat(map(postgresql.get("credentials"))).containsEntry("superuser", "secret").containsEntry("admin", "secret");
		assertThat(map(postgresql.get("cluster"))).containsEntry("instances", 3);
		assertThat(map(postgresql.get("storage"))).containsEntry("size", "10Gi").containsEntry("storageClass", "longhorn");
		assertThat(map(map(postgresql.get("storage")).get("wal"))).containsEntry("enabled", true).containsEntry("size", "2Gi");
		assertThat(map(values.get("cloudflare"))).containsEntry("enabled", true).containsEntry("zoneName", "seang.shop")
			.containsEntry("externalIP", "35.194.146.154");
	}

	@Test
	void rendersMongoReplicaSetHorizonsAndCredentialsAtExpectedPaths() {
		ClusterDeploymentRequest request = new ClusterDeploymentRequest(
			"db-mongo",
			"ns-mongo",
			new ClusterRequest("mongo", null, null, null, null, null, null),
			new DatabaseInstanceRequest(
				DatabaseEngine.MONGODB,
				true,
				(short) 3,
				"10Gi",
				"longhorn",
				true,
				null,
				List.of("mongo-db-0.seang.shop", "mongo-db-1.seang.shop", "mongo-db-2.seang.shop"),
				false,
				TlsMode.CERT_MANAGER,
				null,
				null,
				null,
				null,
				new DatabaseResourceRequest("250m", "1Gi", "1500m", "3Gi", null),
				null,
				null,
				new MongoConfigRequest(true, 27017),
				null,
				null,
				null
			),
			new DeploymentSecretsRequest(null, "mongo-secret", null, null, null, null)
		);

		Map<String, Object> values = yaml.load(service.render(request));
		Map<String, Object> mongodb = map(values.get("mongodb"));

		assertThat(map(mongodb.get("storage"))).containsEntry("size", "10Gi").containsEntry("storageClass", "longhorn");
		assertThat(map(map(mongodb.get("externalAccess")).get("replicaSetHorizons"))).containsEntry("enabled", true)
			.containsEntry("basePort", 27017);
		assertThat(map(mongodb.get("credentials"))).containsEntry("clusterAdminPassword", "mongo-secret")
			.containsEntry("replicationKey", "mongo-secret");
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> map(Object value) {
		return (Map<String, Object>) value;
	}
}
