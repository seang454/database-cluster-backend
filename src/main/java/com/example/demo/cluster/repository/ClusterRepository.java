package com.example.demo.cluster.repository;

import java.util.Optional;
import java.util.UUID;

import com.example.demo.cluster.domain.Cluster;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface ClusterRepository extends JpaRepository<Cluster, UUID> {

	Optional<Cluster> findByName(String name);

	@EntityGraph(attributePaths = "databaseInstances")
	Optional<Cluster> findByDeploymentName(String deploymentName);

	@EntityGraph(attributePaths = "databaseInstances")
	Optional<Cluster> findByDeploymentNameAndDeploymentNamespace(String deploymentName, String deploymentNamespace);
}
