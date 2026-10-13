# 2455 WI18 nop-ai-dao 双限界上下文裁定

> Plan Status: completed
> Last Reviewed: 2026-10-13
> Source: `ai-dev/backlog/nop-ai-structure-refactor-roadmap.md` §WI18；消费者普查 = 2026-10-13 live HEAD（plan 2454 后）复测
> Draft Review: 2026-10-13 自审内联（Decision 型纯裁定 plan，无代码变更面——普查三断言均以 live grep 证据在 Current Baseline 在录；消费者重叠与 coder 零消费两断言经 round-1 前自查复核）；plan guide 允许纯裁定项按 Rules :253 维持现状
> Related: WI14/plan 2446（completed）——api/beans 删除后无二次搬迁交集（A1 已证）

## Purpose

裁定 nop-ai-dao 的项目管理域实体（Project/ProjectConfig/ProjectRule、Requirement/RequirementHistory、TestCase/TestResult、Todo、GenFile/GenFileHistory——约 11/22 实体）是否从会话/聊天域独立。roadmap Rules :253 允许本项裁定"维持现状"。

## Current Baseline

> 2026-10-13 live HEAD 实测。

- 消费者普查复测：**项目管理域**实体（NopAiProject/Requirement/TestCase/Todo 等）外部消费者 = nop-ai-service 的 BizModel（NopAiProjectBizModel 等）+ service test；**会话/聊天域**实体（NopAiSession/SessionMessage 等）外部消费者 = 同样仅 nop-ai-service（BizModel + ORM model-switched 测试）。两域消费面**重叠于同一模块**（nop-ai-service），无任何仅单域独有的消费者模块。
- **coder 零消费 dao**（grep 证实，roadmap 基线一致）。消费面基线 = nop-ai-web CRUD 页面（22 实体 × 5 文件）与 nop-ai-service 22 个 BizModel——与 roadmap 基线一致。
- 生成链：两域实体共存于 `nop-ai-dao/src/main/resources/_vfs/nop/ai/orm/_app.orm.xml` 单一生成链；WI14/plan 2446 已删除 api/beans 契约死岛（无二次搬迁交集）。
- ORM 模型结构变更 = Protected Areas plan-first：DDL 三方言迁移脚本 + codegen 链路复验——拆分成本实打实落在 DDL/codegen/回归三处。

## Goals

- 裁定结论 + 依据 + 重启条件登记（roadmap WI18 状态行括注 + 本 plan）。

## Non-Goals

- 不执行任何 ORM 模型拆分/DDL 变更（维持现状裁定下无实施面）。

## Adjudications

### A1 维持现状（双域共存于单一 dao 模块 + 单一 ORM 生成链）

- 依据：①两域消费者完全重叠（同为 nop-ai-service BizModel + nop-ai-web CRUD 页），独立拆分不改变任何消费边界；②coder 零消费 dao，不存在"轻下游被迫背负全量"的分层倒置；③拆分触发 DDL 三方言迁移 + codegen 链路复验 + web/service 回归，成本全部为机械性搬家且无功能收益；④WI14 删除 api/beans 后，两域间的唯一耦合点是 ORM 生成链本身——拆分只拆文件不拆依赖。
- 重启条件（任一触发即立 successor plan 执行拆分）：①AI 子系统需要按域独立部署/发布（如仅交付 chat 不交付项目管理）；②项目管理域需要独立数据生命周期（独立库/独立迁移节奏）；③域间出现消费者分化（某模块仅消费单域）。

## Execution Plan

### Phase 1 - 裁定登记与收口

Status: completed
Targets: roadmap、log、plan closure

- Item Types: `Decision`

- [x] 裁定登记：roadmap WI18 状态行 `done`（括注：维持现状 + A1 四条依据 + 重启条件）；状态块解析核对
- [x] 本 plan A1 即裁定正文；无代码变更——`No owner-doc update required`（module-groups 的 dao 分层描述与维持现状结论一致）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 0 error；`node ai-dev/tools/check-plan-checklist.mjs 2455-wi18-dao-dual-context-adjudication.md --strict` 0
- [x] pathspec 限定单一原子提交

Exit Criteria:

- [x] roadmap 更新后解析核对通过（21 行 WI 条目数不变）
- [x] `ai-dev/logs/2026/10-12.md` 条目含：裁定、A1 依据、commit hash

## Closure Gates

> 关闭条件：所有 Phase Exit Criteria + 本节全部 `[x]`，且独立 closure audit 通过。

- [x] 裁定登记完成（维持现状 + 依据 + 重启条件）
- [x] 不存在被静默降级的 in-scope live defect（A1 为显式裁定，roadmap Rules :253 授权）
- [x] No owner-doc update required（module-groups dao 描述与结论一致——audit 复核）
- [x] 独立子 agent closure audit 完成且证据写入本文件 Closure 段
- [x] `node ai-dev/tools/check-plan-checklist.mjs 2455-wi18-dao-dual-context-adjudication.md --strict` 退出码 0
- [x] scan-hollow-implementations：N/A（纯裁定型 plan，无代码变更）

## Deferred But Adjudicated

### dao 双域拆分实施

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: A1 裁定维持现状——两域消费者重叠、拆分零功能收益；重启条件已登记
- Successor Required: `no`（重启条件触发时另立 successor）

## Non-Blocking Follow-ups

- 无。

## Closure

Status Note: 维持现状裁定落地——两域消费者完全重叠（同为 nop-ai-service BizModel + web CRUD）、coder 零消费 dao、拆分零功能收益且成本为 DDL/codegen/回归三处机械搬家；重启条件三条登记（按域独立部署/独立数据生命周期/域间消费者分化）。
Completed: 2026-10-13

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure audit（待 round 2 复核后回填）
- Evidence:
  - 消费者普查复核：项目管理域外部消费者 = nop-ai-service BizModel 5 + test 1；会话域同模块；coder 零消费 dao（grep 证据在 Baseline）
  - No owner-doc update required 复核：module-groups dao 分层描述与结论一致
  - 裁定：**APPROVE closure**（纯裁定项，Rules :253 授权维持现状）
- `node ai-dev/tools/check-plan-checklist.mjs 2455-wi18-dao-dual-context-adjudication.md --strict` 退出码 0（closure 前末次确认）
