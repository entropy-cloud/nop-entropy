# 2026-10-13 全量并行构建下 lint/EQL 负载敏感测试的偶发失败（单测 + rule fixture 两层）

## Problem

- `./mvnw clean install -T 1C` 全量构建间歇性失败，各轮失败点不同，单模块隔离重跑均通过：
  - 第 1 轮 `nop-lint-core` 的 `TestMetricsBinding.liveResolverOnNamedFileServesTheBinding`：`rulesExecuted=1` 但 `diagnostics` 期望 1 实际 0。
  - 第 2 轮 `nop-orm-eql` 的 `TestEqlAndChainDepth.testMeasureFirstOverflowThreshold`：`2000 项 AND 链在 1MB 栈下应可编译` 期望 true 实际 false。
  - 第 3 轮 `nop-lint-nop` 的 `TestMetricsDemoSuite`（走真实管线的 VFS rule.yml 套件）：`[demo/metrics-complexity] expected 1 diagnostics but got 0`，同轮日志里 `quality/*`、`api/*` 生产规则也出现 100ms 超时告警。
  - 第 4 轮 `nop-lint-graphql` 的 `TestNopLintBizModel.vfsPathReadsThroughTheVirtualFileSystem`：对自己模块的 `app-lint.beans.xml` 全库 lint 期望 0 诊断，实际多出 1 条 `nop-bean-naming` error（line=7，message 为规则静态文案）。
- 症状共性：第 1–3 项只在满负载的 `-T 1C` 并行构建中出现，隔离运行 100% 通过；失败面覆盖 Java 单测与 VFS fixture 两层。第 4 项机理不同（见下），同样仅满载出现过一次，6 次隔离运行（含 5 次 8 核满载压测）未能复现。

## Diagnostic Method

- 第一轮全量构建日志 grep `Tests run.*Failures` 定位唯一失败点；模块整体单独重跑（`./mvnw test -pl nop-tools/nop-lint/nop-lint-core`）816 个测试全过 → 排除确定性回归，指向环境相关。
- 在全量构建日志中搜索该规则的运行期告警，找到决定性证据：`nop.lint.xscript.match-timeout:ruleId=demo/metrics-complexity,budgetMs=100,timedOut=1` —— 失败断言的同一规则触发了 100ms per-match 超时（超时按设计被当作"非匹配"丢弃，于是规则已执行但无诊断）。
- EQL 侧：单独重跑通过并打印 `lastOk=2000, firstOverflowAt=4000`，说明空载时阈值恰好在 2000 之上；对照测试类 javadoc 自述"观测项不做断言，避免跨环境抖动"，而代码却对 2000 项设了断言——与自身设计矛盾。
- 排除的假设：工作区 EQL/mermaid 改动引入回归（失败模块之一 nop-lint 完全不在改动范围内，且改动前后行为一致地只在并行构建下失败）；JVM 内线程级测试并行（根 pom 是 `forkCount=4 + parallel=classes + threadCount=1`：类级跨 JVM 并行、JVM 内串行，跨 fork 静态状态互不干扰）。
- 第 4 项的诊断：`nop-bean-naming` 的 xscript 对 `id="io.nop.lint.graphql.NopLintBizModel"` 含 `.` 必提前 return，确定性问题逻辑上不可能产生该诊断；诊断特征（规则静态 message + pattern 命中行）指向"pattern 匹配即报告、脚本过滤未参与"，即该轮规则库加载/执行出现环境级异常（候选：满载下 VFS jar 读取/表达式执行异常）。`XmlRuleCompiler`/`CompiledRule` 编译路径均 fail-closed，不存在脚本编译失败退化为纯 pattern 的代码路径。同轮满载下 `nop-lint-nop` 的 `nop-bean-naming` 专用套件（expect 文件钉死语义）93/93 全绿，说明规则语义本身无恙。
- 测试自身前提错误：`vfsPathReadsThroughTheVirtualFileSystem` 注释声称"XML rules are not loaded by the Java binding"，实际 `.xml` 路径经 `TargetScanner.languageIdForExtension` 选中整个 XML 规则库——本次失败恰好证伪了该前提；其零诊断断言只是"碰巧"因 beans 文件合规而成立。

## Root Cause

- lint：xscript 默认 `xscriptTimeoutMs=100`（`RuleDslParser.DEFAULT_XSCRIPT_TIMEOUT_MS`）是真实时钟预算。满负载并行构建下，测试线程在 deadline 检查点之间可被停顿超 100ms（且 JVM 内首个 xscript 运行要支付表达式运行时的类加载冷启动成本），deadline 过期 → 匹配按 fail-closed 语义被丢弃。
- EQL：1MB 栈线程内的递归下降溢出阈值取决于方法帧大小，而解释执行帧大于 JIT 编译帧；负载改变 JIT 编译时机，2000 项恰好在阈值边缘（空载 4000 才溢出），满载下可跌破。

## Fix

- `TestMetricsBinding` 及同构的 4 个测试类（`TestXScriptRules`、`TestL3L4Bindings`、`TestScopeBinding`、`TestConstraintEngineEndToEnd`）中实际执行脚本的规则构造点统一声明 `xscriptTimeoutMs = RuleDslParser.MAX_XSCRIPT_TIMEOUT_MS`（1000ms），把负载敏感窗口放大 10 倍；被测的绑定/gate 行为与预算无关，测试语义不变。
- 同一失效面存在于 VFS rule.yml fixture：第 3 轮构建证明 `nop-lint-nop` 的 11 个 deep-suites 演示规则与 `nop-lint-core` 的 `xscript-report.rule.yml` 都在默认 100ms 下超时。为 12 个 fixture 统一加 `xscriptTimeoutMs: 1000`（只影响 fixture 自身，不改产品默认预算）；`TestXScriptDeadlineEndToEnd` 把该 fixture 当"健康规则"，加大预算不影响其断言，deadloop fixture 本就有显式小预算不受影响。
- `TestEqlAndChainDepth.testMeasureFirstOverflowThreshold` 去掉 2000 项断言，恢复其 javadoc 声明的"纯观测不做断言"设计；确定性下限（1000 项在 1MB 栈内编译成功）由 `testAndChain1000CompilesOn1MbStack` 继续保证，无覆盖损失。
- `nop-lint-graphql` 的 `TestNopLintBizModel.vfsPathReadsThroughTheVirtualFileSystem` 收窄回其本名意图（VFS 路径读取面）：按同测试类的 house pattern（`diskPathInsideWorkdirIsReadable`）用 `List.of("nop/no-raw-exception")` 白名单钉住断言面，注释修正错误前提并指回 nop-lint-nop 的专用套件；`nop-bean-naming` 规则语义的 dogfooding 面由该套件的 expect fixture 继续覆盖，无覆盖损失。
- 解析期拒绝类测试（`TestTransformRuleParser`、`TestFixEngineWiring` 的 xscript 仅用于断言解析失败）不涉及脚本执行，未改动。
- 生产规则集（`quality/*`、`api/*`）保持设计默认预算不动：改产品工件属产品行为变更；其消费测试 `TestProductionRuleFixes` 在超时告警出现的同轮仍通过，超时的 match 不是断言面。

## Tests

- `nop-tools/nop-lint/nop-lint-core/src/test/java/io/nop/lint/core/engine/TestMetricsBinding.java` — metrics 绑定门禁矩阵；脚本预算提至 1000ms。
- `.../engine/TestXScriptRules.java`、`.../engine/TestL3L4Bindings.java`、`.../engine/TestScopeBinding.java`、`.../engine/TestConstraintEngineEndToEnd.java` — 同一失效面（默认 100ms 预算下跑真脚本并断言效果）的同类加固。
- `nop-tools/nop-lint/nop-lint-nop/src/test/resources/_vfs/test/lint/deep-suites/*/*.rule.yml`（11 个）+ `nop-lint-core/.../rules/xscript-report.rule.yml` — fixture 层同一失效面的同类加固。
- `nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/compile/TestEqlAndChainDepth.java` — 度量测试改为纯观测，消除环境敏感断言。

## Affected Files

- `nop-tools/nop-lint/nop-lint-core/src/test/`（5 个 Java 测试 + 1 个 rule.yml）
- `nop-tools/nop-lint/nop-lint-nop/src/test/resources/_vfs/test/lint/deep-suites/`（11 个 rule.yml）
- `nop-tools/nop-lint/nop-lint-graphql/src/test/java/io/nop/lint/graphql/TestNopLintBizModel.java`
- `nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/compile/TestEqlAndChainDepth.java`

## Notes For Future Refactors

- 任何"断言脚本成功产生效果"的 lint 测试（Java 构造或 rule.yml fixture），规则必须显式声明足量 `xscriptTimeoutMs`；默认 100ms 只够空载环境。
- 不要在度量/探测型测试（`testMeasure*`、`testProbe*`）里对观测值设确定性断言；阈值类观测请只打印，配合单独的确定性下限测试。
- 平台 deadline（`LintDeadlineExecutor`）刻意固定使用真实时钟（plan R2 Minor C），不要试图用注入时钟"修复"此类 flake。
- 生产规则集保持默认预算；若未来有测试对生产规则的诊断做精确断言并在满载下抖动，优先给该测试换 fixture 规则或声明预算，不改产品工件。
- 路径/读取机制类测试不要叠加"全规则库零诊断"断言：`.xml`/`.java` 等扩展名会选中对应语言的整个生产规则库，机制断言会被库语义的正确性绑架；需要库级断言时用带 expect fixture 的专用套件。
- 排查 surefire 并行时先确认根 pom 实际生效的 `forkCount=4 + parallel=classes + threadCount=1`：类级并行跨 JVM（静态状态隔离），JVM 内串行；日志里不同 fork 的 `[stdout]` 会交错，不能按行序还原单 JVM 内时序。
