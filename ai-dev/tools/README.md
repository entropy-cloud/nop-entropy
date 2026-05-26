# ai-dev/tools/

AI-assisted development tools for nop-entropy. A self-contained pnpm project.

## Quick Start

```bash
cd ai-dev/tools
pnpm install       # first time only
pnpm check         # run all checks
```

## Tool Index

| Script | Purpose | Usage |
|--------|---------|-------|
| `check-doc-links.mjs` | Check `docs-for-ai/` and `ai-dev/` markdown path references | `pnpm check:doc-links` |
| `check-doc-index.mjs` | Audit doc index health: broken links, orphans, missing sub-indexes, duplicated rules, sync drift | `node ai-dev/tools/check-doc-index.mjs [--fix] [--strict]` |
| `check-import-order.mjs` | Check Java import grouping: `java.* → jakarta.* → third-party → io.nop.*` | `pnpm check:import-order` |
| `check-oversized-files.mjs` | Detect oversized source files (>500 lines warn, >700 error) | `pnpm check:oversized` |
| `check-docs-garbled.mjs` | Detect garbled/corrupted Unicode in documentation | `pnpm check:garbled` |
| `code-stats.mjs` | Per-module code statistics (files, LOC, test ratio) | `pnpm stats` |
| `check-plan-checklist.mjs` | Verify all plan checklist items are checked before closure | `pnpm check:plan` |
| `codex-module-driver.sh` | Launch codex TUI with module-specific goal prompt | `./codex-module-driver.sh nop-stream` |
| `run-java-lint.sh` | Run ast-grep Java lint rules (empty catches, getMessage-only, bare RuntimeException, etc.) | `pnpm lint:java` |

## Per-Tool Details

### check-doc-links.mjs

Checks markdown files for broken path references (backtick paths and `[link](path)` syntax).

```bash
node check-doc-links.mjs              # report only
node check-doc-links.mjs --strict     # exit 1 on errors (CI mode)
```

Skip rules: placeholder paths (`Xxx`), `_gen/` files, `ai-dev/logs/`, completed plans, and a configurable whitelist.

### check-doc-index.mjs

Audits `docs-for-ai/` and `ai-dev/` index health.

```bash
node ai-dev/tools/check-doc-index.mjs           # check only
node ai-dev/tools/check-doc-index.mjs --fix     # auto-fix sync issues
node ai-dev/tools/check-doc-index.mjs --strict  # exit 1 on errors
```

Checks performed:
- Broken index links
- Orphan markdown files not referenced by any index
- Missing `README.md` in directories with many markdown files
- Duplicated routing rules across docs
- Sync drift between `docs-for-ai/INDEX.md` and `docs-for-ai/00-start-here/ai-defaults.md`
- Sync drift in `ai-dev/logs/index.md`

Writes a report to `_tmp/doc-index-audit-{timestamp}.md` and prints the summary to stdout.

### check-import-order.mjs

Verifies Java import ordering convention: `java.* → jakarta.* → third-party → io.nop.*`.

```bash
node check-import-order.mjs                       # all modules
node check-import-order.mjs --module nop-stream   # single module
node check-import-order.mjs --fix                 # show fix hints
```

Migrated from `check-import-order.sh` (original shell script preserved for reference).

### check-oversized-files.mjs

Finds Java files exceeding size thresholds. Defaults: warn at 500, error at 700 lines.

```bash
node check-oversized-files.mjs                          # defaults
node check-oversized-files.mjs --module nop-stream      # single module
node check-oversized-files.mjs --warn 400 --error 600   # custom thresholds
```

Adapted from `nop-chaos-flux/scripts/check-oversized-code-files.mjs`.

### check-docs-garbled.mjs

Scans `docs-for-ai/` and `ai-dev/` for garbled Unicode: mojibake, zero-width chars, BOM, control chars.

```bash
node check-docs-garbled.mjs
```

Reports written to `_tmp/docs-garbled-check/`. Exit 1 if likely-garbled files found.

Adapted from `nop-chaos-flux/scripts/check-docs-garbled.mjs`.

### code-stats.mjs

Per-module Java code statistics: file counts, LOC breakdown (code/comment/blank), test-to-source ratio.

```bash
node code-stats.mjs                        # all modules
node code-stats.mjs --module nop-stream    # single module
```

Adapted from `nop-chaos-flux/scripts/code-stats.mjs`.

### check-plan-checklist.mjs

Verifies that plan files in `ai-dev/plans/` have all checklist items checked (`[x]`) before the plan can be marked as `completed`. Also checks for Closure Evidence.

```bash
node check-plan-checklist.mjs                                    # all plans
node check-plan-checklist.mjs 57-nop-stream-code-cleanup.md      # single plan
node check-plan-checklist.mjs --strict                            # exit 1 on any failures
node check-plan-checklist.mjs --verbose                           # show passing plans too
```

Checks performed:
- All `- [ ]` items must be `- [x]` if plan status is `completed`
- Each Phase/Workstream section is analyzed for unchecked items
- Closure Gates must be fully checked
- Closure Evidence must be present in the `Closure` section
- Draft/pending plan broken links are reported as warnings, not errors

Required by: `ai-dev/plans/00-plan-authoring-and-execution-guide.md` (Rule #26), `ai-dev/skills/codex-goal-driven-development-prompt.md` (C-4.1, C-4.2, C-6).

### run-java-lint.sh (ast-grep rules)

Uses [ast-grep](https://ast-grep.github.io/) (based on tree-sitter AST) to lint Java code for anti-patterns. Rules are YAML files in `rules/`.

```bash
pnpm lint:java                                          # scan entire project
pnpm lint:java nop-ai                                   # scan a module
pnpm lint:java --filter empty-catch                       # run a subset of rules
pnpm lint:java --report-style medium                     # compact output
```

**Available rules** (in `rules/`):

| Rule file | Checks for |
|-----------|-----------|
| `java-lint-empty-catch.yml` | Empty `catch (X e) {}` blocks that silently swallow exceptions |
| `java-lint-getmessage-only.yml` | Catch blocks that use `e.getMessage()` but don't pass `e` to logger or rethrow |
| `java-lint-bare-runtimeexception.yml` | `throw new RuntimeException(...)` — should use NopException subclass |

**Pre-commit hook**: Staged `.java` files are automatically checked before each commit.

```bash
git config core.hooksPath .githooks       # activate (one-time setup per clone)
```

The hook lives at `.githooks/pre-commit` (project root), uses `ai-dev/tools/` rules.
Use `--error` mode so lint findings block the commit; bypass with `git commit --no-verify`.

**Adding new rules**:
1. Create `rules/java-lint-<name>.yml`
2. Run `pnpm lint:java --filter <name>` to test
3. Rules auto-discovered from `rules/` directory

**Design**: Each rule is a standalone YAML file. Rules can be composed (`any`/`all`/`not`) for complex patterns. No JS coding needed.

## Adding New Tools

1. Create `your-tool.mjs` in `ai-dev/tools/`
2. Add script entry to `package.json` under `"scripts"`
3. Register in the Tool Index table above
4. Get `PROJECT_ROOT` via: `const PROJECT_ROOT = new URL('../../..', import.meta.url).pathname;`
5. If you need external deps: `pnpm add <package>`
