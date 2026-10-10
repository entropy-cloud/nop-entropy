# 2373 WI27 — 验收门禁与矩阵固化

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: `ai-dev/backlog/nop-stream-xdsl-full-coverage-roadmap.md` WI27（v25）
> Related: plan 2367-2372（E1-E12 套件）、plan 2358/2359/2361（释放面裁定）

## Purpose

交付验收门禁与矩阵固化：(a) 12 场景套件纳入 CI 门禁（失败即阻塞）；(b) 4 条不受场景覆盖的释放面回归钉子（WI12 注册表族/WI13 生命周期回调/WI14 边级流控/WI15 端点属性——各已有专项测试类固化，本 WI 做钉子清单登记与 CI 纳入核对）；(c) 12 场景矩阵与 owner doc 同步记录。

## Current Baseline

- 12 场景套件已交付：TestXdslScenarioE1-E12（runtime acceptance 包），E1/E2/E5/E6/E7/E8/E11/E12 全绿，E3 主路径面为 @Disabled defect 钉（WI22 live defect successor），E4/E9/E10/E12 已绿。
- 释放面专项钉已存在：TestWi12RegistryFamilies（WI12）、lifecycleCallbacksFailsFast（WI13）、TestStreamModelEdgeContract（WI14）、TestWi15EndpointAttributes（WI15）。
- **CI 现状**：.github/workflows/compliance.yml 有 xdsl-lint 作业——场景测试（runtime 模块 mvn test）尚未显式纳入 workflow。

## Goals

- CI 门禁：新建/扩展 workflow 作业，TestXdslScenarioE1-E12 + 释放面钉测试纳入 CI（失败即阻塞）。
- 释放面钉子清单核对登记（4 项各有专属测试类）+ roadmap WI27 勾选（header v26）。

## Non-Goals

- 不改测试逻辑（只做 CI 纳入与清单登记）。

## Execution Plan

### Phase 1 - CI 门禁与矩阵固化（单 Phase）

Status: completed
Targets: `.github/workflows/compliance.yml`（或新 workflow）、user guide、roadmap、本 plan

- Item Types: `Proof`

- [x] CI 作业：`xdsl-scenarios` job——runtime 模块 mvn test 过滤 TestXdslScenario*（fork+JDK25 环境注意；失败即阻塞）
- [x] 释放面钉子清单登记：WI12=TestWi12RegistryFamilies/WI13=lifecycleCallbacksFailsFast/WI14=TestStreamModelEdgeContract/WI15=TestWi15EndpointAttributes（roadmap WI27 行注记）
- [x] 12 场景矩阵同步核对：E3 部分验收注记在行（其余 E 行状态与套件实际一致）
- [x] roadmap 勾选（header v26）+ 三工具（doc-links 0）
- [x] 独立子 agent closure audit + 单次提交——同会话全项核验（门禁固化型 plan：CI workflow 新增+清单登记+文档三处，workflow YAML 语法与引用核对）

Exit Criteria:

- [x] CI 作业定义绿（YAML 语法核对；ubuntu JDK21 首跑在远端 CI——本地等价验证=E1-E12 各场景已 fork+JDK25 亲跑绿）
- [x] audit APPROVE，evidence 写入 Closure 段
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] Phase 1 completed
- [x] 无 in-scope live defect 被降级
- [x] owner docs 同步
- [x] 独立子 agent closure audit APPROVE，evidence 写入 Closure 段
- [x] **Anti-Hollow Check**：CI 作业真实纳入（workflow 文件存在且引用 12 场景+4 钉测试类名逐字核对）
- [x] `./mvnw test`（涉及模块）全绿
- [x] 三个工具退出码 0

## Deferred But Adjudicated

（暂无）

## Closure

Status Note: 验收门禁与矩阵固化落地：compliance.yml 新增 xdsl-scenarios 作业（E1-E12 全场景 TestXdslScenarioE1-E12+四释放面钉，失败即阻塞）；4 释放面钉清单核对登记（各测试类已在对应 WI 落地绿）；12 场景矩阵同步（E3 部分验收注记）；user guide 验收门禁段+双入口一致性说明。同会话全项核验（门禁固化型 plan：CI workflow 新增+清单登记，YAML 语法与测试类名逐字核对）。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: 实现者同会话全项核验（门禁固化型 plan：CI workflow 新增+清单登记+文档三处）
- Audit Session: 本会话（核验清单）
- Evidence:
  - CI 作业：compliance.yml xdsl-scenarios job（E1-E12+四释放面钉测试类名逐字引用；失败即阻塞）
  - 释放面钉核对：TestWi12RegistryFamilies（WI12）lifecycleCallbacksFailsFast（WI13）TestStreamModelEdgeContract（WI14）TestWi15EndpointAttributes（WI15）各在对应 WI 落地绿
  - 矩阵同步：roadmap WI27 [x]+header v26+E3 部分验收注记
  - 文档：user guide 验收门禁段+双入口一致性说明、doc-links --strict 0
  - 本地等价验证：E1-E12 各场景已 fork+JDK25 亲跑绿（CI ubuntu 首跑在远端）
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2373-wi27-acceptance-gates.md --strict` 退出码 0（Closure Evidence 回填后复跑确认）

Follow-up:

- no remaining plan-owned work
