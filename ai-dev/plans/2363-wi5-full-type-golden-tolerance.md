# 2363 WI5 option 全类型出图验证与容差对比工具

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: ai-dev/backlog/echarts-server-render-roadmap.md WI5（M2）；依赖 plan 2362（WI4 已完成，commit 76f38ef09e）
> Related: WI9 17 图型对版复用本计划的容差工具；WI4 引擎为本计划唯一渲染通道

## Purpose

落地 roadmap WI5：option JSON 全类型出图验证（bar/line/area/pie/doughnut/scatter/radar/heatmap/funnel/gauge/boxplot/graph/sankey/tree/treemap/sunburst/lines/map/parallel/calendar/candlestick + legend/axis/visualMap/dataZoom/markLine/markPoint/rich 文本/渐变/decal/image:// 特性面）、新建仓库首个像素容差 image-diff 测试工具、产出自有 golden 基线；heatmap getImageData 路径、aria、SVG 地图 DOMParser 裁定、progressive 泵复验。

## Current Baseline

以下事实已于 2026-10-10 在 live repo（分支 echarts-server-render @ 76f38ef09e）核验：

- EChartsRenderer.renderPng/renderSvg 可用（WI4，17 测试全绿）；EchartsEngine 包私有测试缝（pre-render JS + evaluateForTest）可供本计划特殊场景注入（registerMap、组件断言）。
- 仓库**无任何像素 diff 工具**（roadmap Current Baseline 记录，本次 grep 复核 0 命中）——WI5 新建。
- 字体环境：mac 本机（PingFang SC 等物理字体 + SansSerif fallback，WI0/WI2 实证）；CI（ubuntu-latest）中文字体不受保证。**门控谓词（落地）**：以 GraphicsEnvironment 探测 `new Font(Font.SANS_SERIF,..).canDisplay('中')`（或 PingFang SC 存在性）为门控——无 CJK 字形的环境 assumeTrue 跳过 golden/文本相关断言；**后果如实记录**：跳过后 golden 比对仅存在于产出机（本机），CI 剩余覆盖 = 非 golden 的结构性断言（尺寸/魔数/异常面）。
- heatmap 消费面（dist:69625-69653）：全画布 getImageData → JS 处理 → putImageData——WI4 已桥接（EchartsBridgeTest 图像桥测试在位），本计划补 heatmap option 端到端出图验证（**最小 option 需要 visualMap** 做色彩映射）。
- **dataZoom 裁定（B1，roadmap 已同步改写）**：dataZoom 属交互件按 D15 剥离是终态——WI5 的 dataZoom 验证 = 剥离行为回归（含/不含输出逐像素一致），非 dataZoom 本体出图。
- lines 系列规范上要求 geo 坐标系；map 系列需 echarts.registerMap(name, geoJSON)（纯 JS，裸环境可用）。SVG 地图（map 系列的 SVG GeoJSON 形态）与 DOMParser 相关——按 roadmap「GeoJSON 优先」裁定只支持 GeoJSON 注册（D25）。
- aria 组件（aria: {show: true}）在 canvas 模式生成 aria-label 的 DOM 属性操作经 shim 的 canvas 对象需有 setAttribute 容忍（spike 未实现 setAttribute——本计划验证 aria 路径，必要时 shim canvas 增 no-op setAttribute/getAttribute/appendChild/removeChild 等 DOM 属性容忍面，记录清单）。
- 渐变 option（itemStyle.color 为线性渐变对象形态 {type:'linear',...}）由 zrender JS 侧构建 ctx 渐变——已桥接（WI3/WI4）；decal（背景贴花图案）经 createPattern 桥——全类型矩阵覆盖验证。
- image:// 形态的元素（graphic image 元素，WI4 已有 loadImage defer 计数测试）——矩阵中以 graphic image 覆盖。
- 根 pom release=17；测试基座 surefire。

## Goals

- ImageDiffAssert 测试工具（nop-chart-echarts test 范围）：尺寸一致性断言 + 逐像素容差（每通道 ±N）+ 差异像素比例阈值 + 差异可视化输出（可选 diff 图落盘 _tmp 或 target）。
- 全类型 golden 基线：21 种图型 × 最小可渲染 option，渲染 → 与 golden 比对（容差内）；golden 以 PNG 提交 src/test/resources/golden/；生成模式由系统属性 -Degcharts.golden.generate=true 开启（开启时写出并跳过比对，输出提示）。
- 特性面验证：legend/axis/visualMap/dataZoom(inside)/markLine/markPoint/rich 文本/渐变 option/decal/graphic image:// 各一条出图断言（非空白 + 关键特征像素或尺寸断言）。
- heatmap getImageData 端到端：heatmap option 渲染 → 引擎层断言 JS 侧读回像素与 Java2D 坐标一致（经 pre-render/evaluate 缝）。
- aria 出图验证（canvas 模式 aria 不崩且有图）；SVG 地图裁定落地（D25：仅 GeoJSON）。
- progressive 泵复验：大数据量 bar（>2000 点）渐进渲染完成度（泵后关键区域像素非空 + 无异常）。

## Non-Goals

- 像素级跨平台一致性（golden 容差只吸收同环境抖动；跨平台字体差异是 CI 门控问题）。
- ChartModel 适配（WI6）；对版验收（WI9 用 Excel 截图，非本计划 golden）；owner doc（WI10）。
- 性能基准（渐进渲染耗时只复验正确性）。

## Adjudications（延续 D 编号）

- **D25 地图**：map 系列仅支持 GeoJSON 注册（echarts.registerMap）；SVG 地图（DOMParser 路径）不支持——roadmap「GeoJSON 优先」落定，SVG 地图需求出现时另行立项。
- **D26 golden 约定**：golden 文件名 = 图型/特性名 + .png；差异像素定义 = 任一 ARGB 通道差 >8（alpha 参与）；比对容差 = 差异像素比例 ≤1.5%（AA 抖动安全垫，单项放宽天花板：通道 ≤16、比例 ≤5%，须记录实测 diff 数值）；生成模式 = 标记文件 target/golden-generate.flag 存在（surefire fork 属性透传在父 pom 配置下不可靠——实测 systemPropertyVariables/argLine 均未达 fork，改标记文件机制，target/ 不入库 CI 天然无标记）：**仍执行非空白断言**（坏渲染不得产出 golden）、检测 CI 环境变量时拒跑、写 target/golden/ 由执行者拷贝；golden 缺失时测试失败并提示生成命令；**收口校验**：提交的 golden 与当前渲染重新 diff 为零（防拷贝陈旧）。失败时必产出 diff 图（target/diff/）。
- **D27 渐进泵复验判据**：>3000 点（dist progressiveThreshold=3000）line/bar 渲染断言 **pumpedTimers > 0（证明渐进帧被泵驱动）且未触顶泵上限（证明泵到静默）** + 非空白；configure 须 finally 恢复（全局单例）。「非空白+无异常」单用是空洞判据，已废弃。

## Scope

### In Scope

- nop-format/nop-chart-echarts：ImageDiffAssert（test）、全类型矩阵测试、特性面测试、golden 资源。
- ai-dev/plans/、ai-dev/logs/。

### Out Of Scope

- ChartModel 适配（WI6）；Excel 截图对版（WI9）；owner doc（WI10）；性能优化。

## Execution Plan

### Phase 1 - 容差工具与全类型 golden 矩阵

Status: completed
Targets: nop-format/nop-chart-echarts

- Item Types: `Feature` + `Proof`

- [x] ImageDiffAssert（test 工具）：assertMatchesGolden(actual, goldenName)（D26 容差与生成模式）、assertSimilar(actual, expected, channelTol, maxDiffRatio)
- [x] 21 图型最小可渲染 option 表（bar/line/area/pie/doughnut/scatter/radar/heatmap/funnel/gauge/boxplot/graph/sankey/tree/treemap/sunburst/lines/map(GeoJSON)/parallel/calendar/candlestick）逐个出图断言非空白。**已知硬约束（预先写明减少往返）**：radar 必带 indicator；heatmap 必带 visualMap；calendar 必带 range；parallel 必带 parallelAxis；graph 显式 layout:'none'+x/y（默认 force 逐帧模拟非确定性）；boxplot 用预计算值或 dataset+boxplotTransform；lines 优先试 coordinateSystem:'cartesian2d'（m5，可不经 geo——以 dist 实测为准）；map 经 registerMap 注入 GeoJSON。实现时实测校正并记录
- [x] golden 生成（本机）+ 提交 + 比对测试全绿
- [x] 单测组织：全类型矩阵参数化入口（断言源条目数 ≥21 的覆盖数断言，Anti-Hollow 机制）+ 每型独立 golden；**矩阵/地图/heatmap 测试落位 io.nop.chart.echarts.engine 包**（registerMap 经 evaluateForTest 注入，包私有缝）；「非空白」统一用 ImageDiffAssert.assertNotBlank（非背景像素比例阈值）

Exit Criteria:

- [x] `./mvnw test -pl nop-format/nop-chart-echarts -am` 全绿
- [x] 21 图型全部有非空白断言 + golden 比对（防静默新增/回归）
- [x] ImageDiffAssert 有自身行为测试（相同图过、篡改图不过）
- [x] No owner-doc update required（WI10）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 特性面、heatmap 数据面与裁定项

Status: completed
Targets: nop-format/nop-chart-echarts

- Item Types: `Feature` + `Proof` + `Decision`

- [x] 特性面出图：legend/axis/visualMap/markLine/markPoint/rich 文本/渐变 option/decal/graphic image://（image 请求计数告警断言）逐条非空白断言（部分带 golden）；**dataZoom 按裁定改为剥离行为回归验证**（含/不含 dataZoom 的 option 输出逐像素一致——B1，roadmap WI5 文本已同步改写）
- [x] heatmap getImageData 端到端：heatmap option 渲染后 JS 侧读回热力区像素（经 evaluateForTest），与 Java2D 坐标断言一致
- [x] aria 出图（canvas 模式 aria:{show:true} 经 dist:92103 无保护 setAttribute 必抛 TypeError——**shim canvas 增 no-op DOM 容忍面是必然项**：setAttribute/getAttribute，清单记录于 shim 注释）
- [x] SVG 地图裁定落地（D25 记录 + 图例注释）
- [x] **泵配置接线修复（Item Type: `Fix`，M1）**：timerPumpMaxRounds 是死配置（EchartsEngine.configure 写入但桥用硬编码 16）——接线为 drainTimers(maxRounds) 参数化，回归断言 configure 生效
- [x] progressive 泵复验（**>3000 点**（dist progressiveThreshold=3000，dist:19052）line/bar，D27 判据：pumpedTimers > 0 且未触顶上限）

Exit Criteria:

- [x] `./mvnw test -pl nop-format/nop-chart-echarts -am` 全绿（Phase 1 不回归）
- [x] 特性面每条有独立断言；heatmap 数据面有一致性断言
- [x] D25 裁定记录于代码注释（registerMap 调用处）
- [x] No owner-doc update required（WI10）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 21 图型 + 特性面全部出图验证且有 golden/非空白断言
- [x] ImageDiffAssert 工具落地且有自身行为测试
- [x] 无空方法体/占位实现
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] No owner-doc update required（WI10 统一收口）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：golden 比对真实生效（篡改 golden 必红的验证记录）；覆盖数断言机制 = 参数化源条目数 ≥21 + golden 目录文件数与源条目一致（测试内断言）
- [x] `./mvnw test -pl nop-format/nop-chart-echarts -am` 通过
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-format/nop-chart-echarts --severity high` 退出码 0
- [x] 代码规范检查（仓库实际命令：node ai-dev/tools/check-import-order.mjs + ai-dev/tools/run-java-lint.sh 或 pmd -Pqa）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

（暂无）

## Non-Blocking Follow-ups

- CI 中文字体安装（fonts-noto-cjk）与跨平台 golden 策略归 WI9 前置（WI9 对版在本机执行则无阻塞）。
- 渐变 option 的 golden 细节抖动若超容差，由执行记录并放宽该单项容差（记录于测试注释）。

## Closure

Status Note: 21 图型全矩阵出图 + golden 容差比对落地；ImageDiffAssert 工具（差异像素定义/生成模式防护/失败必产 diff 图）落地且有自身行为测试；特性面（legend/markLine/markPoint/rich/渐变 option/decal/aria/graphic image）全出图；dataZoom 剥离回归逐像素一致（B1 裁定）；heatmap JS 侧像素读回断言；progressive 泵 >3000 点复验（pumpedTimers>0 未触顶）。独立 closure audit 有条件通过后修复轮完成。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session 子代理（general-purpose）
- Audit Session: agent_f4c6ffb4-e531-4294-bd4d-5d7084aaf8a2（第一轮）+ agent_9e8f7fa1-c6da-4e07-8249-66b7cc6d006c 后续修复轮确认
- Evidence:
  - Phase 1/2 Exit Criteria：audit 亲跑 -am BUILD SUCCESS（reactor 4594 tests 0 fail）
  - Anti-Hollow 四重探针：篡改 golden 必红（diff ratio 16.382%）且必产 diff 图、恢复后复绿、重生成 21 张 byte-identical、CI 拒跑 exit 1
  - audit 修复轮：Major-1 ImageDiffAssert 自身行为测试（ImageDiffAssertBehaviorTest 4 用例：相同过/容差内过/篡改红+diff 图落盘/空白红）；Major-2 pump configure 生效回归（pump=1 → pumpedTimers≤1，恢复 16 → 更多轮次）；Major-3 CJK 字体门控 assumeTrue（双矩阵类，D26 producer-machine scoped 裁定）；Minor（覆盖数断言在位、注释与 roadmap 里程碑/Last updated 更新、WI4 遗留 import-order 修复）
  - 修复轮后 `./mvnw test -pl nop-format/nop-chart-echarts` 35/35 全绿
- Evidence:
  - 实测校正记录：graph layout:none 裸 x/y 空白 → circular；lines cartesian2d 需 polyline:true（effect 形态触发 DOM setAttribute 缺失，未入矩阵）；calendar 需 visualMap；tree/sunburst 调色板产出扩展命名色（CssColors 扩至全 CSS 扩展命名色 148 项）
  - live defect 修复：CssColors rgb/hsl 分量超界未钳制（zrender 计算性颜色 rgba(135,175,274,.2) 实测触发裸 IllegalArgumentException）——按 CSS 语义改钳制 + 回归测试（审查 Minor-1 包装修复使缺陷显形）
  - 基础设施事实：surefire fork 属性透传在本仓库父 pom 下不可靠（systemPropertyVariables/argLine 实测均未达 fork）——golden 生成模式改标记文件 target/golden-generate.flag（D26 修订记录于 plan）
  - 21 goldens 提交 src/test/resources/golden/（含 feature-legend-markline）；比对模式复跑全绿（收口校验 diff 为零）
  - dataZoom 剥离回归：两侧同走 normalize 管线隔离数字序列化变量后逐像素一致
  - heatmap JS 侧读回断言经 keepAlive 测试缝（先断言后手动 dispose）
  - progressive：>3000 点 pumpedTimers>0 且未触顶

Follow-up:

- CI fonts-noto-cjk 安装归 WI9 前置；copyFrom 消费者裁定归 WI4/WI5 后续。
