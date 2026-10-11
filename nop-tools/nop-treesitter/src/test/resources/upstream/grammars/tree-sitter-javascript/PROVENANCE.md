# Provenance

Vendored from the upstream `tree-sitter-javascript` repository (MIT license), read-only reference
copy at `~/sources/treesitter/grammars/tree-sitter-javascript/`.

| Field | Value |
|---|---|
| Upstream | `https://github.com/tree-sitter/tree-sitter-javascript` |
| Tag | none — this commit carries no upstream tag |
| Commit SHA | `58404d8cf191d69f2674a8fd507bd5776f46cb11` |
| Commit date | 2025-09-14 |
| License | MIT, (c) Max Brunsfeld (verified in the reference copy's `LICENSE`; the `LICENSE` file itself is **not** vendored) |
| Generator | `tree-sitter v0.25.8` (per the `src/parser.c` header banner) |
| Grammar version | `0.25.0` (per upstream `Cargo.toml` at the pinned commit) | `LANGUAGE_VERSION 15` per `parser.c` |
## Vendored files

| File | SHA-256 | Role |
|---|---|---|
| `src/parser.c` | `67209ca7ef6e1a4f74e29e48b5928455f892fe1821a3960fbcd62f4e972f7384` | input to `Ts2Java`; produces `grammars/javascript/tree-sitter-javascript-blob.bin` |
| `src/scanner.c` | `b3d3f64284d97bf80749c026862427782cf7ecc0b7dc094e6698ab311c9a42c7` | reference for the hand-translated external scanner (`JavaScriptScanner`) |
| `src/scanner.dsl` | `709d4728a3279fdfd8249e06bba2bc91343ed3579d6cfecce83c6920202a6d2c` | **locally authored** — see below |
| `test/corpus/*.txt` | 6 file(s) | corpus fixture |

## Provenance of `src/scanner.dsl` — locally authored, not upstream

This file has **no upstream source**. At the pinned commit upstream
`tree-sitter-javascript` ships no `.dsl` file — only `src/scanner.c`, which *is*
vendored here and verified byte-identical.

`src/scanner.dsl` is a **hand translation** of that upstream `scanner.c` into this
repository's own scanner DSL (as its first line states), compiled by
`ScannerCompiler` / `ScannerVM` into the Java scanner. So the honest chain is:
upstream `scanner.c` → (hand translation, local) → `scanner.dsl` → (compiled) →
Java scanner.

It is therefore **not** covered by the "never modified" policy below, which
applies to the upstream-derived files only, and nothing machine-checks the
translation against the upstream `scanner.c`.

## Policy

**Never modified (upstream-derived files: `src/parser.c`, `src/scanner.c`,
`test/corpus/*`).** No local edits, no patches, no re-generation. The vendored
`src/parser.c` and `src/scanner.c` were verified
byte-identical to upstream commit `58404d8cf191` by comparing git blob hashes
against the reference copy's `HEAD`. `src/parser.c` is generated upstream; it is
vendored as-is and is the single input to the shipped blob — regenerate the blob
with `Ts2Java` only after re-vendoring a newer upstream commit.

Format documented in `../tree-sitter-bash/PROVENANCE.md`.
