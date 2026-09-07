# nop-treesitter blob format (format version 1)

> **Loader contract.** This document is the authoritative specification of the
> binary blob produced by `io.nop.treesitter.codegen.Ts2Java` and consumed by
> the `Language` loader of plan `2026-09-07-1713-3-lr1-parser-json-corpus.md`.
> The reader must be implementable from this document alone; the round-trip
> test in `src/test/java/io/nop/treesitter/codegen/BlobRoundTripTest.java`
> proves the shipped blob decodes against it.

All multi-byte integers are **big-endian**. The blob is a fixed header followed
by nine sections in a fixed order. Every count is recorded in the header so a
reader can validate total size before decoding.

## 1. Header (fixed 64 bytes)

| Offset | Size | Field | Width rationale |
| --- | --- | --- | --- |
| 0 | 4 | magic `54 53 4A 42` ("TSJB") | — |
| 4 | 1 | format version = `0x01` | — |
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
| 32 | 32 | reserved zeros | — |

Width decision (recorded for the Phase-3 `Decision` item): symbol ids ≤ 255
would fit in 1 byte, but counts and state ids are kept at u16 to mirror the C
runtime `TSStateId` / `TSSymbol` types and to leave headroom for grammars with
more than 255 symbols or states. Symbol *names* use a u8 length prefix because
no upstream symbol name exceeds 255 bytes.

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

The reader must not assume any section is non-empty; each count in the header
may be zero (e.g. keyword lex modes for the JSON grammar are absent).

## 3. Section 1 — symbol names

For each symbol `i` in `0..symbol_count-1`:

```
u8  name_len
name_len × u8  name bytes (UTF-8)
```

The name at index 0 is `"end"` (`ts_builtin_sym_end`). Names are written in
symbol-id order.

## 4. Section 2 — symbol metadata

For each symbol `i` in `0..symbol_count-1`:

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
state `i` must lex with (C `ts_lex_modes[i].lex_state`). The JSON grammar uses
only lex states 0 and 1.

## 11. Section 9 — keyword lex modes

```
keyword_lex_mode_count × u16
```

Keyword lex state per parse state, used by the lexer for keyword-vs-identifier
resolution. The vendored tree-sitter-json grammar has **no** keyword lexer, so
the shipped JSON blob has `keyword_lex_mode_count = 0` and an empty section;
the section exists so grammars with keyword lexers can be encoded later.

## 12. Determinism and validation

- Producing the same `parser.c` twice yields byte-identical output (no map
  iteration, no timestamps).
- The writer throws `IllegalStateException` when a value does not fit its
  declared width (e.g. a symbol name longer than 255 bytes, a count above
  u16 range) instead of truncating.
- The reader throws `IllegalStateException` on wrong magic, unsupported format
  version, or truncated / trailing data.