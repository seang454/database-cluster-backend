package com.example.demo.cluster.model;

import java.util.List;

public record DatabaseResourceSummaryResponse(
	String namespace,
	int total,
	List<DatabaseResourceSummary> resources
) {
	public record DatabaseResourceSummary(
		String kind,
		String name,
		String apiVersion,
		boolean ready,
		String phase,
		String message
	) {
	}
}
