package com.example.demo.cluster.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Embeddable
public class MysqlConfig {

	@Column(name = "mysql_haproxy_size")
	private Short haproxySize;
}
