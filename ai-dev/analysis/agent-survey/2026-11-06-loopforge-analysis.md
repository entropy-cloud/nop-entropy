# LoopForge（Tencent）分析：SOP 驱动的 coding-agent 交付工作流平台

> Status: open
> Date: 2026-11-06
> Scope: `~/ai/LoopForge`（腾讯开源，Go 后端 + Web 工作台 + 本地 CLI，~221K 行 Go / ~191K 行 JS / ~35K 行 Python）
> Conclusion:

## Context

- 调研目的：为一套「把 AI agent、确定性脚本、人工决策编排进同一条交付流程」的参考实现做拆解。
- 关联关注点：SOP/DAG 编排、角色分离（实现/评审/测试）、人工审批门、（可恢复）执行与运行证据、沙箱与执行机拓扑。这些与 nop-ai-agent 的 plan/hook/审批/执行会话设计属同一问题域。
- 仓库来源与许可：`https://github.com/Tencent/LoopForge`，MIT。默认分支 `main`，调研时 HEAD 为 `557482e`（2026-11-05）。

## 一、项目定位

LoopForge 是**自托管的 coding-agent 交付工作流平台**，官方定位为 "GitHub Actions for agent-driven delivery"：

- **流程是固定的，AI 是灵活的**：每个需求沿同一条 SOP 跑（clarify → design → implement → independent review → test → human acceptance → delivery）。
- 不替代 Claude Code / Codex / CodeBuddy，而是**编排它们**：每一步 agent 节点实际调用对应 CLI，LoopForge 负责「谁做、按什么顺序、做哪些检查、谁签收」。
- 三大执行子形态：
  - **Platform**：Docker Compose 起服务，Web 工作台 + 可视化 SOP 画布 + 审批 + 运行证据（面向团队）。
  - **Local**（`local/`，npm 包 `loopforge-cli`）：把内置工作流装进 Claude Code / CodeBuddy / Codex / Cursor / Pi，无需起服务器（面向单开发者，命令 `/start-devflow "<需求>"`）。
  - **Execution machines / runners**：个人或池化执行机，用本地工具链跑任务。
- 目标客户是**可信团队**（默认仅监听 localhost；Docker socket 授予宿主 daemon 控制权，明确声明非多租户隔离）。

## 二、技术栈与顶层架构

| 层 | 技术 / 落位 |
| --- | --- |
| 后端 | Go 1.26.10，module 名仍为 `agentflow`；Gin（HTTP）、GORM（ORM）、MySQL（生产）/ SQLite（测试） |
| 前端（新版 React 工作台） | `chatbot/`，Vite + React |
| 前端（原生页 + 设计系统） | `web/`，Vanilla + `web/app/harness-ui.css`（Harness UI 令牌体系） |
| 本地工作流 | `local/`，Node CLI + Python 运行时（Classic / Portable 两版） |
| 进程入口 | `cmd/agentflow`（API/worker/combined）、`cmd/agentflow-runner`、`cmd/agentflow-mcp`、`cmd/agentflow-gate-mcp`、`cmd/loopforge-account` |
| 文档 | `docs/architecture.md`、`docs/deployment.md`、`docs/validation.md`、`docs/open-source-scope.md` |

后端分层（`docs/architecture.md`）：

```
cmd/                    进程入口（API / worker / runner / MCP / account）
internal/engine/        SOP 图执行、租约、调度、沙箱生命周期、交付
internal/repo/          MySQL 持久化，按业务域一个 Repo（gorm）
internal/httpapi/      HTTP/MCP handler，按业务域一个 handler
internal/runtime/      执行器协议、凭据、隔离、产物
internal/runner/       执行机（personal / pool）
internal/tracker/      远端 issue/PR adapter
web/ chatbot/ local/   前端与本地工作流
migrations/            显式 SQL 迁移（不自动跑）
```

规模（`find` 统计，排除 node_modules）：

| 语言 | 文件 | 行数 | 说明 |
| --- | --- | --- | --- |
| Go | 965 | 221,054 | 后端 + engine/runtime 主体 |
| JS | 354 | 190,872 | `chatbot/` + `web/` 前端 |
| Python | 152 | 35,404 | `local/` 本地工作流运行时 |
| TS | 113 | 66,954 | 本地 CLI / 工具 |
| SQL | 26 | 747 | `migrations/` |

Go 测试文件 525 个（`*_test.go`），测试密度较高。

## 三、核心机制详解

### 3.1 SOP = 有向图 + 四类节点

- `WorkflowDefinition` 由 `Nodes` + `Edges` 组成（`internal/domain/workflow.go:160` 起）。
- 四类节点（`internal/domain/workflow.go:47`，`Type` 字段注释）：**agent / script / approval(人工门) / sop(子流程)**。
- 节点关键字段（`internal/domain/workflow.go:39-70`）：
  - `AgentID` / `ExecutorID`：以哪个角色、哪个执行器跑；
  - `SOPWorkflowID`：`sop` 节点引用的子工作流；
  - `ScriptLanguage` / `Script`：`script` 节点的确定性步骤（shell / python）；
  - `ArtifactOutputs []ArtifactContract`：**声明式产物契约**（`Name/Path/Required`，`workflow.go:84`），引擎据此收集证据；
  - `RejectTo` / `OnFailure`(fail|reject) / `RetryStrategy`(targeted_fix|full_redo) / `MaxAttempts` / `MaxReworks` / `CarryOver`：返工和重试语义。
- 图推进是「**依赖满足即就绪**」：`readyNodes()` 遍历 edges，仅当某节点所有前驱 `Status == "succeeded"` 且自身为 `pending` 才进入就绪集（`internal/engine/workflow_graph.go:11-32`）。
- 边有 `when` 语义（`workflow.go:93-98`），支撑「失败/返工回流到指定阶段」。

### 3.2 角色（AgentDefinition）= 指令 + 执行器 + 规则 + Skills + MCP

- README 明确「separated roles」：内置 SOP 把实现、评审、测试分派给**不同角色**，每个角色有自己的 instructions、executor、rules、Skills、MCP 工具。
- 这相当于「按职责切分的 agent 定义」，而非单一通用 agent；评审与实现**不共享上下文**，从机制上避免「自审自过」。

### 3.3 人工审批门（Human gate）

- `approval` 节点暂停运行，直到有人查看产物并批准/退回。
- 结构化门结果：`GateResult` 以 JSON 产物落盘（`.agentflow/gate-results/<node>/attempt-<n>.json`，`internal/engine/gate_submit.go:25-31`），支持「批准 / 退回到某阶段 / 失败处理」。
- `VerificationGate`（`internal/domain/workflow.go:10-20`）区分 `command`（跑命令校验）与 `manual`（人工），`Stage` 可配 `after_execution` / `before_publish`，`OnFailure` 可取 `loop|reject|fail|human`。这是「校验门」与「审批门」的统一抽象。

### 3.4 执行器与沙箱（多后端可插）

- `internal/runtime/provider_claude.go` / `provider_codebuddy.go` / `provider_codex.go`：三种 CLI 执行器适配。
- 沙箱：`runtime/sandbox_docker.go`（宿主 Docker socket 起容器）、`sandbox_local.go`、`remote_sandbox.go`（远端执行机）。
- 执行目标抽象（`executionTarget`）+ `executeForTargets`（`internal/engine/node_execute.go:544`）把同一脚本/命令派发到 run 工作区或各仓库工作区。
- `internal/managedcli/` 负责托管 CLI 进程的启动/捕获/协调。
- **MCP**：`cmd/agentflow-gate-mcp`（结构化门结果）、`cmd/agentflow-mcp`（公共 MCP）、`runtime/github_mcp.go`（GitHub 适配）、`internal/gatemcp`。MCP 凭据按 actor 作用域隔离（CHANGELOG）。

### 3.5 可观测 / 可恢复

- 运行详情：节点状态、输出、工具轨迹、日志并排（README "Run details"）。
- **工作区检查点**：`migrations/013_workspace_checkpoints.sql`、`018_workspace_restore_status.sql`，配合 `internal/domain/preview*.go` 与 `engine/preview_finalizer`，支持「从任意节点重跑 / 恢复已用环境」。
- **租约与多实例**：`engine` 内有 `leaseTTL`、`instanceID`、`engine_instances`（跑一次加锁、写状态、发 hook、记 trace）。
- **暂停/取消/重试**：`runControlNotify`、`drain`、`retry`、`rerun from node`。
- **运行评测**：`internal/runeval` + `domain/run_eval.go` + `domain/insights.go`（研发效能指标）。

### 3.6 需求 → 运行的数据模型

- 业务域 Repo（`AGENTS.md` 显式约定）：`CatalogRepo`（agents/rules/skills/MCP/executors/workflows…）、`ProjectsRepo`、`IssuesRepo`、`TasksRepo`、`RunsRepo`、`RunnersRepo`、`DeliveryRepo`、`UsersRepo`、`TokensRepo` …
- `Issue`（需求）→ 选 SOP → `Run` → 节点产物 → `DeliveryMR`（合并请求交付状态）。
- `SOPCheckpoint` 冻结**运行时的图与节点状态**（`workflow.go:114-118`），与 catalog 里的「最新定义」解耦，保证重试/审批不依赖后续模板改动。
- 明确的治理纪律（`AGENTS.md`）：代码不做迁移/seed，迁移一律手写 SQL 手动执行；`Store` 只做组合与连接生命周期，禁止新增领域方法——对多域 Go 服务的边界控制很克制。

### 3.7 Local 工作流（`local/`）

- 面向「已有 coding assistant 的单个开发者」：`npx loopforge-cli install claude|codebuddy|codex|cursor`，入口 `/start-devflow`。
- Classic 版通过配置 agent 执行权限与 hooks 实现；Portable 版适配 Pi。
- 与 Platform 共享「内置交付工作流」的理念，但运行时资产分离（`docs/architecture.md` 明确要求不要把运行时资产互相复制）。
- 局限：目前只跑内置本地工作流，「平台 SOP 导出到本地独立执行」仍是计划中能力。

## 四、优缺点

### 优点

1. **流程确定性 + AI 灵活性分离**：脚本节点承担 build/test/lint，agent 只在需要智能处介入——避免「什么都交给 LLM」的不可控。
2. **角色分离 + 独立评审**：实现/评审/测试是不同角色、不同上下文，从结构上对抗「自审自过」。
3. **声明式产物契约**（`ArtifactContract`）：每节点声明输出，运行证据天然可审计。
4. **校验门与审批门统一抽象**（`VerificationGate` 的 command/manual × stage × onFailure）。
5. **可恢复性**：工作区检查点 + 节点级重跑 + 租约，适合长时交付。
6. **多执行后端**：平台 worker / Docker 沙箱 / 远程执行机，且执行器可插（Claude/Codex/CodeBuddy）。
7. **前端与本地两条腿**：平台面向团队，Local 面向个人，覆盖两种真实用法。
8. **工程纪律清晰**：AGENTS.md 对 repo/handler/migration 边界的硬约定，Go 测试密度高（525 测试文件）。

### 缺点

1. **强绑定外部 coding CLI**：能力边界受 CodeBuddy/Codex/Claude Code 的接口与鉴权限制。
2. **可信团队假设**：默认 localhost、Docker socket 直通宿主 daemon，不适合多租户/公网直接暴露。
3. **平台与 Local 能力尚未打通**：SOP 导入本地独立执行仍属计划。
4. **前端技术栈分裂**：`chatbot/`(React) 与 `web/`(原生 + 手写设计系统) 并存，维护面偏大。
5. **Go 后端体量大**（221K 行、`httpapi` 295 个条目），新人上手成本不低。
6. **星标/社区规模小**（调研时 ~111 stars、28 open issues），生态与第三方集成尚早。

## 五、成熟度评估

- 活跃开发：HEAD 2026-11-05，CHANGELOG 有 "platform edition" 大版本整合记录。
- 有明确的发布就绪/验收文档（`docs/release-readiness.md`、`docs/validation.md`、`docs/testing/acceptance-matrix.md`、`e2e/api/45-delivery-model.test.mjs`）。
- 有分类迁移脚本与 seed 目录，说明经历过真实部署演进。
- 定位为「可信团队自托管」而非通用 SaaS，规模与治理匹配其目标。

## 六、与 nop-ai-agent 的关系 / 可借鉴点

| 维度 | LoopForge | nop-ai-agent（参考） | 借鉴判断 |
| --- | --- | --- | --- |
| 编排 | SOP 有向图，依赖满足即就绪 | AgentPlan 线性/静态 | 高：可给 plan 增加「边/依赖 + trigger 语义」 |
| 节点类型 | agent / script / approval / sop | 步骤 + hook | 高：把「确定性脚本」与「人工门」提升为一等节点 |
| 产物 | 声明式 `ArtifactContract` | 产物/证据分散 | 高：显式产物契约便于证据审计 |
| 门 | `VerificationGate` command/manual × stage × onFailure | 审批/校验散落 | 高：统一「命令门 + 人工门」抽象 |
| 恢复 | 工作区检查点 + 节点级重跑 + 租约 | session/恢复机制 | 中高：检查点与「从任意节点重跑」值得对齐 |
| 角色 | 实现/评审/测试分角色、独立上下文 | 子 agent / 权限继承 | 高：角色分离是抗自审的结构性手段 |
| 多后端 | 平台/Docker/远程执行机可插 | 执行会话抽象 | 中：执行目标抽象可参考 |
| Local 形态 | 装进现有 assistant、无需服务器 | CLI exec 已具备 | 中：Local 的「零服务、装进 IDE」体验可借鉴 |
| 治理 | repo/handler 按域切、迁移手写 | — | 中：多域边界的硬约定值得参考 |

**核心可借鉴的三点**：(1) 把「脚本节点」「人工门」作为一等公民，而非附属于 agent 步骤；(2) 声明式产物契约 + 结构化门结果落盘，让证据链可机械审计；(3) 实现/评审/测试分角色、独立上下文。

## Open Questions

- [ ] `sop` 子流程节点的嵌套深度/循环是否有限制？跨 run 的 handoff（`GroupSOPHandoff`）与子 SOP 的关系？
- [ ] 工作区检查点的粒度（文件快照 vs 增量）与恢复时 `runOnWorkspaceRebuild` 的重放语义是否一致？
- [ ] 多实例部署下 lease/`engine_instances` 的脑裂边界（`controlRole`、`fallbackWorkerURL`）？
- [ ] Local 与 Platform 的 SOP 资产将来如何单一来源化（当前明确要求「不互相复制」）？

## References

- `~/ai/LoopForge/README.md`、`README.zh-CN.md`
- `~/ai/LoopForge/docs/architecture.md`、`docs/deployment.md`、`docs/validation.md`、`docs/release-readiness.md`、`docs/local/usage.md`
- `~/ai/LoopForge/AGENTS.md`（repo/handler/migration 边界纪律）
- `~/ai/LoopForge/internal/domain/workflow.go`、`internal/engine/workflow_graph.go`、`internal/engine/node_execute.go`、`internal/engine/gate_submit.go`
- `~/ai/LoopForge/CHANGELOG.md`
- 外部：`https://github.com/Tencent/LoopForge`
