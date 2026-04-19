package com.example.demo.cluster.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.example.demo.cluster.config.ClusterDeploymentProperties;
import com.example.demo.cluster.exception.ClusterDeploymentException;
import com.example.demo.cluster.model.CommandResult;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class KubectlCommandService {

	private final ClusterDeploymentProperties properties;

	public KubectlCommandService(ClusterDeploymentProperties properties) {
		this.properties = properties;
	}

	public CommandResult patchMerge(String resourceType, String resourceName, String namespace, String patchJson) {
		Path patchFile = null;
		try {
			patchFile = Files.createTempFile("kubectl-patch-", ".json");
			Files.writeString(patchFile, patchJson, StandardCharsets.UTF_8);
			List<String> command = new ArrayList<>();
			command.add(kubectlExecutable());
			command.add("patch");
			command.add(resourceType);
			command.add(resourceName);
			command.add("-n");
			command.add(namespace);
			command.add("--type");
			command.add("merge");
			command.add("--patch-file");
			command.add(patchFile.toString());
			return run(command);
		}
		catch (IOException exception) {
			throw new ClusterDeploymentException("Failed to write kubectl patch file: " + exception.getMessage(), exception);
		}
		finally {
			if (patchFile != null) {
				try {
					Files.deleteIfExists(patchFile);
				}
				catch (IOException ignored) {
				}
			}
		}
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
			throw new ClusterDeploymentException("Failed to execute kubectl command: " + exception.getMessage(), exception);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new ClusterDeploymentException("kubectl command was interrupted", exception);
		}
	}

	private String kubectlExecutable() {
		String executable = properties.getKubectlExecutable();
		return StringUtils.hasText(executable) ? executable : "kubectl";
	}
}
