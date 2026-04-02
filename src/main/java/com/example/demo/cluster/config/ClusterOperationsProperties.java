package com.example.demo.cluster.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cluster.operations")
public class ClusterOperationsProperties {

	private String kubectlExecutable = "kubectl";
	private String externalSecretsNamespace = "external-secrets";
	private String externalSecretsReleaseName = "external-secrets";
	private String externalSecretsChart = "ext-secrets/external-secrets";
	private String cnpgNamespace = "cnpg-system";
	private String cnpgReleaseName = "cnpg";
	private String cnpgChart = "cnpg/cloudnative-pg";
	private String psmdbReleaseName = "psmdb-operator";
	private String psmdbChart = "percona/psmdb-operator";
	private String pxcReleaseName = "pxc-operator";
	private String pxcChart = "percona/pxc-operator";
	private String redisOperatorReleaseName = "redis-operator";
	private String redisOperatorChart = "ot-helm/redis-operator";
	private String redisOperatorTimeout = "10m";
	private String k8ssandraReleaseName = "k8ssandra-operator";
	private String k8ssandraChart = "k8ssandra/k8ssandra-operator";
	private String certManagerNamespace = "cert-manager";
	private String certManagerReleaseName = "cert-manager";
	private String certManagerChart = "jetstack/cert-manager";
	private String cnpgVersion = "0.21.0";
	private String psmdbVersion = "1.15.0";
	private String pxcVersion = "1.14.0";
	private String redisOperatorVersion = "0.24.0";
	private String k8ssandraVersion = "1.14.0";
	private String certManagerVersion = "v1.15.3";

	public String getKubectlExecutable() {
		return kubectlExecutable;
	}

	public void setKubectlExecutable(String kubectlExecutable) {
		this.kubectlExecutable = kubectlExecutable;
	}

	public String getExternalSecretsNamespace() {
		return externalSecretsNamespace;
	}

	public void setExternalSecretsNamespace(String externalSecretsNamespace) {
		this.externalSecretsNamespace = externalSecretsNamespace;
	}

	public String getExternalSecretsReleaseName() {
		return externalSecretsReleaseName;
	}

	public void setExternalSecretsReleaseName(String externalSecretsReleaseName) {
		this.externalSecretsReleaseName = externalSecretsReleaseName;
	}

	public String getExternalSecretsChart() {
		return externalSecretsChart;
	}

	public void setExternalSecretsChart(String externalSecretsChart) {
		this.externalSecretsChart = externalSecretsChart;
	}

	public String getCnpgNamespace() {
		return cnpgNamespace;
	}

	public void setCnpgNamespace(String cnpgNamespace) {
		this.cnpgNamespace = cnpgNamespace;
	}

	public String getCnpgReleaseName() {
		return cnpgReleaseName;
	}

	public void setCnpgReleaseName(String cnpgReleaseName) {
		this.cnpgReleaseName = cnpgReleaseName;
	}

	public String getCnpgChart() {
		return cnpgChart;
	}

	public void setCnpgChart(String cnpgChart) {
		this.cnpgChart = cnpgChart;
	}

	public String getPsmdbReleaseName() {
		return psmdbReleaseName;
	}

	public void setPsmdbReleaseName(String psmdbReleaseName) {
		this.psmdbReleaseName = psmdbReleaseName;
	}

	public String getPsmdbChart() {
		return psmdbChart;
	}

	public void setPsmdbChart(String psmdbChart) {
		this.psmdbChart = psmdbChart;
	}

	public String getPxcReleaseName() {
		return pxcReleaseName;
	}

	public void setPxcReleaseName(String pxcReleaseName) {
		this.pxcReleaseName = pxcReleaseName;
	}

	public String getPxcChart() {
		return pxcChart;
	}

	public void setPxcChart(String pxcChart) {
		this.pxcChart = pxcChart;
	}

	public String getRedisOperatorReleaseName() {
		return redisOperatorReleaseName;
	}

	public void setRedisOperatorReleaseName(String redisOperatorReleaseName) {
		this.redisOperatorReleaseName = redisOperatorReleaseName;
	}

	public String getRedisOperatorChart() {
		return redisOperatorChart;
	}

	public void setRedisOperatorChart(String redisOperatorChart) {
		this.redisOperatorChart = redisOperatorChart;
	}

	public String getRedisOperatorTimeout() {
		return redisOperatorTimeout;
	}

	public void setRedisOperatorTimeout(String redisOperatorTimeout) {
		this.redisOperatorTimeout = redisOperatorTimeout;
	}

	public String getK8ssandraReleaseName() {
		return k8ssandraReleaseName;
	}

	public void setK8ssandraReleaseName(String k8ssandraReleaseName) {
		this.k8ssandraReleaseName = k8ssandraReleaseName;
	}

	public String getK8ssandraChart() {
		return k8ssandraChart;
	}

	public void setK8ssandraChart(String k8ssandraChart) {
		this.k8ssandraChart = k8ssandraChart;
	}

	public String getCertManagerNamespace() {
		return certManagerNamespace;
	}

	public void setCertManagerNamespace(String certManagerNamespace) {
		this.certManagerNamespace = certManagerNamespace;
	}

	public String getCertManagerReleaseName() {
		return certManagerReleaseName;
	}

	public void setCertManagerReleaseName(String certManagerReleaseName) {
		this.certManagerReleaseName = certManagerReleaseName;
	}

	public String getCertManagerChart() {
		return certManagerChart;
	}

	public void setCertManagerChart(String certManagerChart) {
		this.certManagerChart = certManagerChart;
	}

	public String getCnpgVersion() {
		return cnpgVersion;
	}

	public void setCnpgVersion(String cnpgVersion) {
		this.cnpgVersion = cnpgVersion;
	}

	public String getPsmdbVersion() {
		return psmdbVersion;
	}

	public void setPsmdbVersion(String psmdbVersion) {
		this.psmdbVersion = psmdbVersion;
	}

	public String getPxcVersion() {
		return pxcVersion;
	}

	public void setPxcVersion(String pxcVersion) {
		this.pxcVersion = pxcVersion;
	}

	public String getRedisOperatorVersion() {
		return redisOperatorVersion;
	}

	public void setRedisOperatorVersion(String redisOperatorVersion) {
		this.redisOperatorVersion = redisOperatorVersion;
	}

	public String getK8ssandraVersion() {
		return k8ssandraVersion;
	}

	public void setK8ssandraVersion(String k8ssandraVersion) {
		this.k8ssandraVersion = k8ssandraVersion;
	}

	public String getCertManagerVersion() {
		return certManagerVersion;
	}

	public void setCertManagerVersion(String certManagerVersion) {
		this.certManagerVersion = certManagerVersion;
	}
}
