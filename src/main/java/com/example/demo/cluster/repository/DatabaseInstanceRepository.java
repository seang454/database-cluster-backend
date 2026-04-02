package com.example.demo.cluster.repository;

import java.util.List;
import java.util.UUID;

import com.example.demo.cluster.domain.DatabaseInstance;
import com.example.demo.cluster.domain.enumtype.DatabaseEngine;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DatabaseInstanceRepository extends JpaRepository<DatabaseInstance, UUID> {

	List<DatabaseInstance> findByClusterId(UUID clusterId);

	List<DatabaseInstance> findByClusterIdAndEngine(UUID clusterId, DatabaseEngine engine);
}
