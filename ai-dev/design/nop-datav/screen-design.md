# nop-datav 大屏设计 (D4)

> Status: **final**
> Last Reviewed: 2026-08-10
> Scope owner: D4-1（自由画布 + 屏幕适配）+ D4-2（装饰/媒体组件族）。主题 D4-3、发布生命周期增强 D4-4 各有独立 plan，不在本文结论范围。

## 概述

nop-datav 的「大屏」（Screen）是与「看板」（Dashboard）并列的可视化容器形态。看板采用网格布局（panel 按 sortOrder/tabId 组织），大屏采用**自由画布**（widget 按 x/y/w/h/z 绝对定位）+ **屏幕尺寸定义**（设计稿基准宽高）+ **屏幕适配模式**（`heightFirst` / `full` / `keep`，参考 DataEase `screenAdaptor`）。

本设计文档定义 D4-1 与 D4-2 的最终架构决策：

- 独立三实体模型（不复用 Dashboard/Panel）
- 自由画布布局 JSON schema
- 屏幕适配语义与缩放基准
- `getScreenLayout` API 契约（读已发布快照 → 结构化解析结果）
- 发布/快照语义（独立表，与 D0 同模式）
- 权限模式（沿用 D3-1 action `@Auth` + owner 行级 RLS）
- widget 越界/重叠运行时校验
- 装饰/媒体组件族（D4-2）：6 类组件 + 配置区域描述符 + 元信息查询 API

本文档为最终结论（无 "Proposed vs Current"）。被拒替代方案及理由在每一节末尾给出。

## 1. 实体模型

**决策：独立三实体 `NopDatavScreen` + `NopDatavScreenWidget` + `NopDatavScreenSnapshot`，不复用 Dashboard/Panel。**

### 1.1 NopDatavScreen（控制面聚合根）

主表，管权限/元数据/发布状态/屏幕尺寸/适配模式/背景配置位，不存已发布内容。

| 字段 | 类型 | 说明 |
|------|------|------|
| screenId | string(32) PK | 主键，seq |
| screenName | string(100) mandatory, UK | 大屏标识名 |
| displayName | string(200) | 显示名 |
| description | string(4000) | 描述 |
| screenWidth | int mandatory | 画布基准宽度（如 1920） |
| screenHeight | int mandatory | 画布基准高度（如 1080） |
| adaptorMode | int, dict `datav/screen-adaptor` | 屏幕适配模式（0/10/20，默认 10） |
| backgroundConfig | json-4000 | 背景配置位（D4-3 主题扩展占位；本 plan 不解析内部结构） |
| publishStatus | int, dict `datav/publish-status` | 发布状态（D0 复用） |
| publishedVersion | long | 已发布版本 |
| publishedBy | string(50) | 发布人 |
| publishedTime | timestamp | 发布时间 |
| 标准审计列 | — | delFlag/version/createdBy/createTime/updatedBy/updateTime/remark |

### 1.2 NopDatavScreenWidget（大屏组件，自由画布定位）

| 字段 | 类型 | 说明 |
|------|------|------|
| widgetId | string(32) PK | 主键，seq |
| screenId | string(32) mandatory, FK→Screen | 归属大屏 |
| widgetName | string(100) | widget 名 |
| displayName | string(200) | 显示名 |
| componentType | string(50) mandatory | 组件类型标识（如 "chart"），经 `PanelComponentRegistry.requireComponent` 校验 |
| datasetRefId | string(32) | 数据集引用ID（**逻辑引用，非 FK 约束**，与 Panel.datasetRefId 同语义；可复用 nop-report 数据集或看板侧 DatasetRef） |
| x | int mandatory | 画布 X 坐标（像素，相对画布左上角） |
| y | int mandatory | 画布 Y 坐标 |
| w | int mandatory | widget 宽度（像素） |
| h | int mandatory | widget 高度（像素） |
| z | int | Z 层级（默认 0；越大越靠上） |
| widgetConfig | json-4000 | 组件配置（透传给组件，由 componentType 决定 schema） |
| 标准审计列 | — | delFlag/version/createdBy/createTime/updatedBy/updateTime/remark |

relations：`to-one screen → NopDatavScreen`（via screenId）。indexes：`IX_NOP_DATAV_SCREEN_WIDGET_SCREEN(screenId)`。

### 1.3 NopDatavScreenSnapshot（独立发布快照表）

与 D0 `NopDatavDashboardSnapshot` 同模式但独立：发布时将大屏编辑内容（screen 基本信息 + 画布尺寸/适配/背景 + widget 列表）序列化为 JSON 写入快照表，主表更新发布状态/版本。

| 字段 | 类型 | 说明 |
|------|------|------|
| snapshotId | string(32) PK | 主键，seq |
| screenId | string(32) mandatory, FK→Screen | 归属大屏 |
| snapshotVersion | long mandatory | 快照版本（每次发布 +1） |
| snapshotContent | clobJson mandatory | 序列化的发布内容 JSON |
| publishedBy | string(50) | 发布人 |
| publishedTime | timestamp | 发布时间 |
| 标准审计列 | — | delFlag/version/createdBy/createTime/updatedBy/updateTime/remark |

unique-key：`UK_NOP_DATAV_SCREEN_SNAPSHOT_SCREEN_VER(screenId, snapshotVersion)`。indexes：`IX_NOP_DATAV_SCREEN_SNAPSHOT_SCREEN(screenId)`。

### 1.4 dict `datav/screen-adaptor`

`valueType=int`，与仓库既有 dict 惯例（`datav/panel-type`/`datav/publish-status` 等 valueType=int）一致。

| code | label | value |
|------|-------|-------|
| HEIGHT_FIRST | 高度优先 | 0 |
| FULL | 整体铺满 | 10 |
| KEEP | 保持原始 | 20 |

### 1.5 被拒替代方案

- **复用 Dashboard + layoutMode=free**：拒绝。理由：(1) 大屏的 screenWidth/screenHeight/adaptorMode/backgroundConfig 是看板没有的概念，复用需在 Dashboard 新增 nullable 列或 JSON 字段，主表查询/权限过滤复杂化；(2) 自由画布绝对定位与看板网格布局语义不同，混用 layoutMode 增加运行时分支；(3) DataEase 仪表板/大屏分离已成业界惯例。→ 独立实体更清晰。

- **复用 NopDatavPanel 作 widget**：拒绝。理由：当前 `NopDatavPanel`（`nop-datav.orm.xml`）**无 x/y/w/h/z 定位字段**；复用需在已 done 的 D0 ORM 新增列（跨已-done-plan 结构变更，触发 plan-first + 看板面板/大屏 widget 双重身份耦合，查询/权限按 dashboardId vs screenId 双归属区分复杂）。→ `NopDatavScreenWidget` 独立实体，自带定位 + 组件类型标识。

- **泛化/多态快照（ownerType+ownerId）**：拒绝。引用 `model-design.md` §拒绝的替代方案：多态关联增加查询复杂度、FK 难表达、权限管理复杂化。→ 大屏独立快照表（screenId + snapshotVersion UK），与 Dashboard 同模式但独立。

## 2. 画布布局 JSON schema

**决策：画布尺寸存于 Screen 实体列（screenWidth/screenHeight/adaptorMode），widget 定位存于 ScreenWidget 实体列；布局整体快照序列化为 JSON 存 ScreenSnapshot.snapshotContent。后端不强制单一「布局 JSON 文档」格式，因为实体归一化已表达编辑态，快照 JSON 仅是发布产物。**

### 2.1 编辑态（实体归一化）

- Screen 行：含 screenWidth/screenHeight/adaptorMode/backgroundConfig
- ScreenWidget 行集合：每行含 componentType/datasetRefId/x/y/w/h/z/widgetConfig

### 2.2 快照 JSON schema（ScreenSnapshot.snapshotContent）

```json
{
  "screenName": "...",
  "displayName": "...",
  "description": "...",
  "screenWidth": 1920,
  "screenHeight": 1080,
  "adaptorMode": 10,
  "backgroundConfig": { ... },
  "widgets": [
    {
      "widgetId": "...",
      "widgetName": "...",
      "displayName": "...",
      "componentType": "chart",
      "datasetRefId": "...",
      "x": 100, "y": 200, "w": 600, "h": 400, "z": 0,
      "widgetConfig": { ... }
    }
  ]
}
```

### 2.3 被拒替代方案

- **画布布局整体存为单一 JSON 列（Dashboard.layoutConfig 式）**：拒绝。理由：(1) 归一化多表支持 widget 独立 CRUD，编辑时修改单个 widget 不需重写整 JSON；(2) FK/索引在归一化表上更有效；(3) 与 D0 Panel 归一化模式一致。→ widget 独立表。

## 3. 屏幕适配语义

**决策：后端给出「画布基准尺寸 + 适配模式」，前端据此计算 transform；后端不做像素级渲染计算。**

| adaptorMode（int） | code | 语义 | 前端缩放算法（参考） |
|----|------|------|---------------------|
| 0 | HEIGHT_FIRST | 高度优先等比缩放（宽度可滚动） | scale = viewportHeight / screenHeight；width 按 scale 计算，超出视口横向滚动 |
| 10 | FULL | 整体等比铺满视口 | scale = min(viewportWidth/screenWidth, viewportHeight/screenHeight)；居中 |
| 20 | KEEP | 保持原始尺寸不缩放 | scale = 1；超出视口滚动 |

后端 `getScreenLayout` 返回的适配配置仅含 `{baseWidth, baseHeight, adaptorMode}`，前端按上表自行计算（前端实现走 nop-chaos-flux，不在本 plan 范围）。

三种模式输出必须可区分：`adaptorMode` 值不同即不同模式，后端不做归一化/降级。

## 4. `getScreenLayout` API 契约

**决策：新建 API（非 D0 复用），`@BizQuery`，读已发布快照，返回结构化的 `ScreenLayoutConfig` + 适配配置。**

| 项 | 值 |
|----|----|
| action | `NopDatavScreen.getScreenLayout(id, context)` |
| 类型 | `@BizQuery` |
| 权限 | `@Auth(permissions = "NopDatavScreen:getScreenLayout")` |
| 输入 | `id: string`（screenId） |
| 行级权限 | `requireEntity(id, "getScreenLayout", context)` → `checkDataAuth`（owner 或 admin 可读编辑态；本 action 读已发布快照，已发布内容对所有登录用户可见——与 Dashboard.getPublishedDashboard 同语义，admin/owner 始终可读，user 经发布状态过滤后也可读已发布） |
| 数据源 | 已发布快照（`NopDatavScreenSnapshot` 最新版本）；无快照抛 `ERR_DATAV_SCREEN_SNAPSHOT_NOT_FOUND` |
| 返回 | `ScreenLayoutConfig`（解析后） |

### 4.1 `ScreenLayoutConfig` 返回结构

```json
{
  "screenId": "...",
  "screenName": "...",
  "displayName": "...",
  "canvas": {
    "width": 1920,
    "height": 1080,
    "adaptorMode": 10,
    "backgroundConfig": { ... }
  },
  "adaptation": {
    "baseWidth": 1920,
    "baseHeight": 1080,
    "adaptorMode": 10
  },
  "widgets": [
    {
      "widgetId": "...",
      "componentType": "chart",
      "datasetRefId": "...",
      "x": 100, "y": 200, "w": 600, "h": 400, "z": 0,
      "widgetConfig": { ... }
    }
  ],
  "snapshotVersion": 3
}
```

`canvas` 与 `adaptation` 字段值相同但语义不同：`canvas` 描述画布定义，`adaptation` 描述适配基准。分离便于前端不同模块消费（画布渲染器 vs 适配器），且未来 D4-3 主题可在 `adaptation` 扩展而不影响画布定义。

### 4.2 与 `getPublishedScreen` 的职责区分

| API | 返回 | 用途 |
|-----|------|------|
| `getPublishedScreen(id, context)` | `NopDatavScreenSnapshot` 实体原文（snapshotContent 为 JSON 字符串） | 原始发布产物，管理/审计用 |
| `getScreenLayout(id, context)` | `ScreenLayoutConfig`（结构化解析 + 适配配置 + widget 越界/未知组件校验） | 前端渲染消费，含运行时校验 |

两者职责区分：前者返回原文，后者返回解析结果 + 校验。前端默认用 `getScreenLayout`；需要原文（如导出）用 `getPublishedScreen`。

## 5. 发布/快照

**决策：`NopDatavScreenSnapshot` 独立表，publish/getPublished/rollback 与 D0 Dashboard 模式一致。**

### 5.1 Biz Action 契约

| Action | 类型 | 主表读写 | 快照表读写 |
|--------|------|----------|------------|
| publishScreen | @BizMutation | 更新 publishStatus/publishedVersion/publishedBy/publishedTime | INSERT 新快照行 |
| getPublishedScreen | @BizQuery | 仅读 | 查询最新快照 |
| rollbackScreen | @BizMutation | 从快照内容恢复 screenWidth/screenHeight/adaptorMode/backgroundConfig/publishStatus/publishedVersion/publishedBy/publishedTime | 查询指定版本快照 |

版本号生成策略：当前大屏最大快照版本 + 1（从快照表查询，非主表 publishedVersion 字段），与 D0 一致。

### 5.2 被拒替代方案

见 §1.5（泛化/多态快照已拒）。

## 6. 权限

**决策：大屏 CRUD/发布沿用 D3-1 action `@Auth` + owner（`createdBy`）行级 RLS，与 Dashboard 同模式。**

### 6.1 action 权限点（`nop-datav-web/.../nop/datav/auth/nop-datav.action-auth.xml`）

新增权限点 + 默认角色绑定（roles 属性）：

- `NopDatavScreen:query`（query 类）→ admin,user
- `NopDatavScreen:mutation`（mutation 类）→ admin
- `NopDatavScreen:publishScreen` → admin
- `NopDatavScreen:getPublishedScreen` → admin,user
- `NopDatavScreen:rollbackScreen` → admin
- `NopDatavScreen:getScreenLayout` → admin,user
- `NopDatavScreenWidget:query/mutation` → admin,user / admin
- `NopDatavScreenSnapshot:query/mutation` → admin,user / admin

### 6.2 行级 RLS（`nop-datav-service/.../nop/datav/auth/nop-datav.data-auth.xml`）

`NopDatavScreen`：admin 无 filter；user `createdBy == $context.userName OR publishStatus == 10`（与 Dashboard 同语义）。

### 6.3 被拒替代方案

- 细粒度 widget 级 RLS：拒绝。widget 是大屏内部组成，权限应在大屏级收口（与大屏同可见性）；widget 级 RLS 增加复杂度且无独立用例。

## 7. widget 越界/重叠校验

**决策：运行时校验，在 `ScreenLayoutConfig` 解析时执行；越界抛异常（非降级），重叠仅告警（不阻断，因合法场景如装饰层叠）。**

### 7.1 校验规则

| 规则 | 失败动作 | 错误码 |
|------|----------|--------|
| widget `x + w ≤ canvas.width` | 抛异常 | `ERR_DATAV_SCREEN_WIDGET_OUT_OF_BOUNDS` |
| widget `y + h ≤ canvas.height` | 抛异常 | `ERR_DATAV_SCREEN_WIDGET_OUT_OF_BOUNDS` |
| widget `x ≥ 0 && y ≥ 0 && w > 0 && h > 0` | 抛异常 | `ERR_DATAV_SCREEN_WIDGET_OUT_OF_BOUNDS` |
| widget `componentType` 未在 `PanelComponentRegistry` 注册 | 抛异常 | `ERR_DATAV_SCREEN_WIDGET_UNKNOWN_COMPONENT` |
| 画布/widget JSON 非法（解析失败） | 抛异常 | `ERR_DATAV_INVALID_SCREEN_LAYOUT` |
| widget 重叠 | **不阻断**（仅告警；装饰层叠合法） | — |

### 7.2 校验时机

- **运行时**：`getScreenLayout` 解析已发布快照时执行（前端消费前保证一致）。
- **配置时**（编辑器保存）：本 plan 不实现配置时校验；编辑器走 flux，未产出。配置时校验为 Non-Blocking Follow-up。

### 7.3 被拒替代方案

- **重叠也抛异常**：拒绝。理由：大屏装饰组件（D4-2）合法场景含层叠（如边框叠加在 chart 上）；强制无重叠会阻断合法设计。→ 仅告警。

## 8. 与既有模型/组件的关系

| 既有资产 | 复用方式 |
|---------|----------|
| `datav/publish-status` dict | 直接复用（DRAFT=0/PUBLISHED=10） |
| `datav/panel-type` dict | **不复用**——widget.componentType 用字符串标识（"chart" 等），与 `PanelComponentRegistry` 类型标识一致（D1-1），不经 dict int 映射（widget 无 panelType int 列，直接存字符串） |
| `PanelComponentRegistry.requireComponent` | 直接复用（D1-1），校验 widget.componentType |
| `NopDatavDatasetRef` | **不直接 FK 引用**——widget.datasetRefId 是逻辑引用（与 Panel.datasetRefId 同语义），可指向任一 DatasetRef 行或外部 nop-report 数据集标识；本 plan 不验证 datasetRefId 存在性（运行时取数在 D1，不在本 plan 范围） |
| D0 主表+快照表模式 | 直接复用模式（独立实体独立建表） |
| D3-1 action `@Auth` + owner RLS | 直接复用模式 |
| domains（json-4000/clobJson/version/createdBy 等） | 直接复用 |

## 9. 模块结构

实体 ORM 源模型编辑于 `nop-datav/model/nop-datav.orm.xml`，codegen 经 `nop-datav-codegen/postcompile/gen-orm.xgen` 生成 dao/entity/meta/beans 到既有 8 件套模块。BizModel 位于 `nop-datav-service/.../service/entity/NopDatavScreenBizModel.java`，布局协议/适配解析位于 `nop-datav-service/.../service/screen/`（新建子包）。包名约定 `io.nop.datav`，与既有实体一致。

## 10. 装饰/媒体组件族（D4-2）

**决策：在既有 `PanelComponentRegistry`（D1-1）登记 6 类装饰/媒体组件，均为 `needsDataset=false`；扩展 `PanelComponentMeta` 携带命名配置区域描述符；通过 `NopDatavScreenBizModel.getComponentTypes` 暴露元信息查询。**

本节是 D4-2 的最终结论。D4-1 已建立自由画布布局协议并将 `widget.componentType` 经 `PanelComponentRegistry.requireComponent` 校验；本节在该注册表上登记装饰/媒体类型并定义其配置 schema 描述符。模型层交付（注册表登记 + 配置 schema + 元信息暴露），不含前端渲染（走 nop-chaos-flux，未产出），不含媒体代理/流后端实现。

### 10.1 组件族清单

6 类装饰/媒体组件，类型标识稳定不变，均 `needsDataset=false`（纯展示/媒体，不绑定数据集；轮询 Tab 是容器式组件，承载其他 widget，但其自身不取数）：

| 类型标识 | 显示名 | 用途 | 配置区域 |
|---------|--------|------|---------|
| `decorative-border` | Decorative Border | 装饰边框（大屏视觉装饰，可叠加在 chart 上，§7.3 装饰层叠合法） | `variant`（边框样式变体，必填）+ `color`（边框颜色） |
| `scroll-text` | Scroll Text | 滚动文字（公告/跑马灯） | `text`（滚动文本内容，必填）+ `speed`（滚动速度）+ `direction`（滚动方向） |
| `time-clock` | Time Clock | 时间时钟（实时显示当前时间） | `format`（时间格式模板，如 `yyyy-MM-dd HH:mm:ss`）+ `timezone`（时区） |
| `video` | Video | 视频（点播/静态视频源） | `src`（视频源 URL，必填）+ `autoplay`（是否自动播放）+ `loop`（是否循环）+ `controls`（是否显示控件） |
| `stream` | Stream | 流媒体（直播/实时流） | `src`（流源 URL，必填）+ `protocol`（流协议，如 hls/rtmp） |
| `carousel-tab` | Carousel Tab | 轮播 Tab（按间隔切换显示内嵌 widget 组） | `tabs`（Tab 定义列表，必填）+ `interval`（切换间隔秒数） |

以上类型标识集中在 `PanelComponentTypes`；登记在 `PanelComponentRegistry` 静态初始化块，保留既有重复检测（`IllegalStateException` on duplicate type）与未知类型拒绝（`ERR_DATAV_UNKNOWN_COMPONENT_TYPE`，不静默降级）。

### 10.2 元信息描述符方案

**决策：扩展 `PanelComponentMeta` 使其可携带命名配置区域描述符（机器可读）。**

描述符结构（每个区域）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | String | 配置区域名称（如 `src` / `autoplay`） |
| `description` | String | 用途说明（如 "视频源 URL"） |
| `required` | boolean | 是否必填 |

向后兼容：既有 8 类组件（D1-1，无描述符）在扩展后的元信息中按「空配置区域列表」处理（即「该类型目前未声明命名配置区域」），不破坏现有注册与测试。新增的 6 类装饰/媒体组件携带非空描述符列表。

**被拒替代方案：仅文档约定、无运行时描述符。** 拒绝。理由：配置 schema 是 D4-2 的显式交付物；纯文档无法被 `getComponentTypes` API 消费，也无法在测试中断言，等于「注册表只回答哪些类型存在，不回答每种类型接受什么配置」，与 D4-2 交付目标不符。

### 10.3 不做硬 JSON Schema 校验

**决策：配置 schema 为描述符 + 向前兼容约定，注册表与 `getScreenLayout` 不强制拒绝含未声明区域的 `widgetConfig`。**

`PanelComponentMeta.configAreas` 是描述性的（供前端/测试消费），不是运行时校验器。校验 `widgetConfig` JSON Schema 与否属组件实现层细节，由 nop-chaos-flux 控件族落地时决定；后端 `ScreenLayoutParser` 仅校验 `componentType` 是否注册（D4-1 既有行为不变）。

**理由**：(1) 与 D1-1 「panelConfig 区域约定、字段级细节由 flux 定稿」哲学一致；(2) 强制 JSON Schema 校验会在 flux 控件 schema 未定稿时阻断合法配置演进。强制校验为 Non-Goal。

### 10.4 复用 PanelComponentRegistry

**决策：装饰/媒体组件登记进既有 `PanelComponentRegistry`，不新建独立 `ScreenComponentRegistry`。**

D4-1 已复用该注册表校验大屏 widget（`ScreenLayoutParser.validateComponentType` / `parseWidget`）。新建独立注册表会割裂组件类型空间、增加 `ScreenLayoutParser` 分发分支。

**被拒替代方案：独立 `ScreenComponentRegistry`。** 拒绝。理由：(1) 同一类型空间更简单，看板面板因无对应 `panelType` dict 项天然不会误引用装饰组件；(2) `widget.componentType` 是 string（非 dict int），不经 `panelType` dict 映射，无需为新类型新增 dict 选项；(3) D4-1 已验证复用模式可行。

### 10.5 与看板面板的边界

装饰/媒体组件是大屏专用，看板面板不引用：

| 路径 | 组件类型空间 | 映射方式 |
|------|-------------|---------|
| 看板面板（`NopDatavPanel.panelType` int） | 经 `PanelTypeMapping` ↔ `PanelComponentTypes` | dict `datav/panel-type` 8 个 int 值（0/10/20/30/40/50/60/70），装饰/媒体组件不在此 dict |
| 大屏 widget（`NopDatavScreenWidget.componentType` string） | 直接字符串标识，经 `PanelComponentRegistry.requireComponent` 校验 | 无 dict int 映射 |

`PanelTypeMapping` 不为装饰/媒体类型新增 int 映射——看板面板（int 映射）天然不会引用装饰组件。

### 10.6 组件元信息查询 API

**决策：在 `NopDatavScreenBizModel` 新增 `getComponentTypes()`（`@BizQuery`，无 screenId 参数），返回全部已注册组件的类型标识 + 显示名 + needsDataset + 配置区域描述符。**

| 项 | 值 |
|----|----|
| action | `NopDatavScreen.getComponentTypes()` |
| 类型 | `@BizQuery` |
| 权限 | `@Auth(permissions = "NopDatavScreen:getComponentTypes")` |
| 输入 | 无（全局查询，不绑定特定大屏） |
| 返回 | `List<PanelComponentMeta>`（含 D4-1 既有 8 类 + D4-2 新增 6 类，共 14 类） |

**裁定：放在 `NopDatavScreenBizModel`（大屏是组件注册表的主要消费方），不新建 entity-less BizModel。** 避免新增 `_service.beans.xml` bean 定义与独立 action-auth 命名空间；与既有 4 个 screen action 同模式（接口声明 + action-auth 权限点）。权限点 `NopDatavScreen:getComponentTypes` 在 `nop-datav.action-auth.xml` 增配，默认 `admin,user` 可读（与 `getScreenLayout` 同语义，元信息对所有可读大屏的角色可见）。
