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

	public String getDefaultCloudflareApiToken() {
		return defaultCloudflareApiToken;
	}

	public void setDefaultCloudflareApiToken(String defaultCloudflareApiToken) {
		this.defaultCloudflareApiToken = defaultCloudflareApiToken;
	}
}
