package com.example.demo.cluster.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.example.demo.cluster.domain.enumtype.DatabaseEngine;
import com.example.demo.cluster.domain.enumtype.DeploymentStatus;

public record DeploymentRecordResponse(
	UUID id,
	UUID clusterId,
	DatabaseEngine databaseEngine,
	String releaseName,
	String namespace,
	DeploymentStatus status,
	Integer exitCode,
	OffsetDateTime startedAt,
	OffsetDateTime finishedAt
) {
}
