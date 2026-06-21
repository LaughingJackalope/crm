# CRM — Local Kubernetes Development

Local Kind cluster deployment for all 6 Bounded Context services.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│  Kind Cluster (crm)                                             │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Kong API Gateway (:80 / NodePort 30080)                 │   │
│  │    /ciam/*         → ciam-service:8080                   │   │
│  │    /sales/*        → sales-service:8080                  │   │
│  │    /billing/*      → billing-service:8080                │   │
│  │    /support/*      → support-service:8080                │   │
│  │    /marketing/*    → marketing-service:8080              │   │
│  │    /communication/*→ communication-service:8080          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                          │                                      │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐        │
│  │ciam-svc  │  │sales-svc │  │billing   │  │support   │  ...   │
│  │  :8080   │  │  :8080   │  │  :8080   │  │  :8080   │        │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘        │
│       │              │              │              │             │
│  ┌────▼──────────────▼──────────────▼──────────────▼─────┐      │
│  │  Shared PostgreSQL (:5432)                             │      │
│  │    Schemas: ciam, sales, billing, support,             │      │
│  │             marketing, communication                   │      │
│  └────────────────────────────────────────────────────────┘      │
│                                                                 │
│  ┌──────────────┐  ┌──────────────┐                             │
│  │    kafka     │  │  zookeeper   │                             │
│  │  :9092       │  │  :2181       │                             │
│  └──────────────┘  └──────────────┘                             │
└─────────────────────────────────────────────────────────────────┘
```

**Key design decisions:**
- **Single entry point**: Kong API Gateway on port 80 routes all traffic via path prefixes.
- **Standard port**: All services listen on port 8080 internally.
- **Shared database**: One PostgreSQL instance with schema-per-service (replaces 6 separate instances).
- **Gateway API**: Kubernetes Gateway API HTTPRoute resources for declarative routing (with Kong as fallback via declarative config).

## Prerequisites

- [Kind](https://kind.sigs.k8s.io/) — `brew install kind`
- [kubectl](https://kubernetes.io/docs/tasks/tools/) — `brew install kubectl`
- [kustomize](https://kubectl.docs.kubernetes.io/installation/kustomize/) — `brew install kustomize`
- Docker Desktop running
- GitHub Container Registry (GHCR) access for pulling images

## Quick Start

### 1. Create the Kind cluster

```bash
kind create cluster --name crm --config k8s/kind-config.yaml
```

### 2. Deploy infrastructure (Kafka + Postgres + Kong)

```bash
kubectl apply -k k8s/infra/
```

Wait for all pods to be ready:

```bash
kubectl -n crm wait --for=condition=ready pod --all --timeout=120s
```

### 3. Deploy services

```bash
kubectl apply -k k8s/services/
```

Wait for all service pods to be ready:

```bash
kubectl -n crm wait --for=condition=ready pod -l "app in (ciam-service,sales-service,billing-service,support-service,marketing-service,communication-service)" --timeout=120s
```

### 4. Access the applications

All services are accessible through Kong on **port 80**:

| Service | URL |
|---|---|
| CIAM (Contacts) | http://localhost/ciam/contacts |
| Sales (Opportunities) | http://localhost/sales/opportunities |
| Billing (Invoices) | http://localhost/billing/invoices |
| Support (Tickets) | http://localhost/support/tickets |
| Marketing (Campaigns) | http://localhost/marketing/campaigns |
| Communication (Messages) | http://localhost/communication/messages |

## Building and Pushing Images

Images are built and pushed to GHCR by the CI pipeline. For local development,
you can build and load images directly into Kind:

```bash
# Build the image using Quarkus Jib
./gradlew :services:ciam-service:build \
  -Dquarkus.container-image.build=true \
  -Dquarkus.container-image.push=false \
  -Dquarkus.container-image.name=ciam-service \
  -Dquarkus.container-image.tag=local

# Load into Kind
kind load docker-image ciam-service:local --name crm

# Update the deployment to use the local image
kubectl -n crm set image deployment/ciam-service ciam-service=ciam-service:local
```

## Useful Commands

```bash
# Check all pods
kubectl -n crm get pods

# Check service logs
kubectl -n crm logs -f deployment/ciam-service

# Check Kong logs
kubectl -n crm logs -f deployment/kong

# Check Kong's routing config
kubectl -n crm exec deployment/kong -- kong config parse /etc/kong/kong.yml

# Check Kafka topics
kubectl -n crm exec -it deployment/kafka -- \
  kafka-topics --bootstrap-server localhost:9092 --list

# Connect to shared Postgres (all schemas in one instance)
kubectl -n crm exec -it statefulset/postgres -- \
  psql -U crm -c "\dn"

# Query a specific schema
kubectl -n crm exec -it statefulset/postgres -- \
  psql -U crm -d crm -c "SET search_path TO ciam; SELECT count(*) FROM customer;"

# Restart a service
kubectl -n crm rollout restart deployment/ciam-service

# Delete everything
kubectl delete -k k8s/
kind delete cluster --name crm
```

## Configuration

Each service is configured via environment variables in its Deployment manifest.
Key variables:

| Variable | Description | Example |
|---|---|---|
| `QUARKUS_DATASOURCE_JDBC_URL` | JDBC URL (shared postgres) | `jdbc:postgresql://postgres:5432/crm` |
| `QUARKUS_DATASOURCE_USERNAME` | DB username | `crm` |
| `QUARKUS_DATASOURCE_PASSWORD` | DB password | `crm` |
| `QUARKUS_KAFKA_BOOTSTRAP_SERVERS` | Kafka brokers | `kafka:9092` |
| `QUARKUS_OIDC_ENABLED` | Enable OIDC auth | `false` (dev) / `true` (prod) |
| `QUARKUS_HTTP_AUTH_PROACTIVE` | Enforce auth on all paths | `false` (dev) / `true` (prod) |

## Resource Budget (24GB Mac)

| Component | Count | Memory Request | Memory Limit |
|---|---|---|---|
| Service pods | 6 | 256Mi each (1.5Gi) | 512Mi each (3Gi) |
| Kong Gateway | 1 | 256Mi | 512Mi |
| PostgreSQL (shared) | 1 | 256Mi | 512Mi |
| Kafka | 1 | 512Mi | 1Gi |
| Zookeeper | 1 | 256Mi | 512Mi |
| **Total** | **10** | **~2.5Gi** | **~5.5Gi** |

This leaves ~18.5Gi for macOS and Docker Desktop overhead.

**Savings vs. previous architecture**: Eliminated 5 Postgres StatefulSets (5 × 256Mi = 1.25Gi saved in limits).

## Troubleshooting

### Pods stuck in `Pending`
Check resource availability: `kubectl -n crm describe pod <name>`

### Pods in `CrashLoopBackOff`
Check logs: `kubectl -n crm logs <pod-name> --previous`

### Services not accessible
- Verify Kong is running: `kubectl -n crm get pods -l app=kong`
- Check Kong logs: `kubectl -n crm logs deployment/kong`
- Test routing: `curl -v http://localhost/ciam/contacts`

### Database connection failures
- Ensure Postgres is ready: `kubectl -n crm get statefulsets`
- Check DNS resolution from a service pod:
  ```bash
  kubectl -n crm exec -it deployment/ciam-service -- nslookup postgres
  ```
- Verify schemas exist:
  ```bash
  kubectl -n crm exec -it statefulset/postgres -- psql -U crm -c "\dn"
  ```

### Kafka connection failures
Ensure Kafka is ready: `kubectl -n crm logs deployment/kafka`
Check advertised listeners match the `KAFKA_ADVERTISED_LISTENERS` env var.
