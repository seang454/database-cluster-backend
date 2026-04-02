package com.example.demo.cluster.model;

public record DeploymentTarget(
	String releaseName,
	String namespace
) {
}
