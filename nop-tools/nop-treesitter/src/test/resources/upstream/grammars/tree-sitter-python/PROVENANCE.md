# Provenance

Vendored from the upstream `tree-sitter-python` repository (MIT license), read-only reference
copy at `~/sources/treesitter/grammars/tree-sitter-python/`.

| Field | Value |
|---|---|
| Upstream | `https://github.com/tree-sitter/tree-sitter-python` |
| Tag | none — this commit carries no upstream tag |
| Commit SHA | `26855eabccb19c6abf499fbc5b8dc7cc9ab8bc64` |
| Commit date | 2025-09-15 |
| License | MIT, (c) Max Brunsfeld (verified in the reference copy's `LICENSE`; the `LICENSE` file itself is **not** vendored) |
| Generator | `tree-sitter v0.25.9` (per the `src/parser.c` header banner) |
| Grammar version | `0.25.0` (per upstream `Cargo.toml` at the pinned commit) | `LANGUAGE_VERSION 15` per `parser.c` |
## Vendored files

| File | SHA-256 | Role |
|---|---|---|
| `src/parser.c` | `53a7bd87ba22c4fdbb8566a2caf848a1478f3ac9d47a1b32677a71de75d75db6` | input to `Ts2Java`; produces `grammars/python/tree-sitter-python-blob.bin` |
| `src/scanner.c` | `6db82134ac2d4c90a1a1475487a625cface02662ebda9b7478cad9c7147e9afe` | reference for the hand-translated external scanner (`PythonScanner`) |
| `test/corpus/*.txt` | 5 file(s) | corpus fixture |

## Policy

**Never modified.** No local edits, no patches, no re-generation. The vendored
`src/parser.c` and `src/scanner.c` were verified
byte-identical to upstream commit `26855eabccb1` by comparing git blob hashes
against the reference copy's `HEAD`. `src/parser.c` is generated upstream; it is
vendored as-is and is the single input to the shipped blob — regenerate the blob
with `Ts2Java` only after re-vendoring a newer upstream commit.

The `scanner.c` for this grammar was vendored on 2026-10-04 by the provenance
backfill plan. Before that it was absent from this repository, and `PythonScanner` had
been hand-translated from a source that was not vendored — so its fidelity could
not be checked against anything in-tree. After vendoring, the external-token
ordinals in `PythonScanner` match this file **12/12**. The `serialize` state
layout corresponds field-for-field (order, widths, the 255 clamp on delimiters,
and the `iter = 1` baseline skip) with one known divergence: this file bounds the
indent loop with `size < TREE_SITTER_SERIALIZATION_BUFFER_SIZE` and
`PythonScanner.deserialize` applies no such bound. That is pre-existing behaviour,
not a defect introduced here, but the layouts are not byte-equivalent in effect.

Format documented in `../tree-sitter-bash/PROVENANCE.md`.
