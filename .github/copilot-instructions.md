# dinesykmeldte-backend

- `./gradlew build` runs build, tests and lint; `./gradlew test` runs tests.
  Database tests start PostgreSQL through Testcontainers and need Docker.
- Local startup: `mise docker-up`, then `mise start`. The compose tasks include
  the local auth server, Texas, PostgreSQL and Kafka.
- A valid TokenX token does not establish access to an employee's data. Keep
  sykmelding, søknad and hendelse reads/updates scoped to the authenticated
  leader's person identifier and the corresponding nearest-leader relation.
- `/api/sykmelding/isActiveSykmelding` uses Azure AD through Texas; keep this
  machine API separate from the citizen/leader TokenX routes.
- The OpenAPI file `api/oas3/dinesykmeldte-backend-api.yaml` is incomplete;
  inspect the actual route when changing an endpoint.
- Person identifiers and sykmelding/søknad content belong outside ordinary
  logs; preserve the existing separation from restricted team logs.
