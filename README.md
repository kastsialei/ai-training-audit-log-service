# audit-log-service

Internal append-only audit event store. Java 21, Spring Boot 3, Postgres + Flyway.

## Layout

```
src/main/java/net/sam/ai/engineering/audit/
  domain/           # business rules, no framework deps
  application/      # use cases / orchestration
  infrastructure/   # JPA, external integrations
  api/              # REST controllers
```

Dependencies point inward: `api → application → domain`, infrastructure plugs
into application ports. See `ARCHITECTURE.md` for layering rules, naming
conventions, and the full repo map.

## Local dev

Start Postgres:

```bash
docker compose -f deploy/docker-compose.yml up -d
```

Run the app:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
curl localhost:8080/api/health
curl localhost:8080/actuator/health
```

## Tests

```bash
./mvnw -B verify
```

- Unit tests: `*Test` (surefire) — JUnit 5 + AssertJ.
- Integration tests: `*IT` (failsafe) — Testcontainers spins up Postgres 16.

## Docker image

Local build to daemon (requires Docker):

```bash
./mvnw -DskipTests jib:dockerBuild
```

Build tar without daemon:

```bash
./mvnw -DskipTests jib:buildTar
```

CI pushes to `ghcr.io/<owner>/audit-log-service` on push to `main`
(`:main-<sha>`, `:latest`) and on `v*` tags (`:<semver>`).

## Kubernetes

```bash
kubectl apply -f deploy/k8s/namespace.yaml
kubectl apply -f deploy/k8s/secret.example.yaml   # replace placeholders first
kubectl apply -f deploy/k8s/configmap.yaml
kubectl apply -f deploy/k8s/postgres.yaml         # dev only; use managed DB in prod
kubectl apply -f deploy/k8s/deployment.yaml
kubectl apply -f deploy/k8s/service.yaml
```

Replace `OWNER` in `deployment.yaml` and `pom.xml` `jib` config with the
GitHub org/user before deploying.
