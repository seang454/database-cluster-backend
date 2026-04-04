package com.example.demo.cluster.controller;

import com.example.demo.cluster.dto.ClusterDeploymentRequest;
import com.example.demo.cluster.model.KubernetesDeploymentResult;
import com.example.demo.cluster.service.ClusterService;
import com.example.demo.cluster.service.KubernetesDeploymentService;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cluster-deployments")
public class ClusterDeploymentController {

	private final KubernetesDeploymentService kubernetesDeploymentService;
	private final ClusterService clusterService;

	public ClusterDeploymentController(KubernetesDeploymentService kubernetesDeploymentService, ClusterService clusterService) {
		this.kubernetesDeploymentService = kubernetesDeploymentService;
		this.clusterService = clusterService;
	}

	@PostMapping
	public KubernetesDeploymentResult deploy(@RequestBody ClusterDeploymentRequest request) {
		return clusterService.saveAndDeploy(request);
	}

	@GetMapping("/{releaseName}")
	public KubernetesDeploymentResult status(
		@PathVariable String releaseName,
		@RequestParam String namespace
	) {
		return kubernetesDeploymentService.status(releaseName, namespace);
	}

	@DeleteMapping("/{releaseName}")
	public KubernetesDeploymentResult uninstall(
		@PathVariable String releaseName,
		@RequestParam String namespace
	) {
		return clusterService.uninstallDeployment(releaseName, namespace);
	}
}
