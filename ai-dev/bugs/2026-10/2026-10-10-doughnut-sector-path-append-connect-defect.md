# 2026-10-10 Doughnut Sector Geometry Broken By Path Append Connect Defect

## Problem

- WI9 对版验收的圆环图（doughnut）渲染产物明显畸形：扇区内缘有楔形白色切口、色楔伸出圆环内孔进入中孔区域、内缘非圆弧（呈现多边形弦线轮廓）。
- 饼图（pie，r0=0）同链路渲染完全正常；仅带内半径（r0>0）的圆环坏。
- 复现最小化：经引擎渲染任意 ≥2 扇区的 doughnut option（`{"series":[{"type":"pie","radius":["56.25%","75%"],"data":[...5 项...]}]}`）即稳定复现；极坐标 alpha 网格显示内缘 r58-64 区间有楔形空洞、r50 有伸入孔内的杂散填充。

## Diagnostic Method

定位困难的根源：**三个直觉假设全部错误，且单弧测试全绿造成了系统性盲区**。最终靠「源码精读 + 顶点级实证 + 极坐标网格」三件套收敛。

- **黑盒探针绕圈（教训）**：先在 shim 层做像素采样探针，连续两轮采样点象限算错（canvas 角度 [−90°,0°) 在 y 向下坐标系是**上半区**，两次都采样了下半区 (142,142)），得出「原代码正确」的假阴性结论。被用户纠偏「严格对比 ECharts 实现代码 / 查 Arc2D 源码」后转入正道。
- **被排除的假设 1——zrender 发弧错误**：经引擎 pre-render 缝捕获 zrender 真实 arc 调用序列：外弧 `arc(r75, 4.71→6.72, false)` + 内弧 `arc(r56.25, 0.43→−1.57, true)`，角度与扇区值占比精确一致；解包 mvnpm jar 读 `buildPath$1` 源码确认非整圆扇区就是发普通 `ctx.arc` 序列。zrender 无辜。
- **被排除的假设 2——Arc2D 负角度翻译错误**：一度怀疑「Arc2D 行为不自洽」（同参数不同结果），并尝试改为不取负号的角度直传——导致 WI1 方向测试 3 例回归，回滚。用户再次纠偏后下载 OpenJDK 源码（ArcIterator.java）精读。
- **被排除的假设 3——polyline 替换方案**：曾考虑用折线近似弧绕开 Arc2D，属规避而非修复，未采纳。
- **决定性证据链**：① 顶点级实证——直接打印两个弧经原始翻译（`Arc2D(−start, −sweep)`，对齐 ArcIterator 参数化 `angStRad=−start`、`increment=−extent/n`）产生的路径顶点：外弧 MOVE(99.8,25)→CUBIC→(168,131.7)（12 点顺时针到 4 点）、内弧 MOVE(168.2,131.3)→CUBIC→(100.1,25)（4 点回扫 12 点）——**弧构造完全正确**；② Java 层同序列渲染仍出月牙 → 问题锁定在弧构造之后的路径拼接；③ `path.append(segment, connect=false)` 使 ArcIterator 的 SEG_MOVETO 另起子路径——第二弧脱离当前子路径，closePath 只闭合最后一个子路径。

## Root Cause

- `Java2DCanvas.appendEllipticalArc` 以 `path.append(segment, connect=false)` 追加 Arc2D。ArcIterator 以 SEG_MOVETO 开头，connect=false 使第二弧成为**独立子路径**；`closePath()` 只闭合最后一个子路径。
- fill（WIND_NON_ZERO）因此退化为「外弧+弦」弓形 ∪「内弧+弦」细弓形的并集——不是圆环扇区。单弧（饼图）只有一个 arc 调用不受影响；多弧扇区（圆环图内弧回扫）必坏。
- WI1 的方向语义测试（quarter/degenerate/wrap）全部使用 **start=0° 的单弧**——start=0 时负号镜像与子路径缺陷均不可见，测试全绿掩盖了两个缺陷（另一隐藏缺陷：负 start 角镜像导致扇区位置偏差，本次一并修复暴露）。

## Fix

- `appendEllipticalArc` 的 `path.append(segment, connect=false)` 改为 **connect = pathSegments > 0**（有当前子路径时以直线并入当前子路径——等价 canvas 的「当前点连到弧起点」语义；路径为空时 GeneralPath 自动忽略 connect，保持 SEG_MOVETO）。
- rotation≠0 分支同步改为 `append(local, connect)`。
- 效果：圆环扇区的两弧并入同一子路径，closePath 正确闭合环带；同时消除了负 start 角镜像（修复前扇区顺序/位置与 Excel 有镜像偏差，修复后圆环扇区自 12 点顺时针与 Excel 同序）。

## Tests

- `nop-format/nop-canvas2d/src/test/java/io/nop/canvas2d/Java2DCanvasAnnularSectorTest.java` — 三层钉：①单扇区（zrender 实测角度）带内三点填充/孔与带外透明；②4 象限铺环 + 孔心透明；③zrender 真实角度扇区带中点 (160,100)/(142,58) 填充 + 孔/span 外透明——connect 缺陷复现时 (160,100) 即空洞。
- `nop-format/nop-chart-echarts/src/test/java/io/nop/chart/echarts/engine/EchartsBridgeTest.java` 的 annularSectorViaShimFillsFullBand 用例 — shim 级（JS→Java 全链）同序列断言，锁死 JS 翻译层。
- 既有 134 canvas2d 测试 + 37 chart-echarts 测试 + 33 chart-export 测试全绿；EchartsFullTypeMatrixTest 的 heatmap golden 按 D26 机制重生成（弧渲染输出随修复变化，属预期）。

## Affected Files

- `nop-format/nop-canvas2d/src/main/java/io/nop/canvas2d/Java2DCanvas.java`（appendEllipticalArc 的 append connect 语义 + 注释更正）
- `nop-format/nop-canvas2d/src/test/java/io/nop/canvas2d/Java2DCanvasAnnularSectorTest.java`（新增回归）
- `nop-format/nop-chart-echarts/src/test/java/io/nop/chart/echarts/engine/EchartsBridgeTest.java`（新增 shim 级回归）
- `nop-format/nop-chart-echarts/src/test/resources/golden/`（matrix goldens 重生成）

## Notes For Future Refactors

- **canvas 弧的像素级回归钉必须覆盖「多弧同一子路径」场景**：单弧测试对 connect/子路径缺陷完全免疫，绿不代表多弧正确。
- **勿按直觉建模 Arc2D 角度语义**：OpenJDK ArcIterator 的参数化是「θ 从 −angleStart 沿 −extent 递增、点 = center + (r·cos θ, r·sin θ)」，直觉推导连续两次翻车；改动前读 JDK 自带的 src.zip 源码（java.desktop/java/awt/geom/ArcIterator.java，JDK lib 下解包）。
- **「样式差异不阻断」归类前先排除路径拼接类 bug**：结构可辨识 ≠ 渲染正确；视觉畸形若能以确定性几何描述（楔形切口/弦线）复现，应按 bug 修复而非记录跟踪。
- shim 的 JS `ctx.arc` 是纯透传（`B().arc(Number(x),...)`），JS 层排查可跳过，直接看 Java `appendEllipticalArc`。
