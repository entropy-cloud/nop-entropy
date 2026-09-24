# nop-lint Roadmap — YAML-Driven AST Lint on nop-treesitter

> Last updated: 2026-09-24
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
- 4. MetaVar matchers (`$VAR`/`$$$VAR`/`$$VAR`/`$_VAR` + MetaVarEnv same-name consistency, design 04 §2): `done` (plan 04) — deps: 3
- 5. ChildMatcher lockstep traversal + trivial-node skipping + trailing handling (design 04 §3): `done` (plan 04) — deps: 3
- 6. Ellipsis lookahead probe + aggregator clone/backtracking (design 04 §3): `done` (plan 04) — deps: 5
- 7. Strictness v1 (Smart/AST levels; CST/Signature/Template deferred, design 04 §4): `done` (plan 04) — deps: 5
- ★ **Milestone M1: pattern kernel usable** (pure-pattern rules run via JUnit; unlocks when 1–7 done): `done` (plan 04, 2026-09-21)

### Wave 2 — Rule base (Phase 1)

- 8. `lint-rule.xdef` + `lint.register-model.xml` (YAML loading via DslJsonResourceLoader) + `RuleDslParser` + matcher-uniqueness validation (design 10): `done` (plan 06) — deps: M1
- 9. `LintEngine` minimal (kind bitmap filter + fast/standard profiles v1 + `skippedByProfile` stats, design 11): `done` (plan "2026-09-21-1420-1-lint-engine-minimal", closure audit approved 2026-09-21) — deps: 8
- 10. `RuleTester` (JUnit `RuleTestRunner` + `.expect` fixture format, design 03 §4): `done` (plan "2026-09-21-2137-1-rule-tester", closure audit approved 2026-09-21) — deps: 9
- 11. 10 core rules + fixtures (exception 5 + API 4 + VFS 1; absorb the 3 existing ast-grep rules under `ai-dev/tools/rules/` with behavior cross-check): `done` (executed 2026-09-22, plan "2026-09-21-2137-3-core-rules-first-batch", closure audit approved 2026-09-22: 7/10 landed in nop-lint-nop with RuleTester fixtures [no-raw-exception, no-empty-catch, ibiz-missing-annotation, ibiz-missing-context, bizmodel-dao-access, bizmodel-safe-api, no-vfs-violation]; 3 successor-adjudicated: silent-swallow + no-log-getmessage → item 23 relational primitives, errorcode-param-consistency → item 28 migration manifest; ast-grep cross-check recorded) — deps: 10
- 12. L1 `DeclTypeResolver` (declaration-type extraction, design 06 §5.2): `done` (plan "2026-09-21-1420-2-decl-type-resolver-l1", closure audit approved 2026-09-21) — deps: 2
- 13. **Benchmark baseline** (JMH + same-rule comparison against ast-grep CLI + perf doc, mirroring nop-treesitter item 13 discipline; gate for all future performance claims): `done` (plan 05) — deps: M1
- ★ **Milestone M2: rules verifiable via `./mvnw test` + perf baseline established** (unlocks when 8–13 done): `done` (plan 2137-3 closure, 2026-09-22 — items 8–13 all `done`)

### Wave 3 — Dynamic checks & execution surface (Phase 1 end)

- 14. xscript engine v1 (compile-scope whitelist + API contract node/captures/report, design 07 §1–§3): `done` (plan "2026-09-21-2137-2-xscript-engine-v1", all phases landed + independent closure audit approved 2026-09-21) — deps: 9
- 15. Deadline executor (route A: global-executor wrapper via `EvalExprProvider.registerGlobalExecutor`, zero platform change; re-evaluate route B/C only on measured need, design 07 §4): `done` (plan "2026-09-22-0128-1-deadline-executor", all phases landed + independent closure audit approved 2026-09-22) — deps: 14
- 16. `EditCalculator` (diff → TSInputEdit) + incremental parse integration (design 03 §1.2): `done` (plan "2026-09-22-0128-2-edit-calculator-incremental-parse", all phases landed + audit-feedback re-execution green 2026-09-22: EditCalculator anchored multi-hunk diff, `LintLanguage.parseIncremental` wired through `TSParser.parseIncremental`, corpus gate 126 instances incremental ≡ full, e2e lint diagnostics proof) — deps: 2
- 17. Suppression v1 (inline comments + @SuppressWarnings, design 09 §2–§3): `done` (plan "2026-09-22-0128-3-suppression-v1", all phases landed + independent closure audit approved 2026-09-22 #audit-202609220537) — deps: 9
- 18. **Minimal CLI** (`nop-lint check` + console output; pulled forward from Wave 6 to close the dogfooding gap): `done` (plan "2026-09-22-0544-1-minimal-cli", all phases landed + independent closure audit approved 2026-09-22 #audit-20260922-0700-...-73ee2347; dogfood: real `java -cp` run, 7 production rules, bounded slices of nop-lint-core itself) — deps: 9
- ★ **Milestone M3: dogfoodable** (run nop-lint on this repo, unlocks when 14–18 done): `done` (derived 2026-09-22 — items 14–18 all `done`, plan 0544-1 closure; repo-wide dogfooding activity belongs to item 40)

### Wave 4 — TypeScript / typing / constraints (Phase 2)

- 19. TS/TSX language adaptation (ts/tsx grammar blobs already shipped in nop-treesitter): `done` (plan "2026-09-22-1045-1-ts-tsx-language-adaptation", draft review approved + all three phases executed green 2026-09-22 — nop-lint-js module with ServiceLoader-discovered `TypeScriptLanguage`/`TsxLanguage` bindings over the shared tree-sitter adapter, `$`-expando identity adjudicated and pinned by parse-level tests, CLI extension table `ts→typescript`/`tsx→tsx` landed in TargetScanner with RuleTester fixture extension reuse, demo TS suites e2e green + CLI wiring proof; closure audit passed + `done` flipped 2026-09-22) — deps: M1
- 20. tsc bridge (resident Node process + program cache + tsconfig-hash invalidation + degrade-to-syntax fallback, design 06 §5.3 / 11 §3): `done` (plan "2026-09-22-0854-1-tsc-bridge", draft review approved + Phases 1–3 executed green + closure audit APPROVED 2026-09-22 (independent subagent, all gates PASS: anti-hollow chain resolver→bridge→tsc checker verified live, TestTscBridgeReal 3/3 real-Node non-skipped, hollow scan 0 findings) — resident Node bridge over newline-delimited JSON (ready handshake with node/typescript versions, structured error frames, per-query deadline, bounded restarts, terminal-unavailable), program cache keyed by tsconfig hash over files/include/exclude/references/paths + options signature, lazy spawn (first real query, design 11 §3), L2 gate wiring into the engine (capability ceiling + runtime availability probe → run/skip/degrade exits with `rulesDegraded`/`degradedRuleIds`, never L1-faked), `TypeOf` constraint real evaluation through `TypeQuerySupport` (byte offset → 0-based line/UTF-16 col), `RuleTestRunner` resolver wiring with file-backed fixture paths, real-Node+tsc e2e (cross-file types, assignability both forms, tsconfig-change rebuild) + demo `requires: L2` RuleTester suite `no-string-throw` under `l2-suites/`, environment contract: missing Node/typescript ⇒ tests report skipped, never silently green; done flips on closure audit) — deps: 19
- 21. XNode pattern engine (XML rules; attribute/text/namespace semantics per design 01 §3.5, incl. ORM/xbiz fixtures): `done` (plan "2026-09-22-1045-2-xnode-pattern-engine", draft review approved + all three phases executed green 2026-09-22 — XNode matcher kernel in nop-lint-core xml/ (tag=kind interning, open-world attrs, trimmed text, closed children lockstep, shared MetaVarSyntax/MetaVarEnv), engine wiring via `LintLanguage.compileRule` hook with identical downstream counters, RuleTester XML suites + CLI `xml→xml`/`xbiz→xml` extension entries, XML comment suppression landed, ORM/xbiz fixture rules nop-orm-mandatory-default + nop-xbiz-auth-not-sole-guard in nop-lint-nop with green suites + real-model dogfood; closure audit passed + `done` flipped 2026-09-22) — deps: M1
- 22. Constraint evaluator (same_text/regex/in_list/type_of/not_exists/within_depth; control-flow excluded, design 01 §3.2 / 10 §2): `done` (plan "2026-09-22-1045-3-constraint-evaluator", draft review approved + all phases executed green 2026-09-22 — top-level `constraints` field through xdef/RuleDslModel/RuleDslParser with the fail-closed parse matrix (unknown kind, missing sub-fields, captures <2, typeOf ⇢ requires:"L2" gate), three Decisions adjudicated (uniform filter polarity incl. withinDepth = subtree depth ≤ max, notExists per-match subtree scope, typeOf L2 gate → skippedByProfile never L1-faked), constraint evaluator + compile-time capture validation + RuleSetRunner integration after-match before-xscript with `constraintFilteredMatches` counter, 7 RuleTester constraint suites + e2e/wiring tests green; closure audit approved 2026-09-22 receipt #audit-2026-09-22-162129-...-621006f7) — deps: M1
- 23. Relational rules (inside/has/follows/precedes + StopBy + field constraints, design 04 §5) + composite all/not (operator division adjudicated 2026-09-22: all/not composition and `stopBy=rule` parse-time validation land here; matches/utils registry and any-nesting refinement stay in 24): `done` (executed 2026-09-22, plan "2026-09-22-0544-2-relational-rules", closure audit approved 2026-09-22 — kernel operators inside/has/follows/precedes × stopBy neighbor/end/rule + field constraints, composite all/not with kind-intersection prefilter, xdef/DSL extension incl. contextual pattern form + stopBy=rule fail-closed validation, successor rules silent-swallow/no-log-getmessage landed with RuleTester fixtures; closure-audit trigger fired: item 23 → done on closure audit pass 2026-09-22) — deps: 5
- 24. Composite rules (matches recursion + utils + any-nesting refinement; all/not already delivered by 23 per the operator division above, design 04 §6): `done` (plan "2026-09-22-0854-2-composite-rules", draft review approved + all three phases executed green + closure audit APPROVED 2026-09-22 (2 rounds: round 1 REJECT found the stopBy=rule eager-resolution false-rejection defect across hash iteration orders + a stale suite-count gate — both fixed with a deterministic forward-reference regression test and honestly rewritten Verification; round 2 re-verified with a 760-case sweep compiled=760/rejected=0 and 609/609 gate exit 0) — xdef recursive surface via two mutually-recursive xdef:define fragments, utils map + matches string-id element in model/parser, cycle-free reference-graph validation at parse time (all cycles re-evaluate on the same node and can never terminate — adjudication recorded), AnyMatcher (probe env isolation, first-branch commits) + ReferentMatcher (lazy registry resolution + expansion depth cap), eager utils registry with memoized kind opinions (matches → referenced util's opinion, nested any → branch union), stopBy=rule runtime through the same registry (inclusive_until), composite demo suite demo-composite-close (utils + matches + nested any in one rule) green through the real RuleTester pipeline, XmlRuleCompiler explicit fail-closed rejections; done flips on closure audit) — deps: 23
- 25. Autofix engine (template fix + conflict merge + multipass ≤10 + dry-run atomic apply, design 03 §3 / 04 §7): `done` (executed 2026-09-23/24 + closure audit APPROVED 2026-09-24 (independent subagent agent_4dcafb7a, all phases live-verified): DSL fix surface fail-closed (xdef/model/parser双必填 + fix+xscript parse 期拒绝 + XML 路径 compile 期拒绝 + 未声明捕获/未声明 $TOKEN 编译期拒绝), TemplateFix ($$$ 序列首末节点切片/空序列空串/字面量保留), Fixer 贪心非重叠 + skipped-conflict 计数, FixApplier multipass ≤10 (候选数严格下降收敛守卫 + temp/ATOMIC_MOVE 原子写 + ERROR/missing 重解析守卫 + 回滚恢复 + 不收敛保留最后成功轮 + dry-run 内存全程), UnifiedDiff 手写格式化器, CLI --fix/--fix-dry-run 互斥 + 报告恒描述磁盘最终内容 + fix 四计数 + suggestion 标注, e2e 三场景 (冲突合并/multipass 收敛/语法破坏回滚) 逐字节断言, autofix demo 套件 + 引擎面证明, owner docs 01/03/04/08 增注) (plan "2026-09-22-2225-1-autofix-engine", draft review approved 2026-09-22 after two adversarial review rounds — Diagnostic.fix carrier adjudicated (match-time generation, suppression filters whole diagnostic), fix+xscript and XML+fix parse/compile-time rejections, deterministic conflict priority (ruleset declaration order → match order, greedy non-overlapping selection), $$$ slice semantics (raw source between first/last captured nodes), multipass semantics (keep last successful round / dry-run in-memory full multipass single diff / convergence on candidate diagnostics), reindent+expand deferred) — deps: 22
- 26. L2 Java symbol solver hookup (ASTMapping boundary-resolution rules per design 06 §6.3 + lazy init + type cache + degrade ladder v1): `done` (executed 2026-09-24: JavaTypeResolver（nop-lint-java semantic 包，单文件+ReflectionTypeSolver v1 项目模型、(filePath,line,col) 三元键缓存、异常翻译层、CU imports-aware expectedType 解析、ResolvedType 祖先链 assignability）+ ASTMapping/JavaNodeIndex（named-only、精确 HashMap + 树下降、LineColBytes 处理 end-col inclusive 与多字节）+ 降级阶梯 e2e（JDK 命中真实诊断/throwing resolver mid-run 降级/无 resolver gate 降级/非 L2 不受影响）) (plan "2026-09-24-0500-1-l2-java-symbol-solver", draft review approved 2026-09-24 after two spike-driven rounds——关键裁定：直接 ParserConfiguration+ReflectionTypeSolver 装配（JavaParserBuilder 实为 nop-ai-code-analyzer 重依赖、nop-java-parser 的 JavaParseTool 有前缀过滤）；ASTMapping named-only + HashMap 精确 + 树下降，位置键主入口（engine 喂 line/col 非 LintNode）、双向查询为前瞻接口显式声明；单文件 + Reflection v1 项目模型（自定义类型显式失败不伪造）；缓存 (filePath,line,col) 三元键；异常翻译层三形态；end-col inclusive 换算) — deps: 20
- 27. Suppression v2 (baseline file + CI stale check + exemptions/ruleset usage, design 09 §4–§5): `done` (executed 2026-09-24 + closure audit APPROVED 2026-09-24 (independent subagent agent_7e2f01e3: 8 条关键裁定逐条 PASS + 红线零改动 diff 取证 + 两模块构建/doc-links/hollow-scan 全绿)：lint-ruleset.xdef + ruleset.yml XDSL 加载（双源重复 id/未知豁免规则 id 延迟统一校验/reason 必填/坏 glob 全 fail-closed）；ExemptionFilter CheckRunner 层无状态谓词（过滤先于 severity 累计、exemptedDiagnostics 计数可见、RuleTester 隔离红线 diff 取证）；BaselineFile（JsonTool+DataBean fail-closed）+ BaselineEngine（字节切片 fingerprint 抗行漂移/决策集 multi-set 消费/stale 判定）；CLI --baseline/--baseline-check/--write-baseline（三开关互斥 + write×fix 互斥；--baseline-check stale→exit 1 = "基线只减不增"执行机构；--write-baseline 成功 exit 0）；测试 33 例新增，core 689/0 + nop 18/0) (plan "2026-09-24-0050-1-suppression-v2", draft review approved 2026-09-24 after three adversarial rounds: R1 3 Blocker+5 Major+8 Minor 修订；R2 指定修复后 REJECT 2 Major+3 Minor（fix 多 pass 决策集无状态谓词裁定、--write-baseline×--fix 互斥）；R3 增量复核 5 项全 PASS APPROVED。关键裁定：模式差异矩阵（--baseline apply/--baseline-check CI 硬门禁 stale→exit 1/--write-baseline 生成）、过滤先于 severity 累计、fingerprint 字节切片、RuleTester 隔离红线。**item 39 范围注记：baseline 参数面（--baseline/--baseline-check/--write-baseline）已随本项提前交付，item 39 清单核销时对齐**)
- 28. check-*.mjs migration manifest (per-script enumeration replacing the vague "25+", incl. check-silent-wrong-result's 5 sub-rules and check-bean-naming → XNode) + per-script switchover/decommission plan: `done` (executed 2026-09-24 + closure audit APPROVED 2026-09-24 (independent subagent agent_7bf60dea: 24 行账本与 live 逐名一致 + 6 脚本源码抽查全对 + 门禁四路径真实 + 破坏试验可复现 + Anti-Slacking 复核无伪装无敷衍): **manifest 权威账本 = ai-dev/design/nop-lint/12-check-scripts-migration-manifest.md**（24 行逐脚本 + 分类汇总 7 maintain-mjs / 7 exclude / 3 migrated-pending-switchover / 5 candidate / 2 deferred + 防腐门禁 check-lint-migration-manifest.mjs 带 self-test 正控）; **errorcode-param-consistency 裁定（2026-09-24）: 维持 mjs zero-hit hard gate，不立项引擎级 analyzer**——子系统级跨文件注册表分析、单一消费者、迁移零表达力收益，重估触发 = Wave 5 语义分析器落地且出现第二个 *Errors.java 注册表模块；plan 2137-3 deferred 消解) (plan "2026-09-22-0854-3-check-scripts-migration-manifest", draft review approved 2026-09-22 via mission-driver REVIEW_PLANS #e18667f8) — deps: 18, 21
- 29. PMD/ErrorProne coverage manifest v1 (tier labels + acceptance fixtures, design 06 §7) + quality/security/XNode rules (9 of the first-20 batch): `done` (executed 2026-09-24 + closure audit APPROVED 2026-09-24 (independent subagent agent_32ec9846: 6 条裁定 PASS、门禁 3 变体独立破坏试验证伪有效、core 689/0 + nop 全绿 + hollow 0)：落地 7 条（quality 4 + security 2 + nop-orm-unique-key xscript 通道）全部带 RuleTester 套件（门禁 13→20），生产规则库 11→18（TestProductionRuleCount 枚举断言）；manifest v1 186 条全量枚举 + 防腐门禁 check-lint-coverage-manifest.mjs；路由 2（import-order→maintain-mjs、xpl-escaping→deferred）——原 plan "2026-09-24-0330-1-pmd-ep-coverage-manifest", draft review approved 2026-09-24 after three adversarial rounds (两轮 spike 驱动：真实 grammar/XNode 内核实测)——关键裁定：manifest 枚举实测 189 行全量首版（tier rubric 五档 + tier 2/3 fixture 前瞻语义 N2 差异裁定）；落地 7 条（no-system-out/no-return-null/no-transactional-annotation/no-star-import/no-sensitive-literal/no-hardcoded-crypto/nop-orm-unique-key）+ 路由 2（import-order→maintain-mjs per item 28；xpl-escaping→deferred，XNode text 全等无 contains + XML 拒 constraints，successor=design 06 §3.3 xscript 面）；no-sensitive-literal 关系式形态（string_literal+inside+full-match regex）；nop-orm-unique-key 走 xscript 通道（pattern open-world + attrValue 判空 report，N1 Blocker 裁定）；门禁 enum-set 对账 design 06 表 + tier-1-only fixture 存在性) — deps: 10
- ★ **Milestone M4: TypeScript + semantic layer + legacy migration can start** (unlocks when 19–29 done): `done` (derived 2026-09-24 — items 19–29 all `done`)

### Wave 5 — Dataflow & full rule library (Phase 3)

- 30. Dataflow analyzer (DefUseChain) + constant propagation (design 06 §4.4): `done` (plan "2026-09-24-0600-1-dataflow-analyzer", draft review approved 2026-09-24 after two spike-driven rounds——v1 方法内/流不敏感/局部+参数面；F1 增减量 UnaryExpr 纳入定义/重赋值；**审计第 1 轮 REJECTED：lambda 参数屏障 + 匿名类体屏障未实现，修复后复审**。原 todo "2026-09-24-0600-1-dataflow-analyzer", draft review approved 2026-09-24 after two spike-driven rounds (41 断言实测 javaparser 3.26.3)——关键裁定：v1 方法内/流不敏感/局部+参数面（字段面/CFG/跨过程路由）；增减量 UnaryExpr 纳入定义/重赋值枚举（F1 Blocker：x++ 非 AssignExpr）；作用域=位置感知词法栈（零依赖）+ 类体屏障 + lambda 参数屏障；SelfAssignment v1=局部/参数 x=x 恒等形态；ConstantResult sealed 三态含六字面量形态) — deps: M4
- 31. deep profile + degrade ladder v2 (design 11 §2/§5/§8): `done` (executed 2026-09-24, plan "2026-09-24-0900-1-deep-profile-degrade-ladder-v2", draft review R1 13 findings + R2 增量复核后 active, closure audit APPROVED 2026-09-24 (independent subagent agent_3547dea9: 三 Phase Exit Criteria + 全部 Closure Gates 逐条 PASS + Anti-Hollow 成立 + 工具门禁独立重跑 0/0/0)——落地 `LintProfile.DEEP`（上限 {L1,L2,L3,L4,SCOPE,METRICS}，词表裁定 = design 06 §4.6 分层名，小写 dataflow/tsc 不引入）+ `AnalyzerAvailability` 探针（fail-closed，注入与 TypeResolver 同构，items 32/33/34 只注册 provider）+ 单文件预算双钟口径（总钟→阶梯 / pattern 钟→熔断，重叠熔断胜出）+ 阶梯四级有序闭合仅记真实开启态（`degradedAnalyzers`）+ 闭合后 `CompiledRule.requires` 边界重判走既有 DEGRADE + fast 10ms xscript 时间片（<1ms 边界，跳过→`xscriptBudgetExceeded` 计数+规则 id 专列+pattern 层诊断，"标记 degraded"落地面不复用 L2 degradedRuleIds）+ fix 门控漂移修复（fast 本就不开；阶梯关闭后 `fixesDegraded` 计数）+ 统计报告五面（RunSummary filesDegraded/first-seen union + ConsoleReporter 补渲染 item 20 遗留的规则级降级）+ 引擎级 JMH 基准 `engineLint`（before/after 0.002±0.001 s/op 零回归，JFR 预算帧未入热点榜，perf-baseline.md 增注）) — deps: 30
- 32. MetricsEvaluator (cyclomatic = decision-point count; cognitive = SonarSource increment table; NPath = product enumeration — NOT AST depth, design 01 §6): `done` (executed 2026-09-24, plan "2026-09-24-1000-1-metrics-evaluator", R1 对抗审查 1 Blocker+4 Major + R2 增量复核（白名单变体机制自洽无漏洞）后 active, closure audit APPROVED 2026-09-24 (independent subagent agent_10316e9c: 三 Phase 逐条 PASS + 8 例独立手算复验 + 工具门禁 0/0/0)——落地 MetricsEvaluator（nop-lint-java，圈=决策点+1 每 &&/|| 节点独立/认知=SonarSource v1.7 Appendix B 增量表 switch 单次+else-if 平坦+lambda 抬嵌套+序列平坦/NPath=乘积枚举 long 饱和）+ MetricsResolver 接口（0-based/具名文件契约）+ JavaMetricsResolver（JavaTypeResolver 同型桥 + ServiceLoader SPI）+ metrics 白名单变体绑定（requires 门控注册，未声明引用编译期失败——R1 Blocker 修复裁定）+ 穿透链（LintEngine metricsReady gate→RuleSetRunner→executeMatch 注入）+ 阶梯 METRICS 闭合记录 + deep-suites demo（expect 钉 cyclomatic=4）+ TestMetricsBinding 四态 + design 01/07/11 增注；JMH engineLint/parseAndMatch 对 item 31 锚点零回归) — deps: M1
- 33. Scope analysis (scopeAnalyzer binding opened to xscript, design 05 §2): `done` (executed 2026-09-24, plan "2026-09-24-1130-1-scope-analyzer", R1 审查 1 Blocker+3 Major+6 Minor（E1 字段可见性口径/A1 byte 逆变换消除/D1 词法归约路径/D2 switch 单一作用域）+ R2 复核 N1-N4 后 active, closure audit APPROVED 2026-09-24 (independent subagent agent_8f71b71d: 三例手算独立核验 + 工具门禁 0/0/0 + 位置链闭环核对)——落地 ScopeAnalyzer（JavaParser 四查询：definitionOf/declaredNames/scopeKind/shadows；类字段全域顺序无关/局部参数声明点后/switch-block 单一作用域/shadows 不预设合法 Java；与 DefUseChain 复用边界注记：类字段域新增+lambda 独立作用域不扁平化）+ ScopeResolver/Discovery SPI + JavaScopeResolver + scope 白名单变体绑定（compile 第三参推广为 Set<LintCapability>，R1 Blocker 裁定链）+ 穿透链（resolverReady 双态 gate→executeMatch 7 参重载→ScopeFunctions byte 贪心下探回传 CST NodeWrapper）+ 阶梯 SCOPE 闭合记录 + deep-suites/scope-shadow demo + TestScopeBinding 四态 + design 05/07/11 增注；engineLint 0.001±0.001 零回归) — deps: 30
- 34. L3/L4 typing enhancements (design 06 §4.6): `done` (executed 2026-09-24, plan "2026-09-24-1300-1-l3l4-typing", R1 审查 3 Major+8 Minor（C-1 门控行为迁移认领/C-2 DeepResolvers 去 filePath/B-1 ConstantPropagation 按名塌缩陷阱）修订后 active, closure audit APPROVED 2026-09-24 (independent subagent agent_eb4cdb09: 三 Phase 逐条 PASS + 手算抽查 3 例吻合 + 行为迁移审计（探针路生产消费归零）+ 工具门禁 0/0/0)——落地 L4 SemanticAnalyzer（implementsInterface erased-name+归一/isOverridable/isLoggerCall 白名单；v1 可答面=JDK+同 CU 层次+修饰符面）+ L3 DataflowQueries（identity 匹配 Record——B-1 byName 塌缩陷阱/name-anchored/方法屏障）+ DeepResolvers record 穿透收敛（run 8 参/executeMatch 6 参/构造族收敛）+ L3/L4 门控 resolver 路迁移（探针路退役）+ semantic/dataflow 绑定（白名单四 capability 全量）+ 四 Discovery SPI + demo 套件 semantic-l4/dataflow-l3 + TestL3L4Bindings 8 例 + design 06/07/11 增注；engineLint 0.002±0.001 零回归) — deps: 26, 30
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
| YAML rule loading / xdef / x:extends | XDSL (`DslJsonResourceLoader` via register-model, `DslNodeLoader`, `XDslExtender`) | Requires `lint.register-model.xml` for fileType `rule.yml` (design 10 §4); platform check-mutex validation inspects attribute-form props only — element-form matcher uniqueness is enforced by `RuleDslParser` fail-closed |
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
