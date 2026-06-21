# 10 · 对比、启示与可借鉴的设计模式

> 本章基于 `ai-dev/design/nop-ai-agent/` 设计文档（`00-vision.md`、`01-architecture-baseline.md`、`nop-ai-agent-dsl.md`、`nop-ai-agent-session-and-storage.md`、`nop-ai-agent-context-model.md`、`04-tool-invocation.md`）+ `IToolFileSystem` 源码，做准确对比。
>
> **修正说明**：本调研初稿曾把 Nop 简单概括为「模型驱动生成 + 单进程同步请求/响应」，这是不准确的。深入阅读 nop-ai-agent 设计后发现 Nop AI agent 实际是 **DSL-First + VFS 统一抽象 + 天然分布式 actor 系统 + Event Log 状态**，且已有 `IToolFileSystem` 统一文件系统接口。本章按准确理解重写。

## 核心范式对照：DSL-First vs Filesystem-First

两者都追求「可读即真相」「约定消灭配置」，但**真相载体**和**统一抽象**截然不同：

| 维度 | Nop AI agent（DSL-First） | eve（Filesystem-First） |
|---|---|---|
| **真相载体** | **DSL**（`agent.xdef`/`tool.xdef`/`agent-plan.xdef` 用 xdef schema 定义字段语义，runtime 解释 DSL） | **文件系统**（目录结构即配置，`define*` 是 identity helper） |
| **统一抽象** | **VFS**：DSL 文件 + 工作文件 + 工具文件系统三者统一管理，后端可替换（本地/DB/对象存储） | **物理目录**：authoring 目录 + 编译产物 `.eve/`（JSON+ESM） |
| **Agent 是什么** | `AgentModel`：从 `agent.xdef` 装载的**纯配置对象**，不持有执行逻辑和状态 | `defineAgent({...})`：identity helper 返回的配置对象 |
| **配置/执行/状态** | **三者严格分离**（AgentModel 配置 / Agent 无状态执行 / AgentSession 独立状态）— 约束 #2#3 | 编译产物/运行时分离，但 agent 定义本身混合配置与 model 选择 |
| **身份来源** | DSL `name` 属性，经 VFS 路径 `/{name}.agent.xml` 加载（路径与 name 绑定） | **纯路径推导**，`define*` 禁写 `name` |
| **定制** | **Delta 可逆覆盖**（同路径合并、可叠加、可撤销）— 可逆计算核心 | 同名文件覆盖（如 `tools/bash.ts` 覆盖内置），不可逆 |
| **工具文件系统** | **`IToolFileSystem`**：统一接口（read/write/glob/grep/patch/move/copy/delete），`isPathAllowed` 路径白名单，底层映射 VFS | sandbox `/workspace`（模型生成代码）+ app runtime（工具 execute） |
| **状态模型** | **Event Log**（append-only session 状态 source of truth）+ 派生快照 + Session Tree（fork）+ Plan 跨 session | **durable workflow**（每个 session 是 workflow run，turn/step checkpoint） |
| **部署模型** | **天然分布式 actor**：单进程 Virtual Thread → 多实例自动扩展，`IMessageService` 屏蔽拓扑，fork/exec 进程隐喻 | durable workflow（Vercel Workflow / local world） |
| **通信** | `IMessageService`（内存队列 / DB-backed 跨进程）+ Agent 域 `IAgentMessenger`（deferred-ack mailbox） | channel 抽象（`continuationToken` 句柄） |
| **扩展机制** | 四层接口（Core→Execution→Reliability→Platform）+ Hook/Skill/Plan DSL + `IContributionRegistry`（7 种贡献类型） | 目录 slot（tools/skills/channels/connections/schedules/subagents/hooks） |
| **渐进式增强** | **约束 #4**：内部最简化，更多假定通过 XDSL 配置逐步引入（权限/安全/审批），扩展靠加接口实现不靠阶段切换 | pre-1.0 偏好 breaking change，无渐进增强契约 |

### 关键洞察

1. **两者都用「文件系统」作为统一抽象，但层级不同**：
   - Nop 的 **VFS 是真正的虚拟文件系统**——DSL 文件、工作文件、`IToolFileSystem` 三者统一到 VFS 接口，后端可换（本地→DB→对象存储），多实例部署切共享存储时代码无感
   - eve 的 filesystem 是**物理目录**——authoring 是磁盘文件，编译成 `.eve/` 产物，没有「后端可替换的虚拟层」

2. **Nop 的 DSL-First 比 eve 的 filesystem-first 更严格**：
   - Nop：xdef schema 定义字段语义，`AgentModel` 是 schema 校验过的纯配置对象，**约束 #1 明确「不把 runtime 假设伪装成 DSL 字段」**
   - eve：`define*` 是 TS 约定（identity helper + brand sentinel），没有独立 schema，校验在 compile 阶段

3. **状态管理两者都有深度，但模型不同**：
   - Nop：Event Log（append-only）+ 派生快照 + Session Tree——**面向无人值守自动化**（约束：可靠性/可恢复性/确定性优先）
   - eve：durable workflow（turn/step checkpoint + park/resume）——**面向长对话/跨天跨重启**

4. **身份派生两者其实都基于路径，但 Nop 允许显式 name**：
   - Nop：`/{agentName}.agent.xml` 路径 = name，但 name 是 DSL 字段（可被 Delta 引用）
   - eve：纯路径，禁写 name（更激进）

## eve vs 传统 agent 框架（LangChain / Autogen）

| 维度 | 传统框架 | eve |
|---|---|---|
| **配置形式** | Python/JS 组装 chain/graph/agent 对象，或大 JSON | 文件系统结构即配置；目录是 manifest |
| **身份来源** | 显式 `name`/`id` 或注册表 | 路径推导；移动文件 = 移动身份 |
| **状态模型** | 一次 request/response，或自接 Redis/DB | **durable by default**：每个 turn 是 durable workflow |
| **暂停-恢复** | 自己搭队列/状态机 | 内建 HITL/OAuth/subagent "park" |
| **工具执行位置** | 通常和 agent 同进程同权限 | 双上下文：app runtime（trusted）+ sandbox（isolated） |
| **测试** | 通常手动或外部脚本 | **一等公民**：`evals/` + `eve eval` + LLM-judge + Braintrust |
| **运行时依赖** | 一堆（LangChain 自身拉几百个传递依赖） | **单一**（nitro） |

## eve vs IDE-style agent（Claude Code / Cursor）

| 维度 | IDE-style agent | eve |
|---|---|---|
| **形态** | 嵌在编辑器/CLI 的本地工具 | 服务化框架，HTTP + 多平台 channel |
| **生命周期** | 一次会话/任务 | durable session 可跨天/跨重启 |
| **工具** | 内置读/写/搜索代码 | 内置 harness + 自定义工具 + MCP/OpenAPI connection |
| **多代理** | 较少 | 内置 subagent + `agent` 拷贝工具 + remote agent |
| **评测** | 通常手动或外部脚本 | 一等公民 |

eve 的 10 个默认工具（bash/glob/grep/read_file/write_file/...）与 Claude Code / Cursor 高度相似——**eve 把「IDE agent 的能力」标准化成了 agent 框架的 baseline**。

---

## 设计模式校准：Nop 已有 vs eve 独有

> 重要修正：初稿把许多 Nop **已有或更强**的能力误列为「借鉴点」。下面严格区分。

### Nop 已有或更强的能力（对比项，非借鉴项）

| 能力 | Nop 的实现 | eve 的对应 | 对比 |
|---|---|---|---|
| **统一文件系统抽象** | `IToolFileSystem`（read/write/glob/grep/patch/move/copy）+ VFS 后端可替换 | sandbox `/workspace`（物理目录） | **Nop 更抽象**：VFS 后端可换 DB/对象存储，多实例共享 |
| **配置/执行/状态分离** | AgentModel / Agent / AgentSession 三者严格分离（约束 #2#3） | 编译产物/运行时分离 | **Nop 更严格**：约束级 |
| **可逆定制** | **Delta 覆盖**（可叠加、可撤销）— 可逆计算核心 | 同名文件覆盖（不可逆） | **Nop 独有**，eve 无对应 |
| **分布式 actor** | 天然分布式（单进程 Virtual Thread → 多实例），fork/exec 隐喻 | durable workflow（单 runtime） | **范式不同**，Nop 面向多实例集群 |
| **状态持久化** | Event Log（append-only source of truth）+ 派生快照 + Session Tree | durable workflow（turn/step checkpoint） | **模型不同**，都支持崩溃恢复 |
| **工具权限** | `isPathAllowed` 路径白名单 + `IApprovalGate` + 四层安全（deny/allow→分级→审批→沙箱） | `needsApproval`（never/once/always） | **Nop 更纵深**（四层渐进） |
| **DSL schema 约束** | xdef 定义字段语义，约束「不把 runtime 假设伪装成 DSL 字段」 | TS `define*` 约定（无独立 schema） | **Nop 更严格** |
| **渐进式增强** | 约束 #4：内部最简化，通过 XDSL 配置逐步引入能力 | pre-1.0 偏好 breaking change | **Nop 有明确契约** |
| **编译产物可 audit** | `_gen/` + xdef schema 本身是可检视契约 | `.eve/compile/*.json`（带 SHA256 + 版本号） | eve 的版本号/摘要更工程化 |

### eve 独有、值得 Nop 借鉴的设计

#### 1. Durable-by-default 的 turn/step checkpoint（eve 独有亮点）

eve 把每个 session 做成 durable workflow run，**每个 turn 是独立 child workflow，每个 step（一次 model call + 工具执行）是 durable checkpoint**。被中断的 step 重跑，已完成的 step 绝不重跑。

**Nop 现状**：nop-ai-agent 用 Event Log（append-only）记录状态变更，崩溃恢复靠重建 AgentSession。但**没有 turn/step 粒度的 checkpoint 契约**——Event Log 是消息级，不是执行步骤级。

**借鉴价值**：Nop 的可靠性设计（`nop-ai-agent-reliability.md`）可以考虑引入「step 级 checkpoint」语义，让长跑 ReAct 循环的中断恢复更精细（已完成 step 不重跑，避免非幂等副作用）。

#### 2. Filesystem-first 的 authoring 体验（eve 独有亮点）

eve 的 `define*` 是 **identity helper**（输入即输出、保留字面量类型、加 brand sentinel、禁写 name），authoring surface 极简——复杂度全部集中到 compile 阶段，产物可 git diff。

**Nop 现状**：DSL authoring 是 XML（`.agent.xml`），有 xdef schema 校验，但没有「identity helper」这种「输入即输出、类型推断友好」的轻量 authoring 形态。

**借鉴价值**：Nop 的 DSL 已足够严格，但可以借鉴 eve 的「authoring 极简 + 复杂度集中到编译」理念——某些场景（如工具定义、skill 定义）可考虑更轻量的 authoring 入口，再编译成标准 DSL。

#### 3. 一等公民的 eval 系统（eve 独有亮点）

eve 的 eval：`defineEval` + `t` 上下文（驱动+断言合一）+ gate/soft 二分 + LLM-judge + fixture-owned e2e。**走真实 HTTP 表面**，通过 = agent 真启动 + 真接受请求 + 真产出结果。

**Nop 现状**：有 AutoTest（自动生成测试数据），但**没有 agent 级的 eval 体系**——没有「驱动 agent session + 断言行为 + LLM-judge」的标准化框架。

**借鉴价值**：这是 eve 最值得 Nop AI agent 借鉴的——**构建 nop-ai-agent 的 eval 层**：
- `t` 上下文模式（命令式驱动 + 链式断言）
- gate/soft 二分（确定性断言 gate，LLM-judge soft）
- fixture-owned e2e（`nop-chaos` 系统化，每个 demo 既是示例又是测试）

#### 4. Vendored 三方依赖 + 单运行时依赖（eve 独有亮点）

eve 通过 Node subpath imports（`#compiled/*`）vendoring 27 个库，**运行时只暴露 `nitro` 一个依赖**。

**Nop 现状**：Java/Maven 生态依赖隔离更成熟，Nop 已有 shaded 模式实践。

**借鉴价值**：理念值得借鉴——「框架自身稳定性不依赖用户 dependency resolution 运气」。Nop 可更系统化 shaded/shading 策略。

#### 5. 机械不变量守卫（eve 独有亮点）

eve 的 `pnpm guard:invariants` 在 CI 跑机械检查，**baseline 只能缩小**。

**Nop 借鉴价值**：可引入「架构不变量守卫」脚本——检查不该出现的依赖/命名/分层违反，baseline 只能缩。

#### 6. 三层句柄解耦：continuationToken vs sessionId（eve 独有亮点）

eve 把「传输层句柄」（continuationToken，channel 拥有）和「运行时句柄」（sessionId/workflow runId，runtime 拥有）解耦——channel 可重新 key 而 session 持久。

**Nop 借鉴价值**：Nop 的 `IAgentMessenger` + session 模型可参考这种「传输句柄 vs 会话句柄」分离，让多 channel 接入时 session 身份稳定。

---

## eve 的风险与局限（客观评估）

1. **强 Vercel binding**：最佳体验在 Vercel，离开需显式替换 8 项能力。
2. **pre-1.0 不保证兼容**：AGENTS.md 明确「prefer breaking changes」「no legacy fallback」。
3. **消息无 FIFO 队列**：并发投递同一 session 不保证有序。
4. **非幂等副作用风险**：被中断的 step 会重跑，要么幂等化要么 approval 闸住——durable 的代价。
5. **TS-only**：authoring surface 强绑 TypeScript。
6. **Workflow DevKit 绑定**：durable 能力深度依赖 `@workflow/core`。
7. **无 Delta 可逆定制**：定制靠同名覆盖，不可叠加撤销——这点 **Nop 的可逆计算明显更强**。

---

## 对 Nop AI agent 的启示（修正版）

基于 nop-ai-agent 已有的深入设计，eve 真正值得借鉴的是**少数几个 Nop 尚未覆盖的点**：

### 最值得借鉴（Nop AI agent 尚缺）
- **Agent eval 体系**：构建 `nop-ai-agent-eval` 模块——`t` 上下文 + gate/soft + LLM-judge + fixture-owned e2e。这是 eve 最成熟、Nop 最缺的一块。
- **step 级 durable checkpoint**：在 Event Log 之上引入「执行步骤级」checkpoint 语义，让长跑 ReAct 中断恢复更精细（已完成 step 不重跑）。

### 可参考的工程实践
- **机械不变量守卫**：`guard:invariants` 模式（baseline 只能缩）
- **编译产物加版本号/摘要/status**：让 `.agent.xml` 编译产物 audit 工具化
- **fixture-owned e2e**：`nop-chaos` 系统化为 agent e2e fixture

### 根本差异（不可照搬，各有所长）
- **Nop 强在**：DSL-First 严格性、VFS 后端可替换、Delta 可逆定制、分布式 actor、四层渐进安全、配置/执行/状态三者分离
- **eve 强在**：filesystem-first 的 authoring DX、durable-by-default 的 turn/step checkpoint、一等公民 eval、单依赖 vendoring、多 channel 开箱即用
- **两者服务的定位不同**：Nop AI agent 面向「大规模无人值守自动化」（约束：可靠性/可恢复性/确定性优先）；eve 面向「durable backend agent on Vercel」（长对话/跨天/多平台入口）

---

## 总结

经过深入阅读 nop-ai-agent 设计文档后，**修正初稿的关键误判**：

1. Nop AI agent **不是**「单进程同步请求/响应」——它是 **DSL-First + VFS 统一抽象 + 天然分布式 actor + Event Log 状态**的完整设计
2. Nop **已有** `IToolFileSystem` 统一文件系统接口、配置/执行/状态三者分离、四层渐进安全——这些初稿误列为「借鉴点」
3. eve 真正的差异化优势集中在：**durable-by-default 的 turn/step checkpoint**、**一等公民 eval 系统**、**filesystem-first 的 authoring DX**、**单依赖 vendoring**

两者是**不同范式的高水平设计**：
- Nop 用 **DSL + VFS + 可逆计算** 解决「自动化执行的可靠性与可定制性」
- eve 用 **filesystem + durable workflow + eval** 解决「agent 的可读性与可测试性」

对 Nop 而言，eve 最有价值的借鉴是 **agent eval 体系**（这是 Nop AI agent 目前最缺的工程化能力）和 **step 级 durable checkpoint 语义**（让长跑恢复更精细）。其余设计 Nop 多已有对等或更强的方案。

---

> 调研完成。Nop AI agent 设计文档位于 `ai-dev/design/nop-ai-agent/`（29 份），权威存储定义在 `nop-ai/model/nop-ai.orm.xml`。eve 调研详见本目录其他 9 份专题文档。
