# dsh-D10 多代理与子代理对比

> Status: resolved
> Date: 2026-09-12
> 基线: nop=800baf32da（nop-ai 模块与 c585459f83 diff 为空）、dsh=c291e7961a（分析当日实测）
> 引用: 00-dimension-matrix.md（WI2，D10 子机制 D10-1..D10-4）、02-terminology-map.md（T17）; 机制事实引用 03（P1-P3/P12）、04（CallAgentExecutor/team 工具面）
> Owner: `ai-dev/design/nop-ai-agent/nop-ai-agent-multi-agent.md`

## ① 结论摘要

- 总裁定：**等价（正交强项）**。nop 拥有团队编排全栈（team 声明/任务流 fan-out/归约/计划状态机 DAG/调度守护）为 dsh 全无；dsh 拥有子代理传输谱系（6 provider 覆盖进程内外/外部协议/SDK）与 continuation 管理为 nop 全无——两者做多代理的"纵向深度"与"横向广度"正交。
- 关键差异 1（D10-3）：nop TeamTaskFlowOrchestrator+MemberFanOutDispatcher+AllMustSucceedReduction+PlanExecutor（DAG ready 计算/重规划/停滞检测）——声明式团队编排 dsh 仅有 experimental agent-team。
- 关键差异 2（D10-1）：dsh SubagentProvider 6 传输（spawn/fork in-process、acp、claude-code、codex、dsh-sdk）+Activation 驻留纪元+reportFrom 子→父通道；nop 派生走 call-agent 工具与 team 成员声明，无外部协议 provider。
- 关键差异 3（D10-2）：双方都有深度预算——nop 父约束钳制（effective tools/paths 逐层收紧）与 dsh delegationDepth 持久化+单调（恢复不能清零）语义不同：nop 钳能力、dsh 计深度。
- 可吸收增量建议一句话：nop 可吸收 dsh 的子代理驻留纪元（Activation）与结构化子→父投递（reportFrom）补齐长生命周期子任务面（见 ⑥）。

## ② nop 侧机制与锚点

按 02 T17 与 01 代码地图（team/runtime/plan 包），对比增量：

- **派生模型**（D10-1）：两条路——①工具化派生：`CallAgentExecutor`（call-agent 工具，`tool/CallAgentExecutor.java`）+ SendMessageExecutor/send-message；②团队声明：`ITeamManager`（InMemory/Db，`team/ITeamManager.java`）声明成员，engine 装配时 `AgentTeamBinder.precheckTeamDeclarations/autoBindTeam`（`engine/DefaultAgentEngine.java:703,826`）。
- **深度预算**（D10-2）：父约束钳制——`prepareDispatchContext` 按 delegationDepth 钳制子代理 effective tools/paths（`engine/AgentToolDispatcher.java:114-155`"父约束钳制的 effective tools/paths"）；metadata/delegationDepth 注入 ctx（`DefaultAgentEngine.java:726-742`）。
- **团队编排**（D10-3）：`TeamTaskFlowOrchestrator`+`MemberFanOutDispatcher`+`AllMustSucceedReduction`（`team.flow/`）+team 五工具（execute-flow/send-message/status/task-create/task-update，`tool/Team*Executor.java`）+team.scheduler；另有计划状态机 `PlanExecutor`/`PlanScheduler`（DAG ready 计算）/`PlanReplanner`/`StagnationDetector`（`plan/runtime/`）——任务编排（fan-out/归约/DAG）全栈内置。
- **隔离边界**（D10-4）：进程内 Actor 容器（opt-in，`runtime/AgentActor.java`+`IActorRuntime`）；跨进程：`ISessionTakeoverLock`（CAS+lease）+`IDaemonCoordinator` 多实例守护（`runtime.coordination/`）+DBMessageService 平台消息桥（at-least-once）。

## ③ 对方侧机制与锚点

按 02 T17 与 01 代码地图（subagent 12 包），对比增量：

- **派生模型**（D10-1）：`SubagentRuntime`（ctx.subagents 具名 provider 注册表：一次性 run/持久发现/continuable 操作/listChildren，`packages/subagent/subagent/src/index.ts:171`）；`SubagentProvider` 传输抽象（name/capabilities/inheritsParentContext/start/prepareContinuable——**方法存在即能力**，`types.ts:292-331`）；6 provider：spawn（全新同进程）/fork（种子继承父历史）/acp/claude-code/codex（外部进程协议）/dsh-sdk（`subagent-*-in-process等/src/index.ts` 各 name 导出）。
- **深度预算**（D10-2）：`SessionHeader.delegationDepth` 持久化+`AgentOptions.subagentDepth` 运行时，`delegationDepthOf=max(header, runtime)` 单调（恢复不能清零）+请求侧 maxDepth（`packages/core/session/src/types.ts:85-91`、`subagent/src/depth.ts:11-39`）；能力表 `SubagentCapabilities` 含 depthLimit（`types.ts:83-89`）。
- **continuation 管理**（D10-1 扩展）：`SubagentContinuationManager`（身份预留/组合/创建/冷恢复/中断/上报/排空，`continuation.ts:355`）；`Activation`（子代理驻留纪元 running|waiting|settled，:8-9）；`reportFrom`（子→父内容投递不结束子 turn，`subagent/src/index.ts:270`）。
- **隔离边界**（D10-4）：子代理本质=另一个普通 session+agent（同 ReactLoopAgent）——隔离来自独立 session 日志+fork 种子；out-of-process provider 经 out-of-process.ts 适配；`subagent/start`/`end` 事件按委派父 scope 过滤。

## ④ 子机制逐项对照表

| 子机制 | nop 机制 | dsh 机制 | 裁定 | 证据 |
|---|---|---|---|---|
| D10-1 派生模型 | 工具化派生（call-agent/send-message）+team 成员声明（TeamManager） | SubagentProvider 具名注册表 + 6 传输 provider（进程内 spawn/fork、外部协议 acp/claude-code/codex、SDK）+Activation 驻留纪元+reportFrom 子→父通道 | 对方领先 | nop `tool/CallAgentExecutor.java`、`team/ITeamManager.java`；dsh `subagent/src/index.ts:171`、`types.ts:292-331`、`continuation.ts:355`——传输谱系与长生命周期子任务管理（continuation/Activation）dsh 显著更宽 |
| D10-2 深度预算 | 父约束钳制（delegationDepth 逐层收紧 effective tools/paths）——钳**能力** | delegationDepth header 持久化+运行时单调 max+maxDepth+depthLimit——计**深度** | 等价 | nop `AgentToolDispatcher.java:114-155`；dsh `depth.ts:11-39`——两种预算语义（能力收敛 vs 层级计数）各自自洽，不可直译；dsh 的"恢复不能清零"单调设计防重启重置逃逸 |
| D10-3 团队与编排 | 全栈内置：TeamTaskFlowOrchestrator+MemberFanOutDispatcher+AllMustSucceedReduction+PlanExecutor（DAG/重规划/停滞检测）+team 五工具+scheduler | 无内置 team（experimental agent-team 包+goal-round-driver 续跑）；编排逻辑留白扩展层 | nop 领先 | nop `team.flow/`、`plan/runtime/PlanExecutor.java`；dsh `packages/experimental/agent-team/`——声明式 fan-out/归约/DAG 编排 nop 独有；dsh 的 goal round（`goal-round-driver/src/index.ts:76`）是单代理续跑非多代理编排 |
| D10-4 与主循环的隔离边界 | 进程内 Actor（opt-in）+跨进程接管锁（CAS+lease）+多实例守护+DB 消息桥（at-least-once） | 隔离=独立 session 日志+fork 种子（进程内）或外部进程 provider（协议适配）；事件按委派父 scope 过滤 | 等价 | nop `runtime/`（Actor/lock/coordination）+`message/DBMessageService.java`；dsh `out-of-process.ts`、`subagent/src/index.ts:140-166`——隔离谱系 dsh 更宽（进程内外/协议），跨实例协调 nop 更强（锁/守护/可靠消息）；维度不同各自领先，综合等价 |

## ⑤ 语义差异与取舍

- **子代理本体**（T17）：dsh 子代理=另一个普通 session+agent（同 ReactLoopAgent，无特殊运行时）——"一切皆 agent"的均匀性；nop 子代理=约束钳制的执行上下文（父约束逐层收紧）+可选 Actor 容器——"派生即收敛"的治理性。词同义异警示：dsh 的 fork（种子继承父历史）≈ nop forkSession(inheritContext=true)（独立快照复制）——语义等价（都是 COW 式前缀继承），载体不同（种子事件 vs 消息拷贝）。
- **Activation/continuation 是 dsh 独有词**（02 T17）：驻留纪元（running|waiting|settled）+冷恢复+排空——长生命周期子任务（跨 turn 存活、稍后取回结果）的一等管理；nop 的对位只有 call-agent 同步等待与 send-message 异步消息，无驻留状态机。
- **编排 vs 组合的立场**：nop 把"多代理协作"产品化为内置编排原语（fan-out/归约/DAG 计划）；dsh 把它留给组合（spawn N 个一次性子代理+宿主聚合，experimental agent-team 亦走 extension 形态）。前者开箱可编排、升级需改引擎；后者零内置成本、编排模式自由但无开箱保障（AllMustSucceed 之类的归约语义要自写）。
- **跨实例维度**：nop 的接管锁+守护协调面向服务端多实例部署；dsh 单进程模型+外部协议 provider 面向端侧——平台形态差异大于设计优劣，登记不硬比。

## ⑥ 裁定 + 可吸收增量建议

**维度总裁定：等价**——D10-3 nop 显著领先（编排全栈）、D10-1 对方领先（传输谱系+continuation）、D10-2/D10-4 等价；两方的多代理能力正交（纵向编排 vs 横向传输），不存在包含关系。

可吸收增量建议（仅记录，不实施）：

1. 【来源 dsh；针对 nop 子任务生命周期】为 call-agent 增加 continuable 形态（Activation 对位：子执行驻留 waiting/settled 状态+稍后 followup/取回），支撑长周期子任务（当前 call-agent 同步等待、send-message 无状态机）。
2. 【来源 dsh；针对 nop 传输面】CallAgentExecutor 抽象 SubagentProvider 式传输接口（in-process/进程外/协议适配），为接入外部 agent 运行时（如 ACP）预留接缝。
3. 【来源 nop；反向记录供 dsh 参考】team.flow 的 AllMustSucceedReduction/fan-out 编排原语可作为 dsh extension 生态的官方编排插件（当前 experimental agent-team 缺归约语义）。
4. 【来源 dsh；针对 nop 深度记账】nop delegationDepth 目前以约束钳制表达，可补充显式深度计数字段（持久化到 session metadata+单调校验），使深度审计与 dsh depthLimit 式上限配置成为可能。

## References

- `ai-dev/analysis/compare-agent-design/00-dimension-matrix.md`（D10 定义）、`02-terminology-map.md`（T17）
- `ai-dev/design/nop-ai-agent/nop-ai-agent-multi-agent.md`（Owner doc）
- nop：`nop-ai/nop-ai-agent/.../team/`、`team.flow/`、`plan/runtime/`、`runtime/`、`tool/CallAgentExecutor.java`
- dsh：`packages/subagent/subagent/src/{index,types,depth,continuation}.ts`（`~/ai/deepseek-harness` @ c291e7961a）
