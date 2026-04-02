package com.example.demo.cluster.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cluster.deployment") //  Consider adding more properties for deployment configuration from file application.properties, such as default resource limits, replica counts, etc.

public class ClusterDeploymentProperties {

	// this number of properties is same as the number of properties in ClusterControlProperties and KubernetesClientProperties, which is a good balance for maintainability and configurability. Adding more properties can provide more flexibility but may also increase complexity. It's important to consider the specific needs of your application and deployment scenarios when deciding on the number of properties to include.
	private String helmExecutable = "helm";
	private String chartReference;
	private String chartVersion;
	private String defaultReleasePrefix = "db";
	private String defaultNamespacePrefix = "ns";
	private Path defaultValuesFile;


	public String getHelmExecutable() {
		return helmExecutable;
	}

	public void setHelmExecutable(String helmExecutable) {
		this.helmExecutable = helmExecutable;
	}

	public String getChartReference() {
		return chartReference;
	}

	public void setChartReference(String chartReference) {
		this.chartReference = chartReference;
	}

	public String getChartVersion() {
		return chartVersion;
	}

	public void setChartVersion(String chartVersion) {
		this.chartVersion = chartVersion;
	}

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

	public Path getDefaultValuesFile() {
		return defaultValuesFile;
	}

	public void setDefaultValuesFile(Path defaultValuesFile) {
		this.defaultValuesFile = defaultValuesFile;
	}
}
