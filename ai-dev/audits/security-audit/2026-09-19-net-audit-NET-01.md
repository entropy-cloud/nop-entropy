# NET-01 — HTTP Client Security Controls Audit (TLS / redirects / timeouts / SSRF boundary)

> Mission: security-audit (roadmap item 7, deliverable NET-01)
> Date: 2026-09-19. Auditor: ZCode session (plan 2026-09-18-2343-9, Phase 2).
> Severity labels: CRITICAL/HIGH/MEDIUM/LOW and P0/P1/P2/P3 (P0=CRITICAL … P3=LOW).
> Scope: `nop-network/nop-http/` — shared `HttpClientConfig`, JDK / Apache / OkHttp client
> variants, OAuth enhancer, `CompositeX509TrustManager`, default wiring
> (`http-api.beans.xml`, `http-client-jdk.beans.xml`); plan-336 closure re-verification +
> uncovered-surface classification.
> Method: full read of HttpClientConfig, ApacheHttpClient(+Helper), JdkHttpClient,
> OkHttpClientProvider, AddAccessTokenHttpClientEnhancer + auth config beans; wiring
> trace via `_dump` merged beans; hc5 5.4.2 redirect/TLS defaults verified from
> bytecode (`_tmp/security-audit/hc5/`); JDK redirect header filtering verified from
> JDK src (`_tmp/security-audit/jdksrc/`); config-default inventory in
> `_tmp/security-audit/net-inventory.md`.

## Verified controls

1. **Default-secure platform defaults** (`HttpClientConfig.java`): `followRedirects=false`,
   `retryOnConnectionFailure=false`, `ignoreSslCerts=false`, `useSsl=false`,
   `connectTimeout=10s`, `readTimeout=30s`, `writeTimeout=30s`, `sslVersion=TLSv1.2`,
   `maxConnTotal=2000`. The DEFAULT transport is `JdkHttpClient`
   (`http-client-jdk.beans.xml` `nopRawHttpClient` ioc:default) with
   `followRedirects → Redirect.NEVER` (JdkHttpClient.java:101) — by default the platform
   does NOT follow redirects at all, disables automatic retries, and uses the JDK
   system trust store with full validation.
2. **JDK cross-origin redirect credential protection is real** (default variant):
   JDK `HttpRequestImpl.newInstanceForRedirection` applies
   `Utils.ALLOWED_REDIRECT_HEADERS` when scheme/authority differ — `Authorization`
   is NOT in the allowlist, so even with `follow-redirects=true` the JDK variant
   strips credentials on cross-origin redirects (verified from zulu-26 src, captured
   in `_tmp/security-audit/jdksrc/`).
3. **Trust-manager composition never drops the JDK default trust store**: both Apache
   (`ApacheHttpClientHelper.createTlsStrategy` L148-150) and JDK
   (`JdkHttpClient.newSSLContext` L131-133) add JDK-default trust managers to the
   composite in addition to any injected ones — custom trust is additive (union), not
   a replacement that could silently narrow or bypass CA validation on its own.
4. **Secret-header masking in debug logs** on all three variants
   (ApacheHttpClient.logRequest L399-416, JdkHttpClient L241-256): `Authorization`/
   cookie-family headers masked `******` unless `CFG_HTTP_LOG_PRINT_ALL_HEADERS` —
   tokens are not echoed to logs at debug level. OAuth client secret is only placed
   in the token-endpoint POST body via config getter (no logging).
5. **Download integrity** (ApacheHttpClient L300-383): checksum expectation
   (explicit > response header > sidecar `{url}.sha256/.sha1`), fail on
   `ERR_HTTP_DOWNLOAD_NO_CHECKSUM` when required, verify-then-atomic-rename from
   `.part`, delete-on-mismatch, 416 whole-retry — same control family as CORE-02
   plugin downloads.
6. **Plan-336 SSRF closures re-verified live**: both AI executors still default-wire
   the guards (`HttpRequestExecutor.java:48` and `GraphqlQueryExecutor.java:35`:
   `private IDnsResolver dnsResolver = new SsrfGuardDnsResolver();`), pre-flight
   `SsrfAddressGuard` on both; `HttpClientConfig.dnsResolver` seam wired into Apache
   connection manager (`ApacheHttpClientHelper.createConnectionManager` L112-126) and
   consulted on every new connection incl. after redirects. 336's client-support
   matrix (`ai-dev/design/nop-ai/ssrf-egress-enforcement-design.md` L43-50) honestly
   records JDK/OkHttp = pre-flight only. Closure verdict: **holds**; residuals
   classified under F-N1-4.
7. **Timeout enforcement on the default path**: JDK builder applies
   `connectTimeout`; per-request `timeout` else `readTimeout` as request deadline
   (JdkHttpClient L221-225) — a hung server cannot hold a caller indefinitely under
   default config. Apache applies connect/response timeouts via RequestConfig
   (L85-100) and SO timeout via reactor config.

## Findings

| ID | Severity | Anchor | Description | Remediation suggestion |
|---|---|---|---|---|
| F-N1-1 | MEDIUM (P2) | nop-network/nop-http/nop-http-client-apache/src/main/java/io/nop/http/apache/ApacheHttpClientHelper.java:54-83,136-185 (no `followRedirects` consumption, no `disableRedirectHandling()`); bytecode evidence hc5 5.4.2 `AsyncRedirectExec` + `BasicRequestBuilder.copy` in `_tmp/security-audit/hc5/` | **Apache variant follows redirects unconditionally and forwards ALL request headers (incl. `Authorization`, cookies) to the redirect target.** (a) `HttpClientConfig.followRedirects` is never consulted in this module — the config knob is a silent no-op on this variant, while hc5 enables `AsyncRedirectExec` with `DefaultRedirectStrategy` unless `disableRedirectHandling()` is called (bytecode: `redirectHandlingDisabled` default false, exec added unconditionally). (b) hc5 redirect reconstruction uses `BasicRequestBuilder.copy(request)` and performs NO Authorization stripping — only `AuthExchange.reset()` (credentials-provider state), so a raw `Authorization` header set by platform code (OAuth enhancer, request headers) leaks cross-origin on 3xx. This is the SAME variant plan 336 directs operators to pair with `SsrfGuardDnsResolver` for AI egress, so the recommended pairing silently turns on credential-forwarding redirect behavior. Not the default client (default=JDK, redirects NEVER), no in-repo consumer of the Apache variant today. | Honor `followRedirects` (call `disableRedirectHandling()` when false); on redirect hops strip `Authorization`/`Cookie` when target host differs (custom RedirectStrategy); add a client-variant redirect test. |
| F-N1-2 | MEDIUM (P2) | nop-network/nop-http/nop-http-client-okhttp/src/main/java/io/nop/http/client/okhttp/OkHttpClientProvider.java:150-168 (NOSONAR L177-201) | **OkHttp variant: `useSsl=true` without an explicitly injected trust manager silently selects trust-ALL defaults.** `addSSLConfig` falls back to `DisableValidationTrustManager` (accepts any cert) + `TrustAllHostnames` (NOSONAR-suppressed, no logging). Semantics are inverted vs the other variants: on JDK/Apache `useSsl=true` merely activates a custom SSL context that still includes the JDK default trust store; on OkHttp the same flag REMOVES all certificate and hostname validation unless the integrator additionally injects a trustManager bean. An operator who enables `nop.http.client.use-ssl` expecting stricter TLS on this variant gets none, silently. Variant is not default-wired and has no in-repo consumers (grep verified) — integrator-facing trap. | Default the fallback to the system trust manager (`TrustManagerFactory.getDefaultAlgorithm()` init null, mirroring JdkHttpClient.newSSLContext) instead of DisableValidation; or hard-fail when useSsl=true and no trust manager wired; log a WARN whenever the trust-all path activates. |
| F-N1-3 | MEDIUM (P2) | nop-network/nop-http/nop-http-api/src/main/java/io/nop/http/api/client/HttpClientConfig.java:51,298-304 + support/CompositeX509TrustManager.java:57-60 + ApacheHttpClientHelper.java:169-176; config-bound via `ioc:config` prefix `nop.http.client` (http-api.beans.xml:20-21; demo dump confirms `@cfg:nop.http.client.use-ssl`) | **TLS verification IS silently disableable via configuration, with zero observability.** Roadmap question answered: `nop.http.client.ignore-ssl-certs=true` config disables BOTH certificate-chain validation (`CompositeX509TrustManager.checkServerTrusted` returns immediately, L58-60) and hostname verification (`NoopHostnameVerifier`, ApacheHttpClientHelper L170-171) with NO warn log, no startup marker, no metrics — on the JDK variant when combined with `use-ssl=true`, on the Apache variant unconditionally. It is an explicit operator opt-in (default false, not attacker-reachable), but the disable is completely silent, contradicting the platform's no-silent-degradation convention (skip-cache-verify in CORE-02 logs by comparison). | Emit a prominent WARN at client start when ignoreSslCerts/useSsl-trust-all is active (include bean id + config key); add to item-9 deployment hardening checklist; optional: dedicated metrics counter. |
| F-N1-4 | LOW (P3) — documented residual, watch-only | nop-network/nop-http/nop-http-client-jdk/.../JdkHttpClient.java (grep: no `getDnsResolver` consumption) + okhttp provider (no `builder.dns(...)`); HttpClientConfig.java:62-64 (`httpProxy/httpsProxy/noProxy` dead fields) | **Transport-level SSRF/redirect coverage gaps consolidated.** (a) The DEFAULT JDK client and OkHttp never consult `HttpClientConfig.dnsResolver` — the plan-336 resolver seam is dead config on them (pre-flight only), as honestly recorded in the 336 design matrix; setting the resolver with the default client silently does nothing. (b) Proxy config fields exist on the shared config but no variant consumes them (dead config). Both are silent-no-op config surfaces. | Item-9 deployment hardening: pair AI HTTP tools with the Apache client for resolver coverage (and fix F-N1-1 first); either implement or remove the proxy fields; javadoc `dnsResolver` as Apache-only. |

## Explicit no-finding statements

- No hardcoded credentials/secrets in nop-http main sources (sweep §6 of inventory).
- The default platform posture (JDK client, all default config) performs full TLS
  validation against the system trust store, does not follow redirects, does not
  retry automatically, and bounds connect/read time — no attacker-reachable weakness
  in the default path found.
- Cross-origin redirect credential leak is NOT present on the default JDK variant
  (JDK-level sensitive-header allowlist) nor on OkHttp (library strips Authorization
  on cross-connection redirect; also non-default); it is specific to the Apache
  variant (F-N1-1).
- OAuth enhancer attaches tokens only to URLs matching a full-match regex
  (`matches()`, not `find()` — no prefix-truncation confusion); token fetch goes
  through the un-enhanced delegate client (no recursion, no token in logs).
  Overly-broad operator regexes (e.g. trailing `.*`) can over-match — operator
  config quality, documented here, no code defect.
- Plan-336 closures hold live (guards default-wired in both executors; 202-test
  suites from plan 336 remain; no public IHttpClient contract change regressed).

## Adjudication (Phase 3 input)

- F-N1-1: `remediation-target` (MEDIUM) — honor the config + strip credentials on
  cross-host redirect hops; successor item 9 → fix batch.
- F-N1-2: `remediation-target` (MEDIUM) — default to system trust, fail or warn
  otherwise; successor item 9.
- F-N1-3: `remediation-target` (observability WARN + deployment checklist);
  successor item 9 (checklist entry regardless of code fix).
- F-N1-4: `watch-only residual` — deployment-pairing note consolidating the plan-336
  matrix + F-AI2-3 pointer; dead proxy fields optional cleanup.

## Owner mapping

- F-N1-1..F-N1-4 → roadmap item 9 consolidation. No overlap: SSRF executor-side
  guards remain plan-336/closed (re-verified, not re-opened); AI-02 F-AI2-3 redirect
  residual is sharpened by F-N1-1 (transport depth was item 7's to own — now owned
  here); Vault transport posture pointer from CRED-02 resolves to F-N1-3 (Vault uses
  the shared `IHttpClient` config, so ignore-ssl-certs applies to it equally).
