# nop-datav 模型设计 (D0)

> Status: **final**
> Last Reviewed: 2026-08-09

## 概述

nop-datav 是 Nop 平台的数据可视化模块，提供看板（Dashboard）、面板（Panel）、页签（DashboardTab）、数据集引用（DatasetRef）的标准 CRUD，以及发布/快照语义。本模块不重建数据源/数据集管理（复用 nop-report）、不重建维度/度量建模（复用 nop-metadata）、不重建渲染引擎（走 nop-chaos-flux）。

## 实体职责划分

| 实体 | 职责 | 关键约束 |
|------|------|----------|
| NopDatavDashboard | 控制面聚合根，管权限/元数据/发布状态 | 主表，不存已发布内容 |
| NopDatavPanel | 面板，归属于看板，通过 datasetRefId 引用数据集 | FK → Dashboard |
| NopDatavDashboardTab | 页签，归属于看板 | FK → Dashboard |
| NopDatavDatasetRef | 数据集引用，记录所引用的 nop-report 数据集标识 + 参数映射 | FK → Dashboard |
| NopDatavDashboardSnapshot | 发布快照，独立表管已发布内容 | FK → Dashboard, UK(dashboardId, snapshotVersion) |

## 关系设计

- Panel → Dashboard（to-one，via dashboardId）
- DashboardTab → Dashboard（to-one，via dashboardId）
- DatasetRef → Dashboard（to-one，via dashboardId）
- Panel.datasetRefId → DatasetRef（逻辑引用，非外键约束，允许 null）

## 发布/快照方案

**选择：主表（Dashboard）管权限/元数据/发布状态 + 独立快照表（DashboardSnapshot）管已发布内容。**

发布时将看板编辑内容（布局配置 + 面板 + 页签 + 数据集引用配置）序列化为 JSON 写入快照表，主表更新发布状态/版本/发布人/发布时间。查看已发布版本返回最新快照内容。回滚到指定历史快照时，从快照内容恢复主表字段。

### Biz Action 契约

| Action | 类型 | 主表读写 | 快照表读写 |
|--------|------|----------|------------|
| publishDashboard | @BizMutation | 更新 publishStatus/publishedVersion/publishedBy/publishedTime | INSERT 新快照行 |
| getPublishedDashboard | @BizQuery | 仅读 | 查询最新快照 |
| rollbackDashboard | @BizMutation | 从快照内容恢复 layoutConfig/publishStatus/publishedVersion/publishedBy/publishedTime | 查询指定版本快照 |

版本号生成策略：当前看板最大快照版本 + 1（从快照表查询，非主表 publishedVersion 字段）。

## DatasetRef 归属方案

**选择：DatasetRef 由 Dashboard 拥有（dashboardId FK），Panel 通过 datasetRefId 引用。**

DatasetRef 记录所引用的 nop-report 数据集标识（refDatasetId）和参数映射（paramMapping），仅做模型侧存储，不实现运行时查询委托（D1 数据绑定管线）。

## 与 nop-report / nop-metadata 的关系

- DatasetRef.refDatasetId → nop-report 数据集标识（存储引用，运行时解析在 D1）
- DatasetRef.paramMapping → 参数映射 JSON（面板变量到数据集参数的映射）
- 维度/度量字段映射元数据来源：nop-metadata（D1 运行时解析）

## 拒绝的替代方案

### 单表存全部内容 vs 主表 + 快照表

拒绝单表方案（Grafana 式单一 JSON blob 存全部看板内容）。理由：
- 主表查询（列表、权限过滤）需要轻量字段，不应扫描大 JSON 列
- 发布版本管理需要独立的历史记录，单表无法表达多版本
- 主表 + 快照表分离了「编辑态」和「已发布态」，权限控制更清晰

### Grafana 式单一 JSON blob vs 归一化多表

拒绝将面板/页签/数据集引用全部嵌入 Dashboard 的 JSON 列。理由：
- 归一化多表支持独立 CRUD，面板/页签/数据集引用可独立管理
- 关系约束（FK）和索引在归一化表上更有效
- 编辑时独立修改单个面板不需要重写整个 JSON

### DatasetRef 多态归属 vs Dashboard 单一拥有

拒绝 DatasetRef 使用 ownerType + ownerId 多态关联（可属于 Dashboard 或 Panel 或其他）。理由：
- 多态关联增加查询复杂度，且在 ORM 模型中难以表达外键约束
- Dashboard 单一拥有简化了权限管理（看板级权限自动覆盖其所有数据集引用）
- Panel 通过 datasetRefId 引用，解耦了数据集引用的归属和面板的使用

## 模块结构

标准 Nop 8 件套：model + codegen/dao/meta/service/web/app/api，与既有 nop-datav-chart 共存于同一父 pom。包名约定 `io.nop.datav`。
