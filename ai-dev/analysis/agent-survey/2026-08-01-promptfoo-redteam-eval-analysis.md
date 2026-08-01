# Promptfoo 红队插件框架深度分析 & Nop AI Agent Guardrail 测试/验收

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/promptfoo`（OpenAI，TS，LLM 评测+红队 CLI，~1071 文件）vs `nop-ai-agent`（guardrail + security）
> Conclusion:

## 一、总览

**Promptfoo** 是 LLM 评测与红队 CLI/库（现属 OpenAI）。核心：**Redteam 插件框架**（Plugin 生成攻击 + Grader rubric 打分分离）、**策略层 Strategies**（payload 二次变换）、**行业垂直集**、**assertion/matcher 三层**。

| 维度 | promptfoo | nop-ai-agent |
|------|-----------|--------------|
| 定位 | 测试时（eval/redteam） | 运行时（guardrail 执行） |
| 架构 | Plugin(攻击生成) + Grader(打分) 分离 | security 多层（执行） |
| 攻击集 | 60+ 插件 × 策略矩阵 | 无测试集 |
| 运行 | 本地 100%，CI/CD 集成 | 运行时 |

## 二、核心机制详解

### 2.1 Redteam 插件框架（`redteam/plugins/base.ts:41,382`）
- **`RedteamPluginBase`**（:41）：`getTemplate()` / `getAssertions()` / `generateTests()`——生成攻击用例。
- **`RedteamGraderBase`**（:382）：Nunjucks 渲染 **rubric 打分**——判定 guardrail 是否正确拦截。
- **生成与判定分离**——Plugin 负责"造攻击"，Grader 负责"判结果"。

### 2.2 策略层 Strategies（`redteam/strategies/`）
- base64 / crescendo / gcg / goat 等对 payload **二次变换**——绕过简单防御。

### 2.3 行业垂直集（`redteam/plugins/`）
- financial / insurance / medical / telecom / pharmacy 分目录预设合规攻击集——行业合规现成用例。

### 2.4 60+ 攻击插件
- ssrf / sqlInjection / promptExtraction / hallucination / …——覆盖 OWASP LLM Top 10。

### 2.5 assertion/matcher 三层
- provider → prompt → assertion，本地 100% 跑，支持 CI/CD 集成。

## 三、对 nop-ai-agent 的借鉴要点

1. **Plugin(生成攻击) + Grader(rubric 打分) 分离**（最高价值）——nop guardrail 的**验收**应采用此模式：用 Plugin 批量生成攻击用例，用 Grader（rubric 模板）判定 guardrail 是否正确拦截。把"防御能力"变成"可度量、可回归"的工程闭环。这是 nop 当前完全缺失的——有 guardrail 执行但无系统化测试。
2. **60+ 现成攻击类型 + 行业垂直集**（高价值）——guardrail 测试用例的现成语料库；尤其 ssrf/sqlInjection/promptExtraction/hallucination 等，直接用于 nop security 层的回归测试。
3. **assertion/matcher 抽象**（中价值）——为 hook 链提供断言基础设施（测试时验证 hook 是否按预期拦截/放行）。
4. **策略层二次变换**（中价值）——测试 guardrail 对变换后攻击的鲁棒性（base64 编码/渐进式攻击等）。

## 三.5 Harness 可靠性（Retry/Replan/Resume）

- **断言失败重跑**：eval 的 assertion 失败 → 标记该用例失败 → 支持 CI 中重跑（回归测试语义）。
- **策略层二次变换**（`redteam/strategies/`）：base64/crescendo 等对 payload 变换——失败后换策略重试（**测试级 replan**）。
- **Grader rubric 打分**（`redteam/plugins/base.ts:382`）：rubric 模板化判定——判定标准稳定，重试结果可比较。
- **对 nop 的启示**：guardrail 测试的"断言失败 → 重跑 + 换策略"闭环是 nop guardrail 验收的参考。

## 四、结论

Promptfoo 补齐了 nop guardrail 的"测试与验收"维度——Plugin+Grader 分离架构是 guardrail 测试闭环的现成范式。与运行时的 parlant（规则执行 `2026-08-01-parlant-conversation-control-analysis.md`）/AGT（策略强制 `2026-08-01-agent-governance-toolkit-analysis.md`）形成"建设+验收"闭环。

## 六.5 Harness 机制维度覆盖（对照参考框架 D1-D12）

> 参考：`2026-08-01-harness-mechanism-reference-framework.md`（Agent Harness 十二大机制维度）

覆盖维度：**D7**（Plugin+Grader 反馈环）、**D9**（assertion/matcher 质量门）、**D12**（断言失败重跑+策略变换重试）。缺失/薄弱：D1、D2（测试时非运行时）。

## 对比结论：nop-ai-agent 全面超越性分析

**nop-ai-agent 已超越的部分**：
- **guardrail 执行**：nop security 6 层 + guardrail-contract 是运行时执行——promptfoo 是测试时工具，场景不同但 nop 覆盖运行时。
- **hook 体系**：nop 12 点 + middleware 比 promptfoo 的 assertion/matcher 更工程化。

**必要参考的增量（以超越方式吸收）**：
- **Plugin+Grader 测试闭环**（攻击生成 + rubric 打分分离）：nop guardrail 缺系统化测试/验收——这是真正增量（把防御能力变成可度量可回归的工程闭环），nop 增加 `GuardrailTestSuite`（Plugin 生成攻击 + Grader 判定）。
- **60+ 现成攻击类型**：guardrail 回归测试的语料库——直接吸收为 nop guardrail 测试用例。

**总评**：nop-ai-agent 在运行时 guardrail 上**全面超越**；Plugin+Grader 测试闭环是补"验收"维度的真正增量（nop 有执行无测试，补齐后形成建设+验收闭环）。

## References
- `~/ai/promptfoo/src/redteam/plugins/base.ts:41,382`、`redteam/strategies/`、`redteam/plugins/`
- `ai-dev/design/nop-ai-agent/guardrail-contract.md`、`nop-ai-agent-security-and-permissions.md`
- `ai-dev/analysis/agent-survey/2026-08-01-parlant-conversation-control-analysis.md`、`2026-08-01-agent-governance-toolkit-analysis.md`
