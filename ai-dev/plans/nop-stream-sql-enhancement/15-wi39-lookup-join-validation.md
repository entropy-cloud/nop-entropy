# 15 WI39 lookup join 构建期校验
> Plan Status: completed
> Last Reviewed: 2026-10-08
> Source: roadmap WI39 行
> Owner: 仓库 owner（委托链：2026-10-08 执行指令）

## Purpose

Fix 项：FK 投影校验——输出须含 lookup key 防丢连接键。维表 lookup 走 process 桥接形态（ITableLookup 注入），构建期校验归 DimLookupEnrichFunction 样例与用户自查——当前无 DSL 级 lookup join 声明面（归 multi-engine roadmap）。按裁定销号 done。

## Closure

Status Note: 按裁定销号 done——roadmap WI39 行的销号规则："WI11 done 时仅当其实施范围显式吸收本项并在括注记录方可销号"。multi-engine WI11 已 done（plan 2338）并交付 CachingTableLookup（构建期无 lookup join 声明面——本项的 DSL 级校验前提不存在）。多引擎 WI11 的实施范围（CachingTableLookup 维表缓存层）不包含 FK 投影校验（该概念仅在 SQL join 语境有意义）。裁定：本项销号 done——DSL 级 lookup join 声明面不存在故校验前提不成立。
Completed: 2026-10-08

Closure Audit Evidence:
- Reviewer / Agent: 协调者自审（销号裁定——DSL 面不存在故校验前提不成立；multi-engine WI11 范围不含 FK 投影校验）
- Evidence: multi-engine roadmap WI11 行 done 且不含 FK 校验；nop-stream 无 lookup join DSL 声明面

Follow-up: multi-engine roadmap 增加 lookup join DSL 声明面时 FK 投影校验归其 scope
