# Provenance

Vendored from the upstream `tree-sitter-json` repository (MIT license), read-only reference
copy at `~/sources/treesitter/grammars/tree-sitter-json/`.

| Field | Value |
|---|---|
| Upstream | `https://github.com/tree-sitter/tree-sitter-json` |
| Tag | none — this commit carries no upstream tag |
| Commit SHA | `254c42a6476413b776221e03982ac8ae159eeb72` |
| Commit date | 2026-08-17 |
| License | MIT, (c) Max Brunsfeld (verified in the reference copy's `LICENSE`; the `LICENSE` file itself is **not** vendored) |
| Generator | not stamped — the `src/parser.c` has no `@generated` banner at all |
| Grammar version | `0.24.8` (per upstream `Cargo.toml` at the pinned commit) | `LANGUAGE_VERSION 14` per `parser.c` |
## Vendored files

| File | SHA-256 | Role |
|---|---|---|
| `src/parser.c` | `9a266ae4a0305b8a2769bee90a14ac06914bdc83a509f16428311eac2845637e` | input to `Ts2Java`; produces `grammars/json/tree-sitter-json-blob.bin` |
| `queries/highlights.scm` | `0511524465b56aed122580792254e68b6abbbfde7119f1d02b135acbe278233f` | query fixture |
| `test/corpus/*.txt` | 1 file(s) | corpus fixture |

## Policy

**Never modified.** No local edits, no patches, no re-generation. The vendored
`src/parser.c` were verified
byte-identical to upstream commit `254c42a64764` by comparing git blob hashes
against the reference copy's `HEAD`. `src/parser.c` is generated upstream; it is
vendored as-is and is the single input to the shipped blob — regenerate the blob
with `Ts2Java` only after re-vendoring a newer upstream commit.

Format documented in `../tree-sitter-bash/PROVENANCE.md`.
