# 2359 WI1 nop-canvas2d 路径与样式状态机核心

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: ai-dev/backlog/echarts-server-render-roadmap.md WI1（M1）；依赖 plan 2358（WI0 已完成，commit cc2062eb19）
> Related: WI2（文本与度量）/WI3（渐变图案阴影图像）在本模块上续建；WI4 引擎经 JS shim 消费本模块

## Purpose

创建 nop-canvas2d 新模块（纯 Java2D Canvas2D，零 Graal/echarts/报表依赖），落地 roadmap WI1 范围：save/restore/transform 状态机、完整路径子系统（beginPath/moveTo/lineTo/bezierCurveTo/quadraticCurveTo/arc/arcTo/ellipse/rect）、fill（非零缠绕）/stroke、线型样式（lineWidth/Cap/Join/miterLimit/setLineDash/lineDashOffset）、clip、globalAlpha、CSS 颜色解析，配像素级语义单测。

## Current Baseline

以下事实已于 2026-10-10 在 live repo（分支 echarts-server-render @ cc2062eb19）核验：

- nop-format/pom.xml modules 含 14 个子模块（nop-chart-echarts、nop-svg 等），无 nop-canvas2d；nop-format 是图表/格式模块的聚合 parent。
- nop-bom/pom.xml 以 `${nop-entropy.version}` 登记平台内部模块（252 个条目）；nop-chart-echarts 与 nop-svg 因尚无下游消费者未登记。roadmap WI1 写「nop-dependencies BOM 条目」——live 惯例是内部模块登记在 nop-bom（第三方版本才在 nop-dependencies），本计划按 live 惯例登记 nop-bom 并视 roadmap 措辞为不精确（记录为基线修正，不改 roadmap 硬边界）。
- nop-svg 先例：parent=nop-format，依赖 junit(test) + nop-core。nop-canvas2d 取更小依赖面：nop-api-core（为 NopException 基类）+ junit(test)。已知代价：nop-api-core 传递引入 jakarta.annotation-api、jakarta.ws.rs-api、jackson-annotations 2.15.2、slf4j-api——「零 Graal/echarts/报表依赖」目标仍成立。
- 模块级异常先例：NopAiException extends NopException（io.nop.api.core.exceptions.NopException），`(String message)` / `(String message, Throwable cause)` 构造，English 消息——符合 roadmap Cross-Cutting #4 第二档（模块级异常 + English 消息，不用裸 RuntimeException，不定义 ErrorCode 表）。
- WI0 spike（nop-chart-echarts test 范围，667 行）已枚举 zrender 的 ctx 调用面：约 28 个方法 + 约 15 个属性写（grep 实证），并实证 GraalJS interop 边界约束：数据形态值必须以纯 JS 对象/基本类型跨越 JS/Java 边界，Java 对象仅作方法调用后端。本模块 API 全部为方法调用形态（无属性读写 API），天然满足该约束。
- **spike 参照物可用性清单**（审查实证，防「迁移即正确」）：(a) arc 取负角度映射（`-start/-sweep` 转 Arc2D）经 Arc2D 探针实证**正确**，保持勿简化；(b) arc 归一化存在**实证 bug**——sweep==0（start==end）被 `while` 循环扩成整圆，Canvas 规范为零长弧（仅连接线），本计划实现按规范并加退化角测试；(c) spike **无 arcTo/ellipse 实现**——两者按 Canvas 规范从零实现；(d) spike 的 hsl 解析用 `Color.HSBtoRGB` 属**实证 bug**（hsl(0,100%,50%) 实测得 (128,0,0)，正确为 (255,0,0)——HSB(brightness)≠HSL(lightness)）——本计划独立实现 HSL→RGB，禁止 HSBtoRGB 代用；(e) spike 的 clearRect 以白底代替透明且受 globalAlpha 合成影响——本计划按 D1 裁定重做。
- 根 pom `maven.compiler.release=17`：不得使用 17 以上的语言特性（record/text block 可用）。
- roadmap Framework 表约束：canvas2d 只实现 zrender 实际使用的约 30 个方法，不追完整 W3C 规范；文本（WI2）、渐变/图案/阴影/图像/getImageData（WI3）不在本计划。

## Goals

- nop-canvas2d 模块落地并注册（nop-format modules + nop-bom 条目）。
- Canvas2D 状态机核心：save/restore（含变换、clip、全部样式状态的快照恢复）、translate/scale/rotate/transform/setTransform、globalAlpha 合成、clip 交集语义、lineDash/lineDashOffset。
- 路径子系统：beginPath/closePath/moveTo/lineTo/bezierCurveTo/quadraticCurveTo/arc/arcTo/ellipse/rect，Canvas 语义（arc 从当前点连线、角度顺时针/y 向下、anticlockwise 反向、rect 追加闭合子路径、非零缠绕填充）。
- CSS 颜色解析：#rgb/#rgba/#rrggbb/#rrggbbaa、rgb()/rgba()（含 %）、hsl()/hsla()、命名色、transparent；非法输入显式抛 NopCanvas2DException（English 消息）。
- 渲染后端：TYPE_INT_ARGB BufferedImage + Graphics2D，初始全透明 (0,0,0,0)，暴露 getImage() 供下游（WI4 PNG 输出，保留 alpha 通道——ECharts 默认透明背景，报表嵌入需要 alpha）。
- 单测：状态机/颜色/路径语义全部有 focused tests；路径与 clip/dash/alpha 用小尺寸画布像素断言，遵循「像素断言纪律」（见 Execution Plan 前的裁定小节）。

## Adjudications（与 Canvas 规范的刻意分歧及底色裁定）

**D1 底色与 clearRect**：画布为 TYPE_INT_ARGB、初始全透明 (0,0,0,0)；clearRect 恢复 (0,0,0,0)，**不受 globalAlpha/globalCompositeOperation 影响**（受 clip 与 transform 影响）；PNG 输出保留 alpha。
**D2 throw/ignore 矩阵**（本模块对 Canvas 规范「静默忽略」的刻意分歧，全部显式裁定）：

| 输入 | Canvas 规范 | 本模块裁定 |
|------|------------|-----------|
| globalAlpha < 0 或 > 1 | 静默忽略保留原值 | **抛 NopCanvas2DException**（fast-fail 便于定位 shim 层错误） |
| lineWidth ≤ 0（stroke 时） | 忽略本次 stroke | **抛 NopCanvas2DException（setter 阶段）**：setLineWidth(≤0) 即抛 |
| lineCap/lineJoin 非法枚举 | 忽略赋值 | **抛** |
| arc/ellipse radius < 0 | 抛 IndexSizeError | **抛 NopCanvas2DException**（与规范一致） |
| NaN/Infinity 坐标（路径/fillRect 等） | 按点忽略 | **抛 NopCanvas2DException**（NaN 穿透会让失败点远离源头，zrender 数据侧 NaN 须在其自身层处理） |
| setLineDash 含负值段 | 忽略整次调用 | **抛**；空数组/单元素段按规范语义（偶数化由实现内部处理） |
| lineDashOffset | 接受有限值 | 接受有限值（相位语义由测试钉住）；NaN/Infinity 抛 |
| miterLimit ≤ 0 或非有限值 | 忽略赋值 | **抛**（setter 阶段） |
| scale() 零因子 | 接受（矩阵退化） | **抛**（退化矩阵让后续坐标全部坍缩，fast-fail 定位 shim 层错误；closure audit Minor-5 补记） |
| globalAlpha 为 NaN/Infinity | 视为非法忽略 | **抛**（与越界同分支） |
| closePath 在空路径上 | no-op | no-op（不抛，与隐式 moveTo 区分） |
| restore 无配对 save | no-op | **抛 NopCanvas2DException**（栈失衡即 shim 层 bug） |
| 未 beginPath 即 fill()/stroke() | 空路径 no-op | beginPath 后 0 段路径：fill/stroke 为 no-op（zrender 会 fill 空形状）；**从未 beginPath**（当前路径对象不存在）：抛 NopCanvas2DException |
| 空路径上 lineTo/bezierCurveTo/quadraticCurveTo/arc 等 | 隐式 moveTo（lineTo→moveTo 终点；bezier/quadratic→moveTo 第一控制点；arc→moveTo 弧起点） | **按 Canvas 规范隐式 moveTo**，且任何 AWT 异常（IllegalPathStateException 等）必须包装为 NopCanvas2DException 再抛出，不泄漏 AWT 类型 |

**D3 路径生命周期**：fill()/stroke()/clip() 均不消费/清空路径（路径仅 beginPath 重置）——Canvas 语义，zrender 的 strokeFirst 与 save/clip/restore 依赖此行为。
**D4 像素断言纪律**（所有像素语义测试遵守）：(a) 禁止整图像素计数断言（AA 波动不可移植）；(b) 采样点距几何边界 ≥2px，断言「布尔覆盖」（采样点是否为前景/背景色）而非精确 RGB/计数；(c) 渲染 hints（ANTIALIAS_ON + STROKE_PURE + TEXT_ANTIALIAS_ON）是后端固定行为，在 Phase 1 落定并在代码注释注明；(d) miterLimit 用锐角 V 形 + 尖端内侧 ≥2px 采样区分 miter/bevel；lineDashOffset 用长 dash 线相位翻转点采样。

## Non-Goals

- 文本与度量（font 解析、textAlign/textBaseline、fillText/strokeText/measureText）——WI2。
- 渐变/图案/阴影/图像（createLinearGradient/createRadialGradient/createPattern/shadow*/drawImage/getImageData/putImageData）——WI3。
- GraalJS 绑定、JS shim、ECharts 引擎——WI4。
- golden 基线与像素容差对比工具——WI5。
- 完整 W3C Canvas2D 规范面（zrender 不用的方法不进 API，缺位是设计决策而非未实现）。画布尺寸固定于构造，不提供 resize()（静态单帧渲染场景；WI4 若需多尺寸由其自行新建画布实例）。
- owner doc（docs-for-ai）与 module-groups 登记——WI10 收口（本计划显式裁定 No owner-doc update required，见 Phase 2 exit criteria）。

## Scope

### In Scope

- nop-format/nop-canvas2d：新模块（pom + src/main + src/test）。
- nop-format/pom.xml：modules 增 nop-canvas2d。
- nop-bom/pom.xml：dependencyManagement 增 nop-canvas2d 条目（${nop-entropy.version}）。
- ai-dev/plans/、ai-dev/logs/：本计划与日志。

### Out Of Scope

- nop-chart-echarts 的任何改动（spike 代码保留原样，WI4 处置）。
- nop-dependencies（无新第三方依赖引入；junit 已在 BOM）。
- 文档目录（docs-for-ai/）改动。

## Execution Plan

### Phase 1 - 模块注册与状态机核心

Status: completed
Targets: nop-format/nop-canvas2d, nop-format/pom.xml, nop-bom/pom.xml

- Item Types: `Feature`

- [x] 模块骨架：nop-format/nop-canvas2d pom（parent=nop-format，依赖 nop-api-core + junit test）+ 根聚合注册（nop-format modules）+ nop-bom 条目
- [x] 核心 API：ICanvas2D（画布元素：getWidth/getHeight/getContext2D/getImage）、ICanvas2DContext（状态机 + 路径 + 绘制接口，覆盖 WI1 范围；WI2/WI3 方法将在后续计划追加式加入，接口不封印）、NopCanvas2DException（extends NopException，English 消息）。Phase 1 期间路径方法可先以 UnsupportedOperationException("not yet implemented: ...") 占位（Phase 2 实现），不得空方法体
- [x] Java2D 后端 Java2DCanvas：TYPE_INT_ARGB 透明底（D1）；rendering hints 固定（D4c）；save/restore 快照（变换/clip/样式/alpha 全量）、translate/scale/rotate/transform/setTransform、globalAlpha（AlphaComposite 合成，越界抛错 per D2）、setFillStyleCss/setStrokeStyleCss、lineWidth/Cap/Join/miterLimit 校验（D2）、setLineDash/setLineDashOffset（负段抛）、clip（与既有 clip 交集，不消费路径 per D3）、fillRect/strokeRect/clearRect（clearRect 绕过 alpha/composite per D1，NaN 坐标抛 per D2）、fill/stroke 入口（路径生命周期 per D3/空路径裁定 per D2；Phase 1 仅接 fillRect/strokeRect 路径，路径 fill/stroke 由 Phase 2 接通）
- [x] CssColorParser：hex(#rgb/#rgba/#rrggbb/#rrggbbaa)、rgb()/rgba()（整数与 %）、hsl()/hsla()（**独立实现 HSL→RGB，禁止 Color.HSBtoRGB 代用**——见 Baseline (d)）、命名色（覆盖 CSS 基础色表，不少于 19 个 spike 命名色并补齐 CSS16 基础色全集）、transparent；非法输入抛 NopCanvas2DException（English 消息，含原始输入）
- [x] 单测（Phase 1）：save/restore 全状态往返、restore 无配对 save 抛错、transform 矩阵复合顺序（像素断言平移/缩放/旋转后的落点）、globalAlpha 混合值与 clearRect 绕过 alpha 的裁定测试（D1，globalAlpha=0.5 时 clearRect 仍完全清除，以 alpha 通道为观测面）、D2 矩阵中 Phase 1 范围的抛错分支（颜色/alpha 含 NaN/miterLimit/lineWidth/Cap/Join/dash 负段/NaN 坐标）、**颜色值断言**（hsl(0,100%,50%)==#ff0000、#ff000080 的 alpha 通道、rgba % 形态、modern 空格语法 rgb(51 51 51 / 0.5)——裁定为支持并钉此例）

Exit Criteria:

- [x] `./mvnw test -pl nop-format/nop-canvas2d -am` 全绿
- [x] nop-bom 登记可机械核验：nop-bom/pom.xml 中 nop-canvas2d 条目与相邻内部模块条目逐字同构（grep 核对 groupId/artifactId/${nop-entropy.version} 三要素）；**真实接线验证**（下游消费者经 BOM 解析到本模块）显式裁定归首个消费者计划（WI2/WI4），本计划无消费者、无从验证，此裁定记录于日志
- [x] **无静默跳过**：WI2/WI3 的能力以「接口方法缺位」呈现（设计决策，非空方法）；Phase 1 占位的路径方法抛 UnsupportedOperationException；D2 矩阵全部裁定项有对应抛错测试
- [x] **测试断言语义**：状态机测试断言恢复后的渲染行为（非仅字段值），每条测试注明所钉住的 Canvas 语义；clearRect 测试以 alpha 通道观测
- [x] No owner-doc update required（新模块的 module-groups/owner doc 登记由 roadmap WI10 统一收口，本计划不触碰 docs-for-ai/）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 路径子系统与像素语义验证

Status: completed
Targets: nop-format/nop-canvas2d

- Item Types: `Feature` + `Proof`

- [x] 路径方法全量：beginPath（重置路径）、moveTo/lineTo、bezierCurveTo、quadraticCurveTo（三点转三次）、arc（取负角度映射——Baseline (a) 已实证正确，保持；**退化角矩阵**：radius<0 抛 per D2、sweep==0（start==end）为零长弧（仅当前点连接线）、|sweep|≥2π 且方向匹配为整圆、anticlockwise 取「符号正确且幅值最小」归一化）、arcTo（radius<0 抛、radius==0 等价 lineTo、共线退化 lineTo 语义）、ellipse（负半径轴抛、含 rotation）、rect（闭合子路径）、closePath；空路径隐式 moveTo 矩阵（D2）与 AWT 异常包装
- [x] fill（非零缠绕 Path2D.WIND_NON_ZERO）/stroke（BasicStroke 按 lineDash/Cap/Join/miterLimit）；路径生命周期 per D3（fill/stroke/clip 不消费路径）
- [x] 像素语义单测（D4 纪律）：矩形填充/描边、非零缠绕（自交路径）、arc 四分之一弧落点与 anticlockwise 反差、**arc 退化角矩阵三例**（sweep==0 零长弧、整圆、anticlockwise 最小幅值）、arcTo 与相邻直线连接（含 radius==0/共线退化）、ellipse 旋转、rect 子路径闭合、closePath 闭合、lineDash 交替 pattern、lineCap butt/round 端点差异、**miterLimit 锐角 V 尖端采样（miter vs bevel 区分）**、**lineDashOffset 相位翻转点采样**、clip 限制绘制区域
- [x] spike 对照（仅限 spike 有实现的部分，按 Baseline 清单）：arc 取负映射一致性确认（保持负号）、quadratic 三次转换公式（无当前点时规范为 moveTo 第一控制点，与 spike 的 (0,0) 起点分歧以规范为准）、CSS 颜色 hex/rgb/命名色行为一致；hsl 与 clearRect **不对照 spike**（Baseline (d)/(e) 已裁定 spike 为 bug，以本计划 D1/独立 HSL 实现为准）

Exit Criteria:

- [x] `./mvnw test -pl nop-format/nop-canvas2d -am` 全绿（含 Phase 1 测试不回归）
- [x] 像素语义测试全部遵守 D4 纪律（无整图计数断言、采样点距边界 ≥2px、布尔覆盖断言、hints 固定）并标注所钉住的 Canvas 语义
- [x] **接线验证（机械代理判据）**：nop-bom 条目与相邻条目逐字同构（grep）；真实消费者接线验证显式归首个消费者计划（同 Phase 1 裁定）
- [x] No owner-doc update required（同 Phase 1，登记归 WI10）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 模块已注册（nop-format modules + nop-bom 条目）且可独立构建
- [x] WI1 范围 API 全部落地且有 focused tests（状态机/颜色/路径/clip/dash/alpha；audit Major-1 指出的 scale/transform/bezierCurveTo/strokeRect 四方法零测试已在修复轮补齐）
- [x] 无空方法体/占位实现；WI2/WI3 能力以接口缺位呈现而非 stub
- [x] 像素语义断言可证伪且标注 Canvas 语义依据
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] No owner-doc update required（WI10 统一收口，理由记录在案）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：fill/stroke 真实写像素（测试以像素颜色断言渲染结果，非仅调用不抛错）
- [x] `./mvnw test -pl nop-format/nop-canvas2d -am` 通过
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-format/nop-canvas2d --severity high` 退出码 0（本计划触及 src/main，此 gate 有实质检测力）
- [x] checkstyle / 代码规范：imports 分组、4 空格、错误消息 English
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

（暂无）

## Non-Blocking Follow-ups

- WI3 落地渐变/图案/阴影时若发现 WI1 状态机快照需扩展（如 shadow 状态入 save/restore），由 WI3 plan 承载，不影响本计划 closure。

## Closure

Status Note: 模块注册（nop-format modules + nop-bom）与 WI1 范围 API 全量落地；D1-D4 裁定全部实现；49 个 focused tests 全绿（含 audit 修复轮补齐的 4 方法覆盖 + closePath 空路径/奇数 dash 偶数化/零因子抛错）；Anti-Hollow 两条指定路径追踪吻合（arc 取负映射与 spike 逐字一致且修复 sweep==0 bug、quadratic 隐式 moveTo 用第一控制点）。独立 closure audit 有条件通过后，修复轮完成全部条件。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session 子代理（general-purpose）
- Audit Session: agent_554a6404-0947-46f5-86c1-a34ec077c5c4
- Evidence:
  - Phase 1 Exit Criteria 6/6 PASS（audit 亲跑 -am 全绿 42 tests→修复轮后 49；BOM 条目与相邻条目逐字同构；无 TODO/FIXME/空方法体；测试断言语义；No owner-doc；日志）
  - Phase 2 Exit Criteria 5/5 PASS（D4 纪律抽 6 测试几何复核；采样点边距 3 处字面 <2px 但经逐像素复核为确定性断言，记录为 Minor-4）
  - Closure Gates 12/12 PASS（scan-hollow 对 src/main 5 文件实扫全 0；check-plan-checklist/check-doc-links 亲跑退出码 0；规范抽查 9 文件通过）
  - Anti-Hollow 指定路径追踪：(1) arc 取负角度映射 Java2DCanvas 与 spike 逐字一致且修复 sweep==0 扩整圆 bug（早退返回）；(2) quadraticCurveTo 空路径隐式 moveTo 用第一控制点（非 spike 的 (0,0)），测试 quadraticCurveImplicitMoveUsesControlPoint 钉住
  - D2 矩阵 14 行覆盖对照：11 行全covered，3 行（closePath 空路径、bezierCurveTo 隐式 moveTo、AWT 异常包装不可达路径）在修复轮补齐前两项测试
  - audit Major-1（4 方法零测试）与 Minor-3/5/6 已在修复轮全部修复：新增 Java2DCanvasCoverageGapTest 7 测试（scale/transform/bezierCurveTo/strokeRect/closePath 空路径/奇数 dash 偶数化/零因子抛错），恒真断言删除，D2 矩阵补 scale 零因子行
  - 修复轮后 `./mvnw test -pl nop-format/nop-canvas2d` 49/49 全绿

Follow-up:

- WI3 落地渐变/图案/阴影时若状态机快照需扩展（shadow 入 save/restore），由 WI3 plan 承载。
