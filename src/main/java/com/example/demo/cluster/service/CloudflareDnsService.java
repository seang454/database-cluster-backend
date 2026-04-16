package com.example.demo.cluster.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import com.example.demo.cluster.config.ClusterDeploymentProperties;
import com.example.demo.cluster.domain.Cluster;
import com.example.demo.cluster.domain.DatabaseInstance;
import com.example.demo.cluster.exception.ClusterDeploymentException;
import com.example.demo.cluster.dto.DeploymentSecretsRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CloudflareDnsService {

	private static final Logger log = LoggerFactory.getLogger(CloudflareDnsService.class);
	private static final URI BASE_URI = URI.create("https://api.cloudflare.com/client/v4/");
	private static final int DEFAULT_TTL = 1;
	private static final boolean DEFAULT_PROXIED = false;

	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;
	private final ClusterDeploymentProperties properties;

	public CloudflareDnsService(ClusterDeploymentProperties properties) {
		this.httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(15))
			.build();
		this.objectMapper = new ObjectMapper();
		this.properties = properties;
	}

	public void upsertClusterRecords(Cluster cluster, DatabaseInstance database, DeploymentSecretsRequest secrets) {
		if (!shouldManageDns(cluster, database)) {
			return;
		}
		String zoneName = resolveZoneName(cluster);
		String apiToken = resolveApiToken(secrets);
		String content = resolveContentIp(cluster);
		List<String> hostnames = database.getPublicHostnames();
		if (!hasZoneReference(cluster) || !StringUtils.hasText(apiToken) || !StringUtils.hasText(content) || hostnames == null || hostnames.isEmpty()) {
			throw new ClusterDeploymentException("Cloudflare DNS management requires zone id or zone name, token, external IP, and at least one hostname");
		}
		String zoneId = resolveZoneId(cluster, zoneName, apiToken);
		for (String hostname : hostnames) {
			if (!StringUtils.hasText(hostname)) {
				continue;
			}
			upsertARecord(zoneId, apiToken, hostname, content);
		}
	}

	public void deleteClusterRecords(Cluster cluster, DatabaseInstance database, DeploymentSecretsRequest secrets) {
		if (!shouldManageDns(cluster, database)) {
			return;
		}
		String zoneName = resolveZoneName(cluster);
		String apiToken = resolveApiToken(secrets);
		List<String> hostnames = database.getPublicHostnames();
		if (!hasZoneReference(cluster) || !StringUtils.hasText(apiToken) || hostnames == null || hostnames.isEmpty()) {
			return;
		}
		String zoneId = resolveZoneId(cluster, zoneName, apiToken);
		for (String hostname : hostnames) {
			if (!StringUtils.hasText(hostname)) {
				continue;
			}
			deleteARecords(zoneId, apiToken, hostname);
		}
	}

	private boolean shouldManageDns(Cluster cluster, DatabaseInstance database) {
		return cluster != null
			&& cluster.getPlatformConfig() != null
			&& Boolean.TRUE.equals(cluster.getPlatformConfig().getCloudflareEnabled())
			&& database != null
			&& database.getPublicHostnames() != null
			&& !database.getPublicHostnames().isEmpty();
	}

	private String resolveZoneName(Cluster cluster) {
		if (cluster == null || cluster.getPlatformConfig() == null) {
			return null;
		}
		if (StringUtils.hasText(cluster.getPlatformConfig().getCloudflareZoneName())) {
			return cluster.getPlatformConfig().getCloudflareZoneName();
		}
		return cluster.getDomain();
	}

	private String resolveZoneId(Cluster cluster, String zoneName, String apiToken) {
		if (cluster != null
			&& cluster.getPlatformConfig() != null
			&& StringUtils.hasText(cluster.getPlatformConfig().getCloudflareZoneId())) {
			return cluster.getPlatformConfig().getCloudflareZoneId();
		}
		return resolveZoneId(zoneName, apiToken);
	}

	private String resolveContentIp(Cluster cluster) {
		return cluster != null ? cluster.getExternalIp() : null;
	}

	private boolean hasZoneReference(Cluster cluster) {
		return cluster != null
			&& cluster.getPlatformConfig() != null
			&& (StringUtils.hasText(cluster.getPlatformConfig().getCloudflareZoneId())
				|| StringUtils.hasText(cluster.getPlatformConfig().getCloudflareZoneName())
				|| StringUtils.hasText(cluster.getDomain()));
	}

	private String resolveApiToken(DeploymentSecretsRequest secrets) {
		if (secrets != null && StringUtils.hasText(secrets.cloudflareApiToken())) {
			return secrets.cloudflareApiToken();
		}
		return properties.getDefaultCloudflareApiToken();
	}

	private String resolveZoneId(String zoneName, String apiToken) {
		HttpRequest request = baseRequestBuilder("/zones?name=" + encode(zoneName))
			.GET()
			.build();
		JsonNode response = sendJsonRequest(request, apiToken, "lookup Cloudflare zone " + zoneName);
		JsonNode result = response.path("result");
		if (!result.isArray() || result.isEmpty()) {
			throw new ClusterDeploymentException("Cloudflare zone not found: " + zoneName);
		}
		String zoneId = result.get(0).path("id").asText(null);
		if (!StringUtils.hasText(zoneId)) {
			throw new ClusterDeploymentException("Cloudflare zone id missing for " + zoneName);
		}
		return zoneId;
	}

	private void upsertARecord(String zoneId, String apiToken, String hostname, String content) {
		JsonNode existing = listDnsRecords(zoneId, apiToken, hostname);
		String body = buildARecordBody(hostname, content);
		if (existing.isArray() && !existing.isEmpty()) {
			String recordId = existing.get(0).path("id").asText(null);
			if (!StringUtils.hasText(recordId)) {
				throw new ClusterDeploymentException("Cloudflare DNS record id missing for " + hostname);
			}
			HttpRequest request = baseRequestBuilder("/zones/" + zoneId + "/dns_records/" + recordId)
				.PUT(HttpRequest.BodyPublishers.ofString(body))
				.build();
			sendJsonRequest(request, apiToken, "update Cloudflare record " + hostname);
			log.info("Updated Cloudflare DNS record {} -> {}", hostname, content);
			return;
		}
		HttpRequest request = baseRequestBuilder("/zones/" + zoneId + "/dns_records")
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.build();
		sendJsonRequest(request, apiToken, "create Cloudflare record " + hostname);
		log.info("Created Cloudflare DNS record {} -> {}", hostname, content);
	}

	private void deleteARecords(String zoneId, String apiToken, String hostname) {
		JsonNode existing = listDnsRecords(zoneId, apiToken, hostname);
		if (!existing.isArray() || existing.isEmpty()) {
			return;
		}
		for (JsonNode record : existing) {
			String recordId = record.path("id").asText(null);
			if (!StringUtils.hasText(recordId)) {
				continue;
			}
			HttpRequest request = baseRequestBuilder("/zones/" + zoneId + "/dns_records/" + recordId)
				.DELETE()
				.build();
			sendJsonRequest(request, apiToken, "delete Cloudflare record " + hostname);
			log.info("Deleted Cloudflare DNS record {}", hostname);
		}
	}

	private JsonNode listDnsRecords(String zoneId, String apiToken, String hostname) {
		HttpRequest request = baseRequestBuilder("/zones/" + zoneId + "/dns_records?type=A&name=" + encode(hostname))
			.GET()
			.build();
		return sendJsonRequest(request, apiToken, "list Cloudflare records for " + hostname).path("result");
	}

	private JsonNode sendJsonRequest(HttpRequest request, String apiToken, String action) {
		try {
			HttpRequest authenticated = HttpRequest.newBuilder(request.uri())
				.timeout(Duration.ofSeconds(30))
				.header("Authorization", "Bearer " + apiToken)
				.header("Content-Type", "application/json")
				.method(request.method(), request.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()))
				.build();
			HttpResponse<String> response = httpClient.send(authenticated, HttpResponse.BodyHandlers.ofString());
			JsonNode root = parseResponseBody(response.body(), action);
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new ClusterDeploymentException("Cloudflare API " + action + " failed with HTTP " + response.statusCode() + ": " + summarizeErrors(root, response.body()));
			}
			if (!root.path("success").asBoolean(false)) {
				throw new ClusterDeploymentException("Cloudflare API " + action + " returned an error: " + summarizeErrors(root, response.body()));
			}
			return root;
		}
		catch (IOException exception) {
			throw new ClusterDeploymentException("Failed to parse Cloudflare API response while trying to " + action, exception);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new ClusterDeploymentException("Cloudflare API request interrupted while trying to " + action, exception);
		}
	}

	private JsonNode parseResponseBody(String body, String action) throws IOException {
		if (!StringUtils.hasText(body)) {
			throw new ClusterDeploymentException("Cloudflare API returned an empty response while trying to " + action);
		}
		return objectMapper.readTree(body);
	}

	private String summarizeErrors(JsonNode root, String fallbackBody) {
		if (root != null && root.has("errors") && root.get("errors").isArray() && !root.get("errors").isEmpty()) {
			return root.get("errors").toString();
		}
		return StringUtils.hasText(fallbackBody) ? fallbackBody : "no response body";
	}

	private HttpRequest.Builder baseRequestBuilder(String path) {
		String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
		return HttpRequest.newBuilder(BASE_URI.resolve(normalizedPath))
			.timeout(Duration.ofSeconds(30));
	}

	private String buildARecordBody(String hostname, String content) {
		try {
			return objectMapper.writeValueAsString(
				java.util.Map.of(
					"type", "A",
					"name", hostname,
					"content", content,
					"ttl", DEFAULT_TTL,
					"proxied", DEFAULT_PROXIED
				)
			);
		}
		catch (IOException exception) {
			throw new ClusterDeploymentException("Failed to build Cloudflare request body", exception);
		}
	}

	private String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
