# nop-pdf 模块能力矩阵

> 派生视图，以各 design 子文档为权威（见 Related）。

nop-pdf 是基于 Apache PDFBox 3.0.8 的通用 PDF 工具箱，能力覆盖三面：**导出**（nop-report-pdf 报表→PDF）、**装配**（已有 PDF 后处理）、**抽取**（PDF→结构化数据）。

## 模块依赖

| 模块 | 依赖 | 说明 |
|---|---|---|
| nop-format/nop-pdf | nop-xlang, pdfbox 3.0.8, jts-core, snakeyaml | 通用 PDF 工具箱（装配/抽取/验证） |
| nop-report/nop-report-pdf | nop-excel, nop-ooxml-xlsx, nop-report-core, nop-pdf | 报表→PDF 渲染 |

## 导出面（nop-report-pdf → PDF）

| 能力 | 状态 | 裁定文档 |
|---|---|---|
| 分页打印（Print_Titles/fit 缩放/分页边界/手动分页符） | ✅ | pdf-pagination.md D1/D5/D6 |
| 页眉页脚占位符（&P/&N/&D/&A/&F/&Z）+ 作用域 + 奇偶首页 | ✅ | pdf-pagination.md D7 |
| 页眉页脚富文本（多字体/字号/图片/分区裁剪） | ✅ | pdf-pagination.md D7 |
| 边框线宽/线型保真（weight 驱动 + 缩放补偿） | ✅ | pdf-pagination.md D10 |
| 颜色保真（统一解析 + alpha 渲染 + indexed/theme） | ✅ | pdf-pagination.md D11 |
| 字体渲染保真（伪粗体/斜体合成 + 变体发现 + ToUnicode） | ✅ | pdf-font-strategy.md D5 |
| 图片嵌入保真（DPI/旋转/alpha/裁剪/缓存） | ✅ | pdf-pagination.md D8 |
| 图表嵌入（图片 provider SPI + 矢量优先） | ✅（桩接线） | echarts-server-render roadmap |
| 富文本 run（同格多字体） | ✅ | pdf-pagination.md D12 |
| 竖排/旋转文本（ECMA-376 textRotation） | ✅ | pdf-pagination.md D12 |
| 数字格式（formatExpr/Excel 格式串） | ✅ | pdf-pagination.md D12 |

## 装配面（nop-pdf → PDF 后处理）

| 能力 | 状态 | 说明 |
|---|---|---|
| 合并（多 PDF → 一，COS 深拷贝） | ✅ | PdfDoc.merge |
| 追加页（append） | ✅ | PdfDoc.append |
| 拆分导出（exportPages，先校验后委托） | ✅ | PdfDoc.exportPages |
| 盖章（文本/图片，指定页指定位置，旋转/透明度） | ✅ | PdfDoc.stampText/stampImage |
| 水印（文字/图片，全页缺省 45°/0.25） | ✅ | PdfDoc.watermarkText/watermarkImage |
| 追加页码（{page}/{pages} 占位符） | ✅ | PdfDoc.appendPageNumbers |
| 追加页眉页脚（字面文本） | ✅ | PdfDoc.appendHeaderFooter |
| 图片→PDF（PNG/JPEG，px/72 页面） | ✅ | PdfDoc.fromImage/fromImages |
| 文档元数据（六字段） | ✅ | PdfDoc.getInfo/setInfo |
| 加密（AES-256 + 权限位） | ✅ | PdfDoc.encrypt |

## 抽取面（PDF → 结构化数据）

| 能力 | 状态 | 说明 |
|---|---|---|
| outline（书签）抽取 + YAML | ✅ | PdfDoc.extractOutline（card-kb 对版 39/39） |
| 目录页检测（三页码形态） | ✅ | TocLineDetector |
| 结构源路由（outline > toc-regex > 钩子 > chunk） | ✅ | StructureSourceRouter |
| 标题回填（markdown 层级） | ✅ | OutlineBackfiller/TocBackfiller |
| 版面文本（strip/layout 双模式） | ✅ | PageTextExtractor |
| 页锚（`<a id="pdf-page-N"></a>`） | ✅ | PageTextExtractor |
| 扫描件检测（硬错误/SKIP） | ✅ | PageTextExtractor |
| 乱码双路信号（unmapped-glyph + U+FFFD） | ✅ | PageTextExtractor |
| 图片普查 + registry YAML | ✅ | ImageCensus |

## 已知限制

- 图表渲染引擎（ECharts 服务端静态渲染）属 echarts-server-render roadmap，nop-report-pdf 仅消费图片 provider。
- 纯定位间隙目录行在 toc-regex 路径系统性漏检（PDFTextStripper 词间只写单分隔符）——WI16 layout 模式为 owner。
- 数字签名、PDF/A 转换：后续增强（见 pdf-metadata-protection.md D3）。
- 富文本 + wrapText 逐 run 折行、旋转文本折行：后续增强。
- inline image（BI/ID/EI）普查：Deferred。
