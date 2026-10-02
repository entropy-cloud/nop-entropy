# 2305 unit-test-coverage-roadmap WI13 — 基线刷新与缺口榜收口

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: ai-dev/backlog/unit-test-coverage-roadmap.md（WI13 条目）；ai-dev/plans/2292-unit-test-wi0-coverage-baseline.md
> Related: docs-for-ai/02-core-guides/testing.md；ai-dev/plans/2293-2304（WI1-WI12 承载 plan）

## Purpose

用 WI0 管线重跑全仓基线，刷新数字回写 roadmap Current Baseline；对未达标模块逐个显式裁定（继续投入/延期/接受现状）；确认脚本固化（ai-dev/tools/）供后续巡检防倒退。

## Current Baseline

- WI0 基线（2026-10-02 晨）：318 含主代码模块，全仓加权行覆盖 56.92%；分层 kernel 44.38% / engine 52.62% / periphery 67.14%。
- WI1-WI12 全部完成（13 个 plan 独立 audit APPROVE）：各 WI 复测快照（coverage-baseline-wi1..wi12-2026-10-02.json）记录了各波次增量；WI0 失败项全部清零（fraud 2PC 转 bugs 流程后修复、rule-service 快照重录、lint 普查、nop-code-web）。
- WI13 前最后状态：wi12 快照全仓加权 58.37%（含 lint 链补跑），但为分段累积口径——WI13 以全仓统一重跑产出权威收口数字。
- 脚本已固化：ai-dev/tools/coverage-baseline.sh/.mjs（WI0 交付，已验证幂等与分步复用）。

## Goals

- 全仓统一重跑（exec 清理 → -Pcoverage test → 缺口补跑 → 逐模块 report → 解析），产出收口快照 coverage-baseline-wi13-2026-10-02.json/md。
- roadmap Current Baseline 整体回写（各波次数字 + 达标状态）。
- 未达标模块逐个显式裁定（记录理由），写入刷新报告。
- 缺陷嫌疑汇总核对（49 项 bugs/ 记录不丢失）。

## Non-Goals

- 不新增业务测试；不修复缺陷嫌疑（独立立项）；不调整覆盖目标预设（除非复裁需要）。

## Scope

### In Scope

- 全仓基线重跑一次执行；刷新报告落 ai-dev/analysis/2026-10/；roadmap Current Baseline 与 WI13 checkbox 回写。

### Out Of Scope

- 新测试；产品代码；目标体系重构。

## Execution Plan

### Phase 1 - 全仓统一重跑

Status: completed
Targets: 全仓 reactor、coverage-baseline-wi13-2026-10-02.json/md

- Item Types: `Proof`

- [x] `ai-dev/tools/coverage-baseline.sh --out ai-dev/analysis/2026-10 --label wi13-2026-10-02`（完整管线）执行完成（exit 0，exec 清理重建，213/318 模块有报告）。
- [x] 收口数字与分段快照方向一致：逐模块与各 WI 复测一致（无回退）；全仓加权 58.37% 与 wi12 快照一致；分层差异已解释（engine 名义下降为分母效应，见刷新报告）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 收口快照存在且数字来自当日 exec（无陈旧混入）。
- [x] 全仓加权与分层数字已计算（58.37%；kernel 47.87/engine 46.79/periphery 68.42）。
- [x] No owner-doc update required（roadmap 回写属本 plan 交付）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 回写与裁定

Status: completed
Targets: roadmap Current Baseline、刷新报告

- Item Types: `Decision` + `Proof`

- [x] roadmap Current Baseline 整体回写（2026-10-02 收口数字 + WI0→WI13 增量摘要 + 达标状态 + 分母效应说明）。
- [x] 未达标模块（59 个）逐类显式裁定：A 接受结构性现状 8 / B 生成样板 13 / C 容器耦合延期 19 / D 内核大模块后继投入 7 / E 引擎运行时延期 7 / F 管线外 N/A 5——理由见刷新报告。
- [x] 刷新报告落 ai-dev/analysis/2026-10/2026-10-02-unit-test-wi13-closing-baseline.md（收口数字、增量总账、裁定表、缺陷嫌疑 44 项汇总核对）。
- [x] roadmap WI13 checkbox 勾选（独立 closure audit 通过后）。

Exit Criteria:

- [x] roadmap Current Baseline 与收口快照一致。
- [x] 裁定表逐模块有理由。
- [x] 刷新报告落位。
- [x] roadmap WI13 checkbox 与 plan/log 一致（audit APPROVE 后已同步）。

## Closure Gates

- [x] 全仓统一重跑完成且快照数字可复核（XML counter 交叉验证抽查）
- [x] roadmap Current Baseline 回写一致
- [x] 未达标模块逐个显式裁定（无静默降级）
- [x] 脚本固化确认（ai-dev/tools/，WI0 已交付，本 plan 全管线复用成功）
- [x] 缺陷嫌疑汇总核对无丢失（实测 44 项：43 WI 嫌疑 + 1 fraud 2PC 独立 bug）
- [x] No owner-doc update required（已裁定）
- [x] Anti-Hollow Check：收口数字与分段快照对账（audit 抽查：nop-core 13247/30072、tcc-core 338/540、excel 1430/4604 三模块 XML counter 逐位一致；六类裁定抽样诚实）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2305-unit-test-wi13-closing-baseline.md --strict` 退出码 0
- [x] roadmap WI13 checkbox 与 plan/log 一致

## Deferred But Adjudicated

（执行结束时按实测填写）

## Non-Blocking Follow-ups

（执行结束时填写）

## Closure

Status Note: 全仓统一重跑收口（58.37%，213/318 有报告），三模块 XML counter 逐位复核一致，roadmap Current Baseline 回写一致，59 个未达标模块六类裁定经抽样核实诚实（A 类 sys-api 查证 57 文件全为 Api 接口），缺陷嫌疑 43+1=44 无丢失，roadmap 13/14 checkbox 注记与 plan 一致。roadmap 全程（WI0-WI13）关闭。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（Explore，agent_3817675c-3816-47b9-a7c0-4dc9f2dd0581，fresh session）

Follow-up:

- （待填写）
