package com.example.demo.cluster.repository;

import java.util.Optional;
import java.util.UUID;

import com.example.demo.cluster.domain.Cluster;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClusterRepository extends JpaRepository<Cluster, UUID> {

	Optional<Cluster> findByName(String name);

	Optional<Cluster> findByDeploymentName(String deploymentName);
}
