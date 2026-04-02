package com.example.demo.cluster.repository;

import java.util.List;
import java.util.UUID;

import com.example.demo.cluster.domain.SecretRef;
import com.example.demo.cluster.domain.enumtype.DatabaseEngine;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SecretRefRepository extends JpaRepository<SecretRef, UUID> {

	List<SecretRef> findByClusterId(UUID clusterId);

	List<SecretRef> findByClusterIdAndEngine(UUID clusterId, DatabaseEngine engine);
}
