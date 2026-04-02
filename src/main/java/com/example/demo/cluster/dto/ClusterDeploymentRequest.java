package com.example.demo.cluster.dto;

public record ClusterDeploymentRequest(
	String releaseName,
	String namespace,
	ClusterRequest cluster,
	DatabaseInstanceRequest database,
	DeploymentSecretsRequest secrets
) {
}
