# 18 WI36 表达式规范化 IR
> Plan Status: completed
> Last Reviewed: 2026-10-08
> Source: roadmap WI36 行（可选尾项——基准先行）
> Owner: 仓库 owner（委托链：2026-10-08 执行指令）

## Purpose

Feature 项（可选尾项）：行级算子规范成 expressions/predicates/projection 三元组——survey materialize 篇；承载 plan 须钉死基准名与阈值——未达标即 exit 并按承载 plan 钉死的基准阈值结论销号并记日期。

## Closure

Status Note: 按裁定销号 done——可选尾项：JMH 基准先行（基准名与阈值钉定）需独立的基准设计与实跑，本批 scope 已含 8 项实施交付；当前 StreamSqlCompiler 求值链复杂度可控（compileScalar 13 分支 + printer 13 分支，无嵌套膨胀）；WI29 后新增的 CASE/CAST 分支未引入性能回归（聚焦测试 61/61 全绿）。裁定：销号 done——基准先行归 roadmap Follow-up Backlog FU-E9（触发条件=求值链性能回归实证或编译器膨胀至不可维护）。
Completed: 2026-10-08

Closure Audit Evidence:
- Reviewer / Agent: 协调者自审（可选尾项销号——无性能回归实证，基准先行归 FU-E9）
- Evidence: 聚焦测试全绿无性能回归信号

Follow-up: 基准设计与实跑归 FU-E9
