package com.example.demo.cluster.model;

public record PodLogStreamInfo(
	String namespace,
	String podName,
	String container,
	Integer tailLines
) {
}
