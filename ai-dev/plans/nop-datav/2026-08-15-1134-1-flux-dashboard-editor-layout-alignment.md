# 1 D1-4 flux dashboard editor 布局对齐 — 后端布局导出/保存回写 API

> Plan Status: completed
> Last Reviewed: 2026-08-15
> Draft Review: 三轮独立子 agent 对抗性审查（含想象性分析）通过——round1 修复 2 Blocker + 3 Major（props↔panelConfig 映射裁定、存量无几何导出裁定、serializeDashboardContent 先例归因、保存载荷形态、json-4000 容量判据）；round2 修复路径前缀/PanelTypeMapping 事实/displayName 派生/双向缺口枚举；round3 验证通过（round-3 reviewer 明确「可进入执行（active）：无 Blocker」）
> Source: roadmap `ai-dev/backlog/nop-datav-roadmap.md` D1-4（「flux dashboard editor 布局 JSON 与 nop-datav layoutJson 双向对齐，flux 侧落地后对接」）；flux 侧 dashboard editor 已落地（`nop-chaos-flux:packages/flux-renderers-dashboard`，plan `2026-08-09-182611` full-green，commit `a2ac7f5d7`）
> Related: D0 plan `2026-08-09-2255-1-dashboard-model-crud-and-tests.md`、D1-2 plan `2026-08-10-1000-2-dashboard-runtime-panel-data-binding-refresh.md`、`ai-dev/design/nop-datav/runtime-design.md`、后续 plan `2026-08-15-1134-2`（D2-4）
> Mission: nop-datav
> Work Item: D1-4

## Purpose

把 D1-4 从 `todo` 收口：nop-datav 后端提供 flux dashboard editor 布局 JSON（`DashboardLayoutSchema`）与归一化看板模型（`NopDatavDashboard` + `NopDatavPanel`/`NopDatavDashboardTab`/`NopDatavDatasetRef` 行）的**双向对齐能力**——

1. **导出**：任一看板可导出为 flux 编辑器直接可加载的布局 JSON；
2. **保存回写**：flux 编辑器产出的布局 JSON 可保存回 nop-datav，reconcile（增/删/改）回归一化面板行，且不破坏数据绑定（datasetRef）与查询管线（`getDashboardData`）。

使「flux 编辑器 ⇄ nop-datav 看板」的加载/编辑/保存闭环在**后端侧**成立。前端编辑器本身的改造在 flux 仓库，不在本计划内。

## Current Baseline

- **flux 侧（外部仓库，已落地）**：`nop-chaos-flux:packages/flux-renderers-dashboard` 提供运行态 renderer + 编辑器（editor-core 内核、palette、canvas、inspector、undo/redo diff）。布局 schema：`{ type:'dashboard', panels:[{ id,type,title,x,y,w,h,props?,source? }], cols?(12), rowHeight?(40), gap?(8), height?, empty? }`，网格坐标 x/y/w/h 编辑态与运行态同构。palette 内建类型：`chart` / `table` / `stat-tile` / `iframe` / `html` / `text`。编辑会话文档 = 面板数组，diff-based 保存（`DashboardLayoutDiff`：patches/added/removed，以 panel `id` 为锚点）。
- **nop-datav 侧（本仓库）**：
  - `NopDatavDashboard.layoutConfig`（`json-4000`）：看板级布局 JSON，后端透传存储（发布/回滚时随快照整体序列化/恢复），**内部结构未被后端契约钉死**。
  - `NopDatavPanel` 归一化行：`panelId`（主键，seq）、`panelType`（**int 字典** `datav/panel-type`：0=图表/10=表格/20=指标/30=文本/40=容器/50=透视表/60=地图/70=内嵌页面）、`datasetRefId`、`tabId`、`sortOrder`、`panelConfig`（`json-4000`）。**无 x/y/w/h 几何列**。
  - `PanelComponentRegistry`（14 类**字符串**类型：chart/pivot-table/stat-tile/map/table/text/iframe/container + 6 类装饰/媒体），查询未知类型显式抛 `ERR_DATAV_UNKNOWN_COMPONENT_TYPE`；类型元信息暴露先例为 `NopDatavScreen:getComponentTypes`（screen 侧 action）。dict int ↔ registry 字符串的映射腿**已裁定并实现**（`PanelTypeMapping` 全量双向映射 0-70 ↔ 8 类字符串，runtime-design §1.3 裁定表）。
  - 模型整体序列化先例：`NopDatavDashboardBizModel` 私有 `serializeDashboardContent`（发布快照路径，dashboard + panels + tabs + datasetRefs 序列化为 JSON；注意与 D3-3 的 `exportDashboard` 数据导出 action 无关，勿混淆）；`getDashboardData` 按 `sortOrder` 加载归一化面板执行数据集查询（并行 + 缓存，runtime-design §八）。
  - 发布/快照/回滚（D0-2）、权限点模式已确立（源文件 `nop-datav/nop-datav-web/src/main/resources/_vfs/nop/datav/auth/nop-datav.action-auth.xml`，`_` 前缀为生成物禁改；测试侧另有 `nop-datav/nop-datav-service/src/test/resources/_vfs/test/datav/auth/app.action-auth.xml` 需同步登记）。
  - **flux 编辑器保存载荷形态**：编辑会话文档 = 面板数组，`serialize` 输出**仅面板数组 JSON**；网格配置（cols/rowHeight/gap/height）经 schema props 传入，不属会话文档、不在保存载荷中（`nop-chaos-flux:packages/flux-renderers-dashboard/src/editor/dashboard-domain-adapter.ts`）。
  - **存量看板无几何来源**：现有模型任何位置都不存 x/y/w/h（`panelConfig` 区域约定无几何区，`layoutConfig` 未钉死且通常为空）——未经 flux 保存过的存量看板导出时无几何可读。
- **gap（本计划要消除的）**：
  1. 无任何 API 产出 flux 兼容的布局 JSON（现状 flux 编辑器无法加载 nop-datav 看板）；
  2. 无保存回写路径（flux 编辑器保存的布局 JSON 无法回灌归一化面板行）；
  3. 类型词表映射缺口：flux palette 字符串 ↔ registry 字符串这条腿无映射裁定（dict ↔ registry 腿已有 `PanelTypeMapping`；flux `html` 在 nop-datav 无对应项，反向 registry 的 pivot-table/map/container 及 6 类装饰/媒体类型在 flux palette 无对应项）；
  4. 面板身份（flux client 生成 `id` ↔ 服务端 seq `panelId`）、几何存放（`layoutConfig` vs `panelConfig`）、tabs 语义、数据绑定描述（flux `source` 表达式 ↔ `datasetRefId`）、面板配置区（flux `props` ↔ `panelConfig` 结构化区域）、存量无几何看板的导出行为、保存载荷形态（面板数组 vs 完整布局）、roundtrip 等价判据均未裁定。

## Goals

- 布局对齐**契约定稿**：flux `DashboardLayoutSchema` ↔ nop-datav 归一化模型的映射裁定（面板身份、类型映射、几何与网格参数存放、面板配置区 props 映射、保存载荷形态、存量无几何看板导出行为、tabs 语义、数据绑定描述与保护、面板数量上界、roundtrip 等价判据），写入 `runtime-design.md` 新章节（D1-4 契约）。
- **导出 API**：按契约定义的 action 返回 flux 编辑器可直接消费的布局 JSON（含类型映射、几何、网格参数、数据绑定描述）。
- **保存回写 API**：接受 flux 编辑器产出的布局 JSON → 显式校验 → reconcile 归一化面板行（新增/更新/删除，按裁定）→ 持久化（事务内）；保存后数据绑定与 `getDashboardData` 查询管线不受破坏。
- **roundtrip 语义等价**：导出 → 编辑 → 保存 → 再导出，布局语义无损（按契约定义的等价判据）。

## Non-Goals

- 前端 flux 编辑器/渲染器的任何改造（flux 仓库 scope；本计划只交付后端对齐层）。
- 图像导出 PDF/PNG、大屏缩略图生成（渲染能力，待前端/无头渲染后继 plan）。
- 大屏（screen）编辑器对齐（D4 前端渲染，独立后继）。
- 筛选/联动的前端约定对齐（D2-4，plan `2026-08-15-1134-2`）。
- 发布/快照/回滚语义变更（D0 已定，本计划导出/保存均作用于**编辑态**主表数据）。
- ORM 结构变更（不新增列；几何/网格参数裁定存入既有 JSON 列。若 Phase 1 裁定确需 ORM 变更，则该裁定必须回写本计划并按 Protected Area 规则单独评估，默认拒绝）。

## Scope

### In Scope

- `ai-dev/design/nop-datav/runtime-design.md` 新增 D1-4 布局对齐契约章节（含拒绝的替代方案）。
- 导出 action + 保存回写 action（`NopDatavDashboardBizModel` 或裁定的 BizModel），含 `@Auth` 权限点与 action-auth 配置（源文件 `nop-datav/nop-datav-web/src/main/resources/_vfs/nop/datav/auth/nop-datav.action-auth.xml` + 测试侧 `nop-datav/nop-datav-service/src/test/resources/_vfs/test/datav/auth/app.action-auth.xml`）。
- 类型词表映射、几何/网格参数存取、reconcile 逻辑、身份冲突检测、数据绑定保留、面板上界校验。
- 配套错误码（`NopDatavErrors`，英文消息）。
- 单元测试 + 端到端 roundtrip 测试。

### Out Of Scope

- flux 侧仓库任何代码/文档变更。
- `NopDatavPanel`/`NopDatavDashboard` ORM 列变更。
- 数据集引用（DatasetRef）的编辑能力（保存路径仅**保留/透传**既有绑定，不提供绑定编辑）。

## Execution Plan

### Phase 1 - 布局对齐契约定稿

Status: completed
Targets: `ai-dev/design/nop-datav/runtime-design.md`

- Item Types: `Decision`

- [x] 裁定**面板身份映射**：flux panel `id` ↔ `panelId` 的对应规则（编辑器新增面板的身份如何产生、保存时如何与服务端既有面板对齐、身份冲突如何显式报错）；连带裁定新增/更新面板行名字列派生（`panelName` mandatory 列从 title 派生规则、`displayName` 与 title 的对应关系——导出 title 取自哪列、保存 title 改动写回哪列、roundtrip 等价判据比较哪列、`sortOrder` 从数组序派生规则）
- [x] 裁定**action 命名**：导出/保存 action 命名避免与 D3-3 既有 `exportDashboard`（数据导出任务）语义冲突
- [x] 裁定**类型词表映射**：flux palette 字符串 ↔ `PanelComponentTypes` 字符串映射表（复用既有 `PanelTypeMapping` 对接 dict int，不重建已裁定腿）；双向缺口处置——flux `html`（nop-datav 无对应项）与 registry 的 pivot-table/map/container 及 6 类装饰/媒体类型（flux palette 无对应项）的导出/保存行为（显式拒绝或扩展注册表+字典——若裁定扩展，扩展登记在本计划 Phase 2/3 内完成，不得后置；未映射输入 fail-fast 不得静默丢弃）
- [x] 裁定**几何与网格参数存放**：x/y/w/h/title 与 cols/rowHeight/gap/height 存放位置（`layoutConfig` 采纳 flux 结构或 `panelConfig` 分散存放），判据：roundtrip 无损 + 后端契约钉死 + 不新增 ORM 列 + **`layoutConfig` 为 `json-4000`（VARCHAR 4000）的容量上界**（按 max-panels≈50 面板估算序列化长度，超限风险须进裁定——溢出时显式报错或裁定其他既有 JSON 列，不得截断）
- [x] 裁定**存量无几何看板的导出行为**：未经 flux 保存过的存量看板（无任何几何来源）导出时——默认布局合成（如按 sortOrder 顺序网格排布）或显式拒绝——二选一裁定，不得让执行者自行发明
- [x] 裁定**面板配置区映射**：flux panel `props` ↔ `panelConfig` 结构化区域（title/fieldMapping/styleOptions/dataBinding/refresh/content 等，runtime-design §一）的双向映射——导出如何从区域构造 `props`、保存时编辑器改过的 `props` 写回哪些区域/保护哪些区域（如 fieldMapping/dataBinding/refresh 是否允许经布局保存改写）
- [x] 裁定**保存载荷形态**：保存 API 接受「flux 编辑器实际输出的面板数组 JSON」还是「完整 DashboardLayoutSchema」；网格参数在保存路径的处理（不随编辑器保存到达——载荷不含则保留不动，含则更新）
- [x] 裁定 **tabs 语义**：`NopDatavDashboardTab`/`tabId` 与 flux 平铺网格布局的对应关系（v1 单页平铺如何处理既有 tab 数据——保留不动或显式拒绝多 tab 看板导出编辑，不得静默丢失）
- [x] 裁定**数据绑定描述与保护**：导出时 `datasetRefId` 如何进入布局 JSON（如 panel 数据绑定描述区）；保存时对绑定字段的保护策略（仅保留既有绑定，编辑器不可经布局保存改写/丢失绑定；越权或失效引用显式报错）
- [x] 裁定**面板数量上界**：保存路径的上限校验（对齐 `nop.datav.dashboard-query.max-panels` 既有配置语义或独立配置项，防无界建面板）
- [x] 裁定 **roundtrip 等价判据**：导出→保存→再导出的等价谓词（哪些字段参与比较、面板顺序是否敏感、id 稳定性、被保护区域是否豁免比较），作为 Phase 4 E2E 断言的直接输入
- [x] 契约章节写入 `runtime-design.md`（含每项裁定拒绝的替代方案及理由）

Exit Criteria:

- [x] `runtime-design.md` 含 D1-4 布局对齐契约章节，覆盖上述全部裁定项
- [x] 每项裁定附拒绝的替代方案及理由（design doc 规范）
- [x] 无静默跳过裁定：任何「无法映射/无法保留」的输入在契约中均规定为显式错误（对应错误码命名进契约）
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] No owner-doc update required beyond上述（nop-datav 设计契约归属 `ai-dev/design/nop-datav/`；`docs-for-ai/` 无 nop-datav 专章，无需更新）
- [x] No new test required: 纯 Decision/文档 phase，契约的可验证性由 Phase 2/3/4 测试承载

### Phase 2 - 导出 API

Status: completed
Targets: `nop-datav/nop-datav-service`（BizModel action + 映射实现）、`nop-datav/nop-datav-web/src/main/resources/_vfs/nop/datav/auth/nop-datav.action-auth.xml`、`nop-datav/nop-datav-service/src/test/resources/_vfs/test/datav/auth/app.action-auth.xml`

- Item Types: `Fix | Proof`

- [x] 实现导出 action（`@BizQuery` + `@Auth`，Phase 1 裁定命名；权限点同步登记 action-auth 源文件与测试侧 auth 资源）：读取看板编辑态（主表 + 面板行），按契约产出 flux `DashboardLayoutSchema` 形态 JSON
- [x] 实现类型词表映射（Phase 1 契约的映射表）；无法映射的类型显式抛契约规定的错误码
- [x] 测试：有数据集面板 / 无数据集面板（text 等）/ 各类型映射 / 未知类型显式报错 / 多 tab 看板按契约行为 / **存量无几何看板按契约行为（默认布局合成或显式拒绝）**

Exit Criteria:

- [x] 导出 action 存在且经 `@Auth` 保护，权限点已在 action-auth 源文件与测试侧 auth 资源登记
- [x] 导出结果满足：panels 数组含 id/type/title/x/y/w/h（按契约存放裁定），网格参数齐全，数据绑定描述与面板配置区（props）进入契约规定的区域
- [x] 新增测试显式列出并全绿（测试类名/用例名进 daily log）；每个新分支（未知类型、多 tab、存量无几何）有对应断言
- [x] 无静默跳过：未知/不可映射类型走显式错误码（非 skip/null）
- [x] No owner-doc update required in this Phase（契约章节已在 Phase 1 定稿；如实现与契约偏差，回写 `runtime-design.md` 属 Phase 4 终稿校对项）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 保存回写 API

Status: completed
Targets: `nop-datav/nop-datav-service`（action + reconcile + 事务）、`nop-datav/nop-datav-web/src/main/resources/_vfs/nop/datav/auth/nop-datav.action-auth.xml`、`nop-datav/nop-datav-service/src/test/resources/_vfs/test/datav/auth/app.action-auth.xml`

- Item Types: `Fix | Proof`

- [x] 实现保存 action（`@BizMutation` + `@Auth`，权限点两处登记）：接受 Phase 1 裁定形态的保存载荷（flux 编辑器面板数组或完整布局）→ 结构校验（panels 数组、id 唯一非空、几何数值合法、类型可映射）→ 按契约 reconcile 面板行（新增/更新/删除）→ 几何/网格参数/props 按裁定持久化 → 单事务提交
- [x] 身份冲突与失效引用显式报错（错误码进 `NopDatavErrors`，英文消息）
- [x] 数据绑定保留：保存后既有 `datasetRefId` 按契约保留（或显式报错），`getDashboardData` 对保存后面板集的查询行为不变
- [x] 面板上界校验：超限显式拒绝
- [x] 测试：纯新增 / 纯删除 / 混合 reconcile / 重复 id / 未知类型 / 超上限 / 绑定保留断言 / props 区域写回与保护区域断言 / 事务原子性（校验失败不落库）

Exit Criteria:

- [x] 保存 action 存在且经 `@Auth` 保护，权限点已在两处 auth 资源登记
- [x] reconcile 语义与契约一致：diff 三类（增/删/改）各有定向测试且全绿
- [x] 失败路径全部显式报错（重复 id/未知类型/超上限/绑定失效/载荷形态非法），无静默丢弃面板、绑定或 props 区域
- [x] 校验失败时零落库（事务回滚断言）
- [x] 新增测试显式列出并全绿
- [x] No owner-doc update required in this Phase（同 Phase 2，契约偏差回写归 Phase 4）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 端到端 roundtrip 验证与收尾

Status: completed
Targets: `nop-datav/nop-datav-service`（E2E 测试）、`runtime-design.md`（如需终稿校对）

- Item Types: `Proof`

- [x] E2E 测试：创建看板（含数据集面板 + 无数据集面板）→ 导出 → 模拟编辑器编辑（移动几何/新增面板/删除面板/改标题/改 props）→ 保存 → 再导出，断言 roundtrip 按契约等价判据（Phase 1 裁定）语义等价
- [x] 接线验证：保存后的面板行被 `getDashboardData` 实际消费（断言查询结果包含保存后仍存在绑定的面板数据，而非仅返回面板行）
- [x] 发布路径回归：保存后看板可 `publishDashboard`，快照内容含保存后布局（既有发布链路不被破坏）

Exit Criteria:

- [x] **端到端验证**：导出→编辑→保存→再导出→`getDashboardData` 完整链路单测全绿（Minimum Rules #22）
- [x] **接线验证**：`getDashboardData` 对保存后面板集的查询断言存在且全绿（Minimum Rules #23）
- [x] 既有测试全绿（无回归）
- [x] `runtime-design.md` 契约章节与实现终态一致（实现期如有偏差，回写契约）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] D1-4 布局对齐契约定稿且与 live 实现一致（导出/保存/action 权限/错误码均可对上）
- [x] 导出 + 保存回写 + roundtrip E2E 全部落地并有定向测试
- [x] 数据绑定与 `getDashboardData` 管线经保存回写后行为不变（有断言）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] `runtime-design.md` 已同步；flux 侧对接工作显式标注为外部后续（非本计划 debt）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）导出/保存 action 在运行时被测试实际调用（非仅类型存在），（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-datav/nop-datav-service,nop-datav/nop-datav-web -am` 通过
- [x] `./mvnw test -pl nop-datav/nop-datav-service -am` 全绿
- [x] checkstyle / 代码规范检查通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-datav --severity high` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（本计划修改 `ai-dev/design/` 与 `ai-dev/plans/` 文件）

## Deferred But Adjudicated

### flux 编辑器实际对接（前端仓库工作）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: D1-4 在 nop-datav 侧的交付物是布局对齐契约 + 导出/保存 API；flux 编辑器加载/调用这些 API 的前端接线属 flux 仓库 scope（本仓库无法交付）。后端契约+API+roundtrip 测试成立即 D1-4 后端结果面成立。
- Successor Required: `yes`
- Successor Path: flux 仓库侧 dashboard editor ↔ nop-datav 对接任务（外部跟踪，非本仓库 plan）

## Non-Blocking Follow-ups

- 无（flux `html` 类型扩展如被 Phase 1 裁定为「扩展注册表+字典」，其实现已纳入 Phase 2/3 in-scope，不得后置）。

## Closure

Status Note: D1-4 后端交付面全部落地：契约定稿（runtime-design.md §九，11 项裁定 + §9.12 拒绝方案表）+ `exportDashboardLayout`/`saveDashboardLayout` action（双 auth 登记、5 新错误码、独立上界配置 `nop.datav.dashboard-layout.max-panels`）+ 编解码器 `DashboardLayoutCodec` + 30 新测试（13 导出 + 14 保存 + 3 E2E roundtrip 含 getDashboardData 接线与发布回归），nop-datav-service 530/0/0 全绿。flux 编辑器对接为外部后续（Deferred But Adjudicated）；D2-4（plan 2026-08-15-1134-2）为独立后继。
Completed: 2026-08-15

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure audit（task `ses_ffc1ae023ffegQgXqM6T8FKhUf`，fresh session）
- Evidence:
  - Phase 1 Exit Criteria：PASS——runtime-design.md §九（L362–495）覆盖全部 11 项裁定 + §9.12 拒绝方案表（10 项）
  - Phase 2 Exit Criteria：PASS——`@BizQuery`+`@Auth` action（NopDatavDashboardBizModel.java:664-666）；双 auth 登记（nop-datav.action-auth.xml:52-61 + test app.action-auth.xml:41-48）；`TestNopDatavDashboardLayoutExport` 13/13 绿（未知类型/多 tab/存量无几何合成各有定向断言）
  - Phase 3 Exit Criteria：PASS——reconcile 三路径 live code（delete :747-756 / update :762-769 / create :770-786）；7 类失败路径错误码与 NopDatavErrors 精确对上；校验先行零落库（Phase A :688-729 严格先于 Phase B 写入 :731+，4 处测试断言零落库）；`TestNopDatavDashboardLayoutSave` 14/14 绿（含删除面板 AlertRule 级联）
  - Phase 4 Exit Criteria：PASS——`TestNopDatavDashboardLayoutRoundtripE2E` 3/3 绿（roundtrip §9.11 判据 + no-op 幂等 L2≡L3；getDashboardData 真实 SQL 接线（结果行含 region 筛选值）；publishDashboard 快照含保存后布局与 displayName）
  - Closure Gates 命令：执行者跑 `./mvnw test -pl nop-datav -am -T 1C`（530/0/0 + web 1/1，BUILD SUCCESS）与 `./mvnw clean install -pl nop-datav -am -T 1C -DskipTests`（BUILD SUCCESS）；compile 门（service+web -am）exit 0；checkstyle 插件于根构建为注释禁用态，代码规范按 AGENTS.md 人工核验（import 分组 io.nop.*→第三方→java.*、静态导入最后、英文错误消息）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（本表勾选 + Closure Evidence 写入后）
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-datav --severity high` 退出码 0（0 findings；审计者独立复跑一致）
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（0 errors；6 warnings 均为无关历史 plan `ai-dev/plans/338-*.md` 既有项）
  - Anti-Hollow 检查：PASS——（a）30 次测试经 NopIoC 真实 bean 实际调用两 action（`_service.beans.xml` `ioc:default="true"`，审计者复跑 fresh surefire 30/30 绿）；（b）无空方法体/静默跳过/no-op/TODO-as-implemented；（c）create/update/delete 三路径各有定向测试执行并断言
  - Deferred 项分类检查：PASS——flux 对接为外部仓库 scope（out-of-scope improvement，successor 显式标注）；Non-Blocking Follow-ups 无隐藏 live defect

Follow-up:

- no remaining plan-owned work（flux 编辑器实际对接为外部仓库 successor，见 Deferred But Adjudicated）
