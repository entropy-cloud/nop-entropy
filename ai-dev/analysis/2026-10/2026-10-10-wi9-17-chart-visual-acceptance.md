# 2026-10-10 WI9 17 图型对版验收报告

> Plan: ai-dev/plans/2367-wi9-17-chart-visual-acceptance.md
> 渲染通道：test-all-charts.xlsx（17 sheet/17 图型）→ ChartModel → ECharts option 适配器 → GraalJS 引擎 → PNG（1000x750，白底 #FFFFFF）
> 基线：docs/dev-guide/report/excel-chart/ 17 张 Excel 原生截图
> 渲染产物证据：ai-dev/analysis/2026-10/wi9-render/（17 张，D53 映射命名）
> 判定方式：AI vision 独立子代理逐图判定（D54），实现者不自查；判定员 agent_78318653/e562b8e1（首轮）、agent_07d18b9b（复审轮）、agent_5d804679（终审轮）

## 判定口径

结构语义五项（任一不符 = FAIL，回灌修复）：图型一致 / 系列数一致 / 数据形状可辨识一致 / 图例按模型出现 / 坐标轴按模型出现。数据标签按模型语义（fixture dLbls 旗标全 0 = 模型隐藏，渲染侧出现即 FAIL）。样式差异（配色/刻度格式/网格/标记形态/外框/辐条方向）记录不阻断（roadmap「尽量贴近」）。

## D53 对版映射表（渲染序 = workbook.xml 序，标题锚 17/17 MATCH）

| idx | fixture sheet | 图型/grouping | Excel 截图 | 终审判定 |
|-----|--------------|---------------|-----------|---------|
| 0 | Chart_填充雷达图 | radar filled 3系 | filled-radar-chart.png | PASS |
| 1 | Chart_雷达图 | radar marker(symbol none→线形) 3系 | radar-chart.png | PASS |
| 2 | Chart_散点连线图 | scatter lineMarker 线有色 | scatter-with-lines-chart.png | PASS |
| 3 | Chart_散点图 | scatter lineMarker 线 noFill（纯点） | scatter-chart.png | PASS |
| 4 | Chart_圆环图 | doughnut legend bottom | doughnut-chart.png | PASS |
| 5 | Chart_饼图 | pie legend bottom | pie-chart.png | PASS |
| 6 | Chart_百分比堆积面积图 | area percentStacked | 100pct-stacked-area-chart.png | PASS |
| 7 | Chart_堆积面积图 | area stacked | stacked-area-chart.png | PASS |
| 8 | Chart_面积图 | area standard | area-chart.png | PASS |
| 9 | Chart_带标记折线图 | line+marker | line-with-markers-chart.png | PASS |
| 10 | Chart_折线图 | line | line-chart.png | PASS |
| 11 | Chart_百分比堆积条形图 | bar percentStacked dir=bar | 100pct-stacked-bar-chart.png | PASS |
| 12 | Chart_堆积条形图 | bar stacked dir=bar | stacked-bar-chart.png | PASS |
| 13 | Chart_簇状条形图 | bar clustered dir=bar | clustered-bar-chart.png | PASS |
| 14 | Chart_百分比堆积柱形图 | bar percentStacked dir=col | 100pct-stacked-column-chart.png | PASS |
| 15 | Chart_堆积柱形图 | bar stacked dir=col | stacked-column-chart.png | PASS |
| 16 | Chart_簇状柱形图 | bar clustered dir=col | clustered-column-chart.png | PASS |

## 验收轮次与回灌修复记录

**首轮判定**（渲染于 WI6 适配器 + WI8 产物基线）：PASS 4/17，FAIL 13 项缺陷，归为 6 个根因族。全部按 D55 回灌修复：

| # | 根因 | 修复 | 复验 |
|---|------|------|------|
| 1 | **数据标签无条件显示**：dLbls 旗标全 0（模型隐藏）时 label.show 仍 true，17 图全部多出 Excel 没有的标签 | EchartsStyleMapper.label 可见性 = 各 show 旗标 OR（全 0 → show=false），针对性断言钉住 | 首轮修复后即消（终审 17 图无多余标签） |
| 2 | **雷达 per-spoke max 归一化**：每辐条独立 max 使各系列在所有辐条上都近满值，数据形状退化为正多边形 | 全辐条共享全局 max（全部系列值最大值 ×1.1） | 复审轮 RESOLVED，终审 PASS（双峰风筝形与 Excel 同构） |
| 3 | **散点 marker 白色 fill 不可见**：fixture spPr solidFill white 在白底渲染不可见（散点 0 可见点） | line/scatter 族白 fill 丢弃 → ECharts 调色板着色（Excel 视觉 = 主题色实心标记） | 复审轮 RESOLVED，终审 PASS（10 点全可见） |
| 4 | **折线全白不可见**：同白色 fill 根因族（line 系列 itemStyle.color 白 → 折线白） | 同修复 3 | 复审轮 RESOLVED，终审 PASS（W 形三线清晰） |
| 5 | **圆环零宽不可见**：inner=holeSize%、outer=75%，fixture holeSize=75 → inner==outer | holeSize 语义改为外径的百分比（inner = outer × holeSize/100） | 复审轮 RESOLVED，终审 PASS（环形完整） |
| 6 | **柱/条 1px 发丝化**：Excel gapWidth（gap 占单柱宽 %，柱宽=band/(n+g)）被直接透传为 ECharts barCategoryGap（gap 占带宽 %，柱宽=band×(1−G)/n）→ 负宽钳 1px | 语义换算 G = g/(n_eff+g)（stacked n_eff=1，clustered n_eff=系列数） | 终审 PASS（柱/条正常宽度、簇状并列可数） |

**复审轮新增 FAIL 1 项并回灌**：

| # | 缺陷 | 修复 | 复验 |
|---|------|------|------|
| 7 | **散点图连线语义**：散点图（线 a:noFill）渲染出连接折线，散点连线图（线 solidFill）与纯点散点未区分 | 系列 spPr a:ln 解析为 shapeStyle.border——border.noFill → lineStyle.opacity=0（隐藏连线）；border 有色 → 连线取其色/宽 | 终审 RESOLVED（idx3 连线保留、idx4 纯点） |

**终审轮：17/17 结构语义全 PASS**（判定员 agent_5d804679，逐图五项判定 + 标题锚 17/17 MATCH）。

## 样式差异清单（不阻断，roadmap「尽量贴近」口径）

- 配色方案：渲染调色板 vs Excel 主题色（系列色映射差异，结构无影响）
- 值轴范围取整：渲染 0–1500/5000 vs Excel 0–1600/4500；百分比轴无 % 号（0–100 vs 0%–100%）
- 渲染侧面积/折线系列带数据点标记（Excel 按模型 marker=none 无标记的部分图仍有小标记——marker 配置映射的剩余差异）
- 面积未延伸至绘图区左右边缘（类目轴 band 语义差异）
- 雷达辐条起始方向与 Excel 镜像；横向簇状条形组内系列排列方向与 Excel 相反（Excel 条形图默认系列反序）
- 雷达无径向刻度数字；网格线密度/深浅；无绘图区外框
- **已知渲染瑕疵（跟踪项）**：圆环图扇区几何畸形（内缘楔形切口、色楔伸入内圈；饼图无此缺陷，问题特定于 innerRadius 路径几何）——数据形状与比例完全可辨识，不阻断；建议后续排查 zrender 圆环 path 或 innerRadius 传参

## 结论

17/17 图型结构语义对版 PASS（含 6 个根因族 + 1 个散点线语义共 7 项适配器缺陷回灌修复并复验）。样式差异已列明，圆环扇区几何瑕疵作为已知跟踪项。本验收达成 roadmap WI9 的「结构语义必须一致、样式尽量贴近」标准。

## 回归

回灌修复后：nop-chart-export 33/33 绿（含数据标签隐藏新断言、fixture 17 charts E2E + 数据形状强断言）；pdf/docx 腿与 html 腿复验在收口 gate 执行（见 plan Closure）。
