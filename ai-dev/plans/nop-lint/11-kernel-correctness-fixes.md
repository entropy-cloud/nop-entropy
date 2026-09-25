# 11 内核正确性修复（遮蔽判定/TemplateFix NPE/Myers 上界/缓存条目校验）

> Plan Status: completed
> Last Reviewed: 2026-09-25
> Source: `ai-dev/analysis/2026-09/2026-09-25-nop-lint-quality-optimization-deep-audit.md`（findings C1、C2、C10-内核、M3-A/Myers）
> Related: 07/08/09/10（已收口）、12-semantic-resolver-convergence.md、ai-dev/design/nop-lint/09-suppression.md
> Review: R1 对抗审查（2026-09-25）：3 必修——(1) C1 修复规格补全：深度排序须叠加"声明位置先于引用位置"过滤（ScopeAnalyzer.visibleDeclarationIn 语义），否则兄弟同深度（双 catch 同名参数、for-init 重名）今天仍误判；(2) C10a stopBy 修复形态：parser 将缺省归一化为 "end"（RuleDslParser:530-531），必须先加显式 `case "end"` 再让 default 抛——否则全部关系规则编译爆炸；(3) Closure 验证命令纳入 nop-lint-nop（dataflow 规则的 RuleTester 套件在下游，-am 不覆盖）。次要修订：TemplateFix 加 ruleId 字段（消息含规则 id 需要它）、Myers 钉死回退形态、测试措辞可证伪化、TestAutofix* 更正为 TestCliAutofixEndToEnd、行号刷新（plan 10 后）、11↔12 follow-up 环解开。全部修订后执行。

## Purpose

修复审计确认的内核层 live correctness 缺陷：数据流遮蔽判定用行号近似作用域深度（下游 dataflow 规则可张冠李戴）、fix 模板跨 `any` 分支捕获引用的运行期裸 NPE、Myers diff trace 内存无上界（LSP 大粘贴 OOM 面）、以及三处 fail-closed 契约与实现不符。全部为已确认 live defect 的 Fix，不设 deferred。

## Current Baseline

- **C1（DefUseChain 遮蔽误判）**：nop-lint-java/semantic/DefUseChain.java:254-270 `findVar` 在"作用域包含引用点"的同名声明中取 `begin.line` 最大者；`scopeEncloses`（:277-305）对局部变量取"最近 BlockStmt"作边界。反例（合法 Java，两声明在不同块）：`{ int x = 2; use(x); } int x = 1;`——外层 x 的块（方法体）包含引用点且行号更大，`use(x)` 被错判为外层 x 的 use。下游 `useCount`/`isSelfAssigned`/`constantValue`（L3 dataflow 规则的求值面）随之出错。javadoc（:249-253）宣称 "the innermost declaration"，实现不符。消费方：DataflowQueries/ConstantPropagation（silent-swallow、no-log-getmessage 等规则经 xscript 调用）。正确语义已存在于同包 `ScopeAnalyzer.resolve`（外向逐界走查）——两套实现并存。
- **C2（TemplateFix 多捕获 NPE）**：fix/TemplateFix.java:126-131 `Slot.render` 多捕获路径 `env.getMultiCapture(name)` 返回 null 时 `nodes.isEmpty()` 裸 NPE；单捕获路径（:133-138）有完整 fail-closed `NopLintException`。根因：CompiledRule.CaptureIndex（engine/CompiledRule.java:474-482）把 `any` 各分支捕获取**并集**做编译期校验（:268,335-336 `captures.collect`），但每个 match 的 env 只含命中分支的绑定——`any: [{pattern: "foo($$$A)"}, {pattern: "bar($B)"}]` + 模板用 `$$$A` 时 bar 分支的 match 通过编译校验、运行期 NPE 从 `RuleSetRunner` fix 渲染点穿透出 `lint()`，整个文件失败。
- **M3-A（Myers trace 无上界）**：lang/EditCalculator.java:316-339 `myersOps` 每个 d 步 `trace.add(v.clone())`，`v` 尺寸 `2*(n+m)+1`——D 最坏 = N+M，trace 内存 O(D·(N+M))：5000 行文件整体替换 ≈ 万步 × 2 万 int ≈ 800MB。入口 `EditCalculator.diff`（:65）在 `TreeSitterLanguageAdapter.java:118` 的 `parseIncremental` 上，即 LSP 每次 didChange——大段粘贴即触发。同模块 `fix/UnifiedDiff.java:31-33` 对同形态问题有 `MAX_LCS_CELLS = 4_000_000` 上界并回退整块替换（design 03 §1.2 已裁定增量正确性与分解方式无关——回退为单 hunk 合法且正确）。
- **C10a（relationalStopBy 静默兜底）**：engine/CompiledRule.java:694-695（plan 10 后行号）`default: return StopBy.end();`——**注意 parser 将缺省 stopBy 归一化为 "end"（RuleDslParser:530-531），合法值 "end" 今天就走 default**；修复须先加显式 case。STOP_BY_VALUES={neighbor,end,rule}（:103）校验挡住非法值，但静默兜底的不变式应收归本地。
- **C10b（RuleResultCache 条目级零校验）**：cli/RuleResultCache.java:84-85 version 非 Number 时 `(Number)` 强转 CCE；:120-127 hash 命中但 `diagnostics` 缺失/形状错时 `raw.size()` NPE 或产出全 null Diagnostic。类 javadoc 承诺 corrupt cache fail-closed abort——顶层与 version 两层有校验，条目级损坏虽也中断 run 但错误信息丢失（裸 NPE/CCE），与自述契约不符。
- **C10c（SuiteResult.ruleId 语义漂移）**：testing/RuleTestRunner.java:177,186 早失败路径传 `suiteName`，:194,213 正常路径传 `rule.getId()`；SuiteResult.java:12 契约写明 "the id of the rule under test"。规则加载失败时规则 id 客观不可得，用 suiteName 是合理降级但未声明——同一字段两种含义。
- **C10d（ConstantPropagation javadoc 漂移）**：semantic/ConstantPropagation.java:27-28 宣称字符串拼接已折叠；:156-164 实现对 BinaryExpr 返回 `expr.toString()` 原文（`"a" + "b"` 返回含引号源文本而非 `ab`）。实现自洽（v1 字面原文形态），javadoc 失真。
- 测试防线：core 全量 761 绿；nop-lint-java 101 绿（TestSemanticAnalyzer/TestDataFlowAnalyzer/TestDataflowQueries 家族）；TestDeepProfileGate/TestBudgetDegradeLadder 覆盖降级面。

## Goals

- `findVar` 按真正的作用域嵌套深度解析最内层声明：C1 反例形态下 `use(x)` 解析到内层 x（可证伪回归测试钉死），全部既有 dataflow 测试零回归。
- fix 模板多捕获路径与单捕获路径防御对称：未绑定多捕获抛 `NopLintException`（fail-closed 消息含捕获名与规则 id），不再 NPE。
- Myers diff trace 设显式上界（同 `UnifiedDiff.MAX_LCS_CELLS` 的规模纪律），超界回退"公共前后缀 + 单一合并 hunk"（结果仍正确，design 03 §1.2 裁定背书）；LSP 增量路径大粘贴不再有 OOM 面。
- `relationalStopBy` 未知值 fail-closed；RuleResultCache 条目级形状校验报"cache entry corrupt"语义错误；SuiteResult 早失败路径的 ruleId 降级语义显式化；ConstantPropagation javadoc 与实现对齐。
- 行为语义面（匹配结果、诊断流、缓存哈希/指纹值）零变化。

## Non-Goals

- 不改 ConstraintIndex 的并集校验语义（跨分支模板引用的编译期拒绝属 design 层裁定，本轮以运行期 fail-closed 收口；编译期方案归 Non-Blocking Follow-up）。
- 不合并 findVar 与 ScopeAnalyzer.resolve 两套实现（收敛归 plan 12 语义层收敛）。
- 不做 Myers 线性空间分治（上界 + 回退已达目的）。
- 不改 LintNode/matcher 任何性能面（plan 08/09 范围）。

## Scope

### In Scope

- `nop-lint-java`：semantic/DefUseChain（findVar/scopeEncloses）。
- `nop-lint-core`：fix/TemplateFix、lang/EditCalculator、engine/CompiledRule（仅 relationalStopBy 一处）、cli/RuleResultCache（get/条目解析）、testing/RuleTestRunner + SuiteResult（javadoc/占位语义）、semantic/ConstantPropagation（javadoc 对齐）。
- 各缺陷可证伪焦点测试。

### Out Of Scope

- CompiledRule 编译矩阵结构（plan 10）、MetaVarEnv/KindIndex（plan 08）。
- LSP/CLI/Mojo/GraphQL 面（plan 07 已收口）。

## Execution Plan

### Phase 1 - DefUseChain 最内层作用域判定（Fix）

Status: completed
Targets: `nop-lint/nop-lint-java/src/main/java/io/nop/lint/java/semantic/DefUseChain.java`

- Item Types: `Fix`

- [x] `findVar` 改为真正的最内层作用域比较，**双键 + 前置过滤（审查 R1 必修 1）**：(a) 候选声明的 scope BlockStmt 嵌套深度更深者优先；(b) 深度并列时**声明位置必须先于引用位置**（`varLine <= refLine` 过滤，对齐 ScopeAnalyzer.visibleDeclarationIn :186 语义——否则兄弟同深度形态[双 `catch (Exception e)`、for-init 重名]仍然误判）；(c) 兜底键 = 声明行号。不引入 solve 层依赖
- [x] 参数/lambda/类体屏障语义逐字保持（plan F3 裁定面：lambda 参数独立作用域、匿名类体屏障、方法级参数全域）
- [x] 回归测试（可证伪）：C1 反例形态（内层块声明在前、外层声明在后）`use(x)` 解析到内层 x；既有同名遮蔽（正常嵌套）行为不变；全部 TestDataFlowAnalyzer/TestDataflowQueries 零回归

Exit Criteria:

- [x] C1 反例回归测试落地（修复前必红：行号最大键选择外层声明）；修复后全绿
- [x] nop-lint-java 全量测试零回归（101+）；javadoc 与实现一致
- [x] `No owner-doc update required`（设计语义 06 §4.4 未变，属实现纠偏）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - TemplateFix 多捕获 fail-closed + stopBy 兜底收口（Fix）

Status: completed
Targets: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/fix/TemplateFix.java`、`nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/engine/CompiledRule.java`

- Item Types: `Fix`

- [x] `Slot.render` 多捕获路径补 null 检查：未绑定时抛 `NopLintException`（消息含捕获名与规则 id——**TemplateFix 增加 ruleId 字段**（compile 传入，审查 R1 裁定），单捕获路径消息同步带 ruleId）
- [x] `relationalStopBy`：**先加显式 `case "end": return StopBy.end();`（parser 将缺省归一化为 "end"——RuleDslParser:530-531，一切未声明 stopBy 的关系规则今天就走 default），再让 `default` 抛 `NopLintException`（消息含实际值与合法值集）**——顺序颠倒会使全部关系规则编译爆炸（审查 R1 必修 2）
- [x] 回归测试：跨 `any` 分支模板引用场景——`TemplateFix.compile("demo/x", "m2($$$A);", Set.of(), Set.of("A"))` + 空 env `apply` 直测 NPE→NopLintException（单测足矣），**另补 CompiledRule.compile 级集成**（any 两分支模型 + bar 分支 match → templateFix().apply）；stopBy 非法值测试放在 `io.nop.lint.core.rule` test 包（Relational/Matcher 构造器包私有——审查 R1 注）

Exit Criteria:

- [x] NPE 面消除（跨分支场景测试红转绿）；既有 TestCliAutofixEndToEnd/TestFixEngineWiring/TestConstraintEngineEndToEnd 零回归（fix e2e 三场景不破坏）
- [x] stopBy fail-closed 测试落地；CompiledRule 既有测试零回归
- [x] `No owner-doc update required`（fail-closed 纪律对齐，无契约变化）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Myers trace 上界 + RuleResultCache 条目校验 + 语义显式化（Fix）

Status: completed
Targets: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/lang/EditCalculator.java`、`nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/cli/RuleResultCache.java`、`nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/testing/RuleTestRunner.java`、`nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/testing/SuiteResult.java`、`nop-lint/nop-lint-java/src/main/java/io/nop/lint/java/semantic/ConstantPropagation.java`

- Item Types: `Fix`

- [x] `myersOps` trace 设显式规模上界（trace 总 int 数，量级对齐 `UnifiedDiff.MAX_LCS_CELLS=4_000_000` 纪律，static final 同款惯例）；**超界钉死为回退**（审查 R1 裁定：抛出会把 LSP 大粘贴从 OOM 面变成每次 didChange 硬失败，与 Goal 和 UnifiedDiff :127 预检+回退先例冲突）——复用既有公共前后缀剥离（:73-78），超界时直接构造单一合并 hunk（anchored end = oldStart+(newEnd-newStart) 恒合法）
- [x] `RuleResultCache.get` 条目级形状校验：`raw instanceof List`、逐字段类型判定，损坏条目抛 `NopLintException`（消息 "cache entry corrupt" 语义 + 路径）；version 读取用 `instanceof Number` 模式匹配替代强转；**指纹与哈希值不变**（旧缓存兼容）
- [x] `SuiteResult` 早失败路径 ruleId 降级显式化：ruleId 字段 javadoc 写明"规则加载失败时为 suitePath（id 客观不可得的显式降级）"或改传显式占位符——二选一，消除歧义
- [x] `ConstantPropagation` javadoc 与实现对齐（v1 返回字面原文形态，字符串拼接不在折叠面）
- [x] 回归测试（可证伪形态，审查 R1）：**~1000 行全量替换**触发上界 → 断言回退路径恰好产出 1 个 hunk，且将 hunk 应用回 old source 能**逐字节重建 new source**；损坏缓存条目（`put()+save()` 后文本改写：diagnostics 缺失/元素错型，保住 hash 与 fingerprint 走到条目解析）报 "cache entry corrupt" 而非裸 NPE/CCE；旧格式合法缓存仍命中（指纹不变性）

Exit Criteria:

- [x] 上界回退测试（单 hunk + 逐字节重建）+ 损坏条目测试 + 缓存兼容测试落地；既有 TestNopLintCliCache/TestEditCalculator 家族零回归
- [x] LSP 增量解析测试（TestNopLintLanguageServer didChange）零回归
- [x] `No owner-doc update required`（UnifiedDiff 已有同型上界先例，design 03 §1.2 裁定覆盖回退语义）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 4 类 in-scope live defect 全部修复且有可证伪测试背书（C1 反例、C2 NPE 场景、M3-A 上界、C10a/b/c/d）
- [x] 无 in-scope live defect 被降级到 deferred/follow-up
- [x] 行为语义面零漂移（匹配/诊断/缓存指纹值不变——缓存兼容测试背书）
- [x] owner docs：`No owner-doc update required`（逐 Phase 已裁定）；如有偏离在此更新
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] Anti-Hollow Check：修复调用链连通（DefUseChain.findVar←DataflowQueries←xscript dataflow 绑定；Slot.render←FixApplier 渲染点；EditCalculator.diff←TreeSitterLanguageAdapter.parseIncremental←LSP didChange；RuleResultCache.get←CheckRunner cacheHit）；无空方法体/静默跳过
- [x] `./mvnw test -pl nop-lint/nop-lint-core,nop-lint/nop-lint-java,nop-lint/nop-lint-nop -am` 全绿（**nop-lint-nop 必须在列——dataflow 规则的 RuleTester 套件在下游，C1 改变 useCount 归属的回归在盲区里不可见，审查 R1 必修 3**）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-lint-core --severity high` 退出 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出 0

## Non-Blocking Follow-ups

- 跨分支模板引用的编译期拒绝（CaptureIndex 按分支携带捕获集）——需 design 层裁定，本轮运行期 fail-closed 已收口风险。
- findVar 与 ScopeAnalyzer.resolve 的实现合并（plan 12 语义层收敛收纳）。

## Closure

Status Note:四类内核缺陷全部修复且有红转绿可证伪测试：C1 遮蔽判定（深度+声明先于引用双过滤，跨解析反例与双 catch 同名参数两测试在旧代码下实测为红）、C2 多捕获 fail-closed（TemplateFix 增 ruleId 字段，直测+引擎级集成双覆盖）、M3-A Myers 上界回退（1000 行全量替换→单 hunk→逐字节重建）、C10a-d 全部收口。下游 nop-lint-nop 76 测试（dataflow 规则套件）零回归。
Completed: 2026-09-25

Closure Audit Evidence:

- Reviewer / Agent: R1 对抗审查 agent_b9372a38（draft）；独立 closure audit agent_805765fb（fresh session）
- Audit Session: agent_805765fb-32b7-48ed-9362-f654e4f6c235（配额中断后于 13:36 窗口完成）
- Closure Audit Evidence 补充：audit 员亲手 git checkout 还原旧代码复现 2 Failures（红转绿独立证实）；969 测试（790+103+76）实测全绿；sha256/指纹零触碰 diff 取证；3 项 Info 级观察（findVar 同行同深度并列由迭代序决定——归 plan 12 双实现合并时消除；工作树另有无关在途文件）均非阻塞
- Evidence:
  - C1：TestOldDataFlowAnalyzer 两新例 stash 法验证旧代码 2 Failures、修复后 24/0 全绿；java 模块 103/0
  - C2：TestTemplateFixUnboundMultiCapture（直测）+ TestCompositeRuleCompile.crossBranchTemplateCaptureFailsClosedAtRenderTime（引擎级集成，消息含规则 id 与捕获名）12/0；TestCliAutofixEndToEnd 11/0（fix e2e 不破坏）
  - M3-A：TestEditCalculator 18/0（1000 行全量替换→恰 1 hunk→逐字节重建断言）；MAX_TRACE_CELLS=4M 对齐 UnifiedDiff 纪律，回退非抛出（R1 裁定）
  - C10a：TestStopByFailClosed 2/0（显式 end 编译通过+bypass parser 的 sideways 值抛错含合法值集）；C10b：TestRuleResultCacheCorruptEntry 3/0（缺 diagnostics/错型字段→"cache entry corrupt"，合法条目重放不变）；C10c：SuiteResult javadoc 显式降级语义；C10d：ConstantPropagation javadoc 与实现对齐
  - 工具门禁：doc-links 0 errors；hollow-scan exit 0；check-plan-checklist exit 0（completed 态）
- Follow-up: findVar 与 ScopeAnalyzer.resolve 合并归本 plan follow-up（不阻塞 12）
