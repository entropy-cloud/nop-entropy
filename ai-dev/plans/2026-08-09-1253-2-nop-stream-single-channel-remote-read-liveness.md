# 2 nop-stream Single-Channel Remote-Read Heartbeat-Timeout Liveness

> Plan Status: active
> Last Reviewed: 2026-08-09
> Source: `ai-dev/audits/nop-stream-independent-audit/2026-08-09-1252-open-audit-nop-stream-independent-audit.md` finding **AR-1 (P1)**; related Stage 43 channel-heartbeat-timeout safety feature.
> Related: mission `nop-stream-independent-audit`; execution order **{2}** (residual cross-JVM liveness hole; ranked after {1} which is silent data loss in a documented capability).
> Review history: draft v1 reviewed by independent sub-agent (ses_019f0bf46ffem1mTzL4FNSl5R3) — 1 Blocker (B1 bounded `read()` returns null for both poll-timeout and EOS) + 5 Majors → revised. v2 reviewed by independent sub-agent (ses_019e89b07ffemRO8SwzNQp4lYq) — **READY for active**, 0 Blockers / 0 Majors; B1 resolution traced across all three loop cases (poll-timeout-live / EOS / real-element), all live-repo claims verified, in-process lane confirmed safe. Promoted to active on consensus.

## Purpose

Close the AR-1 P1 finding: the Stage 43 channel-heartbeat-timeout is a piggyback detector that only re-evaluates `isChannelTimedOut()` at the top of `read()`. The multi-channel path polls with a 50 ms ceiling and re-enters `read(...)` each round, so the timeout re-fires. But the single-channel path `InputGate.readSingleChannel()` calls the **unbounded blocking** `read()` → `queue.take()`, so once a remote single-input consumer parks inside `take()`, a producer that subsequently dies (crash / network partition / stops emitting data **and** heartbeats) leaves the consumer blocked indefinitely — the heartbeat-timeout can never re-fire because the thread never returns to `checkChannelTimeout()`. The documented fast-fail-on-producer-death safety feature silently does not protect the most common streaming topology (one upstream → one operator → one sink) in the cross-JVM lane.

## Current Baseline

(Verified against live repo on 2026-08-09.)

- **Single-channel path uses unbounded blocking read.** `InputGate.readSingleChannel()` (`nop-stream-core/.../execution/InputGate.java:~378-380`) calls `channels.get(0).read()` — the blocking overload.
- **Blocking `read()` parks in `queue.take()`.** `RemoteInputChannel.read()` (`nop-stream-runtime/.../transport/RemoteInputChannel.java:~167-182`) calls `checkChannelTimeout()` **once** at the top, then `queue.take()` (line `:~173`) which blocks forever. The timeout check cannot re-fire while parked.
- **Multi-channel path is already correct.** `InputGate.readMultiChannel()` (`InputGate.java:~418`) uses `channel.read(50, TimeUnit.MILLISECONDS)` (bounded poll) and loops, so `checkChannelTimeout()` re-evaluates every ~50 ms.
- **No scheduled timeout task exists.** `RemoteInputChannel` schedules nothing; only `RemoteResultPartition` schedules heartbeats on the producer side. There is no separate timer that force-closes a channel on timeout.
- **The bounded overload already exists and is correct.** `RemoteInputChannel.read(long timeout, TimeUnit unit)` (`:~190-205`) does `queue.poll(timeout, unit)` after `checkChannelTimeout()` — this is what the multi-channel path uses.
- **CRITICAL — the bounded overload returns `null` for BOTH poll-timeout AND `END_OF_STREAM`** (`RemoteInputChannel.read(long,TimeUnit)`: `if (element == null) return null; if (element == END_OF_STREAM) return null;`). So a single-channel loop on the bounded overload MUST disambiguate `null` via `channel.isFinished()` (exactly as `readMultiChannel` does at `InputGate.java:~420`), otherwise it either busy-spins on EOS or silently terminates on every poll-timeout.
- **`channelTimeoutMs` is a field on `RemoteInputChannel`, NOT on the `InputChannel` base type** (`RemoteInputChannel.java:~88`), so `InputGate.readSingleChannel()` cannot read it. The fix therefore uses a **fixed 50 ms poll** (same as `readMultiChannel`), not `min(channelTimeoutMs, 50)`.
- **`readMultiChannel` is the disambiguation reference.** At `InputGate.java:~420`, on a `null` element it checks `channel.isFinished()`; barriers are returned via `Optional.of(element)` and (in multi-channel) routed through `handleBarrierNonRecursive` (`:~431-432`). Single-channel mode needs **no barrier alignment** (a single channel is trivially aligned), so barriers are returned as-is.
- **The fix is in shared `InputGate` code, so it affects BOTH lanes.** `InputGate` (`nop-stream-core`) is concrete and `readSingleChannel` is `private`; the in-process lane (single-channel gate backed by an in-process `ResultPartition`) uses the same `readSingleChannel` path. The rewrite changes the in-process single-channel wait from indefinite `queue.take()` to `queue.poll(50ms)` + loop. Existing single-channel in-process tests must remain green (e.g. `TestInputGate` single-channel read tests, `TestTaskLifecycle` interrupt→CANCELED, the checkpoint E2E tests that use single-input gates).
- **The asymmetry passes component tests but fails in production.** A 2-channel gate test exercises the safe path; a 1-channel gate in production exercises the broken path.

## Goals

- A single-channel remote consumer (`InputGate.readSingleChannel` over a `RemoteInputChannel`) fails fast on producer death within ~`channelTimeoutMs` instead of hanging forever — i.e. the Stage 43 heartbeat-timeout actually protects the single-input topology.
- The fix mirrors the already-correct multi-channel bounded-poll pattern (fixed 50 ms poll + `isFinished()` disambiguation + loop; no new per-channel timer thread, consistent with the design's "no dedicated timer thread per channel" note in `RemoteInputChannel.read()` Javadoc).
- A fault-injection regression test exists that (a) drives a single-channel `InputGate` read with a live-then-dead producer, (b) asserts the consumer raises `ERR_STREAM_CHANNEL_TIMEOUT` within a bounded window — **not** hang — and (c) proves normal `END_OF_STREAM` completion and a momentarily-idle-but-live producer do **not** time out or busy-spin.
- No regression in the in-process single-channel lane (existing single-channel tests remain green).

## Non-Goals

- Introducing a per-channel scheduled timeout task / timer thread. The design deliberately avoids this; the fix reuses the bounded-poll pattern.
- The 2PC sink fail-fast gate (CONN-01, P1 — covered by plan **{1}**).
- `WindowOperator` silent state-backend fallback (AR-2, P2 — backlog); `JobCoordinator.start()` fencing-epoch assertion (AR-3, P2 — backlog).
- Cross-JVM RPC / data-plane wire-codec stress hardening beyond the single-channel liveness path.
- Multi-channel path changes (it is already correct).

## Scope

### In Scope

- `InputGate.readSingleChannel()` (`nop-stream-core/.../execution/InputGate.java`): replace the unbounded `channels.get(0).read()` with a **fixed 50 ms bounded poll + loop** that mirrors `readMultiChannel`'s `read(50, TimeUnit.MILLISECONDS)` pattern. On a `null` return, disambiguate via `channel.isFinished()` (finished → `Optional.empty()` = EOS; not finished → continue loop = momentary idle). `channelTimeoutMs` is NOT read here (it lives on `RemoteInputChannel`, not the base `InputChannel`); the bounded overload re-fires `checkChannelTimeout()` inside `RemoteInputChannel.read(long, TimeUnit)` each ~50 ms.
- Preserve: watermark tracking (`currentWatermarks[0]`), `END_OF_STREAM` → `Optional.empty()` (via `isFinished()`), barrier elements returned as-is (single-channel needs no alignment), and the P1-8 interrupt contract (`InterruptedException` → set interrupt flag, return `Optional.empty()` so the state machine reaches CANCELED).
- A fault-injection regression test in `nop-stream-runtime` proving the `InputGate.readSingleChannel` → bounded-overload → `checkChannelTimeout` path times out on producer death instead of hanging, modeled on the existing `TestRemoteInputChannelHeartbeat.testConsumerTimesOutWhenSilent` (real-time elapse via `Thread.sleep`, not reflection on `lastReceivedTime`).
- A guard that existing single-channel in-process tests remain green (no in-process lane regression).

### Out Of Scope

- Changing `RemoteInputChannel.read()` (the unbounded overload) itself — the fix is at the `InputGate.readSingleChannel` dispatch site; the unbounded overload stays available to callers that genuinely want blocking semantics.
- Any change to the producer-side heartbeat scheduling (`RemoteResultPartition`).
- Adding `channelTimeoutMs` to the `InputChannel` base type (not needed; fixed 50 ms poll suffices).
- The CEP, connector, and state-backend findings (P2 — backlog).

## Execution Plan

### Phase 1 - Implement bounded-poll single-channel read

Status: planned
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java` (`readSingleChannel`).

- Item Types: `Fix`

- [ ] Rewrite `readSingleChannel()` to loop on `channels.get(0).read(50, TimeUnit.MILLISECONDS)` (fixed 50 ms, identical to `readMultiChannel`). Each iteration the bounded overload re-enters `checkChannelTimeout()` at the top of `RemoteInputChannel.read(long, TimeUnit)`, so the channel heartbeat-timeout re-fires every ~50 ms.
- [ ] **Disambiguate `null` via `isFinished()`** (resolves review B1): when `read(50, MILLISECONDS)` returns `null`, call `channels.get(0).isFinished()`; if `true` → return `Optional.empty()` (EOS); if `false` → continue the loop (momentary idle, keep polling). Do NOT treat all `null` as "continue" (busy-spin on EOS) or as "return empty" (silent premature termination on every idle poll).
- [ ] On a non-null element: preserve watermark tracking (`currentWatermarks[0] = wm.getTimestamp()` when `element.isWatermark()`); return `CheckpointBarrier` elements as-is via `Optional.of(element)` (single-channel = trivially aligned, no `handleBarrierNonRecursive`); return `END_OF_STREAM`-derived null already handled by the `isFinished()` branch above.
- [ ] Preserve the P1-8 interrupt contract: on `InterruptedException`, `Thread.currentThread().interrupt()` and return `Optional.empty()` (state machine reaches CANCELED, not FAILED/SUCCESS).

Exit Criteria:

- [ ] `readSingleChannel` no longer calls the unbounded `read()` overload (grep-verifiable: only `read(50, TimeUnit.MILLISECONDS)` appears in `readSingleChannel`).
- [ ] The loop's `null` branch calls `isFinished()` (grep-verifiable in the new `readSingleChannel` body).
- [ ] Single-channel read returns promptly on `END_OF_STREAM` (via `isFinished()`) and on interrupt (no regression vs current completion/cancel semantics).
- [ ] **No Silent No-Op** (#24): no empty branch / silent `continue` that masks the `null`-without-`isFinished` case; every branch either returns a documented value or continues with a disambiguated reason.
- [ ] `./mvnw compile -pl nop-stream-core -am` passes.
- [ ] Owner-doc adjudication: the Stage 43 owner-text update is Phase 3 (deferred, not skipped).
- [ ] `ai-dev/logs/` entry updated.

### Phase 2 - Fault-injection liveness regression test (and in-process guard)

Status: planned
Targets: new test under `nop-stream/nop-stream-runtime/src/test/...` (transport / `InputGate` lane); reuse the `TestRemoteInputChannelHeartbeat` harness pattern.

- Item Types: `Proof`

- [ ] **Model on `TestRemoteInputChannelHeartbeat.testConsumerTimesOutWhenSilent`** (`nop-stream-runtime/src/test/.../transport/TestRemoteInputChannelHeartbeat.java:~112-130`), which proves channel-level timeout via real-time elapse (`Thread.sleep(channelTimeout + 80)`) then `assertThrows(StreamException.class, () -> consumer.read(50, MILLISECONDS))` matching `nop.err.stream.channel-timeout`. The new test lifts this proof to the **`InputGate.readSingleChannel`** layer (the gap the existing tests don't cover). Do NOT use reflection on `lastReceivedTime` (it has no setter — `:~96`, getter-only `:~262`); use real time elapse with a short `channelTimeoutMs` (e.g. 120 ms).
- [ ] **Liveness case**: build a single-channel `InputGate` over a `RemoteInputChannel`; start a `gate` read on a background thread with a live producer (send one message first to reset `lastReceivedTime` so the read enters the poll loop while liveness is fresh); then let the producer go silent; wait beyond `channelTimeoutMs`; assert the read throws `ERR_STREAM_CHANNEL_TIMEOUT` within a bounded window (≤ `2 * 50ms + channelTimeoutMs + slack`), **not** hang.
- [ ] **Negative control (live producer)**: with the producer still emitting within the window, the single-channel read does **not** time out and does **not** busy-spin (it returns elements / parks briefly).
- [ ] **EOS control**: a producer that sends `CONTROL_END_OF_STREAM` makes the single-channel read return promptly (no busy-spin, no timeout) — directly verifies the `isFinished()` disambiguation (review B1).
- [ ] **In-process lane guard**: the existing single-channel in-process tests (`TestInputGate` single-channel read, `TestTaskLifecycle` interrupt→CANCELED, and at least one single-input checkpoint E2E) remain green after the fix (no in-process regression — review M3).
- [ ] Verify the multi-channel path remains green in the same suite (regression guard for the already-correct path).

Exit Criteria:

- [ ] **Test-Mandated Feature Rule** (#25): the tests assert the **result** (timeout within a bounded window for a silent producer; elements returned for a live producer; prompt empty return on EOS), not merely absence of a hang.
- [ ] **End-to-End / Anti-Hollow** (#22): the liveness test exercises the full single-channel path gate → `read(50, ms)` → `queue.poll` → `checkChannelTimeout` re-firing each loop, proving the wiring is live.
- [ ] **Wiring Verification** (#23): the test proves `checkChannelTimeout()` is re-entered each loop (the timeout fires during a *parked* read), not just once at the top — this is the core AR-1 fix.
- [ ] **Revert guard (timing-correct, review M4)**: the liveness case starts the read while liveness is fresh (one message sent first), so reverting Phase 1 (back to unbounded `read()` → `queue.take()`) makes the consumer park in `take()` past `channelTimeoutMs` and the test fails the bounded-window assertion. (If the read started after timeout, the reverted code's single top-level `checkChannelTimeout()` would also throw — the test would not distinguish; hence the fresh-start requirement.)
- [ ] `./mvnw test -pl nop-stream-runtime -am` passes (the `-am` reactor includes `nop-stream-core`, so the in-process guard tests there also run).
- [ ] No owner-doc update required in Phase 2 (the doc reconciliation is Phase 3).
- [ ] `ai-dev/logs/` entry updated.

### Phase 3 - Document the single-channel timeout behavior

Status: planned
Targets: `ai-dev/design/nop-stream/checkpoint-design.md:~1391` (the "channel 心跳（distributed）" row that states "`read()` 路径 piggyback 超时检查" without qualifying single vs multi channel — the implicit assumption that caused AR-1); `ai-dev/design/nop-stream/component-roadmap.md:~192` (the heartbeat row).

- Item Types: `Fix`

- [ ] Update the "channel 心跳（distributed）" row at `checkpoint-design.md:~1391` and the heartbeat row at `component-roadmap.md:~192` so they state the piggyback heartbeat-timeout protects **both** single-channel and multi-channel remote reads (post-fix), removing the implicit "works on the read path" ambiguity that caused AR-1. (`docs-for-ai/04-reference/source-anchors.md` has no Stage 43 / `RemoteInputChannel` anchor today — verified — so no `source-anchors.md` edit is required for this finding; if a `RemoteInputChannel` anchor is added later it should mention single+multi coverage.)

Exit Criteria:

- [ ] The two design-doc rows read consistently with Phase 1 + Phase 2 (single + multi channel both covered), verified against live code.
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exits 0.
- [ ] `ai-dev/logs/` entry updated.

## Closure Gates

- [ ] AR-1 P1 finding closed: `readSingleChannel` uses a bounded poll that re-evaluates `checkChannelTimeout` each round (verified against live code).
- [ ] The single-channel remote-read no longer hangs forever on producer death; the fault-injection test proves it surfaces within a bounded window.
- [ ] Multi-channel path and normal `END_OF_STREAM` / interrupt semantics are preserved (regression tests green).
- [ ] No in-scope live defect downgraded to a non-blocking follow-up.
- [ ] Independent sub-agent closure audit completed and evidence recorded (see Closure).
- [ ] **Anti-Hollow Check**: closure audit traced the runtime call chain `readSingleChannel` → `RemoteInputChannel.read(long, TimeUnit)` → `queue.poll` → `checkChannelTimeout` and confirmed the loop re-enters `checkChannelTimeout` each round (not just once); no silent no-op; the `null` branch is disambiguated by `isFinished()`.
- [ ] `./mvnw clean install -pl nop-stream -am -T 1C` passes (build + tests).
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exits 0.
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` exits 0.
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exits 0.

## Deferred But Adjudicated

*(none — all in-scope items are addressed in Phases 1-3)*

## Non-Blocking Follow-ups

- Consider unifying the single-channel and multi-channel read loops into a shared helper to prevent future drift (maintainability only; the two paths are behaviorally aligned after this fix). Source: AR-1 fix residue.

## Closure

Status Note: *(filled at closure)*
Completed: *(filled at closure)*

Closure Audit Evidence:

- Reviewer / Agent: *(filled at closure — independent sub-agent, fresh session)*
- Audit Session: *(task id)*
- Evidence: *(per-Exit-Criterion PASS/FAIL with live code/test references; `check-plan-checklist.mjs --strict` exit 0; `scan-hollow-implementations.mjs --module nop-stream --severity high` exit 0; anti-hollow trace of the readSingleChannel → checkChannelTimeout loop)*

Follow-up:

- *(only non-blocking follow-ups; the shared-helper unification above is the only candidate)*
