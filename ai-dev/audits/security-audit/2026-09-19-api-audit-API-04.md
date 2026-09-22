# API-04 — Distributed Proxy `/px/` Audit

> Mission: security-audit (roadmap item 4, deliverable API-04)
> Date: 2026-09-19. Auditor: ZCode session (plan 2026-09-18-2340-6, Phase 2).
> Severity labels: CRITICAL/HIGH/MEDIUM/LOW + P0-P3.
> Scope: `/px/{serviceName}/{opName}` core logic `GraphQLWebService.runProxy`
> (nop-graphql-core), boundary reads into the HTTP starters mounting the endpoint
> (`SpringGraphQLWebService`, `QuarkusGraphQLWebService`) for token forwarding /
> header sanitization; `nop-gateway` audited separately as a message-routing
> gateway (NOT the /px/ owner).
> Method: full read of runProxy / buildRequest / getHeaders in all three classes +
  ContextBinder / ClusterRpcServiceInvoker / HttpRpcService / DefaultRpcUrlBuilder /
  IRpcUrlBuilder; bean-definition grep repo-wide; owner-doc contract cross-check
  (docs-for-ai/02-core-guides/rpc-and-distributed-rpc.md).

## Verified controls (anchor-verified)

1. **Endpoint inert unless the application wires it**: `runProxy` resolves bean
   `nopProxyRpcServiceInvoker` (nop-service-framework/nop-graphql/nop-graphql-core/
   src/main/java/io/nop/graphql/core/web/GraphQLWebService.java:181-182; name
   constant GraphQLConstants.java:171). Repo-wide grep: the bean is defined NOWHERE
   in platform modules — only documented as application-provided
   (docs-for-ai/02-core-guides/rpc-and-distributed-rpc.md:76,99-117). Without it the
   container lookup fails — no accidental open proxy.
2. **serviceName whitelist fails closed**: `ClusterRpcServiceInvoker.isAllowedService`
   returns false for a null/absent `allowedServiceNames` set and rejects with
   `ERR_RPC_NOT_ALLOWED_SERVICE_NAME` before any forwarding (nop-cluster/
   nop-rpc-cluster/src/main/java/io/nop/rpc/cluster/ClusterRpcServiceInvoker.java:
   90-95, 100-102). Matches the owner doc "默认值为空（不允许任何服务）"
   (rpc-and-distributed-rpc.md:131).
3. **No local action-auth bypass illusion**: `/px/` performs no GraphQL
   action-auth locally — it is a transport proxy; the target service re-enters the
   standard `/r/` path (`GraphQLWebService.runRest` → `initRpcContext` →
   `executeRpcAsync` → `GraphQLExecutor.executeOneAsync` → checker, per API-02
   control 1). Token forwarding (below) is what makes target-side auth possible.
4. **Authentication token forwarding by design**: inbound headers are captured into
   `ApiRequest.headers` (`SpringGraphQLWebService.getHeaders`
   nop-spring/nop-spring-web-starter/src/main/java/io/nop/spring/web/service/
   SpringGraphQLWebService.java:74-85 → `SpringMvcHelper.getHeaders`
   SpringMvcHelper.java:24-34; Quarkus equivalent QuarkusGraphQLWebService.java:
   117-127), carried by `buildRequest` (GraphQLWebService.java:336-362) and re-emitted
   as HTTP headers on the outbound call (`HttpRpcService.toHttpRequest`
   nop-network/nop-rpc/nop-rpc-http/src/main/java/io/nop/rpc/http/HttpRpcService.java:
   53-79). Documented contract (rpc-and-distributed-rpc.md:185-228).
5. **Context propagation is whitelisted**: `ContextBinder.initContext`
   (nop-network/nop-rpc/nop-rpc-api/src/main/java/io/nop/rpc/api/ContextBinder.java:
   53-68) extracts only locale/timezone/tenant/timeout plus
   `nop.rpc.propagate-headers` (default `nop-svc-route,nop-tags,nop-client-addr`,
   nop-kernel/nop-api-core/src/main/java/io/nop/api/core/ApiConfigs.java:81-84) into
   `IContext`.
6. **Response-side consistency**: proxy responses/errors flow through
   `buildRpcResponse` (GraphQLWebService.java:212-228) — same error-masking posture
   as API-01 (F-API1-1 applies to this endpoint too); cancel-token + logging wired.
7. **nop-gateway is not a `/px/` owner**: zero `/px` references in
   nop-service-framework/nop-gateway sources (grep verified). It is a model-driven
   message-routing gateway (`GatewayHandler` nop-service-framework/nop-gateway/src/
   main/java/io/nop/gateway/impl/GatewayHandler.java:56-282; routes/interceptors from
   `app.gateway.xml`, `GatewayConfigs.CFG_GATEWAY_MODEL_PATH`). Framework-side
   posture verified: interceptor rejections (401/429/Retry-After) propagate verbatim
   and are not rewritten into generic errors (L132-141); upstream `Retry-After`
   abuse is capped (`InvokeProcessor.MAX_RETRY_DELAY_MS` 30s); empty catches in the
   retry parser are bounded fallbacks (PMD P3, dispositioned in Phase 1 QA log).
   Route/interceptor policy content is application model data (consumer scope).

## Findings

| ID | Severity | Anchor | Description | Remediation suggestion |
|---|---|---|---|---|
| F-API4-1 | MEDIUM (P2) | SpringMvcHelper.java:17-34 (drop-list of 4); HttpRpcService.java:53-79 (forwards all `ApiRequest` headers); IRpcUrlBuilder.java:16-20 (only `__`-prefixed temp headers dropped); rpc-and-distributed-rpc.md:303-315 (`nop-svc-target-host` instance steering) | **Outbound header sanitization gap on `/px/`**: the wire forwarding is a full pass-through of every inbound header — only `connection/accept/accept-encoding/content-length` (starters) and `__`-temp prefix (url builder) are dropped. Consequences once an application enables `/px/` (bean + service whitelist): (a) client-supplied `nop-svc-target-host` is forwarded unfiltered and steers which cluster instance receives the call (SpecificServiceInstanceFilter contract); (b) `cookie`, `host`, and arbitrary internal headers cross the trust boundary to the target service; (c) operators reading `nop.rpc.propagate-headers` may believe it constrains forwarding — it only populates `IContext`, never the wire headers (doc L228). Auth token forwarding itself is by-design (control 4) and not part of this finding. Fix handoff: introduce an outbound allowlist (or extended drop-list covering `nop-svc-*` internal routing headers) in the `/px/` invoker path. | Fix-phase candidate: sanitize in `runProxy` (or `ClusterRpcServiceInvoker`) — forward auth headers + whitelisted propagate headers, drop internal routing/control headers; document the outbound header policy in rpc-and-distributed-rpc.md. |

## Explicit no-finding statements

- No platform module pre-defines `nopProxyRpcServiceInvoker` — `/px/` cannot become
  active through platform defaults alone (control 1).
- No serviceName bypass: absent/empty whitelist rejects before forwarding
  (control 2); `*` must be configured explicitly by the application.
- No local GraphQL execution happens on the proxy path (context is created only for
  cancel-token/logging/response building; the operation executes on the target).
- No unbounded retry/backoff: retry count is bean-configured and Retry-After is
  capped at 30s (control 7).
- No double ownership: JWT/token validation at the target is AUTH-01; tenant-header
  trust semantics are item 2 (AUTH-05 noted "no tenant header trust" for SYS
  principal — link-only); HTTP client transport security (`use-https`) is item 6/8
  network scope.

## Adjudication (Phase 3 input)

- F-API4-1: `remediation-target` (MEDIUM/P2) — outbound header policy for the proxy
  path; requires an application that actually enables `/px/` to be exploitable
  (platform default is inert).

## Owner mapping

- F-API4-1 successor owner: fix-phase triage (nop-graphql-core `GraphQLWebService.
  runProxy` or nop-rpc-cluster invoker; doc update in `docs-for-ai/02-core-guides/
  rpc-and-distributed-rpc.md`).
- Coverage note: no tests exist for `runProxy` or for
  `ClusterRpcServiceInvoker.allowedServiceNames` (grep: zero hits repo-wide) —
  both belong in the fix-phase test batch if `/px/` hardening is implemented.
