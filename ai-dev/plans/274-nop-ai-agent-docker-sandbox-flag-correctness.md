# 274 nop-ai-agent——DockerSandboxBackend 标志语义修正与环境变量注入防护（AUDIT-13-8 + AUDIT-13-9）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: AUDIT-13-8 + AUDIT-13-9（dim-13 安全审计，bundled）
> Last Reviewed: 2026-06-20
> Source: deep audit 2026-06-19 dimension 13（`ai-dev/audits/2026-06-19-1355-deep-audit-nop-ai-agent/13-security-permission.md` 维度13-8 / 维度13-9）；roadmap §5c line 358（AUDIT-13-8 ⏸ deferred）
> Related: 270-nop-ai-agent-security-hardening（同 dim-13 安全硬化前序，已落地 `validateHostPath` + `allowedBaseDirs`），219（DockerSandboxBackend 引入）

## Purpose

收口 dim-13 安全审计中剩余的两项 `DockerSandboxBackend.buildDockerCommand` 标志构建缺陷，使该方法的 Docker 标志映射语义正确、并对 LLM 可控的环境变量键做 fail-closed 校验。两项缺陷位于同一方法、同一文件、同一安全面（Docker 标志构建正确性 + 注入硬化），符合 bundle 条件，合并为一个 plan。

## Bundled Items

- **AUDIT-13-8（P2）**：`DockerSandboxBackend` 把 `SandboxConfig.cpuSeconds`（CPU *时间预算*，默认 30）错误映射到 Docker `--cpus`（CPU *核心数* 配额）。默认 `--cpus 30` = "30 个 CPU 核心"，绝大多数宿主机无此核心数，cgroup 限额无意义 → 资源耗尽型 DoS（如 `while true; do :; done`）不被有效限制。来源：`13-security-permission.md:190`；代码 `DockerSandboxBackend.java:356-357` + `SandboxConfig.java`。roadmap §5c line 358 标记 ⏸ deferred（"归入 reliability plan"），但 plan 271/273 仅覆盖 dim-14，从未接管 → 真正未处理项。
- **AUDIT-13-9（P3）**：`DockerSandboxBackend.buildDockerCommand`（`DockerSandboxBackend.java:372-376`）将环境变量键/值拼接为 `-e KEY=VALUE`，未对键做 `^[A-Za-z_][A-Za-z0-9_]*$` 校验 → 跨 Docker 版本/CLI backend（Podman 兼容层等）的参数注入不确定性。键若由 LLM 影响则可被攻击者控制。来源：`13-security-permission.md:216`（复核状态：未复核，未进 roadmap §5b/c）。

> **Granularity note（bundle justification）**：两项单独均 < ~100 行生产代码（13-8 ≈ SandboxConfig 语义改名 + 标志映射 + Javadoc ≈ 25 行；13-9 ≈ 键校验 + fail-closed reason + Javadoc ≈ 20 行），均不满足独立 plan 门槛。二者同方法/同文件/同修复范式（Docker 标志构建语义正确性 + 注入硬化），是 ROADMAP 预分组的 bundle-eligible sibling。合并后生产代码 ≈ 45–55 行 + 校验/异常/Javadoc + 测试 ≈ 35–45 行，总 churn ≈ 90–110 行，达到 bundle 门槛。dim-13 同一修复面（`buildDockerCommand`）内无其他未处理项可继续并入。

## Current Baseline

- `DockerSandboxBackend.java:356-357`：资源限制块 `cmd.add("--cpus"); cmd.add(Integer.toString(config.getCpuSeconds()));` —— 把时间预算当核心数传给 `--cpus`。
- `SandboxConfig.java`：`DEFAULT_CPU_SECONDS = 30`（`:36`）、字段 `cpuSeconds`（`:50`）、构造校验 `cpuSeconds <= 0`（`:62-64`）、`getCpuSeconds()`（`:85-87`）、Builder 默认 `cpuSeconds = DEFAULT_CPU_SECONDS`（`:106`）、`Builder.cpuSeconds(int)`（`:112-114`）。类型为 `int`，命名/默认值/语义均为"秒级 CPU 时间预算"。
- `SandboxConfig.java:7,76` Javadoc 与 `DockerSandboxBackend.java:69-84`（"Resource limits mapping"块，`:71` 显式写 `getCpuSeconds() → --cpus=<n>`）均按错误语义文档化。
- `NoOpSandboxBackend.java:36` Javadoc 引用 `getCpuSeconds()`（host backend 不强制 CPU 限制，仅记录于 config）。
- `DockerSandboxBackend.java:372-376`：环境变量叠加块 `for (...) { cmd.add("-e"); cmd.add(e.getKey() + "=" + e.getValue()); }`，无键校验。模块内全量扫描确认**无任何** env-key 校验代码。
- `SandboxRequest.java:32,45-47,63-65`：`environmentVariables` 为 `Map<String,String>`，`Map.copyOf` 防御性拷贝，键/值无字符约束。
- `SandboxFailureReason.java`：现有 5 个枚举值（DOCKER_UNAVAILABLE / CONTAINER_START_FAILED / TIMEOUT / RESOURCE_LIMIT_EXCEEDED / HOST_PATH_NOT_ALLOWED）。`HOST_PATH_NOT_ALLOWED`（plan 270）是"请求在 Docker 调用前被拒绝"的先例。
- 设计 `nop-ai-agent-security-and-permissions.md:591-598` 资源限制表列出 `cpuSeconds | 30`。
- roadmap `nop-ai-agent-roadmap.md:358`：AUDIT-13-8 = ⏸ deferred。
- 现有测试引用旧 API：`TestDockerSandboxBackend.java:59`（`.cpuSeconds(2)`）、`:80`（`assertEquals("2", nextOf(cmd, "--cpus"))`）、`:430`（`.cpuSeconds(1)`）；`TestNoOpSandboxBackend.java:78`（`.cpuSeconds(30)`）、`:246-247`（`cpuSeconds(-1)` 校验测试）、`:259`（`assertEquals(30, d.getCpuSeconds(), "design §7.1 default cpuSeconds=30")`）。
- **真正的 gap**：(1) `--cpus` 语义错配，资源限制形同虚设；(2) env 键无注入防护。二者均为已确认 live defect，非优化项。

## Goals

- `DockerSandboxBackend` 传给 `--cpus` 的值在语义上表示 **CPU 核心数配额**（Docker `--cpus` 语义，支持小数如 0.5 / 1.0 / 2.5），默认 **1 个核心**，而非"CPU 秒数预算"。
- `SandboxConfig` 中控制 `--cpus` 的公共契约（常量 / 字段 / getter / Builder 方法 / 默认值 / 构造校验）统一反映核心数语义；旧的 `cpuSeconds` / `getCpuSeconds` / `DEFAULT_CPU_SECONDS` 命名与"秒"语义从公共面消失。
- `buildDockerCommand` 在拼接 `-e KEY=VALUE` 前，对每个环境变量键做 POSIX 环境变量名校验；非法键在 Docker 调用**之前**被 fail-closed 拒绝（抛 `SandboxException` 并带可观测 reason，沿用 `HOST_PATH_NOT_ALLOWED` 先例）。
- 受影响 owner docs（设计 §7.1 表 + `DockerSandboxBackend` Javadoc）与 roadmap §5c 状态同步到 live baseline。

## Non-Goals

- 不新增 CPU 时间预算（真正的"总 CPU 秒数"限额）字段——Docker 无对应标志，且 `wallSeconds` 已提供总时限；本计划只纠正现有 `--cpus` 的语义错配，不引入新维度。
- 不改用 `--cpu-quota` + `--cpu-period` 方案：保留 `--cpus`（Docker 1.13+ 标准），仅纠正名称/类型/默认值错配，最小惊讶。
- 不对环境变量**值**做字符校验：值是任意字符串，作为单个 argv 元素交给 Docker，`=` 切分后值可含任意字符；仅键需校验（参数注入面）。
- 不处理 AUDIT-13-1（symlink，已 plan 270 修复）/13-7（hostPath，已 plan 270 修复）等其他 dim-13 项。
- 不改变 `NoOpSandboxBackend` 的强制行为（host backend 仍只强制 wallSeconds/maxOutputBytes，CPU 限制仅记录于 config）。
- 不外部化 `--cpus`/env 校验到 XDSL 配置（保持硬编码默认，外部化是独立 successor）。

## Scope

### In Scope

- `SandboxConfig`：CPU 限制字段从"秒级时间预算（int，默认 30）"改为"核心数配额（支持小数，默认 1.0）"——常量/字段/getter/Builder 方法/默认值/构造校验全部改名并调整类型与默认值。
- `DockerSandboxBackend.buildDockerCommand`：`--cpus` 取值改为核心数；新增环境变量键的 POSIX 名校验，非法键 fail-closed 拒绝。
- `DockerSandboxBackend` "Resource limits mapping" Javadoc 块（`:69-84`）与环境变量叠加说明同步到正确语义。
- `NoOpSandboxBackend.java:36` Javadoc 引用同步。
- 新增一个 `SandboxFailureReason` 值（与 `HOST_PATH_NOT_ALLOWED` 并列，表达"请求在调用 Docker 前被拒绝"），用于非法 env 键拒绝。
- 受影响测试（`TestDockerSandboxBackend` / `TestNoOpSandboxBackend`）迁移到新 CPU 契约 + 新增 env 键校验 focused 测试。
- 设计 `nop-ai-agent-security-and-permissions.md` §7.1 资源限制表 + roadmap §5c line 358 状态同步。

### Out Of Scope

- `SandboxRequest.environmentVariables` 的来源链路（shell-exec 等工具执行器如何构造 env）——独立 successor；本计划只在消费侧（`buildDockerCommand`）做防御性校验。
- CPU/memory/wall 限额的外部化配置（XDSL）。
- 其他 dim-13 发现（13-1/13-2/13-3/13-4/13-5/13-6/13-10~13-20）。

## Execution Plan

### Phase 1 - AUDIT-13-8：CPU 限制语义修正（核心数配额）

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/SandboxConfig.java`、`DockerSandboxBackend.java`、`NoOpSandboxBackend.java`、对应 `src/test/.../security/Test*.java`、`ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md`

- Item Types: `Fix`

- [x] 将 `SandboxConfig` 控制 `--cpus` 的公共契约改为 CPU 核心数配额语义：常量、字段、getter、Builder 方法统一改名（如 `cpuCores` / `getCpuCores()` / `DEFAULT_CPU_CORES` / `Builder.cpuCores(...)`），类型支持小数（以忠实表达 Docker `--cpus` 的分数核心语义），默认值改为 **1.0 核心**，构造校验改为 `> 0`（核心数必须为正）。
- [x] `DockerSandboxBackend.buildDockerCommand` 的 `--cpus` 取值改为读核心数 getter（值原样传给 `--cpus`，如 `1.0` / `1.5`）。
- [x] 同步 `DockerSandboxBackend` "Resource limits mapping" Javadoc 块（`--cpus` 映射行）与 `NoOpSandboxBackend` Javadoc 引用为新 getter。
- [x] 更新设计 `nop-ai-agent-security-and-permissions.md` §7.1 资源限制表：CPU 行从"秒级预算默认 30"改为"核心数配额默认 1.0"，并补一句实现约定（`--cpus` = 分数核心，Docker 1.13+ 语义）。
- [x] 迁移 `TestDockerSandboxBackend` / `TestNoOpSandboxBackend` 到新 CPU 契约：把 `.cpuSeconds(...)` / `getCpuSeconds()` / `DEFAULT_CPU_SECONDS` 断言改为新 API；默认值断言改为 1.0；`--cpus` 值断言改为小数核心数（如 `1.5`）。
- [x] 新增/调整 focused 测试：明确断言 `--cpus` 收到的是核心数（含小数用例，如 `.cpuCores(1.5)` → `nextOf(cmd,"--cpus")` 为 `"1.5"`），验证默认 config 产出 `--cpus 1.0`。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 仓库内 `cpuSeconds` / `getCpuSeconds` / `DEFAULT_CPU_SECONDS` 标识符在 `nop-ai-agent` 模块 main+test 中 0 命中（grep 验证）；新核心数 API 存在且默认 1.0。
- [x] `DockerSandboxBackend.buildDockerCommand` 对默认 config 产出 `--cpus` 值为单核心（如 `"1.0"`），对小数 config（如 1.5）产出 `"1.5"`——focused 测试断言通过。
- [x] 设计 §7.1 资源限制表 CPU 行已改为核心数语义、默认 1.0；`DockerSandboxBackend`/`NoOpSandboxBackend` Javadoc 引用与新 getter 一致。
- [x] **接线验证**：`DockerSandboxBackend.execute` → `buildDockerCommand` → `--cpus` 路径仍读取同一个 config 字段（改名后无悬空引用），编译通过即证明调用链连通。
- [x] **无静默跳过**：核心数 `<= 0` 在构造期 fail-closed 抛异常（非静默默认）——focused 测试覆盖。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - AUDIT-13-9：环境变量键注入防护（fail-closed 校验）

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/DockerSandboxBackend.java`、`SandboxFailureReason.java`、`src/test/.../security/TestDockerSandboxBackend.java`、`ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md`

- Item Types: `Fix`

- [x] 在 `buildDockerCommand` 拼接 `-e KEY=VALUE` 前，对每个环境变量键做 POSIX 环境变量名校验（`^[A-Za-z_][A-Za-z0-9_]*$`）；非法键（如以 `-` 开头、以数字开头、含空格/控制字符/`=`）在调用 Docker **之前**被拒绝。
- [x] 非法键拒绝抛 `SandboxException` 并携带一个**新增的、专用的** `SandboxFailureReason` 值（与 `HOST_PATH_NOT_ALLOWED` 并列，语义="请求在 Docker 调用前因校验失败被拒绝"）；沿用 plan 270 `validateHostPath` 的 fail-closed 先例。
- [x] `SandboxFailureReason` 新增值的 Javadoc 按现有 5 个值的风格补全（说明触发条件 + fail-closed 语义）。
- [x] 同步 `DockerSandboxBackend` Javadoc（环境变量叠加说明 + 失败分类表如有必要）。
- [x] 新增 focused 测试（无需 Docker 守护进程，经 `buildDockerCommand` 或专用校验入口）：合法键（`FOO`、`_BAR`、`BAZ123`）通过；非法键（`--privileged`、`1ABC`、`A B`、`A=B`、空串）被 fail-closed 拒绝，异常 reason 为新增值。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `buildDockerCommand` 对含非法键的 `SandboxRequest` 抛 `SandboxException`（reason = 新增值），且异常在 `docker` 进程启动之前发生（focused 测试通过、无需 Docker）。
- [x] 合法键仍正常产出 `-e KEY=VALUE`（回归现有 `buildCommandPropagatesEnvironmentVariables` 测试语义）。
- [x] 新增 `SandboxFailureReason` 值存在且有 Javadoc；`SandboxException.getReason()` 在 focused 测试中被断言。
- [x] **接线验证**：`DockerSandboxBackend.execute` → `buildDockerCommand` 的 env 拼接路径确实经过新校验（非法键在 `pb.start()` 之前抛出）——focused 测试通过即证明。
- [x] **无静默跳过**：非法键走抛异常路径，不 `continue` 跳过、不静默忽略——focused 测试覆盖至少一个非法键用例。
- [x] owner-doc 裁定已记录（`DockerSandboxBackend` Javadoc 同步；若新增枚举值算公共契约变化，在设计 §7.1 补一句注记，或明确写 `No owner-doc update required beyond Javadoc`）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] AUDIT-13-8 live defect 已修复（`--cpus` 语义为核心数、默认 1.0）
- [x] AUDIT-13-9 live defect 已修复（env 键 fail-closed 校验）
- [x] `cpuSeconds`/`getCpuSeconds`/`DEFAULT_CPU_SECONDS` 旧标识符在模块内 0 命中
- [x] 新增 `SandboxFailureReason` 值 + Javadoc 已落地，非法键拒绝经 focused 测试验证
- [x] 受影响 owner docs（设计 §7.1 表 + `DockerSandboxBackend`/`NoOpSandboxBackend` Javadoc）与 roadmap §5c line 358（AUDIT-13-8 ⏸→✅）已同步
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证 `execute` → `buildDockerCommand` → `--cpus` 与 env 校验两条路径在运行时确实连通，无空方法体/静默跳过
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（无；两项 in-scope 缺陷均在本计划内修复，不留 deferred。）

## Non-Blocking Follow-ups

- env 键来源链路（shell-exec 等工具执行器如何构造 `SandboxRequest.environmentVariables`、键是否真的 LLM 可控）的追溯与文档化——独立 successor，不影响本计划 fail-closed 校验的正确性（防御性校验在消费侧成立）。
- CPU/memory/wall 限额外部化到 XDSL 配置——optimization candidate，不影响当前 baseline。
- `scan-hollow-implementations.mjs --module nop-ai/nop-ai-agent --severity high` 在模块内有 20 个 high 发现（exit=1），但**全部**位于与本计划无关的既有文件（`DefaultAgentEngine`/`IAgentEngine`/`NoOpHookRegistry`/`IAiMemoryStore`/`ISessionStore`/`NoOpFencingTokenService`/`AlwaysClosed`/`NoOpGoalTracker`/`NoOpSustainer`/`InMemoryTeamTaskStore`），本计划触碰的 4 个文件（`SandboxConfig`/`DockerSandboxBackend`/`SandboxFailureReason`/`NoOpSandboxBackend`）0 发现。这些是 pre-existing NoOp 默认实现 / 接口可选方法，predates 本计划，需独立 plan 治理——watch-only residual，不影响本计划 Anti-Hollow 结论。

## Closure

Status Note: 两项 dim-13 in-scope live defect（AUDIT-13-8 `--cpus` 语义错配 + AUDIT-13-9 env 键注入）均已在本计划内修复并经独立 closure audit 验证。无 deferred in-scope defect，无静默跳过，调用链端到端连通。Phase 1+2 全部 Exit Criteria + Closure Gates 已勾选。
Completed: 2026-06-20

Closure Audit Evidence:

- Reviewer / Agent: independent closure-audit subagent (general), session `ses_11efa95ffffesiSbL2sOzEmy5f`（fresh session，非实现阶段 session）
- Audit Session: `ses_11efa95ffffesiSbL2sOzEmy5f`
- Evidence:
  - **A. Old API absence（Phase 1 EC1）— PASS**：grep `cpuSeconds|getCpuSeconds|DEFAULT_CPU_SECONDS` on `nop-ai-agent/src/` = 0 hits；新 API 存在（`SandboxConfig.java:42` `DEFAULT_CPU_CORES=1.0`、`:56` `double cpuCores`、`:98` `getCpuCores()`、`:125` `Builder.cpuCores(double)`）。
  - **B. CPU semantics（Phase 1 EC2/3/5）— PASS**：默认 1.0（`:42`）；构造校验 `!(cpuCores > 0)`（`:70`）拒绝 0/负/NaN；`DockerSandboxBackend.java:412-413` `--cpus` 取 `Double.toString(config.getCpuCores())`；Javadoc 引用 `getCpuCores()`（`DockerSandboxBackend.java:72`、`NoOpSandboxBackend.java:36`）。
  - **C. CPU focused tests（Phase 1 EC2）— PASS**：`TestDockerSandboxBackend` 断言 `"1.5"`(`:82`)/`"1.0"`(`:143`)/`"0.5"`(`:122`)/`"2.5"`(`:130`)；`TestNoOpSandboxBackend` `defaultsMatchDesignTable` `getCpuCores()==1.0`(`:265`) + `cpuCores(0/NaN/-1)` fail-closed(`:246-252`)。
  - **D. Env key validation（Phase 2 EC1/3）— PASS**：`ENV_KEY_PATTERN`(`DockerSandboxBackend.java:127`)；env loop `validateEnvironmentVariableKey`(`:434`) before `cmd.add("-e")`(`:435`)；invalid → `SandboxException(INVALID_ENVIRONMENT_VARIABLE)`(`:387-392`)；新枚举值 + Javadoc（`SandboxFailureReason.java:61-74`)；校验在 `buildDockerCommand`（`execute():233-234` 调用，`pb.start():239` 之前）。
  - **E. Wiring / Anti-Hollow（Phase 1 EC4, Phase 2 EC4, Closure Gate Anti-Hollow）— PASS**：`execute()→buildDockerCommand()→ProcessBuilder` 调用链连通；无空方法体/无 `continue` 跳过/无吞异常；合法键产出 `-e KEY=VALUE`，非法键抛异常。
  - **F. Env key focused tests（Phase 2 EC1/2/5）— PASS**：合法键 `FOO/_BAR/BAZ123` 通过（`:205-223`)；非法键 `--privileged/1ABC/A B/A=B/空串` 拒绝（`:226-250`)；`ex.getReason()==INVALID_ENVIRONMENT_VARIABLE` 断言(`:246-247`,`:266-267`)。
  - **G. Docs sync — PASS**：设计 §7.1 表 `cpuCores|1.0`（`:595`)+实现注记(`:600`)；roadmap §5c AUDIT-13-8 ✅(`:358`)+ AUDIT-13-9 ✅(`:359`)。
  - **H. Build/test — PASS**：`./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → BUILD SUCCESS（2753 tests, 0 failures）；`-Dtest=TestDockerSandboxBackend,TestNoOpSandboxBackend` → 39 tests 全绿（26+13）。
  - **check-plan-checklist.mjs --strict** 退出码 0（Closure Gates 本 audit 授权勾选前为 warnings-only，符合预期）。
  - **Anti-Hollow / scan-hollow-implementations.mjs**：模块级 exit=1，但 20 个 high 发现**全部**位于无关既有文件（见 Non-Blocking Follow-ups）；本计划触碰的 4 个文件 0 发现（已 grep 验证）。Anti-Hollow 结论成立。
  - **Deferred 项分类检查**：无 in-scope live defect 被降级；两项 follow-up 均为 optimization candidate / watch-only residual。

Follow-up:

- env 键来源链路追溯（independent successor，不影响本计划消费侧校验正确性）。
- CPU/memory/wall 限额 XDSL 外部化（optimization candidate）。
- 模块级 pre-existing hollow 发现（20 个，无关文件）需独立 plan 治理（watch-only residual）。

## Follow-up handled by 276-nop-ai-agent-carryover-batch-r2-sandbox-threatmodel-roadmap-reconcile-team-members.md

- **env 键来源链路追溯**（本文件 §Non-Blocking Follow-ups 第 1 条）：已由 `276-nop-ai-agent-carryover-batch-r2-sandbox-threatmodel-roadmap-reconcile-team-members.md` Phase 1 收口。追溯结论：main src 0 生产构造点 + `ISandboxBackend.execute` 从未被调用 → 键当前非 LLM 可控，本计划 fail-closed POSIX 键校验定位为 defense-in-depth。结论固化于设计 `nop-ai-agent-security-and-permissions.md` §7.1 威胁模型注记 + roadmap §5c env-source 行（标 ✅ 已追溯）。
