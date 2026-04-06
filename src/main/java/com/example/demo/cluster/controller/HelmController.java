package com.example.demo.cluster.controller;

import com.example.demo.cluster.model.HelmConnectionResponse;
import com.example.demo.cluster.service.HelmConnectivityService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/helm")
public class HelmController {

	private final HelmConnectivityService helmConnectivityService;

	public HelmController(HelmConnectivityService helmConnectivityService) {
		this.helmConnectivityService = helmConnectivityService;
	}

	@GetMapping("/test")
	public HelmConnectionResponse test() {
		return helmConnectivityService.probeChartSource();
	}
}
