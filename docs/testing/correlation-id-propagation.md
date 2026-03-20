# Correlation ID Propagation Verification

## Automated Regression Suite

Run the targeted backend propagation suite from the BE workspace root:

```bash
./gradlew correlationIdPropagationChecks
```

This suite verifies the current correlation chain end to end:

- `channel-service` generates `X-Correlation-Id` when absent and forwards the same value to `corebank-service`
- `corebank-service` preserves the incoming value and forwards the same value to `fep-gateway`
- `fep-gateway` preserves the incoming value and bridges the same value to the simulator internal boundary
- `fep-simulator` preserves the incoming value on its correlation and internal-secret filters

Run a single module when you only need one slice:

```bash
./gradlew :channel-service:correlationPropagationTests
./gradlew :corebank-service:correlationPropagationTests
./gradlew :fep-gateway:correlationPropagationTests
./gradlew :fep-simulator:correlationPropagationTests
```

The `fep-gateway` slice includes both unit-level best-effort bridge coverage and the Spring integration proof for the simulator hop.

## Compose Log Evidence

After running a canonical order flow with an explicit `X-Correlation-Id`, search all four backend service logs with that exact value:

```bash
CID="trace-correlation-demo-001"
docker compose logs --since=30m channel-service corebank-service fep-gateway fep-simulator | rg --line-number --context 1 "$CID"
```

Service-scoped fallback:

```bash
docker compose logs --since=30m channel-service | rg --line-number "$CID"
docker compose logs --since=30m corebank-service | rg --line-number "$CID"
docker compose logs --since=30m fep-gateway | rg --line-number "$CID"
docker compose logs --since=30m fep-simulator | rg --line-number "$CID"
```

Expected evidence:

- the same correlation id string appears in `channel-service`
- the same correlation id string appears in `corebank-service`
- the same correlation id string appears in `fep-gateway`
- the same correlation id string appears in `fep-simulator`
- no service regenerates a different correlation id for the same flow

This log query is the operator-readable verification path for Story 8.3 AC 3.
