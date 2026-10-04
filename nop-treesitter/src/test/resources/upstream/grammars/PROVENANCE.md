# Vendored Upstream Grammar Policy

This directory holds read-only, vendored copies of upstream tree-sitter grammar
sources. They are test fixtures and codegen inputs — **never modified**.

## Per-directory provenance

Every directory now carries its own verified `PROVENANCE.md` in the normalized
format (documented in `tree-sitter-bash/PROVENANCE.md`). This file holds policy
only; the measured facts live per directory.

| Directory | Upstream commit | Tagged | `scanner.c` vendored | Corpus files |
|---|---|---|---|---|
| `tree-sitter-bash/` | `a06c2e4415e9bc0346c6b86d401879ffb44058f7` | `v0.25.1` | yes | 5 |
| `tree-sitter-c-sharp/` | `9150f7d56bb47f1a809fa23623f1ba1413e93fa9` | no | yes | 19 |
| `tree-sitter-go/` | `2346a3ab1bb3857b48b29d779a1ef9799a248cd7` | no | n/a (no external tokens) | 7 |
| `tree-sitter-java/` | `e10607b45ff745f5f876bfa3e94fbcc6b44bdc11` | no | n/a (no external tokens) | 6 |
| `tree-sitter-javascript/` | `58404d8cf191d69f2674a8fd507bd5776f46cb11` | no | yes | 6 |
| `tree-sitter-json/` | `254c42a6476413b776221e03982ac8ae159eeb72` | no | n/a (no external tokens) | 1 |
| `tree-sitter-python/` | `26855eabccb19c6abf499fbc5b8dc7cc9ab8bc64` | no | yes (vendored 2026-10-04) | 5 |
| `tree-sitter-rust/` | `77a3747266f4d621d0757825e6b11edcbf991ca5` | `v0.24.2` | yes (vendored 2026-10-04) | 9 |
| `tree-sitter-typescript/` | `75b3874edb2dc714fb1fd77a32013d0f8699989f` | no | split: `common/scanner.h` + two C shims | 4 |

Seven of the nine commits carry **no upstream tag** (all but `bash` and `rust`);
their provenance is the raw commit SHA, and no tag is asserted for them. Each
negative was verified against upstream tag refs, not inferred from a shallow clone
lacking tags.

## The exception to "never modified": four `.dsl` files

Four vendored files are **locally authored** rather than upstream copies. All
four are `*.dsl` — inputs to this repository's own scanner-DSL pipeline
(`ScannerCompiler` / `ScannerVM`), which compiles them into the Java scanners.
Upstream ships **no** `.dsl` file for either grammar.

| File | Lines | Origin |
|---|---|---|
| `tree-sitter-typescript/common/scanner.dsl` | 538 | locally authored |
| `tree-sitter-typescript/typescript/src/scanner.dsl` | 538 | locally authored (copy of `common/scanner.dsl`) |
| `tree-sitter-typescript/tsx/src/scanner.dsl` | 538 | locally authored (copy of `common/scanner.dsl`) |
| `tree-sitter-javascript/src/scanner.dsl` | 657 | locally authored hand translation of upstream `src/scanner.c` |

The three typescript copies are byte-identical to each other (same SHA-256). The
javascript one is a different, larger translation. See each directory's
`PROVENANCE.md`.

**Every other file in this directory is an unmodified upstream copy.** Note that
nothing machine-checks these four translations against the upstream `scanner.c`
/ `scanner.h` they were derived from — they are the one place where the
"byte-identical to upstream" guarantee does not apply, and where drift could go
unnoticed.

## Rules

- Vendored `parser.c` / `scanner.c` / `test/corpus/*` are **byte-identical**
  copies of an upstream commit. No patches, no re-generation, no formatting.
- Shipped grammar blobs under `src/main/resources/grammars/<name>/` are
  regenerated from these sources via `Ts2Java`; never hand-edited.
- A vendored-directory update is a separate change from any code change: it
  re-copies the sources, re-verifies the corpus, and updates the per-directory
  `PROVENANCE.md` in the same commit.
- Record a commit SHA **only** after verifying the vendored bytes against it.
  An unverified SHA is worse than no SHA, because it reads as verified.
- Where the upstream commit carries no tag, say so. Do not record a nearby tag.
