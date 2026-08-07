# 336 AI HTTP SSRF Egress Enforcement

> Plan Status: draft
> Last Reviewed: 2026-08-08
> Review Hold: Blocked on User-Authorization Gate (DR-4a) — requires explicit user disposition of the enforcement-layer decision before implementation can begin. Cannot be resolved at review time; correctly held at draft. (2026-08-08 review pass: format/completeness/scope/closure all PASS; all source anchors in Current Baseline and Public-API Migration Consideration verified against live repo — `HttpRequestExecutor` :41/:78/:133, `GraphqlQueryExecutor` :31/:93/:116, `HttpClientConfig.dnsResolver` :66, `IDnsResolver` :38-49. Plan is ready to activate once DR-4a is authorized.)
> Source: `ai-dev/analysis/2026-08/2026-08-04-security-hardening-baseline.md` (DR-4a, DR-3b)
> Related: `ai-dev/plans/328-security-hardening-remediation-planning.md`
> Predecessor: Plan 328 Phase 1 froze the decision records this plan consumes.

## Purpose

Resolve the AI HTTP SSRF finding (M-4) by closing the gap between pre-flight host-text
validation and the actual connection/redirect/proxy behavior owned by the HTTP transport.
This plan is **blocked at draft until the user approves the SSRF enforcement contract**
(DR-4a), including the enforcement layer and whether the public `IHttpClient` contract must
change.

## User-Authorization Gate (BLOCKER)

This plan changes AI tool network-egress behavior and may require a public `nop-http-api`
contract change. It MUST remain `Plan Status: draft` with a blocked implementation slice
until the user records an explicit disposition for:

- DR-4a (enforcement layer, redirect ownership, multi-address/DNS-rebinding policy,
  IPv4/IPv6 encoded-address policy, proxy policy, connection-address pinning, fail-closed
  behavior, public HTTP API migration gate).

## Current Baseline

See Plan 328 analysis DR-4a/DR-3b for verified source anchors. Summary:

- `HttpRequestExecutor.validateUrl` parses the URL with `java.net.URI`, checks scheme and a
  regex/private-IP / cloud-metadata blocklist. This is pre-flight text validation only.
- The actual connection is `httpClient.fetch(request, null)`; `IHttpClient` implementations
  own redirect following (`HttpClientConfig.followRedirects`) and proxy behavior
  (`HttpClientConfig` proxy config).
- Because validator and transport are separate components, the validator cannot see
  redirect hops, DNS rebinding, or multi-address resolution: a public hostname that
  302-redirects to `169.254.169.254` passes pre-flight and is followed by the transport.
- `IHttpClient` is injectable (`HttpRequestExecutor.setHttpClient`), so a fake client can
  assert "no request was sent for a denied target" — the seam for no-request assertions.

## Goals

- A target-validation layer that owns BOTH target validation and actual connection
  establishment, so redirects, DNS rebinding, and multi-address resolution cannot bypass it.
- Every redirect hop and the final connection are validated before transport sends data.
- Unresolved or policy-ambiguous targets fail closed (no request sent).
- IPv4/IPv6 encoded representations normalize to the same internal/external verdict as the
  literal form.

## Non-Goals

- Replacing the HTTP client implementation; this plan wires enforcement at the right seam.
- AI Bash isolation (Plan 335).
- Authentication/JWT (Plan 333) and AES encrypted values (Plan 334).
- Generated (`_`-prefixed) files.

## Scope

### In Scope

- `HttpRequestExecutor` (validator-to-transport wiring).
- The enforcement layer chosen in DR-4a (resolver+pinning layer, transport callback, or
  `IHttpClient` contract change).
- Redirect-hop, DNS-rebinding, multi-address, encoded-address, and proxy policies.
- No-request transport assertion tests via the injectable `IHttpClient` seam.

### Out Of Scope

- Authentication/JWT (Plan 333), AES (Plan 334), Bash (Plan 335).
- Generated (`_`-prefixed) files.

## Scope Coverage Note (sibling executor)

The source analysis (DR-4a) scopes M-4 to `HttpRequestExecutor`. However
`GraphqlQueryExecutor.java` shares the identical validator-to-transport gap
(`GraphqlQueryExecutor.validateUrl` pre-flight at `:31`/`:93`, then
`httpClient.fetchAsync` at `:116`). Whether the sibling executor is covered
depends entirely on the DR-4a enforcement-layer choice:

- If DR-4a selects a **transport/resolver-level** layer (e.g. a validating
  `IDnsResolver` + connection pinning wired via `HttpClientConfig`), both
  executors are covered automatically because they share `IHttpClient`.
- If DR-4a selects a **validator-local** fix inside `HttpRequestExecutor` only,
  `GraphqlQueryExecutor` remains vulnerable and MUST be tracked as a successor
  scope item.

Phase 1 MUST record which case applies before this plan can close, so the
closure audit can confirm no in-scope sibling gap is silently left open.

## Public-API Migration Consideration

A DNS-resolver injection point already exists in `nop-http-api`:
`HttpClientConfig.dnsResolver` (`HttpClientConfig.java:66`) holds a public
`IDnsResolver` (`IDnsResolver.java:38-49`) returning `InetAddress[]`. Plan 328's DR-4a
therefore records that the "public `IHttpClient` migration" answer is **most likely no** —
a validating+pinning resolver can be wired via `IDnsResolver` without new `IHttpClient`
methods. Phase 1 MUST confirm the chosen `IHttpClient` implementation actually consults
`IDnsResolver` on every connection (including after redirects) before declaring the
migration unnecessary; per-redirect-hop re-validation and socket-level pinning may still
need implementation-specific (not public-contract) changes. If the chosen enforcement layer
nonetheless requires a new `IHttpClient` method or a mandatory resolver argument, that is a
public-contract migration and needs its own user-approval sub-gate within DR-4a.

## Execution Plan

> All Phases are `blocked` until the User-Authorization Gate is satisfied.

### Phase 1 - Enforcement Layer And Resolver/Pinning Contract

Status: blocked (pending DR-4a user approval)
Targets: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/HttpRequestExecutor.java`,
`nop-network/nop-http/nop-http-api/.../client/IHttpClient.java`, `HttpClientConfig.java`

- Item Types: `Decision | Fix`

- [ ] Freeze the DR-4a decisions: enforcement layer, redirect ownership, multi-address /
  DNS-rebinding policy, encoded-address normalization, proxy policy, connection-address
  pinning, fail-closed behavior, and whether a public `IHttpClient` migration is required.
- [ ] Implement the chosen enforcement layer so the validator and the connection owner are
  the same component (or the transport is forced through a resolver+pinning layer).

Exit Criteria:

- [ ] DR-4a disposition recorded as `approved`; the public-API-migration sub-question is
      answered yes/no with rationale.
- [ ] **No silent no-op (Rule #24)**: unresolved / policy-ambiguous targets throw or return
      an explicit error and no transport call is made (asserted via the injectable
      `IHttpClient` seam).
- [ ] Sibling-executor coverage recorded per the Scope Coverage Note (transport-level
      covers `GraphqlQueryExecutor`, or validator-local leaves a tracked successor).
- [ ] Owner-doc update: `ai-dev/design/` records the DR-4a enforcement-layer decision
      and rationale; `docs-for-ai/` updated if AI-tool network-egress behavior is
      user-visible (or explicitly `No owner-doc update required` with reason).
- [ ] `ai-dev/logs/` entry for the execution day.

### Phase 2 - Redirect-Hop, DNS-Rebinding, And Encoded-Address Enforcement

Status: blocked (pending Phase 1)
Targets: the enforcement layer, `HttpRequestExecutor`

- Item Types: `Fix | Proof`

- [ ] Validate every redirect hop and the final connection before data is sent (per the
  DR-4a redirect-ownership decision).
- [ ] Pin the connection to a validated resolved address (no re-resolution between validate
  and connect); reject multi-A/AAAA sets containing an internal address.
- [ ] Normalize encoded IPv4/IPv6 (decimal/octal/hex, `[::ffff:...]`, IDN) to the same
  internal/external verdict as the literal form.

Exit Criteria:

- [ ] **端到端 (End-to-End, Rule #22)**: resolver-to-transport wiring proof — a request to
      a public hostname that 302-redirects to an internal/metadata address is blocked at
      the redirect hop (no data sent to the internal target), asserted end-to-end.
- [ ] No-request transport assertion: a denied target (internal IP, metadata host, encoded
      internal form) never reaches `IHttpClient.fetch` (verified with a fake client that
      records calls).
- [ ] DNS-rebinding / multi-address test: a hostname resolving to a mixed public+internal
      set is rejected per policy.
- [ ] Encoded-address tests: decimal/octal/hex IPv4 and IPv6-mapped forms match the literal
      verdict.
- [ ] `./mvnw test -pl nop-ai/nop-ai-toolkit -am -T 1C` green (and the relevant
      `nop-http-api` / client module if its contract changed).
- [ ] Owner-doc update: enforcement behavior documented in `ai-dev/design/` and
      `docs-for-ai/` where user-visible (or `No owner-doc update required` with reason).
- [ ] `ai-dev/logs/` entry for the execution day.

## Closure Gates

- [ ] M-4 resolved: the validator and the connection owner are the same component (or
      forced through a shared resolver); redirects, DNS rebinding, and multi-address sets
      cannot bypass validation.
- [ ] DR-4a enforcement-layer decision has a landed implementation matching the recorded
      disposition; the public-API migration sub-question is resolved.
- [ ] No-request transport assertions prove denied targets never reach `IHttpClient.fetch`.
- [ ] Encoded-address normalization verified for IPv4/IPv6 forms.
- [ ] `./mvnw clean install -pl nop-ai/nop-ai-toolkit,nop-network/nop-http/nop-http-api -am -T 1C -DskipTests` builds.
- [ ] `./mvnw test -pl <affected modules> -am -T 1C` green.
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exits 0.
- [ ] Independent closure audit recorded in `Closure`.

## Deferred But Adjudicated

### User Authorization Of DR-4 Decision Records

- Classification: `blocked (not a residual — a hard prerequisite)`
- Why Not Blocking Closure Of Plan 328: this is a successor plan; Plan 328 closed on
  having created this draft.
- Successor Required: this plan IS the successor; it activates only after user `approved`.

## Non-Blocking Follow-ups

- Egress allowlist / forward-proxy integration as a separate deployment concern.

## Closure

Status Note: Draft created by Plan 328 Phase 2. Blocked at draft pending user authorization
of DR-4a. No implementation has begun.
Completed: N/A

Closure Audit Evidence:

- Reviewer / Agent: N/A (draft, not yet completed)
- Evidence: N/A

Follow-up:

- User must record `approved`/`rejected`/`deferred` for DR-4a before this plan can be
  promoted to `active`.
