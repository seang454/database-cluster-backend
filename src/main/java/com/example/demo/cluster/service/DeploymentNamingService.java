package com.example.demo.cluster.service;

import java.util.Locale;

import com.example.demo.cluster.config.ClusterDeploymentProperties;
import com.example.demo.cluster.dto.ClusterDeploymentRequest;
import com.example.demo.cluster.exception.ClusterDeploymentException;
import com.example.demo.cluster.model.DeploymentTarget;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DeploymentNamingService {

	private final ClusterDeploymentProperties properties;

	public DeploymentNamingService(ClusterDeploymentProperties properties) {
		this.properties = properties;
	}

	public DeploymentTarget resolve(ClusterDeploymentRequest request) {
		String base = request.cluster() != null && StringUtils.hasText(request.cluster().name())
			? request.cluster().name()
			: "cluster";
		String slug = slugify(base);

		String releaseName = StringUtils.hasText(request.releaseName())
			? slugify(request.releaseName())
			: properties.getDefaultReleasePrefix() + "-" + slug;

		String namespace = StringUtils.hasText(request.namespace())
			? slugify(request.namespace())
			: properties.getDefaultNamespacePrefix() + "-" + slug;

		if (!StringUtils.hasText(releaseName) || !StringUtils.hasText(namespace)) {
			throw new ClusterDeploymentException("Unable to resolve release name or namespace");
		}
		return new DeploymentTarget(releaseName, namespace);
	}

	private String slugify(String input) {
		String normalized = input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]+", "-").replaceAll("-{2,}", "-");
		normalized = normalized.replaceAll("^-+", "").replaceAll("-+$", "");
		if (normalized.length() > 53) {
			normalized = normalized.substring(0, 53).replaceAll("-+$", "");
		}
		return normalized;
	}
}
