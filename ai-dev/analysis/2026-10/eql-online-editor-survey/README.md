# EQL 在线编辑与测试工具调研（2026-10）

> Status: open（随后端能力调研与后续设计更新）
> Date: 2026-10-18
> Scope: 在线 SQL 编辑器外部调研（7 个开源项目）+ Flux SQL 编辑器现状差距 + Nop 后端 EQL 能力底账 + 横向对比取舍
> 后续 owner：`ai-dev/backlog/eql-online-editor-roadmap.md`（执行状态）；设计结论将收敛到 `ai-dev/design/eql-online-editor/`

## 调研目标

为 EQL 交付一个 Web 在线编辑与测试工具（对标在线版 DBeaver），先回答三个问题：

1. 成熟在线 SQL 编辑器（Web/桌面）在**编辑器、补全、元数据、执行、结果集、方言抽象、权限安全**上分别怎么设计？
2. `nop-chaos-flux` 现有 SQL 编辑器能复用多少、EQL 差距在哪？
3. `nop-entropy` 后端已有多少 EQL 解析/编译/执行/元数据/安全能力，最短落地路径是什么？

## 文件清单

| 文件 | 内容 | 一句话结论 |
|---|---|---|
| [2026-10-18-cloudbeaver-analysis.md](./2026-10-18-cloudbeaver-analysis.md) | DBeaver Web 版完整架构 | 把语言智能收敛为「每连接方言描述符 + 服务端语言服务 API」，前端只做 CodeMirror 装配与结果呈现 |
| [2026-10-18-dbgate-analysis.md](./2026-10-18-dbgate-analysis.md) | 现代 Web DB IDE | 每标签页独立会话进程 + 结果集物化为可随机读数据集 + SSE 只通知/HTTP 取数 |
| [2026-10-18-chat2db-analysis.md](./2026-10-18-chat2db-analysis.md) | AI 数据库工具（Java + React） | 后端多阶段补全流水线 + 声明式方言能力配置 + 统一 candidate 契约 |
| [2026-10-18-sqlpad-analysis.md](./2026-10-18-sqlpad-analysis.md) | 轻量在线 SQL 编辑器 | batch/statement 执行模型 + 运行记录与结果数据分离 + 驱动能力声明式注册 |
| [2026-10-18-beekeeper-studio-analysis.md](./2026-10-18-beekeeper-studio-analysis.md) | 桌面 SQL 编辑器（Electron + Vue） | 语句为执行单元 + 统一方言描述符驱动编辑/执行/结果；`queryId` 句柄 execute/cancel |
| [2026-10-18-querybook-analysis.md](./2026-10-18-querybook-analysis.md) | Notebook 式 SQL IDE | 执行记录闭环 + 异步任务 + 结果对象存储 + 权限/审计 |
| [2026-10-18-hue-analysis.md](./2026-10-18-hue-analysis.md) | 老牌 Web SQL 工作台 | `SqlExecutable` 语句级状态机 + 客户端语句定位/选中执行 + 多引擎方言分层 |
| [2026-10-18-online-sql-editor-architecture-comparison.md](./2026-10-18-online-sql-editor-architecture-comparison.md) | 7 项目横向对比与 EQL 取舍 | 薄前端 + 服务端网关 + 数据化方言描述符 + 语句级执行状态机；EXPLAIN/校验是差异化 |
| [2026-10-18-flux-sql-editor-current-state-and-eql-gap.md](./2026-10-18-flux-sql-editor-current-state-and-eql-gap.md) | Flux SQL 编辑器现状与 EQL 差距 | 已有字段级骨架；缺 EQL 方言、实体元数据补全、后端校验、结果集分页/导出、工作台壳层、后端 API |
| [2026-10-18-nop-entropy-eql-backend-capability-survey.md](./2026-10-18-nop-entropy-eql-backend-capability-survey.md) | Nop 后端 EQL 能力证据底账 | 见报告结论（解析/编译/执行/元数据/安全/宿主模块候选） |

## 证据口径

- 外部项目：`~/sources/sql-editors/{cloudbeaver,dbgate,sqlpad,beekeeper-studio,querybook,hue}` 与 `~/sources/Chat2DB`，均为 depth-1 clone；每个项目报告记录 HEAD commit，只读源码、未构建、未联网。
- 本仓能力：`nop-entropy`、`nop-chaos-flux`、`nop-chaos-next` 当前工作树；关键事实带 `path:line`。
- 无法验证的外部数据库/运行时行为在各报告中标注「未确认/未实测」，不做无证据断言。
- 外部实现 License 差异显著（Apache-2.0 / MIT / GPL-3.0 / 自定义限制），本调研只做架构借鉴，不复制源码；合规结论需法务确认。

## 使用方式

- 先读本 README 的「文件清单」定位项目，再读对应项目报告的第 12 节（Adopt/Adapt/Reject）。
- 需要做架构取舍时读横向对比报告给第 3—5 节（共性结论/争议裁定/采纳清单）。
- roadmap 的状态与工作项以 `ai-dev/backlog/eql-online-editor-roadmap.md` 为唯一来源；本目录不复制状态。
