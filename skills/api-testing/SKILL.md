---
name: api-testing
description: Stack-agnostic API testing — REST and GraphQL, contract-first from OpenAPI/Swagger or GraphQL schema, response schema validation, the coverage matrix (happy path, validation, auth, permissions, idempotency, pagination, concurrency, error format), and how to implement it in Playwright TS (request fixture + Zod) or Kotlin (REST Assured / Ktor client + kotlinx.serialization). Use when testing an endpoint, designing API test cases, validating a backend before the UI exists, converting Postman/Bruno collections into automated tests, or investigating API vs UI mismatches.
---

# API testing

## 1. Contract first

1. Source of truth: OpenAPI/Swagger (`/v3/api-docs`, `openapi.yaml`, `swagger.json`) or the GraphQL schema (introspection / `schema.graphql`). Record its location in PROJECT.md.
2. No contract → capture real responses on dev/qa as a *fallback*, mark the schema `inferred`, and add `> [!question] No API documentation for <endpoint>` — missing docs are a finding.
3. Runtime response ≠ documented contract → **bug** (or doc bug). Never loosen the schema to make a test pass; mark the test as known-failing the way the project does and hand to triage.
4. Note endpoints seen in the app in `app-map/api.md` (method, path, status) — never tokens or personal data.

## 2. Coverage matrix (per endpoint)

| Area | Cases |
|---|---|
| Happy path | minimal valid request, full valid request; response status, schema, key values, headers (content-type, caching) |
| Validation | each required field missing; wrong type; boundaries (EP/BVA from `test-design`); empty string vs null; unknown fields; max lengths; special chars & umlauts |
| Auth | no token, expired, malformed, token of another tenant/user → 401/403 (not 404/500) |
| Permissions | role × action matrix from `domain/roles-permissions.md` |
| Resource state | not found, already deleted, invalid state transition (see `domain/states/`) |
| Idempotency | repeated PUT/DELETE; POST retry with idempotency key if supported; double submit |
| Lists | pagination (first/last/empty/out-of-range page), sorting, filtering, size limits |
| Concurrency | two updates on the same resource (optimistic locking / ETag), race on create |
| Errors | consistent error body (code, message, field), no stack traces / internal data in responses |
| Integration | downstream effects (DB row, message, SAP/third-party call) verified via API or DB — not via the UI |

GraphQL extras: query depth/complexity limits, partial errors (`data` + `errors`), field-level auth, nullability, introspection disabled on prod-like envs.

## 3. Implementation

Follow the repo's existing API layer if there is one. Defaults:

**Playwright TS** — `request` fixture (or `request.newContext({ baseURL, extraHTTPHeaders })`), Zod schemas with `z.strictObject`, one `test.step` per call when a test makes several calls, factories for payloads.
```ts
const res = await request.post(Endpoints.ORDERS, { data: OrderFactory.valid() });
expect(res.status()).toBe(201);
expect(OrderSchema.parse(await res.json())).toBeTruthy();
```

**Kotlin** — REST Assured (`given().spec(spec).body(payload).post(path).then().statusCode(201)`) or a Ktor client; DTOs with `@Serializable`; schema validation with REST Assured's `matchesJsonSchemaInClasspath` or `com.networknt:json-schema-validator`; JUnit5 `@ParameterizedTest` + `@MethodSource` for partition tables.

**Postman / Bruno collections** — convert request by request into code; keep environments as config, not literals; secrets stay in env files.

## 4. Rules

- Base URLs, credentials, tokens: from env/config only. Never print tokens in logs or the vault.
- Each test creates its own data and cleans it up (see `test-data`).
- Destructive or mass calls only on dev/qa; never load-test shared environments without approval.
- Report per endpoint: cases covered vs matrix, contract mismatches (bugs), missing docs.
