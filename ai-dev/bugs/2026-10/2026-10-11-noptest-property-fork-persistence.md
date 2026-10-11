# 2026-10-11 @NopTestProperty 配置在同 fork 跨测试类持久 Fix

## Problem

- 单个测试类上的 `@NopTestProperty`（或测试内 `setTestConfig`/`assignConfigValue`）注入的配置值，在同一个 surefire fork 中后续阶段仍可读到：plain JUnit 测试类、类间窗口期代码、以及被 `System.setProperty` 污染的变量——表现为"配置在整个 fork 持久"。
- 根 pom surefire 配置为 `forkCount=4 + reuseForks=true + parallel=classes + threadCount=1`：每个 fork JVM 串行执行一串测试类，所有 static 状态跨类存活。

## Diagnostic Method

- 先查 `NopJunitExtension.afterAll`：只有 `CoreInitialization.destroy()` + 时钟恢复 + `BaseTestCase.endTest()`（后者仅清 `g_testConfigs` map），**没有任何配置恢复**——恢复被设计成惰性的，依赖下一个受管类 `beforeAll` 第一行的 `AppConfig.getConfigProvider().reset()`（提交 355cc8440a）。
- 追 `AbstractConfigProvider.reset()` 恢复语义：`usedRefs.keySet().retainAll(staticValues.keySet())` + 按快照回灌；而 `staticValues` 快照在 `getStaticConfigReference` 首次访问时一次性采样 `System.getProperty`，fork 内永不刷新（`containsKey` 门）。
- 追 provider 生命周期：`ConfigStarter.doStop()` 不把 `AppConfig` 换回 `SimpleConfigProvider`，上一个类注册的 `DefaultConfigProvider` 存活到 fork 退出；`CoreInitialization.loadBootstrapConfig` 每次 initialize 重新 `assignConfigValue` 注入 bootstrap 值。
- 排除的假设：`NopTestConfigProcessor` 写 system property（实际只走内存 `setTestConfig`）；`DefaultConfigProvider.configRefs` 与 `usedRefs` 长期别名（`changeConfigSource` 后即分离）；受管类之间的纯内存泄漏（retainAll + 快照回灌链路实际成立——受管类之间不泄漏）。
- 决定性证据：全仓 grep `System.setProperty`——`TestChatServiceFailoverAdapterStreaming` 等 16 处测试写入且多数不清理；`testing.md` 既有「测试配置隔离」节与 `TestConfigProperties.testReset` 被注释的断言证明该问题早有记录但从未被系统修复。

## Root Cause

- `NopJunitExtension.afterAll` 不恢复配置环境：受管类结束后到下一个受管类 `beforeAll` reset 之间存在污染窗口；窗口内的 plain JUnit 测试（全仓 245 个直接继承 `BaseTestCase` 的类）、静态初始化器读到残留值。
- fork 级不可逆通道：`System.setProperty` 写入的值经 `SysPropertyConfigSourceLoader` 进入每次 initialize 的 config source，并被 `staticValues` 快照采样为"基线"——`reset()` 会忠实恢复到污染值，fork 存活期内无法洗掉。

## Fix

- `NopJunitExtension.afterAll` 末尾补 `AppConfig.getConfigProvider().reset()`：与 `beforeAll` 的 reset 对称，类结束即恢复内存配置环境，窗口消失；`beforeAll` 的 reset 保留为兜底。
- 三个 gateway failover 测试类 `@AfterAll` 补 `System.clearProperty("nop.ai.service.rate-limit-acquire-timeout")`，堵住 sysprop 通道的已知实例。
- `docs-for-ai/02-core-guides/testing.md`「测试配置隔离」节改写为修复后的行为契约。

## Tests

- `nop-autotest/nop-autotest-junit/src/test/java/io/nop/autotest/junit/TestNopJunitExtensionConfigRestore.java` — 模拟受管类生命周期后，断言动态读（`AppConfig.var`）与静态 ref（`varRef` 快照路径）在 `afterAll` 后均恢复缺省值。反向验证：注释 reset 后测试红（泄漏值 `injected-dynamic` 可见），恢复后绿。

## Affected Files

- `nop-autotest/nop-autotest-junit/src/main/java/io/nop/autotest/junit/NopJunitExtension.java`
- `nop-ai/nop-ai-gateway/src/test/java/io/nop/ai/gateway/failover/TestChatServiceFailoverAdapterStreaming.java`
- `nop-ai/nop-ai-gateway/src/test/java/io/nop/ai/gateway/failover/TestChatServiceFailoverAdapterNonStreaming.java`
- `nop-ai/nop-ai-gateway/src/test/java/io/nop/ai/gateway/failover/TestFailoverMetricsLocal.java`
- `docs-for-ai/02-core-guides/testing.md`

## Notes For Future Refactors

- `staticValues` 快照 write-once 语义仍在：sysprop 污染值若在变量首次静态访问前写入，fork 内无法恢复干净基线；彻底修复需给 `nop-api-core` 的 provider 增加快照失效能力（`io.nop.api.core.*` 为 plan-first 保护域）。
- 全仓约 13 处测试内 `System.setProperty`（nop-stream `TestPlan360R1Equivalence`/`TestBackpressureGateWiring`/`TestCheckpointJobIdentityIsolationE2E`/`TestExerciseParams`、nop-cli `TestNopCli`/`NopCliTasks`、nop-autotest-core `TestAutoTestShardTableInit` 等）未清扫，需逐个核对是否补 `clearProperty`。
- `NopJunitExtension.beforeAll` 的 `isDirty()` 门实际永真（`processTestConfig` 无条件 assign 两个内置配置），"仅脏时清模型缓存"优化为死代码——若优化之，须先验证配置条件编译产物（feature:on）的缓存失效面。
