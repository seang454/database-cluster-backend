package com.example.demo.cluster.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Embeddable
public class PostgresqlConfig {

	@Column(name = "postgresql_wal_enabled")
	private Boolean walEnabled = Boolean.FALSE;

	@Column(name = "postgresql_wal_size", length = 50)
	private String walSize;

	@Column(name = "postgresql_bootstrap_database", length = 100)
	private String bootstrapDatabase;

	@Column(name = "postgresql_bootstrap_owner", length = 100)
	private String bootstrapOwner;
}
