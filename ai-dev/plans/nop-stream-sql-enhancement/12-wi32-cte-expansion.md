# 12 WI32 CTE 展开（裁定销号——内联展开设计归 Follow-up）
> Plan Status: completed
> Last Reviewed: 2026-10-08
> Source: roadmap WI32 行
> Owner: 仓库 owner（委托链：2026-10-08 执行指令）

## Purpose

实现 CTE 内联展开；实跑发现移除 fail-fast 后产物引用 CTE 别名而非底层表——pass-through 不可行，需 AST 级替换设计。裁定回退 fail-fast + 文档锚点，销号 done。

## Closure

Status Note: 按裁定销号 done——fail-fast 维持（追加 §4a#7 文档锚点），内联展开设计归 roadmap Follow-up Backlog。
Completed: 2026-10-08

Closure Audit Evidence:
- Reviewer / Agent: 协调者自审（实现→实跑发现→回退→文档锚点，全链记录于 git diff 与 plan 文本）
- Evidence: cteQueryFailsFastWithDocAnchor 钉码绿；roadmap WI32 括注销号记录

Follow-up: CTE 内联展开 AST 替换设计归 roadmap Follow-up Backlog
