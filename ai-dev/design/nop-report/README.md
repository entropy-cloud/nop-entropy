# nop-report 设计文档

本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织，承载 nop-report 报表引擎子系统的架构决策、使用契约与需求规格。

## 阅读顺序

**必读路径**（理解本子系统的横切契约）：

1. `parameter-model.md` — 报表参数模型：声明内嵌 XPT、`${param.*}` 取值、查询表单生成、缓存键参与
2. `auth-wiring.md` — 报表/数据源权限接线：渲染入口 + 取数标签双重检查点、deny-by-default、错误码与指引
3. `html-pagination.md` — HTML 分页打印保真：浏览器打印语境、与 PDF 分页语义对齐、套打正交
4. `html-acceptance-baseline.md` — HTML 验收基线：浏览器截图 golden + DOM 断言的验收口径

**按需深入**（PDF 渲染器专题，归 nop-pdf-report-export roadmap 交付）：

- `pdf-pagination.md` — PDF 分页语义（repeat-header-rows / repeat-key-columns / 页脚占位符）的 canonical 来源
- `pdf-font-strategy.md` — PDF 字体解析顺序与 CJK 逐字符回退
- `pdf-metadata-protection.md` — PDF 加密/签名/权限元数据
- `pdf-text-extraction.md` — PDF 结构与文本抽取
- `pdf-toc-structure.md` — PDF outline/目录结构

## 职责边界

- 本目录只覆盖 nop-report 引擎与其直接渲染器；看板/BI/定时报送/AI 问数归 `nop-datav/`；Word 通用文档模型归 `word-editor/`。
- 平台使用规范（怎么调用引擎、模板语法）的权威来源是 `docs-for-ai/03-modules/nop-report.md` 与 `docs-for-ai/03-runbooks/generate-report.md`；本目录保留决策上下文（为什么）。
- 草案状态的文档（parameter-model / auth-wiring / html-pagination / html-acceptance-baseline）是已裁定的目标契约，实施编排见 `ai-dev/backlog/nop-report-capability-roadmap.md`。
