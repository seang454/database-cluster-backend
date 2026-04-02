package com.example.demo.cluster.domain;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.example.demo.cluster.domain.enumtype.DatabaseEngine;
import com.example.demo.cluster.domain.enumtype.TlsMode;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "database_instance")
public class DatabaseInstance extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cluster_id", nullable = false)
	private Cluster cluster;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private DatabaseEngine engine;

	@Column(nullable = false)
	private Boolean enabled = Boolean.FALSE;

	@Column(nullable = false)
	private Short instances = 1;

	@Column(name = "storage_size", length = 50)
	private String storageSize;

	@Column(name = "storage_class", length = 100)
	private String storageClass;

	@Column(name = "external_access_enabled", nullable = false)
	private Boolean externalAccessEnabled = Boolean.FALSE;

	@Column
	private Integer port;

	@ElementCollection
	@CollectionTable(name = "database_instance_public_hostname", joinColumns = @JoinColumn(name = "database_instance_id"))
	@OrderColumn(name = "hostname_order")
	@Column(name = "hostname", nullable = false, length = 255)
	private List<String> publicHostnames = new ArrayList<>();

	@Column(name = "tls_enabled", nullable = false)
	private Boolean tlsEnabled = Boolean.FALSE;

	@Enumerated(EnumType.STRING)
	@Column(name = "tls_mode", length = 30)
	private TlsMode tlsMode = TlsMode.DISABLED;

	@Column(name = "tls_secret_name", length = 150)
	private String tlsSecretName;

	@Column(name = "tls_ca_secret_name", length = 150)
	private String tlsCaSecretName;

	@Column(name = "monitoring_enabled", nullable = false)
	private Boolean monitoringEnabled = Boolean.FALSE;

	@Column(name = "last_deployed_at")
	private OffsetDateTime lastDeployedAt;

	@Lob
	private String notes;

	@Embedded
	private PostgresqlConfig postgresqlConfig = new PostgresqlConfig();

	@Embedded
	private MysqlConfig mysqlConfig = new MysqlConfig();

	@Embedded
	private MongoConfig mongoConfig = new MongoConfig();

	@Embedded
	private RedisConfig redisConfig = new RedisConfig();

	@Embedded
	private CassandraConfig cassandraConfig = new CassandraConfig();

	@OneToOne(mappedBy = "databaseInstance", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private DatabaseResource databaseResource;

	@OneToOne(mappedBy = "databaseInstance", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private DatabaseBackup databaseBackup;
}
