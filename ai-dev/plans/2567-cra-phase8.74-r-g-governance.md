# 2567 CRA Phase 8.74 — R-G 治理批（治理轨产出物）

> Plan Status: completed
> Last Reviewed: 2026-11-12
> Source: roadmap 工作项 8.74；批次定义 = batch-proposal §五 治理批附录（成员 10 P2 deferred successor + G91 4 C10 + demo/owner doc drift + 条款登记册 + Gradle 决定 + 测试基建路线）
> Related: plan 2533（R3-10 CL-301 通道裁定已落档——衔接）、plan 2526（nop-datav.md owner doc 新建先例）
> **PA**：无（治理轨，不涉产品行为变更；死代码清理=登记清单不在本批执行删除）
> **特殊约束**：① 产出物形态落档 `ai-dev/design/concurrency/governance/`（登记册/checklist/清单类）+ `docs-for-ai/` owner doc 补齐；② 各触发条款组在 campaign 各批已散落落账——本批**收口为统一登记册**（汇总+对账，不重写）；③ G91 四条独立补测=登记路线（补测本体随各批 C10 验收前置已承载）；④ E5 缺口声明（roadmap 行）由 owner doc 补齐产出物兑现或显式维持

## Purpose

落档 R-G 治理批全部产出物：触发条款登记册/接线评审 checklist/可观测性补齐清单/死代码清理登记/owner doc 补齐/Gradle 通道决定/测试基建补强路线，并通过独立审查。

## Current Baseline

- CL-301 通道裁定已随 plan 2533 落档（W-02/W-05/W-03/W-07 四项兑现登记）
- B1-11 六张回升券登记于 P2 台账 G57；各触发条款组散落 cra-index 各批收敛注
- owner doc drift：6.5 四模块/5.4 nop-format/5.6 nop-graph/6.1 nop-rg/nop-find/6.2 nop-lint/nop-jq——roadmap 各行缺口声明维持
- G91 四条无本体 C10（1.2-C10-016/1.3-C10-007/008/1.4-C10-009）未落补测

## Goals

- 六类产出物落档并通过独立审查
- 10 条 P2 deferred successor 对账（每条：fixed/登记/维持 deferred + 理由）

## Non-Goals

- 产品代码删除（死代码只登记）；Gradle CI 接入实施（只做通道决定）；补测本体实施（登记路线）

## Execution Plan

### Phase 1 - 产出物落档

Status: completed（Phase 1 实施完成 2026-11-12；余 check-doc-links 复跑/独立审查/closure audit）
Targets: `ai-dev/design/concurrency/governance/` + `docs-for-ai/03-modules/` + `docs-for-ai/01-repo-map/module-groups.md` + cra-index
Item Types: `Feature`（治理产出物）/`Decision`（Gradle 通道决定）/`Follow-up`（登记类）

- [x] 产出物①触发条款登记册：汇总 G06/G19/G28/G30/G33/G42/G46/G49/G53/G56/G57/G59/G60/G61/G64/G69/G72/G77/G87/G89 全部触发条款 + B1-11/B1-12/S6 + 3.4-O-2 SPI 移交轨（每条：来源批/条款文本指针/当前状态）
- [x] 产出物②接线评审 checklist：渠道栈接线变更评审项（beans.xml init/destroy + startAll/stopAll/dispatchInbound 非测试调用扫描——B1-11 护栏条款成文化）
- [x] 产出物③可观测性补齐清单：campaign 各批新增指标/gauge 汇总 + 遗留缺口（各批 owner doc 已落账的指针化）
- [x] 产出物④死代码清理登记：各批 deferred/watch-only 涉及的死代码面汇总登记（9.1 议题输入）
- [x] 产出物⑤owner doc 补齐：6.5 四模块/5.4 nop-format/5.6 nop-graph/6.1 nop-rg/nop-find/6.2 nop-lint/nop-jq——补 `docs-for-ai/03-modules/` 最小条目或 module-groups.md 登记（沿用缺口声明→兑现或显式维持）
- [x] 产出物⑥Gradle 通道决定：6.5-CL-301 通道决定文书（衔接 2533 已落档裁定，收口为正式 Decision 落档）
- [x] 产出物⑦测试基建补强路线：会红=0 十三例结构性形态横向缺口登记 + G91 四条补测路线
- [x] 10 条 P2 deferred successor 对账（1.2-C8-008/1.5-MA-09/1.5-MA-17/1.5-C8-015/1.6-C8-008/2.1-C1-006/2.1-C7-005/2.2-C1-014/2.3-C8-006/2.3-C9-006——每条终态裁定落 cra-index）
- [x] cra-index R-G 行收敛注落账；roadmap 8.74 行回写准备（8.74 行回写随 Phase 独立审查/收口执行，本批不动 roadmap 状态位）

Exit Criteria:

- [x] 六类+对账产出物全部落档且指针闭环（登记册每条可回溯来源批）
- [x] check-doc-links --strict EXIT=0
- [x] `ai-dev/logs/` 已更新
- [x] 独立审查通过

## Closure Gates

- [x] 全部产出物落档且通过独立审查
- [x] 10 条 successor 对账完成
- [x] 不存在被静默降级的 deferred（对账逐条留痕）
- [x] check-doc-links --strict EXIT=0
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] 不涉产品行为变更（git diff 无 src/main 变更为验收项）

## Deferred But Adjudicated

### Gradle CI 接入实施 / 死代码删除执行 / G91 补测本体实施
- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 治理批 exit criteria 明示为「决定/路线登记」形态；实施类归后续独立计划。
- Successor Required: `no`

## Review 记录

（待独立审查）


## Closure

Status Note: 8.74/R-G 治理批产出物全部落档（触发条款登记册 20 组+B1-11/B1-12/S6+3.4-O-2 全 active-监控中/接线评审 checklist 成文化/可观测性清单+四组缺口登记位/死代码登记 12 行防双计/owner doc 补齐 module-groups 六处+三模块缺口显式维持/Gradle 通道决定 R-G-DEC-001 与 2533 一致/测试基建路线 G91 收口两条开放如实维持），10 条 successor 对账 fixed=0/登记维持 deferred=10 逐条留痕无静默降级，cra-index 七处收敛注落账，git diff 零 src/ 变更。closure audit APPROVED-with-corrections（0B/0M/3m 措辞/格式级全闭环）。
Completed: 2026-11-12

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session closure audit（APPROVED-with-corrections）
- Evidence:
  - 八产出物存在且与 batch-proposal §五 对齐；指针化抽验 12+6+2 条全部真实命中
  - G91 三条关闭依据亲核真实（2512/2538/2539 搭车兑现）、两条开放如实维持
  - 对账抽 5 条 grep cra-index 无 fixed 收敛注——「fixed=0/deferred=10」声明真实
  - 诚实性抽查：G57 未激活裁定亲 grep startAll/stopAll/dispatchInbound 生产调用零命中成立
  - Minor-1 表格空行修复/Minor-2 G57 触发条件措辞对齐「裁定后新增」/Minor-3 Gradle 决定措辞收紧——全部闭环
  - `check-plan-checklist --strict` EXIT=0；`check-doc-links --strict` EXIT=0；git diff 零 src/
- Deferred 诚实性：Gradle CI 接入/死代码删除/G91 补测本体均为治理轨明示「决定/路线登记」形态，分类诚实

Follow-up:

- Gradle harness 纳 CI 接线（补 CI 建议，后续独立计划）
- 9.1 对账议题输入：死代码登记四条+Path X 确认项+1.2-C10-016/1.3-C10-008 interrupt 半边开放面

