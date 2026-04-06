package com.example.demo.cluster.model;

import java.util.List;

public record PersistentVolumeClaimSummaryResponse(
	String namespace,
	int total,
	List<PersistentVolumeClaimSummary> claims
) {
	public record PersistentVolumeClaimSummary(
		String name,
		String status,
		String storageClassName,
		String volumeName,
		String requestedStorage
	) {
	}
}
