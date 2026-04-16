package com.example.demo.cluster.controller;

import com.example.demo.cluster.dto.ClusterDeploymentRequest;
import com.example.demo.cluster.dto.DatabaseBackupSettingsRequest;
import com.example.demo.cluster.model.KubernetesDeploymentResult;
import com.example.demo.cluster.service.ClusterService;
import com.example.demo.cluster.service.KubernetesDeploymentService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/namespaces/{namespace}/cluster-deployments")
public class ClusterDeploymentController {

	private final KubernetesDeploymentService kubernetesDeploymentService;
	private final ClusterService clusterService;

	public ClusterDeploymentController(
		KubernetesDeploymentService kubernetesDeploymentService,
		ClusterService clusterService
	) {
		this.kubernetesDeploymentService = kubernetesDeploymentService;
		this.clusterService = clusterService;
	}

	@PostMapping
	public ResponseEntity<KubernetesDeploymentResult> deploy(
		@PathVariable("namespace") String namespace,
		@RequestBody ClusterDeploymentRequest request
	) {
		ClusterDeploymentRequest namespacedRequest = request == null ? null : new ClusterDeploymentRequest(
			request.releaseName(),
			namespace,
			request.cluster(),
			request.database(),
			request.secrets()
		);
		return ResponseEntity.ok(clusterService.saveAndDeploy(namespacedRequest));
	}

	@GetMapping("/{releaseName}")
	public KubernetesDeploymentResult status(
		@PathVariable("namespace") String namespace,
		@PathVariable("releaseName") String releaseName
	) {
		return kubernetesDeploymentService.status(releaseName, namespace);
	}

	@PatchMapping("/{releaseName}/backup")
	public ResponseEntity<KubernetesDeploymentResult> updateBackupSettings(
		@PathVariable("namespace") String namespace,
		@PathVariable("releaseName") String releaseName,
		@RequestBody DatabaseBackupSettingsRequest request
	) {
		return ResponseEntity.ok(clusterService.updateBackupSettings(releaseName, namespace, request));
	}
}
