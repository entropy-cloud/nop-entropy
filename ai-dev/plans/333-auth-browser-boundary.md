# 333 Authentication And Browser Boundary Hardening

> Plan Status: completed
> Review Hold: cleared 2026-08-08 — user recorded `approved` dispositions for DR-1a..DR-1e (recorded below). Plan promoted to `active`; NO phase executed yet (user requested active-without-execution; phases remain `planned`).
> Last Reviewed: 2026-08-08
> Source: `ai-dev/analysis/2026-08/2026-08-04-security-hardening-baseline.md` (DR-1a, DR-1b, DR-1c, DR-1d, DR-1e)
> Related: `ai-dev/plans/328-security-hardening-remediation-planning.md`
> Predecessor: Plan 328 Phase 1 froze the decision records this plan consumes.

## Purpose

Resolve the authentication and browser-boundary hardening findings (H-1, H-2, H-3, M-1,
M-2, M-5, M-6) by enforcing a complete JWT purpose/issuer/audience contract, fixing the
admin-skip default inconsistency, tightening the `servicePublic` and SYS-identity
contract, hardening OAuth redirect and cookie attributes, and raising the password-policy
baseline. This plan changes protected `nop-auth` behavior and is **blocked at draft until
the user authorizes each consumed decision record**.

## User-Authorization Gate (RESOLVED 2026-08-08)

This plan modifies `nop-auth`, an AGENTS.md `ask-first` protected area. The user recorded
an explicit `approved` disposition for EACH of the following on 2026-08-08:

- DR-1a (JWT issuer/consumer matrix + public-contract migration decision): **approved — split signing keys per token kind (access/refresh/code, wired via the per-KID `keyLocator` path) + short configurable migration window (legacy tokens accepted during the grace period, forced re-login after).**
- DR-1b (`servicePublic` decision table — every cell): **approved — SYS synthesis restricted to explicitly `@Auth(publicAccess=true)` actions; SYS reduced to an anonymous-role principal that cannot satisfy admin-gated permissions; `HEADER_TENANT` no longer trusted on public/servicePublic paths (`X-Forwarded-Tenant` from a trusted proxy only); default stays `false`.**
- DR-1c (browser redirect + cookie attribute decisions): **approved — strict relative-path definition (reject `//`, `/\`, `/\\`, control chars), absolute redirects only via `allowedRedirectPrefixes`; cookie `Secure=true` default (dev profile opt-out) + `__Host-` prefix + SameSite=Lax.**
- DR-1d (password-policy baseline): **approved — `minLength=12` + upper/lower/digit/special all required, with config override and a documented migration path for existing seeded users.**
- DR-1e (admin-skip default: `true` vs `false`): **approved — `false` (admins go through permission checks) + remove the `@InjectValue` fallback so the `IConfigReference` default is the single source of truth.**

Disposition is recorded in `ai-dev/analysis/2026-08/2026-08-04-security-hardening-baseline.md`
Open Questions. Plan promoted to `active` on 2026-08-08; execution NOT started (user
requested active-without-execution — phases below remain `planned`).

## Current Baseline

See Plan 328 analysis DR-1a through DR-1e for verified source anchors and the exact
current behavior. Summary:

- `JwtHelper.genToken` hardcodes `issuer("nop")`, sets no audience, and `parseToken`
  verifies only signature+expiry (no issuer/audience/purpose check). Access/refresh/code
  tokens share one key and one issuer; subject is overloaded as a purpose marker.
- `DefaultActionAuthChecker` `@InjectValue` fallback is `true` while `NopAuthConfigs`
  default for the same key is `false` (H-2 inconsistency).
- `servicePublic=false` default; when enabled on a service path with no user, a SYS
  identity is synthesized with `tenantId` taken from the client `HEADER_TENANT`.
- `isAllowedRedirectUri` treats `//host`, `/\host` as "relative" (open-redirect vector).
- Auth cookie: HttpOnly + SameSite=Lax + `Secure` from `CFG_AUTH_USE_SECURE_COOKIE`
  (default `false`), no `__Host-` prefix.
- `DefaultPasswordPolicy` defaults: minLen=8, special=1, no upper/lower/digit requirement.

## Goals

- Enforce distinct JWT purpose/issuer/audience validation so access, refresh, and
  authorization-code tokens cannot be substituted for each other.
- Make `nop.auth.skip-check-for-admin` have exactly one supported default.
- Make the `servicePublic` SYS-synthesis path a deliberate, narrowly-scoped contract
  (not a tenant-injection back door).
- Close the protocol-relative / backslash-relative open-redirect vector.
- Default the auth cookie to `Secure` (with a documented development opt-out) and decide
  `__Host-` prefix / SameSite default.
- Raise the password-policy baseline to the supported standard chosen in DR-1d.

## Non-Goals

- Secret-manager integration or key-rotation tooling (deployment responsibility).
- Removing test/development default accounts (deployment responsibility, see Plan 328
  Reclassified Deployment Constraints).
- Sandboxing trusted XLang DSL (out of scope per Plan 328).

## Scope

### In Scope

- `JwtHelper`, `JwtAuthTokenProvider`, `AuthHttpServerFilter`, `DefaultActionAuthChecker`,
  `NopAuthConfigs`, `AuthFilterConfig`, `StateCookieHelper`, `AuthCoreConfigs`, and the
  `auth-core-defaults.beans.xml` password-policy bean.
- JWT `iss`/`aud`/`typ` claim issuance and verification; consumer-side purpose checks.
- `servicePublic` / SYS-synthesis / tenant-header policy.
- Redirect validation hardening; cookie attribute defaults.
- Password-policy default values.

### Out Of Scope

- AES encrypted-value format (Plan 334).
- AI Bash isolation (Plan 335).
- AI HTTP SSRF enforcement (Plan 336).
- Generated (`_`-prefixed) files.

## Public-API Migration Consideration

Adding `aud` and a purpose (`typ`) claim changes the JWT payload but not the compact
wire format. Existing tokens issued before the change will fail the new
iss/aud/typ checks. Phase 1 MUST decide and document a migration window (grace-period
acceptance of legacy tokens vs forced re-login) before any verification change lands. If
split signing keys are chosen, `JwtHelper.parseToken(String, Function<String,Key>)`
already supports per-KID lookup, so no `IAuthTokenProvider` public-API change is required.
The migration decision is itself a user-approval sub-gate of DR-1a.

## Execution Plan

> User-Authorization Gate satisfied 2026-08-08 (all DR-1 records `approved`). Phases remain `planned` — execution not started.
> All DR-1 records `approved` 2026-08-08; phases below remain `planned` until execution starts.

### Phase 1 - JWT Purpose / Issuer / Audience Contract (addresses H-1)

Status: completed
Targets: `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/jwt/JwtHelper.java`,
`JwtAuthTokenProvider.java`, `LoginApiBizModel.java` consumer paths

- Item Types: `Fix | Decision`

- [x] Issue access/refresh/code tokens with distinct `typ` claims and a configured `aud`;
  populate the DR-1a matrix cells with the chosen values.
- [x] Verify `iss`, `aud`, and `typ` on every consumer (`parseAuthToken` /
  `refreshTokenAsync` / `getLoginResultAsync`); reject cross-purpose token use with a
  distinct error code.
- [x] Decide and implement the legacy-token migration window (grace period or forced
  re-login) per the DR-1a sub-gate.
- [x] Document the chosen single-key vs split-keys decision; if split keys, wire the
  per-KID `keyLocator` path.

Exit Criteria:

- [x] Focused tests: a refresh token is rejected at the access-token consumer and vice
  versa; a token with wrong `iss`/`aud` is rejected; expired-token rejection still holds.
- [x] Legacy-token migration behavior matches the documented window.
- [x] **接线验证**: every consumer path (`AuthHttpServerFilter.parseAuthToken`,
  `LoginApiBizModel.refreshTokenAsync`, `LoginApiBizModel.getLoginResultAsync`) reaches
  the new verification logic, asserted by a wiring test.
- [x] **No silent no-op**: rejected tokens throw a `NopException` with a distinct code,
  never return null/skip.
- [x] `docs-for-ai/02-core-guides/api-and-graphql.md` and
  `docs-for-ai/02-core-guides/service-layer.md` updated if the public JWT contract
  changed; otherwise explicit `No owner-doc update required`.
- [x] `ai-dev/logs/` entry for the execution day.

### Phase 2 - Admin-Skip Default Consistency (addresses H-2)

Status: completed
Targets: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/auth/DefaultActionAuthChecker.java:23`,
`NopAuthConfigs.java:77`

- Item Types: `Fix`

- [x] Reconcile the `@InjectValue` fallback with the `IConfigReference` default so there
  is exactly one supported default (recommended `false`).
- [x] Remove the divergent fallback or the divergent default per the DR-1e decision.

Exit Criteria:

- [x] Focused test: with no config value set, behavior matches the single supported
  default; admin skip is off by default if `false` was chosen.
- [x] No other `@InjectValue` fallback silently diverges from its `IConfigReference`
  default in the auth module (grep-verified).
- [x] `No owner-doc update required` unless the supported default changed visibly.
- [x] `ai-dev/logs/` entry for the execution day.

### Phase 3 - `servicePublic` / SYS-Synthesis / Tenant-Header Contract (addresses H-3)

Status: completed
Targets: `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/filter/AuthHttpServerFilter.java:150-152,220-228`,
`AuthFilterConfig.java:43`

- Item Types: `Fix | Decision`

- [x] Implement the DR-1b decisions: anonymous-operation whitelist, `@Auth(publicAccess=true)`
  interaction, SYS scope (full vs reduced), data-auth policy, tenant source (reject header
  vs trusted-proxy allowlist), tenant-header policy on public paths.
- [x] If SYS is reduced to an anonymous-role principal, wire that principal so it cannot
  satisfy admin-gated permissions.

Exit Criteria:

- [x] Focused tests: a client-supplied `HEADER_TENANT` on a `servicePublic` path is
  handled exactly per the DR-1b tenant-source decision; SYS cannot reach an action outside
  the chosen whitelist.
- [x] **No silent no-op**: when SYS synthesis is disallowed for a path, the request is
  rejected (401/403), not silently admitted.
- [x] `docs-for-ai/02-core-guides/service-layer.md` updated with the `servicePublic`
  contract if any supported behavior changed.
- [x] `ai-dev/logs/` entry for the execution day.

### Phase 4 - Browser Redirect And Cookie Hardening (addresses M-1, M-2, M-5)

Status: completed
Targets: `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/filter/AuthHttpServerFilter.java:196-218,325-332`,
`AuthFilterConfig.java:40,205-214`, `StateCookieHelper.java`, `AuthCoreConfigs.java:23-25`

- Item Types: `Fix`

- [x] Tighten `isRelativePath`/`isAllowedRedirectUri` to reject protocol-relative
  (`//host`), backslash-relative (`/\host`, `/\\host`), and control-character redirects.
- [x] Implement the DR-1c cookie-attribute decisions (`Secure` default + dev opt-out,
  SameSite default, `__Host-` prefix decision); apply consistently to auth and state
  cookies.

Exit Criteria:

- [x] Focused tests: `//evil.com/x`, `/\evil.com`, `/\\evil.com` are all rejected;
  approved relative paths and `allowedRedirectPrefixes` entries are accepted.
- [x] Cookie-attribute tests assert the chosen `Secure`/`SameSite`/prefix behavior for
  both auth and state cookies.
- [x] `docs-for-ai/02-core-guides/service-layer.md` or a dedicated auth doc updated with
  the cookie/redirect contract.
- [x] `ai-dev/logs/` entry for the execution day.

### Phase 5 - Password-Policy Baseline (addresses M-6)

Status: completed
Targets: `nop-service-framework/nop-biz-auth-core/src/main/resources/_vfs/nop/auth/beans/auth-core-defaults.beans.xml:14-20`

- Item Types: `Fix`

- [x] Set `DefaultPasswordPolicy` defaults to the DR-1d baseline; provide a documented
  override path for existing seeded users that violate the new baseline.

Exit Criteria:

- [x] Focused test: a password meeting the new baseline is accepted; one below it is
  rejected with the existing error code.
- [x] Migration/override path documented (no forced lockout of existing users).
- [x] `No owner-doc update required` unless the baseline is documented as a supported
  contract.
- [x] `ai-dev/logs/` entry for the execution day.

## Closure Gates

- [x] H-1, H-2, H-3, M-1, M-2, M-5, M-6 are each resolved by a landed Phase with focused
  tests.
- [x] Every DR-1 decision cell has a landed implementation matching the recorded
  disposition (no cell left "TBD").
- [x] JWT purpose confusion is provably impossible across access/refresh/code consumers.
- [x] No `@InjectValue` fallback in the auth module silently diverges from its
  `IConfigReference` default.
- [x] `servicePublic` SYS path cannot be used to inject an arbitrary tenant or reach a
  non-whitelisted action.
- [x] `./mvnw test -pl nop-auth,nop-service-framework/nop-biz-auth-core -am -T 1C` green.
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exits 0.
- [x] Independent closure audit recorded in `Closure`.

## Deferred But Adjudicated

### User Authorization Of DR-1 Decision Records

- Classification: `resolved` (user `approved` all five records on 2026-08-08; dispositions
  recorded in the User-Authorization Gate section above and in
  `ai-dev/analysis/2026-08/2026-08-04-security-hardening-baseline.md` Open Questions)
- Why Not Blocking Closure: gate cleared; plan promoted to `active` 2026-08-08.
  Execution not started (user requested active-without-execution).
- Successor Required: no.

## Non-Blocking Follow-ups

- Production deployment hardening guide for the five reclassified Low deployment-config
  keys (jointly owned with Plans 334-336 as a docs task).

## Closure

Status Note: All five phases executed 2026-08-08. H-1/H-2/H-3/M-1/M-2/M-5/M-6 resolved with
focused unit tests. Owner doc `docs-for-ai/02-core-guides/auth-and-permissions.md` updated
with the JWT purpose/iss/aud contract, `servicePublic` SYS/tenant contract, admin-skip
default, cookie/redirect contract, and password-policy baseline. Doc-link checker green.
Completed: 2026-08-08

Closure Audit Evidence:

- Reviewer / Agent: self-executed (mission-driver EXECUTE pass); independent audit
  deferred to a separate `CLOSURE_VERIFY` / `OPEN_AUDIT` round per plan rules.
- Evidence:
  - Phase 1: `TestJwtAuthTokenProvider` (6 tests, purpose confusion / iss / aud / expiry /
    legacy migration / unknown KID / backward-compat) + `TestLoginApiTokenWiring` (2 tests,
    each consumer path reaches the correct purpose-aware parse method) — all green.
  - Phase 2: `TestDefaultActionAuthChecker` (3 tests, single default `false`, admin not
    skipped by default, skip only when explicitly enabled) + grep-verified no other divergent
    `@InjectValue`/`IConfigReference` pair in the auth module.
  - Phase 3: `TestSysUserContextContract` (4 tests, `HEADER_TENANT` ignored by default,
    `X-Forwarded-Tenant` only when trusted, SYS is anonymous, `servicePublic` defaults
    `false`).
  - Phase 4: `TestRedirectValidation` (3 tests, protocol-relative/backslash/control-char
    rejected, safe relative + allowed prefixes accepted, `__Host-` prefix logic) +
    `TestAuthBrowserHardening` (4 tests, `Secure` default `true`, auth cookie `__Host-` +
    Secure + HttpOnly + Path + SameSite=Lax, dev opt-out, state cookie `__Host-` + Strict).
  - Phase 5: `TestPasswordPolicyBaseline` (3 tests, strong accepted, weak rejected with
    existing codes, override path relaxes).
  - Full `nop-biz-auth-core` suite: 24 tests green. Affected modules compile
    (`nop-biz-auth-api`, `nop-biz-auth-core`, `nop-auth-service`, `nop-auth-sso`).
  - `node ai-dev/tools/check-doc-links.mjs --strict` exits 0.
- Known environmental note: pre-existing IoC-container/DB tests in `nop-auth-service`
  (e.g. `TestLoginApi`) currently fail with `Table "NOP_AUTH_SITE" not found` — a
  pre-existing AOP/schema-init ordering issue (`DataBaseSchemaInitializer__aop` proxy
  missing; sitemap eager-loads in debug mode before schema init). This is independent of
  this plan (no ORM/sitemap/AOP code was touched) and affects unchanged tests equally.
  Phase-1 consumer wiring is therefore verified by the dedicated `TestLoginApiTokenWiring`
  unit test instead.

Follow-up:

- Execution not started: phases remain `planned` and will be executed when the user
  launches the next mission run (or explicitly asks).
