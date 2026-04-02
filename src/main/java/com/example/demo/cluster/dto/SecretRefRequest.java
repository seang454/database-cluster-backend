package com.example.demo.cluster.dto;

import com.example.demo.cluster.domain.enumtype.DatabaseEngine;
import com.example.demo.cluster.domain.enumtype.InjectVia;

public record SecretRefRequest(
	DatabaseEngine engine,
	String secretKey,
	String vaultPath,
	String k8sSecretName,
	String k8sSecretKey,
	InjectVia injectVia
) {
}
