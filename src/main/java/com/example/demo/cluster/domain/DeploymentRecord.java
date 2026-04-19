package com.example.demo.cluster.domain;

import java.time.OffsetDateTime;

import com.example.demo.cluster.domain.enumtype.DatabaseEngine;
import com.example.demo.cluster.domain.enumtype.DeploymentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "deployment_record")
public class DeploymentRecord extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cluster_id", nullable = false)
	private Cluster cluster;

	@Enumerated(EnumType.STRING)
	@Column(name = "database_engine", length = 30, nullable = false)
	private DatabaseEngine databaseEngine;

	@Column(name = "release_name", nullable = false, length = 100)
	private String releaseName;

	@Column(nullable = false, length = 100)
	private String namespace;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private DeploymentStatus status = DeploymentStatus.PENDING;

	@Column(name = "values_file", length = 255)
	private String valuesFile;

	@Column(name = "started_at")
	private OffsetDateTime startedAt;

	@Column(name = "finished_at")
	private OffsetDateTime finishedAt;

	@Column(name = "exit_code")
	private Integer exitCode;

	@JdbcTypeCode(SqlTypes.LONGVARCHAR)
	@Column(name = "command_text", columnDefinition = "text")
	private String commandText;

	@JdbcTypeCode(SqlTypes.LONGVARCHAR)
	@Column(columnDefinition = "text")
	private String stdout;

	@JdbcTypeCode(SqlTypes.LONGVARCHAR)
	@Column(columnDefinition = "text")
	private String stderr;
}
