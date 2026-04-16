package com.example.demo.cluster.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.example.demo.cluster.domain.enumtype.DatabaseEngine;
import com.example.demo.cluster.domain.enumtype.TlsMode;
import com.example.demo.cluster.domain.DatabaseBackup;
import com.example.demo.cluster.dto.ClusterDeploymentRequest;
import com.example.demo.cluster.dto.ClusterPlatformConfigRequest;
import com.example.demo.cluster.dto.ClusterRequest;
import com.example.demo.cluster.dto.DatabaseBackupRequest;
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
				new ClusterPlatformConfigRequest(true, "nginx", true),
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
				new DatabaseBackupRequest(true, null, "minio-credentials", "7d", "0 * * * * *"),
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
		Map<String, Object> ingress = map(values.get("ingress"));
		Map<String, Object> externalTcpProxy = map(values.get("externalTcpProxy"));
		Map<String, Object> backup = map(postgresql.get("backup"));

		assertThat(ingress).containsEntry("className", "nginx").containsEntry("enabled", true);
		assertThat(externalTcpProxy).containsEntry("enabled", true);
		assertThat(postgresql).containsEntry("enabled", true);
		assertThat(map(postgresql.get("credentials"))).containsEntry("superuser", "secret").containsEntry("admin", "secret");
		assertThat(map(postgresql.get("cluster"))).containsEntry("instances", 3);
		assertThat(map(postgresql.get("storage"))).containsEntry("size", "10Gi").containsEntry("storageClass", "longhorn");
		assertThat(map(map(postgresql.get("storage")).get("wal"))).containsEntry("enabled", true).containsEntry("size", "2Gi");
		assertThat(backup).containsEntry("destinationPath", "s3://ns-my-db/db-my-db/db-my-db-postgresql");
		assertThat(map(backup.get("schedule"))).containsEntry("cron", "0 * * * * *").containsEntry("immediate", false);
		assertThat(map(backup.get("s3Credentials"))).containsEntry("accessKeyId", Map.of("secretName", "minio-credentials-db-my-db", "key", "root-user"))
			.containsEntry("secretAccessKey", Map.of("secretName", "minio-credentials-db-my-db", "key", "root-password"));
		assertThat(backup).containsEntry("credentialSecret", "minio-credentials-db-my-db");
		assertThat(values).doesNotContainKey("cloudflare");
	}

	@Test
	void rendersMinimalPostgresqlRequestWithoutOverridingChartDefaults() {
		ClusterDeploymentRequest request = new ClusterDeploymentRequest(
			"db-my-db",
			"ns-my-db",
			new ClusterRequest("my-db", null, null, null),
			new DatabaseInstanceRequest(
				DatabaseEngine.POSTGRESQL,
				null,
				null,
				null,
				null,
				null,
				null,
				List.of("postgres-db.seang.shop"),
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				new PostgresqlConfigRequest(null, null, "appdb", "appuser"),
				null,
				null,
				null,
				null
			),
			new DeploymentSecretsRequest("secret", null, null, null, null, null)
		);

		Map<String, Object> values = yaml.load(service.render(request));
		Map<String, Object> postgresql = map(values.get("postgresql"));

		assertThat(values).doesNotContainKeys("ingress", "externalTcpProxy");
		assertThat(postgresql).containsEntry("enabled", true);
		assertThat(postgresql).doesNotContainKey("storage");
		assertThat(postgresql).doesNotContainKey("externalAccess");
		assertThat(postgresql).doesNotContainKey("tls");
		assertThat(postgresql).doesNotContainKey("backup");
		assertThat(map(postgresql.get("cluster"))).containsEntry("bootstrap", Map.of("database", "appdb", "owner", "appuser"));
		assertThat(map(postgresql.get("cluster"))).doesNotContainKeys("instances", "resources", "monitoring");
	}

	@Test
	void forcesSelectedDatabaseEnabledEvenWhenRequestDisablesIt() {
		ClusterDeploymentRequest request = new ClusterDeploymentRequest(
			"db-my-db",
			"ns-my-db",
			new ClusterRequest("my-db", null, null, null),
			new DatabaseInstanceRequest(
				DatabaseEngine.POSTGRESQL,
				false,
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
				new DatabaseBackupRequest(true, null, "minio-credentials", "7d", "0 * * * * *"),
				new PostgresqlConfigRequest(true, "2Gi", "appdb", "appuser"),
				null,
				null,
				null,
				null
			),
			new DeploymentSecretsRequest("secret", null, null, null, null, null)
		);

		Map<String, Object> values = yaml.load(service.render(request));
		Map<String, Object> postgresql = map(values.get("postgresql"));

		assertThat(postgresql).containsEntry("enabled", true);
	}

	@Test
	void rendersMongoReplicaSetHorizonsAndCredentialsAtExpectedPaths() {
		ClusterDeploymentRequest request = new ClusterDeploymentRequest(
			"db-mongo",
			"ns-mongo",
			new ClusterRequest("mongo", null, null, null),
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
				new DatabaseBackupRequest(true, null, "minio-backup-credentials", null, "10 2 * * *"),
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
		Map<String, Object> backup = map(mongodb.get("backup"));

		assertThat(map(mongodb.get("storage"))).containsEntry("size", "10Gi").containsEntry("storageClass", "longhorn");
		assertThat(map(map(mongodb.get("externalAccess")).get("replicaSetHorizons"))).containsEntry("enabled", true)
			.containsEntry("basePort", 27017);
		assertThat(map(mongodb.get("credentials"))).containsEntry("clusterAdminPassword", "mongo-secret")
			.containsEntry("replicationKey", "mongo-secret");
		assertThat(map(backup.get("s3"))).containsEntry("bucket", "ns-mongo")
			.containsEntry("prefix", "db-mongo/db-mongo-mongodb")
			.containsEntry("credentialSecret", "minio-backup-credentials-db-mongo");
	}

	@Test
	void rendersSharedBackupTargetsForManualBackupOverrides() {
		DatabaseBackup backup = new DatabaseBackup();
		backup.setEnabled(Boolean.TRUE);
		backup.setDestinationPath("s3://custom-bucket/custom-prefix");
		backup.setCredentialSecret("minio-backup-credentials");
		backup.setRetentionPolicy("7d");
		backup.setSchedule("0 * * * * *");

		Map<String, Object> postgresql = map(yaml.load(service.renderBackupOverride(DatabaseEngine.POSTGRESQL, backup, "ns-my-db", "db-my-db", "my-db")));
		Map<String, Object> mongodb = map(yaml.load(service.renderBackupOverride(DatabaseEngine.MONGODB, backup, "ns-mongo", "db-mongo", "mongo")));
		Map<String, Object> mysql = map(yaml.load(service.renderBackupOverride(DatabaseEngine.MYSQL, backup, "ns-mysql", "db-mysql", "mysql")));
		Map<String, Object> cassandra = map(yaml.load(service.renderBackupOverride(DatabaseEngine.CASSANDRA, backup, "ns-cassandra", "db-cassandra", "cassandra")));

		assertThat(map(map(postgresql.get("postgresql")).get("backup"))).containsEntry("destinationPath", "s3://ns-my-db/db-my-db/db-my-db-postgresql");
		assertThat(map(map(postgresql.get("postgresql")).get("backup")).get("schedule")).isEqualTo(Map.of(
			"cron", "0 * * * * *",
			"immediate", false
		));
		assertThat(map(map(postgresql.get("postgresql")).get("backup")).get("s3Credentials")).isEqualTo(Map.of(
			"accessKeyId", Map.of("secretName", "minio-credentials-db-my-db", "key", "root-user"),
			"secretAccessKey", Map.of("secretName", "minio-credentials-db-my-db", "key", "root-password")
		));
		assertThat(map(map(postgresql.get("postgresql")).get("backup"))).containsEntry("credentialSecret", "minio-backup-credentials-db-my-db");
		assertThat(map(mongodb.get("mongodb")).get("backup")).isEqualTo(Map.of(
			"enabled", true,
			"bucket", "ns-mongo",
			"prefix", "db-mongo/db-mongo-mongodb",
			"credentialSecret", "minio-backup-credentials-db-mongo",
			"retentionPolicy", "7d",
			"schedule", "0 * * * * *"
		));
		assertThat(map(mysql.get("mysql")).get("backup")).isEqualTo(Map.of(
			"enabled", true,
			"bucket", "ns-mysql",
			"prefix", "db-mysql/db-mysql-mysql",
			"credentialSecret", "minio-backup-credentials-db-mysql",
			"retentionPolicy", "7d",
			"schedule", "0 * * * * *"
		));
		assertThat(map(cassandra.get("cassandra")).get("backup")).isEqualTo(Map.of(
			"enabled", true,
			"bucket", "ns-cassandra",
			"prefix", "db-cassandra/db-cassandra-cassandra",
			"credentialSecret", "minio-backup-credentials-db-cassandra",
			"retentionPolicy", "7d",
			"schedule", "0 * * * * *"
		));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> map(Object value) {
		return (Map<String, Object>) value;
	}
}
