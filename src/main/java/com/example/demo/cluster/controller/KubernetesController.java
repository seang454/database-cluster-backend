package com.example.demo.cluster.controller;

import com.example.demo.cluster.model.KubernetesConnectionResponse;
import com.example.demo.cluster.service.KubernetesConnectivityService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kubernetes")
public class KubernetesController {

	private final KubernetesConnectivityService connectivityService;

	public KubernetesController(KubernetesConnectivityService connectivityService) {
		this.connectivityService = connectivityService;
	}

	@GetMapping("/test")
	public KubernetesConnectionResponse test() {
		return connectivityService.probe();
	}
}
