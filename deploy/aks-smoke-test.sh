#!/usr/bin/env bash
# AKS smoke test — runs the saga (golden path + compensation) against
# the gateway's public LoadBalancer IP. Mirror of deploy/smoke-test.sh
# for the Azure stack (no port-forward needed).
#
# Usage:  make -C deploy aks-smoke-test
#         (or: ./aks-smoke-test.sh directly)

set -euo pipefail

GW_IP=$(kubectl get svc api-gateway -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || true)
if [ -z "$GW_IP" ]; then
  echo "No LoadBalancer IP yet. Run 'make -C deploy aks-public-ip' and retry once it's allocated."
  exit 1
fi
GW="http://${GW_IP}:8080"
echo "Gateway: $GW"
echo

# Deterministic seed accounts (account-service creates these on first boot).
ALICE=11111111-1111-1111-1111-111111111111
BOB=22222222-2222-2222-2222-222222222222
CAROL=33333333-3333-3333-3333-333333333333

balance() { curl -s "$GW/accounts/$1" | python3 -c 'import sys,json;print(json.load(sys.stdin)["balance"])'; }
state()   { curl -s "$GW/transfers/$1" | python3 -c 'import sys,json;print(json.load(sys.stdin)["state"])'; }

transfer() {
  local key=$1 src=$2 dst=$3 amt=$4
  curl -s -X POST "$GW/transfers" \
    -H 'Content-Type: application/json' -H "Idempotency-Key: $key" \
    -d "{\"sourceAccountId\":\"$src\",\"destinationAccountId\":\"$dst\",\"amount\":$amt}" \
    | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])'
}

poll() {
  local tx=$1 want=$2
  for i in $(seq 1 16); do
    local s=$(state "$tx")
    if [ "$s" = "$want" ]; then echo "  → $want (t=${i})"; return 0; fi
    sleep 0.5
  done
  echo "  → TIMEOUT (last state: $(state $tx))"; return 1
}

echo "Initial balances:"
echo "  Alice = $(balance $ALICE)"
echo "  Bob   = $(balance $BOB)"
echo "  Carol = $(balance $CAROL)"

KEY=aks-smoke-gp-$(date +%s)
echo
echo "Golden path: Alice → Bob 25"
TX=$(transfer "$KEY" "$ALICE" "$BOB" 25.00)
poll "$TX" COMPLETED

KEY=aks-smoke-cp-$(date +%s)
echo
echo "Compensation: Alice → Carol 10 (Carol rejects credits)"
TX=$(transfer "$KEY" "$ALICE" "$CAROL" 10.00)
poll "$TX" FAILED

echo
echo "Final balances:"
echo "  Alice = $(balance $ALICE)"
echo "  Bob   = $(balance $BOB)"
echo "  Carol = $(balance $CAROL)"
