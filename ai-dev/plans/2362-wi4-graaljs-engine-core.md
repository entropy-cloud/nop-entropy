# 2362 WI4 nop-chart-echarts GraalJS 引擎核心

> Plan Status: completed
> Last Reviewed: 2026-10-09
> Source: ai-dev/backlog/echarts-server-render-roadmap.md WI4（M2）；依赖 plan 2358（WI0 裁定）、2359-2361（WI1-3 nop-canvas2d）
> Related: WI5 golden 消费本引擎；WI7-WI8 报表接线消费 EChartsRenderer

## Purpose

落地 roadmap WI4：在 nop-chart-echarts 模块实现生产级 GraalJS 引擎——Context 池（每线程复用、echarts.js 每 Context 只加载一次、超时/异常 → 模块级异常 English 消息）、polyglot↔canvas2d 桥（假 canvas 根、ctx 属性写入、drawImage 吃 fake canvas、getImageData → JS 数据）、setPlatformAPI 注入（createCanvas 覆盖、loadImage defer、measureText、getTime）、OptionNormalizer（剥离交互件）、EChartsRenderer.render PNG/SVG 统一入口、progressive timer 泵。

## Current Baseline

以下事实已于 2026-10-10 在 live repo（分支 echarts-server-render @ 987327b80e）核验：

- nop-canvas2d 完整可用（131 测试）：Java2DCanvas 全语义面（状态机/路径/文本/渐变/图案/阴影/图像数据），ICanvas2D.getImage() → BufferedImage，Canvas2DFactory 工厂——引擎桥的 Java 侧基座。
- WI0 spike（nop-chart-echarts test 范围）已实证：裸环境（无 window/document/navigator/self）+ global/console/timer 注入 + setPlatformAPI 四键（createCanvas/loadImage/measureText/getTime）+ JS 层 shim 把属性读写翻译为 Java 方法调用 + dist 加载 → 中文 bar PNG 端到端可行；Interop 硬约束：**数据形态值必须以纯 JS 对象跨越边界**（measureText 返回值 .width 事件）。
- dist 源码核验：UMD 用 globalThis；环境探测不引用 process；无 window → env.node → animation:false → setOption 末尾自动 flush；setPlatformAPI 仅 4 键（createCanvas/measureText/loadImage/getTime）；**dist dev 构建在无 window 时读裸 global 标识符**（echarts 构造器），裸环境需 `global.global = global`。
- zrender ctx 调用面（审查按 spike shim 实测）：33 方法 + 16 属性写；未用 ctx.filter/roundRect/evenodd。
- createPattern(image, pattern.repeat || 'repeat')（dist:27336）；pattern.setTransform 有 DOMMatrix 守卫（裸环境跳过，dist:27337-27345）；getImageData(0,0,w,h)/putImageData(data,0,0) 为 heatmap 消费面（dist:69625-69653）；drawImage 9 参切片形态（dist:27501）。
- SVG SSR：dist:7341 `opts.ssr || painter.ssrOnly`；echarts 官方 Node SVG 用法 `init(null, null, {renderer:'svg', ssr:true, width, height})` + `renderToSVGString()`。WI0 baseline 记录「ssr:true 重开动画不自动 flush」针对 canvas 路径的自动 flush——SSR 路径按官方用法显式 `chart.render()` 后 `renderToSVGString()`，flush 语义由 ssr 分支保证（计划内含 spike 验证步，失败则 SVG 输出降级为显式 scope 变更）。
- 依赖坐标（WI0 裁定）：org.graalvm.polyglot:polyglot + org.graalvm.polyglot:js 25.2.4、org.mvnpm:echarts:6.1.0（排除 tslib/zrender）——现挂 test 范围，WI4 将主代码需要提为 compile 范围（BOM 管理版本不变）。
- 渐进渲染：zrender 在 node 环境经 setTimeout 泵帧（WI0 spike 已有 timer 队列 drain 原型，执行 8 轮上限）。
- 浏览器交互组件（toolbox/brush/dataZoom 等）在静态渲染下要么不渲染要么渲染交互 UI 残留——需要 Normalizer 剥离（roadmap「剥离 toolbox 交互件等」，具体清单为计划裁定）。
- 根 pom release=17；模块异常惯例：NopException 子类 + English 消息（plan 2359 先例）。
- GraalJS 25.2.4 在 JDK 26/21 走解释器模式（版本检查警告，功能完好）——性能观测归 WI5，勿在 WI4 误判为缺陷（审查复跑实证 2.5s/渲染可接受）。

## Goals

- 生产 shim（src/main/resources）：WI0 spike shim 生产化——绑定 Java2DCanvas（经 Canvas2DFactory），ctx 属性写入翻译（defineProperty）、drawImage 识别 fake canvas 源、getImageData 返回纯 JS 数据对象、gradient 对象暴露 addColorStop、pattern 经 createPattern 桥、timer defer 到 Java 泵。
- EchartsEngine：ThreadLocal Context 复用（每线程一池）、echarts.js 源缓存且每 Context 只 eval 一次、裸环境红线（无 window/document/navigator/self、不定义 process）、setPlatformAPI 注入、progressive timer 泵（有界 drain 至静默）、渲染超时（scheduled interrupt + 污染 Context 重建）。
- EChartsRenderer 统一入口：`renderPng(optionJson, width, height)` → **byte[]**（D19）、`renderSvg(optionJson, width, height)` → SVG 字符串；OptionNormalizer 剥离交互组件清单（裁定：toolbox/tooltip/brush/dataZoom/timeline——静态渲染无交互语义且 toolbox 渲染 UI 残留）。
- 模块异常 NopChartEchartsException（extends NopException，English 消息）：坏 JSON、JS 执行异常（PolyglotException 包装含 JS 侧消息）、超时、尺寸非法。
- 单测：端到端中文 bar/line/pie PNG（像素断言）、SVG 字符串（含 <svg 根与 path）、Normalizer 剥离断言、超时与错误面、线程复用（两次渲染同 Context——经引擎统计计数断言）。

## Non-Goals

- ChartModel→option 适配器（WI6）；golden 基线（WI5）；报表接线（WI7-8）；owner doc（WI10）。
- 多 Context 并发池化调优（ThreadLocal 每线程单 Context 已满足平台渲染形态；并发压测归 WI5 观测）。
- 渐进渲染帧级精度（泵到静默即可，帧调度归 zrender）。
- SVG 的 DOM 解析/后处理（WI5 SVG 地图 DOMParser 裁定）。

## Adjudications（延续 D 编号）

- **D13 引擎形态**：EchartsEngine 单例（静态），ThreadLocal<Context> 每线程复用；Context 关闭（超时/不可恢复错误）后清空 ThreadLocal 条目，本线程下次渲染重建。**共享 Engine**：Context.newBuilder().engine(sharedEngine)（避免每 Context 独立引擎开销）。echarts.js 源从 classpath 读一次缓存（String）；每 Context eval 一次（引擎计数器可观测）。allowAllAccess(true)（host 绑定需要；模块内可控输入）。
- **D14 超时**：渲染提交时经 ScheduledExecutor 注册 interrupt 任务（默认 30s，经 EchartsEngine 静态 setter 配置——静态单例吃不到 NopIoC 注入，IoC bean 接线归 WI7-8 报表装配时做）；**渲染正常完成在 finally 中取消该任务**（Context.interrupt(Duration) 是 cancel+close 语义，迟到中断会关闭健康 Context——25.2.4 仅此签名）；触发 → interrupt → PolyglotException 且 isCancelled → 抛 NopChartEchartsException（render timed out）；非取消的 PolyglotException → JS 执行失败（分类错误消息）。超时后 Context 污染：ThreadLocal 条目关闭重建。**测试缝（裁定，否决大数据 option 方案）**：包私有渲染入口允许附加 pre-render JS 脚本（仅 io.nop.chart.echarts 包内测试可见）——超时测试 = 50ms 超时 + `while(true){}`；JS 异常测试 = pre-render `throw new Error('boom')` 断言包装消息。
- **D15 Normalizer 清单与理由**：剥离顶层组件键 toolbox、tooltip、brush、dataZoom、timeline（含数组形态）。**理由（修正）**：防交互 UI 残留（toolbox 图标按钮、inside 型 dataZoom 滑条、timeline 控件确实绘制 UI）+ 减组件开销；tooltip 本体不上 canvas（浏览器带/不带 tooltip 像素一致），顶层剥离即可——series 级 tooltip 不上 canvas 不处理（如实记录）。保留其余一切；深拷贝剥离不改调用方输入。
- **D16 SVG 路径**：init(null, null, {renderer:'svg', ssr:true, width, height}) + **setOption 前强制合并 animation:false**（ssr 重开动画——renderToSVGString 不停动画会捕获中间态，dist:28746 无 stopAnimation；静态渲染语义要求终态）+ chart.render() + renderToSVGString + dispose；**cssAnimation:false**（renderToString 参数，防 @keyframes 嵌入——PDF/docx 光栅化消费方不支持）；若裸环境 SVG painter 失败 → 显式 scope 变更记录，SVG 输出抛 UnsupportedOperationException，不得静默返回空串。**roadmap Cross-Cutting #2 红线回写**（执行项）：canvas 路径不用 ssr:true；SVG SSR 用 ssr:true + 强制 animation:false——roadmap 该句按 Rules 在动工前同步改写。
- **D17 资源桥**：drawImage 参数为 fake canvas 时经其绑定的 Java2DCanvas.getImage() 绘制；getImageData 返回 {width, height, data: JS 数组（RGBA 0-255）}（纯 JS 对象，WI0 interop 约束）；putImageData 接受同构 JS 对象。
- **D18 timer 泵**：setOption 后 drain timer 队列至静默（上限轮次默认 16）；**每次渲染开始清空队列并重置计数**（loadImage defer 永不执行的闭包不跨渲染堆积）；loadImage 注册计数可观测，图表含 image 时告警日志。
- **D19 返回形态**：`renderPng(optionJson, width, height)` → **byte[]**（PNG 编码 ImageIO.write 在 Renderer 内做——WI7 HTML dataURL 与 WI8 PdfImageHelper 均消费编码字节，PdfImageHelper.java:34 实证消费 ByteString）；`renderSvg` → String；BufferedImage 仅内部传递。
- **D20 JSON 解析位置**：Java 侧 Jackson 仅做校验 + Normalizer 剥离 + 重序列化（坏 JSON 与 JS 异常分属两个错误面）；JS 侧 eval 内 `JSON.parse(normalizedString)` 得纯 JS 对象（WI0 interop 约束：Java map 不跨界）。
- **D21 chart 生命周期**：每次渲染 init 新 chart，**try/finally chart.dispose()**（instances$1 主注册表 dist:30282 强引用，不 dispose 每渲染泄漏整棵对象树 + BufferedImage 引用链；dispose 对 fake canvas 安全——CanvasPainter.dispose 仅属性写，dist:40831）。
- **D22 canvas 尺寸 setter（复核修订）**：CanvasPainter init 必写 rootCanvas.width=width*dpr（同值，dpr=1）；**platform 离屏画布会被 zrender 合法赋异值**（heatmap brush canvas dist:69663、decal canvas dist:27942）——裁定：**任意 shim 画布异值赋值 → 重建底层 Java2DCanvas 缓冲（浏览器 resize 清空语义），root 路径恒同值行为不变**；引擎/shim 持有包装引用惰性取当前 backend（不持直接 backend 引用）。
- **D23 字体配置面**：EchartsEngine 暴露 TextMeasurer 定制入口（TextMeasurer(mapper, cacheSize) 构造已支持），FontMapper 缺字体报错路径有单测；默认逻辑字体三元组。
- **D24 生产 shim 与 spike 差异清单**（完整）：(a) canvas 绑定 Java2DCanvas（Canvas2DFactory），非 spike 后端；(b) shadowBlur/Color/OffsetX/Y 从 throw 改为 Java2DCanvas D11 真实现；(c) arcTo/ellipse 从 throw 改真实现；(d) createPattern 桥接（pattern 源为 fake canvas 时经 getImage()，decal 路径 dist:27336）；(e) globalCompositeOperation≠source-over 与 fill('evenodd') **维持 throw**（baseline 实证 zrender 未用——保留 throw 即裁定）；(f) setInterval/clearInterval **维持 throw**；(g) measureText 经注入的 platformApi.measureText→TextMeasurer——zrender 隐藏测量画布不再创建（dist:170 短路；spike 实际创建了 300×150 隐藏画布）；(h) canvas.style={} 保留（Layer/createDom 读 domStyle，dist:39871）；(i) width/height setter 按 D22（异值重建缓冲）；(j) getTime 不注入（Date.now 原生可用，spike 实证未注入通过）。

## Scope

### In Scope

- nop-format/nop-chart-echarts：pom 依赖提升（polyglot/js/echarts compile 范围，echarts 排除 tslib/zrender 维持）；src/main 生产代码（engine/bridge/normalizer/renderer/异常）；src/main/resources 生产 shim；测试。
- ai-dev/plans/、ai-dev/logs/。

### Out Of Scope

- ChartModel 适配（WI6）；golden（WI5）；owner doc（WI10）；WI0 spike 代码处置（本计划内裁定去留：生产 shim 就位后删除 spike 或保留为对照——执行时裁定并记录）。

## Execution Plan

### Phase 1 - 引擎骨架与 canvas 渲染路径

Status: completed
Targets: nop-format/nop-chart-echarts

- Item Types: `Feature`

- [x] pom：polyglot/js/echarts 提升 compile 范围（版本仍 BOM 管理）；NopChartEchartsException
- [x] 生产 shim echarts-canvas-bridge.js（src/main/resources）：global 自引用红线注释、console/timer/平台注入、ctx 属性翻译、gradient/pattern 桥、drawImage fake canvas、getImageData/putImageData 桥、bare-env 红线断言（globalThis 无 window/document/navigator/self）
- [x] EchartsEngine：ThreadLocal Context + 共享 Engine、dist 源缓存 + 每 Context 单次 eval、platform 注入（createCanvas→Java2DCanvas、loadImage defer、measureText→TextMeasurer；getTime 不注入 D24j）、timer 泵（D18，渲染开始清队列）、超时（D14 含 finally 取消 + 污染重建）
- [x] **roadmap 红线回写**（D16）：Cross-Cutting #2 改写为「canvas 路径不用 ssr:true；SVG SSR 用 ssr:true + 强制 animation:false」
- [x] **dispose 生命周期**（D21/B1）：renderPng/renderSvg 全路径 try/finally chart.dispose()；回归断言 dispose 后 getInstanceByDom 为 undefined
- [x] EChartsRenderer.renderPng(optionJson, width, height) → byte[]（D19，PNG 编码内置）：Java 侧 Jackson 校验 → Normalizer（D15，Phase 2 实现前透传——记录）→ JS 内 JSON.parse → setOption → drain → getImage → ImageIO 编码
- [x] 单测：中文 bar/line/pie 端到端 PNG byte[]（PNG 魔数 + 解码尺寸/非空白像素断言）；两次渲染 Context 复用（引擎 eval 计数不增长）；坏 JSON → NopChartEchartsException；JS 异常（pre-render 缝 throw 'boom'）→ 包装异常含消息；超时（50ms 超时 + while(true) pre-render 缝）→ render timed out 且下次渲染健康（迟到中断不毒化回归）；红线断言（typeof 四对象 undefined）；**dispose 泄漏回归**（getInstanceByDom undefined）；缺字体报错（自定义 mapper 空家族）

Exit Criteria:

- [x] `./mvnw test -pl nop-format/nop-chart-echarts -am` 全绿（canvas2d 131 不回归）
- [x] **端到端验证**：optionJson 入口 → GraalJS → shim → nop-canvas2d → BufferedImage PNG 全链路（非手工造图）
- [x] **接线验证**：EChartsRenderer 经 EchartsEngine 的 Context 真实执行 dist/echarts.js（eval 计数断言 + 渲染产物断言双证）
- [x] 裸环境红线断言在测试内成立
- [x] No owner-doc update required（WI10）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - SVG 路径、OptionNormalizer 与图像桥

Status: completed
Targets: nop-format/nop-chart-echarts

- Item Types: `Feature` + `Proof`

- [x] SVG spike（D16）：裸环境 ssr:true init + renderToSVGString 可行性验证；失败则显式降级裁定（UnsupportedOperationException + 记录），成功则落地
- [x] EChartsRenderer.renderSvg(optionJson, width, height)：SVG 字符串（<svg 根、含图形 path、无 @keyframes——cssAnimation:false）；renderPng/renderSvg 双方法共享引擎
- [x] OptionNormalizer（D15 清单与修正理由）：Java 侧 Jackson 校验+剥离+重序列化；剥离断言（toolbox 消失/legend 保留/原始 option 不可变——深拷贝语义）
- [x] drawImage fake canvas 桥（D17）+ getImageData/putImageData 桥测试（heatmap 路径准备：JS 侧全画布读取 → Java 像素一致）
- [x] 单测：SVG 断言（<svg、</svg>、path 元素存在、width/height 属性）；Normalizer 剥离（toolbox 键消失、legend 保留、原 option 不可变）；fake canvas drawImage（前置画布 → 主画布合成像素）；getImageData 像素值与 Java 侧一致

Exit Criteria:

- [x] `./mvnw test -pl nop-format/nop-chart-echarts -am` 全绿（Phase 1 不回归）
- [x] SVG 路径 spike 结论落地（成功实现或显式降级裁定），不得静默空实现
- [x] Normalizer 深拷贝语义有测试
- [x] No owner-doc update required（WI10）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 引擎/桥/Normalizer/Renderer 全部落地且有 focused tests
- [x] 无空方法体/占位实现
- [x] 裸环境红线（无 window/document/navigator/self、不用 canvas ssr）在引擎层成立
- [x] 超时与异常错误面有直接测试
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] No owner-doc update required（WI10 统一收口）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：端到端 optionJson→PNG 链路真实连通（eval 计数 + 像素断言双证）；SVG 路径真实产出（若降级则有显式裁定记录）
- [x] `./mvnw test -pl nop-format/nop-chart-echarts -am` 通过
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-format/nop-chart-echarts --severity high` 退出码 0（本计划触及 src/main，有实质检测力）
- [x] checkstyle / 代码规范检查
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

（暂无）

## Non-Blocking Follow-ups

- Context 并发压测与池化调优归 WI5（渲染吞吐观测后立项）。
- loadImage 实际图像加载（网络/dataURL）如 WI5 对版需要，由其裁定立项。

## Closure

Status Note: 引擎核心全量落地（Context 池/平台注入/timer 泵/超时错误面/Normalizer/renderPng byte[]/renderSvg/图像桥）；D13-D24 裁定逐项落地（audit 源码亲核 22 项属实）。独立 closure audit 有条件通过——修复轮完成全部条件（M1 测试缝改包私有 + M2 平坦性断言 + D24g measureText 平台注入补齐 + 死代码清理 + import 顺序 + configure finally 恢复）。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session 子代理（general-purpose）
- Audit Session: agent_9e8f7fa1-c6da-4e07-8249-66b7cc6d006c
- Evidence:
  - Phase 1 Exit Criteria 6/6 PASS；Phase 2 Exit Criteria 5/5 PASS（audit 亲跑 -am：chart-echarts 17/17 + canvas2d 132/132；端到端 PNG 魔数+解码+柱色>300+中文墨迹；SVG 无 @keyframes）
  - Closure Gates 12/12 PASS（scan-hollow 4 src/main 文件 0 发现；check-plan-checklist/check-doc-links 亲跑退出码 0；规范抽查 3 处 import 瑕疵修复轮已修）
  - Anti-Hollow 双证：eval 计数平坦性断言（修复轮改为不依赖测试顺序）+ 渲染产物像素断言
  - D13-D24 逐条源码核查：22 项属实；2 项偏差（D14 缝 public 违规、D24g measureText 未注入）在修复轮全部修正（3 参公共重载 + 4 参包私有缝；shim __bridgeSetupPlatform 注入 measureText）
  - D21 dispose：finally 双路径 dispose；泄漏断言经清理标记 + 再渲染复用验证（getInstanceByDom 对 fake canvas root 抛 TypeError 的原因已记录于测试注释）
  - audit 发现的 Minor（死代码 beginRender/getRootCanvas/getTimerCancelled/CanvasBindings 清理、configure finally 恢复、import 顺序、日志分桶）已全部处理
  - 修复轮后 `./mvnw test -pl nop-format/nop-chart-echarts` 17/17 全绿

Follow-up:

- Context 并发压测、loadImage 实际加载归 WI5（记录于 Non-Blocking Follow-ups）。
