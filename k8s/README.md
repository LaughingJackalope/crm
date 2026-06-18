# CRM — Local Kubernetes Development

Local Kind cluster deployment for all 6 Bounded Context services.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│  Kind Cluster (crm)                                             │
│                                                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ ciam-service │  │sales-service │  │billing-svc   │  ...     │
│  │  :8081       │  │  :8082       │  │  :8083       │          │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘          │
│         │                 │                 │                   │
│  ┌──────▼───────┐  ┌──────▼───────┐  ┌──────▼───────┐          │
│  │ postgres-ciam│  │postgres-sales│  │postgres-bill │  ...     │
│  │  :5432       │  │  :5432       │  │  :5432       │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
│                                                                 │
│  ┌──────────────┐  ┌──────────────┐                             │
│  │    kafka     │  │  zookeeper   │                             │
│  │  :9092       │  │  :2181       │                             │
│  └──────────────┘  └──────────────┘                             │
└─────────────────────────────────────────────────────────────────┘
```

Each service has its own PostgreSQL instance (StatefulSet) and connects to a
shared Kafka cluster. Services discover each other via Kubernetes DNS
(`<service-name>.crm.svc.cluster.local`).

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

### 2. Deploy infrastructure (Kafka + Postgres)

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

| Service | URL |
|---|---|
| CIAM (Contacts) | http://localhost:8081/ciam/contacts |
| Sales (Opportunities) | http://localhost:8082/sales/opportunities |
| Billing (Invoices) | http://localhost:8083/billing/invoices |
| Support (Tickets) | http://localhost:8084/support/tickets |
| Marketing (Campaigns) | http://localhost:8085/marketing/campaigns |
| Communication (Messages) | http://localhost:8086/communication/messages |

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

# Port-forward a specific service (alternative to NodePort)
kubectl -n crm port-forward svc/ciam-service 8081:8081

# Check Kafka topics
kubectl -n crm exec -it deployment/kafka -- \
  kafka-topics --bootstrap-server localhost:9092 --list

# Connect to a Postgres instance
kubectl -n crm exec -it statefulset/postgres-ciam -- \
  psql -U crm -c "SELECT count(*) FROM ciam.customer;"

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
| `QUARKUS_DATASOURCE_JDBC_URL` | JDBC URL | `jdbc:postgresql://postgres-ciam:5432/crm` |
| `QUARKUS_DATASOURCE_USERNAME` | DB username | `crm` |
| `QUARKUS_DATASOURCE_PASSWORD` | DB password | `crm` |
| `QUARKUS_KAFKA_BOOTSTRAP_SERVERS` | Kafka brokers | `kafka:9092` |
| `QUARKUS_OIDC_ENABLED` | Enable OIDC auth | `false` (dev) / `true` (prod) |
| `QUARKUS_HTTP_AUTH_PROACTIVE` | Enforce auth on all paths | `false` (dev) / `true` (prod) |

## Resource Budget (24GB Mac)

| Component | Count | Memory Request | Memory Limit |
|---|---|---|---|
| Service pods | 6 | 256Mi each (1.5Gi) | 512Mi each (3Gi) |
| Postgres StatefulSets | 6 | 128Mi each (768Mi) | 256Mi each (1.5Gi) |
| Kafka | 1 | 512Mi | 1Gi |
| Zookeeper | 1 | 256Mi | 512Mi |
| **Total** | **14** | **~3.5Gi** | **~6Gi** |

This leaves ~18Gi for macOS and Docker Desktop overhead.

## Troubleshooting

### Pods stuck in `Pending`
Check resource availability: `kubectl -n crm describe pod <name>`

### Pods in `CrashLoopBackOff`
Check logs: `kubectl -n crm logs <pod-name> --previous`

### Services not accessible
Verify NodePort mapping: `kubectl -n crm get svc`
Check pod readiness: `kubectl -n crm get pods -o wide`

### Database connection failures
Ensure Postgres pods are ready: `kubectl -n crm get statefulsets`
Check DNS resolution from a service pod:
```bash
kubectl -n crm exec -it deployment/ciam-service -- nslookup postgres-ciam
```

### Kafka connection failures
Ensure Kafka is ready: `kubectl -n crm logs deployment/kafka`
Check advertised listeners match the `KAFKA_ADVERTISED_LISTENERS` env var.
