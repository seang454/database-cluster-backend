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
import com.example.demo.cluster.domain.enumtype.DatabaseEngine;
import com.example.demo.cluster.domain.enumtype.TlsMode;

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
		cluster.setNotes(request.notes());
		cluster.setPlatformConfig(toPlatformConfig(request.platformConfig()));
	}

	public DatabaseInstance toDatabaseInstance(DatabaseInstanceRequest request, Cluster cluster) {
		DatabaseInstance databaseInstance = new DatabaseInstance();
		databaseInstance.setCluster(cluster);
		databaseInstance.setEngine(request.engine());
		databaseInstance.setEnabled(request.enabled() != null ? request.enabled() : Boolean.TRUE);
		databaseInstance.setInstances(request.instances() != null ? request.instances() : 1);
		databaseInstance.setStorageSize(request.storageSize());
		databaseInstance.setStorageClass(request.storageClass());
		databaseInstance.setExternalAccessEnabled(defaultExternalAccessEnabled(request.engine(), request.externalAccessEnabled()));
		databaseInstance.setPort(request.port());
		databaseInstance.setPublicHostnames(request.publicHostnames() != null ? new ArrayList<>(request.publicHostnames()) : new ArrayList<>());
		databaseInstance.setTlsEnabled(defaultTlsEnabled(request.engine(), request.tlsEnabled()));
		databaseInstance.setTlsMode(defaultTlsMode(request.engine(), request.tlsMode()));
		databaseInstance.setTlsSecretName(request.tlsSecretName());
		databaseInstance.setTlsCaSecretName(request.tlsCaSecretName());
		databaseInstance.setMonitoringEnabled(Boolean.TRUE.equals(request.monitoringEnabled()));
		databaseInstance.setNotes(request.notes());
		databaseInstance.setPostgresqlConfig(toPostgresqlConfig(request.engine(), request.postgresql()));
		databaseInstance.setMongoConfig(toMongoConfig(request.mongo()));
		databaseInstance.setMysqlConfig(toMysqlConfig(request.mysql()));
		databaseInstance.setRedisConfig(toRedisConfig(request.redis()));
		databaseInstance.setCassandraConfig(toCassandraConfig(request.cassandra()));
		databaseInstance.setDatabaseResource(toDatabaseResource(request.resource(), databaseInstance));
		databaseInstance.setDatabaseBackup(toDatabaseBackup(request.engine(), request.backup(), databaseInstance));
		return databaseInstance;
	}

	private ClusterPlatformConfig toPlatformConfig(ClusterPlatformConfigRequest request) {
		ClusterPlatformConfig config = new ClusterPlatformConfig();
		if (request == null) {
			return config;
		}
		if (request.ingressEnabled() != null) {
			config.setIngressEnabled(request.ingressEnabled());
		}
		if (request.ingressClassName() != null) {
			config.setIngressClassName(request.ingressClassName());
		}
		if (request.externalTcpProxyEnabled() != null) {
			config.setExternalTcpProxyEnabled(request.externalTcpProxyEnabled());
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

	private DatabaseBackup toDatabaseBackup(DatabaseEngine engine, DatabaseBackupRequest request, DatabaseInstance databaseInstance) {
		if (request == null) {
			return null;
		}
		DatabaseBackup backup = new DatabaseBackup();
		backup.setDatabaseInstance(databaseInstance);
		backup.setEnabled(request.enabled() != null ? request.enabled() : Boolean.TRUE);
		backup.setDestinationPath(request.destinationPath());
		backup.setCredentialSecret(request.credentialSecret());
		backup.setRetentionPolicy(request.retentionPolicy());
		backup.setSchedule(request.schedule());
		return backup;
	}

	private PostgresqlConfig toPostgresqlConfig(DatabaseEngine engine, PostgresqlConfigRequest request) {
		PostgresqlConfig config = new PostgresqlConfig();
		if (request == null) {
			config.setWalEnabled(engine == DatabaseEngine.POSTGRESQL ? Boolean.TRUE : Boolean.FALSE);
			return config;
		}
		config.setWalEnabled(request.walEnabled() != null
			? request.walEnabled()
			: engine == DatabaseEngine.POSTGRESQL ? Boolean.TRUE : Boolean.FALSE);
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

	private Boolean defaultExternalAccessEnabled(DatabaseEngine engine, Boolean value) {
		if (value != null) {
			return value;
		}
		return engine == DatabaseEngine.POSTGRESQL ? Boolean.TRUE : Boolean.FALSE;
	}

	private Boolean defaultTlsEnabled(DatabaseEngine engine, Boolean value) {
		if (value != null) {
			return value;
		}
		return engine == DatabaseEngine.POSTGRESQL ? Boolean.TRUE : Boolean.FALSE;
	}

	private TlsMode defaultTlsMode(DatabaseEngine engine, TlsMode value) {
		if (value != null) {
			return value;
		}
		return engine == DatabaseEngine.POSTGRESQL ? TlsMode.CERT_MANAGER : TlsMode.DISABLED;
	}
}
