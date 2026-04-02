package com.example.demo.cluster.repository;

import java.util.List;
import java.util.UUID;

import com.example.demo.cluster.domain.DeploymentRecord;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentRecordRepository extends JpaRepository<DeploymentRecord, UUID> {

	List<DeploymentRecord> findByClusterIdOrderByCreatedAtDesc(UUID clusterId);
}
