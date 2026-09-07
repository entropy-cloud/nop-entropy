# nop-treesitter blob format (format version 2)

> **Loader contract.** This document is the authoritative specification of the
> binary blob produced by `io.nop.treesitter.codegen.Ts2Java` and consumed by
> the `Language` loader. The reader must be implementable from this document
> alone; the round-trip tests in
> `src/test/java/io/nop/treesitter/codegen/BlobRoundTripTest.java` prove the
> shipped blobs decode against it.

All multi-byte integers are **big-endian**. The blob is a fixed header followed
by sixteen sections in a fixed order. Every count is recorded in the header so a
reader can validate total size before decoding.

Format version 2 adds the field/alias tables and the serialized lexer automata
(the v1 lex-mode section is retained, but the runtime lexer is now table-driven
from the automata instead of hand-translated per grammar).

## 1. Header (fixed 96 bytes)

| Offset | Size | Field | Width rationale |
| --- | --- | --- | --- |
| 0 | 4 | magic `54 53 4A 42` ("TSJB") | — |
| 4 | 1 | format version = `0x02` | — |
| 5 | 1 | language ABI version (`LANGUAGE_VERSION` from `parser.c`, e.g. `14`) | — |
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
| 54 | 1 | lexer fn count | 1 (main) or 2 (main + keyword) |
| 55 | 41 | reserved zeros | — |

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
lex_mode_count × u16
```

`lex_mode_count` equals `state_count`. Entry `i` is the lex state id that parse
state `i` must lex with (C `ts_lex_modes[i].lex_state`).

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

## 18. Determinism and validation

- Producing the same `parser.c` twice yields byte-identical output (no map
  iteration, no timestamps).
- The writer throws `IllegalStateException` when a value does not fit its
  declared width (e.g. a symbol name longer than 255 bytes, a count above its
  width) instead of truncating.
- The reader throws `IllegalStateException` on wrong magic, unsupported format
  version (v1 blobs are rejected with a typed "unsupported blob format version"
  error), inconsistent cross-section counts, or truncated / trailing data.
