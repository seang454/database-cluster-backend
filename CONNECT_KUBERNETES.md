

# Connect Spring To Kubernetes

This project runs Spring Boot on Windows and connects to a remote Kubernetes cluster on GCP.
Spring talks to Kubernetes through the Fabric8 client for checks, and uses Helm for the chart-based deploy path.

## SSH Tunnel Setup - Step by Step

### Step 1: Get Your GCP Instance Public IP

Go to the Google Cloud Console:
- Compute Engine
- VM Instances

Copy the External IP of your master node.

### Step 2: Check SSH Key On Windows

```powershell
ls C:\Users\M\.ssh\
```

If you see `id_rsa` or `id_ed25519`, you already have a key.

If the folder is empty, generate one:

```powershell
ssh-keygen -t ed25519 -C "your-email@gmail.com"
```

Press Enter for all questions.

### Step 3: Copy SSH Key To GCP Instance

Option A: via GCP Console

- Go to Compute Engine -> VM Instances
- Click your instance -> Edit
- Find the SSH Keys section
- Click Add Item
- Paste your public key

Get your public key:

```powershell
cat C:\Users\M\.ssh\id_ed25519.pub
```

Copy the output and paste it into the GCP Console, then save.

Option B: via GCP Cloud Shell

```bash
echo "YOUR_PUBLIC_KEY_HERE" >> ~/.ssh/authorized_keys
```

### Step 4: Test SSH Connection

```powershell
ssh YOUR_USERNAME@YOUR_GCP_EXTERNAL_IP
```

Example:

```powershell
ssh m@34.126.xxx.xxx
```

If connected, type `exit` to disconnect.

### Step 5: Copy Kubeconfig To Windows

On your GCP server:

```bash
cat ~/.kube/config
```

On Windows:

```powershell
mkdir C:\Users\M\.kube
notepad C:\Users\M\.kube\config
```

Paste the full kubeconfig content, then save and close.

### Step 6: Update Server IP In Kubeconfig

Open the kubeconfig file:

```powershell
notepad C:\Users\M\.kube\config
```

Change the `server:` line to the correct API endpoint for your setup.

## What You Need

- A running Kubernetes cluster
- Network access to the cluster API server, or an SSH tunnel
- A kubeconfig file on Windows
- Spring Boot running on Windows

## Recommended Setup For This Project

Use an SSH tunnel so Spring can reach the cluster API through `127.0.0.1`.

### 1. Start The SSH Tunnel

Run this from Windows and keep the window open:

```powershell
ssh -N -L 16443:127.0.0.1:6443 window@34.80.140.7
```

What this does:
- `16443` is the local Windows port
- `127.0.0.1:6443` is the Kubernetes API on the remote host
- the tunnel forwards local traffic to the remote cluster

### 2. Use A Kubeconfig That Points To The Local Tunnel

Set the cluster server in your kubeconfig to:

```yaml
server: https://127.0.0.1:16443
```

Keep the same certificate and client key data that belong to your cluster.

### 3. Configure Spring

Set these properties in `src/main/resources/application.properties`:

```properties
kubernetes.client.mode=KUBECONFIG
kubernetes.client.kubeconfig-path=C:/Users/M/.kube/config
```

Optional settings:

```properties
kubernetes.client.trust-certificates=true
kubernetes.client.disable-hostname-verification=true
```

Use those only if your certificate hostname does not match the address you are using.

## Verify The Connection

Call the Spring smoke-test endpoint:

```http
GET /api/kubernetes/test
```

Expected result:
- `connected: true`
- namespace list returned from the cluster

## How The Connection Works

1. Spring reads the kubeconfig from Windows.
2. Spring connects to `https://127.0.0.1:16443`.
3. SSH forwards that port to the remote GCP master.
4. The Kubernetes API responds through the tunnel.

## Helm settings for deploy

Set these before starting Spring:

```properties
cluster.deployment.chart-path=D:/path/to/your/chart
cluster.deployment.defaults-file=D:/path/to/your/chart/values.yaml
cluster.deployment.helm-executable=helm
```

If `helm` is not on PATH, point `cluster.deployment.helm-executable` at `helm.exe`.

## Common Problems

### Connection Refused

If you see `connectex: No connection could be made`, the SSH tunnel is not running.

Fix:
- start the SSH tunnel again
- keep that terminal open

### TLS Hostname Error

If you see a hostname verification error, the API server address does not match the certificate SANs.

Fix:
- use a host that is in the certificate SAN list
- or enable:

```properties
kubernetes.client.trust-certificates=true
kubernetes.client.disable-hostname-verification=true
```

### `kubectl` Works But Spring Fails

Check that both tools use the same kubeconfig and the same tunnel port.

## Notes

- Spring does not start the SSH tunnel automatically.
- The tunnel must be running before Spring starts.
- Use the same kubeconfig path for Spring and `kubectl` if you want both to behave the same.
