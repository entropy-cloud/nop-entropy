# CORE-02 — Plugin Artifact Integrity Controls Audit

> Mission: security-audit (roadmap item 1, deliverable CORE-02)
> Date: 2026-09-18. Auditor: ZCode session (plan 2026-09-17-0831, Phase 2).
> Scope: `nop-core-framework/nop-plugin/` artifact resolution, verification,
> classloading, lifecycle guardrails.
> Method: full read of HttpPluginResourceResolver (320 lines), PluginClassLoader head,
> PluginManagerImpl load/unload/reconcile paths, VfsPluginDefinition failure threshold;
> wiring trace resolver → classloader; test cross-reference (TestPluginLifecycle).

## Verified control chain (download → cache → verify → load)

1. **Download verifies before use**: `HttpPluginResourceResolver.download()` (L156-201)
   writes to a temp file, computes SHA256, compares against expected hash, and only
   then moves to the cache location. Mismatch → `ERR_PLUGIN_SHA256_MISMATCH`,
   temp file deleted. The jar is never loaded from an unverified artifact.
2. **Hash source priority with fail-closed default** (L207-223): response header
   `X-Checksum-Sha256` → `{url}.sha256` sidecar fetch → configured `expectedHashes`
   map. If NO source available → `ERR_PLUGIN_CHECKSUM_NOT_AVAILABLE` (explicit
   failure; no silent unverified download).
3. **Cache hit re-verification** (L109-131): cached jar is re-hashed against its
   `.sha256` sidecar; mismatch → fail-fast `ERR_PLUGIN_SHA256_MISMATCH` (no silent
   re-download, per W7 design).
4. **Legacy cache without `.sha256`** (L127-130): re-downloads and verifies — never
   silently uses an unverified cached jar; redownload logged
   (`nop.plugin.legacy-cache-redownload`).
5. **`skip-cache-verify` is the only bypass** (L64, L81-84, L110-111): explicit
   `@cfg:nop.plugin.skip-cache-verify|false`, default false; when true it skips cache
   re-verify AND legacy redownload — declared startup-optimization escape hatch, not
   a hidden downgrade.
6. **Path traversal defense** (L141-154): coordinate segments (groupId/artifactId/
   version) matched against whitelist `[A-Za-z0-9_-]+(\.[A-Za-z0-9_-]+)*`; separators
   and `..` rejected → cache path cannot escape `cacheDir`.
7. **Classloader consumes only resolver output**: `PluginManagerImpl.loadPluginFromJar`
   (L567-594) calls `resourceResolver.resolvePluginResource(coords)` then constructs
   `PluginClassLoader(urls…)` from those URLs only; `loadPlugin()` requires
   `plugin.json` (`ERR_PLUGIN_NO_PLUGIN_CLASS_NAME` otherwise).
8. **Load failure rollback** (L581-591): plugin.load/start failure → unload/stop +
   `IoHelper.safeCloseObject(classLoader)` + rethrow — no stale classes remain
   registered.
9. **Unload guard**: `ERR_PLUGIN_NOT_DEACTIVATED` enforced (PluginManagerImpl:159;
   tests TestPluginLifecycle:296-301 assert the error code) — unload cannot clear
   name indexes/subscriptions while plugin still activated.
10. **Failure-threshold pause**: `VfsPluginDefinition.AUTO_ACTIVATION_FAILURE_THRESHOLD
    = 5` (L126) with `autoActivationPaused` (L133, L346-354); test
    `TestPluginLifecycle.testFailureThresholdPausesAutoActivation` covers it —
    repeated activation failure stops auto-retry (no crash-loop reload of a poisoned
    artifact).

## Findings

| ID | Severity | Anchor | Description | Remediation suggestion |
|---|---|---|---|---|
| F-C2-1 | MEDIUM (adjudicated: deferred — existing precedent) | HttpPluginResourceResolver.java:46-50, 207-223 | `expectedHashes` — the only trust source independent of the download server — has the LOWEST priority: when the server supplies header/`.sha256`, the pinned hash is never consulted. A compromised/malicious plugin server can serve a matching jar+hash pair and defeat pinning. Same-origin hash only defends against accidental corruption, not MITM/malicious server (documented honestly in the class Javadoc). | This is plan-344 P2-9 "strict-hash mode" (expectedHashes-first / fail-on-mismatch), adjudicated deferred with recorded reasons. Do NOT re-litigate here; consolidation (item 9) carries the pointer. Interim deployment guidance: use HTTPS plugin service URLs. |

## Explicit no-finding statements

- No code path loads an artifact that bypassed SHA256 verification (all four entry
  states — fresh download, cache hit, legacy cache, skip-verify — verified above).
- No path traversal via coordinates (whitelist enforced before any file operation).
- No silent downgrade paths: every degradation (no hash, hash mismatch, legacy cache)
  is an explicit error or a logged redownload.
- Trust boundary is accurately documented in-code (Javadoc states hash sources are
  same-origin; expectedHashes does not override higher-priority sources).

## Adjudication (Phase 3 input)

- F-C2-1: `adjudicated-no-fix` in this audit phase per plan-344 P2-9 precedent
  (recorded deferral with rationale). Pointer recorded for item 9 consolidation;
  if Phase 3 reprioritizes, the remediation shape is "strict-hash mode config flag".

## Owner mapping

- F-C2-1 successor owner: roadmap item 9 consolidation triage (with plan-344
  precedent citation).
- No overlap with sibling audit plans: auth/credential plans do not touch plugin
  resolution; network audit (item 7) owns HTTP-client transport security generally
  (TLS/redirect), which is downstream of but separate from artifact verification.
