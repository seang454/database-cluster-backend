package com.example.demo.cluster.service;

import java.util.ArrayList;
import java.util.List;

import com.example.demo.cluster.config.ClusterDeploymentProperties;
import com.example.demo.cluster.exception.ClusterDeploymentException;
import com.example.demo.cluster.model.CommandResult;
import com.example.demo.cluster.model.HelmConnectionResponse;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class HelmConnectivityService {

	private final HelmCommandService helmCommandService;
	private final ClusterDeploymentProperties properties;

	public HelmConnectivityService(HelmCommandService helmCommandService, ClusterDeploymentProperties properties) {
		this.helmCommandService = helmCommandService;
		this.properties = properties;
	}

	public HelmConnectionResponse probeChartSource() {
		CommandResult result = helmCommandService.showChart();
		if (!result.successful()) {
			throw new ClusterDeploymentException("Helm chart test failed: "
				+ (StringUtils.hasText(result.stderr()) ? result.stderr() : result.stdout()));
		}
		return new HelmConnectionResponse(
			true,
			showChartCommand(),
			properties.getChartPath(),
			properties.getChartVersion(),
			result.stdout(),
			result.stderr()
		);
	}

	private List<String> showChartCommand() {
		List<String> command = new ArrayList<>();
		command.add(StringUtils.hasText(properties.getHelmExecutable()) ? properties.getHelmExecutable() : "helm");
		command.add("show");
		command.add("chart");
		command.add(properties.getChartPath());
		if (StringUtils.hasText(properties.getChartVersion())) {
			command.add("--version");
			command.add(properties.getChartVersion());
		}
		return command;
	}
}
