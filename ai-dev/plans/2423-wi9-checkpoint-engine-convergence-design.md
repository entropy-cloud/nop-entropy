# 2423 WI9 双 checkpoint 驱动面重叠测绘与收敛设计裁定

> Plan Status: active
> Last Reviewed: 2026-10-11
> Source: ai-dev/backlog/nop-stream-structure-refactor-roadmap.md Phase D WI9；E3 分叉项以分布式路径为权威的默认假设
> Related: plan 2420（WI8 已收）；WI10/WI11 为本裁定的实施承接

## Purpose

产出 `ai-dev/design/nop-stream/checkpoint-engine-convergence.md`：双 checkpoint 驱动面（分布式 JobCoordinator→CheckpointCoordinator 与本地/savepoint GMCE→内嵌 CheckpointCoordinator）在七个维度上的逐条差异测绘（完全一致 / 语义等价实现不同 / 真实语义分叉三档标注）、两条加载链的显式记录、收敛方案裁定（含权威路径、分叉裁决、双跑校验方案）与反向污染风险声明。**本 WI 不改任何生产代码**；owner 确认门见 Current Baseline 末条——WI10/WI11 以 owner 对该设计文档的确认为 gate 承接实施。

## Current Baseline

2026-10-11 实测（commit 2b78f8b7f4 工作树）：

- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java` **1890 行**；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java`（GMCE）**1872 行**；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java` **2886 行**。
- 分布式路径：JobCoordinator 直驱 CheckpointCoordinator，不经 SPI。本地/savepoint 路径：core `StreamExecutionEnvironment` 经 `ServiceLoader` 加载 `META-INF/services/io.nop.stream.core.execution.ICheckpointExecutorFactory`（接口在 `core/execution/`），`runtime/execution/CheckpointExecutorFactoryImpl` 提供 `GraphModelCheckpointExecutor`；GMCE `:668` 内嵌 `new CheckpointCoordinator(...)`，trigger/restore/manifest 核心 ≥11 处真实委托。
- 路线图 v1.1 复审已勘误「两套独立引擎」表述：重叠在**驱动面**（trigger 时机、restore 后状态重建、savepoint 触发、终止模式等外围语义），非内核。
- 既有裁定边界：R5 的 CC-06（coordinator monitor 内阻塞 RPC fan-out）与 CC-17（无界队列 + 终态 RPC 同步发送）归 WI10 落地时顺带修正，不在本 WI 立项。
- 本计划编号 2423 为当前最大+1（2420 本会话 WI8 已收、2421/2422 并发会话占用）。
- `ai-dev/design/nop-stream/` 已有 `checkpoint-design.md`（§8.4.1 状态迁移注册载体等既有契约）与 `core-design.md`（§2.2.1 组件获取）；新文档须与其一致并互链，不得改写其已裁定内容。
- owner 确认门：AGENTS.md 要求框架核心引擎变更需 plan-first + 设计文档，roadmap 134 行要求「本 WI 产出设计文档后需 owner 确认方可进入 WI10」。本会话用户指令为「执行 nop-stream-structure-refactor-roadmap.md直到彻底完成。每个工作项按照plan guide拟制计划执行。每个计划执行完毕自动提交一次。」（本会话 /goal 原文；指令本身不留 repo 内文本，故在本 plan 与 daily log 双处留痕）。据此本 WI 交付设计文档；**WI10 是否进入以 owner 对该文档的确认为 gate**——若 owner 未否决且继续下达执行指令，视为确认成立；本 plan 不预设 WI10/11 已获授权。

## Goals

- 设计文档落盘 `ai-dev/design/nop-stream/checkpoint-engine-convergence.md`，包含：
  1. **七维逐项对照表**：trigger 语义 / barrier 传播 / epoch manifest 格式 / savepoint 触发条件 / commit-abort 协议 / restore 路径 / 终止模式（Drain/Suspend/Export），每条标注〔完全一致 | 语义等价实现不同 | 真实语义分叉〕并附两侧行号锚；
  2. **两条加载链**：services SPI 加载方（core StreamExecutionEnvironment 经 ServiceLoader）与分布式直驱链（JobCoordinator 不经 SPI）的显式记录；
  3. **收敛裁定**：回答路线图三问 + 路线裁决——（i）哪条路径语义权威（E3 默认分布式权威，除非证据反转）；（ii）分叉项以谁为准（逐条裁决）；（iii）迁移期双跑校验怎么做（WI10/WI11 的差分门设计）；（iv）**路线 (a) 下沉共享内核 vs (b) 统一单一驱动面 的显式裁决**（WI10 首句依赖此裁定），裁决依据 = 测绘产出的散落语义清单〔(a) 的下沉范围〕与驱动面合并面/双跑成本〔(b) 的迁移成本〕比较；
  4. **反向污染风险声明**：单侧回归不可见的双向污染风险 + WI10/WI11 不得以单侧回归为完成标准的硬约束；
  5. CC-06/CC-17 边界注记（顺带修正项归属，不重复立项）。
- structure roadmap WI9 状态翻转 + daily log 条目。

## Non-Goals

- 不改任何生产代码、测试代码、pom（设计裁定 WI）；不动 CheckpointCoordinator/GMCE/JobCoordinator 任何一行。
- 不重复立项 CC-06/CC-17（R5 已登记，WI10 顺带）。
- 不裁决 checkpoint 存储格式 / 增量 checkpoint / reshard 细节（既有 `checkpoint-design.md` 契约不变）。
- 不产出 WI10/WI11 的执行计划（其自行拟制）。

## Scope

### In Scope

- 新增 `ai-dev/design/nop-stream/checkpoint-engine-convergence.md`（唯一交付物）。
- `ai-dev/design/nop-stream/README.md` 索引行（如该目录 README 有文档清单则补一行）。
- structure roadmap WI9 状态行翻转（解析器合规格式）；`ai-dev/logs/2026/10-11.md` 条目。
- 测绘证据：仅引用 live 源码行号锚，不复制大段代码。

### Out Of Scope

- docs-for-ai 使用文档（WI10/WI11 实施后由其同步；STRM-024 锚点描述本 WI 不动）。
- gate-inventory / invariant-catalog（无代码变更，不触及表完备性门禁）。

## Execution Plan

### Phase 1 - 双驱动面测绘（七维对照表取证）

Status: completed
Targets: CheckpointCoordinator/GMCE/JobCoordinator 只读测绘；证据汇总

- Item Types: `Proof`

- [x] 独立测绘子代理逐维取证（agent_092f67c5）：七维 19 条对照行（4 完全一致 / 5 语义等价 / 10 真实分叉，相邻分叉——分布式状态后端固定 MemoryStateBackend——含于分叉口径）+ 双链调用点全录 + 分叉条目证据强度评估；背景事实行号全部复核无误
- [x] 两路线比较素材成文：散落语义清单 = savepoint 物化（GMCE :362-395/:413-464 vs JC :2374-2377）+ restore 对齐缺口（6a）+ 终态预算显式化（1c）；合并面评估 = JC 2886 + GMCE 1872 合并、10 分叉须一次性预裁、双跑参照面消失（详见设计文档 §四）
- [x] 本会话回读复核：分叉档 10 条锚点全部逐一回读命中（1b :908-927、1c :100 vs CheckpointConfig :28、2c deploy/RemoteTaskDeploySupport :206-212 自认缺口、4a :460、4c grep=0、5b 双侧、5c :2505、6a :2029、7 双侧）；等价档抽验 1a（AtFixedRate :275 vs WithFixedDelay :1272）+ 6a 任务侧；完全一致档抽验 1d（CheckpointConfig :28/:52/:53）+ 3（:1658）+ 4b（:511 vs :2365/:2370）

Exit Criteria:

- [x] 七维逐条对照条目穷尽（19 条对照行 = 4+5+10，各维声明穷尽检索），全部带双侧行号锚；档位标注齐备
- [x] 加载链两链调用点行号齐备；分叉档全量、等价档与完全一致档各 ≥2 条经二次回读确认（不接受单次取证即定档）
- [x] 「散落语义清单 + 合并面评估」两段成文（入设计文档 §四）

### Phase 2 - 设计文档落盘

Status: completed
Targets: `ai-dev/design/nop-stream/checkpoint-engine-convergence.md`

- Item Types: `Decision`

- [x] 按设计写作指南落盘：设计结论 / 背景与动机〔v1.1 勘误事实：GMCE 内嵌 coordinator，重叠在驱动面〕/ 核心设计〔七维对照表 + 两条加载链 + 收敛裁定（三问 + 路线 (a)/(b) 裁决）+ 双跑校验门〕/ 拒绝了什么〔落选路线及理由〕/ 与已有设计的关系〔checkpoint-design.md、core-design.md、distributed-runbook.md 互链，小节锚按实际标题核对〕
- [x] 定位与状态声明：文档头部注明「七维对照表为两现存驱动面的事实测绘（裁定证据基础），非 Proposed-vs-Current 演进对比」；状态字段取值：owner 确认前为 `草案（待 owner 确认）`，确认后转 `active`；行号锚注明基线 commit 2b78f8b7f4（2026-10-11），锚以『类#方法』语义标识为主、行号为辅
- [x] 反向污染风险声明成节（含「WI10/WI11 不得以单侧回归通过为完成标准」硬约束）；CC-06/CC-17 边界注记
- [x] `ai-dev/design/nop-stream/README.md` 文件索引与阅读顺序表补本篇条目 + 顶部 Updated: 变更记录行追加一行（既有惯例）
- [x] 本 Phase 不改 owner docs：docs-for-ai 同步归 WI10/WI11 实施后（显式递延声明，非跳过）

Exit Criteria:

- [x] 路线图 WI9 要求的五项产出齐备（对照表 / 加载链 / 裁定〔三问 + 路线 (a)/(b) 裁决〕/ 风险声明 / CC 边界注记）
- [x] 文档内引用的行号锚经抽检命中（Phase 1 复核已回读 ≥15 处锚点全部命中）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 本计划触及文件 0 断链（剩余 2 warnings 属并发 nop-ai 会话 2424 plan，已归因）
- [x] 设计文档不引用 ai-dev/analysis 与 ai-dev/discussions（grep=0）

### Phase 3 - 独立审查 + roadmap 翻转 + 收口

Status: completed
Targets: structure roadmap、daily log

- Item Types: `Proof`

- [x] 独立子代理审查设计文档（agent_4422f039，审查基线 0e84aa52e7）：五维核对——路线图五项要求全 PASS、行号锚约 30 处逐一抽验全命中、设计指南合规 PASS、DIV-01～06 双向一致 PASS；发现 1 Major（checkpoint-design.md §8.7/§7 设计意图证据未被消费且 §8.7 distributed 段与代码偏差漏登记）+ 4 Minor——全部修复（§8.7/§7.2/§7.3 补偏差注记、DIV-02/05 裁决重写、统计 4/5/10 三处联动、DIV 枚举与切分修正、68 行勘误），条件闭合确认中
- [x] 审查发现问题修至通过（无 Blocker；Major 修复已落地待审查员条件闭合确认）
- [x] structure roadmap WI9 `todo`→`done`（注记半角括号曾致解析器 12→11 丢行，修复后复跑 12 条目 9 done）
- [x] `ai-dev/logs/2026/10-11.md` WI9 条目（含统计勘误与质量门记录）
- [x] pathspec 提交（仅 design 新文件 + README 索引 + checkpoint-design 偏差注记 + r5-successors 登记节 + roadmap + log + 本 plan；审计 E4 允许清单精确吻合）

Exit Criteria:

- [x] 审查无 Blocker，档位判定抽查通过（审查员条件闭合确认「文档可作为 WI10/WI11 执行输入」）
- [x] roadmap-check 12 条目、WI9 done
- [x] daily log 条目已写
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2423-wi9-checkpoint-engine-convergence-design.md --strict` exit 0

## Closure Gates

- [x] 设计文档五项路线图要求产出齐备且独立审查通过（内容审查 1 Major + 4 Minor 修复至条件闭合；closure audit 12/12 PASS）
- [x] 生产代码零变更（closure audit E4：git log 近 5 提交均非 2423、git status 无 nop-stream 路径）
- [x] roadmap WI9 done + M3 未达成状态如实（M3 需 WI10+WI11；roadmap-check 12 条目 9 done）
- [x] daily log 条目已写（含质量门与统计勘误记录）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 本计划触及文件 0 新增断链（closure audit E9：2 errors + 1 warning 全部归因并发 nop-ai 会话）
- [x] 独立 closure audit 完成且证据写入本 plan Closure 段（fresh session 子代理 agent_03c68efc，与内容审查 agent_4422f039 分工；12 项全 PASS + 附加发现「22 条」残留口径已修）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2423-wi9-checkpoint-engine-convergence-design.md --strict` exit 0（关闭前终跑）
- [x] 路径限定提交（git commit -- 指定路径，不卷入共享工作树其他会话改动）

## Deferred But Adjudicated

（无。）

## Non-Blocking Follow-ups

- 无剩余 plan-owned work；WI10/WI11 按本裁定实施，其 owner-doc 同步与 STRM-024 描述更新由其自身计划承载。

## Closure

Status Note: WI9 四项 Phase 全部完成：七维测绘（19 条对照行 = 4 一致/5 等价/10 分叉，三档对称回读复核）+ 双加载链 + 收敛裁定（路线 a 下沉共享内核、E3 口径逐条裁决、DIV-01～06 登记 r5-successors）+ 双跑差分门五条硬约束成文；checkpoint-design.md 三处偏差注记（§7.2/§7.3/§8.7，原意图文字保留为裁决输入）；生产代码零变更；roadmap WI9 done（12 条目 9 done）。设计文档状态=草案（待 owner 确认），WI10 以 owner 确认为 gate。
Completed: 2026-10-11

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理（general-purpose，fresh session，agent_03c68efc-5bca-4427-aeea-1b859555515c）
- Audit Session: agent_03c68efc-5bca-4427-aeea-1b859555515c（内容质量门为 agent_4422f039-2287-4af9-9d3d-6131a45c9b26，两轮分工）
- Evidence:
  - A 组（交付物五项要求）A1-A3 全 PASS：七维表 4/5/10 口径 + 双锚、加载链、裁定、§4.4.4 双硬约束、CC 边界注记；状态/锚基线/定位三声明在位；analysis/discussions 引用 grep=0。
  - B 组（零代码变更）B4 PASS：git log 近 5 提交均非 2423；git status 无 nop-stream 路径；触碰文件与允许清单精确吻合。
  - C 组（双向登记与偏差注记）C5-C7 全 PASS：DIV-01～06 逐条对应双向互指；checkpoint-design 三处注记在位且原意图保留；roadmap-check exit 0、解析 12 条目 9 done、M3 如实未达成。
  - D 组（文档卫生）D8-D10 全 PASS：README Updated/5c 条目/统计口径；本计划触及文件 0 断链（其余归因并发会话）；daily log 含质量门记录。
  - E 组（计划一致性）E11-E12 全 PASS：Phase 状态与勾选一致（关闭前未勾项属预期）；8 处行号锚抽验全命中（超 5 处要求）。
  - 附加发现已修：「22 条」残留口径（plan Phase 1 两处 + daily log）改为 19 条对照行 = 4+5+10。
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2423-wi9-checkpoint-engine-convergence-design.md --strict` 关闭前终跑 exit 0。

Follow-up:

- no remaining plan-owned work。WI10/WI11 按设计档 §4.1/§4.3/§4.4 实施，进入以 owner 对设计档的确认为 gate；DIV-01～06 处置归 r5-successors 台账纪律。
