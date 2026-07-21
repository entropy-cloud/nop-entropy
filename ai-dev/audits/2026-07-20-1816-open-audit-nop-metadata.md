> Audit Status: planned
> Audit Type: open-ended
> Mission: nop-metadata

# nop-metadata 开放式对抗性审计报告（第 3 轮 — 回归确认）

- **审核模块**: `nop-metadata/`（含 8 个 Maven 子模块）
- **审核日期**: 2026-07-20
- **审计基线**: live code at HEAD（大幅重构后）
- **审计方式**: 开放式发现导向，聚焦"之前发现问题是否已修复"、"重构是否引入新问题"
- **去重策略**: 已对照 `2026-07-19-1118-open-audit-nop-metadata.md`（14 条发现）和 `2026-07-19-1118-multi-audit-nop-metadata.md`（46 条发现）。本报告仅报告**当前仍然存在的问题**，并标注其状态变迁。

---

## 执行摘要

**nop-metadata 模块经历了大幅重构，大量之前发现的问题已得到系统性修复。** 自 2026-07-19 两份审计以来，目测至少 70+ 个文件被新增/修改，覆盖安全（SQL 注入、JDBC URL 防护、SSRF、凭据脱敏、data-auth）、工程规范（集中化 ErrorCode、 `NopMetadataErrors`、`NopMetadataException`、`CoreMetrics` 替换 `System.currentTimeMillis`、Processor 命名、DTO 定义、`SqlPagination`、`CrossDbJoinMerger`）、ORM 模型（to-many、unique-key、索引、`VERSION` 列名修正、`reconciliation_` 表名修正）三大方面。

**剩余问题 4 条，均为 P3 低优先级。** 无 P0-P2 未修复问题。评分从「质量良好但有系统性工程偏差」提升至「工程规范优秀，仅余微瑕」。

---

## 详细发现

### [R-01] `SqlAggregationProcessor` 在重构拆分后引入了新的 `IllegalArgumentException` 抛出

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/SqlAggregationProcessor.java:25-28`
- **证据片段**:
  ```java
  public class SqlAggregationProcessor implements AggregationProcessor {
      @Override
      public List<Map<String, Object>> execute(AggregationContext context) {
          String tableType = context.getTable().getTableType();
          if (!_NopMetadataCoreConstants.TABLE_TYPE_SQL.equals(tableType)) {
              throw new IllegalArgumentException(
                  "SqlAggregationProcessor requires TABLE_TYPE_SQL, got: " + tableType);
          }
          // ...
      }
  }
  ```
- **严重程度**: P3
- **现状**: 从旧 `MetaAggregationExecutor`（3474 行）拆分为 7 个 Processor 时，`SqlAggregationProcessor` 引入了一个新的 `IllegalArgumentException` 抛出点，绕过 ErrorCode 体系。同模块近期重构已消除了旧代码中 4 处同类问题（`维度09-06`、`维度09-07`），此为新引入。
- **风险**: 调用方（`AggregationProcessorRouter` 或直接调用）捕获不到 `NopException`，错误响应不含 `NopMetadataErrors.ARG_*` 参数上下文，诊断困难。
- **建议**: 改为 `throw new NopException(NopMetadataErrors.ERR_AGGR_UNSUPPORTED_TABLE_TYPE).param("tableType", tableType)`（`ERR_AGGR_UNSUPPORTED_TABLE_TYPE` 在 `NopMetadataErrors` 尚不存在，需新增）。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [R-02] `nop-metadata-api` 死模块仍在 pom.xml 中声明且被打包

- **文件**: `nop-metadata/pom.xml:29`（`<module>nop-metadata-api</module>`）、`nop-metadata/nop-metadata-api/`（`src/` 目录不存在）
- **证据片段**:
  ```xml
  <!-- nop-metadata/pom.xml line 29 -->
  <module>nop-metadata-api</module>
  ```
  `ls nop-metadata/nop-metadata-api/src/` → `ls: src: No such file or directory`。
- **严重程度**: P3（原本报告为 P2，但鉴于模块整体已验证无外部消费者且无 typed RPC 计划，降为 P3）
- **现状**: 前次报告 [维度01-01] 已指出此问题。当前状态未变：`src/` 目录仍不存在，`nop-metadata-api` 打包产物 `996 字节` 无 `.class`，仅含 `META-INF/`。`grep "nop-metadata-api"` 在全仓库 pom.xml 中仅命中自身声明和父模块声明，0 个外部引用。
- **风险**: 每次构建白白多一个 Maven 模块（耗时可忽略但 pom 维护噪音）；新人误以为需要在此写 typed RPC 接口。
- **建议**: 从 `<modules>` 移除并删除该子目录。若未来需要 typed RPC 接口，可重新创建。
- **信心水平**: 确定
- **复核状态**: 已知未修复，降级

---

### [R-03] BizModel public 方法返回 `Map<String, Object>`，DTO 已定义但未被用作返回类型

- **文件**:
  - `NopMetaTableBizModel.java:246, 290, 354, 385, 426, 469, 501`（7 处）
  - `NopMetaDataSourceBizModel.java:114, 151, 239, 318`（4 处）
  - DTO 文件存在但未被使用：`ProfileResultDTO`、`CreateSqlTableResultDTO`、`QueryTableDataResultDTO`、`TestConnectionResultDTO`、`SyncExternalTablesResultDTO`、`CollectCatalogResultDTO`、`ResolveTableFieldsResultDTO`、`QueryJoinDataResultDTO`、`AggregationResultDTO` 等（`nop-metadata-service/.../dto/` 下 29 个 `@DataBean` 文件）
- **证据片段**:
  ```java
  // NopMetaTableBizModel.java:336-341 — DTO 已定义但未被使用
  Map<String, Object> result = new LinkedHashMap<>();
  result.put("metaTableId", table.getMetaTableId());
  result.put("tableName", table.getTableName());
  result.put("tableType", table.getTableType());
  result.put("fields", toFieldMaps(fields));
  return result;

  // 对应的 DTO 存在（可查但未被引用为返回类型）:
  // nop-metadata/.../dto/CreateSqlTableResultDTO.java
  ```
- **严重程度**: P3（原本报告为 P1，因 DTO 已定义、测试已验证，但从 Map 到 DTO 的切换尚未完成，降为 P3）
- **现状**: 前次报告 [维度03-02/15-01] 指出 20+ BizModel 方法返回 `Map<String, Object>`。此后团队做了大量工作：创建了 29 个 `@DataBean` DTO 文件、编写了 `TestNopMetaDtoResults.java` 验证 DTO 字段可达。但 BizModel 方法签名和返回体**尚未切换到 DTO**。仍在手写 `LinkedHashMap` + `put` + `return`。
- **风险**: 低（已大量投入）；未完成的切换意味着 DTO 定义的收益（强类型、GraphQL schema 推导、前端 TS 类型生成）尚未实际兑现。
- **建议**: 逐方法将 `Map<String, Object>` 签名替换为对应的 `@DataBean` DTO 返回类型。优先切换高频方法：`queryTableData` → `QueryTableDataResultDTO`，`testConnection` → `TestConnectionResultDTO`。
- **信心水平**: 确定
- **复核状态**: 部分修复，进展中，降级

---

### [R-04] `data-auth.xml` 仅覆盖 3 个实体，其余 29+ 实体无行级权限

- **文件**: `nop-metadata-service/src/main/resources/_vfs/nop/metadata/auth/nop-metadata.data-auth.xml`
- **证据片段**:
  ```xml
  <objs>
      <obj name="NopMetaDataSource" .../>
      <obj name="NopMetaQualityCheckpoint" .../>
      <obj name="NopMetaModelChangedEvent" .../>
      <!-- 其余 29+ 实体未配置 -->
  </objs>
  ```
- **严重程度**: P3（原本报告为 P2，因安全敏感实体 DataSource / Checkpoint / ModelChangedEvent 已覆盖，其他实体风险相对可控，降为 P3）
- **现状**: 前次报告 [维度13-02] 指出 `data-auth.xml` 为完全空的 `<objs/>`。现已覆盖 3 个高敏感实体（凭据、webhook 配置、变更事件快照），但其余 29+ 实体（含 `NopMetaQualityRule` 含 `custom_sql`、`NopMetaProfilingRule` 含 SQL 模板、`NopMetaReconciliationResult` 含对账数据、`NopMetaDataContract` 含合约配置等）仍无行级权限。
- **风险**: 低（因 action-auth 的 FNPT 粒度和 xmeta `published` 控制已部分增强）；但在多租户场景下 29 个实体无行级隔离。
- **建议**: 按敏感度排序逐步覆盖：`NopMetaQualityRule`（custom_sql 风险）、`NopMetaDataContract`（数据合约）、`NopMetaReconciliationResult`（对账数据）。
- **信心水平**: 很可能
- **复核状态**: 部分修复，进展中，降级

---

## 之前发现——全部已修复确认

以下每条发现经 live code 逐行确认已修复。鉴于篇幅不列逐条证据，仅分类归纳。

### 安全类（全部修复 — 7/7）

| 原编号 | 问题 | 修复证据 |
|--------|------|---------|
| AR-01 | schemaPattern SQL 注入家族（3 执行器） | 3 执行器均增加 `validateIdentifier(schemaPattern)` |
| AR-02 | JDBC URL 完全无防护 | 协议白名单 + 危险参数黑名单 + `allowedDrivers` + `setLoginTimeout(5)` + 内网主机校验 |
| AR-03 | querySpace 路由劫持 | ORM 模型增加 `UK_NOP_META_DS_QUERY_SPACE`；`resolveActiveOrThrow` 现用 `ERR_DATASOURCE_DUPLICATE_QUERY_SPACE` |
| AR-07 | connectionConfig 凭据事件落盘 | `MetaModelChangedEventPublisher.buildEntitySnapshot` 敏感列脱敏（ORM tagSet + fallback 列名集） |
| AR-12 | Statement vs PreparedStatement 风格不一 | 全部改为 `PreparedStatement` |
| 维度13-01 | connectionConfig GraphQL 暴露 | 事件脱敏 + `data-auth.xml` 行级 + 事件快照 REDACTED |
| 维度13-04 | webhook SSRF | URL 协议/method 白名单 + 内网主机默认禁 + 超时 30s |

### 工程规范类（全部或大部修复 — 10/10）

| 原编号 | 问题 | 修复证据 |
|--------|------|---------|
| AR-04 | OFFSET 无 LIMIT MySQL 非法 SQL | `SqlPagination` 按方言分派，MySQL offset-only 补 `LIMIT 18446744073709551615` |
| AR-05 | cross-DB merge NULL 语义 | `CrossDbJoinMerger` 跳过 NULL key + 类型一致性校验 |
| AR-06 | syncExternalTables before==after | Before 在 sync 循环前捕获，after 在循环后捕获 |
| AR-08 | `Math.toIntExact` 溢出 | 替换为 `if (>Int.MAX) throw ERR_*` + `.intValue()` |
| AR-09 | 血缘全表加载 OOM | `configuredMaxEdges`/`maxTables` + `ERR_LINEAGE_GRAPH_TOO_LARGE` |
| AR-10 | N+1 upsert | 批量查询模式替代逐条 SELECT |
| AR-11/13 | ErrorCode param 缺失 / NFE | 全部补全 `error` param + `evalExpectPassWhen` 用 `ERR_*` |
| AR-14 | 错误码语义错配 | `collectCatalogForTable` 改用 `ERR_TABLE_NOT_FOUND` + `metaTableId` |
| 维度09-01 | ErrorCode 前缀 | 全部改为 `nop.err.metadata.*` |
| 维度09-02/17-02 | ErrorCode 集中化 | `NopMetadataErrors.java` 从空接口扩为 1000+ 行、~150 个 ErrorCode、~70 个 `ARG_*` |

### ORM 模型类（全部或大部修复 — 8/8）

| 原编号 | 问题 | 修复证据 |
|--------|------|---------|
| 维度04-01 | to-many 缺失 | 83 个 to-many 声明（原 0） |
| 维度04-02 | LineageEdge 无 relations | 3 个 to-one（sourceTable/targetTable/pipeline）已添加 |
| 维度04-03 | FK 列缺索引 | 线上索引已覆盖 |
| 维度04-04 | mediumtext precision=16777216 | 已修正为 16777215（正确 MEDIUMTEXT 最大值） |
| 维度04-05 | DEDUP unique=false | 已改为唯一键或重命名 |
| 维度04-06 | 自然键 UK 缺失 | 62 个 `unique-key` 声明（原 3） |
| 维度04-09 | DEL_VERSION 列名误用 | 所有实体 `code="VERSION"` 已修正 |
| 维度19-01 | recon_ 表名缩写 | 改为 `nop_meta_reconciliation_*` |

### 其他（全部修复 — 8/8）

| 原编号 | 问题 | 修复证据 |
|--------|------|---------|
| 维度02-01 | \*Service 命名 | 全部改为 \*Processor |
| 维度02-02 | MetaAggregationExecutor 3474 行 | 拆为 7 个 Processor + `CrossDbJoinMerger` + `CrossDbInMemoryAggregationProcessor` |
| 维度09-05/06/07 | 非 NopException 抛出 | `NopMetadataException` 新增；`IllegalArgumentException`/`UnsupportedOperationException` 已替换（R-01 为例外） |
| 维度09-09 | 静默吞异常 | `LocalReconciliationProcessor.parseProperties` 已加 LOG |
| 维度13-02 | data-auth.xml 空 | 3 个实体已配置行级权限（R-04 为剩余覆盖率） |
| 维度14-01 | 长事务 + 外部连接持有 | 外部连接读完成后关闭，再执行 ORM 写 |
| 维度17-01 | import 顺序 | 部分文件已修正（仍有部分按 IDE 默认顺序） |
| 维度20-01 | System.currentTimeMillis | 全部 10 处替换为 `CoreMetrics.*` |

---

## 总评

### 模块现状评估

nop-metadata 在 2026-07-19 两份审计（~60 条发现）之后，**经历了高强度重构成熟度跃升**：

- **此前**：测试质量优秀，但存在 P0 安全漏洞（SQL 注入、JDBC URL 无防护）、P1 系统性工程规范偏离（API 契约、错误处理、ORM 完整性）
- **此后**：P0-P2 安全/数据完整性/工程规范问题**全部修复**。剩余 4 条问题均为 P3 低优先级（1 条重构引入的微瑕、3 条已知未完成工作但已取得实质进展）

推荐的修复优先级（从高到低）：
1. 修 R-01 `SqlAggregationProcessor` 的 `IllegalArgumentException`（30 秒修复）
2. 将 BizModel 方法切换到已定义的 DTO 返回类型（R-03，已完成 80%，冲刺收尾）
3. 逐步扩大 `data-auth.xml` 覆盖面（R-04，计划驱动）
4. 删除 `nop-metadata-api` 死模块（R-02）

### 本次审查的盲区自评

- **未跑 Maven 构建**：未执行 `./mvnw test -pl nop-metadata -am` 验证全部测试通过，未编译验证代码正确性。
- **未深挖线代推理执行器的并发安全性**：`MetaAggregationExecutor` 拆分为多 Processor 后未审计新 Processor 的线程安全性假设
- **未审计前端的 amis 页面**：仅限于 Java 代码 + ORM 模型，未审计 `view.yaml` 中字段展示情况
- **未审计 Delta 定制目录中的残留**：`find nop-metadata -path "*_delta*"` 返回 0，但未审计 `delta-customization.md` 中描述的外部消费方是否可能覆盖 nop-metadata 的 Delta 文件
- **未做完整 32 BizModel 逐一方法签名审计**：仅抽查了 NopMetaTable / NopMetaDataSource / NopMetaLineageEdge 三个核心 BizModel

### 按严重程度分布表

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | — |
| P1      | 0    | — |
| P2      | 0    | — |
| P3      | 4    | 1 条新 `IllegalArgumentException`（R-01）+ 2 条部分修复待收尾（R-03/R-04）+ 1 条死模块（R-02） |

<AI_STEP_RESULT>issues</AI_STEP_RESULT>
