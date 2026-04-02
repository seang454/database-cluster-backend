package com.example.demo.cluster.dto;

public record CassandraConfigRequest(
	String clusterName,
	String datacenter,
	Boolean requireClientAuth
) {
}
