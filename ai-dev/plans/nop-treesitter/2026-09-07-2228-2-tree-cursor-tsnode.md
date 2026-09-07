---
status: active
mission: nop-treesitter
work-item: "6"
group: "2026-09-07-2228"
verify: [test]
---

# Tree cursor + TSNode value class + node navigation (M2, part 2)

## Current Baseline

- M1 closed: `TSParser.parse(language, source)` produces an immutable `TSTree` (private snapshot arena + root id + `toSExpression()`); `SubtreeArena` tracks `parentOf(id)`; `Subtree` is an int-field record with up to `MAX_CHILDREN=4` inline slots.
- Overflow children (> 4) are stored in invisible container-chain nodes built by `Parser.buildChain` (reserved container symbol = `language.symbolCount()`); `toSExpression()` already flattens invisible/unnamed nodes transparently, but no **navigation** API exists yet.
- No `TSNode`, no `TSTreeCursor`, no field-name lookup, no named-child iteration — consumers currently have only `root()`, `arena()`, and `toSExpression()` on `TSTree`.
- Blob v1 has no field tables. Upstream Java grammar declares `FIELD_COUNT=40` + field maps; JSON grammar declares `FIELD_COUNT=2` (`key`/`value` on pair) with `ts_field_map_slices[1]` and `ts_field_map_entries` — extractable but not yet encoded/decoded.
- Sibling draft `2026-09-07-2228-1-glr-graph-stack-java-corpus.md` (roadmap item 5, executes first in this group) lands blob v2 field/alias tables + Java grammar blob + alias-aware parse trees. Plan 6 consumes those tables for `currentFieldName`/field navigation; per roadmap dependency graph (`P5 → P6`) item 6 executes after item 5.
- References: upstream C `lib/src/tree_cursor.c` / `node.c`; gotreesitter `cursor.go` (414 lines) and `tree.go` (MIT, translate don't copy). Roadmap deliverables CUR-01..03.
- Roadmap item 6 is `todo`.

## Goals

- `TSNode` value type (immutable; holds subtree index/id, owning `TSTree`, optional field/context data) with O(1)-style navigation backed by arena int ids — no per-node heap allocation during traversal.
- `TSTreeCursor` supporting `gotoFirstChild`, `gotoNextSibling`, `gotoParent`, `gotoFirstChildForIndex`-style motion, plus named-only child iteration and `currentFieldName` / field-aware lookup via the grammar's field map (blob v2 data).
- Transparent navigation through invisible constructs (container chains for >4 children, invisible/unnamed nodes) so external structure matches upstream semantics.
- 15 focused unit tests (deep navigation, sibling walk, parent ascent, field names, named-child iteration, extras/comments traversal, >4-child node flattening).

## Non-Goals

- Query engine / S-expression matching (roadmap item 8 — consumes the cursor later).
- Incremental reparse (item 9), GLR internals (item 5 — sibling plan), scanner VM (item 7), error recovery (item 11).
- Public byte-range / point APIs beyond what navigation needs (`TSInputEdit`/`TSPoint`/`TSRange` are roadmap item 9).
- `childByFieldName` on `TSNode` beyond cursor-level field lookup; full upstream `TSNode` API surface is roadmap item 14 (public API docs).

## Phase 1 — TSNode + TSTreeCursor core navigation

Status: planned
Targets: `nop-treesitter/src/main/java/io/nop/treesitter/TSNode.java`, `io.nop.treesitter.cursor/`, tests under `src/test/java/io/nop/treesitter/cursor/`

- Item Types: `Fix | Proof`

- [x] `TSNode` value class: immutable handle `(tree, subtreeId)` with `type()` (symbol name), `named()`, `isVisible()` (from `Language` symbol metadata), and navigation entry points delegating to the cursor.
- [x] `TSTreeCursor` on top of the existing arena: `gotoFirstChild` / `gotoNextSibling` / `gotoParent` over subtree ids; child iteration follows visible children and transparently descends through invisible container-chain nodes (reserved container symbol) — navigation over a >4-child node returns the same child sequence as `toSExpression` flattening.
- [x] Named-child iteration: `gotoFirstNamedChild` / `gotoNextNamedChild` skip unnamed/extra tokens while extras (comments) remain reachable in unfiltered iteration, matching upstream child semantics.
- [x] No silent skip: cursor motion past the end / above root returns false and leaves the cursor at a defined, documented position (no exceptions swallowed, no null-subtree no-ops).
- [x] Unit tests (≤10 first pass): deep navigation into a nested JSON document; sibling walk order equals `toSExpression` child order; parent ascent reproduces the original path; named-vs-all iteration; comment (extra) reachability; >4-child flattening; empty/leaf nodes.

Exit Criteria:

- [x] **端到端验证**: parse a JSON snippet via `TSParser.parse`, then walk root → first child → ... → leaf and back with the cursor; every visited visible node's `type()` appears in the corresponding `toSExpression()` output in the same order.
- [x] **接线验证**: the cursor walks the same `TSTree`/arena snapshot that `TSParser.parse` produces (no second tree representation in tests).
- [x] 10 focused tests pass; `./mvnw -pl nop-treesitter -am test -T 1C` green.
- [x] No silent skip: out-of-range motion is tested and defined (returns false, position documented).
- [x] `No owner-doc update required` — module-internal; public API docs are roadmap item 14.
- [x] `ai-dev/logs/{year}/{month}-{day}.md` entry added.

## Phase 2 — field-name lookup via grammar field tables

Status: planned
Targets: `Language` field-map accessors (consuming blob v2 from plan `...-1`), parser production-id retention, cursor field API, tests

- Item Types: `Fix | Proof`

- [x] `Language` exposes field tables decoded from blob v2: `fieldCount`, field-name lookup by field id, and per-production-id field-map slices/entries (JSON pair → `key`/`value`; Java grammar 40 fields).
- [x] Parse-time production-id retention: nodes produced by a reduce carry the reduce action's `productionId` through the arena so a parent node can resolve child field names from its production id + child slot (JSON blob v2 + Java blob from plan `...-1`).
- [x] Cursor field API: `currentFieldName()` returns the grammar field name of the current child within its parent (null/absent when the node occupies no field slot), and field-directed access (e.g. first child under a named field) works for JSON pair and Java constructs.
- [x] Tests: JSON `{"a":1}` — pair children resolve to field `key` and field `value`; Java `method_declaration` children resolve expected fields (`type`, `name`, `parameters`, `body`, ...); a node with no field slot reports absent field name.

Exit Criteria:

- [x] **端到端验证**: `parse → cursor → currentFieldName` returns the upstream-expected field names for the JSON pair fixture and at least one Java fixture with multiple fields.
- [x] **接线验证**: field resolution uses the Language blob v2 field tables + parse-time production ids (not hand-coded per-grammar maps).
- [x] No silent skip: lookup of an out-of-range field id / missing production id raises or returns a defined absent value, covered by a test.
- [x] Field tests pass on both JSON and Java blobs; JSON corpus 7/7 regression intact.
- [x] `No owner-doc update required` — module-internal navigation API; public docs are roadmap item 14.
- [x] `ai-dev/logs/` entry updated.

## Phase 3 — full 15-test suite + upstream cross-check

Status: planned
Targets: cursor package tests, corpus-level fixtures

- Item Types: `Proof | Follow-up`

- [x] Complete the roadmap CUR-03 suite to 15 unit tests covering: deep navigation, field names, named-child iteration, sibling/parent round-trips, extras, >4-child flattening, leaf behavior.
- [x] Cross-check: for ≥ 5 upstream Java corpus fixtures (from the vendored `tree-sitter-java/test/corpus/`), the cursor-reachable **named** node sequence equals the named nodes in the expected s-expression tree (byte-order equivalence on the visible spine).
- [x] Follow-up note (roadmap item 13 ref): record per-node cursor step allocation counts in the daily log — no tuning in this plan.

Exit Criteria:

- [x] 15/15 focused tests pass.
- [x] **端到端验证**: cursor-named-sequence equals expected s-expression named nodes on ≥ 5 Java corpus fixtures (entry: `TSParser.parse`; exit: ordered named-type sequence).
- [x] **接线验证**: cross-check drives the same parse + cursor path as Phases 1–2 (no parallel implementation).
- [x] `./mvnw -pl nop-treesitter -am test -T 1C` green; JSON corpus 7/7 and any Java corpus runner from plan `...-1` unaffected.
- [x] `No owner-doc update required` — module-internal API; public API docs are roadmap item 14.
- [x] Roadmap item 6 flipped to `done` only via closure audit of this plan (derived status).
- [x] `ai-dev/logs/` entry updated.

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 所有 in-scope confirmed live defects 已修复（无已知遗留 defect）
- [ ] 行为/契约结果已达成：cursor 导航 + field-name 解析 + 15-test 套件全部落地
- [ ] 必要 focused verification 已完成（15/15 focused tests + JSON 7/7 回归）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响的 owner docs 已同步到 live baseline（本计划显式 `No owner-doc update required` — 模块内部 API）
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据（写入 `## Closure`）
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）cursor 确实被 `TSParser.parse` 产出的同一 `TSTree`/arena 在运行时调用（接线验证），（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw -pl nop-treesitter -am test -T 1C` 通过
- [ ] checkstyle / 代码规范检查通过

## Draft Review Record

- dispatch review #review-2026-09-07-200420-mission-driver-2026-09-07-2228-2-tree-cursor-tsnode-1-9c6bafdc to ses_f83aad192ffeMh9V0wSN9tJexO
- 2026-09-07：iteration 1，共识 approved #review-2026-09-07-200420-mission-driver-2026-09-07-2228-2-tree-cursor-tsnode-1-9c6bafdc

## Verification

## Closure
