# 2 批量面板数据查询 API（getDashboardData）

> Plan Status: active
> Mission: nop-datav
> Work Item: D1-2/D2-1 deferred follow-up — 批量面板数据查询
> Last Reviewed: 2026-08-14
> Source: D2-1 plan `2026-08-10-1030-1-dashboard-global-filter-parameters.md` Non-Blocking Follow-ups「批量面板查询 API（当前前端逐面板调用 getPanelData，批量查询为优化项）」+ D1-2 plan `2026-08-10-1000-2-dashboard-runtime-panel-data-binding-refresh.md` Non-Blocking Follow-ups「面板数据查询的 GraphQL 批量查询优化」
> Related: `ai-dev/plans/nop-datav/2026-08-10-1000-2-dashboard-runtime-panel-data-binding-refresh.md`、`ai-dev/plans/nop-datav/2026-08-10-1030-1-dashboard-global-filter-parameters.md`

## Purpose

为看板运行时提供单次调用取回全部面板数据的批量查询 API，消除「N 个面板 = N 次 GraphQL 往返」的渲染路径开销，并把看板级全局筛选一次性求值后统一映射到各面板。

## Current Baseline

以下事实均已对照 live repo 核实（2026-08-14，含独立审查轮复核）：

- 面板查询仅有单面板 action：`getPanelData(id, params)`（`NopDatavPanelBizModel.java:39-48`）与 `refreshPanel(id)`（`:53-61`），内部经 `PanelDataBinder.queryPanelData(panelId, panel, requestParams)`（另有带 rowLimit 四参重载）执行「DatasetRef → NopReportDataset → SQL → 结构化结果」绑定管线。
- 看板级筛选已就绪：`NopDatavDashboardBizModel.resolveFilterValues`（`:126-135`）+ `DashboardFilterResolver`（static `resolve`，`DashboardFilterResolver.java:42`）。`resolve()` 输出按设计可直接作为 `getPanelData` 的 requestParams（`DashboardFilterResolver.java:22-23` javadoc），故「批量一次求值」与「逐面板 resolve+getPanelData 组合」在参数流上结构等价（锚点是 resolve 过滤后的组合语义，非裸 getPanelData 的任意参数）。
- **既有批量迭代先例**：`PanelDataExporter.exportDashboard`（`PanelDataExporter.java:148-191`）已实现「按 dashboardId+sortOrder 加载全部面板 → **过滤掉 needsDataset=false 面板** → 顺序迭代 PanelDataBinder → 任一面板失败整体抛错」。其面板加载顺序与排序方式可参照；但其「排除无数据集面板」与「整体失败」两点语义与批量查询 API 的目标（与逐面板调用一致、部分失败隔离）**有意分歧**，须按 D3 裁定而非照抄。
- owner doc 现状契约：`ai-dev/design/nop-datav/linkage-design.md:76`「不重建查询逻辑，不引入批量查询（前端逐面板调用 getPanelData）」+ §七 拒绝方案表「后端批量查询 API」——本计划落地后这两处陈述将过期，须同步（见 Phase 1/3）。
- GraphQL action 层面无任何批量/聚合查询入口（全仓无 `getDashboardData` 或等价 action）。
- 该缺口被两份已关闭 plan 独立 defer（见 Source），均为 backend 侧可落地项，无 flux 依赖。

## Goals

- 新增 `getDashboardData(dashboardId, params, panelIds?)` 批量查询 action：一次调用返回该看板（或指定 panelIds 子集）各面板的 `PanelDataResult`。
- 看板级筛选参数一次求值、统一应用到全部面板（与逐面板 `resolveFilterValues` + `getPanelData` 组合语义一致）。
- 部分失败语义：单个面板查询失败不拖垮整个批量响应（失败面板在响应中携带显式错误信息，成功面板正常返回）。
- 面板纳入集合与逐面板语义一致：包含无数据集面板（text/iframe 等返回 `hasDataset=false` 条目），与 `exportDashboard` 的排除语义有意区分。
- 面板数量上限可配置，超限显式拒绝。
- 复用既有 `PanelDataBinder` 数据绑定管线，不重复实现查询逻辑。

## Non-Goals

- 前端对接（flux 侧批量渲染改造）。
- 查询结果缓存 / nop-report 缓存复用（独立优化项，另行评估）。
- 并行查询（线程池化）——本计划 Purpose 是消除往返开销，顺序执行即满足；并行列为后续优化（见 Deferred『并行查询』）。
- 跨看板批量查询。
- 面板数据订阅 / WebSocket 推送。
- 联动（resolveLinkage）与跳转（resolveJump）的批量化。

## Scope

### In Scope

- `NopDatavDashboardBizModel`（或 D1 裁定的归属 BizModel）新增批量查询 action + `@Auth` + `nop-datav.action-auth.xml` 权限点 + 对应 biz 接口（`INopDatavDashboardBiz.java`）方法声明。
- 批量结果 DTO（落 `nop-datav-dao` 的 `io.nop.datav.biz` 包，镜像 PanelDataResult/LinkageResult 先例）。
- 上限配置项（`NopDatavConfigs`）。
- owner docs：`runtime-design.md` 批量查询章节 + `linkage-design.md` 两处过期陈述同步。

### Out Of Scope

- 前端消费方式与渲染（flux）。
- 缓存、并行、订阅、联动批量化（见 Non-Goals）。

## Execution Plan

### Phase 1 - 设计裁定与 owner doc 增补

Status: planned
Targets: `ai-dev/design/nop-datav/runtime-design.md`、`ai-dev/design/nop-datav/linkage-design.md`

- Item Types: `Decision`

- [ ] D1 action 归属裁定：`getDashboardData` 放 `NopDatavDashboardBizModel`（看板视角）还是 `NopDatavPanelBizModel`（面板视角）——倾向前者（输入输出均以看板为单位）
- [ ] D2 面板集合语义裁定：默认「看板全部面板」+ 可选 `panelIds` 子集过滤；panelIds 中不存在/不属于该看板的 id 的处理（显式报错 vs 忽略并标注）二选一，禁止静默忽略不标注
- [ ] D3 响应形态与面板纳入集裁定：响应结构必须能区分「面板级失败」与「看板级失败」（如看板不存在 → 整体报错；某面板 datasetRef 失效 → 该面板条目携带错误码）；响应包含看板全部面板（含无数据集面板的 `hasDataset=false` 条目），与逐面板语义一致、与 exportDashboard 排除语义有意区分；DTO 形态写入 design doc
- [ ] D4 上限与执行模式裁定：默认上限值与超限错误码（防单请求放大为海量 SQL）；N 个面板默认顺序执行（对齐 `exportDashboard` 先例），并行明确列为后续优化
- [ ] D5 数据源与发布状态语义裁定：批量入口读 live 表还是已发布快照、是否要求 PUBLISHED——既有组合语义用 live 表（getPanelData 加载 live panel，不检查 publishStatus），默认裁 live 表 + `requireEntity` 存在性校验，发布非前置
- [ ] `runtime-design.md` 增补「批量面板查询」章节（最终结论式）
- [ ] `linkage-design.md` 同步两处过期陈述：§三 `:76` 附近「不引入批量查询」与 §七 拒绝方案表「后端批量查询 API」——改写为「批量 API 作为优化层落地，逐面板 getPanelData 仍为基础查询模型」

Exit Criteria:

- [ ] 5 项 Decision 均有明确裁定并写入 `runtime-design.md` 对应章节
- [ ] `linkage-design.md` 两处陈述已同步（repo-observable：不再含「不引入批量查询」的绝对化契约）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `ai-dev/logs/` 对应日期条目已更新
- [ ] No new test required: 纯 Decision/文档 Phase，行为测试落在 Phase 2-3

### Phase 2 - 批量查询实现

Status: planned
Targets: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/`、`nop-datav/nop-datav-dao/src/main/java/io/nop/datav/biz/INopDatavDashboardBiz.java`、`nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/NopDatavConfigs.java`、`nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/NopDatavErrors.java`、`nop-datav/nop-datav-web/src/main/resources/_vfs/nop/datav/auth/nop-datav.action-auth.xml`

- Item Types: `Proof`

- [ ] 实现 `getDashboardData(dashboardId, params, panelIds?)`：按 D5 校验看板存活与权限（`@Auth` + 既有权限模式）→ 看板级筛选一次求值 → 按 D3 纳入集加载面板（排序参照 exportDashboard 先例：dashboardId + sortOrder）→ 逐面板复用 `PanelDataBinder` → 按 D3 形态聚合响应
- [ ] 上限校验与超限显式拒绝（按 D4）
- [ ] biz 接口方法声明 + 批量结果 DTO（`io.nop.datav.biz` 包）+ `nop-datav.action-auth.xml` 权限点（镜像 `NopDatavPanel:getPanelData` 的角色绑定）
- [ ] 上限配置项加入 `NopDatavConfigs`

Exit Criteria:

- [ ] 测试证明：多面板看板单次调用返回全部面板数据，且各面板结果与「resolveFilterValues + getPanelData」组合语义一致（同一筛选参数下）
- [ ] 测试证明：全局筛选参数一次求值后对所有面板生效（改变筛选值 → 多面板结果同步变化）
- [ ] 测试证明：单面板失败（如 datasetRef 失效）时其余面板正常返回、失败面板条目携带显式错误信息（按 D3 形态断言）
- [ ] 测试证明：无数据集面板（text/iframe）在响应中携带 `hasDataset=false` 条目而非被排除
- [ ] 测试证明：panelIds 子集按 D2 裁定行为执行（不存在/不属于该看板的 id 的处理有断言）
- [ ] 测试证明：超限请求显式拒绝（断言错误码）
- [ ] **接线验证**：action 经 biz 层入口调用真实 `PanelDataBinder` 管线（非 mock 绕过）
- [ ] **无静默跳过**：任何失败路径显式返回错误信息，无空结果静默填充
- [ ] owner-doc 更新由 Phase 1 裁定章节与 Phase 3 一致性核对覆盖（本 Phase 契约细节以 Phase 1 已写入的 DTO 形态为准，无独立 owner-doc 写作项）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 端到端验证与文档收口

Status: planned
Targets: `nop-datav/nop-datav-service/src/test/`

- Item Types: `Proof`

- [ ] 端到端测试：创建看板（多面板 + 含无数据集面板 + 全局筛选参数）→ 单次 `getDashboardData` → 断言筛选前后结果差异、无数据集面板条目存在、面板级错误隔离

Exit Criteria:

- [ ] **端到端验证**：从 biz action 入口到 SQL 执行到批量结果回传的完整路径测试存在且通过
- [ ] `runtime-design.md` / `linkage-design.md` 章节与实现一致（Phase 1 裁定无漂移）
- [ ] `./mvnw test -pl nop-datav/nop-datav-service -am` 全绿
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 批量 API 行为与 5 项 Decision 裁定一致，owner docs（runtime-design + linkage-design）同步无漂移
- [ ] 部分失败隔离、面板纳入集、子集语义、上限拒绝均有 focused tests
- [ ] 与逐面板组合调用语义一致性有测试保障
- [ ] 不存在被静默降级到 deferred 的 in-scope 项
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：批量入口到各面板数据绑定管线运行时连通，无空壳聚合
- [ ] `./mvnw compile -pl nop-datav/nop-datav-service -am` 通过
- [ ] `./mvnw test -pl nop-datav/nop-datav-service -am` 通过
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### 查询结果缓存（nop-report 缓存复用）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 批量 API 消除的是往返开销；缓存消除的是重复计算，二者正交。nop-report 侧可复用缓存抽象尚未经调研确认，宜独立评估。
- Successor Required: `no`
- Successor Path: —

### 并行查询（线程池化面板执行）

- Classification: `optimization candidate`
- Why Not Blocking Closure: Purpose 是消除 N 次往返，顺序执行已达成；并行引入连接池占用与并发语义新风险面，需独立评估与测试设计。
- Successor Required: `no`
- Successor Path: —

## Non-Blocking Follow-ups

- flux 前端切换到批量 API 的渲染改造（D1-4/D2-4 对接时一并考虑）。
- nop-report 数据集查询缓存复用调研（见 Deferred）。
- `schedule-report-design.md:171` 引用 `PanelDataExporter.java:107` 的行号锚点已过期（exportDashboard 经 cancel-checker 重构后位于 :148-191）——与本计划变更无关的既有 owner-doc drift，留待 doc-audit 或下次触碰该文档时顺手修正。

## Closure

Status Note: 
Completed: 

Closure Audit Evidence:

- Reviewer / Agent: 
- Evidence: 

Follow-up:

- 
