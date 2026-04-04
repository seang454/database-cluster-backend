# Kubernetes Cluster Setup Guide
## From master01 (GCP) to Windows kubectl + Spring Boot

---

## Overview

This guide covers the full journey of configuring a self-managed Kubernetes cluster
on GCP and connecting to it from a Windows machine using kubectl, helm, and Spring Boot
with the Fabric8 Kubernetes client.

**Infrastructure:**
- 3 master nodes (master01, master02, master03) — `asia-east1`
- 3 worker nodes (worker01, worker02, worker03) — `asia-east1` / `us-east4`
- All nodes: `e2-standard-2` GCE VMs
- Kubernetes version: `v1.34.3`

---

## Part 1 — On master01 (GCP Server)

### 1.1 Get the kubeconfig file

SSH into master01 and run:

```bash
cat /etc/kubernetes/admin.conf
```

Copy the entire output — you will need it on Windows in Part 2.

---

### 1.2 Add external IPs to the API server certificate

By default the TLS certificate only includes internal IPs. To connect from
Windows using the external IP without disabling TLS verification, regenerate
the certificate with external IPs added.

**Step 1 — backup existing certificates**
```bash
sudo cp /etc/kubernetes/pki/apiserver.crt /etc/kubernetes/pki/apiserver.crt.bak
sudo cp /etc/kubernetes/pki/apiserver.key /etc/kubernetes/pki/apiserver.key.bak
```

> Why backup? If certificate regeneration fails, your cluster goes down.
> The backup lets you restore in 10 seconds.

**Step 2 — extract ClusterConfiguration from kubeadm configmap**
```bash
sudo kubectl get configmap kubeadm-config -n kube-system \
  -o jsonpath='{.data.ClusterConfiguration}' > ~/kubeadm-cluster.yaml
```

**Step 3 — add external IPs to certSANs**
```bash
nano ~/kubeadm-cluster.yaml
```

Find the `certSANs` section and add your 3 master external IPs:
```yaml
certSANs:
  - kubernetes
  - kubernetes.default
  - kubernetes.default.svc
  - kubernetes.default.svc.cluster.local
  - 10.233.0.1
  - localhost
  - 127.0.0.1
  - ::1
  - 10.140.0.7
  - 10.140.0.8
  - 10.140.0.6
  - master01
  - master02
  - master03
  - master01.asia-east1-b.c.project-9445469d-6690-4a81-aef.internal
  - master02.asia-east1-a.c.project-9445469d-6690-4a81-aef.internal
  - master03.asia-east1-a.c.project-9445469d-6690-4a81-aef.internal
  # add these external IPs
  - 34.80.140.7    # master01 external IP
  - 35.229.234.83  # master02 external IP
  - 35.221.173.104 # master03 external IP
```

Save with `Ctrl+X` → `Y` → `Enter`

**Step 4 — verify external IPs are in the file**
```bash
grep "34.80\|35.229\|35.221" ~/kubeadm-cluster.yaml
```

Expected output:
```
  - 34.80.140.7
  - 35.229.234.83
  - 35.221.173.104
```

**Step 5 — delete old certificate**
```bash
sudo rm /etc/kubernetes/pki/apiserver.crt
sudo rm /etc/kubernetes/pki/apiserver.key
```

**Step 6 — regenerate certificate**
```bash
sudo kubeadm init phase certs apiserver \
  --config ~/kubeadm-cluster.yaml
```

Expected output:
```
[certs] Generating "apiserver" certificate and key
[certs] apiserver serving cert is signed for DNS names [...] and IPs [...34.80.140.7 35.229.234.83 35.221.173.104]
```

**Step 7 — verify new certificate has external IPs**
```bash
sudo openssl x509 -in /etc/kubernetes/pki/apiserver.crt -text -noout \
  | grep -A2 "Subject Alternative Name"
```

You should see all 3 external IPs in the output.

**Step 8 — restart kubelet**
```bash
sudo systemctl restart kubelet
```

**Step 9 — verify cluster is still running**
```bash
sudo kubectl get nodes
```

Expected output:
```
NAME       STATUS   ROLES           AGE   VERSION
master01   Ready    control-plane   11d   v1.34.3
master02   Ready    control-plane   11d   v1.34.3
master03   Ready    control-plane   11d   v1.34.3
worker01   Ready    <none>          11d   v1.34.3
worker02   Ready    <none>          11d   v1.34.3
worker03   Ready    <none>          11d   v1.34.3
```

**Step 10 — clean up backup files**
```bash
sudo rm /etc/kubernetes/pki/apiserver.crt.bak
sudo rm /etc/kubernetes/pki/apiserver.key.bak
```

---

## Part 2 — On Windows Machine

### 2.1 Install required tools

**kubectl** — already installed via gcloud:
```powershell
gcloud components install kubectl
```

**helm** — install via winget (no admin required):
```powershell
winget install Helm.Helm
```

Verify:
```powershell
helm version
kubectl version --client
```

**gke-gcloud-auth-plugin:**
```powershell
gcloud components install gke-gcloud-auth-plugin
```

---

### 2.2 Setup kubeconfig on Windows

**Step 1 — create the .kube directory if it does not exist**
```powershell
New-Item -ItemType Directory -Force -Path "C:\Users\M\.kube"
```

**Step 2 — create the config file**
```powershell
notepad C:\Users\M\.kube\config
```

Paste the content from `cat /etc/kubernetes/admin.conf` on master01.

**Step 3 — change server IP from localhost to master01 external IP**

Find this line:
```yaml
server: https://127.0.0.1:6443
```

Change to:
```yaml
server: https://34.80.140.7:6443
```

Save and close.

**Step 4 — verify server is set correctly**
```powershell
type C:\Users\M\.kube\config | Select-String "server"
```

Expected:
```
server: https://34.80.140.7:6443
```

---

### 2.3 Open GCP firewall for port 6443

**Step 1 — get your Windows public IP**
```powershell
(Invoke-WebRequest -Uri "https://api.ipify.org").Content
```

**Step 2 — create firewall rule on GCP**
```powershell
gcloud compute firewall-rules create allow-k8s-api `
  --allow tcp:6443 `
  --source-ranges YOUR_WINDOWS_IP/32 `
  --project project-9445469d-6690-4a81-aef
```

> Note: Your public IP may change (ISP dynamic IP). If kubectl stops working,
> check your current IP and update the firewall rule:
```powershell
# get current IP
(Invoke-WebRequest -Uri "https://api.ipify.org").Content

# update firewall rule
gcloud compute firewall-rules update allow-k8s-api `
  --source-ranges YOUR_NEW_IP/32 `
  --project project-9445469d-6690-4a81-aef
```

---

### 2.4 Test connection

```powershell
kubectl get nodes
```

Expected output:
```
NAME       STATUS   ROLES           AGE   VERSION
master01   Ready    control-plane   11d   v1.34.3
master02   Ready    control-plane   11d   v1.34.3
master03   Ready    control-plane   11d   v1.34.3
worker01   Ready    <none>          11d   v1.34.3
worker02   Ready    <none>          11d   v1.34.3
worker03   Ready    <none>          11d   v1.34.3
```

---

### 2.5 Login helm to Harbor

```powershell
helm registry login YOUR_HARBOR_URL `
  --username YOUR_USERNAME `
  --password YOUR_PASSWORD
```

Verify:
```powershell
helm show chart oci://YOUR_HARBOR_URL/YOUR_PROJECT/db-cluster --version 1.0.0
```

---

## Part 3 — Spring Boot + Fabric8 Connection

### 3.1 Dependencies (pom.xml)

```xml
<dependency>
    <groupId>io.fabric8</groupId>
    <artifactId>kubernetes-client</artifactId>
    <version>6.13.0</version>
</dependency>
```

---

### 3.2 Fabric8 auto-config

Fabric8 automatically reads `C:\Users\M\.kube\config` on Windows.
No extra configuration needed in `application.properties`.

```java
@Configuration
public class KubernetesConfig {

    @Bean
    public KubernetesClient kubernetesClient() {
        // reads C:\Users\M\.kube\config automatically on Windows
        return new KubernetesClientBuilder().build();
    }
}
```

---

### 3.3 Helm deploy service

```java
@Service
public class HelmDeployService {

    private static final String HARBOR    = "oci://harbor.yourdomain.com/your-project";
    private static final String NAMESPACE = "databases";

    public String deployChart(String chartName, String releaseName,
                              String version, Map<String, String> values) throws Exception {

        String helmCmd = isWindows()
            ? System.getenv("USERPROFILE") + "\\AppData\\Local\\Microsoft\\WinGet\\Packages\\Helm.Helm_Microsoft.Winget.Source_8wekyb3d8bbwe\\helm.exe"
            : "helm";

        List<String> cmd = new ArrayList<>(List.of(
            helmCmd, "upgrade", "--install", releaseName,
            HARBOR + "/" + chartName,
            "--version",   version,
            "--namespace", NAMESPACE,
            "--create-namespace",
            "--timeout",   "10m"
        ));

        values.forEach((k, v) -> {
            cmd.add("--set");
            cmd.add(k + "=" + v);
        });

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().put("KUBECONFIG",
            System.getProperty("user.home") + "\\.kube\\config");
        pb.redirectErrorStream(true);

        Process process = pb.start();
        String output   = new String(process.getInputStream().readAllBytes());
        int exitCode    = process.waitFor();

        if (exitCode != 0) throw new RuntimeException("Helm failed:\n" + output);
        return output;
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
```

---

### 3.4 Fabric8 watch service

```java
@Service
public class ClusterWatchService {

    private final KubernetesClient client;

    public ClusterWatchService(KubernetesClient client) {
        this.client = client;
    }

    public void watchPods() {
        client.pods()
              .inNamespace("databases")
              .watch(new Watcher<Pod>() {
                  @Override
                  public void eventReceived(Action action, Pod pod) {
                      System.out.println(
                          pod.getMetadata().getName() + " → " + action
                          + " [" + pod.getStatus().getPhase() + "]"
                      );
                  }
                  @Override
                  public void onClose(WatcherException e) {
                      System.err.println("Watch closed: " + e.getMessage());
                  }
              });
    }

    public List<String> listPods() {
        return client.pods()
                     .inNamespace("databases")
                     .list()
                     .getItems()
                     .stream()
                     .map(p -> p.getMetadata().getName())
                     .toList();
    }

    public boolean isPodReady(String releaseName) {
        return client.pods()
                     .inNamespace("databases")
                     .withLabel("app.kubernetes.io/instance", releaseName)
                     .list()
                     .getItems()
                     .stream()
                     .allMatch(pod ->
                         pod.getStatus().getConditions().stream()
                            .anyMatch(c -> "Ready".equals(c.getType())
                                       && "True".equals(c.getStatus()))
                     );
    }
}
```

---

### 3.5 REST controller

```java
@RestController
@RequestMapping("/api/helm")
public class HelmController {

    private final HelmDeployService helmService;
    private final ClusterWatchService watchService;

    public HelmController(HelmDeployService helmService,
                          ClusterWatchService watchService) {
        this.helmService  = helmService;
        this.watchService = watchService;
    }

    @PostMapping("/deploy")
    public ResponseEntity<String> deploy(@RequestBody DeployRequest req) throws Exception {
        String output = helmService.deployChart(
            req.chartName(),
            req.releaseName(),
            req.version(),
            req.values()
        );
        return ResponseEntity.ok(output);
    }

    @GetMapping("/pods")
    public ResponseEntity<List<String>> pods() {
        return ResponseEntity.ok(watchService.listPods());
    }

    @GetMapping("/ready")
    public ResponseEntity<Boolean> ready(@RequestParam String label) {
        return ResponseEntity.ok(watchService.isPodReady(label));
    }

    @DeleteMapping("/uninstall/{releaseName}")
    public ResponseEntity<String> uninstall(@PathVariable String releaseName) throws Exception {
        return ResponseEntity.ok(helmService.uninstallChart(releaseName));
    }
}

record DeployRequest(String chartName, String releaseName,
                     String version, Map<String, String> values) {}
```

---

### 3.6 Example API calls

**Deploy db-cluster chart:**
```powershell
curl -X POST http://localhost:8080/api/helm/deploy `
  -H "Content-Type: application/json" `
  -d '{
    "chartName":   "db-cluster",
    "releaseName": "my-db",
    "version":     "1.0.0",
    "values": {
      "postgresql.enabled": "true",
      "mongodb.enabled":    "true"
    }
  }'
```

**List pods:**
```powershell
curl http://localhost:8080/api/helm/pods
```

**Check if release is ready:**
```powershell
curl "http://localhost:8080/api/helm/ready?label=my-db"
```

**Uninstall:**
```powershell
curl -X DELETE http://localhost:8080/api/helm/uninstall/my-db
```

---

## Part 4 — Troubleshooting

### kubectl connection refused
```
dial tcp 127.0.0.1:6443: connectex: No connection could be made
```
**Fix:** kubeconfig server is still pointing to localhost. Change to `34.80.140.7:6443`.

---

### kubectl connection timeout
```
dial tcp 34.80.140.7:6443: connectex: A connection attempt failed
```
**Fix:** GCP firewall is blocking port 6443. Update firewall rule with your current IP:
```powershell
(Invoke-WebRequest -Uri "https://api.ipify.org").Content
gcloud compute firewall-rules update allow-k8s-api `
  --source-ranges YOUR_CURRENT_IP/32 `
  --project project-9445469d-6690-4a81-aef
```

---

### TLS certificate error
```
x509: certificate is valid for 10.140.0.7, not 34.80.140.7
```
**Fix:** Regenerate API server certificate with external IPs added (see Part 1.2).

---

### helm not found in ProcessBuilder
**Fix:** Use full path to helm.exe or add helm to Windows PATH:
```powershell
where.exe helm
```

---

### Dynamic IP change
Your Windows public IP changes when you reconnect to the internet.
Always check before running kubectl:
```powershell
(Invoke-WebRequest -Uri "https://api.ipify.org").Content
```

---

## Quick Reference

| Task | Command |
|---|---|
| Check Windows public IP | `(Invoke-WebRequest -Uri "https://api.ipify.org").Content` |
| Update firewall IP | `gcloud compute firewall-rules update allow-k8s-api --source-ranges IP/32 --project PROJECT` |
| List all nodes | `kubectl get nodes` |
| List all pods | `kubectl get pods -n databases` |
| List helm releases | `helm list -n databases` |
| Deploy chart | `helm upgrade --install my-db oci://HARBOR/PROJECT/db-cluster --version 1.0.0 -n databases` |
| Uninstall chart | `helm uninstall my-db -n databases` |
| SSH into master01 | `gcloud compute ssh master01 --zone asia-east1-b --project PROJECT` |
| Check API cert SANs | `sudo openssl x509 -in /etc/kubernetes/pki/apiserver.crt -text -noout \| grep -A2 "Subject Alternative"` |
