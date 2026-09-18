# NET-03 — Stream Processing Isolation Audit (control-plane auth / transport / checkpoint storage / prior closures)

> Mission: security-audit (roadmap item 7, deliverable NET-03)
> Date: 2026-09-19. Severity labels: CRITICAL/HIGH/MEDIUM/LOW and P0/P1/P2/P3.
> Scope: `nop-stream/` (10 submodules) NEW security surface only + prior-audit closure
> re-verification: control-plane RPC (stream-control-rpc.beans.xml, StreamControlRpcServer,
> MessageRpcServer), data-plane transport codecs, checkpoint storage
> (LocalFileCheckpointStorage, JdbcCheckpointStorage, CheckpointSerDe), ops HTTP server
> (F-09b), standing invariant checkers, F-STREAM-1 fix anchor.
> Method: wiring + full read of the RPC/storage/ops classes; `node
> ai-dev/tools/check-nop-stream-invariants.mjs` and `check-nop-stream-audit-manifest.mjs`
> executed live (logs `_tmp/security-audit/net-stream-invariants.log`,
> `net-stream-manifest.log`); prior closure anchors spot-checked against live code;
> adjudicated findings NOT re-opened absent new live evidence.

## Prior-audit closure anchor status (latest series)

| Anchor (2026-08-13-1930 series) | Live verification |
|---|---|
| P1-21-01 container-value serde loss → plan `2026-08-13-1930-1` (completed) | Fix live: `MemoryStateSerDe.restoreMapState` rematerializes map values via `deserializeValue` (MemoryStateSerDe.java:426-427); CEP queue restore path covered by the plan's regression suites. Holds. |
| P1-09-01 ChannelState silent skip → plan `2026-08-13-1930-1` (completed) | Fix live: skip is now observable — catch block logs the throwable with the P1-09-01 rationale comment (ChannelState.java:207-213). Holds. |
| P1-18-02 component-roadmap C5 drift → plan `2026-08-13-1930-3` (completed) | Plan completed; doc sync outside this audit's re-verification loop (no contrary live evidence sought). Holds. |
| P2×15 backlog items (nop-stream-invariant-loop roadmap) | Not re-opened per plan `Deferred But Adjudicated`; no new live evidence contrary to adjudications encountered during this audit's surface reads. |
| F-STREAM-1 checkpoint checksum fix (fix batch 2, plan 5, closed ALLOW) | Fix live and regression-tested: write-side checksum stamp (CheckpointSerDe.java:114-117); read-side verify-BEFORE-consume with typed fail-fast `checkpointChecksumMismatch` (L142-164); text-round-trip-then-normalize in `computeCanonicalChecksumHex` (L351-365); `TestCheckpointManifestChecksum` includes the bean-BigDecimal convergence cases (L67-68, L274-290). Holds. |

## Verified controls

1. **Ops HTTP surface is hard-fail secure by default** (`StreamOpsHttpServer`, F-09b):
   disabled by default; loopback 127.0.0.1:8901 default bind; non-loopback bind without
   a token REFUSES TO START (L96-101); bearer-token guard enforced on EVERY context
   including the catch-all 404 (L116-138); 401 with structured body on bad credentials;
   threaddump/job-lifecycle/checkpoint endpoints all behind the same guard
   (`TestStreamOpsAuthAndClassLoading`).
2. **Checkpoint storage path confinement is double-layered**
   (`LocalFileCheckpointStorage`): id whitelist `[a-zA-Z0-9_-]+` (L325-331) AND
   canonical-path `startsWith(baseDirCanonical)` traversal check (L333-340) — no path
   escape from job/checkpoint ids; write path is temp-then-move.
3. **JDBC checkpoint storage is injection-safe** (`JdbcCheckpointStorage`): constant
   table names (L50-51), parameterized values (e.g. L674 `params[0] = jobId`),
   querySpace-scoped access.
4. **Checkpoint integrity end-to-end** (F-STREAM-1, above): tamper/truncation of the
   stored body fails fast at load; future envelope versions fail fast (Stage 51);
   legacy no-checksum bytes tolerated with debug log (adjudicated).
5. **Default transport is in-process** (both `stream-control-rpc.beans.xml` and
   `stream-data-plane.beans.xml`: `nopStreamMessageService` ioc:default =
   `LocalMessageService`) — single-JVM deployments have no network surface for the
   stream control/data planes at all.

## Findings

| ID | Severity | Anchor | Description | Remediation suggestion |
|---|---|---|---|---|
| F-N3-1 | MEDIUM (P2) — process integrity regression | `node ai-dev/tools/check-nop-stream-invariants.mjs` → exit 1: 8× scan-output-contract V4 (unregistered emission points `WindowOperator.java:1284,2128`, `CepOperator.java:711,1113`) + 9× V5/V3 stale registry points; `node ai-dev/tools/check-nop-stream-audit-manifest.mjs` → exit 1: 10 denominator mismatches (e.g. test-java 453→637, beans files 2→7, connector files 16→24). Logs: `_tmp/security-audit/net-stream-{invariants,manifest}.log`; registries `ai-dev/audits/nop-stream-invariants/{output-contract,wiring}-registry.json` last synced 524446eee8, before stream refactors dde612/448626/8e33ab/7925058/1f153f3c | **Both standing invariant checkers are RED on the live tree.** The 2026-08-12/13 invariant-loop series established these checkers as THE closure anchor for nop-stream ("violation = red" contract). Post-audit stream refactors landed without re-syncing the registries/manifest, so the anchor infrastructure now fails wholesale: new OutputTag emission points exist unregistered (unverified by the invariant gate), stale wiring points accumulate, and the audit manifest no longer describes the module (test corpus grew 453→637 without manifest update). Not directly exploitable, but the drift saturates the red signal — a REAL new invariant violation would be indistinguishable from noise, defeating the standing control. | Re-sync `output-contract-registry.json` / `wiring-registry.json` / audit manifest against live code (registry sync tooling exists — plan 1326-1 precedent); re-run both checkers to green; add "checkers green" to the stream module's change checklist. |
| F-N3-2 | MEDIUM (P2) — design gap, multi-JVM deployments only | nop-stream/nop-stream-runtime/src/main/resources/_vfs/nop/stream/beans/stream-control-rpc.beans.xml (deployment scaffold) + nop-network/nop-rpc/nop-rpc-core/src/main/java/io/nop/rpc/core/message/MessageRpcServer.java:83-103 (`processRequest` dispatches `ApiRequest` with no authentication) + nop-stream-runtime/.../rpc/StreamControlRpcServer.java (no auth layer; `@Internal` marker only) | **Stream control-plane RPC has no application-level authentication/authorization — the message transport IS the entire trust boundary.** `MessageRpcServer.processRequest` dispatches any `ApiRequest` that arrives on the topic (`nop-stream.rpc.task.{nodeId}` / `nop-stream.rpc.coordinator.{jobId}`) straight into reflective service dispatch: deployTask, cancelTask, checkpoint control. There is no caller identity, no credential propagation, and no seam to add one (StreamControlRpcServer wraps rpcHandler directly). With the documented production backends this means: SysDao = anyone with INSERT on the message table can drive job lifecycle; Pulsar/Kafka = anyone with produce permission on the topic. Default (LocalMessageService, in-JVM) is unaffected. The beans.xml documents backend choice but documents NO trust-boundary requirement for it. | Minimum: document the required transport ACL posture next to the deployment scaffold (control-plane topics must be producer-restricted) + item-9 deployment checklist; better: support an `IRpcService` auth interceptor seam (or ApiHeaders credential check) in StreamControlRpcServer wiring. |
| F-N3-3 | LOW (P3) | nop-stream/nop-stream-runtime/.../ops/StreamOpsHttpServer.java:148-162 (`expected.equals(header)`) | **Ops bearer-token compare is not constant-time** (`String.equals`), and tokens are compared without hashing — timing side channel on a (typically loopback/ops-network) endpoint. Theoretical exposure. | Use `MessageDigest.isEqual` on UTF-8 bytes; LOW. |
| F-N3-4 | LOW (P3) — documented residual | LocalFileCheckpointStorage.java:374 (`Files.createDirectories`, default umask) + serialized state content | **Checkpoint files inherit process umask** — checkpoints contain serialized job state (potentially business events) and are created world-readable under default umask 022. Same-class behavior as most frameworks; access control = directory ownership. | Item-9 deployment hardening: run with restrictive umask / dedicated checkpoint dir ownership; optional explicit POSIX perms when supported. |

## Explicit no-finding statements

- No path traversal into checkpoint storage (double whitelist+canonical confinement),
  no SQL injection in JDBC storage (constants + parameters), no secret material in
  stream main sources (sweep §6 of inventory).
- Data-plane codecs (Identity/SysDao/PulsarString/KafkaString) serialize the envelope
  without interpreting it — no injection surface beyond the transport itself (covered
  by F-N3-2's trust-boundary classification).
- F-STREAM-1 and F-09b fixes verified live with regression tests; prior 2026-08-13
  series adjudications hold — no re-opened findings (no new live evidence contrary to
  any adjudication was found during this audit's reads).
- Ops server explicit-off semantics: disabled config + start() throws (no silent
  empty listener); unknown paths structured 404 behind auth.

## Adjudication (Phase 3 input)

- F-N3-1: `remediation-target` (registry/manifest re-sync — tooling exists) —
  successor item 9; process fix ("sync registries with stream refactors") alongside.
- F-N3-2: `remediation-target (docs/ACL seam decision)` — at minimum a documented
  trust boundary; successor item 9.
- F-N3-3: `watch-only residual`.
- F-N3-4: `watch-only residual` (deployment checklist).

## Owner mapping

- F-N3-1..F-N3-4 → item 9. No overlap: CRED-02 Vault transport posture → NET-01
  (F-N1-3); AI-02 SSRF transport depth → NET-01 (F-N1-1/F-N1-4); nop-stream
  functional P2s remain the nop-stream backlog (not security-scope here).
