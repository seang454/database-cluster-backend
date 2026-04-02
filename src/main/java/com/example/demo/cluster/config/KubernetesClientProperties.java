package com.example.demo.cluster.config;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kubernetes.client")
public class KubernetesClientProperties {

	private Mode mode = Mode.AUTO;
	private Path kubeconfigPath;
	private String defaultNamespace = "default";
	private Duration connectTimeout = Duration.ofSeconds(10);
	private Duration readTimeout = Duration.ofSeconds(30);
	private Duration writeTimeout = Duration.ofSeconds(30);

	public Mode getMode() {
		return mode;
	}

	public void setMode(Mode mode) {
		this.mode = mode;
	}

	public Path getKubeconfigPath() {
		return kubeconfigPath;
	}

	public void setKubeconfigPath(Path kubeconfigPath) {
		this.kubeconfigPath = kubeconfigPath;
	}

	public String getDefaultNamespace() {
		return defaultNamespace;
	}

	public void setDefaultNamespace(String defaultNamespace) {
		this.defaultNamespace = defaultNamespace;
	}

	public Duration getConnectTimeout() {
		return connectTimeout;
	}

	public void setConnectTimeout(Duration connectTimeout) {
		this.connectTimeout = connectTimeout;
	}

	public Duration getReadTimeout() {
		return readTimeout;
	}

	public void setReadTimeout(Duration readTimeout) {
		this.readTimeout = readTimeout;
	}

	public Duration getWriteTimeout() {
		return writeTimeout;
	}

	public void setWriteTimeout(Duration writeTimeout) {
		this.writeTimeout = writeTimeout;
	}

	public enum Mode {
		AUTO,
		KUBECONFIG,
		IN_CLUSTER
	}
}
