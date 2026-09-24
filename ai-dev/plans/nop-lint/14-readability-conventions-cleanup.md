# 14 可读性与规范清理（死代码/复制粘贴/import 规范/诊断文本）

> Plan Status: draft
> Last Reviewed: 2026-09-25
> Source: `ai-dev/analysis/2026-09/2026-09-25-nop-lint-quality-optimization-deep-audit.md`（findings D2、D3、D5、D6、D7、C10-杂项）
> Related: 07/08/10/11/12/13（功能与性能项先行，本 plan 纯清理收尾）

## Purpose

收纳审计的可读性/规范类 finding：CheckRunner 编排拆分、RuleDslParser XOR 校验收敛、五处死代码删除、PrintStream→Writer 桥去重、RunSummary/CliOptions/RuleDslModel 集合与构造器面治理、import 规范与内联 FQN 清理、过时 roadmap 引用的诊断文本修正、异常类型统一。纯重构：行为零变化（ConsoleReporter golden 字节约束为回归防线）。

## Current Baseline

（行号引用见分析报告 D2/D3/D5/D6/D7/C10-杂项；draft review 逐条重核。plan 07-13 执行后部分行号会漂移，以 review 时 live 代码为准。）

- **D2**：CheckRunner `run(scan,…,cacheFile)` 约 70 行串行六件事（规则集加载/语言校验/baseline 装载/engine 组装/cache 互斥与指纹/三模式主循环/落盘），前置 3 个委托 run 重载 + 2 个委托构造器；`discoverMetricsResolver` 等 3 个私有静态方法零调用（plan 07 后仍在）。**注意**：plan 07 已为 run 增加 SourceReader 测试缝重载，拆分须保持该缝。
- **D3**：RuleDslParser 五处同型"XOR 恰一"校验（parseUtils/parseMatcher/parseAllElements/parseNotInner/parseAnyBranches 对象分支）+ 消息拼接模式重复十余次；`RULE_MATCHERS` 与 `NESTED_MATCHERS` 两份相同字面量；`csvToList`/`csvSet` 可合并。解析器状态机本身不拆类（审计裁定）。
- **D5**：`NopLintCli.writerOf` 与 `ConsoleReporter` PrintStream 面内联匿名 Writer 逐行等价（提取共享 PrintStreamWriter，golden 字节不变）；`CheckRunner.cacheHit` 豁免过滤循环与 `applyFilters` 前半段重复；RunSummary 6 个 union 集合暴露策略不一致（2 个防御拷贝 vs 4 个泄漏内部可变集）。
- **D6**：`XmlRuleCompiler`（:281-284,340-344）与 `CompiledRule.regexRejected`（:484-488）错误消息引用已落地的 roadmap item（"until roadmap item 24"/"deferred to roadmap item 22"）——未来时表述已成误导；`Constraints.java:46-67` 与 `SemanticAnalyzer.java:52-62` 孤儿 Javadoc；内联 FQN（CheckRunner `java.nio.file.Path.of`/`LintStats.builder()`、NopLintCli `Arrays.copyOfRange`、TestCommand/MatchCommand/RuleDslParser 等散点）；io.nop 组内 import 紊乱 ×3 文件（plan 07 触碰 CheckRunner 后需复核）；plan 07 在 MatchCommand/CheckRunner 新增的代码须符合规范（已按规范写）。
- **D7**：`CliOptions` 三个兼容构造器零使用（2/3/5 参）；`withFixMode` 的 flag 参数未使用；`RuleDslModel` 13/14 参构造器为死代码（唯一调用点是 15 参版）、`Matcher` 4/10/11 参位置式可空构造器（parser 侧工厂 `matchesMatcher`/`relationalMatcher`/`textOnlyMatcher` 移入本体、其余私有化）、`Constraint` 8 参同型。
- **D6-异常统一**：模块内 `IllegalArgumentException`/`IllegalStateException` 散点（ConsoleReporter:68、RuleResultCache:203、XNodePatternMatcher:110、FileDiff:15、FileFindings:20、DataflowQueries、ScopeAnalyzer:320、LineColBytes:52）统一为 `NopLintException`（已是 NopException 子类）；两个无栈控制流子类（`DiagnosticCapReached`/`AbortedRun`）为显式裁定保留。
- 测试防线：core 全量绿 + TestConsoleReporter golden 逐字节断言（ConsoleReporter 输出面重构的回归防线）。

## Goals

- CheckRunner 主 run 收敛为顺序编排（加载/校验/组装/循环/落盘各成私有方法），行为与测试面零变化；死方法删除。
- RuleDslParser XOR 校验收敛为单一 helper（预计 -100 行，语义零变化）；重复字面量别名化。
- PrintStreamWriter 提取（两处消费，golden 字节不变）；cacheHit/applyFilters 豁免过滤去重；RunSummary 集合统一不可变视图。
- 诊断文本现代化（现在时 + 出口指引）；孤儿 Javadoc 清理；import 规范全绿；内联 FQN 归 import 区；异常类型统一 NopLintException。
- CliOptions/RuleDslModel 构造器面收敛（死构造器删除、工厂方法入本体、位置式可空构造器私有化）。

## Non-Goals

- 不改变任何行为语义、输出字节、退出码、统计口径。
- 不动 plan 08-13 已划定范围的性能/正确性改动。
- 不拆 RuleDslParser 为多类（审计裁定：状态机清晰，仅抽 helper）。
- 不重写 RunSummary 字段结构（30 字段即契约，只统一暴露策略）。

## Scope

### In Scope

- `nop-lint-core`：cli/（CheckRunner、NopLintCli、ConsoleReporter、RunSummary、CliOptions、RuleResultCache 仅异常类型）、rule/（RuleDslParser、RuleDslModel）、xml/XmlRuleCompiler、constraint/Constraints、engine/CompiledRule（仅 regexRejected 文本）、testing/（RuleTestRunner 死重载、SuiteResult 无）、xscript/XScriptEngine（import 顺序）。
- 全部为重构：以全量测试 + golden 断言为回归防线。

### Out Of Scope

- nop-lint-java/js/graphql/maven-plugin（其 finding 已分属 plan 12/13/07）。
- 规则 YAML（D8 独立裁定，见分析报告 Deferred）。

## Execution Plan

### Phase 1 - 死代码与构造器面收敛（Fix）

Status: planned
Targets: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/cli/`、`rule/RuleDslModel.java`

- Item Types: `Fix`

- [ ] 死代码删除：CheckRunner 三个 `discover*` 私有方法（连同不再需要的 import）、RuleTestRunner 两参 `lint` 重载、CliOptions 三个零使用兼容构造器、`withFixMode` 未用 flag 参数（或拼入错误消息——二选一）、RuleDslModel 13/14 参死构造器
- [ ] `Matcher`/`Constraint` 工厂方法移入本体（parser 侧既有工厂搬移），位置式可空构造器私有化；parser 调用点改走工厂
- [ ] RunSummary 集合暴露统一不可变视图（`Collections.unmodifiableXxx`，删两处多余拷贝）；RuleDslModel `getRequires/getOptions/getSettings` 同步统一
- [ ] 回归：全量 core 测试零变化（纯重构无新增测试——`No new test required: 纯重构，行为由既有 761+ 测试背书`）

Exit Criteria:

- [ ] 五处死代码不存在（全仓 grep 零引用）；工厂方法入本体且 parser 调用点迁移
- [ ] RunSummary/RuleDslModel 集合全部不可变视图（写视图抛 UnsupportedOperationException 断言入既有测试或新增 1 例）
- [ ] `./mvnw test -pl nop-lint/nop-lint-core` 全绿零数字变化
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - RuleDslParser XOR 收敛 + CheckRunner 编排拆分（Fix）

Status: planned
Targets: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/rule/RuleDslParser.java`、`nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/cli/CheckRunner.java`

- Item Types: `Fix`

- [ ] `requireSingleMatcher`（或等名）helper 收敛五处 XOR 校验；消息模板保持"exactly one of …"语义与 rule id/location 携带；`RULE_MATCHERS`/`NESTED_MATCHERS` 别名化；`csvToList`/`csvSet` 合并点收敛
- [ ] CheckRunner 主 run 拆分：规则集加载+过滤、语言校验、baseline 装载、engine 组装、cache 打开与指纹、per-file 主循环（cache/fix/report 三路径）、落盘——各成私有方法，主方法收敛为顺序编排；**保持 plan 07 的 SourceReader 测试缝重载**；`NESTED` 分支 `continue` 语义不得静默化
- [ ] `cacheHit` 与 `applyFilters` 的豁免过滤循环提取共享私有方法
- [ ] 回归：TestRuleDslParser（657 行）/TestConstraintParsing 全绿（解析语义零变化——含 fail-closed 拒绝矩阵）；TestCheckRunner/TestNopLintCliCache/TestCliBaseline/TestCliAutofixEndToEnd 全绿

Exit Criteria:

- [ ] XOR 五处收敛为单一 helper（代码结构可观察）；解析矩阵测试零回归（760+ 编译用例语义不变）
- [ ] CheckRunner 主方法为顺序编排（读码可判断六步）；测试缝保留
- [ ] `./mvnw test -pl nop-lint/nop-lint-core` 全绿
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 规范与文本清理（Fix）

Status: planned
Targets: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/`（散点）

- Item Types: `Fix`

- [ ] PrintStreamWriter 提取（包私有）：`NopLintCli.writerOf` 与 `ConsoleReporter` PrintStream 面共用；**golden 字节不变**（TestConsoleReporter 逐字节断言为防线）
- [ ] 诊断文本现代化：XmlRuleCompiler 两处 + CompiledRule regexRejected——"until roadmap item 24"/"deferred to roadmap item 22" 改为现在时 + 出口指引（regex → constraints block；utils/stopBy=rule → tree-sitter 语言路径 only）；孤儿 Javadoc 合并（Constraints、SemanticAnalyzer）
- [ ] import 规范：内联 FQN 归 import 区（CheckRunner/NopLintCli/TestCommand/MatchCommand/RuleDslParser/DefUseChain/JavaSemanticResolver 散点）；io.nop 组内顺序整理（CheckRunner/RuleTestRunner/XScriptEngine）
- [ ] 异常类型统一：散点 IAE/ISE → `NopLintException`（ConsoleReporter:68、RuleResultCache:203、XNodePatternMatcher:110、FileDiff:15、FileFindings:20、DataflowQueries、ScopeAnalyzer:320、LineColBytes:52）；`DiagnosticCapReached`/`AbortedRun` 显式保留（无栈控制流，注释已在）
- [ ] 回归：全量测试零变化；`node ai-dev/tools/check-doc-links.mjs --strict` 绿

Exit Criteria:

- [ ] golden 断言全绿（TestConsoleReporter 未改断言）；诊断文本无已落地 roadmap 的未来时表述（grep 背书）
- [ ] 内联 FQN 散点清零（审计清单范围 grep 背书）；异常类型统一完成（清单范围）
- [ ] `./mvnw test -pl nop-lint/nop-lint-core` 全绿
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 全部 in-scope 清理项落地且行为零漂移（全量测试 + golden 背书）
- [ ] 死代码清零（审计清单范围）
- [ ] owner docs：`No owner-doc update required`（纯重构；诊断文本修正不改契约语义——若文本被 docs 引用则同步，收口时核查）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] Anti-Hollow Check：PrintStreamWriter 被两处真实消费；requireSingleMatcher 被 XOR 五处真实调用（grep 背书）；无空方法体/静默跳过
- [ ] `./mvnw test -pl nop-lint/nop-lint-core -am` 全绿
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-lint-core --severity high` 退出 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出 0

## Deferred But Adjudicated

### 规则 YAML 治理（分析报告 D8）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 涉及 design 层裁决（report() 无参回落顶层 message、metadata 默认值兜底）与 62 文件用户可见输出变更，须先出 design 增补再立项
- Successor Required: `yes`
- Successor Path: design 增补后新 plan（暂记 backlog）

## Non-Blocking Follow-ups

- 表驱动关系匹配器分派（XmlRuleCompiler/RuleDslParser——审计裁定收益有限，可不做）。

## Closure

Status Note: （关闭时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- （关闭时填写或写 no remaining plan-owned work）
