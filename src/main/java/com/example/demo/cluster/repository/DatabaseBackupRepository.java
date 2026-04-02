package com.example.demo.cluster.repository;

import java.util.Optional;
import java.util.UUID;

import com.example.demo.cluster.domain.DatabaseBackup;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DatabaseBackupRepository extends JpaRepository<DatabaseBackup, UUID> {

	Optional<DatabaseBackup> findByDatabaseInstanceId(UUID databaseInstanceId);
}
