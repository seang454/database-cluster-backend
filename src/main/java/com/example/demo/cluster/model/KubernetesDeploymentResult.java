package com.example.demo.cluster.model;

import java.time.Instant;
import java.util.List;

public record KubernetesDeploymentResult(
	String releaseName,
	String namespace,
	List<String> command,
	int exitCode,
	boolean successful,
	String valuesFile,
	String stdout,
	String stderr,
	Instant startedAt,
	Instant finishedAt
) {
}
