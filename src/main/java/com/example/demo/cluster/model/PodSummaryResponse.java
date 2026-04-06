package com.example.demo.cluster.model;

import java.util.List;

public record PodSummaryResponse(
	String namespace,
	int total,
	List<PodSummary> pods
) {
	public record PodSummary(
		String name,
		String phase,
		String podIP,
		String nodeName
	) {
	}
}
