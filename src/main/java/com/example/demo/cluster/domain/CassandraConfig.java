package com.example.demo.cluster.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Embeddable
public class CassandraConfig {

	@Column(name = "cassandra_cluster_name", length = 100)
	private String clusterName;

	@Column(name = "cassandra_datacenter", length = 100)
	private String datacenter;

	@Column(name = "cassandra_require_client_auth")
	private Boolean requireClientAuth = Boolean.FALSE;
}
