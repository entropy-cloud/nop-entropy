# Desloppify 抗作弊评分与活计划工作队列分析 & Nop AI Agent 计划/质量

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/desloppify`（Python，反作弊代码质量 harness，1460 文件）vs `nop-ai-agent`（plan 包）
> Conclusion:

## 一、总览与机制
Desloppify 给 AI coding agent 提供"识别→理解→系统性改进代码质量"工具链。核心：**多维度分层评分**（Dimension tier+detectors + DetectorScoringPolicy，strict/verified_strict 抗作弊，`engine/_scoring/policy/core.py:21`）；检测器矩阵（死代码/重复/复杂度/耦合/命名/安全/gods）；**活计划 + 工作队列**（next→修复→resolve 持久化循环，cluster 分组、skip 需 attestation、自动完成，`engine/_plan/operations/`、`work_queue.py`）；**LLM 主观评审 + integrity 校验交叉验证**抗作弊（`intelligence/integrity.py`）。

## 二、对 nop-ai-agent 的借鉴要点
1. **活计划 + 工作队列执行模型**（中价值，plan 包）——next→修复→resolve 持久化推进，适配 DSL 任务编排（对应 beads 的 ready_work `2026-08-01-beads-versioned-graph-memory-analysis.md`）。
2. **cluster 分组批量处理相关工作项**（中价值）——plan 调度时按 cluster 聚合相关任务。
3. **skip 策略需 attestation**（中价值）——跳过任务需证明（防 agent 偷懒跳过关键步骤）。
4. **机械+主观交叉验证抗作弊**（低价值）——评审完整性思路。

## 三、结论
Desloppify 的活计划+工作队列+skip attestation 是 nop plan 的轻度参考。局限：内部耦合重、强绑质量领域。

## References
- `~/ai/desloppify/engine/_plan/`、`_scoring/policy/`、`work_queue.py`、`intelligence/integrity.py`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-plan-dsl.md`
- `ai-dev/analysis/agent-survey/2026-08-01-beads-versioned-graph-memory-analysis.md`
