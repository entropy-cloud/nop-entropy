# Nop AI Agent 远程服务平台层设计

**日期**：2026-10-08
**范围**：nop-ai-service / nop-ai-web / nop-ai-app（服务面装配）、nop-ai-gateway / nop-credential（模型与密钥边界）、nop-job（定时入口）。不修改 Agent 引擎内核契约。
**状态**：active（设计基线；文中标注为 successor 的接线项尚未实现）
**决策来源**：2026-10-08 平台维护者对远程 agent 分布式服务形态的愿景陈述与裁定

---

## 一、设计结论

1. **部署目标形态是远程多用户异步任务调度平台**。用户远程登录使用系统：提交任务（即时或定时）、查看执行进度、通过 git checkout 获取代码产出。优先级排序：可靠性、资源控制、可恢复 > 交互体验；不追求交互式 vibe coding 的用户友好性（00-vision 已将交互式 REPL 与逐 token 流式决策列为 Non-Goal）。
2. **引擎内核保持嵌入式库形态，平台层只做服务面装配**。Agent 引擎（ReAct / Team / Scheduler Daemon）不感知远程部署拓扑；多用户、任务提交、进度查询全部通过装配层实现——遵循 00-vision「渐进式增强：扩展通过添加接口实现，不通过阶段切换」。
3. **agent 任务的远程入口是一等 ORM 实体 + BizModel**。团队/任务存储（`ai_agent_team`、`ai_agent_task` 等）注册为 ORM 实体并暴露 GraphQL/REST + AMIS 页面；`ITeamTaskStore` 契约保持不变，DB 实现迁移到 ORM 实体之上。`IAgentEngine` 由 service 层持有，提交任务 = 创建任务记录，执行由既有 `ITeamTaskSchedulerDaemon` claim 或显式触发。
4. **进度可观察性 = 事件 + 状态快照**，经 DB + GraphQL 查询交付；明确不做逐 token 打字机 UI。
5. **交付边界双轨**：代码工件以 git 分支为交付界面（agent 分支提交，用户 checkout / 评审 / 合并，见 branch-affinity §3.6）；任务元数据（状态、进度、用量计费）与非代码工件以 DB + GraphQL 为界面。git 不是唯一交换总线。
6. **密钥三层不可见边界**：LLM 不可见 → agent 执行环境不可见 → 服务进程不持明文。远程平台的目标部署形态是网关形态 A（密钥仅存在于独立网关进程）。
7. **模型共享语义 = 凭证归属 + 凭证级 RBAC**：用户专属模型与团队共享模型由凭证归属 scope（`user` | `system`）加授权关系表达；模型/账号行经 `credentialId` 绑定凭证，不复制 key。多租户 key 归属的 open question 就此裁定为账号/凭证级。
8. **定时触发复用 nop-job**：cron 任务定义 → 到点创建 agent 任务记录（幂等）→ 进入既有排队执行链路。平台不自建 cron 解析器，不引入独立队列中间件。
9. **资源配额在远程平台部署形态是一等公民**：模型/账号并发度、并发 actor 数、构建并发度必须显式装配并配置非零限额；`NoOp*` shipped 默认只适用于嵌入式库形态（零回归语义），平台部署不得以其为终态。

## 二、背景与动机

00-vision 将产品定位为「大规模无人值守自动化执行」，架构基线（01-architecture-baseline.md §五）定义了「用户请求 → Gateway → 持久化层 → Agent 实例消费」的部署模型。但当前服务面存在结构性缺口：

- agent 团队/任务表由 `DbTeamTaskStore` / `DbTeamManager` 以 raw JDBC 自建自管，未注册 ORM BizModel，无 GraphQL 暴露、无 AMIS 页面——远程用户没有任何提交/查询 agent 任务的结构化端点；
- `IAgentEngine` 在 nop-ai-service 中零引用，远程进入引擎的唯一通路是 IM 信道连接器（channel-connector，如飞书）；
- 密钥解密与 LLM 调用发生在同一 JVM 进程——「运行时 AI 不可见密钥」只满足了 LLM 层（密钥不进 prompt），未满足进程层；
- 用户定时启动任务没有入口。

本篇将平台维护者的愿景陈述固化为服务面设计基线，作为后续 plan 立项依据。

## 三、核心设计

### 3.1 服务面：任务提交与进度

**任务实体与状态机**：服务面投影既有团队任务状态机（创建 → claim → 执行 → 终态，含 reclaim 语义），不另造状态模型。任务记录增加面向用户的字段：提交者、提交时间、触发方式（manual / scheduled）、关联 session、进度事件引用。

**GraphQL 契约**（命名遵循 Nop biz 惯例 `{Entity}__{action}`，action 集合在实施 plan 定稿）：

| action | 语义 |
|--------|------|
| `submit` | 即时提交：创建任务记录并入队 |
| `stop` | 终止执行中任务（复用引擎 cancel 语义） |
| `queryProgress` | 查询任务状态 + 已发生事件列表 |
| CRUD（CrudBizModel 自动暴露） | 任务/团队实体的分页查询、详情、权限过滤 |

**权限**：任务记录继承 Nop RBAC（提交者可见自己的任务；团队角色可见团队任务）；服务端 action 经 action-auth 配置约束。

**进度查询是拉取 + 订阅两种形态**：GraphQL query 拉取状态快照与事件列表；推送（GraphQL Subscription / SSE / Webhook）是部署选择，不进入引擎层契约（01-architecture-baseline §五已裁定推送机制是 Gateway/前端实现选择）。

### 3.2 交付边界：git 分支与 DB 元数据双轨

| 产出 | 交付界面 | 依据 |
|------|---------|------|
| 代码工件（源码修改、构建产物对应提交） | git 分支：agent 小步本地 commit 到 agent 分支，session 结束/用户触发才 push；用户 checkout / 评审 / 合并 | branch-affinity §3.6 |
| 非代码工件（生成文档、报表等） | DB（`NopAiGenFile` 及其历史版本）+ AMIS 页面下载 | 既有实现 |
| 任务元数据（状态、进度、用量计费） | DB + GraphQL（本篇 §3.1） | — |

**约束**：
- 代码工件必须可从 git 分支 checkout 复现——分支是权威载体，DB 只存引用（worktree 路径、分支名、commit hash），不存代码内容。
- 元数据不得写入 git（不塞 commit message / 不落 repo 内文件）——元数据的查询、权限过滤、聚合是 DB 的职责，进 git 会丧失结构化查询能力且污染工作区。

### 3.3 模型与密钥边界

**三层不可见边界**（逐层收紧，全部为硬约束）：

| 层级 | 含义 | 现状 |
|------|------|------|
| L1 LLM 不可见 | 密钥不进 prompt、工具结果、事件流、日志 | ✅ 满足（密钥服务端解析注入 HTTP 客户端，不进模型上下文） |
| L2 agent 执行环境不可见 | agent 沙箱 / bash / git 子进程的环境变量与文件系统不携带密钥；git 子进程环境清洗 `GIT_*`（见 build-scheduling §3.5 工程约束） | 部分满足（密钥本就不落 agent 工作区；环境清洗纪律待 git CLI 落地时一并强制） |
| L3 服务进程不持明文 | LLM 调用方进程内零密钥：解密与 key 注入收敛到独立网关进程 | ❌ 目标形态（successor） |

**目标部署形态 = 网关形态 A**（nop-ai-gateway 02-account-failover-requirement §一.2）：nop-gateway 独立网关持凭证并终结 LLM 连接，agent 服务进程经网关转发调用。凭证解密边界收敛为：nop-credential（解密服务）→ 网关（key 注入点），其余进程零明文。形态 B（本地 `IChatService` 适配器，解密与调用同进程）满足 L1/L2，保留为嵌入式/开发形态，不是远程平台的部署基线。

**模型共享语义**：模型可见性 = f(凭证归属， 授权关系)，不复制 key、不做租户级隔离（与 nop-credential 二期裁定「归属统一 scope=system|user」一致）：

| 模型形态 | 表达方式 |
|---------|---------|
| 用户专属模型 | 凭证 scope=`user` + 凭证级 RBAC 授权该用户；模型行经 `credentialId` 绑定 |
| 团队共享模型 | 凭证 scope=`system` + RBAC 授权到团队角色/用户组；模型行经 `credentialId` 绑定 |

**拒绝**：把 key 配置进 agent DSL、工具参数或 prompt 模板——key 一旦进入模型上下文即破坏 L1，且会随会话持久化/压缩链路扩散。

### 3.4 定时入口

- 定时任务定义为 nop-job cron 任务；到点触发时以**幂等创建**方式生成 agent 任务记录（同一调度周期内重复触发去重），任务随后进入既有排队执行链路（`ITeamTaskSchedulerDaemon` 扫描 + CAS claim）。
- 定时入口只负责任务创建，不直接调用 `IAgentEngine`——与即时提交共享同一条执行链路，保证两种触发方式的排队/配额语义一致。
- **拒绝**：平台自建 cron 解析器或独立调度服务（复用优先）；定时入口绕过队列直发引擎（会产生第二条并发控制路径，破坏资源控制语义）。

### 3.5 资源配额是一等公民

远程平台部署必须显式装配并配置以下配额（缺省装配 = 部署错误，启动期校验告警）：

| 资源 | 控制点 | 现状 |
|------|--------|------|
| 模型/账号并发度 | `ConcurrencyRegistry`（per provider + accountKey） | ✅ 已落地 |
| 租户并发 actor 数 | `IResourceGuard` / `CONCURRENT_ACTORS_PER_TENANT` | ✅ 已落地（默认 NoOp） |
| 团队并行成员数 | `IResourceGuard` / `TEAM_PARALLEL_BOUND_MEMBERS` | ✅ 已落地（默认 NoOp） |
| 构建并发度 | ResourceGuard 构建配额维度 + worktree 构建锁 | 设计见 build-scheduling（successor） |
| per-agent token/时间累积、存储配额 | ResourceGuard | successor |

部署纪律：`NoOp*` shipped 默认的定位是**嵌入式库形态的零回归语义**，不是平台部署的合法终态——远程平台装配必须替换为功能实现并配置限额，配额装配完整性属于部署检查项。

## 四、拒绝了什么

| 拒绝项 | 理由 |
|--------|------|
| git 作为唯一交换总线（元数据塞 commit message / repo 内文件） | 元数据丧失结构化查询与权限过滤；污染工作区。见 §3.2 约束 |
| 逐 token 流式打字机进度 UI | 00-vision Non-Goal：无人值守场景不需要，徒增引擎状态机复杂度 |
| 为 agent 任务自建队列中间件/独立调度服务 | 平台已有 DB CAS 排队 + 调度守护 + nop-job cron，复用优先；新增中间件引入第二套可靠性语义 |
| 密钥进 DSL/工具参数/prompt | 破坏 L1 边界，随会话链路扩散 |
| 多租户级 key 隔离/复制 | 与 nop-credential 二期裁定冲突：scope=system\|user + 凭证级 RBAC 已覆盖「用户专属/团队共享」语义 |
| 定时入口绕过队列直发引擎 | 破坏统一排队与配额语义 |

## 五、与已有设计的关系

- `00-vision.md` — 产品定位与 Non-Goal 的出处；本篇是定位在部署形态上的具体化
- `01-architecture-baseline.md` §五/§九 — 部署模型与多租户配额基线；本篇的 §3.5 将其提升为平台部署纪律
- `nop-ai-agent-actor-runtime-vision.md` — 多用户隔离为 Phase 2 successor；其 open question「LLM API Key 租户级还是全局级」由本篇 §3.3 裁定
- `nop-ai-agent-branch-affinity-scheduling.md` §3.6 — git 分支交付模型的出处；本篇 §3.2 界定其与 DB 元数据的边界
- `nop-ai-agent-channel-connector.md` — IM 信道是本层传输侧的一种连接器，与本篇的结构化服务面并行
- `nop-ai-agent-task-flow-integration.md` — 任务编排复用 nop-task DAG；定时入口创建的任务走同一条编排链路
- `../nop-ai-gateway/02-account-failover-requirement.md` — 形态 A/形态 B 定义；本篇裁定远程平台基线为形态 A
- `../nop-credential/README.md` — 凭证归属 scope 与凭证级 RBAC；本篇 §3.3 的共享语义建立其上
- `nop-ai-agent-build-scheduling.md` — 构建并发配额与仓库隔离的专题设计
- `docs-for-ai/03-modules/nop-credential.md`、`docs-for-ai/03-modules/nop-job.md` — 平台使用侧对应文档
