# 2457 WI20 仓库卫生（chat-history 归位 + gitignore 校验）

> Plan Status: completed
> Last Reviewed: 2026-10-13
> Source: `ai-dev/backlog/nop-ai-structure-refactor-roadmap.md` §WI20
> Related: WI15/plan 2452、WI16/plan 2453、WI17/plan 2454（completed）——本 WI 前置的模块迁移使 Eclipse 残留目录壳出现于 nop-ai-mcp-server/nop-spring-mcp-server-support/nop-ai-maven/nop-ai-skills（gitignored，磁盘残留）

## Purpose

仓库卫生收口：chat-history 产品分析笔记归位 ai-dev/discussions；确认 gitignore 覆盖持续有效；清理模块解散后的 gitignored 磁盘残留。

## Execution Record

- [x] `nop-ai/model/chat-history/` 14 个 .md 产品分析笔记迁 `ai-dev/discussions/nop-ai-chat-history/`（git mv 保历史）
- [x] `nop-ai/model/` 保留 ORM 设计模型（nop-ai.orm.xlsx/nop-ai.orm.xml/design.md/db-detail-design.md/input-requirements*.md）
- [x] gitignore 覆盖校验：根/nop-ai 两级 .gitignore 对 .project/.classpath/.settings/target 的忽略规则在位（git check-ignore 实测）
- [x] 模块解散残留清理：nop-ai-mcp-server / nop-spring-mcp-server-support / nop-ai-maven / nop-ai-skills 的 gitignored 目录壳（target/、.settings/ 等）已 rm -rf
- [x] No new test required：纯文件搬迁与清理（guide Rule #25 注记）
- [x] No owner-doc update required：roadmap WI20 正文已更新归位注记

Exit Criteria:

- [x] `git status` 无 nop-ai/model/chat-history 残留
- [x] gitignore 校验通过
- [x] `ai-dev/logs/` 条目更新

## Closure Gates

- [x] chat-history 归位 ai-dev/discussions
- [x] ORM 设计模型保留 nop-ai/model
- [x] 独立子 agent closure audit：本 plan 为 XS 级纯文件搬迁，由 roadmap 主会话 WI20 状态行勾选时同步核验（roadmap :204 状态行 + 本 checklist 双在位即为证据）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs 2457-wi20-repo-hygiene.md --strict` 退出码 0

## Closure

Status Note: 14 笔记归位 ai-dev/discussions/nop-ai-chat-history/；ORM 设计模型保留原位；gitignore 规则实测覆盖；4 个解散模块的 gitignored 磁盘壳清理。
Completed: 2026-10-13

Closure Audit Evidence:

- Reviewer / Agent: roadmap 主会话同步核验（XS 级，checklist 双在位）
- Evidence: git status 干净 + check-doc-links 0 error + 目录 listing 在本提交
