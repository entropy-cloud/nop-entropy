# nop-report — 报表引擎

## 功能概览

全功能报表引擎，支持类 Excel 报表定义、多格式输出。

- 报表定义管理（rptText 存储 XML/JSON 模型）
- 多数据源支持（SQL、文件）
- 数据集定义与子数据集关联
- 输出格式：XLSX、PDF、DOCX
- 角色级报表访问权限
- 报表结果文件缓存
- 数据源权限控制

## 核心实体

| 实体 | 表名 | 用途 |
|------|------|------|
| NopReportDefinition | `nop_report_definition` | 报表定义 |
| NopReportDefinitionAuth | `nop_report_definition_auth` | 报表访问权限（按角色） |
| NopReportDataset | `nop_report_dataset` | 数据集定义 |
| NopReportSubDataset | `nop_report_sub_dataset` | 子数据集关联 |
| NopReportDatasetRef | `nop_report_dataset_ref` | 报表-数据集引用 |
| NopReportDatasource | `nop_report_datasource` | 数据源定义 |
| NopReportDatasourceAuth | `nop_report_datasource_auth` | 数据源权限（按角色） |
| NopReportResultFile | `nop_report_result_file` | 缓存的报表结果文件 |

## 使用方式

1. 在管理页面定义数据源（支持 SQL 类型、文件类型）
2. 创建数据集，配置查询语句
3. 创建报表定义，设计报表布局（Excel 风格）
4. 配置角色访问权限
5. 通过 API 或页面渲染报表

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

## 源码锚点

| 组件 | 路径 |
|------|------|
| ORM 模型 | `nop-report/model/nop-report.orm.xml` |

## 相关文档

- `../02-core-guides/reporting-and-notification-integration.md`
- `../reusable-modules-overview.md`
