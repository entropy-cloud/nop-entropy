# HTML 分页打印保真 设计

**范围**：`nop-format/nop-excel`（`HtmlReportRendererFactory`/`SimpleHtmlReportRendererFactory` 输出层）
**状态**：active（纵向分页已落地；横向分页/关键列重复为后续交付）

> 本档为规范性文档：只描述当前有效设计；变更历史见 git 与 AI 开发记录区的时效性文档。

---

## 一、设计结论

1. HTML 输出的分页打印保真走**浏览器打印语境**：`@page` 规则 + 分页 CSS + 分页节 DOM 结构，由浏览器打印引擎消费；不在服务端引入 HTML→PDF 式第二排版引擎——分页节由服务端按行高装箱产出，浏览器 page-break 消费。
2. 语义与 PDF 渲染器既有分页配置**同名同义**：`nop.report.pdf.repeat-header-rows`（默认 1）已在 HTML 侧等价落地——续页重复表格表头行；`nop.report.pdf.repeat-key-columns`（默认 1）的 HTML 等价（列拆分横向溢出页重复关键列）为后续交付（见「三、核心设计 > 边界」）；配置 0 关闭的语义一致。
3. 页眉页脚以 HTML 等价物提供（`@page` margin boxes 能力不足时降级为每页顶部/底部的固定打印区），支持与 PDF 页脚一致的占位符口径（页码/总页数/报表名）。
4. shtml（简化输出）不承担分页保真；分页契约只约束 html 渲染类型。

## 二、背景与动机

HTML 是屏幕预览与浏览器打印的载体，当前输出为普通表格流：浏览器打印时无页眉页脚、无重复表头、无分页控制，长表打印不可用。PDF 侧分页语义已定型并被业务依赖，HTML 侧对齐同一语义可让「浏览器直接打印」成为 PDF 之外的轻量打印路径。

## 三、核心设计

### 输出契约

- 输出 HTML 内嵌 `<style>`：`@page` 设定页幅/页边距（取模板打印区设置），表格按服务端装箱输出分页节（`.xpt-page`），表头行进每节 `thead`。
- 打印与屏幕双形态并存：屏幕预览保持连续表格流（现有行为不回归），打印形态经 `@media print` 与分页容器切换；提供分页预览容器（按 `@page` 尺寸分节的视觉预览）。
- 页眉页脚在每页节内出现；`&P`/`&N`/报表名占位符语义与 PDF 页脚一致。
- 交互与响应式（渐进增强）：html 输出内嵌幂等脚本（文档级事件委托：th 列排序、展开层级折叠组切换），消费契约为**文档级解析**（file:// / iframe srcdoc / document.write）；innerHTML 注入不执行 script 属已知限制。`ICellView`/`IRowView` 增 additive 交互访问器（editorId/viewerId/expandGroup/expandGroupHeader，缺省 null/false 输出不变）。viewport meta 由调用方页面负责，渲染片段只输出 `overflow-x:auto` 容器与窄屏媒体查询。
- 套打语义保持：`print=false` 背景图在 `@media print` 中排除（既有行为），与分页形态正交。

### 边界

- 分页采用**服务端按行高装箱 + 浏览器 page-break 消费**：行高以 pt 显式输出，服务端按 `@page` 内容区高度把行装箱为分页节（每节复制表头行进 thead、附页脚 `&P`/`&N` 求值）；分页正确性最终以 headless 浏览器打印截图验收裁决（见 `html-acceptance-baseline.md`），不依赖浏览器分页算法的语义保证。
- fitToWidth/fitToHeight/scale 缩放模式：分页模式按原始尺寸装箱，忽略缩放并登记已知限制（PDF 侧 fit 语义不受影响）。
- 页脚占位符：`&P`（页码）/`&N`（总页数），缺省页脚「第 &P / &N 页」；多 sheet 按 sheet 作用域各自编号（全局作用域留后续）。
- 横向分页与关键列重复（`repeat-key-columns` HTML 等价）为后续交付（纵向长表打印为主场景）。
- 服务端装箱按行高判定分页位置；不承诺与 PDF 分页逐页一致，承诺的是**语义一致**（重复表头/页脚占位符在每打印页成立）。装箱断点避让合并单元格：断点不得落在 mergeDown 跨界行上，回退到合并块起始行之前（整块移入下一页）。
- PDF 输出能力（服务端分页排版/页眉页脚绘制）归 PDF 渲染器设计（`pdf-pagination.md`），本档不重复。

## 四、拒绝了什么

| 替代方案 | 拒绝理由 |
|----------|----------|
| 服务端引入第二排版引擎（html→pdf 中转式排版） | 与「PDF 是 PDFBox 直渲、HTML 是浏览器渲染」的既有架构冲突；服务端装箱只按行高切节，不做排版计算 |
| 仅用 `@page` margin boxes 做页眉页脚 | 主流浏览器支持不完整（margin box 内容生成受限）；降级路径必须有 |
| 分页只做 CSS `page-break` 不重复表头 | 长表续页无表头不可读，等于没解决打印问题 |

## 五、与已有设计的关系

- `pdf-pagination.md`：PDF 分页语义的 canonical 来源；本档复用其配置语义并声明 HTML 等价。
- `html-acceptance-baseline.md`：本档交付物的验收口径来源。
- 套打行为与 `ExcelImage.print=false` 既有三端契约保持正交。
