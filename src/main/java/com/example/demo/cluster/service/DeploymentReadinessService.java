package com.example.demo.cluster.service;

import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import com.example.demo.cluster.domain.enumtype.DatabaseEngine;
import com.example.demo.cluster.exception.ClusterDeploymentException;
import com.example.demo.cluster.model.DeploymentTarget;

import org.springframework.stereotype.Service;

@Service
public class DeploymentReadinessService {

	private static final String CNPG_API_VERSION = "postgresql.cnpg.io/v1";
	private static final String CNPG_KIND = "Cluster";
	private static final String PSMDB_API_VERSION = "psmdb.percona.com/v1";
	private static final String PSMDB_KIND = "PerconaServerMongoDB";
	private static final String PXC_API_VERSION = "pxc.percona.com/v1";
	private static final String PXC_KIND = "PerconaXtraDBCluster";
	private static final String REDIS_API_VERSION = "redis.redis.opstreelabs.in/v1beta1";
	private static final String REDIS_KIND = "RedisCluster";
	private static final String K8SSANDRA_API_VERSION = "k8ssandra.io/v1alpha1";
	private static final String K8SSANDRA_KIND = "K8ssandraCluster";

	private final KubernetesClient client;

	public DeploymentReadinessService(KubernetesClient client) {
		this.client = client;
	}

	public void verifyDeployment(DeploymentTarget target, DatabaseEngine engine) {
		KubernetesResourceDescriptor descriptor = descriptorFor(engine, target);
		waitForReady(descriptor, target.namespace(), descriptor.kind() + " did not become Ready");
	}

	private void waitForReady(KubernetesResourceDescriptor descriptor, String namespace, String failureMessage) {
		for (int attempt = 0; attempt < 36; attempt++) {
			GenericKubernetesResource resource = client.genericKubernetesResources(descriptor.apiVersion(), descriptor.kind())
				.inNamespace(namespace)
				.withName(descriptor.resourceName())
				.get();
			if (isReady(resource)) {
				return;
			}
			sleepSeconds(5);
		}
		throw new ClusterDeploymentException(failureMessage);
	}

	private KubernetesResourceDescriptor descriptorFor(DatabaseEngine engine, DeploymentTarget target) {
		return switch (engine) {
			case POSTGRESQL -> new KubernetesResourceDescriptor(CNPG_API_VERSION, CNPG_KIND, target.releaseName() + "-postgresql");
			case MONGODB -> new KubernetesResourceDescriptor(PSMDB_API_VERSION, PSMDB_KIND, target.releaseName() + "-mongodb");
			case MYSQL -> new KubernetesResourceDescriptor(PXC_API_VERSION, PXC_KIND, target.releaseName() + "-mysql");
			case REDIS -> new KubernetesResourceDescriptor(REDIS_API_VERSION, REDIS_KIND, target.releaseName() + "-redis");
			case CASSANDRA -> new KubernetesResourceDescriptor(K8SSANDRA_API_VERSION, K8SSANDRA_KIND, target.releaseName() + "-cassandra");
		};
	}

	private boolean isReady(GenericKubernetesResource resource) {
		if (resource == null || resource.getAdditionalProperties() == null) {
			return false;
		}
		Object statusObject = resource.getAdditionalProperties().get("status");
		if (!(statusObject instanceof Map<?, ?> status)) {
			return false;
		}
		Object conditionsObject = status.get("conditions");
		if (!(conditionsObject instanceof List<?> conditions)) {
			return false;
		}
		for (Object conditionObject : conditions) {
			if (!(conditionObject instanceof Map<?, ?> condition)) {
				continue;
			}
			String type = String.valueOf(condition.get("type"));
			String state = String.valueOf(condition.get("status"));
			if ("Ready".equalsIgnoreCase(type) && "True".equalsIgnoreCase(state)) {
				return true;
			}
		}
		return false;
	}

	private void sleepSeconds(long seconds) {
		try {
			Thread.sleep(seconds * 1000L);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new ClusterDeploymentException("Readiness check was interrupted", exception);
		}
	}

	private record KubernetesResourceDescriptor(
		String apiVersion,
		String kind,
		String resourceName
	) {
	}
}
