package com.example.demo.cluster.dto;

public record ClusterPlatformConfigRequest(
	Boolean ingressEnabled,
	String ingressClassName,
	Boolean externalTcpProxyEnabled
) {
}
