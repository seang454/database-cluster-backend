package com.example.demo.cluster.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Embeddable
public class RedisConfig {

	@Column(name = "redis_exporter_enabled")
	private Boolean exporterEnabled = Boolean.FALSE;
}
