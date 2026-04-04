package com.example.demo.cluster.config;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kubernetes.client")
public class KubernetesClientProperties {

	private Mode mode = Mode.AUTO;
	private Path kubeconfigPath;
	private String masterUrl;
	private Duration connectTimeout = Duration.ofSeconds(10);
	private Duration readTimeout = Duration.ofSeconds(30);
	private boolean trustCertificates;
	private boolean disableHostnameVerification;

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

	public String getMasterUrl() {
		return masterUrl;
	}

	public void setMasterUrl(String masterUrl) {
		this.masterUrl = masterUrl;
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

	public boolean isTrustCertificates() {
		return trustCertificates;
	}

	public void setTrustCertificates(boolean trustCertificates) {
		this.trustCertificates = trustCertificates;
	}

	public boolean isDisableHostnameVerification() {
		return disableHostnameVerification;
	}

	public void setDisableHostnameVerification(boolean disableHostnameVerification) {
		this.disableHostnameVerification = disableHostnameVerification;
	}

	public enum Mode {
		AUTO,
		KUBECONFIG,
		IN_CLUSTER
	}
}
