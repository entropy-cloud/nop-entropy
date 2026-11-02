# nop-report — 报表引擎

## 功能概览

全功能报表引擎，支持类 Excel 报表定义、多格式输出。

- 报表模板是 Excel 工作簿（`.xpt.xlsx`/`.xpt.xml`），用 Excel 思维设计（格子 + 展开 + 公式），不写代码
- **三种输出载体**：XLSX、PDF（PDFBox 直接渲染）、HTML（屏幕预览）—— 均可作打印载体
- **套打**：背景图（`ExcelImage.print=false`）屏幕可见、打印隐藏，动态文字对位填表单
- 数据集支持程序构造 / 原生 SQL / ORM / query-bean（`xpt-rt.xlib` 标签）
- 角色级报表与数据源访问权限（**预留未接线**——权限表可配置但渲染与取数链路当前不校验，勿依赖；勘误见下方实体表标注）
- 报表结果文件缓存
- 可从导入模板（Excel + `imp.xml`）自动生成报表模型

## 主要能力入口

| 能力 | 怎么做 | 入口 |
|------|--------|------|
| **生成报表 / 单据打印（含套打）** | 注入 `IReportEngine`，`getRenderer(path, renderType)` 渲染 xlsx/pdf/html | **`../03-runbooks/generate-report.md`**（XPT 单元格语法 / 数据集 / 套打 / 调用范例 / imp 模板自动生成） |

## 核心实体

> 运行时管理的报表（通过 CRUD 页面定义数据集/数据源）：实体表见下。**渲染入口不是这些 CRUD BizModel，而是 `IReportEngine`**（见上方 runbook）。

| 实体 | 表名 | 用途 |
|------|------|------|
| NopReportDefinition | `nop_report_definition` | 报表定义 |
| NopReportDefinitionAuth | `nop_report_definition_auth` | 报表访问权限（按角色；**预留未接线**——引擎渲染/取数当前不消费，P1-3.6-002） |
| NopReportDataset | `nop_report_dataset` | 数据集定义 |
| NopReportSubDataset | `nop_report_sub_dataset` | 子数据集关联 |
| NopReportDatasetRef | `nop_report_dataset_ref` | 报表-数据集引用 |
| NopReportDatasource | `nop_report_datasource` | 数据源定义 |
| NopReportDatasourceAuth | `nop_report_datasource_auth` | 数据源权限（按角色；**预留未接线**——同上，P1-3.6-002） |
| NopReportResultFile | `nop_report_result_file` | 缓存的报表结果文件 |

## 子模块

| 子模块 | 职责 |
|--------|------|
| `nop-report-core` | 报表核心引擎 |
| `nop-report-pdf` | PDF 输出支持 |
| `nop-report-docx` | DOCX 输出支持 |
| `nop-report-api` | API DTO |
| `nop-report-dao` | ORM 实体与 DAO |
| `nop-report-service` | 业务逻辑 |
| `nop-report-web` | Web 层与 AMIS 页面 |
| `nop-report-ext` | 扩展功能 |

## 并发与资源契约

> 本节同时承载报表依赖的 nop-format 组件（nop-record / nop-excel / nop-converter）的并发与资源契约——nop-format 暂无独立 owner doc。

### record 解码长度上界（nop-record）

- 由 `lengthExpr` 数据驱动的字段长度与对象长度均受 `RecordConstants.DEFAULT_MAX_FIELD_LENGTH`（64MB）约束：超出时在任何 reader 分配之前抛 `ERR_RECORD_DECODE_LENGTH_IS_TOO_LONG`（参数 `length` / `maxLength`）。校验位于长度求值点，文件、内存缓冲、流式与 netty 编解码路径共用，不依赖 `maxFrameLength`（其缺省值 0 不生效）。
- 对象级 `lengthExpr` 未声明静态 `length` 时，表达式返回空值按 -1（无区域）处理。
- `rawVarName` 变量承载的是**诊断前缀**而非完整原文：二进制区域取前 2048 字节的 hex，文本区域取前 4096 字符，超出部分以 `...(truncated)` 标记代替。

### RandomAccessFile reader 的 detach/duplicate（nop-record）

- `IBinaryDataReader.detach()` / `duplicate()` 返回与父实例**相互独立**的 reader：读位置互不影响、生命周期独立，初始位置等于调用时父实例的位置；`subInput()` 则返回共享底层读位置的视图。
- `RandomAccessFileBinaryDataReader` 的 detach/duplicate 会重新打开文件（占用新的文件句柄），调用方负责关闭返回的 reader。构造时取文件长度失败会关闭已打开的句柄再抛出；`close()` 可重复调用。

### PDF 字体管理（nop-report-pdf）

- `FontManager.addFontAlias` 可在运行期与渲染并发调用；初始化只执行一次，完成后取字体不再竞争实例锁。
- CJK 回退字体探测会逐个加载字体文件（首次可达秒级），在专用锁上串行执行，结果按 JVM 缓存；探测期间其他导出线程取字体不受阻塞。频率校准：`getFont` 在每个文档内每个字体键只调用一次（`PdfRenderer` 文档级字体缓存），开销集中在首次回退探测。

### Excel 格式缓存（nop-excel）

- `ExcelFormatHelper.addFormat` 线程安全，可运行期调用；注册的 `Format` 实例被所有线程共享，调用方须保证其线程安全。
- 由格式串创建的 `SimpleDateFormat` / `DecimalFormat` 按格式串缓存、**每线程一个实例**（不跨线程共享），缓存的格式串数量上限 256，超出后的格式串每次直接创建。无法转换的格式串仍返回 null。

### Excel 导入行数上界（nop-report-core）

- `ExcelIOConfig.maxCountPerSheet`（缺省 200,000）在读写两侧均按**数据行**计数（表头行不计入）：写侧达到上限后换新 sheet；读侧（`ExcelResourceIO` 导入）在解析期读到第 maxCount+1 个数据行时抛 `ERR_XPT_EXCEL_READ_ROW_COUNT_EXCEED_LIMIT`（参数 `sheetName` / `maxCount`），不做静默截断——整张 sheet 物化在内存中的行数因此有确定上界。
- `maxCountPerSheet <= 0` 表示不设上界（恢复全量物化，内存随行数线性增长）。大数据量导入应调大该值或改用 csv / jdbc 读取。

### 文档转换器注册（nop-converter）

- `DocumentConverterManager.registerConverter` 可在运行期与 `getConverter` 并发调用；链式查找跳过与源类型相同的中间类型。

## 源码锚点

| 组件 | 路径 |
|------|------|
| ORM 模型 | `nop-report/model/nop-report.orm.xml` |
| 并发与资源契约 | 见 `../04-reference/source-anchors.md` 的 `RPT-CC-001` / `FMT-CC-001` |

## 相关文档

- `../02-core-guides/reporting-and-notification-integration.md`
- `../reusable-modules-overview.md`
