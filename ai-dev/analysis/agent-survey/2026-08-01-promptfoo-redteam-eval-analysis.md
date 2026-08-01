# Promptfoo 红队插件框架深度分析 & Nop AI Agent Guardrail 测试/验收

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/promptfoo`（OpenAI，TS，LLM 评测+红队 CLI）vs `nop-ai-agent`（guardrail + security）
> Conclusion:

## 一、总览

**Promptfoo** 是 LLM 评测与红队 CLI/库（现属 OpenAI）。核心：**Redteam 插件框架**（Plugin 生成攻击 + Grader rubric 打分分离）、**策略层 Strategies**（payload 二次变换）、**行业垂直集**（financial/medical/…）、**assertion/matcher 三层**。

| 维度 | promptfoo | nop-ai-agent |
|------|-----------|--------------|
| 定位 | 测试时（eval/redteam） | 运行时（guardrail 执行） |
| 架构 | Plugin(攻击生成) + Grader(打分) 分离 | security 6 层（执行） |
| 攻击集 | 60+ 插件 × 策略矩阵 | 无测试集 |
| 运行 | 本地 100%，CI/CD 集成 | 运行时 |

## 二、核心机制

### 2.1 Redteam 插件框架（`redteam/plugins/base.ts:41`）
- `RedteamPluginBase`：`getTemplate()/getAssertions()/generateTests()`。
- `RedteamGraderBase`（:382）：Nunjucks 渲染 rubric 打分。**生成与判定分离**。

### 2.2 策略层 + 行业垂直
- base64/crescendo/gcg/goat 等对 payload 二次变换（`strategies/`）。
- financial/insurance/medical/telecom/pharmacy 分目录预设合规攻击集。

### 2.3 assertion/matcher 三层
- provider→prompt→assertion，本地 100% 跑，支持 CI/CD 集成。

## 三、对 nop-ai-agent 的借鉴要点

1. **Plugin(生成攻击) + Grader(rubric 打分) 分离**（最高价值）——nop guardrail 的**验收**应采用此模式：用 Plugin 批量生成攻击用例，用 Grader（rubric 模板）判定 guardrail 是否正确拦截。把"防御能力"变成"可度量、可回归"的工程闭环。这是 nop 当前完全缺失的——有 guardrail 执行但无系统化测试。
2. **60+ 现成攻击类型 + 行业垂直集**（高价值）——guardrail 测试用例的现成语料库；尤其 ssrf/sqlInjection/promptExtraction/hallucination 等，直接用于 nop security 层的回归测试。
3. **assertion/matcher 抽象**（中价值）——为 hook 链提供断言基础设施（测试时验证 hook 是否按预期拦截/放行）。

## 四、结论

Promptfoo 补齐了 nop guardrail 的"测试与验收"维度——Plugin+Grader 分离架构是 guardrail 测试闭环的现成范式。它与运行时的 parlant（规则执行 `2026-08-01-parlant-conversation-control-analysis.md`）/AGT（策略强制 `2026-08-01-agent-governance-toolkit-analysis.md`）形成"建设+验收"闭环。局限：偏测试时非运行时、TS 生态需协议桥、红队结果依赖外部 LLM 评判。

## References
- `~/ai/promptfoo/src/redteam/plugins/base.ts`、`redteam/strategies/`、`redteam/plugins/`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-guardrail.md`、`nop-ai-agent-security.md`
- `ai-dev/analysis/agent-survey/2026-08-01-parlant-conversation-control-analysis.md`、`2026-08-01-agent-governance-toolkit-analysis.md`
