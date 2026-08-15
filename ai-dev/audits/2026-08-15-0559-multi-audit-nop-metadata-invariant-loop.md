> Audit Status: planned
> Audit Type: multi-dimensional
> Mission: nop-metadata-invariant-loop

# nop-metadata 多维度审计报告（multi-audit）

- **审核模块**: `nop-metadata/`（8 子模块：api/core/dao/meta/service/web/app/codegen；39 实体；~241 手写 main Java + 41 生成实体；139 测试文件）
- **审核日期**: 2026-08-15
- **审计基线**: live code 为准；owner 文档 `docs-for-ai/03-modules/nop-metadata.md` 为契约对照基线
- **方法论**: `ai-dev/skills/deep-audit-prompts.md` 全流程 —— 准备阶段（基线命令 + 文档）→ 阶段一（12 个维度并行初审子代理）→ 阶段二（3 个独立复核子代理对全部 P0/P1 候选逐条「成立/降级/驳回」，P0 经实机验证）→ 阶段三（本汇总）
- **执行维度**: 01 依赖图、02 模块职责、03 API 表面积、04 ORM 模型、05 生成管线、07 BizModel、08 IoC、09 错误处理、11 XMeta 对齐、13 安全权限、16+21 测试覆盖与有效性、18 文档-代码一致性

## 结论速览

| 优先级 | 数量 | 说明 |
|--------|------|------|
| **P0** | 1 | SSRF 校验绕过（F2 契约覆盖缺口，实机验证可利用） |
| **P1** | 9 | API 契约缺陷 ×4、错误码参数漂移家族 ×2、功能不可达 ×1、测试假绿 ×1、日志脱敏契约同族缺口 ×1 |
| **P2** | 35 | 记录在案，不驱动修复计划（含 3 条初审 P1 经复核降级项） |

初审共 15 个 P1 候选：12 个经独立复核维持，3 个降级至 P2（TagLabel UK NULL-distinct、IoC 双向循环、OrmModelImporter 驻留 dao——降级理由见 P2 节）。P0 候选 1 个经实机双端验证（校验层放行 + 驱动 jar 字节码确认实际连 loopback）维持。

---

## P0 发现（必须修复）

### [P0] F2 SSRF 防御绕过：MySQL hostless 属性组隐式 localhost + PostgreSQL query 参数 `host=` 不校验

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/connection/MetaDataSourceConnectionProcessor.java:364-462`（`extractHosts`/`extractSingleHost`/`extractHostKeyValue`/`extractPlainHost`）及 `:246-252`（F5 检查不覆盖 `host=`）
- **证据**:
```java
// :373-375 authority 截断于 '?' —— query string 完全不参与主机提取
int slash = rest.indexOf('/');
int q = rest.indexOf('?');
int end = minPositive(slash, q);
// :432-438 无 host= 键的属性组整段按"主机名"处理 → "(port=3306)" 原样返回（非空、过形状检查）
// :285-299 isPlausibleHostShape 首字符 '(' 不在任何拒绝分支 → 放行
```
- **复核验证（实机）**: 用 live 编译产物实调 `validateJdbcUrl`，4 个攻击向量全部放行：
  - `jdbc:mysql://(port=3306)/db` → PASS
  - `jdbc:mysql://address=(port=3306)/db` → PASS
  - `jdbc:postgresql://public.example.com/db?host=127.0.0.1` → PASS
  - `jdbc:postgresql://public.example.com:5432/db?host=169.254.169.254` → PASS（云元数据端点）
  驱动侧实机验证：MySQL Connector/J 9.2.0（本地 ~/.m2）对两个 hostless URL 解析主机为 **localhost:3306**（字节码确认空 host 走 `getDefaultHost()` 返回 `"localhost"`）；pgjdbc 42.7.9 `Driver.parseURL` 确认 query 中 `host=` **完全覆盖**（非追加）authority 主机。
- **现状**: owner 文档 F2 契约（"对 URL 中**每一**主机执行 isInternalHost"）存在两个驱动语义级覆盖缺口：(a) MySQL Connector/J 对不含 `host=` 键的属性组（`(port=3306)`/`address=(port=3306)`）默认 host=localhost，而 `extractHosts` 产出 `"(port=3306)"` 非空串——既过 `isPlausibleHostShape` 又过 `isInternalHost`（无前缀命中判外部）；(b) PG JDBC 官方支持以 URL query 参数 `host=` 指定/覆盖主机，而 authority 截断于 `?`，query 中的主机完全不经内网校验；`DANGEROUS_URL_TOKENS`（F5）不含 `host=`，无兜底。
- **风险**: 可登录用户即可利用（`testConnection`/`syncExternalTables` 为 `@BizMutation`，action-auth 平台默认关闭，创建自己的数据源即可触发建连）——SSRF 内网探测原语，绕过 F2 fail-closed 契约；`169.254.169.254` 向量可直连云元数据端点。仓库既有 F2 测试（`TestMetaDataSourceConnectionSecurity.java:351-413`）全部是显式带 `host=` 键的形态，未覆盖这两个变体。
- **建议**: (1) `extractSingleHost` 对含 `=`/`(`/`)` 的段在无 `host=` 键命中时显式抛 `ERR_DATASOURCE_JDBC_URL_BLOCKED`（hostless 属性组 = 隐式 localhost，fail-closed）；(2) query 部分提取 `host=`/`hostaddr=` 纳入逐主机校验，或最低限度将 query 中的 `host=`/`hostaddr=` 列入 `DANGEROUS_URL_TOKENS` over-block 拒绝；(3) 补两个向量的回归测试。
- **信心水平**: 确定（校验层放行 + 驱动实际连 loopback 双端实机验证）
- **误报排除**: 两种形态均为驱动官方文档支持的合法 URL 语法且真实建连；非"驱动拒绝的畸形 URL lucky fail-closed"情形。
- **复核状态**: 已复核（独立复核员实机验证，维持 P0）

---

## P1 发现（必须修复）

### [P1-1] NopMetaDataSource.connectionConfig 三层封死后无任何受控写路径，外部数据源功能经 API/UI 不可达

- **文件**: `nop-metadata/nop-metadata-meta/src/main/resources/_vfs/nop/metadata/model/NopMetaDataSource/NopMetaDataSource.xmeta:14-15`
- **证据**:
```xml
<prop name="connectionConfig" published="false" insertable="false" updatable="false" queryable="false" sortable="false"/>
<prop name="connectionConfigComponent" published="false" insertable="false" updatable="false"/>
```
- **现状**: 保留层为满足凭证脱敏把读**和写**一并锁死；`NopMetaDataSourceBizModel`（607 行）无 save 覆写、4 个 `@BizMutation`（testConnection/syncExternalTables/collectCatalog/collectCatalogForTable）全部只读 `getConnectionConfig()`；全仓 15 处 `setConnectionConfig` 全在测试代码；UI 生成层表单因 `tagSet="sensitive"` 排除该字段且保留层 view.xml 未补。三层同时封死：GraphQL 出口（published=false 不进 schema，`ObjMetaToGraphQLDefinition.java:61-62`）、写入口（insertable=false 经 `ObjMetaBasedValidator._validate:175-177` **静默丢弃**）、UI 表单。
- **风险**: `NopMetaDataSource__save` 创建的数据源永远没有连接配置（客户端传了也静默丢弃、零感知），testConnection/syncExternalTables/collectCatalog 及全部 external 表联邦查询/质量规则/血缘功能经暴露的 API/UI 面不可达，只能直连 DB 造数。保留层注释自述"表单配置 deferred"，属未完成的收紧而非有意设计。
- **建议**: 恢复 `insertable/updatable`（保留 `published="false"` 屏蔽读出口即可满足脱敏契约），或补带权限控制的专用 mutation + 保留层表单字段，二选一闭环。
- **信心水平**: 确定（功能链断裂经复核逐层验证，无绕过路径）
- **误报排除**: 与"BizModel 返回实体 + xmeta 控制可见性"标准模式不同——那是**读**可见性；此处是**写路径被整体消除**且无替代入口。
- **复核状态**: 已复核（维持 P1）

### [P1-2] owner 文档 I*Biz 契约清单漂移：列 9 个接口，实际 14 个非空接口（5 个含自定义 API 的接口未记载）

- **文件**: `docs-for-ai/03-modules/nop-metadata.md:181-191`；未列接口：`INopMetaDataProductBiz`、`INopMetaQualityResultBiz`、`INopMetaReconciliationConfigBiz`、`INopMetaReconciliationResultBiz`、`INopMetaTagLabelBiz`（dao `io.nop.metadata.biz` 包）
- **证据**:
```java
// INopMetaReconciliationConfigBiz.java:17-19（未入 doc 清单）
public interface INopMetaReconciliationConfigBiz extends ICrudBiz<NopMetaReconciliationConfig>{
    @BizMutation
    NopMetaReconciliationResult executeReconciliation(@Name("configId") String configId, IServiceContext context);
}
```
- **现状**: dao biz 包共 39 个 `INopMeta*Biz` 接口（另 1 个 NopMetaSearchBizModel 无接口为已裁定例外），其中 14 个含自定义方法（41 个方法）；文档"API 契约"节仅列 9 个。代码侧三方闭合（39 实体 ↔ 39 BizModel ↔ 39 接口，签名程序化比对零漂移）——缺陷在文档契约记载面。
- **风险**: 漏掉的恰是审批（approve/reject）、对账执行与人工确认（executeReconciliation/confirmMatch）、资产挂链（linkAsset）、标签传播（propagateTags/suggestTags）共 9 个公开 mutation——契约导航对这些 API 失效；以文档为基线的审计产生假阴性。
- **建议**: 5 个接口及方法补入清单（或改为全部非空接口完整列表）；同批补记 `NopMetaTagLabel.xbiz` approve/reject XPL 事实源。
- **信心水平**: 确定（计数与签名逐一核实；接口数 39、BizModel 40）
- **误报排除**: 该节自述为 API 契约对账入口（"声明全部自定义方法签名"），清单不完整即契约记载漂移。
- **复核状态**: 已复核（维持 P1）

### [P1-3] lineage `sourceTables` 字段双重契约缺陷：表级路径填入 unresolved（语义反转），列级/指标级路径恒为空

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaLineageEdgeBizModel.java:127-161`；`LineageExtractResultDTO.java:21`
- **证据**:
```java
// extractLineageFromSql（表级）:130
dto.setSourceTables(r.unresolved);      // ← unresolved（解析失败名单）填入 sourceTables
// extractColumnLineageFromSql（列级）:142-147 —— 只设 unresolved，sourceTables 保持默认空列表
```
- **现状**: 表级路径 `sourceTables` 装的是**未解析**引用列表（已解析源表只进内部 `candidateSourceIds`，仅以 count 返回）；列级/指标级路径从不填充。内部 `LineageExtractResult`（QueryAction:539-549）无 sourceTables 字段——resolved 源表在内部已计算（`candidateSourceIds`/`resolvedSourceIds`）却被丢弃。
- **风险**: owner 文档官方 GraphQL 示例（nop-metadata.md:99-103）恰是 `extractColumnLineageFromSql(...) { edgeCount sourceTables }`——按示例调用永远得到空列表；表级路径读到的语义反转。调用方无法从任何返回字段获取已解析源表。
- **建议**: `LineageExtractResult` 增加 resolved 载体，三条路径统一填充；移除 `setSourceTables(r.unresolved)` 误植。
- **信心水平**: 确定
- **误报排除**: 不是 P2-24/F4 已裁定家族——强类型 DTO 的字段填错数据源/恒空，owner 文档无对应 no-op 裁定且示例在消费它。
- **复核状态**: 已复核（维持 P1）

### [P1-4] `linkAsset`/`unlinkAsset` 不校验自身聚合根 dataProductId 存在性，且 entityId 同样不校验

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaDataProductBizModel.java:41-104`
- **证据**:
```java
@BizMutation
public NopMetaTagLabel linkAsset(@Name("dataProductId") String dataProductId, ...) {
    if (!LINKABLE_ASSET_TYPES.contains(entityType)) { throw ...; }   // 只校验 entityType 枚举
    // → labelDao.newEntity/saveEntity，全程无 requireEntity(dataProductId)
```
- **现状**: 两个方法挂在 NopMetaDataProduct 聚合根上但从不加载/校验该实体；任意（含不存在）dataProductId 均成功创建/删除 linkage 行（dataProductId 只进 metadata JSON 字符串，无 FK 兜底）；entityId 也仅靠 entityType 白名单。
- **风险**: 为不存在的产品静默创建孤儿 TagLabel 数据；unlinkAsset 对不存在产品静默成功；与同模块惯例对照（`executeReconciliation` requireEntity、`createSqlTable` requireEntityById、`recordLineage` 逐一校验）违反模块自身"引用非法 → 显式抛 NopException"契约。
- **建议**: 入口加 `requireEntity(dataProductId, "linkAsset"/"unlinkAsset", context)`。
- **信心水平**: 确定
- **误报排除**: 不是批量 per-row 隔离裁定例外（那是 syncExternalTables/executeCheckpoint 的模式）；此处是单对象入口的引用完整性校验缺失。
- **复核状态**: 已复核（维持 P1）

### [P1-5] `linkAsset` 直写 NopMetaTagLabel DAO 绕过 TagLabel BizModel save 管线，Automated 标签审批流行为分裂

- **文件**: `NopMetaDataProductBizModel.java:61-77`；对照 `NopMetaTagLabelBizModel.java:96-108`、`NopMetaGlossaryTermBizModel.java:109-115`
- **证据**:
```java
// DataProduct.linkAsset — labelDao 直写，手工 setState("Suggested")，不经 save 管线
labelDao.saveEntity(label);
// TagLabelBizModel.triggerApprovalIfNeeded:101 — Automated 明确在审批触发分支
} else if ("Derived".equals(labelType) || "Propagated".equals(labelType) || "Automated".equals(labelType)) {
    entity.setState("Suggested"); ... trySubmitForApproval(entity, context);
```
- **现状**: TagLabel 聚合根不变式要求 Automated 标签 `state=Suggested + trySubmitForApproval`，linkAsset 用 `labelDao.saveEntity` 绕过该管线；同模块 GlossaryTerm 对同类跨聚合创建走 `bizObjectManager().getBizObject("NopMetaTagLabel").invoke("save", ...)` 正确先例。
- **风险**: 配置审批流后，glossary 传播的 Derived 标签进审批、DataProduct 链接的 Automated 标签永不进审批——同一聚合根同一 labelType 族行为分裂；未来 save 管线新增校验对该路径静默失效。
- **建议**: 改为 bizObject invoke("save") 或注入 `INopMetaTagLabelBiz`（如需跳过审批须注释显式裁定）。
- **信心水平**: 很可能（行为分歧确定；"故意免审"意图无任何注释/文档证据）
- **误报排除**: 不是 raw-impl 注入已裁定例外（那条有 javadoc 明示）；此处是跨聚合写路径绕过属主 BizModel，无任何注释。
- **复核状态**: 已复核（维持 P1）

### [P1-6] 错误码识别性参数漂移家族：11 个 throw 点缺声明参数，用户可见错误消息渲染字面 `{placeholder}`

- **文件**: 代表点 `NopMetaTableJoinBizModel.java:164-166`（`{joinId}`）、`NopMetaReconciliationResultBizModel.java:159,165`（`{resultId}`）、`MetaTableQueryExecutor.java:132`（`{metaTableId}`）、`AggregationHelper.java:135`（`{metaTableId}`）、`CrossDbJoinMerger.java:171`（`{joinId}`）、`MetaTableFieldResolver.java:348/355/359/383`（`{elementIndex}`，同方法 :367 却传齐——自相矛盾）、`MetaTableReferenceResolver.java:87`、`MemoryFilterEvaluator.java:86`、`MetaTableFieldResolver.java:84`
- **证据**:
```java
// ErrorCode 描述: "{joinId} side={side} tableId={tableId}..."（JoinErrors.java:31-35）
throw new NopMetadataException(NopMetadataErrors.ERR_JOIN_TABLE_TYPE_NOT_ALLOWED)
        .param("metaTableId", metaTableId).param("side", side)   // ← {joinId} 未传
// 框架行为（ErrorMessageManager.java:143-153）:
if (!params.containsKey(name)) { return "{" + name + "}"; }      // 缺参 → 字面 {name} 留在最终消息
```
- **现状**: ErrorCode 描述占位符与 throw 点 `.param()` 键漂移，复核抽查 6/6 命中无一反例；GraphQL 错误响应 description 渲染 `joinId={joinId}` 等字面占位符，失败对象身份从消息中丢失。
- **风险**: 调用方/前端按消息定位"哪张表/哪条结果/哪个 join 失败"时得到字面占位符，诊断链断裂；同族成批出现表明系统性疏漏。
- **建议**: 每个 throw 点补齐声明参数（调用方上下文有值的一律下沉）；防御性 null 分支改用无必需占位符的错误码；建立 define 占位符 ↔ 常量 ↔ 调用点三方一致性校验防复发（invariant-loop 同族视角）。
- **信心水平**: 确定
- **误报排除**: 已排除 ARG 常量限定名误报与上游 e.param 增补包裹两类同类误报；与 P1-7 区分：本条是"漏传识别性参数"。
- **复核状态**: 已复核（维持 P1）

### [P1-7] 方言白名单门禁参数键错配：传 `databaseProductName`，占位符是 `{datasourceType}`，被拒产品名永不渲染

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/sync/ExternalTableStructureReader.java:139-143`；`DataSourceErrors.java:13-15`
- **证据**:
```java
throw new NopMetadataException(NopMetadataErrors.ERR_DATASOURCE_TYPE_NOT_SUPPORTED)
        .param(NopMetadataErrors.ARG_DATABASE_PRODUCT_NAME, String.valueOf(productName));
// ErrorCode 描述: "DataSource type not supported yet: {datasourceType}"，声明 ARG_DATASOURCE_TYPE
// 旁证：同码在 MetaDataSourceConnectionProcessor.java:216 传 .param("datasourceType",...) 用法正确
```
- **现状**: 对 ClickHouse/Oracle 等数据源执行 `syncExternalTables` 被方言白名单拒绝时，最终消息为 `...not supported yet: {datasourceType}` 字面量——被拒的真实产品名以错误键传入永不渲染；错误码命名（type）与触发量（databaseProductName）语义双重漂移。
- **风险**: 运维无法得知哪个数据库产品被拒；与 AR-23⑤ 裁定的"扫描故障（带真实 productName）vs 方言不支持可区分"契约形成不对称（另一侧带真实名）。
- **建议**: 改用 product-name 语义错误码（`{databaseProductName}`）或最低限度把参数键改为与占位符一致并渲染产品名。
- **信心水平**: 确定
- **误报排除**: 非 P1-6 简单重复：是"传了参数但键错配 + 错误码语义面与触发量不符"，且位于 AR-23⑤ 显式裁定的可诊断性契约路径上。
- **复核状态**: 已复核（维持 P1）

### [P1-8] sql 视图路径 8 处 INFO 级 SQL 全文落日志，与 AR-16 脱敏裁定系统性不一致

- **文件**: `MetaTableQueryExecutor.java:100`、`ExternalAggregationProcessor.java:84`（经 `SqlAggregationProcessor` 委托覆盖 sql 聚合路径）、`MixedSameDbJoinAggregationProcessor.java:160`、`MetaJoinExecutor.java:362/662`、`ExternalExternalJoinAggregationProcessor.java:125`、`MetaTableProfiler.java:484/495`
- **证据**:
```java
// MetaTableQueryExecutor.java:100（sql 路径——sql = "SELECT * FROM (<sourceSql>) _t ..."）
LOG.info("MetaTableQueryExecutor SQL: {}", sql);
// 对照 AR-16 已脱敏的同族路径（MetaQualityRuleExecutor.java:675-676）:
LOG.info("qualityRule SQL executed: sqlHash={}", sqlHashOf(sql));   // INFO 只记摘要
LOG.debug("qualityRule SQL: {}", sql);                              // 全文降 DEBUG
```
- **现状**: `queryTableData`/`queryJoinData`/`queryAggregation`/`profileTable` 的 sql（TABLE_TYPE_SQL）路径把用户视图 SQL（`NopMetaTable.sourceSql`，`createSqlTable` 时任意用户提供、可内嵌敏感字面量——与 AR-16 对 custom_sql 的裁定前提完全相同）以 INFO 级全文落日志，共 8 处（复核确认；初审列的 10 处中 MetaJoinExecutor.java:300 与 EntityEntityJoinAggregationProcessor.java:139 为 entity 路径仅含白名单标识符，属误列）。
- **风险**: AR-16 已确认该数据族"可内嵌敏感字面量"并据此脱敏日志面；sql 视图路径同族未同步——生产日志聚合面重复落盘放大泄漏。
- **建议**: 沿 AR-16 形态统一：INFO 级只记 `sqlHashOf(sql)`，全文降 DEBUG。
- **信心水平**: 确定
- **误报排除**: 仅针对 sql 路径（含 sourceSql 全文）；external/entity 路径 SQL 仅白名单标识符 + `?` 占位，落日志无害不计入。
- **复核状态**: 已复核（事实全部核验；复核建议 P1~P2，本报告按"已裁定契约同族缺口=契约漂移"定 P1，沿 R6.2/R8.2 先例补兄弟的裁定传统）

### [P1-9] INV-LIMIT 不变式守卫测试的 searchMetadata 行对"移除负 limit 检查"变异假绿（P-8 无效负面测试）

- **文件**: `nop-metadata/nop-metadata-service/src/test/java/io/nop/metadata/service/invariant/TestLimitNegativeValueInvariant.java:65-91`（关键 :68/:86）；`NopMetaSearchBizModel.java:66-86`
- **证据**:
```java
// 测试侧:68 仅断言异常类型；:86 直接 new（无 IoC 注入）
assertThrows(NopException.class, () -> invokeLimitHandler(limitHandler), ...);
case "inline-searchMetadata": new NopMetaSearchBizModel().searchMetadata(null, null, Integer.valueOf(-1), null);
// 被测侧:83-86（limit 检查之后）: searchEngine == null → 抛 ERR_SEARCH_ENGINE_UNAVAILABLE（NopException 子类）
```
- **现状**: 删除 `limit < 0` 检查（:68-72）后执行流落到 `searchEngine == null` 抛 NopMetadataException，`assertThrows(NopException.class)` 仍通过——该行对声明的防御目标区分力为零。该测试类是 CI invariant-gate 指定防线（javadoc 自述由 `run-nop-metadata-invariants.sh` 调用）。
- **风险**: 4 行表中 1 行空转；若 `TestNopMetadataSearchIntegration.java:147-155`（现有真守卫）未来被重构删除，INV-LIMIT 第 4 入径无有效防护且表面绿灯。
- **建议**: 断言精确错误码 `nop.err.metadata.search-limit-invalid`，或注入 mock engine 使唯一异常源为 limit 校验。
- **信心水平**: 确定（变异路径逐行核实）
- **误报排除**: 非"断言宽松但仍有部分捕获力"——变异后无任何路径变红。
- **复核状态**: 已复核（维持 P1）

---

## P2 发现（记录在案，不驱动修复计划）

| # | 发现 | 位置 | 降级/定级理由（一句话） |
|---|------|------|------------------------|
| P2-01 | 初审 P1 降级：TagLabel UK `(entityType,entityId,tagId,source)` 缺 glossaryTermId，GLOSSARY 来源行 NULL 互异不受唯一约束 | `nop-metadata.orm.xml:3372-3375`、`NopMetaTagLabelBizModel.java:66-87` | 复核：缺口真实但限 GLOSSARY 行重复累积（脏数据），全部内部传播路径有预检且带 tagId 受 UK 保护，触发面为显式构造的 save 调用——数据完整性缺陷而非核心不变式失效 |
| P2-02 | 初审 P1 降级：NopMetaQualityCheckpointBizModel ↔ MetaQualityCheckpointScheduler 双向 @Inject 真实环（@Nullable 不豁免依赖边，raw impl 注入不经 proxy） | `NopMetaQualityCheckpointBizModel.java:89-91`、`MetaQualityCheckpointScheduler.java:110-113` | 复核：默认 `allow-cycle=true` + 早期引用语义下零故障、初始化期无环上调用；仅未来严格模式配置下爆炸——架构债务而非现行可触发缺陷 |
| P2-03 | 初审 P1 降级：OrmModelImporter（253 行模型映射）驻留 dao 模块 | `nop-metadata-dao/.../dao/model/OrmModelImporter.java` | 复核：nop-auth-dao（mapper/delegate/generator）、nop-wf-dao（store）先例充分，职责紧贴 dao 实体无依赖倒挂；真实缺口是 owner 文档模块结构表漏列 model/ 子包 |
| P2-04 | KEYWORD_BLACKLIST 对函数调用形态（`REPLACE(`/`TRUNCATE(`）跳过关键字检查 | `ExpressionMeasureValidator.java:415-429, 480-500` | 复核：表达式上下文无 DML/DDL 逃逸路径（聚合包裹、`;`/注释已禁、参数化完整），REPLACE/TRUNCATE 函数形态是合法用法——hardening 项非漏洞 |
| P2-05 | NopMetaTable 等元数据实体无行级数据权限；外部数据可达性依赖 ORM 条件装配隐式过滤 | `nop-metadata.data-auth.xml:20-132`、`orm-defaults.beans.xml:60-65` | 复核：8 实体白名单是被 `TestDataAuthRowLevelScoping` 锁定的显式裁定；元数据共享 vs 数据隔离的边界待产品级裁定——补规则或文档化裁定 |
| P2-06 | webhook 侧主机提取无 F7 同款形状校验，null/畸形主机静默跳过 | `CheckpointActionDispatcher.java:299-350` | lucky fail-closed（非法 host 无法建连），防御纵深不对称项 |
| P2-07 | custom_sql 全文写入 QualityResult.details 落库，与 AR-16 日志脱敏语义不一致 | `MetaQualityRuleExecutor.java:318-325` | 持久化面 vs 日志面处理分歧；泄漏面条件性（取决于 result 读取权限） |
| P2-08 | SQLException 原始消息未过滤进 error param，驱动回显可击穿 jdbcUrl 脱敏 | `MetaDataSourceConnectionProcessor.java:634-639` | 低概率条件泄漏（仅 userinfo 形式 + 驱动回显时触发） |
| P2-09 | 6 个 throw 点缺 `{error}` 附注参数，描述尾部渲染字面 `-- {error}`（cause 链保留） | `NopMetaTagLabelBizModel.java:138` 等 6 处 | 与 P1-6 同族但仅附注性参数缺失，识别性参数齐备、信息不丢 |
| P2-10 | 17 个 ErrorCode define 声明参数与描述占位符不一致 + 2 个死错误码 | `LineageErrors.java:44-48`、`MiscErrors.java:215-218` 等 | 运行时未断裂（throw 点用字面量键绕过）；声明面契约漂移 |
| P2-11 | `.param()` 键 454 处字面量 vs 199 处 ARG_* 常量双轨混用 | 全模块 | 当前键值一致无错配；是 P1-6/P2-10 漂移得以潜伏的结构土壤 |
| P2-12 | ErrorCode 描述全英文且 i18n 零覆盖，与 error-handling.md「define 描述用中文」规则冲突 | 227 处 define vs `_vfs/i18n/{zh-CN,en}/nop-metadata.i18n.yaml`（零命中） | 文档-代码语言契约冲突需 ask-first 裁定后单向收敛 |
| P2-13 | `throw new SQLException` 作方法内控制流哨兵，与模块自身裁定惯例相悖 | `MetaQualityRuleExecutor.java:549-555, 594-598` | 无泄漏无静默（同 try 内捕获+LOG+ERROR 判定），形态一致性问题 |
| P2-14 | 并发拒绝降级 WARN 未把异常对象作为 logger 末参数 | `MetaQualityCheckpointScheduler.java:220-229` | R4.3 裁定内容合规，仅形式违约 |
| P2-15 | owner 文档 DTO 计数漂移：两处宣称 31 个 @DataBean，实际 30 个 | `docs-for-ai/03-modules/nop-metadata.md:207,259` | 计数失真（`find -name "*.java" \| wc -l` = 30，全 @DataBean） |
| P2-16 | `_templates/README.md` 文件计数 32 vs 实际 39（+1 共 40 JSON） | `nop-metadata/nop-metadata-meta/_templates/README.md:7` | 实体从 32 增至 39 后说明未同步 |
| P2-17 | TestNopMetaBizInterfaceCompleteness 覆盖 12/14 非空接口且断言强度不足（仅方法名+参数数下限） | `TestNopMetaBizInterfaceCompleteness.java:40-151` | 缺 2 个 Reconciliation 接口；无注解/@Name/返回类型断言；当前无实际漂移（程序化强校验通过） |
| P2-18 | `testConnection` 只读探测标注 `@BizMutation`（接口+实现一致错） | `NopMetaDataSourceBizModel.java:119-120` | 无写操作是代码事实；同模块 judgeByRuleId/checkContractReadOnly 同类探测均正确用 @BizQuery |
| P2-19 | 6 个 save override 缺 null-data 防护，NPE 抢先于基类 `ERR_BIZ_EMPTY_DATA_FOR_SAVE` | `NopMetaTagLabelBizModel.java:68` 等 6 处 | null 可达性依赖入口层；同模块 3 个 override 有防护，两种行为并存 |
| P2-20 | CheckpointExecutionResultDTO.executionResults/executionErrors 为 List<Map> 且与 ruleResults 数据重复 | `CheckpointExecutionResultDTO.java:31-32` | 不在 P2-24 例外内（条目结构固定且类型化版本已并存） |
| P2-21 | DataProduct 三方法手工字符串拼接 JSON（无转义） | `NopMetaDataProductBizModel.java:51,86,109` | 非常规 ID 导致 JSON 损坏+匹配失真；应统一 JsonTool |
| P2-22 | NopMetaQualityResultBizModel.approve 为无字段变更的 no-op updateEntity（javadoc 声称重新判定） | `NopMetaQualityResultBizModel.java:19-31` | wf 回调入口"看起来成功"但无效果；真实 re-judge 在工作流侧 |
| P2-23 | NopMetaReconciliationResultBizModel 死代码 toInt/toStr + 死错误码分支 | `NopMetaReconciliationResultBizModel.java:154-172` | 引入类型化 DTO 后遗留 |
| P2-24 | computeQualityScore 以空 lambda 调 doSave，绕过 xbiz 可覆盖的 defaultPrepareSave | `NopMetaQualityScoreBizModel.java:52` | cron 自动评分链路绕过宿主 xbiz 定制 |
| P2-25 | queryJoinData（6 参）/queryAggregation（10 参）超出 5 参数规则未用 @RequestBean | `NopMetaTableBizModel.java:268-305` | 签名是 AR-09/F4 裁定契约，改动即破坏对外契约——应为文档裁定例外 |
| P2-26 | NopMetaQualityResult.checkpointId 为无关系弱引用，checkpoint 删除产生孤儿结果行（与 rule→results 级联不对称） | `nop-metadata.orm.xml:2096-2109` | 脏数据非损坏；是否保留历史需裁定 |
| P2-27 | NopMetaTableJoin 源模型注释陈旧：描述已被 D1 裁定推翻的行级互斥不变式 | `nop-metadata.orm.xml:1687-1693` | 源模型注释会被 codegen 读者持续消费 |
| P2-28 | NopMetaBusinessDomain UK (parentDomainId,name) 对根域重名不生效（NULL-distinct） | `nop-metadata.orm.xml:3443-3447` | 仅根域失守，导航歧义 |
| P2-29 | NopMetaGlossaryTerm/NopMetaTag 双重 UK 冗余（全局 FQN UK 已蕴含 per-parent UK） | `nop-metadata.orm.xml:3073-3078, 3239-3244` | 冗余索引 + 语义漂移陷阱 |
| P2-30 | save override Javadoc 与实际注入方式自相矛盾（声称 tryGetBean 懒查找避环，实为直接 @Inject） | `NopMetaQualityCheckpointBizModel.java:259-265` | 两段 Javadoc 描述互斥设计，误导 P2-02 修复决策 |
| P2-31 | Scheduler BEAN_NAME 注释引用不存在的 beans 文件 | `MetaQualityCheckpointScheduler.java:83-84` | 实际注册于 app-service.beans.xml |
| P2-32 | NopMetaSearch.xmeta 使用未声明的 i18n-en 命名空间前缀 + 无 i18n 抽取配套 | `NopMetaSearch.xmeta:2-12` | 运行时可加载（非 namespace-aware 解析器）但按 XML 规范畸形 |
| P2-33 | 审批流/状态机字段未收紧 updatable，标准 update 可绕过保留层守卫（TagLabel/DataContract/QualityResult 三处同族） | `_NopMetaTagLabel.xmeta:44`、`_NopMetaDataContract.xmeta:103` 等 | 与平台基线一致（nop-wf 同款默认）；依赖 update 级 RBAC 兜底，应显式裁定 |
| P2-34 | 2 个 dict 声明零引用（quality-trend-direction/checkpoint-action-type，语义载体是 JSON 列无法挂载） | `nop-metadata.orm.xml:111,124` | 死元数据，无运行时影响 |
| P2-35 | 杂项：AggregationHelper 938 行混装通用工具与领域逻辑；nop-search-lucene compile+optional 应为 test；io.nop.wf.api 使用未显式声明 nop-wf-api；NopMetaSearch.xmeta/.xwf 未入 owner 文档模块结构表；source-anchors META-001 行数 268→274；TestAggregationHelper 无 @Test 虚增计数；TestLimitTargetSetCompleteness 计数正则单一形态；TestCoreMetricsUsage 注释剥离正则假阴性洞；TestNopMetadataErrorsCentralized 镜像断言；testCrossDbAliasOf 仅 assertNotNull；TestNopMetaDtoResults 首方法无 parse-back | 各处 | 单项均为琐碎打磨，聚合记录 |

---

## 执行统计

| 维度 | 初审发现 | 复核结果 |
|------|---------|---------|
| 01 依赖图 | P2×2 | 维持（并入 P2-35） |
| 02 模块职责 | P1×1, P2×4 | P1 降级 P2（P2-03）；其余维持 |
| 03 API 表面积 | P1×1, P2×2 | P1 维持（P1-2）；P2 维持（P2-15/P2-17） |
| 04 ORM 模型 | P1×1, P2×4 | P1 降级 P2（P2-01）；其余维持（P2-26~29） |
| 05 生成管线 | P2×2 | 维持（P2-16；P2-15 去重） |
| 07 BizModel | P1×3, P2×8 | P1 全维持（P1-3/4/5）；P2 维持（P2-18~25） |
| 08 IoC | P1×1, P2×2 | P1 降级 P2（P2-02）；P2 维持（P2-30/31） |
| 09 错误处理 | P1×2, P2×6 | P1 全维持（P1-6/7）；P2 维持（P2-09~14） |
| 11 XMeta 对齐 | P1×1, P2×3 | P1 维持（P1-1）；P2 维持（P2-32/33/34） |
| 13 安全权限 | **P0×1**, P1×3, P2×3 | P0 维持（实机验证）；P1×1 维持（P1-8）、×2 降级 P2（P2-04/05）；P2 维持+新增（P2-06/07/08） |
| 16+21 测试 | P1×1, P2×6 | P1 维持（P1-9）；P2 维持（并入 P2-35） |
| 18 文档一致性 | P2×1 | 维持（并入 P2-35） |

**复核统计**: 初审 P0×1 → 维持 1；初审 P1×14 → 维持 P1×9、降级 P2×5（含 13-2/13-4/04-01/08-1/02-1）；初审 P2×~28 → 维持（去重合并后 35 项含降级流入）。

## 总评

nop-metadata 的**生成管线纪律、安全契约实现（8/9 项方法级一致）、测试体系（>93% 高区分性行为测试、全部裁定契约点有可定位回归）质量显著高于常见基线**：39 实体 ↔ 39 BizModel ↔ 39 接口三方闭合、78 xbiz 双层机制无手改痕迹、UK↔DDL constraint 37=37 三方言闭环、owner 文档与代码一致性极高。

本批发现集中在三类：(1) **F2 SSRF 防御的两个驱动语义级绕过**（P0，实机验证）——校验层实现与契约一致但契约覆盖面被驱动语义击穿，且测试防线全部 misses 这两个变体；(2) **API 契约语义缺陷族**（sourceTables 反转/恒空、connectionConfig 写路径三层封死、linkAsset 绕过聚合根校验与审批管线、I*Biz 文档清单缺 36%）——代码侧三方闭合但公开契约的语义/记载面有真实缺口；(3) **错误码识别性参数漂移家族**（11 处漏传 + 1 处键错配）——`.param()` 字面量/常量双轨制（70% 字面量）是该族潜伏的土壤，建议修复后建立占位符↔常量↔调用点三方一致性校验。

## 优先修复建议

1. **P0（立即）**: `extractSingleHost` 对 hostless 属性组 fail-closed + query `host=`/`hostaddr=` 纳入校验或 blocklist + 回归测试（4 个实机验证向量可直接入测试）。
2. **P1 批次**: P1-1（connectionConfig 写路径，功能不可达）→ P1-3/4/5（lineage sourceTables 与 DataProduct 聚合根）→ P1-6/7（错误码参数机械补齐 + 三方一致性校验）→ P1-8（sqlHash 化 8 处）→ P1-2/P1-9（文档清单与守卫测试补强）。
3. **P2**: 按 backlog 排期；P2-01/02/05 三条降级项含裁定需求（UK 列集、IoC 严格模式、数据权限边界），建议在下一轮 invariant-loop 派生时优先裁决。

## 本次审核盲区自评

- 未运行完整 `./mvnw test`（静态审计 + 既有测试代码阅读为主；P0 复核以 target/classes 实机调用替代）；未审计 AutoTest 快照数据内容本体。
- web 层页面（view/page.yaml）与 amis 渲染细节仅经生成管线维度抽查，未逐页面审计。
- deploy/sql 仅核对 UK constraint 与三方言存在性，未做全列 DDL diff。
- 驱动语义验证限于本地 ~/.m2 的 mysql-connector-j 9.2.0 / pgjdbc 42.7.9；其他驱动版本（MariaDB、Oracle JDBC）的等价语义未逐一验证。
- 维度 22（工作流/审批流语义）未独立执行（3 个 xwf 仅在维度02/11 顺带触及）；建议下轮 invariant-loop 派生时补。
