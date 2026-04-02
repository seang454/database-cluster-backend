package com.example.demo.cluster.repository;

import java.util.Optional;
import java.util.UUID;

import com.example.demo.cluster.domain.DatabaseResource;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DatabaseResourceRepository extends JpaRepository<DatabaseResource, UUID> {

	Optional<DatabaseResource> findByDatabaseInstanceId(UUID databaseInstanceId);
}
