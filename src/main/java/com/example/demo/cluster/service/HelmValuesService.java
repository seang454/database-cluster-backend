package com.example.demo.cluster.service;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.demo.cluster.dto.CassandraConfigRequest;
import com.example.demo.cluster.dto.ClusterDeploymentRequest;
import com.example.demo.cluster.dto.DatabaseBackupRequest;
import com.example.demo.cluster.dto.DatabaseInstanceRequest;
import com.example.demo.cluster.dto.DatabaseResourceRequest;
import com.example.demo.cluster.dto.MongoConfigRequest;
import com.example.demo.cluster.dto.MysqlConfigRequest;
import com.example.demo.cluster.dto.PostgresqlConfigRequest;
import com.example.demo.cluster.dto.RedisConfigRequest;
import com.example.demo.cluster.exception.ClusterDeploymentException;
import com.example.demo.cluster.model.DeploymentTarget;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

@Service
public class HelmValuesService {

	public Path writeValuesFile(ClusterDeploymentRequest request, DeploymentTarget target) {
		try {
			Path file = Files.createTempFile("cluster-values-" + target.releaseName() + "-", ".yaml");
			try (Writer writer = Files.newBufferedWriter(file)) {
				yaml().dump(render(request), writer);
			}
			return file;
		}
		catch (IOException exception) {
			throw new ClusterDeploymentException("Failed to write generated values file", exception);
		}
	}

	private Map<String, Object> render(ClusterDeploymentRequest request) {
		Map<String, Object> root = new LinkedHashMap<>();
		put(root, "cloudflare.enabled", request.cluster() != null && request.cluster().platformConfig() != null
			? request.cluster().platformConfig().cloudflareEnabled()
			: null);
		put(root, "cloudflare.zoneName", request.cluster() != null && request.cluster().platformConfig() != null
			? request.cluster().platformConfig().cloudflareZoneName()
			: null);
		put(root, "vault.enabled", request.cluster() != null && request.cluster().platformConfig() != null
			? request.cluster().platformConfig().vaultEnabled()
			: null);

		disableAllDatabases(root);

		DatabaseInstanceRequest database = request.database();
		if (database == null || database.engine() == null) {
			throw new ClusterDeploymentException("Exactly one database configuration is required");
		}

		String section = database.engine().name().toLowerCase();
		put(root, section + ".enabled", database.enabled() != null ? database.enabled() : Boolean.TRUE);
		put(root, section + ".operator.enabled", false);
		put(root, section + ".externalAccess.enabled", database.externalAccessEnabled());
		put(root, section + ".externalAccess.publicHostnames", database.publicHostnames());
		put(root, section + ".tls.enabled", database.tlsEnabled());
		put(root, section + ".tls.mode", database.tlsMode() != null ? database.tlsMode().name().toLowerCase() : null);
		put(root, section + ".cluster.instances", database.instances());
		put(root, section + ".storage.size", database.storageSize());
		put(root, section + ".storage.storageClass", database.storageClass());

		DatabaseResourceRequest resource = database.resource();
		if (resource != null) {
			put(root, section + ".cluster.resources.requests.cpu", resource.cpuRequest());
			put(root, section + ".cluster.resources.requests.memory", resource.memRequest());
			put(root, section + ".cluster.resources.limits.cpu", resource.cpuLimit());
			put(root, section + ".cluster.resources.limits.memory", resource.memLimit());
		}

		DatabaseBackupRequest backup = database.backup();
		if (backup != null) {
			put(root, section + ".backup.enabled", backup.enabled());
			put(root, section + ".backup.destinationPath", backup.destinationPath());
			put(root, section + ".backup.credentialSecret", backup.credentialSecret());
			put(root, section + ".backup.retentionPolicy", backup.retentionPolicy());
			put(root, section + ".backup.schedule", backup.schedule());
		}

		switch (database.engine().name()) {
			case "POSTGRESQL" -> renderPostgresql(root, database.postgresql());
			case "MONGODB" -> renderMongo(root, database.mongo());
			case "MYSQL" -> renderMysql(root, database.mysql());
			case "REDIS" -> renderRedis(root, database.redis());
			case "CASSANDRA" -> renderCassandra(root, database.cassandra());
			default -> {
			}
		}
		return root;
	}

	private void disableAllDatabases(Map<String, Object> root) {
		put(root, "postgresql.enabled", false);
		put(root, "mongodb.enabled", false);
		put(root, "mysql.enabled", false);
		put(root, "redis.enabled", false);
		put(root, "cassandra.enabled", false);
	}

	private void renderPostgresql(Map<String, Object> root, PostgresqlConfigRequest request) {
		if (request == null) {
			return;
		}
		put(root, "postgresql.storage.wal.enabled", request.walEnabled());
		put(root, "postgresql.storage.wal.size", request.walSize());
		put(root, "postgresql.cluster.bootstrap.database", request.bootstrapDatabase());
		put(root, "postgresql.cluster.bootstrap.owner", request.bootstrapOwner());
	}

	private void renderMongo(Map<String, Object> root, MongoConfigRequest request) {
		if (request == null) {
			return;
		}
		put(root, "mongodb.externalAccess.replicaSetHorizons.enabled", request.replicaSetHorizonsEnabled());
		put(root, "mongodb.externalAccess.replicaSetHorizons.basePort", request.replicaSetHorizonsBasePort());
	}

	private void renderMysql(Map<String, Object> root, MysqlConfigRequest request) {
		if (request == null) {
			return;
		}
		put(root, "mysql.haproxy.size", request.haproxySize());
	}

	private void renderRedis(Map<String, Object> root, RedisConfigRequest request) {
		if (request == null) {
			return;
		}
		put(root, "redis.exporter.enabled", request.exporterEnabled());
	}

	private void renderCassandra(Map<String, Object> root, CassandraConfigRequest request) {
		if (request == null) {
			return;
		}
		put(root, "cassandra.cluster.config.clusterName", request.clusterName());
		put(root, "cassandra.cluster.config.datacenter", request.datacenter());
		put(root, "cassandra.tls.requireClientAuth", request.requireClientAuth());
	}

	@SuppressWarnings("unchecked")
	private void put(Map<String, Object> root, String path, Object value) {
		if (value == null) {
			return;
		}
		if (value instanceof String text && !StringUtils.hasText(text)) {
			return;
		}
		if (value instanceof List<?> list && list.isEmpty()) {
			return;
		}

		String[] parts = path.split("\\.");
		Map<String, Object> current = root;
		for (int i = 0; i < parts.length - 1; i++) {
			current = (Map<String, Object>) current.computeIfAbsent(parts[i], key -> new LinkedHashMap<String, Object>());
		}
		current.put(parts[parts.length - 1], value);
	}

	private Yaml yaml() {
		DumperOptions options = new DumperOptions();
		options.setPrettyFlow(true);
		options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
		options.setIndent(2);
		return new Yaml(options);
	}
}
