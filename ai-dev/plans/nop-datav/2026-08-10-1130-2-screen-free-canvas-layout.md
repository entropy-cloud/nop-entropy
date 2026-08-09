# 2 大屏自由画布布局 + 屏幕适配（D4-1）

> Plan Status: active
> Last Reviewed: 2026-08-10
> Source: `ai-dev/backlog/nop-datav-roadmap.md` D4-1；`ai-dev/analysis/2026-08/2026-08-09-nop-datav-function-analysis.md` §五大屏能力
> Related: `2026-08-10-1000-2-dashboard-runtime-panel-data-binding-refresh.md`（D1 运行时/组件注册表复用）、`2026-08-09-2255-1-dashboard-model-crud-and-tests.md`（D0 模型/发布快照参考）
> Mission: nop-datav
> Work Item: D4-1

## Purpose

将 roadmap D4-1 收口：为 nop-datav 引入「大屏」布局模型——自由画布（组件按 x/y/w/h 绝对定位）+ 屏幕尺寸定义 + 屏幕适配模式（heightFirst/full/keep），并建立大屏 CRUD + 发布/快照语义。本计划是 D4 的基建层（D4-2 装饰/媒体组件族、D4-3 主题、D4-4 发布生命周期均依赖本布局模型）。本计划只做模型层 + 布局协议 + 适配解析（运行时编排），不做前端渲染（走 nop-chaos-flux，未产出）。

## Current Baseline

基于 live repo 核对（2026-08-10）：

- **看板模型已就绪（但仅网格布局）**：`nop-datav/model/nop-datav.orm.xml` 含 7 实体（Dashboard/Panel/Tab/DatasetRef/Snapshot/FilterState/Share）。Dashboard 有 `layoutConfig`（json-4000，网格布局）+ `publishStatus`/`publishedVersion` + 发布快照表 `NopDatavDashboardSnapshot`（主表管权限/发布状态，快照表管已发布内容，见 `model-design.md`）。**无大屏实体、无自由画布布局、无屏幕尺寸/适配概念**。
- **组件注册表已就绪（可复用扩展）**：`nop-datav-service/.../component/PanelComponentRegistry.java`（接口注册表 + 代码构建，D1-1）覆盖 7 类组件（chart/pivot-table/stat-tile/map/table/text/iframe）+ container。`PanelTypeMapping` 维护 dict int → 组件类型标识。大屏装饰/媒体组件族（D4-2）将在该注册表扩展；本计划仅确保大屏 widget 可引用已有组件类型，装饰组件归 D4-2。
- **设计契约为 stub**：`ai-dev/design/nop-datav/screen-design.md` 仅占位（~270 字节），无任何决策。本计划 Phase 1 将其定稿。
- **权限基建可复用**：D3-1 action 级 RBAC + 行级 RLS（权限点+角色绑定定义于 `nop-datav-web/.../nop/datav/auth/nop-datav.action-auth.xml`，RLS 定义于 `nop-datav-service/.../nop/datav/auth/nop-datav.data-auth.xml`；owner = `createdBy`，`requireEntity` → `checkDataAuth`）。大屏 CRUD 可沿用同模式。
- **错误码集中点**：`NopDatavErrors.java`。
- **前端（flux）未产出**：大屏设计器/渲染控件不存在，与 D1-4/D2-4 同类阻塞。本计划只交付后端模型 + 布局协议 + 适配解析，可独立验证（与 D1-1 组件注册表同模式）。
- **真正剩余 gap**：无大屏模型、无自由画布布局协议、无屏幕适配解析。

## Goals

- 引入**独立的大屏实体模型**（不复用 Dashboard/Panel）：`NopDatavScreen`（控制面聚合根，管权限/元数据/发布状态/屏幕尺寸/适配模式/背景）+ `NopDatavScreenWidget`（大屏组件，自带 x/y/w/h/z 自由定位 + 组件类型标识 + 数据集引用）+ `NopDatavScreenSnapshot`（独立发布快照表），含 CRUD + 发布/快照语义（复用 D0 主表+快照表**模式**，但为独立实体独立建表）。
- 定义自由画布布局协议：widget 按 x/y/w/h 绝对定位 + z 层级，存储为布局 JSON（widget 配置区，不新增海量 ORM 列）。
- 定义屏幕适配模式（`heightFirst` / `full` / `keep`，参考 DataEase screenAdaptor）与适配解析（后端给出画布基准尺寸 + 模式，供前端按视口缩放；后端不做像素级渲染计算）。
- 在 design doc（`screen-design.md`）记录最终架构决策（含推荐方向的 trade-off 与被拒方案理由）。
- 大屏 CRUD/发布经 D3-1 权限模式收口（action `@Auth` + owner 行级 RLS）。

## 设计方向预声明（推荐方向，Phase 1 确认并记录拒绝理由）

基于 live repo 核对，本计划采用以下推荐方向，Phase 1 设计文档确认并记录被拒替代方案的理由（不再作为开放式裁定）：

1. **独立大屏实体，不复用 Dashboard**：大屏的屏幕尺寸/适配模式/背景是看板没有的概念；自由画布绝对定位与看板网格布局语义不同。DataEase 仪表板/大屏分离可参考。→ 新建 `NopDatavScreen`/`NopDatavScreenWidget`/`NopDatavScreenSnapshot` 三表。拒绝"Dashboard + layoutMode=free"复用方案（耦合两种布局语义，主表查询/权限复杂化）。
2. **widget 不复用 Panel**：当前 `NopDatavPanel`（`nop-datav.orm.xml:112-176`）**无 x/y/w/h/z 定位字段**；复用需在已 done 的 D0 ORM 新增列（跨已-done-plan 结构变更 + 看板面板/大屏 widget 双重身份耦合）。→ `NopDatavScreenWidget` 独立实体，自带定位 + 组件类型标识 + datasetRefId。
3. **新建 `NopDatavScreenSnapshot`，不复用泛化/多态快照**：`model-design.md` §拒绝的替代方案已明确拒绝 `ownerType+ownerId` 多态关联（查询复杂、FK 难表达）。→ 大屏独立快照表（screenId + snapshotVersion UK），与 `NopDatavDashboardSnapshot` 同模式但独立。
4. **适配模式 dict 用 int + code 映射**（`heightFirst=0/full=10/keep=20`）：与仓库既有 dict 惯例（`datav/panel-type` 等 valueType=int）一致。

## Non-Goals

- **装饰/媒体组件族（D4-2）**：装饰边框/滚动文字/时间时钟/视频/流媒体/轮播 Tab 的组件注册与配置 schema 归 D4-2。本计划仅确保大屏 widget 可承载组件引用，不在本计划新增装饰组件类型。
- **大屏主题（D4-3）**：主题色板/背景样式归 D4-3。本计划大屏实体仅保留最小背景配置位（或完全不纳入，由 D4-3 增补）。
- **发布生命周期增强（D4-4）**：暂存/历史/缩略图归 D4-4。本计划只复用 D0 已有的发布/快照语义（publish/getPublished/rollback），不新增暂存/缩略图。
- **前端渲染/设计器（flux）**。
- **重建数据取数/数据集引用**：大屏 widget 复用既有 Panel + DatasetRef + D1 数据绑定管线（不重建）。
- **AI 大屏生成（D6-2）**。

## Scope

### In Scope

- 设计文档定稿：`ai-dev/design/nop-datav/screen-design.md`（实体模型裁定、画布布局 JSON schema、屏幕适配模式与缩放基准、与 Dashboard/Panel/ComponentRegistry 的关系、发布/快照复用裁定）。
- 新增大屏实体（ORM 源模型编辑 + codegen）。
- 大屏 CRUD BizModel（标准 CrudBizModel + publish/getPublished/rollback，复用 D0 模式）。
- 布局协议：画布配置 + widget 自由定位 JSON 的解析与运行时校验（`ScreenLayoutConfig` 解析、widget 越界/重叠的运行时校验裁定、未知组件类型显式报错）。
- 屏幕适配解析方法（给定画布尺寸 + 适配模式 + 目标视口，给出缩放基准；供前端消费）。
- 错误码：大屏相关（`ERR_DATAV_SCREEN_*`）。
- 权限：大屏 action `@Auth` + owner 行级 RLS（`nop-datav-web/.../nop-datav.action-auth.xml` 增配权限点+角色，`nop-datav-service/.../nop-datav.data-auth.xml` 增配 RLS）。
- 单元测试 + AutoTest + 端到端（创建大屏 → 配置画布/widget → 发布 → 读取已发布 → 适配解析）。

### Out Of Scope

- 装饰/媒体组件族（D4-2）、主题（D4-3）、暂存/历史/缩略图（D4-4）。
- 前端设计器/渲染（flux）。
- 轮播 Tab 的运行时调度（D4-2 范围，本计划 widget 配置可预留 carousel 配置位但不实现调度）。

## Execution Plan

### Phase 1 - 设计文档定稿（screen-design.md）

Status: planned
Targets: `ai-dev/design/nop-datav/screen-design.md`

- Item Types: `Decision`

- [ ] 将 `screen-design.md` 从 stub 定稿为最终设计文档（无 "Proposed vs Current"），记录推荐方向（见上方「设计方向预声明」）的最终决策 + 被拒方案理由：
  - [ ] **实体模型**：确认独立三实体 `NopDatavScreen` + `NopDatavScreenWidget` + `NopDatavScreenSnapshot`；记录"复用 Dashboard/Panel"被拒理由（屏幕尺寸/适配/绝对定位语义不同 + Panel 无定位字段 + 跨已-done-plan 结构变更）。给出列级行为规格（Screen：屏幕宽高/适配模式 int dict/背景配置位 + 标准审计列 + 发布状态/版本；ScreenWidget：screenId FK + x/y/w/h/z + 组件类型标识 + datasetRefId + widgetConfig JSON + 审计列；ScreenSnapshot：screenId + snapshotVersion UK + snapshotContent CLOB）。
  - [ ] **画布布局 JSON schema**：画布尺寸（width/height，如 1920×1080）、适配模式（heightFirst=0/full=10/keep=20，int dict `datav/screen-adaptor`）、背景配置位、widget 列表（每个 widget：组件类型标识 + x/y/w/h/z + 组件配置 + 数据绑定 datasetRefId）。
  - [ ] **屏幕适配语义**：heightFirst=按高度等比缩放（宽度可滚动）/ full=整体等比铺满 / keep=原始尺寸不缩放；后端给出"画布基准尺寸 + 模式"，前端据此算 transform；后端不做像素级渲染。
  - [ ] **`getScreenLayout` API 契约**（新建 API，非 D0 复用）：定义返回结构（解析后的 `ScreenLayoutConfig`：画布尺寸 + 适配模式 + widget 列表含定位/组件类型/datasetRefId）；明确它**读已发布快照**（`getPublishedScreen` 返回快照实体原文，`getScreenLayout` 返回结构化解析结果 + 适配配置，两者职责区分）。
  - [ ] **发布/快照**：`NopDatavScreenSnapshot` 独立表，publish/getPublished/rollback 与 D0 Dashboard 模式一致（主表管权限/发布状态，快照表管已发布内容）；记录"泛化/多态快照"被拒理由（引用 `model-design.md` 既有决策）。
  - [ ] **权限**：大屏 CRUD/发布沿用 D3-1 action `@Auth` + owner（`createdBy`）行级 RLS，与 Dashboard 同模式（admin 无 filter；user owner + 已发布）。
  - [ ] **widget 越界/重叠校验**：裁定运行时校验范围（如 widget x+w ≤ canvasWidth、y+h ≤ canvasHeight；重叠是否告警）——在 `ScreenLayoutConfig` 解析时执行，越界抛 `ERR_DATAV_SCREEN_WIDGET_OUT_OF_BOUNDS`。

Exit Criteria:

- [ ] `screen-design.md` 为最终设计（Status: final），覆盖全部子项，无 "Proposed"/"待定"
- [ ] 实体模型、画布 schema、适配模式、与既有模型关系、发布/权限裁定均有明确结论
- [ ] 该 Phase 改变 live baseline（design doc）：`docs-for-ai/` 无需更新（无新平台约定）；`ai-dev/logs/` 对应日期条目已更新

### Phase 2 - ORM 模型与代码生成（大屏实体）

Status: planned
Targets: `nop-datav/model/nop-datav.orm.xml`、`nop-datav/nop-datav-dao/_gen/`

- Item Types: `Decision | Proof`

- [ ] 按 Phase 1 裁定在 `nop-datav/model/nop-datav.orm.xml` 新增**三个独立实体**：`NopDatavScreen`（表 `nop_datav_screen`，含 screenName/displayName/screenWidth/screenHeight/adaptorMode(int, dict `datav/screen-adaptor`)/backgroundConfig/publishStatus/publishedVersion + 标准审计列）、`NopDatavScreenWidget`（表 `nop_datav_screen_widget`，screenId FK + x/y/w/h/z + componentType + datasetRefId + widgetConfig(json-4000) + 审计列）、`NopDatavScreenSnapshot`（表 `nop_datav_screen_snapshot`，screenId + snapshotVersion + snapshotContent(clobJson) + UK(screenId,snapshotVersion)）；新增 dict `datav/screen-adaptor`（valueType=int：heightFirst=0/full=10/keep=20）；复用既有 domains
- [ ] `./mvnw install -pl nop-datav/nop-datav-meta -am -DskipTests` 触发 codegen，确认 `_gen` 下 `NopDatavScreen`/`NopDatavScreenWidget`/`NopDatavScreenSnapshot` 三实体的 dao/entity/meta/api/beans 生成物齐全（不手改）
- [ ] 三个大屏实体 xmeta 暴露标准 CRUD + 管理页（与既有 7 实体同模式）

Exit Criteria:

- [ ] `nop-datav/model/nop-datav.orm.xml` 含 `NopDatavScreen`、`NopDatavScreenWidget`、`NopDatavScreenSnapshot` 三个实体（源模型），列与 Phase 1 规格一致（Screen 含 screenWidth/screenHeight/adaptorMode；ScreenWidget 含 x/y/w/h/z/componentType/datasetRefId；ScreenSnapshot 含 screenId/snapshotVersion/snapshotContent + UK）
- [ ] `./mvnw install -pl nop-datav/nop-datav-meta -am -DskipTests` 成功，`_gen` 下出现三实体对应生成物
- [ ] **无静默跳过**：本 Phase 仅 codegen，不涉及运行时分支
- [ ] 该 Phase 改变 live baseline（ORM 结构）：属 plan-first 区域，本 plan 即其 plan；`ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 布局协议与屏幕适配实现

Status: planned
Targets: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/screen/`（新建）、`.../entity/NopDatavScreenBizModel.java`、`NopDatavErrors.java`

- Item Types: `Fix | Decision`

- [ ] 新增错误码（`NopDatavErrors.java`）：`ERR_DATAV_SCREEN_NOT_FOUND`、`ERR_DATAV_INVALID_SCREEN_LAYOUT`（画布/widget JSON 非法）、`ERR_DATAV_SCREEN_WIDGET_UNKNOWN_COMPONENT`（widget 引用未注册组件类型）、`ERR_DATAV_SCREEN_WIDGET_OUT_OF_BOUNDS`（widget 越界，见 Phase 1 裁定）、`ERR_DATAV_SCREEN_SNAPSHOT_NOT_FOUND`（大屏无已发布快照——**新建，不复用 `ERR_DATAV_SNAPSHOT_NOT_FOUND`**，因后者消息为 "for dashboard: {dashboardId}" 语义不符）
- [ ] 实现 `ScreenLayoutConfig` 解析：从大屏布局 JSON 解析画布尺寸/适配模式/widget 列表；JSON 非法抛 `ERR_DATAV_INVALID_SCREEN_LAYOUT`（不静默降级）
- [ ] widget 组件类型引用经 `PanelComponentRegistry.requireComponent` 校验（未知类型显式报错，复用 D1-1 注册表，rule #24）
- [ ] widget 越界/重叠运行时校验（Phase 1 裁定范围）：在 `ScreenLayoutConfig` 解析时校验 widget x+w ≤ canvasWidth / y+h ≤ canvasHeight，越界抛 `ERR_DATAV_SCREEN_WIDGET_OUT_OF_BOUNDS`
- [ ] 实现屏幕适配解析：给定画布基准尺寸 + 适配模式，输出适配配置（基准 width/height + mode），供前端计算缩放；后端不做像素渲染
- [ ] 实现 `NopDatavScreenBizModel`（`@BizModel("NopDatavScreen")`）：标准 CRUD（继承 CrudBizModel）+ `publishScreen`/`getPublishedScreen`/`rollbackScreen`（复用 D0 主表+快照表模式，操作 `NopDatavScreenSnapshot`）+ `getScreenLayout(screenId, context)`（`@BizQuery`，按 Phase 1 契约**读已发布快照**并返回解析后的 `ScreenLayoutConfig` + 适配配置）；action 经 `requireEntity` → `checkDataAuth`
- [ ] 大屏权限：在 `nop-datav/nop-datav-web/src/main/resources/_vfs/nop/datav/auth/nop-datav.action-auth.xml`（**权限点 + 角色绑定定义处**，非 `app.action-auth.xml`）增配大屏 action 权限点 + 默认角色；在 `nop-datav/nop-datav-service/src/main/resources/_vfs/nop/datav/auth/nop-datav.data-auth.xml`（**RLS 定义处**）增配 `NopDatavScreen` owner 行级规则（admin 无 filter；user owner + 已发布，与 Dashboard 同语义）

Exit Criteria:

- [ ] 大屏 CRUD + publish/getPublished/rollback 可用，发布快照语义落地（与 D0 模式一致）
- [ ] **接线验证**：`getScreenLayout`/widget 校验确实调用 `PanelComponentRegistry.requireComponent`（运行时连通，非仅类型存在）——由端到端测试断言
- [ ] **无静默跳过**：非法画布 JSON 抛异常非降级；未知组件类型显式报错；新增公共方法无空方法体/continue/吞异常
- [ ] 屏幕适配三种模式（heightFirst/full/keep）的解析输出可区分且正确（见 Phase 4 测试）
- [ ] 该 Phase 改变 live baseline（API/行为）：`screen-design.md`（Phase 1）已覆盖设计；`docs-for-ai/` 无需更新；`ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 测试与端到端验证

Status: planned
Targets: `nop-datav/nop-datav-service/src/test/`

- Item Types: `Proof`

- [ ] 单元测试 `ScreenLayoutConfig` 解析：合法画布 JSON 解析正确；非法 JSON → `ERR_DATAV_INVALID_SCREEN_LAYOUT`；widget 引用未知组件 → `ERR_DATAV_SCREEN_WIDGET_UNKNOWN_COMPONENT`；widget 越界（x+w>canvasWidth 等）→ `ERR_DATAV_SCREEN_WIDGET_OUT_OF_BOUNDS`
- [ ] 单元测试 屏幕适配：heightFirst/full/keep 三种模式各自输出正确缩放基准
- [ ] 单元测试 发布/快照：publish 写快照、getPublished 读最新、rollback 恢复；版本号递增（参考 D0 测试模式）
- [ ] 端到端测试（rule #22）：创建大屏 → 配置画布尺寸 + 适配模式 + 含一 chart widget（引用已有 Panel/DatasetRef）→ publish → getPublishedScreen → getScreenLayout 返回解析布局 + 适配配置 → 断言 widget 定位/组件类型/适配模式正确
- [ ] 权限测试：owner 行级过滤（user A 大屏对 user B 不可见，与 D3-1 同模式）；无权角色 fail-closed
- [ ] 测试基建：复用 D3 鉴权测试配置（`nop-auth-service` test 依赖 + action-auth/data-auth 路径）

Exit Criteria:

- [ ] 新增大屏功能（CRUD/发布/布局解析/适配/widget 校验）每个均有对应测试（rule #25）
- [ ] **端到端验证**：从创建大屏到 getScreenLayout 输出完整链路跑通
- [ ] **接线验证**：端到端测试断言 `PanelComponentRegistry.requireComponent` 被实际调用
- [ ] `./mvnw test -pl nop-datav/nop-datav-service -am` 通过
- [ ] 该 Phase 不改变 live baseline；`ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 大屏实体模型 + 自由画布布局协议 + 屏幕适配解析落地
- [ ] 大屏 CRUD + 发布/快照语义可用（复用 D0 模式）
- [ ] 大屏权限（action `@Auth` + owner RLS）生效
- [ ] 未知组件类型 / 非法画布 JSON 显式失败（非静默跳过）
- [ ] 端到端测试（创建→配置→发布→读取→适配解析）通过
- [ ] `screen-design.md` 为最终设计与 live 实现一致
- [ ] 不存在被静默降级到 deferred 的 in-scope live defect
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 验证 getScreenLayout→PanelComponentRegistry 调用链运行时连通，无空方法体/静默跳过
- [ ] `./mvnw compile -pl nop-datav -am`
- [ ] `./mvnw test -pl nop-datav/nop-datav-service -am`
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### 大屏渲染 / 设计器（flux 侧）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: D4-1 是模型层 + 布局协议 + 适配解析（后端可独立验证，与 D1-1 组件注册表同模式）。前端设计器/渲染控件走 nop-chaos-flux（未产出），与 D1-4/D2-4 同类阻塞。后端协议就绪后 flux 侧对接即可。
- Successor Required: `yes`
- Successor Path: flux 大屏设计器/渲染控件落地后对接（合入 D1-4/D2-4 前端集成阶段或独立大屏前端 plan）

## Non-Blocking Follow-ups

- 装饰/媒体组件族（D4-2）：在本布局协议上扩展注册表，独立 plan
- 大屏主题色板/背景增强（D4-3）：独立 plan
- 暂存/历史/缩略图发布生命周期增强（D4-4）：独立 plan
- 轮播 Tab 运行时调度：D4-2 范围
- widget 越界/重叠的配置时校验（当前为运行时校验，可增加配置时校验）——优化项

## Closure

Status Note: <<关闭时填写>>
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: <<独立子 agent>>
- Audit Session: <<session/task id>>
- Evidence:
  - 每条 Exit Criterion 的验证结果（PASS/FAIL + live code path / test name）
  - 每条 Closure Gate 的验证结果
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0
  - Anti-Hollow 检查结果：端到端调用链追踪 + `scan-hollow-implementations.mjs` 退出码
  - Deferred 项分类检查：大屏渲染确为 out-of-scope（flux 阻塞），非 in-scope live defect 降级

Follow-up:

- <<关闭时填写；confirmed live defect 不得出现>>
