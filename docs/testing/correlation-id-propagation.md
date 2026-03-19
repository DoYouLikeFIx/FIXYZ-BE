# Correlation ID Propagation Verification

## Automated Regression Suite

Run the targeted backend propagation suite from the BE workspace root:

```bash
./gradlew correlationIdPropagationChecks
```

This suite verifies the current correlation chain hop by hop:

- `channel-service` verifies the real `/execute` boundary forwards the same `X-Correlation-Id` and `traceparent` to the outbound corebank request
- `corebank-service` verifies the real `/internal/v1/orders` boundary forwards the same `X-Correlation-Id` and `traceparent` to the outbound fep-gateway request
- `fep-gateway` verifies an explicit internal diagnostic endpoint forwards supplied trace headers to the simulator boundary without touching the business order flow
- `fep-simulator` verifies the internal diagnostic boundary preserves the supplied headers and emits an explicit receipt log

Run a single module when you only need one slice:

```bash
./gradlew :channel-service:correlationPropagationTests
./gradlew :corebank-service:correlationPropagationTests
./gradlew :fep-gateway:correlationPropagationTests
./gradlew :fep-simulator:correlationPropagationTests
```

The aggregate task is a hop-by-hop regression gate. It is not a single four-service full-chain test, and the gateway to simulator check is an explicit internal diagnostic boundary rather than part of the canonical order submission flow.

## Gateway to Simulator Diagnostic Probe

When you need live evidence for the gateway to simulator hop, call the explicit diagnostic endpoint with fixed headers:

```bash
CID="trace-correlation-demo-001"
TRACEPARENT="00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"

curl -sS \
  -H "X-Internal-Secret: ${INTERNAL_SECRET:-local-internal-secret}" \
  -H "X-Correlation-Id: ${CID}" \
  -H "traceparent: ${TRACEPARENT}" \
  http://localhost:8083/fep-internal/v1/diagnostics/trace-forwarding/simulator
```

Then inspect only the two services that actually participate in that diagnostic hop:

```bash
docker compose logs --since=30m fep-gateway | rg --line-number "$CID"
docker compose logs --since=30m fep-simulator | rg --line-number "$CID"
```

Expected evidence:

- the same correlation id string appears in the gateway diagnostic log and the simulator internal diagnostic log
- `fep-gateway` emits `operation=SIMULATOR_TRACE_DIAGNOSTIC` when the forward succeeds or fails
- `fep-simulator` emits `operation=SIMULATOR_TRACE_DIAGNOSTIC_RECEIVED` when the internal ping is accepted
- no service regenerates a different correlation id for that explicit diagnostic flow

Use the automated regression suite for the actual `channel -> corebank -> fep-gateway` business boundaries. Use the diagnostic probe only when you need live operator-readable evidence for the explicit gateway to simulator boundary.
