# Vendored grammar provenance backfill: pin the sources, fill the gaps

> Plan Status: draft
> Last Reviewed: 2026-10-04
> Source: `ai-dev/plans/nop-treesitter/2026-10-04-1430-1-bash-grammar-and-shell-analysis.md` Phase 0 provenance adjudication, recorded in `ai-dev/analysis/nop-treesitter-bash-grammar-contracts.md` § 9
> Related: `nop-treesitter/src/test/resources/upstream/grammars/PROVENANCE.md`

## Purpose

Give every vendored grammar directory a verified provenance record, and close the
two gaps that currently make a record impossible to write honestly: the missing
`scanner.c` for python and rust, and the unpinned local reference copies.

## Current Baseline

`tree-sitter-bash/PROVENANCE.md` now records tag + commit SHA + license +
generator version + "never modified" + a SHA-256 per vendored file. The other
seven directories do not, and two of them *cannot* until this plan lands.

Measured 2026-10-04:

| Directory | vendored `parser.c` | vendored `scanner.c` | upstream has `scanner.c` | per-directory provenance |
|---|---|---|---|---|
| `tree-sitter-json` | yes | absent | no (no external tokens) | none |
| `tree-sitter-java` | yes | absent | no (no external tokens) | none |
| `tree-sitter-go` | yes | absent | no (no external tokens) | none |
| `tree-sitter-javascript` | yes | present, matches | yes | none |
| `tree-sitter-c-sharp` | yes | present, matches | yes | none |
| `tree-sitter-python` | yes | **absent** | yes (437 lines) | none |
| `tree-sitter-rust` | yes | **absent** | yes (403 lines) | none |
| `tree-sitter-typescript` | split (`typescript/src`, `tsx/src`) | `common/scanner.h` + `common/scanner.dsl` | n/a | yes, but "commit-less snapshot" |

The local reference copies (`~/sources/treesitter/grammars/*`) are shallow
clones tracking `master`; their `HEAD` has moved past the vendored bytes, so
attaching it would describe the reference copy rather than the fixture.

Two consequences:

- `PythonScanner` and `RustScanner` were hand-translated from a `scanner.c` that
  is not in the repository. Their fidelity is currently unverifiable from the
  vendored sources alone — which is a real reviewability gap, not just a
  bookkeeping one.
- `tree-sitter-typescript/PROVENANCE.md`'s "commit-less snapshot, no local
  edits" wording conflicts with the normalized format, and the `dsl` scanner
  pipeline (`ScannerCompiler` / `ScannerVM`) has no recorded upstream source
  reference at all.

## Goals

- every vendored grammar directory carries a verified provenance record in the
  normalized format
- the `scanner.c` that produced `PythonScanner` and `RustScanner` is in the
  repository
- the reference copies are pinned to the exact commit each vendored copy came from
- the scanner-DSL path (typescript/js) records which upstream source its `.dsl`
  and `.h` came from

## Non-Goals

- upgrading any vendored grammar to a newer upstream tag (that is a separate
  change per grammar, with its own corpus re-run)
- changing any scanner's behaviour
- touching the shipped blobs

## Scope

### In Scope

- vendoring `src/scanner.c` for python and rust from the commit that matches the
  already-vendored `parser.c`
- pinning each reference copy to a commit and recording it
- rewriting the seven provenance files in the normalized format
- recording the scanner-DSL upstream provenance for typescript / javascript

### Out Of Scope

- grammar upgrades
- any `src/main` code change
- regenerating blobs

## Execution Plan

### Phase 1 - Close the source gaps

Status: planned
Targets: `src/test/resources/upstream/grammars/tree-sitter-python/`, `tree-sitter-rust/`, reference copies

- Item Types: `Fix`, `Proof`

- [ ] Determine, for python and rust, the upstream commit whose `parser.c` hashes
      equal the already-vendored `parser.c`; vendor `scanner.c` from that same
      commit
- [ ] Verify the vendored `scanner.c` is the one the existing
      `PythonScannerIdiomsTest` / `RustScannerIdiomsTest` expectations describe
      (token ordinals, guards); if it is not, that is a finding, not a reason to
      vendor the wrong file
- [ ] Pin every reference copy to a commit (detach or record the SHA) and record
      it in the module-level provenance file
- [ ] Record the upstream source of `tree-sitter-typescript/common/scanner.dsl`
      and `scanner.h`, and of `tree-sitter-javascript/src/scanner.dsl`
- [ ] `ai-dev/logs/` entry updated

Exit Criteria:

- [ ] Both `scanner.c` files are vendored, byte-verified, with a recorded SHA
- [ ] All seven reference copies are pinned and recorded
- [ ] `./mvnw test -pl nop-treesitter -am -T 1C` exits 0 (no behaviour change)
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exits 0

### Phase 2 - Normalize the provenance records

Status: planned
Targets: the seven `PROVENANCE.md` files, `upstream/grammars/PROVENANCE.md`

- Item Types: `Fix`

- [ ] Write each directory's `PROVENANCE.md` in the `tree-sitter-bash` format,
      with every field verified (no inherited guesses)
- [ ] Reduce `upstream/grammars/PROVENANCE.md` to policy + a pointer table, since
      the per-directory files now carry the facts
- [ ] `nop-treesitter/README.md` links the provenance policy

Exit Criteria:

- [ ] Seven per-directory records exist and are individually verified
- [ ] The module-level policy file no longer carries measured data that belongs
      per-directory
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exits 0
- [ ] `ai-dev/logs/` entry updated

## Closure Gates

- [ ] No vendored grammar directory lacks a verified provenance record
- [ ] The two missing `scanner.c` files are vendored
- [ ] No provenance field is unverified
- [ ] `./mvnw test -pl nop-treesitter -am -T 1C` exits 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exits 0
- [ ] Independent subagent closure audit recorded in this file's `Closure` section

## Deferred But Adjudicated

（起草时为空。）

## Non-Blocking Follow-ups

- **Upgrading the older vendored grammars to current upstream tags**
  - Classification: `out-of-scope improvement`
  - Why Not Blocking Closure: a per-grammar upgrade with its own corpus re-run and
    adjudication; orthogonal to provenance
  - Successor Required: `no`
  - Successor Path: —

## Risks And Rollback

| Risk | Mitigation |
|---|---|
| The vendored `parser.c` matches no single upstream commit (e.g. hand-patched) | Phase 1 requires proving byte equality against a commit before writing a SHA; if no commit matches, that is a finding to resolve, not a SHA to fudge |
| Vendoring the "wrong" `scanner.c` misleads future readers about `PythonScanner` / `RustScanner` | Phase 1 verifies the vendored file against the existing idioms tests' expectations and records any mismatch |
| Reference copies get re-cloned and lose their pin | Phase 1 records the SHA in the repo so the pin survives the local directory |

## Closure

Status Note: （待填）
Completed: （待填）

Closure Audit Evidence:

- Reviewer / Agent: （待独立子 agent 填写）
- Audit Session: （待填）
- Evidence: （待填）

Follow-up:

- （待填；或明确写 no remaining plan-owned work）
