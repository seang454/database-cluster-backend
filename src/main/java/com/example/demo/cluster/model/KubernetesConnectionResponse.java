package com.example.demo.cluster.model;

import java.util.List;

public record KubernetesConnectionResponse(
	boolean connected,
	int namespaceCount,
	List<String> namespaces
) {
}
