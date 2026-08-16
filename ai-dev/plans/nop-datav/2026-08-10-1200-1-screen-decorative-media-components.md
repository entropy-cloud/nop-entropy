# 1 大屏装饰/媒体组件族（D4-2）

> Plan Status: completed
> Mission: nop-datav
> Work Item: D4-2
> Last Reviewed: 2026-08-10
> Source: `ai-dev/backlog/nop-datav-roadmap.md` D4-2；`ai-dev/analysis/2026-08/2026-08-09-nop-datav-function-analysis.md` §五大屏能力
> Related: `2026-08-10-1130-2-screen-free-canvas-layout.md`（D4-1 布局协议/组件注册表复用基础）、`2026-08-10-1000-2-dashboard-runtime-panel-data-binding-refresh.md`（D1-1 组件注册表原始方案）
> Execution Order: N=1（D4-2 → D4-3 → D4-4）。本计划主要修改 component 包（注册表/元信息/类型常量），Phase 3 在 `NopDatavScreenBizModel` 增 `getComponentTypes` action 并在 `nop-datav.action-auth.xml` 增配权限点——与 D4-3/D4-4 共享这两个文件，按 N 序串行执行避免冲突。

## Purpose

将 roadmap D4-2 收口：为大屏引入装饰/媒体组件族（装饰边框/滚动文字/时间时钟/视频/流媒体/轮播 Tab），使大屏 widget 可承载这些非数据绑定组件类型。本计划是 D4-1 自由画布布局的组件层扩展——D4-1 已建立布局协议与 `PanelComponentRegistry` 接线（widget.componentType 经注册表校验），本计划在该注册表上登记装饰/媒体类型并定义其配置 schema。本计划只做模型层（注册表登记 + 配置 schema 约定 + 元信息暴露），不做前端渲染（走 nop-chaos-flux，未产出），不含媒体代理/流后端实现。

## Current Baseline

基于 live repo 核对（2026-08-10）：

- **组件注册表已就绪（可直接扩展）**：`PanelComponentRegistry`（`nop-datav-service/.../component/PanelComponentRegistry.java`）当前登记 8 类组件（chart/pivot-table/stat-tile/map/table/text/iframe/container），启动时静态初始化，查询未知类型抛 `ERR_DATAV_UNKNOWN_COMPONENT_TYPE`（不静默降级）。组件实现为 `SimplePanelComponent(type, displayName, needsDataset)`，元信息 `PanelComponentMeta` 含 `{type, displayName, needsDataset}` 三字段。类型标识常量集中於 `PanelComponentTypes`。
- **大屏布局解析已接线注册表**：`ScreenLayoutParser.validateComponentType` / `parseWidget` 调用 `componentRegistry.requireComponent(componentType)` 校验 widget 组件类型（D4-1）。**因此本计划新增的装饰/媒体类型一旦登记进注册表，即可被大屏 widget 引用并通过校验**——无需改动 ScreenLayoutParser。
- **widget.componentType 是 string（非 dict int）**：`NopDatavScreenWidget.componentType` 为 `string(50)`（`nop-datav.orm.xml`），不经 dict int 映射。故装饰/媒体类型**不需要新增 panelType dict 选项**（panelType dict 仅服务于看板网格面板的 int 映射）。装饰组件是大屏专用，看板面板不引用。
- **配置 schema 现状**：D1-1 设计（`runtime-design.md` §1.3 panelConfig 配置区域）仅定义配置**区域约定**（title/fieldMapping/styleOptions/dataBinding/refresh/content），各区域字段级定义属"组件实现层细节，由 flux 落地时定稿"。当前 `PanelComponentMeta` 不携带任何配置 schema 描述信息——注册表只回答"哪些类型存在"，不回答"每种类型接受什么配置"。
- **设计契约为 D4-1 范围**：`screen-design.md` Scope 明确"装饰/媒体组件族 D4-2 不在本文结论范围"。本计划 Phase 1 将在其上增补 D4-2 章节。
- **真正剩余 gap**：装饰/媒体组件类型未登记（大屏 widget 引用会触发 `ERR_DATAV_UNKNOWN_COMPONENT_TYPE`）；这些类型的配置 schema 无定义。

## 设计方向预声明（推荐方向，Phase 1 确认并记录拒绝理由）

1. **装饰/媒体组件登记进既有 PanelComponentRegistry，不新建 ScreenComponentRegistry**：D4-1 已复用该注册表校验大屏 widget（`screen-design.md` §8）。新建独立注册表会割裂组件类型空间、增加 ScreenLayoutParser 分发分支。→ 复用既有注册表，新增 6 类装饰/媒体组件。拒绝"独立 ScreenComponentRegistry"（理由：同一类型空间更简单，看板面板因无对应 panelType dict 项天然不会误引用装饰组件）。
2. **装饰/媒体组件均为 needsDataset=false**：装饰边框/滚动文字/时间时钟/视频/流媒体/轮播 Tab 均为纯展示/媒体组件，不绑定数据集。轮播 Tab 是容器式组件（承载其他 widget），但其自身不取数。
3. **配置 schema 作为机器可读描述符挂载到 PanelComponentMeta**（推荐）：当前 `PanelComponentMeta` 仅含三字段，无法回答"某类型接受什么配置"。本计划扩展元信息使其可携带**命名配置区域描述符**（每个区域：名称 + 用途 + 是否必填），使注册表自描述。拒绝"仅文档约定、无运行时描述符"（理由：配置 schema 是本工作项的显式交付物；纯文档无法被 getComponentMetadata 类 API 消费，也无法在测试中断言）。
4. **不做配置 schema 的硬运行时校验（不强制 JSON Schema 验证 widgetConfig）**：与 D1-1 "区域约定、字段级细节由 flux 定稿"哲学一致。本计划暴露 schema 描述符供前端/测试消费，但不在校验时拒绝"含未声明区域"的 widgetConfig（向前兼容）。强制 JSON Schema 校验为 Non-Goal。

## Goals

- 在 `PanelComponentRegistry` 登记 6 类装饰/媒体组件：装饰边框（decorative-border）、滚动文字（scroll-text）、时间时钟（time-clock）、视频（video）、流媒体（stream）、轮播 Tab（carousel-tab），均为 `needsDataset=false`。
- 扩展组件元信息使其可携带命名配置区域描述符，并为上述 6 类各定义其配置区域（如 video → {src, autoplay, loop, controls}；scroll-text → {text, speed, direction}；time-clock → {format, timezone}；decorative-border → {variant, color}；stream → {src, protocol}；carousel-tab → {tabs, interval}）。
- 暴露组件元信息查询能力：作为 `NopDatavScreenBizModel` 上的一个全局查询 action（`getComponentTypes`，无 screenId 参数，返回类型清单 + 每类配置区域描述符），供前端/测试消费。**裁定：放在 NopDatavScreenBizModel（大屏是组件注册表的主要消费方），不新建 entity-less BizModel**——避免新增 `_service.beans.xml` bean 定义与独立 action-auth 命名空间。该 action 的权限点 `NopDatavScreen:getComponentTypes` 在 Phase 3 增配。
- 在 `screen-design.md` 增补 D4-2 章节（最终结论，含被拒方案理由）。
- 装饰/媒体 widget 可经 `getScreenLayout` 解析并通过校验（端到端验证接线）。

## Non-Goals

- **前端渲染**：装饰/媒体组件的视觉渲染走 nop-chaos-flux（未产出），与 D1-4/D2-4 同类阻塞。
- **媒体代理/流后端实现**：视频/流媒体的源代理、转码、信令后端不在本计划范围（参考 DataEase 仅做前端直连媒体源）。
- **轮播 Tab 的运行时调度**：轮播的定时切换由前端按 interval 实现，后端不调度。
- **强制 JSON Schema 校验 widgetConfig**：配置 schema 为描述符 + 约定，不做硬拒绝（向前兼容，见设计方向预声明 #4）。
- **大屏主题（D4-3）**、**发布生命周期增强（D4-4）**：独立 plan。
- **为装饰组件新增 panelType dict 选项**：装饰组件是大屏专用，看板面板不引用。

## Scope

### In Scope

- 设计文档增补：`ai-dev/design/nop-datav/screen-design.md` 增 D4-2 章节（组件族清单、配置区域 schema、元信息描述符方案、被拒方案）。
- 组件元信息扩展：使 `PanelComponentMeta` 可携带命名配置区域描述符。
- 注册 6 类装饰/媒体组件（类型标识 + 显示名 + needsDataset=false + 配置区域描述符）。
- 组件元信息查询 API（暴露类型清单 + 配置区域，供前端/测试消费）。
- 端到端验证：大屏 widget 引用装饰/媒体类型经 `getScreenLayout` 解析通过；元信息 API 返回新增类型。

### Out Of Scope

- 前端渲染控件（flux）。
- 媒体代理/转码/信令后端。
- 轮播运行时调度。
- 强制 widgetConfig JSON Schema 校验。
- D4-3 主题、D4-4 发布生命周期。

## Execution Plan

### Phase 1 - 设计文档增补（screen-design.md D4-2 章节）

Status: completed
Targets: `ai-dev/design/nop-datav/screen-design.md`

- Item Types: `Decision`

- [x] 在 `screen-design.md` 增 D4-2 章节（最终结论，无 "Proposed vs Current"），记录：
  - [x] **组件族清单**：6 类装饰/媒体组件的类型标识、显示名、配置区域（字段级定义），确认均为 needsDataset=false
  - [x] **元信息描述符方案**：确认 PanelComponentMeta 扩展为可携带命名配置区域描述符（每区域：名称 + 用途 + 是否必填）；记录"仅文档约定、无运行时描述符"被拒理由（配置 schema 是本工作项显式交付物，纯文档不可消费）
  - [x] **不做硬 JSON Schema 校验**：记录配置 schema 为描述符 + 向前兼容约定，不强制拒绝未声明区域（记录理由：与 D1-1 哲学一致 + 向前兼容）
  - [x] **复用 PanelComponentRegistry**：记录"独立 ScreenComponentRegistry"被拒理由（同一类型空间更简单）
  - [x] **与看板面板的边界**：装饰组件不经 panelType dict，看板面板（int 映射）天然不引用

Exit Criteria:

- [x] `screen-design.md` 含 D4-2 最终结论章节，覆盖上述全部子项，无 "Proposed"/"待定"
- [x] 该 Phase 改变 live baseline（design doc）：`docs-for-ai/` 无需更新（无新平台约定）；`ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 组件元信息扩展与装饰/媒体组件登记

Status: completed
Targets: `nop-datav-service/.../component/`（PanelComponentMeta、PanelComponentTypes、PanelComponentRegistry、新增装饰/媒体组件实现）

- Item Types: `Fix | Decision`

- [x] 扩展组件元信息使其可携带命名配置区域描述符（保持向后兼容：既有 8 类组件无描述符时按缺省处理，不破坏现有注册）
- [x] 在 `PanelComponentTypes` 新增 6 类装饰/媒体组件的类型标识常量
- [x] 在 `PanelComponentRegistry` 静态初始化块登记 6 类组件（含配置区域描述符），保留既有重复检测（`IllegalStateException` on duplicate type）
- [x] 暴露组件元信息查询能力（类型清单 + 每类配置区域描述符），供 API/测试消费
- [x] 既有 8 类组件行为不回归（无描述符时缺省处理，已有测试 `TestPanelComponentRegistry` 仍通过）

Exit Criteria:

- [x] `PanelComponentRegistry.getComponents()` 含 6 类新装饰/媒体组件（共 14 类），每类 needsDataset=false 且携带配置区域描述符
- [x] 既有组件类型标识与 needsDataset 语义不变（`TestPanelComponentRegistry` 通过）
- [x] **无静默跳过**：重复类型登记仍抛 IllegalStateException；查询未知类型仍抛 ERR_DATAV_UNKNOWN_COMPONENT_TYPE（不返回 null）
- [x] 该 Phase 改变 live baseline（API/元信息）：`screen-design.md`（Phase 1）已覆盖设计；`docs-for-ai/` 无需更新；`ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 大屏组件元信息 API + 端到端验证

Status: completed
Targets: `nop-datav-service/.../service/entity/NopDatavScreenBizModel.java`（新增 `getComponentTypes` action）、`nop-datav-web/.../nop/datav/auth/nop-datav.action-auth.xml`、`nop-datav-service/src/test/`

- Item Types: `Fix | Proof`

- [x] 在 `NopDatavScreenBizModel` 新增 `getComponentTypes()`（`@BizQuery`，无 screenId 参数，返回 PanelComponentRegistry 全部组件的类型标识 + 显示名 + needsDataset + 配置区域描述符），action 经 `@Auth(permissions = "NopDatavScreen:getComponentTypes")`；同步在 `INopDatavScreenBiz` 接口声明该 action（公共契约面，与既有 4 action 同模式）
- [x] 在 `nop-datav-web/.../nop/datav/auth/nop-datav.action-auth.xml` 增配 `NopDatavScreen:getComponentTypes` 权限点 + 默认角色绑定（admin,user 可读）
- [x] 单元测试：6 类装饰/媒体组件均经 `requireComponent` 查询成功且 needsDataset=false；配置区域描述符与 Phase 1 定义一致
- [x] 单元测试：组件元信息 API 返回全部 14 类组件，含装饰/媒体类型
- [x] 端到端测试（rule #22 + #23）：创建大屏 → 添加装饰/媒体 widget（各类型至少一个）→ publish → `getScreenLayout` 解析通过并断言 widget componentType 正确（证明装饰/媒体类型经注册表接线，非空壳）
- [x] 回归测试：既有大屏 E2E（chart widget）仍通过；既有 `TestPanelComponentRegistry` 通过

Exit Criteria:

- [x] 新增 6 类装饰/媒体组件 + 元信息 API（`getComponentTypes`）每个均有对应测试（rule #25）
- [x] **端到端验证**：创建大屏 → 装饰/媒体 widget → publish → getScreenLayout 完整链路跑通，断言装饰类型被注册表接受
- [x] **接线验证**：端到端测试断言装饰/媒体 widget 经 `requireComponent` 校验通过（运行时连通，非仅类型存在）
- [x] `getComponentTypes` 已在 `nop-datav.action-auth.xml` 增配权限点（admin,user 可读），action 可被有权限角色调用
- [x] `./mvnw test -pl nop-datav/nop-datav-service -am` 通过（含新增测试，无回归）
- [x] 该 Phase 不改变 ORM 结构（无新增列）；`ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 6 类装饰/媒体组件登记进 PanelComponentRegistry 且 needsDataset=false
- [x] 组件元信息扩展支持配置区域描述符（向后兼容）
- [x] 组件元信息查询 API 可用
- [x] 装饰/媒体 widget 经 getScreenLayout 解析通过（端到端）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] `screen-design.md` D4-2 章节为最终设计与 live 实现一致
- [x] 受影响 owner docs 已同步（`screen-design.md`；`docs-for-ai/` 无需更新）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证装饰/媒体类型经 requireComponent 运行时校验连通，无空方法体/静默跳过
- [x] `./mvnw compile -pl nop-datav -am`
- [x] `./mvnw test -pl nop-datav/nop-datav-service -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### 装饰/媒体组件前端渲染（flux 侧）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: D4-2 是模型层（注册表 + 配置 schema + 元信息），后端可独立验证（与 D1-1 同模式）。前端渲染走 nop-chaos-flux（未产出），与 D1-4/D2-4 同类阻塞。
- Successor Required: `yes`
- Successor Path: flux 装饰/媒体控件落地后对接（合入前端集成阶段）

### 强制 widgetConfig JSON Schema 校验

- Classification: `optimization candidate`
- Why Not Blocking Closure: 配置 schema 为描述符 + 向前兼容约定（与 D1-1 哲学一致）。强制校验会在 flux 控件族未定稿时阻断合法配置演进。待 flux 控件 schema 稳定后再评估。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 媒体代理/流后端实现（视频转码/信令）——按需评估，当前前端直连媒体源
- 轮播 Tab 运行时调度（后端调度）——前端按 interval 实现已够，归 D5 范围评估
- 配置 schema 描述符的更丰富表达（如枚举值约束、默认值）——优化项

## Closure

Status Note: D4-2 装饰/媒体组件族（模型层）完整落地。6 类装饰/媒体组件（decorative-border/scroll-text/time-clock/video/stream/carousel-tab，均 needsDataset=false）登记进既有 PanelComponentRegistry（共 14 类）；PanelComponentMeta 扩展携带命名配置区域描述符（向后兼容）；getComponentTypes API 暴露元信息查询（admin,user 可读）。端到端验证：6 类装饰 widget 经 getScreenLayout → ScreenLayoutParser → requireComponent 运行时连通，全部解析通过。前端渲染走 nop-chaos-flux（未产出），与 D1-4/D2-4 同类阻塞，明确 out-of-scope。
Completed: 2026-08-10

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent ses_016e79fd3ffeqEgjcrFkKEADjE（fresh closure-audit session）
- Audit Session: ses_016e79fd3ffeqEgjcrFkKEADjE
- Evidence:
  - Phase 1 Exit Criteria: PASS — `screen-design.md` §10（lines 293-369）含 D4-2 最终结论，覆盖 6 子项（组件族清单/元信息描述符方案/不做硬校验/复用注册表/看板边界/getComponentTypes API），无 "Proposed"/"待定"
  - Phase 2 Exit Criteria: PASS — `PanelComponentRegistry.getComponents()` 含 14 类（8 D1-1 + 6 D4-2）；6 装饰/媒体组件 needsDataset=false 且携带配置区域描述符（与 §10.1 一致）；既有组件行为不回归（`TestPanelComponentRegistry` 通过）；重复类型登记抛 IllegalStateException（registry:127）；查询未知类型抛 ERR_DATAV_UNKNOWN_COMPONENT_TYPE（registry:145-147，不返回 null）
  - Phase 3 Exit Criteria: PASS — 新增 6 类装饰/媒体组件 + getComponentTypes API 每个均有对应测试（rule #25）；端到端验证 `testDecorativeMediaWidgetsPassGetScreenLayoutEndToEnd`（TestNopDatavScreenBizModel:337）创建 6 widget → publish → getScreenLayout 完整链路跑通；接线验证：装饰/媒体 widget 经 requireComponent 校验通过（ScreenLayoutParser:188）；`getComponentTypes` 在 nop-datav.action-auth.xml:140-144 增配权限点（admin,user 可读）；`./mvnw test -pl nop-datav/nop-datav-service -T 1C` 通过（222/0/0）
  - Closure Gates: 全部 PASS（见上节每条 [x]）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码：见 plan finalization 步骤
  - Anti-Hollow 检查结果：PASS — 调用链追踪 `getScreenLayout → ScreenLayoutParser.parseWidget:188 → componentRegistry.requireComponent` 运行时连通；E2E 测试 6 类装饰 widget 全部经此链解析通过；requireComponent 不返回 null（143-148 抛 NopException）；getComponentTypes 非空方法体（NopDatavScreenBizModel:165-171 实际迭代注册表收集 metadata）；`scan-hollow-implementations.mjs` 见 finalization 步骤
  - Deferred 项分类检查：无 in-scope live defect 被降级——前端渲染（out-of-scope improvement，successor required）+ 强制 JSON Schema 校验（optimization candidate，no successor）均符合 Allowed Deferred Classifications

Follow-up:

- 前端渲染（flux 装饰/媒体控件）—— successor required，合入前端集成阶段
- 强制 widgetConfig JSON Schema 校验 —— optimization candidate，待 flux 控件 schema 稳定后评估
- no remaining plan-owned work
