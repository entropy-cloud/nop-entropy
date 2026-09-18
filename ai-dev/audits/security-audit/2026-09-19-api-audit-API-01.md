# API-01 — GraphQL Endpoint Guards Audit

> Mission: security-audit (roadmap item 4, deliverable API-01)
> Date: 2026-09-19. Auditor: ZCode session (plan 2026-09-18-2340-6, Phase 2).
> Severity labels: CRITICAL/HIGH/MEDIUM/LOW + P0-P3 (P0=CRITICAL … P3=LOW).
> Scope: GraphQL engine request guards — query depth, batch/operation count,
> introspection exposure, request-size/directive caps, page-size caps, error-message
> leakage. Modules: `nop-graphql-core` engine (+ `nop-biz` page clamp, `nop-graphql-orm`
> connection clamp as consumers).
> Method: full read of GraphQLEngine / GraphQLSelectionResolver / GraphQLConfigs /
> response-build paths; ErrorMessageManager masking semantics; anchors verified live;
> cross-check vs CORE/AUTH reports (no overlap found on these controls).

## Verified controls (anchor-verified)

1. **Query depth limit — enforced by default (7)**: every parsed document is resolved
   through `GraphQLSelectionResolver` with `maxDepth = CFG_GRAPHQL_QUERY_MAX_DEPTH`
   (`GraphQLEngine.initDocument`, nop-service-framework/nop-graphql/nop-graphql-core/
   src/main/java/io/nop/graphql/core/engine/GraphQLEngine.java:254-265); level violation
   throws `ERR_GRAPHQL_QUERY_EXCEED_MAX_DEPTH` (GraphQLSelectionResolver.java:209-213).
   Config `nop.graphql.query.max-depth` default 7 (GraphQLConfigs.java:35).
2. **Fragment-chain DoS bounded**: fragment reference nesting limited to maxDepth and
   cycles rejected (`GraphQLSelectionResolver.checkSelectionSetFragments`,
   GraphQLSelectionResolver.java:126-165; cycle throw L144-147, depth throw L149-153).
   Regression-tested (`TestGraphQLFragmentDepth`: chain-exceeds, within-depth,
   introspection-shape pass, cycle rejected).
3. **Introspection exposure — disabled by default**: builtin schema loaded without
   introspection types (`GraphQLEngine.init` L233-235 consumes
   `CFG_GRAPHQL_SCHEMA_INTROSPECTION_ENABLED`, default false, GraphQLConfigs.java:52-53);
   `__`-prefixed operation names rejected with `ERR_GRAPHQL_INTROSPECTION_NOT_ENABLED`
   (`getOperationDefinition` L348-354). The depth exception for the canonical
   `IntrospectionQuery` shape (maxDepth→10, L257-261) only matters when introspection
   is explicitly enabled.
4. **Request size / operation count / directive caps — enforced by default**: parse
   length 4096 (`parseOperation` L298-306, `ERR_GRAPHQL_PARSE_EXCEED_MAX_LENGTH`);
   top-level selection (batch) count 10 (`initGraphQLContext` L394-396,
   `ERR_GRAPHQL_QUERY_EXCEED_MAX_OPERATION_COUNT`); directive count 20 on the document
   path (`validateDocument` L410-417). Defaults at GraphQLConfigs.java:31-38,72-73.
5. **Page-size caps — enforced, configurable upward per object**: global
   `nop.graphql.max-page-size`=1000 clamped in `CrudBizModel.getMaxPageSize` /
   query preparation (nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/
   CrudBizModel.java:417-427, L384+; findList clamp L1551) and in connection fetchers
   (nop-graphql-orm .../OrmEntityPropConnectionFetcher.java:90-105,180). Note:
   per-object `EXT_MAX_PAGE_SIZE` can only **raise** the cap (documented platform
   semantics).
6. **Guard ordering**: executor runs `GraphQLActionAuthChecker` → operation-MFA →
   argument validation **before** any fetcher (GraphQLExecutor.java:72-74, L155-157;
   subscription paths at GraphQLEngine.java:683-684, L746-747). Auth-side MFA semantics
   owned by AUTH-03 (link-only here).

## Guard classification (per plan: enforced / configurable / absent)

| Guard | Classification |
|-------|----------------|
| Query max depth (7) | enforced-by-default, configurable |
| Fragment chain depth + cycle rejection | enforced-by-default |
| Batch (top-level selection) count (10) | enforced-by-default, configurable |
| Parse max length (4096) | enforced-by-default, configurable |
| Directive count (20) | enforced-by-default on document path; RPC path applies a different cap (see F-API1-2) |
| Introspection | enforced-off by default, configurable |
| Page size (1000) | enforced-by-default, configurable (per-object raise only) |
| Error masking | configurable; **default leaks unmapped errors** (F-API1-1) |
| Query complexity cost analysis | absent (no cost-based limiting; depth/count/size caps only) — recorded as platform posture, not a defect |

## Findings

| ID | Severity | Anchor | Description | Remediation suggestion |
|---|---|---|---|---|
| F-API1-1 | MEDIUM (P2) | nop-kernel/nop-core/src/main/java/io/nop/core/exceptions/ErrorMessageManager.java:320-343 (applyMapping), CoreConfigs.java:225-227, GraphQLEngine.java:610-614 & 636-641 | API-boundary error responses return unmapped error codes verbatim: `nop.core.error-message-public-for-no-mapping` defaults **true**, so any error code without an external mapping (incl. raw non-`IException` failures, where errorCode = exception class name and description = `e.getMessage()`, `defaultBuildErrorMessage`) reaches GraphQL/REST clients. Stack traces are excluded (`includeStack=false`), but internal class names / messages can leak implementation detail. Fix handoff: deployment checklist entry (set `nop.core.error-message-public-for-no-mapping=false` in production to mask unmapped errors as SysError); optionally flip the default in a hardening plan. | Set `nop.core.error-message-public-for-no-mapping=false` for internet-facing deployments (item 9 checklist); framework change (default flip) is a candidate fix-phase item with i18n regression on error messages. |
| F-API1-2 | LOW (P3) | GraphQLEngine.java:488-496 (`validateSelection`) | RPC/`/r/` path caps directive count using `CFG_GRAPHQL_QUERY_MAX_OPERATION_COUNT` (10) instead of `CFG_GRAPHQL_MAX_DIRECTIVE_PER_REQUEST` (20) — the `nop.graphql.max-directive-per-request` knob does not govern the RPC path. Direction is stricter (fail-safe); recorded as config-semantics inconsistency, not a bypass. | Fix-phase candidate: align `validateSelection` with `CFG_GRAPHQL_MAX_DIRECTIVE_PER_REQUEST` or document the divergence; add the missing unit test either way. |

## Explicit no-finding statements

- No reachable path executes a document without the depth/operation-count/directive
  caps: `/graphql` (parse → initGraphQLContext), `/r/`,`/p/`,`/jsonrpc` (initRpcContext
  → resolveSelections with maxDepth), subscriptions (subscribeGraphQL/subscribeRpc
  operate on already-resolved documents).
- Introspection cannot be enabled implicitly: only the config flag loads builtin
  introspection types; `__` fields error otherwise.
- No stack trace is serialized into API error responses on either GraphQL or RPC
  response paths (includeStack=false at both call sites).
- Parse cache is bounded (2000 entries) — no unbounded memory growth from distinct
  query strings within the 4096-char cap.

## Adjudication (Phase 3 input)

- F-API1-1: `remediation-target` (MEDIUM/P2) — config-default hardening; deployment
  checklist for item 9 + candidate default-flip in fix phase.
- F-API1-2: `remediation-target` (LOW/P3) — config-semantics alignment.

## Owner mapping

- Error-masking mechanism lives in nop-core but the API-boundary consumption and
  default posture are owned here (no CORE/AUTH report covered it — verified by grep
  of items 1-3 reports).
- Page-clamp consumer code in nop-biz/nop-graphql-orm owned here; ORM query
  internals remain item 1 scope (link-only).
- Test coverage gaps (introspection-off assertion; operation-count/parse-length/
  directive-limit unit tests) recorded in the plan Phase 2 evidence contract; main
  session runs the focused suites.
