# Provenance

Vendored from the upstream `tree-sitter-java` repository (MIT license), read-only reference
copy at `~/sources/treesitter/grammars/tree-sitter-java/`.

| Field | Value |
|---|---|
| Upstream | `https://github.com/tree-sitter/tree-sitter-java` |
| Tag | none — this commit carries no upstream tag |
| Commit SHA | `e10607b45ff745f5f876bfa3e94fbcc6b44bdc11` |
| Commit date | 2025-09-14 |
| License | MIT, (c) Ayman Nadeem (verified in the reference copy's `LICENSE`; the `LICENSE` file itself is **not** vendored) |
| Generator | not stamped — the `src/parser.c` has no `@generated` banner at all |
| Grammar version | `0.23.5` (per upstream `Cargo.toml` at the pinned commit) | `LANGUAGE_VERSION 14` per `parser.c` |
## Vendored files

| File | SHA-256 | Role |
|---|---|---|
| `src/parser.c` | `4add5150cf4531eb5dd97f3343dcf65cd11704c84711348b328582b83424a0e4` | input to `Ts2Java`; produces `grammars/java/tree-sitter-java-blob.bin` |
| `test/corpus/*.txt` | 6 file(s) | corpus fixture |

## Policy

**Never modified.** No local edits, no patches, no re-generation. The vendored
`src/parser.c` were verified
byte-identical to upstream commit `e10607b45ff7` by comparing git blob hashes
against the reference copy's `HEAD`. `src/parser.c` is generated upstream; it is
vendored as-is and is the single input to the shipped blob — regenerate the blob
with `Ts2Java` only after re-vendoring a newer upstream commit.

Format documented in `../tree-sitter-bash/PROVENANCE.md`.
