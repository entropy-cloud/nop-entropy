# 04 WI37 D11 emit 两档实施（update/close）

> Plan Status: completed
> Last Reviewed: 2026-10-08
> Source: `ai-dev/backlog/nop-stream-sql-enhancement-roadmap.md`（WI37 行、门控判据表 D11 行）；`ai-dev/design/nop-stream/window-failfast-decisions.md` §3（D11 放行裁定 2026-10-08——形态锁定/三项随裁/挂点）；`ai-dev/analysis/streaming-sql-survey/2026-10-07-kafka-streams-analysis.md`（EmitStrategy 两档形态，转引）
> Related: ai-dev/plans/nop-stream-sql-enhancement/01-wi0a-d10-d11-emit-decisions.md（裁定）；`ai-dev/plans/nop-stream-sql-enhancement/03-wi38-accumulation-mode-wiring-fix.md`（ACCUMULATING 接线前置——emit=update 依赖该通道）
> Owner: 仓库 owner（委托链：2026-10-08 执行指令「在 nop-entropy-stream-roadmap worktree 中执行 nop-stream-sql-enhancement-roadmap.md 直到彻底完成，每工作项按 plan guide 拟制计划，每完成一个计划提交一次」）

## Purpose

按 D11 裁定实施 emit 两档：`<sql>` 面（及 raw DSL strategy 面）新增封闭枚举声明 `emit="update|close"`——update 档逐记录发射窗口当前聚合值（ON_WINDOW_UPDATE 语义），close 档维持现行为（窗口末单次发射，ON_WINDOW_CLOSE 语义）。不引入自由 trigger 体系（triggerId fail-fast 保留）；`<sql>` 顶层属性挂点，编译产物落 windowingStrategy 声明。

## Current Baseline

- **D11 裁定**（window-failfast-decisions §3，2026-10-08）：放行 emit 两档（`<sql>` 面 emit 属性取值 `update|close`）；trigger 注册表前置作废；triggerId fail-fast 保留（双层门原样——WI38 重写后实测 strategy 级 :245-250、window 节点级 :271-276；D11 落档时行号已漂移）；挂点 = `<sql>` 顶层属性，编译产物落 windowingStrategy 声明（本 plan 在 stream.xdef strategy 增 emit 属性承载）。update 档=逐元素 FIRE + ACCUMULATING 不清空，close 档=现 fire-once 行为；判别性测试是 D11「部分值未经验证」反对的处置承诺。
- **机制现状**（2026-10-08 实测）：
  - trigger 机制齐备：`Trigger/TriggerResult`（FIRE/FIRE_AND_PURGE/CONTINUE/PURGE）、`EventTimeTrigger`（onElement 注册窗口末 timer、onEventTime 返回 FIRE 的范式，`nop-stream/nop-stream-core/.../windowing/triggers/EventTimeTrigger.java` :29-69）；`WindowedStream.trigger(...)` 接口点（:80）存在，`WindowedStreamImpl.trigger` 已覆写。
  - ACCUMULATING 通道已由 WI38 打通：模型 strategy accumulationMode 声明值经五环到达算子；`WindowOperator` DISCARDING fire 后清空（:1388-1400）、ACCUMULATING 不清空；A&R 双层拒绝。
  - emit 两档在算子层无新机制：update 档 = ACCUMULATING + 逐元素 FIRE trigger 的组合；close 档 = 现缺省（EventTimeTrigger fire-once + 声明档 accumulationMode）。
  - `<sql>` 面：`<sql sinkBean>` 元素（stream.xdef :101-112）→ `StreamModelDslBuilder.expandSqlModel`（:295-353）→ `ISqlStreamCompiler.compileSql(sql, schema, sinkBean)`（SPI，`nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/spi/ISqlStreamCompiler.java`）→ `StreamSqlCompiler.compile`（nop-stream-sql，:114-173）产物 XML 回读替换。SPI 现无 emit 参数。
  - 编译器窗口聚合路径：TUMBLE 查询生成 `<windowingStrategies><strategy strategyId windowFnId duration>`（`StreamSqlCompiler.java` :1050-1054 一带，strategy 节点经 `mk` XNode 构造）；连续聚合（无窗口）与 join 路径不生成 strategy。
  - xdef typed 模型类（`_StreamSqlModel`/`_WindowingStrategyModel`）由 nop-stream-flow 构建期 codegen 从 stream.xdef 再生成（不手工编辑 `_gen/`）；stream.xdef 唯一位于 `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef`。
  - 测试 fake：`TestSqlModelExpansion` 用 `FakeSqlStreamCompiler`（flow 测试）实现 SPI——SPI 加参须同步。

## Execution Finding F-1（2026-10-08，实施中实测）

R1-P1 的修复方向需修正：xdef 枚举属性解析走 **EnumStdDomainHandler.parseProp**（对枚举常量名按字典精确匹配，大小写敏感），**不经过** EnumTypeConverter（其 @StaticFactoryMethod 机制在 xdef parse 之后才参与 typed 绑定）。故 D11 表面 `update|close`（小写）在 xdef 层不可解析——表面格式修正为 **`UPDATE|CLOSE`**（与 accumulationMode="DISCARDING" 同一约定），WI0a 落档的小写表述系格式自选细节已按表面格式修正回改 D11 记录（window-failfast-decisions §3 新增「表面格式修正」行，两档语义/挂点/triggerId 保留等实质零变化）。StreamEmitMode.fromName 大小写不敏感工厂保留（typed 绑定层容错 + xdef 缺省字面量 =CLOSE 经同一链解析）。flow/sql 测试已按大写表面钉码（含小写被拒断言，声明面契约诚实）。

> **文本一致性注记（closure audit M-5）**：Goals 与 Phase 1 条目中残留的 `emit="update"` 小写字样系 R1 修订时的历史文本，表面以 Execution Finding F-1 与本注记为准——live 实现为 `UPDATE|CLOSE` 大写表面（xdef 注释/编译器写点/测试钉码三处一致）。

## Goals

1. **声明面**：stream.xdef 新增 `StreamEmitMode` 枚举承载——`<strategy emit>`（可选，缺省 CLOSE）与 `<sql emit>`（可选，缺省 CLOSE）两处属性；枚举类 `io.nop.stream.core.windowing.StreamEmitMode { UPDATE, CLOSE }` 落 nop-stream-core。**小写表面解析**（R1-P1）：D11 锁定声明面取值 `update|close`（小写），而 EnumTypeConverter 缺省回退 `Enum.valueOf` 大小写敏感——枚举必须按 FilterOp 先例（`io.nop/core/model/query/FilterOp.java`）提供 `@StaticFactoryMethod`（io.nop.api.core.annotations.core）标注的静态工厂 `fromName(String)`（小写 name map + 未知值 typed fail-fast），使 xdef 解析链接受小写；钉码断言 `<sql emit="update">` 可解析。
2. **DSL 消费**：`AdvancedTransforms.buildWindow` 消费 `strategy.getEmit()`——UPDATE 档装配 `EmitUpdateTrigger`（逐元素 FIRE + 窗口末 FIRE 的核心新 trigger）并**无条件强制** accumulationMode=ACCUMULATING（xdef 缺省回填后显式 DISCARDING 与缺省不可区分，故语义只能是 update 无条件覆盖 accumulationMode 声明——R1-P3；A&R 仍被门拒绝）；CLOSE 档行为与现状逐字节等价（缺省触发器 + 声明档 accumulationMode）。钉码：显式 DISCARDING+emit=update 构建通过且算子档 ACCUMULATING。
3. **SQL 面贯通**：`ISqlStreamCompiler.compileSql` 增 emit 参数；`StreamSqlModel.getEmit()` 经 expandSqlModel 传入；`StreamSqlCompiler` 在 TUMBLE 窗口聚合查询的产物 strategy 上写 `emit="update"`（UPDATE 时）。**emit 门按编译路径钉死**（R1-P2）：UPDATE 仅 `compileWindowedAggregate` 路径合法；`compilePlainPipeline`（**含 plain-TUMBLE 无聚合变体**——该路径不生成 strategy，emit 落上去即静默丢弃）/`compileContinuousAggregate`/`compileJoinQuery` 一律显式 fail-fast（无静默忽略）；plain-TUMBLE+emit 单独钉码。
4. **边界保持**：triggerId 双层 fail-fast 原样（emit≠triggerId——封闭枚举非自由 trigger）；**DSL 面 join 路径口径**（R2-N1）：`<joinSpec windowStrategyRef>` 引用的 strategy 声明 emit=update 时 buildJoin 显式 fail-fast（join 窗口不消费 emit，拒绝而非静默忽略）；**fromName 大小写域**（R2-N2）：xdef 缺省字面量 `=CLOSE` 经同一 EnumTypeConverter 链解析——fromName 必须同时接受全大写 UPDATE/CLOSE 与小写 update/close（大小写不敏感 name map），钉码含大写两例；D10 双档 accumulationMode 语义与判别测试不回退；emit=update 与 allowedLateness 组合语义 = 迟到更新继续重发（skipPurgeForLateness 与 ACCUMULATING 同向）；**update × maxDelay 组合裁定**（R1-P6）：无冲突——max-delay fallback 不调 user trigger 且 FIRE 不 purge，ACCUMULATING 下仅多一次等值发射（last-value-wins 幂等），不新增构建期拒绝，文档一句话记录。

## Non-Goals

- 不引入自由 trigger 声明面/trigger 注册表；不实现 ksqlDB EMIT FINAL 或 Esper OUTPUT EVERY 等其他 emit 家族。
- 不改 WindowOperator 运行时语义（:590-596 A&R 门、:1388-1400 清空分支均不动——update 档纯由 trigger+ACCUMULATING 组合表达）。
- 不做多档 emit（两档封闭）；join/连续聚合路径不提供 emit 语义。
- RDBMS 双目标不涉及 emit（流目标专属声明，toSQL 侧不渲染）。

## Scope

### In Scope

- `nop-kernel/nop-xdefs`：stream.xdef `<strategy emit>` + `<sql emit>` 属性（enum StreamEmitMode，缺省 CLOSE）。
- `nop-stream/nop-stream-core`：`StreamEmitMode` 枚举；`EmitUpdateTrigger`（windowing/triggers）。
- `nop-stream/nop-stream-flow`：`AdvancedTransforms.buildWindow` 消费 emit；`ISqlStreamCompiler` SPI 增参；`StreamModelDslBuilder.expandSqlModel` 传参（`_gen` typed 类经构建再生成，不手工编辑）。
- `nop-stream/nop-stream-sql`：`StreamSqlCompiler` emit 参数 + TUMBLE 产物 strategy 写 emit=update + 非窗口查询 emit fail-fast；`StreamSqlCompilerProvider` 适配。
- 测试：core（EmitUpdateTrigger 单测）、flow（emit 声明消费 + triggerId 保持）、sql（产物 shape + fail-fast + **端到端执行 emit=update 逐记录发射**）、`FakeSqlStreamCompiler` 适配。
- 文档：`docs-for-ai/03-modules/nop-stream-sql.md`（emit 声明与语义）；`ai-dev/design/nop-stream/window-failfast-decisions.md` §3 增「WI37 交付」注记；`ai-dev/design/nop-stream/sql-subset-and-semantics.md` §4b 窗口声明行补 emit。
- roadmap：WI37 状态行；当日 `ai-dev/logs/2026/10-08.md`。

### Out Of Scope

- WindowOperator/WindowOperatorBuilder 运行时变更；triggerId 声明面；CEP/连续聚合 emit 语义；multi-engine roadmap。

## Execution Plan

### Phase 1 - 声明面 + 消费 + 编译器贯通 + 判别测试

Status: completed
Targets: `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef`、`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/windowing/`、`nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/`、`nop-stream/nop-stream-sql/src/main/java/io/nop/stream/sql/compile/`

- Item Types: `Feature`

- [x] core：`StreamEmitMode` 枚举（UPDATE/CLOSE，javadoc 记 D11 形态锁定与 Kafka Streams EmitStrategy 映射）；`EmitUpdateTrigger<T, W extends Window> extends Trigger<T, W>`——onElement 返回 FIRE（并按 EventTimeTrigger 范式注册窗口末 event-time timer）、onEventTime 窗口末返回 FIRE（终值再发射，ACCUMULATING 下 last-value-wins 等值幂等）、onProcessingTime CONTINUE、clear 删除 timer、canMerge/onMerge 按 EventTimeTrigger 范式
- [x] xdef：stream.xdef strategy 与 sql 两元素增 `emit="!enum:io.nop.stream.core.windowing.StreamEmitMode=CLOSE"` 属性（含注释：D11 两档、update 隐含 ACCUMULATING）；构建验证 typed 模型再生成（flow 模块 compile）
- [x] flow：`ISqlStreamCompiler.compileSql` 增 `StreamEmitMode emit` 参数（javadoc 记 update 仅窗口聚合查询合法）；`expandSqlModel` 传 `sql.getEmit()`；`AdvancedTransforms.buildWindow`——`strategy.getEmit()==UPDATE` 时 `windowed.trigger(new EmitUpdateTrigger<>())` 并 `windowed.accumulationMode(ACCUMULATING)`（隐含强制，javadoc 声明优先级：emit=update 覆盖缺省 accumulationMode；A&R 已被上游门拒绝）；CLOSE 与缺省路径零行为变化
- [x] sql：`StreamSqlCompiler` 增 5 参 `compile(loc, sql, schema, sinkBean, emit)`——既有 4 参静态重载**保留**并委托 CLOSE（约 20 处既有测试直调 4 参零改动，R1-P5）；SPI 与 Provider/Fake 同步签名；TUMBLE 窗口聚合产物 strategy 在 UPDATE 时增 `emit="update"` 属性；emit=UPDATE 在 compilePlainPipeline（含 plain-TUMBLE 变体）/compileContinuousAggregate/compileJoinQuery 三路径显式 fail-fast（`CompileErrors.invalidArg`，文案说明仅窗口聚合查询支持）
- [x] flow 测试（`TestWindowEmitDeclaration`）：emit=update 构建通过且 `getTrigger()` 为 EmitUpdateTrigger、`getAccumulationMode()`==ACCUMULATING（无条件强制钉——含**显式 DISCARDING+update 构建通过且强制 ACCUMULATING** 一例，P3）；emit 缺省/CLOSE 与现状等价（trigger 缺省 EventTimeTrigger、accumulationMode 声明档保留）；`<window><triggerId>` 双层门仍 fail-fast（D11 边界，锚点 WI38 后实测 :245-250/:271-276）
- [x] sql 测试：产物 shape——`<sql emit="update">` TUMBLE 聚合查询产物 strategy 含 emit="update"（缺省产物不含该属性，既有 shape 断言不回退）；**plain-TUMBLE 无聚合查询 + emit=update fail-fast 钉码**（P2 专属）；连续聚合与 join 查询 + emit=update fail-fast 钉码；**端到端**（guide #22）：`TestSqlEmitUpdateE2E`——`<sql emit="update">` 窗口聚合查询经 builder→execute→sink 真实执行，断言逐记录发射（每条记录产生一次窗口更新发射，运行值递增，last-value-wins），对照 close 档单次发射
- [x] core 测试（`TestEmitUpdateTrigger` + `TestStreamEmitMode`）：onElement FIRE+timer 注册、onEventTime 窗口末 FIRE、非窗口末 timer CONTINUE（cleanup timer 经同一 triggerContext 到达——必需分支）、clear 删 timer、canMerge/onMerge 范式、Serializable；`fromName` 大小写不敏感（update/close 小写与混合）+ 未知值 typed fail-fast 钉码（对应 xdef 构建期拒绝的 Exit 断言，R1-P7）
- [x] 全量回归：`./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-flow,nop-stream/nop-stream-sql -am` BUILD SUCCESS（_tmp/wi37-full-regression.log；runtime 作为窗口算子回归面全绿）

Exit Criteria:

- [x] update 档端到端语义可观测：`<sql emit="update">` 产物经真实执行逐记录发射窗口更新值（E2E 断言发射序列），close 档与既有行为逐字节等价（既有 SQL E2E 全绿即证）
- [x] **接线验证**（guide #23）：`<sql emit>` → SPI → 产物 strategy → buildWindow → EmitUpdateTrigger+ACCUMULATING 链路连通——flow 层断言 WindowedStreamImpl 的 trigger 类型与 accumulationMode，sql 层断言产物 strategy 属性，两段拼接全覆盖
- [x] **无静默跳过**（guide #24）：emit=UPDATE 在 plain-TUMBLE/连续聚合/join 三路径显式 fail-fast（不静默忽略——含 plain-TUMBLE 变体专属钉码）；emit 非法值构建期拒绝（xdef enum 字典 + fromName 双层，钉码见 core 组）
- [x] 边界钉码：triggerId 双层 fail-fast 断言保留绿；D10 accumulationMode 判别测试不回退（回归全绿）；emit=update 隐含 ACCUMULATING 有钉码（显式 DISCARDING 被覆盖例）
- [x] 新功能测试覆盖（guide #25）：StreamEmitMode/EmitUpdateTrigger/DSL 消费/SPI 贯通/编译器写点五处均有对应测试（+29 例）
- [x] `./mvnw test -pl <四模块> -am` 全绿
- [x] owner doc 更新：docs-for-ai/nop-stream-sql.md、window-failfast-decisions §3 WI37 交付注记（含 F-1 表面格式修正）、sql-subset §4b emit 行
- [x] ai-dev/logs/2026/10-08.md 已更新

### Phase 2 - roadmap 同步与收口

Status: completed
Targets: `ai-dev/backlog/nop-stream-sql-enhancement-roadmap.md`

- Item Types: `Proof`

- [x] 独立子 agent closure audit（与起草审查不同 task）：核验 emit 链路贯通、两档语义判别、triggerId 边界保持、零越权；证据落 ai-dev/audits/nop-stream-sql-enhancement/wi37-closure-audit.md 与本 plan Closure 段
- [x] audit 通过后 roadmap WI37 状态行 `todo` → `done`（括注单层、内无 `）` 与 `)` 字符——**禁嵌套全角括号**）；重跑解析器核对 items 18 milestones 4
- [x] plan Closure 段写入证据，Plan Status → `completed`，check-plan-checklist --strict 退出码 0，check-doc-links --strict 退出码 0，`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0

Exit Criteria:

- [x] roadmap WI37 行同步，解析器实测 items 18 milestones 4
- [x] 独立 audit 证据已写入 plan Closure 段与 ai-dev/audits/nop-stream-sql-enhancement/wi37-closure-audit.md
- [x] check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0；scan-hollow high 退出码 0
- [x] ai-dev/logs/2026/10-08.md 收口记录与 plan 状态一致

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（无——本项为 Feature；FU-8 相关已由 plan 03 收口）
- [x] 行为/契约结果已达成：emit 两档按 D11 形态锁定交付，update 档逐记录发射经端到端判别，close 档零回归
- [x] 必要 focused verification 已完成（core/flow/sql 三层 + 端到端 E2E）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步到 live baseline（三处）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）`<sql emit>`→SPI→产物→buildWindow→trigger/accumulationMode 全链运行时连通（sql E2E 真实执行 + flow 消费断言），（b）无空方法体/静默跳过（非窗口查询 emit 显式拒绝）
- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-flow,nop-stream/nop-stream-sql -am` 全绿
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: emit 两档按 D11 形态锁定交付——`<sql emit>`/`<strategy emit>` 声明面（UPDATE|CLOSE 大写表面，F-1 表面格式修正合法落档）、EmitUpdateTrigger+ACCUMULATING 消费、编译器贯通含三路径显式拒绝门；判别性 E2E 真跑（emit=UPDATE 12 发射 vs close 档 4）；triggerId 边界与 WindowOperator 零改动；owner doc 三处同步；roadmap WI37 → done。独立 closure audit APPROVED（5 Minor 全处置）。
Completed: 2026-10-08

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，与起草审查 agent_18f92107/agent_d39b912a 均不同 task）：agent_e48657b7-01b3-4e3d-933c-a6204cd427ca
- Audit Session: 证据落档 ai-dev/audits/nop-stream-sql-enhancement/wi37-closure-audit.md
- Evidence:
  - Exit Criteria 逐条 PASS：声明面/消费链/三路径拒绝门逐环行号核对；判别测试 +29 例断言实质（E2E 真实执行 multiset 精确）；D11 边界（triggerId 双层门/无注册表/WindowOperator 零改动）；F-1 修正合法性与四处落档一致；owner doc 三处与 live 一致；回归 BUILD SUCCESS（实测 core 1755/1s、flow 198、sql 100、runtime 1336/10s——M-4 修正后数字）
  - Anti-Hollow PASS：全链由 E2E env.execute()→sink 真实执行证明；非窗口路径显式拒绝无静默丢弃；scan-hollow --severity high 退出码 0
  - audit 5 Minor 全处置：M-1 javadoc 更正、M-2 gate-inventory 归属注记（随本提交）、M-3 SPI 接缝断言补（TestSqlModelExpansion 新增用例）、M-4 实测数字、M-5 plan 一致性注记 + Serializable 断言
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql-enhancement/04-wi37-emit-two-tier.md --strict` 退出码 0（翻转后复跑确认）

Follow-up:

- no remaining plan-owned work
