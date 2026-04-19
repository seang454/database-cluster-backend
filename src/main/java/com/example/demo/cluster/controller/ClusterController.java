package com.example.demo.cluster.controller;

import java.util.List;
import java.util.UUID;

import com.example.demo.cluster.dto.DatabaseBackupSettingsRequest;
import com.example.demo.cluster.model.ClusterConfigResponse;
import com.example.demo.cluster.model.DeploymentRecordResponse;
import com.example.demo.cluster.service.ClusterService;
import com.example.demo.cluster.model.KubernetesDeploymentResult;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/namespaces/{namespace}/clusters")
public class ClusterController {

	private final ClusterService clusterService;

	public ClusterController(ClusterService clusterService) {
		this.clusterService = clusterService;
	}

	@GetMapping
	public List<ClusterConfigResponse> list(@PathVariable("namespace") String namespace) {
		return clusterService.listClusters(namespace);
	}

	@GetMapping("/{id}")
	public ClusterConfigResponse get(@PathVariable("namespace") String namespace, @PathVariable("id") UUID clusterId) {
		return clusterService.getCluster(clusterId, namespace);
	}

	@GetMapping("/{id}/deployments")
	public List<DeploymentRecordResponse> deployments(@PathVariable("namespace") String namespace, @PathVariable("id") UUID clusterId) {
		return clusterService.listDeploymentRecords(clusterId, namespace);
	}

	@DeleteMapping("/{id}")
	public KubernetesDeploymentResult uninstallDeployment(@PathVariable("namespace") String namespace, @PathVariable("id") UUID clusterId) {
		return clusterService.uninstallCluster(clusterId, namespace);
	}

	@PatchMapping("/{id}/backup")
	public KubernetesDeploymentResult updateBackupSettings(
		@PathVariable("namespace") String namespace,
		@PathVariable("id") UUID clusterId,
		@RequestBody DatabaseBackupSettingsRequest request
	) {
		return clusterService.updateBackupSettings(clusterId, namespace, request);
	}
}
