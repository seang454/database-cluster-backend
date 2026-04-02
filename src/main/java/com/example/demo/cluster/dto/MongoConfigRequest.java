package com.example.demo.cluster.dto;

public record MongoConfigRequest(
	Boolean replicaSetHorizonsEnabled,
	Integer replicaSetHorizonsBasePort
) {
}
