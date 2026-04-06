package com.example.demo.cluster.model;

import java.util.List;

public record EventSummaryResponse(
	String namespace,
	int total,
	List<EventSummary> events
) {
	public record EventSummary(
		String type,
		String reason,
		String involvedObjectKind,
		String involvedObjectName,
		String message,
		String lastTimestamp
	) {
	}
}
