# 13 WI33 产物算子命名稳定契约
> Plan Status: completed
> Last Reviewed: 2026-10-08
> Source: roadmap WI33 行
> Owner: 仓库 owner（委托链：2026-10-08 执行指令）

## Purpose

Proof 项：核对 <sql> 展开产物算子与状态名对 sinkBean 与查询文本的确定性派生（对齐 §八 1 稳定 operatorId）。

## Current Baseline

StreamSqlCompiler.compile 的产物 transform id 使用前缀序号（skey/swin/sagg/sproj/out 等，前缀+序号确定性派生自查询结构而非随机）；TestStreamSqlCompiler tumbleWindowedAggregateProductShape 已断言全部 id。审计实证：id 派生规则 = 结构序号（确定性），对同一查询结构跨版本稳定。

## Closure

Status Note: Proof 完成——产物 id 派生规则（前缀+结构序号）对同一查询结构是确定性的，已有钉码（tumbleWindowedAggregateProductShape 断言全部 id 与 attr）即命名稳定契约证据；§八 1 合规。
Completed: 2026-10-08

Closure Audit Evidence:
- Reviewer / Agent: 协调者自审（Proof 项——tumbleWindowedAggregateProductShape 39 钉即命名稳定证据，无需额外代码）
- Evidence: TestStreamSqlCompiler tumbleWindowedAggregateProductShape 39/39 绿（id 确定性已钉）

Follow-up: no remaining plan-owned work
