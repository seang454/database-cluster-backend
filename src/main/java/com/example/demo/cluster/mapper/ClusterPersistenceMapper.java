package com.example.demo.cluster.mapper;

import java.util.ArrayList;

import com.example.demo.cluster.domain.CassandraConfig;
import com.example.demo.cluster.domain.Cluster;
import com.example.demo.cluster.domain.ClusterPlatformConfig;
import com.example.demo.cluster.domain.DatabaseBackup;
import com.example.demo.cluster.domain.DatabaseInstance;
import com.example.demo.cluster.domain.DatabaseResource;
import com.example.demo.cluster.domain.MongoConfig;
import com.example.demo.cluster.domain.MysqlConfig;
import com.example.demo.cluster.domain.PostgresqlConfig;
import com.example.demo.cluster.domain.RedisConfig;
import com.example.demo.cluster.dto.CassandraConfigRequest;
import com.example.demo.cluster.dto.ClusterDeploymentRequest;
import com.example.demo.cluster.dto.ClusterPlatformConfigRequest;
import com.example.demo.cluster.dto.ClusterRequest;
import com.example.demo.cluster.dto.DatabaseBackupRequest;
import com.example.demo.cluster.dto.DatabaseInstanceRequest;
import com.example.demo.cluster.dto.DatabaseResourceRequest;
import com.example.demo.cluster.dto.MongoConfigRequest;
import com.example.demo.cluster.dto.MysqlConfigRequest;
import com.example.demo.cluster.dto.PostgresqlConfigRequest;
import com.example.demo.cluster.dto.RedisConfigRequest;

import org.springframework.stereotype.Component;

@Component
public class ClusterPersistenceMapper {

	public Cluster toCluster(ClusterDeploymentRequest request) {
		Cluster cluster = new Cluster();
		applyCluster(cluster, request.cluster());
		DatabaseInstance databaseInstance = toDatabaseInstance(request.database(), cluster);
		cluster.getDatabaseInstances().clear();
		cluster.getDatabaseInstances().add(databaseInstance);
		return cluster;
	}

	public void applyCluster(Cluster cluster, ClusterRequest request) {
		if (request == null) {
			return;
		}
		cluster.setName(request.name());
		cluster.setEnvironment(request.environment());
		cluster.setDomain(request.domain());
		cluster.setExternalIp(request.externalIp());
		cluster.setDeploymentName(request.deploymentName());
		cluster.setNotes(request.notes());
		cluster.setPlatformConfig(toPlatformConfig(request.platformConfig()));
	}

	public DatabaseInstance toDatabaseInstance(DatabaseInstanceRequest request, Cluster cluster) {
		DatabaseInstance databaseInstance = new DatabaseInstance();
		databaseInstance.setCluster(cluster);
		databaseInstance.setEngine(request.engine());
		databaseInstance.setEnabled(Boolean.TRUE.equals(request.enabled()));
		databaseInstance.setInstances(request.instances() != null ? request.instances() : 1);
		databaseInstance.setStorageSize(request.storageSize());
		databaseInstance.setStorageClass(request.storageClass());
		databaseInstance.setExternalAccessEnabled(Boolean.TRUE.equals(request.externalAccessEnabled()));
		databaseInstance.setPort(request.port());
		databaseInstance.setPublicHostnames(request.publicHostnames() != null ? new ArrayList<>(request.publicHostnames()) : new ArrayList<>());
		databaseInstance.setTlsEnabled(Boolean.TRUE.equals(request.tlsEnabled()));
		databaseInstance.setTlsMode(request.tlsMode());
		databaseInstance.setTlsSecretName(request.tlsSecretName());
		databaseInstance.setTlsCaSecretName(request.tlsCaSecretName());
		databaseInstance.setMonitoringEnabled(Boolean.TRUE.equals(request.monitoringEnabled()));
		databaseInstance.setNotes(request.notes());
		databaseInstance.setPostgresqlConfig(toPostgresqlConfig(request.postgresql()));
		databaseInstance.setMongoConfig(toMongoConfig(request.mongo()));
		databaseInstance.setMysqlConfig(toMysqlConfig(request.mysql()));
		databaseInstance.setRedisConfig(toRedisConfig(request.redis()));
		databaseInstance.setCassandraConfig(toCassandraConfig(request.cassandra()));
		databaseInstance.setDatabaseResource(toDatabaseResource(request.resource(), databaseInstance));
		databaseInstance.setDatabaseBackup(toDatabaseBackup(request.backup(), databaseInstance));
		return databaseInstance;
	}

	private ClusterPlatformConfig toPlatformConfig(ClusterPlatformConfigRequest request) {
		ClusterPlatformConfig config = new ClusterPlatformConfig();
		if (request == null) {
			return config;
		}
		if (request.cloudflareEnabled() != null) {
			config.setCloudflareEnabled(request.cloudflareEnabled());
		}
		config.setCloudflareZoneName(request.cloudflareZoneName());
		if (request.vaultEnabled() != null) {
			config.setVaultEnabled(request.vaultEnabled());
		}
		return config;
	}

	private DatabaseResource toDatabaseResource(DatabaseResourceRequest request, DatabaseInstance databaseInstance) {
		if (request == null) {
			return null;
		}
		DatabaseResource resource = new DatabaseResource();
		resource.setDatabaseInstance(databaseInstance);
		resource.setCpuRequest(request.cpuRequest());
		resource.setMemRequest(request.memRequest());
		resource.setCpuLimit(request.cpuLimit());
		resource.setMemLimit(request.memLimit());
		if (request.resourceProfile() != null) {
			resource.setResourceProfile(request.resourceProfile());
		}
		return resource;
	}

	private DatabaseBackup toDatabaseBackup(DatabaseBackupRequest request, DatabaseInstance databaseInstance) {
		if (request == null) {
			return null;
		}
		DatabaseBackup backup = new DatabaseBackup();
		backup.setDatabaseInstance(databaseInstance);
		backup.setEnabled(Boolean.TRUE.equals(request.enabled()));
		backup.setDestinationPath(request.destinationPath());
		backup.setCredentialSecret(request.credentialSecret());
		backup.setRetentionPolicy(request.retentionPolicy());
		backup.setSchedule(request.schedule());
		return backup;
	}

	private PostgresqlConfig toPostgresqlConfig(PostgresqlConfigRequest request) {
		PostgresqlConfig config = new PostgresqlConfig();
		if (request == null) {
			return config;
		}
		config.setWalEnabled(Boolean.TRUE.equals(request.walEnabled()));
		config.setWalSize(request.walSize());
		config.setBootstrapDatabase(request.bootstrapDatabase());
		config.setBootstrapOwner(request.bootstrapOwner());
		return config;
	}

	private MongoConfig toMongoConfig(MongoConfigRequest request) {
		MongoConfig config = new MongoConfig();
		if (request == null) {
			return config;
		}
		config.setReplicaSetHorizonsEnabled(Boolean.TRUE.equals(request.replicaSetHorizonsEnabled()));
		config.setReplicaSetHorizonsBasePort(request.replicaSetHorizonsBasePort());
		return config;
	}

	private MysqlConfig toMysqlConfig(MysqlConfigRequest request) {
		MysqlConfig config = new MysqlConfig();
		if (request == null) {
			return config;
		}
		config.setHaproxySize(request.haproxySize());
		return config;
	}

	private RedisConfig toRedisConfig(RedisConfigRequest request) {
		RedisConfig config = new RedisConfig();
		if (request == null) {
			return config;
		}
		config.setExporterEnabled(Boolean.TRUE.equals(request.exporterEnabled()));
		return config;
	}

	private CassandraConfig toCassandraConfig(CassandraConfigRequest request) {
		CassandraConfig config = new CassandraConfig();
		if (request == null) {
			return config;
		}
		config.setClusterName(request.clusterName());
		config.setDatacenter(request.datacenter());
		config.setRequireClientAuth(Boolean.TRUE.equals(request.requireClientAuth()));
		return config;
	}
}
