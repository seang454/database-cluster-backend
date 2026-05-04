package com.example.demo.cluster.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import com.example.demo.cluster.domain.Cluster;
import com.example.demo.cluster.domain.ClusterPlatformConfig;
import com.example.demo.cluster.domain.DatabaseInstance;
import com.example.demo.cluster.domain.enumtype.DatabaseEngine;
import com.example.demo.cluster.exception.ClusterDeploymentException;
import com.example.demo.cluster.model.ClusterCertificateDownload;
import com.example.demo.cluster.repository.ClusterRepository;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClusterCertificateServiceTest {

	@Mock
	private KubernetesClient kubernetesClient;

	@Mock
	private MixedOperation<Secret, io.fabric8.kubernetes.api.model.SecretList, Resource<Secret>> secretOperations;

	@Mock
	private NonNamespaceOperation<Secret, io.fabric8.kubernetes.api.model.SecretList, Resource<Secret>> namespacedSecretOperations;

	@Mock
	private Resource<Secret> secretResource;

	@Mock
	private ClusterRepository clusterRepository;

	@Test
	void downloadsPostgresqlCaCertificateFromReleaseScopedSecret() {
		UUID clusterId = UUID.randomUUID();
		Cluster cluster = new Cluster();
		cluster.setId(clusterId);
		cluster.setName("seang-postgres");
		cluster.setDeploymentName("db-seang-postgres");
		cluster.setDeploymentNamespace("seang");
		cluster.setPlatformConfig(new ClusterPlatformConfig());

		DatabaseInstance database = new DatabaseInstance();
		database.setEngine(DatabaseEngine.POSTGRESQL);
		database.setEnabled(Boolean.TRUE);
		cluster.getDatabaseInstances().add(database);

		when(clusterRepository.findById(clusterId)).thenReturn(java.util.Optional.of(cluster));
		when(kubernetesClient.secrets()).thenReturn(secretOperations);
		when(secretOperations.inNamespace("seang")).thenReturn(namespacedSecretOperations);
		when(namespacedSecretOperations.withName("db-seang-postgres-postgresql-ca")).thenReturn(secretResource);
		Secret secret = new SecretBuilder()
			.withNewMetadata()
			.withName("db-seang-postgres-postgresql-ca")
			.endMetadata()
			.addToData("ca.crt", Base64.getEncoder().encodeToString("CERT".getBytes(StandardCharsets.UTF_8)))
			.build();
		when(secretResource.get()).thenReturn(secret);

		ClusterCertificateService service = new ClusterCertificateService(kubernetesClient, clusterRepository);
		ClusterCertificateDownload download = service.downloadCaCertificate(clusterId, "seang");

		assertThat(download.fileName()).isEqualTo("ca.crt");
		assertThat(download.contentType()).isEqualTo("application/octet-stream");
		assertThat(new String(download.content(), StandardCharsets.UTF_8)).isEqualTo("CERT");
	}

	@Test
	void rejectsNamespaceMismatch() {
		UUID clusterId = UUID.randomUUID();
		Cluster cluster = new Cluster();
		cluster.setId(clusterId);
		cluster.setName("seang-postgres");
		cluster.setDeploymentName("db-seang-postgres");
		cluster.setDeploymentNamespace("seang");
		cluster.setPlatformConfig(new ClusterPlatformConfig());

		when(clusterRepository.findById(clusterId)).thenReturn(java.util.Optional.of(cluster));

		ClusterCertificateService service = new ClusterCertificateService(kubernetesClient, clusterRepository);
		org.junit.jupiter.api.Assertions.assertThrows(ClusterDeploymentException.class, () -> service.downloadCaCertificate(clusterId, "other"));
	}
}
