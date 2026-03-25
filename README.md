# FIXYZ-BE
Let's FIX! BE!

## Local profile reference

`application-local.yml.template` is the reviewer-facing backend local-profile guide.
It summarizes the shared env contract and links the runtime defaults back to the service-local files that actually boot with Spring:

- `channel-service/src/main/resources/application-local.yml`
- `corebank-service/src/main/resources/application-local.yml`
- `fep-gateway/src/main/resources/application-local.yml`
- `fep-simulator/src/main/resources/application-local.yml`

Use `../.env.example` together with [`application-local.yml.template`](./application-local.yml.template) when you need to understand or explain the local backend runtime setup without reading all four service files first.

## Docs
- [CoreBank position lock observability](docs/testing/corebank-position-lock-observability.md)
- [Correlation ID propagation verification](docs/testing/correlation-id-propagation.md)
