# 2 D2-4 flux dashboard-filter 约定与 nop-datav 参数模型对齐

> Plan Status: active
> Last Reviewed: 2026-08-15
> Draft Review: 三轮独立子 agent 对抗性审查（含想象性分析）通过——round1 修复 2 Major（preset 值形态核实、options 来源裁定）；round2 修复 date-range 钉死范围收窄（手工 relative 初始值仅 display 解析的 nuance 进契约）；round3 确认按其处方完成局部修订即可放行（原话「完成该修订后……plan 可转 active 进入执行」，修订已按处方落地，第四轮全文复审经 reviewer 预先豁免）
> Source: roadmap `ai-dev/backlog/nop-datav-roadmap.md` D2-4（「flux dashboard-filter 约定与 nop-datav 参数模型对齐」）；flux 侧 dashboard-filter 编排约定已落地（nop-chaos-flux `nop-chaos-flux:docs/components/dashboard-filter/design.md`，走查单测 `dashboard-filter-walkthrough.test.tsx` 4 条全绿）
> Related: D2-1 plan `2026-08-10-1030-1-dashboard-global-filter-parameters.md`、D2-2/D2-3 plan `2026-08-10-1030-2-chart-linkage-and-filter-state.md`、`ai-dev/design/nop-datav/linkage-design.md`、建议先行 plan `2026-08-15-1134-1`（D1-4 布局对齐，软顺序非硬依赖）
> Mission: nop-datav
> Work Item: D2-4

## Purpose

把 D2-4 从 `todo` 收口：nop-datav 参数模型（`paramConfig` 参数定义 + `resolveFilterValues` 校验/默认值 + `parseFilterFromUrl` URL 反序列化）与 flux **dashboard-filter 编排约定**对齐——后端从 `paramConfig` 产出 flux 看板页可直接消费的筛选定义，并钉死两侧关键形态映射（date-range 值形态、widget 词表、URL 同步），使「nop-datav 参数定义 → flux 筛选栏渲染 → 筛选值 → `getDashboardData` 参数注入」的闭环在**后端侧契约与 API 上**成立。flux 侧渲染实现不在本计划内。

## Current Baseline

- **flux 侧（外部仓库，已落地）**：dashboard-filter 是编排约定（非新控件）——筛选表单用普通 `form` 且 `valuesPath: 'filter'`，把字段值持续发布到共享 page scope `filter.*`；消费端（data-source/chart/table/stat-tile）经 `${filter?.xxx}` null-safe 表达式引用并自动重载；reset 发布空值等价未筛。示例含 select（region）+ date-range（period）。**date-range 值形态**：preset 选中值已钉死（`nop-chaos-flux:docs/components/date-range/design.md` §4.1）——运行期解析为** delimited 绝对日期字符串**（如 `'2026-01-01,2026-03-31'`；delimiter 默认 `,`，格式由 `valueFormat` 决定）；但 flux 页面若**手工配置 relative 形态初始值**（如 `'today,today'`），该解析仅用于本地 display、不写回发布值——发布初始值可保持 relative 形态，属 D2-4 消费契约之外的页面配置。
- **nop-datav 侧（本仓库）**：
  - `NopDatavDashboard.paramConfig`（CLOB JSON 数组）：参数定义 `{ name, type(string|number|date|date-range), defaultValue?, label?, widget? }`；date-range 用**扁平 key** `name.start` / `name.end`（输入输出均是）。
  - `resolveFilterValues`（@BizQuery）：按参数定义校验类型 / 填默认值 / 过滤未定义参数；输出可直接作 `getPanelData`/`getDashboardData` 的 `requestParams`。
  - `parseFilterFromUrl`（@BizQuery）：URL 查询串 → 扁平 key 参数 Map（URL 同步反序列化侧）。
  - `getDashboardData(requestParams)`：消费扁平 key，执行面板数据查询（D2-1 语义）。
- **gap（本计划要消除的）**：
  1. 无 API 产出 flux 可消费的筛选定义（flux 侧拿到的只有裸 `paramConfig`，表单 schema 组装规则无契约）；
  2. **date-range 值形态失配**：nop-datav 扁平 `name.start`/`name.end` vs flux delimited 绝对字符串（delimiter/valueFormat 由 flux 字段配置决定）——两侧转换归属与参数钉死未裁定，直接对接必然失配；
  3. `widget` 词表（如 `dropdown`/`date-picker`）与 flux 字段控件（select/date-range/input-\* 等）无映射契约；dropdown 参数的**候选值（options）来源无契约**（`paramConfig` 结构无 options 区域）；
  4. URL 同步语义（nop-datav `parseFilterFromUrl` 扁平 key ↔ flux 筛选状态的 URL 承载）无对齐裁定。

## Goals

- 对齐**契约定稿**：`linkage-design.md` 新增 D2-4 对齐章节——筛选定义产出形态、date-range 值形态、widget 词表映射、URL 同步语义，含拒绝的替代方案。
- **筛选定义产出 API**：从 `paramConfig` 生成 flux dashboard-filter 约定可消费的筛选定义（产出形态在「完整 form schema」与「参数描述符（前端轻组装）」之间裁定，判据：flux 侧零/轻转换可用且后端不越界生成 UI 细节）。
- **形态映射实现与验证**：date-range 与 widget 映射按契约落地；产出的定义经「模拟 flux 提交的筛选值」回灌 `resolveFilterValues` → `getDashboardData` 闭环验证。

## Non-Goals

- flux 侧渲染/组装代码（flux 仓库 scope）。
- 布局对齐 / 编辑器集成（D1-4，建议先行 plan `2026-08-15-1134-1`；两计划边界在 Phase 1 裁定并记录，预期结论：筛选定义仅经本计划 action 产出、不经布局导出物携带）。
- 联动/跳转的前端交互（D2-2 后端已落地）、filter_state 的前端消费（D2-3 后端已落地）。
- `paramConfig` 编辑能力（写路径由既有 CRUD/发布覆盖）。
- ORM 变更（零列变更）。

## Scope

### In Scope

- `ai-dev/design/nop-datav/linkage-design.md` 新增 D2-4 对齐契约章节。
- 筛选定义产出 action（`@BizQuery` + `@Auth` + `nop-datav.action-auth.xml` 权限点）。
- date-range / widget / URL 同步的映射裁定与实现（如裁定需要，含 `resolveFilterValues`/`parseFilterFromUrl` 的适配调整——行为变更须向后兼容或显式契约变更说明）。
- 配套错误码（英文消息）与单元测试 + 闭环 E2E 测试。

### Out Of Scope

- flux 仓库任何变更。
- 面板布局/数据绑定（D1-2 既有能力）。

## Execution Plan

### Phase 1 - 对齐契约定稿

Status: planned
Targets: `ai-dev/design/nop-datav/linkage-design.md`

- Item Types: `Decision`

- [ ] 裁定**筛选定义产出形态**：完整 flux form schema（后端生成表单结构）vs 参数描述符数组（flux 组装）——判据：flux dashboard-filter 约定（`valuesPath:'filter'` + 字段控件）消费成本最小化 + 后端不生成超出参数模型的 UI 细节；连带裁定与 D1-4 布局对齐（plan `2026-08-15-1134-1`）的边界（预期结论：筛选定义仅经本计划 action 产出、不经 D1-4 布局导出物携带——裁定记录含拒绝的替代方案）
- [ ] 裁定 **date-range 值形态转换归属**：preset 选中值为 delimited 绝对字符串（见 baseline）；裁定其与 nop-datav 扁平 `name.start`/`name.end` 的转换归属（后端产出定义时规定字段拆分/合并规则，或 `resolveFilterValues` 增设接受形态），并**钉死 delimiter 与 valueFormat 约定**（delimited 字符串的拆分符与日期格式须进契约，避免 datetime 类 valueFormat 与 `YYYY-MM-DD` 假设不符）；核实并显式记录「nop-datav 不需接受 relative 语义值」（本计划产出定义所控制的初始值为绝对形态；flux 手工配置的 relative 初始值仅 display 解析、不写回发布值，显式排除在 D2-4 消费契约外）；选定后两侧契约钉死，杜绝双形态漂移
- [ ] 裁定 **widget 词表映射**：`paramConfig.widget`（dropdown/date-picker 等）↔ flux 字段控件（select/date-range/input-\*）映射表（核对来源：flux `nop-chaos-flux:docs/components/` 各控件文档，防凭通配草率收窄）；未声明的 widget 缺省行为；无法映射 widget 显式报错或回退规则（不得静默渲染错误控件）；连带裁定 **dropdown 候选值（options）来源**（`paramConfig` 扩展 / flux 页面 schema 自供 / 数据集推导——即使裁定「定义产出不含 options」也要显式记录，杜绝产出定义 flux 侧实际不可消费）
- [ ] 裁定 **URL 同步语义**：flux 筛选状态 ↔ `parseFilterFromUrl` 扁平 key 的对应约定（date-range 在 URL 中的 key 形态与 §二 扁平 key 一致性）
- [ ] 契约章节写入 `linkage-design.md`（含每项裁定拒绝的替代方案及理由），并**同步修订受影响的既有章节**：§一中「`widget` 供前端消费，后端不解析」的表述（widget 词表落地后该行为变更）、概述/§十中对 D2-4「纯前端、不影响后端」的框架性描述

Exit Criteria:

- [ ] `linkage-design.md` 含 D2-4 对齐契约章节，覆盖上述全部裁定项（含 date-range 转换归属 + delimiter/valueFormat 钉死 + options 来源裁定 + relative 值不需支持的核实记录）
- [ ] date-range 裁定后，`paramConfig` 既有扁平 key 契约（§二）与新裁定无矛盾（如修订既有章节，显式标注修订）
- [ ] 每项裁定附拒绝的替代方案及理由
- [ ] `ai-dev/logs/` 对应日期条目已更新
- [ ] No owner-doc update required beyond上述（nop-datav 设计契约归属 `ai-dev/design/nop-datav/`）
- [ ] No new test required: 纯 Decision/文档 phase，契约的可验证性由 Phase 2/3 测试承载

### Phase 2 - 筛选定义产出 API 与形态映射

Status: planned
Targets: `nop-datav/nop-datav-service`（BizModel action + 映射实现）、`nop-datav/nop-datav-web/src/main/resources/_vfs/nop/datav/auth/nop-datav.action-auth.xml`、`nop-datav/nop-datav-service/src/test/resources/_vfs/test/datav/auth/app.action-auth.xml`

- Item Types: `Fix | Proof`

- [ ] 实现筛选定义产出 action（`@BizQuery` + `@Auth`，权限点同步登记 action-auth 源文件与测试侧 auth 资源）：读取 `paramConfig`，按契约产出 flux 可消费定义
- [ ] 实现类型/widget 映射：四类参数类型（string/number/date/date-range）+ widget 词表按契约落地；非法/未声明 widget 按契约（缺省或显式报错）；options 按契约裁定落地
- [ ] date-range 适配按契约落地（如裁定涉及 `resolveFilterValues`/`parseFilterFromUrl` 适配，保证既有扁平 key 行为向后兼容）
- [ ] 测试：四类参数各产出正确 / defaultValue/label 透传 / 空参数（无 paramConfig 看板产出空定义）/ 非法参数定义显式报错 / date-range delimited 字符串按契约转换（含 delimiter/valueFormat 边界用例）/ options 按裁定出现或显式不含

Exit Criteria:

- [ ] action 存在且经 `@Auth` 保护，权限点已在 action-auth 源文件与测试侧 auth 资源登记
- [ ] 产出定义与契约章节逐字段一致（定向断言，非仅「不抛错」）
- [ ] `resolveFilterValues`/`parseFilterFromUrl` 既有测试不回归；新增行为有新测试
- [ ] 无静默跳过：非法定义/不可映射 widget 显式失败
- [ ] 新增测试显式列出并全绿
- [ ] No owner-doc update required in this Phase（契约章节已在 Phase 1 定稿含既有章节修订；如实现与契约偏差，回写 `linkage-design.md` 属 Phase 3 终稿校对项）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 闭环 E2E 与收尾

Status: planned
Targets: `nop-datav/nop-datav-service`（E2E 测试）、`linkage-design.md`（终稿校对）

- Item Types: `Proof`

- [ ] E2E：`paramConfig`（含全部四类参数）→ 产出筛选定义 → 按定义模拟 flux 筛选值提交（含 date-range delimited 字符串契约形态）→ `resolveFilterValues` 校验/默认值 → `getDashboardData` 参数注入，断言查询携带正确筛选值
- [ ] 接线验证：`getDashboardData` 结果断言筛选值实际影响查询（如不同筛选值产出不同数据），非仅参数透传

Exit Criteria:

- [ ] **端到端验证**：参数定义 → 筛选定义 → 模拟筛选值 → 校验 → 数据查询注入完整链路单测全绿（Minimum Rules #22）
- [ ] **接线验证**：筛选值改变查询结果的断言存在且全绿（Minimum Rules #23）
- [ ] 既有测试全绿（无回归）
- [ ] `linkage-design.md` 契约与实现终态一致
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] D2-4 对齐契约定稿且与 live 实现一致（产出 API/映射/错误码可对上）
- [ ] date-range 双形态漂移已消除（单一契约形态 + 转换归属明确）
- [ ] 闭环 E2E（定义 → 值 → 校验 → 查询注入）落地并有定向测试
- [ ] 既有 `resolveFilterValues`/`parseFilterFromUrl`/`getDashboardData` 行为无未声明回归
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] `linkage-design.md` 已同步；flux 侧消费工作显式标注为外部后续（非本计划 debt）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证产出 action 被测试实际调用、映射逻辑无空实现/静默跳过
- [ ] `./mvnw compile -pl nop-datav/nop-datav-service,nop-datav/nop-datav-web -am` 通过
- [ ] `./mvnw test -pl nop-datav/nop-datav-service -am` 全绿
- [ ] checkstyle / 代码规范检查通过
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-datav --severity high` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（本计划修改 `ai-dev/design/` 与 `ai-dev/plans/` 文件）

## Deferred But Adjudicated

### flux 侧筛选栏实际渲染与对接（前端仓库工作）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: D2-4 在 nop-datav 侧的交付物是对齐契约 + 筛选定义产出 API + 闭环测试；flux 页面渲染/组装属 flux 仓库 scope。后端契约+API 成立即 D2-4 后端结果面成立。
- Successor Required: `yes`
- Successor Path: flux 仓库侧 dashboard-filter ↔ nop-datav 对接任务（外部跟踪，非本仓库 plan）

## Non-Blocking Follow-ups

- 与 plan `2026-08-15-1134-1`（D1-4）无硬依赖：本计划筛选定义产出不依赖布局对齐 API，可独立先行；执行顺序仍建议 D1-4 先（边界裁定已在 Phase 1 显式化）。

## Closure

Status Note: <<完成或关闭时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<task id / daily log link / findings 摘要>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
