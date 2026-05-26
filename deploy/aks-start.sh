#!/usr/bin/env bash
# Bring the whole AKS stack up from a fully-destroyed state.
#
# Runs the 7-step sequence:
#   1. terraform apply (Azure infra)
#   2. aks-credentials   — wire kubectl to the cluster
#   3. aks-create-service-account — workload-identity SA
#   4. aks-install-postgres — Bitnami chart inside AKS
#   5. aks-bootstrap-secrets — pull Event Hubs JAAS from Key Vault
#   6. aks-build-push    — build amd64 images, push to ACR
#   7. aks-install       — helm-install the four services
#
# Wall clock: ~25-35 min on a fresh subscription (most of it is the
# Mac-cross-architecture docker build in step 6).
#
# Usage:  make -C deploy aks-start
#         (or: ./aks-start.sh directly from any CWD)
#
# Prerequisites: az login completed; terraform + kubectl + helm + docker
# (buildx) installed; ~/.azure/ has cached credentials.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

# Pretty-print step headers so the long-running script is easy to follow.
step() {
  echo
  echo "════════════════════════════════════════════════════════════════════"
  echo "  $1"
  echo "════════════════════════════════════════════════════════════════════"
}

step "1/7 — terraform apply (Azure infra; ~10-15 min)"
cd "$REPO_ROOT/infra/azure"
# init is idempotent — a fresh clone has no .terraform/, so apply alone would
# fail with "provider ... no version is selected". Safe to run every time.
terraform init -input=false
terraform apply -auto-approve
cd "$REPO_ROOT"

step "2/7 — aks-credentials (merge kubeconfig)"
make -C deploy aks-credentials

step "3/7 — aks-create-service-account (workload-identity SA for the four services)"
make -C deploy aks-create-service-account

step "4/7 — aks-install-postgres (Bitnami chart inside AKS — free-trial workaround)"
make -C deploy aks-install-postgres

step "5/7 — aks-bootstrap-secrets (Event Hubs JAAS config from Key Vault → k8s Secret)"
make -C deploy aks-bootstrap-secrets

step "6/7 — aks-build-push (amd64 images → ACR; ~8-12 min on a Mac)"
make -C deploy aks-build-push

step "7/7 — aks-install (helm install the four services)"
make -C deploy aks-install

step "Waiting for LoadBalancer IP to be allocated (Azure takes 30-60s after install)"
for i in $(seq 1 24); do
  GW_IP=$(kubectl get svc api-gateway -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || true)
  if [ -n "$GW_IP" ]; then break; fi
  echo "  attempt $i: still pending..."
  sleep 5
done

if [ -z "${GW_IP:-}" ]; then
  echo
  echo "LoadBalancer IP not allocated yet. Retry: make -C deploy aks-public-ip"
  exit 1
fi

step "Stack is up"
cat <<EOF

  Gateway public IP:  http://${GW_IP}:8080

  Demo the saga:      make -C deploy aks-smoke-test
  Check pods:         kubectl get pods,svc
  Tear down when done: make -C deploy aks-uninstall && (cd infra/azure && terraform destroy -auto-approve)

EOF
