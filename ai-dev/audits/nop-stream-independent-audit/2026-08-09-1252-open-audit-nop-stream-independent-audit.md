> Audit Status: planned
> Audit Type: open-ended
> Mission: nop-stream-independent-audit
> Disposition: P1 AR-1 → remediation plan `ai-dev/plans/2026-08-09-1253-2-nop-stream-single-channel-remote-read-liveness.md`. P2 (AR-02, AR-03) → follow-up backlog at `ai-dev/backlog/nop-stream-independent-audit-roadmap.md §Follow-up Backlog`.

# Open-Ended Adversarial Audit — `nop-stream/` (1st round)

- **Audit date**: 2026-08-09
- **Scope**: live code/config/tests under `nop-stream/` (10 submodules, 1036 non-generated Java files) probed for contract drift, dead code, missing error handling, framework-specific anti-patterns, and convention violations per `AGENTS.md`. Read the methodology (`ai-dev/skills/open-ended-adversarial-review-prompt.md`) and `AGENTS.md` **completely** before executing.
- **Methodology**: discovery-oriented (not a fixed checklist). Cut-ins came from code signals. De-duplicated against the frozen finding corpus (`stage-{18..22}-*-disposition.md`, 97 findings all disposed) and the same-day multi-audit (`2026-08-09-1252-multi-audit-*`, 1 P1 + 13 P2 + 3 P3). Only **new** findings not already in those corpora are reported below.
- **Baseline facts re-confirmed**: dependency tree is a strict tree rooted at `nop-stream-core`; 0 forbidden imports (`org.apache.flink`/`io.netty`/`org.apache.zookeeper`); 34 `_*.java` in `_gen/` are valid codegen (no hand-edits); no empty `catch{}` blocks; no `TODO/FIXME/HACK`; executor/scheduler lifecycle (TaskManager, JobCoordinator failure-detector, CheckpointCoordinator scheduler/timeout/persist, GraphModelCheckpointExecutor barrier-injector) all shut down in their `close()`/`shutdown()`. The wire-format barrier round-trip (`DataPlaneWireSupport.normalizePayload` ↔ `StreamElementCodec.decodeBarrierFromPayload`) is faithful (all 3 `CheckpointBarrier` fields preserved). `LocalFileCheckpointStorage` path-traversal guard (`validateId` + `validatePath` canonical-prefix check) is robust. `ClassNameValidator` allowlist guards the cross-JVM `Class.forName(valueType)` deserialization path.
- **Heuristics used**: "异常路径侦探" (exception/liveness paths) + "事务边界追踪者" (fencing/recovery boundaries) + "组合爆炸测试者" (single-channel × blocking-read × heartbeat-timeout combination).

---

## Severity summary

| Severity (mission scheme) | Count | Drives remediation? |
|---|---|---|
| P0 | 0 | — |
| P1 | 1 | yes |
| P2 | 2 | backlog triage (no plan by itself) |

---

## Findings

### [P1] AR-1 — `RemoteInputChannel.read()` (blocking) cannot re-fire the Stage 43 channel-heartbeat-timeout once it enters `queue.take()`; the single-channel remote read path (`InputGate.readSingleChannel`) hangs forever on producer death

*Justification: real functional defect + contract drift in a documented safety feature (Stage 43 heartbeat-timeout, built specifically to fail-fast on producer death "faster than the coarse lease timeout ~15-20s"). The feature silently does not work for the single-input topology — the most common streaming shape. Bounded to the cross-JVM (residual-risk) lane, hence P1 not P0.*

- **Files**:
  - `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteInputChannel.java:167-182` (blocking `read()` → `queue.take()` at :173; timeout checked ONCE at :172 before blocking)
  - `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteInputChannel.java:223-241` (`isChannelTimedOut()` / `checkChannelTimeout()` — the only timeout check, piggybacked on the read path; **no** scheduled task ever closes the channel on timeout)
  - dispatch: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java:378-380` (`readSingleChannel` calls the **blocking** `channels.get(0).read()`) vs `:418` (`readMultiChannel` uses the bounded `channel.read(50, TimeUnit.MILLISECONDS)` and loops)
- **Evidence**:
  ```java
  // RemoteInputChannel.java:167-182 — blocking read
  @Override
  public StreamElement read() throws InterruptedException {
      checkDecodeError();
      checkChannelTimeout();              // checked ONCE …
      StreamElement element = queue.take(); // … then blocks FOREVER. A producer that dies
                                            // after this point is never detected: there is no
                                            // scheduled timeout task, and checkChannelTimeout()
                                            // cannot re-fire while we are parked inside take().
      ...
  }
  ```
  ```java
  // InputGate.java:378-380 — single-channel path uses the BLOCKING read()
  private Optional<StreamElement> readSingleChannel() {
      try {
          StreamElement element = channels.get(0).read();   // NOT read(50ms)
  ```
  ```java
  // InputGate.java:418 — multi-channel path correctly uses a bounded poll, so it loops
  StreamElement element = channel.read(50, TimeUnit.MILLISECONDS);  // re-checks timeout each ~50ms
  ```
- **Current state**: the Stage 43 channel-heartbeat-timeout is a **piggyback** detector — it only evaluates `isChannelTimedOut()` at the top of `read()` / `read(timeout)`. The multi-channel `InputGate` path is safe because it polls with a 50 ms ceiling and re-enters `read(...)` each round, re-evaluating the timeout. But the single-channel path (`readSingleChannel`, used whenever `channels.size() == 1`) calls the **unbounded** `read()` → `queue.take()`. Once a remote single-input consumer has drained the queue and parked inside `take()`, a producer that subsequently crashes / network-partitions / stops emitting (data **and** heartbeats) leaves the consumer blocked **indefinitely** — the heartbeat-timeout can never re-fire because the thread never returns to `checkChannelTimeout()`. There is no separate scheduled task that force-closes the channel on timeout (confirmed: `RemoteInputChannel` schedules nothing; only `RemoteResultPartition` schedules heartbeats on the producer side). The consumer is only unblocked by a later message (which a dead producer never sends), an external `close()` (which requires some *other* mechanism to notice the death — exactly what the heartbeat-timeout was meant to be), or an interrupt.
- **Risk**: for the most common streaming topology (one upstream → one operator → one sink), the cross-JVM producer-death fail-fast that Stage 43 explicitly advertises does not function. The system silently falls back to the ~15-20 s coarse lease timeout (or hangs until an operator-level/liveness probe intervenes) — the precise "too slow" scenario the feature was built to prevent. The defect is asymmetric and non-obvious: it works for multi-input operators but not single-input, so it can pass component-level tests that use a 2-channel gate while failing in production on a 1-channel gate.
- **Mitigation context (why P1 not P0)**: within the e2e-proved envelope (`ready only for ...`), the in-process lane does not use `RemoteInputChannel` (it uses in-process `InputChannel` backed by a `ResultPartition`, not the message-service transport), so the defect is **not active** in the proven in-process capability surface. It is live only in the cross-JVM single-channel configuration, which the readiness report already classifies as `residual-risk` (the stronger cross-JVM invariants are not directly covered by the passing T2 tests). A reviewer weighting "liveness hole in a documented safety feature" categorically may legitimately keep it P1; one weighting "residual-lane only" may downgrade to P2. Either way it is a real, mechanical defect.
- **Suggested fix**: make the single-channel path use a bounded poll loop (mirror `readMultiChannel`'s `read(50, TimeUnit.MILLISECONDS)` + re-check), OR have `RemoteInputChannel.read()` self-interrupt / offer `END_OF_STREAM` from a scheduled timeout task. The latter is cleaner but adds a per-channel timer (the design currently forbids that); the former is a 1-line change in `readSingleChannel` (use `channel.read(channelTimeoutMs > 0 ? Math.min(channelTimeoutMs, 50) : 50, MILLISECONDS)` and loop).
- **Confidence**: certain (verified blocking `take()` at :173, single-channel dispatch at InputGate:380, no scheduled timeout task, multi-channel bounded-poll contrast at :418).
- **Review status**: open
- **De-dup check**: not in the frozen finding corpus (97 findings, all disposed) nor in the same-day multi-audit (CONN/CEP/API/TEST/DOC/NAME families). The Stage 13/14 evidence files list cross-JVM liveness as `residual-risk` in the aggregate but do not identify this specific single-channel-blocking-read mechanism.

---

### [P2] AR-2 — `WindowOperator.copyForSubtask()` silently drops `stateBackend` and `open()` silently falls back to `MemoryStateBackend` (no log); strictly worse than the already-reported CEP-03 (CepOperator) variant which at least logs a warning

*Justification: amplification of the multi-audit's CEP-03 pattern. The new, previously-unreported fact is that the WindowOperator variant is SILENT — a misconfigured deployment degrades to non-checkpointed in-memory state with zero observable signal, unlike CepOperator which emits `LOG.warn`.*

- **Files**:
  - `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:355-370` (copy constructor — does not propagate `stateBackend`) and `:381-383` (`copyForSubtask` → `new WindowOperator<>(this)`)
  - `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:418-419` (`open()` silent fallback)
  - contrast: `nop-stream/nop-stream-cep/.../operator/CepOperator.java:265-268` (the **logged** variant)
- **Evidence**:
  ```java
  // WindowOperator.java:418-419 — open() silent fallback, NO log
  if (this.stateBackend == null) {
      this.stateBackend = new MemoryStateBackend();
  }
  ```
  ```java
  // WindowOperator.java:355-370 — copy constructor omits stateBackend (protected field in AbstractStreamOperator)
  WindowOperator(WindowOperator<K, IN, ACC, OUT, W> other) {
      super(other.userFunction);
      this.windowAssigner = other.windowAssigner;
      // ... 11 immutable fields copied by reference ...
      this.accumulationMode = other.accumulationMode;
      // stateBackend NOT set → null after copyForSubtask
  }
  ```
  ```java
  // CepOperator.java:265-268 — the SAME pattern but WITH a warn (the reference CEP-03 finding)
  LOG.warn("CepOperator opened without a configured state backend; falling back to " +
          "MemoryKeyedStateBackend. Checkpoint consistency is not guaranteed. ...");
  keyedStateStore = new MemoryKeyedStateBackend<>(Object.class);
  ```
- **Current state**: `stateBackend` is a `protected` field on `AbstractStreamOperator`. `WindowOperator.copyForSubtask()` constructs via the package-private copy constructor, which copies 11 immutable config fields but **not** `stateBackend`. The framework's normal flow re-injects `stateBackend` per-subtask after `copyForSubtask()` (so the default path is fine — same as CEP-03's mitigation), but if a deployment flow injects `stateBackend` only on the template operator and relies on `OperatorChain.deepCopy()` propagation without re-injection, every WindowOperator subtask lands on `MemoryStateBackend` with **no log line at all**. CepOperator — the sibling operator with the identical pattern — at least emits `LOG.warn("... Checkpoint consistency is not guaranteed ...")`. WindowOperator emits nothing.
- **Risk**: a silent checkpoint-consistency degradation is strictly harder to diagnose than the logged CepOperator variant. The inconsistency between the two operators (one warns, one doesn't) is itself a triage hazard — an operator seeing the CepOperator warn might assume WindowOperator would warn too. This is the same latent-configuration-hazard class as CEP-03 (already P2 in the multi-audit); the new information is the silent/no-signal variant.
- **Suggested fix**: add a `LOG.warn(...)` to `WindowOperator.open()` at the fallback branch mirroring CepOperator's message, AND/OR document/test the re-injection requirement (CEP-03's suggested fix). Ideally extract a shared `AbstractStreamOperator.ensureStateBackend()` helper so both operators share one fallback-with-warn path.
- **Confidence**: certain.
- **Review status**: open
- **De-dup check**: CEP-03 in the multi-audit explicitly notes "mirrors a cross-cutting pattern (WindowOperator)" but grades/logs only the CepOperator instance. The WindowOperator silent variant is not separately recorded.

---

### [P2] AR-3 — `JobCoordinator.start()` `else` branch syncs `recoveryGen` from the full pre-set `fencingEpoch` value, assuming the leaderEpoch component is 0 (non-HA); no assertion enforces the assumption, so a future caller pre-setting an HA-mode epoch corrupts `recoveryGen` and breaks `EPOCH_SCALE` separation

*Justification: latent fencing-invariant hazard introduced by the most recent hardening commit (e973d7f38). The assumption is documented in a comment but not enforced; no current caller violates it, so P2 (backlog) not P1.*

- **File**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:342-357` (the `else` branch at :356) and `:110` (`EPOCH_SCALE = 1_000_000L`) + `:1187-1188` (`deriveHaFencingEpoch = leaderEpochValue * EPOCH_SCALE + recoveryGen`)
- **Evidence**:
  ```java
  // JobCoordinator.java:342-357
  if (fencingEpoch.get() == 0L) {
      recoveryGen.set(1L);
      fencingEpoch.set(deriveHaFencingEpoch(0L, recoveryGen.get()));
  } else {
      // ... comment: "in non-HA mode the leaderEpoch component is 0,
      //  so the epoch's low-order component equals recoveryGen" ...
      recoveryGen.set(fencingEpoch.get());   // assumes leaderEpoch component == 0
  }
  ```
  ```java
  // JobCoordinator.java:1187-1188 — the composition the sync assumes a clean split of
  public static long deriveHaFencingEpoch(long leaderEpochValue, long recoveryGen) {
      return leaderEpochValue * EPOCH_SCALE + recoveryGen;
  }
  ```
- **Current state**: the `else` branch runs whenever `fencingEpoch` was pre-set non-zero before `start()`. The comment documents the assumption "in non-HA mode the leaderEpoch component is 0, so the epoch's low-order component equals recoveryGen." That holds for every documented pre-set caller today (`JobCoordinatorMain`, `RpcDistributedExecutor`, `EmbeddedDistributedExecutor` all derive `deriveHaFencingEpoch(0,1) = 1`). But the code performs **no** assertion that the pre-set value's high-order component is actually 0. If a future caller pre-sets an HA-mode epoch — e.g. `setFencingEpoch(deriveHaFencingEpoch(5, 1)) = 5_000_001` — then `recoveryGen.set(5_000_001)` (the *full* value, not the low-order `1`). The next `globalRecovery()` does `recoveryGen.incrementAndGet() = 5_000_002` and `deriveHaFencingEpoch(leaderEpochValue, 5_000_002)`. With `leaderEpochValue = 0` (non-HA still) that yields `5_000_002` — strictly greater, so the rotate-on-recovery invariant *happens* to hold, but `recoveryGen` no longer counts recoveries (it's a 7-digit number) and the `EPOCH_SCALE` separation is destroyed: a subsequent leader switch to `leaderEpoch = 6` would derive `6 * 1_000_000 + recoveryGen`, which can **collide** with the corrupted recoveryGen space. The fencing-epoch monotonicity/collision guarantee is a documented HA invariant (`:72`, `:95-110`).
- **Risk**: no current caller triggers this (all pre-set paths use leaderEpoch 0). The hazard is a future regressor: a contributor wiring a new HA start path that pre-sets a leaderEpoch-bearing epoch would silently corrupt the fencing space with no fail-fast. The comment is the only guard.
- **Suggested fix**: add an assertion in the `else` branch that the pre-set value is non-HA, e.g. `assert fencingEpoch.get() < EPOCH_SCALE : "pre-set fencingEpoch must have leaderEpoch component 0 in start(); HA activation goes through onGrantLeadership"` — or compute `recoveryGen` from the low-order component via `fencingEpoch.get() % EPOCH_SCALE` and assert the high-order component is 0.
- **Confidence**: certain (mechanical trace of `deriveHaFencingEpoch` arithmetic); impact is latent.
- **Review status**: open
- **De-dup check**: introduced by commit e973d7f38 (the cross-JVM hardening, dated 2026-08-09 — the same day as this audit). Not in any prior finding corpus (the commit post-dates the frozen corpus freeze).

---

## Total assessment (free-form)

The module is, by some margin, the most heavily audited surface in this repository (30+ prior audit directories, a 23-stage independent mission, 97 frozen findings all disposed, a same-day multi-audit). My independent pass confirms the core correctness infrastructure is sound: dependency/boundary hygiene, generated-file discipline, IoC wiring, executor lifecycle, wire-format round-trip faithfulness, path-traversal protection, and deserialization allowlisting are all clean. The 3 findings above are the residual that survived that scrutiny:

1. **AR-1 (P1)** is the one I would act on first: it is a genuine liveness hole where a documented safety feature (Stage 43 heartbeat-timeout) silently does not protect the single-channel remote-read path — the most common streaming topology. The asymmetry (works for multi-input, not single-input) is the kind of defect that passes component tests and bites in production. The fix is small.
2. **AR-2 (P2)** closes a consistency gap between two sibling operators (WindowOperator silent vs CepOperator logged) on a pattern already flagged next door.
3. **AR-3 (P2)** hardens a comment-only assumption in the newest commit before a future contributor trips it.

## Blind-spot self-assessment

- **No live build/test run**: this pass is static. AR-1 is a static-read liveness argument, not a run-confirmed hang (though the control-flow is unambiguous). A fault-injection test that kills a remote single-channel producer mid-`read()` would confirm.
- **RocksDB deep incremental semantics**: I scanned `nop-stream-rocksdb` for boundary/dependency only; I did not independently re-derive the incremental-snapshot → restore → compaction → cleanup correctness (the multi-audit admitted the same blind spot).
- **CEP NFA matching under concurrency with checkpoint continuation**: examined `SharedBuffer.registerEvent` overflow (matches the multi-audit's CEP-01) but did not re-derive the full refcount/release lattice across `advanceTime` × `process` × checkpoint restore.
- **Connector external-effect semantics** (JDBC 2PC ledger, file sink atomic rename, CDC offset): the headline CONN-01 (subtask-qualified idempotency key) from the multi-audit is the dominant issue; I did not independently re-audit the ledger schema beyond confirming the multi-audit's read.
- **Test-vacuity sweep beyond TEST-01/TEST-02**: Stage 17 owns test-effectiveness governance; I spot-checked but did not re-enumerate the full critical-test registry.

## Severity distribution

| Severity | Count | Main categories |
|---------|------|---------|
| P0 | 0 | — |
| P1 | 1 | liveness / contract drift (heartbeat-timeout vs single-channel blocking read) |
| P2 | 2 | silent state-backend fallback (CEP-03 amplification); fencing-epoch assertion gap (recent commit) |

<AI_STEP_RESULT>issues</AI_STEP_RESULT>
