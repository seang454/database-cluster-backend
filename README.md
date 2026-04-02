# DB Cluster Deployment Backend

Spring Boot backend for deploying one database cluster at a time into Kubernetes by using Helm, `kubectl`, and Spring Data JPA.

This project is not the Kubernetes platform bootstrap itself. It is the application layer that:

- stores deployment configuration in PostgreSQL
- generates Helm override values
- installs required operators for the selected database engine
- deploys the chart from an OCI registry
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

1. Save cluster config in PostgreSQL
2. Create a deployment record with status `PENDING`
3. Mark deployment as `INSTALLING`
4. Ensure Helm repos and required operator are installed
5. Create Cloudflare token secret if provided
6. Deploy the Helm chart from Harbor OCI
7. Wait for readiness checks
8. Mark deployment as `DEPLOYED` or `FAILED`

## What This Project Does Not Bootstrap

These are still expected to exist already, or be managed separately by admin/bootstrap scripts:

- Longhorn
- Vault transit bootstrap
- main Vault install/init
- full destructive teardown of the whole platform

The backend assumes the platform is already prepared enough for per-cluster deploys.

## Tech Stack

- Java 21
- Spring Boot 4
- Spring Data JPA
- PostgreSQL
- Gradle
- Helm
- kubectl

## Configuration

Main config lives in [application.properties](D:\CSTADPreUniversityTraining\ITP\spring\db-cluster\demo\src\main\resources\application.properties).

### Database

By default the app uses local PostgreSQL:

```properties
spring.datasource.url=${APP_DATASOURCE_URL:jdbc:postgresql://localhost:5432/db-cluster}
spring.datasource.username=${APP_DATASOURCE_USERNAME:postgres}
spring.datasource.password=${APP_DATASOURCE_PASSWORD:seang0405}
```

### Helm Chart Source

The chart is deployed from Harbor OCI:

```properties
cluster.deployment.helm-executable=${HELM_EXECUTABLE:helm}
cluster.deployment.chart-reference=${CLUSTER_CHART_REFERENCE:oci://harbor.devith.it.com/db-cluster/db-cluster}
cluster.deployment.chart-version=${CLUSTER_CHART_VERSION:4.0.0}
cluster.deployment.default-values-file=${CLUSTER_DEFAULT_VALUES_FILE:D:/CSTADPreUniversityTraining/ITP/iacfinal/db-cluster-autounseal/db-cluster-autounseal/db-cluster/values.yaml}
```

Before deploying, the machine running Spring must already be logged in to Harbor:

```bash
helm registry login harbor.devith.it.com
```

### Operator and kubectl Settings

These properties control deploy-time operator installation and readiness checks:

```properties
cluster.operations.kubectl-executable=${KUBECTL_EXECUTABLE:kubectl}
cluster.operations.external-secrets-namespace=${CLUSTER_EXTERNAL_SECRETS_NAMESPACE:external-secrets}
cluster.operations.external-secrets-release-name=${CLUSTER_EXTERNAL_SECRETS_RELEASE_NAME:external-secrets}
cluster.operations.cnpg-namespace=${CLUSTER_CNPG_NAMESPACE:cnpg-system}
cluster.operations.cnpg-release-name=${CLUSTER_CNPG_RELEASE_NAME:cnpg}
cluster.operations.psmdb-release-name=${CLUSTER_PSMDB_RELEASE_NAME:psmdb-operator}
cluster.operations.pxc-release-name=${CLUSTER_PXC_RELEASE_NAME:pxc-operator}
cluster.operations.redis-operator-release-name=${CLUSTER_REDIS_OPERATOR_RELEASE_NAME:redis-operator}
cluster.operations.k8ssandra-release-name=${CLUSTER_K8SSANDRA_RELEASE_NAME:k8ssandra-operator}
cluster.operations.cert-manager-namespace=${CLUSTER_CERT_MANAGER_NAMESPACE:cert-manager}
cluster.operations.cert-manager-release-name=${CLUSTER_CERT_MANAGER_RELEASE_NAME:cert-manager}
cluster.operations.cnpg-version=${CLUSTER_CNPG_VERSION:0.21.0}
cluster.operations.psmdb-version=${CLUSTER_PSMDB_VERSION:1.15.0}
cluster.operations.pxc-version=${CLUSTER_PXC_VERSION:1.14.0}
cluster.operations.redis-operator-version=${CLUSTER_REDIS_OPERATOR_VERSION:0.24.0}
cluster.operations.k8ssandra-version=${CLUSTER_K8SSANDRA_VERSION:1.14.0}
cluster.operations.cert-manager-version=${CLUSTER_CERT_MANAGER_VERSION:v1.15.3}
```

## How Spring Uses the Config

The project uses `@ConfigurationProperties`, not `@Value`, for grouped config.

Examples:

- [ClusterDeploymentProperties.java](D:\CSTADPreUniversityTraining\ITP\spring\db-cluster\demo\src\main\java\com\example\demo\cluster\config\ClusterDeploymentProperties.java)
- [ClusterOperationsProperties.java](D:\CSTADPreUniversityTraining\ITP\spring\db-cluster\demo\src\main\java\com\example\demo\cluster\config\ClusterOperationsProperties.java)
- [KubernetesClientProperties.java](D:\CSTADPreUniversityTraining\ITP\spring\db-cluster\demo\src\main\java\com\example\demo\cluster\config\KubernetesClientProperties.java)

Spring binds property names like:

- `helm-executable` -> `helmExecutable`
- `chart-reference` -> `chartReference`
- `redis-operator-version` -> `redisOperatorVersion`

## Main Services

- [ClusterService.java](D:\CSTADPreUniversityTraining\ITP\spring\db-cluster\demo\src\main\java\com\example\demo\cluster\service\ClusterService.java)
  - orchestration, persistence, deployment record handling
- [HelmReleaseService.java](D:\CSTADPreUniversityTraining\ITP\spring\db-cluster\demo\src\main\java\com\example\demo\cluster\service\HelmReleaseService.java)
  - runs Helm install/status/uninstall
- [HelmValuesService.java](D:\CSTADPreUniversityTraining\ITP\spring\db-cluster\demo\src\main\java\com\example\demo\cluster\service\HelmValuesService.java)
  - generates override `values.yaml`
- [OperatorInstallerService.java](D:\CSTADPreUniversityTraining\ITP\spring\db-cluster\demo\src\main\java\com\example\demo\cluster\service\OperatorInstallerService.java)
  - installs required operator before deployment
- [DeploymentReadinessService.java](D:\CSTADPreUniversityTraining\ITP\spring\db-cluster\demo\src\main\java\com\example\demo\cluster\service\DeploymentReadinessService.java)
  - waits for secrets and workloads to be ready
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

### Save cluster config only

```http
POST /api/clusters
```

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

### Get Helm release status

```http
GET /api/cluster-deployments/{releaseName}?namespace={namespace}
```

### Uninstall Helm release

```http
DELETE /api/cluster-deployments/{releaseName}?namespace={namespace}
```

## Example Deployment Request

```json
{
  "releaseName": "db-seang-postgres",
  "namespace": "ns-seang-postgres",
  "cluster": {
    "name": "seang-postgres",
    "environment": "DEVELOPMENT",
    "domain": "seang.shop",
    "externalIp": "35.194.146.154",
    "platformConfig": {
      "cloudflareEnabled": true,
      "cloudflareZoneName": "seang.shop",
      "vaultEnabled": true
    }
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

- Helm install succeeded
- required readiness checks passed

It is no longer just “Helm exit code was 0”.

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
- The local default values file is still required even when the chart comes from OCI.
- If operator versions or namespaces change in the cluster, update `cluster.operations.*`.
