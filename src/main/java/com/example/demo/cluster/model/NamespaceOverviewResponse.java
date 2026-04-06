package com.example.demo.cluster.model;

public record NamespaceOverviewResponse(
	String namespace,
	int podCount,
	int activePodCount,
	int serviceCount,
	int persistentVolumeClaimCount,
	int secretCount,
	int databaseResourceCount
) {
}
