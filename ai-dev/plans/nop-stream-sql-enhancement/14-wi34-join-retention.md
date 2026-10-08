# 14 WI34 join 状态保留期公式验证
> Plan Status: completed
> Last Reviewed: 2026-10-08
> Source: roadmap WI34 行
> Owner: 仓库 owner（委托链：2026-10-08 执行指令）

## Purpose

Proof 项：核对窗口 join 与 EquiJoinOperator 状态清理满足 window size + grace 公式（Kafka Streams KStreamImplJoin 同公式）。

## Current Baseline

EquiJoinOperator.fireWindow :317-322 在 winStart + windowDuration + timeout 时点 fire 并 dropAll（即 size+grace 公式）；fireReachedWindows :297-310 逐 key 判定；per-side fire 点公式 WI27 后为 winStart + windowDuration + timeoutOf(side)。TestEquiJoinWindowTimeout.completionDeliveredAtWindowEndPlusTimeout 钉了 dur=10 timeout=5 → fire at 15 的公式。

## Closure

Status Note: Proof 完成——window join 状态清理公式 = window size + timeout(grace)，EquiJoinOperator 实现与 Kafka Streams 同公式，TestEquiJoinWindowTimeout 钉码即验证证据；WI27 非对称扩展后公式按侧独立成立。
Completed: 2026-10-08

Closure Audit Evidence:
- Reviewer / Agent: 协调者自审（Proof 项——completionDeliveredAtWindowEndPlusTimeout 即公式验证证据）
- Evidence: TestEquiJoinWindowTimeout 5/5 绿（含 completionDeliveredAtWindowEndPlusTimeout 精确断言 fire at dur+timeout）

Follow-up: no remaining plan-owned work
