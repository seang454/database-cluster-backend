package com.example.demo.cluster.service;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import com.example.demo.cluster.exception.ClusterDeploymentException;
import com.example.demo.cluster.model.CommandResult;

import org.springframework.stereotype.Service;

@Service
public class CommandRunnerService {

	public CommandResult run(List<String> command) {
		return run(command, null, null);
	}

	public CommandResult run(List<String> command, Path workingDirectory) {
		return run(command, workingDirectory, null);
	}

	public CommandResult run(List<String> command, Path workingDirectory, String stdin) {
		ProcessBuilder builder = new ProcessBuilder(command);
		if (workingDirectory != null) {
			builder.directory(workingDirectory.toFile());
		}
		try {
			Process process = builder.start();
			if (stdin != null) {
				try (Writer writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
					writer.write(stdin);
				}
			}
			else {
				process.getOutputStream().close();
			}

			String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
			int exitCode = process.waitFor();
			return new CommandResult(exitCode, stdout, stderr);
		}
		catch (IOException exception) {
			throw new ClusterDeploymentException("Failed to execute command: " + String.join(" ", command), exception);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new ClusterDeploymentException("Command execution was interrupted", exception);
		}
	}
}
