---
status: active
mission: nop-treesitter
work-item: "7"
group: "2026-09-08-0234"
verify: [test]
---

# External scanner bytecode VM + JS grammar validation (M2, part 3)

## Current Baseline

- M2 parts 1-2 closed (roadmap items 5/6 `done`): blob v2 (field/alias tables + lexer automata), GLR + graph-structured stack + conflict resolution, alias-aware reduce, TSNode/TSTreeCursor with field-name navigation. `./mvnw -pl nop-treesitter -am test -T 1C` green — **134 tests** (JSON corpus 7/7 byte-exact, Java corpus 108/108).
- Blob format v2 (`blob-format.md`) encodes 15 sections. Lex-mode section 8 carries only `lex_state` per parse state. `ParserCExtractor` already parses `external_lex_state` and `reserved_word_set_id` out of the `ts_lex_modes[]` initializer into `ExtractedGrammar.LexMode` (`ParserCExtractor.java:583-584`), but `BlobWriter.writeLexModes`/`BlobReader` never encode/decode them (dropped on write). Header has no `external_token_count`; blob has no external-scan data at all.
- `Lexer` (`io.nop.treesitter.lexer.Lexer`) is a pure DFA driver (`Lexer.next(language, source, position, parseState)` → token via lex-state automaton); keyword capture is a post-hoc relabel in `GLRParser.java:133-135` using `language.keywordCaptureToken()`. There is no external-scan path, no valid-symbol filtering, no reserved-word-set filtering.
- `GLRParser.parse` is the single parse path (`TSParser.java:48`); it consumes `Lexer.Token` and has no hook where the C runtime calls `ts_lexer_external_scan` (lexer.c) when `external_lex_state != 0`.
- **Roadmap premise drift (already recorded in plan `2026-09-07-2228-1` closure)**: item 7 says "Java grammar scanner bytecode" — the vendored tree-sitter-java has `EXTERNAL_TOKEN_COUNT 0`, no `scanner.c`. The validation grammar must be re-adjudicated.
- Upstream reference material at `~/sources/treesitter/`:
  - `grammars/tree-sitter-javascript/` — `parser.c` (LANGUAGE_VERSION **15**, STATE_COUNT 1870, SYMBOL_COUNT 261, FIELD_COUNT 36, **EXTERNAL_TOKEN_COUNT 8**; `ts_external_scanner_symbol_map[8]`, `ts_external_scanner_states[10][8]`, `ts_reserved_words[14][MAX_RESERVED_WORD_SET_SIZE=35]`, `.external_scanner = {states, map, create, destroy, scan, serialize, deserialize}`), `scanner.c` (364 lines; external tokens: AUTOMATIC_SEMICOLON, TEMPLATE_CHARS, TERNARY_QMARK, HTML_COMMENT, LOGICAL_OR, ESCAPE_SEQUENCE, REGEX_PATTERN, JSX_TEXT), corpus 6 files (232 sections total: destructuring 10, expressions 112, injectables 4, literals 14, semicolon_insertion 24, statements 68). Not yet vendored.
  - `grammars/tree-sitter-json/` (LANGUAGE_VERSION 14, EXTERNAL_TOKEN_COUNT 0) — regression harness for the no-external-scan path.
  - gotreesitter `external_vm.go` (MIT) — bytecode ISA reference: Fail / Jump / RequireValid / RequireStateEq / SetState / IfRuneEq / IfRuneInRange / IfRuneClass / Advance(skip) / MarkEnd / Emit.
  - C `lib/src/lexer.c` (`ts_lexer_external_scan`), `lib/src/language.h` (`LANGUAGE_VERSION_WITH_RESERVED_WORDS 15`, `LANGUAGE_VERSION_WITH_PRIMARY_STATES 14`).
- JS scanner.c is **stateless** (`serialize` returns 0, `create` returns NULL) — single-parse state register suffices for it; payload serialize/deserialize persistence is not exercised by this grammar.
- Roadmap item 7 is `todo`; stage deps: item 5 (`done`). Roadmap acceptance: VM must produce byte-identical tokens to the C scanner (token-for-token tests, not just tree-shape).

## Goals

- **Blob v3** (backward-incompatible, v2 blobs rejected with a typed error): header gains `external_token_count`; lex-mode section 8 extended to carry `external_lex_state` + `reserved_word_set_id` per parse state; new sections for `ts_external_scanner_symbol_map`, `ts_external_scanner_states`, reserved word sets, and the external-scanner bytecode program. `blob-format.md` documents v3 byte-for-byte.
- **`ScannerVM`**: interpreter for the roadmap ISA (PUSH_BYTE / PUSH_BYTES / SPAN / ACCEPT / ADVANCE / SKIP / JMP_IF_EQ / JMP_IF_NE / CALL / RET) extended with valid-symbol and scanner-state support; final opcode set adjudicated against gotreesitter's `external_vm.go` (Decision). Operates on the Lexer's byte cursor; produces external tokens (symbol + span) identical to the C scanner.
- **`ScannerCompiler`**: textual DSL → bytecode program with load-time validation (jump targets in range, stack discipline, unknown-mnemonic fail-loud). The JS `scanner.c`'s 6 scan functions + dispatcher are hand-translated to the DSL (translation reference: scanner.c semantics; gotreesitter hand-translation pattern).
- **GLR parser integration** mirroring C `lexer.c`: when `externalLexState(parseState) != 0`, compute the valid external-symbol list for the parse state, invoke the VM first, shift the scanner token through the existing arena/shift machinery, fall back to the internal DFA only when the scanner rejects; reserved-word-set filtering in the keyword path (abi ≥ 15 semantics).
- **JS grammar vendored + JS blob v3 built end-to-end** by the Ts2Java CLI; token-level scanner tests + JS parse smoke on the **external-token-scoped corpus subset** (100% pass); full JS corpus is roadmap item 10's acceptance (explicit successor ownership).
- Regression: JSON corpus 7/7 and Java corpus 108/108 byte-exact on the v3 path (external scan never fires — both grammars have external lex state 0).

## Non-Goals

- Full JS/TS corpus pass (item 10 successor; this plan passes only the external-token-scoped subset and records the skipped list).
- Scanner payload serialize/deserialize persistence across parses (consumed by item 9 incremental reparse; JS scanner is stateless so a single-parse state register is all item 7 needs).
- `get_column` / JSX-included-range support (needed by stateful TSX/JS scanners — item 10).
- Query engine (item 8), incremental reparse (item 9), error recovery (item 11), performance tuning (item 13).
- Upgrading the vendored JSON/Java grammars to LANGUAGE_VERSION 15 (JSON/Java stay v14; the ABI gap is handled by the blob header's `abi_version` byte, already present).

## Phase 1 — Blob v3 format + JS grammar vendoring + JS blob end-to-end

Status: planned
Targets: `src/main/resources/blob-format.md`, `io.nop.treesitter.codegen` (ParserCExtractor/BlobWriter/BlobReader), `io.nop.treesitter.language.Language`, `src/test/resources/upstream/grammars/tree-sitter-javascript/`, `src/main/resources/grammars/javascript/`, codegen + loader tests

- Item Types: `Fix | Decision | Proof`

- [x] `Decision` recorded: validation-grammar re-adjudication — the roadmap's "Java grammar scanner bytecode" premise is void (vendored tree-sitter-java: EXTERNAL_TOKEN_COUNT 0, no scanner.c); tree-sitter-javascript is the validation grammar (EXTERNAL_TOKEN_COUNT 8, scanner.c 364 lines, corpus 232 sections). Also adjudicated: ABI handling — JS parser.c is LANGUAGE_VERSION 15 while the vendored JSON/Java grammars are 14; decision is to vendor the v15 JS grammar as-is and extend the extractor for its additions (reserved words are gated on abi ≥ 15 per `language.h`), keeping the existing v14 blobs untouched.
- [x] Vendor `~/sources/treesitter/grammars/tree-sitter-javascript/` `src/parser.c`, `src/scanner.c`, and all 6 `test/corpus/*.txt` into `src/test/resources/upstream/grammars/tree-sitter-javascript/` mirroring the JSON/Java fixture layout.
- [x] `blob-format.md` v3: header fields `external_token_count` (u16), `external_lex_state_count` (u16), `reserved_word_set_count` (u16), `max_reserved_word_set_size` (u16), scanner-program presence + byte length; section 8 extended to `(u16 lex_state, u16 external_lex_state, u16 reserved_word_set_id)` per parse state; new sections: external scanner symbol map (`external_token_count × u16`), external scanner states (`external_lex_state_count × external_token_count` u8), reserved word sets (set count × `(u8 length, length × u16 symbol)` rows), scanner bytecode program (u32 length + opcode stream, opcode encoding specified).
- [x] `ParserCExtractor` extensions: emit `external_lex_state` / `reserved_word_set_id` per state (already parsed — stop dropping them), extract `ts_external_scanner_symbol_map`, `ts_external_scanner_states`, `ts_reserved_words` rows (row length = first 0-symbol index, fail-loud if row exceeds `max_reserved_word_set_size`); throw typed exceptions on any undecodable construct (no silent skip).
- [x] `BlobWriter`/`BlobReader` v3 for the new sections; JSON **and Java** blobs regenerated as v3 (`external_token_count = 0`, empty scanner sections — both shipped blobs must be rebuilt, since the v3 loader rejects v2 blobs with a typed error and Phase 4 runs the Java corpus on the v3 path); determinism test (two runs byte-identical) + read-back round-trip test against the vendored JSON `parser.c`.
- [x] `Language` loader: decode v3 sections; expose `externalTokenCount()`, `externalLexState(parseState)`, `reservedWordSetId(parseState)`, `externalSymbolMap()`, `externalStates()`, `reservedWordSet(id)`, `scannerProgram()`; v2 blobs rejected with typed "unsupported blob format version" error.
- [x] Build `tree-sitter-javascript-blob.bin` via the `Ts2Java` CLI from the vendored `parser.c`; ship under `src/main/resources/grammars/javascript/`.
- [x] Unit tests: JS blob decode matches vendored `parser.c` statics (external token count 8, symbol map ordinals, states matrix 10×8, reserved-word rows); v2 JSON blob → typed rejection; v3 JSON blob decodes with identical symbol/state/table values as v2.

Exit Criteria:

- [x] `blob-format.md` v3 documents the writer output byte-for-byte (fresh decode of the shipped blobs round-trips).
- [x] **端到端验证**: single CLI run `Ts2Java <js parser.c> <blob>` produces the shipped JS blob; `Language.fromClasspath("/grammars/javascript/tree-sitter-javascript-blob.bin")` loads it and reports external token count 8.
- [x] JSON corpus 7/7 **and Java corpus 108/108** byte-exact on the regenerated v3 blobs (regression gate for the section-8 width change; the shipped Java blob is rebuilt in this phase and must stay green).
- [x] No silent skip: v2 blobs rejected typed; undecodable C constructs / out-of-range rows throw with state/symbol context.
- [x] `No owner-doc update required` beyond `blob-format.md` (module-internal contract).
- [x] `ai-dev/logs/{year}/{month}-{day}.md` entry added.

## Phase 2 — Scanner bytecode ISA + ScannerVM interpreter

Status: planned
Targets: `io.nop.treesitter.scanner` (new package), scanner VM tests

- Item Types: `Fix | Decision | Proof`

- [ ] `Decision` recorded: final ISA — the roadmap opcode set (PUSH_BYTE/PUSH_BYTES/SPAN/ACCEPT/ADVANCE/SKIP/JMP_IF_EQ/JMP_IF_NE/CALL/RET) augmented with valid-symbol check (gotreesitter `RequireValid` equivalent — the JS dispatcher gates every scan path on `valid_symbols[...]`) and scanner-state register (`SetState`/`RequireStateEq`); step budget for loop termination. Justification per opcode with the JS scanner.c constructs it must express (template-char loop, whitespace/comment skip loops, regex pattern scan, multi-byte 0x2028/0x2029 checks, mark_end + result_symbol).
- [ ] `ScannerVM`: program counter + instruction fetch/decode, lookahead byte read over the byte[] source at the Lexer cursor, advance(skip), mark-end span tracking, result-symbol set, valid-symbol list, state register, operand stack for CALL/RET; step budget exceeded → typed exception (no infinite loop possible).
- [ ] Load-time program validation: unknown opcode, out-of-range jump target, underflowing stack discipline, malformed operand → typed exception at compile/load (no silent skip).
- [ ] VM unit tests (≥ 12): each opcode's semantics (push byte/bytes, span, accept, advance, skip, conditional jump taken/not-taken, call/ret round-trip, valid-symbol gate true/false, state set/require), EOF lookahead behavior, step-budget exhaustion, out-of-bounds program counter.
- [ ] ISA encoding documented in `blob-format.md` (scanner program section) so the writer/VM agree on bytes.

Exit Criteria:

- [ ] Every opcode has an observable focused test (a stub or no-op would fail them).
- [ ] No silent skip: invalid programs are rejected at load/compile time; VM execution failure modes throw typed exceptions.
- [ ] `blob-format.md` scanner-program section matches `BlobWriter` output byte-for-byte on a fresh decode.
- [ ] `No owner-doc update required` beyond `blob-format.md` (module-internal; public API docs are roadmap item 14).
- [ ] `ai-dev/logs/` entry updated.

## Phase 3 — ScannerCompiler DSL + JS scanner.c translation + token-level tests

Status: planned
Targets: `io.nop.treesitter.scanner.ScannerCompiler`, JS scanner DSL translation, scanner token tests

- Item Types: `Fix | Proof`

- [ ] `ScannerCompiler`: parse the textual DSL (mnemonics + operands, one instruction per line, labels for jump targets) into the Phase-2 bytecode; determinism (same DSL text → byte-identical program, two runs).
- [ ] Hand-translate the JS `scanner.c` scan functions to the DSL: `scan_template_chars`, `scan_whitespace_and_comments` (incl. `//` line comments, `/* */` block comments, newline tracking), `scan_automatic_semicolon`, `scan_ternary_qmark`, `scan_html_comment`, `scan_jsx_text`, and the `scan()` dispatcher gated on `valid_symbols` — semantics preserved verbatim from the 364-line C source (translate, don't copy; MIT). Note the vendored scanner.c defines **only these 6 scan functions**; `LOGICAL_OR`, `ESCAPE_SEQUENCE` and `REGEX_PATTERN` appear in the dispatcher purely as `valid_symbols` gates (e.g. the html-comment branch is suppressed when they are valid) and are never emitted by the scanner — the actual `regex_pattern` / `escape_sequence` tokens are produced by the internal DFA lexer (`ts_lex` `ACCEPT_TOKEN(sym_regex_pattern)` / `ACCEPT_TOKEN(sym_escape_sequence)` in `parser.c`), reached via the no-token fallback. Also note `scan_automatic_semicolon` calls `lexer->is_at_included_range_start` (line 134) — in this plan's single-document parse scope (no included ranges) it maps to a constant-false check; real included-range support is item 10.
- [ ] Compile the translated DSL into the JS blob's scanner-program section (end-to-end through `Ts2Java`).
- [ ] Token-level tests (≥ 15): curated JS fragments — template literals with `${...}` (incl. nested and unterminated), ASI cases (newline, comment, unterminated statement), ternary `?` contexts (incl. `?.`/`??` rejection), HTML comments (incl. the `<!--`/`-->` forms), JSX-text-adjacent bytes, and dispatcher-gate cases where `valid_symbols` admits only `LOGICAL_OR` / `ESCAPE_SEQUENCE` / `REGEX_PATTERN` (the VM must reject and fall through to the internal DFA, never emitting a bogus token) — assert the VM's external `(symbol, start, end)` exactly matches the expectation derived from `scanner.c` control flow, cross-checked against vendored corpus fixtures that exercise the same constructs.
- [ ] Fail-loud inventory: any `scanner.c` construct the DSL cannot express raises at translation time with the construct named; the JS scanner must translate with an empty inventory (0 unsupported constructs).

Exit Criteria:

- [ ] **Token-for-token**: ≥ 15 cases where VM external-token (symbol/start/end) equals the scanner.c-derived expectation; no tree-shape-only assertions in this phase.
- [ ] Determinism: DSL → program byte-identical across runs.
- [ ] No silent skip: unsupported-construct inventory is empty for the JS scanner and the compiler fails loudly when it would otherwise occur.
- [ ] `No owner-doc update required` beyond `blob-format.md` (module-internal).
- [ ] `ai-dev/logs/` entry updated.

## Phase 4 — Parser integration + external-token-scoped corpus validation + acceptance

Status: planned
Targets: `GLRParser` external-scan path, `Lexer` reserved-word filtering, `src/test/java/io/nop/treesitter/corpus/` JS corpus runner, roadmap item 7 status

- Item Types: `Fix | Proof`

- [ ] GLR external-scan integration mirroring C `lexer.c` `ts_lexer_external_scan`: when `externalLexState(parseState) != 0`, build the valid external-symbol list for the state (external ordinals → symbol ids via the symbol map → parse-table action check), invoke the VM; scanner-accepted token joins the normal shift/extra path; no token → fall back to the internal DFA lexing; scanner result re-validated against the state (a scanner token with no action is rejected, never shifted).
- [ ] Reserved-word-set filtering in the keyword path (abi ≥ 15 grammars only): a keyword match is only accepted when the parse state's reserved-word set admits it; identifier fallback otherwise — mirroring the C lexer's keyword capture semantics (JSON/Java unaffected — no reserved-word data).
- [ ] JSON corpus 7/7 + Java corpus 108/108 byte-exact regressions on the integrated v3 path (external scan never fires: both grammars have external lex state 0 everywhere).
- [ ] JS corpus runner (upstream-format, same pattern as `JavaCorpusTest`): a section is **in scope** iff its expected s-expression references one of the 8 external token symbols; all in-scope sections must pass 100%; out-of-scope sections are skipped with the recorded section list (successor ownership: roadmap item 10 full JS/TS corpus).
- [ ] Fixes landed in the runtime for every failing in-scope section (scanner translation, valid-symbol handling, reserved words, lexer/GLR) — no section silently skipped; any adjudicated deviation requires vendored corpus file + section + upstream evidence.
- [ ] Roadmap item 7 flipped to `done` only via closure audit of this plan (derived status); milestone M2 derives `done` when items 5+6+7 are all `done`.

Exit Criteria:

- [ ] **端到端验证**: `TSParser.parse` with the JS blob v3 on every in-scope corpus fixture produces a tree byte-equal to the upstream expected s-expression (entry: corpus fixture bytes; exit: comparison verdict).
- [ ] **接线验证**: the external-scan path is the path the corpus runner and token tests exercise — no second scanner implementation in tests; the scanner token flows through the same `GLRParser` shift machinery as internal tokens.
- [ ] All external-token-scoped JS corpus sections pass (100%), zero skipped in-scope sections; JSON corpus 7/7 and Java corpus 108/108 intact.
- [ ] No silent skip: external-scan integration has observable tests (a stub VM or a bypassed valid-symbol check would fail them).
- [ ] `ai-dev/logs/` entry updated.

## Draft Review Record

- dispatch review #review-2026-09-07-200420-mission-driver-2026-09-08-0234-1-external-scanner-vm-1-8ef0d43d to ses_f82d2e14dffeNmuFsxt28Sj4mc
- 2026-09-08：iteration 1，共识 approved #review-2026-09-07-200420-mission-driver-2026-09-08-0234-1-external-scanner-vm-1-8ef0d43d

## Verification

## Closure
