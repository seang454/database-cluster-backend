package com.example.demo.cluster.model;

import java.util.List;

public record ServiceSummaryResponse(
	String namespace,
	int total,
	List<ServiceSummary> services
) {
	public record ServiceSummary(
		String name,
		String type,
		String clusterIP,
		List<String> ports
	) {
	}
}
