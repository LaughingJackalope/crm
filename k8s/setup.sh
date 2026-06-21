#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════════
# CRM — Local Kind Cluster Setup
# ═══════════════════════════════════════════════════════════════════════════
# Prerequisites:
#   - Docker Desktop running
#   - kind (brew install kind)
#   - kubectl (brew install kubectl)
#
# Images are pulled from GHCR as public packages — no auth needed.
#
# Usage: bash k8s/setup.sh
# ═══════════════════════════════════════════════════════════════════════════

set -euo pipefail

NAMESPACE="crm"
CLUSTER_NAME="crm"

echo "═══════════════════════════════════════════════════════════════════"
echo "  CRM — Local Kind Cluster Setup"
echo "═══════════════════════════════════════════════════════════════════"

# ── 1. Create Kind cluster ─────────────────────────────────────────────────
echo ""
echo "▶ Creating Kind cluster: ${CLUSTER_NAME}"
if kind get clusters 2>/dev/null | grep -q "^${CLUSTER_NAME}$"; then
    echo "  Cluster '${CLUSTER_NAME}' already exists. Skipping creation."
else
    kind create cluster --name "${CLUSTER_NAME}" --config k8s/kind-config.yaml
    echo "  ✅ Cluster created."
fi

# ── 2. Set kubectl context ─────────────────────────────────────────────────
echo ""
echo "▶ Setting kubectl context"
kubectl cluster-info --context "kind-${CLUSTER_NAME}" > /dev/null 2>&1
echo "  ✅ Context: kind-${CLUSTER_NAME}"

# ── 3. Deploy infrastructure ───────────────────────────────────────────────
echo ""
echo "▶ Deploying infrastructure (Postgres, Kong, Kafka, Zookeeper)"
kubectl apply -k k8s/infra/
echo "  ✅ Infrastructure deployed."

echo ""
echo "  Waiting for infrastructure pods to be ready (timeout 120s)..."
kubectl -n "${NAMESPACE}" wait --for=condition=ready pod \
    --selector="app in (postgres, kong, kafka, zookeeper)" \
    --timeout=120s 2>/dev/null || echo "  ⚠️  Some pods not ready yet — check: kubectl -n ${NAMESPACE} get pods"

# ── 4. Deploy services ─────────────────────────────────────────────────────
echo ""
echo "▶ Deploying services (CIAM, Sales, Billing, Support, Marketing, Comms)"
kubectl apply -k k8s/services/
echo "  ✅ Services deployed."

echo ""
echo "  Waiting for service pods to be ready (timeout 120s)..."
kubectl -n "${NAMESPACE}" wait --for=condition=ready pod \
    --selector="app in (ciam-service, sales-service, billing-service, support-service, marketing-service, communication-service)" \
    --timeout=120s 2>/dev/null || echo "  ⚠️  Some pods not ready yet — check: kubectl -n ${NAMESPACE} get pods"

# ── 5. Summary ─────────────────────────────────────────────────────────────
echo ""
echo "═══════════════════════════════════════════════════════════════════"
echo "  Deployment Complete"
echo "═══════════════════════════════════════════════════════════════════"
echo ""
echo "  All services accessible via Kong API Gateway on port 80:"
echo "    CIAM          → http://localhost/ciam/contacts"
echo "    Sales         → http://localhost/sales/opportunities"
echo "    Billing       → http://localhost/billing/invoices"
echo "    Support       → http://localhost/support/tickets"
echo "    Marketing     → http://localhost/marketing/campaigns"
echo "    Communication → http://localhost/communication/messages"
echo ""
echo "  Useful commands:"
echo "    kubectl -n ${NAMESPACE} get pods"
echo "    kubectl -n ${NAMESPACE} logs -f deployment/ciam-service"
echo "    kubectl -n ${NAMESPACE} logs -f deployment/kong"
echo ""
echo "  Teardown:"
echo "    kubectl delete -k k8s/"
echo "    kind delete cluster --name ${CLUSTER_NAME}"
echo ""
