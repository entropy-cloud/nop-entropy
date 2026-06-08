# Nop AI Agent 安全与权限设计

**日期**：2026-06-08（v2 — 四层接口组织 + agent-survey 调研融合）
**范围**：`nop-ai-agent` 安全子系统
**状态**：active

---

## 1. 目标

本篇定义 Agent 的安全边界，回答五个问题：

1. Agent 如何限制可调用工具
2. Agent 如何限制可访问文件和路径
3. 子 agent 如何继承且不能提升权限
4. 外部内容如何被隔离以防注入攻击
5. 高风险操作如何被治理（审批、拒绝、暂停）

## 2. 设计原则

安全设计遵循五条原则：

1. **权限默认收敛** — deny by default，显式 allow 才放行
2. **程序校验优先于 prompt 约束** — 安全规则必须可测试、可审计、可复现
3. **子 agent 只能继承或收缩权限，不能提升** — call-agent 是能力扩张入口
4. **渐进式增强** — Layer 1 只做最简 deny/allow，Layer 2 引入分级策略，Layer 3 引入审批治理，Layer 4 引入平台级安全。每层通过 XDSL 模型引入更多假定，内部运行时始终最简化
5. **纵深防御** — 不依赖单一安全层。Tool dispatch、Tool handler、文件系统三层各自独立检查

## 3. 四层接口组织

```
Layer 4: Platform Security (平台安全层)
   ISandboxBackend, ISensitivePathProvider, IAuditLogger
   ─── 依赖 Layer 1-3 ───

Layer 3: Approval Governance (审批治理层)
   IApprovalGate, IDenialLedger, IPostDenialGuard
   ─── 依赖 Layer 1-2 ───

Layer 2: Policy Extensions (策略扩展层)
   ISecurityLevelResolver, IContentGuardrail, IPermissionMatrix
   ─── 依赖 Layer 1 ───

Layer 1: Core Security (核心安全层)
   IPermissionProvider, IToolAccessChecker, IPathAccessChecker
   ─── 无内部依赖 ───
```

**依赖规则**：上层只依赖下层的接口。每层都有 pass-through 默认实现——系统可以只带 Layer 1 运行。

## 4. Layer 1: Core Security（核心安全层）

系统运行的最低安全要求。没有这些接口，Agent 无法安全执行任何工具。

### 4.1 IPermissionProvider — 权限派生

**接口**：3-source merge（opencode 模式）

```
Permission resolve(toolName, agentName, sessionId, channelKind)
```

**三个来源**（按优先级）：

| 来源 | 配置位置 | 说明 |
|------|---------|------|
| Session 级临时规则 | 运行时参数 | 最高优先级 |
| Agent 专属规则 | `agent.xdef` 或 `.permissions/agent-{name}.yml` | Agent DSL 声明 |
| 默认规则 | `.permissions/agent-default.yml` | 全局基线 |

**合并语义**：deny-first（先匹配 deny，再匹配 allow，未命中默认拒绝）。

**默认实现**：`HierarchicalPermissionProvider`（roadmap.md Layer 1 已定义）。

### 4.2 IToolAccessChecker — 工具访问检查

**职责**：每次工具执行前检查 allow/deny。

**规则语义**：

1. 先匹配 deny → 拒绝
2. 再匹配 allow → 放行
3. 未命中 allow → 默认拒绝（fail-closed）

**硬编码 deny（不可被 DSL 覆盖）**：与 OpenSquilla 的 `HARDCODED_ADMIN_ONLY` 模式对齐——shell_exec、file_write、file_delete、git_push 等高风险工具在未显式授权时默认拒绝。

**为什么不能只靠 prompt**：

- 模型可能幻觉工具名
- prompt 不能替代安全边界
- 安全规则必须可测试、可审计、可复现

### 4.3 IPathAccessChecker — 路径访问检查

**职责**：每次文件操作前检查路径权限。

**访问级别**：`read` | `read-write` | `deny`

**路径匹配**：glob 模式，从上到下匹配，第一条命中生效。

**路径规范化**（必须防御）：

- `..` 目录穿越
- 符号链接绕过
- 绝对路径逃逸
- 大小写折叠（Windows）

**系统目录默认保护**：

| 路径 | 默认级别 |
|------|---------|
| `.nop/**` | read-only |
| `.nop/.permissions/**` | deny |
| `.nop/.system/**` | read-only |

**敏感路径默认保护**（参考 OpenSquilla `sensitive_paths.py`）：

| 类别 | 路径 |
|------|------|
| SSH 密钥 | `~/.ssh/**` |
| 云凭证 | `~/.aws/**`, `~/.azure/**`, `~/.config/gcloud/**`, `~/.kube/**` |
| 凭证文件 | `**/.env`, `**/.env.*`, `**/id_rsa`, `**/id_ed25519`, `**/.netrc` |
| Shell 历史 | `**/.bash_history`, `**/.zsh_history` |
| 系统目录 | `/etc/**`, `/boot/**`, `/sys/**`, `/proc/**`, `/root/**` |

**默认实现**：`DefaultPathAccessChecker`——只做 deny/allow + 路径规范化。敏感路径 denylist 可通过 XDSL 外部配置。

### 4.4 Subagent 权限继承

**基本规则**：子 Agent 只能继承父 Agent 的权限，不能提升。

**继承语义**：

- 工具权限 = 父权限 ∩ 子配置（交集或收缩）
- 文件权限 = 父权限 ∩ 子配置（交集或收缩）
- 未明确授权的提升行为一律拒绝

**为什么**：call-agent 是能力扩张入口。如果子 Agent 可以提升权限，任意 prompt 都可能绕过父 Agent 约束。

### 4.5 日志与审计

安全相关执行记录：

- sessionId, agentName, actorId
- toolName, 访问路径
- 权限命中规则
- 拒绝原因

## 5. Layer 2: Policy Extensions（策略扩展层）

扩展安全策略的粒度，不改变核心 deny/allow 语义。所有接口有 pass-through 默认。

### 5.1 ISecurityLevelResolver — 安全等级解析

**职责**：根据 action 种类和上下文 hints 解析安全等级。

**来源**：OpenSquilla sandbox.policy 模块的 `select_level(action_kind, hints)` 确定性规则表（见 `ai-dev/analysis/agent-survey/2026-06-08-opensquilla-analysis.md` §4.3.1）。

**SecurityLevel 分级**：

| 等级 | 语义 | 典型操作 |
|------|------|---------|
| `STANDARD` | 正常执行，无额外限制 | fs.read, fs.list |
| `ELEVATED` | 需要确认，资源收紧 | fs.write, shell.exec (trusted source) |
| `RESTRICTED` | 需要审批，最小权限 | shell.exec (untrusted source), network.fetch |

**LevelHints 输入**（flat dataclass，每个字段是可审计的布尔值）：

| Hint | 含义 |
|------|------|
| `trustedSource` | 内容来源是否可信 |
| `writesOutsideWorkspace` | 是否写工作目录外 |
| `crossesTrustBoundary` | 是否跨信任边界 |
| `needsNetwork` | 是否需要网络访问 |
| `highImpact` | 是否高影响操作 |

**规则表**（确定性，无 AI 决策）：

| action_kind | 默认等级 | 升级条件 |
|-------------|---------|---------|
| `fs.read`, `fs.list`, `fs.grep` | STANDARD | — |
| `fs.write`, `fs.edit`, `patch.apply` | STANDARD | `writesOutsideWorkspace` → ELEVATED |
| `shell.exec`, `code.exec` | STANDARD | `!trustedSource` → ELEVATED; `highImpact` → RESTRICTED |
| `network.fetch`, `web.fetch` | STANDARD | `!trustedSource` → RESTRICTED |
| 其他 | STANDARD | `!trustedSource` → ELEVATED; `highImpact` → RESTRICTED |

**默认实现**：`NoOpSecurityLevelResolver`（所有操作返回 STANDARD，等于不分级）。

**XDSL 配置化**：规则表定义为 `security-policy.xdef` schema，运行时从 DSL 装载。操作员可通过 Delta 覆盖默认规则，不需要改代码。

### 5.2 IContentGuardrail — 内容护栏

**职责**：拦截和分析进入/离开 LLM 的内容。

**来源**：VoltAgent 的 Input/Output Guardrail pipeline + OpenSquilla 的 injection_guard.py。

**方向**：

| 方向 | 拦截点 | 用途 |
|------|--------|------|
| `INPUT` | 用户消息 → LLM 之前 | 检测注入攻击、过滤敏感内容 |
| `OUTPUT` | LLM 输出 → 工具/用户之前 | 检测越权指令、过滤泄露内容 |

**返回值**：`PASS` | `BLOCK(reason)` | `MODIFY(content)`

**预构建 Guardrail**（Layer 2 默认提供，可选启用）：

| Guardrail | 检测内容 | 来源 |
|-----------|---------|------|
| `PromptInjectionGuardrail` | prompt_override / role_hijack / exfiltration / invisible_char 四类威胁 | OpenSquilla `injection_guard.py` 4 类正则 |
| `UntrustedEnvelopeGuardrail` | 将外部内容包裹在 `<untrusted>` 信封中 | OpenSquilla `wrap_untrusted()` |
| `PIIDetectionGuardrail` | 个人信息检测 | VoltAgent 预构建 |
| `ContentLengthGuardrail` | 输出长度限制 | VoltAgent 预构建 |

**Prompt 注入检测**（基于 OpenSquilla 分类法，参考 Simon Willison 分类法、GARAK 基准、Anthropic 红队报告）：

| 威胁类 | 检测目标 |
|--------|---------|
| `prompt_override` | 试图让模型忽略/重置系统指令 |
| `role_hijack` | 伪装为 system/admin/root |
| `exfiltration` | 试图泄露 secrets/API keys/env vars |
| `invisible_char` | 零宽字符/BIDI 控制字符走私 |

**执行模式**：`off` / `report`（仅记录）/ `enforce`（阻止）。

**Tool Call 注入守卫**：在 Tool Dispatch 管线中，如果 Tool Call 的 origin trace 位于 `<untrusted>` 块内，拒绝执行。这是双重防护——即使 LLM 被诱导生成 tool_call，只要 origin 在 untrusted 内容中就会被阻止。

**默认实现**：`NoOpContentGuardrail`（roadmap.md Layer 2 已定义）。

### 5.3 IPermissionMatrix — 通道权限矩阵

**职责**：按通道类型控制允许的工具风险等级。

**来源**：OpenSquilla safety.permission_matrix 模块（见 `ai-dev/analysis/agent-survey/2026-06-08-opensquilla-analysis.md` §4.3.2）。

| 通道类型 | 允许的工具等级 |
|---------|--------------|
| `webui` | STANDARD + ELEVATED + RESTRICTED |
| `api` | STANDARD + ELEVATED |
| `dm` | STANDARD + ELEVATED |
| `group` | STANDARD |
| 未知通道 | STANDARD（fail-closed） |

**Principal 身份模型**：

| 字段 | 含义 |
|------|------|
| `role` | `user` / `operator`（operator 可绕过 RESTRICTED） |
| `channelId` | 用于 per-channel override |
| `tenantId` | 多租户标识（Nop `IContext` 天然支持） |

**默认实现**：`PassThroughPermissionMatrix`（所有通道允许所有等级）。

## 6. Layer 3: Approval Governance（审批治理层）

为生产环境提供人类审批和拒绝治理。所有接口有最简默认。

### 6.1 IApprovalGate — 审批门

**职责**：当 `ISecurityLevelResolver` 返回需要审批的等级时，向人类请求批准。

**来源**：OpenSquilla sandbox.governance 模块的 ApprovalGate（见 `ai-dev/analysis/agent-survey/2026-06-08-opensquilla-analysis.md` §4.4.1）。

**行为**：

1. 如果 policy 不需要审批 → 直接 ALLOW
2. 入队审批请求（namespace 区分 exec/plugin）
3. 等待人类响应（带超时，默认 300s）
4. 超时或拒绝 → DenialResult

**审批通道**：

- Web UI 通知
- GraphQL Subscription
- RPC 轮询

**默认实现**：`AutoApproveGate`（所有请求自动通过——适用于无人值守自动化的 Layer 1 基线）。

### 6.2 IDenialLedger — 拒绝账本

**职责**：per-session 拒绝计数，达到阈值自动暂停 autonomous 执行。

**来源**：OpenSquilla sandbox.governance 模块的 DenialLedger（见 `ai-dev/analysis/agent-survey/2026-06-08-opensquilla-analysis.md` §4.4.2）。

**关键设计**：

| 参数 | 默认值 | 说明 |
|------|-------|------|
| `denialThreshold` | 3 | 累计 N 次拒绝后暂停 |
| `pauseBehavior` | sticky | 暂停后只有人类干预才能恢复 |
| `persistence` | DB | DenialLedger 持久化到数据库（不丢失） |

**Fingerprint**：`action_fingerprint = SHA-256(actionKind + argv + cwd + criticalEnv)[:32]`，用于标识"相同的危险意图"。

**默认实现**：`NoOpDenialLedger`（不计数，不暂停）。

### 6.3 IPostDenialGuard — 拒绝后守卫

**职责**：被拒后，阻止 Agent 盲重试相同操作。

**来源**：OpenSquilla sandbox.governance 模块的 post_denial_guard（见 `ai-dev/analysis/agent-survey/2026-06-08-opensquilla-analysis.md` §4.4.3）。

**合法 follow-up 标签**（只有 3 种）：

| 标签 | 语义 |
|------|------|
| `LOWER_PRIVILEGE` | 降权重试 |
| `EXPLAIN` | 向用户解释限制 |
| `NARROWER_APPROVAL` | 请求更窄范围的审批 |

**无标签的盲重试**（相同 fingerprint）→ REPEATED_SAME_INTENT 拒绝。

**DenialResult 结构化信封**：

```
DenialResult {
  reason: enum         // human_rejected / threshold_exceeded / repeated_same_intent / ...
  suggestedNextStep    // replan / askUser / lowerPrivilege / narrowerApproval
  actionFingerprint    // SHA-256[:32]
  message: String      // 人类可读
  retryable: boolean
}
```

**默认实现**：`PassThroughPostDenialGuard`（不阻止重试）。

## 7. Layer 4: Platform Security（平台安全层）

多租户、分布式、企业级安全。所有接口有单进程默认。

### 7.1 ISandboxBackend — 沙箱后端

**职责**：在隔离环境中执行高风险命令。

**来源**：OpenSquilla `sandbox/backend/`（Bubblewrap/Seatbelt/Noop），但 Nop 部署在服务器端。

**推荐后端**：

| 后端 | 场景 |
|------|------|
| Docker 容器 | 服务器端 shell/code 执行隔离（推荐） |
| ProcessBuilder + 资源限制 | 轻量级隔离 |
| Noop | 无隔离（默认） |

**安全不可降级保证**：沙箱启动失败 → `SandboxBackendError` → 拒绝执行，**绝不回退到 unsandboxed host 执行**。

**资源限制**（可配置）：

| 参数 | 默认 |
|------|------|
| `cpuSeconds` | 30 |
| `memoryMb` | 1024 |
| `wallSeconds` | 60 |
| `network` | deny |

**默认实现**：`NoOpSandboxBackend`（直接在 host 执行——Layer 1 无沙箱）。

### 7.2 ISensitivePathProvider — 敏感路径配置

**职责**：外部化敏感路径 denylist，支持 Delta 覆盖。

**配置来源**：`security-sensitive-paths.xdef` schema → YAML/XML 外部配置。

**特性**：

- 路径前缀匹配 + 文件后缀匹配
- Workspace 感知排除（workspace 内的 `/root` 前缀不阻止合法操作，只阻止凭证叶子文件）
- 命令级扫描（`rm /tmp/ok /etc/bad` → 阻止整个命令）

**默认实现**：`DefaultSensitivePathProvider`（内置 denylist + workspace 排除）。

### 7.3 IAuditLogger — 安全审计日志

**职责**：持久化安全事件到数据库（而非内存 log），支持审计查询。

**记录内容**：每次 gate_decision 的结构化日志（fingerprint, level, decision, reason, sessionId, timestamp）。

**默认实现**：`Slf4jAuditLogger`（写入标准日志——单进程够用）。

## 8. 纵深防御总结

外部内容（Web fetch / Channel / File read）经过的防御层：

```
外部内容
  │
  ├─ Layer 2: IContentGuardrail ──── 注入检测 + wrap_untrusted()
  │                                    │
  │                                    └─ enforce 模式: 替换为 [BLOCKED]
  │
  ├─ Tool Call 产生
  │   │
  │   ├─ Layer 2: Tool Call 注入守卫 ── origin trace 在 untrusted 块内 → 拒绝
  │   │
  │   ├─ Layer 4: ISensitivePathProvider ── 敏感路径 → hard-block
  │   │
  │   ├─ Layer 2: IPermissionMatrix ── 通道 × 等级矩阵
  │   │
  │   ├─ Layer 1: IPermissionProvider ── deny/allow 工具权限
  │   │
  │   ├─ Layer 1: IPathAccessChecker ── deny/allow 路径权限
  │   │
  │   └─ Layer 2: ISecurityLevelResolver ── hints → SecurityLevel
  │       │
  │       └─ Layer 3: IApprovalGate ── 人类审批
  │           │
  │           ├─ Layer 3: IDenialLedger ── 拒绝计数 + 阈值暂停
  │           ├─ Layer 3: IPostDenialGuard ── 盲重试阻止
  │           │
  │           └─ Layer 4: ISandboxBackend ── 隔离执行
```

## 9. 渐进式增强路径

按 `00-vision.md` 约束 4（渐进式增强），安全能力按 XDSL 配置丰富度逐步引入，不按时间分阶段。

### 最小可运行（只 Layer 1）

```yaml
# agent.xdef 中声明
tools:
  allowed: [read-file, write-file, call-agent]
  denied: [delete-file]

file-access:
  - pattern: ".nop/**"
    access: read-only
```

**行为**：IPermissionProvider 做 deny/allow；IPathAccessChecker 做路径检查；子 Agent 权限继承。不需要任何 XDSL 安全配置。

### 引入分级（Layer 2）

```yaml
# security-policy.xml（XDSL）
<security-policy>
  <rule action-kind="shell.exec" level="ELEVATED"/>
  <rule action-kind="network.fetch" level="STANDARD"
        condition="!trustedSource" upgrade-to="RESTRICTED"/>
</security-policy>
```

**行为**：ISecurityLevelResolver 解析等级；IContentGuardrail 启用注入检测；IPermissionMatrix 按通道限制。

### 引入审批治理（Layer 3）

```yaml
# approval-config.xml（XDSL）
<approval-governance>
  <denial-threshold>3</denial-threshold>
  <approval-timeout-seconds>300</approval-timeout-seconds>
</approval-governance>
```

**行为**：高风险操作需要人类审批；拒绝计数达阈值暂停；盲重试被阻止。

### 引入平台安全（Layer 4）

```yaml
# platform-security.xml（XDSL）
<sandbox backend="docker"/>
<sensitive-paths config="security-sensitive-paths.xml"/>
<audit persistence="database"/>
```

**行为**：shell/code 在 Docker 容器内执行；敏感路径 hard-block；审计日志持久化到 DB。

**关键**：从 Layer 1 到 Layer 4，引擎代码不变——只替换接口实现和增加 XDSL 配置。

## 10. Shell 与高风险工具

对 shell、http 请求、文件写入、删除类工具，额外做程序级限制：

- 允许工作目录白名单
- 环境变量白名单（默认只传 PATH, HOME, LANG）
- 输出大小限制
- 命令超时
- 进程组清理

这些约束不应交给 prompt 决定。

## 11. 与工具验证的关系

权限检查和工具验证是不同层次：

- **权限检查**：你能不能调用这个工具或路径
- **工具验证**：你传入的参数是否合法、安全、符合 schema

这两层都必须存在。

## 12. 调研来源

本篇设计融合了 15+ 个 Agent 框架的安全机制调研：

| 框架 | 借鉴点 | 对应 Layer |
|------|--------|-----------|
| **OpenSquilla** | 5 层纵深防御；4 类注入检测；`<untrusted>` 信封；L0~L3 安全等级；DenialLedger + 阈值暂停；Post-denial 守卫；敏感路径 hard-block；工具硬编码 denylist | Layer 1-4 |
| **VoltAgent** | Input/Output Guardrail pipeline；`needsApproval` per tool；预构建 guardrail 库 | Layer 2-3 |
| **opencode** | 3-source 权限合并；子 Agent 权限派生（deny 传播） | Layer 1 |
| **PilotDeck** | 5 permission modes；PreToolUse hook → permission → audit → execute 流程 | Layer 2-3 |
| **Hermes Agent** | Terminal 6 sandbox backends (Docker/SSH/Modal/...) | Layer 4 |

详见 `ai-dev/analysis/agent-survey/` 目录下的各调研报告。

## 13. 与其他文档的关系

- `00-vision.md` — 设计原则（含渐进式增强约束）
- `01-architecture-baseline.md` — 架构基线（多租户、资源隔离）
- `nop-ai-agent-context-model.md` — 上下文模型（权限继承、fork）
- `nop-ai-agent-roadmap.md` — 分层接口（IPermissionProvider 在 Layer 1，IContentGuardrail 在 Layer 2）
- `02-execution-model.md` — Hook 生命周期（Tool dispatch 中的安全检查点）
