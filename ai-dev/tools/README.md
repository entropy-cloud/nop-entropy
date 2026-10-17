# ai-dev/tools/

AI-assisted development tools for nop-entropy. A self-contained pnpm project.

**边界约定**：本目录是仓库工具依赖的唯一 pnpm 根——新增工具依赖一律改 `package.json` 后在此 `pnpm install`，禁止在仓库其他位置另建 pnpm 根或引用仓库外的工具脚本路径（既有例外：`ai-dev/tools/mission-driver.sh` 默认指向 `$HOME/app/attractor-guided-engineering-template` 的 AGE 模板，即 mission driver 例外；根 `tools/mission-driver/` 为其仓库内引擎副本）。

## Quick Start

```bash
cd ai-dev/tools
pnpm install       # first time only
pnpm check         # run all checks
```

未安装依赖时，依赖 Node 包的工具会显式输出跳过原因并降级为轻量检查（不产生误报）。**例外**：`run-java-lint.sh`（pre-commit 钩子的 Java lint 门禁）没有降级路径——它需要 `node`（渲染 nop-lint 的 JSON 报告）和一个可用的 `nop-cli`（缺失时直接失败并输出一键安装指引 `bash tools/install-cli.sh`）。`node_modules/` 不受 git 跟踪，新 clone / 新 worktree 提交 Java 文件前需先装一次。

## Tool Index

| Script | Purpose | Usage |
|--------|---------|-------|
| `check-doc-links.mjs` | Check `docs-for-ai/` and `ai-dev/` markdown path references | `pnpm check:doc-links` |
| `check-doc-index.mjs` | Audit doc index health: broken links, orphans, missing sub-indexes, duplicated rules, sync drift | `node ai-dev/tools/check-doc-index.mjs [--fix] [--strict]` |
| `check-i18n-en-xml.mjs` | Check `i18n-en:*` attributes in `*.xml` and `*.xmeta` for non-English or suspicious values | `node ai-dev/tools/check-i18n-en-xml.mjs [--strict]` |
| `convert-model-xml-to-xlsx.sh` | Batch convert source `model/*.orm.xml` and `model/*.api.xml` to sibling `*.xlsx` files for configured projects | `bash ai-dev/tools/convert-model-xml-to-xlsx.sh` |
| `check-import-order.mjs` | Check Java import grouping: `java.* → jakarta.* → third-party → io.nop.*` | `pnpm check:import-order` |
| `check-oversized-files.mjs` | Detect oversized source files (>500 lines warn, >700 error) | `pnpm check:oversized` |
| `check-docs-garbled.mjs` | Detect garbled/corrupted Unicode in documentation | `pnpm check:garbled` |
| `code-stats.mjs` | Per-module code statistics (files, LOC, test ratio) | `pnpm stats` |
| `check-plan-checklist.mjs` | Verify all plan checklist items are checked before closure | `pnpm check:plan` |
| `codex-module-driver.sh` | Launch codex TUI with module-specific goal prompt | `./codex-module-driver.sh nop-stream` |
| `run-java-lint.sh` | Java lint gate via `nop-cli lint` (empty catches, getMessage-only, bare RuntimeException) | `pnpm lint:java` |
| `check-nop-stream-invariants.mjs` | nop-stream invariant gate scanner (inventory / sync / scan-iterations / scan-output-contract / scan-wiring / self-test / init / all) | `node ai-dev/tools/check-nop-stream-invariants.mjs all` |
| `check-flux-pages.mjs` | Static checker for `*.flux.yaml` (and `_dump` pages): flags AMIS-only component types/keys (e.g. `button-toolbar`, page-level `toolbar`, `actionType`, `validations`) that flux silently drops | `pnpm check:flux-pages` |

## 非脚本使用方（Node 依赖）

| 依赖包 | 使用方 |
|---|---|
| mermaid + jsdom | `.opencode/skills/nop-deepwiki/scripts/check-wiki.mjs`——deepwiki 门禁的 Mermaid 块无头解析校验（jsdom 注入全局 DOM 后 `mermaid.parse`） |
| @ast-grep/cli / tree-sitter-java / web-tree-sitter | `run-java-lint.sh` 等语法级检查 |
| js-yaml / typescript | 各 check 脚本按需加载 |

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

### check-i18n-en-xml.mjs

Checks `i18n-en:*` attributes in repository `*.xml` and `*.xmeta` files.

Temporary or derived directories such as `target/`, `_tmp/`, and `_dump/` are skipped by default.

```bash
node ai-dev/tools/check-i18n-en-xml.mjs           # report issues
node ai-dev/tools/check-i18n-en-xml.mjs --strict  # exit non-zero on errors
```

Checks performed:
- `error`: empty values, Chinese text, values with no English letters, or incomplete labels such as `View`, `Add`, `Edit`, `Update`, `Query`, `Modify`
- `warn`: common spelling or style issues such as `Worflow`, `CamlCase`, `TableName`, `Sql`, `Oauth`

Writes reports to `_tmp/i18n-en-xml-report.json` and `_tmp/i18n-en-xml-report.md`.

### convert-model-xml-to-xlsx.sh

Uses `ai-dev/tools/model-convert-projects.txt` as the configured project list, finds source `model/*.orm.xml` and `model/*.api.xml`, and converts each to a sibling `*.xlsx` file via `nop-cli convert`.

```bash
bash ai-dev/tools/convert-model-xml-to-xlsx.sh
```

Validation rules:
1. Each `nop-cli convert` invocation must return exit code 0.
2. The expected output file must exist.
3. Any failed conversion makes the whole script exit non-zero and prints the failed entries.

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

### run-java-lint.sh (nop-lint gate)

Runs the Java lint gate through `nop-cli lint check` (the `nop-lint` engine, 65 rules in `nop-tools/nop-lint/nop-lint-nop`). The ast-grep ruleset this used to run has been migrated into nop-lint; the three rules below are the landed equivalents, traceable via each rule's `metadata.source` field.

```bash
pnpm lint:java                                          # scan every git-tracked .java
pnpm lint:java nop-ai                                   # scan a module
pnpm lint:java --rules nop/no-empty-catch some/dir     # run a different rule set
```

**Gate rules** (default):

| ast-grep rule (now historical) | nop-lint rule | Severity |
|-------------------------------|---------------|----------|
| `rules/java-lint-empty-catch.yml` | `nop/no-empty-catch` | warning |
| `rules/java-lint-getmessage-only.yml` | `nop/no-log-getmessage` | warning |
| `rules/java-lint-bare-runtimeexception.yml` | `nop/no-raw-exception` | warning |

**nop-cli resolution** (first hit wins): `nop-cli` on PATH → `~/.nop/cli/bin/nop-cli` → a jar built in this worktree. Run `bash tools/install-cli.sh` for the first, or build the jar for the third. A PATH entry that exists but is not executable is skipped — a stale registration otherwise wins the lookup and then fails to exec.

**Why the gate filters `ruleId`** instead of using `--max-warnings 0`: nop-lint also emits the engine meta-diagnostic `unused-disable-directive` (fixed severity `warning`) for any file carrying a stale suppression directive. Those are pre-existing debt in files a commit merely touches, so counting them would fail unrelated commits. The wrapper runs one `--format json` pass and counts only the loaded gate rules' diagnostics.

**Fixture exclusion**: `src/test/resources/_vfs/test/lint/` is filtered out before the file list reaches the CLI. Those `invalid/*.java` files are intentionally dirty; their violations are asserted by nop-lint's own RuleTestRunner suite gate. nop-lint has no ignore-file mechanism and `--rules` cannot exclude them (they violate real rules).

**Pre-commit hook**: Staged `.java` files (ACMR) are checked before each commit.

```bash
git config core.hooksPath .githooks       # activate (one-time setup per clone)
```

The hook lives at `.githooks/pre-commit` and calls this wrapper. Exit codes: 0 clean, 1 gate findings, 2 usage/parse error. Bypass with `git commit --no-verify`.

**Widening the gate**: switching to the full 65-rule profile is a deliberate follow-up, gated on paying down the existing findings (e.g. `nop-stream/nop-stream-flow` already has 10 `nop/silent-swallow` errors).

**Full docs**: `docs-for-ai/03-runbooks/nop-cli-and-lint-gate.md`.

**Adding new rules**: add a rule YAML under `nop-tools/nop-lint/nop-lint-nop/src/main/resources/_vfs/nop/lint/rules/`, then select it by id via `--rules <id>`. nop-lint discovers rules from that VFS prefix; `--rules` with an unknown id fails the run.


Checks `I*Biz` service interfaces (in `*-dao` modules' `biz` package, `extends ICrudBiz`) against the platform contract in `docs-for-ai/02-core-guides/service-layer.md`:

1. Every method must be annotated `@BizQuery` / `@BizMutation` / `@BizAction` — `BizProxyInvocationHandler` routes by annotation; without one the proxy can't recognize the method and GraphQL won't expose it.
2. Every method's last parameter must be `IServiceContext` — matches the `ICrudBiz` contract (all methods take `context` last); it carries `IUserContext` (identity/data-auth), cache, and txn context, and must be propagated in cross-service `I*Biz` calls.

This rule is **not** expressible in ast-grep (its `has`/`inside` predicates can't reliably match annotations in the modifiers subtree or `extends ICrudBiz<generic>`), so it uses **tree-sitter-java (WASM via web-tree-sitter)** for accurate AST parsing. Scope is limited to `I*Biz` interfaces (not `BizModel` classes) since interfaces are the contract source, contain no `@BizLoader`/constructors/private helpers, and `BizModel` methods sync to the interface per the service-layer guide.

```bash
```

Exit code 1 if any violation; 0 otherwise.

### check-flux-pages.mjs

Static checker for hand-written `*.flux.yaml` pages (and optionally resolved pages under `_dump/`). The flux runtime **silently drops** unknown component types/keys, so an AMIS→flux migration can lose whole toolbars/buttons with no error. This tool reports high-confidence AMIS-only constructs before runtime:

- AMIS-only component types: `button-toolbar`, `service`, `static`, `static-mapping`, `static-image`, `input-rich-text`, …
- AMIS-only keys on schema nodes: `actionType`, `visibleOn`/`hiddenOn`/`disabledOn`, `validations`, `asideResizor`, `initApi`/`initFetch`/`initFetchOn`
- page-level `toolbar` key (flux pages use the `header` region instead)

```bash
pnpm check:flux-pages                                  # all *.flux.yaml in the repo
node ai-dev/tools/check-flux-pages.mjs <file...>       # specific files
node ai-dev/tools/check-flux-pages.mjs --include-dump  # also scan _dump (flux-mode build output)
node ai-dev/tools/check-flux-pages.mjs --type-check    # best-effort "unknown flux type" warnings
```

Exit code 1 on any error (0 with only warnings, unless `--strict`). This is a **static** check and does not replace browser testing; the authoritative diagnostic is the flux schema compiler in nop-chaos-flux, which runs at frontend build time and is not yet wired into this repo.

## Adding New Tools

1. Create `your-tool.mjs` in `ai-dev/tools/`
2. Add script entry to `package.json` under `"scripts"`
3. Register in the Tool Index table above
4. Get `PROJECT_ROOT` via: `const PROJECT_ROOT = new URL('../../..', import.meta.url).pathname;`
5. If you need external deps: `pnpm add <package>`
