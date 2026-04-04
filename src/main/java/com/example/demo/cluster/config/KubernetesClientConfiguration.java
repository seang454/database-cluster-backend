package com.example.demo.cluster.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(KubernetesClientProperties.class)
public class KubernetesClientConfiguration {

	@Bean(destroyMethod = "close")
	public KubernetesClient kubernetesClient(KubernetesClientProperties properties) {
		Config config = switch (properties.getMode()) {
			case KUBECONFIG -> fromKubeconfig(properties.getKubeconfigPath());
			case IN_CLUSTER -> autoConfigure();
			case AUTO -> properties.getKubeconfigPath() != null ? fromKubeconfig(properties.getKubeconfigPath()) : autoConfigure();
		};
		ConfigBuilder builder = new ConfigBuilder(config);
		if (properties.getMasterUrl() != null && !properties.getMasterUrl().isBlank()) {
			builder.withMasterUrl(properties.getMasterUrl());
		}
		config = builder.build();
		config.setConnectionTimeout(toMillis(properties.getConnectTimeout()));
		config.setRequestTimeout(toMillis(properties.getReadTimeout()));
		config.setTrustCerts(properties.isTrustCertificates());
		config.setDisableHostnameVerification(properties.isDisableHostnameVerification());
		return new KubernetesClientBuilder().withConfig(config).build();
	}

	private Config autoConfigure() {
		return Config.autoConfigure(null);
	}

	private Config fromKubeconfig(Path kubeconfigPath) {
		if (kubeconfigPath == null) {
			return autoConfigure();
		}
		try {
			String kubeconfig = Files.readString(kubeconfigPath);
			return Config.fromKubeconfig(null, kubeconfig, kubeconfigPath.toString());
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to read kubeconfig: " + kubeconfigPath, exception);
		}
	}

	private int toMillis(java.time.Duration duration) {
		if (duration == null) {
			return 0;
		}
		long millis = duration.toMillis();
		return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
	}
}
