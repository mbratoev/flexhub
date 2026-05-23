#!/usr/bin/env bash
# Smoke test — golden path + compensation + statement via the gateway.
# Assumes `kubectl port-forward svc/api-gateway 8080:8080` is NOT already running;
# starts one for the duration of the test, then cleans up.

set -euo pipefail

ALICE=11111111-1111-1111-1111-111111111111
BOB=22222222-2222-2222-2222-222222222222
CAROL=33333333-3333-3333-3333-333333333333

# Start port-forward, capture PID, ensure it dies on exit
kubectl port-forward svc/api-gateway 8080:8080 >/dev/null 2>&1 &
PF=$!
trap "kill ${PF} 2>/dev/null || true" EXIT
sleep 2

balance() { curl -s "http://localhost:8080/accounts/$1" | python3 -c 'import sys,json;print(json.load(sys.stdin)["balance"])'; }
state()   { curl -s "http://localhost:8080/transfers/$1" | python3 -c 'import sys,json;print(json.load(sys.stdin)["state"])'; }

transfer() {
  local key=$1 src=$2 dst=$3 amt=$4
  curl -s -X POST http://localhost:8080/transfers \
    -H 'Content-Type: application/json' -H "Idempotency-Key: $key" \
    -d "{\"sourceAccountId\":\"$src\",\"destinationAccountId\":\"$dst\",\"amount\":$amt}" \
    | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])'
}

poll() {
  local tx=$1 want=$2
  for i in $(seq 1 16); do
    local s=$(state "$tx")
    if [ "$s" = "$want" ]; then echo "  → $want (t=${i})"; return 0; fi
    sleep 0.25
  done
  echo "  → TIMEOUT (last state: $(state $tx))"; return 1
}

echo "Initial balances:"
echo "  Alice = $(balance $ALICE)"
echo "  Bob   = $(balance $BOB)"
echo "  Carol = $(balance $CAROL)"

KEY=smoke-gp-$(date +%s)
echo
echo "Golden path: Alice → Bob 25"
TX=$(transfer "$KEY" "$ALICE" "$BOB" 25.00)
poll "$TX" COMPLETED

KEY=smoke-cp-$(date +%s)
echo
echo "Compensation: Alice → Carol 10 (Carol rejects credits)"
TX=$(transfer "$KEY" "$ALICE" "$CAROL" 10.00)
poll "$TX" FAILED

echo
echo "Final balances:"
echo "  Alice = $(balance $ALICE)"
echo "  Bob   = $(balance $BOB)"
echo "  Carol = $(balance $CAROL)"
