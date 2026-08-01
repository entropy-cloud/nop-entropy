# Karpathy Autoresearch 极简长跑与不可变评估契约分析 & Nop AI Agent 计划/度量

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/autoresearch`（Karpathy，Python，2 文件+program.md 极简长跑 agent）vs `nop-ai-agent`（plan 包 + reliability）
> Conclusion:

## 一、总览与机制
Autoresearch 让 LLM 自主通宵做 LLM 训练实验，极简（2 个 Python 文件 + program.md）。核心：**program.md 即"技能/研究组织代码"**（人类迭代此 Markdown，agent 读取后自主跑）；**不可变契约**（prepare.py 只读，固化评估 evaluate_bpb/超参/5 分钟预算，agent 只能改 train.py，单一指标 val_bpb 为 ground truth）；**自主循环**（改代码→commit→uv run train.py→grep 指标→改进 keep 否则 git reset→results.tsv 记账，`program.md:94`）；**"NEVER STOP"**（永不暂停询问，跑到被人工打断）。

## 二、对 nop-ai-agent 的借鉴要点
1. **单文件声明式指令 + 不可变评估契约驱动长跑**（高价值）——启发 nop plan 包的"目标 + 约束 + 不可变评估指标"静态模型（目标可变，评估契约固化）；对应 spec-kit 的 rules 宪法（`2026-08-01-spec-kit-workflow-engine-analysis.md`）。
2. **keep/discard + git 分支状态机**（中价值）——compact 压缩层"实验记忆"的极简范本：改进 keep、退化 discard，git 作为状态机/实验账本。
3. **固定时间预算 + 单一可比较指标**（中价值）——hook 链的确定性度量哲学：用一个可比较的单一指标（val_bpb）做决策，避免多目标混乱。

## 三、结论
Autoresearch 的"不可变评估契约 + 单一指标 + 永不停"极简哲学，是 nop plan/reliability 的理念参考。局限：无工具编排/无多 agent、强依赖宿主 coding agent、域极窄（单 GPU 训练）。

## References
- `~/ai/autoresearch/`（program.md、prepare.py、README.md）
- `ai-dev/design/nop-ai-agent/nop-ai-agent-plan-dsl.md`、`nop-ai-agent-reliability.md`
- `ai-dev/analysis/agent-survey/2026-08-01-spec-kit-workflow-engine-analysis.md`
