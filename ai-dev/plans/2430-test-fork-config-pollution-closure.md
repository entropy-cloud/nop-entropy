# 2430 测试 fork 配置污染残留三通道收口

> Plan Status: completed
> Last Reviewed: 2026-10-11
> Source: `ai-dev/bugs/2026-10/2026-10-11-noptest-property-fork-persistence.md`；`ai-dev/logs/2026/10-11.md` 同日条目
> Related: 2026-10-11 已落地的先行修复（NopJunitExtension.afterAll 补 reset + gateway 三测试类 clearProperty，未走 plan，直接 fix）

## Purpose

同一 surefire fork（`forkCount=4 + reuseForks=true`）中测试类串行共享全部 static 状态。先行修复已闭合"受管类 → 窗口期"通道；本计划收口剩余三条残留通道，使测试 fork 内配置环境满足：**任何测试类结束后，`System.clearProperty` 过的配置变量在下一次 reset 后恢复到无污染基线；测试代码写入的 system property 均有对称清理；框架内不再存在语义与行为不符的死分支。**

## Current Baseline

- `NopJunitExtension.afterAll` 已补 `AppConfig.getConfigProvider().reset()`（本日先行修复），受管类之间与受管类→窗口期的内存值泄漏已闭合；回归测试 `TestNopJunitExtensionConfigRestore` 在位并做过反向验证。
- `AbstractConfigProvider.reset()`（`nop-kernel/nop-api-core`）恢复基线 = `staticValues` 中**首次静态访问时一次性采样**的 `System.getProperty` 快照（`getStaticConfigReference` 的 `containsKey` 门，write-once）。后果：sysprop 污染值若在变量首次静态访问前写入，即使后续 `clearProperty`，fork 内所有后续 `reset()` 都会"忠实"恢复污染值。`nop-api-core` 属 plan-first 保护域，故走本计划。
- 全仓测试代码 `System.setProperty` 散点（`grep -rn "System.setProperty" --include="*.java" */src/test`，已排除 nop-ai-gateway 三处已修）。**逐文件 triage（2026-10-11 独立审查轮 1 实测）**：
  - 已有对称清理（verify-only，6 文件）：`TestStreamDeserializationFilter`（finally 且 prior==null→clearProperty，正确）、`TestPlan360R1Equivalence`（同模式，正确）、`TestBackpressureGateWiring`（try/finally）、`TestCheckpointJobIdentityIsolationE2E`（@AfterAll）、`TestExerciseParams`（@BeforeEach+@AfterEach 双清）、`TestStreamAggregatorFunctionResolver`（@AfterAll）；
  - 真实缺口（3 文件）：`TestNopCli`（`nop.dev.root-path` 在 2 个 @Disabled 测试内 set 后从不清理；VFS override 用 `System.getProperties().remove(...)` 清理）、`NopCliTasks`（`testLargeDataSet` 设 4 个 datasource prop 零清理）、`TestAutoTestShardTableInit`（@BeforeAll 设 4 个 datasource prop、@AfterAll 不清）；
  - `CodeGen.java` 已注释，无需处理。
- `NopJunitExtension.beforeAll` 的 `if (isDirty()) clearAllCache()` 为死分支：`processTestConfig` 无条件 `setTestConfig` 两个内置项（`CFG_IOC_APP_BEANS_CONTAINER_START_MODE`、`CFG_GRAPHQL_IGNORE_MILLIS_IN_TIMESTAMP`），`assignConfigValue` 必置 dirty=true → 每类必清。注释宣称"没有配置修改时跳过"，与行为不符。
- main 代码中的 `System.setProperty`（`QuarkusIntegration`/`CodeGenTask`/`XlangJavaGenTask`）为生产语义，不在清理范围。
- **执行前置条件**：先行修复（afterAll reset + 回归测试 + gateway clearProperty + testing.md）须先以仅含本任务文件的 scoped commit 落库，避免 baseline 悬空于未提交工作区。

## Goals

- `reset()` 的恢复基线从"首次访问快照"改为"当前系统属性重采样并回写快照"：sysprop 污染 + `clearProperty` 后，fork 内下一次 reset 即恢复干净基线。
- 上述清单内全部测试文件的 `System.setProperty` 具备对称清理（`@AfterAll`/`@AfterEach` `clearProperty`），或逐处记录豁免理由。
- `NopJunitExtension` 的缓存清理分支行为诚实化：无条件 `clearAllCache` + 注释说明真实原因，外部行为零变化。

## Non-Goals

- 不改 `IConfigProvider` SPI 签名（不新增/删除接口方法）。
- 不实现跨测试类"配置发散检测"以跳过 `clearAllCache`（需要按类跟踪有效配置差分，回归风险大于每类清缓存的固定成本；如后续要做，另立 plan）。
- 不清扫 main 代码的 `System.setProperty`。
- 不处理 `staticValues` 之外的模型缓存/编译产物失效面（先行修复的每类 `clearAllCache` 已兜住）。

## Scope

### In Scope

- `nop-kernel/nop-api-core`：`AbstractConfigProvider.reset()` 重采样语义 + javadoc + 回归测试。
- 3 个缺口文件 set/clear 对称化 + 6 个 verify-only 文件复核（见 Current Baseline triage）。
- `nop-autotest/nop-autotest-junit`：`NopJunitExtension.beforeAll` 死分支诚实化。
- owner docs：`docs-for-ai/02-core-guides/testing.md`（测试配置隔离节两处语义同步）。

### Out Of Scope

- `DefinitionConfigProvider`：其 `reset()` 实际**委托** `globalProvider.reset()`（`DefinitionConfigProvider.java:192`，globalProvider 即 `AbstractConfigProvider` 子类），故会进入新语义；不入 scope 的真实理由是主代码无实际调用方、且插件流不在测试 fork 生效（2026-10-11 审查轮 1 裁定）。
- surefire fork 策略本身（`reuseForks=true` 保留）。

## Execution Plan

### Phase 1 - api-core reset() 重采样语义

Status: completed
Targets: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/config/AbstractConfigProvider.java`、`nop-kernel/nop-api-core/src/test/java/...`

- Item Types: `Fix`

- [x] `reset()` 恢复基线改为：对**全部 `staticValues` 键**按当前 `System.getProperty` 重采样、回写快照、对 retainAll 后仍存活的 ref 回灌（语义：reset 后每个静态已知变量 = 当前系统属性值，无则落到声明缺省值）
- [x] `AbstractConfigProvider.reset` javadoc 补语义说明（重采样 + 回写，及为什么：fork 内 sysprop 污染可被 clearProperty+reset 洗净）
- [x] `nop-api-core` 新增回归测试：污染 sysprop → 静态引用登记快照 → clearProperty → reset → 断言 ref 恢复缺省且快照已刷新（旧实现下此断言为污染值）——`TestConfigProviderResetSampling`
- [x] 变异探针：注释重采样行 → 新测试红（`expected: <default-x> but was: <polluted>`）→ 恢复 → 绿；恢复后 grep 确认可执行行真实存在（`AbstractConfigProvider.java:71`，R5 探针纪律）
- [x] `docs-for-ai/02-core-guides/testing.md`「测试配置隔离」节同步重采样语义
- [x] **调用方影响裁定**（2026-10-11 独立审查轮 1 已代做分析，收录为 adjudication）：(a) surefire argLine fork 常驻 sysprop 不受影响（重采样取同值）；(b) setProperty 未清理的变量 reset 后仍为污染值，与旧语义下下次 initialize 经 `SysPropertyConfigSourceLoader` 重注入殊途同归，无实质恶化；(c) setProperty→clearProperty→reset 即本修复目标场景；(d) 从未静态访问的变量经 retainAll 移出 usedRefs 后直落 sysprop/缺省，不受影响；(e) `DefaultConfigProvider` 下"reset 用 sysprop 覆写静态已知 ref、无视 config file 值"为**既有**行为（旧实现相同），后续 `changeConfigSource`/`updateRefs` 顺序保持。

Exit Criteria:

- [x] 新增回归测试在旧实现下失败（污染值可见）、新实现下通过，探针全程留档于 daily log——`./mvnw test -pl nop-kernel/nop-api-core -Dtest=TestConfigProviderResetSampling`：探针红（`expected: <default-x> but was: <polluted>`）/恢复后绿
- [x] `./mvnw test -pl nop-kernel/nop-api-core` 全绿——165/165
- [x] reset 语义消费方现有测试不回归：`./mvnw test -pl nop-core-framework/nop-config -pl nop-autotest/nop-autotest-junit` 全绿——36/36 + 1/1（依赖本地仓库同版本 artifact；未遇并发干扰）
- [x] `testing.md` 与 live 行为一致（人工比对该节文字 vs 实现语义）
- [x] 当日 daily log（`ai-dev/logs/2026/10-11.md`）已更新

### Phase 2 - 测试代码 System.setProperty 对称清扫

Status: completed
Targets: Current Baseline triage 所列 3 个真实缺口文件（`TestNopCli`、`NopCliTasks`、`TestAutoTestShardTableInit`）+ 6 个 verify-only 文件的独立复核

- Item Types: `Fix`

- [x] `TestNopCli`：补 `nop.dev.root-path` / VFS override 的对称清理；裁定现有 `System.getProperties().remove(...)` 与 `clearProperty` 等效（均移除映射），统一保留其一；@Disabled 测试内 set 是否可达需核实（不可达则在代码处注释说明）
- [x] `NopCliTasks`：`testLargeDataSet` 的 4 个 datasource prop 补对称清理（`nop.*` 配置命名空间变量一律补清理，不设豁免——`SysPropertyConfigSourceLoader` 会把全部 sysprop 装入每次 initialize 的 fork 级 config source，不存在"仅该测试自身消费"；豁免仅适用于证明不进 config source 的直读属性，当前清单内不存在此类）
- [x] `TestAutoTestShardTableInit`：@AfterAll 补 4 个 datasource prop 的 clearProperty
- [x] 6 个 verify-only 文件独立复核已记录于 Current Baseline triage（2026-10-11 审查实测），不重复改动
- [x] `No owner-doc update required`（纯测试代码，不改变任何文档化契约）
- [x] **grep 门禁 GAP 裁定（2026-10-11 执行实测）**：行数统计对 3 文件报 GAP，逐一核实均 benign——`TestStreamDeserializationFilter` 2 set/1 clear 中第二处 set 是 finally 恢复语句本身（prior==null→clearProperty 分支正确）；`TestExerciseParams` 5 set/4 clear 中 @BeforeEach+@AfterEach 双清覆盖全部测试；`CodeGen.java` 的 set 为注释行（baseline 已记录）

Exit Criteria:

- [x] 全仓 `grep -rn "System.setProperty" --include="*.java" */src/test` 的每一处，同一文件内存在对应 `clearProperty`（`getProperties().remove` 视同等效）或计划/baseline triage 内记录的豁免理由——行数统计 3 处 GAP 已逐一裁定 benign（见上方 GAP 裁定项）
- [x] 受影响模块聚焦测试绿：`./mvnw test -pl nop-runner/nop-cli-core -pl nop-autotest/nop-autotest-core`——69/69（15 skipped 为 @Disabled，含两个被改测试类）+ 28/28（含 `TestAutoTestShardTableInit` 2/2；离线模式缺 quarkus-picocli/mysql artifact，在线模式通过，已归因）
- [x] `No owner-doc update required` 已裁定
- [x] 当日 daily log（`ai-dev/logs/2026/10-11.md`）已更新

### Phase 3 - NopJunitExtension 缓存清理死分支诚实化

Status: completed
Targets: `nop-autotest/nop-autotest-junit/src/main/java/io/nop/autotest/junit/NopJunitExtension.java`

- Item Types: `Fix`

- [x] `beforeAll` 的 `if (isDirty()) clearAllCache()` 改为无条件 `clearAllCache()` + 注释说明（永真原因：processTestConfig 无条件 assign 两个内置项；每类清缓存是配置条件编译产物——feature:on 求值——的安全网）；`isDirty()` API 保留不删
- [x] `No owner-doc update required`（testing.md「测试配置隔离」节不描述 isDirty 门，经审查轮 1 实测确认）

Exit Criteria:

- [x] 行为零变化论证成立并留档：旧代码 `isDirty()` 在 beforeAll 时点恒为 true（reset 置 false → 两条内置 assign 必置 true → 判断在 assign 之后），新代码删去判断后执行的调用集合相同
- [x] `./mvnw test -pl nop-autotest/nop-autotest-junit` 全绿（`TestNopJunitExtensionConfigRestore` 守门）——1/1
- [x] `No owner-doc update required` 已裁定
- [x] 当日 daily log（`ai-dev/logs/2026/10-11.md`）已更新

## Closure Gates

- [x] 三条残留通道全部落地：reset 重采样（Phase 1）、set/clear 对称（Phase 2）、死分支诚实化（Phase 3）——审计 A/C/D 实测：`AbstractConfigProvider.java:71` 真实可执行重采样行、13+1 个测试 set 站点全部对称或 benign 豁免、beforeAll 44 行无条件 clearAllCache
- [x] 无 in-scope live defect 被降级为 follow-up——审计 G：两项 Deferred 分类诚实（optimization candidate / watch-only）
- [x] owner docs（testing.md）已同步到 live baseline——审计 H：503-515 行与 reset 新语义逐点一致
- [x] 独立子 agent closure audit 已完成并记录证据于本文件 Closure 节——agent_6d50d649-b282-404b-901f-4bf8e4f1f465，结论 ALLOW
- [x] **Anti-Hollow Check**：reset() 新语义被 NopJunitExtension.afterAll/beforeAll 运行时真实消费（beforeAll:32 / afterAll:84 真实调用 `AppConfig.getConfigProvider().reset()`，链路 测试类结束→afterAll→reset→重采样回写 连通）；无空方法体/静默跳过
- [x] `./mvnw test -pl nop-kernel/nop-api-core -pl nop-core-framework/nop-config -pl nop-autotest/nop-autotest-junit` 全绿——165+36+1，BUILD SUCCESS（实现者与审计者双跑）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（warning 均在并发会话 plan 2432 与旧对比文档，与本 plan 无关）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2430-test-fork-config-pollution-closure.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-kernel/nop-api-core --severity high` 退出码 0（332 文件 0 发现，审计者复跑）

## Deferred But Adjudicated

### 跨测试类配置发散检测（跳过 clearAllCache 的真优化）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 现状每类清缓存行为正确且为 feature:on 编译产物安全网；跳过优化需按类跟踪有效配置差分，回归风险（重新引入 5659a2aed1 修复的 TenantSupport 类污染）大于固定成本。
- Successor Required: `no`
- Successor Path: 如需做另立 plan，须先给出差分检测的正确性论证。

### staticValues 之外：bootstrap.yaml 每次 initialize 重注入

- Classification: `watch-only residual`
- Why Not Blocking Closure: bootstrap 值经 `assignConfigValue` 注入属框架既定语义（reset 后由下次 initialize 恢复），非测试污染；仅记录。
- Successor Required: `no`
- Successor Path: 无

## Non-Blocking Follow-ups

- main 代码 `System.setProperty` 三处（QuarkusIntegration/CodeGenTask/XlangJavaGenTask）维持现状，如未来引入可配置化再评估。

## Closure

Status Note: 三条测试 fork 配置污染残留通道全部真实落地（api-core reset 重采样语义 + 回归测试与变异探针闭环；测试代码 System.setProperty 对称清扫含逐处 GAP 裁定；NopJunitExtension 死分支诚实化行为零变化）。审计者对 live repo 实测复跑全部门禁（代码直读 + 命令复跑，不采信实现者自述），未发现空壳实现或被降级的 in-scope defect。审计 Minor 2 条已处置：@Disabled 不可达注释已补（TestNopCli 两处）；scoped commit 在标记 completed 同一收口批次完成。
Completed: 2026-10-11

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，非实现会话）agent_6d50d649-b282-404b-901f-4bf8e4f1f465
- Audit Session: agent_6d50d649-b282-404b-901f-4bf8e4f1f465
- Evidence:
  - Phase 1 Exit Criteria：全部 PASS——`AbstractConfigProvider.java:71` 真实可执行重采样行（非注释/stub）+ javadoc 56-65 行；`TestConfigProviderResetSampling.java:44`（恢复缺省）+ 46-47（快照刷新）双断言点；`./mvnw test -pl nop-kernel/nop-api-core` 165/165 实跑绿；nop-config 36 + autotest-junit 1 联跑绿；探针红/绿记录见 daily log
  - Phase 2 Exit Criteria：全部 PASS——src/test 14 个 setProperty 文件逐一核验（13 在 triage 清单 + 1 为本 plan 新增测试自清理）；3 缺口文件 `git diff HEAD` 与 plan 描述逐字一致；3 处 benign GAP 逐一确认；cli-core 69 run/0 fail/15 skip + autotest-core 28/28 实跑绿
  - Phase 3 Exit Criteria：全部 PASS——beforeAll 无 isDirty 门、44 行无条件 clearAllCache、`isDirty()` API 保留（`AbstractConfigProvider.java:42-44`）；autotest-junit 1/1 绿
  - Closure Gates：9/9 PASS（证据来源同上 + 审计者复跑 scan-hollow exit 0 / check-doc-links exit 0 / check-plan-checklist 在 Evidence 写入后 exit 0）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2430-test-fork-config-pollution-closure.md --strict` 退出码 0（Closure Evidence 写入本节后复跑确认）
  - Anti-Hollow 检查结果：beforeAll:32 / afterAll:84 真实调用 reset()，测试类结束→afterAll→reset→重采样回写链路连通；`scan-hollow-implementations.mjs` exit 0（332 文件 0 发现）
  - Deferred 项分类检查：两项 Deferred（发散检测优化 / bootstrap 重注入）均为 non-blocking 优化或 watch-only，无 in-scope live defect 被降级
  - 审计 Minor 处置：①@Disabled 不可达注释已补（TestNopCli testRunBatchDemo/testRunBatchXmlDemo 两处）；②本 plan 实现改动以仅含本任务文件的 scoped commit 落库（与先行修复 c0606ce66f 同纪律）

Follow-up:

- main 代码 `System.setProperty` 三处维持现状（Non-Blocking Follow-ups 已记录）
- Maven 4 wrapper 下多 `-pl` 累加语义依赖项：若未来 wrapper 降级 Maven 3 需改逗号分隔（审查轮 2 Info 项，记录在案）
