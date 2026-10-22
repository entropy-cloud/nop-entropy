# 2502 WI16 端到端验收与文档收口（nop-report-capability-roadmap）

> Plan Status: completed
> Last Reviewed: 2026-10-22
> Source: `ai-dev/backlog/nop-report-capability-roadmap.md` WI16

## Purpose

全链路验收（参数化 WI2 + 权限 WI1/WI3 + HTML 分页打印 WI4 + 缓存 WI6 + 交互/子报表 WI5/WI9 + 新导出 WI10 组合场景）与文档收口（owner doc 能力矩阵、runbook、INDEX/模块登记/源码锚点、logs）。

## Current Baseline

- 各 WI 端到端测试已散落落地：auth e2e（TestReportAuthWiring）、参数表单（TestReportFormBizModel）、数据安全（TestReportDataSecurity）、缓存（TestReportResultCache）、条件样式（TestCondStyles）、子报表（TestSubreport）、导出（TestExportFormats/TestPngExport）、分页 golden（accept.mjs）、交互（interact.mjs）。
- **缺一个组合场景验收**：单模板串联 参数+权限+子报表+条件样式+多格式导出 的综合 AutoTest。
- owner doc 能力面已随各 WI 更新；INDEX/模块登记（module-groups.md）/source-anchors 的 nop-report 路由核对未做。

## Goals

- 组合验收测试 `TestE2EAcceptance`（service 基座）：注册定义（rptText 路径）→ 授权用户经缓存 download（xlsx + fods）→ html 渲染含参数文本/条件样式类/子报表内容 → 未授权用户被拒。
- 文档收口：INDEX.md / module-groups.md / source-anchors.md 的 nop-report 路由核对（缺失则补）；owner doc 能力矩阵终版核对；roadmap WI16 勾选 + 总 Milestone；logs 收口。

## Non-Goals

- 新能力；Playwright e2e（各 WI 已有浏览器级验收）。

## Execution Plan

### Phase 1 - 组合验收测试

Status: completed
Targets: nop-report-service

- Item Types: `Proof`

- [x] `TestE2EAcceptance`：组合场景断言（授权/未授权、缓存命中、参数、条件样式、子报表、fods 导出）
- [x] docs 核对：INDEX/module-groups/source-anchors nop-report 路由

Exit Criteria:

- [x] `./mvnw test -pl nop-report/nop-report-service -am` 全绿
- [x] docs 路由核对完成（缺失补齐）
- [x] `ai-dev/logs/` 条目更新

### Phase 2 - roadmap 收口

Status: completed
Targets: roadmap/logs

- Item Types: `Proof`

- [x] roadmap WI16 勾选 + 总 Milestone「端到端验收通过」勾选（口径注明：WI13/14/17 显式裁定转独立立项/backlog）
- [x] logs 收口；check-plan-checklist --strict；独立 audit

Exit Criteria:

- [x] roadmap 状态一致（勾选 + 口径）
- [x] `ai-dev/logs/` 条目更新

## Closure Gates

- [x] 组合验收测试全绿
- [x] docs 路由核对完成
- [x] roadmap 总 Milestone 勾选
- [x] 独立子 agent closure audit 完成并记录证据
- [x] `./mvnw test -pl nop-report/nop-report-service -am` 绿
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退 0
- [x] `ai-dev/logs` 收口记录
- [x] `check-plan-checklist.mjs --strict` 收口运行

## Closure

Status Note: WI16 端到端验收与文档收口完成，roadmap 执行收口。审计中发现并修复 roadmap 两处派生状态不一致：①WI16 条目自身 checkbox 未勾（与头部 Last updated、总 Milestone「端到端验收通过」矛盾）；②派生 Milestone「分析与展现增强达标」（WI8–WI9）与「产品化与生态增强完成」（WI13/14 显式裁定 + WI15 完成）未按 roadmap 规则「Milestone 勾选是所有依赖 WI 完成后派生」勾选。已原地补勾，其余状态核对一致（WI0–WI12/WI15 全勾，WI13/14/17 未勾且带显式裁定记录）。
Completed: 2026-10-22

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure auditor subagent（新会话，ZCode agent sess_subagent_agent_b68b9349-0380-4ce6-8af2-8a80b99b8f27，model GLM-5.3-Flash）
- Evidence（2026-10-22 实测）:
  1. 组合验收测试 PASS：`./mvnw test -pl nop-report/nop-report-service -Dtest=TestE2EAcceptance` 退 0；`./mvnw test -pl nop-report/nop-report-service -am` BUILD SUCCESS——core 125 tests 0 失败，service 29 tests 0 失败（1 skipped 为 TestLargeDatasetExport 既有跳过），`TestE2EAcceptance` 2/2（testCombinedScenario、testUnauthorizedDenied）。
  2. docs 路由 PASS：`docs-for-ai/INDEX.md` L211 nop-report 路由；`docs-for-ai/01-repo-map/module-groups.md` L16/L69/L73 模块登记；`docs-for-ai/04-reference/source-anchors.md` L108 REPORT-001 renderType 清单含 CSV/FODS/RTF/PNG；owner doc `docs-for-ai/03-modules/nop-report.md` 能力矩阵（L19/L20/L25）与各 WI 交付一致（csv/fods/rtf/png、条件样式、子报表、缓存、i18n、条码）。
  3. roadmap PASS（含修复）：`ai-dev/backlog/nop-report-capability-roadmap.md` 总 Milestone「端到端验收通过」[x] 且口径注明 WI13/14/17 显式裁定；审计补勾 WI16 条目 + M4/M6 派生 Milestone 后全文件勾选一致。
  4. `node ai-dev/tools/check-doc-links.mjs --strict` 退 0（0 errors / 0 warnings，4195 files）。
  5. 文本一致性 PASS：plan Phase 1/2 Status: completed、全部 Exit Criteria 已勾且与实测相符；本 Closure 段填写后 `check-plan-checklist.mjs --strict` 复核退 0。
  6. Anti-Hollow PASS：`TestE2EAcceptance`（nop-report/nop-report-service/src/test/java/io/nop/report/service/TestE2EAcceptance.java）真实走通链条——建表/注册定义 + viewer 角色授权 → alice html 渲染断言参数文本「验收参数」→ `NopReportCache__download` xlsx + fods 真实文件落盘 → 二次 download 断言命中同文件路径 → fods 文件内容断言 Flat ODF `office:document` → mallory（intruder 角色）`getXptModel` 抛 `ERR_REPORT_AUTH_REPORT_DENIED` 精确错误码；非空壳断言。
