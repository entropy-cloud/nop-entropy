# 2360 WI2 nop-canvas2d 文本与度量子系统

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: ai-dev/backlog/echarts-server-render-roadmap.md WI2（M1）；依赖 plan 2359（WI1 已完成，commit 84f9f918d1）
> Related: WI3 在本模块续建特效子系统；WI4 引擎经 shim 以 platformApi.measureText 消费本模块

## Purpose

在 nop-canvas2d 上续建 roadmap WI2 范围：CSS font 串解析、FontMapper 泛族映射（中文必覆盖、缺字体显式报错）、textAlign/textBaseline 语义换算、measureText（含 zrender 宽字符度量规则的支撑与缓存）、fillText/strokeText、truncate/ellipsis 截断与富文本分行，全部配 focused tests。

## Current Baseline

以下事实已于 2026-10-10 在 live repo（分支 echarts-server-render @ 84f9f918d1）核验：

- nop-canvas2d 已存在（WI1）：ICanvas2DContext 含状态机/路径/绘制接口与 Java2DCanvas 实现；plan 2359 明确「WI2 将追加文本与度量……接口追加式生长，不封印」——本计划的方法追加是非破坏演进。
- zrender 宽字符度量规则实证：dist/echarts.js:5843 `stWideCharWidth: platformApi.measureText('国', font).width`——行高与宽字符度量经 platformApi.measureText 流动，本模块的职责是提供**准确的 CJK measureText** 与缓存；zrender 侧的行高计算自会随之正确。
- zrender 生成的 font 串形态：DEFAULT_FONT = "12px sans-serif"；option 的 fontStyle/fontWeight/fontSize/fontFamily 拼接为标准 canvas 简写——解析器只需覆盖 px 单位简写（其他单位显式报错，不静默）。
- 字体环境（WI0 初验）：mac 逻辑字体 SansSerif 经 AWT fallback 可渲染中文（1027 非白像素实证），物理 PingFang SC/Hiragino Sans GB/STHeiti/Songti SC 可用；Noto Sans CJK SC/Source Han Sans SC/Microsoft YaHei 不可用（解析回 Dialog）。CI（Linux/JDK21）字体环境未知——FontMapper 缺字体显式报错是 roadmap Cross-Cutting #5 硬约束；CI 探查已提前至本计划 Phase 1 第一步（结论记录于当日日志），并配 assumeTrue 环境门控策略。
- WI1 D2/D4 裁定体系有效：非法输入抛 NopCanvas2DException（English 消息）、像素断言纪律（布尔覆盖、采样点距几何边界 ≥2px、hints 固定）。WI1 已设 TEXT_ANTIALIAS 吗——未设（WI1 无文本）；本计划将 TEXT_ANTIALIAS_ON 补入固定 hints（D4c 的追加，属文本语义的一部分）。
- WI0 spike 的文本实现（SpikeCanvasBackend.setFont/anchorText/measureText）为可参照原型，但 spike 的 font 解析仅支持部分形态且无 FontMapper/缓存/截断——按本计划裁定从零实现文本子系统，spike 仅作行为 sanity 对照（font 解析的「可用物理字体检查」语义与 spike 一致）。
- 根 pom maven.compiler.release=17（record 可用）。

## Goals

- CSS font 串解析：`[style] [variant] [weight] size[/line-height] family-list`——style（normal/italic/oblique）、weight（normal/bold/数字 100-900）、size（必须 px）、family 列表（引号/非引号、泛族 sans-serif/serif/monospace/cursive/fantasy/system-ui）；不支持的 token 显式报错。
- CanvasFontMapper：泛族 → 物理字体可配置映射（默认 sans-serif→SansSerif、serif→Serif、monospace→Monospaced——**JDK 逻辑字体名是 Font.MONOSPACED（Monospaced），不是 Monospace**（探针实证 new Font("Monospace") 解析回 Dialog）；可注入自定义映射如 sans-serif→PingFang SC）；五个逻辑字体名（Dialog/DialogInput/Monospaced/SansSerif/Serif，按 Font 常量）**直接放行、绕过 getAvailableFontFamilyNames 精确匹配**（该名单对逻辑字体跨平台不保证完整）；物理字体按可用性名单精确匹配（default + Locale.ENGLISH 双名单匹配，防本地化名漂移）——**可用性检查必须先于 canDisplay**（探针实证不可用字体名经 Dialog fallback 也能通过 canDisplay，顺序颠倒会静默接受豆腐块字体）；通过可用性后校验「A+中」双字形 canDisplay；全部家族不可用 → NopCanvas2DException（**缺字体显式报错，禁静默 fallback**，Cross-Cutting #5）。
- 文本状态：ICanvas2DContext 追加 setFont(String)/setTextAlign/setTextBaseline；textAlign（start/end/left/right/center）、textBaseline（alphabetic/top/hanging/middle/ideographic/bottom）语义换算为 Java2D 锚点；start≡left、end≡right（无 direction 语义，记录为裁定）；save/restore 快照扩展覆盖 font/align/baseline（WI1 状态机正确性延伸）。
- measureText：返回 ITextMetrics（width；ascent/descent 供模块内布局用）；**(text, font) 维度 LRU 缓存**（默认 4096 条，可配）；getLineHeight(font) 助手返回 width('国')（zrender 宽字符/行高规则的直接支撑，dist:5843 实证）。
- fillText/strokeText：锚点换算（textAlign 水平、textBaseline 垂直，含 metrics 的 ascent/descent 语义映射）；strokeText 为描边轮廓（TextLayout.getOutline 路径）。
- TextLayoutUtils：truncateText（maxWidth 内截断 + "..." 后缀，省略号宽度计入 maxWidth、省略号自身超宽则返回空——语义对齐 zrender truncateText2，dist:9632-9703）、splitLines（\n 分行 + maxWidth 自动换行：非字母即断点→CJK 逐字、英文按词边界保持、超长词硬断——语义对齐 zrender wrapText，dist:10033+）。**消费者裁定（M3）**：zrender JS 自带 truncate/wrap（只消费 platformApi.measureText），WI4 引擎链路**不会**调用这两个 Java 工具——保留它们是 roadmap WI2 明确 scope（硬边界），定位为「Java 侧基础设施，消费者=后续 ChartModel 适配层的 Java 侧文本处理」，无引擎消费者是已裁定事实而非缺线。
- 单测：font 解析表、FontMapper 映射与缺字体报错、measureText 数值与缓存、textAlign/textBaseline 像素落点、truncate/ellipsis、分行。

## Non-Goals

- 渐变/图案/阴影/图像/getImageData（WI3）。
- GraalJS shim 接线（WI4——platformApi.measureText/setFont 由 shim 桥接，本模块只提供 Java API）。
- golden 基线（WI5）。
- 富文本 rich text 的完整 echarts 语义（rich style 的 per-segment 样式在 zrender JS 侧驱动，本模块只提供分行/截断/度量原语）。
- owner doc（WI10 统一收口）。
- 字形渲染的跨平台像素一致（同一环境内确定性即可；跨环境差异是 WI5 golden 的容差问题）。

## Adjudications（延续 WI1 D 编号体系）

- **D5 font 解析严格性**：仅接受 canvas 简写且 size 必须 px（em/rem 等单位显式报错）；weight 接受 normal/bold/**bolder/lighter**（bolder 映射 BOLD、lighter 映射 PLAIN，对齐 spike 与 echarts 文档合法值）及数字 100-900（≥600 → BOLD）；font-variant 仅接受 normal（small-caps 显式报错——zrender 不产出）；line-height 分数语法 `16px/1.5` 接受并解析（模块内 lineHeight 字段），zrender 当前不产出但浏览器允许；family 列表必须非空；解析失败/不可解析 token → NopCanvas2DException（English 消息含原始串）。**已知边界**：rich 文本可能直传任意用户串（dist:42313）与 em/rem fontSize 透传（dist:12884）——setFont 抛错在 WI4 shim 桥接层按浏览器「非法赋值忽略」语义降级（保留原 font 状态），该契约归 WI4 plan 承载；本模块只保证快速失败与 English 消息。
- **D6 字体可用性**：五个逻辑字体名（Dialog/DialogInput/Monospaced/SansSerif/Serif，按 Font 常量）直接放行、绕过可用性名单精确匹配；物理字体按 getAvailableFontFamilyNames 的 default + Locale.ENGLISH 双名单精确匹配——**可用性检查必须先于 canDisplay**（探针实证不可用字体名经 Dialog fallback 也能通过 canDisplay，顺序颠倒会静默接受豆腐块字体）；通过可用性后校验「A+中」双字形 canDisplay（防单西文字体静默接受）；泛族经映射表解析（默认 sans-serif→SansSerif、serif→Serif、monospace→Monospaced——JDK 逻辑字体名是 Monospaced 非 Monospace）；全部家族不可用 → NopCanvas2DException。物理字体映射由配置注入（WI4 接 config）。
- **D7 缓存语义**：缓存 key = **原始 font 串恒等**（不做任何规范化——解析器确定性由 CssFontParser 保证，lossy 规范化会造成不同解析结果碰撞）；LRU 上限默认 4096（可配）；缓存命中必须与直算结果一致（同字体环境内确定性）；字体环境变化不失效缓存——记录为已知限制（服务端环境字体集静态）。setFont 的串→Font 解析同样走缓存（快路径，zrender 每 brushText 都 set ctx.font）。
- **D8 对齐语义**：textAlign start≡left、end≡right（无 direction 支持，zrender 不产出 rtl）；textBaseline：top/hanging→同锚（顶部），middle→(ascent-descent)/2，alphabetic→0，bottom/ideographic→-descent 语义；fillText 的 x/y 为锚点（换算后为 drawString 基线点）。**初始文本状态**：10px sans-serif / textAlign=start / textBaseline=alphabetic（canvas 规范默认，构造即有效——未 setFont 直接 fillText 不抛错）；null 文本按浏览器语义强转 "null" 字符串，空串宽度 0 不绘制；measureText/fillText 不受「当前路径」影响且不污染路径状态。**strokeText 的 FontRenderContext 取 g.getFontRenderContext()**（与渲染 hints 一致，outline 形状与 fillText 对齐）。**strokeFirst 裁定**（roadmap WI2 措辞 delta）：stroke/fill 先后顺序由 zrender JS 侧 brush 决定，shim 与本模块无感知，无需 strokeFirst API。**undefined 对齐值过滤**是 WI4 桥接职责（dist:27520 zrender 可能赋 undefined），记入 follow-ups。

## Scope

### In Scope

- nop-format/nop-canvas2d：CssFontParser、CanvasFontMapper、ITextMetrics、TextMeasurer（缓存 + getLineHeight）、TextLayoutUtils；ICanvas2DContext/Java2DCanvas 追加文本方法与状态快照扩展。
- ai-dev/plans/、ai-dev/logs/。

### Out Of Scope

- nop-chart-echarts spike 代码改动。
- docs-for-ai/（WI10）。
- 配置系统集成（WI4 经 shim/config 接线 FontMapper）。

## Execution Plan

### Phase 1 - 字体解析与度量基础设施

Status: completed
Targets: nop-format/nop-canvas2d

- Item Types: `Feature`

- [x] **CI 字体环境探查（开工第一步，从 WI5 提前）**：核对 .github/workflows runner 的中文字体可用性（ubuntu-latest 镜像 fonts-noto-cjk 装配情况，CI probe 或 runner 镜像文档）；结论记录于当日日志；结论决定 CI 门控策略——若 CI 无 CJK 字体，「A+中」校验与中文相关断言以 assumeTrue(canDisplay('中')) 环境门控，逻辑覆盖由自定义 mapper 测试与自造不可用家族测试承载（「缺字体报错」测试不依赖环境字体名单）
- [x] CssFontParser：canvas 简写解析（D5）→ CssFont record（style/weight/sizePx/lineHeight/families）；非法输入全部显式抛错（含 size 非 px、未知 token、空 family）
- [x] CanvasFontMapper：泛族映射表（默认逻辑字体三元组，可注入覆盖）+ 物理字体精确匹配 + 「A+中」双字形校验（D6）；全不可用抛 NopCanvas2DException
- [x] Java2DCanvas 接线：setFont(String)（解析 + 映射 + 存储 Font 与 lineHeight）、textAlign/textBaseline 存储与枚举校验（D8）、save/restore 快照扩展（font/align/baseline 入栈）、固定 hints 补 TEXT_ANTIALIAS_ON
- [x] TextMeasurer：measureText(text, font)（FontMetrics.stringWidth）+ ITextMetrics record（width/ascent/descent）+ (text,font) LRU 缓存（D7，可配上限）+ getLineHeight(font)=width('国')
- [x] 单测：font 解析表（形态×预期字段；非法×抛错）、FontMapper（泛族解析/物理匹配/中文校验/缺字体报错/自定义映射注入）、measureText 数值与 FontMetrics 一致、缓存命中一致性、getLineHeight 与 width('国') 相等、setFont/save/restore 行为恢复

Exit Criteria:

- [x] `./mvnw test -pl nop-format/nop-canvas2d -am` 全绿（WI1 的 49 测试不回归）
- [x] **无静默跳过**：不可解析 font 串/缺字体/非法对齐枚举全部抛 NopCanvas2DException 且有测试
- [x] 缺字体显式报错有直接测试（模拟不可用家族列表 → 异常，Cross-Cutting #5）
- [x] No owner-doc update required（WI10 统一收口）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 文本渲染与布局工具

Status: completed
Targets: nop-format/nop-canvas2d

- Item Types: `Feature` + `Proof`

- [x] fillText/strokeText：textAlign 水平锚点换算（left/center/right）+ textBaseline 垂直锚点换算（D8 五类）+ strokeText 轮廓描边（TextLayout.getOutline）；measureText 不受 align/baseline 影响
- [x] TextLayoutUtils：truncateText（超出 maxWidth 裁剪 + "..."，宽度按 TextMeasurer 计）、splitLines（\n 分行 + maxWidth 自动换行，长词/CJK 逐字断行语义）
- [x] 像素/语义单测（D4 纪律）：textAlign 三态落点（left/center/right 相对锚点的像素反差）、textBaseline 五类锚点（top/middle/alphabetic/bottom 两两反差采样）、strokeText 描边轮廓可见、fillText 后路径状态不受污染、truncate 短文原样/长文省略号、\n 分行与 CJK 逐字换行
- [x] spike 锚点换算结论固化为字面期望值：SpikeCanvasBackend.anchorText 的五类 baseline/三态 align 换算已被审查核实与本计划 D8 逐项一致，测试直接以 D8 换算的固定坐标写期望（不跨模块引用 spike 代码——spike 模块不依赖本模块，跨模块运行时对照不可达，已裁定弃用）

Exit Criteria:

- [x] `./mvnw test -pl nop-format/nop-canvas2d -am` 全绿（Phase 1 不回归）
- [x] 像素断言遵守 D4（布尔覆盖、≥2px 边距、无整图计数）并标注所钉语义
- [x] **接线验证（机械代理判据）**：getLineHeight(font) 与 measureText('国', font).width 相等有直接断言（zrender dist:5843 规则锚定）；真实消费者接线归 WI4
- [x] No owner-doc update required（WI10）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] WI2 范围 API 全部落地且有 focused tests（font 解析/FontMapper/度量缓存/对齐锚点/fillText/strokeText/truncate/分行）
- [x] 无空方法体/占位实现
- [x] 缺字体显式报错与中文字形校验有直接测试（Cross-Cutting #5）
- [x] 像素断言遵守 D4 纪律
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] No owner-doc update required（WI10 统一收口）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：fillText/strokeText 真实写像素；measureText 缓存真实生效（命中一致性测试）
- [x] `./mvnw test -pl nop-format/nop-canvas2d -am` 通过
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-format/nop-canvas2d --severity high` 退出码 0
- [x] checkstyle / 代码规范检查
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

（暂无）

## Non-Blocking Follow-ups

- FontMapper 的平台配置接线（nop-config/system property）归 WI4 引擎 plan。
- 缓存的字体环境失效策略（当前记录为已知限制，见 D7）。
- textAlign/textBaseline 的 undefined 值过滤归 WI4 桥接层（dist:27520 zrender 可能对 ctx 赋 undefined）。
- CI 无 CJK 字体时（Phase 1 探查结论），WI5 golden 前需在 CI 安装 fonts-noto-cjk（CI 工作流文件改动，届时显式扩 scope）。

## Closure

Status Note: WI2 文本子系统全量落地（font 解析/FontMapper/度量缓存/对齐锚点/fillText/strokeText/truncate/分行）；D5-D8 裁定逐项落地（audit 亲核含可用性先于 canDisplay 的顺序、缓存 key 恒等、FRC 来源）；86→93 测试（audit 修复轮补齐五类 baseline 锚点反差、save/restore align/baseline 恢复、LRU 逐出、ascent/descent、注入路径）。独立 closure audit 有条件通过后修复轮完成全部条件。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session 子代理（general-purpose）
- Audit Session: agent_b8dbb8a8-58cc-42fe-934d-ffebb7dbe859
- Evidence:
  - Phase 1 Exit Criteria 5/5 PASS（audit 亲跑 -am BUILD SUCCESS 86/86；缺字体报错测试环境无关；无 TODO/FIXME）
  - Phase 2 Exit Criteria 5/5 PASS（区域墨迹布尔断言判定符合 D4 精神；getLineHeight==measureText('国') 直接断言）
  - Closure Gates 12/12 PASS（scan-hollow 12 文件实扫 0 发现；check-import-order 0 错；check-plan-checklist/check-doc-links 亲跑退出码 0）
  - Anti-Hollow：(a) fillText→drawString / strokeText→TextLayout outline 真实写像素；(b) 缓存计数器语义亲核——若缓存是摆设 cacheHitsOnRepeatCalls 必红；(c) isUsable 源码核对：名单检查先于 canDisplay
  - D5/D6/D7/D8 裁定落地核查：全部 PASS（含 bolder/lighter 映射、Monospaced 名字、key 恒等、anchorText 公式逐项一致）
  - D1-D4 未被破坏：CtxState 扩展全链路、D2 抛错风格延续、WI1 49 测试不回归
  - audit Major-1（五类 baseline 锚点仅 2/4 组有像素断言 + save/restore align/baseline 无测试等覆盖虚标）与 Minor-1（引号家族病理输入裸 StringIndexOutOfBounds）/Minor-3（自比恒真断言）已在修复轮全部补齐：新增 Java2DCanvasTextCoverageTest 7 用例、引号 guard、跨实例对比断言、fontCache 计数器 getter
  - 修复轮后 `./mvnw test -pl nop-format/nop-canvas2d` 93/93 全绿

Follow-up:

- FontMapper 平台配置接线、undefined 对齐值过滤归 WI4；CI fonts-noto-cjk 安装归 WI5（均记录于 Non-Blocking Follow-ups）。
