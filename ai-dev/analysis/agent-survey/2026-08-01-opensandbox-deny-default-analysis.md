# OpenSandbox Deny-by-default 与 Credential Vault 分析 & Nop AI Agent 安全/沙箱

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/opensandbox`（CNCF，Go+Python，通用 AI 沙箱平台）vs `nop-ai-agent`（security 6 层）
> Conclusion:

## 一、总览与机制
OpenSandbox 是 CNCF Landscape 通用 AI 沙箱平台（Coding/GUI Agent/评估/代码执行/RL）。核心：**6 surface 分层**（server→execd→egress→ingress→sdks→cli，`AGENTS.md:7-17`）；**Deny-by-default 网络策略**（空/null 默认 deny，first-match 域名规则，`components/egress/pkg/policy/policy.go:26-104`）；**Always-rules 覆盖层**（always-deny 覆盖用户 allow，always-allow 覆盖用户 deny，deny-always-beats-allow-always，`policy_test.go:27-50`）；**Credential Vault**（按 host+method+path 绑定凭证，出口请求自动注入，workload 看不到 secret）；细粒度状态机（state+reason+message）。

## 二、对 nop-ai-agent 的借鉴要点
1. **Deny-by-default + first-match 规则引擎**（高价值）——直接用于 nop security 检查层：默认拒绝，逐条匹配放行（安全优先）。
2. **Always-rules 覆盖模式**（最高价值）——安全层 deny 永远优先于业务层 allow；这是 nop 6 层安全检查中"安全优先"原则的实现范式（与 AGT 结构性不可绕过 `2026-08-01-agent-governance-toolkit-analysis.md` 同精神）。
3. **Credential Vault 模式**（高价值）——凭证在出口层注入而非传入 workload，可用于 nop 工具调用的密钥管理（工具不接触真实 secret）。
4. **细粒度状态机（state+reason+message）**（中价值）——比简单 status 更可诊断，适合 checkpoint 状态设计（对应 hive is_clean `2026-08-01-hive-dual-middleware-analysis.md`）。

## 三、结论
OpenSandbox 的 Deny-by-default + Always-rules + Credential Vault 是 nop 安全/沙箱设计的直接参考。局限：聚焦沙箱隔离非 agent 逻辑、基础设施重（Docker/K8s）、无 agent 行为级 hook/guardrail。

## References
- `~/ai/opensandbox/components/egress/pkg/policy/policy.go`、`AGENTS.md`、`specs/sandbox-lifecycle.yml`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-security.md`
- `ai-dev/analysis/agent-survey/2026-08-01-agent-governance-toolkit-analysis.md`、`2026-08-01-hive-dual-middleware-analysis.md`
