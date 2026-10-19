# 2026-10-20 PDF 导出保真独立视觉验收（visual-judge 全 golden 复核）

## 测试环境

- commit: b779ee8a69（WI18 收口后）；golden PNG 生成于 2026-10-19 各 WI 测试运行（`_tmp/pdf-golden/wi1..wi12`）
- 验收方式：4 个独立 visual-judge 子代理并行，对 32 张 golden 逐张查看 + 像素探针核实，按逐图保真断言判定
- 背景：此前各 WI 日志记录的"人工抽查 PASS"由执行 agent 自写，无独立评审记录；本次为补齐独立图片识别验收

## 总体结论

32 张图 **25 pass / 7 fail**；7 个 fail 经测试源码与设计裁定逐一对照，**0 个确认产品缺陷**：
3 个为验收断言口径与已裁定语义不符（断言错，非实现错）、2 个为场景构造无法承载视觉断言（真实证据是程序化像素/内容流断言）、1 个为已裁定延后（桩图表）、1 个为单元格裁剪语义内的正常表现。

## 发现清单

| # | 图 | 现象 | 预期(断言) | 实际 | 分诊 | 状态 |
|---|---|------|-----------|------|------|------|
| 1 | wi9 alpha-blend | 红色块不与表格重叠，50% 红叠白底与不透明浅红视觉不可区分 | "半透明可辨" | 场景中色块下方只有白页 | 场景局限：alpha 真实生效的权威证据是 `testAlphaFillBlendsOverBackground` 通道探针（255,127,127 精确混色） | wontfix（程序化断言已钉） |
| 2 | wi11 rich-page-1 | 竖排 "B" 下半被裁（A 完整） | "两字符完整堆叠" | B 越过 20pt 单元格下缘被裁 | 单元格裁剪语义（Excel 同现）；程序化断言钉提取序/Tm 矩阵，非字形完整性 | wontfix（场景改高行可美化，非缺陷） |
| 3 | wi6 geometry | 40x40 方形源图被拉伸为 3:1 填满 120x40pt 锚区 | "保持原始宽高比" | 锚区填充语义 | 断言口径错误：实现为 Excel 兼容的锚区拉伸填充，`testImageAtAnchoredGeometry` 断言的正是锚点几何 | wontfix（裁定语义如此） |
| 4 | wi3 oversized-row p1 | 超页行未在页底切分，整行推到独立页，p1 底部留白 | "按行内换行切分" | 独立成页 + MediaBox 裁切 | 断言口径错误：`testOversizedRowGetsOwnPageWithoutLosingNeighbors` javadoc 写明裁定语义"独立成页（MediaBox 裁切），前后行不丢" | wontfix（裁定语义如此） |
| 5 | wi3 oversized-row p2 | 超页行独立页上只有 R5 标签无行内容 | "行内容延续可读" | 900pt 行内本只有 1 个标签（构造如此），非渲染丢失 | 断言口径错误（同 #4，文本层断言 R0-R11 全在） | wontfix |
| 6 | wi1 print-titles p1 | 标题行无表头样式（无灰底/粗体） | "表头样式" | 白底常规字重，但 p2 顶部与 p1 逐像素一致 | 断言口径错误：Print_Titles 的 feature 是跨页重复（已 pass），样式不在场景内 | wontfix |
| 7 | wi7 stub-chart | 蓝色渐变矩形，无坐标轴/数据图形 | "真图表特征" | 桩 provider 图案 | 已裁定延后：plan 2470 写明 golden 为桩图表页，视觉对版随 echarts provider 另行验收 | wontfix（有 successor 口径） |

## watch-only（不计 fail）

- ~~wi4 page-numbers 三页页脚 "N / 3" 的总页数字符右缘轻微裁切~~ → **已升格确认并修复**：像素测量证实末位 "3" 墨迹 5px vs 首位 8px（右 1/3 被裁）；根因与修复见 `ai-dev/bugs/2026-10/2026-10-20-hf-footer-trailing-glyph-clip.md`，回归测试 `TestPdfHeaderFooterRender.testTrailingPageNumberGlyphNotClipped`，模块 109 测试全绿。状态：fixed。

## 程序化证据对照（load-bearing）

视觉验收不替代、也不弱于以下已存在的自动化断言：alpha 通道精确混色探针、伪粗体 Tr/描边色内容流、旋转 Tm 矩阵系数、逐 run sc 颜色、页码文本提取、锚区几何探针、分页文本断言。本次独立视觉复核与之一致，未发现与程序化证据矛盾的产品缺陷。

## 流程改进

- 结论性"人工抽查 PASS"字样应指明评审者（执行 agent 自查 vs 独立评审）；后续视觉验收统一走 visual-judge 独立门。
