# mission-driver Troubleshooting & Diagnostics

> Status: active
> Created: 2026-06-21
> Scope: operational diagnostics for the `tools/mission-driver` tool itself (the runner/executor/flow engine), not the project it drives.

This is the playbook for "a mission-driver step looks stuck". It covers how to tell a real hang from normal long work, the failure modes the runner can hit, the runner's own safety net, and how to recover. A concrete case study (silent sub-agent stream stall) is included because it is the most common and most easily misdiagnosed mode.

---

## 0. Mental model first

A mission run is a tree of steps executed by `src/engine.js`. Each AI step is one `opencode run` child process spawned by `src/runner.js` → `src/execute()` in `src/executor.js`. That child process:

- streams its text output to a per-step log file `_tmp/<run-dir>/oc-<STEP>-<ts>-<rand>.log`,
- runs **one** top-level agent loop, which may itself spawn **in-process** sub-agents (e.g. `agent=explore mode=subagent` via the Task tool),
- shares a global `opencode.db` and a global log at `~/.local/share/opencode/log/opencode.log` with every other opencode process on the machine.

Key consequence: a sub-agent is **not** a separate OS process. The whole step is one PID. When a sub-agent hangs, it hangs inside that single PID, and the parent step process waits on it.

---

## 1. Quick triage: is the step actually hung?

Run these four checks. All four together distinguish "working slowly" from "wedged".

### 1.1 Is the step process alive and burning CPU?

```sh
# <RUN_PID> = the mission-driver node process (the one running main.js)
# <STEP_PID> = its current opencode child (find via pgrep -P <RUN_PID>)
ps -o pid,ppid,etime,%cpu,stat,command -p <STEP_PID>
```

| Observation | Meaning |
|---|---|
| `%cpu` > 0, `R`/`R+` | Actively computing — keep waiting. |
| `%cpu` = 0, `S`/`Ss`, for many minutes | **Blocked/waiting.** Candidate hang. |
| process gone | Step ended (check main log for the result). |

A step at **0% CPU in `S` state for >10 min with no output** is almost certainly wedged, not thinking.

### 1.2 Is the step log file still growing?

```sh
ls -l _tmp/<run-dir>/oc-<STEP>-*.log     # mtime = last write
stat -f "%m %z %N" _tmp/<run-dir>/oc-<STEP>-*.log   # mtime epoch + size
```

If the step log's mtime/size has not changed in >5 min while the process is alive, the step is emitting nothing. This is the same signal the runner's watchdog uses (see §4).

### 1.3 Any live network socket? (does it have an open request to the model?)

```sh
lsof -i -a -p <STEP_PID>          # TCP/UDP sockets of the step
```

- **No socket at all** while the process sleeps → it is **not** waiting on a live HTTP request. The model call is in a dead/reaped connection or a backoff sleep. Strong hang signal.
- **ESTABLISHED socket to the model provider** → there is still a live request in flight; could be a slow/long stream. Watch the byte counter (`nettop` / `lsof -r`) before declaring it dead.

### 1.4 What did the step do last, in the global opencode log?

```sh
LOG=~/.local/share/opencode/log/opencode.log
# find the run id for this step (the one whose cwd = this project, active when the step started)
grep -E "timestamp=<YYYY-MM-DD>T<hh:mm>" "$LOG" | grep -oE "run=[a-f0-9]+" | sort | uniq -c | sort -rn
# then inspect that run's last events
grep "run=<RUNID> " "$LOG" | tail -25
```

Interpret the **last event** for the step's run and for each sub-agent session:

- last line is `loop ... step=N` then `exiting loop` → that agent finished cleanly.
- last line is `stream ... modelID=...` with **no** subsequent `tracking`/`loop`/`exiting loop` for that session → **hung mid-stream** (see case study §5).
- last line is `level=ERROR "stream error" ...` → the model call failed explicitly (quota, socket closed, etc.); usually retried then surfaced.

For sub-agents, list all sub-agent sessions and check each has an `exiting loop`:

```sh
RUN=<RUNID>
for s in $(grep "run=$RUN " "$LOG" | grep "mode=subagent" | grep -oE "ses_[A-Za-z0-9]+" | sort -u); do
  ex=$(grep "run=$RUN " "$LOG" | grep "exiting loop" | grep -c "$s")
  last=$(grep "run=$RUN " "$LOG" | grep "$s" | tail -1 | grep -oE "message=[^ ]+")
  echo "$s exiting_loop=$ex last=$last"
done
```

A sub-agent with `exiting_loop=0` and `last=message=stream` is the one that poisoned the step.

### 1.5 What was the step's last action? (read the step log tail)

The single most decisive `_tmp` signal — and the one that needs no global log. Tail the stuck step's log to see exactly where it stopped:

```sh
ls -t _tmp/<run-dir>/oc-<STEP>-*.log | head -1 | xargs tail -40
```

Read the tail for:

- **A list of parallel sub-agents** rendered as `• <task>  Explore Agent` lines, some marked `✓` (done) and one or more **unmarked** → the unmarked one is the sub-agent that never returned. This spots Mode A from `_tmp` alone.
- **A trailing tool/model call** (`→ Read …`, `→ Bash …`) with no following result → blocked inside that call.
- **A final `<AI_STEP_RESULT>…</AI_STEP_RESULT>` marker → the step actually finished cleanly; go to the main log (§2.2) for the transition.
- **Only a header, no body** → the step emitted no agent output at all (Mode E spawn/config failure; read the header for `# cmd:` and `# started:`).

Cross-check with the freeze time: the file's mtime (§1.2) is when this last line was written.

---

## 2. Where the logs live

| Artifact | Path | What it tells you |
|---|---|---|
| Mission run dir | `_tmp/<YYYY-MM-DD-HHMMSS>-mission-driver/` | Everything for one mission invocation |
| Per-step AI log | `<run-dir>/oc-<STEP>-<ts>-<rand>.log` | The `opencode run` stdout/stderr for one step (the watchdog watches this file) |
| Per-tool log | `<run-dir>/oc-<STEP>-...log` (tool steps use the same scheme) | `pnpm`/`turbo` build/test output |
| Mission main log | `<run-dir>/<mission-name>.log` | High-level step transitions, markers, retries ("[step N] STEP (visit #M)") |
| System snapshots | `<run-dir>/sys-snapshot.log` | Periodic process-tree snapshot; keeps appending even while a step is stuck (so its growth does NOT mean the step is progressing) |
| Global opencode log | `~/.local/share/opencode/log/opencode.log` | Per-event trace of every opencode process on the machine (`run=`, `session.id=`, `stream`, `level=ERROR`) |
| opencode state | `~/.local/share/opencode/opencode.db` | Sessions, messages (SQLite; shared across all opencode processes) |

> Note: `sys-snapshot.log` growth is **not** progress. It is written by the runner on a timer regardless of step state. Use the per-step `oc-<STEP>-*.log` mtime to judge progress.

### 2.1 Per-step log `oc-<STEP>-<ts>-<rand>.log` — where the step actually stopped

The stdout/stderr of the one `opencode run` for the step. Read two places:

- **Header (first ~4 lines)**: `# cmd:`, `# cwd:`, `# started: <local>`. Confirms which step/model/command spawned and when. An empty body after the header = no agent output → Mode E (spawn/config failure).
- **Tail (last ~40 lines)**: the agent's rendered text up to the stall (see §1.5 for the tells). The file's mtime = when the step last emitted anything; the runner watchdog resets its deadline on each byte written here (§4).

### 2.2 Mission main log `<mission-name>.log` — step transitions, retries, visit count

High-level flow trace, one line per transition. When a step "looks stuck", check here first to tell a genuine block from a quiet retry:

- `[step N] STEP (visit #M)` → current step and how many times it has been entered this run. A high visit count on one step means the flow is looping (e.g. `DRAFT_PLANS (visit #5)`).
- `marker: pass | fail | issues | all_complete` → the step outcome the transition logic consumes.
- `retry X→Y (n/3)` → the engine is retrying from step X back to Y, attempt n of 3.
- `subflow NAME: forEach item K → …` → progress inside a group/forEach step.
- indented `[child] …` lines → a subflow's own step trace.

### 2.3 `sys-snapshot.log` — NOT a progress signal

Appended on a timer (and at each watchdog heartbeat) regardless of step state, so **its growth does not mean the step is progressing**. Use it instead to:

- confirm the step PID and its children are still in the process tree,
- spot orphaned processes (entries with `"reason":"orphaned…"`),
- correlate RSS/CPU at a timestamp with what the step log shows.

Always prefer `oc-<STEP>-*.log` mtime (§1.2) as the progress signal.

---

## 3. Failure-mode taxonomy

| Mode | Symptom | Root layer | Recovery |
|---|---|---|---|
| **A. Silent sub-agent stream stall** | Step process 0% CPU, `S` state, no socket, step log frozen, one sub-agent session has no `exiting loop` | opencode + model provider (SSE request neither resolves nor rejects) | Wait for runner watchdog (§4) or kill the step PID (§6) |
| **B. Explicit model error** | `level=ERROR "stream error"` in global log (quota / "socket closed unexpectedly" / `AI_RetryError`) | model provider / quota | Usually self-retries; if exhausted, step fails and flow transitions |
| **C. Tool/build step stuck** | A non-AI step (`runTool`) hangs (e.g. a `pnpm`/`turbo` process) | project toolchain / deadlocked build | Same watchdog applies; or kill the tool PID |
| **D. Process-group orphaning** | After a kill, child/agent processes survive detached | kill did not reach the whole group | `src/reap-orphans.mjs` (see §6) |
| **E. Run dir not created / log empty** | `execute()` could not spawn | bad model id, missing `opencode` on PATH, perms | Check `oc-<STEP>-*.log` header + stderr of mission-driver |

Modes A and B share an upstream cause (the model provider) but differ critically: B throws and is handled; A goes silent and is only bounded by the runner's 60-min watchdog.

---

## 4. The runner's safety net (and its limits)

`src/executor.js` has an **activity-based watchdog**, not a wall-clock timeout:

```js
const LIVENESS_CHECK_MS = 5 * 60_000;   // check every 5 min
const BASE_TIMEOUT_MS  = 60 * 60_000;   // 60 min

// every 5 min, while the child is still running:
//   if the step LOG FILE grew since last check -> deadline = now + 60min
//   if now > deadline                        -> [TIMEOUT] killGroup()
```

So: **a step whose log file produces zero new bytes for 60 consecutive minutes is killed** (`SIGTERM` to the process group, then `SIGKILL` after `SIGKILL_DELAY`). After the kill, the flow's transition logic takes over (retry / fail / next step).

This means mission-driver will **eventually self-recover from a silent stall** — but:

1. **The penalty is up to 60 minutes per stuck step.** For a step that wedges in minute 2, that is ~58 wasted minutes.
2. **The signal is indirect.** It proxies "progress" by "stdout bytes". This works today because `opencode run` (non-interactive) stops emitting stdout when blocked. It is a **latent risk**: if opencode ever emits periodic heartbeats / spinner frames / keepalive lines to stdout while internally blocked, the deadline would keep resetting and the watchdog would **never** fire — a true permanent deadlock.
3. **It measures the step log, not the model connection.** A live-but-idle socket with no stdout would still trip it (good), but only after 60 min.

Signal-handling: `src/main.js` installs `SIGINT`/`SIGTERM` handlers that call `runner.close()`, which runs the `SIGTERM → 6s → SIGKILL` tree kill in `src/runner.js` (`killTree`). So Ctrl-C / `kill <RUN_PID>` cleans up the current step's tree.

---

## 5. Case study: silent sub-agent stream stall (Mode A)

This is the dominant real-world stall. Recorded from a `DRAFT_PLANS` step on 2026-06-21.

### Timeline
```
09:51:56  DRAFT_PLANS step spawns (opencode run, PID X)
09:53:41  parent agent spawns 3 explore sub-agents (in-process, agent=explore mode=subagent)
          - ses_...8269  "Audit dialog/drawer surface baseline"
          - ses_...1da50  "Audit form shell renderer baseline"
          - ses_...1dcca  "Audit button renderer baseline"
09:53-54  button sub-agent globs *button*, reads button.tsx (basic + ui), reaches step 2
09:54:12  button sub-agent issues stream glm-5.2   <-- LAST event for this session
09:57:00  dialog/drawer sub-agent: exiting loop    (done)
09:57:46  form sub-agent:        exiting loop      (done)
09:57:46  parent run's own loop: exiting loop      <-- LAST event for the whole step run
09:57:46  step log file stops growing (size frozen)
...       parent process alive, 0% CPU, S state, no TCP socket
~10:57    runner watchdog deadline (09:57 + 60min) expires
~11:00    next 5-min liveness check fires -> [TIMEOUT] killGroup() -> step killed
```

### Why it stalled (root cause)
The button sub-agent's model streaming request (SSE to the model provider) **neither resolved nor rejected**. The connection was silently dropped server-side / by an intermediary, but the client never received an event that would reject the promise. With **no stream idle/abort timeout** configured in opencode for that provider, the promise stays pending forever. The provider in question has a known habit of `Cannot connect to API: The socket connection was closed unexpectedly` (visible as `level=ERROR` on other runs); the silent variant is the worse form of the same flakiness.

### Why the whole step went down with it
The parent step awaits its sub-agents. Two finished; the parent then has nothing left to do but wait for the third. Because the third's model-call promise is pending forever and nothing aborts it, the parent's await never resolves. The process sits at 0% CPU, produces no stdout, and is only terminated by the runner's 60-min watchdog.

This is the canonical **await-without-timeout** failure: one non-rejecting promise defeats an unbounded `await`.

### Evidence that nails it
Global opencode log, filtered to the step's run id:
- 3 sub-agent sessions present; exactly one has `exiting_loop=0` and `last=stream`.
- No `level=ERROR`/`WARN` around the stall time for that run (silent, not thrown).
- Step process: 0% CPU, `S` state, `lsof -i -a -p <PID>` empty (no live socket).

---

## 6. Recovery procedures

### Option 1 — Let the watchdog handle it (do nothing)
If the step log has been frozen, the watchdog will kill it within at most 60 min of the freeze. Lowest risk; highest latency.

### Option 2 — Kill the stuck step and let the flow continue
Kill only the **step's** process group, not the mission-driver node process. The runner's `child.on("close")` will then resolve the step as failed and the flow's transition logic will proceed.

```sh
STEP_PID=<opencode run PID>
kill -TERM -"$STEP_PID"          # negative = process group (Unix). Needs detached group (default).
# if still alive after ~10s:
kill -KILL -"$STEP_PID"
```

Then sweep any survivors (sub-agent processes are in-process, but MCP servers / spawned tools may orphan):

```sh
node tools/mission-driver/src/reap-orphans.mjs <PGID> _tmp/<run-dir>
# or, to clear orphans from a previous crashed run at startup:
node tools/mission-driver/src/reap-orphans.mjs --startup _tmp <RUN_PID>
```

### Option 3 — Stop the whole mission
```sh
RUN_PID=<mission-driver node main.js PID>
kill -TERM "$RUN_PID"            # main.js SIGTERM handler -> runner.close() -> tree kill
```

After any manual kill, confirm with §1.1 that the PID is gone and check `<run-dir>/<mission>.log` for the resulting transition.

---

## 7. Fix directions (prevention)

Ordered by leverage.

1. **Provider-level stream timeout (root fix).** Configure an idle/overall timeout for streaming requests in opencode's provider entry so a silent SSE stream aborts after N seconds instead of pending forever. This kills Mode A at the source and also bounds Mode B retry storms.
2. **Step wall-clock cap in the runner.** Add an optional hard `maxStepMs` per step (engine step def → runner → executor), independent of the activity watchdog, so a stuck step is killed in minutes, not up to 60. The activity watchdog stays as the backstop.
3. **Tighten the activity signal.** Have the runner treat "no new `loop`/`tracking` event in the global log for N min" as stalled, not just "no stdout bytes". This closes the latent risk in §4.2 (stdout heartbeats defeating the watchdog).
4. **Per-subagent timeout / fail-soft.** If opencode exposes it, cap each sub-agent so one hung sub-agent fails instead of poisoning the whole step. At minimum, prefer fewer parallel sub-agents in step prompts when the provider is flaky.
5. **Reduce shared-state contention (secondary).** A multi-GB shared `opencode.db` written by many concurrent opencode processes slows everything and makes diagnosis harder; it is not the cause of Mode A but it amplifies noise.

---

## 8. Diagnostic cheat sheet

```sh
# 1. current step PID + state
pgrep -af "mission-driver/src/main.js"                                     # runner PID
pgrep -P <RUN_PID> | xargs -I{} ps -o pid,etime,%cpu,stat,command -p {}    # step PID + state

# 2. is the step log frozen?
ls -l _tmp/*-mission-driver/oc-*.log | sort -k6,8                          # newest mtime last

# 2b. tail the stuck step -> see the last action / pending sub-agent (the decisive _tmp signal)
ls -t _tmp/*-mission-driver/oc-*.log | head -1 | xargs tail -40

# 2c. current step + visit/retry history in the mission main log (the non-oc-*.log file)
ML=$(ls _tmp/*-mission-driver/*.log | grep -v '/oc-' | head -1); tail -40 "$ML"

# 3. live socket?
lsof -i -a -p <STEP_PID>

# 4. global log: run ids active in a window, then the run's last events
LOG=~/.local/share/opencode/log/opencode.log
grep -E "timestamp=<YYYY-MM-DD>T<hh:mm>" "$LOG" | grep -oE "run=[a-f0-9]+" | sort | uniq -c | sort -rn
grep "run=<RUNID> " "$LOG" | tail -25

# 5. which sub-agent session lacks "exiting loop"?
RUN=<RUNID>
for s in $(grep "run=$RUN " "$LOG" | grep "mode=subagent" | grep -oE "ses_[A-Za-z0-9]+" | sort -u); do
  echo "$s exiting=$(grep "run=$RUN " "$LOG" | grep "exiting loop" | grep -c "$s") last=$(grep "run=$RUN " "$LOG" | grep "$s" | tail -1 | grep -oE "message=[^ ]+)"
done

# 6. recent explicit errors (Mode B)
grep -E "level=(ERROR|WARN)" "$LOG" | tail -20

# 7. recover
kill -TERM -<STEP_PID> ; sleep 10 ; kill -KILL -<STEP_PID> 2>/dev/null
node tools/mission-driver/src/reap-orphans.mjs <PGID> _tmp/<run-dir>
```

---

## 10. Related

- `design/mission-driver-flow-design.md` — top-level flow orchestration (steps, transitions, retries)
- `design/flow-engine-design.md` — engine layer (Step / Transition / StepResult / subflows)
- `src/executor.js` — the activity watchdog (`BASE_TIMEOUT_MS`, `LIVENESS_CHECK_MS`, `killGroup`)
- `src/runner.js` — `realRun` (spawns `opencode run`), `killTree`, `close`
- `src/reap-orphans.mjs` — orphan process cleanup
- `src/sys-snapshot.mjs` — the periodic snapshot writer (note: its output growing does not mean progress)
