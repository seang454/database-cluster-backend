# DB Cluster Deployment Backend

Spring Boot backend for deploying one database cluster at a time into Kubernetes by using your Helm chart, Spring Data JPA, and Fabric8 for cluster checks.

This project is not the Kubernetes platform bootstrap itself. It is the application layer that:

- stores deployment configuration in PostgreSQL
- renders Helm values, installs the chart, and still uses the Kubernetes API for readiness checks and deploy-time secrets
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
4. Load the chart defaults file and render request overrides.
5. Create the Cloudflare token secret if provided.
6. Render database passwords into the Helm override file so Helm owns the chart-created secrets.
7. Run `helm upgrade --install` against your chart.
8. Wait for readiness checks through the Kubernetes API.
9. Mark deployment as `DEPLOYED` or `FAILED`.

## What This Project Does Not Bootstrap

These are still expected to exist already, or be managed separately by admin/bootstrap scripts:

- Longhorn
- Vault and Vault transit
- required database operators
- full destructive teardown of the whole platform

The backend assumes the platform is already prepared enough for per-cluster deploys.
The backend also assumes the database chart owns its own per-engine Secrets. Spring only creates the optional Cloudflare API token Secret.

## Tech Stack

- Java 21
- Spring Boot 4
- Spring Data JPA
- Fabric8 Kubernetes Client
- PostgreSQL
- Gradle

## Configuration

Main config lives in [src/main/resources/application.properties](D:\CSTADPreUniversityTraining\ITP\spring\db-cluster\demo\src\main\resources\application.properties).

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
- [DeploymentReadinessService.java](D:\CSTADPreUniversityTraining\ITP\spring\db-cluster\demo\src\main\java\com\example\demo\cluster\service\DeploymentReadinessService.java)
  - waits for Kubernetes custom resources to become ready
- [KubernetesSecretService.java](D:\CSTADPreUniversityTraining\ITP\spring\db-cluster\demo\src\main\java\com\example\demo\cluster\service\KubernetesSecretService.java)
  - creates deploy-time Kubernetes secrets such as Cloudflare token

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

### List saved clusters

```http
GET /api/clusters
```

### Get one saved cluster

```http
GET /api/clusters/{id}
```

### List deployment history for a cluster

```http
GET /api/clusters/{id}/deployments
```

### Deploy one database cluster

```http
POST /api/cluster-deployments
```

### Get deployment status

```http
GET /api/cluster-deployments/{releaseName}?namespace={namespace}
```

### Uninstall deployment

```http
DELETE /api/cluster-deployments/{releaseName}?namespace={namespace}
```

## Example Deployment Request

Use the reusable sample file at [api-test-request.json](D:\CSTADPreUniversityTraining\ITP\spring\db-cluster\demo\api-test-request.json) for the first API test.
Optional cluster fields like `environment`, `domain`, `externalIp`, and `platformConfig` can be omitted if you want the chart defaults to handle them.

```json
{
  "releaseName": "db-seang-postgres",
  "namespace": "ns-seang-postgres",
  "cluster": {
    "name": "seang-postgres"
  },
  "database": {
    "engine": "POSTGRESQL",
    "enabled": true,
    "instances": 3,
    "storageSize": "10Gi",
    "storageClass": "longhorn",
    "externalAccessEnabled": false,
    "publicHostnames": [
      "postgres-db.seang.shop"
    ],
    "tlsEnabled": true,
    "tlsMode": "OPERATOR",
    "resource": {
      "cpuRequest": "250m",
      "memRequest": "512Mi",
      "cpuLimit": "1500m",
      "memLimit": "2Gi",
      "resourceProfile": "MEDIUM"
    },
    "postgresql": {
      "walEnabled": true,
      "walSize": "2Gi",
      "bootstrapDatabase": "appdb",
      "bootstrapOwner": "appuser"
    }
  },
  "secrets": {
    "pgPassword": "secret",
    "mongoPassword": "secret",
    "mysqlPassword": "secret",
    "redisPassword": "secret",
    "cassandraPassword": "secret",
    "cloudflareApiToken": "your-cloudflare-token"
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
- Set `CLUSTER_DEPLOYMENT_DEFAULTS_FILE` to your chart's `values.yaml`.
- Set `CLUSTER_DEPLOYMENT_HELM_EXECUTABLE` if `helm` is not on PATH.
- The Kubernetes cluster must already have the required operators and storage installed if your chart expects them separately.
- Do not pre-create database Secrets with the same names the chart renders, or Helm will reject the install because it cannot adopt resources owned by something else.
