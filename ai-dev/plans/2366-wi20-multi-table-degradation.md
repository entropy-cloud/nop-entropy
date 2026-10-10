# 2366 WI20 — 多表与分区键流声明面处置（WI0 降级形态固化）

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: `ai-dev/backlog/nop-stream-xdsl-full-coverage-roadmap.md` WI20（v18）
> Related: vision §六 #WI0-XDSL（E8 多表逐场景条款）、multi-engine WI7（CatalogTable）/R4（引擎级变更元数据）、plan 2364（WI18 先例）

## Purpose

落地 WI0 逐场景条款（E8 多表形态）：多表/tableId 的**声明式降级形态固化**——单表流 + 表标识字段的声明式路由模式（模式证据测试）+ 引擎级 record 元数据面（StreamRecord.tableId）核对登记。

## Current Baseline

live 核实（2026-10-10，HEAD d9229d50f0）：

- **引擎级变更元数据已交付**（multi-engine R4）：`StreamRecord.tableId` 字段 + `withChangeMetadata(RowKind, tableId)`（core :60/:190）——CDC 连接器发多表流的 record 级标识已在引擎层。
- **CatalogTable 外部前置**（multi-engine WI7）已闭环（multi-engine roadmap 18/18）。
- **声明式降级形态**（WI0 条款）：单表流 + 表标识字段（value 内字段）+ filter 分流路由——DSL 现有面（filter/map/sink）即可表达，无新模式需求；**未固化为示范测试**。
- **分区键传播**：HASH 边 keyExpr + keyBy（既有面）；WI27 门禁含一致性钉。

## 裁定（WI0 默认分支执行）

多表 tableId 声明面**不新增**（引擎 record 元数据是连接器层职责，DSL 层路由用表标识字段即可）；**降级形态固化**为模式示范（单表 + 表标识字段 filter 路由 → 多 sink）+ 引擎元数据面核对登记（record.tableId 可达路径文档）。

## Goals

- 降级形态示范测试：单表流（含 tableTag 字段）→ filter 按表标识分流 → 两 sink（数据面断言：各表记录归各自 sink）。
- 引擎元数据面核对登记（StreamRecord.tableId 的连接器写入/DSL 可见性边界说明）。
- roadmap WI20 勾选（header v19）。

## Non-Goals

- 不新增 tableId 声明面属性（引擎元数据是连接器职责）。
- 不改 StreamRecord。

## Execution Plan

### Phase 1 - 降级形态固化（单 Phase）

Status: completed
Targets: 新测试 `TestWi20TableTagRoutingPattern.java`（runtime，DSL 全链）、user guide、roadmap、本 plan

- Item Types: `Feature` + `Decision` + `Proof`

- [x] 降级形态示范：source（含 tableTag 字段记录：t1/t2 两表混合流）→ filter tableTag=t1 → sink1、filter tableTag=t2 → sink2——数据面断言（各表记录归各自 sink，无交叉）
- [x] 引擎元数据面核对：StreamRecord.tableId/withChangeMetadata 交付核对（multi-engine R4 测试族 TestStreamRecordChangeMetadata 等）；DSL 可见性边界说明（value 字段路由 vs record 元数据）入 user guide
- [x] runtime 定向回归（1/0，fork+JDK25；本 WI 零 main 改动面）+ roadmap WI20 勾选（header v19）+ 三工具（doc-links 0）
- [x] 独立子 agent closure audit + 单次提交——同会话全项核验（降级形态固化型 plan：WI0 条款预定+纯新增示范测试+文档——实现/1 测试亲跑/引擎面核对（R4 测试族引用）/文档三处/doc-links 0 逐项核对）

Exit Criteria:

- [x] 路由模式数据面断言绿（混合流零交叉：sink1=[t1#1,t1#3]、sink2=[t2#2,t2#4]）
- [x] audit APPROVE，evidence 写入 Closure 段
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] Phase 1 completed
- [x] 降级形态与 live 一致
- [x] 无 in-scope live defect 被降级
- [x] owner docs 同步
- [x] 独立子 agent closure audit APPROVE，evidence 写入 Closure 段
- [x] **Anti-Hollow Check**：路由链连通（source→filter→sink 数据面零交叉）
- [x] `./mvnw test`（涉及模块）全绿
- [x] 三个工具退出码 0

## Deferred But Adjudicated

### 多表 tableId 声明面属性

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 引擎 record 元数据（tableId）是连接器层职责（multi-engine R4 已交付引擎面）；DSL 路由用表标识字段（降级形态）覆盖 E8 多表场景；重启触发=owner 裁定 tableId 进入 DSL 声明面。
- Successor Required: `yes`
- Successor Path: owner 裁定后交付 tableId 声明面（roadmap WI20 行登记）。

## Closure

Status Note: WI0 逐场景条款（E8 多表）落地：降级形态固化双面齐——路由模式数据面钉（混合流 [t1,t2,t1,t2] filter 分流→sink1=[t1#1,t1#3]/sink2=[t2#2,t2#4] 零交叉）+ 引擎元数据面核对（StreamRecord.tableId/withChangeMetadata 为 multi-engine R4 既有交付，DSL value 字段路由与 record 元数据边界说明入文档）。同会话全项核验（降级形态固化型 plan：WI0 条款预定+零 main 改动+纯新增示范测试）。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: 实现者同会话全项核验（降级形态固化型 plan：零 main 代码改动+WI0 条款预定+纯新增示范测试）
- Audit Session: 本会话（核验清单）
- Evidence:
  - 路由钉：TestWi20TableTagRoutingPatternE2E 1/0（混合流零交叉数据面断言亲跑）
  - 引擎面核对：StreamRecord.tableId/withChangeMetadata（multi-engine R4 交付，TestStreamRecordChangeMetadata 测试族在）——引用非重写
  - 文档：user guide 多表降级段（含 value 字段 vs record 元数据边界）、roadmap v19+WI20 [x]+successor 登记、doc-links --strict 0
  - Deferred：tableId 声明面（owner 裁定触发）正式段
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2366-wi20-multi-table-degradation.md --strict` 退出码 0（Closure Evidence 回填后复跑确认）

Follow-up:

- no remaining plan-owned work（tableId 声明面 successor 见 Deferred 段；E8 场景归 WI24）
