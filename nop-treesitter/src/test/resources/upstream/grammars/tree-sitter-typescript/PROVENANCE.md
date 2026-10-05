# Provenance

Vendored from the upstream `tree-sitter-typescript` repository (MIT license),
read-only reference copy at `~/sources/treesitter/grammars/tree-sitter-typescript/`.

| Field | Value |
|---|---|
| Upstream | `https://github.com/tree-sitter/tree-sitter-typescript` |
| Tag | none — this commit carries no upstream tag |
| Commit SHA | `75b3874edb2dc714fb1fd77a32013d0f8699989f` |
| Commit date | 2025-01-30 |
| License | MIT, (c) Max Brunsfeld (verified in the reference copy's `LICENSE`; the `LICENSE` file itself is **not** vendored) |
| Generator | not stamped — neither `typescript/src/parser.c` nor `tsx/src/parser.c` has an `@generated` banner |
| Grammar version | `0.23.2` (per upstream `Cargo.toml` at the pinned commit) | `LANGUAGE_VERSION 14` per `parser.c` |
This grammar has a **split layout**: two grammars (`typescript`, `tsx`) share one
`common/` scanner. There is no single `src/parser.c`.

## Vendored files

| File | SHA-256 | Role |
|---|---|---|
| `typescript/src/parser.c` | `74fe453edd70f4eae9af0a1050cbd7943d8971d59165b6aaebbaa0a0b716d1aa` | input to `Ts2Java`; produces `grammars/typescript/tree-sitter-typescript-blob.bin` |
| `tsx/src/parser.c` | `1902cb53fa7ff5179df89b2eea863165e84c8cc866226419dc26921d8c055885` | input to `Ts2Java`; produces `grammars/tsx/tree-sitter-tsx-blob.bin` |
| `typescript/src/scanner.c` | `9125013b42cb888379d9be909f1d73dfb75a37626c2cdbf4122718a2b431a6d3` | thin C shim delegating to `common/scanner.h` |
| `tsx/src/scanner.c` | `d563cd30b2f39718c9ae4292795c5ce03a2ad01954ba3a86ef84c2781a736673` | thin C shim delegating to `common/scanner.h` |
| `common/scanner.h` | `da66ef2bd14a3f7ea743e25ba068c6c9aae2c3509db200ff80c4a0e6116e564c` | upstream scanner implementation shared by both shims |
| `common/scanner.dsl` | `9127645ff78602176f3d09f4320241b360b29d1a3b8aa04a9bbd16d55eadb120` | **locally authored** — see below |
| `typescript/src/scanner.dsl` | `9127645ff78602176f3d09f4320241b360b29d1a3b8aa04a9bbd16d55eadb120` | **locally authored** copy of `common/scanner.dsl` |
| `tsx/src/scanner.dsl` | `9127645ff78602176f3d09f4320241b360b29d1a3b8aa04a9bbd16d55eadb120` | **locally authored** copy of `common/scanner.dsl` |
| `test/corpus/*.txt` | 4 file(s) | corpus fixture |

## Provenance of the `.dsl` files — read this before trusting them

`common/scanner.dsl`, `typescript/src/scanner.dsl` and `tsx/src/scanner.dsl`
have **no upstream source**. At the pinned commit, upstream
`tree-sitter-typescript` ships no `.dsl` file — the C implementation is
`common/scanner.h` plus per-grammar `scanner.c` shims, all of which *are*
vendored here and verified byte-identical. (Checked at commit `75b3874edb2`; a
claim that no *historical* commit ever shipped one was not verified, because the
reference copy is a shallow clone.)

The three `.dsl` files are **locally authored** inputs to this repository's own
scanner-DSL pipeline (`ScannerCompiler` / `ScannerVM`), which compiles them into
the Java scanners. All three are the same 538-line definition (identical SHA-256
above), kept in three places because each generated scanner reads its own copy.

Consequences a reader must not gloss over:

- Their provenance is **this repository**, not upstream. They are the one vendored
  artefact here that is not a copy of an upstream file.
- They are therefore **not covered** by the "never modified, byte-identical to
  upstream" policy that governs every other file in this directory.
- Their fidelity to upstream `common/scanner.h` is **not machine-checked** by any
  test. The DSL pipeline is exercised by the scanner tests, but nothing asserts
  that the DSL and the header describe the same scanner.

## Policy

**Never modified (upstream-derived files).** The upstream-derived files listed
above were verified byte-identical to upstream commit `75b3874edb2` by comparing
git blob hashes against the reference copy's `HEAD`. No local edits, no patches,
no re-generation. `*/src/parser.c` is generated upstream; it is vendored as-is and
is the single input to the shipped blobs — regenerate a blob with `Ts2Java` only
after re-vendoring a newer upstream commit.

**Locally authored (`.dsl` files).** Edit these freely as this repository's own
scanner source, but keep the three copies in sync, and keep them consistent with
`common/scanner.h`. Changing a `.dsl` is a behaviour change to the Java scanner
and requires re-running the scanner tests.

Format documented in `../tree-sitter-bash/PROVENANCE.md`.
