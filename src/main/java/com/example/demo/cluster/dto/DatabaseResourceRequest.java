package com.example.demo.cluster.dto;

import com.example.demo.cluster.domain.enumtype.ResourceProfile;

public record DatabaseResourceRequest(
	String cpuRequest,
	String memRequest,
	String cpuLimit,
	String memLimit,
	ResourceProfile resourceProfile
) {
}
