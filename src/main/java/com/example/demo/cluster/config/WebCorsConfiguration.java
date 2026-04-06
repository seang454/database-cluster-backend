package com.example.demo.cluster.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebCorsConfiguration implements WebMvcConfigurer {

	private final FrontendCorsProperties frontendCorsProperties;

	public WebCorsConfiguration(FrontendCorsProperties frontendCorsProperties) {
		this.frontendCorsProperties = frontendCorsProperties;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		var allowedOrigins = frontendCorsProperties.getAllowedOrigins();
		if (allowedOrigins.isEmpty()) {
			return;
		}

		registry.addMapping("/api/**")
			.allowedOrigins(allowedOrigins.toArray(String[]::new))
			.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
			.allowedHeaders("*");
	}
}
