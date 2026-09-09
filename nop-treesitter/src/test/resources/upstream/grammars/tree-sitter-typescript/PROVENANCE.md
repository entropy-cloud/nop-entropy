# Provenance

Vendored from the upstream `tree-sitter-typescript` repository (MIT license),
reference copy at `~/sources/treesitter/grammars/tree-sitter-typescript/`.
Read-only test fixtures: the corpus files drive `TsCorpusTest` / `TsxCorpusTest`;
`parser.c` / `scanner.h` drive `ParserCExtractor` and the scanner-DSL hand
translation. Never modified; regenerate the shipped grammar blobs from these
sources via the codegen tests. Vendoring policy follows the
`tree-sitter-javascript` precedent (commit-less snapshot, no local edits).
