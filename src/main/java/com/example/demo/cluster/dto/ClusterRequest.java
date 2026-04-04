package com.example.demo.cluster.dto;

import com.example.demo.cluster.domain.enumtype.ClusterEnvironment;

public record ClusterRequest(
	String name,
	ClusterEnvironment environment,
	String domain,
	String externalIp,
	String deploymentName,
	ClusterPlatformConfigRequest platformConfig,
	String notes
) {
}
