# HTML 分页打印保真 设计

**范围**：`nop-format/nop-excel`（`HtmlReportRendererFactory`/`SimpleHtmlReportRendererFactory` 输出层）
**状态**：草案（规范性文档；实施编排见 `ai-dev/backlog/nop-report-capability-roadmap.md`）

> 本档为规范性文档：只描述当前有效设计；变更历史见 git 与 AI 开发记录区的时效性文档。

---

## 一、设计结论

1. HTML 输出的分页打印保真走**浏览器打印语境**：`@page` 规则 + 分页 CSS + 重复表头/关键列的 DOM 结构，由浏览器打印引擎消费；不在服务端做 HTML→分页服务端排版。
2. 语义与 PDF 渲染器既有分页配置**同名同义**：`nop.report.pdf.repeat-header-rows`（默认 1）、`nop.report.pdf.repeat-key-columns`（默认 1）的语义在 HTML 侧等价落地——续页重复表格表头行、列拆分（横向溢出页）重复关键列；配置 0 关闭。
3. 页眉页脚以 HTML 等价物提供（`@page` margin boxes 能力不足时降级为每页顶部/底部的固定打印区），支持与 PDF 页脚一致的占位符口径（页码/总页数/报表名）。
4. shtml（简化输出）不承担分页保真；分页契约只约束 html 渲染类型。

## 二、背景与动机

HTML 是屏幕预览与浏览器打印的载体，当前输出为普通表格流：浏览器打印时无页眉页脚、无重复表头、无分页控制，长表打印不可用。PDF 侧分页语义已定型并被业务依赖，HTML 侧对齐同一语义可让「浏览器直接打印」成为 PDF 之外的轻量打印路径。

## 三、核心设计

### 输出契约

- 输出 HTML 内嵌 `<style>`：`@page` 设定页幅/页边距（取模板打印区设置），表格行以打印分页友好的结构输出（`thead` 承载重复表头，浏览器打印时跨页自动重复；列拆分页以并列分页节或 CSS 分列结构表达，关键列在每节重复）。
- 打印与屏幕双形态并存：屏幕预览保持连续表格流（现有行为不回归），打印形态经 `@media print` 与分页容器切换；提供分页预览容器（按 `@page` 尺寸分节的视觉预览）。
- 页眉页脚占位符在打印形态下每页出现；`&N`（页码）/总页数/报表名等占位符语义与 PDF 页脚一致。
- 套打语义保持：`print=false` 背景图在 `@media print` 中排除（既有行为），与分页形态正交。

### 边界

- 分页的正确性由浏览器打印引擎裁决；服务端不模拟分页结果，仅保证 CSS/DOM 输入正确。验收以 headless 浏览器打印截图为准（见 `html-acceptance-baseline.md`）。
- 分页位置逐行判定依赖浏览器分页算法，不承诺与 PDF 分页逐页一致；承诺的是**语义一致**（重复表头/关键列/页脚占位符在每打印页成立）。
- PDF 输出能力（服务端分页排版/页眉页脚绘制）归 PDF 渲染器设计（`pdf-pagination.md`），本档不重复。

## 四、拒绝了什么

| 替代方案 | 拒绝理由 |
|----------|----------|
| 服务端把 HTML 排版分页（html→pdf 中转） | 与「PDF 是 PDFBox 直渲、HTML 是浏览器渲染」的既有架构冲突；引入第二排版引擎 |
| 仅用 `@page` margin boxes 做页眉页脚 | 主流浏览器支持不完整（margin box 内容生成受限）；降级路径必须有 |
| 分页只做 CSS `page-break` 不重复表头 | 长表续页无表头不可读，等于没解决打印问题 |

## 五、与已有设计的关系

- `pdf-pagination.md`：PDF 分页语义的 canonical 来源；本档复用其配置语义并声明 HTML 等价。
- `html-acceptance-baseline.md`：本档交付物的验收口径来源。
- 套打行为与 `ExcelImage.print=false` 既有三端契约保持正交。
