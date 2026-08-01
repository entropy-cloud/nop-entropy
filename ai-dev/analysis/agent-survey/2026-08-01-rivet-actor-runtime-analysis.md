# Rivet Actor 原语与 AgentOS 深度分析 & Nop AI Agent Actor 运行时

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/rivet`（rivet-gg/rivet，TS 定义层 + Rust 运行层的 Actor 模型运行时）vs `nop-ai-agent`（AgentActor/InMemoryActorRuntime 半成品 Actor 抽象）
> Conclusion:

## 一、总览

**Rivet** 是一个 Actor 模型运行时：开发者用 TypeScript 定义 Actor（`src/actor/definition.ts`），由 Rust 核心（`src/actor/`，ActorTask 约 3000 行）执行。核心抽象：**Actor 声明（声明式状态机：init/action/state）+ ActorContext（keepAwake/waitUntil/cron/queue 原语）+ 4 条 mpsc 消息通道 + sleep 默认超时 30s + saveState 持久化**。

| 维度 | Rivet | Nop AI Agent |
|------|-------|--------------|
| Actor 定义 | TS 接口（definition.ts + config.ts ~2254 行） | Java 接口（AgentActor + AgentActorHandler） |
| 执行层 | Rust ActorTask（生命周期/调度/恢复） | 无（InMemoryActorRuntime 内存注册表） |
| 通道 | 4 条 mpsc（lifecycle_inbox/dispatch_inbox/…） | 无（同步调用） |
| 睡眠 | 默认 sleepTimeout 30s，可 keepAwake | 无（AgentSession 生命周期外无任务唤醒） |
| 持久化 | saveState（显式调用，恢复时 replay） | AgentSession 存储 + checkpoint（快照非 replay） |
| 消息 | Actor 消息 + queue + cron + waitUntil | 无消息抽象（仅工具调用） |

**核心结论先行**：nop 的 `AgentActor` 目前是"1:1 绑定 session 的 API 门面"（`AgentActor.java` 直接操作 AgentSession），不是真正的位置透明 Actor。Rivet 的三个设计——**声明式定义层与命令式执行层分离**、**ActorContext 计时/队列原语**、**saveState + sleep 的持久化语义**——正好补齐 nop Actor 运行时（InMemoryActorRuntime 只有 start/get 注册表）的缺口。7 月博客《Rivet Harness：通用 Actor 运行时深度解析》逐层拆解了 ActorTask，与 `ai-dev/design/nop-ai-agent/nop-ai-agent-actor-runtime-vision.md` 中"有状态 Agent 即 Actor"的方向直接对应。

## 二、Context（调研背景）

- **为什么需要这个分析**：nop 已有 AgentActor 抽象和 actor-runtime-vision 设计文档（设计先行），但实现层（InMemoryActorRuntime）只有最简注册表。Rivet 是"完整 Actor 运行时"的现成参考，且 7 月文章有逐文件拆解。
- **要回答的问题**：Rivet 的 ActorTask 生命周期、消息通道、sleep/saveState 语义如何映射到 nop 的 Actor 运行时设计？
- **约束**：nop 是 Java 21 单进程，有既有会话存储/checkpoint；Rivet 是 Rust + TS 双语言。

## 三、核心机制详解

### 3.1 定义层与执行层分离（架构核心）

```
定义层（TS，声明式）               执行层（Rust，命令式）
src/actor/definition.ts  ────────► src/actor/task.rs
  Actor interface                  ActorTask（~3000 行）
  init(context)                     生命周期管理（start/handle/sleep/resume）
  action(state, payload)            4 条 mpsc 通道
  state<T>()                        调度器集成（slot + 恢复）
config.ts（~2254 行）                ActorInstanceHandle（外部 handle）
  运行时配置：sleep timeout/队列/cron
```

- 定义层只声明"状态、动作、入口"；执行层负责并发、调度、恢复、持久化钩子。
- 对 nop 的映射：`AgentActorHandler`（定义）vs `ActorRuntime`（执行）——nop 已有此分离骨架，缺的是执行层的生命周期/通道/持久化细节。

### 3.2 4 条消息通道（task.rs）

```
ActorTask 内部 4 条 mpsc：
  lifecycle_inbox   — 生命周期事件（start/stop/pause/terminate）
  dispatch_inbox    — 外部 Actor 消息（dispatch 调用）
  wake_inbox        — 计时器唤醒（sleep 到期）
  queue_inbox       — 队列消息（后台任务/定时任务）
```

- 每个 inbox 由独立的 actor 循环处理，消息类型与优先级分离。
- 对 nop 的借鉴：会话的「用户消息 / 工具结果 / 超时唤醒 / 后台任务」也应分通道排队，而不是单一的 agent 循环顺序处理——这是支撑 keepAlive/awaitUserInput 的基础设施。

### 3.3 sleep 与 keepAwake

- 默认 sleep timeout 30s；sleep 是**可恢复挂起**（记录唤醒时间 → 计时器到点投递 wake 消息），不是阻塞线程。
- `keepAwake(timeout)`：延长未收到消息时的存活时间。
- 对 nop：AgentSession 是"一次会话 = 一次执行"；actor 化后 session 可长驻（await 用户输入 / 定时任务），这正是 actor-runtime-vision 里的 1:1 扩展方向。

### 3.4 saveState 与恢复

- `saveState()` 显式保存 Actor 状态快照；恢复时**重放未完成动作**（replay semantics）。
- 与 nop checkpoint（快照 + 恢复验证）语义互补：Rivet 强调动作级重放，nop 强调消息序列 checkpoint。

## 三.5 Harness 可靠性（Retry/Replan/Resume）

- **sleep + keepAwake 可恢复挂起**（`task.rs`）：sleep 到期投递 wake 消息——**挂起而非终止**，醒来继续（Actor 级重试）。
- **saveState 显式持久化**：Actor 状态快照 + 恢复时 replay 未完成动作——**动作级重放**。
- **4 通道消息分离**：lifecycle/dispatch/wake/queue 分通道——失败消息重投不阻塞其他通道。
- **对 nop 的启示**：saveState replay 是 nop checkpoint 的动作级恢复参考；wake 通道对应 nop 的 WAIT_FOR 语义（hatchet 借鉴）。

## 四、优缺点

### 优点

1. 声明式 Actor 定义让并发模型可静态检查（状态/动作图清晰）。
2. 4 通道分离消息优先级，避免队头阻塞（用户消息不会被后台任务卡死）。
3. sleep/keepAwake 不占线程，调度器 + 计时器统一管理。
4. 持久化语义显式（saveState 是普通方法，不是魔法）。
5. 定义层与执行层解耦 → 定义可序列化/可迁移/可审查。

### 缺点

1. 双语言（TS 定义 + Rust 执行）增加工具链复杂度；对 Java 生态的 nop 是架构参考而非代码参考。
2. mpsc 多通道的调度/唤醒时序复杂，边界条件多。
3. 文档与测试重心在 Rust 侧，TS 定义层的能力边界文档不足。
4. 消息传递 vs 方法调用的心智模型对简单 session 是过度设计。

## 五、对 nop-ai-agent 的借鉴要点（核心价值）

nop 现状：`AgentActor.java`（1:1 session 门面，操作 AgentSession 的 run/stop/checkpoint）、`InMemoryActorRuntime`（start/get 注册表 + 生命周期监听）、`ActorChannel`（入站消息信封）。vision 文档已有方向（多 session Actor、位置透明、事件总线）。

### 5.1 通道分离（高优先，性价比最高）

- 在 `ActorChannel` 基础上增加**事件类型分派**：`LIFECYCLE / DISPATCH / WAKE / QUEUE` 四类入站信封，分别对应 start/stop、用户消息、超时唤醒、后台任务。
- ReActAgentExecutor 的循环从"顺序处理"改为"按优先级取队列"：用户消息优先于后台任务；超时唤醒驱动等待。

### 5.2 sleep/keepAwake 持久化（高优先）

- `AgentSession` 增加 `waitUntil(timeout, condition)`：挂起而不是返回；条件满足或超时 → 投递 WAKE 消息恢复执行。
- 与 checkpoint 集成：挂起时保存 WAIT_FOR 检查点（借鉴 hatchet `2026-08-01-hatchet-durable-execution-analysis.md`），恢复时无需重放全部历史。
- keepAwake 映射为 session 保活租约（多实例场景需要，但单机可从简）。

### 5.3 定义/执行分离的深化（中优先）

- `AgentActorHandler`（定义：动作/状态/入口）与 `ActorRuntime`（执行：调度/生命周期）已有骨架；补齐：**actor 注册即持久化**（InMemoryActorRuntime 增加 DB 持久化适配器，对应 vision 中的 ActorRegistry）。

### 5.4 不照搬的

- TS/Rust 双语言架构；nop 全 Java 即可。
- Actor 消息传递取代工具调用——nop 的工具调用仍是主通道，Actor 消息用于生命周期/唤醒/后台任务。

## 六、结论

- Rivet 验证了「声明式 Actor 定义 + 命令式执行层 + 通道/计时/持久化原语」是通用 Actor 运行时的完整形态。
- nop 的 Actor 抽象已有正确骨架（AgentActor/ActorChannel/ActorRuntime），最缺的是**四通道分派、sleep/waitUntil 原语、注册持久化**——按 5.1/5.2/5.3 顺序落地即可对齐 vision 文档。
- 后续工作：将 `nop-ai-agent-actor-runtime-vision.md` 的「有状态 Agent 即 Actor」细化为 actor-runtime 实现设计。

## Open Questions

- [ ] nop 的 WAKE 唤醒由谁驱动（单机 Timer / 数据库轮询 / 事件总线）？
- [ ] Actor 消息是否需要对用户开放（外部系统向 agent 发消息）？
- [ ] 多 session Actor 与现有 AgentSession 1:1 绑定的兼容策略？

## 六.5 Harness 机制维度覆盖（对照参考框架 D1-D12）

> 参考：`2026-08-01-harness-mechanism-reference-framework.md`（Agent Harness 十二大机制维度）

覆盖维度：**D10**（Actor 定义/执行层分离+4 通道）、**D4**（saveState+replay）、**D12**（sleep/keepAwake 可恢复挂起）。缺失/薄弱：D6、D9。

## 对比结论：nop-ai-agent 全面超越性分析

**nop-ai-agent 已超越的部分**：
- **Actor 抽象**：nop `AgentActor` + `InMemoryActorRuntime` + `ActorChannel` 已有正确骨架（1:1 session 绑定）；rivet 是 TS 定义层 + Rust 执行层的双语言方案——nop 单 Java 栈更简洁。
- **持久化**：nop `DBCheckpointManager` append-only + `AgentSession` 存储，优于 rivet 的 saveState 手动快照。
- **会话生命周期**：nop `AgentSessionLifecycle`（12 hook 点）比 rivet 的 ActorTask 生命周期更细粒度。

**必要参考的增量（以超越方式吸收）**：
- **4 通道消息分离**（lifecycle/dispatch/wake/queue）：nop `ActorChannel` 目前单通道——按事件类型分派是真正增量（用户消息优先于后台任务）。
- **sleep/keepAwake 原语**：nop 会话缺"挂起等待 + 定时唤醒"——以 `waitUntil(timeout, condition)` 结构化实现。

**总评**：nop-ai-agent 在 Actor 抽象/持久化/生命周期上**全面超越**（vision 文档已对齐方向）；4 通道分派 + waitUntil 两个原语值得吸收，实现完全 nop 原生。

## References

- `~/ai/rivet/src/actor/definition.ts`、`config.ts`、`task.rs`、`scheduler.rs`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-actor-runtime-vision.md`
- `nop-ai-agent/src/main/java/io/nop/ai/agent/runtime/agent/AgentActor.java`、`runtime/InMemoryActorRuntime.java`
- `ai-dev/analysis/agent-survey/2026-08-01-hatchet-durable-execution-analysis.md`
