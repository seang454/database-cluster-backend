package com.example.demo.cluster.domain;

import java.time.OffsetDateTime;

import com.example.demo.cluster.domain.enumtype.DatabaseEngine;
import com.example.demo.cluster.domain.enumtype.InjectVia;

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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "secret_ref")
public class SecretRef extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cluster_id", nullable = false)
	private Cluster cluster;

	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private DatabaseEngine engine;

	@Column(name = "secret_key", length = 100)
	private String secretKey;

	@Column(name = "vault_path", length = 255)
	private String vaultPath;

	@Column(name = "k8s_secret_name", length = 150)
	private String k8sSecretName;

	@Column(name = "k8s_secret_key", length = 100)
	private String k8sSecretKey;

	@Enumerated(EnumType.STRING)
	@Column(name = "inject_via", length = 30)
	private InjectVia injectVia;

	@Column(name = "last_rotated_at")
	private OffsetDateTime lastRotatedAt;

	@Column(name = "rotation_due_at")
	private OffsetDateTime rotationDueAt;
}
