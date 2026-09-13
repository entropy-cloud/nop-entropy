# pi-D10 多代理与子代理对比

> Status: resolved
> Date: 2026-09-12
> 基线: nop=800baf32da（nop-ai 模块与 c585459f83 diff 为空）、pi=c49906ec7（分析当日实测）
> 引用: 00-dimension-matrix.md（WI2，D10 子机制 D10-1..D10-4）、02-terminology-map.md（T17）; 机制事实引用 03 §2.3、04（call-agent 面）
> Owner: `ai-dev/design/nop-ai-agent/nop-ai-agent-multi-agent.md`

## ① 结论摘要

- 总裁定：**nop 领先**。pi 核心无内置多代理（roadmap 初步假设当日复核**成立**且为三方中唯一"零内置"）；官方形态仅为 example extension（每子代理 spawn 独立 pi 进程），无深度预算、无编排原语、无跨实例协调。
- 关键差异 1（D10-3）：nop 团队编排全栈（fan-out/AllMustSucceed 归约/DAG 计划状态机/调度）vs pi example 的 Single/Parallel(≤8)/Chain 三模式脚本化编排。
- 关键差异 2（D10-4）：pi 的进程级隔离是三方最彻底（独立进程/独立 context/结构化输出捕获）——隔离强度换编排能力；nop Actor+接管锁是"隔离与协调并重"。
- 设计取舍辨析：pi 的"零内置"是克制（多代理策略高度多变，框架不押注）——但对比的是能力存在性而非哲学优劣，裁定不打折。
- 可吸收增量建议一句话：反向记录为主——pi 的进程级隔离与结构化输出协议可作为 nop out-of-process 派生的参照（见 ⑥）。

## ② nop 侧机制与锚点

nop 事实基线与 dsh-D10 报告 ② 节共享：CallAgentExecutor/send-message 工具化派生+team 声明（`tool/CallAgentExecutor.java`、`team/ITeamManager.java`）；父约束钳制（`AgentToolDispatcher.java:114-155`）；TeamTaskFlowOrchestrator+FanOut+AllMustSucceedReduction+PlanExecutor DAG（`team.flow/`、`plan/runtime/`）；Actor+接管锁+守护协调+DB 消息桥（`runtime/`、`message/DBMessageService.java`）。

## ③ pi 侧机制与锚点

按 02 T17 与 01 代码地图，对比增量：

- **核心无内置**（D10-1）：`packages/agent`、`packages/coding-agent/src/core` 无任何 subagent/team 编排类型（grep 仅命中 examples）；官方形态=`packages/coding-agent/examples/extensions/subagent/index.ts`——每个 subagent **spawn 独立 pi 进程**（JSON/print 模式捕获结构化输出，tmpdir 会话，:240）；Single/Parallel（MAX_PARALLEL_TASKS=8）/Chain 三模式；agent 定义由 `./agents.ts` 的 `discoverAgents` 文件发现（header :1-14）。
- **深度预算**（D10-2）：无（example 无层级限制字段；进程隔离天然限制资源放大但不设深度计数）。
- **编排**（D10-3）：example 内脚本化三模式（顺序/并行上限 8/链式）；无 fan-out 归约原语、无 DAG、无团队声明、无调度守护。
- **隔离边界**（D10-4）：进程级隔离（独立 context/会话/进程）——比 dsh 的 in-process provider 更彻底；跨进程无协调（无锁/守护/可靠消息）；编排逻辑全在扩展层（同目录 handoff.ts 是会话交接另一形态）。

## ④ 子机制逐项对照表

| 子机制 | nop 机制 | pi 机制 | 裁定 | 证据 |
|---|---|---|---|---|
| D10-1 派生模型 | 内置 call-agent/send-message 工具+team 成员声明（引擎绑定） | 零内置；example extension spawn 独立进程+结构化输出捕获+discoverAgents 文件发现 | nop 领先 | nop `tool/CallAgentExecutor.java`；pi `examples/extensions/subagent/index.ts:1-14,240`——内置派生 vs 示例脚本：能力存在性与保障度不同级 |
| D10-2 深度预算 | delegationDepth 父约束钳制（effective tools/paths 逐层收紧） | 无 | nop 领先 | nop `AgentToolDispatcher.java:114-155`；pi 无对位——递归派生的资源放大 pi 无防护（进程隔离部分缓解但不设上限） |
| D10-3 团队与编排 | TeamTaskFlowOrchestrator+FanOut+AllMustSucceedReduction+PlanExecutor DAG+五工具+scheduler | example 三模式（Single/Parallel≤8/Chain）脚本编排；无归约/DAG/团队声明 | nop 领先 | nop `team.flow/`、`plan/runtime/PlanExecutor.java`；pi example 三模式——编排原语 nop 全栈 vs pi 脚本 |
| D10-4 与主循环的隔离边界 | 进程内 Actor（opt-in）+跨进程接管锁+守护协调+可靠消息桥 | 进程级隔离（独立进程/context/会话）——隔离最彻底但零跨进程协调 | 等价 | nop `runtime/`+`message/DBMessageService.java`；pi example 进程隔离——隔离强度 pi 胜、协调能力 nop 胜，维度正交 |

## ⑤ 语义差异与取舍

- **"无内置"的三方谱系**（合 dsh-D10 ⑤）：dsh 内置子代理运行时（6 provider 谱系+continuation）、nop 内置编排全栈（team/plan）、pi 零内置（example 示范）。pi 的克制与其"核心最小+生态扩展"哲学一致（D2 的单槽/多播分层同旨）——但对比维度是能力存在性，pi 在四个子机制上均为最低配置。
- **隔离即编排的替代**：pi 的进程隔离使子代理崩溃/超时不影响宿主（最强故障隔离），代价是编排（聚合/重试/归约）全部要跨进程协议化（JSON 输出捕获）——nop 的进程内 Actor 恰相反。这是部署形态（端侧单用户 vs 服务端多实例）的合理分化。
- **结构化输出协议**：pi example 用 JSON/print 模式捕获子代理输出——与 dsh reportFrom（结构化子→父通道）、nop send-message（消息桥）三方三种子→父结果回传形态；pi 最弱（文本协议）但最通用。
- **不可比项**：pi 的 handoff.ts（会话交接）是会话所有权转移形态，与子代理派生正交——登记不硬比。

## ⑥ 裁定 + 可吸收增量建议

**维度总裁定：nop 领先**——D10-1/2/3 三项 nop 领先（内置派生/深度预算/编排全栈），D10-4 等价（正交）；pi 是三方中多代理能力最弱的一方（零内置），但进程隔离设计有其部署形态合理性。

可吸收增量建议（仅记录，不实施）：

1. 【来源 pi；针对 nop out-of-process 派生】若 nop 未来支持进程外子代理（当前 Actor 进程内），pi 的"JSON/print 模式结构化输出捕获+独立会话 tmpdir"是最小可行协议参照。
2. 【来源 nop；反向记录供 pi 生态】pi 若产品化多代理，nop 的 team.flow（fan-out+AllMustSucceed 归约）与父约束钳制（delegationDepth）是两个可直接移植的原语（比 dsh 的 continuation 更贴近编排需求）。
3. 【来源 pi；针对 nop 隔离选项】Actor 容器可增加"进程隔离档位"（当前 opt-in 进程内）——不可信子任务进程级隔离（nop DockerSandboxBackend 已有进程禁闭基础，概念可延伸到子代理托管）。

## References

- `ai-dev/analysis/compare-agent-design/00-dimension-matrix.md`（D10 定义）、`02-terminology-map.md`（T17）、`dsh-D10-multi-agent.md`（nop 基线共享）
- `ai-dev/design/nop-ai-agent/nop-ai-agent-multi-agent.md`（Owner doc）
- nop：`nop-ai/nop-ai-agent/.../team/`、`team.flow/`、`runtime/`、`tool/CallAgentExecutor.java`
- pi：`packages/coding-agent/examples/extensions/subagent/index.ts`（`~/ai/pi` @ c49906ec7）
