package com.example.demo.cluster.service;

import com.example.demo.cluster.dto.DatabaseBackupSettingsRequest;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ClusterChangeRoutingService {

	public ChangeDestination routeBackupSettings(DatabaseBackupSettingsRequest request) {
		if (request == null) {
			return ChangeDestination.DB_ONLY;
		}
		if (request.enabled() != null || StringUtils.hasText(request.schedule())) {
			return ChangeDestination.OPERATOR_PATCH;
		}
		if (StringUtils.hasText(request.destinationPath())
			|| StringUtils.hasText(request.credentialSecret())
			|| StringUtils.hasText(request.retentionPolicy())) {
			return ChangeDestination.HELM;
		}
		return ChangeDestination.DB_ONLY;
	}

	public String describeBackupRoute(DatabaseBackupSettingsRequest request) {
		return switch (routeBackupSettings(request)) {
			case OPERATOR_PATCH -> "enabled/schedule are operator-managed and can be patched live";
			case HELM -> "destinationPath/credentialSecret/retentionPolicy are chart-managed and should be re-applied with Helm";
			case DB_ONLY -> "no live backup field changed; DB persistence only";
		};
	}
}
