package com.example.demo.cluster.controller;

import java.util.List;
import java.util.UUID;

import com.example.demo.cluster.dto.ClusterDeploymentRequest;
import com.example.demo.cluster.model.ClusterConfigResponse;
import com.example.demo.cluster.model.DeploymentRecordResponse;
import com.example.demo.cluster.service.ClusterService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clusters")
public class ClusterController {

	private final ClusterService clusterService;

	public ClusterController(ClusterService clusterService) {
		this.clusterService = clusterService;
	}

	@PostMapping
	public ClusterConfigResponse create(@RequestBody ClusterDeploymentRequest request) {
		return clusterService.saveClusterConfig(request);
	}

	@GetMapping
	public List<ClusterConfigResponse> list() {
		return clusterService.listClusters();
	}

	@GetMapping("/{id}")
	public ClusterConfigResponse get(@PathVariable UUID id) {
		return clusterService.getCluster(id);
	}

	@GetMapping("/{id}/deployments")
	public List<DeploymentRecordResponse> deployments(@PathVariable UUID id) {
		return clusterService.listDeploymentRecords(id);
	}
}
