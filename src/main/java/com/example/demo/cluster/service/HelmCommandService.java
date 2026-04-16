package com.example.demo.cluster.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.example.demo.cluster.config.ClusterDeploymentProperties;
import com.example.demo.cluster.exception.ClusterDeploymentException;
import com.example.demo.cluster.model.CommandResult;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class HelmCommandService {

	private final ClusterDeploymentProperties properties;

	public HelmCommandService(ClusterDeploymentProperties properties) {
		this.properties = properties;
	}

	public CommandResult upgradeInstall(String releaseName, String namespace, Path overrideValuesFile) {
		return run(buildUpgradeInstallCommand(releaseName, namespace, overrideValuesFile, false));
	}

	public CommandResult upgradeInstallReuseValues(String releaseName, String namespace, Path overrideValuesFile) {
		return run(buildUpgradeInstallCommand(releaseName, namespace, overrideValuesFile, true));
	}

	public CommandResult uninstall(String releaseName, String namespace) {
		return run(List.of(
			helmExecutable(),
			"uninstall",
			releaseName,
			"-n",
			namespace
		));
	}

	public CommandResult status(String releaseName, String namespace) {
		return run(List.of(
			helmExecutable(),
			"status",
			releaseName,
			"-n",
			namespace
		));
	}

	public CommandResult showChart() {
		return run(buildShowChartCommand());
	}

	private List<String> buildUpgradeInstallCommand(String releaseName, String namespace, Path overrideValuesFile, boolean reuseValues) {
		List<String> command = new ArrayList<>();
		command.add(helmExecutable());
		command.add("upgrade");
		command.add("--install");
		command.add(releaseName);
		command.add(chartPath());
		command.add("-n");
		command.add(namespace);
		command.add("--create-namespace");
		command.add("--wait");
		command.add("--timeout");
		command.add(StringUtils.hasText(properties.getHelmTimeout()) ? properties.getHelmTimeout() : "30m");
		if (reuseValues) {
			command.add("--reuse-values");
		}
		if (StringUtils.hasText(properties.getChartVersion())) {
			command.add("--version");
			command.add(properties.getChartVersion());
		}
		if (StringUtils.hasText(properties.getDefaultsFile())) {
			command.add("-f");
			command.add(properties.getDefaultsFile());
		}
		if (overrideValuesFile != null) {
			command.add("-f");
			command.add(overrideValuesFile.toString());
		}
		return command;
	}

	private List<String> buildShowChartCommand() {
		List<String> command = new ArrayList<>();
		command.add(helmExecutable());
		command.add("show");
		command.add("chart");
		command.add(chartPath());
		if (StringUtils.hasText(properties.getChartVersion())) {
			command.add("--version");
			command.add(properties.getChartVersion());
		}
		return command;
	}

	private CommandResult run(List<String> command) {
		try {
			ProcessBuilder builder = new ProcessBuilder(command);
			builder.redirectErrorStream(false);
			Process process = builder.start();
			String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
			int exitCode = process.waitFor();
			return new CommandResult(exitCode, stdout, stderr);
		}
		catch (IOException exception) {
			throw new ClusterDeploymentException("Failed to execute Helm command: " + exception.getMessage(), exception);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new ClusterDeploymentException("Helm command was interrupted", exception);
		}
	}

	private String helmExecutable() {
		return StringUtils.hasText(properties.getHelmExecutable()) ? properties.getHelmExecutable() : "helm";
	}

	private String chartPath() {
		if (!StringUtils.hasText(properties.getChartPath())) {
			throw new ClusterDeploymentException("cluster.deployment.chart-path is required");
		}
		return properties.getChartPath();
	}
}
