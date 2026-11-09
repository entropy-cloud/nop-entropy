# docx 参考实现索引与能力对版矩阵（docx roadmap WI0）

> Status: open
> Date: 2026-11-10
> Sources: ~/sources/springreport（本地代码实证）、poi/docx4j/mammoth 官方文档事实（未本地下载源码）、docs-for-ai/02-core-guides/word-document-model.md

## 1. 参考实现定位

| 实现 | 许可 | 定位 | 对本 roadmap 的参考价值 | 禁用项 |
| --- | --- | --- | --- | --- |
| **springreport**（本地 `~/sources/springreport`，官方 https://github.com/springreport/springreport ） | 后端以仓库 LICENSE 为准；前端 ui-vue3 为 MIT（SpringReport-ui-vue3/LICENSE） | 开源报表平台，Word 模板动态填充 + 在线设计器 | 三层能力对版（文档结构层/模板配置层/产品化编辑层）；动态表格纵向/横向/分组扩展策略实证 | 其 Word→PDF 依赖 **Aspose-Words（闭源付费 jar，common/pom.xml:11-15 systemPath 引 lib/aspose-words-21.1-jdk17.jar）**；设计器前端闭源组件；代码/依赖一律不引入 |
| **apache-poi**（官方 https://poi.apache.org ） | Apache-2.0 | Java OOXML 库（XWPF） | OOXML 细节语义参考（XWPFRun rPr 字段集合、numbering、headers/footers API 形态）；springreport 自身即用 POI 写 docx（WordUtil.java:51 `import org.apache.poi.xwpf.usermodel.XWPFDocument`） | 不引入依赖：Nop 走自有 nop-ooxml 编解码，POI 仅作语义对照 |
| **docx4j**（官方 https://github.com/plutext/docx4j ） | Apache-2.0 / LGPL 双许可 | JAXB 全量 OOXML 对象化 + 内容合并 | 完整 schema 覆盖清单、模板变量（OpenDoPE）思路、HTML/PDF 导出的第三方桥参考 | 不引入依赖；其 PDF 导出走商业转换器，仅了解路线 |
| **mammoth**（官方 https://mwilliamson.github.io/mammoth.js/ ） | BSD-2 | docx→HTML 语义化转换 | 「语义优先」的解析取舍哲学（忽略非语义格式、保留结构标签），对 round-trip 语义等价验收口径有启发 | JS 生态，不引入 |

## 2. springreport 本地证据锚点

| 能力 | 证据（file:line） | 说明 |
| --- | --- | --- |
| 文档结构 JSON 化（header/footer/main 三段） | pojo/src/main/java/com/springreport/entity/doctplsettings/DocTplSettings.java:43-52（`header`/`footer`/`main` String 字段） | 前端 JSON 块数据存库，非强类型模型——Nop 结论：不回退到 JSON DTO 即最终模型 |
| 页面设置 | DocTplSettings.java:55-68（`paperDirection`/`width`/`height`/`margins`） | 宽高/方向/页边距 |
| 首页页眉页脚开关 | pojo/src/main/java/com/springreport/entity/doctpl/DocTpl.java:80（`firstpageHeaderFooterShow`） | 属页面设置语义，应进 page/doc.model 或页面级字段 |
| 水印 | pojo/src/main/java/com/springreport/entity/doctplsettings/DocTplSettings.java:90-92（`watermark` String 字段） | 运行时属性，归属裁定留 WI10 |
| 图表绑定 | pojo/src/main/java/com/springreport/entity/doctplcharts/DocTplCharts.java:36-40（`id`/`tplId` + chart 配置）；common/base/DocChartSettingDto.java | 独立绑定表——Nop 对应「图片占位 + 绑定定义内嵌 doc/table.model」 |
| 二维码/条码 | pojo/src/main/java/com/springreport/entity/doctplcodes/DocTplCodes.java:42-44（`code_url`/`code_name`）；common/util/WordUtil.java:103-104（import BarCodeUtil/QRCodeUtil） | 图片替换模式 |
| 动态表格扩展策略 | common/util/ReportWordUtil.java:200-213（`horizontal`/`vertical` switch 分派） | 纵向/横向/分组展开——复用 nop-report-docx 对应实现 |
| Word→PDF | common/pom.xml:11-15（aspose-words systemPath） | **禁用**：Nop 走 nop-pdf（PDFBox）路线 |
| POI 读写路径 | common/util/WordUtil.java:51,117,160,178（XWPFDocument/heading 样式/纸张/边距） | springreport 的 docx 结构读写即 POI 薄封装 |
| 参数合并策略 | pojo/src/main/java/com/springreport/entity/doctpl/DocTpl.java:52（`paramMerge`） | 报表运行控制语义，归属裁定留 WI10 |

## 3. 能力对版矩阵（吸收 / 仅对版 / 禁用）

| 能力（roadmap 范围） | springreport | apache-poi | docx4j | mammoth | 裁定 |
| --- | --- | --- | --- | --- | --- |
| 段落/标题/列表块 | 有（JSON 块） | 有（XWPFParagraph/numbering） | 有 | 有（语义标签） | 吸收语义；Nop 强类型建模 |
| 图片（关系/媒体部件） | 有 | 有 | 有 | 转内联 img | 吸收；OOXML 语义以 POI/docx4j 对照 |
| 超链接 | 有 | 有 | 有 | 有 | 吸收（含模板语义 expr:/xpl: 超链接） |
| tab/br 行内 | 有 | 有 | 有 | 有（br→换行） | 吸收 |
| 分页/分隔线 | 有 | 有 | 有 | 分页不保留 | 吸收 |
| run 全样式（粗斜下删高上下标/字体/字号/颜色） | 有 | 有 | 有 | 部分（粗斜下） | 吸收；WordRunStyle/OfficeFont 承载 |
| 段落样式（对齐/行距/缩进） | 有 | 有 | 有 | 部分 | 吸收 |
| 表格合并/列宽/行高/边框/底纹 | 有（WordTableMerge） | 有 | 有 | 简化 | 吸收 |
| 多节 section/页眉页脚 | 部分（header/footer/main 三段，无多节） | 有 | 有 | 忽略 | 吸收（Nop 目标超集） |
| 页面设置/页边距/背景/水印 | 有（含 watermark） | 有 | 有 | 忽略 | 吸收 |
| 模板变量/数据集绑定 | 有（绑定表 + settings JSON） | 无 | OpenDoPE | 无 | 吸收语义，**内嵌 model 承载**（不建外挂 settings 表） |
| 动态表格 vertical/horizontal/group 展开 | 有（ReportWordUtil） | 无 | 无 | 无 | 复用 nop-report-docx |
| 图表/二维码/条码占位 | 有（charts/codes 绑定表） | 无 | 无 | 无 | 吸收「占位+绑定+替换」模式 |
| Word→PDF | **Aspose（禁用）** | 无（需外部） | 商业转换器 | 无 | **禁用**；Nop 走 nop-pdf 路线 |
| 在线设计器/分类/发布/权限 | 有 | — | — | — | **仅对版**；产品化编辑层外部化，产品管理字段不入文档模型 |

## 4. 结论

1. springreport 的文档结构层与 Nop 当前 OfficeDocModel 同层，且 Nop 的强类型 + 内嵌模板语义路线表达力更优；对版不改变建模方向。
2. springreport 的价值集中在「模板配置层」的绑定语义（charts/codes/扩展策略/firstpageHeaderFooterShow/watermark/paramMerge）——逐项归属裁定（进模型 vs 留外部）由 WI10 承载。
3. poi/docx4j 仅作 OOXML 语义对照（不引依赖）；mammoth 的「语义优先」取舍用于 round-trip 验收口径设计。
4. Aspose/OnlyOffice 等闭源付费件在任何 WI 中禁用；Word→PDF 只有 nop-pdf（PDFBox）一条路线。
