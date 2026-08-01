# Karpathy Autoresearch 极简长跑与不可变评估契约分析 & Nop AI Agent 计划/度量

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/autoresearch`（Karpathy，Python，2 文件 + program.md 极简长跑 agent）vs `nop-ai-agent`（plan 包 + reliability）
> Conclusion:

## 一、总览

**Autoresearch** 让 LLM 自主通宵做 LLM 训练实验，极简（2 个 Python 文件 + program.md）。核心：**program.md 即技能/研究组织代码**、**不可变评估契约**、**keep/discard + git 分支状态机**、**NEVER STOP**。

| 维度 | autoresearch | nop-ai-agent |
|------|-------------|--------------|
| 配置 | program.md（人类迭代的 Markdown） | XDEF DSL |
| 评估契约 | 不可变（prepare.py 只读） | — |
| 状态机 | git 分支（keep/discard） | AgentExecStatus 9 值 |
| 停止 | NEVER STOP（跑到人工打断） | CompletionJudge |

## 二、核心机制详解

### 2.1 program.md 即"技能/研究组织代码"（`README.md:15`）
- 人类迭代此 Markdown，agent 读取后自主跑。
- **program.md 是 agent 的"大脑"**——所有研究方法论、实验流程、判断标准都在这个文件里。

### 2.2 不可变契约（`prepare.py` 只读）
- `prepare.py` 固化评估逻辑：`evaluate_bpb`（bits-per-byte 指标）、超参、**5 分钟时间预算**。
- agent 只能改 `train.py`（训练代码），不能改 `prepare.py`（评估契约）。
- **单一指标 `val_bpb` 为 ground truth**——只用一个可比较的指标做决策，避免多目标混乱。

### 2.3 自主循环（`program.md:94`）
- 改代码 → commit → `uv run train.py > run.log` → grep 取指标 → **改进则 keep 否则 `git reset`** → `results.tsv` 记账。
- **git 分支本身当状态机/实验账本**——每次实验是一个 commit，改进保留、退化回退。

### 2.4 "NEVER STOP"（`program.md:112`）
- 永不暂停询问，跑到被人工打断为止——真正的"长跑"agent。

## 三、对 nop-ai-agent 的借鉴要点

1. **单文件声明式指令 + 不可变评估契约驱动长跑**（高价值）——启发 nop plan 包的"目标 + 约束 + 不可变评估指标"静态模型（目标可变，评估契约固化）；对应 spec-kit 的 rules 宪法（`2026-08-01-spec-kit-workflow-engine-analysis.md`）。**不可变评估契约**是长跑 agent 的关键安全网：agent 可自由探索，但"什么算成功"不可被 agent 自己修改。
2. **keep/discard + git 分支状态机**（中价值，compact 包）——compact 压缩层"实验记忆"的极简范本：改进 keep、退化 discard，git 作为状态机/实验账本。与 beads 的可逆压缩（snapshot→summarize `2026-08-01-beads-versioned-graph-memory-analysis.md`）同构但更极简。
3. **固定时间预算 + 单一可比较指标**（中价值）——hook 链的确定性度量哲学：用一个可比较的单一指标做决策，避免多目标混乱。5 分钟时间预算是防卡死的安全网。

## 三.5 Harness 可靠性（Retry/Replan/Resume）

- **keep/discard + git reset 状态机**（`program.md:94`）：实验改进则 keep、退化则 `git reset`——**最简重试/回退机制**，git 分支即状态。
- **5 分钟时间预算**（`prepare.py`）：固定时间预算防卡死——超时即重来。
- **不可变评估契约**：`evaluate_bpb` 单一指标 ground truth——重试的判定标准不可被 agent 修改。
- **NEVER STOP**：永不暂停询问——由人类打断，天然支持"重试到成功或被人为终止"。
- **对 nop 的启示**：单一指标 + 固定预算 + git 回退是最简 retry 范本；不可变评估契约是长跑 agent 的重试安全网。

## 四、结论

Autoresearch 的"不可变评估契约 + 单一指标 + 永不停"极简哲学，是 nop plan/reliability 的理念参考。不可变评估契约是长跑 agent 的安全网设计。

## 六.5 Harness 机制维度覆盖（对照参考框架 D1-D12）

> 参考：`2026-08-01-harness-mechanism-reference-framework.md`（Agent Harness 十二大机制维度）

覆盖维度：**D8**（program.md 不可变评估契约+单一指标）、**D1**（自主循环 NEVER STOP）、**D2**（git 分支状态机）、**D12**（keep/discard+git reset+5min 预算）。缺失/薄弱：D6（无审批）、D9（无质量门）。

## 对比结论：nop-ai-agent 全面超越性分析

**nop-ai-agent 已超越的部分**：
- **计划模型**：nop AgentPlan 21 模型类 + AgentExecStatus 9 态远优于 autoresearch 的单 Markdown 文件。
- **可靠性**：nop reliability 包（熔断/重试/checkpoint）比其 git reset 状态机更工程化。
- **度量**：nop `GoalAssessment` + CompletionJudge 比其单一 val_bpb 指标更完整。

**必要参考的增量（以超越方式吸收）**：
- **不可变评估契约**（目标可变，评估指标固化，agent 不可修改"什么算成功"）：nop plan 包可增加"评估契约不可变"语义——真正增量（长跑 agent 的安全网思想）。

**总评**：nop-ai-agent **全面超越** autoresearch（计划/可靠性/度量更完整）；不可变评估契约思想一个增量吸收。

## References
- `~/ai/autoresearch/`（program.md:94,112、prepare.py、README.md:15）
- `ai-dev/design/nop-ai-agent/nop-ai-agent-plan-dsl.md`、`nop-ai-agent-reliability.md`
- `ai-dev/analysis/agent-survey/2026-08-01-spec-kit-workflow-engine-analysis.md`、`2026-08-01-beads-versioned-graph-memory-analysis.md`
