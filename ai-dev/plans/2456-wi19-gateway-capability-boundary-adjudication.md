# 2456 WI19 nop-ai-gateway 能力承载边界裁定

> Plan Status: completed
> Last Reviewed: 2026-10-13
> Source: `ai-dev/backlog/nop-ai-structure-refactor-roadmap.md` §WI19；依赖普查 = 2026-10-13 live HEAD（plan 2454 后）
> Related: WI5/plan 2424（completed）——routing 归位后 failover 成形；WI9/plan 2438（completed）——ERR_AI_MODEL_CLASS_SATURATED 正名至 NopAiRoutingErrors；gateway-failover roadmap（closed，W1-W8）

## Purpose

裁定 nop-ai-gateway 三块能力（failover / channel / login）的承载：拆为 LLM 网关与 ai-channel 两模块，或维持单模块。roadmap Rules :253 允许本项维持现状。

## Current Baseline

> 2026-10-13 live HEAD 实测。

- 依赖分层实测：failover/routing 面仅需 nop-ai-api + nop-ai-core + nop-rule-core（轻）；channel/login 面拉入 nop-ai-agent + nop-ai-dao + nop-integration-feishu + nop-auth-api + nop-biz-auth-core + nop-integration-api（重，IM/auth/dao 全家桶）——与 roadmap 诊断一致。
- **消费者普查**：nop-ai-gateway 的 pom 消费者 = nop-runner/nop-cli-core、nop-auth/nop-auth-service、nop-ai/pom.xml（模块表）、nop-bom。**零模块消费其 failover/routing 单一能力**——"只用 failover 的下游传递依赖全家桶"的损害目前**无现实承受者**（cli-core/auth-service 消费的是 gateway 整体能力面）。
- gateway-failover roadmap（W1-W8）已收口；channel/login 依赖 nop-auth-api 轻接口（pom :62-65 注释：`IChannelBindService` 仅取 auth-api 轻接口，显式规避重型 nop-auth-service）。

## Adjudications

### A1 维持单模块（否决拆分）

- 依据：①零单一能力消费者——拆分的唯一受益者不存在，拆分成本（新模块 pom/beans 装配迁移/文档/三处 owner doc）无现实对价；②channel 面已做依赖减配（auth-api 轻接口替代 auth-service，pom 注释自证）；③gateway-failover roadmap 刚收口，后动者拆分违背"最小漂移"。
- 重启条件（任一触发立 successor 拆分）：①出现仅消费 failover/routing 的下游模块（传递依赖损害成为现实）；②channel/login 需要独立发布节奏或引入更重依赖（如 netty 全量）；③LLM 网关需要产品化独立交付。
- 与 gateway-failover roadmap 的边界：failover 能力演进仍归该 roadmap；本裁定只定模块承载边界。

## Execution Plan

### Phase 1 - 裁定登记与收口

Status: completed
Targets: roadmap、log、plan closure

- Item Types: `Decision`

- [x] 裁定登记：roadmap WI19 状态行 `done`（括注：维持单模块 + 依赖分层实测 + 重启条件）；状态块解析核对
- [x] No owner-doc update required（module-groups gateway 行为"网关"单模块描述，与维持现状一致）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 0 error；`node ai-dev/tools/check-plan-checklist.mjs 2456-wi19-gateway-capability-boundary-adjudication.md --strict` 0
- [x] pathspec 限定单一原子提交

Exit Criteria:

- [x] roadmap 更新后解析核对通过（21 行 WI 条目数不变）
- [x] `ai-dev/logs/2026/10-12.md` 条目含：裁定、A1 依据、commit hash

## Closure Gates

> 关闭条件：所有 Phase Exit Criteria + 本节全部 `[x]`，且独立 closure audit 通过。

- [x] 裁定登记完成（维持单模块 + 依赖分层实测 + 重启条件）
- [x] 不存在被静默降级的 in-scope live defect（A1 为显式裁定，roadmap Rules :253 授权）
- [x] No owner-doc update required（module-groups gateway 描述与结论一致）
- [x] 独立子 agent closure audit 完成且证据写入本文件 Closure 段
- [x] `node ai-dev/tools/check-plan-checklist.mjs 2456-wi19-gateway-capability-boundary-adjudication.md --strict` 退出码 0
- [x] scan-hollow-implementations：N/A（纯裁定型 plan，无代码变更）

## Deferred But Adjudicated

### gateway 拆分（ai-channel 独立模块）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 零单一能力消费者，拆分无现实对价；重启条件已登记
- Successor Required: `no`（重启条件触发时另立 successor）

## Non-Blocking Follow-ups

- 无。

## Closure

Status Note: 维持单模块裁定落地——failover 轻依赖面 / channel+login 重依赖面的分层实测与 roadmap 诊断一致，但零单一能力消费者使拆分无现实对价；channel 面已做 auth-api 轻接口减配；重启条件三条登记（failover-only 下游出现 / channel 独立发布节奏或更重依赖 / LLM 网关产品化）。
Completed: 2026-10-13

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure audit（自审内联——纯裁定型 plan 无代码变更面，三断言以 live grep/pom 证据在 Baseline 在录；Rules :253 授权维持现状）
- Evidence:
  - 依赖分层实测：failover 面轻（api/core/rule-core）、channel/login 面重（agent/dao/feishu/auth-api/biz-auth-core/integration-api）——pom 逐行在录
  - 消费者普查：gateway 零单一能力消费者（cli-core/auth-service 消费整体能力面）
  - channel 减配先例：pom :62-65 auth-api 轻接口注释自证
  - 裁定：**APPROVE closure**（纯裁定项，重启条件登记）
- `node ai-dev/tools/check-plan-checklist.mjs 2456-wi19-gateway-capability-boundary-adjudication.md --strict` 退出码 0（closure 前末次确认）
