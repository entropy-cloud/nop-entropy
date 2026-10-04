# Provenance

Vendored from the upstream `tree-sitter-bash` repository (MIT license),
read-only reference copy at `~/sources/treesitter/grammars/tree-sitter-bash/`.

| Field | Value |
|---|---|
| Upstream | `https://github.com/tree-sitter/tree-sitter-bash` |
| Tag | `v0.25.1` |
| Commit SHA | `a06c2e4415e9bc0346c6b86d401879ffb44058f7` |
| Commit date | 2025-12-02 |
| License | MIT (see `LICENSE`) |
| Generator | `tree-sitter v0.25.10` (per the `src/parser.c` header banner) |
| Grammar version | `0.25.1` (per `tree-sitter.json` / `Cargo.toml`) |

## Vendored files

| File | SHA-256 | Role |
|---|---|---|
| `src/parser.c` | `5ad30bb1a260c76df5397490b8bd97e272e62ed9c99be36fa53da959d7667e7f` | input to `Ts2Java`; produces `grammars/bash/tree-sitter-bash-blob.bin` |
| `src/scanner.c` | `7cc25d70626f8939b35ecd504bf724e2001b817412aa29bceb4c4955a91558a9` | reference for the `BashScanner` hand translation |
| `LICENSE` | `49bf33cf78ef5897e4e161ce1517df7de1ae5042a65b6bcfd44401e0fc606559` | MIT license text — the only vendored `LICENSE` in this tree |
| `src/node-types.json` | `b644629f7f9460cb0af080cb88f3bef898e6c47b6f8a76ed512b67ae16ef5ac5` | node/field contract reference (`ai-dev/analysis/nop-treesitter-bash-grammar-contracts.md`) |
| `src/grammar.json` | `6c14f325170826cbc6c523ff5c2aeaa46f24bbff3a6e5eadb4533d9838fe3d81` | grammar rule reference |
| `test/corpus/*.txt` | — | 100 sections across 5 files; drive `BashCorpusTest`  |

## Policy

**Never modified.** No local edits, no patches, no re-generation. The vendored
files are byte-identical to the upstream tag `v0.25.1` (verified by SHA-256
against the reference copy). `src/parser.c` is generated upstream; it is
vendored as-is and is the single input to the shipped blob — regenerate the
blob with `Ts2Java` only after re-vendoring a newer upstream tag.

This is the **normalized provenance format** for this repository: it records
tag + commit SHA + license + generator version + "never modified" explicitly.
All eight other vendored directories were backfilled into this format on
2026-10-04 (see `../PROVENANCE.md`). Four vendored files are **not** upstream
copies — the `*.dsl` files, three in `tree-sitter-typescript` and one in
`tree-sitter-javascript`; each of those two records flags them.
