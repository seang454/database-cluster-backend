package com.example.demo.cluster.model;

import java.util.List;

public record HelmConnectionResponse(
	boolean success,
	List<String> command,
	String chartPath,
	String chartVersion,
	String stdout,
	String stderr
) {
}
