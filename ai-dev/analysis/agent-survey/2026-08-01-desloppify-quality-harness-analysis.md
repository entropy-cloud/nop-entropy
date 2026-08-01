# Desloppify 抗作弊评分与活计划工作队列分析 & Nop AI Agent 计划/质量

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/desloppify`（Python，反作弊代码质量 harness，~1460 文件，v1.0）vs `nop-ai-agent`（plan 包 AgentExecStatus 9 值）
> Conclusion:

## 一、总览

**Desloppify** 给 AI coding agent 提供"识别→理解→系统性改进代码质量"工具链。核心：**多维度分层评分**（strict/verified_strict 抗作弊）、**活计划 + 工作队列**、**LLM 主观 + integrity 校验交叉验证**。

| 维度 | desloppify | nop-ai-agent |
|------|-----------|--------------|
| 评分 | Dimension(tier+detectors) + DetectorScoringPolicy | — |
| 抗作弊 | strict / verified_strict 模式 | — |
| 计划 | 活计划 + 工作队列（next→修复→resolve） | AgentPlan 静态（AgentExecStatus 9 值） |
| 校验 | LLM 主观 + integrity 交叉验证 | — |

## 二、核心机制详解

### 2.1 多维度分层评分（`engine/_scoring/policy/core.py:21`）
- **Dimension**：tier + detectors 组合。
- **DetectorScoringPolicy**：评分策略。
- **strict / verified_strict 模式**：抗作弊——strict 要求机械验证通过，verified_strict 额外要求主观验证通过。
- 检测器矩阵：死代码 / 重复 / 复杂度 / 耦合 / 命名 / 安全 / gods 类（`engine/detectors/`）。

### 2.2 活计划 + 工作队列（`engine/_plan/operations/`、`engine/work_queue.py`）
- **next → 修复 → resolve** 持久化循环：取下一个工作项 → 修复 → 标记完成。
- **cluster 分组**：相关工作项批量处理。
- **skip 策略（需 attestation）**：跳过任务需要提供证明（防止 agent 偷懒跳过关键步骤）。
- **自动完成**：部分场景自动 resolve。

### 2.3 LLM 主观 + integrity 交叉验证（`intelligence/integrity.py`）
- LLM 主观评审 + 机械 integrity 校验**交叉验证**——单一手段可作弊，双重验证抗作弊。

## 三、对 nop-ai-agent 的借鉴要点

1. **活计划 + 工作队列执行模型**（中价值，plan 包）——next→修复→resolve 持久化推进，适配 DSL 任务编排（对应 beads 的 ready_work `2026-08-01-beads-versioned-graph-memory-analysis.md`）。nop 的 AgentExecStatus 已有 escalated 等状态，可对接工作队列的 resolve 语义。
2. **cluster 分组批量处理**（中价值）——plan 调度时按 cluster 聚合相关任务，减少上下文切换。
3. **skip 策略需 attestation**（中价值）——跳过任务需证明（防 agent 偷懒跳过关键步骤）——nop plan 的 gate（spec-kit `2026-08-01-spec-kit-workflow-engine-analysis.md` 的 require_explicit_verdict）同精神。
4. **机械+主观交叉验证**（低价值）——评审完整性思路。

## 三.5 Harness 可靠性（Retry/Replan/Resume）

- **活计划 + 工作队列持久化循环**（`engine/_plan/operations/`）：next→修复→resolve——**中断后从队列恢复**（磁盘即断点）。
- **cluster 分组重试**：相关工作项批量处理——失败项在同一 cluster 重试。
- **skip 需 attestation**：跳过任务需证明——**防跳过重试**（agent 不能跳过失败项）。
- **strict/verified_strict 抗作弊**：评分模式防重试时作弊。
- **对 nop 的启示**：活计划工作队列（next→修复→resolve）是 nop plan 运行时恢复的参考；skip attestation 是 gate 的防跳过语义。

## 四、结论

Desloppify 的活计划+工作队列+skip attestation 是 nop plan 的轻度参考。局限：内部耦合重、强绑质量领域。

## 六.5 Harness 机制维度覆盖（对照参考框架 D1-D12）

> 参考：`2026-08-01-harness-mechanism-reference-framework.md`（Agent Harness 十二大机制维度）

覆盖维度：**D7**（LLM 主观+integrity 交叉验证抗作弊）、**D9**（strict/verified_strict 评分门）、**D5**（活计划+工作队列）、**D12**（cluster 重试+skip attestation）。缺失/薄弱：D1、D6。

## 对比结论：nop-ai-agent 全面超越性分析

**nop-ai-agent 已超越的部分**：
- **计划模型**：nop AgentPlan 21 模型类 + AgentExecStatus 9 态比 desloppify 的活计划更丰富。
- **修复**：nop repair 包 + reliability 比其 integrity 校验更系统化。

**必要参考的增量（以超越方式吸收）**：
- **skip attestation**（跳过任务需证明）：nop plan gate 可增加"防跳过"语义——真正增量（与 codewhale require_explicit_verdict 统一）。

**总评**：nop-ai-agent **全面超越** desloppify（计划/修复更系统化）；skip attestation 一个增量吸收。

## References
- `~/ai/desloppify/engine/_plan/operations/`、`engine/_scoring/policy/core.py:21`、`engine/work_queue.py`、`intelligence/integrity.py`、`engine/detectors/`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-plan-dsl.md`
- `ai-dev/analysis/agent-survey/2026-08-01-beads-versioned-graph-memory-analysis.md`、`2026-08-01-spec-kit-workflow-engine-analysis.md`
