---
status: active
mission: nop-treesitter
work-item: "4"
group: "2026-09-07-1713"
verify: [test]
---

# LR(1) parser + keyword lexer — JSON grammar end-to-end (M1)

## Current Baseline

- `nop-treesitter/` scaffolded (pom.xml, parent reactor registration, `TreeSitterBootstrap` smoke tests green) — commit `0d5971af6a`.
- Prerequisites from this plan group must land first: plan `...-1-ts2java-extractor` delivers `src/main/resources/grammars/json/tree-sitter-json-blob.bin` + `src/main/resources/blob-format.md`; plan `...-2-subtree-arena` delivers `Subtree` / `SubtreeArena` / `SymbolTable` in `io.nop.treesitter.subtree` (+ `io.nop.treesitter.util`).
- Upstream JSON corpus: `~/sources/treesitter/grammars/tree-sitter-json/test/corpus/main.txt` — 14 test sections (Arrays, String content, Top-level numbers, Exponents, Top-level null, ...), not yet vendored into the module.
- Roadmap item 4 is `todo`. Acceptance per roadmap: `./mvnw -pl nop-treesitter test -T 1C` shows 100% JSON corpus pass, 0 failures, 0 errors.
- Read-only references: gotreesitter `~/sources/treesitter/gotreesitter/parser.go` (MIT, translate don't copy); upstream `parser.c` (extraction source for the blob, v0.25.x line).

## Goals

- `Language` loader that reads the plan-1 blob: symbol table, metadata, state table, parse actions, lex modes, keyword lex modes; validates magic + version stamp.
- `Lexer`: UTF-8 byte stream → token stream using per-state lex modes and keyword lex modes, single-char fallback tokens.
- `Parser`: LR(1) with a linear stack driving parse actions (shift / reduce / accept / error); no GLR; conflict resolution policy deferred to roadmap item 5.
- `TSTree` immutable wrapper around the arena root plus `toSExpression()` for golden comparison.
- Upstream corpus runner: every section of the vendored `main.txt` parses to a tree shape byte-equivalent to the expected s-expression.

## Non-Goals

- GLR + graph-structured stack (roadmap item 5) — shift/reduce conflict resolution policy deferred there.
- External scanner bytecode VM (roadmap item 7).
- Error recovery / ERROR nodes / missing-token injection (roadmap item 11).
- Tree cursor / `TSNode` public navigation API (roadmap item 6).
- Incremental reparse (roadmap item 9).

## Phase 1 — Language blob loader

Targets: `nop-treesitter/src/main/java/io/nop/treesitter/language/`, `src/test/java/io/nop/treesitter/language/`

- Item Types: `Fix | Proof`

- [ ] Vendor `~/sources/treesitter/grammars/tree-sitter-json/test/corpus/main.txt` into `nop-treesitter/src/test/resources/upstream/grammars/tree-sitter-json/test/corpus/` so corpus tests are hermetic (roadmap cross-cutting: carry upstream fixtures, don't synthesize).
- [ ] `Language` loader per `blob-format.md`: decodes symbol table, metadata, state table, parse actions, lex modes, keyword lex modes; validates magic + version stamp.
- [ ] Unit tests: loader against the shipped blob — symbol names / counts and state count match the format doc's expectations; wrong version stamp raises a typed exception; truncated / corrupt blob raises instead of silently defaulting.

Exit Criteria:

- [ ] Loader tests pass and are repo-observable against the committed blob resource (classpath load of `src/main/resources/grammars/json/tree-sitter-json-blob.bin`).
- [ ] No silent skip: corrupt / mismatched-version blobs raise typed exceptions, covered by focused tests.
- [ ] `No owner-doc update required` — module-internal loader; public API docs are roadmap item 14.
- [ ] `ai-dev/logs/{year}/{month}-{day}.md` entry added (reverse-chronological top insert).

## Phase 2 — Lexer

Targets: `nop-treesitter/src/main/java/io/nop/treesitter/lexer/`, tests

- Item Types: `Fix | Proof`

- [ ] `Lexer`: byte-stream scanning (UTF-8) with per-state lex mode selection from the loaded language, keyword lex mode for keyword-vs-identifier resolution, single-char fallback tokens; byte offsets tracked.
- [ ] Unit tests: JSON token decoding — strings (incl. escapes), numbers (incl. exponents), keywords `true` / `false` / `null`, punctuation; keyword-vs-identifier distinction (e.g. `truex` lexes as identifier, not keyword).
- [ ] No silent skip: an unexpected byte raises a lex error that propagates (fast-fail) instead of being swallowed or skipped.

Exit Criteria:

- [ ] Token-level tests pass with expected token types and byte offsets for representative JSON inputs.
- [ ] Keyword-mode behavior verified (a near-keyword identifier does not collapse into a keyword token).
- [ ] No silent skip verified: malformed byte sequences raise rather than produce empty/default tokens.
- [ ] `No owner-doc update required` — module-internal lexer.
- [ ] `ai-dev/logs/` entry updated.

## Phase 3 — LR(1) parser + TSTree + toSExpression

Targets: `nop-treesitter/src/main/java/io/nop/treesitter/parser/`, `nop-treesitter/src/main/java/io/nop/treesitter/TSTree.java`, tests

- Item Types: `Fix | Proof`

- [ ] `Parser`: LR(1) with a linear stack operating on int ids only, driving parse actions (shift / reduce / accept / error) from the loaded language's state table; stack lives on the arena (plan `...-2`).
- [ ] `TSTree` immutable wrapper around the root `Subtree`.
- [ ] `toSExpression()` producing the canonical `(document (array ...))` text form for golden comparison against upstream expected trees.
- [ ] Unit tests: small JSON snippets (nested arrays/objects, escapes, numbers with exponents, top-level scalars) parse to expected s-expressions.
- [ ] **接线验证**: `Language` → `Lexer` → `Parser` → `TSTree` connected in a single `parse(source, language)` path — no component left unwired (Anti-Hollow Rule).

Exit Criteria:

- [ ] `parse(source, language)` end-to-end path exists and is exercised by tests (language from blob, bytes from source string).
- [ ] S-expression golden tests pass for the small-snippet set.
- [ ] No silent skip: encountering an action type the parser does not implement raises `UnsupportedOperationException` naming the action (Minimum Rules #24); verified by test on a synthetic action.
- [ ] `No owner-doc update required` — module-internal; public API docs are roadmap item 14.
- [ ] `ai-dev/logs/` entry updated.

## Phase 4 — upstream corpus runner + acceptance

Targets: `src/test/java/io/nop/treesitter/corpus/`, corpus fixtures

- Item Types: `Fix | Proof`

- [ ] Corpus runner: for every section of the vendored `main.txt`, parse the input bytes, produce the s-expression, compare against the expected tree text.
- [ ] Fix runtime deviations until all 14 sections pass with byte-equivalent tree shape (s-expression text normalized to upstream format).
- [ ] Acceptance run: `./mvnw -pl nop-treesitter -am test -T 1C` → 100% JSON corpus pass, 0 failures, 0 errors.
- [ ] Adjudication rule: any fixture that cannot pass is either fixed in the runtime or explicitly adjudicated with upstream evidence (link to the vendored `main.txt` section + reason) — never silently skipped or marked known-failure without evidence.

Exit Criteria:

- [ ] **端到端验证**: the corpus runner goes from corpus fixture file bytes → `TSTree` → s-expression → equality with expected text, for every section (entry: fixture file; exit: comparison verdict).
- [ ] **接线验证**: the runner drives the same `parse(source, language)` path as Phase 3 (no second parser implementation in tests).
- [ ] Acceptance command output: 100% JSON corpus pass, 0 failures, 0 errors on `./mvnw -pl nop-treesitter -am test -T 1C`.
- [ ] No silent skip: zero corpus sections skipped; each either passes or carries recorded upstream evidence.
- [ ] `No owner-doc update required` — module-internal; README already links the roadmap, public API docs are roadmap item 14.
- [ ] `ai-dev/logs/` entry updated.

## Draft Review Record

- dispatch review #review-2026-09-07-171303-mission-driver-2026-09-07-1713-3-lr1-parser-json-corpus-1-ff7350c5 to ses_f84c15faeffeofjUshgk9Yb8BR
- 2026-09-07：iteration 1，共识 approved #review-2026-09-07-171303-mission-driver-2026-09-07-1713-3-lr1-parser-json-corpus-1-ff7350c5

## Verification

## Closure