# 351 全仓裸时间 API 统一 CoreMetrics

> Plan Status: active
> Last Reviewed: 2026-09-12
> Source: `ai-dev/audits/2026-09/2026-09-12-2130-nop-platform-conformance/`（02-repo-wide-scans §F-A/S03、04 ST-1、03 AI-18、05 MD-4、01 §3 H2、06 H2）
> Related: 350（契约快修）、352-357 系列。Plan 已过一轮独立对抗审查（agent_f5791667，1 Blocker + 6 Major 已吸收：复扫口径统一、真实计数修正、nanoTime 判定规则、stream Clock 决策、注释跳过、估计时钟警告、垫仓测试策略）。

## Purpose

把审计 P1-4（全仓裸时间 API）收口：src/main 中所有**时间线语义**的当前时间获取一律走 `CoreMetrics`（wall-clock 轴 TestClock 可注入）；**相对计时语义**（nanoTime 单调轴）按判定规则处理并给 `IClock.nanoTime` 补单调性契约注释。修复后 autotest 的过期/租约/超时/调度类断言恢复可控时钟。

## Current Baseline（对抗审查后真实口径）

- **规范复扫命令（全 plan 唯一定义，注释感知）**：
  `rg -n 'System\.currentTimeMillis\(\)|System\.nanoTime\(\)|LocalDateTime\.now\(\)|LocalDate\.now\(\)|new Date\(\)' <scope> --glob '**/src/main/java/**' --glob '!**/target/**' | grep -vE ':[0-9]+:\s*(//|\*|/\*)'`
  （`new Timestamp(` 不在复扫模式中——合法的 `new Timestamp(expr)` 约 118 处不可替换，只有其参数含 `System.currentTimeMillis` 的形态才随 currentTimeMillis 模式命中。）
- 真实可改计数（审查实测，按文件）：
  - nop-ai **53**（agent 42、gateway/core/tools 等；ai-core `DefaultAiChatExchangePersister` 为注释假阳性）；javadoc 提及需同步改写 3 处：`DefaultWaitCoordinator.java:38`、`UsageRecord.java:18`、`ProviderFailoverQueue.java:22`（描述"直接调 System.currentTimeMillis 是反模式"的对比句，正文修复后需改写）。
  - nop-stream **32**（runtime 18、core 12、connector-jdbc 1、fraud-example 1——example 排除则 31）；含自造 `util.clock.Clock`/`SystemClock`（见 Phase 2 Decision）。
  - nop-datav **16**（ExportTaskRecovery/ReportDeliveryRecovery 的写库时间戳为审计实证有害形态）。
  - nop-auth **7**（6 个 MFA/Redis/DB store + DaoUserContextCache；LoginServiceImpl 等 8 文件仅 `new Timestamp(expr)` 非当前时间形态，不在范围）。
  - nop-metadata **2**（MD-4：MetaContractChecker:67/122、NopMetaModuleBizModel:605）。
  - 零星业务：batch 1、code 1、integration 3、message 2、network 2、format 1、graph 2（**sys/retry/tcc/job 的命中经审查证实全部为估计时钟或已转换形态，真实可改为 0**，其中 job 仅 LocalJobScheduler 的 nanoTime deadline）。
  - 框架层：service-framework 8、persistence 5、core-framework 2、nop-kernel/nop-core 4；**api-core 5 文件全部为时钟自身实现或类型转换（CoreMetrics/IClock/DefaultSysCalendar/IEstimatedClock/ConvertHelper）——0 可改**；**xlang 2 文件为纯 javadoc 提及——0 可改**；commons 为依赖天花板豁免。
  - benchmark 4（vendored fastjson2 基准代码 perf 计时）+ demo 1（`DemoServiceBizModel.java:93`）——**审查发现的计划外命中，裁定见 Deferred**。
- CoreMetrics API（nop-api-core）：`currentTimeMillis()/currentTimestamp()/today()/currentDate()/currentDateTime()/nanoTime()`。
- `IClock.nanoTime` 是 default 方法直通 `System.nanoTime`；**TestClock 未覆写 nanoTime**——`CoreMetrics.nanoTime()` 今天与 `System.nanoTime()` 行为完全一致，替换对现有测试零影响。
- 估计时钟事实：`fireDao().getDbEstimatedClock().getMaxCurrentTimeMillis()`（JobFireStoreImpl:154/285）、`scheduleStore.getCurrentTime()`、`IEstimatedClock`——**DB 估计时钟源，替换为 CoreMetrics 是语义错误，一律不动**。
- 既有正确惯例：`new Timestamp(CoreMetrics.currentTimeMillis())` 已存在 23 处（TccRecordStore 等），证明映射惯例成立。
- checkstyle：无 ImportOrder 规则、UnusedImports 仅 warning——import 插入无门禁风险。

## Goals

- 替换映射（**仅代码行，跳过注释/javadoc 行**）：
  - `System.currentTimeMillis()` → `CoreMetrics.currentTimeMillis()`
  - `System.nanoTime()` → `CoreMetrics.nanoTime()`（**判定规则**：相对计时/性能测量/deadline 也替换——今天语义逐位等价（TestClock 不覆写）；同时在 `IClock.nanoTime` javadoc 补"实现必须单调、不得与时钟回退联动"契约，防止未来实现破坏。唯 `nop-stream SystemClock.relativeTime*` 见 Phase 2 Decision）
  - `LocalDateTime.now()` → `CoreMetrics.currentDateTime()`；`LocalDate.now()` → `CoreMetrics.currentDate()`
  - `new Date()`（仅空参形态）→ `new Date(CoreMetrics.currentTimeMillis())`
  - `new Timestamp(System.currentTimeMillis())` → `CoreMetrics.currentTimestamp()`；`new Timestamp(System.currentTimeMillis() ± x)` → `new Timestamp(CoreMetrics.currentTimeMillis() ± x)`
- **不替换**：估计时钟/DB 时钟源表达式；参数非当前时间的 `new Timestamp(expr)`；注释/javadoc（3 处 javadoc 描述句作为正文修复的伴随项改写）。
- 复扫归零（豁免/裁定清单外）。
- 测试策略：先 `./mvnw install -DskipTests -pl <全部受影响模块> -am` 垫仓一次，之后每 Phase 只 `./mvnw test -pl <模块>`（无 -am），最终阶段抽大模块回归。

## Non-Goals

- 不改 CoreMetrics/IClock 的方法签名（只补 javadoc 契约注释）。
- 不处理 src/test。
- 不顺带修复其他审计发现（352-357）。

## Scope

### In Scope

- 上列真实可改文件（约 130）+ IClock.nanoTime javadoc + 3 处 javadoc 伴随改写 + stream SystemClock 桥接。

### Out Of Scope

- commons（依赖天花板）、api-core 时钟族/xlang javadoc、benchmark vendored、demo、估计时钟形态、src/test。

## Execution Plan

### Phase 1 - nop-ai 模块组（53 文件）

Status: planned
Targets: `nop-ai/**/src/main/java`（agent 42、gateway 5、core 5、tools 1 等）

- Item Types: `Fix`

- [ ] 逐文件替换（跳过注释行）；重点核对 `DbUsageRecorder.java:131`（写库）、`AgentMessageEnvelope.java:30`
- [ ] 3 处 javadoc 伴随改写（DefaultWaitCoordinator:38、UsageRecord:18、ProviderFailoverQueue:22——正文修复后对比句改为描述已修复状态或删句）
- [ ] 补 import（io.nop 组）
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent,nop-ai/nop-ai-core,nop-ai/nop-ai-gateway,nop-ai/nop-ai-tools`（垫仓后无 -am；agent 过重可 -Dtest 聚焦后补全量）

Exit Criteria:

- [ ] 规范复扫命令对 nop-ai 输出为空（假阳性 DefaultAiChatExchangePersister 注释行被注释过滤规则排除）
- [ ] 模块测试通过并记录
- [ ] No new test required: 时间源替换行为等价（TestClock 未注入时委托系统时钟；nanoTest 轴 TestClock 未覆写）
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - nop-stream 模块组（31 文件，ST-1）+ 自造 Clock Decision

Status: planned
Targets: `nop-stream/**/src/main/java`（runtime 18、core 12、connector-jdbc 1；fraud-example 排除）

- Item Types: `Fix | Decision`

- [ ] 逐文件替换；重点 `JdbcClusterRegistry:61/108`（租约/心跳）、`InputGate:510`、`StreamTaskInvokable:903` 等算子计时（按 nanoTime 判定规则替换）
- [ ] **Decision—SystemClock 桥接**：`absoluteTimeMillis()` 改委托 `CoreMetrics.currentTimeMillis()`（wall-clock 轴 TestClock 可注入）；`relativeTimeMillis()/relativeTimeNanos()` 保留 `System.nanoTime()` 并在 javadoc 引用 IClock 单调契约（相对计时轴）；自造 `util.clock.Clock` 接口的删除登记 Deferred（Flink 移植算子签名兼容原因）
- [ ] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime`

Exit Criteria:

- [ ] 规范复扫对 nop-stream main（除 fraud-example）输出为空，SystemClock.relativeTime* 两行以 Deferred 裁定引用豁免
- [ ] 模块测试通过并记录
- [ ] No owner-doc update required（`03-modules/nop-stream.md` 不记载时间 API；Clock 接口去留裁定记录在本 plan）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - nop-datav-service（16 文件）

Status: planned
Targets: `nop-datav/nop-datav-service/src/main/java`

- Item Types: `Fix`

- [ ] 重点：`NopDatavExportTaskRecovery:107/121/143`、`NopDatavReportDeliveryRecovery:103/118/142` 的 `new Timestamp(System.currentTimeMillis()...)` 全部走 CoreMetrics
- [ ] `./mvnw test -pl nop-datav/nop-datav-service`

Exit Criteria:

- [ ] 规范复扫对 nop-datav 输出为空；两个 Recovery 类有害形态消除
- [ ] 模块测试通过并记录
- [ ] No new test required: 行为等价替换
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - nop-auth MFA/会话缓存族（7 文件，H2）

Status: planned
Targets: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/mfa/store/*`（6 文件）+ `login/DaoUserContextCache.java`

- Item Types: `Fix`

- [ ] MFA 挑战码/验证码过期判定、会话缓存时间全部走 CoreMetrics（TestClock 敏感路径）
- [ ] `./mvnw test -pl nop-auth/nop-auth-service`

Exit Criteria:

- [ ] 规范复扫对上述范围输出为空（LoginServiceImpl 等 `new Timestamp(expr)` 形态不在模式内自然排除）
- [ ] 模块测试通过（MFA 相关无 flaky）并记录
- [ ] No new test required: 行为等价替换
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - 零星业务 + nop-metadata（~12 文件，MD-4）

Status: planned
Targets: batch 1、code 1、integration 3、message 2、network 2、format 1、graph 2、metadata 2（MetaContractChecker、NopMetaModuleBizModel）

- Item Types: `Fix | Decision`

- [ ] 逐文件替换；metadata 两文件为 MD-4 审计点
- [ ] 现场核实 job 的 LocalJobScheduler nanoTime（审查判定为唯一真实点）：按判定规则替换并引用 IClock 契约
- [ ] 各模块 `./mvnw test -pl <module>`（无测试基建的模块 compile 即可并注明）

Exit Criteria:

- [ ] 规范复扫对上述模块输出为空
- [ ] 模块测试/编译通过并记录
- [ ] No new test required: 行为等价替换
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 6 - 框架层（~19 文件）+ IClock 契约注释

Status: planned
Targets: `nop-service-framework` 8、`nop-persistence` 5、`nop-core-framework` 2、`nop-kernel/nop-core` 4、`nop-kernel/nop-api-core/.../IClock.java`（javadoc）

- Item Types: `Fix | Decision`

- [ ] 逐文件替换（替换前逐处确认非时钟自身实现——api-core 5 文件已裁定 0 可改，勿动）
- [ ] `IClock.nanoTime()` javadoc 补单调性契约："实现必须单调递增（与 System.nanoTime 语义一致），不得与 currentTimeMillis 时钟回退联动"
- [ ] `./mvnw test -pl` 受影响框架模块（有测试基建的）

Exit Criteria:

- [ ] 规范复扫对全仓 src/main（除 Deferred 豁免清单）输出为空
- [ ] IClock javadoc 契约落地
- [ ] 模块测试通过并记录
- [ ] No owner-doc update required（common-java-helpers.md 规则已存在，本 plan 是执行）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 7 - 全量回归与收口

Status: planned
Targets: 全仓

- Item Types: `Proof`

- [ ] 规范复扫命令全仓输出 + 豁免清单对照，存日志归零证据
- [ ] 大模块回归抽样：`./mvnw test -pl nop-ai/nop-ai-agent,nop-stream/nop-stream-runtime,nop-auth/nop-auth-service,nop-datav/nop-datav-service` 全绿
- [ ] 独立 closure audit（fresh subagent）

Exit Criteria:

- [ ] 归零证据 + 豁免对照无未解释残留
- [ ] 回归抽样通过并记录
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/351-repo-wide-core-metrics.md --strict` 退出码 0
- [ ] 独立 closure audit 证据写入 Closure 段

## Closure Gates

- [ ] 审计 P1-4/F-A/ST-1/AI-18/MD-4/H2 的全部真实可替换点已修复（规范复扫归零）
- [ ] nanoTime 判定规则与 IClock 契约落地；stream SystemClock 桥接完成
- [ ] 豁免/裁定清单完整且理由有据（commons/时钟族/xlang-javadoc/benchmark/demo/估计时钟/fraud-example/Clock 接口）
- [ ] 受影响模块测试通过
- [ ] 无 in-scope live defect 降级
- [ ] 独立 closure audit 完成且证据已写入

## Deferred But Adjudicated

### nop-commons（依赖天花板）

- Classification: `watch-only residual`
- Why Not Blocking Closure: CoreMetrics 在 nop-api-core，api-core 依赖 commons，反向不可达；commons 内裸时间（StandardThreadPoolExecutor、NopThread、DateHelper 等）为框架底层既定形态。
- Successor Required: no

### 时钟自身实现（CoreMetrics/IClock/DefaultSysCalendar/IEstimatedClock/TestClock）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 时钟本体；ConvertHelper 的命中为纯类型转换（非取当前时间），属模式假阳性。
- Successor Required: no

### nop-xlang 2 文件（JsDate/LexicalScopeAnalysis javadoc）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 命中全部为 javadoc 中 XScript 类型别名说明文字，无代码位点；注释感知复扫已排除。
- Successor Required: no

### Quartz 移植 2 文件（BaseCalendar/ICronExpression）

- Classification: `watch-only residual`
- Why Not Blocking Closure: Terracotta 版权移植代码，保持与上游可 diff（审计 01 §3 已注明豁免区）。
- Successor Required: no

### benchmark 4 文件 + demo 1 文件

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: benchmark 为 vendored fastjson2 基准代码（perf 计时，非平台产物）；demo 为示例模块（`DemoServiceBizModel`），非生产路径。
- Successor Required: no

### nop-stream 自造 `util.clock.Clock` 接口删除

- Classification: `optimization candidate`
- Why Not Blocking Closure: SystemClock.absoluteTime 已桥接 CoreMetrics（时间线语义统一达成）；接口本身镜像 Flink API 面，被移植算子（WatermarksWithIdleness 等）签名引用，删除需重写移植算子签名，风险大于现值。
- Successor Required: no

## Non-Blocking Follow-ups

- 无

## Closure

Status Note: <<待填>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<待填>>
- Evidence: <<待填>>

Follow-up:

- <<待填>>
