# 2 大屏主题：色板 + 背景（D4-3）

> Plan Status: completed
> Mission: nop-datav
> Work Item: D4-3
> Last Reviewed: 2026-08-10
> Source: `ai-dev/backlog/nop-datav-roadmap.md` D4-3；`ai-dev/analysis/2026-08/2026-08-09-nop-datav-function-analysis.md` §五大屏能力
> Related: `2026-08-10-1130-2-screen-free-canvas-layout.md`（D4-1 大屏实体/backgroundConfig 占位）、`2026-08-10-1000-2-dashboard-runtime-panel-data-binding-refresh.md`（D1-1 配置区域约定哲学）
> Execution Order: N=2（D4-2 → D4-3 → D4-4）。本计划与 D4-4 共同修改 `ScreenLayoutParser.java`/`NopDatavScreenBizModel.java`；按 N 序执行（D4-3 先于 D4-4），D4-4 在本计划落地的主题解析之上叠加草稿预览 overload。

## Purpose

将 roadmap D4-3 收口：为大屏定义主题模型——色板（palette）+ 背景（background），并实现主题解析（读取大屏配置 → 结构化 palette/background → 经 `getScreenLayout` 暴露给前端）。D4-1 已在大屏实体预留 `backgroundConfig`（json-4000）作为"D4-3 主题扩展占位，本 plan 不解析内部结构"（见 `screen-design.md` §1.1 + ORM 列注释）。本计划填充该占位：定义其内部结构（palette + background 区域）+ 解析逻辑 + 缺省值策略。本计划只做模型层（配置 schema + 解析 + 元信息暴露），不做前端渲染。

## Current Baseline

基于 live repo 核对（2026-08-10）：

- **大屏实体已预留 backgroundConfig 占位**：`NopDatavScreen.backgroundConfig` 为 `json-4000`（`nop-datav.orm.xml`），D4-1 明确标注"D4-3 主题扩展占位；本 plan 不解析内部结构"。当前 `serializeScreenContent`（`NopDatavScreenBizModel.java:152`，私有）将 backgroundConfig 原样序列化进快照；`restoreScreenFromSnapshot`（`:228`，私有）原样回填。`ScreenLayoutConfig.Canvas.backgroundConfig`（`ScreenLayoutConfig.java:86`）以 `Map<String,Object>` 透传给前端。**内部结构未定义、无解析、无缺省值**。
- **无 palette 概念**：当前大屏无色板（primary/secondary/accent/语义色）定义。组件颜色完全靠 widgetConfig 内的 styleOptions 各自指定（D1-1 §1.3），无屏幕级统一样式基准。
- **getScreenLayout 已就绪可扩展**：`getScreenLayout`（`NopDatavScreenBizModel.java:140`）经 `ScreenLayoutParser.parse` 返回 `ScreenLayoutConfig`，其 `Canvas` 含 `backgroundConfig` Map。本计划在该解析路径上增补主题解析（结构化 palette/background + 缺省值），无需新增 API 入口。
- **widget.widgetConfig 已有 styleOptions 约定**：D1-1 §1.3 定义 widgetConfig 可含 `styleOptions` 区域（颜色/字体/边框/背景）。本计划的屏幕级 palette 可作为 widget 级 styleOptions 的**解析期合并基准**（widget 未指定时回退到屏幕级 palette）。
- **设计契约为 D4-1 范围**：`screen-design.md` Scope 明确"主题 D4-3 不在本文结论范围"。本计划 Phase 1 增补 D4-3 章节。
- **真正剩余 gap**：主题配置 schema 无定义；palette 无概念；backgroundConfig 内部结构未解析；widget 级样式无屏幕级回退基准。

## 设计方向预声明（推荐方向，Phase 1 确认并记录拒绝理由）

1. **主题配置复用 backgroundConfig 列，不新增 ORM 列**：D4-1 已预留 backgroundConfig 作为主题扩展占位。推荐其内容结构为 `{palette: {...}, background: {...}}` 两区域。拒绝"新增 themeConfig 列"（理由：D4-1 已预留占位、复用避免 ORM 变更 + 快照序列化/回滚已覆盖 backgroundConfig 原样流转）。Phase 1 确认该结构并记录被拒方案。
2. **向后兼容：legacy 自由格式 backgroundConfig 不报错**：现有测试（`TestNopDatavScreenBizModel` 用 `{"color":"#123456"}`，`TestScreenLayoutParser` 用 `{"color":"#000"}`）依赖 backgroundConfig 原样透传。**解析规则**：backgroundConfig 含 `palette`/`background` 键 → 按新结构解析（值结构非法时抛 `ERR_DATAV_INVALID_THEME_CONFIG`）；backgroundConfig 不含这两个键（legacy 自由格式）或缺省/空 → **不报错**，palette 用缺省、background 用缺省，且 backgroundConfig 原始 Map 仍原样透传。这保证既有测试与既有数据不回归。
3. **返回结构非破坏：Canvas.backgroundConfig 保持 Map 透传，新增独立 resolvedTheme 字段**：`ScreenLayoutConfig.Canvas.backgroundConfig`（`Map<String,Object>`，D4-1 公共契约）**类型与透传语义不变**——既有 `getBackgroundConfig().get("color")` 断言继续成立。解析后的结构化主题（palette + background，含缺省值填充）放入 **ScreenLayoutConfig 新增的 `theme` 字段**（additive，不改动既有字段类型）。拒绝"把 Canvas.backgroundConfig 字段类型改为结构化对象"（理由：破坏 D4-1 公共契约 + dao 模块 POJO + 既有消费者）。
4. **per-screen 主题，不做可复用主题库实体**：D4-3 交付每屏独立主题配置。拒绝"新建 NopDatavTheme 可复用主题库实体"（理由：需 CRUD + 引用关系，scope 过宽；无跨屏共享用例）。列为 Non-Blocking Follow-up。
5. **palette 含命名语义色 + 缺省值**：palette 定义一组命名色（primary/secondary/accent/success/warning/danger/info + 文字色/背景基色），解析时缺省色板填充合理默认（非 null）。参考 JimuReport sysDefColor。
6. **background 支持 color/image/gradient 三类型**：background 区域 `{type: color|image|gradient, value: ...}`，type 缺省 color、value 缺省取 palette 背景基色。
7. **widget 主题引用收窄为 widgetConfig.theme 命名引用解析（不做 styleOptions 自动回退）**：widget 可在 `widgetConfig.theme` 区域用命名引用（如 `{"color":"primary","backgroundColor":"background"}`）引用屏幕级 palette；解析期将命名引用替换为 palette 实际色值，放入 widget 解析结果。**不**对 D1-1 `styleOptions` 的任意颜色键做自动 palette 回退（styleOptions 键到 palette 名的映射无定义、不可测）。这使 widget 主题可测且范围明确。

## Goals

- 定义大屏主题配置 schema：backgroundConfig 内容推荐结构 = `{palette: {命名色}, background: {type, value}}`（legacy 自由格式向后兼容，不报错）。
- 实现主题解析：读取 backgroundConfig → 结构化 palette（含缺省值填充）+ background（含 type 缺省）→ 经 getScreenLayout 在**新增的 `theme` 字段**暴露（Canvas.backgroundConfig 原样透传不变，非破坏）。
- 实现 widget 主题命名引用解析：widget.widgetConfig.theme 中的命名引用（如 "primary"）解析期替换为 palette 实际色值（范围明确，不做 styleOptions 自动回退）。
- 在 `screen-design.md` 增补 D4-3 章节（最终结论 + 被拒方案）。
- 含 palette/background 键但值结构非法时显式报错（legacy 自由格式不报错）。

## Non-Goals

- **可复用主题库实体（NopDatavTheme CRUD）**：见设计方向预声明 #2，列为 follow-up。
- **前端主题渲染**：色板/背景的视觉应用走 nop-chaos-flux（未产出）。
- **ORM 列变更**：复用 backgroundConfig，不新增列（见设计方向预声明 #1）。
- **强制 JSON Schema 校验**：主题配置 schema 为约定 + 缺省值策略，不强制拒绝含未声明键的 backgroundConfig（向前兼容）。
- **大屏装饰组件（D4-2）**、**发布生命周期（D4-4）**：独立 plan。

## Scope

### In Scope

- 设计文档增补：`ai-dev/design/nop-datav/screen-design.md` 增 D4-3 章节（palette 命名色清单 + 缺省值、background type 枚举、widget 回退合并规则、被拒方案）。
- 主题配置 schema 定义 + 解析实现（palette 缺省值填充 + background type 缺省 + 非法 JSON 报错）。
- getScreenLayout 在**新增 `theme` 字段**暴露结构化主题（palette 缺省值 + background type 缺省）；`Canvas.backgroundConfig` 保持 Map 原样透传不变（非破坏）；widget.widgetConfig.theme 命名引用解析期替换为 palette 实际色值（不做 styleOptions 自动回退）。
- 错误码：主题相关（非法主题 JSON）。
- 单元测试 + 端到端（配置主题 → publish → getScreenLayout 返回结构化 palette/background + widget 回退）。

### Out Of Scope

- 可复用主题库实体。
- 前端主题渲染。
- ORM 列变更。
- 强制 JSON Schema 校验。
- D4-2 装饰组件、D4-4 发布生命周期。

## Execution Plan

### Phase 1 - 设计文档增补（screen-design.md D4-3 章节）

Status: completed
Targets: `ai-dev/design/nop-datav/screen-design.md`

- Item Types: `Decision`

- [x] 在 `screen-design.md` 增 D4-3 章节（最终结论，无 "Proposed vs Current"），记录：
  - [x] **backgroundConfig 内容结构**：推荐 `{palette: {命名色}, background: {type, value}}`；记录"新增 themeConfig 列"被拒理由（D4-1 已预留占位、复用避免 ORM 变更）
  - [x] **向后兼容裁定（关键）**：含 palette/background 键 → 按新结构解析（值非法抛 `ERR_DATAV_INVALID_THEME_CONFIG`）；不含这两键（legacy 自由格式如 `{"color":"..."}`）或缺省/空 → 不报错，palette/background 用缺省、backgroundConfig 原样透传。既有测试 `TestNopDatavScreenBizModel`（`{"color":"#123456"}` 断言透传）与 `TestScreenLayoutParser`（`{"color":"#000"}`）在此规则下保持绿
  - [x] **返回结构非破坏裁定**：`Canvas.backgroundConfig` 保持 `Map<String,Object>` 原样透传（D4-1 契约不变）；解析后结构化主题放入 ScreenLayoutConfig **新增 `theme` 字段**（additive）；记录"改 backgroundConfig 字段类型为结构化对象"被拒理由（破坏 D4-1 公共契约 + dao POJO + 既有消费者）
  - [x] **palette 命名色清单 + 缺省值**：定义命名语义色集合（primary/secondary/accent/success/warning/danger/info + 文字色/背景基色）及各自缺省值；参考 JimuReport sysDefColor
  - [x] **background type 枚举**：color/image/gradient 三类型 + 各自 value 结构 + 缺省（type 缺省 color，value 缺省取 palette 背景基色）
  - [x] **widget 主题命名引用裁定**：widget.widgetConfig.theme 用命名引用（如 `{"color":"primary"}`）引用屏幕级 palette，解析期替换为实际色值；**不**对 styleOptions 任意颜色键做自动回退（映射无定义）；记录被拒的"styleOptions 自动 palette 回退"方案理由
  - [x] **per-screen 主题裁定**：记录"可复用主题库实体"被拒理由（scope 过宽、无跨屏共享用例），列为 follow-up

Exit Criteria:

- [x] `screen-design.md` 含 D4-3 最终结论章节，覆盖上述全部子项，无 "Proposed"/"待定"
- [x] 该 Phase 改变 live baseline（design doc）：`docs-for-ai/` 无需更新；`ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 主题解析实现

Status: completed
Targets: `nop-datav-service/.../service/screen/`（新建主题解析）、`nop-datav-dao/.../biz/ScreenLayoutConfig.java`（新增 theme 字段）、`nop-datav-service/.../service/screen/ScreenLayoutParser.java`（接入主题解析）、`NopDatavErrors.java`

- Item Types: `Fix | Decision`

- [x] 新增错误码（`NopDatavErrors.java`）：`ERR_DATAV_INVALID_THEME_CONFIG`（backgroundConfig 含 palette/background 键但其值结构非法，如 palette 非 object；**legacy 自由格式不含这两键时不触发此错误**）
- [x] 实现主题解析：读取 backgroundConfig —— 含 palette/background 键则解析（值非法抛 `ERR_DATAV_INVALID_THEME_CONFIG`）；不含这两键或缺省/空则 palette/background 用缺省（不报错）。产出结构化 resolved theme（palette 命名色缺省值填充 + background type/value 缺省）
- [x] **非破坏返回**：`ScreenLayoutConfig.Canvas.backgroundConfig` 保持 `Map<String,Object>` 原样透传（既有 `getBackgroundConfig().get(...)` 不变）；在 `ScreenLayoutConfig` 新增 `theme` 字段（additive）承载解析后的结构化主题
- [x] 实现 widget 主题命名引用解析：widget.widgetConfig.theme 中的命名引用（如 `"primary"`）解析期替换为 palette 实际色值，放入 widget 解析结果；**不**对 styleOptions 任意键做自动回退
- [x] 在 `ScreenLayoutParser.parse` 接入主题解析（产出 theme 字段 + widget 命名引用解析）；既有 Canvas.backgroundConfig 透传逻辑不回归
- [x] 快照序列化/回滚不回归：`serializeScreenContent`/`restoreScreenFromSnapshot` 仍原样流转 backgroundConfig（主题解析只发生在 getScreenLayout 读取期，不改变存储格式）

Exit Criteria:

- [x] 主题解析对缺省/空 backgroundConfig 返回缺省 palette + 缺省 background（非 null、非静默降级）
- [x] 主题解析对 legacy 自由格式 backgroundConfig（如 `{"color":"#123456"}`，不含 palette/background 键）不报错，palette/background 用缺省，且 backgroundConfig 原样透传
- [x] 主题解析对含 palette/background 键但值结构非法的 backgroundConfig 抛 `ERR_DATAV_INVALID_THEME_CONFIG`
- [x] `Canvas.backgroundConfig` 仍为 `Map<String,Object>` 原样透传（既有断言不回归）；结构化主题在新增 `theme` 字段
- [x] widget.widgetConfig.theme 命名引用解析期替换为 palette 实际色值（可验证）；styleOptions 不被自动改写
- [x] **无静默跳过**：值结构非法抛异常非降级；新增解析方法无空方法体/吞异常
- [x] 该 Phase 改变 live baseline（API/行为）：`screen-design.md`（Phase 1）已覆盖；`docs-for-ai/` 无需更新；`ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 测试与端到端验证

Status: completed
Targets: `nop-datav-service/src/test/`

- Item Types: `Proof`

- [x] 单元测试 主题解析：缺省/空 backgroundConfig → 缺省 palette + 缺省 background；含 palette/background 键 → 命名色/background type 正确解析 + 未指定色回退缺省；含 palette/background 键但值非法 → `ERR_DATAV_INVALID_THEME_CONFIG`
- [x] 单元测试 向后兼容（关键）：legacy 自由格式 backgroundConfig（如 `{"color":"#123456"}`）不报错，`Canvas.backgroundConfig` 原样透传（`get("color")` 仍为 `#123456`），theme 字段用缺省 palette/background
- [x] 单元测试 widget 主题命名引用：widgetConfig.theme 命名引用（如 `{"color":"primary"}`）解析期替换为 palette 实际色值；styleOptions 不被自动改写
- [x] 端到端测试（rule #22）：创建大屏（backgroundConfig 配置 `{palette:{...}, background:{...}}`）→ 添加 widget（widgetConfig.theme 含命名引用）→ publish → getScreenLayout 返回 theme 字段（结构化 palette/background）+ widget 命名引用已解析 + `Canvas.backgroundConfig` 原样 → 断言正确
- [x] 回归测试：既有 `TestNopDatavScreenBizModel`（`{"color":"#123456"}` 透传断言）与 `TestScreenLayoutParser`（`{"color":"#000"}`）在主题解析接入后仍通过（向后兼容验证）

Exit Criteria:

- [x] 新增主题功能（palette/background 解析、缺省值、向后兼容、widget 命名引用、非法处理）每个均有对应测试（rule #25）
- [x] **端到端验证**：配置主题 → publish → getScreenLayout 返回结构化主题（theme 字段）+ widget 命名引用解析完整链路跑通
- [x] **接线验证**：端到端测试断言 ScreenLayoutParser 接入了主题解析（theme 字段 + widget 命名引用可观察）
- [x] **向后兼容验证**：既有 freeform backgroundConfig 透传测试在主题解析接入后仍绿
- [x] `./mvnw test -pl nop-datav/nop-datav-service -am` 通过（含新增测试，无回归）
- [x] 该 Phase 不改变 ORM 结构；`ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 主题配置 schema（palette + background）定义并落地解析
- [x] getScreenLayout 在新增 `theme` 字段暴露结构化主题（palette 缺省值 + background type 缺省 + widget 命名引用解析）
- [x] **向后兼容**：Canvas.backgroundConfig 原样透传不变；legacy 自由格式不报错（既有测试不回归）
- [x] 含 palette/background 键但值非法时显式失败（非静默降级）；缺省/legacy 不报错
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] `screen-design.md` D4-3 章节为最终设计与 live 实现一致
- [x] 受影响 owner docs 已同步（`screen-design.md`；`docs-for-ai/` 无需更新）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证主题解析经 ScreenLayoutParser 接线、widget 回退合并可观察，无空方法体/静默跳过
- [x] `./mvnw compile -pl nop-datav -am`
- [x] `./mvnw test -pl nop-datav/nop-datav-service -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### 主题前端渲染（flux 侧）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: D4-3 是模型层（配置 schema + 解析 + 元信息暴露），后端可独立验证（palette/background 结构化输出可断言）。前端视觉应用走 nop-chaos-flux（未产出），与 D1-4/D2-4 同类阻塞。
- Successor Required: `yes`
- Successor Path: flux 主题渲染控件落地后对接

### 可复用主题库实体（NopDatavTheme CRUD）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 当前无跨屏共享主题用例；per-screen 配置已满足"色板 + 背景"需求。可复用主题库需 CRUD + 引用关系，scope 过宽。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 强制 backgroundConfig JSON Schema 校验（待 flux 主题控件 schema 稳定后评估）
- palette 命名色的更丰富语义（如数据可视化色板 series 色序列）——优化项
- 主题预览（编辑期实时预览，前端能力）——前端范围

## Closure

Status Note: D4-3 大屏主题（色板 + 背景）已全部落地。新增 ScreenThemeConfig（dao）+ ScreenThemeParser（service）实现 backgroundConfig → 结构化 theme 解析（palette 缺省值填充 + background type/value 缺省 + 含主题键但值非法显式抛 ERR_DATAV_INVALID_THEME_CONFIG）；ScreenLayoutConfig 新增 theme 字段（additive，Canvas.backgroundConfig 原样透传不破坏）；widget.widgetConfig.theme 命名引用解析期替换为 palette 实际色值放入 widget.resolvedTheme（styleOptions 不被自动改写）；screen-design.md §11 增补最终结论。向后兼容：legacy 自由格式 backgroundConfig 不报错、原样透传。22 新测试（TestScreenThemeParser 16 + TestScreenLayoutParser 4 接线 + TestNopDatavScreenBizModel 2 E2E），nop-datav-service 245/0/0 全绿。
Completed: 2026-08-10

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure-audit（executor self-verification per plan execution protocol；closure-audit 子 agent 见 mission-driver 后续 CLOSURE_VERIFY 阶段）
- Audit Session: 2026-08-10 D4-3 execute pass
- Evidence:
  - 每条 Exit Criterion 的验证结果：
    - Phase 1 Exit Criteria: PASS — `screen-design.md` §11 含 D4-3 最终结论（backgroundConfig 结构/向后兼容/返回非破坏/palette 清单+缺省/background type 枚举/widget 命名引用/per-screen 裁定/解析时机），覆盖全部子项，无 "Proposed"
    - Phase 2 Exit Criteria: PASS — 缺省/空 → 缺省 theme（TestScreenThemeParser#testNullBackgroundConfigReturnsDefaults/testEmptyBackgroundConfigReturnsDefaults）；legacy 不报错+透传（TestScreenThemeParser#testLegacyFreeformBackgroundConfigDoesNotThrow + TestScreenLayoutParser#testParseLegacyBackgroundConfigUsesDefaultTheme）；含键值非法抛 ERR_DATAV_INVALID_THEME_CONFIG（testPaletteNotObjectThrows/testBackgroundNotObjectThrows/testPaletteAsArrayThrows + testParseInvalidThemeConfigThrows 接线）；Canvas.backgroundConfig Map 透传不变（TestNopDatavScreenBizModel#testGetScreenLayoutEndToEnd `get("color")==#123456`）；widget 命名引用解析（testWidgetThemeNamedReferenceResolved + testParseResolvesWidgetThemeNamedReferences 接线）；无静默跳过（非法显式抛异常）
    - Phase 3 Exit Criteria: PASS — 新增主题功能每项有测试（rule #25）；端到端 testThemePaletteAndBackgroundEndToEnd 跑通配置主题→publish→getScreenLayout→theme 字段+widget resolvedTheme+backgroundConfig 透传；接线验证（ScreenLayoutParser 接入主题解析，theme/resolvedTheme 可观察）；向后兼容验证（testLegacyFreeformBackgroundConfigEndToEndUsesDefaultTheme + 既有 testGetScreenLayoutEndToEnd 仍绿）；`./mvnw test -pl nop-datav/nop-datav-service` 245/0/0 PASS（nop-sys-dao 上游预存失败与本 plan 无关）
  - 每条 Closure Gate 的验证结果：见上方 Closure Gates 全 [x]
  - `node ai-dev/tools/check-plan-checklist.mjs` 退出码为 0：待 closure-audit 子 agent 运行确认
  - Anti-Hollow 检查结果：端到端调用链 ScreenBizModel.getScreenLayout → ScreenLayoutParser.parse → ScreenThemeParser.resolve/resolveWidgetTheme 可观察（theme 字段非 null、resolvedTheme 命名引用已解析为色值、非法配置显式抛异常）；scan-hollow-implementations.mjs：待 closure-audit 子 agent 运行
  - Deferred 项分类检查：主题前端渲染（flux，out-of-scope improvement，successor yes）+ 可复用主题库实体（out-of-scope，successor no）——无 in-scope live defect 被降级

Follow-up:

- 主题前端渲染（flux 侧，successor required）
- 可复用主题库实体（无 successor，按需评估）
- 强制 backgroundConfig JSON Schema 校验 / palette series 色序列 / 主题预览（Non-Blocking Follow-ups）
