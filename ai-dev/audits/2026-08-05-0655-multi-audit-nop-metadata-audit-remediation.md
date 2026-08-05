> Audit Status: planned
> Audit Type: multi-dimensional
> Mission: nop-metadata-audit-remediation
> Processed: 2026-08-05 — P1-01/P1-02 → plan `ai-dev/plans/nop-metadata-audit-remediation/2026-08-05-1842-1-ssrf-host-normalization-unification.md`；P1-03 → plan `2026-08-05-1842-2-lineage-api-explicit-error.md`；P1-04/05/06 → plan `2026-08-05-1842-3-doc-drift-and-audit-tracking.md`；P2-01~27 → roadmap `## Follow-up Backlog`（`ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`）

# 多维度审计报告 — nop-metadata（mission: nop-metadata-audit-remediation）

## 基本信息

- **审核模块**: nop-metadata 模块组（api / core / dao / codegen / meta / service / web / app，39 实体，387 Java 文件）
- **审核日期**: 2026-08-05
- **执行维度**: 01 依赖图与模块边界 / 03 API 表面积与契约一致性 / 07 BizModel 规范 / 09 错误处理与错误码 / 13 安全与权限 / 16+21 测试覆盖与有效性 / 18 文档-代码一致性 / 20 跨模块契约一致性
- **审核范围**: nop-metadata/ 全部代码、配置（beans.xml/pom.xml/orm.xml/xmeta/xbiz）、测试（98 文件 868 测试方法）、公开契约（31 DTO + 40 INopMeta*Biz + GraphQL 方法）、docs-for-ai 交叉核对
- **方法**: 6 路并行初审（deep-audit-prompts.md 共享前缀 + 维度正文）→ 4 路独立复核（live code 重读）+ 主 agent 机械基线（死错误码脚本、黑名单核对）→ 汇总

## 执行统计

| 维度 | 初审发现数 | 独立复核 | 保留 | 降级 | 驳回 |
|------|-----------|---------|------|------|------|
| 01 依赖图与模块边界 | 3 | 1（含于文档复核） | 0 | 3 | 0 |
| 03+20 API 契约 | 3 | 1 | 2（P2） | 1 | 0 |
| 07+09 BizModel+错误处理 | 10 | 1 | 10 | 0 | 0 |
| 13 安全与权限 | 6 | 1 | 5 | 1 | 0 |
| 16+21 测试覆盖 | 8 | 1 | 8 | 0 | 0 |
| 18 文档-代码一致性 | 4 | 1 | 4 | 0 | 0 |

## 按严重程度分布

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | — |
| P1 | 6 | 安全（SSRF 变体归一化 ×2）、血缘公开 API 契约漂移、文档契约漂移 ×3 |
| P2 | 27 | 死错误码/死 DTO、异常链/日志缺失、测试镜像断言、行号漂移 |

---

## P1 发现（必须修复）

### [P1-01] [维度13] MetaDataSourceConnectionProcessor.isInternalHost 缺少 IP 记法变体归一化 —— JDBC 建连 SSRF 绕过

- **文件**: `nop-metadata-service/src/main/java/io/nop/metadata/service/connection/MetaDataSourceConnectionProcessor.java:343-375`
- **证据片段**:
  ```java
  private static boolean isInternalHost(String host) {
      String h = host.toLowerCase();
      if ("localhost".equals(h) || h.endsWith(".localhost")) { return true; }
      if (h.equals("127.0.0.1") || h.startsWith("127.")) { return true; }
      if (h.startsWith("10.") || h.startsWith("192.168.")) { return true; }
      if (h.startsWith("172.")) { ... }
      if (h.startsWith("169.254.")) { return true; }
      if ("::1".equals(h) || "0:0:0:0:0:0:0:1".equals(h)) { return true; }
      return false;
  }
  ```
- **严重程度**: P1 — 安全：JDBC 路径内网判定可被数字变体绕过，属已修复问题（dispatcher 侧 MA7.6-04）的未同步变体
- **现状**: 纯字符串前缀比对，无十进制整数/前导零/0.0.0.0/8 短格式/IPv4-mapped 归一化。`extractHost`（L269-298）仅处理 userinfo 与带括号 `[::ffff:x.x.x.x]`。同模块 `CheckpointActionDispatcher.isInternalHost`（L308-378）已含 MA7.6-04 全套归一化，且其 L301 注释声称"与 MetaDataSourceConnectionProcessor 一致"——**注释漂移，实际不一致**（git 验证：MA7.6-04 提交 9b769490e 只修了 dispatcher）。
- **风险**: 拥有数据源配置权限的用户可绕过内网判定触达内网 DB 服务（DB 型 SSRF）：
  - `jdbc:mysql://2130706433:3306/db` → Java `InetAddress` 解析为 127.0.0.1
  - `jdbc:mysql://0.1:3306/db`、`0.256` → 0.0.0.0/8（等效 localhost）
  - `jdbc:mysql://0169.254.169.254:3306/db`（前导零）→ 169.254.169.254（云元数据端点）
  - `jdbc:mysql://010.0.0.1:3306/db` → 10.0.0.1（内网）
  - 以上经 JDK 21+ 源码核对 + jshell 实测确认（注意：`0177.0.0.1`/`0x7f.*` 在 JDK 21+ 不可利用——严格十进制解析，初审示例有误，已修正）
- **建议**: 提取共享 `HostSecurityUtil`（按 Java `textToNumericFormatV4` 严格十进制 + 1-4 段位移语义），dispatcher 与 connection processor 统一调用；补 `0.0.0.0`/`0` 前缀与短格式段；测试补 `2130706433`/`0.1`/`010.0.0.1`/`0169.254.169.254` 正负用例。
- **信心水平**: 确定（JDK 源码 + jshell 双重验证）
- **误报排除**: 协议白名单（mysql/postgresql/h2）、driver 白名单、loginTimeout 均已核验生效，本次发现仅针对内网判定这一道防线；非"看起来不优雅"，是实测可绕过的安全缺口。

### [P1-02] [维度13] webhook 主机校验遗漏短格式 IPv4 且八进制语义与 JDK 实际解析错配 —— webhook SSRF 绕过

- **文件**: `nop-metadata-service/src/main/java/io/nop/metadata/service/quality/CheckpointActionDispatcher.java:308-378`（isInternalHost）、`406-449`（looksLikeDottedIpv4/parseDottedIpv4）、`452-472`（isInternalIpv4）
- **证据片段**:
  ```java
  if (looksLikeDottedIpv4(h)) {          // L406-417: return dotCount == 3;
      int[] octets = parseDottedIpv4(h); // "0.1" 只有 1 个点 → 不进入
      ...
  }
  if (isAllDigits(h) || isHexInteger(h)) { ... }   // "0.1" 含 '.' → false
  ...
  return false;                          // "0.1" / "0.256" 判定为外部主机
  ```
- **严重程度**: P1 — 安全：默认 fail-closed 配置下 `http://0.1/`、`http://0.256/` 等短格式 URL 被放行并解析到 0.0.0.0/8（等效 localhost），可触达本机管理端口/内部 HTTP 服务
- **现状**: 快速路径无 `h.startsWith("0.")` 检查；`looksLikeDottedIpv4` 要求恰好 3 个点。JDK `textToNumericFormatV4` 对 1-4 段十进制做 24/16/8 位移位解析（jshell 实测：`0.1→0.0.0.1`、`0.0.1→0.0.0.1`、`0.256→0.0.1.0`、`0→0.0.0.0`）。策略自相矛盾：`isInternalIpv4` L452-455 明确将 `0.0.0.0/8` 判内网，但识别路径全部漏掉映射进 0.0.0.0/8 的 1-2 段短格式。另有同根因家族：`parseDottedIpv4`（L433-434）采用 inet_aton 八进制语义，与 Java 严格十进制错配——`0127.0.0.1` Java 解析为 127.0.0.1 但 dispatcher 按八进制 87.0.0.1 判非内网放行；`0169.254.169.254` Java 解析为云元数据地址但被判 87.254.x 放行。MA7.6-04 修复本身语义与 JDK 实际行为不一致。
- **风险**: 普通可配 checkpoint actions 的用户无需白名单即可触发 webhook SSRF 打到 localhost（0.0.0.0/8 在 macOS/Linux connect 等效 loopback）；测试 `TestCheckpointActionDispatcherWebhookSsrf` 无 `0.1`/`0.256`/`0127.*` 用例。
- **建议**: 快速路径补 `h.startsWith("0.")`；将 1-4 段短格式统一按 Java 严格十进制语义归一化后交 `isInternalIpv4` 判定；废弃 inet_aton 八进制语义；补 `"http://0.1/"`、`"http://0.256/"`、`"http://0127.0.0.1/"` 回归用例。
- **信心水平**: 确定（JDK 21/26 源码逐行一致 + jshell 实测）
- **误报排除**: userinfo 剥离（at/lastAt 双保险）、`[::ffff:127.0.0.1]`、十进制整数、0.0.0.0 等已正确拦截且有回归测试；本发现聚焦 1-2 段短格式与八进制语义这两个真实缺口，非误报。

### [P1-03] [维度09] 血缘提取公开 API 将"SQL 解析失败"降级为成功响应 —— 违反文档化"无静默跳过"契约

- **文件**: `nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaLineageEdgeQueryAction.java:160-166, 216-222`；`nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaLineageEdgeBizModel.java:106-131`
- **证据片段**:
  ```java
  // QueryAction.java:160-166（表级）
  try {
      refs = sqlExtractor.extract(sourceSql);
  } catch (NopException e) {
      LOG.error("extractLineageFromSql failed for metaTableId={}", metaTableId, e);
      errors.add(errorMap("sql_parse", e));
      refs = Collections.emptyList();
  }
  // BizModel.java:106-118：dto.setErrors(r.errors); return dto;  ← 无 errors 非空检查
  ```
- **严重程度**: P1 — 契约漂移：公开 GraphQL mutation 返回 HTTP 200 + edgeCount=0，违反 `docs-for-ai/03-modules/nop-metadata.md:161`"SQL 解析失败 → 显式抛 NopException + ErrorCode，不静默空集"
- **现状**: `extractLineageFromSql`/`extractColumnLineageFromSql` 是 @BizMutation 公开 API；内部 SQL 解析失败（`ERR_LINEAGE_SQL_PARSE_FAILED`/`ERR_COL_LINEAGE_SQL_PARSE_FAILED`）被 catch 转为 errors 列表 + 空血缘，BizModel 层无任何 `if (!r.errors.isEmpty()) throw` 分支，errors 原样透传进 `data`。单表操作，不在文档豁免的批量操作名单（syncExternalTables/collectCatalog/executeCheckpoint）内。空 sourceSql 在表级路径（160-166）被降级，而列级路径（211-213）反而显式抛 `ERR_LINEAGE_SQL_SOURCE_EMPTY`——两级行为不一致。
- **风险**: 调用方不深入检查 `data.errors` 嵌套字段时，解析失败被当作成功的零边抽取（"提取成功但无血缘"）；与同模块对表不存在/类型错误的显式抛异常路径（有测试断言 hasError()）形成鲜明反差。抽取器层单元测试钉死"必须抛异常"（`TestSqlColumnLineageExtractor` 106-119 assertThrows），但该异常被 QueryAction 吞掉，未到达 API 边界。
- **建议**: 首选：BizModel 在 `r.errors` 非空时抛 `NopMetadataException(ERR_LINEAGE_SQL_PARSE_FAILED / ERR_COL_LINEAGE_SQL_PARSE_FAILED)`（param 带 metaTableId），补非法 SQL 的 API 级回归测试（`assertTrue(resp.hasError())`）；现有测试无契约冲突（无测试钉死"errors 非空 → 成功"）。备选：若 in-band errors 为既定设计，修改模块文档 161 行明示豁免并统一表级/列级空 SQL 行为。
- **信心水平**: 确定
- **误报排除**: 有 LOG.error 留证 + errors 透出，故非 P0；但"单表操作 + 公开 API + 成功响应"组合与文档明令禁止的"静默空集"直接冲突，且无任何代码分支缓解、无测试钉死，P1 成立。

### [P1-04] [维度18] 模块文档核心实体表格仅列 21/39 个实体 —— 18 个实体缺失

- **文件**: `docs-for-ai/03-modules/nop-metadata.md:19-41` vs `nop-metadata/model/nop-metadata.orm.xml`（39 个 `<entity>`，rg -c 核实）
- **证据片段**:
  ```markdown
  | NopMetaModule | `nop_meta_module` | ... |
  ...（21 行表格，止于 NopMetaManifest）
  ```
  缺失 18 个：NopMetaOrmModel / NopMetaSemanticType / NopMetaEntityRelation / NopMetaEntityUniqueKey / NopMetaEntityIndex / NopMetaDomain / NopMetaDict / NopMetaDictItem / NopMetaPipeline / NopMetaReconciliationEntity / NopMetaModelChangedEvent / NopMetaGlossary / NopMetaGlossaryTerm / NopMetaClassification / NopMetaTag / NopMetaTagLabel / NopMetaBusinessDomain / NopMetaDataProduct
- **严重程度**: P1 — 文档契约漂移：owner 文档是模块规范来源，实体清单缺近半（含 NopMetaClassification/NopMetaTagLabel/NopMetaGlossary 等有独立 BizModel+页面+xmeta 的实体），会误导开发（重复建模、漏用已有字典/分类实体）
- **现状**: 表格内 21 个实体的表名全部与 orm.xml 一致（无错误），但 46% 实体未列出；NopMetaOrmModel、NopMetaModelChangedEvent 在正文提及却未入表。
- **风险**: 读者误认为模块只有 21 个实体；新开发者可能重复建模已存在实体；任务描述"39 实体"与文档 21 的差距正是契约漂移的直接证据。
- **建议**: 补全表格至 39 个实体，或显式注明"以下为核心实体，完整清单见 orm.xml（39 个）"。
- **信心水平**: 确定
- **误报排除**: 非措辞问题——核心实体章节是模块全貌的第一入口，46% 覆盖缺口属实质性文档契约漂移（复核 agent 以"无运行时影响"建议 P2，主 agent 依 owner 文档规范性裁定维持 P1）。

### [P1-05] [维度18] source-anchors.md META-004 transformType 枚举错误 —— direct/expression/aggregate vs 实际 direct/derived/aggregated

- **文件**: `docs-for-ai/04-reference/source-anchors.md:168` vs `nop-metadata-core/.../_NopMetadataCoreConstants.java:149-159`、`nop-metadata-meta/.../dict/meta/lineage-transform.dict.yaml`、`SqlColumnLineageExtractor.java:479-492`
- **证据片段**:
  ```markdown
  <!-- source-anchors.md:168 -->
  产出列级 NopMetaLineageEdge（含 transformType: direct/expression/aggregate）
  ```
  ```java
  // _NopMetadataCoreConstants.java:149-159
  LINEAGE_TRANSFORM_DIRECT = "direct";
  LINEAGE_TRANSFORM_DERIVED = "derived";      // 文档写作 expression ← 错
  LINEAGE_TRANSFORM_AGGREGATED = "aggregated"; // 文档写作 aggregate ← 错
  ```
- **严重程度**: P1 — 文档契约漂移：规范参考文档写出实际不存在的枚举值，开发者按文档写 `transformType == "expression"` 会静默永不匹配
- **现状**: dict 文件、常量类、extractor 实现三处均为 direct/derived/aggregated，文档是唯一 outlier（两个值都错：第二个值名称、第三个值拼写）。
- **风险**: 按文档枚举写查询/断言（`transformType: "expression"`）静默失败或永不匹配；锚点文档用于代码定位与语义速查，误导直接。
- **建议**: source-anchors.md:168 改为 `direct/derived/aggregated`，并交叉检查 META-004 同包注释。
- **信心水平**: 确定（三方核实）
- **误报排除**: 已核实常量类、dict 文件、extractor 三处一致，文档为唯一 outlier；这是事实性枚举错误而非措辞歧义。

### [P1-06] [维度03] NopMetaSearchBizModel 无对应 INopMetaSearchBiz 接口 —— 文档"每个 BizModel 都有接口"全称断言不成立

- **文件**: `nop-metadata-service/src/main/java/io/nop/metadata/service/search/NopMetaSearchBizModel.java:26-27, 35, 51-105`；`docs-for-ai/03-modules/nop-metadata.md:109`；`nop-metadata-dao/.../biz/`（40 个接口，无 INopMetaSearchBiz）
- **证据片段**:
  ```java
  @BizModel("NopMetaSearch")
  public class NopMetaSearchBizModel {
      @BizMutation
      public List<IndexResult> rebuildSearchIndex(@Name("entityTypes") List<String> entityTypes, IServiceContext context) {...}
      @BizQuery
      public SearchResultDTO searchMetadata(@Name("query") String query, ...) {...}
  }
  ```
- **严重程度**: P1 — 契约漂移：规范文档宣称"每个 BizModel 都实现了对应的 INopMeta*Biz 接口"，实际 1/40 例外，跨模块按文档模式 `@Inject INopMetaSearchBiz` 会编译失败
- **现状**: 该 BizModel 是 Pseudo-BizModel（javadoc 自述无单一实体），有 2 个自定义 GraphQL 方法，bean 已注册于 `app-service.beans.xml:57`；接口缺失在 ai-dev 层有记录（"INopMetaSearchBiz deferred (no cross-module callers)"），但规范文档 :109 是全称断言未排除例外。搜索能力当前经 NopMetaSearchProcessor 被 NopMetaTableBizModel 内部复用，规避了功能缺口，但契约声明与实现漂移。
- **风险**: 未来跨模块调用方按文档 `@Inject INopMetaSearchBiz` 编译失败；文档误导。无当前功能影响（复核 agent 因此建议 P2，主 agent 按"规范文档契约声明错误"维持 P1，但修复成本极低）。
- **建议**: 二选一：(a) 在 dao/biz/ 新增 `INopMetaSearchBiz` 接口声明两方法（与其余 40 个 BizModel 对齐）；(b) 在模块文档 :109 显式声明该 Pseudo-BizModel 例外。
- **信心水平**: 确定
- **误报排除**: 已确认 bean 注册正常、GraphQL 可调用，非功能缺陷；聚焦"文档全称断言 vs 实现例外"的契约漂移。

---

## P2 发现（记录，不单独驱动 remediation plan）

### [P2-01] [维度09] NopMetaSearchProcessor fail-closed 分支丢失原始异常链且无日志
- **文件**: `nop-metadata-service/src/main/java/io/nop/metadata/service/search/NopMetaSearchProcessor.java:56-66, 77-87`
- **证据**:
  ```java
  } catch (Exception e) {
      if (searchIndexFailOpen) {
          LOG.error("addToIndex failed for ...", e);
      } else {
          throw new NopMetadataException(NopMetadataErrors.ERR_SEARCH_INDEX_ADD_FAILED)
                  .param(NopMetadataErrors.ARG_ENTITY_TYPE, entityType)
                  .param(NopMetadataErrors.ARG_ENTITY_ID, entityId);
          // 未传 e 作 cause，无日志；NopMetadataException(ErrorCode, Throwable) 构造器存在但未用
      }
  }
  ```
- **严重程度**: P2 — 异常链丢失 + 无日志，排障只能看到无 cause 包装异常；`ERR_SEARCH_INDEX_ADD_FAILED` 描述无 `{error}` 占位符无法携带细节
- **现状**: fail-open 分支有 LOG.error(...,e)；fail-closed 分支 `throw new NopMetadataException(...)` 未把 e 作为 cause，也无日志。removeFromIndex（77-87）同类。
- **风险**: 线上搜索索引故障细节完全丢失，排障困难。
- **建议**: `throw new NopMetadataException(ERR_SEARCH_INDEX_ADD_FAILED, e)`，视需要增加 `{error}` 占位符。
- **信心水平**: 确定；**复核状态**: 已保留（复核确认成立，核心结论不变）

### [P2-02] [维度09] AutoClassificationProcessor 正则编译失败静默 continue 无日志
- **文件**: `nop-metadata-service/src/main/java/io/nop/metadata/service/entity/AutoClassificationProcessor.java:129-134`
- **证据**:
  ```java
  try {
      compiled = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
  } catch (Exception e) {
      continue;   // 无任何日志
  }
  ```
- **严重程度**: P2 — 非法正则导致该规则对所有字段永久失效且完全不可观测；同文件 99-102 行 config 解析失败有 LOG.warn，风格不一致
- **建议**: `catch (Exception e) { LOG.warn("auto-classify rule pattern invalid: classificationId={}, pattern={}", ..., e); continue; }`
- **信心水平**: 确定；**复核状态**: 已保留

### [P2-03] [维度09] 21 个错误码死码（定义从未使用），其中 ERR_RECON_PARSE_PROPERTIES_FAILED 与实现契约漂移
- **文件**: `nop-metadata-service/src/main/java/io/nop/metadata/service/{AggregationErrors, MiscErrors, QualityErrors, ReconErrors, SqlErrors, ModuleErrors, DataSourceErrors, FieldErrors, LineageErrors, JoinErrors}.java`；`reconciliation/LocalReconciliationProcessor.java:171-187`
- **证据**: 主 agent 脚本验证（211 定义 vs 191 非定义处使用）：21 个死码 = ERR_AGGR_JOIN_CROSS_QUERY_SPACE / ERR_AGGR_JOIN_EXTERNAL_CROSS_QUERY_SPACE / ERR_AGGR_JOIN_MIXED_CROSS_DB_DEFERRED / ERR_AGGR_JOIN_MIXED_ENDPOINT_DEFERRED / ERR_CONTRACT_INVALID_TRANSITION / ERR_CONTRACT_NOT_FOUND / ERR_DTO_SERIALIZE_FAILED / ERR_MANIFEST_BUILD_FAILED / ERR_PROFILING_NO_DATASOURCE / ERR_PROFILING_DATASOURCE_DISABLED / ERR_PROFILING_TABLE_NOT_EXTERNAL / ERR_PROFILING_TABLE_FAILED / ERR_PROFILING_RULE_NOT_FOUND / ERR_PROPAGATE_DEPTH_EXCEEDED / ERR_QUERY_TABLE_NOT_FOUND / ERR_RECON_PARSE_PROPERTIES_FAILED / ERR_RECON_RESULT_NOT_FOUND / ERR_SEARCH_INDEX_REBUILD_FAILED / ERR_SQL_VIEW_MODULE_NOT_FOUND / ERR_SQL_VIEW_TABLE_NOT_FOUND / ERR_TAG_LABEL_NOT_FOUND
- **严重程度**: P2 — 死码误导（开发者以为某失败路径已有错误码）；`ERR_RECON_PARSE_PROPERTIES_FAILED` 定义了"显式失败"意图，但实现（LocalReconciliationProcessor:171-187）选择 LOG.warn + 空 Map 降级——代码与错误码契约漂移
- **建议**: 死码清理或补充使用点；对 ERR_RECON_PARSE_PROPERTIES_FAILED 二选一（改为抛出该码，或删除定义并文档化降级行为）。
- **信心水平**: 确定（脚本口径：`rg -o 'ERR_[A-Z0-9_]+' src/main/java -g '!**/*Errors.java'`，211 vs 191）
- **误报排除**: 死码不影响运行时正确性；RECON 路径有 LOG.warn 留证故非静默吞异常

### [P2-04] [维度09] 4 处辅助函数 catch 后无日志静默返回默认值
- **文件**: `quality/MetaQualityCheckpointExecutor.java:348-358`（parseValidations→emptyList）、`quality/MetaQualityScorer.java:249-264`（readExtConfigDimension）、`entity/NopMetaQualityCheckpointBizModel.java:351-364`（readAutoScoreConfig→true）、`quality/MetaQualityCheckpointScheduler.java:253-261`（readRegisteredCron）
- **严重程度**: P2 — 配置损坏与"用户没配"无法区分；parseValidations 有 ERR_CHECKPOINT_NO_RULES 兜底（不静默执行），但错误语义漂移（配置损坏报为"空规则集"）且无根因日志。同模块 Scheduler:320-323 有 LOG.warn 先例
- **建议**: 每个 catch 补 LOG.warn（throwable 末参）；parseValidations 更优方案是定义 validations 解析错误码显式抛出
- **信心水平**: 很可能

### [P2-05] [维度09] .param() 键 399 处裸字符串 vs ~120 处 ARG_* 常量，双风格并存
- **文件**: 全模块，代表点 `query/MetaAggregationExecutor.java:107-108` vs `query/AggregationHelper.java:116`
- **严重程度**: P2 — 现阶段占位符 100% 匹配（脚本验证 0 处缺失），纯维护性风险：裸字符串拼错只能运行时发现
- **建议**: 渐进统一到 NopMetadataArgs.ARG_* 常量
- **信心水平**: 确定

### [P2-06] [维度09] AggregationHelper.checkTableExists 将 getTables SQLException 归类为"表不可见"
- **文件**: `nop-metadata-service/src/main/java/io/nop/metadata/service/query/AggregationHelper.java:496-506`
- **严重程度**: P2 — 连接中断/权限缺失等真实故障被错误分类为业务性"空字段集"（ERR_FIELD_RESOLVE_NO_FIELDS），错误码语义漂移；有 LOG.warn 留证故非静默
- **建议**: SQLException 应 rethrow 为连接类 ErrorCode，仅无行才返回 false
- **信心水平**: 很可能

### [P2-07] [维度09] NopMetaModuleBizModel.parseDeltaModel 解析失败降级 delta=full
- **文件**: `entity/NopMetaModuleBizModel.java:217-234, 244-278`
- **严重程度**: P2 — x:extends 链存在时 delta=full 语义不等价（"伪造值"），有 LOG.warn + javadoc 明示降级为有意设计；建议改为记入 importOrmModels 的 per-path errors 列表
- **信心水平**: 很可能

### [P2-08] [维度09] judgeRegex 对"方言不支持 REGEXP"用 SKIP 状态而非文档字面的"显式抛"
- **文件**: `quality/MetaQualityRuleExecutor.java:540-553`
- **严重程度**: P2 — SKIP + LOG.warn + reason 标记完全可见，属质量规则领域合理语义建模；仅与文档字面约定存在张力，建议在模块文档补充该例外说明
- **信心水平**: 确定

### [P2-09] [维度09] NopMetaTagLabelBizModel 提审失败仅 LOG.warn 继续，用户侧无感知
- **文件**: `entity/NopMetaTagLabelBizModel.java:124-132`（save 路径 81 行调用）
- **严重程度**: P2 — 标签保存成功但永远不进入审批流（业务链路静默中断）；有 LOG.warn + 注释说明为有意旁路容错；建议日志升级或响应透出 warning
- **信心水平**: 确定

### [P2-10] [维度13] custom_sql 黑名单存在已知遗漏（pg_read_binary_file / RUNSCRIPT 等）
- **文件**: `quality/MetaQualityRuleExecutor.java:67-83, 326-349`
- **证据**: 黑名单含 UNION/LOAD_FILE/CALL/EXEC/EXECUTE/SHUTDOWN/DROP/TRUNCATE/ALTER/CREATE/GRANT/REVOKE/INFORMATION_SCHEMA/COPY/PG_READ_FILE/PG_LS_DIR/SYS_EXEC + 5 组 token 序列；缺 `PG_READ_BINARY_FILE`、`PG_LS_LOGDIR`、`PG_LS_WALDIR`、`PG_STAT_FILE`、`RUNSCRIPT`、`SCRIPT`。代码注释（:323）明确承认"不防御 future SQL 方言新增的同类关键字，需阶段性审查更新"
- **严重程度**: P2 — 属文档化的"已知显式风险"设计（custom_sql 是管理员显式提供 SQL 的高特权功能），非隐蔽回退分支；未命中即原样执行是设计边界。建议补充上述关键字到 token 黑名单并补测试
- **信心水平**: 确定

### [P2-11] [维度13] webhook 请求未显式关闭重定向跟随（依赖全局 HttpClientConfig 默认值）
- **文件**: `quality/CheckpointActionDispatcher.java:208-216`；平台 `nop-http-api/.../HttpClientConfig.java:33`（followRedirects 默认 false）
- **严重程度**: P2 — 当前默认安全（Jdk Redirect.NEVER / OkHttp followRedirects(false)）；若部署全局开启 followRedirects=true，允许的外网 webhook 可 302 跳转到内网地址且跳转目标不再复核（经典重定向绕过）。防御纵深缺口，建议显式声明要求 followRedirects=false
- **信心水平**: 确定

### [P2-12] [维度13] rawJdbcUrl（含明文凭据）作为错误参数保留，可能进入日志/错误响应
- **文件**: `connection/MetaDataSourceConnectionProcessor.java:224-245`
- **证据**: `ERR_DATASOURCE_JDBC_URL_BLOCKED` 抛错时 `.param("jdbcUrl", redactJdbcUrl(jdbcUrl))`（脱敏）+ `.param(ARG_RAW_JDBC_URL, jdbcUrl)`（原始含 user:pass@）；测试 L292-294 明确断言 rawJdbcUrl 含完整 URL
- **严重程度**: P2 — 显示面已脱敏，但 NopException params 会随异常序列化进入日志/错误追踪系统，凭据可能落盘。建议 rawJdbcUrl 仅调试级输出或从错误响应移除
- **信心水平**: 确定

### [P2-13] [维度13] ExpressionMeasureValidator 两个黑名单条目因分词机制永远无法命中（死条目）
- **文件**: `field/ExpressionMeasureValidator.java:57-84, 469-489`
- **证据**: `KEYWORD_BLACKLIST` 含 "SET TRANSACTION"（含空格，tokenizer 切成两个 IDENTIFIER，contains 永不命中）；`FUNCTION_BLACKLIST` 含 "INTO OUTFILE"/"INTO DUMPFILE"（INTO 不后跟 `(` 不成 FUNCTION_CALL，永不命中）
- **严重程度**: P2 — 当前无实际漏洞（measure expression 上下文无 SELECT 形态，标识符过白名单）；死条目误导后续维护者以为该面已覆盖
- **建议**: 删除或改写为可命中形式（对齐 CUSTOM_SQL_FORBIDDEN_SEQUENCES 的 token 序列匹配）
- **信心水平**: 确定

### [P2-14] [维度16/21] TestAutoNopMetaAggregationCrud 是"挂羊头卖狗肉"的错标快照测试
- **文件**: `nop-metadata-service/src/test/java/io/nop/metadata/service/TestAutoNopMetaAggregationCrud.java:24-32`
- **证据**: 类名暗示聚合快照覆盖，实际 RPC 是 `NopMetaModule__findPage`（request.json5 为 `{data:{limit:10, offset:0}}`），与"Aggregation"无关
- **严重程度**: P2 — 命中反模式 P-6（方法名不表达行为）+ P-2（快照镜像）；误导审核者以为聚合路径有 AutoTest 约束。聚合核心逻辑本身被 TestAggregation* 65 测端到端约束，故不升 P1
- **建议**: 改为真实 queryAggregation RPC 或改名+删快照
- **信心水平**: 确定

### [P2-15] [维度16/21] NopMetadataHelperTest.testToSearchableDoc* 只镜像 trivial 字段复制
- **文件**: `nop-metadata-service/src/test/java/io/nop/metadata/service/NopMetadataHelperTest.java:91-125` vs `service/NopMetadataHelper.java:45-76`
- **严重程度**: P2 — summary 截断、content 拼接、tagSet 三个影响搜索索引的行为字段无断言；核心逻辑改成错误实现测试仍通过（命中 P-2/P-3）
- **建议**: 补 summary>500 截断、content join、tagSet 断言
- **信心水平**: 确定

### [P2-16] [维度16/21] TestNopMetadataErrorsCentralized.testArgConstantsIntroduced 是纯常量镜像
- **文件**: `nop-metadata-service/src/test/java/io/nop/metadata/service/TestNopMetadataErrorsCentralized.java:55-64`
- **严重程度**: P2 — 7 条断言全部"常量值==字面量"（P-2）；同文件 testAllErrorsUseNopErrPrefix 反射扫描才有独立价值
- **建议**: 删除或并入反射扫描
- **信心水平**: 确定

### [P2-17] [维度16/21] TestAllEntitiesHaveBizModels 是手工维护的 39 实体清单
- **文件**: `nop-metadata-service/src/test/java/io/nop/metadata/service/TestAllEntitiesHaveBizModels.java:57-73, 75-116`
- **严重程度**: P2 — 清单随模型演进静默失准（新实体漏同步时保护力悄悄退化）；建议反射扫描 dao.entity 包动态推导
- **信心水平**: 确定

### [P2-18] [维度16/21] NopMetadataWebPagesTest 是纯页面编译冒烟测试且无法检测页面被删空
- **文件**: `nop-metadata-web/src/test/java/io/nop/metadata/web/NopMetadataWebPagesTest.java:17-20`
- **严重程度**: P2 — PageProvider.validateAllPages 遍历启用模块页面，0 页面也通过；建议加页面计数断言
- **信心水平**: 确定

### [P2-19] [维度16/21] testToErrorMessageNopException 存在死分支 + 弱断言
- **文件**: `nop-metadata-service/src/test/java/io/nop/metadata/service/NopMetadataHelperTest.java:84-89`
- **严重程度**: P2 — `|| msg.contains("ERR_QUALITY_RULE_NOT_FOUND")` 分支永远不可达（错误码返回小写连字符串）；断言退化为子串包含
- **建议**: 精确断言 `assertEquals("nop.err.metadata.quality-rule-not-found", msg)`
- **信心水平**: 确定

### [P2-20] [维度16/21] TestCoreMetricsUsage 是脆弱的源码静态扫描测试
- **文件**: `nop-metadata-service/src/test/java/io/nop/metadata/service/TestCoreMetricsUsage.java:37-76`
- **严重程度**: P2 — 注释剥离正则无 DOTALL 会假失败；路径解析依赖 user.dir；但有真实约束力（可 mock 时钟约定门禁）
- **建议**: 修正正则 `(?s)/\*.*?\*/`、用模块绝对路径
- **信心水平**: 确定

### [P2-21] [维度16/21] 反射测私有方法 + MockHttpClient 双通道行为不一致（潜伏）
- **文件**: `TestMetaQualityRuleExecutorCustomSqlSandbox.java:209-230`、`TestEvalExpectPassWhenErrorPath.java:23-35`、`TestMetaJoinTruncateOverflow.java:78-91`、`mock/MockHttpClient.java:48-57 vs 60-67`
- **严重程度**: P2 — fetchAsync 不响应 blockLatch 而 fetch 响应；当前生产只走同步 fetch，若未来改 fetchAsync，R4.3 并发测试钉住窗口失效。私有方法直测为安全逻辑合理取舍
- **建议**: MockHttpClient 两方法统一阻塞语义
- **信心水平**: 很可能

### [P2-22] [维度18] ai-dev 设计文档 orm.xml 行号锚点全部失效
- **文件**: `ai-dev/design/nop-metadata/01-architecture-baseline.md:501, 713, 1393, 1413, 1542` vs `model/nop-metadata.orm.xml`
- **严重程度**: P2 — 行号普遍偏移 300-440 行（NopMetaLineageEdge 实际在 1866 行而非 1427 行等），引用对象本身（列名/约束）仍正确；建议改"类名:列名"锚点
- **信心水平**: 确定

### [P2-23] [维度18] 模块文档 I*Biz 接口包路径表述易误读
- **文件**: `docs-for-ai/03-modules/nop-metadata.md:109, 168`
- **严重程度**: P2 — "nop-metadata-dao/.../biz/" 省略写法可能被理解为 io.nop.metadata.dao.biz；实际包为 `io.nop.metadata.biz`。接口存在性与签名 100% 核实通过
- **建议**: 补全为 `nop-metadata-dao/src/main/java/io/nop/metadata/biz/（包 io.nop.metadata.biz）`
- **信心水平**: 确定

### [P2-24] [维度03] docs-for-ai 模块文档未声明 items 为 List<Map<String,Object>> 的合理例外
- **文件**: `docs-for-ai/03-modules/nop-metadata.md:56-59, 67-73`；DTO javadoc 已声明（`QueryTableDataResultDTO.java:14-15`）
- **严重程度**: P2 — DTO 外层类型安全，仅规范文档缺该例外的显式声明（下游按文档示例做类型假设时可能误判）
- **建议**: 在示例处补一句"items 为动态行数据 List<Map<String,Object>>，schema 随表结构变化，属有意保留的 Map 例外"
- **信心水平**: 确定

### [P2-25] [维度01] service pom 未直接声明 nop-dataset（经 nop-core 传递）
- **文件**: `nop-metadata-service/pom.xml:15-134`；`query/MetaJoinExecutor.java:11-12`、`query/AggregationHelper.java:9-10`
- **严重程度**: P2 — nop-dataset 是内核固定依赖，风险为零，属"平台核心包传递链"排除条款边界；可补显式声明或文档备注
- **信心水平**: 确定

### [P2-26] [维度01] 模块文档依赖表未记录各模块 test-scope 基建依赖
- **文件**: `docs-for-ai/03-modules/nop-metadata.md:147-153` vs 各 pom（web 的 codegen/ooxml-xlsx/autotest-junit、service 的 h2/mysql/mockito 等）
- **严重程度**: P2 — compile 依赖与文档 100% 一致，仅信息不完整；建议加脚注说明
- **信心水平**: 确定

### [P2-27] [维度01] dao 模块 test-scope 声明 nop-metadata-codegen 但无 src/test 目录
- **文件**: `nop-metadata-dao/pom.xml:30-35`
- **严重程度**: P2 — 该依赖供 precompile 代码生成用（与 nop-auth-dao 模式一致，仓库标准模式）；可在注释中说明
- **信心水平**: 确定

---

## 已核验安全 / 排除误报记录（维度 13）

以下经代码核验为**安全**，不构成发现（供后续审计排除）：

1. **HAVING name 白名单"未命中即透传"（历史 P0）— 已修复**：`AggregationHelper.nameResolverFor` 对未命中 nameToExpr 的 name 一律抛 `ERR_AGGR_HAVING_UNKNOWN_NAME`；7 处 HAVING 拼接点均先 `preprocessHavingArithmetic` 再 translate；ORDER BY 同构（`ERR_AGGR_ORDER_BY_UNKNOWN_NAME`）；`TestHavingArithmeticPreprocess` 存在。
2. **webhook 主机解析核心路径**：userinfo 剥离双保险、IPv6 `[::1]`、端口/path 截断、fail-closed 默认禁内网、method 白名单、回归测试 20+ 用例（除 P1-02 短格式缺口）。
3. **SQL 注入面**：FilterToSqlTranslator 值一律 `?` 绑定；列名/表名/schema 过 `^[A-Za-z_][A-Za-z0-9_]*$`（`validateIdentifier` 在 7+ 执行器调用）；limit/offset 参数绑定；entity 路径 EQL 参数化；sourceSql 以子查询包裹 + 多语句拒绝。
4. **敏感字段**：connectionConfig 在 xmeta `published/insertable/updatable/queryable=false`；事件快照双重脱敏（AR-07）；testConnection 返回不含凭据。
5. **权限模型**：全部入口经 @BizModel + action-auth（FNPT）+ data-auth 行级作用域；无外部直接 DAO 路径；Scheduler 直调 raw impl 属内部 cron 触发链（无外部输入）。
6. **tableType 分派** 三态严格匹配，未知类型显式抛错；方言白名单 {H2, MySQL, PostgreSQL} 运行时校验。
7. **importOrmModel VFS path** 读 classpath 资源非 OS 文件系统，非路径穿越。

## 测试覆盖总评（维度 16/21）

- 868 个测试方法全部通过（surefire 867 service + 1 web），AutoTest 快照新鲜（最后修改 2026-08-05，实测通过）
- 8 个高风险核心逻辑全部有测试约束且大多含错误路径：联邦查询 7 路径分派（TestAggregation* 65 测 + queryTableData/queryJoinData 全套）、SQL 解析血缘（TestSqlColumnLineageExtractor 29 测）、质量规则 7 类（含 SKIP + 5 不可执行路径）、检查点调度、对账、跨库 JOIN 限流、per-row 失败隔离、安全三件套（47 测）
- 唯一结构性小缺口：sql 表类型 queryAggregation 无 GraphQL 端到端测试（仅有处理器单元测试），P2 级
- 有效测试与低价值测试比例 ≈ 93-96% 高价值 / 3-4% 镜像门禁类
- 反模式命中：P-2（P2-14/15/16）、P-3（P2-15）、P-4（P2-17）、P-6（P2-14）；无 P-1/P-5/P-7/P-8 命中

## 总评

nop-metadata 是本仓库工程质量第一梯队的模块：依赖边界干净（8 子模块与文档化规则零偏差、无环、api 纯净）、错误码体系高度规范（211 个码前缀 100% 合规、占位符 100% 匹配、无裸异常/无 System.out/无中文消息）、测试覆盖强（868 测全绿、快照新鲜、高风险逻辑与错误路径均有约束）、安全基线良好（HAVING 注入历史 P0 已修复并有回归测试钉死）。

但**安全维度仍有真实缺口**：两个 isInternalHost 实现（JDBC 连接处理器 + webhook dispatcher）存在 IP 记法变体归一化盲区（十进制整数、前导零、0.0.0.0/8 短格式），经 JDK 源码 + jshell 实测可绕过——这是历史 MA7.6-04 修复只做了一半的遗留（dispatcher 侧修复时 connection processor 未同步，且 dispatcher 自身的八进制语义与 Java 严格十进制错配）。这 2 个 P1 是本轮审计最高优先级。

**文档契约漂移集中点**：模块文档实体表格 21/39（P1）、META-004 transformType 枚举错误（P1）、"每个 BizModel 都有接口"全称断言（P1）——三者均不影响运行时，但会直接误导后续开发。

**错误处理 P1**：血缘提取公开 API 的"SQL 解析失败 → 成功响应"是唯一与文档化"无静默跳过"原则直接冲突的公开行为，修复成本低（BizModel 层 errors 非空即抛）且无测试契约冲突。

## 优先修复建议

1. **[P1-01 + P1-02] 统一 HostSecurityUtil**：抽取共享主机校验工具（Java 严格十进制 + 1-4 段位移语义），两处 isInternalHost 统一调用；补 `0.` 前缀、0.0.0.0/8、前导零变体回归测试（两个入口各一正一负用例）——一次性消除同根因家族。
2. **[P1-03] 血缘 API 显式抛错**：BizModel 层 errors 非空即抛 ERR_LINEAGE_SQL_PARSE_FAILED / ERR_COL_LINEAGE_SQL_PARSE_FAILED，补 API 级非法 SQL 回归测试。
3. **[P1-04/05/06] 文档三修**：补全实体表格至 39、修正 META-004 枚举、模块文档补 NopMetaSearch Pseudo-BizModel 例外；修复后运行 `node ai-dev/tools/check-doc-links.mjs --strict`。
4. **[P2] 清理批次**：21 死错误码、KeyValueDTO 死 DTO、4 处无日志 catch（P2-01/02/04）、custom_sql 黑名单补充关键字（P2-10）、测试镜像断言清理（P2-14/15/16）。

## 本次审核盲区自评

- 未执行 `./mvnw test` 全量重跑（子 agent 声称 surefire 报告全绿 + 104 测实测重跑通过，但主 agent 未独立重跑；时间窗口内依赖离线 mvnw 可用性）
- 未审计 nop-metadata-web 页面内容（仅页面冒烟测试）；未审计 app 模块运行时配置（application.yaml）
- 未审计 xmeta 字段级权限与 GraphQL 字段可见性的逐字段核对（由维度 11 覆盖，本次并入维度 03 简略核对）
- 维度 22（工作流语义）未执行——nop-metadata 的审批流（TagLabel/DataContract approve-reject）在 xbiz XPL 层，已有测试钉死（TestNopMetaBizInterfaceCompleteness），本轮未深挖 xwf 文件（模块无 xwf 目录）
- 未运行 checkstyle（命令基线缺失，风格维度仅抽查 import 分组）

---

<AI_STEP_RESULT>issues</AI_STEP_RESULT>
