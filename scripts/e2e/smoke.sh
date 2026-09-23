#!/usr/bin/env bash
#
# OrderHub end-to-end smoke test.
#
# Drives the full docker-compose stack through the api-gateway to prove the whole
# saga works: register/login -> admin creates a product -> user places an order ->
# outbox -> Kafka -> payment-service approves it -> order-service flips the order to
# CONFIRMED -> catalog-service decrements stock -> notification-service emails Mailhog.
# Also spot-checks the gateway's Redis-backed rate limiter on /auth/login.
#
# Requires: curl, jq. Assumes the stack is already up (docker compose up -d --build --wait)
# and the gateway is reachable at BASE_URL (default http://localhost:8080).
#
# Usage: ./scripts/e2e/smoke.sh
# Env overrides: BASE_URL, MAILHOG_URL

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
MAILHOG_URL="${MAILHOG_URL:-http://localhost:8025}"

# Seeded by auth-service's Flyway migration V2__seed_admin_user.sql. Dev-only credentials.
ADMIN_EMAIL="admin@orderhub.dev"
ADMIN_PASSWORD="Admin@123"

# Unique per run so re-running the suite never collides with a previous user (register
# is not idempotent — a repeat email gets 409).
USER_EMAIL="smoketest.${RANDOM}${RANDOM}@orderhub.dev"
USER_PASSWORD="Sm0keTest${RANDOM}!"

TMP_BODY="$(mktemp)"
RL_DIR="$(mktemp -d)"

cleanup() {
  rm -f "$TMP_BODY"
  rm -rf "$RL_DIR"
}
trap cleanup EXIT

STEP=""

log() {
  echo "[smoke] $*"
}

fail() {
  echo "" >&2
  echo "[smoke] FAIL at step: ${STEP}" >&2
  echo "[smoke] reason: $*" >&2
  if [ -s "$TMP_BODY" ]; then
    echo "[smoke] last response body:" >&2
    cat "$TMP_BODY" >&2
    echo "" >&2
  fi
  exit 1
}

# Generic HTTP request helper. Writes the response body to $TMP_BODY and prints the
# HTTP status code on stdout. Never lets curl's own exit code kill the script (a
# connection error becomes status "000" so callers can assert on it explicitly).
#
# Usage: status=$(request METHOD URL [BEARER_TOKEN] [JSON_BODY])
request() {
  local method="$1" url="$2" token="${3:-}" data="${4:-}"
  local args=(-s -o "$TMP_BODY" -w '%{http_code}' -X "$method" "$url" -H "Content-Type: application/json")
  if [ -n "$token" ]; then
    args+=(-H "Authorization: Bearer ${token}")
  fi
  if [ -n "$data" ]; then
    args+=(-d "$data")
  fi
  curl "${args[@]}" 2>/dev/null || echo "000"
}

# Polls a boolean check function until it succeeds or the timeout elapses.
# Usage: wait_for "description" timeout_s interval_s check_function_name
wait_for() {
  local description="$1" timeout="$2" interval="$3" check_fn="$4"
  local elapsed=0
  log "waiting for: ${description} (timeout ${timeout}s)"
  while true; do
    if "$check_fn"; then
      log "ready: ${description} (after ${elapsed}s)"
      return 0
    fi
    if [ "$elapsed" -ge "$timeout" ]; then
      return 1
    fi
    sleep "$interval"
    elapsed=$((elapsed + interval))
  done
}

assert_status() {
  local expected="$1" actual="$2" what="$3"
  if [ "$actual" != "$expected" ]; then
    fail "expected HTTP ${expected} for ${what}, got ${actual}"
  fi
}

json_field() {
  # jq -r on $TMP_BODY, returns empty string (not "null") when absent.
  jq -r "$1 // empty" "$TMP_BODY" 2>/dev/null || true
}

# ---------------------------------------------------------------------------
# a) Wait for the gateway to be reachable and healthy.
# ---------------------------------------------------------------------------
STEP="gateway health check"
check_gateway_health() {
  local code
  code=$(curl -s -o /dev/null -w '%{http_code}' "${BASE_URL}/actuator/health" 2>/dev/null || echo "000")
  [ "$code" = "200" ]
}
wait_for "gateway /actuator/health returns 200" 180 5 check_gateway_health || fail "gateway never became healthy within 180s"
log "gateway is up"

# ---------------------------------------------------------------------------
# b) Register a fresh user, log in as that user, and log in as the seeded admin.
# ---------------------------------------------------------------------------
STEP="register user"
register_payload=$(jq -n --arg email "$USER_EMAIL" --arg password "$USER_PASSWORD" \
  '{email: $email, password: $password, firstName: "Smoke", lastName: "Test"}')
status=$(request POST "${BASE_URL}/auth/register" "" "$register_payload")
assert_status "201" "$status" "register user (${USER_EMAIL})"
log "registered user ${USER_EMAIL}"

STEP="login user"
login_payload=$(jq -n --arg email "$USER_EMAIL" --arg password "$USER_PASSWORD" \
  '{email: $email, password: $password}')
status=$(request POST "${BASE_URL}/auth/login" "" "$login_payload")
assert_status "200" "$status" "login user"
USER_TOKEN=$(json_field '.token')
[ -n "$USER_TOKEN" ] || fail "login user response had no token"
log "user JWT captured"

STEP="login admin"
admin_login_payload=$(jq -n --arg email "$ADMIN_EMAIL" --arg password "$ADMIN_PASSWORD" \
  '{email: $email, password: $password}')
status=$(request POST "${BASE_URL}/auth/login" "" "$admin_login_payload")
assert_status "200" "$status" "login admin"
ADMIN_TOKEN=$(json_field '.token')
ADMIN_ROLE=$(json_field '.role')
[ -n "$ADMIN_TOKEN" ] || fail "login admin response had no token"
[ "$ADMIN_ROLE" = "ADMIN" ] || fail "seeded admin login did not report role ADMIN (got '${ADMIN_ROLE}')"
log "admin JWT captured"

# ---------------------------------------------------------------------------
# c) Admin creates a product with stock; a plain user is forbidden from doing so.
# ---------------------------------------------------------------------------
STEP="admin creates product"
INITIAL_STOCK=5
product_payload=$(jq -n --argjson stock "$INITIAL_STOCK" \
  '{name: "Smoke Test Burger", description: "e2e smoke product", price: 10.00, category: "SMOKE", stock: $stock}')
status=$(request POST "${BASE_URL}/api/v1/products" "$ADMIN_TOKEN" "$product_payload")
assert_status "201" "$status" "admin create product"
PRODUCT_ID=$(json_field '.id')
[ -n "$PRODUCT_ID" ] || fail "product create response had no id"
log "admin created product ${PRODUCT_ID} with stock ${INITIAL_STOCK}"

STEP="user forbidden from creating product"
forbidden_payload=$(jq -n '{name: "Should Not Be Created", description: "RBAC check", price: 1.00, category: "SMOKE", stock: 1}')
status=$(request POST "${BASE_URL}/api/v1/products" "$USER_TOKEN" "$forbidden_payload")
assert_status "403" "$status" "user create product (RBAC)"
log "confirmed USER cannot mutate the catalog (403)"

# ---------------------------------------------------------------------------
# d) User places an order; poll until the saga confirms it.
# ---------------------------------------------------------------------------
STEP="user creates order"
ORDER_QTY=2
order_payload=$(jq -n --arg pid "$PRODUCT_ID" --argjson qty "$ORDER_QTY" \
  '{items: [{productId: $pid, quantity: $qty}]}')
status=$(request POST "${BASE_URL}/api/v1/orders" "$USER_TOKEN" "$order_payload")
assert_status "201" "$status" "create order"
ORDER_ID=$(json_field '.id')
ORDER_STATUS=$(json_field '.status')
[ -n "$ORDER_ID" ] || fail "order create response had no id"
[ "$ORDER_STATUS" = "PENDING" ] || fail "order was not created as PENDING (got '${ORDER_STATUS}')"
log "created order ${ORDER_ID} (quantity ${ORDER_QTY}), status PENDING"

STEP="poll order until CONFIRMED"
ORDER_POLL_TIMEOUT=90
ORDER_POLL_INTERVAL=3
elapsed=0
final_status=""
while [ "$elapsed" -lt "$ORDER_POLL_TIMEOUT" ]; do
  status=$(request GET "${BASE_URL}/api/v1/orders/${ORDER_ID}" "$USER_TOKEN")
  assert_status "200" "$status" "get order (poll)"
  final_status=$(json_field '.status')
  log "order ${ORDER_ID} status: ${final_status} (elapsed ${elapsed}s)"
  case "$final_status" in
    CONFIRMED)
      break
      ;;
    PAYMENT_FAILED|CANCELLED)
      fail "order reached a terminal failure state (${final_status}) instead of CONFIRMED"
      ;;
  esac
  sleep "$ORDER_POLL_INTERVAL"
  elapsed=$((elapsed + ORDER_POLL_INTERVAL))
done
[ "$final_status" = "CONFIRMED" ] || fail "order did not reach CONFIRMED within ${ORDER_POLL_TIMEOUT}s (last status: '${final_status}')"
log "saga confirmed order ${ORDER_ID} (outbox -> Kafka -> payment-service -> order-service)"

# ---------------------------------------------------------------------------
# e) Stock was decremented by catalog-service's OrderCreated consumer.
# ---------------------------------------------------------------------------
STEP="poll product stock decrement"
EXPECTED_STOCK=$((INITIAL_STOCK - ORDER_QTY))
STOCK_POLL_TIMEOUT=60
STOCK_POLL_INTERVAL=3
elapsed=0
current_stock=""
while [ "$elapsed" -lt "$STOCK_POLL_TIMEOUT" ]; do
  status=$(request GET "${BASE_URL}/api/v1/products/${PRODUCT_ID}" "")
  assert_status "200" "$status" "get product (poll stock)"
  current_stock=$(json_field '.stock')
  log "product ${PRODUCT_ID} stock: ${current_stock} (elapsed ${elapsed}s)"
  if [ "$current_stock" = "$EXPECTED_STOCK" ]; then
    break
  fi
  sleep "$STOCK_POLL_INTERVAL"
  elapsed=$((elapsed + STOCK_POLL_INTERVAL))
done
[ "$current_stock" = "$EXPECTED_STOCK" ] || fail "stock was not decremented to ${EXPECTED_STOCK} within ${STOCK_POLL_TIMEOUT}s (last value: '${current_stock}')"
log "confirmed stock reservation decremented stock to ${EXPECTED_STOCK}"

# ---------------------------------------------------------------------------
# f) notification-service sent a confirmation email, visible in Mailhog.
# ---------------------------------------------------------------------------
STEP="mailhog confirmation email"
check_mailhog_received() {
  local resp
  resp=$(curl -s "${MAILHOG_URL}/api/v2/messages" 2>/dev/null || echo "")
  [ -n "$resp" ] || return 1
  echo "$resp" | jq -r '.items[]?.Content.Headers.To[]?' 2>/dev/null | grep -F -q "$USER_EMAIL"
}
wait_for "Mailhog message to ${USER_EMAIL}" 60 3 check_mailhog_received || fail "no confirmation email to ${USER_EMAIL} found in Mailhog within 60s"
log "confirmed notification-service delivered the confirmation email to Mailhog"

# ---------------------------------------------------------------------------
# g) Rate-limit spot check: hammer /auth/login with bad credentials.
# ---------------------------------------------------------------------------
STEP="rate limit spot check"
log "hammering /auth/login with bad credentials (30 concurrent requests)..."
bad_login_payload='{"email":"nobody@orderhub.dev","password":"definitely-wrong"}'
for i in $(seq 1 30); do
  (
    code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "${BASE_URL}/auth/login" \
      -H "Content-Type: application/json" -d "$bad_login_payload" 2>/dev/null || echo "000")
    echo "$code" > "${RL_DIR}/${i}"
  ) &
done
wait || true

saw_429=false
for f in "${RL_DIR}"/*; do
  code=$(cat "$f" 2>/dev/null || echo "")
  case "$code" in
    429) saw_429=true ;;
    401|403) : ;; # expected for bad credentials when not rate-limited
  esac
done
if [ "$saw_429" != "true" ]; then
  fail "expected at least one 429 from the gateway's RequestRateLimiter after 30 rapid /auth/login attempts, saw none"
fi
log "confirmed gateway RequestRateLimiter returned 429 under burst load"

# ---------------------------------------------------------------------------
# h) Summary
# ---------------------------------------------------------------------------
echo ""
echo "=================================================================="
echo " PASS — OrderHub end-to-end smoke test"
echo "------------------------------------------------------------------"
echo "  user:            ${USER_EMAIL}"
echo "  product:         ${PRODUCT_ID} (stock ${INITIAL_STOCK} -> ${EXPECTED_STOCK})"
echo "  order:           ${ORDER_ID} (PENDING -> CONFIRMED)"
echo "  notification:    confirmation email seen in Mailhog"
echo "  rate limiter:    429 observed under burst load on /auth/login"
echo "=================================================================="
