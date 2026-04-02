package com.example.demo.cluster.controller;

import com.example.demo.cluster.dto.ClusterDeploymentRequest;
import com.example.demo.cluster.model.HelmReleaseResult;
import com.example.demo.cluster.service.ClusterService;
import com.example.demo.cluster.service.HelmReleaseService;

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

	private final HelmReleaseService helmReleaseService;
	private final ClusterService clusterService;

	public ClusterDeploymentController(HelmReleaseService helmReleaseService, ClusterService clusterService) {
		this.helmReleaseService = helmReleaseService;
		this.clusterService = clusterService;
	}

	@PostMapping
	public HelmReleaseResult deploy(@RequestBody ClusterDeploymentRequest request) {
		return clusterService.saveAndDeploy(request);
	}

	@GetMapping("/{releaseName}")
	public HelmReleaseResult status(
		@PathVariable String releaseName,
		@RequestParam String namespace
	) {
		return helmReleaseService.status(releaseName, namespace);
	}

	@DeleteMapping("/{releaseName}")
	public HelmReleaseResult uninstall(
		@PathVariable String releaseName,
		@RequestParam String namespace
	) {
		return helmReleaseService.uninstall(releaseName, namespace);
	}
}
