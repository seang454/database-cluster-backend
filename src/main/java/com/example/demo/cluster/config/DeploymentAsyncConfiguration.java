package com.example.demo.cluster.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

@Configuration
public class DeploymentAsyncConfiguration {

	@Bean(name = "deploymentReadinessExecutor")
	public Executor deploymentReadinessExecutor() {
		return new SimpleAsyncTaskExecutor("deployment-readiness-");
	}
}
