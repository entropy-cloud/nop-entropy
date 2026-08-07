# nop-stream Independent Audit — Source Manifest (Frozen)

> Status: frozen
> Frozen at: HEAD 2026-08-07
> Owner: nop-stream-independent-audit mission (Stage 4)
> Validator: `ai-dev/tools/check-nop-stream-audit-manifest.mjs manifest`
> Rule: Each domain registers a **reproducible selection command** + **expected denominator**. The validator executes every `command:` at PROJECT_ROOT, parses the integer result, and rejects any mismatch with `expected_denominator:`. Pure path lists without a live selection command are not allowed.

## Live Layout (fact baseline)

`nop-stream/` reactor declares **10** submodules (verified against `nop-stream/pom.xml` `<module>` entries at HEAD):

`nop-stream-core`, `nop-stream-runtime`, `nop-stream-flow`, `nop-stream-cep`, `nop-stream-rocksdb`, `nop-stream-connector`, `nop-stream-connector-batch`, `nop-stream-connector-jdbc`, `nop-stream-connector-debezium`, `nop-stream-fraud-example`.

> **Doc drift (recorded, not fixed here):** `docs-for-ai/01-repo-map/module-groups.md` does not list the live 10-module set. Convergence is owned by a separate owner-doc plan (see plan `Non-Blocking Follow-ups`). This manifest records the live 10-module layout as the authoritative fact baseline.

## Include / Exclude Rules (authoritative text — not oral convention)

- **Generated sources (`_`-prefix)**: any file whose basename starts with `_` (`_*.java`, `_*.xml`, `_*.xmeta`) or that lives under a `_gen/` directory is **generated** and is **excluded** from the public capability surface. It is treated as a build artifact, not a hand-authored contract. These may be audited separately as "generated content" but never counted toward hand-authored capability denominators.
- **`@Internal` SPI**: types/methods annotated `@Internal` are **counted** in the denominator (they are part of the reachable public surface and carry real contracts), but each evidence row touching an `@Internal` symbol must set `required-lane` accordingly. They are NOT excluded; the `@Internal` marker is recorded as a per-symbol attribute, not a blanket exclusion.
- **Example module (`nop-stream-fraud-example`)**: **excluded from production-capability denominators** but **included in the fail-fast / semantic surface**. It is registered as its own manifest entry (domain `f`) so its capability is bounded and observable, and so a finding in the example cannot inflate or deflate a production denominator.
- **Target directories** (`target/`, build output): always excluded; only `src/` paths count.
- **Selection commands** must run from PROJECT_ROOT (the repo root containing `nop-stream/`) and emit a single integer on stdout (typically via `wc -l`). The validator compares that integer to `expected_denominator`.

## Domain Entries

Each `@@ENTRY ... @@END` block is a machine-parsable record consumed by the validator. `command:` is executed verbatim via `sh -c` at PROJECT_ROOT.

### Domain (a) — Java public types (production)

@@ENTRY
domain_id: java-public-types-production
scope: Public Java type declarations (class/interface/enum) across the 9 production reactor modules' src/main/java (excludes fraud-example)
command: rg -l '^public (abstract )?(final )?(class|interface|enum) ' nop-stream --glob 'nop-stream/nop-stream-*/src/main/java/**/*.java' --glob '!**/_*.java' --glob '!nop-stream/nop-stream-fraud-example/**' | wc -l
expected_denominator: 527
include: public class/interface/enum declarations in src/main/java of the 9 production modules
exclude: _-prefixed generated sources; fraud-example module; non-public types; target/ build output
notes: denominator measured at HEAD 2026-08-07
@@END

@@ENTRY
domain_id: java-public-types-example
scope: Public Java declarations in the fraud-example module (example capability surface)
command: rg -l '^public ' nop-stream/nop-stream-fraud-example/src/main/java | wc -l
expected_denominator: 10
include: public declarations in nop-stream-fraud-example/src/main/java
exclude: production modules; generated sources
notes: example module is bounded separately so it cannot inflate production denominators
@@END

@@ENTRY
domain_id: internal-spi-markers
scope: @Internal annotation usages across the production stream main sources (reachable-but-internal SPI surface)
command: rg --no-heading '@Internal' nop-stream --glob 'nop-stream/nop-stream-*/src/main/java/**/*.java' --glob '!nop-stream/nop-stream-fraud-example/**' | wc -l
expected_denominator: 121
include: every @Internal annotation occurrence in production src/main/java
exclude: fraud-example; test sources; generated sources
notes: @Internal symbols are COUNTED in the public surface (not excluded); this denominator bounds the internal-SPI subset
@@END

### Domain (b) — XDSL node surface (stream.xdef)

@@ENTRY
domain_id: stream-xdef-tag-occurrences
scope: XDEF schema declaration tags in the stream XDSL entry schema (stream.xdef) — bounds the declarative node/attribute surface
command: rg -o 'xdef:[a-z-]+' nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef | wc -l
expected_denominator: 92
include: every xdef:* tag occurrence (xdef:name, xdef:ref, xdef:body-type, xdef:key-attr, xdef:unique-attr, xdef:define, xdef:bean-*, etc.) in stream.xdef
exclude: none (single schema file); the root schema declaration attribute is not counted as a body node
notes: StreamModel Java classes live in nop-stream-flow/src/main/java/io/nop/stream/flow/model/ (orthogonal Java binding, covered by domain a)
@@END

### Domain (c) — Delta surface (stream.xml overlay)

@@ENTRY
domain_id: stream-xml-overlays-src
scope: *.stream.xml model files under src/ (the user-facing Delta/declarative surface), excluding build output
command: find nop-stream -path '*/src/*' -name '*.stream.xml' | wc -l
expected_denominator: 12
include: all *.stream.xml files under any */src/* path (main demo + test fixtures + _delta overlays)
exclude: target/ build output (compiled copies under target/classes and target/test-classes)
notes: 1 production demo + 11 test fixtures (smoke/delta/reduce variants); Delta semantics are audited as the same supported-surface as the Java entry path
@@END

### Domain (d) — beans / SPI wiring

@@ENTRY
domain_id: beans-xml-files
scope: beans.xml wiring files under stream runtime _vfs bean stores
command: find nop-stream -path '*/src/main/resources/_vfs/*/beans/*.beans.xml' | wc -l
expected_denominator: 2
include: stream-control-rpc.beans.xml, stream-data-plane.beans.xml under nop-stream-runtime/src/main/resources/_vfs/nop/stream/beans/
exclude: target/ output; non-beans wiring files
notes: NO _module marker present under _vfs/nop/stream/ (tracked as M8-2-P1-11); ioc:default beans may be skipped by global discovery
@@END

@@ENTRY
domain_id: ioc-default-bean-declarations
scope: ioc:default bean declarations across stream beans.xml wiring files (the implicit-override bean surface)
command: rg --no-heading 'ioc:default' nop-stream --glob '**/_vfs/**/beans/*.beans.xml' | wc -l
expected_denominator: 5
include: every ioc:default bean occurrence (streamMessageService->LocalMessageService, streamDataPlaneWireCodec->IdentityWireCodec, and the control-rpc beans)
exclude: target/ output
notes: bounds the runtime data-plane + control-rpc bean surface
@@END

### Domain (e) — Connector factory / config

@@ENTRY
domain_id: connector-main-java-files
scope: Java sources across the 4 connector reactor modules (connector, connector-batch, connector-jdbc, connector-debezium) src/main/java — bounds the connector factory/config surface
command: find nop-stream/nop-stream-connector/src/main/java nop-stream/nop-stream-connector-batch/src/main/java nop-stream/nop-stream-connector-jdbc/src/main/java nop-stream/nop-stream-connector-debezium/src/main/java -name '*.java' | wc -l
expected_denominator: 16
include: all *.java in the 4 connector modules' src/main/java
exclude: core/runtime/flow/cep/rocksdb modules; fraud-example; target/ output; generated _-prefixed sources
notes: key SPI/factory types — MessageSourceFunction/MessageSinkFunction, FileSplitEnumerator/FileSourceReader, BatchLoaderSourceFunction/BatchConsumerSinkFunction, JdbcTwoPhaseCommitSinkBuilder/JdbcTwoPhaseCommitSink, DebeziumCdcSourceFunction
@@END

### Domain (f) — Examples

@@ENTRY
domain_id: example-stream-xml
scope: Production example *.stream.xml model (the declarative example surface shipped to users)
command: find nop-stream/nop-stream-fraud-example/src -name '*.stream.xml' | wc -l
expected_denominator: 1
include: fraud-detection.stream.xml under nop-stream-fraud-example/src
exclude: test fixtures (covered by domain g); target/ output
notes: example is bounded separately from production capability
@@END

### Domain (g) — Test lane

Tests are classified into three lanes. Each lane gets its own bounded denominator so that a later capability claim cannot silently upgrade a unit-only result into an e2e claim.

@@ENTRY
domain_id: test-java-files-all
scope: All Java test sources across the 10 stream modules src/test (the full test corpus — classified into lanes below by separate commands)
command: find nop-stream -path '*/src/test/*' -name '*.java' | wc -l
expected_denominator: 448
include: every *.java under any */src/test/* path across all 10 modules
exclude: target/ output; main sources
notes: total test corpus; lane breakdowns below are subsets of this total
@@END

@@ENTRY
domain_id: test-resource-fixtures
scope: Non-Java test resource fixtures (model files, expected outputs, test configs) under src/test/resources
command: find nop-stream -path '*/src/test/resources/*' -type f | wc -l
expected_denominator: 13
include: every regular file under any */src/test/resources/* path
exclude: target/ output; Java test sources (covered by test-java-files-all)
notes: bounds the fixture evidence surface used by in-process integration tests
@@END

@@ENTRY
domain_id: test-lane-multi-jvm-fixtures
scope: Multi-JVM / cluster test fixtures — the `multijvm` test package (MiniStreamCluster + cross-JVM recovery/failover harness). These define the "multi-JVM" evidence lane.
command: find nop-stream -path '*/src/test/*' -path '*multijvm*' -name '*.java' | wc -l
expected_denominator: 4
include: test Java files under any */src/test/* path in the `multijvm` package (MiniStreamCluster, TestMiniStreamClusterProcessSpawn, TestMultiJvmCoordinatorFailover, TestMultiJvmExactlyOnceRecovery)
exclude: unit tests; in-process tests; target/ output
notes: multi-JVM lane is the strongest evidence class for control-plane + data-plane cross-JVM claims; 4 fixtures at HEAD 2026-08-07
@@END

---

## Lane Classification (semantic, used by evidence-schema `required-lane`)

| Lane | Meaning | Typical evidence strength |
| --- | --- | --- |
| `unit` | Single-class / single-component JUnit test, no real operator chain | weakest — cannot prove wiring or end-to-end semantics |
| `in-process` | Runs the full pipeline in one JVM (env.execute() -> sink), real operators, no network split | medium — proves wiring + data-plane semantics, NOT cross-JVM control-plane |
| `multi-jvm` | Spins up >1 JVM/process (MiniStreamCluster, real RPC, real fencing) | strongest — only lane that can prove control-plane / HA / cross-JVM recovery claims |

A capability claim may only set `required-lane: multi-jvm` when at least one evidence row in that lane covers it. The manifest's `test-lane-multi-jvm-fixtures` denominator bounds how many such fixtures exist to be drawn upon.
