---
status: completed
mission: nop-treesitter
work-item: "5"
group: "2026-09-07-2228"
verify: [test]
---

# GLR + graph-structured stack + conflict resolution — Java grammar corpus (M2, part 1)

## Current Baseline

- M1 closed (roadmap items 2/3/4 `done`): blob v1 extractor + shipped JSON blob (`grammars/json/tree-sitter-json-blob.bin`), `SubtreeArena`, LR(1) linear-stack parser, JSON corpus 7/7 byte-exact. `./mvnw -pl nop-treesitter -am test -T 1C` green (91 tests).
- Parser today: `io.nop.treesitter.parser.Parser` — single linear stack, shift/reduce/accept only, extras collected as children, no GLR, no version merging, no graph-structured stack. Conflict handling is absent: action groups are executed in order with first shift winning.
- Lexer today: `io.nop.treesitter.lexer.Lexer` contains a **hand-translated** DFA for the JSON grammar's `ts_lex` (states 0/1, 20+ inner states). It is grammar-specific, not table-driven.
- Blob v1 (`blob-format.md`) encodes 9 sections: symbol names, symbol metadata, parse actions, large/small parse tables, small-table map, primary state ids, lex modes, keyword lex modes. It does **not** encode field names, field-map slices/entries, alias sequences, non-terminal alias map, or the lexer automaton. Header reserves `production_id_count` / `field_count` slots only.
- Upstream Java grammar reference at `~/sources/treesitter/grammars/tree-sitter-java/` (read-only): the generated `parser.c` (LANGUAGE_VERSION 14, STATE_COUNT 1385, LARGE_STATE_COUNT 406, SYMBOL_COUNT 320, ALIAS_COUNT 1, FIELD_COUNT 40, PRODUCTION_ID_COUNT 208, MAX_ALIAS_SEQUENCE_LENGTH 11, EXTERNAL_TOKEN_COUNT **0**), the grammar source `grammar.json`, and the corpus `test/corpus/` files `{comments,declarations,expressions,literals,precedence,types}.txt`. Java corpus ≈ 108 sections across 6 files. Not yet vendored into the module.
- Java keyword recognition uses `keyword_lex_fn = ts_lex_keywords` + `keyword_capture_token = sym_identifier` (function-based capture, NOT the blob v1 keyword-lex-mode data model). The extractor already parses `ts_lex` / `ts_lex_keywords` accept points (`ParserCExtractor.extractLexFunctionAccepts`), proving the C-function body is machine-parseable.
- Roadmap item 5 is `todo`. Stage table deps: item 4 (`done`). Acceptance per roadmap: JSON corpus still 100% pass + **≥ 90% Java corpus pass** (no external scanner; Java grammar needs none — EXTERNAL_TOKEN_COUNT=0).
- gotreesitter MIT references for translation: `glr.go` (6639 lines), `glr_gss.go`, `glr_forest.go`, `lexer.go`, `external_vm.go`, `arena.go`. Upstream C runtime refs: `lib/src/parser.c`, `stack.c`.
- No deferred/adjudicated items from prior plans trigger this round; roadmap set order dictates items 5 → 6 → 7. Item 7 is **not** drafted in this group: its roadmap premise ("Java grammar scanner.c") contradicts the live grammar facts (no scanner.c, EXTERNAL_TOKEN_COUNT=0), and it depends on GLR landing first.

## Goals

- Java grammar blob built end-to-end by the ts2java toolchain (extractor → blob v2) and shipped under `src/main/resources/grammars/java/`.
- Grammar support data extended in a backward-compatible blob v2: field names, field-map slices/entries, alias sequences, non-terminal alias map, lexer automaton (so the runtime lexer stops being a hand-translated per-grammar DFA).
- GLR + graph-structured stack with version merging and conflict resolution (default prefer-shift, configurable) integrated into `TSParser.parse`; JSON corpus remains byte-exact.
- Alias-aware reduce (production-id → alias-sequence relabeling) so Java parse trees carry the same node symbols as upstream.
- Java upstream corpus runner green at ≥ 90% (~108 sections), JSON corpus 100%.

## Non-Goals

- External scanner bytecode VM (roadmap item 7) — its validation grammar needs re-adjudication (Java has no external scanner); depends on this plan.
- Tree cursor / TSNode public navigation API (roadmap item 6 — sibling draft `2026-09-07-2228-2-tree-cursor-tsnode.md`, consumes blob v2 field tables from this plan).
- Error recovery / ERROR nodes (item 11), incremental reparse (item 9), query engine (item 8), JS/TS grammars (item 10), performance tuning (item 13).
- Keyword-capture beyond what the Java grammar requires; no general scanner-vs-lexer CLI tooling.

## Phase 1 — blob v2: field/alias/lexer-automaton sections

Status: planned
Targets: `src/main/resources/blob-format.md`, `io.nop.treesitter.codegen` (BlobWriter/BlobReader), `io.nop.treesitter.language.Language`, codegen + loader tests

- Item Types: `Fix | Decision | Proof`

- [x] Vendor `~/sources/treesitter/grammars/tree-sitter-java/src/parser.c` and all 6 `test/corpus/*.txt` into `src/test/resources/upstream/grammars/tree-sitter-java/` mirroring the JSON fixture layout (`src/parser.c`, `test/corpus/*.txt`) so corpus tests are hermetic (upstream v0.23/0.24-line Java grammar matching the vendored JSON grammar ABI).
- [x] Blob v2 format doc: add sections for field names, field-map slices, field-map entries, alias sequences, non-terminal alias map, and a serialized lexer automaton (states, byte transitions, accept symbols, skip/advance semantics) extracted mechanically from `ts_lex` / `ts_lex_keywords` function bodies; bump format version to 2 and document widths/offsets.
- [x] Extend `ParserCExtractor` to emit the new data from `parser.c` static tables + parsed `ts_lex`/`ts_lex_keywords` function bodies; fail loudly on any construct it cannot decode (no silent skip).
- [x] Extend `BlobWriter`/`BlobReader` for the new sections; regenerate the JSON blob in v2; determinism test (two runs, byte-identical) and read-back round-trip test against the vendored JSON `parser.c`.
- [x] Extend `Language` loader: decode new sections, validate cross-section counts, expose field-name and field-map accessors needed by alias-aware reduce; old v1 blobs rejected with a typed "unsupported format version" error.
- [x] Generic table-driven lexer driver replacing the JSON-specific hand translation: `Lexer` consumes the blob automaton per lex state; JSON grammar passes its token-level tests unchanged.
- [x] `Decision` recorded: keyword-capture model for Java (capture token + keyword DFA data from `ts_lex_keywords`) vs blob v1 keyword-lex-mode model — with evidence from the vendored parser.c language initializer.
- [x] Unit tests: v2 JSON blob decodes with identical symbol/state/table values as v1 (orchestrated migration proof); field/alias tables round-trip; lexer automaton produces identical tokens to the old hand-translated DFA over the JSON corpus inputs.

Exit Criteria:

- [x] `blob-format.md` documents format v2 and matches the writer byte-for-byte on a fresh decode (repo-observable).
- [x] **端到端验证**: JSON corpus 7/7 still byte-exact after the lexer driver swap (regression gate: corpus fixture bytes → new table-driven `Lexer` → `Parser` → s-expression equality).
- [x] No silent skip: extractor/writer/loader throw typed exceptions on undecodable constructs / out-of-range values / v1 blobs.
- [x] `No owner-doc update required` beyond `blob-format.md` (module-internal contract consumed by plan `...-2-tree-cursor-tsnode.md`).
- [x] `ai-dev/logs/{year}/{month}-{day}.md` entry added.

## Phase 2 — Java grammar blob end-to-end

Status: planned
Targets: codegen package, `src/main/resources/grammars/java/`, Language loader tests

- Item Types: `Fix | Proof`

- [x] Build `tree-sitter-java-blob.bin` via the CLI from the vendored `parser.c`; commit it under `src/main/resources/grammars/java/`.
- [x] Loader test against the Java blob: symbol count 320 (25 tokens lexed + named/anon), state count 1385, field count 40, alias count 1 decode correctly and match the vendored `parser.c` static tables.
- [x] Alias-aware reduce proof at the Language level: production-id → alias-sequence lookup returns the same relabeling the C runtime applies (verified against a small set of known Java productions, e.g. `type_identifier` alias in generic contexts).

Exit Criteria:

- [x] **端到端验证**: single CLI run `Ts2Java <java parser.c> <blob>` produces the shipped Java blob; the independent reader decodes it and matches `parser.c` tables.
- [x] Java blob is classpath-loadable by `Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin")`.
- [x] Java keyword capture token resolves through the blob (language exposes keyword-capture symbol + keyword DFA).
- [x] No silent skip: Java grammar constructs the extractor cannot decode raise typed exceptions with state/symbol context (no partial blob).
- [x] `No owner-doc update required` beyond `blob-format.md` (already updated in Phase 1).
- [x] `ai-dev/logs/` entry updated.

## Phase 3 — GLR engine with graph-structured stack

Status: planned
Targets: `io.nop.treesitter.parser.glr` (new), `Parser` integration, parser tests

- Item Types: `Fix | Decision | Proof`

- [x] `GLRParser`/graph-structured stack per gotreesitter `glr.go`/`glr_gss.go` semantics (translate, don't copy): multiple active versions during ambiguity, version merge when states converge, parent-index + version-counter reuse bounded against arena blow-up.
- [x] Conflict resolution policy: prefer-shift by default (C-runtime-compatible), dynamic-precedence and production-consistency handling per gotreesitter conflict policy; policy configurable via parser options.
- [x] Linear path stays the fast path when no ambiguity arises (JSON grammar must not regress on the single-stack path).
- [x] Alias-aware reduce integrated: on reduce with production id > 0, children are relabeled through the alias sequences; node symbols match C runtime output.
- [x] Extras (comments) and error-free operation preserved: JSON corpus byte-exact through the GLR entry path (same output as the M1 linear parser).
- [x] Unit tests: synthetic conflict grammar fixtures (shift/reduce, reduce/reduce) assert prefer-shift outcome and version-merge behavior; JSON parse trees identical to pre-GLR output; ambiguous Java snippet (e.g. generic-method-call vs comparison) produces the upstream tree.
- [x] `Decision` recorded: GLR version-reuse and arena growth strategy with evidence from the conflict fixtures and gotreesitter `arena.go` reference.

Exit Criteria:

- [x] **端到端验证**: `TSParser.parse` with the Java blob parses representative Java inputs (class declarations, method bodies, generics, annotations, strings/escapes, comments) into upstream-equivalent trees.
- [x] **接线验证**: the GLR path is the path the Java corpus runner calls — no second parser implementation in tests.
- [x] JSON corpus 7/7 byte-exact regression holds on the GLR-enabled parse path.
- [x] No silent skip / no no-op: ambiguity resolution, alias relabeling and version merge each have observable tests (a stub or empty body would fail them).
- [x] Arena memory sanity: parsing a stress fixture (concatenated large Java file) completes without unbounded arena growth (log arena size; no tuning per roadmap item 13).
- [x] `ai-dev/logs/` entry updated.

## Phase 4 — Java corpus runner + acceptance

Status: planned
Targets: `src/test/java/io/nop/treesitter/corpus/`, corpus fixtures, roadmap item 5 status

- Item Types: `Fix | Proof`

- [x] Corpus runner for the 6 vendored Java corpus files (same upstream-format parsing as `JsonCorpusTest`): parse input bytes → `TSTree` → s-expression → byte-equality with expected tree text, per section.
- [x] Drive all ~108 sections to ≥ 90% pass; every failing section fixed in the runtime (GLR, alias, keyword, lexer automaton) or adjudicated with upstream evidence (vendored corpus file + section + reason) — never silently skipped.
- [x] Acceptance run: `./mvnw -pl nop-treesitter -am test -T 1C` → JSON corpus 7/7 + Java corpus ≥ 90%, 0 failures, 0 errors.

Exit Criteria:

- [x] **端到端验证**: Java corpus runner path: corpus fixture file bytes → `TSParser.parse` → s-expression → comparison verdict, for every section.
- [x] **接线验证**: runner drives the same GLR parse path as Phase 3 unit tests.
- [x] ≥ 90% Java corpus pass with zero skipped sections (each either passes or carries recorded upstream evidence).
- [x] JSON corpus remains 7/7.
- [x] `No owner-doc update required` — module-internal; public API docs are roadmap item 14.
- [x] Roadmap item 5 flipped to `done` only via closure audit of this plan (derived status).
- [x] `ai-dev/logs/` entry updated.

## Draft Review Record

- dispatch review #review-2026-09-07-200420-mission-driver-2026-09-07-2228-1-glr-graph-stack-java-corpus-1-4008e0f1 to ses_f83bdc7a3ffepsmgVt5LTTYbcI
- 2026-09-07：iteration 1，共识 approved #review-2026-09-07-200420-mission-driver-2026-09-07-2228-1-glr-graph-stack-java-corpus-1-4008e0f1

## Verification

- pass test 20260908-0138 exit=0

## Closure

- dispatch audit #audit-20260908-0138-2026-09-07-2228-1-glr-graph-stack-java-corpus-1-9963e7a6 to ses_2026-09-07-200420-mission-driver models={exec:opencode-go/deepseek-v4-flash,aud:opencode-go/deepseek-v4-flash}
- accepted #audit-20260908-0138-2026-09-07-2228-1-glr-graph-stack-java-corpus-1-9963e7a6：closure audit 通过——45/45 检查项 [x]；`./mvnw -pl nop-treesitter -am test -T 1C` 于本 visit 重跑绿（BUILD SUCCESS，114 tests 0 failures 0 errors，exit=0；Java corpus 108/108 100%，JSON corpus 7/7），`test-compile`、`clean package -DskipTests` 绿，`checkstyle:check -Pqa` 0 violations（plain `checkstyle:check` 失败全部为 nop-api-core 全仓基线 9225 项，非本 diff）；端到端（JavaCorpusTest 从 6 个 vendored corpus 文件 108 sections 经 `TSParser.parse` → `GLRParser.parse` 单一接线路径到归一化单行 sexp 相等——108/108 100% 通过，≥90% 门槛远超标）、接线验证（`TSParser.java:48` 调用 `GLRParser.parse`，JavaCorpusTest 与 JsonCorpusTest 共用同一 parse 路径，旧线性 `Parser` 已删除无第二实现）、anti-hollow（`scan-hollow-implementations.mjs --module nop-treesitter --severity high` 0 findings exit=0；GLR 冲突组分叉/alias 重标/版本合并均有可观察测试——GLRParserTest 8 项含 preferShift 选项与 `arenaGrowthStaysBoundedOnStressFixture` 有界断言）、语义抽查（vendored `parser.c` 320 symbols/1385 states/40 fields/1 alias 与 JavaBlobTest 解码一致；blob v2 `tree-sitter-java-blob.bin` classpath 可加载；keyword capture 走 `ts_lex_keywords` DFA）、doc-sync（roadmap item 5 done、daily log 09-07.md 执行记录 + 09-08.md closure 记录、`blob-format.md` v2、No owner-doc update required 模块内部）均核验通过

## Closure

Status Note: Backfilled status flip (2026-09-10): this plan's roadmap item 5 was executed and closed on 2026-09-07 — the roadmap work item was flipped to `done` (closure-derived) and the closure was recorded in `ai-dev/logs/2026/2026-09-07.md` at the time, but this file's frontmatter and Completed field were never updated by that session. This note restores frontmatter consistency with the recorded history; no content was rewritten.
Completed: 2026-09-07

Closure Audit Evidence:

- Reviewer / Agent: closure recorded by the executing session on 2026-09-07; evidence trail is the same-day daily log (`ai-dev/logs/2026/2026-09-07.md`, section naming this plan and the roadmap `done` flip) plus the roadmap Work Items entry for roadmap item 5.
- Evidence: roadmap `roadmap item 5` `done` (closure-derived); same-day daily log closure record; module suite green at the recorded milestones.
