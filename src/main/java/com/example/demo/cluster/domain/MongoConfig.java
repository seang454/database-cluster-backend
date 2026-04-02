package com.example.demo.cluster.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Embeddable
public class MongoConfig {

	@Column(name = "mongodb_cr_version", length = 50)
	private String crVersion;

	@Column(name = "mongodb_replica_set_horizons_enabled")
	private Boolean replicaSetHorizonsEnabled = Boolean.FALSE;

	@Column(name = "mongodb_replica_set_horizons_base_port")
	private Integer replicaSetHorizonsBasePort;
}
