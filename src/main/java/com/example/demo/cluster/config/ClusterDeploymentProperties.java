package com.example.demo.cluster.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cluster.deployment")
public class ClusterDeploymentProperties {

	private String defaultReleasePrefix = "db";
	private String defaultNamespacePrefix = "ns";
	private String defaultsFile;
	private String chartPath;
	private String chartVersion;
	private String helmExecutable = "helm";
	private String helmTimeout = "30m";
	private String minioEndpointUrl = "http://my-minio-minio.storage.svc:9000";
	private String minioBucketTimeout = "2m";
	private String minioCredentialsSecretPrefix = "minio-credentials";
	private String minioCredentialsAccessKey = "root-user";
	private String minioCredentialsSecretKey = "root-password";
	private String defaultClusterDomain;
	private String defaultExternalIp;
	private Boolean defaultCloudflareEnabled = Boolean.TRUE;
	private String defaultCloudflareZoneName;
	private String defaultCloudflareZoneId;
	private String defaultCloudflareApiToken;

	public String getDefaultReleasePrefix() {
		return defaultReleasePrefix;
	}

	public void setDefaultReleasePrefix(String defaultReleasePrefix) {
		this.defaultReleasePrefix = defaultReleasePrefix;
	}

	public String getDefaultNamespacePrefix() {
		return defaultNamespacePrefix;
	}

	public void setDefaultNamespacePrefix(String defaultNamespacePrefix) {
		this.defaultNamespacePrefix = defaultNamespacePrefix;
	}

	public String getDefaultsFile() {
		return defaultsFile;
	}

	public void setDefaultsFile(String defaultsFile) {
		this.defaultsFile = defaultsFile;
	}

	public String getChartPath() {
		return chartPath;
	}

	public void setChartPath(String chartPath) {
		this.chartPath = chartPath;
	}

	public String getChartVersion() {
		return chartVersion;
	}

	public void setChartVersion(String chartVersion) {
		this.chartVersion = chartVersion;
	}

	public String getHelmExecutable() {
		return helmExecutable;
	}

	public void setHelmExecutable(String helmExecutable) {
		this.helmExecutable = helmExecutable;
	}

	public String getHelmTimeout() {
		return helmTimeout;
	}

	public void setHelmTimeout(String helmTimeout) {
		this.helmTimeout = helmTimeout;
	}

	public String getMinioEndpointUrl() {
		return minioEndpointUrl;
	}

	public void setMinioEndpointUrl(String minioEndpointUrl) {
		this.minioEndpointUrl = minioEndpointUrl;
	}

	public String getMinioBucketTimeout() {
		return minioBucketTimeout;
	}

	public void setMinioBucketTimeout(String minioBucketTimeout) {
		this.minioBucketTimeout = minioBucketTimeout;
	}

	public String getMinioCredentialsSecretPrefix() {
		return minioCredentialsSecretPrefix;
	}

	public void setMinioCredentialsSecretPrefix(String minioCredentialsSecretPrefix) {
		this.minioCredentialsSecretPrefix = minioCredentialsSecretPrefix;
	}

	public String getMinioCredentialsAccessKey() {
		return minioCredentialsAccessKey;
	}

	public void setMinioCredentialsAccessKey(String minioCredentialsAccessKey) {
		this.minioCredentialsAccessKey = minioCredentialsAccessKey;
	}

	public String getMinioCredentialsSecretKey() {
		return minioCredentialsSecretKey;
	}

	public void setMinioCredentialsSecretKey(String minioCredentialsSecretKey) {
		this.minioCredentialsSecretKey = minioCredentialsSecretKey;
	}

	public String getDefaultClusterDomain() {
		return defaultClusterDomain;
	}

	public void setDefaultClusterDomain(String defaultClusterDomain) {
		this.defaultClusterDomain = defaultClusterDomain;
	}

	public String getDefaultExternalIp() {
		return defaultExternalIp;
	}

	public void setDefaultExternalIp(String defaultExternalIp) {
		this.defaultExternalIp = defaultExternalIp;
	}

	public Boolean getDefaultCloudflareEnabled() {
		return defaultCloudflareEnabled;
	}

	public void setDefaultCloudflareEnabled(Boolean defaultCloudflareEnabled) {
		this.defaultCloudflareEnabled = defaultCloudflareEnabled;
	}

	public String getDefaultCloudflareZoneName() {
		return defaultCloudflareZoneName;
	}

	public void setDefaultCloudflareZoneName(String defaultCloudflareZoneName) {
		this.defaultCloudflareZoneName = defaultCloudflareZoneName;
	}

	public String getDefaultCloudflareZoneId() {
		return defaultCloudflareZoneId;
	}

	public void setDefaultCloudflareZoneId(String defaultCloudflareZoneId) {
		this.defaultCloudflareZoneId = defaultCloudflareZoneId;
	}

	public String getDefaultCloudflareApiToken() {
		return defaultCloudflareApiToken;
	}

	public void setDefaultCloudflareApiToken(String defaultCloudflareApiToken) {
		this.defaultCloudflareApiToken = defaultCloudflareApiToken;
	}
}
