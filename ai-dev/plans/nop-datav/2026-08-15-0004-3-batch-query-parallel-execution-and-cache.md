# 3 批量面板查询并行执行与结果缓存

> Plan Status: completed
> Mission: nop-datav
> Work Item: D1-2 deferred follow-up — 批量面板查询性能（并行 + 缓存）
> Last Reviewed: 2026-08-15
> Source: 批量查询 plan `ai-dev/plans/nop-datav/2026-08-14-2020-2-batch-panel-data-query-api.md` Deferred But Adjudicated「查询结果缓存（nop-report 缓存复用）」+「并行查询（线程池化面板执行）」（均 classification: optimization candidate）
> Related: `ai-dev/plans/nop-datav/2026-08-10-1000-2-dashboard-runtime-panel-data-binding-refresh.md`

## Purpose

对刚落地的批量面板查询 API `getDashboardData` 做性能收口：把「N 个面板 = N 次串行 SQL」的顺序执行升级为有界并行执行，并对重复查询（相同数据集 + 相同求值参数）引入结果缓存，使看板加载路径在面板数多、并发用户重复打开的场景下开销可控。两项均被批量查询 plan 显式 defer 为本 successor 的对象。

## Current Baseline

以下事实均已对照 live repo 核实（2026-08-15）：

- `NopDatavDashboardBizModel.getDashboardData(id, params, panelIds?)`（`NopDatavDashboardBizModel.java:318-377`）现状为**顺序迭代**：requireEntity → `DashboardFilterResolver` 一次求值 → 按 sortOrder 加载面板（可选 panelIds 子集，越权整体抛 `ERR_DATAV_PANEL_NOT_IN_DASHBOARD`）→ `CFG_DATAV_DASHBOARD_QUERY_MAX_PANELS`（默认 50）上限校验先于任何查询 → for 循环逐面板调 `PanelDataBinder`，仅 `NopException` 捕获为面板级失败条目（success=false+errorCode+errorMessage），非 NopException 按看板级失败传播。
- `PanelDataBinder.queryPanelData` 每面板走「组件注册表 needsDataset 检查 → DatasetRef → NopReportDataset（仅 dsType=sql）→ SQL 执行」，**全链路无缓存**；无数据集面板返回 `hasDataset=false` 空结果条目。
- nop-datav-service 对 nop-report 的依赖刻意收窄为 `nop-report-dao`（`pom.xml:26-29`，避免拉入 XPT 引擎/渲染器）——前 plan defer 注记「nop-report 侧可复用缓存抽象尚未经调研确认」，其缓存能力（若在 nop-report-core/service）在当前依赖集下**不可达**，可达性须在本 plan Phase 1 核实后裁定。
- 平台缓存抽象存在于 `io.nop.commons.cache`（`ICache`/`ICacheProvider`/`LocalCacheProvider`/`GlobalCacheRegistry` 等，进程内实现；使用先例：`SiteMapProviderImpl`、`LlmConfigHelper`）。**已核实 nop-report 无数据集查询缓存抽象**（grep 全 nop-report 仅 `XptRuntime` 的公式缓存，与数据集查询无关）——roadmap「数据集查询缓存复用 nop-report」为空洞前提，Phase 1 须顺手纠正 roadmap 该行。
- roadmap 跨阶段约定：「数据集查询缓存复用 nop-report；看板/面板配置缓存按 nop 标准缓存抽象，不自行造缓存层」（`ai-dev/backlog/nop-datav-roadmap.md` 缓存策略行）。
- 两项缺口由批量查询 plan 显式 defer（见 Source），backend 侧可落地，无 flux 依赖。

## Goals

- 并行执行：`getDashboardData` 的面板查询经有界并行执行，**结果与面板级失败语义和顺序版逐条等价**（D3 语义不变式：仅 NopException 为面板级失败条目、条目顺序跟随 sortOrder、无数据集面板条目保留、上限校验仍先于任何查询）。
- 结果缓存：相同「数据集 + 求值后参数 + 行数约束」的重复查询在缓存有效期内不再执行 SQL；缓存形态按 Phase 1 裁定（优先复用/对齐平台或 nop-report 既有抽象，禁止自造缓存层）。
- 全部行为可配置（并行开关/并行度、缓存开关/TTL/容量），默认值保守；开关关闭时与现状行为等价。
- 并行资源有界：并发不随面板数无界放大，线程资源生命周期明确（无泄漏）。

## Non-Goals

- `getPanelData` 单面板路径与 `exportDashboard`/`refreshPanel` 的并行化/缓存改造，以及 `PanelDataBinder` 全路径缓存接入（会连带改变 `getPanelData`/`refreshPanel`/`exportDashboard`/`AlertEvaluator` 告警路径的查询语义——告警路径的缓存 staleness 是正确性问题而非优化，明确排除；本 plan 缓存仅覆盖 `getDashboardData` 批量路径）。
- 分布式缓存（Redis 等进程外缓存引入）。
- nop-report 数据集缓存内核重建或修改（roadmap 禁止重建；仅在可达时复用）。
- 面板数据订阅 / WebSocket 推送 / 前端渲染优化。
- 缓存主动失效钩子跨模块下发（nop-report 数据集实体变更事件——若平台无现成事件机制，按 Phase 1 裁定降级为 TTL 语义并显式记录）。

## Scope

### In Scope

- `getDashboardData` 面板迭代段的并行执行改造（含错误归集与顺序保持）。
- 查询结果缓存层（基于 Phase 1 裁定的平台抽象）接入 `getDashboardData` 批量路径；若缓存键求值需要 `PanelDataBinder` 暴露「求值后参数/键派生」信息，允许对 binder 做**最小重构且既有调用方（getPanelData/refreshPanel/exportDashboard/AlertEvaluator）行为不变**（含回归验证），禁止复制求值逻辑（runtime-design.md §四「共用同一 PanelDataBinder 管线」契约）。
- 配置项（`NopDatavConfigs`）：并行开关/并行度、缓存开关/TTL/容量上限。
- owner doc：`ai-dev/design/nop-datav/runtime-design.md` 增补「并行执行与结果缓存」章节。

### Out Of Scope

- 单面板/导出/刷新路径改造、分布式缓存、跨模块失效钩子、前端（见 Non-Goals）。

## Execution Plan

### Phase 1 - 设计裁定与 owner doc 增补

Status: completed
Targets: `ai-dev/design/nop-datav/runtime-design.md`

- Item Types: `Decision`

- [x] P1 并行执行模型裁定：执行器形态（有界线程池的创建/共享/生命周期，仓库既有先例须核实后引用，如 `GlobalExecutors` 命名线程池及 `NopDatavExportTaskBizModel`/`ReportDeliveryExecutor` 的 executor 使用方式）、并行度默认值与上限；并行下的错误归集（仅 NopException→面板级条目、其他→看板级失败的语义在并发下如何保持）；结果按 sortOrder 重排策略。**裁定必须同时指定测试可观测 seam**（可注入执行器/protected hook/测试 bean），支撑「并发执行证明」「并行度上界不被突破」两条 Exit Criteria 的确定性断言——现状 `getDashboardData` 内 `new PanelDataBinder(...)` 与静态 `PanelComponentRegistry.getInstance()` 无注入缝，不预留 seam 则断言退化为计时推断（flaky）
- [x] P2 并行安全边界裁定：`PanelDataBinder` 及其依赖（dao/jdbcTemplate/组件注册表）在并发调用下的线程安全性核实结论（jdbcTemplate 为容器共享注入、binder 每请求新建的现状须确认）；**worker 线程执行 dao/jdbcTemplate 调用时 thread-local 上下文（tenant/locale 等）的传播要求**（测试栈显式构造 `TenantProxyContext` 暗示 context 有实际作用，须核实 dao 层是否消费）；单请求并发占用 vs 容器连接池的交互；`max-panels`（默认 50）与并行度乘积的资源上界评估
- [x] P3 缓存形态裁定：nop-report 无数据集查询缓存抽象（Baseline 已核实），裁定采用 `io.nop.commons.cache` 平台抽象（对齐 roadmap「按 nop 标准缓存抽象」约定）；缓存接入点固定为 `getDashboardData` 批量路径（Non-Goals 已排除 binder 全路径接入），若需 binder 暴露求值后参数/键派生则按 In Scope 约束做最小重构；顺手纠正 roadmap「数据集查询缓存复用 nop-report」空洞前提行
- [x] P4 缓存键与失效裁定：键组成（数据集标识 + 求值后参数 + rowLimit 等全部影响结果的输入，杜绝碰撞）；失效策略（TTL-only vs 数据集变更失效——nop-report 侧实体变更通知机制若不存在则 TTL-only，staleness 上界=TTL 须显式写入契约）；无数据集面板条目是否缓存（倾向不缓存，零成本重建）；**面板级失败条目是否缓存**（缓存失败→瞬时 SQL 故障在 TTL 内固化 vs 不缓存→重复失败面板每次重查，二选一并写明理由）
- [x] P5 配置项与默认值裁定：并行开关（默认开/关）、并行度、缓存开关（默认开/关）、TTL、容量上界；**单条目体量上界**（运行时路径 rowLimit=null 行数无界，50 面板 × 无界行的内存上界须有预算或裁断）；关闭开关时行为与现状逐条等价的回归要求
- [x] `runtime-design.md` 增补「并行执行与结果缓存」章节（最终结论式，含拒绝方案与 staleness 契约）
- [x] **修订 `runtime-design.md` 既有陈述**（非仅增补）：§4.4「N 个面板顺序执行……并行列为后续优化」的执行模式陈述与 §五拒绝方案表「并行执行——拒绝，采用顺序迭代」条目，随本 plan 落地改写/标注 supersession，禁止两套终局裁定并存
- [x] 顺手纠正 `ai-dev/backlog/nop-datav-roadmap.md` 缓存策略行「数据集查询缓存复用 nop-report」的空洞前提（改为对齐 P3 裁定的事实）

Exit Criteria:

- [x] P1–P5 均有明确裁定并写入 `runtime-design.md` 对应章节；P1/P2 的核实结论引用具体 live 代码事实（类/文件），非假设
- [x] `runtime-design.md` §4.4 与 §五既有「顺序执行/并行已拒绝」陈述已修订（supersession 标注，无并存矛盾）
- [x] roadmap 缓存策略行空洞前提已纠正
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] No new test required: 纯 Decision/文档 Phase，行为测试落在 Phase 2-3

### Phase 2 - 并行执行

Status: completed
Targets: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavDashboardBizModel.java`、`nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/NopDatavConfigs.java`（若新建执行器类，落 `io.nop.datav.service` 对应包）

- Item Types: `Fix | Proof`

- [x] 按 P1/P2 实现有界并行执行：面板查询并行提交、结果按 sortOrder 归位、错误归集保持 D3 语义；测试可观测 seam 按 P1 裁定落位
- [x] 并行配置项加入 `NopDatavConfigs`，默认值按 P5
- [x] focused tests：并行结果与顺序版逐面板等价（含面板级失败条目、无数据集条目、条目顺序）；并发执行证明（经可观测断言，如测试用 latch/计数器证明 ≥2 面板查询重叠执行）；并行度上界不被突破；线程资源不复现泄漏（执行器关闭/复用断言）；开关关闭时与现状等价

Exit Criteria:

- [x] 测试证明：同一看板在并行与顺序（开关关）两种模式下返回结果完全一致（含错误条目形态与顺序）
- [x] 测试证明：多面板查询确实并发执行（非假并行串行）且并发度受并行度配置约束
- [x] 测试证明：任一面板抛非 NopException 时仍按看板级失败传播（D3 语义在并行下保持）
- [x] 测试证明：上限校验仍先于任何查询执行（50 上限语义不因并行改变）
- [x] **无静默跳过**：并行框架自身的执行异常（如任务被拒）显式失败，无任务静默丢弃导致条目缺失
- [x] **接线验证**：并行路径在 `getDashboardData` 真实调用链上生效（非独立执行器存在但入口仍串行）
- [x] owner-doc：本 Phase 行为契约由 Phase 1 `runtime-design.md` 裁定章节前置覆盖；实现与裁定无漂移（若实现偏离 P1/P2/P5 裁定，须先回写裁定再勾选，不允许无同步漂移）——实现期两处裁定修订（runInNewSession 包裹、每任务 context 拷贝防 TransactionRegistry 并发损坏）已回写 §8.2
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 查询结果缓存

Status: completed
Targets: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/query/PanelDataBinder.java`（仅 P3 裁定的最小重构）、`nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/NopDatavConfigs.java`（若新建缓存类，落 `io.nop.datav.service` 对应包）

- Item Types: `Fix | Proof`

- [x] 按 P3/P4 实现缓存层（`io.nop.commons.cache` 平台抽象，不自造）：键构造、TTL/容量、命中返回/未命中执行后回填；面板级失败条目与无数据集条目按 P4 裁定处理
- [x] 缓存配置项加入 `NopDatavConfigs`，默认值按 P5
- [x] focused tests：相同键二次查询命中（经可观测断言，如 SQL 执行计数器不增长）；参数变化/rowLimit 变化不误命中（键区分）；TTL 过期后重新执行；缓存开关关闭时每次都执行 SQL；无数据集面板条目与面板级失败条目按 P4 裁定行为断言；binder 最小重构后既有调用方（getPanelData/refreshPanel/exportDashboard/AlertEvaluator 路径）回归不变

Exit Criteria:

- [x] 测试证明：命中路径不再触发 SQL 执行（计数器/断言可观测，非仅耗时推断）
- [x] 测试证明:不同求值参数的查询互不串台（键无碰撞断言）
- [x] 测试证明：TTL 内数据集变更后的读取行为与 P4 裁定的 staleness 契约一致（显式断言，而非未定义）
- [x] 测试证明：缓存关闭回归现状（每次查询执行 SQL）
- [x] **无静默跳过**：缓存读写异常显式处理（按裁定：缓存故障降级为直查须显式记录，不允许吞异常返回错误数据）
- [x] **接线验证**：缓存接入点在真实查询路径上生效（P3 裁定接入点处，非旁路）
- [x] owner-doc：本 Phase 行为契约由 Phase 1 `runtime-design.md` 裁定章节前置覆盖；实现与裁定无漂移（若实现偏离 P3/P4/P5 裁定，须先回写裁定再勾选，不允许无同步漂移）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 端到端验证与文档收口

Status: completed
Targets: `nop-datav/nop-datav-service/src/test/`、`ai-dev/design/nop-datav/runtime-design.md`

- Item Types: `Proof`

- [x] 端到端测试：多面板看板（含全局筛选 + 无数据集面板 + 一个失败面板）→ `getDashboardData` 并行执行 → 结果与顺序基线一致 → 立即重复调用 → 缓存命中且结果一致 → 修改筛选参数 → 重新执行 SQL 且结果正确

Exit Criteria:

- [x] **端到端验证**：从批量入口到并行执行/缓存判定到 SQL（或缓存命中）到结果回传的完整路径测试存在且通过
- [x] `runtime-design.md` 章节与实现一致（Phase 1 裁定无漂移；§4.4/§五既有陈述已按 supersession 修订，无两套终局裁定并存）
- [x] `./mvnw test -pl nop-datav/nop-datav-service -am` 全绿
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 并行与缓存行为与 P1–P5 裁定一致，`runtime-design.md` 同步无漂移
- [x] 语义等价（并行 vs 顺序）、面板级失败语义保持、缓存命中/键区分/TTL、开关回归均有 focused tests
- [x] staleness 契约与集群边界（进程内缓存单节点语义）在 design doc 显式记录
- [x] 不存在被静默降级到 deferred 的 in-scope 项
- [x] 受影响 owner docs（`runtime-design.md`）已同步
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：并行执行器与缓存层在 `getDashboardData` 真实调用链上生效，非孤立组件；无静默降级路径
- [x] `./mvnw compile -pl nop-datav/nop-datav-service -am` 通过
- [x] `./mvnw test -pl nop-datav/nop-datav-service -am` 通过
- [x] checkstyle / 代码规范检查通过（仓库无独立 lint 命令时按 import 分组约定人工核对）

## Deferred But Adjudicated

（本 plan 起草时无预裁定的 deferred 项；执行中产生的延期项须按 guide 归类并写明 Why Not Blocking Closure。）

## Non-Blocking Follow-ups

- `getPanelData`/`exportDashboard` 路径复用缓存层的可行性（本 plan 落地后评估，接入点裁定时顺带记录结论）。
- nop-report 数据集变更事件失效钩子（若 Phase 1 核实平台无现成事件机制，TTL-only 契约下此项保持 watch）。

## Closure

Status Note: 全部 4 Phase（设计裁定 P1–P5 / 并行执行 / 查询结果缓存 / 端到端验证）完成并勾选；
Goals 全部达成——`getDashboardData` 面板查询升级为有界并行（共享 globalWorker + 请求内 Semaphore，
D3/D4 语义不变式与顺序版逐条等价有 focused tests 证明），重复查询（同数据集+同求值参数+同行数约束）
经平台 LocalCache 结果缓存（TTL-only staleness 契约、失败/无数据集条目不缓存、行数准入上界），
6 个配置项默认值保守（并行默认开/缓存默认关，关闭均回归现状等价）。两项批量查询 plan defer 项收口，
无剩余 plan-owned work；Non-Blocking Follow-ups 两项均为显式裁定的 watch/optimization 项。
Completed: 2026-08-15

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure audit（task id: ses_ffcae889affeFQHPrUPbiE3I1d，fresh session，与实现会话不同）
- Audit Verdict: **CLOSABLE**（A–H 全项 PASS，0 must-fix findings）
- Evidence:
  - Phase 1–4 Exit Criteria 逐条 PASS，证据为 file:line / 测试名（审计报告 A1–D2）：
    runtime-design.md:304-349（§8.1–8.5 裁定）、:215 与 :253-255（§4.4/§五 supersession 无并存矛盾）、
    roadmap:137（缓存策略行纠正）；测试 `testParallelResultsMatchSequentialBaseline` /
    `testQueriesExecuteConcurrently`（latch 并发证明）/ `testParallelismBoundNotExceeded`（≤2 且 ≥2）/
    `testNonNopExceptionPropagatesAsDashboardLevelFailure` /
    `testMaxPanelLimitPrecedesAnyQueryUnderParallel`（零任务提交）/ `testExecutorSharedReuseAcrossRequests` /
    `testCacheHitSkipsSqlAndParamChangeMisses`（staleness+hit/miss 计数）/ `testTtlExpiryReExecutes` /
    `testStalenessContractWithinTtl` / `testCacheDisabledReExecutesEveryTime` /
    `testFailureAndNoDatasetEntriesNotCached` / `testGetPanelDataPathNotCachedEvenWhenEnabled`（Non-Goal
    边界）/ `testBuildKeyDiscrimination` / `testMaxRowsPerEntryAdmission` /
    `TestNopDatavDashboardPerfE2E.testEndToEndParallelWithCacheFullLifecycle`（端到端全链路）
  - Anti-Hollow：调用链追踪 getDashboardData → executePanelQueriesInParallel（BizModel:420-423）→
    worker（Semaphore + 每任务 context 拷贝 + runInNewSession）；缓存 resolveQueryResultCache（:545-547）
    → 5 参 binder（:558）→ get/tryPut（PanelDataBinder:160-184）——均在真实调用链上，非孤立组件；
    `scan-hollow-implementations.mjs --module nop-datav --severity high` 退出码 0（0 findings）；
    缓存故障降级为显式 WARN + 直查（§8.4 裁定，非吞异常）
  - Deferred 项分类检查：`Deferred But Adjudicated` 为空；Non-Blocking Follow-ups 两项均为显式
    watch/optimization 裁定（告警路径缓存 staleness 为正确性问题 → Non-Goal；数据集变更事件失效钩子
    平台无现成机制 → TTL-only 契约 watch），无 in-scope live defect 降级
  - 验证命令：`./mvnw compile -pl nop-datav/nop-datav-service -am -q` 退出码 0；
    `./mvnw test -pl nop-datav/nop-datav-service -am` BUILD SUCCESS（审计者独立复跑，全 reactor 绿，
    datav-service **500/0/0**：485 基线 + 6 并行 + 8 缓存 + 1 E2E）；
    `check-plan-checklist.mjs --strict` 退出码 0；`check-doc-links.mjs --strict` 退出码 0；
    import 分组约定人工核对 4 个主源文件 PASS

Follow-up:

- `getPanelData`/`exportDashboard` 路径复用缓存层的可行性（本 plan 落地后评估，接入点裁定时顺带记录结论）
- nop-report 数据集变更事件失效钩子（TTL-only 契约下保持 watch）
