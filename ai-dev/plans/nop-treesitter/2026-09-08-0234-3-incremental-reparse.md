---
status: active
mission: nop-treesitter
work-item: "9"
group: "2026-09-08-0234"
verify: [test]
---

# Incremental reparse + getChangedRanges + edit APIs (M3, part 2)

## Current Baseline

- Items 2-6 `done`: blob v2, GLR + graph stack, Java corpus 108/108, cursor + field navigation. `./mvnw -pl nop-treesitter -am test -T 1C` green — **134 tests** (JSON corpus 7/7, Java corpus 108/108).
- `TSParser.parse` is one-shot only: fresh `SubtreeArena` → `GLRParser.parse` → `TSTree.snapshot` (`TSParser.java:48`). No incremental entry point, no edit application, no tree versioning.
- `SubtreeArena` is int-array backed with free-list slot reuse and parent tracking; `Subtree` is an int-field record. **No per-subtree version stamp exists** — nothing distinguishes a subtree's parse epoch, which both subtree reuse and `getChangedRanges` need.
- No `TSPoint` / `TSRange` / `TSInputEdit` value types exist anywhere in the module (roadmap IR-01 all absent).
- `TSTree` is immutable (snapshot arena + root id + `toSExpression()`); no source-bytes retention needed by the plan's algorithms (edit application receives the new source bytes explicitly).
- Upstream reference material at `~/sources/treesitter/`:
  - C `lib/src/get_changed_ranges.c` (557 lines) — changed-range algorithm: parallel pre-order walk of old/new trees, subtree comparison, range merging; `lib/src/parser.c` incremental machinery (`reusable_node.h`, version-tagged subtree reuse, `ts_subtree_edit` byte/point adjustment); `lib/src/tree.c` (`ts_tree_edit` edit application).
  - gotreesitter `tree.go` (MIT, translate don't copy) — edit + changed-ranges implementation.
- Roadmap item 9 is `todo`; stage deps: item 5 (`done`). Deliverables IR-01..04 (value types, `parseIncremental`, `getChangedRanges`, 8 tests: insert / delete / replace / multi-edit / no-op / nested edits).

## Goals

- **IR-01** value types: `TSPoint` (row/column), `TSRange` (start/end point + byte), `TSInputEdit` (startByte/oldEndByte/newEndByte + point triple) — records, immutable.
- **IR-02** `parseIncremental(language, oldTree, edits, newSourceBytes)`: applies the edit list, reuses every subtree outside the affected region, re-parses the affected region, and produces a `TSTree` that is **byte-for-byte equal to a fresh full parse** of the new source — the correctness invariant.
- **IR-03** `getChangedRanges(oldTree, newTree) → List<TSRange>`: parallel walk comparing subtree content; emitted ranges cover exactly the regions whose tree content differs between the two trees (merge-adjacent semantics per upstream).
- **IR-04** 8 focused tests (insert / delete / replace / multi-edit / no-op / nested / point coordinates / file-boundary edits), each asserting: (a) new tree == full reparse, (b) reuse actually happened (observable count > 0 for non-trivial edits), (c) changed ranges == the edited region.
- Reuse is observable: the incremental result exposes a reused-subtree count (test-facing stat) so reuse is proven, not assumed (Anti-Hollow).
- Arena change: per-subtree version stamp (`SubtreeArena` gains a parallel version int array) — the mechanism both reuse and `getChangedRanges` rely on, mirroring the C runtime's version-tagged subtrees.

## Non-Goals

- External scanner payload serialize/deserialize persistence across incremental parses (item 7 lands the single-parse VM; scanner state persistence is a follow-on for item 10's stateful scanners — recorded successor ownership, not silently skipped).
- Full C-runtime splice fidelity (reusable stack splicing, error-recovery subtree resumption, `ts_subtree_edit` point-adjustment corner parity) beyond what the equality-with-full-reparse invariant requires — any semantic gap is caught by the invariant tests.
- Error recovery (item 11), query engine (item 8), performance tuning of the incremental path (item 13).
- Multibyte-locale point arithmetic beyond UTF-8 byte/point conversion (UTF-8 is the module's only encoding).

## Phase 1 — Edit value types + arena version stamps

Status: completed
Targets: `io.nop.treesitter.parser.incremental` (new package), value-type tests

- Item Types: `Fix | Proof | Decision`

- [x] `TSPoint` (row/column), `TSRange` (start/end points + bytes), `TSInputEdit` (startByte, oldEndByte, newEndByte, startPoint, oldEndPoint, newEndPoint) — immutable records with null/negative-input validation (typed errors).
- [x] Byte↔point conversion utility for UTF-8 (`TSPoint.fromByteOffset(source, offset)`-style; row/column semantics per upstream `point.h` — columns count UTF-8 bytes, not codepoints).
- [x] `Decision` (recorded 2026-09-09 during execution): the planned `SubtreeArena` parallel version-int column is **adjudicated out and replaced by edit-intersection reuse gating**. Rationale: the C runtime's per-subtree `has_changes`/version bits exist because C *mutates* the old tree at `ts_tree_edit` time and later gates reuse on those bits; this runtime keeps `TSTree` immutable and gates reuse by "candidate subtree does not intersect any `TSInputEdit` old range", which is the same predicate derivable from the edit list the caller supplies anyway. A version column in this design would be write-only — exactly the hollow mechanism Minimum Rules #24 forbids. Consequence: the same `oldTree` can be reused for multiple independent `parseIncremental` calls (C's mutation is one-shot).
- [x] Unit tests (≥ 8): value-type validation, byte↔point conversion (incl. multi-byte chars, boundary rows).

Exit Criteria:

- [x] ≥ 8 focused tests pass; version semantics are repo-observable (slot-reuse test proves no stale-version leak). *(Adjudicated with the Decision above: the version-column tests are replaced by the 10 value-type/validation/conversion tests of `TSEditValueTypesTest`; the observable reuse gates are the ReuseCursor unit + invariant tests of Phase 2.)*
- [x] `./mvnw -pl nop-treesitter -am test -T 1C` green (280 tests, parse path untouched this phase).
- [x] `No owner-doc update required` — module-internal; public API docs are roadmap item 14.
- [x] `ai-dev/logs/{year}/{month}-{day}.md` entry added (`ai-dev/logs/2026/09-09.md`).

## Phase 2 — parseIncremental with subtree reuse

Status: completed
Targets: `TSParser.parseIncremental`, `ReuseCursor` in `io.nop.treesitter.parser.incremental`, reuse hook in `GLRParser`, incremental tests

- Item Types: `Fix | Decision | Proof`

- [x] `Decision` recorded — final reuse design (refined 2026-09-09 from C `parser.c` `ts_parser__reuse_node` / `can_reuse_first_leaf` and the generator's `mark_fragile_tokens`): reuse granularity is the **leaf** (C breaks reused composites down via `breakdown_lookahead` and shifts only their first leaf; all parents are re-reduced). The `ReuseCursor` flattens the old tree to a leaf spine (old coordinates) and offers, per head position, the candidate whose old start maps exactly to that position through the edit list (old→new shift; offsets inside an edited old range have no coordinate). A candidate is accepted iff (a) its old range intersects no edit — the immutable-tree equivalent of C's `has_changes` bits, (b) it is non-empty, a real grammar symbol, and not the keyword-capture token, (c) the current state has no external lex mode, and (d) the cell is safe for reuse either because the leaf's recorded lex state equals the state's lex state (same DFA + same bytes + same start ⇒ deterministic same token) or because the blob's generator-computed `reusable` bit (`ActionGroup.reusable`, extracted from `.reusable` in parser.c by the existing item-2 extractor) marks it lexically unambiguous across lex states. The accepted candidate is copied into the parse arena at the mapped position and substituted for the *lookahead* — every table action (reduce first, then shift) dispatches exactly as for a freshly lexed token, so tree shape, production ids and dynamic precedence match a fresh parse by construction. Leaves record their lex state in the arena `state` slot (free for leaves; production ids are only ever read from composite parents); external-scanner lex states record `-1` and never reuse.
  - **`Decision` (recorded 2026-09-09)**: `TSTree` retains the `byte[]` source it was parsed from. The baseline note said source retention was not needed; implementing `getChangedRanges` per the Phase 3 correctness property (leaf content comparison) and `TSRange` point computation requires each tree's own source bytes. Old trees remain immutable; retention only adds a reference held by the snapshot.
- [x] `Fix`: `GLRParser.accept` derived the root subtree's byte size from the GSS base node position (always 0), so the root span was wrong in every full parse since item 4; now derived from the END token's end offset. Surfaced by the new root-span assertions in `ParseIncrementalTest` and regression-covered there.
- [x] `parseIncremental(language, oldTree, List<TSInputEdit>, byte[] newSource)` on `TSParser` (+ overload with `ParserOptions` and `IncrementalStats` exposing reused-subtree / lexed-token counts); sequential edit validation (nulls, bounds, overlap → typed `TreeSitterException`); drives the same `Language`/arena/GLR machinery as `parse` — no second parser.
- [x] Correctness invariant enforced in tests: every case asserts the incremental tree's corpus form and flat field-aware form are byte-identical to a fresh full parse, and the root spans the new source.
- [x] Reuse observability: non-trivial edits assert reused-subtree count > 0; a no-op edit on a gap-free JSON fixture asserts 100% leaf reuse and exactly one lex (the end token); a gapped fixture documents the C-identical whitespace-gap fallback (aligned leaves reuse, leaves behind gaps re-lex).
- [x] Incremental tests: insert / delete / replace / multi-edit / no-op / nested / string-edit / two Java fixtures / same-oldTree-twice / overlapping & out-of-range & null validation (14 tests in `ParseIncrementalTest`).

Exit Criteria:

- [x] **端到端验证**: `oldTree → parseIncremental → s-expression` byte-equal to full reparse over JSON fixtures (edits inside array, object, string value, nested arrays) and 2 Java fixtures (method-body edit, field insert).
- [x] **接线验证**: incremental path drives the same `GLRParser`/`Lexer`/`Language` machinery through the `reuseLeafForPosition` hook inside `advance` — no second parser implementation.
- [x] No silent skip: overlapping/out-of-range/null edits raise typed errors; the no-op path provably reuses (counts asserted, not assumed).
- [x] `No owner-doc update required` — module-internal.
- [x] `ai-dev/logs/` entry updated (`ai-dev/logs/2026/09-09.md`).
- [x] `./mvnw -pl nop-treesitter -am test -T 1C` green — 294 tests (270 baseline + 14 new), JSON corpus 7/7, Java corpus 108/108.
## Phase 3 — getChangedRanges + full 8-test suite + acceptance

Status: planned
Targets: `getChangedRanges`, changed-range tests, roadmap item 9 status

- Item Types: `Fix | Proof`

- [ ] `getChangedRanges(oldTree, newTree)` → `List<TSRange>`: parallel pre-order walk comparing subtree content (symbol + byte range + s-expression spine); differing regions emitted, adjacent/overlapping ranges merged (per `get_changed_ranges.c` merge semantics); identical trees → empty list.
  - **Execution refinement (2026-09-09, from C `get_changed_ranges.c` + gotreesitter `DiffChangedRanges`)**: unlike C — which *mutates* the old tree into new coordinates via `ts_subtree_edit` and compares with `has_changes` bits — this runtime keeps trees immutable, so the diff compares leaf spines (pre-order leaves, invisible nodes and chain containers flattened, effective/alias symbols) with **position-independent equality** (same effective symbol + same source content bytes, each read from the node's own tree's retained source). Differing leaf pairs emit the union span `[min(start), max(end))`; the walk advances both sides past `min(end)`; trailing one-side remainder is emitted as a range; ranges are coalesced per C's `ts_range_array_add` (`start <= last.end` merges). This yields tight ranges without the shifted-tail over-report that absolute-position equality would produce, and reports pure token-content changes (e.g. `1`→`2`) which upstream detects via its dirty bits.
- [ ] Changed-range correctness property, test-asserted: for every edit test, the computed ranges cover exactly the edited region — verified by asserting, for every byte outside the computed ranges, that old and new tree content at that byte is identical (mechanical cross-check, no hand-waved expectations).
- [ ] Complete the IR-04 suite to 8 tests (add: point-coordinate edit — same bytes, different reported points; file-boundary edits at byte 0 and at EOF), each asserting (a) equality-with-full-reparse, (b) reuse count, (c) changed ranges == edited region.
- [ ] JSON corpus 7/7 + Java corpus 108/108 regression intact after all Phase 2/3 changes.
- [ ] Roadmap item 9 flipped to `done` only via closure audit of this plan (derived status).

Exit Criteria:

- [ ] **端到端验证**: `parse → edit → parseIncremental → getChangedRanges` produces ranges that exactly match the edited region on all 8 test cases (entry: old tree + edit list; exit: asserted ranges + full-reparse equality).
- [ ] **接线验证**: `getChangedRanges` consumes the same old/new `TSTree` snapshots produced by `parse`/`parseIncremental` (no third tree representation).
- [ ] All 8 IR-04 tests pass with all three assertions; identical-tree → empty range list tested.
- [ ] No silent skip: empty/malformed edit lists and null inputs fail loudly; the outside-ranges byte-identity property is mechanically asserted, not spot-checked.
- [ ] `ai-dev/logs/` entry updated.

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 frontmatter `status` 改为 `completed`。关闭流程详见 `ai-dev/plans/00-plan-authoring-and-execution-guide.md` 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] 所有 in-scope confirmed live defects 已修复（本 plan 无已知 live defect 入口）
- [ ] 所有 in-scope confirmed contract drifts 已收敛（无）
- [ ] 行为/契约结果已达成：`parseIncremental` 产物与 full reparse 字节一致 + `getChangedRanges` 覆盖精确编辑区域 + 复用可观测
- [ ] 必要 focused verification 已完成（Phase 1 ≥ 8 / Phase 2 ≥ 6 / Phase 3 补齐至 8 测试 + 端到端验证 + 接线验证）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required（module-internal，公开 API 文档归属 roadmap item 14）
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据（写入 `## Closure` 段）
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）`parseIncremental`/`getChangedRanges` 确实被端到端测试从 `TSParser.parse` 产物驱动（非空壳），（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw -pl nop-treesitter -am test -T 1C` 通过（134 基线 + 新增测试全绿）
- [ ] checkstyle / 代码规范检查通过（`checkstyle:check -Pqa` 0 violations，按 M1 判定方式）

## Draft Review Record

- dispatch review #review-2026-09-07-200420-mission-driver-2026-09-08-0234-3-incremental-reparse-1-8e2095eb to ses_f829c179dffecJmpLm6GoYuYbI
- 2026-09-08：iteration 1，共识 approved #review-2026-09-07-200420-mission-driver-2026-09-08-0234-3-incremental-reparse-1-8e2095eb

## Verification

## Closure
