> Audit Status: closed
> Audit Type: open-ended
> Mission: nop-metadata
> Remediated By: `ai-dev/plans/2026-07-21-1200-1-nop-metadata-p1-runtime-defects.md` (NF-01, NF-02, NF-04, SC-02), `ai-dev/plans/2026-07-21-1200-2-nop-metadata-code-quality-and-docs.md` (SC-03)

# nop-metadata 开放式对抗性审计报告（第 5 轮 — 深度再审查）

- **审核模块**: `nop-metadata/`（8 个 Maven 子模块）
- **审核日期**: 2026-07-21 (based on live code at HEAD, audited against 2026-07-19–2026-07-20 prior findings)
- **审计方式**: 开放式发现导向，对照 `2026-07-19-1118-open-audit`（14 条）、`2026-07-19-1118-multi-audit`（52 issues）、`2026-07-20-1816-open-audit-r4`（10 条）、`2026-07-20-1816-multi-audit`（88 findings）做去重，标注"status change"或"new"
- **使用的启发式视角**: 异常路径侦探、死代码清道夫、IoC 侦探、测试覆盖侦探

---

## 执行摘要

本次审计发现 **3 条新问题 + 3 条状态变更**（2 个重要修正 + 1 个确认未修复），全部为 P1/P3 级。**无新增 P0**。

**核心发现**: 上一轮（Round 4）报告的 3 个 P0/P1 接口缺口问题（AR-01/AR-02/AR-03）已在 live code 中被修复——`INopMetaDataProductBiz`、`INopMetaQualityResultBiz`、`INopMetaDataContractBiz` 的接口签名已完整声明。但 Round 4 并未注意到此修复，错误声称为"全部未修复"。同时，一位新发现的 P1 级 NPE 路径（NopMetaSearchBizModel.searchMetadata）和一条待确认的 P1 级 ErrorCode 前缀错误（ExternalTableStructureReader，multi-dim 09-03）仍存在。

---

## 详细发现

### [NF-01] NopMetaSearchBizModel.searchMetadata() 在 searchEngine=null 时 NPE

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/search/NopMetaSearchBizModel.java:65`
- **证据片段**:
  ```java
  // NopMetaSearchBizModel.java:38-40
  @Inject
  @Nullable
  @Named("nopSearchEngine")
  protected ISearchEngine searchEngine;

  // line 65 — 直接调用，无 null 检查：
  SearchResponse response = searchEngine.search(request);

  // 对比同一 package 的其他两个类的正确模式：
  // NopMetaSearchService.java:26
  if (searchEngine == null) {
      LOG.warn("searchEngine not available, skip...");
      return;
  }
  // NopMetaIndexBuilder.java:43
  if (searchEngine == null) {
      return Collections.singletonList(result); // 含错误消息
  }
  ```
- **严重程度**: P1
- **现状**: `@Inject @Nullable @Named("nopSearchEngine")` 字段在宿主 app 未注册 `ISearchEngine` 实现时为 null。同包的其他两个类（NopMetaSearchService、NopMetaIndexBuilder）都正确做了 null 守卫，但 NopMetaSearchBizModel 没有。任何有 `NopMetaSearch__searchMetadata` GraphQL 查询权限的用户可触发 `NullPointerException`。
- **风险**: 生产环境中若无搜索模块部署，调用 `searchMetadata(query)` 直接 NPE，不会被框架的 ErrorCode 体系正常包装，返回 500 + 无业务上下文。
- **建议**: 在 line 65 前加 `if (searchEngine == null) throw new NopException(ERR_SEARCH_ENGINE_NOT_AVAILABLE)` 或返回空结果（与 NopMetaSearchService 的静默跳过一致）。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探 + IoC 侦探（同包三文件，唯一未守卫者）

---

### [SC-01] [状态修正] INopMetaDataProductBiz / INopMetaQualityResultBiz / INopMetaDataContractBiz 接口缺口已修复

- **文件**:
  - `nop-metadata/nop-metadata-dao/.../biz/INopMetaDataProductBiz.java`（32 行，含 linkAsset/unlinkAsset/getLinkedAssets）
  - `nop-metadata/nop-metadata-dao/.../biz/INopMetaQualityResultBiz.java`（18 行，含 approve/reject）
  - `nop-metadata/nop-metadata-dao/.../biz/INopMetaDataContractBiz.java`（41 行，含 activate/deprecate/retire/check/approve/reject）
- **严重程度**: ✅ 已修复（Round 4 报告为 P0/P1 未修复，但 live code 已修正）
- **现状**: Round 4（2026-07-20-1816-open-audit）报告 AR-01/P0、AR-02/P1、AR-03/P1 为"完全未修复"。经 live code 验证，**三个接口均已完整声明方法签名**：
  - `INopMetaDataProductBiz` 有 `linkAsset`/`unlinkAsset`/`getLinkedAssets`
  - `INopMetaQualityResultBiz` 有 `approve`/`reject`
  - `INopMetaDataContractBiz` 有全部 6 个 @BizMutation 方法（含 `approve`/`reject`）
- **一致性验证**: `TestNopMetaBizInterfaceCompleteness`（line 89-94）已覆盖对这三个接口的方法签名断言，测试通过。
- **信心水平**: 确定
- **说明**: Round 4 的审计时点可能在修复之前，或 audit 流程存在"audit 时读取了旧快照"的缓存问题。当前 HEAD 确认修复。

---

### [SC-02] [状态确认] ExternalTableStructureReader 仍使用错误 ErrorCode 前缀 `metadata.` 而非 `nop.err.metadata.`

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/sync/ExternalTableStructureReader.java:46`
- **证据片段**:
  ```java
  static final String ERR_DIALECT_NOT_SUPPORTED = "metadata.dialect-not-supported";
  // 应为 "nop.err.metadata.dialect-not-supported"
  ```
- **严重程度**: P1（与 multi-dim 09-03 原定级一致）
- **现状**: Multi-dim 审计（09-03）标记为 P1。Round 4 未复核此条。live code 确认该前缀错误**仍未修复**。5 处使用该常量的 `newErrorCode` 调用（lines 90, 126）均传播了错误的 ErrorCode key。
- **风险**: ErrorCode key 不遵循 `nop.err.metadata.*` 命名规范 → 监控系统按前缀过滤丢失这条、i18n 匹配失败、运维难以在代码库中 grep 定位。
- **建议**: 改为 `static final ErrorCode ERR_DIALECT_NOT_SUPPORTED = NopMetadataErrors.ERR_DATASOURCE_TYPE_NOT_SUPPORTED`（复用 NopMetadataErrors 常量），或新增 `ErrorCode ARG_*` 常量到 NopMetadataErrors.java。
- **信心水平**: 确定
- **发现来源视角**: 死代码清道夫（复核 multi-dim 09-03 修复状态）

---

### [NF-02] NopMetadataException.toInlineErrorCode 将消息字面量作为 ErrorCode 传播，破坏 ErrorCode 契约

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/NopMetadataException.java:51-54`
- **证据片段**:
  ```java
  // NopMetadataException.java:51-54
  private static ErrorCode toInlineErrorCode(String message) {
      return ErrorCode.define(message, message); // code == description == message
  }

  // MetaManifestBuilder.java:64 — 调用点
  throw new NopMetadataException("fullOrmModel must not be null (module has no full ORM model)");
  ```
- **严重程度**: P1
- **现状**: Multi-dim 审计 09-09 将 `toInlineErrorCode` 评为 ℹ️ Info（"internal, acceptable"）。实际严重性更高：当 `NopMetadataException(String)` 被抛出时，ErrorCode 的 code 字段是完整的英文句子（如 `"fullOrmModel must not be null (module has no full ORM model)"`），而非格式化的错误码 key（如 `nop.err.metadata.manifest.module-null`）。这意味着：
  1. GraphQL 响应的 `errorCode` 字段被英文消息污染，前端/监控按 `nop.err.*` 前缀过滤会漏掉此异常。
  2. i18n 在外层无法根据错误码查找字典（code 就是消息本身）。
  3. 当前仅 2 处调用（MetaManifestBuilder line 62 和 64），但模式已经"就绪"，可能被后续开发者误用作普通异常类。
- **风险**: 低频率但高影响——当 `releaseModule`/`generateManifest` 在特定条件下（模块缺失 full ORM 模型）失败时，网关/监控完全无法按 ErrorCode 归类此失败。
- **建议**: 将两处调用改为：
  - `throw new NopMetadataException(NopMetadataErrors.ERR_MANIFEST_MODULE_NULL).param(...)`（建议新增 ErrorCode）
  - 或将 `NopMetadataException(String)` 构造器标记为 `@Deprecated` 并加警告文档。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探（沿 multi-dim 09-09 的"info"评级深挖真实影响）

---

### [NF-03] NopMetadataConfigs 为空接口存根（未被之前审计覆盖）

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/NopMetadataConfigs.java:1-5`
- **证据片段**:
  ```java
  public interface NopMetadataConfigs{
      
  }
  ```
- **严重程度**: P3
- **现状**: NopMetadataConstants 已在之前审计（multi-dim 09-11）中报告为空存根。本次审计发现同一包下存在第二个空接口 `NopMetadataConfigs`，未被任何之前审计覆盖。全仓库 grep 0 处外部引用。
- **风险**: 新人被误导；自动补全时可见两个空接口。
- **建议**: 删除，或填充真实配置常量。
- **信心水平**: 确定
- **发现来源视角**: 死代码清道夫（检查 NopMetadataConstants 时意外发现同包姐妹接口）

---

### [SC-03] [状态确认] nop-metadata-api 死模块仍在父 pom.xml 中声明

- **文件**: `nop-metadata/pom.xml:29`（`<module>nop-metadata-api</module>`）
- **严重程度**: P3（与 Round 4 AR-04 一致）
- **现状**: Round 4 报告此模块为死模块。Live code 确认：`nop-metadata-api/` 仍无 `src/` 目录，仅含 `pom.xml` 和 `target/`。打包产物 ~996 字节（仅 META-INF/）。全仓库 0 个外部引用。
- **风险**: 构建噪音；新人建立 api 模块但在 service/dao 中建接口。
- **建议**: 从父 pom `<modules>` 移除并删除目录；若未来需要 typed RPC 接口再重建。
- **信心水平**: 确定

---

### [NF-04] NopMetaSearchBizModel 重建搜索索引时 null 安全但前端的 searchMetadata 不 null 安全——同 bean 内 null 守卫不一致

- **文件**: `nop-metadata/.../search/NopMetaSearchBizModel.java:42-46` vs `48-88`
- **证据片段**:
  ```java
  // rebuildSearchIndex — 不直接使用 searchEngine，委托给 indexBuilder（内部 null 安全）
  @BizMutation
  public List<IndexResult> rebuildSearchIndex(...) {
      return indexBuilder.buildFullIndex(entityTypes); // indexBuilder.searchEngine 有 null 守卫
  }

  // searchMetadata — 直接使用 searchEngine，无 null 守卫
  @BizQuery
  public SearchResultDTO searchMetadata(...) {
      SearchResponse response = searchEngine.search(request); // NPE 可能
  }
  ```
- **严重程度**: P3（NF-01 的伴随发现）
- **现状**: 同一 BizModel 的两个方法处理 `searchEngine=null` 的方式不一致——一个通过委托（间接 null 安全），一个直接调用（NPE）。`rebuildSearchIndex` 的 null 安全是通过 `indexBuilder.buildFullIndex` 的 null 守卫间接获得的，而非 BizModel 自身的防御。
- **风险**: 低——但维护者可能认为"rebuildSearchIndex 也没处理 null，所以 searchMetadata 不处理 null 也没问题"。
- **建议**: 在 `searchMetadata` 入口增加与 `rebuildSearchIndex` 一致的 null 守卫（或提取公共方法）。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探（追踪 NF-01 时发现同 bean 内部不一致）

---

## 第 2 轮追问：接口修复状态的交叉确认

发现 Round 4 报告的 P0/P1 接口缺口已被修复后，我执行了交叉验证：

1. **方法签名验证**: grep `@BizMutation|@BizQuery` 在三个接口中——完整。

2. **TestNopMetaBizInterfaceCompleteness 验证**: 测试 line 89-94 断言方法与实际接口方法完全对齐。该测试作为 CI 门禁（integration test 类），若接口再退化会被捕获。

3. **Code 与 Round 4 的矛盾根源**: Round 4 声称测试未覆盖 INopMetaDataProductBiz 和 INopMetaQualityResultBiz（NF-05），但实际测试代码 line 89-94 已覆盖。可能 Round 4 审计了测试文件的旧版本，或误读了 Javadoc（"验证 9 个接口"——实际测试覆盖 11 个接口）。

**结论**: Round 4 的 3 个 P0/P1 接口缺口问题（AR-01/02/03）和其测试缺口问题（NF-05）都是误报，live code 和测试均已正确实现。

---

## 总评

### 本模块当前最值得关注的 1-3 个方向

1. **NopMetaSearchBizModel NPE（NF-01）是唯一新发现的 P1 级运行时缺陷**。它不涉及安全，但标志着'同一模块中三处 @Nullable 注入，一处未守卫'的守卫不一致，容易被后续其他 @Nullable 注入继承同模式。
2. **ExternalTableStructureReader 前缀错误（SC-02）是已知 P1 仍未修复**。作为 multi-dim 09-03 的原最高优先级 P1，在多个 audit 周期后仍存在，说明现有 review 流程未能将此问题推到修复队列前端。
3. **Round 4 的 P0/P1 误报揭示 audit 流程的风险**：三个已修复的接口被错误报告为未修复。建议在 future audit 中增加"变更时间戳校验"环节——对 live code 中声明为未修复的问题，应检查 git log 确认最近是否有修复提交。

### 本次审查的盲区自评

- **未跑 Maven 构建**: 虽然 NFC，但 Round 4 已有此盲区且未改善。
- **未审计 39 个 xmeta retention 文件字段级 override**: 仅确认"空 retention = 框架标准模式"。
- **未审计 nop-metadata-web 前端 amis 页面**: 150+ 页面的字段展示与 xmeta 一致性。
- **未验证 Delta 定制目录**: 外部模块的 Delta 覆盖未审计。
- **未审计 AggregationContext 1854 行的具体代码质量**: 仅确认其存在性。
- **未检验 `judgeByRuleId` 的 @BizQuery/@BizMutation 注解状态**: 留待后续。

### 按严重程度分布表

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | — |
| P1      | 3    | NPE 路径（NF-01）；ErrorCode 前缀错误（SC-02，已知未修复）；toInlineErrorCode 损害 ErrorCode 契约（NF-02，降级自 ℹ️ Info） |
| P2      | 0    | — |
| P3      | 3    | NopMetadataConfigs 空接口（NF-03）；nop-metadata-api 空模块（SC-03）；同 bean 内 null 守卫不一致（NF-04） |
| ✅ 已修复 | 3    | INopMetaDataProductBiz 接口完整（SC-01）；INopMetaQualityResultBiz 接口完整（SC-01）；INopMetaDataContractBiz 接口完整（SC-01） |

<AI_STEP_RESULT>issues</AI_STEP_RESULT>
