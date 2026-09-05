# nop-stream 数据面传输收敛与持续运行稳定性设计（items 28 + 31）

> Status: active（最终设计状态）
> Created: 2026-09-04
> Source: plan `ai-dev/plans/nop-stream-productization/2026-09-03-1951-3-remote-deploy-dataplane-stability.md` Phase 1 裁定；证据 = `ai-dev/analysis/2026-09/2026-09-03-distributed-stability-exercise-report.md` + `ai-dev/analysis/2026-09/2026-09-01-nop-stream-runtime-module-audit.md` §2.2 F-D/W-8
> 关联: `01-architecture-baseline.md` §六（数据流模型）、`distributed-runbook.md` §7（已知边界）、`checkpoint-design.md`（恢复模型）

---

## 一、问题与目标

remote-deploy 数据面存在一个停摆缺陷族（演练 runId 证据见 Source）：

1. **全对订阅**：TM 侧 remote-deploy（`SubtaskPlanBuilder` 路径）为整个 job graph 的全部 (src, tgt) subtask 对构建通道并**在构造期订阅**；TM 只运行 assigned subtask，其余通道订阅后无人读取——纯投递垃圾面。
2. **队列满永久阻塞**：消费通道入队使用无界阻塞等待；通道队列（默认 1024）填满后阻塞消息后端 dispatch 线程。JDBC 轮询后端下 dispatch 线程池仅 2 线程、被全部订阅（数据面 + 控制面 topic）共享，两条死通道即可停摆该 TM 的全部投递（含控制面），停摆形态 = 永久 jam（非 fail）。
3. **继发控制面失效**：jam 诱发 per-task liveness stall 检测 → 自动 global recovery 连发耗尽全局 recovery cap（`maxRestarts=3`）→ 此后真实节点 kill/租约到期永久不可恢复。

目标：消除上述三环（含 `RpcDistributedExecutor` remoteDeployMode=true 下协调器侧的同构实例），并补齐 JDBC checkpoint 存储的 retained manifests 恢复语义。吞吐优化、credit-based 流控、TM 观察面（roadmap item 32）不在本设计范围。

## 二、D1 裁定：per-subtask 订阅收敛（订阅范围切面）

**决策：采用候选①（assigned-subtask 视角的订阅收敛），拒绝惰性订阅（候选②）。**

执行计划构建器引入**订阅范围**概念，三态：

| 订阅范围 | 语义 | 绑定调用方 |
|---|---|---|
| **全订阅** | 全部消费通道在构建期订阅（既有语义，逐字不变） | 单 JVM 执行器：`EmbeddedDistributedExecutor`、`RpcDistributedExecutor`（remoteDeployMode=false）——单 JVM 内运行全部 subtask，全矩阵通道全部被消费 |
| **指定 subtask 集订阅** | 仅「投递目标 ∈ 指定 subtask 集」的消费通道订阅；其余消费通道**构造但不订阅**（永不订阅，非延迟订阅）；producer 侧 partition 全矩阵照常构造 | TM remote-deploy 路径（`SubtaskPlanBuilder.buildSubtaskPlan`）：每次 deploy 的订阅集 = 该 descriptor 的 assigned (vertex, subtaskIndex) 单例 |
| **零订阅** | 全部消费通道构造但不订阅 | `RpcDistributedExecutor` remoteDeployMode=true 的协调器侧 plan：协调器运行零 subtask，收敛终态 = 零订阅（非「只订 assigned 输入」——D1(f) 二态裁定的落地形态） |

**硬约束论证**（对应 plan D1 约束 (a)—(f)）：

- **(a) InputGate 语义不变**：assigned subtask 的 InputGate 仍见其**全部** upstream 通道（barrier 对齐 / unaligned channel-state 语义依赖的通道集合不变）；被收敛掉的只是**其他 subtask** 的通道订阅。
- **(b) 单 JVM 全订阅不回退**：全订阅是构建器对无范围参数调用方的默认行为；单 JVM 执行器路径零改动。
- **(c) 全图构建结构保留**：收敛只作用于**订阅激活**，不裁剪任何构建产物——`DeployedSubtaskPlan.getPlan()` 的消费方（TM 侧 checkpoint plan 构建、rescale 感知 restore）继续拿到镜像全局拓扑的完整 plan。
- **(d) 不依赖「订阅早于生产」时序**：被订阅通道的订阅时点不变（plan 构建期，早于任何 task 运行）；被收敛通道**从订阅面上消失**而非延迟激活——不存在「激活晚于首消息」的新时序假设。这是拒绝候选②的直接理由：`LocalMessageService` 无 backlog 重放，JDBC 后端 cursor-0 重放不能作为唯一安全论证，惰性激活会引入激活竞态。
- **(e) 结构语义不变**：topic 命名确定性（`StreamTopicNaming`）、restore-time rescale、TM 间同构图 fingerprint 钉定均不受影响（收敛不触碰图结构与命名）。
- **(f) 协调器侧同构实例**：remoteDeployMode=true 时协调器侧 plan 改为零订阅构建（见上表）；构建结构保留（plan 对象仍产出），仅订阅面清零。该实例的收敛终态与 TM 路径不同型（零 subtask 运行 ⇒ 零订阅），不允许与 TM 路径混用同一形态。

**累积语义（union）**：同一 TM 连续部署 N 个 subtask 后，其消息订阅集 = 各次 deploy 输入集之并（逐次累积，不重置；通道随所属 task 关闭而取消订阅）。

**误用防护（no-silent-noop）**：未订阅通道被读取属接线错误，必须以 typed 错误快速失败，不得表现为空读/永久阻塞。

**producer 侧不变式**：producer 向全部 target topic 的发送能力保持（发送侧从不订阅）；send 侧消息表行数是后端行为，不在本设计收敛范围（演练报告已修正归因：表行由 send 侧 INSERT，订阅收敛消除的是无人消费通道的**投递面**）。

## 三、D2 裁定：队列满语义（有界等待 + typed 可恢复失败）

**决策：采用候选①（有界等待，超时转通道内 typed 可观测失败），拒绝非阻塞投递 + 重投协议（候选②）。**

消费通道入队语义从「无界阻塞等待」改为「**有界等待**」：

- 入队等待上限默认 **10 秒**（构造参数可调）。等待期内槽位释放即成功——**健康慢消费者不触发失败**（节流 sink 档位下按消费速率持续释放槽位，有界等待退化为逐条短等待，即 dispatch 层背压）。
- 超时（= 队列满且**整整一个等待窗内零消费进展**）判定为下游停滞，走 **decodeError 同款通道内可观测失败**：置 typed 溢出错误标志 + 标记通道结束 + 唤醒阻塞读者。读者（task 线程）在 read 路径 surfaced 为 typed 异常 → task FAILED → 恢复链路接管。**不得以正常 EOS 形态返回**（空读 EOS = 截断数据被静默确认，违反不丢数据约束）。
- 失败不依赖 `onMessage` 异常上抛（轮询后端吞消费异常且 cursor 照常推进，上抛不可观测）——通道内标志是唯一可靠观测面。

**后端安全论证**：

- **JDBC 轮询后端（多 JVM）**：消息消费后不删除、恢复后新订阅自 cursor-0 重读 + 新 fencing epoch 过滤陈旧消息 + producer 自 checkpoint 状态重发——溢出失败经恢复闭环重放，exactly-once 不破坏。dispatch 线程（2 线程池）单通道阻塞上限 = 等待窗；等待窗 10s 与 60s liveness 超时 / 30s 租约阈值之间留有安全余量（且租约续期走独立线程直写注册表，不经消息后端）。
- **LOCAL 后端（单 JVM，`LocalMessageService` 同步派发）**：无界阻塞等待的原语义实际是「生产者线程阻塞 = 进程内背压」；有界等待保留该背压（槽位持续释放则持续成功），仅把「消费者死亡 → 生产者永久静默挂死」升级为「等待窗零进展 → typed 失败 → 作业失败可见」。LOCAL 无重放，重放语义由作业级恢复/重跑承载——失败可见性严格优于静默挂死。
- **健康路径不变式**：有读者的正常路径按序投递、不丢不重（有界等待仅在零进展满窗时才触发失败路径）。

**拒绝候选②的理由**：非阻塞投递 + 消费进度/重投协议要求消息后端原生支持按消息位点的重投协商，超出 `IMessageService` 平台接口与现有 harness 后端的能力契约（`PollingJdbcMessageService` 为测试树装置，生产级传输语义重构被 plan 显式排除）；候选①在既有后端语义内可完整论证不丢数据。

## 四、D3 裁定：stall 恢复预算与真实故障恢复的区分

**决策：采用候选①（机制：stall 触发的恢复独立预算 + 冷却窗口），同时保留候选②的根因消除证据作为回归锚点。**

恢复触发增加**原因维度**，预算按原因分池：

| 触发原因 | 预算池 | 语义 |
|---|---|---|
| 节点租约到期（failure detector） | 真实故障预算（既有 `maxRestarts=3`，值与超限 failJob 语义不变） | 真实故障恢复能力不被 stall 消耗 |
| task FAILED 报告（含 D2 溢出失败 surfaced 的 FAILED） | 真实故障预算 | typed 任务失败是真实故障 |
| 其他/兼容入口（无原因参数的恢复请求） | 真实故障预算 | 既有调用方语义不变 |
| per-task liveness stall 检测 | **独立 stall 预算**（`maxStallRestarts`，默认 3）+ **冷却窗口**（`stallRecoveryCooldownMs`，默认 30s） | 冷却窗内的 stall 恢复请求以可观测 WARN 跳过（周期检测器冷却后自然重试）；stall 预算超限 → failJob（持续 stall 是有界重试后应终止的缺陷，不无限重试） |

- 检测器分类规则：节点租约失效优先归真实故障；纯 liveness 停滞（节点存活）归 stall。两类同时命中按真实故障计。
- fencing 语义不变：**所有** global recovery（不论原因）照常轮转 fencing epoch（陈旧任务 fencing 不变量与恢复原因无关）。
- **拒绝纯候选②（仅论证不设机制）的理由**：D1+D2 消除 jam 根因后，演练观察到的「stall ×3 耗尽 cap」路径在 jam 场景不可达——但「stall 恢复与真实故障共享预算」是结构性属性，未来任何 stall 源（用户代码挂死、病理性 GC 停顿）都会静默消耗真实故障恢复能力；机制成本极小（计数分池 + 冷却）且可独立测试。候选②的根因消除由 Phase 2/3 focused 测试（通道积压不再诱发永久 dispatch 阻塞）与 Phase 4 CHAOS-1 kill 轮次（真实故障恢复能力保持）作为回归证据固定。

## 五、D4 裁定：复验范围

**决策：BP-1 与 CHAOS-2 全格复验纳入（非 watch-only）。**

- **BP-1（三档节流 50/200/500ms）纳入**：它是唯一「持续输入 + 慢消费者持续排空」的演练形态——D2 有界等待不得误伤健康慢消费者的核心回归点；500ms 档窗口累计流量跨过旧 jam 阈值，同时携带 D1/D2 双重正向信号。
- **CHAOS-2（HA JC failover ×2）纳入**：其演练中唯一失败的判据（failover 后 fencing 轮转 + 终态收敛）归因 jam 抑制重部署；租约翻转/leaderEpoch 递增子判据在 jam 下已通过（控制面租约独立于数据面，本修复不触碰租约/选主代码）。全格复验以证据关闭「jam 消除后新 leader 重发 assignment + fencing 轮转」的遗留问题，而非以论证代验证。

## 六、JDBC retained manifests 恢复语义（W-8 承接）

`JdbcCheckpointStorage` 补齐 `loadRetainedEpochManifests`：按 epoch 降序取最近 `count` 条 retained manifest，语义与 `LocalFileCheckpointStorage` 对等（多 epoch 保留集 + count 上界），供 Stage-31 重启恢复（`restoreSharedStateRegistry` 重建 SharedStateRegistry 引用计数）在 JDBC 后端不降级。双存储语义契约：**任一 ICheckpointStorage 实现的 retained 集读取必须是「最新优先、count 截断、含全部 retained epoch」**，默认实现（仅返回 latest）只是无 per-epoch 持久化能力存储的降级底座，具备 per-epoch 持久化的实现必须 override。

## 七、使用契约（面向后续开发）

1. 新增数据面执行器/部署路径时，必须显式选择订阅范围三态之一；「构建全图 + 订阅谁」是两个独立决策，禁止把「构建范围」当「订阅范围」用（或反之）。
2. 消费通道的入队等待窗是可观测契约：任何新后端接入时，必须论证「等待窗零进展 ⇒ typed 失败」在后端的可恢复性（重放来源明确），禁止静默丢弃或无限阻塞。
3. 恢复触发方必须携带原因（真实故障 / stall）；新增触发入口默认归真实故障池并显式声明。
4. 演练判据锚点（本设计引入的回归信号）：SOAK-3 全参数（36000 行 = 旧 jam 阈值 ~36 倍）完整收敛 + durable epoch 推进 ≥5 且无 >120s gap；CHAOS-1 kill 轮次 fencing 严格递增 + 终态 == 期望集；BP-1 各档位 checkpoint 持续推进。
