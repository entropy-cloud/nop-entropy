# springreport 能力对齐裁定（docx roadmap WI10）

> Status: active（规范性：本文件记录归属裁定结论与理由；演进状态见 roadmap）
> Sources: `~/sources/springreport`（本地代码实证，file:line）+ 本仓库 live code 锚点 + `ai-dev/analysis/2026-11/2026-11-10-docx-reference-impl-index.md`（WI0 对版矩阵）

## 结论速览

| springreport 能力 | 证据（springreport file:line） | Nop 现状锚点 | 归属裁定 |
| --- | --- | --- | --- |
| `firstpageHeaderFooterShow`（首页不同页眉页脚） | `pojo/.../entity/doctpl/DocTpl.java:80` | doc-model page 无 first 页眉页脚字段；OOXML 对应 sectPr `titlePg` + `type="first"` 引用（未建模） | **应进模型，延期补齐**（模型变更 + 解析/生成/PDF 三面接线；需求出现时立项） |
| `watermark`（水印） | `pojo/.../entity/doctplsettings/DocTplSettings.java:90-92` | doc-model 不承载原始 VML；docx 生成不保真水印（WI5 口径）；PDF 渲染无水印（模型无从渲染） | **留外部/模型不承载**——水印是视觉装饰，模型层保留会破坏强类型边界；需求时按渲染目标（PDF stamp 已有 `PdfDoc.stampText/stampImage`）实现 |
| `charts`（图表） | `pojo/.../entity/doctplcharts/DocTplCharts.java`（tplId 绑定表） | WI8 `chartBinding`（image.model 内嵌，echarts opt-in 渲染） | **已进模型**（绑定内嵌 + opt-in 渲染；比绑定表更就近） |
| `codes`（二维码/条码） | `pojo/.../entity/doctplcodes/DocTplCodes.java:42-44`（code_url/code_name）；`common/util/WordUtil.java:103-104`（BarCodeUtil/QRCodeUtil） | WI8 `codeBinding`（qr 经 IQrcodeService / code128 内置 zxing） | **已进模型** |
| 数据集扩展策略（vertical/horizontal/group） | **Word 侧未实证**（WI0 矩阵原引 ReportWordUtil.java:200-213 实为单元格对齐 switch，系错锚；springreport 的纵/横扩展枚举 CellExtendEnum（NOEXTEND/HORIZONTAL/VERTICAL，springreport 仓库 common/enums/ 下）展开驱动在 Excel 报表链 ReportTplServiceImpl.java:967-997） | WI7 `OfficeDocModelExpander` + xpt 标记 `*=^ds!field`（`^` 纵向/`>` 横向/`@` 分组聚合，复用 NopReport 展开引擎） | **已进模型**（Nop Word 侧经既有报表管线；对版基准为 springreport Excel 链的扩展语义） |
| `paramMerge`（预览查询参数合并） | `pojo/.../entity/doctpl/DocTpl.java:52` | 模型与出口均无对应字段——属报表运行控制（预览请求参数拼装策略），非文档结构或模板语义 | **留外部**（应用层/运行控制配置；进模型会造成文档模型承载运行时行为） |

## 归属裁定原则（复述自设计文档既有决策）

参与文档生成的语义进模型（内嵌各层 `model`）；产品管理字段（模板名/分类/发布/权限）与纯运行控制参数留模型外；视觉装饰（水印）不进入强类型结构模型。

## 必要补齐实现

无——charts/codes/扩展策略三项已在 WI7/WI8 落地；`firstpageHeaderFooterShow` 与水印裁定为延期（见下），不属于「必要」（无当前需求方，模型补齐需三面接线）。

## Deferred（登记于 roadmap 承载计划）

1. **首页不同页眉页脚（titlePg）**：模型变更（page 增 first 页眉页脚字段）+ 解析（titlePg/first 引用）+ 生成（w:hdrReference first）+ PDF 渲染（首页独立页眉页脚）三面接线。successor：需求出现时立项。
2. **水印**：视觉装饰不进结构模型；PDF 场景可复用既有 stamp 能力按需求另议。

## 禁用项（重申）

springreport 的代码与闭源付费依赖（Aspose-Words、OnlyOffice 设计器）一律不引入；仅作能力对版参考。
