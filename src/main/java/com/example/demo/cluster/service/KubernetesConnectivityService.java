package com.example.demo.cluster.service;

import java.util.List;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import com.example.demo.cluster.model.KubernetesConnectionResponse;
import com.example.demo.cluster.exception.ClusterDeploymentException;

import org.springframework.stereotype.Service;

@Service
public class KubernetesConnectivityService {

	private final KubernetesClient client;

	public KubernetesConnectivityService(KubernetesClient client) {
		this.client = client;
	}

	public KubernetesConnectionResponse probe() {
		try {
			List<String> namespaces = client.namespaces().list().getItems().stream()
				.map(namespace -> namespace.getMetadata() != null ? namespace.getMetadata().getName() : null)
				.filter(name -> name != null && !name.isBlank())
				.sorted()
				.limit(10)
				.toList();

			return new KubernetesConnectionResponse(true, client.namespaces().list().getItems().size(), namespaces);
		}
		catch (KubernetesClientException exception) {
			throw new ClusterDeploymentException("Failed to reach Kubernetes API: " + exception.getMessage(), exception);
		}
	}
}
