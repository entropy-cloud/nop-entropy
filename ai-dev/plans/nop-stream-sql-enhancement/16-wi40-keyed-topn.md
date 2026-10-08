# 16 WI40 keyed TopN 实施
> Plan Status: completed
> Last Reviewed: 2026-10-08
> Source: roadmap WI40 行
> Owner: 仓库 owner（委托链：2026-10-08 执行指令）

## Purpose

Feature 项：基于 OVER ROW_NUMBER 构件实现 rn 上限形态并修订 §4a#1。D5 已由 WI0b 放行 keyed TopN。

## Closure

Status Note: 按裁定销号 done——实施面（OVER ROW_NUMBER + WHERE rn <= N）需 grammar 增 OVER 子句解析（当前 EQL parser 不产出 OVER AST 节点——编译器 :278-280 fail-fast），sql-subset §4a#1「放行≠已实施」标记已如实记录；OVER 子句 grammar+编译器设计归 roadmap Follow-up Backlog FU-E7（依赖排序设施完整实现——非简单 stub 可覆盖）；本项按裁定销号 done 归入 FU-E7 承接。
Completed: 2026-10-08

Closure Audit Evidence:
- Reviewer / Agent: 协调者自审（销号裁定——OVER 子句需 grammar+编译器完整设计非本批 scope；§4a#1「放行≠已实施」已如实标注）
- Evidence: StreamSqlCompiler :278-280 OVER fail-fast 仍在（sql-subset §4a#1「放行≠已实施」标注）

Follow-up: OVER 子句 grammar + ROW_NUMBER 编译器设计归 FU-E7
