package com.example.demo.cluster.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import com.example.demo.cluster.model.DatabaseResourceSummaryResponse;
import com.example.demo.cluster.model.EventSummaryResponse;
import com.example.demo.cluster.model.NamespaceOverviewResponse;
import com.example.demo.cluster.model.PersistentVolumeClaimSummaryResponse;
import com.example.demo.cluster.model.ServiceSummaryResponse;

import org.springframework.stereotype.Service;

@Service
public class KubernetesResourceQueryService {

	private static final List<ResourceDescriptor> DATABASE_RESOURCE_TYPES = List.of(
		new ResourceDescriptor("postgresql.cnpg.io/v1", "Cluster"),
		new ResourceDescriptor("psmdb.percona.com/v1", "PerconaServerMongoDB"),
		new ResourceDescriptor("pxc.percona.com/v1", "PerconaXtraDBCluster"),
		new ResourceDescriptor("redis.redis.opstreelabs.in/v1beta1", "RedisCluster"),
		new ResourceDescriptor("k8ssandra.io/v1alpha1", "K8ssandraCluster")
	);

	private final KubernetesClient client;

	public KubernetesResourceQueryService(KubernetesClient client) {
		this.client = client;
	}

	public NamespaceOverviewResponse namespaceOverview(String namespace) {
		List<Pod> pods = client.pods().inNamespace(namespace).list().getItems();
		int activePods = (int) pods.stream()
			.filter(pod -> pod.getStatus() == null || !"Succeeded".equalsIgnoreCase(pod.getStatus().getPhase()))
			.count();
		int services = client.services().inNamespace(namespace).list().getItems().size();
		int claims = client.persistentVolumeClaims().inNamespace(namespace).list().getItems().size();
		int secrets = client.secrets().inNamespace(namespace).list().getItems().size();
		int databaseResources = listDatabaseResources(namespace).total();
		return new NamespaceOverviewResponse(namespace, pods.size(), activePods, services, claims, secrets, databaseResources);
	}

	public DatabaseResourceSummaryResponse listDatabaseResources(String namespace) {
		List<DatabaseResourceSummaryResponse.DatabaseResourceSummary> resources = new ArrayList<>();
		for (ResourceDescriptor descriptor : DATABASE_RESOURCE_TYPES) {
			List<GenericKubernetesResource> items = client.genericKubernetesResources(descriptor.apiVersion(), descriptor.kind())
				.inNamespace(namespace)
				.list()
				.getItems();
			for (GenericKubernetesResource resource : items) {
				resources.add(toDatabaseSummary(descriptor, resource));
			}
		}
		resources.sort(Comparator.comparing(DatabaseResourceSummaryResponse.DatabaseResourceSummary::kind)
			.thenComparing(DatabaseResourceSummaryResponse.DatabaseResourceSummary::name));
		return new DatabaseResourceSummaryResponse(namespace, resources.size(), resources);
	}

	public EventSummaryResponse listEvents(String namespace, boolean warningsOnly, int limit) {
		List<EventSummaryResponse.EventSummary> events = client.v1().events().inNamespace(namespace).list().getItems().stream()
			.filter(event -> !warningsOnly || "Warning".equalsIgnoreCase(event.getType()))
			.sorted(Comparator.comparing(this::eventSortKey).reversed())
			.limit(Math.max(limit, 1))
			.map(this::toEventSummary)
			.toList();
		return new EventSummaryResponse(namespace, events.size(), events);
	}

	public ServiceSummaryResponse listServices(String namespace) {
		List<ServiceSummaryResponse.ServiceSummary> services = client.services().inNamespace(namespace).list().getItems().stream()
			.map(this::toServiceSummary)
			.sorted(Comparator.comparing(ServiceSummaryResponse.ServiceSummary::name))
			.toList();
		return new ServiceSummaryResponse(namespace, services.size(), services);
	}

	public PersistentVolumeClaimSummaryResponse listPersistentVolumeClaims(String namespace) {
		List<PersistentVolumeClaimSummaryResponse.PersistentVolumeClaimSummary> claims = client.persistentVolumeClaims()
			.inNamespace(namespace)
			.list()
			.getItems()
			.stream()
			.map(this::toClaimSummary)
			.sorted(Comparator.comparing(PersistentVolumeClaimSummaryResponse.PersistentVolumeClaimSummary::name))
			.toList();
		return new PersistentVolumeClaimSummaryResponse(namespace, claims.size(), claims);
	}

	private DatabaseResourceSummaryResponse.DatabaseResourceSummary toDatabaseSummary(
		ResourceDescriptor descriptor,
		GenericKubernetesResource resource
	) {
		Map<String, Object> properties = resource.getAdditionalProperties();
		Map<?, ?> status = properties != null && properties.get("status") instanceof Map<?, ?> statusMap ? statusMap : Map.of();
		boolean ready = isReady(status);
		String phase = readString(status.get("phase"));
		String message = readString(status.get("message"));
		String name = resource.getMetadata() != null ? resource.getMetadata().getName() : "";
		return new DatabaseResourceSummaryResponse.DatabaseResourceSummary(
			descriptor.kind(),
			name,
			descriptor.apiVersion(),
			ready,
			phase,
			message
		);
	}

	private boolean isReady(Map<?, ?> status) {
		Object conditionsObject = status.get("conditions");
		if (!(conditionsObject instanceof List<?> conditions)) {
			return false;
		}
		for (Object conditionObject : conditions) {
			if (!(conditionObject instanceof Map<?, ?> condition)) {
				continue;
			}
			String type = String.valueOf(condition.get("type"));
			String state = String.valueOf(condition.get("status"));
			if ("Ready".equalsIgnoreCase(type) && "True".equalsIgnoreCase(state)) {
				return true;
			}
		}
		return false;
	}

	private EventSummaryResponse.EventSummary toEventSummary(Event event) {
		String objectKind = event.getInvolvedObject() != null ? event.getInvolvedObject().getKind() : null;
		String objectName = event.getInvolvedObject() != null ? event.getInvolvedObject().getName() : null;
		return new EventSummaryResponse.EventSummary(
			event.getType(),
			event.getReason(),
			objectKind,
			objectName,
			event.getMessage(),
			readString(event.getLastTimestamp())
		);
	}

	private ServiceSummaryResponse.ServiceSummary toServiceSummary(io.fabric8.kubernetes.api.model.Service service) {
		List<String> ports = service.getSpec() != null && service.getSpec().getPorts() != null
			? service.getSpec().getPorts().stream()
				.map(port -> port.getPort() + "/" + port.getProtocol())
				.toList()
			: List.of();
		return new ServiceSummaryResponse.ServiceSummary(
			service.getMetadata() != null ? service.getMetadata().getName() : "",
			service.getSpec() != null ? service.getSpec().getType() : null,
			service.getSpec() != null ? service.getSpec().getClusterIP() : null,
			ports
		);
	}

	private PersistentVolumeClaimSummaryResponse.PersistentVolumeClaimSummary toClaimSummary(PersistentVolumeClaim claim) {
		String requestedStorage = claim.getSpec() != null && claim.getSpec().getResources() != null
			&& claim.getSpec().getResources().getRequests() != null
			&& claim.getSpec().getResources().getRequests().get("storage") != null
			? claim.getSpec().getResources().getRequests().get("storage").toString()
			: null;
		return new PersistentVolumeClaimSummaryResponse.PersistentVolumeClaimSummary(
			claim.getMetadata() != null ? claim.getMetadata().getName() : "",
			claim.getStatus() != null ? claim.getStatus().getPhase() : null,
			claim.getSpec() != null ? claim.getSpec().getStorageClassName() : null,
			claim.getSpec() != null ? claim.getSpec().getVolumeName() : null,
			requestedStorage
		);
	}

	private OffsetDateTime eventSortKey(Event event) {
		if (event.getSeries() != null && event.getSeries().getLastObservedTime() != null) {
			return OffsetDateTime.parse(event.getSeries().getLastObservedTime().toString());
		}
		if (event.getEventTime() != null) {
			return OffsetDateTime.parse(event.getEventTime().toString());
		}
		if (event.getLastTimestamp() != null) {
			return OffsetDateTime.parse(event.getLastTimestamp());
		}
		if (event.getMetadata() != null && event.getMetadata().getCreationTimestamp() != null) {
			return OffsetDateTime.parse(event.getMetadata().getCreationTimestamp());
		}
		return OffsetDateTime.MIN;
	}

	private String readString(Object value) {
		return value != null ? String.valueOf(value) : null;
	}

	private record ResourceDescriptor(String apiVersion, String kind) {
	}
}
