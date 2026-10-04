# Vendored Upstream Grammar Policy

This directory holds read-only, vendored copies of upstream tree-sitter grammar
sources. They are test fixtures and codegen inputs — **never modified**.

## Per-directory provenance

| Directory | Per-directory `PROVENANCE.md` | Precise tag + SHA recorded |
|---|---|---|
| `tree-sitter-bash/` | yes | yes — `v0.25.1` / `a06c2e4415e9bc0346c6b86d401879ffb44058f7` |
| `tree-sitter-typescript/` | yes | no — historical "commit-less snapshot" wording |
| `tree-sitter-json/`, `-java/`, `-javascript/`, `-python/`, `-go/`, `-rust/`, `-c-sharp/` | no | no |

## Why the other seven are not backfilled with tag + SHA

Backfilling a tag + commit SHA is only honest if the **vendored bytes** are
verified to come from that commit. Measured on 2026-10-04:

Measured on 2026-10-04 (`shasum -a 256` against
`~/sources/treesitter/grammars/*`):

| Directory | vendored `src/parser.c` == local reference copy | vendored `src/scanner.c` | upstream has `scanner.c` |
|---|---|---|---|
| `tree-sitter-json` | yes | absent | no (no external tokens) |
| `tree-sitter-java` | yes | absent | no (no external tokens) |
| `tree-sitter-go` | yes | absent | no (no external tokens) |
| `tree-sitter-javascript` | yes | present, matches | yes |
| `tree-sitter-c-sharp` | yes | present, matches | yes |
| `tree-sitter-python` | yes | **absent** | yes (437 lines) |
| `tree-sitter-rust` | yes | **absent** | yes (403 lines) |
| `tree-sitter-typescript` | n/a — split `typescript/src/parser.c` + `tsx/src/parser.c` | `common/scanner.h` + `common/scanner.dsl` (DSL, not C) | n/a |

Two blockers make a blanket backfill an *invented* record rather than a
verified one:

1. `tree-sitter-python` and `tree-sitter-rust` vendor **only** `parser.c`.
   Their hand-translated scanners (`PythonScanner`, `RustScanner`) were written
   against a `scanner.c` that is not in the repository, so no SHA can be
   attached to the scanner source that produced them. Vendoring the missing
   `scanner.c` for those two grammars is itself a separate change.
2. The local reference copies (`~/sources/treesitter/grammars/*`) are not
   pinned — they track `master` and have moved past the vendored bytes.
   Attaching their current `HEAD` would describe the reference copy, not the
   vendored fixture.

Recording an unverified SHA would introduce exactly the owner-doc drift this
directory policy exists to prevent. New vendoring must use the normalized
per-directory format documented in `tree-sitter-bash/PROVENANCE.md`.

## Rules

- Vendored `parser.c` / `scanner.c` / `test/corpus/*` are **byte-identical**
  copies of an upstream tag. No patches, no re-generation, no formatting.
- Shipped grammar blobs under `src/main/resources/grammars/<name>/` are
  regenerated from these sources via `Ts2Java`; never hand-edited.
- A vendored-directory update is a separate change from any code change: it
  re-copies the sources, re-verifies the corpus, and updates
  `PROVENANCE.md` in the same commit.
