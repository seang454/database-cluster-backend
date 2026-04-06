package com.example.demo.cluster.service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import com.example.demo.cluster.exception.ClusterDeploymentException;
import com.example.demo.cluster.model.PodLogStreamInfo;
import com.example.demo.cluster.model.PodSummaryResponse;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class KubernetesPodLogService {

	private final KubernetesClient client;

	public KubernetesPodLogService(KubernetesClient client) {
		this.client = client;
	}

	public PodSummaryResponse listPods(String namespace) {
		List<PodSummaryResponse.PodSummary> pods = client.pods().inNamespace(namespace).list().getItems().stream()
			.map(this::toSummary)
			.sorted((left, right) -> left.name().compareToIgnoreCase(right.name()))
			.toList();
		return new PodSummaryResponse(namespace, pods.size(), pods);
	}

	public SseEmitter streamLogs(String namespace, String podName, String container, Integer tailLines) {
		Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
		if (pod == null) {
			throw new ClusterDeploymentException("Pod not found: " + namespace + "/" + podName);
		}

		SseEmitter emitter = new SseEmitter(0L);
		AtomicBoolean closed = new AtomicBoolean(false);
		PodLogStreamInfo streamInfo = new PodLogStreamInfo(namespace, podName, container, tailLines);

		CompletableFuture.runAsync(() -> {
			LogWatch watch = null;
			try {
				sendEvent(emitter, "open", streamInfo);
				watch = openLogWatch(namespace, podName, container, tailLines, emitter, closed);
				while (!closed.get()) {
					Thread.sleep(1000L);
				}
			}
			catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				completeWithError(emitter, closed, new ClusterDeploymentException("Log stream was interrupted", exception));
			}
			catch (Exception exception) {
				completeWithError(emitter, closed, exception);
			}
			finally {
				if (watch != null) {
					watch.close();
				}
				if (closed.compareAndSet(false, true)) {
					emitter.complete();
				}
			}
		});

		emitter.onCompletion(() -> closed.set(true));
		emitter.onTimeout(() -> closed.set(true));
		emitter.onError(ignored -> closed.set(true));
		return emitter;
	}

	private PodSummaryResponse.PodSummary toSummary(Pod pod) {
		String name = pod.getMetadata() != null ? pod.getMetadata().getName() : "";
		String phase = pod.getStatus() != null ? pod.getStatus().getPhase() : "Unknown";
		String podIp = pod.getStatus() != null ? pod.getStatus().getPodIP() : null;
		String nodeName = pod.getSpec() != null ? pod.getSpec().getNodeName() : null;
		return new PodSummaryResponse.PodSummary(name, phase, podIp, nodeName);
	}

	private void sendEvent(SseEmitter emitter, String event, Object data) throws IOException {
		emitter.send(SseEmitter.event().name(event).data(data));
	}

	private void completeWithError(SseEmitter emitter, AtomicBoolean closed, Exception exception) {
		if (closed.compareAndSet(false, true)) {
			emitter.completeWithError(exception);
		}
	}

	private LogWatch openLogWatch(
		String namespace,
		String podName,
		String container,
		Integer tailLines,
		SseEmitter emitter,
		AtomicBoolean closed
	) {
		SseOutputStream outputStream = new SseOutputStream(emitter, closed);
		var podResource = client.pods().inNamespace(namespace).withName(podName);
		boolean hasContainer = StringUtils.hasText(container);
		boolean hasTailLines = tailLines != null && tailLines > 0;

		if (hasContainer && hasTailLines) {
			return podResource.inContainer(container).tailingLines(tailLines).watchLog(outputStream);
		}
		if (hasContainer) {
			return podResource.inContainer(container).watchLog(outputStream);
		}
		if (hasTailLines) {
			return podResource.tailingLines(tailLines).watchLog(outputStream);
		}
		return podResource.watchLog(outputStream);
	}

	private static final class SseOutputStream extends OutputStream {

		private final SseEmitter emitter;
		private final AtomicBoolean closed;
		private final StringBuilder buffer = new StringBuilder();

		private SseOutputStream(SseEmitter emitter, AtomicBoolean closed) {
			this.emitter = emitter;
			this.closed = closed;
		}

		@Override
		public void write(int value) throws IOException {
			if (closed.get()) {
				return;
			}
			char current = new String(new byte[] { (byte) value }, StandardCharsets.UTF_8).charAt(0);
			if (current == '\r') {
				return;
			}
			if (current == '\n') {
				flushBuffer();
				return;
			}
			buffer.append(current);
		}

		@Override
		public void flush() throws IOException {
			flushBuffer();
		}

		@Override
		public void close() throws IOException {
			flushBuffer();
		}

		private void flushBuffer() throws IOException {
			if (buffer.isEmpty() || closed.get()) {
				return;
			}
			String line = buffer.toString();
			buffer.setLength(0);
			emitter.send(SseEmitter.event().name("log").data(line));
		}
	}
}
