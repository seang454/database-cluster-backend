package com.example.demo.cluster.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "database_backup")
public class DatabaseBackup extends BaseEntity {

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "database_instance_id", nullable = false, unique = true)
	private DatabaseInstance databaseInstance;

	@Column(nullable = false)
	private Boolean enabled = Boolean.FALSE;

	@Column(name = "destination_path", length = 255)
	private String destinationPath;

	@Column(name = "credential_secret", length = 150)
	private String credentialSecret;

	@Column(name = "retention_policy", length = 100)
	private String retentionPolicy;

	@Column(length = 100)
	private String schedule;

	@Column(name = "last_backup_at")
	private OffsetDateTime lastBackupAt;
}
