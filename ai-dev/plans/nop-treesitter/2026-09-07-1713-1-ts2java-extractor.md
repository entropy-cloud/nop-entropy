---
status: active
mission: nop-treesitter
work-item: "2"
group: "2026-09-07-1713"
verify: [test]
---

# ts2java parse-table extractor + JSON grammar blob

## Current Baseline

- `nop-treesitter/` scaffolded: `pom.xml` (JDK 17, `nop-commons`, slf4j, junit-jupiter), registered in root `pom.xml` `<modules>`; `TreeSitterBootstrap` + `TreeSitterBootstrapTest` (2 tests) green on `./mvnw -pl nop-treesitter -am test -T 1C` (commit `0d5971af6a`).
- No codegen tooling exists: no `io.nop.treesitter.codegen` package, no binary grammar blob, no blob-format doc, no `src/main/resources/grammars/` tree.
- Read-only reference sources present: `~/sources/treesitter/grammars/tree-sitter-json/` (`src/parser.c`, `grammar.json`, `node-types.json`, `test/corpus/main.txt`); gotreesitter extraction references `~/sources/treesitter/gotreesitter/cmd/ts2go/extract.go`, `~/sources/treesitter/gotreesitter/grammargen/codegen_c.go` (parser.c static-table extraction), `grammargen/encode.go` (blob encoding) — MIT, translate don't copy, never modify.
- Roadmap item 2 is `todo`; the stages table names this plan as its owner.
- Sibling plans in this group: `2026-09-07-1713-2-subtree-arena.md` (independent, parallel) and `2026-09-07-1713-3-lr1-parser-json-corpus.md` (depends on this plan's blob + format doc).

## Goals

- `Ts2Java` CLI (invoked via `mvn exec:java` or a generated `ParserMain.main`) that reads an upstream `parser.c` and emits a compact binary blob containing: named + anonymous symbol table, symbol metadata, state table, parse actions, lex modes, keyword lex modes.
- Extraction correctness proven against the JSON grammar's `parser.c` static tables — counts and names, not just "runs without error".
- Blob format documented authoritatively in `nop-treesitter/src/main/resources/blob-format.md` (field order, widths, offsets, version stamp) — this doc is the loader contract consumed by plan `...-3-lr1-parser-json-corpus`.
- Pre-built `tree-sitter-json-blob.bin` shipped at `src/main/resources/grammars/json/`, produced by the CLI itself (end-to-end run), verified by an independent read-back round-trip test.

## Non-Goals

- No `Language` loader / runtime parsing classes (roadmap item 4 / PARSE-01 — sibling plan `...-3-lr1-parser-json-corpus`).
- No external scanner bytecode extraction (roadmap item 7).
- No `grammar.json`-based generation path — `parser.c` is the only extraction source for now.
- No Java / JS / TS grammar extraction (roadmap items 5 and 10).

## Phase 1 — parser.c structural extraction core

Targets: `nop-treesitter/src/main/java/io/nop/treesitter/codegen/`, `src/test/java/io/nop/treesitter/codegen/`, `src/test/resources/upstream/grammars/tree-sitter-json/`

- Item Types: `Fix | Proof`

- [x] Vendor `~/sources/treesitter/grammars/tree-sitter-json/src/parser.c` into `nop-treesitter/src/test/resources/upstream/grammars/tree-sitter-json/` so extraction tests are hermetic (upstream JSON grammar, Tree-sitter v0.25.x line).
- [x] `Ts2Java` CLI skeleton: `main(String[] args)` accepting `<parser.c> <out-blob>`, with usage error on missing/extra args.
- [x] Extract symbol table: `_ts_symbol_names[]` (name per symbol id) and `_ts_symbol_metadata[]` (named / visible flags) into Java structures, following the gotreesitter `codegen_c.go` approach (translate, don't copy).
- [x] Extract parse actions from `_ts_parse_actions[]` (shift / reduce / accept / error with payloads).
- [x] Extract state table: `_ts_parse_table[]` / `_ts_small_parse_table[]` rows per state plus `_ts_primary_state_ids[]`.
- [x] Fail loudly (`IllegalStateException` carrying symbol/state context) on any unsupported C construct or table encoding instead of skipping — no silent no-op extraction (Minimum Rules #24).
- [x] Unit tests: extracted symbol count and every symbol name match the vendored `parser.c` `_ts_symbol_names`; parse-action decode round-trip; `_ts_primary_state_ids[]` length equals state count.

Exit Criteria:

- [x] `Ts2Java` class exists under `io.nop.treesitter.codegen` and its `main` runs with `<parser.c> <out>` args.
- [x] Tests are repo-observable: they read the vendored `parser.c` and assert exact counts / names (symbol list, state-table dimensions) against the C static tables.
- [x] No silent skip: decoding a table row the tool cannot handle raises a typed exception with context — proven by a synthetic malformed-table test case.
- [x] `No owner-doc update required` — extraction is module-internal; no platform convention changes. `blob-format.md` lands in Phase 3.
- [x] `ai-dev/logs/{year}/{month}-{day}.md` entry added (reverse-chronological top insert).

## Phase 2 — lex modes and keyword lex modes

Targets: same codegen package + tests

- Item Types: `Fix | Proof`

- [x] Extract lex states: `_ts_lex_modes[]` (per-parse-state lex table) and any `_ts_lex_state_transitions[]` present in the JSON grammar's `parser.c`, with lex action decoding (accept with symbol id).
- [x] Extract keyword lex modes: `_ts_keyword_lex_modes[]` and their transitions (used by the lexer for keyword-vs-identifier resolution).
- [x] Complete coverage check: every static table referenced by the `parser.c` language initializer is either extracted or diagnosed — an unreferenced/unhandled table raises `IllegalStateException` naming the table.
- [x] Unit tests: lex-mode count equals parse-state count; keyword lex mode present; lex action decode round-trip against the vendored `parser.c`.

Exit Criteria:

- [x] The extractor covers every `TSLanguage` field the JSON grammar's `parser.c` initializes; unhandled fields raise a typed exception naming the field (verified by scanning the vendored `parser.c` language initializer in a test).
- [x] Unit tests pass (counts + round-trip vs vendored `parser.c`).
- [x] No silent skip: unhandled lex-table encodings throw instead of being dropped.
- [x] `No owner-doc update required` — module-internal extraction; platform docs unchanged.
- [x] `ai-dev/logs/` entry updated.

## Phase 3 — binary blob encoding, format doc, shipped JSON blob

Targets: `nop-treesitter/src/main/resources/blob-format.md`, `src/main/resources/grammars/json/`, codegen package

- Item Types: `Fix | Decision | Proof`

- [x] Binary writer: compact blob layout (magic + language-version stamp, symbol table, metadata, state table, parse actions, lex modes, keyword lex modes) written via `java.nio.ByteBuffer`, big-endian, widths justified by data range.
- [x] `Decision` recorded in the format doc: int-width choice per section with range rationale (e.g. symbol ids ≤ 255 → 1 byte; state ids ≤ 65535 → 2 bytes).
- [x] Author the format contract in `nop-treesitter/src/main/resources/blob-format.md` (field order, widths, offsets, version stamp) — the loader contract for plan `...-3-lr1-parser-json-corpus`.
- [x] End-to-end CLI run: `Ts2Java <vendored parser.c> <blob>` produces `tree-sitter-json-blob.bin` at `src/main/resources/grammars/json/`; commit the binary.
- [x] Read-back round-trip test: an independent minimal reader (not the writer) decodes the shipped blob and asserts symbol names / counts, state count, lex-mode count equal the vendored `parser.c` static tables.
- [x] Determinism test: regenerating the blob from the same `parser.c` yields byte-identical output.

Exit Criteria:

- [x] **端到端验证**: a single CLI invocation `Ts2Java <vendored parser.c> <out-blob>` produces the shipped blob; the round-trip test decodes it and matches the `parser.c` tables (entry: CLI; exit: decoded tables).
- [x] **接线验证**: the shipped blob is loadable from the classpath (`src/main/resources/grammars/json/tree-sitter-json-blob.bin`) by the independent reader — the same resource the Phase-1 loader of plan `...-3-lr1-parser-json-corpus` will consume; proven by test.
- [x] **无静默跳过**: the writer throws on values outside the declared width range instead of truncating.
- [x] `blob-format.md` exists and matches the actual writer output byte-for-byte on a fresh decode (doc is repo-observable).
- [x] Determinism test passes (two runs, identical bytes).
- [x] `ai-dev/logs/` entry updated.

## Draft Review Record

- dispatch review #review-2026-09-07-171303-mission-driver-2026-09-07-1713-1-ts2java-extractor-1-e39d1e10 to ses_f84d0f6b8ffe2I8Xl0Xjy66JU8
- 2026-09-07：iteration 1，共识 approved #review-2026-09-07-171303-mission-driver-2026-09-07-1713-1-ts2java-extractor-1-e39d1e10

## Verification

- pass test 20260907-2015 exit=0
- pass test 20260907-2257 exit=0

## Closure

- dispatch audit #audit-20260907-2015-2026-09-07-1713-1-ts2java-extractor-1-d177ee6b to ses_2026-09-07-200420-mission-driver models={exec:opencode-go/deepseek-v4-flash,aud:opencode-go/deepseek-v4-flash}
- accepted #audit-20260907-2015-2026-09-07-1713-1-ts2java-extractor-1-d177ee6b：closure audit 通过——全部检查项 [x]；`./mvnw -pl nop-treesitter -am test -T 1C` 绿（BUILD SUCCESS，10+10+7+2 tests，exit=0）；端到端（shipped blob 与 fresh CLI 输出 byte-identical、classpath 可读）、anti-hollow（scan-hollow exit 0；writer/extractor 越界即抛）、doc-sync（blob-format.md、roadmap item 2 done、daily log 收口）均核验通过
- dispatch audit #audit-20260907-2257-2026-09-07-1713-1-ts2java-extractor-2-9f1f6d7b to ses_2026-09-07-200420-mission-driver models={exec:opencode-go/deepseek-v4-flash,aud:opencode-go/deepseek-v4-flash}
- accepted #audit-20260907-2257-2026-09-07-1713-1-ts2java-extractor-2-9f1f6d7b：独立 closure audit 复核通过——修复 pass line 前导空格（原 `missing-pass:test` 根因）后 plan-check 派生 completed；`./mvnw -pl nop-treesitter -am test -T 1C` 绿（BUILD SUCCESS，nop-treesitter 91 tests exit=0，含 codegen 10+7+10 tests）；test-compile/clean package exit=0、lint not configured（mission 默认）；anti-hollow（scan-hollow exit 0、无 return null/TODO）、doc-sync（blob-format.md、roadmap item 2 done、daily log 收口）核验通过