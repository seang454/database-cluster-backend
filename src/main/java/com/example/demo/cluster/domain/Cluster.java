package com.example.demo.cluster.domain;

import java.util.ArrayList;
import java.util.List;

import com.example.demo.cluster.domain.enumtype.ClusterEnvironment;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
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
@Table(name = "cluster")
public class Cluster extends BaseEntity {

	@Column(nullable = false, length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private ClusterEnvironment environment = ClusterEnvironment.PRODUCTION;

	@Column(length = 255)
	private String domain;

	@Column(name = "external_ip", length = 50)
	private String externalIp;

	@Column(name = "deployment_name", length = 100)
	private String deploymentName;

	@Column(name = "deployment_namespace", length = 100)
	private String deploymentNamespace;

	@Embedded
	private ClusterPlatformConfig platformConfig = new ClusterPlatformConfig();

	@JdbcTypeCode(SqlTypes.LONGVARCHAR)
	@Column(columnDefinition = "text")
	private String notes;

	@OneToMany(mappedBy = "cluster", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<DatabaseInstance> databaseInstances = new ArrayList<>();

	@OneToMany(mappedBy = "cluster", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<SecretRef> secretRefs = new ArrayList<>();
}
