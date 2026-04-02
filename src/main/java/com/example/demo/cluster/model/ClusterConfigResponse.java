package com.example.demo.cluster.model;

import java.util.UUID;

import com.example.demo.cluster.domain.enumtype.ClusterEnvironment;
import com.example.demo.cluster.domain.enumtype.DatabaseEngine;

public record ClusterConfigResponse(
	UUID clusterId,
	String name,
	ClusterEnvironment environment,
	DatabaseEngine engine,
	String releaseName,
	String namespace
) {
}
