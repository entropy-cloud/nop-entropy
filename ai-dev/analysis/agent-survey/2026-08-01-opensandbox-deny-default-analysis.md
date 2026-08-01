# OpenSandbox Deny-by-default 与 Credential Vault 分析 & Nop AI Agent 安全/沙箱

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/opensandbox`（CNCF，Go+Python，通用 AI 沙箱平台，~2036 文件）vs `nop-ai-agent`（security + ContentOrigin）
> Conclusion:

## 一、总览

**OpenSandbox** 是 CNCF Landscape 通用 AI 沙箱平台（Coding/GUI Agent/评估/代码执行/RL）。核心：**6 surface 分层**、**Deny-by-default 网络策略**、**Always-rules 覆盖层**、**Credential Vault**、**细粒度状态机**。

| 维度 | opensandbox | nop-ai-agent |
|------|-------------|--------------|
| 安全模型 | Deny-by-default + first-match 规则引擎 | security 多层检查 |
| 覆盖层 | Always-rules（deny-always > allow-always > 用户策略） | — |
| 凭证 | Credential Vault（出口层注入，workload 看不到 secret） | — |
| 隔离 | gVisor/Kata/Firecracker 三级 | — |
| 状态机 | state+reason+message 三段 | AgentExecStatus 9 值 |

## 二、核心机制详解

### 2.1 6 Surface 分层（`AGENTS.md:7-17`）
- `server`（生命周期控制面）→ `execd`（沙箱内执行守护）→ `egress`（网络出口策略 sidecar）→ `ingress`（入口网关路由）→ `sdks`（多语言客户端）→ `cli`（osb 命令行）。

### 2.2 Deny-by-default 网络策略（`components/egress/pkg/policy/policy.go:26-104`）
- `ActionAllow="allow"` / `ActionDeny="deny"`。
- **空/null/{} 默认 deny**——不显式 allow 就拒绝。
- **first-match 域名规则评估**：按顺序匹配，第一条命中即决定。

### 2.3 Always-rules 覆盖层（`policy_test.go:27-50`）
- **always-deny**：覆盖用户 allow（安全策略优先）。
- **always-allow**：覆盖用户 deny。
- **deny-always-beats-allow-always**：安全 deny 永远最高优先。
- 三层优先级：always-deny > always-allow > 用户策略。

### 2.4 Credential Vault（`components/egress/credential_vault_handler_test.go:43-60`）
- 按 `host + method + path` 匹配绑定凭证。
- 沙箱出口请求**自动注入认证**。
- **workload 本身看不到真实 secret**——密钥不进入 agent 执行环境。

### 2.5 生命周期状态机（`specs/sandbox-lifecycle.yml:13-22`）
- Creation→Execution→Pause→Resume→Termination→Error。
- **`status` 细分为 `state` + `reason` + `message`**——三段式比单一 status 更可诊断。

## 三、对 nop-ai-agent 的借鉴要点

1. **Deny-by-default + first-match 规则引擎**（高价值）——直接用于 nop security 检查层：默认拒绝，逐条匹配放行（安全优先）。nop 当前 PermissionMatrix 可增加 first-match 语义。
2. **Always-rules 覆盖模式**（最高价值）——安全层 deny 永远优先于业务层 allow；这是 nop 安全检查中"安全优先"原则的实现范式（与 AGT 结构性不可绕过 `2026-08-01-agent-governance-toolkit-analysis.md` 同精神）。三层优先级（always-deny > always-allow > 用户策略）可直接映射 nop 的 security 策略覆盖机制。
3. **Credential Vault 模式**（高价值）——凭证在出口层注入而非传入 workload，可用于 nop 工具调用的密钥管理（工具不接触真实 secret——agent 只看到代理后的请求结果）。
4. **细粒度状态机（state+reason+message）**（中价值）——比简单 status 更可诊断，适合 checkpoint 状态设计（对应 hive is_clean `2026-08-01-hive-dual-middleware-analysis.md`）。nop 的 AgentExecStatus 9 值可增加 reason/message 维度。

## 三.5 Harness 可靠性（Retry/Replan/Resume）

- **Deny-by-default 的重试语义**：默认拒绝 → 被拒请求不重试（安全边界）——**安全失败优先于重试**。
- **Always-rules 覆盖**：deny-always > allow-always > 用户策略——重试判定受安全策略约束。
- **沙箱生命周期状态机**（`specs/sandbox-lifecycle.yml:13-22`）：Pause/Resume——**沙箱级暂停恢复**。
- **对 nop 的启示**：安全失败不重试（防放大攻击）；Pause/Resume 是 nop AgentSession 挂起的参考。

## 四、优缺点

### 优点
1. Deny-by-default + Always-rules 是安全优先的完整实现。
2. Credential Vault 让 secret 不暴露给 workload。
3. gVisor/Kata/Firecracker 三级隔离覆盖不同安全级别。

### 缺点
1. 聚焦沙箱隔离非 agent 逻辑。
2. 基础设施重（Docker/K8s 依赖）。
3. 无 agent 行为级 hook/guardrail 系统。

## 五、结论

OpenSandbox 的 Deny-by-default + Always-rules + Credential Vault 是 nop 安全/沙箱设计的直接参考。

## 六.5 Harness 机制维度覆盖（对照参考框架 D1-D12）

> 参考：`2026-08-01-harness-mechanism-reference-framework.md`（Agent Harness 十二大机制维度）

覆盖维度：**D2**（6 surface 沙箱架构+三级隔离）、**D6**（Deny-by-default+Always-rules+Credential Vault）、**D4**（沙箱生命周期状态机）、**D12**（Pause/Resume）。缺失/薄弱：D1、D5。

## 对比结论：nop-ai-agent 全面超越性分析

**nop-ai-agent 已超越的部分**：
- **安全检查**：nop security 6 层（ContentOrigin/PermissionMatrix/ToolAccessChecker）比 opensandbox 的网络策略更全面（nop 覆盖工具级 + 内容级）。
- **审批**：nop `ApprovalGate`/`AutoApproveGate` 比 opensandbox 的 credential vault 更贴近 agent 场景。

**必要参考的增量（以超越方式吸收）**：
- **Deny-by-default + Always-rules 覆盖层**（安全 deny 永远优先于业务 allow）：nop PermissionMatrix 可增加"安全优先覆盖"语义——真正增量（与 AGT 结构性不可绕过同精神）。
- **Credential Vault**（凭证出口层注入，workload 不见 secret）：nop 工具调用密钥管理可增加——增强。

**总评**：nop-ai-agent **全面超越** opensandbox（安全覆盖更全面）；Deny-by-default + Always-rules + Credential Vault 三个增量吸收。

## References
- `~/ai/opensandbox/components/egress/pkg/policy/policy.go:26-104`、`policy_test.go:27-50`、`credential_vault_handler_test.go:43-60`、`AGENTS.md:7-17`、`specs/sandbox-lifecycle.yml:13-22`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md`
- `ai-dev/analysis/agent-survey/2026-08-01-agent-governance-toolkit-analysis.md`、`2026-08-01-hive-dual-middleware-analysis.md`
