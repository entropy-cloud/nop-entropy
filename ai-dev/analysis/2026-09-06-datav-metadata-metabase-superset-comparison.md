# nop-metadata/nop-datav vs Metabase vs Superset 功能对比分析

> Status: open
> Date: 2026-09-06
> Scope: nop-metadata、nop-datav、Metabase、Superset 功能对比
> Conclusion: open

## Context

对比 Nop 平台的元数据管理(nop-metadata)和数据分析(nop-datav)模块与业界领先的 BI 平台 Metabase 和 Apache Superset，识别功能差距和改进方向。

---

## 一、整体架构对比

| 维度 | nop-metadata | nop-datav | Metabase | Superset |
|------|-------------|-----------|----------|----------|
| **语言** | Java | Java | Clojure | Python |
| **ORM** | 自研 ORM | 自研 ORM | Toucan 2 | SQLAlchemy |
| **查询引擎** | EQL + SQL | SQL | MBQL | SQL + Jinja |
| **前端** | AMIS 声明式 | Flux Schema | React | React + ECharts |
| **实体数量** | 39 | 17 | ~40+ | ~30+ |
| **Java 文件** | 282 | 231 | - | - |
| **测试文件** | 168 | 待统计 | - | - |

---

## 二、功能模块对比

### 2.1 数据源管理

| 功能 | nop-metadata | Metabase | Superset | Nop 差距 |
|------|-------------|----------|----------|---------|
| 数据源类型 | JDBC/HTTP/REST/File | 68+ 数据库 | 68+ 数据库 | 需增加更多连接器 |
| 连接测试 | ✅ | ✅ | ✅ | - |
| Schema 同步 | ✅ syncExternalTables | ✅ 自动同步 | ✅ 自动同步 | - |
| 连接池 | 由 nop-dao 提供 | 内置 | SQLAlchemy | - |
| SSH 隧道 | ❌ | ❌ | ✅ | 需要 |
| OAuth2 认证 | ❌ | ✅ (Google等) | ✅ | 需要 |

### 2.2 语义层 (Semantic Layer)

| 功能 | nop-metadata | Metabase | Superset | Nop 差距 |
|------|-------------|----------|----------|---------|
| 逻辑表 | ✅ NopMetaTable | ✅ Table | ✅ Dataset | - |
| 维度 | ✅ NopMetaTableDimension | ✅ Field | ✅ TableColumn | - |
| 指标 | ✅ NopMetaTableMeasure | ✅ Metric/Measure | ✅ SqlMetric | - |
| 关联 | ✅ NopMetaTableJoin | ✅ 自动推断 | ✅ 手动配置 | - |
| 过滤器 | ✅ NopMetaTableFilter | ✅ Segment | ✅ RLS | - |
| SQL 视图 | ✅ sql tableType | ✅ Native Query | ✅ Virtual Dataset | - |
| **血缘追踪** | ✅ NopMetaLineageEdge | ❌ | ❌ | **Nop 优势** |
| **数据质量** | ✅ 7种规则类型 | ❌ | ❌ | **Nop 优势** |
| **数据对账** | ✅ NopMetaReconciliation | ❌ | ❌ | **Nop 优势** |

### 2.3 查询引擎

| 功能 | nop-metadata | Metabase | Superset | Nop 差距 |
|------|-------------|----------|----------|---------|
| 可视化查询 | ❌ (需前端) | ✅ MBQL | ✅ SQL Lab | 需要可视化查询构建器 |
| 原生 SQL | ✅ | ✅ | ✅ | - |
| 查询缓存 | ✅ DashboardPanelQueryCache | ✅ | ✅ Redis | - |
| 并行查询 | ✅ Semaphore 并行 | ✅ | ✅ Celery | - |
| 跨库 JOIN | ✅ 6种聚合策略 | ❌ | ❌ | **Nop 优势** |
| 参数化查询 | ✅ | ✅ Template Tags | ✅ Jinja | - |

### 2.4 仪表板 (Dashboard)

| 功能 | nop-metadata | nop-datav | Metabase | Superset | Nop 差距 |
|------|-------------|-----------|----------|----------|---------|
| 仪表板 | - | ✅ Dashboard | ✅ | ✅ | - |
| 面板/卡片 | - | ✅ Panel | ✅ DashboardCard | ✅ Slice | - |
| 标签页 | - | ✅ DashboardTab | ✅ DashboardTab | ✅ Tabs | - |
| 布局 | - | ✅ layoutConfig | 网格拖拽 | position_json | 前端拖拽 |
| 筛选器 | - | ✅ FilterState | ✅ Parameters | ✅ Native Filters | - |
| 筛选器联动 | - | ✅ Linkage | ✅ Linked Filters | ✅ | - |
| 分享 | - | ✅ DashboardShare | ✅ Public Links | ✅ | - |
| 定时刷新 | - | ❌ | ✅ Auto-refresh | ✅ | 需要 |
| 全屏模式 | - | ❌ | ✅ | ✅ | 需要 |

### 2.5 大屏 (Screen)

| 功能 | nop-datav | Metabase | Superset | Nop 差距 |
|------|-----------|----------|----------|---------|
| 大屏定义 | ✅ NopDatavScreen | ❌ | ❌ | **Nop 优势** |
| 组件定位 | ✅ x/y/w/h/z | - | - | - |
| 自适应模式 | ✅ adaptorMode | - | - | - |
| 发布快照 | ✅ ScreenSnapshot | - | - | - |

### 2.6 可视化类型

| 功能 | nop-datav | Metabase | Superset | Nop 差距 |
|------|-----------|----------|----------|---------|
| 表格 | ✅ | ✅ | ✅ | - |
| 折线图 | ✅ (Flux) | ✅ | ✅ ECharts | - |
| 柱状图 | ✅ (Flux) | ✅ | ✅ ECharts | - |
| 饼图 | ✅ (Flux) | ✅ | ✅ ECharts | - |
| 散点图 | ✅ (Flux) | ✅ | ✅ ECharts | - |
| 地图 | ✅ (Flux) | ✅ | ✅ DeckGL | - |
| 透视表 | ✅ Pivot | ✅ | ✅ | - |
| 仪表盘 | ✅ (Flux) | ✅ | ✅ ECharts | - |
| 漏斗图 | ✅ (Flux) | ✅ | ✅ ECharts | - |
| 词云 | ❌ | ❌ | ✅ | 需要 |
| 桑基图 | ❌ | ✅ | ✅ ECharts | 需要 |
| 热力图 | ❌ | ✅ | ✅ ECharts | 需要 |
| 树图 | ❌ | ✅ | ✅ ECharts | 需要 |
| 箱线图 | ❌ | ✅ | ✅ ECharts | 需要 |
| 瀑布图 | ❌ | ✅ | ✅ ECharts | 需要 |
| 甘特图 | ❌ | ❌ | ✅ ECharts | 需要 |

### 2.7 报表与导出

| 功能 | nop-datav | Metabase | Superset | Nop 差距 |
|------|-----------|----------|----------|---------|
| 异步导出 | ✅ ExportTask | ✅ | ✅ | - |
| 导出格式 | CSV/XLSX | CSV/XLSX/PDF | CSV/XLSX/PDF | 需要 PDF |
| 定时报表 | ✅ ReportTask | ✅ Pulse | ✅ ReportSchedule | - |
| 邮件发送 | ✅ NotificationSender | ✅ Email | ✅ Email | - |
| Slack 集成 | ❌ | ✅ | ✅ | 需要 |
| Webhook | ❌ | ✅ | ✅ | 需要 |

### 2.8 告警

| 功能 | nop-datav | Metabase | Superset | Nop 差距 |
|------|-----------|----------|----------|---------|
| 告警规则 | ✅ AlertRule | ✅ Alert | ✅ ReportSchedule | - |
| 告警状态 | ✅ AlertState | ✅ | ✅ | - |
| 阈值比较 | ✅ AlertThresholdComparator | ✅ | ✅ | - |
| 告警通知 | ✅ | ✅ | ✅ | - |

### 2.9 AI 能力

| 功能 | nop-datav | Metabase | Superset | Nop 差距 |
|------|-----------|----------|----------|---------|
| **ChatBI** | ✅ ChatBiBizModel | ✅ MetaBot | ✅ MCP Service | - |
| NL to Query | ✅ chatToQuery | ✅ | ✅ | - |
| NL to Dashboard | ✅ chatToDashboard | ❌ | ❌ | **Nop 优势** |
| NL to Screen | ✅ chatToScreen | ❌ | ❌ | **Nop 优势** |
| 多轮会话 | ✅ ChatSession | ✅ | ✅ | - |
| AI 工具调用 | ✅ 6个工具 | ✅ MCP | ✅ MCP | - |

### 2.10 数据治理 (nop-metadata 独有)

| 功能 | nop-metadata | Metabase | Superset |
|------|-------------|----------|----------|
| **元数据目录** | ✅ NopMetaCatalog | ❌ | ❌ |
| **血缘追踪** | ✅ NopMetaLineageEdge | ❌ | ❌ |
| **数据质量** | ✅ 7种规则 | ❌ | ❌ |
| **数据剖析** | ✅ ProfilingRule | ❌ | ❌ |
| **数据对账** | ✅ Reconciliation | ❌ | ❌ |
| **数据合同** | ✅ DataContract | ❌ | ❌ |
| **业务术语表** | ✅ Glossary | ❌ | ❌ |
| **分类体系** | ✅ Classification | ❌ | ❌ |
| **标签系统** | ✅ Tag/TagLabel | ✅ Tags | ✅ Tags |
| **数据产品** | ✅ DataProduct | ❌ | ❌ |

---

## 三、Metabase 独有功能 (Nop 缺失)

| 功能 | 描述 | 优先级 |
|------|------|--------|
| **可视化查询构建器** | 拖拽式 MBQL 查询构建 | P1 |
| **Collection 层级** | 内容组织到层级文件夹 | P1 |
| **Revision 历史** | 完整版本历史和回滚 | P1 |
| **Content Verification** | 内容审核/验证工作流 | P2 |
| **Document** | 富文本文档 (ProseMirror) | P2 |
| **Timeline Events** | 图表业务事件标注 | P2 |
| **X-Rays** | 自动从数据生成仪表板 | P2 |
| **Native Query Snippets** | 可复用 SQL 片段 | P1 |
| **Transform (ETL)** | SQL/MBQL/Python 数据转换 | P1 |
| **Embedding SDK** | React 嵌入组件 | P1 |
| **Git 版本控制** | 内容导出到 Git | P2 |
| **SCIM** | 用户自动配置 | P2 |

---

## 四、Superset 独有功能 (Nop 缺失)

| 功能 | 描述 | 优先级 |
|------|------|--------|
| **SQL Lab** | 交互式 SQL 开发环境 | P0 |
| **68+ 数据库引擎** | 最广泛的数据库支持 | P1 |
| **DeckGL 地理可视化** | 10种地理图层类型 | P1 |
| **CTAS/CVAS** | 创建表/视图 | P1 |
| **SSH 隧道** | 安全数据库连接 | P2 |
| **Guest Token** | 嵌入式访问令牌 | P1 |
| **RLS** | 行级安全 (已在 nop-auth) | ✅ |
| **软删除** | Dashboard/Chart 软删除 | P1 |
| **图表认证** | certified_by 标记 | P2 |
| **缩略图** | 异步生成仪表板缩略图 | P2 |
| **Annotation Layers** | 图表注释层 | P2 |

---

## 五、Nop 平台独有优势 (Metabase/Superset 缺失)

| 功能 | 描述 |
|------|------|
| **元数据治理平台** | 39实体完整元数据管理体系 |
| **血缘追踪** | SQL 解析自动抽取表级+列级血缘 |
| **数据质量引擎** | 7种规则类型 + Cron 调度 + 质量评分 |
| **数据对账** | 跨系统数据一致性校验 |
| **数据合同** | 数据契约生命周期管理 |
| **数据产品** | 数据产品目录和生命周期 |
| **跨库 JOIN** | 6种聚合策略支持跨数据源查询 |
| **Delta 扩展机制** | 可逆计算差量定制 |
| **ChatBI** | NL to Dashboard/Screen 生成 |
| **大屏支持** | Screen 实体 + 组件定位 |

---

## 六、nop-datav 待完成工作

### 6.1 P0 高优先级

| 功能 | 描述 | 工作量 |
|------|------|--------|
| **前端仪表板设计器** | 拖拽式布局、组件配置 | 大 |
| **SQL Lab** | 交互式 SQL 编辑器 | 大 |
| **更多图表类型** | 桑基图、热力图、树图、箱线图、瀑布图 | 中 |
| **导出 PDF** | 报表导出为 PDF 格式 | 小 |
| **软删除** | Dashboard/Panel 软删除支持 | 小 |

### 6.2 P1 中优先级

| 功能 | 描述 | 工作量 |
|------|------|--------|
| **定时刷新** | 仪表板自动刷新 | 小 |
| **全屏模式** | 演示模式 | 小 |
| **Slack 集成** | 告警/报表发送到 Slack | 小 |
| **Webhook 通知** | 告警/报表通过 Webhook | 小 |
| **Collection 层级** | 内容组织到文件夹 | 中 |
| **Revision 历史** | 版本历史和回滚 | 中 |
| **Native Query Snippets** | 可复用 SQL 片段 | 小 |
| **Embedding SDK** | React 嵌入组件 | 中 |
| **缩略图** | 异步生成仪表板缩略图 | 中 |

### 6.3 P2 低优先级

| 功能 | 描述 | 工作量 |
|------|------|--------|
| **可视化查询构建器** | 拖拽式查询构建 | 大 |
| **Transform (ETL)** | 数据转换管道 | 大 |
| **Document** | 富文本文档 | 中 |
| **Timeline Events** | 业务事件标注 | 小 |
| **X-Rays** | 自动仪表板生成 | 中 |
| **Git 版本控制** | 内容导出到 Git | 中 |

---

## 七、nop-metadata 与 nop-datav 集成建议

### 7.1 当前关系

```
nop-metadata (元数据治理)          nop-datav (数据分析)
┌─────────────────────┐          ┌─────────────────────┐
│ NopMetaTable        │ ───────→ │ NopDatavDatasetRef  │
│ NopMetaTableMeasure │          │ NopDatavPanel       │
│ NopMetaTableDimension│         │                     │
│ NopMetaLineageEdge  │          │                     │
│ NopMetaQualityRule  │          │                     │
└─────────────────────┘          └─────────────────────┘
     元数据定义                        数据消费
```

### 7.2 建议集成点

| 集成点 | 描述 | 优先级 |
|--------|------|--------|
| **Dataset → MetaTable** | nop-datav 的数据集直接引用 nop-meta 的逻辑表 | P0 |
| **指标复用** | nop-datav 的指标定义引用 nop-meta 的 Measure | P0 |
| **血缘展示** | nop-datav 展示 nop-meta 的血缘图谱 | P1 |
| **质量门禁** | nop-datav 查询前检查 nop-meta 的质量评分 | P1 |
| **元数据搜索** | nop-datav 复用 nop-meta 的搜索引擎 | P1 |

---

## 八、结论

### 8.1 Nop 平台优势

1. **元数据治理领先** - 血缘、质量、对账、合同等 Metabase/Superset 均无
2. **ChatBI 能力** - NL to Dashboard/Screen 是独特优势
3. **大屏支持** - Screen 实体是 Metabase/Superset 没有的
4. **跨库查询** - 6种聚合策略支持跨数据源
5. **Delta 扩展** - 可逆计算差量定制能力

### 8.2 需要补齐的能力

1. **前端设计器** - 拖拽式仪表板/大屏设计器
2. **SQL Lab** - 交互式 SQL 开发环境
3. **更多图表** - 桑基图、热力图、树图等
4. **内容管理** - Collection、Revision、Verification
5. **通知渠道** - Slack、Webhook
6. **数据转换** - ETL/Transform 管道

### 8.3 建议优先级

| 阶段 | 目标 | 工作量 |
|------|------|--------|
| **Phase 1** | 前端设计器 + SQL Lab + 更多图表 | 3-6月 |
| **Phase 2** | Slack/Webhook + Collection + Revision | 2-3月 |
| **Phase 3** | Embedding SDK + ETL + 可视化查询 | 3-6月 |

---

## Open Questions

- [ ] nop-datav 的前端是否继续使用 Flux 还是考虑其他方案？
- [ ] SQL Lab 是否需要集成 nop-search 的全文搜索能力？
- [ ] ETL 功能是复用 nop-stream 还是新建模块？
- [ ] 是否需要支持 Superset 的 68+ 数据库引擎？

---

## References

- nop-metadata: 282 Java files, 39 entities, 168 test files
- nop-datav: 231 Java files, 17 entities (feat-nop-datav merged to master)
- Metabase: ~/sources/bi/metabase (Clojure)
- Superset: ~/sources/bi/superset (Python)
- `docs-for-ai/03-modules/nop-metadata.md`
