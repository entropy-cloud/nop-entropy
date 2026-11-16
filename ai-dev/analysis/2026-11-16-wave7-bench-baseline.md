# Wave 7 Phase 1 — nop-ib E2E Bench Baseline

- Date: 2026-11-16
- Status: open
- Harness: `nop-tools/nop-ib/tools/bench-e2e.sh` (IB_HOME=/Users/abc/app/nop-entropy-wt/nop-entropy-ib)
- Repeats: 2 warm passes per variant (cold = first pass of each variant), 1 module per roadmap project
- Machine data: `<project>/_tmp/bench/<module>.json` + per-pass logs `<project>/_tmp/bench/<label>.log`

## Harness fix (recorded)

The harness as checked in had a field-count bug: `run_timed` echoed 3 fields
(`t0 t1 rc`) but the callers read 4 (`_ _ ms rc`), so wall-clock was discarded
(all rows printed `ms:0, exit:`). Fixed in-place (duration computed in
`run_timed`, `read -r ms rc`) — measurement tooling only, no product code touched.
Also required: harness must be invoked from the external project root (reactor
resolution for `-pl :module`).

## Results

Times are wall-clock seconds; cold = first pass of the variant, warm = median of repeats.

| project | module | variant | cold (s) | warm median (s) |
|---|---|---|---|---|
| zeebe | zeebe-gateway-grpc | mvn | 29.1 | 28.1 |
| zeebe | zeebe-gateway-grpc | ib | 30.5 | 31.1 |
| zeebe | zeebe-gateway-grpc | ib-maven | 27.7 | 28.9 |
| flink-agents | flink-agents-integrations-embedding-models-ollama | mvn | 2.2 | 1.7 |
| flink-agents | ...-ollama | ib | 0.3 | 0.3 |
| flink-agents | ...-ollama | ib-maven | 1.8 | 1.8 |
| jsr352 | jberet-se | mvn | 14.3 | 14.6 |
| jsr352 | jberet-se | ib | 17.4 | 9.2 |
| jsr352 | jberet-se | ib-maven | 14.8 | 14.8 |
| shardingsphere | shardingsphere-infra-context | mvn | 3.4 | 3.1 |
| shardingsphere | shardingsphere-infra-context | ib | 1.1 | 1.1 |
| shardingsphere | shardingsphere-infra-context | ib-maven | 3.0 | 3.0 |
| spring-batch | spring-batch-core | mvn | 268.8 | 270.1 |
| spring-batch | spring-batch-core | ib | 788.2 | 691.1 |
| spring-batch | spring-batch-core | ib-maven | 269.1 | 269.5 |

### Warm speedup (ib vs mvn)

| project / module | mvn warm (s) | ib warm (s) | speedup |
|---|---|---|---|
| zeebe / zeebe-gateway-grpc | 28.1 | 31.1 | 0.90x (ib slower) |
| flink-agents / ...ollama | 1.7 | 0.3 | 5.5x |
| jsr352 / jberet-se | 14.6 | 9.2 | 1.6x |
| shardingsphere / infra-context | 3.1 | 1.1 | 2.8x |
| spring-batch / spring-batch-core | 270.1 | 691.1 | 0.39x (ib 2.6x slower) |

## Notes / caveats

- No TIMEOUTs; no harness-level failures. All zeebe / flink-agents / jsr352 / shardingsphere passes exit 0.
- spring-batch-core: all ib / ib-maven passes exit 1, but both sides have the **same single preexisting red**: `MongoDefaultBatchConfigurationTests` (1 error, ~241s on both mvn and ib-maven; ib: failed=1). mvn variant exits 0 only because the harness passes `-Dmaven.test.failure.ignore=true`; exit codes are therefore not cross-variant comparable. ib differential rerun classifies it "genuine failures confirmed". ib warnings (watch-only): `${surefireArgLine}` placeholder dropped; 5 abstract `Abstract*Tests` helper classes unresolvable at dispatch (0-test IB-ONLY helpers).
- zeebe-gateway-grpc: ib junit-fork path is *not* faster than mvn at this module size (~30s both) — compile+deps setup dominates; ib-maven ≈ mvn as expected (surefire delegation).
- spring-batch-core: ib junit-fork is **2.6x slower** than mvn (691s vs 270s warm). Likely cause: single-fork execution of a 906-test module plus per-failure differential rerun (cold log shows rerun gate re-executing a fresh full-module fork with cap=1800, freshExit=2), vs surefire's multi-fork parallelism. Watchdog cap not hit (max pass 794s < 1800s).
- jsr352 jberet-se: ib cold pass 17.4s > warm 9.2s (first-pass JVM/classpath warmup); warm steady-state is 1.6x over mvn.
- The last spring-batch ib-maven pass (pass 2) was run manually after the background harness process was killed externally (exit 137) between passes; timing measured with the same command line as the harness (269983 ms, exit 1).
- Honesty note (per harness header): this measures wall clock only; correctness/parity is judged by the parity runbook (6B-3 matrix, 2026-11-16), not derivable from timing data.
- Model freshness verified before runs: no `*.java` newer than each project's `.nop-ib/model.json`, so no `model refresh` was needed.
