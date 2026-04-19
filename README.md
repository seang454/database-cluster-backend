# DB Cluster Deployment Backend

Spring Boot backend for deploying one database cluster at a time into Kubernetes by using your Helm chart, Spring Data JPA, Fabric8 for cluster checks, and Cloudflare DNS automation.

This project is not the Kubernetes platform bootstrap itself. It is the application layer that:

- stores deployment configuration in PostgreSQL
- renders Helm values, installs the chart, and still uses the Kubernetes API for readiness checks
- waits for post-deploy readiness
- records deployment history

## What This Project Does

One deployment request represents one database cluster.

Current supported engines:

- PostgreSQL
- MongoDB
- MySQL
- Redis
- Cassandra

Current deployment flow:

1. Save cluster config in PostgreSQL.
2. Create a deployment record with status `PENDING`.
3. Mark deployment as `INSTALLING`.
4. Load the checked-in Helm defaults file and render request overrides.
5. Create or update Cloudflare DNS records from the requested `publicHostnames` when Cloudflare is enabled.
6. Render database passwords and namespace/release/cluster-derived backup settings into the Helm override file so Helm owns the chart-created secrets.
7. Run `helm upgrade --install` against your chart.
8. Use the configured MinIO bucket path when backup is enabled. Spring creates the namespace bucket through the MinIO API, then Helm points backup writes at the destination path prefix for that release and database engine.
9. Wait for readiness checks through the Kubernetes API.
10. Mark deployment as `DEPLOYED` or `FAILED`.

## What This Project Does Not Bootstrap

These are still expected to exist already, or be managed separately by admin/bootstrap scripts:

- Longhorn
- Vault and Vault transit
- required database operators
- full destructive teardown of the whole platform

The backend assumes the platform is already prepared enough for per-cluster deploys.
The backend also assumes the database chart owns its own per-engine Secrets. Spring now manages Cloudflare DNS directly through the Cloudflare API instead of relying on chart-side DNS automation.
MinIO bucket creation uses the S3 API endpoint, not the MinIO console URL, so the endpoint must point at the API port. In the old in-cluster flow, Spring uses the MinIO service URL directly inside Kubernetes.
Backup storage paths are rendered by Spring at deploy time, so the chart does not depend on Helm template expressions like `{{ .Release.Name }}` or `{{ .Release.Namespace }}` inside `helm/values.yaml`.
When backup is enabled, Spring renders the destination path into Helm and creates the namespace bucket through the MinIO API. The release and engine paths appear as object prefixes when backups are written.

## Tech Stack

- Java 21
- Spring Boot 4
- Spring Data JPA
- Fabric8 Kubernetes Client
- PostgreSQL
- Gradle

## Configuration

Main config lives in [src/main/resources/application.properties](D:\CSTADPreUniversityTraining\ITP\spring\db-cluster\demo\src\main\resources\application.properties).
The chart defaults used by the app live in [helm/values.yaml](D:\CSTADPreUniversityTraining\ITP\spring\db-cluster\demo\helm\values.yaml).

### Database

By default the app uses local PostgreSQL:

```properties
spring.datasource.url=${APP_DATASOURCE_URL:jdbc:postgresql://localhost:5432/db-cluster}
spring.datasource.username=${APP_DATASOURCE_USERNAME:postgres}
spring.datasource.password=${APP_DATASOURCE_PASSWORD:seang0405}
```

### Kubernetes Access

Spring talks to Kubernetes through the Fabric8 client for health/readiness checks. Point it at your Windows kubeconfig if needed:

```properties
kubernetes.client.mode=AUTO
kubernetes.client.kubeconfig-path=C:/Users/your-user/.kube/config
```

### Local Secrets

For local development, you can keep private values in a root-level `.env` file. The app loads it before Spring starts, then maps values into system properties.

Example:

```env
CLOUDFLARE_API_TOKEN=your_token_here
```

You can copy [.env.example](D:\CSTADPreUniversityTraining\ITP\spring\db-cluster\demo\.env.example) and rename it to `.env`.

## How Spring Uses the Config

The project uses `@ConfigurationProperties`, not `@Value`, for grouped config.

Examples:

- [ClusterDeploymentProperties.java](D:\CSTADPreUniversityTraining\ITP\spring\db-cluster\demo\src\main\java\com\example\demo\cluster\config\ClusterDeploymentProperties.java)

Spring binds property names like:

- `default-release-prefix` -> `defaultReleasePrefix`
- `default-namespace-prefix` -> `defaultNamespacePrefix`

## Main Services

- [ClusterService.java](D:\CSTADPreUniversityTraining\ITP\spring\db-cluster\demo\src\main\java\com\example\demo\cluster\service\ClusterService.java)
  - orchestration, persistence, deployment record handling
- [KubernetesDeploymentService.java](D:\CSTADPreUniversityTraining\ITP\spring\db-cluster\demo\src\main\java\com\example\demo\cluster\service\KubernetesDeploymentService.java)
  - renders Helm overrides, runs the chart install/upgrade, and handles uninstall/status
- [CloudflareDnsService.java](D:\CSTADPreUniversityTraining\ITP\spring\db-cluster\demo\src\main\java\com\example\demo\cluster\service\CloudflareDnsService.java)
  - creates and removes A records for requested public hostnames
- [DeploymentReadinessService.java](D:\CSTADPreUniversityTraining\ITP\spring\db-cluster\demo\src\main\java\com\example\demo\cluster\service\DeploymentReadinessService.java)
  - waits for Kubernetes custom resources to become ready

## Persistence

Core entities:

- `Cluster`
- `DatabaseInstance`
- `DatabaseResource`
- `DatabaseBackup`
- `SecretRef`
- `DeploymentRecord`

Repositories are under [cluster/repository](D:\CSTADPreUniversityTraining\ITP\spring\db-cluster\demo\src\main\java\com\example\demo\cluster\repository).

## API Endpoints

### List saved clusters in a namespace

```http
GET /api/namespaces/{namespace}/clusters
```

### Get one saved cluster

```http
GET /api/namespaces/{namespace}/clusters/{id}
```

### List deployment history for a cluster

```http
GET /api/namespaces/{namespace}/clusters/{id}/deployments
```

### Deploy one database cluster

```http
POST /api/namespaces/{namespace}/cluster-deployments
```

### Get deployment status

```http
GET /api/namespaces/{namespace}/cluster-deployments/{releaseName}
```

### Uninstall a saved cluster

```http
DELETE /api/namespaces/{namespace}/clusters/{id}
```

### Update backup settings

```http
PATCH /api/namespaces/{namespace}/clusters/{id}/backup
```

Enable scheduled backups for PostgreSQL:

```json
{
  "enabled": true,
  "schedule": "0 * * * * *"
}
```

Disable scheduled backups:

```json
{
  "enabled": false
}
```

Update backup destination:

```json
{
  "enabled": true,
  "credentialSecret": "minio-credentials"
}
```

### List pods in a namespace

```http
GET /api/kubernetes/namespaces/{namespace}/pods
```

### Namespace overview

```http
GET /api/kubernetes/namespaces/{namespace}/overview
```

### List database custom resources

```http
GET /api/kubernetes/namespaces/{namespace}/database-resources
```

### List events

```http
GET /api/kubernetes/namespaces/{namespace}/events?warningsOnly=false&limit=50
```

### List services

```http
GET /api/kubernetes/namespaces/{namespace}/services
```

### List persistent volume claims

```http
GET /api/kubernetes/namespaces/{namespace}/persistent-volume-claims
```

### Stream pod logs in real time

```http
GET /api/kubernetes/namespaces/{namespace}/pods/{podName}/logs/stream?tailLines=100
```

Optional query parameter:

- `container`

For a local Next.js app, the backend already allows browser access from `http://localhost:3000` by default.
If your frontend runs on a different origin, update `frontend.cors.allowed-origins` in [application.properties](D:\CSTADPreUniversityTraining\ITP\spring\db-cluster\demo\src\main\resources\application.properties).

Example client code in Next.js:

```ts
const url = new URL(
  `http://localhost:8080/api/kubernetes/namespaces/${namespace}/pods/${podName}/logs/stream`
);
url.searchParams.set("tailLines", "100");

const eventSource = new EventSource(url);

eventSource.addEventListener("log", (event) => {
  console.log("pod log:", event.data);
});

eventSource.addEventListener("open", (event) => {
  console.log("stream opened:", event.data);
});

eventSource.onerror = () => {
  eventSource.close();
};
```

### Test Helm chart access

```http
GET /api/helm/test
```

## Example Deployment Request

Use the reusable sample file at `postman/db-cluster.postman_collection.json` for the first API test.
Optional cluster fields like `environment` and `platformConfig` can be omitted if you want the Spring defaults to handle them.
Cloudflare settings are configured in Spring Boot defaults, so the request does not need `cloudflareEnabled`, `cloudflareZoneName`, or `cloudflareZoneId`.
`database.publicHostnames` is still accepted by the API, but Spring uses it to create Cloudflare DNS records instead of passing it into Helm values.
For PostgreSQL production deploys, Spring and the checked-in Helm defaults now provide the SSL/public-access defaults, so you do not need to send `environment`, `externalAccessEnabled`, `storageSize`, `storageClass`, `resource`, `tlsEnabled`, `tlsMode`, `backup.enabled`, `backup.credentialSecret`, `backup.retentionPolicy`, `postgresql.walEnabled`, or `postgresql.walSize`.
Backup paths are derived from the namespace, release name, and cluster name in Spring-generated Helm overrides:

- PostgreSQL: `s3://<namespace>/<releaseName>/<releaseName>-postgresql`
- MongoDB/MySQL/Cassandra: `<namespace>` bucket with `<releaseName>/<releaseName>-mongodb|mysql|cassandra` prefix

Spring creates the MinIO bucket automatically through the MinIO API before marking the deployment complete. The release folder prefix is represented by the backup object path, not by a separate folder object.

```json
{
  "releaseName": "db-seang-postgres",
  "cluster": {
    "name": "seang-postgres",
    "notes": "Production PostgreSQL cluster"
  },
  "database": {
    "engine": "POSTGRESQL",
    "publicHostnames": [
      "postgres-db.seang.shop"
    ],
    "backup": {
      "enabled": true,
      "schedule": "0 * * * * *"
    },
    "postgresql": {
      "bootstrapDatabase": "appdb",
      "bootstrapOwner": "appuser"
    }
  },
  "secrets": {
    "pgPassword": "secret"
  }
}
```

## Deployment Status

Deployment records currently use:

- `PENDING`
- `INSTALLING`
- `DEPLOYED`
- `FAILED`
- `UNINSTALLED`

`DEPLOYED` now means:

- Helm installed or upgraded the chart successfully
- required readiness checks passed through the Kubernetes API

## Run Locally

Start PostgreSQL locally, then run:

```bash
./gradlew bootRun
```

On Windows PowerShell:

```powershell
.\gradlew.bat bootRun
```

Run tests:

```powershell
.\gradlew.bat test
```

## Notes

- This backend deploys one database cluster per request.
- One release should map to one namespace for cleaner isolation.
- Set `CLUSTER_DEPLOYMENT_CHART_PATH` to your chart directory, packaged `.tgz`, or OCI reference.
- Set `CLUSTER_DEPLOYMENT_DEFAULTS_FILE` to the repo's `helm/values.yaml` or your own chart defaults file.
- Set `CLUSTER_DEPLOYMENT_HELM_EXECUTABLE` if `helm` is not on PATH.
- The Kubernetes cluster must already have the required operators and storage installed if your chart expects them separately.
- Do not pre-create database Secrets with the same names the chart renders, or Helm will reject the install because it cannot adopt resources owned by something else.
