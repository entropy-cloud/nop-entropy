# 2361 WI3 nop-canvas2d 渐变/图案/阴影/图像

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: ai-dev/backlog/echarts-server-render-roadmap.md WI3（M1）；依赖 plan 2360（WI2 已完成，commit d89a2c2061）
> Related: WI4 引擎 shim 消费 createPattern/getImageData/putImageData；WI5 heatmap/visualMap 出图依赖图像数据面

## Purpose

在 nop-canvas2d 上完成 roadmap WI3 范围：线性/径向渐变（用户空间坐标语义）、图案（createPattern + 变换矩阵 + repetition）、阴影（shadowBlur/Color/OffsetX/Y 离屏合成）、drawImage 三形态、getImageData/putImageData/copyFrom、createCanvas 离屏工厂，全部配 focused tests。

## Current Baseline

以下事实已于 2026-10-10 在 live repo（分支 echarts-server-render @ d89a2c2061）核验：

- nop-canvas2d 已有 WI1 状态机/路径 + WI2 文本子系统（93 测试全绿）；D1-D8 裁定体系有效；ICanvas2DContext 追加式生长（2359 预留）。
- zrender 调用面实测（dist 6.1.0）：createPattern(image, pattern.repeat || 'repeat')（repetition 缺省 'repeat'）；getImageData(0, 0, canvas.width, canvas.height) 全画布读取 + putImageData(imageData, 0, 0)（heatmap 路径，dist:69625-69653）；**copyFrom 在 dist 全文 0 命中**——roadmap「copyFrom（heatmap 依赖）」与 echarts 6.1.0 dist 实测不符，按 roadmap 硬边界保留该 API（canvas 间像素复制），消费者如实标注（heatmap 自定义层/后续证实不需要时由消费者 WI 裁定移除）。
- 渐变创建形态（WI0 spike 与 dist 一致）：zrender 经 ctx.createLinearGradient/createRadialGradient + addColorStop 构建，fillStyle 赋值为渐变对象——本模块 API 为方法形态（setFillStyleGradient(ICanvasGradient)），JS 侧由 shim 翻译（WI0 interop 约束：数据形态值走纯 JS 对象，Java 对象仅方法调用后端——gradient 对象即方法调用后端，shim 包一层 JS 对象暴露 addColorStop）。
- Java2D 渐变坐标语义：LinearGradientPaint/RadialGradientPaint 坐标在绘制时的用户空间解释（g.setTransform(current) 后 fill）——与 Canvas「渐变坐标在使用时用户空间」语义天然对齐（两侧都是绘制时用户空间解释——探针 P1 实证，与调用顺序无关）。
- 阴影：Java2D 无原生 canvas 阴影——需离屏合成（形状按 shadowColor 绘制到离屏层 → 卷积模糊 → 偏移合成 → 再绘本体）；σ=blur/2，blur 在设备空间（见 D11）。
- **【审查发现的 WI1 规范分歧，本计划内修复（Fix）】**：现有 applyState 每次绘制以当前用户空间重设 clip——clip 后 translate 会使裁剪区随新变换移动（审查探针 ProbeClip 实证：device(110,25) alpha=255，规范应为 0）。canvas 规范 clip 固定于设备空间。修复：clip() 时把路径经当前 CTM 变换为设备空间形状存储，applyState 以设备空间 setClip（不再受后续 transform 影响）。
- WI1 D2 抛错裁定、D4 像素断言纪律、CtxState 快照机制均就绪；shadow 参数需入快照（WI1 Non-Blocking Follow-ups 已预留此扩展归属 WI3）。
- 根 pom maven.compiler.release=17。

## Goals

- 渐变：ICanvasGradient（addColorStop）+ createLinearGradient/createRadialGradient；用户空间坐标语义；径向渐变以内圆为焦点近似（RadialGradientPaint 模型差异记录）。
- 图案：ICanvasPattern + createPattern(BufferedImage, repetition)（repetition 缺省 repeat，dist:27336）；四态 repetition（repeat/repeat-x/repeat-y/no-repeat）；setFillStylePattern/setStrokeStylePattern 挂载；图案锚定用户空间原点、随当前变换（自定义 Paint Raster，须自行消费 createContext 传入的 xform——探针 P4 实证 TexturePaint 恒双向平铺不可复用）；**pattern.setTransform 不实现**——zrender 在 typeof DOMMatrix === function 且 canvasPattern.setTransform 守卫下才调用（dist:27337-27345），裸环境无 DOMMatrix 分支必然跳过，记录为安全缺位；源图在 createPattern 时**拷贝快照**（规范：创建后修改源图不影响 pattern）。
- 阴影：setShadowBlur/setShadowColor/setShadowOffsetX/setShadowOffsetY 状态 + shapes/text/images 三类绘制的真实离屏合成（作用面见 D11）（offset 移位、blur 扩散、shadowColor 着色、globalAlpha 作用于阴影）；shadow 参数入 save/restore 快照。
- 图像：drawImage 三形态（(img,dx,dy) / (img,dx,dy,dw,dh) / (img,sx,sy,sw,sh,dx,dy,dw,dh)）；getImageData/putImageData（含 dirty-rect 变体）；ICanvas2D.copyFrom；Canvas2DFactory.create 离屏画布工厂。
- 单测：渐变方向与端色、径向中心与边缘、pattern 平铺与四态、阴影位置/扩散/着色、drawImage 三形态映射、图像数据往返、快照恢复。

## Non-Goals

- GraalJS shim（WI4）；golden（WI5）；owner doc（WI10）。
- 高斯模糊的性能够优化（首次实现用卷积近似，质量以像素断言可观测为准；性能归 WI5/WI4 观测后优化）。
- Pattern 的 DOM Image/Canvas 元素源（本模块只接受 BufferedImage；JS Image 由 WI4 shim 转换）。
- SVG filter/DOM 滤镜语义（只实现 canvas shadow API 面）。

## Adjudications（延续 D 编号）

- **D9 渐变**：addColorStop offset 必须有限且 ∈ [0,1]（越界抛 NopCanvas2DException，与规范 IndexSizeError 对齐）；stops 按 offset 稳定排序；**退化渐变（linear x0=x1,y0=y1 或 radial 同心 r0=r1）与无 stop 渐变：fill/stroke 绘制为空（透明、无异常）——与浏览器 paint-nothing 语义一致，属 D2 的规范对齐例外**（Java2D Paint 构造器对退化参数抛 IllegalArgumentException，实现须先检测退化并短路）；CycleMethod 用 NO_CYCLE（canvas 越端取端点色 = pad）。**径向渐变等价性收窄**：r0=0（zrender 唯一调用形态，dist:27228 恒为同心 r0=0）时 Java2D 焦点模型与 canvas 双圆模型**精确等价**（审查探针 P3 实证），r0>0 时内圆区域非纯色——记录为近似并加 r0=0 精确性测试；渐变坐标不烘焙变换（绘制时用户空间解释，探针 P1 实证 Java2D 天然一致）。
- **D10 图案**：image 参数 null 抛错；repetition 非法值抛错；no-repeat 在铺贴区外透明；图案坐标锚定用户空间原点并随 CTM（绘制时语义）。空 BufferedImage（0 宽高）抛错。
- **D11 阴影**（按 WHATWG/W3C REC 2dcontext 绘图模型修正）：**shapes/text/images 三类绘制均受阴影影响（含 drawImage）**；shadowOffsetX/Y 与 shadowBlur **在设备空间解释、不受 CTM 影响**（规范明文 not affected by the current transformation matrix，σ=blur/2）——合成实现必须：轮廓按 CTM 绘制、blur 在设备空间卷积、offset 在设备空间平移；shadowBlur<0 抛错；shadowColor 经 CssColors 解析（非法串抛错）；阴影激活条件 = shadowColor 的 alpha 分量非零 且 (blur>0 或 offsetX/Y≠0)（规范恒真条件，零阴影零开销直绘）；阴影受 clip 约束、受 globalAlpha 影响、不由 fillStyle 着色（用 shadowColor）；shadow 不作用于 putImageData/getImageData（像素直写）。
- **D12 图像**：drawImage image 参数 null 抛错；dw/dh 缺省为图像原始宽高；**源矩形越界 = 与图像求交 + 目标矩形同比例收缩（不补边）**；sw/sh 为 0 → no-op；负 sw/sh → 抛错（**deliberate divergence**：现行规范归一化为镜像翻转源，zrender 不产负值，按 D2 哲学快失败）；getImageData 的矩形越界部分返回透明像素（规范语义）；**putImageData 整块替换（wholesale 含 alpha 无混合）**——直写 raster 或 AlphaComposite.Src，绕过变换/clip/alpha/阴影；负 dirty rect 参数抛错（deliberate divergence，规范归一化）；IImageData 契约 = **int[] non-premultiplied ARGB**（TYPE_INT_ARGB getRGB 直读往返无损；WI4 桥接 JS ImageData 的 byte[] RGBA 转换在桥接层做）；copyFrom 要求源目标同尺寸（不同尺寸抛错）。

## Scope

### In Scope

- nop-format/nop-canvas2d：ICanvasGradient/ICanvasPattern/渐变实现/PatternPaint/阴影合成/drawImage/IImageData/getImageData/putImageData/copyFrom/Canvas2DFactory；ICanvas2DContext 追加方法；CtxState 快照扩展。
- ai-dev/plans/、ai-dev/logs/。

### Out Of Scope

- shim/引擎（WI4）；golden（WI5）；owner doc（WI10）；DOM 图像模型。

## Execution Plan

### Phase 1 - 渐变与图案

Status: completed
Targets: nop-format/nop-canvas2d

- Item Types: `Feature`

- [x] ICanvasGradient + 渐变实现（Linear/Radial，stops 排序/校验 per D9）+ setFillStyleGradient/setStrokeStyleGradient + createLinearGradient/createRadialGradient
- [x] ICanvasPattern + PatternPaint（自定义 Paint Raster：repeat 双向平铺/repeat-x/repeat-y/no-repeat 裁剪 per D10）+ createPattern + setFillStylePattern/setStrokeStylePattern
- [x] Canvas2DFactory.create(w,h) 离屏工厂 + ICanvas2D.copyFrom（同尺寸校验 per D12）
- [x] clip 设备空间修复（见 Baseline 分歧条目，Item Type: `Fix`）：clip() 存储设备空间形状 + applyState 不再随 transform 重设 clip
- [x] 单测：线性渐变两端色与方向像素断言、径向中心/边缘色与 r0=0 精确性、addColorStop 越界抛错、**退化渐变与无 stop 渐变绘制为空（不抛错）**、渐变随 CTM（变换下渐变锚点随用户空间，探针 P1 场景）、pattern 平铺周期、四态 repetition 像素反差、pattern 随 translate、copyFrom 往返、工厂产物可绘制、**clip 后 translate 设备空间反差（Fix 回归钉）**

Exit Criteria:

- [x] `./mvnw test -pl nop-format/nop-canvas2d -am` 全绿（93 测试不回归）
- [x] 像素断言遵守 D4（布尔覆盖、≥2px 边距、无整图计数）
- [x] D9/D10/D12 裁定的抛错分支有直接测试
- [x] No owner-doc update required（WI10）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 阴影与图像数据

Status: completed
Targets: nop-format/nop-canvas2d

- Item Types: `Feature` + `Proof`

- [x] shadow 状态（setShadowBlur/Color/OffsetX/Y，校验 per D11）+ CtxState 快照扩展 + 离屏合成路径（激活条件、模糊卷积、着色、偏移、alpha 复合）
- [x] drawImage 三形态（D12 校验/裁剪）+ getImageData/putImageData（含 dirty-rect）+ IImageData
- [x] 单测：阴影 offset 位移（墨迹出现在形状+offset 处）、**scale CTM 下 offset/blur 像素位移不变（B2 回归钉）**、blur 扩散（采样距边界 ≥2σ，σ=blur/2）、shadowColor 着色（阴影像素色 ≈ shadowColor）、透明 shadowColor 不激活（与零阴影等价）、零阴影直绘等价、快照恢复 shadow、**fillText/strokeText 阴影（M7）**、**drawImage 受阴影影响（B1 修正）**、drawImage 三形态像素映射（含源矩形求交同比例收缩断言，M4 语义）、getImageData 往返一致、putImageData 半透明像素覆盖不透明底读回精确相等（M5 SRC 语义）+ 落点与绕过 clip/alpha/阴影 的裁定断言、负 dirty rect 抛错、越界矩形透明补边（getImageData 侧）
- [x] spike sanity：阴影行为与浏览器语义对照以规范文本为准（spike 无阴影实现，无从对照——记录于测试注释）

Exit Criteria:

- [x] `./mvnw test -pl nop-format/nop-canvas2d -am` 全绿（Phase 1 不回归）
- [x] D11/D12 抛错与裁剪分支有直接测试
- [x] 像素断言遵守 D4
- [x] No owner-doc update required（WI10）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] WI3 范围 API 全部落地且有 focused tests（渐变/图案/阴影/drawImage/图像数据/copyFrom/工厂）
- [x] 无空方法体/占位实现
- [x] D9-D12 裁定抛错/裁剪分支有直接测试
- [x] 像素断言遵守 D4 纪律
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] No owner-doc update required（WI10 统一收口）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：渐变/图案/阴影/drawImage 真实写像素（像素断言）；getImageData/putImageData 真实读写数据（往返测试）；零阴影路径真实直绘（等价断言）
- [x] `./mvnw test -pl nop-format/nop-canvas2d -am` 通过
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-format/nop-canvas2d --severity high` 退出码 0
- [x] checkstyle / 代码规范检查
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

（暂无）

## Non-Blocking Follow-ups

- copyFrom 的消费者裁定：dist 0 命中（Baseline），WI4/WI5 若证实 heatmap 路径不需要，由该 WI 显式裁定移除或保留为公共 API。
- 阴影模糊算法质量/性能优化（WI4/WI5 观测后立项）。

## Closure

Status Note: WI3 全量落地（渐变/图案/阴影/drawImage/图像数据/copyFrom/工厂）+ clip 设备空间修复；D9-D12 裁定逐项落地（audit 源码逐行亲核含阴影设备空间三要素、三类作用面、putImageData wholesale、同比例收缩数学）。独立 closure audit 有条件通过——修复轮完成全部条件（Major-1 drawImage 阴影分支丢 globalAlpha 的 live defect 一行修复 + 回归钉；Major-2 六项覆盖缺口补 Java2DCanvasWi3CoverageTest 8 用例；Minor-1 blur 尾部越 clip 合成修复；Minor-2 repeat-y 测试）。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session 子代理（general-purpose）
- Audit Session: agent_57dc5b2d-8c5e-44c9-ae1c-945c09e8d908
- Evidence:
  - Phase 1 Exit Criteria 5/5 PASS；Phase 2 Exit Criteria 5/5 PASS（audit 亲跑 -am 123/123；D4 布尔断言抽查；D9-D12 抛错/裁剪分支测试在位）
  - Closure Gates 12/12 PASS（修复轮后；scan-hollow 20 文件 0 发现；pmd -Pqa 零违规；check-plan-checklist/check-doc-links 退出码 0）
  - Anti-Hollow：渐变端点/图案平铺/阴影位移扩散着色/drawImage 真实像素断言；getImageData/putImageData 真实往返；transparentShadowColorIsInert 逐像素等价；PatternPaint 真实逆变换 raster 采样
  - D9-D12 源码逐行核查：D9 短路绘制为空/NO_CYCLE/越界抛错；D10 快照拷贝/四态（快照与 repeat-y 在修复轮补测）；D11 三要素（blur 设备空间卷积 σ=blur/2、offset 设备空间平移、clip 烘入）+ 三类作用面（fill/stroke/fillRect/strokeRect/fillText/strokeText 走 paintWithShadow，drawImage 走等价专属分支）+ 可用性顺序；D12 同比例收缩数学逐行核对/wholesale 直写/负 dirty 抛错
  - clip 设备空间修复：四处 setClip 全部 identity 先行（审查实现时发现的 setClip 烘焙 CTM 隐患在同轮修复）
  - audit 发现与修复：Major-1（drawImageCropped 阴影分支缺 applyGraphicsState 导致 globalAlpha 对本体失效——探针实证 alpha 255 应为 77，一行修复 + drawImageShadowRespectsGlobalAlphaOnImage 回归钉）；Major-2（setStrokeStyleGradient/setStrokeStylePattern/setShadowOffsetY/快照/工厂重载零测试——补 8 用例）；Minor-1（blur 尾部越 clip——compositeShadow 保持 setClip(savedClip) 修复 + 回归钉）；Minor-2（repeat-y 测试）；Minor-3（plan spike 措辞与日志分桶修正）
  - 修复轮后 `./mvnw test -pl nop-format/nop-canvas2d` 131/131 全绿

Follow-up:

- copyFrom 消费者裁定、阴影模糊性能优化归 WI4/WI5（记录于 Non-Blocking Follow-ups）。
