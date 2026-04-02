package com.example.demo.cluster.domain;

import com.example.demo.cluster.domain.enumtype.ResourceProfile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "database_resource")
public class DatabaseResource extends BaseEntity {

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "database_instance_id", nullable = false, unique = true)
	private DatabaseInstance databaseInstance;

	@Column(name = "cpu_request", length = 50)
	private String cpuRequest;

	@Column(name = "mem_request", length = 50)
	private String memRequest;

	@Column(name = "cpu_limit", length = 50)
	private String cpuLimit;

	@Column(name = "mem_limit", length = 50)
	private String memLimit;

	@Enumerated(EnumType.STRING)
	@Column(name = "resource_profile", length = 20, nullable = false)
	private ResourceProfile resourceProfile = ResourceProfile.MEDIUM;
}
