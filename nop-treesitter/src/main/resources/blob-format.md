# nop-treesitter blob format (format version 3)

> **Loader contract.** This document is the authoritative specification of the
> binary blob produced by `io.nop.treesitter.codegen.Ts2Java` and consumed by
> the `Language` loader. The reader must be implementable from this document
> alone; the round-trip tests in
> `src/test/java/io/nop/treesitter/codegen/BlobRoundTripTest.java` prove the
> shipped blobs decode against it.

All multi-byte integers are **big-endian**. The blob is a fixed header followed
by nineteen sections in a fixed order. Every count is recorded in the header so a
reader can validate total size before decoding.

Format version 3 is **backward-incompatible with v2**: the lex-mode section is
widened to carry `external_lex_state` / `reserved_word_set_id` per parse state
(v15 ABI additions), and four new sections carry the external scanner symbol
map, the external scanner states matrix, the reserved word sets and the compiled
external-scanner bytecode program. v2 blobs are rejected with a typed
"unsupported blob format version" error.

## 1. Header (fixed 96 bytes)

| Offset | Size | Field | Width rationale |
| --- | --- | --- | --- |
| 0 | 4 | magic `54 53 4A 42` ("TSJB") | — |
| 4 | 1 | format version = `0x03` | — |
| 5 | 1 | language ABI version (`LANGUAGE_VERSION` from `parser.c`, e.g. `14`/`15`) | — |
| 6 | 2 | reserved = 0 | — |
| 8 | 2 | symbol count | grammar symbol tables fit u16 |
| 10 | 2 | state count | `TSStateId` is u16 in the C runtime |
| 12 | 2 | large state count | `TSStateId` is u16 |
| 14 | 2 | token count | fits u16 |
| 16 | 2 | production id count | fits u16 |
| 18 | 2 | field count | fits u16 |
| 20 | 2 | parse action group count | fits u16 |
| 22 | 2 | small parse table word count | fits u16 |
| 24 | 2 | small parse table map count (= state_count − large_state_count) | fits u16 |
| 26 | 2 | lex mode count (= state_count) | fits u16 |
| 28 | 2 | keyword lex mode count | fits u16 |
| 30 | 2 | primary state id count (= state_count) | fits u16 |
| 32 | 2 | alias count | `ALIAS_COUNT` from `parser.c` |
| 34 | 2 | max alias sequence length | `MAX_ALIAS_SEQUENCE_LENGTH` |
| 36 | 2 | field name count (= field_count + 1; index 0 is the null name) | fits u16 |
| 38 | 2 | field map slice count (= production_id_count) | fits u16 |
| 40 | 4 | field map entry count | Java grammar has hundreds; u32 headroom |
| 44 | 4 | alias sequence element count (= production_id_count × max_alias_sequence_length) | row-major element count |
| 48 | 4 | non-terminal alias map count | u32 headroom |
| 52 | 2 | keyword capture token | `keyword_capture_token` from the initializer, 0 when absent |
| 54 | 2 | external token count | `EXTERNAL_TOKEN_COUNT` from `parser.c`, 0 when the grammar has no scanner |
| 56 | 2 | external lex state count | row count of `ts_external_scanner_states` (max external lex state + 1) |
| 58 | 2 | reserved word set count | set count of `ts_reserved_words` (max set id + 1) |
| 60 | 2 | max reserved word set size | `MAX_RESERVED_WORD_SET_SIZE`, 0 when absent |
| 62 | 4 | scanner program length | compiled external-scanner bytecode, bytes follow §20 |
| 66 | 1 | lexer fn count | 1 (main) or 2 (main + keyword) |
| 67 | 29 | reserved zeros | — |

When `external_token_count` is 0 the three following counts must be 0 (JSON and
Java grammars: no external scanner, no reserved words).

## 2. Section order

| # | Section | Encoded by |
| --- | --- | --- |
| 1 | symbol names | `BlobWriter.writeSymbolNames` |
| 2 | symbol metadata | `BlobWriter.writeSymbolMetadata` |
| 3 | parse actions | `BlobWriter.writeParseActions` |
| 4 | large parse table | `BlobWriter.writeParseTable` |
| 5 | small parse table | `BlobWriter.writeSmallParseTable` |
| 6 | small parse table map | `BlobWriter.writeSmallParseTableMap` |
| 7 | primary state ids | `BlobWriter.writePrimaryStateIds` |
| 8 | lex modes | `BlobWriter.writeLexModes` |
| 9 | keyword lex modes | `BlobWriter.writeKeywordLexModes` |
| 10 | field names | `BlobWriter.writeFieldNames` |
| 11 | field map slices | `BlobWriter.writeFieldMapSlices` |
| 12 | field map entries | `BlobWriter.writeFieldMapEntries` |
| 13 | alias sequences | `BlobWriter.writeAliasSequences` |
| 14 | non-terminal alias map | `BlobWriter.writeNonTerminalAliasMap` |
| 15 | lexer automata | `BlobWriter.writeLexerAutomata` |
| 16 | external scanner symbol map | `BlobWriter.writeExternalSymbolMap` |
| 17 | external scanner states | `BlobWriter.writeExternalStates` |
| 18 | reserved word sets | `BlobWriter.writeReservedWords` |
| 19 | scanner program | `BlobWriter.writeScannerProgram` |

The reader must not assume any section is non-empty; each count in the header
may be zero (e.g. keyword lex modes for the JSON grammar are absent).

## 3. Section 1 — symbol names

For each symbol `i` in `0..symbol_count+alias_count-1`:

```
u8  name_len
name_len × u8  name bytes (UTF-8)
```

The name at index 0 is `"end"` (`ts_builtin_sym_end`). Names are written in
symbol-id order. Alias symbols (ids `>= symbol_count`, e.g.
`alias_sym_type_identifier = 320` in the Java grammar) follow the ordinary
symbols; their names live in `ts_symbol_names` in the C source.

## 4. Section 2 — symbol metadata

For each symbol `i` in `0..symbol_count+alias_count-1`:

```
u8  flags
```

| Bit | Meaning |
| --- | --- |
| 0 | visible |
| 1 | named |
| 2 | supertype |

## 5. Section 3 — parse actions

For each group `g` in `0..parse_action_group_count-1`:

```
u16  index      — the group's header index in the C ts_parse_actions array
u8   count      — number of actions (must equal the number of action records)
u8   reusable   — 1 if reusable, else 0
count × action records
```

Each action record is fixed-width, 10 bytes:

```
u8   type   — 0=shift, 1=reduce, 2=accept, 3=recover
u8   flags  — shift: bit0=extra, bit1=repetition; 0 for other types
u16  a
u16  b
i16  c
u16  d
```

Payload interpretation by type:

| type | a | b | c | d |
| --- | --- | --- | --- | --- |
| shift | target state | 0 | 0 | 0 |
| reduce | symbol id | child count | dynamic precedence (signed) | production id |
| accept | 0 | 0 | 0 | 0 |
| recover | 0 | 0 | 0 | 0 |

The `index` field preserves the C array index so that parse-table cells
(`ACTIONS(n)` in `parser.c`) can be resolved to groups: a cell with value `n`
names the group whose `index == n`. A cell value of 0 means "no action".

## 6. Section 4 — large parse table

```
large_state_count × symbol_count × u16  (row-major)
```

Cell `[state][symbol]` holds the action-group index (see §5) when `symbol <
token_count` and a goto state id when `symbol >= token_count`, exactly like the
C `ts_parse_table[LARGE_STATE_COUNT][SYMBOL_COUNT]`. Value 0 = no entry.

## 7. Section 5 — small parse table

```
small_parse_table_word_count × u16
```

Raw words of the C `ts_small_parse_table[]`: for each small state, the word
stream is `[group_count, (group_value, symbol_count, symbol_id…)*]` where
`group_value` is an action-group index (terminals) or goto state id
(non-terminals). Values are stored exactly as they appear in `parser.c` after
macro expansion (`ACTIONS(n)`/`STATE(n)` → `n`, symbol names → ids).

## 8. Section 6 — small parse table map

```
small_parse_table_map_count × u32
```

Entry `i` is the word offset into the small parse table (§7) of small state
`large_state_count + i` — the same mapping as C `ts_small_parse_table_map[]`.

## 9. Section 7 — primary state ids

```
state_count × u16
```

Primary state id per parse state (C `ts_primary_state_ids[]`).

## 10. Section 8 — lex modes

```
lex_mode_count × (u16 lex_state, u16 external_lex_state, u16 reserved_word_set_id)
```

`lex_mode_count` equals `state_count`. Entry `i` carries the parse state's full
`TSLexerMode` from `ts_lex_modes[i]`: the lex state id the internal DFA must
lex with, the external scanner lex state (0 = no external scan for this state),
and the reserved-word set id (0 = none). v3 widened this section from a single
`u16` per state to the three-field record.

## 11. Section 9 — keyword lex modes

```
keyword_lex_mode_count × u16
```

Keyword lex state per parse state. The shipped JSON grammar has none (count 0);
the Java grammar has one per keyword-accepting DFA state.

## 12. Section 10 — field names

For each field name `i` in `0..field_name_count-1`:

```
u8  name_len
name_len × u8  name bytes (UTF-8)
```

Index 0 is the null field name (encoded as `name_len = 0`); names otherwise
match `ts_field_names` by field id.

## 13. Section 11 — field map slices

```
field_map_slice_count × (u16 index, u16 length)
```

Per production id: the slice of the field-map entry array (§12) that carries
this production's fields (C `ts_field_map_slices[PRODUCTION_ID_COUNT]`).

## 14. Section 12 — field map entries

```
field_map_entry_count × (u16 field_id, u16 child_index, u8 inherited)
```

The flat field map entries (C `ts_field_map_entries[]`).

## 15. Section 13 — alias sequences

```
alias_sequence_element_count × u16  (row-major: production_id × max_alias_sequence_length)
```

Row `p` is the alias sequence for production id `p`: element `c` is the symbol
applied to the child at structural index `c` (0 = no alias). Matches C
`ts_alias_sequences[PRODUCTION_ID_COUNT][MAX_ALIAS_SEQUENCE_LENGTH]`. Alias
symbols resolve through sections 1/2 like ordinary symbols.

## 16. Section 14 — non-terminal alias map

```
non_terminal_alias_map_count × u16
```

C `ts_non_terminal_alias_map[]` (sparse map of real symbol → alias symbols).

## 17. Section 15 — lexer automata

```
u8   lexer_fn_count        — must equal header lexer_fn_count
lexer_fn_count × lexer automaton
```

Each automaton is decoded mechanically from a `ts_lex` / `ts_lex_keywords`
function body:

```
u16  state_count           — number of DFA states (max state id + 1)
u16  accept_count          — number of (state, symbol, at_entry) accept records
u32  transition_count      — total transitions across all states
u16  set_count             — number of TSCharacterRange sets referenced
u32  set_range_count       — total range records across all sets
accept records (accept_count × 5 bytes): u16 state_id, u16 symbol_id, u8 at_entry
set ranges (set_range_count × 10 bytes): u16 set_id, u32 start, u32 end
per-state transition counts (state_count × u8)
transitions: for each state in id order, transition_count records:
  u16 next_state
  u8  skip       — 1 = C SKIP macro (consumed characters become token padding)
  u8  clause_count
  per clause: u8 literal_count, then literal_count × (u8 kind, u32 a, u32 b)
```

Literal kinds:

| kind | meaning | a | b |
| --- | --- | --- | --- |
| 1 | lookahead == a | char | 0 |
| 2 | lookahead != a | char | 0 |
| 3 | a <= lookahead <= b | range start | range end |
| 4 | lookahead in char set a | set id | 0 |
| 5 | lookahead != 0 | 0 | 0 |
| 6 | EOF | 0 | 0 |

A transition matches when any clause matches and a clause matches when every
literal matches. Transitions within a state are ordered and first-match wins,
mirroring the C case-body statement order. `accept_at_entry` records whether the
state's accept fires before (1) or after (0) the transitions.

## 18. Section 16 — external scanner symbol map

```
external_token_count × u16
```

External token ordinal → symbol id, matching C
`ts_external_scanner_symbol_map[EXTERNAL_TOKEN_COUNT]`. Empty when
`external_token_count` is 0.

## 19. Section 17 — external scanner states

```
external_lex_state_count × external_token_count × u8 (row-major)
```

`states[s][i] = 1` when external lex state `s` admits external token ordinal
`i` (C `ts_external_scanner_states[EXTERNAL_LEX_STATE_COUNT][EXTERNAL_TOKEN_COUNT]`).

## 20. Section 18 — reserved word sets

For each set `s` in `0..reserved_word_set_count-1`:

```
u8  length
length × u16 symbol
```

Row `s` is the reserved word set for `reserved_word_set_id = s` (C
`ts_reserved_words[s]`). `length` must not exceed
`max_reserved_word_set_size`. Set 0 is the null set (usually empty).

## 21. Section 19 — scanner program

```
u32  byte length
length × u8 bytecode
```

The compiled external-scanner program for the roadmap ISA (see §22). A length of
0 means the grammar has no external scanner program.

## 22. External scanner ISA (roadmap opcode set)

The bytecode is interpreted by `io.nop.treesitter.scanner.ScannerVM` and
produced by `io.nop.treesitter.scanner.ScannerCompiler` from a textual DSL (the
hand translation of an upstream `scanner.c`). All multi-byte operands are
big-endian. Instructions are variable-width; each instruction is its opcode byte
followed by opcode-specific operands.

### VM state

- **lookahead**: the UTF-8 codepoint at the lexer cursor; 0 at EOF (the C
  runtime's `lexer->lookahead == 0` convention). The cursor is a byte offset
  into the source.
- **position**: current byte offset; `ADVANCE` / `SKIP` / `SPAN` move it.
- **token start**: byte offset where the token content begins; initially the scan
  start, `SKIP` advances it (C `ts_lexer__advance` treats skipped characters as
  padding). The emitted token's start is the token start clamped to at most
  `mark_end` (C `ts_lexer_finish`), so a token whose content was entirely
  skipped (e.g. ASI) is zero-width.
- **mark_end**: token end position; initially the scan start.
- **result symbol**: set by `EMIT`; the token is `(symbol, start, mark_end)`.
- **result register** (i32): procedure return value, default 0.
- **flag register** (u8): auxiliary out-parameter (e.g. scanner.c's
  `*scanned_comment`).
- **state register** (u32): external-scanner payload state (the C
  `serialize`/`deserialize` payload). Stateless scanners (e.g. JavaScript) never
  touch it.
- **valid symbols**: boolean array indexed by external token ordinal (§19
  states row ∩ §18 symbol map, filtered by parse-table actions).
- **operand stack** (u8): literal byte stack for `PUSH_BYTE`/`PUSH_BYTES`/`SPAN`.
- **call stack**: return addresses for `CALL`/`RET`, bounded depth 64.

### Step budget

Each scan invocation runs with `max(program_length × 16, remaining_source × 4 +
128)` steps (mirroring the internal lexer's budget). Exceeding the budget raises
a typed `TreeSitterException` — an infinite loop is impossible.

### Opcodes

| Opcode | Mnemonic | Operands | Semantics |
| --- | --- | --- | --- |
| 0x00 | `FAIL` | — | Terminate the scan with no token. |
| 0x01 | `JMP` | u16 target | `pc = target`. |
| 0x02 | `JMP_IF_EQ` | i32 v, u16 t | `lookahead == v ? pc+1 : pc = t`. |
| 0x03 | `JMP_IF_NE` | i32 v, u16 t | `lookahead != v ? pc+1 : pc = t`. |
| 0x04 | `JMP_IF_IN_RANGE` | i32 lo, i32 hi, u16 t | `lo <= lookahead <= hi ? pc+1 : pc = t`. |
| 0x05 | `JMP_IF_WS` | u16 t | iswspace(lookahead) ? pc+1 : pc = t. |
| 0x06 | `JMP_IF_ALPHA` | u16 t | iswalpha(lookahead) ? pc+1 : pc = t. |
| 0x07 | `JMP_IF_DIGIT` | u16 t | iswdigit(lookahead) ? pc+1 : pc = t. |
| 0x08 | `JMP_IF_VALID` | u8 ordinal, u16 t | validSymbols[ordinal] ? pc+1 : pc = t. |
| 0x09 | `JMP_IF_STATE_EQ` | i32 v, u16 t | state == v ? pc+1 : pc = t. |
| 0x0A | `SET_STATE` | i32 v | state = v. |
| 0x0B | `ADVANCE` | — | Consume the lookahead codepoint as token content. |
| 0x0C | `SKIP` | — | Consume the lookahead codepoint as padding (C `advance(lexer, true)`). |
| 0x0D | `MARK_END` | — | Token end = current position. |
| 0x0E | `EMIT` | u16 symbol id | Result symbol = symbol id; terminate the scan with a token. |
| 0x0F | `CALL` | u16 target | Push `pc+1`, set result = 0, `pc = target`. |
| 0x10 | `RET` | — | `pc` = popped return address (procedure returns; result/flag preserved). |
| 0x11 | `SET_RESULT` | i32 v | result = v. |
| 0x12 | `JMP_IF_RESULT_EQ` | i32 v, u16 t | result == v ? pc+1 : pc = t. |
| 0x13 | `SET_FLAG` | u8 v | flag = v. |
| 0x14 | `JMP_IF_FLAG_EQ` | u8 v, u16 t | flag == v ? pc+1 : pc = t. |
| 0x15 | `PUSH_BYTE` | u8 v | Push v onto the operand stack. |
| 0x16 | `PUSH_BYTES` | u16 len, len×u8 | Push len bytes onto the operand stack. |
| 0x17 | `SPAN` | u8 len, u16 failT | Pop len bytes (FIFO); for each, if the raw source byte at the cursor equals it, advance the cursor as content, else `pc = failT`; all matched → pc+1. |

### Character classes

`JMP_IF_WS` matches `0x09`–`0x0D`, `0x20` and the Unicode whitespace runes
`0x85`, `0xA0`, `0x1680`, `0x2000`–`0x200A`, `0x2028`, `0x2029`, `0x202F`,
`0x205F`, `0x3000` (C `iswspace`). `JMP_IF_ALPHA` matches Unicode letters (C
`iswalpha`). `JMP_IF_DIGIT` matches `0x30`–`0x39` (C `iswdigit`).

### Load-time validation

`ScannerCompiler` and `ScannerVM.load` reject programs that fail any of: unknown
opcode, malformed operands, jump target outside `[0, program_length)` or not on
an instruction boundary, `SPAN` length exceeding the statically reachable
operand-stack height on any control-flow path, and a jump into the middle of an
instruction. All violations raise a typed `IllegalStateException` with the
program offset — nothing is silently skipped.

## 23. Determinism and validation

- Producing the same `parser.c` twice yields byte-identical output (no map
  iteration, no timestamps). When a `scanner.dsl` file sits next to the
  `parser.c`, the compiled scanner program is embedded; the DSL compiles
  deterministically too.
- The writer throws `IllegalStateException` when a value does not fit its
  declared width (e.g. a symbol name longer than 255 bytes, a count above its
  width) instead of truncating.
- The reader throws `IllegalStateException` on wrong magic, unsupported format
  version (v1/v2 blobs are rejected with a typed "unsupported blob format
  version" error), inconsistent cross-section counts, or truncated / trailing
  data.