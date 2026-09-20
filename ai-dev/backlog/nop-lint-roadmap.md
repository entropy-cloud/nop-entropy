# nop-lint Roadmap — YAML-Driven AST Lint on nop-treesitter

> Last updated: 2026-09-20
> Design authority: `ai-dev/design/nop-lint/` (00-overview → 01–11, index at 00-nop-lint-design.md)

## Purpose

This roadmap tracks the implementation of nop-lint: a YAML-driven AST lint system
built on the pure-Java tree-sitter runtime (nop-treesitter, M4 done) with
ast-grep-style pattern matching, XLang xscript dynamic checks, and Nop-specific
rule library. Terminal goal: replace the current checkstyle + PMD + 24
check-*.mjs scripts with one declarative rule engine that runs in-editor (fast),
in CI (standard), and nightly (deep) — see design doc 11-performance-profiles.md.

Modules: `nop-lint/` group (`nop-lint-core`, `nop-lint-java`, `nop-lint-js`,
`nop-lint-nop`; ecosystem modules `nop-lint-maven-plugin` / `nop-lint-graphql` /
`nop-lint-cli` in Wave 6).

Enables `./tools/mission-driver.sh run nop-lint` to drive the implementation
autonomously. Roadmap contains no implementation details — each `planned` stage
is owned by its execution plan. Wave ↔ Phase mapping and analyzer dependency
matrix live in design doc 08-migration.md; this file is the **only dynamic
state block**.

## Work Items

> **This is the only dynamic state block. Update status only here.**
> The roadmap is a human-AI alignment artifact: humans set items and their order;
> AI takes the first `todo` item, drafts/executes plans (humans don't review individual
> plans), and writes the item back to `done` when closure audit passes.

### Wave 1 — Pattern matching kernel (Phase 1 start)

- 1. Module skeleton `nop-lint-core`/`-java`/`-nop` + parent pom registration + bootstrap test: `done` (plan 01)
- 2. `LintNode` facade (TSTreeCursor wrapper + byte-range source slicing + kind mapping, design 01 §5) **+ Java language adaptation** (java grammar binding, `$`-expando rules for Java, kind-name mapping — the Java counterpart of item 19): `done` (plan 02)
- 3. `SourcePatternCompiler` pipeline (expando preprocess → TSParser parse → PatternNode tree → effective-node extraction → kind precompute, design 01 §4): `done` (plan 03) — deps: 2
- 4. MetaVar matchers (`$VAR`/`$$$VAR`/`$$VAR`/`$_VAR` + MetaVarEnv same-name consistency, design 04 §2): `todo` — deps: 3
- 5. ChildMatcher lockstep traversal + trivial-node skipping + trailing handling (design 04 §3): `todo` — deps: 3
- 6. Ellipsis lookahead probe + aggregator clone/backtracking (design 04 §3): `todo` — deps: 5
- 7. Strictness v1 (Smart/AST levels; CST/Signature/Template deferred, design 04 §4): `todo` — deps: 5
- ★ **Milestone M1: pattern kernel usable** (pure-pattern rules run via JUnit; unlocks when 1–7 done): `todo`

### Wave 2 — Rule base (Phase 1)

- 8. `lint-rule.xdef` + `lint.register-model.xml` (YAML loading via DslJsonResourceLoader) + `RuleDslParser` + matcher-uniqueness validation (design 10): `todo` — deps: M1
- 9. `LintEngine` minimal (kind bitmap filter + fast/standard profiles v1 + `skippedByProfile` stats, design 11): `todo` — deps: 8
- 10. `RuleTester` (JUnit `RuleTestRunner` + `.expect` fixture format, design 03 §4): `todo` — deps: 9
- 11. 10 core rules + fixtures (exception 5 + API 4 + VFS 1; absorb the 3 existing ast-grep rules under `ai-dev/tools/rules/` with behavior cross-check): `todo` — deps: 10
- 12. L1 `DeclTypeResolver` (declaration-type extraction, design 06 §5.2): `todo` — deps: 2
- 13. **Benchmark baseline** (JMH + same-rule comparison against ast-grep CLI + perf doc, mirroring nop-treesitter item 13 discipline; gate for all future performance claims): `todo` — deps: M1
- ★ **Milestone M2: rules verifiable via `./mvnw test` + perf baseline established** (unlocks when 8–13 done): `todo`

### Wave 3 — Dynamic checks & execution surface (Phase 1 end)

- 14. xscript engine v1 (compile-scope whitelist + API contract node/captures/report, design 07 §1–§3): `todo` — deps: 9
- 15. Deadline executor (route A: global-executor wrapper via `EvalExprProvider.registerGlobalExecutor`, zero platform change; re-evaluate route B/C only on measured need, design 07 §4): `todo` — deps: 14
- 16. `EditCalculator` (diff → TSInputEdit) + incremental parse integration (design 03 §1.2): `todo` — deps: 2
- 17. Suppression v1 (inline comments + @SuppressWarnings, design 09 §2–§3): `todo` — deps: 9
- 18. **Minimal CLI** (`nop-lint check` + console output; pulled forward from Wave 6 to close the dogfooding gap): `todo` — deps: 9
- ★ **Milestone M3: dogfoodable** (run nop-lint on this repo, unlocks when 14–18 done): `todo`

### Wave 4 — TypeScript / typing / constraints (Phase 2)

- 19. TS/TSX language adaptation (ts/tsx grammar blobs already shipped in nop-treesitter): `todo` — deps: M1
- 20. tsc bridge (resident Node process + program cache + tsconfig-hash invalidation + degrade-to-syntax fallback, design 06 §5.3 / 11 §3): `todo` — deps: 19
- 21. XNode pattern engine (XML rules; attribute/text/namespace semantics per design 01 §3.5, incl. ORM/xbiz fixtures): `todo` — deps: M1
- 22. Constraint evaluator (same_text/regex/in_list/type_of/not_exists/within_depth; control-flow excluded, design 01 §3.2 / 10 §2): `todo` — deps: M1
- 23. Relational rules (inside/has/follows/precedes + StopBy + field constraints, design 04 §5): `todo` — deps: 5
- 24. Composite rules (all/not/matches recursion + utils, design 04 §6): `todo` — deps: 23
- 25. Autofix engine (template fix + conflict merge + multipass ≤10 + dry-run atomic apply, design 03 §3 / 04 §7): `todo` — deps: 22
- 26. L2 Java symbol solver hookup (ASTMapping boundary-resolution rules per design 06 §6.3 + lazy init + type cache + degrade ladder v1): `todo` — deps: 20
- 27. Suppression v2 (baseline file + CI stale check + exemptions/ruleset usage, design 09 §4–§5): `todo` — deps: 17
- 28. check-*.mjs migration manifest (per-script enumeration replacing the vague "25+", incl. check-silent-wrong-result's 5 sub-rules and check-bean-naming → XNode) + per-script switchover/decommission plan: `todo` — deps: 18, 21
- 29. PMD/ErrorProne coverage manifest v1 (tier labels + acceptance fixtures, design 06 §7) + quality/security/XNode rules (9 of the first-20 batch): `todo` — deps: 10
- ★ **Milestone M4: TypeScript + semantic layer + legacy migration can start** (unlocks when 19–29 done): `todo`

### Wave 5 — Dataflow & full rule library (Phase 3)

- 30. Dataflow analyzer (DefUseChain) + constant propagation (design 06 §4.4): `todo` — deps: M4
- 31. deep profile + degrade ladder v2 (design 11 §2/§5/§8): `todo` — deps: 30
- 32. MetricsEvaluator (cyclomatic = decision-point count; cognitive = SonarSource increment table; NPath = product enumeration — NOT AST depth, design 01 §6): `todo` — deps: M1
- 33. Scope analysis (scopeAnalyzer binding opened to xscript, design 05 §2): `todo` — deps: 30
- 34. L3/L4 typing enhancements (design 06 §4.6): `todo` — deps: 26, 30
- 35. Rule library to 48+ (incl. the 8 anti-pattern rules and query-limit-required, the 20th first-batch rule — deliver the enumeration list FIRST as part of this item's plan): `todo` — deps: 29
- 36. ESLint dataflow-rule ports + PMD/EP P0/P1 batch (manifest reaches auditable state): `todo` — deps: 30, 35
- ★ **Milestone M5: full rule library** (unlocks when 30–36 done): `todo`

### Wave 6 — Ecosystem integration (Phase 4)

- 37. `nop-lint-maven-plugin` (check goal @ validate phase, design 03 §2.1): `todo` — deps: M3
- 38. `nop-lint-graphql` (lint__checkSource/checkFile/listRules + security boundaries per design 03 §2.3): `todo` — deps: M3
- 39. CLI completion (match/check/test/--fix-dry-run/baseline + sarif/checkstyle-xml/json/junit-xml/console outputs + exit codes, design 03 §2.4): `todo` — deps: 18, 25
- 40. checkstyle.xml (17 rules) + pmd-ruleset.xml (9 rules) migration mapping + dual-tool parallel period + rollback plan (design 06 §8): `todo` — deps: 29, 39
- 41. Editor integration (VS Code / Neovim via LSP or incremental API; fast profile): `todo` — deps: 16, 18
- 42. Rule catalog doc generator (from YAML metadata) + rule versioning policy closeout (semver/compat; minimal version stamp + CHANGELOG convention starts in Wave 2, design 01 §2): `todo` — deps: 35
- 43. CI cache artifactization + editor watch integration + GraphQL fast-profile tuning (design 11 §4/§8): `todo` — deps: 37, 38, 41
- ★ **Milestone M6: ecosystem integration complete** (unlocks when 37–43 done): `todo`

## Status values

| Status | Meaning |
| --- | --- |
| `todo` | Not started, no plan |
| `planned` | Has execution plan, passed draft review |
| `done` | Complete, passed closure audit |

> Milestone status is derived: a milestone flips to `done` only when all its
> dependencies are `done`. Never mark a milestone `done` prematurely.

## Hard constraints (from design docs, enforced at closure audit)

- TSQuery is **frozen** — never extended to carry pattern DSL; SourcePattern is the only rule path (design 01 §4.1)
- No modifications to existing nop-treesitter classes; share TSParser/LintNode, isolate the matching kernel (design 01 §4.1)
- Prefer **zero platform change**: nop-xlang / nop-core / nop-xdef are Protected Areas (plan-first). Deadline executor route A must be exhausted before proposing route C (design 07 §4)
- Profile degradation must never silently skip rules (`skippedByProfile` / `degraded` counters are mandatory) and never fake L2 with L1 results (design 11 §5)
- Every Wave-2+ rule lands with RuleTester fixtures; manifest tier 1–3 rules without fixtures are not done (design 03 §4)

## Framework / platform reuse

| Capability | Provider | Notes |
| --- | --- | --- |
| Parsing / incremental / query | `nop-treesitter` (M4 done) | TSParser.parseIncremental(language, oldTree, edits, byte[]); TSQuery for auxiliary S-expr queries only |
| YAML rule loading / xdef / x:extends | XDSL (`DslJsonResourceLoader` via register-model, `DslNodeLoader`, `XDslExtender`) | Requires `lint.register-model.xml` for fileType `rule.yml` (design 10 §4); check-\*/check-mutex have no runtime consumer — RuleDslParser enforces uniqueness |
| xscript execution | XLang (`ScriptEvalAction`, `IExpressionExecutor`, `EvalExprProvider.registerGlobalExecutor`) | Lambda is `=>`; no existing timeout facility — deadline via route A wrapper (design 07) |
| L2 Java typing | `nop-utils/nop-java-parser` + `nop-ai/nop-ai-skills/nop-ai-code-analyzer` | JavaParserBuilder CombinedTypeSolver + MavenProject; do not build a resolver |
| Logging / test / build | slf4j, JUnit 5, Maven wrapper | Same as nop-treesitter |
| NopIoC / GraphQL | `io.nop.core.ioc`, nop-graphql | Wave 6 only |

## Stages

| # | Stage | Wave | Owner plan | Deps | Critical path |
| --- | --- | --- | --- | --- | --- |
| 1–7 | Pattern kernel | 1 | — | 2→3→{4,5}→6; 5→7 | **Yes** |
| 8–13 | Rule base | 2 | — | M1 | **Yes** |
| 14–18 | Dynamic + CLI | 3 | — | 9 | Yes |
| 19–29 | TS/typing/constraints | 4 | — | M1/M3 | **Yes** (20, 26 longest) |
| 30–36 | Dataflow/rules | 5 | — | M4 | Yes |
| 37–43 | Ecosystem | 6 | — | M3+ | No (parallelizable) |

## Current baseline

**Nothing exists yet** — no `nop-lint/` module, no mission entry. Design docs are
complete (ai-dev/design/nop-lint/, 13 files) and independently audited
(2026-09-20: 3-agent deep audit, findings folded back into design; verify-then-
trust code-level claims are marked as such in the docs).

**Main external risks:**
- Matcher kernel (items 3–7) is a full re-implementation of ast-grep's core algorithms — largest single block, est. 2.5–4 weeks alone
- tsc bridge (item 20) manages a resident Node process — independent subsystem-scale work
- Realistic total (single agent, no interruptions): **24–36 weeks** vs the original 14-week calendar guess (deprecated; see design 08 §1)
