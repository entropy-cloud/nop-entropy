# Provenance

Vendored from the upstream `tree-sitter-go` repository (MIT license), read-only reference
copy at `~/sources/treesitter/grammars/tree-sitter-go/`.

| Field | Value |
|---|---|
| Upstream | `https://github.com/tree-sitter/tree-sitter-go` |
| Tag | none — this commit carries no upstream tag |
| Commit SHA | `2346a3ab1bb3857b48b29d779a1ef9799a248cd7` |
| Commit date | 2025-09-15 |
| License | MIT, (c) Max Brunsfeld (verified in the reference copy's `LICENSE`; the `LICENSE` file itself is **not** vendored) |
| Generator | `tree-sitter v0.25.8` (per the `src/parser.c` header banner) |
| Grammar version | `0.25.0` (per upstream `Cargo.toml` at the pinned commit) | `LANGUAGE_VERSION 15` per `parser.c` |
## Vendored files

| File | SHA-256 | Role |
|---|---|---|
| `src/parser.c` | `3dbf6ed1238b5dfcf2be4d2f2d4cb27a14d34f34d7784eccccbfd532fd4a6d85` | input to `Ts2Java`; produces `grammars/go/tree-sitter-go-blob.bin` |
| `test/corpus/*.txt` | 7 file(s) | corpus fixture |

## Policy

**Never modified.** No local edits, no patches, no re-generation. The vendored
`src/parser.c` were verified
byte-identical to upstream commit `2346a3ab1bb3` by comparing git blob hashes
against the reference copy's `HEAD`. `src/parser.c` is generated upstream; it is
vendored as-is and is the single input to the shipped blob — regenerate the blob
with `Ts2Java` only after re-vendoring a newer upstream commit.

Format documented in `../tree-sitter-bash/PROVENANCE.md`.
