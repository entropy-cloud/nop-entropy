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
| `src/node-types.json` | — | node/field contract reference (`ai-dev/analysis/nop-treesitter-bash-grammar-contracts.md`) |
| `src/grammar.json` | — | grammar rule reference |
| `test/corpus/*.txt` | — | 100 sections across 5 files; drive `BashCorpusTest` |

## Policy

**Never modified.** No local edits, no patches, no re-generation. The vendored
files are byte-identical to the upstream tag `v0.25.1` (verified by SHA-256
against the reference copy). `src/parser.c` is generated upstream; it is
vendored as-is and is the single input to the shipped blob — regenerate the
blob with `Ts2Java` only after re-vendoring a newer upstream tag.

This is the **new normalized provenance format** for this repository: it
records tag + commit SHA + license + generator version + "never modified"
explicitly, rather than the older "commit-less snapshot" phrasing used by
`tree-sitter-typescript/PROVENANCE.md`. The other six vendored grammar
directories are **not** backfilled by the bash plan — see
`ai-dev/analysis/nop-treesitter-bash-grammar-contracts.md` § "Provenance
Backfill Adjudication" for the reason and the module-level policy file that
covers them.
