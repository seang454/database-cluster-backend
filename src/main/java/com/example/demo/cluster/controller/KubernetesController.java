package com.example.demo.cluster.controller;

import com.example.demo.cluster.model.DatabaseResourceSummaryResponse;
import com.example.demo.cluster.model.EventSummaryResponse;
import com.example.demo.cluster.model.KubernetesConnectionResponse;
import com.example.demo.cluster.model.NamespaceOverviewResponse;
import com.example.demo.cluster.model.PersistentVolumeClaimSummaryResponse;
import com.example.demo.cluster.model.PodSummaryResponse;
import com.example.demo.cluster.model.ServiceSummaryResponse;
import com.example.demo.cluster.service.KubernetesConnectivityService;
import com.example.demo.cluster.service.KubernetesPodLogService;
import com.example.demo.cluster.service.KubernetesResourceQueryService;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/kubernetes")
public class KubernetesController {

	private final KubernetesConnectivityService connectivityService;
	private final KubernetesPodLogService podLogService;
	private final KubernetesResourceQueryService resourceQueryService;

	public KubernetesController(
		KubernetesConnectivityService connectivityService,
		KubernetesPodLogService podLogService,
		KubernetesResourceQueryService resourceQueryService
	) {
		this.connectivityService = connectivityService;
		this.podLogService = podLogService;
		this.resourceQueryService = resourceQueryService;
	}

	@GetMapping("/test")
	public KubernetesConnectionResponse test() {
		return connectivityService.probe();
	}

	@GetMapping("/namespaces/{namespace}/pods")
	public PodSummaryResponse listPods(@PathVariable("namespace") String namespace) {
		return podLogService.listPods(namespace);
	}

	@GetMapping("/namespaces/{namespace}/overview")
	public NamespaceOverviewResponse overview(@PathVariable("namespace") String namespace) {
		return resourceQueryService.namespaceOverview(namespace);
	}

	@GetMapping("/namespaces/{namespace}/database-resources")
	public DatabaseResourceSummaryResponse listDatabaseResources(@PathVariable("namespace") String namespace) {
		return resourceQueryService.listDatabaseResources(namespace);
	}

	@GetMapping("/namespaces/{namespace}/events")
	public EventSummaryResponse listEvents(
		@PathVariable("namespace") String namespace,
		@RequestParam(defaultValue = "false") boolean warningsOnly,
		@RequestParam(defaultValue = "50") int limit
	) {
		return resourceQueryService.listEvents(namespace, warningsOnly, limit);
	}

	@GetMapping("/namespaces/{namespace}/services")
	public ServiceSummaryResponse listServices(@PathVariable("namespace") String namespace) {
		return resourceQueryService.listServices(namespace);
	}

	@GetMapping("/namespaces/{namespace}/persistent-volume-claims")
	public PersistentVolumeClaimSummaryResponse listPersistentVolumeClaims(@PathVariable("namespace") String namespace) {
		return resourceQueryService.listPersistentVolumeClaims(namespace);
	}

	@GetMapping(path = "/namespaces/{namespace}/pods/{podName}/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter streamPodLogs(
		@PathVariable("namespace") String namespace,
		@PathVariable("podName") String podName,
		@RequestParam(required = false) String container,
		@RequestParam(required = false) Integer tailLines
	) {
		return podLogService.streamLogs(namespace, podName, container, tailLines);
	}
}
