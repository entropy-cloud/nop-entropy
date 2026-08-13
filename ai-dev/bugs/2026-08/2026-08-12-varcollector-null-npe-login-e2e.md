# 2026-08-12 VarCollector 置空导致 LoginApiBizModel NPE（纯 JUnit E2E 在 AutoTestCase 之后执行）

## Problem

- 全量套件 `./mvnw test -pl nop-ai -am -T 1C` 中 `nop-auth-service` 的 `TestChannelScanBindLoginE2E.testScanLoginFullChainE2E` 报 NPE：`VarCollector.instance()` 返回 null，`LoginApiBizModel.buildLoginResult:99` 崩溃。
- 隔离运行 `-Dtest=TestChannelScanBindLoginE2E` 通过（4/4），失败只在全量套件中出现，表现为"偶发"。
- 2026-08-09（nop-ai-channel-integration W6-2 引入该测试）即有，I1 closure audit 已登记为 pre-existing Info（08-12.md 当日记录）。

## Diagnostic Method

- 先确认失败与 mission 改动无关：HEAD~1 只含文档，门禁提交只动 nop-ai/ai-dev/tools，nop-auth 最近改动为 2026-08-09 的 channel-integration 提交 → 判定 pre-existing。
- 隔离运行复现失败 → 失败必然依赖类间顺序/共享状态，而非用例本身逻辑。
- 读 `VarCollector` 源码：静态 `_instance` 默认 `new VarCollector()` 非 null，唯一置空入口是 `AutoTestCase.complete()` 的 `VarCollector.registerInstance(null)`。
- 确认 nop-auth-service 测试里既有 `AutoTestCase` 子类（TestBizAction 等，按字母序排在 TestChannelScanBindLoginE2E 之前），其 complete() 将静态实例置 null 后不再恢复；E2E 类是纯 JUnit 类不重新注册 → 命中。
- 排除的假设：非并发/时序竞态（失败在 0.6s 同步调用点，非异步完成回调）；非本次提交引入。

## Root Cause

- `AutoTestCase.complete()` 执行 `VarCollector.registerInstance(null)`（nop-autotest AutoTestCase.java:230），将 JVM 级静态 `VarCollector._instance` 置 null，且后续不再恢复。
- `LoginApiBizModel.buildLoginResult`（nop-auth-service）与 `RuleRuntime.java:201` 等生产代码无条件调用 `VarCollector.instance().collectVar(...)`。`VarCollector` 是可选的自测支持设施（生产默认实例为 no-op），但生产调用方未容忍其缺失。
- 同 JVM 内任何 AutoTestCase 类先于纯 JUnit 类执行，即触发 NPE（surefire 类序决定"偶发"）。

## Fix

- `LoginApiBizModel.buildLoginResult` 改为 null 安全：先取 `VarCollector varCollector = VarCollector.instance()`，非 null 才 `collectVar`（2 处调用点）。设计意图：测试设施缺失不影响生产逻辑，与默认 no-op 实例语义一致。
- 不改 `AutoTestCase.complete()` 的 null 行为（框架层、影响面大，且无代码依赖 null 信号）；同样语义风险的 `RuleRuntime.java:201` 留待后续（不在本次模块集）。

## Tests

- `nop-auth/nop-auth-service/src/test/java/io/nop/auth/service/TestChannelScanBindLoginE2E.java`：`@BeforeEach` 显式 `VarCollector.registerInstance(null)` 模拟 AutoTestCase 之后的 JVM 污染状态，使全链登录 E2E 确定性走 null 路径——回归防护，隔离运行也能复现原 NPE。

## Affected Files

- `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/biz/LoginApiBizModel.java`
- `nop-auth/nop-auth-service/src/test/java/io/nop/auth/service/TestChannelScanBindLoginE2E.java`

## Notes For Future Refactors

- 新增调用 `VarCollector.instance()` 的生产代码必须 null 容忍（其被 AutoTestCase 置空是平台既有行为）。
- 纯 JUnit（非 AutoTestCase）E2E 测试在全量套件中可能跑在 AutoTestCase 之后，涉及 VarCollector 的代码路径必须显式模拟或重置静态状态。
- `RuleRuntime.java:201`（nop-rule-core）存在同族隐患，重构规则执行路径时注意。
