# 报表子报表引用 设计

**范围**：`nop-kernel/nop-xdefs`（excel-table.xdef cell model）、`nop-format/nop-excel`（XptCellModel）、`nop-kernel/nop-core`（raw html 输出）、`nop-report/nop-report-core`（求值）
**状态**：active（规范性文档；实施编排见 `ai-dev/backlog/nop-report-capability-roadmap.md`）

> 本档为规范性文档：只描述当前有效设计；变更历史见 git 与 AI 开发记录区的时效性文档。

---

## 一、设计结论

1. 子报表引用为 cell 级声明：`<subreportPath>`（模板 VFS 路径）+ `<subreportParams>`（report-expr，返回 Map）。求值期由引擎渲染子模板（param 注入 `${param.*}`，经 WI2 语义），**html 渲染类型下子模板 html 片段作为单元格 raw html 内容；xlsx/pdf 渲染类型取子模板渲染文本（去标签）**——嵌套表格结构渲染为 residual（roadmap 登记）。
2. 引擎获取经 `BeanContainer.tryGetBean("nopReportEngine")`（IReportAuthChecker 先例）；**bean 缺失（嵌入式）→ fail-fast** `nop.err.xpt.subreport-engine-unavailable`，非静默。
3. 递归深度上限 `nop.report.max-subreport-depth`（默认 5，scope 深度计数，子作用域内传递）；超限 fail-fast `nop.err.xpt.subreport-depth-exceeded`（防循环引用）。参数表达式非 Map → `nop.err.xpt.subreport-params-error`。
4. raw html 输出为 nop-core `ICellView` additive `isHtmlContent()`（缺省 false = 纯文本转义不变），`HtmlTableOutput` 按 flag 分支。**转义责任链**：param 值在子模板内部作为单元格文本经子模板自身的 HtmlTableOutput 转义；主模板整体 raw 嵌入不二次转义、不引入注入面。
5. 参数化钻取（主报表→明细跳转）由 `linkExpr` 承载（可引用 `${param.*}` 生成目标 URL），非独立机制。

## 二、背景与动机

父子层级展开只能复用同一模板内的结构；跨模板复用（明细子表、图表区块）需要引用另一模板并传参。渲染类型感知（html 嵌套 vs 文本回退）通过渲染入口写入 scope 的 `xptRenderType` 变量（各渲染器入口写入；`DisabledEvalScope` 纯静态渲染下跳过写入，子报表按文本回退）。

## 三、核心设计

- 求值挂点：`ExpandedSheetEvaluator` cell 求值尾部（styleIdExpr/condStyles 之后）；子作用域传参（`param` + 深度计数），不污染父作用域。
- 安全：子模板为主模板的内部引用，**授权由主模板入口覆盖，不逐层重复检查**；param 经子模板的 WI2 校验；嵌套深度 fail-fast。
- 已知限制：`DisabledEvalScope`（HtmlRenderHelper 静态渲染）下 renderType 未标记 → 子报表按文本回退；子报表渲染结果不缓存（同 param 多 cell 重复渲染为 follow-up）。

## 四、拒绝了什么

| 替代方案 | 拒绝理由 |
|----------|----------|
| xlsx/pdf 嵌套表格结构渲染 | OOXML/绘制层深改（姊妹 roadmap 量级）；html 端承载主场景 |
| 子报表独立机制（非 cell 声明） | cell 级声明与展开/样式/条件格式同一模型，作者心智一致 |
| 静默跳过（引擎缺失/超限） | 违反 no-silent-no-op；渲染结果缺一块不如快速失败 |

## 五、与已有设计的关系

- `parameter-model.md`：子模板 param 经同一 WI2 校验/默认值语义。
- `auth-wiring.md`：子模板加载过 WI1 授权。
- `html-pagination.md`：子报表 html 与分页装箱正交（子内容随父 cell 装箱）。
