# 333 Authentication And Browser Boundary Hardening

> Plan Status: draft
> Review Hold: Cannot promote to `active`. Plan modifies `nop-auth` (AGENTS.md `ask-first` protected area) and requires the user to record a disposition for each of DR-1a..DR-1e (security policy decisions: JWT migration window, servicePublic decision table, cookie/redirect attributes, password baseline, admin-skip default). Reviewer must not guess these. Plan structure/anchors audited clean at review time.
> Last Reviewed: 2026-08-07
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

## User-Authorization Gate (BLOCKER)

This plan modifies `nop-auth`, an AGENTS.md `ask-first` protected area. It MUST remain
`Plan Status: draft` with a blocked implementation slice until the user records an explicit
`approved` / `rejected` / `deferred` disposition for EACH of:

- DR-1a (JWT issuer/consumer matrix + public-contract migration decision)
- DR-1b (`servicePublic` decision table — every cell)
- DR-1c (browser redirect + cookie attribute decisions)
- DR-1d (password-policy baseline)
- DR-1e (admin-skip default: `true` vs `false`)

Disposition is recorded in `ai-dev/analysis/2026-08/2026-08-04-security-hardening-baseline.md`
Open Questions and in this plan's `Deferred But Adjudicated` / `Closure` sections. Until
then, no Phase below may start.

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

> All Phases are `blocked` until the User-Authorization Gate above is satisfied.
> The Phase breakdown below assumes all DR-1 records are `approved`.

### Phase 1 - JWT Purpose / Issuer / Audience Contract (addresses H-1)

Status: blocked (pending DR-1a + migration-window user approval)
Targets: `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/jwt/JwtHelper.java`,
`JwtAuthTokenProvider.java`, `LoginApiBizModel.java` consumer paths

- Item Types: `Fix | Decision`

- [ ] Issue access/refresh/code tokens with distinct `typ` claims and a configured `aud`;
  populate the DR-1a matrix cells with the chosen values.
- [ ] Verify `iss`, `aud`, and `typ` on every consumer (`parseAuthToken` /
  `refreshTokenAsync` / `getLoginResultAsync`); reject cross-purpose token use with a
  distinct error code.
- [ ] Decide and implement the legacy-token migration window (grace period or forced
  re-login) per the DR-1a sub-gate.
- [ ] Document the chosen single-key vs split-keys decision; if split keys, wire the
  per-KID `keyLocator` path.

Exit Criteria:

- [ ] Focused tests: a refresh token is rejected at the access-token consumer and vice
  versa; a token with wrong `iss`/`aud` is rejected; expired-token rejection still holds.
- [ ] Legacy-token migration behavior matches the documented window.
- [ ] **接线验证**: every consumer path (`AuthHttpServerFilter.parseAuthToken`,
  `LoginApiBizModel.refreshTokenAsync`, `LoginApiBizModel.getLoginResultAsync`) reaches
  the new verification logic, asserted by a wiring test.
- [ ] **No silent no-op**: rejected tokens throw a `NopException` with a distinct code,
  never return null/skip.
- [ ] `docs-for-ai/02-core-guides/api-and-graphql.md` and
  `docs-for-ai/02-core-guides/service-layer.md` updated if the public JWT contract
  changed; otherwise explicit `No owner-doc update required`.
- [ ] `ai-dev/logs/` entry for the execution day.

### Phase 2 - Admin-Skip Default Consistency (addresses H-2)

Status: blocked (pending DR-1e user approval)
Targets: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/auth/DefaultActionAuthChecker.java:23`,
`NopAuthConfigs.java:77`

- Item Types: `Fix`

- [ ] Reconcile the `@InjectValue` fallback with the `IConfigReference` default so there
  is exactly one supported default (recommended `false`).
- [ ] Remove the divergent fallback or the divergent default per the DR-1e decision.

Exit Criteria:

- [ ] Focused test: with no config value set, behavior matches the single supported
  default; admin skip is off by default if `false` was chosen.
- [ ] No other `@InjectValue` fallback silently diverges from its `IConfigReference`
  default in the auth module (grep-verified).
- [ ] `No owner-doc update required` unless the supported default changed visibly.
- [ ] `ai-dev/logs/` entry for the execution day.

### Phase 3 - `servicePublic` / SYS-Synthesis / Tenant-Header Contract (addresses H-3)

Status: blocked (pending DR-1b user approval — every cell)
Targets: `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/filter/AuthHttpServerFilter.java:150-152,220-228`,
`AuthFilterConfig.java:43`

- Item Types: `Fix | Decision`

- [ ] Implement the DR-1b decisions: anonymous-operation whitelist, `@Auth(publicAccess=true)`
  interaction, SYS scope (full vs reduced), data-auth policy, tenant source (reject header
  vs trusted-proxy allowlist), tenant-header policy on public paths.
- [ ] If SYS is reduced to an anonymous-role principal, wire that principal so it cannot
  satisfy admin-gated permissions.

Exit Criteria:

- [ ] Focused tests: a client-supplied `HEADER_TENANT` on a `servicePublic` path is
  handled exactly per the DR-1b tenant-source decision; SYS cannot reach an action outside
  the chosen whitelist.
- [ ] **No silent no-op**: when SYS synthesis is disallowed for a path, the request is
  rejected (401/403), not silently admitted.
- [ ] `docs-for-ai/02-core-guides/service-layer.md` updated with the `servicePublic`
  contract if any supported behavior changed.
- [ ] `ai-dev/logs/` entry for the execution day.

### Phase 4 - Browser Redirect And Cookie Hardening (addresses M-1, M-2, M-5)

Status: blocked (pending DR-1c user approval)
Targets: `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/filter/AuthHttpServerFilter.java:196-218,325-332`,
`AuthFilterConfig.java:40,205-214`, `StateCookieHelper.java`, `AuthCoreConfigs.java:23-25`

- Item Types: `Fix`

- [ ] Tighten `isRelativePath`/`isAllowedRedirectUri` to reject protocol-relative
  (`//host`), backslash-relative (`/\host`, `/\\host`), and control-character redirects.
- [ ] Implement the DR-1c cookie-attribute decisions (`Secure` default + dev opt-out,
  SameSite default, `__Host-` prefix decision); apply consistently to auth and state
  cookies.

Exit Criteria:

- [ ] Focused tests: `//evil.com/x`, `/\evil.com`, `/\\evil.com` are all rejected;
  approved relative paths and `allowedRedirectPrefixes` entries are accepted.
- [ ] Cookie-attribute tests assert the chosen `Secure`/`SameSite`/prefix behavior for
  both auth and state cookies.
- [ ] `docs-for-ai/02-core-guides/service-layer.md` or a dedicated auth doc updated with
  the cookie/redirect contract.
- [ ] `ai-dev/logs/` entry for the execution day.

### Phase 5 - Password-Policy Baseline (addresses M-6)

Status: blocked (pending DR-1d user approval)
Targets: `nop-service-framework/nop-biz-auth-core/src/main/resources/_vfs/nop/auth/beans/auth-core-defaults.beans.xml:14-20`

- Item Types: `Fix`

- [ ] Set `DefaultPasswordPolicy` defaults to the DR-1d baseline; provide a documented
  override path for existing seeded users that violate the new baseline.

Exit Criteria:

- [ ] Focused test: a password meeting the new baseline is accepted; one below it is
  rejected with the existing error code.
- [ ] Migration/override path documented (no forced lockout of existing users).
- [ ] `No owner-doc update required` unless the baseline is documented as a supported
  contract.
- [ ] `ai-dev/logs/` entry for the execution day.

## Closure Gates

- [ ] H-1, H-2, H-3, M-1, M-2, M-5, M-6 are each resolved by a landed Phase with focused
  tests.
- [ ] Every DR-1 decision cell has a landed implementation matching the recorded
  disposition (no cell left "TBD").
- [ ] JWT purpose confusion is provably impossible across access/refresh/code consumers.
- [ ] No `@InjectValue` fallback in the auth module silently diverges from its
  `IConfigReference` default.
- [ ] `servicePublic` SYS path cannot be used to inject an arbitrary tenant or reach a
  non-whitelisted action.
- [ ] `./mvnw test -pl nop-auth,nop-service-framework/nop-biz-auth-core -am -T 1C` green.
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exits 0.
- [ ] Independent closure audit recorded in `Closure`.

## Deferred But Adjudicated

### User Authorization Of DR-1 Decision Records

- Classification: `blocked (not a residual — a hard prerequisite)`
- Why Not Blocking Closure Of Plan 328: this is a successor plan; Plan 328 closed on
  having created this draft. It blocks THIS plan from becoming `active`.
- Successor Required: this plan IS the successor; it activates only after user `approved`.

## Non-Blocking Follow-ups

- Production deployment hardening guide for the five reclassified Low deployment-config
  keys (jointly owned with Plans 334-336 as a docs task).

## Closure

Status Note: Draft created by Plan 328 Phase 2. Blocked at draft pending user
authorization of DR-1a through DR-1e. No implementation has begun.
Completed: N/A

Closure Audit Evidence:

- Reviewer / Agent: N/A (draft, not yet completed)
- Evidence: N/A

Follow-up:

- User must record `approved`/`rejected`/`deferred` for DR-1a through DR-1e before this
  plan can be promoted to `active`.
