---
status: completed
mission: nop-treesitter
work-item: "8"
group: "2026-09-08-0234"
verify: [test]
---

# Query engine — S-expression queries + JSON highlights.scm end-to-end (M3, part 1)

## Current Baseline

- Items 2-6 `done`: blob v2, GLR + graph stack, Java corpus 108/108, TSNode/TSTreeCursor with field-name navigation (blob v2 field tables + parse-time production-id retention). `./mvnw -pl nop-treesitter -am test -T 1C` green — **134 tests** (JSON corpus 7/7, Java corpus 108/108).
- `TSTreeCursor` (cursor package) already provides the executor's walking primitives: gotoFirstChild/gotoNextSibling/gotoParent, named-child iteration, extras reachable, `currentFieldName`/field-directed access, transparent container-chain flattening. `TSTree` exposes `rootNode()` / `cursor()`.
- **No query code exists**: no S-expression parser, no `TSQuery` IR, no match executor, no capture machinery. `grep query` over `src/main/java` returns nothing query-related.
- Upstream reference material at `~/sources/treesitter/`:
  - C `lib/src/query.c` (4880 lines) — the authoritative semantics: pattern compilation, capture ordering, match emission, predicate evaluation.
  - gotreesitter `query.go` (1311) + `query_reader.go` / `query_compile.go` / `query_predicates.go` / `query_matcher.go` (MIT, translate don't copy).
  - `grammars/tree-sitter-json/queries/highlights.scm` — the roadmap acceptance target: 6 patterns `(pair key: (_) @string.special.key)`, `(string) @string`, `(number) @number`, `[ (null) (true) (false) ] @constant.builtin`, `(escape_sequence) @escape`, `(comment) @comment`. Requires: named-type patterns, anonymous-node patterns (none in JSON file), **field qualifiers**, **wildcard `(_)`**, **alternation `[ ... ]`**, captures. No predicates, no anchors.
  - `grammars/tree-sitter-java/queries/highlights.scm` (25 patterns: 24 `(`/`[`-starting + 1 `"@"` anonymous; one `.` anchor in the `method_reference` pattern; 5 `#match?` predicates) — recorded as a downstream consumer, **not** in this plan's scope.
  - Vendored test resources carry only `test/corpus/` fixtures — `queries/highlights.scm` is not yet vendored.
- Roadmap item 8 is `todo`; stage deps: item 6 (`done`). Acceptance (Q-04): JSON grammar's `queries/highlights.scm` runs end-to-end. Out of scope per roadmap: `#any-of?` and custom predicates beyond string/regex match.

## Goals

- **Q-01** `TSQuery` S-expression parser: `(type field: (child) @capture)` — named node types, anonymous string literals (`"@"`), wildcard `(_)`, field qualifiers, alternation groups, `@capture` names; strict fail-loud on malformed or unsupported syntax (unknown capture reference, unclosed paren, bad field name).
- **Q-02** `TSQuery` IR: compiled pattern list — per-pattern matcher tree (node type / wildcard / field-qualified child matchers), capture list with dedup semantics per upstream (`query.c`), predicate list (`#eq?` string equality, `#match?` regex) attached to patterns; compile-time validation (unknown node type / field for the language → typed error).
- **Q-03** `TSQueryCursor` executor: walks the tree via `TSTreeCursor`; at each named node tries every pattern; on match emits the match with its capture set (capture → TSNode); predicates evaluated at match time against captured nodes; zero per-match heap churn beyond the result objects.
- **Q-04** End-to-end: vendored JSON `highlights.scm` runs over the JSON corpus fixtures + curated snippets; asserted capture sets are the ground truth for the engine.
- **Predicates**: `#eq?` and `#match?` implemented and covered by focused tests using synthetic queries over JSON parse trees (the JSON highlights file itself has no predicates).
- Successor note: anchors (`.`), quantifiers, `#any-of?`, and capture-precedence extras beyond the JSON file's needs are owned by later grammar-integration work (item 10 / item 14) — recorded, not silently dropped.

## Non-Goals

- Anchors (`.`) / quantifiers (`+` `*` `?`) / `#any-of?` / custom predicates — recorded successor ownership for JS/TS highlight work (item 10) and public API docs (item 14).
- Java `highlights.scm` (25 patterns, uses `.` anchors + `#match?`) — downstream consumer, not in this plan.
- External scanner VM (item 7, sibling plan), incremental reparse (item 9), error recovery (item 11).
- Query performance tuning (item 13); capture-column/byte-range APIs beyond what match emission needs (`TSPoint`/`TSRange` are item 9).
- Editing support for the query syntax (no incremental query recompile).

## Phase 1 — Query S-expression parser

Status: planned
Targets: `io.nop.treesitter.query` (new package), `TSQueryParser`, parser tests

- Item Types: `Fix | Proof`

- [x] `TSQueryParser`: lexer + recursive-descent parser for the query syntax — `( node_type [field:] ( child_pattern )* @capture? )`, anonymous literal patterns (`"@"`), wildcard `(_)`, alternation `[ p1 p2 ... ]` (nested allowed), capture names `@name`; whitespace/comment (`;`) tolerant per upstream query syntax.
- [x] AST shapes: `PatternNode` (type or wildcard or anonymous-literal, children with optional field names, capture name, nested alternation), `Query` root (pattern list); malformed input → `TreeSitterException` with offset + reason (unclosed paren, unexpected token, empty pattern, duplicate/missing capture reference).
- [x] Parser unit tests (≥ 10): type pattern, anonymous literal, wildcard, field qualifier, alternation (incl. nested), capture on pattern and on alternation element, comments/whitespace tolerance, malformed-input failures (each error class covered, no silent acceptance).
- [x] No silent skip: every unsupported syntax construct raises a typed error naming the construct and offset (no ignore-and-continue).

Exit Criteria:

- [x] ≥ 10 parser tests pass; every error class has an observable test.
- [x] `./mvnw -pl nop-treesitter -am test -T 1C` green (module suite intact, JSON/Java corpus unaffected — no runtime touched this phase).
- [x] `No owner-doc update required` — module-internal; public API docs are roadmap item 14.
- [x] `ai-dev/logs/{year}/{month}-{day}.md` entry added.

## Phase 2 — TSQuery compile → IR + validation

Status: planned
Targets: `TSQuery` IR, `TSQueryCompiler`, `Language`-driven validation, compile tests

- Item Types: `Fix | Decision | Proof`

- [x] `TSQuery` IR: pattern array (matcher tree: node-type ids / anonymous symbol ids / wildcard flag, field-id-qualified children, capture slot, alternation expansion), capture name table (dedup per upstream `query.c` semantics — Decision recorded with evidence from `query.c` capture-counting behavior), predicate list per pattern (`#eq?` with capture-ref + string; `#match?` with capture-ref + regex string).
- [x] Compile-time validation against the `Language`: unknown node type name / anonymous string not in the grammar's symbol table → typed error with the name; unknown field name → typed error; regex compile failure for `#match?` → typed error (no silent skip).
- [x] JSON `highlights.scm` vendored to `src/test/resources/upstream/grammars/tree-sitter-json/queries/highlights.scm` and compiles clean against the JSON language (6 patterns → expected matcher/capture structure asserted in a test).
- [x] Compile tests (≥ 8): IR structure for each pattern kind from Phase 1; capture dedup; unknown-type / unknown-field / bad-regex errors; alternation flattening; wildcard under field qualifier.
- [x] `Decision` recorded: capture ordering and duplicate-match semantics (multiple patterns matching the same node, same capture name across patterns) — mirror `query.c` semantics, evidence-cited.

Exit Criteria:

- [x] ≥ 8 compile tests pass; IR assertions are repo-observable (specific matcher shapes named in tests).
- [x] Compiling the vendored JSON highlights.scm succeeds and yields the expected 6-pattern structure.
- [x] No silent skip: all validation error classes are tested (unknown type/field, bad regex, malformed predicate).
- [x] `No owner-doc update required` — module-internal.
- [x] `ai-dev/logs/` entry updated.

## Phase 3 — TSQueryCursor executor + highlights.scm end-to-end + predicates

Status: planned
Targets: `TSQueryCursor`, match/capture runtime, corpus-level query tests, roadmap item 8 status

- Item Types: `Fix | Proof`

- [x] `TSQueryCursor`: pre-order walk of the tree via `TSTreeCursor` (named nodes as match roots); at each node, pattern matching with child-sequence backtracking through field-qualified and positional child matchers; on full match → `TSQueryMatch` (pattern index + capture map `name → TSNode`); extras/unnamed handling per cursor semantics (query roots are named nodes).
- [x] Predicate evaluation at match time: `#eq?` (exact string compare on captured node text) and `#match?` (full-match regex, Java `Pattern`); failed predicate → match suppressed.
- [x] End-to-end: run vendored JSON `highlights.scm` over the 7 JSON corpus fixture inputs + ≥ 5 curated snippets; assert the produced capture sets — expected captures hand-derived from the corpus s-expressions + the JSON highlight annotations (each capture's node type, text and field position verified; `key: (_) @string.special.key` captures only pair-key children, `[null true false]` capture only those literal nodes).
- [x] Predicate focused tests (≥ 6) with synthetic queries over JSON trees: `#eq?` true/false, `#match?` true/false, `#match?` with regex metachars, predicate on capture inside alternation, suppressed-match path.
- [x] Cross-check invariant (Anti-Hollow): every emitted capture node is reachable through the same `TSTreeCursor` walk that `toSExpression` flattens (capture node's type + text + byte range appear in the tree's s-expression at the expected position).
- [x] Roadmap item 8 flipped to `done` only via closure audit of this plan (derived status).

Exit Criteria:

- [x] **端到端验证**: `parse → TSQuery.compile(highlights.scm) → TSQueryCursor` over JSON corpus fixtures + snippets produces the expected capture sets (entry: corpus fixture bytes; exit: asserted captures), JSON corpus 7/7 regression intact.
- [x] **接线验证**: the executor drives the same `TSTree`/`TSTreeCursor` produced by `TSParser.parse` — no parallel tree representation in tests.
- [x] Predicates: `#eq?`/`#match?` each have true/false-path tests; unsupported predicate forms raise at compile time (typed, tested).
- [x] No silent skip: no pattern/capture/predicate path silently degrades to no-op (a stub executor or empty capture set would fail the end-to-end assertions).
- [x] `No owner-doc update required` — module-internal; public API docs are roadmap item 14 (roadmap item 8 status flips at closure via this plan's audit).
- [x] `ai-dev/logs/` entry updated.

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 frontmatter `status` 改为 `completed`。关闭流程详见 `ai-dev/plans/00-plan-authoring-and-execution-guide.md` 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] 所有 in-scope confirmed live defects 已修复（本 plan 无已知 live defect 入口）
- [x] 所有 in-scope confirmed contract drifts 已收敛（无）
- [x] 行为/契约结果已达成：JSON `highlights.scm` 端到端 capture 断言全绿 + JSON corpus 7/7 回归
- [x] 必要 focused verification 已完成（parser ≥ 10 / compile ≥ 8 / predicate ≥ 6 测试 + 接线验证 + 端到端验证）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required（module-internal，公开 API 文档归属 roadmap item 14）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据（写入 `## Closure` 段）
- [x] **Anti-Hollow Check**：closure audit 已验证（a）`TSQueryCursor` 确实被端到端测试从 `TSParser.parse` 产物驱动（非空壳），（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw -pl nop-treesitter -am test -T 1C` 通过（134 基线 + 新增测试全绿）
- [x] checkstyle / 代码规范检查通过（`checkstyle:check -Pqa` 0 violations，按 M1 判定方式）

## Draft Review Record

- dispatch review #review-2026-09-07-200420-mission-driver-2026-09-08-0234-2-query-engine-json-highlights-1-de74b4b5 to ses_f82a38697ffepWsUi3boI5Gyqd
- 2026-09-08：iteration 1，共识 approved #review-2026-09-07-200420-mission-driver-2026-09-08-0234-2-query-engine-json-highlights-1-de74b4b5

## Verification

- 2026-09-08 Phase 1: `./mvnw -pl nop-treesitter -am test -T 1C` BUILD SUCCESS — 243 tests (TSQueryParserTest 25, prior 218; JSON corpus 7/7, Java corpus 108/108 regression).
- 2026-09-08 Phase 2: `./mvnw -pl nop-treesitter -am test -T 1C` BUILD SUCCESS — 258 tests (TSQueryCompileTest 15).
- 2026-09-08 Phase 3: `./mvnw -pl nop-treesitter -am test -T 1C` BUILD SUCCESS — 270 tests (JsonHighlightsQueryTest 3: corpus 7/7 fixtures + 6 curated snippets with hand-derived capture sets + key-only capture; TSQueryPredicateTest 9; JSON corpus 7/7, Java corpus 108/108, JS corpus 33/33 in-scope regressions on the size/padding-enhanced arena path). `scan-hollow-implementations.mjs --module nop-treesitter --severity high` 0 findings; `checkstyle:check -Pqa` 0 violations in nop-treesitter.
- pass test 20260908-0714 exit=0

## Closure

- dispatch audit #audit-20260908-0714-2026-09-08-0234-2-query-engine-json-highlights-1-a2710acb to ses_f81df0fe7ffe7t3BP82TeKf700 models={exec:opencode-go/deepseek-v4-flash,aud:opencode-go/deepseek-v4-flash}
- accepted #audit-20260908-0714-2026-09-08-0234-2-query-engine-json-highlights-1-a2710acb：独立 closure audit 通过——全部 30 项 + Closure Gates 10 项 [x]；`./mvnw -pl nop-treesitter -am test -T 1C` 本 visit 重跑绿（BUILD SUCCESS，270 tests exit=0，含 TSQueryParserTest 25 / TSQueryCompileTest 15 / JsonHighlightsQueryTest 3 / TSQueryPredicateTest 9，JSON corpus 7/7、Java 108/108、JS 33/33 回归）；test-compile + clean package -DskipTests 绿；checkstyle `-Pqa` nop-treesitter 0 violations（plain checkstyle 失败为 nop-api-core 全仓基线 9225 项，非本 diff）；anti-hollow（scan-hollow --severity high 0 findings；TSQueryCursor 由 TSParser.parse 产物经 TSTreeCursor 驱动，端到端 capture 断言 + 可达性 cross-check 成立）；doc-sync（roadmap item 8 done closure-derived、ai-dev/logs/2026/09-08.md 收口）；接线验证：executor 无平行树表示，predicate 真假路径 + 编译期 fail-loud 均有测试

## Closure

Status Note: Backfilled status flip (2026-09-10): this plan's roadmap item 8 was executed and closed on 2026-09-08 — the roadmap work item was flipped to `done` (closure-derived) and the closure was recorded in `../../logs/2026/09-08.md` at the time, but this file's frontmatter and Completed field were never updated by that session. This note restores frontmatter consistency with the recorded history; no content was rewritten.
Completed: 2026-09-08

Closure Audit Evidence:

- Reviewer / Agent: closure recorded by the executing session on 2026-09-08; evidence trail is the same-day daily log (`../../logs/2026/09-08.md`, section naming this plan and the roadmap `done` flip) plus the roadmap Work Items entry for roadmap item 8.
- Evidence: roadmap `roadmap item 8` `done` (closure-derived); same-day daily log closure record; module suite green at the recorded milestones.
