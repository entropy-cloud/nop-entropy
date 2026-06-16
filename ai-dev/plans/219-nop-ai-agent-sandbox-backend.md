# 219 nop-ai-agent Sandbox Backend (ISandboxBackend + NoOpSandboxBackend + DockerSandboxBackend)

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L4-7
> Last Reviewed: 2026-06-16
> Source: `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 4（L4-7 ❌ 未实现，line 246，依赖 L1-8 ✅）；`ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md` §7.1（ISandboxBackend 沙箱后端设计契约）+ §8（纵深防御链：ISandboxBackend 是最终隔离执行层）+ §10（Shell 与高风险工具约束）；设计文档 §3 Layer 4 架构（`ISandboxBackend` 位于 Platform Security 层，依赖 Layer 1-3）
> Related: `193`（Layer 1 secure-by-default）、`200`（Layer 2/3 Default* secure 实现）、`210`（ICircuitBreaker Layer 3，与 ISandboxBackend 同属纵深防御链）

## Purpose

把 nop-ai-agent 的高风险命令执行从"直接在 host 上运行、无任何隔离"扩展为"按可插拔 `ISandboxBackend` 决策执行环境"。本计划交付沙箱后端契约表面（`ISandboxBackend` 接口 + `SandboxRequest`/`SandboxResult`/`SandboxConfig` 数据对象）、shipped 默认 `NoOpSandboxBackend`（行为零变化——直接在 host 执行）、功能性 `DockerSandboxBackend`（在 Docker 容器中隔离执行，可配置 CPU/内存/wall-time/网络限制，fail-closed 保证沙箱启动失败时拒绝执行而非回退到 host）。本计划只负责这一件事：为 Agent 的高风险工具执行（shell/code exec）提供平台级隔离能力，闭合 roadmap §4 Layer 4 的最后一个 ❌ 工作项。

## Current Baseline

基于 live repo 核对（`nop-ai/nop-ai-agent/src/main`，2026-06-16）：

- **零 sandbox 代码存在**：grep `SandboxBackend|ISandboxBackend|sandbox` 在 `nop-ai-agent/src/main` 仅返回 3 处 javadoc 注释引用（`IApprovalGate.java:15`、`IDenialLedger.java:8`、`IPostDenialGuard.java:11`），均标注 `ISandboxBackend` 为 "Layer 4 — deferred successor"。无任何接口、实现或数据对象存在。
- **纵深防御链已就位到 Layer 3**：`ReActAgentExecutor` 工具分发循环已接通 Layer 1（`IToolAccessChecker`/`IPathAccessChecker`/`IPermissionProvider`）→ Layer 2（`ISecurityLevelResolver`/`IPermissionMatrix`）→ Layer 3（`IApprovalGate`/`IDenialLedger`/`IPostDenialGuard`）的完整检查链。`ISandboxBackend`（Layer 4）是设计 §8 纵深防御链的最后一环：审批通过后，高风险命令在沙箱中执行。
- **安全组件接线模式已固化**：`DefaultAgentEngine` 通过 private 字段 + shipped 默认值 + public setter 模式装配全部安全组件（`approvalGate`=`DefaultApprovalGate`、`denialLedger`=`DefaultDenialLedger`、`circuitBreaker`=`AlwaysClosed` 等，见 `DefaultAgentEngine.java:150-192`）。`resolveExecutor`（`:1943`）将这些组件透传到 `ReActAgentExecutor.Builder`。`ReActAgentExecutor.Builder.build()` 的 null 兜底也使用对应 shipped 默认。`ISandboxBackend` 将沿用同一模式。
- **安全组件命名规范已固化**：全部 shipped 默认实现以 `Default*` 或功能性名称命名（`DefaultToolAccessChecker`、`DefaultApprovalGate`、`AlwaysClosed`、`Slf4jAuditLogger`），全部 pass-through/insecure 保留实现以 `NoOp*`/`PassThrough*`/`AllowAll*`/`Auto*` 命名并保留为 public opt-in。
- **tool 执行模型**：工具通过 `IToolExecutor`（nop-ai-toolkit `IToolExecutor.java`）执行，`executeAsync(AiToolCall, IToolExecuteContext)` 返回 `CompletionStage<AiToolCallResult>`。nop-ai-agent 已有多个 `IToolExecutor` 实现（`CallAgentExecutor`、`SendMessageExecutor`、`AbstractMemoryToolExecutor`）。`ISandboxBackend` 不替换 `IToolExecutor` 机制，而是作为平台级安全基础设施提供给高风险工具执行器使用。
- **nop-ai-shell 同级模块已存在**（`nop-ai/nop-ai-shell/`，44 Java 文件）：提供 Bash 语法解析器（`BashSyntaxParser`/`BashLexer`）、内置命令（`LsCommand`/`EchoCommand`/`CdCommand`）、命令检查器（`ICommandChecker`/`DefaultCommandChecker`）、命令执行器（`ShellCommandExecutor` 620 行）+ 结果数据类（`ExecutionResult`：exitCode/stdout/stderr）。关键发现：`ExternalCommandAdapter.execute()` 是 **stub——抛出 `UnsupportedOperationException("External command fallback requires nop-shell dependency")`**，即外部命令的 ProcessBuilder 执行当前未实现。`ISandboxBackend` 的 `NoOpSandboxBackend` 正好填补这一空缺（提供 host 级 ProcessBuilder 执行），而 `DockerSandboxBackend` 在此之上增加容器隔离。两层关系须在 Phase 1 裁定（见 Phase 1 Decision item）。
- **L1-8 依赖已满足**：`IPathAccessChecker`（L1-8 ✅）及全部 Layer 1-3 安全组件均已落地。roadmap §4 L4-7 声明的唯一依赖 L1-8 ✅。
- **设计文档 §7.1 已定义契约**：`ISandboxBackend` 职责 = 在隔离环境中执行高风险命令；推荐后端 = Docker 容器（服务器端）/ ProcessBuilder + 资源限制（轻量级）/ Noop（默认无隔离）；安全不可降级保证 = 沙箱启动失败 → 拒绝执行，绝不回退到 unsandboxed host；资源限制 = cpuSeconds(30) / memoryMb(1024) / wallSeconds(60) / network(deny)。
- **roadmap §4 Layer 4 状态**：L4-1~L4-6、L4-8 均 ✅，L4-7 是唯一 ❌。本计划关闭后 Layer 4 全部 ✅。

## Goals

- `ISandboxBackend` 接口定义隔离执行契约：接收命令执行请求（含命令、工作目录、环境变量、资源限制），返回执行结果（exit code、stdout、stderr、耗时）。
- `NoOpSandboxBackend` 作为 shipped 默认，行为零变化——命令直接在 host 上执行（经 ProcessBuilder），仅应用 wall-time 超时与输出大小限制（不提供 CPU/内存/网络隔离）。
- `DockerSandboxBackend` 作为功能性 opt-in 实现，在 Docker 容器中隔离执行命令，支持全部资源限制（CPU/内存/wall-time/网络），fail-closed 保证（Docker 不可用或容器启动失败时拒绝执行并抛出异常，绝不回退到 host 执行）。
- `DefaultAgentEngine` 通过 field + setter + `resolveExecutor` 装配 `ISandboxBackend`（默认 `NoOpSandboxBackend`），与现有安全组件模式一致。`setSandboxBackend` 不调用 `warnIfInsecureDefaults`（NoOp 是 Layer 1 设计性基线，非安全降级——见 Phase 1 Decision）。
- `ReActAgentExecutor` 持有 `ISandboxBackend` 引用，使其可供工具分发路径和工具执行器使用（接线验证 = engine → executor → sandboxBackend 引用链连通 + `execute()` 方法可被调用并返回结果）。
- roadmap §4 L4-7 从 ❌ → ✅ 并标注本 plan。
- 设计文档 §7.1 如有与实现不一致的描述，已同步更新。

## Non-Goals

- **Shell/Code 工具执行器本身**：本计划交付 `ISandboxBackend` 契约与实现，不创建具体的 shell-exec / code-exec `IToolExecutor`。具体高风险工具执行器如何消费 `ISandboxBackend` 是独立 successor——本计划只提供基础设施，使其可用。
- **`ISensitivePathProvider`（设计 §7.2）**：敏感路径配置外部化（XDSL schema + Delta 覆盖）是独立的 Layer 4 successor。当前 `DefaultPathAccessChecker` 的内置 denylist 已满足 Layer 1 需求。
- **XDSL 配置化（`<sandbox backend="docker"/>`）**：agent.xdef 增加 sandbox 配置元素是独立增强（optimization candidate）。本计划通过 programmatic setter 装配（与 `setCircuitBreaker`/`setDenialLedger` 等模式一致）。
- **Docker 镜像管理**：镜像构建、推送、拉取等生命周期管理不在本计划 scope。`DockerSandboxBackend` 接收已存在的镜像名作为配置参数。
- ** 非 Docker 后端（Bubblewrap/Seatbelt/gVisor/kata-containers）**：设计 §7.1 提及多后端选项，本计划只交付 Docker 后端（推荐的服务器端方案）。其他后端是独立 successor。
- **沙箱文件系统挂载管理**：容器内的工作目录映射（volume mount）的精细化配置是增强项。首版提供基础的工作目录映射（host workDir → container workDir）。

## Scope

### In Scope

- `ISandboxBackend` 接口定义（契约：接收命令执行请求，返回执行结果）
- `SandboxRequest` 数据对象（命令 + 工作目录 + 环境变量 + 资源限制配置）
- `SandboxResult` 数据对象（exit code + stdout + stderr + 执行耗时 + 超时标志）
- `SandboxConfig` 资源限制配置对象（cpuSeconds / memoryMb / wallSeconds / network 模式）
- `SandboxException`（fail-closed 异常，标识沙箱执行失败——Docker 不可用/容器启动失败/资源限制违反）
- `NoOpSandboxBackend` shipped 默认实现（ProcessBuilder host 执行 + wall-time 超时 + 输出限制）
- `DockerSandboxBackend` 功能性实现（Docker CLI 容器创建/执行/清理 + 全资源限制 + fail-closed）
- `DefaultAgentEngine` 新增 `sandboxBackend` 字段 + `setSandboxBackend` setter + `resolveExecutor` 透传
- `ReActAgentExecutor` 新增 `sandboxBackend` 字段 + Builder 接线
- focused 单元测试覆盖 NoOp / Docker 两个实现
- 端到端接线测试（engine → executor → sandboxBackend 引用链）

### Out Of Scope

- Shell/code `IToolExecutor` 实现（独立 successor）
- `ISensitivePathProvider` 接口（独立 Layer 4 successor）
- XDSL `<sandbox>` 配置元素（optimization candidate）
- Docker 镜像构建/管理生命周期
- 非 Docker 沙箱后端
- 沙箱持久化状态（容器复用/池化）
- `warnIfInsecureDefaults` 扩展（NoOpSandboxBackend 是设计性默认——无沙箱隔离是 Layer 1 基线行为，非安全降级。与 `NoOpDenialLedger` 不同，后者是 secure Default* 已收敛后的 opt-in 回退。sandbox 的 NoOp 默认不存在"更安全的已 shipped 替代"因此无需 WARN。）

## Execution Plan

### Phase 1 - Contract Surface + NoOp Default + Engine Wiring

Status: completed
Targets: `io.nop.ai.agent.security` (or `io.nop.ai.agent.sandbox`) package; `DefaultAgentEngine.java`; `ReActAgentExecutor.java`

- Item Types: `Proof`、`Follow-up`

- [x] 裁定 `ISandboxBackend` 包归属：放入 `io.nop.ai.agent.security`（与其他安全接口同包）或新建 `io.nop.ai.agent.sandbox`（独立包减少 security 包膨胀）。裁定须写清理由。注意 `SandboxException extends NopAiAgentException`（位于 `io.nop.ai.agent.engine`）意味着跨包 import，无论 sandbox 类放在哪个包。
- [x] **裁定 `ISandboxBackend` 与 `nop-ai-shell` 的关系**（Decision）：`nop-ai-shell` 的 `ShellCommandExecutor` 是 in-JVM shell 解析/执行器（解析 Bash 语法 → 派发内置命令 + 外部命令），而 `ISandboxBackend` 是 nop-ai-agent 的平台级安全隔离契约（位于 security 层纵深防御链末端）。两者处于不同层、不同模块、服务不同消费者。`NoOpSandboxBackend` 使用 `ProcessBuilder` 提供 host 级外部命令执行——这正好填补了 `nop-ai-shell` 的 `ExternalCommandAdapter` stub（当前抛 UOE）。但 `NoOpSandboxBackend` 不依赖 `nop-ai-shell`（避免 nop-ai-agent → nop-ai-shell 的模块依赖），而是独立提供安全层命令执行能力。`SandboxResult` 与 `ExecutionResult` 形状相似（exitCode/stdout/stderr），但 `SandboxResult` 携带额外安全语义字段（timedOut/executionTimeMs），且属于不同模块的不同包——不复用 `ExecutionResult` 以避免跨模块耦合。裁定须写清上述理由。
- [x] **裁定 `setSandboxBackend` 是否调用 `warnIfInsecureDefaults`**（Decision）：**不调用**。理由：`warnIfInsecureDefaults` 的语义是"检测集成商从 secure 默认回退到 insecure 默认"。`NoOpSandboxBackend` 从未被更安全的替代实现取代——它是 Layer 1 设计性基线（设计 §7.1 明确"Noop | 无隔离（默认）"是 Layer 1 无沙箱基线）。与 `AutoApproveGate`（曾被 `DefaultApprovalGate` 取代为 engine 默认 → 回退到 AutoApproveGate 是降级）不同，`NoOpSandboxBackend` 不存在"更安全的已 shipped 替代"，因此 NoOp 不是降级而是起始状态。`setSandboxBackend` setter 不调用 `warnIfInsecureDefaults`，与 `setMemoryStoreProvider` 等不涉及安全降级的 setter 一致。
- [x] 定义 `ISandboxBackend` 接口契约：方法签名接收 `SandboxRequest`，返回 `SandboxResult`。契约须涵盖：同步执行语义（沙箱执行是同步阻塞的——调用方等待命令完成或超时）、fail-closed 保证（执行失败时抛异常而非返回部分结果）。
- [x] 定义 `SandboxRequest` 数据对象：含 command（字符串列表，如 ProcessBuilder 的 List<String>）、workingDirectory（File 或 Path）、environmentVariables（Map<String,String>）、config（SandboxConfig）。
- [x] 定义 `SandboxResult` 数据对象：含 exitCode（int）、stdout（String，截断到配置上限）、stderr（String，截断到配置上限）、executionTimeMs（long）、timedOut（boolean）。
- [x] 定义 `SandboxConfig` 资源限制配置：含 cpuSeconds（默认 30）、memoryMb（默认 1024）、wallSeconds（默认 60）、networkMode（枚举：DENY/ALLOW，默认 DENY）、maxOutputBytes（默认 1MB，截断而非报错）。提供 builder 或静态工厂 `defaults()`。
- [x] 定义 `SandboxException extends NopAiAgentException`：标识沙箱执行失败。区分失败原因（枚举 `SandboxFailureReason`：`DOCKER_UNAVAILABLE` / `CONTAINER_START_FAILED` / `TIMEOUT` / `RESOURCE_LIMIT_EXCEEDED`）。枚举字段存储在 `SandboxException` 上，通过构造器传入。
- [x] 实现 `NoOpSandboxBackend implements ISandboxBackend`：使用 `ProcessBuilder` 在 host 上执行命令。应用 wallSeconds 超时（`Process.waitFor(timeout, unit)`）、maxOutputBytes 输出截断。不应用 CPU/内存/网络隔离（这些需要容器）。超时时销毁进程树（`Process.descendants().forEach(p -> p.destroyForcibly())`）。
- [x] `DefaultAgentEngine` 新增 `private ISandboxBackend sandboxBackend = new NoOpSandboxBackend()` 字段 + `public void setSandboxBackend(ISandboxBackend)` setter。
- [x] `DefaultAgentEngine.resolveExecutor` 透传 `sandboxBackend` 到 `ReActAgentExecutor.Builder.sandboxBackend(...)`。
- [x] `ReActAgentExecutor` 新增 `private final ISandboxBackend sandboxBackend` 字段 + Builder `sandboxBackend()` 方法 + `build()` null 兜底为 `NoOpSandboxBackend`。
- [x] 编写 focused 单元测试 `TestNoOpSandboxBackend`：覆盖正常执行（exit code + stdout + stderr）、wall-time 超时（timedOut=true + 进程销毁）、输出截断（maxOutputBytes 超限后截断）、环境变量传递、工作目录设置、空命令 fail-fast。
- [x] 编写接线测试：验证 `DefaultAgentEngine` 构造的 `ReActAgentExecutor` 持有非 null `sandboxBackend` 引用；验证 setter 注入的自定义 backend 到达 executor。
- [x] 编写 smoke 执行测试：通过测试专用的路径（直接调用 `sandboxBackend.execute(SandboxRequest)`），验证 `NoOpSandboxBackend.execute()` 端到端跑通——从 `SandboxRequest` 构造 → `execute()` 调用 → `SandboxResult` 返回。此测试证明 `ISandboxBackend` 不只是被持有引用的空壳（Anti-Hollow），而是有可执行的运行时行为。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `ISandboxBackend` 接口 + `SandboxRequest`/`SandboxResult`/`SandboxConfig`/`SandboxException` 数据对象存在于 `nop-ai/nop-ai-agent/src/main/java` 中
- [x] `NoOpSandboxBackend` 存在且实现 `ISandboxBackend`，命令在 host 上执行（经 ProcessBuilder）
- [x] `DefaultAgentEngine` 持有 `sandboxBackend` 字段（默认 `NoOpSandboxBackend`）且有 public setter
- [x] **接线验证**（Minimum Rules #23）：`DefaultAgentEngine` 构造的 `ReActAgentExecutor` 持有非 null `sandboxBackend` 引用——测试断言引用链连通（engine → resolveExecutor → Builder → executor field）
- [x] **无静默跳过**（Minimum Rules #24）：`NoOpSandboxBackend` 的所有公共方法有实际执行逻辑（非空方法体），超时/截断路径显式处理
- [x] **新增功能测试覆盖**（Minimum Rules #25）：`TestNoOpSandboxBackend` 至少 6 tests 覆盖正常执行/超时/截断/环境变量/工作目录/空命令 + smoke 执行测试验证 `execute()` 端到端可调用
- [x] nop-ai-shell 关系已裁定并落档（Phase 1 Decision item 已勾选）
- [x] `warnIfInsecureDefaults` 裁定已落档（Phase 1 Decision item 已勾选：不调用）
- [x] shipped 默认行为零回归：`./mvnw test -pl nop-ai/nop-ai-agent -am` 全量通过（NoOp 默认不影响任何既有测试）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - DockerSandboxBackend Functional Implementation

Status: completed
Targets: `DockerSandboxBackend.java`（新文件）；`ReActAgentExecutor.java`（如需调整）

- Item Types: `Proof`

- [x] 裁定 Docker 交互方式：使用 Docker CLI（经 `ProcessBuilder` 调用 `docker run`/`docker rm`）而非引入 Docker Java client 依赖。理由：(1) 不引入额外 Maven 依赖；(2) Docker CLI 是服务器端通用前提；(3) 与 `ProcessBuilder` host 执行模式一致。裁定须写清。
- [x] **裁定 Docker 失败分类检测策略**（Decision）：(1) `docker` 命令不存在或 `Process.start()` 抛 `IOException` → `DOCKER_UNAVAILABLE`；(2) `docker run` 进程 exit code 非零且 stderr 包含 daemon 连接错误模式（如 "Cannot connect to the Docker daemon"）或镜像不存在（"Unable to find image"）→ `CONTAINER_START_FAILED`；(3) `Process.waitFor(wallSeconds, SECONDS)` 返回 false（超时）→ `TIMEOUT`，随后 `docker kill` 强制终止容器；(4) exit code 137（SIGKILL，通常为 OOM-killer 在 `--memory` 限制下触发）→ `RESOURCE_LIMIT_EXCEEDED`；exit code 124（`timeout` 命令的 SIGTERM 信号）→ `TIMEOUT`。无法明确分类的非零 exit code → `CONTAINER_START_FAILED`（保守 fail-closed）。裁定须写清每个映射。
- [x] 实现 `DockerSandboxBackend implements ISandboxBackend`：构造器接收 Docker 镜像名 + 可选 `SandboxConfig` 覆盖默认值。
- [x] Docker 容器创建逻辑：构建 `docker run` 命令，包含资源限制 flags（`--cpus`、`--memory`、`--network none` 当 networkMode=DENY）、工作目录映射（`-v hostWorkDir:containerWorkDir`）、环境变量传递（`-e KEY=VALUE`）、自动删除（`--rm`）、超时由 ProcessBuilder.waitFor(wallSeconds) 控制。
- [x] 结果捕获与解析：读取容器 stdout/stderr（经 ProcessBuilder.redirectOutput 或 `docker run` 的标准输出流），解析 exit code（`docker inspect` 或 `Process.exitValue()`），截断输出到 maxOutputBytes。
- [x] fail-closed 错误处理：Docker 不可用（`docker` 命令不存在或 daemon 未运行）→ 抛 `SandboxException(DOCKER_UNAVAILABLE)`；容器启动失败（镜像不存在、权限拒绝）→ 抛 `SandboxException(CONTAINER_START_FAILED)`；超时 → 销毁容器 + 抛 `SandboxException(TIMEOUT)`。**绝不回退到 host 执行**。
- [x] 进程/容器清理：无论成功或失败，确保容器被清理（`--rm` flag 保证容器退出后自动删除；超时时 `docker kill` 强制终止 + 等待容器退出 + `docker rm -f` 兜底）。
- [x] 编写 focused 单元测试 `TestDockerSandboxBackend`：
  - Docker 命令构建测试（不依赖 Docker daemon）：验证生成的 `docker run` 命令包含正确的 flags（--cpus/--memory/--network/-v/-e/--rm）、镜像名、工作目录映射、环境变量
  - 结果解析测试：验证 stdout/stderr/exitCode 解析逻辑、输出截断逻辑
  - fail-closed 错误处理测试：验证 Docker 不可用时抛 `SandboxException`（mock ProcessBuilder 抛 IOException）、超时时容器被 kill
  - 资源限制映射测试：验证 SandboxConfig 的每个字段正确映射到 Docker flags
- [x] 编写条件性集成测试（`@EnabledIfEnvironmentVariable` 或 `Assumptions.assumeTrue` 检测 Docker 可用性）：当 Docker daemon 可用时，执行真实容器命令（如 `echo hello` 在 alpine 镜像中）并验证结果；Docker 不可用时跳过（不失败）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `DockerSandboxBackend` 存在且实现 `ISandboxBackend`，在 `nop-ai/nop-ai-agent/src/main/java` 中
- [x] Docker 命令构建逻辑覆盖全部 SandboxConfig 字段（CPU/内存/wall-time/网络/输出限制）
- [x] **无静默跳过**（Minimum Rules #24）：Docker 不可用/容器启动失败时抛 `SandboxException`，绝不回退到 host 执行（无 catch-and-fallback 模式）
- [x] Docker 失败分类检测策略已裁定并落档（Phase 2 Decision item 已勾选：exit code 137→RESOURCE_LIMIT_EXCEEDED / 124→TIMEOUT / waitFor false→TIMEOUT / IOException→DOCKER_UNAVAILABLE 等）
- [x] **新增功能测试覆盖**（Minimum Rules #25）：`TestDockerSandboxBackend` 至少 6 tests 覆盖命令构建/结果解析/fail-closed/资源映射/清理/条件性集成
- [x] 容器清理保证：测试验证容器在成功/失败/超时场景下均被清理（`--rm` + `docker kill` + `docker rm -f` 兜底）
- [x] shipped 默认行为零回归：`NoOpSandboxBackend` 仍为 engine 默认，DockerSandboxBackend 是 opt-in（`engine.setSandboxBackend(new DockerSandboxBackend("alpine"))`），全量测试通过
- [x] 设计文档 §7.1 已同步：如实现与设计描述有差异（如 Docker CLI 交互方式），已在设计文档中更新
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `ISandboxBackend` 接口 + `NoOpSandboxBackend` shipped 默认 + `DockerSandboxBackend` 功能实现全部存在于 `nop-ai/nop-ai-agent/src/main/java`
- [x] `SandboxRequest`/`SandboxResult`/`SandboxConfig`/`SandboxException` 数据对象已定义
- [x] `DefaultAgentEngine` 通过 field + setter + resolveExecutor 装配 sandboxBackend，模式与现有安全组件一致
- [x] **接线验证 PASS**：engine → resolveExecutor → Builder → executor field → sandboxBackend 引用链连通（Minimum Rules #23）
- [x] **Anti-Hollow Check**：NoOpSandboxBackend 有真实 ProcessBuilder 执行逻辑（非空方法体）；DockerSandboxBackend 有真实 Docker CLI 交互逻辑（非 stub）；`execute()` 方法端到端可调用并返回 `SandboxResult`（smoke 测试验证）
- [x] **基础设施-only 交付裁定**：本计划交付 `ISandboxBackend` 契约 + 实现但无运行时消费者（无 shell-exec/code-exec `IToolExecutor`）。这是 roadmap L4-7 的字面要求（"ISandboxBackend DockerSandboxBackend"）。Anti-Hollow 证据 = smoke 测试直接调用 `execute()` 验证可执行行为 + 接线验证引用链连通。消费侧 Anti-Hollow（从工具分发到 sandbox 执行的完整路径）是 successor plan（Shell/Code IToolExecutor）的 closure 前置条件。
- [x] **无静默跳过 PASS**：fail-closed 路径全部抛异常（DockerSandboxBackend 绝不 catch-and-fallback 到 host 执行）；NoOpSandboxBackend 超时/截断路径显式处理
- [x] **新增功能测试覆盖 PASS**：TestNoOpSandboxBackend（≥6 tests）+ TestDockerSandboxBackend（≥6 tests）覆盖全部新行为
- [x] shipped 默认行为零回归：`./mvnw test -pl nop-ai/nop-ai-agent -am` 全量通过
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope 项
- [x] roadmap §4 L4-7 从 ❌ → ✅ 并标注本 plan
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] 独立子 agent closure-audit 已完成并记录证据

## Deferred But Adjudicated

（暂无；Shell/Code IToolExecutor、ISensitivePathProvider、XDSL 配置化、非 Docker 后端、Docker 镜像管理、容器池化均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **Shell/Code `IToolExecutor` 消费 `ISandboxBackend`**：创建具体的 shell-exec / code-exec 工具执行器，在执行前通过 engine 的 sandboxBackend 执行命令。本计划交付的 ISandboxBackend 基础设施是此类工具执行器的直接前置依赖。Classification: successor plan required。
- **`ISensitivePathProvider`（设计 §7.2）**：外部化敏感路径 denylist 支持 Delta 覆盖。当前 `DefaultPathAccessChecker` 内置 denylist 满足 Layer 1 需求。Classification: successor plan required。
- **XDSL 配置化**：`agent.xdef` 增加 `<sandbox backend="docker" image="..." cpuSeconds="30" .../>` 元素。Classification: optimization candidate。
- **非 Docker 沙箱后端**：Bubblewrap（Linux）/ Seatbelt（macOS）/ gVisor / kata-containers。Classification: successor plan required。
- **容器池化/复用**：频繁命令执行时复用容器减少启动开销。Classification: optimization candidate。
- **`warnIfInsecureDefaults` 扩展**：`NoOpSandboxBackend` 是 Layer 1 设计性基线（无沙箱隔离是设计 §7.1 的起始状态），不存在"更安全的已 shipped 替代"因此不是安全降级。与 `AutoApproveGate`（曾被 `DefaultApprovalGate` 取代 → 回退是降级）不同，NoOp 从未被取代。`setSandboxBackend` 不调用 `warnIfInsecureDefaults`。如后续创建了 `DefaultSandboxBackend`（host 级安全增强，如 seccomp/chroot）并切换为 engine 默认，则回退到 NoOp 将成为降级，届时再扩展 WARN。Classification: watch-only residual。

## Closure

Status Note: Plan 219 关闭——Layer 4 defense-in-depth 链最后一环（ISandboxBackend 沙箱后端）已落地。Phase 1 交付契约表面 + NoOp shipped 默认 + engine/executor 接线；Phase 2 交付 DockerSandboxBackend 功能实现（Docker CLI 交互，不引入 docker-java 依赖，fail-closed 保证）。Anti-Hollow 证据：smoke 测试直接调用 execute() 返回 populated SandboxResult + 接线测试断言 engine→resolveExecutor→Builder→field 引用链连通 + reader 线程非 stub（解决 inline-read 死锁 bug）+ classifyFailure 非空映射表 + execute() 绝不 catch-and-fallback 到 host。本计划为基础设施-only 交付（roadmap L4-7 字面要求"ISandboxBackend DockerSandboxBackend"），无运行时消费者；消费侧 successor plan（Shell/Code IToolExecutor）是独立后续。
Completed: 2026-06-16

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit subagent（fresh session `ses_12fe15b78ffeAEqsEi5BBZz0YF`，explore 类型，非实现者 session）
- Audit Session: ses_12fe15b78ffeAEqsEi5BBZz0YF
- Evidence:
  - **Phase 1 (1-6) PASS**:
    - (1) 6 契约类型存在：`ISandboxBackend.java:72-89` 接口 / `SandboxRequest.java:28-50` 不可变 / `SandboxResult.java:26-41` 不可变 / `SandboxConfig.java:31-74` defaults 匹配设计表 / `SandboxException.java:21-38` extends NopAiAgentException / `SandboxFailureReason.java:14-50` 4 值枚举 / `NoOpSandboxBackend.java:63` implements ISandboxBackend
    - (2) `DefaultAgentEngine.java:267-268` field + `:1100-1104` setter + `:1111-1113` getter
    - (3) `DefaultAgentEngine.java:2039` resolveExecutor 透传 `.sandboxBackend(this.sandboxBackend)`
    - (4) `ReActAgentExecutor.java:293` field + `:839-842` Builder 方法 + `:891-893` build() null fallback + `:906-908` getSandboxBackend() public accessor
    - (5) **Anti-Hollow PASS**：NoOpSandboxBackend.execute() 有真实 ProcessBuilder 逻辑（`:75-95`）+ 专用 reader 线程（`:105-108`，避免 inline-read 死锁）+ killTree（`:219-238` descendants + root destroyForcibly）
    - (6) setSandboxBackend 不调用 warnIfInsecureDefaults（setter body `:1100-1104` 只赋值，7 处 warnIfInsecureDefaults 调用点 :341/:613/:637/:696/:725/:756/:790 不含 :1100）
  - **Phase 2 (7-11) PASS**：
    - (7) `DockerSandboxBackend.java:96` implements ISandboxBackend + 2-arg ctor `:119-126` + 1-arg 便利 ctor `:129-131`
    - (8) `buildDockerCommand :222-257` 构造 `docker run --rm --name --cpus --memory [--network none] [-v host:/workspace] [--workdir /workspace] [-e K=V]... <image> <cmd>`；pom.xml 无 docker-java / com.github.docker-java 依赖
    - (9) `classifyFailure :264-299` 映射表完整：exit 0→null / daemon→DOCKER_UNAVAILABLE / image-missing→CONTAINER_START_FAILED / 137→RESOURCE_LIMIT_EXCEEDED / 124→TIMEOUT / 其余非零→CONTAINER_START_FAILED
    - (10) **Fail-closed PASS**：execute() IOException→SandboxException(DOCKER_UNAVAILABLE)（`:156-162`）/ wall-time→killContainer+throw(TIMEOUT)（`:181-189`）/ classify 非 null→throw(reason)（`:197-204`）；无 catch-and-fallback-to-host
    - (11) 容器清理：`--rm` 无条件 emitted（`:228`）/ 超时 killContainer 调 docker kill+rm -f（`:327-338`）/ container name UUID 生成（`:148`）防并发碰撞
  - **Tests (12-14) PASS**：
    - (12) `TestNoOpSandboxBackend.java` 13 tests：executesCommandAndCapturesExitCodeAndStdout + capturesNonZeroExitCode + wallTimeTimeoutKillsRunawayProcess + outputIsTruncatedToMaxBytes + largeOutputIsTruncatedWithoutDeadlock + environmentVariablesArePropagatedToChild + workingDirectoryIsHonoured + emptyCommandFailsFast + nullCommandFailsFast + nullConfigFailsFast + sandboxConfigRejectsNonPositiveLimits + defaultsMatchDesignTable + launchFailureRaisesSandboxException
    - (13) `TestSandboxWiring.java` 4 tests：shippedDefaultExecutorHoldsNoOpSandboxBackend（接线）+ customBackendInjectedViaEngineReachesExecutor（identity 注入）+ setSandboxBackendNullFallsBackToNoOp + smokeExecuteReturnsPopulatedResult（Anti-Hollow smoke）
    - (14) `TestDockerSandboxBackend.java` 15 tests：6 命令构建 + 6 失败分类 + missingDockerBinarySurfacesAsUnavailableException（fail-closed）+ cleanupGuaranteesRmFlagAndName + conditionalIntegrationRunsEchoInAlpineWhenDockerAvailable（条件性集成）
  - **Closure Gates (15-17) PASS**：
    - (15) roadmap §4 L4-7 ✅（`nop-ai-agent-roadmap.md:246`）
    - (16) 设计 §7.1 实现约定段落（`nop-ai-agent-security-and-permissions.md:602-609`）
    - (17) Plan Status 由 active → completed（本 commit）
  - **Anti-Hollow 检查结果**：无空壳实现 / 无静默 no-op / 无 catch-and-swallow；reader 线程 + killTree + classifyFailure + buildDockerCommand 均为真实逻辑；DockerSandboxBackend.runShortCommand 的 empty drain loop + killContainer 的 best-effort catch 均为有目的的实现（防 pipe-full 死锁 + 不掩盖原 timeout 异常），非静默跳过
  - **测试运行**：`./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → BUILD SUCCESS，零回归（32 新测试 + 既有全过）
  - **Deferred 项分类检查**：所有 Non-Goals（Shell/Code IToolExecutor / ISensitivePathProvider / XDSL 配置化 / 非 Docker 后端 / Docker 镜像管理 / 容器池化 / warnIfInsecureDefaults 扩展）均显式标注为 successor plan 或 optimization candidate 或 watch-only residual，无 in-scope live defect 被降级

Follow-up:

- Shell/Code `IToolExecutor` 消费 `ISandboxBackend`（successor plan required — 消费侧 Anti-Hollow closure 前置）
- `ISensitivePathProvider` §7.2（successor plan required）
- XDSL `<sandbox backend="docker" image="..."/>` 配置化（optimization candidate）
- 非 Docker 沙箱后端 Bubblewrap/Seatbelt/gVisor/kata（successor plan required）
- Docker 镜像构建/管理生命周期（out-of-scope improvement）
- 容器池化/复用（optimization candidate）
- `warnIfInsecureDefaults` 扩展（watch-only residual：若未来 DefaultSandboxBackend ship 并切换为 engine 默认，回退到 NoOp 才成为降级）
