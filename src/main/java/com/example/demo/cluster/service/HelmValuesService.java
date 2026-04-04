package com.example.demo.cluster.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.demo.cluster.domain.enumtype.DatabaseEngine;
import com.example.demo.cluster.domain.enumtype.TlsMode;
import com.example.demo.cluster.dto.ClusterDeploymentRequest;
import com.example.demo.cluster.dto.ClusterPlatformConfigRequest;
import com.example.demo.cluster.dto.DatabaseInstanceRequest;
import com.example.demo.cluster.dto.DatabaseResourceRequest;
import com.example.demo.cluster.dto.DeploymentSecretsRequest;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

@Service
public class HelmValuesService {

	public Path renderOverrideValues(ClusterDeploymentRequest request) {
		try {
			// Create a short-lived override file in the OS temp directory for this deploy only.
			Path tempFile = Files.createTempFile("db-cluster-overrides-", ".yaml");
			Files.writeString(tempFile, render(request), StandardCharsets.UTF_8);
			return tempFile;
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to write Helm override values", exception);
		}
	}

	public String render(ClusterDeploymentRequest request) {
		Map<String, Object> values = new LinkedHashMap<>();
		// The Vault bootstrap stack is installed separately. Spring only deploys the
		// database release and must not let the chart try to own vault-transit.
		values.put("vaultTransit", Map.of("enabled", false));
		values.put("vault", Map.of("enabled", false));
		ClusterPlatformConfigRequest platformConfig = request.cluster() != null ? request.cluster().platformConfig() : null;
		if (platformConfig != null) {
			Map<String, Object> cloudflare = new LinkedHashMap<>();
			if (platformConfig.cloudflareEnabled() != null) {
				cloudflare.put("enabled", platformConfig.cloudflareEnabled());
			}
			if (StringUtils.hasText(platformConfig.cloudflareZoneName())) {
				cloudflare.put("zoneName", platformConfig.cloudflareZoneName());
			}
			if (request.cluster() != null && StringUtils.hasText(request.cluster().externalIp())) {
				cloudflare.put("externalIP", request.cluster().externalIp());
			}
			if (!cloudflare.isEmpty()) {
				values.put("cloudflare", cloudflare);
			}
			if (platformConfig.vaultEnabled() != null) {
				values.put("externalSecrets", Map.of("enabled", platformConfig.vaultEnabled()));
			}
		}

		DatabaseInstanceRequest database = request.database();
		if (database != null && database.engine() != null) {
			values.put(databaseSection(database.engine()), renderDatabase(database, request.secrets()));
		}

		DumperOptions options = new DumperOptions();
		options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
		options.setPrettyFlow(true);
		options.setIndent(2);
		Yaml yaml = new Yaml(options);
		return yaml.dump(values);
	}

	private Map<String, Object> renderDatabase(DatabaseInstanceRequest database, DeploymentSecretsRequest secrets) {
		Map<String, Object> values = new LinkedHashMap<>();
		if (database.enabled() != null) {
			values.put("enabled", database.enabled());
		}
		if (database.externalAccessEnabled() != null || hasAnyPublicHostnames(database)) {
			Map<String, Object> externalAccess = new LinkedHashMap<>();
			if (database.externalAccessEnabled() != null) {
				externalAccess.put("enabled", database.externalAccessEnabled());
			}
			if (database.publicHostnames() != null && !database.publicHostnames().isEmpty()) {
				externalAccess.put("publicHostnames", new ArrayList<>(database.publicHostnames()));
			}
			values.put("externalAccess", externalAccess);
		}
		if (database.tlsEnabled() != null || database.tlsMode() != null || StringUtils.hasText(database.tlsSecretName()) || StringUtils.hasText(database.tlsCaSecretName())) {
			Map<String, Object> tls = new LinkedHashMap<>();
			if (database.tlsEnabled() != null) {
				tls.put("enabled", database.tlsEnabled());
			}
			if (database.tlsMode() != null) {
				tls.put("mode", toChartTlsMode(database.tlsMode()));
			}
			if (StringUtils.hasText(database.tlsSecretName())) {
				tls.put(secretKey(database.engine()), database.tlsSecretName());
			}
			if (StringUtils.hasText(database.tlsCaSecretName())) {
				String caKey = switch (database.engine()) {
					case POSTGRESQL -> "existingCASecretName";
					case MONGODB, MYSQL -> "sslInternalSecretName";
					case REDIS -> "caSecretName";
					case CASSANDRA -> "clientSecretName";
				};
				tls.put(caKey, database.tlsCaSecretName());
			}
			values.put("tls", tls);
		}

		switch (database.engine()) {
			case POSTGRESQL -> renderPostgresql(values, database, secrets);
			case MONGODB -> renderMongo(values, database, secrets);
			case MYSQL -> renderMysql(values, database, secrets);
			case REDIS -> renderRedis(values, database, secrets);
			case CASSANDRA -> renderCassandra(values, database, secrets);
		}
		return values;
	}

	private void renderPostgresql(Map<String, Object> values, DatabaseInstanceRequest database, DeploymentSecretsRequest secrets) {
		Map<String, Object> cluster = new LinkedHashMap<>();
		if (database.instances() != null && database.instances() > 0) {
			cluster.put("instances", database.instances());
		}
		Map<String, Object> bootstrap = new LinkedHashMap<>();
		if (database.postgresql() != null) {
			if (StringUtils.hasText(database.postgresql().bootstrapDatabase())) {
				bootstrap.put("database", database.postgresql().bootstrapDatabase());
			}
			if (StringUtils.hasText(database.postgresql().bootstrapOwner())) {
				bootstrap.put("owner", database.postgresql().bootstrapOwner());
			}
		}
		if (!bootstrap.isEmpty()) {
			cluster.put("bootstrap", bootstrap);
		}
		Map<String, Object> resources = resourceBlock(database.resource());
		if (!resources.isEmpty()) {
			cluster.put("resources", resources);
		}
		if (database.monitoringEnabled() != null) {
			cluster.put("monitoring", Map.of("enabled", database.monitoringEnabled()));
		}
		values.put("cluster", cluster);
		Map<String, Object> storage = storage(database);
		if (database.postgresql() != null && database.postgresql().walEnabled() != null) {
			Map<String, Object> wal = new LinkedHashMap<>();
			wal.put("enabled", database.postgresql().walEnabled());
			if (StringUtils.hasText(database.postgresql().walSize())) {
				wal.put("size", database.postgresql().walSize());
			}
			storage.put("wal", wal);
		}
		if (!storage.isEmpty()) {
			values.put("storage", storage);
		}
		if (secrets != null && StringUtils.hasText(secrets.pgPassword())) {
			values.put("credentials", Map.of(
				"superuser", secrets.pgPassword(),
				"admin", secrets.pgPassword()
			));
		}
	}

	private void renderMongo(Map<String, Object> values, DatabaseInstanceRequest database, DeploymentSecretsRequest secrets) {
		Map<String, Object> cluster = new LinkedHashMap<>();
		if (database.instances() != null && database.instances() > 0) {
			cluster.put("instances", database.instances());
		}
		Map<String, Object> resources = resourceBlock(database.resource());
		if (!resources.isEmpty()) {
			cluster.put("resources", resources);
		}
		values.put("cluster", cluster);
		Map<String, Object> storage = storage(database);
		if (!storage.isEmpty()) {
			values.put("storage", storage);
		}
		if (database.mongo() != null && (database.mongo().replicaSetHorizonsEnabled() != null || database.mongo().replicaSetHorizonsBasePort() != null)) {
			Map<String, Object> externalAccess = nestedMap(values, "externalAccess");
			Map<String, Object> replicaSetHorizons = new LinkedHashMap<>();
			if (database.mongo().replicaSetHorizonsEnabled() != null) {
				replicaSetHorizons.put("enabled", database.mongo().replicaSetHorizonsEnabled());
			}
			if (database.mongo().replicaSetHorizonsBasePort() != null) {
				replicaSetHorizons.put("basePort", database.mongo().replicaSetHorizonsBasePort());
			}
			externalAccess.put("replicaSetHorizons", replicaSetHorizons);
		}
		if (secrets != null && StringUtils.hasText(secrets.mongoPassword())) {
			values.put("credentials", Map.of(
				"clusterAdminPassword", secrets.mongoPassword(),
				"userAdminPassword", secrets.mongoPassword(),
				"clusterMonitorPassword", secrets.mongoPassword(),
				"databaseAdminPassword", secrets.mongoPassword(),
				"backupPassword", secrets.mongoPassword(),
				"replicationKey", secrets.mongoPassword()
			));
		}
	}

	private void renderMysql(Map<String, Object> values, DatabaseInstanceRequest database, DeploymentSecretsRequest secrets) {
		Map<String, Object> cluster = new LinkedHashMap<>();
		if (database.instances() != null && database.instances() > 0) {
			cluster.put("instances", database.instances());
		}
		Map<String, Object> resources = resourceBlock(database.resource());
		if (!resources.isEmpty()) {
			cluster.put("resources", resources);
		}
		Map<String, Object> storage = storage(database);
		if (!storage.isEmpty()) {
			values.put("storage", storage);
		}
		if (database.mysql() != null && database.mysql().haproxySize() != null) {
			values.put("haproxy", Map.of("size", database.mysql().haproxySize()));
		}
		values.put("cluster", cluster);
		if (secrets != null && StringUtils.hasText(secrets.mysqlPassword())) {
			values.put("credentials", Map.of(
				"rootPassword", secrets.mysqlPassword(),
				"replicationPassword", secrets.mysqlPassword(),
				"monitorPassword", secrets.mysqlPassword(),
				"clusterCheckPassword", secrets.mysqlPassword()
			));
		}
	}

	private void renderRedis(Map<String, Object> values, DatabaseInstanceRequest database, DeploymentSecretsRequest secrets) {
		Map<String, Object> cluster = new LinkedHashMap<>();
		if (database.instances() != null && database.instances() > 0) {
			cluster.put("instances", database.instances());
		}
		Map<String, Object> resources = resourceBlock(database.resource());
		if (!resources.isEmpty()) {
			cluster.put("resources", resources);
		}
		Map<String, Object> storage = storage(database);
		if (!storage.isEmpty()) {
			values.put("storage", storage);
		}
		if (database.monitoringEnabled() != null) {
			values.put("exporter", Map.of("enabled", database.monitoringEnabled()));
		}
		values.put("cluster", cluster);
		if (secrets != null && StringUtils.hasText(secrets.redisPassword())) {
			values.put("auth", Map.of("password", secrets.redisPassword()));
		}
	}

	private void renderCassandra(Map<String, Object> values, DatabaseInstanceRequest database, DeploymentSecretsRequest secrets) {
		Map<String, Object> cluster = new LinkedHashMap<>();
		if (database.instances() != null && database.instances() > 0) {
			cluster.put("instances", database.instances());
		}
		Map<String, Object> config = new LinkedHashMap<>();
		if (database.cassandra() != null) {
			if (StringUtils.hasText(database.cassandra().clusterName())) {
				config.put("clusterName", database.cassandra().clusterName());
			}
			if (StringUtils.hasText(database.cassandra().datacenter())) {
				config.put("datacenter", database.cassandra().datacenter());
			}
			if (database.cassandra().requireClientAuth() != null) {
				config.put("requireClientAuth", database.cassandra().requireClientAuth());
			}
		}
		if (!config.isEmpty()) {
			cluster.put("config", config);
		}
		Map<String, Object> resources = resourceBlock(database.resource());
		if (!resources.isEmpty()) {
			cluster.put("resources", resources);
		}
		Map<String, Object> storage = storage(database);
		if (!storage.isEmpty()) {
			values.put("storage", storage);
		}
		if (database.tlsEnabled() != null) {
			Map<String, Object> tls = new LinkedHashMap<>();
			tls.put("enabled", database.tlsEnabled());
			tls.put("clientEncryption", database.tlsEnabled());
			if (database.cassandra() != null && database.cassandra().requireClientAuth() != null) {
				tls.put("requireClientAuth", database.cassandra().requireClientAuth());
			}
			values.put("tls", tls);
		}
		values.put("cluster", cluster);
		if (secrets != null && StringUtils.hasText(secrets.cassandraPassword())) {
			values.put("credentials", Map.of("password", secrets.cassandraPassword()));
		}
	}

	private Map<String, Object> storage(DatabaseInstanceRequest database) {
		Map<String, Object> storage = new LinkedHashMap<>();
		if (StringUtils.hasText(database.storageSize())) {
			storage.put("size", database.storageSize());
		}
		if (StringUtils.hasText(database.storageClass())) {
			storage.put("storageClass", database.storageClass());
		}
		return storage;
	}

	private Map<String, Object> resourceBlock(DatabaseResourceRequest request) {
		Map<String, Object> resources = new LinkedHashMap<>();
		if (request == null) {
			return resources;
		}
		Map<String, Object> requests = new LinkedHashMap<>();
		if (StringUtils.hasText(request.cpuRequest())) {
			requests.put("cpu", request.cpuRequest());
		}
		if (StringUtils.hasText(request.memRequest())) {
			requests.put("memory", request.memRequest());
		}
		if (!requests.isEmpty()) {
			resources.put("requests", requests);
		}
		Map<String, Object> limits = new LinkedHashMap<>();
		if (StringUtils.hasText(request.cpuLimit())) {
			limits.put("cpu", request.cpuLimit());
		}
		if (StringUtils.hasText(request.memLimit())) {
			limits.put("memory", request.memLimit());
		}
		if (!limits.isEmpty()) {
			resources.put("limits", limits);
		}
		return resources;
	}

	private boolean hasAnyPublicHostnames(DatabaseInstanceRequest database) {
		return database.publicHostnames() != null && !database.publicHostnames().isEmpty();
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> nestedMap(Map<String, Object> values, String key) {
		return (Map<String, Object>) values.computeIfAbsent(key, ignored -> new LinkedHashMap<String, Object>());
	}

	private String databaseSection(DatabaseEngine engine) {
		return switch (engine) {
			case POSTGRESQL -> "postgresql";
			case MONGODB -> "mongodb";
			case MYSQL -> "mysql";
			case REDIS -> "redis";
			case CASSANDRA -> "cassandra";
		};
	}

	private String secretKey(DatabaseEngine engine) {
		return switch (engine) {
			case POSTGRESQL -> "existingSecretName";
			case MONGODB -> "sslSecretName";
			case MYSQL -> "sslSecretName";
			case REDIS -> "secretName";
			case CASSANDRA -> "serverSecretName";
		};
	}

	private String toChartTlsMode(TlsMode mode) {
		return switch (mode) {
			case DISABLED -> "disabled";
			case OPERATOR -> "operator";
			case CERT_MANAGER -> "certManager";
			case EXISTING_SECRET -> "existingSecret";
		};
	}
}
