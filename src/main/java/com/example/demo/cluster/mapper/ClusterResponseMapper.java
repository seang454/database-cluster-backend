package com.example.demo.cluster.mapper;

import com.example.demo.cluster.domain.Cluster;
import com.example.demo.cluster.domain.DatabaseInstance;
import com.example.demo.cluster.domain.DeploymentRecord;
import com.example.demo.cluster.model.ClusterConfigResponse;
import com.example.demo.cluster.model.DeploymentRecordResponse;
import com.example.demo.cluster.model.DeploymentTarget;

import org.springframework.stereotype.Component;

@Component
public class ClusterResponseMapper {

	public ClusterConfigResponse toConfigResponse(Cluster cluster, DatabaseInstance database, DeploymentTarget target) {
		return new ClusterConfigResponse(
			cluster.getId(),
			cluster.getName(),
			cluster.getEnvironment(),
			database != null ? database.getEngine() : null,
			target.releaseName(),
			target.namespace()
		);
	}

	public DeploymentRecordResponse toRecordResponse(DeploymentRecord record) {
		return new DeploymentRecordResponse(
			record.getId(),
			record.getCluster().getId(),
			record.getDatabaseEngine(),
			record.getReleaseName(),
			record.getNamespace(),
			record.getStatus(),
			record.getExitCode(),
			record.getStartedAt(),
			record.getFinishedAt()
		);
	}

	public String defaultNamespace(Cluster cluster) {
		return cluster.getName() != null
			? "ns-" + cluster.getName().toLowerCase().replaceAll("[^a-z0-9-]+", "-")
			: null;
	}
}
