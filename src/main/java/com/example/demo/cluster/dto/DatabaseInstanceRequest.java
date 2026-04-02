package com.example.demo.cluster.dto;

import java.util.List;

import com.example.demo.cluster.domain.enumtype.DatabaseEngine;
import com.example.demo.cluster.domain.enumtype.TlsMode;

public record DatabaseInstanceRequest(
	DatabaseEngine engine,
	Boolean enabled,
	Short instances,
	String storageSize,
	String storageClass,
	Boolean externalAccessEnabled,
	Integer port,
	List<String> publicHostnames,
	Boolean tlsEnabled,
	TlsMode tlsMode,
	String tlsSecretName,
	String tlsCaSecretName,
	Boolean monitoringEnabled,
	String notes,
	DatabaseResourceRequest resource,
	DatabaseBackupRequest backup,
	PostgresqlConfigRequest postgresql,
	MongoConfigRequest mongo,
	MysqlConfigRequest mysql,
	RedisConfigRequest redis,
	CassandraConfigRequest cassandra
) {
}
