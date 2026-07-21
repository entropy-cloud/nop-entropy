> Audit Status: closed
> Audit Type: open-ended
> Mission: nop-metadata

# nop-metadata 开放式对抗性审计报告（第 4 轮 — 交叉验证 + 盲区深挖）

- **审核模块**: `nop-metadata/`（含 8 个 Maven 子模块）
- **审核日期**: 2026-07-20
- **审计基线**: live code at HEAD
- **审计方式**: 开放式发现导向，对第 3 轮审计（2026-07-20-1816）的结果做交叉验证，同时深挖其报告的 4 个盲区
- **去重策略**: 已对照 `2026-07-19-1118-open-audit-nop-metadata.md`（14 条）、`2026-07-19-1118-multi-audit-nop-metadata.md`（46 条）、`2026-07-20-1816-open-audit-nop-metadata.md`（4 条）。标注"cross-ref"的发现为旧问题仍存在并补充现状变化；"NF"为本次新发现。
- **使用的启发式视角**: 异常路径侦探、GraphQL 契约考古学家、死代码清道夫、测试覆盖侦探

---

## 执行摘要

本次审计发现 **10 条问题**（3 条 high-impact 旧问题未修复 + 3 条 medium 新问题 + 4 条 low 问题），其中：

- **P0**: 1 条（跨模块 API 契约完全缺失，第 3 轮审计声称"全部 P0-P2 已修复"但此条被遗漏）
- **P1**: 2 条（同上，被遗漏的旧问题）
- **P2**: 3 条（1 条旧问题补充 + 2 条新问题）
- **P3**: 4 条（2 条旧问题仍存在 + 2 条新问题）

**核心发现**: 第 3 轮审计的范围存在严重盲区——它只验证了自己 14 条新发现的修复状态和部分重叠的多维度审计发现，但 **多维度审计的 P0/P1 API 契约问题（03-F01/02/03）完全未被复核**，仍全部未修复。同时，第 3 轮对自身 4 条问题（R-01/R-04）的修复声明与企业级证据矛盾（`SqlAggregationProcessor` 实际已正确使用 `NopException`，`data-auth.xml` 实际已覆盖 8 个实体而非 3 个）。

---

## 详细发现

### [AR-01] INopMetaDataProductBiz 接口完全缺失 3 个 @BizMutation/@BizQuery 方法声明

- **文件**:
  - `nop-metadata/nop-metadata-dao/src/main/java/io/nop/metadata/biz/INopMetaDataProductBiz.java:1-11`（空接口）
  - `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaDataProductBizModel.java:40-115`（实现 `linkAsset`/`unlinkAsset`/`getLinkedAssets`）
- **证据片段**:
  ```java
  // INopMetaDataProductBiz.java (空接口)
  public interface INopMetaDataProductBiz extends ICrudBiz<NopMetaDataProduct>{
  }

  // NopMetaDataProductBizModel.java — 3 个已实现的 @BizQuery/@BizMutation 方法:
  @BizMutation
  public NopMetaTagLabel linkAsset(...) { ... }
  @BizMutation
  public boolean unlinkAsset(...) { ... }
  @BizQuery
  public List<NopMetaTagLabel> getLinkedAssets(...) { ... }
  ```
- **严重程度**: P0（多维度审计 03-F01 原定级，重审确认）
- **现状**: 多维度审计（2026-07-19-1118）将此标记为 **P0**。第 3 轮审计（2026-07-20-1816）在"全部已修复确认"表中未提及此条，实际 **仍完全未修复**。接口仍为空，BizModel 的方法即使通过 GraphQL 入口可达，但跨模块 `@Inject INopMetaDataProductBiz` 调用方无法访问 `linkAsset`/`unlinkAsset`/`getLinkedAssets`。
- **风险**: 跨模块服务调用在编译期不报错（因接口无方法），但运行时调用 `@Inject INopMetaDataProductBiz.linkAsset(...)` 将通过反射失败。目前全仓库搜索 `INopMetaDataProductBiz` 仅声明处被引用，尚无外部调用方——但如果未来有其他模块需要 inject DataProductBiz，会静默失败。
- **建议**: 在 `INopMetaDataProductBiz` 中声明 `linkAsset`、`unlinkAsset`、`getLinkedAssets` 的方法签名（与其他 I*Biz 接口一致的 `@BizMutation`/`@BizQuery` + `@Name` 模式）。
- **信心水平**: 确定
- **复核状态**: 已知未修复，多维度审计 03-F01

---

### [AR-02] INopMetaQualityResultBiz 接口缺失 approve/reject 方法声明

- **文件**:
  - `nop-metadata/nop-metadata-dao/src/main/java/io/nop/metadata/biz/INopMetaQualityResultBiz.java:1-11`（空接口）
  - `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaQualityResultBizModel.java:26-50`（实现 `approve`/`reject`）
- **证据片段**:
  ```java
  // INopMetaQualityResultBiz.java
  public interface INopMetaQualityResultBiz extends ICrudBiz<NopMetaQualityResult>{
  }

  // NopMetaQualityResultBizModel.java
  @BizMutation
  public NopMetaQualityResult approve(@Name("id") String id, IServiceContext context) { ... }
  @BizMutation
  public NopMetaQualityResult reject(@Name("id") String id, IServiceContext context) { ... }
  ```
- **严重程度**: P1（多维度审计 03-F02 原定级）
- **现状**: 多维度审计标记为 P1 的接口缺口。3 周后仍完全未修复。接口为空，`approve`/`reject` 方法在 BizModel 中存在但不对外可见（跨模块注入视角）。
- **风险**: 质量结果审批功能无法通过 typed RPC 跨模块调用。当前所有调用路径都发生在模块内部（同 JVM 反射），未暴露问题，但任何重构将触发不可达。
- **建议**: 在 `INopMetaQualityResultBiz` 中添加 `approve`/`reject` 方法签名。
- **信心水平**: 确定
- **复核状态**: 已知未修复，多维度审计 03-F02

---

### [AR-03] INopMetaDataContractBiz 接口缺失 approve/reject 方法声明

- **文件**:
  - `nop-metadata/nop-metadata-dao/src/main/java/io/nop/metadata/biz/INopMetaDataContractBiz.java:19-32`（声明了 4/6 方法）
  - `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaDataContractBizModel.java:37-135`（实现 6 个 @BizMutation 方法）
- **证据片段**:
  ```java
  // INopMetaDataContractBiz.java
  public interface INopMetaDataContractBiz extends ICrudBiz<NopMetaDataContract> {
      @BizMutation NopMetaDataContract activateContract(...);
      @BizMutation NopMetaDataContract deprecateContract(...);
      @BizMutation NopMetaDataContract retireContract(...);
      @BizMutation Map<String, Object> checkContract(...);
      // 缺少: approve, reject
  }

  // NopMetaDataContractBizModel.java
  @BizMutation
  public NopMetaDataContract approve(@Name("id") String id, IServiceContext context) { ... }
  @BizMutation
  public NopMetaDataContract reject(@Name("id") String id, IServiceContext context) { ... }
  ```
- **严重程度**: P1（多维度审计 03-F03 原定级）
- **现状**: 接口已声明了 4 个关键方法但缺少合约审批工作流的核心 action：`approve`/`reject`。BizModel 完整实现了这两个方法（含状态校验和审批人验证），但接口签名缺失意味着跨模块 `@Inject INopMetaDataContractBiz` 注入无法调用审批操作。
- **风险**: 数据合约的完整审批生命周期在 typed RPC 层不完整（只能 activate/deprecate/retire，不能 approve/reject）。
- **建议**: 在 `INopMetaDataContractBiz` 中添加 `approve`/`reject` 方法签名。
- **信心水平**: 确定
- **复核状态**: 已知未修复，多维度审计 03-F03

---

### [NF-01] MetaModelChangedEventPublisher.buildSnapshot 在 Throwable catch 块中丢失原始异常堆栈

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/event/MetaModelChangedEventPublisher.java:172-179`
- **证据片段**:
  ```java
  } catch (Throwable e) {
      throw new NopException(NopMetadataErrors.ERR_EVENT_SNAPSHOT_SERIALIZE_FAILED)
              .param("entityType", entityType)
              .param("entityId", entityId)
              .param("error", e.toString());   // ← 原始异常 stack trace 丢失！
  }
  ```
- **严重程度**: P2
- **现状**: `catch (Throwable e)` 将原始异常的 `toString()` 作为 param 值写入，但**未调用 `.cause(e)`** 或使用两参构造器。`NopException` 的 cause chain 为空，导致：
  1. 运维拿到 `ERR_EVENT_SNAPSHOT_SERIALIZE_FAILED` 但看不到完整的原始异常堆栈（仅能看到 `e.toString()` 的第一行）。
  2. `catch (Throwable)` 捕获 `OutOfMemoryError`、`StackOverflowError` 等 Error 类型——一般情况下 `Error` 应重新抛出而非捕获包装。
- **风险**: 快照序列化失败时诊断极其困难（不知道哪一层、哪一列触发了异常）。在 OOM 场景下，本 catch 块将 `Error` 包装后继续执行，可能使应用进入不一致状态。
- **建议**:
  ```java
  } catch (Exception e) {
      throw new NopException(NopMetadataErrors.ERR_EVENT_SNAPSHOT_SERIALIZE_FAILED, e)
              .param("entityType", entityType)
              .param("entityId", entityId);
  }
  ```
  将 `Throwable` 改为 `Exception`（不捕获 `Error`），使用两参构造器 `NopException(ErrorCode, Throwable)`。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [NF-02] SqlColumnLineageExtractor 为链式异常创建裸 IllegalStateException

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/lineage/SqlColumnLineageExtractor.java:188-218`（3 处）
- **证据片段**:
  ```java
  // 第 1 处（line 188-190）:
  throw new NopException(NopMetadataErrors.ERR_COL_LINEAGE_SQL_PARSE_FAILED)
          .param("sql", sql)
          .cause(new IllegalStateException("unhandled SELECT statement class: " + stmt.getClass().getName()));

  // 第 2 处（line 200-202）:
  throw new NopException(NopMetadataErrors.ERR_COL_LINEAGE_SQL_PARSE_FAILED)
          .param("sql", sql)
          .cause(new IllegalStateException("empty projections ..."));

  // 第 3 处（line 215-217）:
  throw new NopException(NopMetadataErrors.ERR_COL_LINEAGE_SQL_PARSE_FAILED)
          .param("sql", sql)
          .cause(new IllegalStateException("unhandled projection class: " + proj.getClass().getName()));
  ```
- **严重程度**: P2
- **现状**: 3 处创建 `IllegalStateException` 仅用于携带字符串消息作为 cause。消息应通过 `.param()` 传递。当前模式：
  1. 跨模块使用同一 ErrorCode `ERR_COL_LINEAGE_SQL_PARSE_FAILED` 但无法通过 param 区分具体失败原因（SQL 解析阶段不同）。
  2. 创建 `IllegalStateException` 裸实例对维护者产生误导（看起来像代码缺陷）。
  3. NopException 的 cause chain 应用于真正的外部异常（SQLException、IOException 等），而非作为文本容器。
- **风险**: 维护者看到 `IllegalStateException` 会觉得代码有问题；根因分析时消息散落在 cause 中而非 param 结构化字段。
- **建议**: 将每条错误改为独立 ErrorCode（`ERR_COL_LINEAGE_SQL_UNHANDLED_STATEMENT`、`ERR_COL_LINEAGE_SQL_EMPTY_PROJECTIONS`、`ERR_COL_LINEAGE_SQL_UNHANDLED_PROJECTION`），或将消息放入 `.param("reason", ...)`。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [NF-03] NopMetadataConstants.java 和 NopMetadataConfigs.java 为空接口存根

- **文件**:
  - `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/NopMetadataConstants.java:1-5`
  - `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/NopMetadataConfigs.java:1-5`
- **证据片段**:
  ```java
  // NopMetadataConstants.java
  package io.nop.metadata.service;
  public interface NopMetadataConstants{
  }
  // NopMetadataConfigs.java — 完全相同的空接口
  ```
- **严重程度**: P3（`NopMetadataConstants` 先前被多维度审计 09-11 定为 P1，但鉴于常量已集中在 `NopMetadataErrors.java` 且代码中无引用，降为 P3）
- **现状**: 多维度审计 09-11 指出 `NopMetadataConstants` 为空。第 3 轮审计声称工程规范类"全部或大部修复"但未提及此条。实际 `NopMetadataConstants` **仍为空**，同时发现第二个空接口 `NopMetadataConfigs`。
- **风险**: 新人被误导认为此接口有常量/配置可引用；自动补全时会看到这两个空接口。
- **建议**: 删除这两个空接口文件，或将真实常量/配置迁移至此（确认无此计划后删除）。
- **信心水平**: 确定
- **复核状态**: 已知未修复（多维度审计 09-11）+ 新发现（NopMetadataConfigs）

---

### [NF-04] `.cause(e)` 链式调用 vs 两参构造器（7 处）

- **文件**:
  1. `query/MetaTableQueryExecutor.java:139-142`
  2. `connection/MetaDataSourceConnectionProcessor.java:354-357`
  3. `query/DefaultFilterApplicator.java:77-80`
  4. `quality/MetaQualityRuleExecutor.java:638-641`
  5. `query/EntityEntityJoinAggregationProcessor.java:146-149`
  6. `query/AggregationContext.java:1081-1084`
  7. `query/MetaJoinExecutor.java:706-709`
- **证据片段**:
  ```java
  // 当前模式 (7 处全部一致的链式风格):
  throw new NopException(ERR_XXXX).param("sql", sql).param("error", messageOf(e)).cause(e);

  // 推荐模式:
  throw new NopException(ERR_XXXX, e).param("sql", sql).param("error", messageOf(e));
  ```
- **严重程度**: P3
- **现状**: 7 处异常构造使用 `.cause(e)` 链式方法而非 `NopException(ErrorCode, Throwable)` 两参构造器。在当前 `NopException` 实现下功能等价，但链式风格脆弱——如果 `NopException` 内部变更了 `.param()` 对 cause 的处理顺序（如某些实现中先记录 cause 再设置 param 会导致 cause 丢了部分 param 上下文），就会产生语义差异。
- **风险**: 极低——当前实现功能等价。但若团队决定统一风格，7 处不一致点需要逐一修复。
- **建议**: 逐步迁移到 `new NopException(ErrorCode, Throwable)` 两参构造器（对齐 Nop 平台的推荐用法）。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [NF-05] TestNopMetaBizInterfaceCompleteness 未覆盖 INopMetaDataProductBiz / INopMetaQualityResultBiz 接口验证

- **文件**: `nop-metadata/nop-metadata-service/src/test/java/io/nop/metadata/service/TestNopMetaBizInterfaceCompleteness.java:43-85`
- **证据片段**:
  ```java
  // 测试覆盖了 9 个接口:
  INopMetaTableBiz, INopMetaDataSourceBiz, INopMetaModuleBiz,
  INopMetaLineageEdgeBiz, INopMetaQualityRuleBiz, INopMetaQualityCheckpointBiz,
  INopMetaQualityScoreBiz, INopMetaDataContractBiz, INopMetaProfilingRuleBiz

  // 但未覆盖:
  // INopMetaDataProductBiz ← P0 缺口所在 (linkAsset/unlinkAsset/getLinkedAssets)
  // INopMetaQualityResultBiz ← P1 缺口所在 (approve/reject)
  ```
- **严重程度**: P2
- **现状**: Javadoc 注明了"验证至少 7 个 I*Biz 接口（实际验证 9 个）"，但选择性地跳过了 `INopMetaDataProductBiz` 和 `INopMetaQualityResultBiz`。这两个接口恰好是 P0/P1 级问题（AR-01/AR-02）的归属接口。测试无法捕获这些回归。
- **风险**: AR-01/AR-02 不会被任何自动化测试捕获；如果将来有人开始使用这些接口，可能会引入编译期无法检测的运行时故障。
- **建议**: 在 `testRequiredInterfacesContainCustomMethods()` 中增加 `INopMetaDataProductBiz`（`linkAsset`/`unlinkAsset`/`getLinkedAssets`）和 `INopMetaQualityResultBiz`（`approve`/`reject`）的断言。
- **信心水平**: 确定
- **发现来源视角**: 测试覆盖侦探

---

### [AR-04] nop-metadata-api 死模块仍在父 pom.xml 中声明且被打包

- **文件**: `nop-metadata/pom.xml:29`
- **证据片段**:
  ```xml
  <module>nop-metadata-api</module>
  ```
- **严重程度**: P3（与 R-02 状态一致）
- **现状**: 第 3 轮报告 R-02 时判断为 P3 死模块。重审确认：`src/` 目录仍不存在，全仓库 0 个外部引用。该模块打包产物 996 字节，仅含 `META-INF/`。
- **风险**: 构建噪音、新人误导。
- **建议**: 从 `<modules>` 移除并删除子目录。如果未来需要 typed RPC 接口再重新创建。
- **信心水平**: 确定
- **复核状态**: 已知未修复（R-02）

---

## 第 3 轮结论修正

### 第 3 轮声称已修复但实际未修复的发现

| 第 3 轮编号 | 声称 | 实际情况 |
|------------|------|---------|
| 全部 P0-P2 | "P0-P2 安全/数据完整性/工程规范问题全部修复" | 多维度审计 03-F01 (P0)、03-F02 (P1)、03-F03 (P1) **完全未修复**——工程规范类 P0/P1 仍在 |
| R-01 | `SqlAggregationProcessor` 存在 `IllegalArgumentException` | 实际已正确使用 `NopException(ERR_AGGR_UNSUPPORTED_TABLE_TYPE)`——声称错误（误报已修复） |
| R-04 | `data-auth.xml` 仅覆盖 3 个实体 | 实际已覆盖 8 个实体（含 Phase 2 的 QualityRule/ProfilingRule/ReconciliationResult/DataContract/BusinessDomain）——声称错误 |

### 第 3 轮盲区——本次审计深挖覆盖

| 第 3 轮报告的盲区 | 本次覆盖 |
|------------------|---------|
| 未做完整 32 BizModel 逐一方法签名审计 | 已做——发现 AR-01/02/03 接口缺口 |
| 未审计 Delta 定制目录 | 未覆盖——仍属盲区 |
| 未审计前端的 amis 页面 | 未覆盖——仍属盲区 |
| 未跑 Maven 构建 | 未覆盖——仍属盲区 |

---

## Multi-Dimension Audit 交叉验证：之前发现的修复状态

### 已确认修复（多维度审计原 P0/P1）

| 原 ID | 问题 | 修复证据 |
|-------|------|---------|
| 03-F06 | connectionConfig queryable=true 未覆盖 | xmeta 已设 `published="false" insertable="false" updatable="false"` |
| 03-F09/10 | 空 xmeta retention 层 | 37 个 retention xmeta 仍为空——但此为框架标准模式（空保留层接受基类生成物），不计为问题 |
| 09-05 | 中文硬编码业务消息 | 全模块 0 处中文错误消息；唯一遗留中文 `"外部表系统模块"` 为显示名称非错误消息 |

### 仍存在的问题（全部或部分未修复）

| 原 ID | 问题 | 当前状态 |
|-------|------|---------|
| 03-F01 | INopMetaDataProductBiz 空接口 | **P0 未修复** ——见 AR-01 |
| 03-F02 | INopMetaQualityResultBiz 空接口 | **P1 未修复** ——见 AR-02 |
| 03-F03 | INopMetaDataContractBiz 缺 approve/reject | **P1 未修复** ——见 AR-03 |
| 09-11 | NopMetadataConstants 空接口 | **P3 未修复** ——见 NF-03 |
| 03-F04 | 21 个方法返回 Map<String,Object> 而非 DTO | **部分进展**——DTO 已定义但未切换，见第 3 轮 R-03 |
| 01-02 | nop-metadata-service 的 nop-sys-dao 编译依赖 | **未验证** ——见盲区 |
| 16-01 | approve/reject 零测试覆盖 | **已改善** ——`TestNopMetaDataContractBizModel.java` 包含 approve/reject 测试 |
| 07-F2 | judgeByRuleId 无 @BizQuery/@BizMutation 注解 | **未验证** ——见盲区 |

---

## 总评

### 模块核心态势变化

nop-metadata 经历了高强度重构，**安全、ORM、内存安全、SQL 注入类的 P0/P2 问题已被彻底解决**（上一轮审计正确确认了这一点）。但**跨模块 API 契约的 3 个 P0/P1 问题完全未被触及**。

本次审计最大发现是：第 3 轮审计的"全部 P0-P2 已修复"声明具有误导性——它只覆盖了自己 14 条+重叠发现的修复状态，但 **多维度审计的 P0（03-F01）和 P1（03-F02/03）被完全遗漏**。同时第 3 轮自己对 2 个修复状态的声明确认有误（R-01 被错误报告为未修复，R-04 被错误报告为范围不足）。

### 本次发现中最值得关注的 3 个方向

1. **P0 接口契约缺口（AR-01）仍在**：`INopMetaDataProductBiz` 空接口是唯一剩余 P0 问题。不修复它，跨模块 typed RPC 的整条路线上就有"断头路"——但现在因为它未被任何外部模块引用，所以没有被触发。
2. **`MetaModelChangedEventPublisher.buildSnapshot` 堆栈丢失（NF-01）**: 这是框架核心与模块边界交汇处的典型"灯下黑"——事件快照序列化失败在实际生产中的诊断难度极高，且 `catch Throwable` 过度捕获。
3. **审计回路中的自反性盲区**: 测试 `TestNopMetaBizInterfaceCompleteness` 不验证存在 P0 缺口的接口（INopMetaDataProductBiz/INopMetaQualityResultBiz）——这正是 P0 问题未被任何自动化防线拦截的根因。

### 本次审查的盲区自评

- **未跑 Maven 构建**：未执行 `./mvnw test -pl nop-metadata -am`，未验证编译和测试通过。
- **未审计 nop-metadata-web 前端 amis 页面**：150+ 页面文件的字段展示与 xmeta `published` 的一致性未验证。
- **未审计 Delta 定制目录**：未检查是否有外部模块通过 Delta 机制覆盖 nop-metadata 的配置/模型。
- **未审计 nop-metadata-api 模块外的其他死依赖**：未验证 `nop-sys-dao`、`nop-wf-meta` 等编译依赖是否真正为 dead code。
- **未验证 `judgeByRuleId` 的 @BizQuery/@BizMutation 注解状态**：multi-dim 07-F2 提及此问题但未在本次审计中复核。
- **未审计 nop-metadata-service pom.xml 中 nop-wf-core/nop-wf-meta 的 scope**：未确认是否应为 runtime/test 而非 compile。
- **未审计 39 个 xmeta retention 文件的字段级 override 覆盖度**：仅确立了"空 retention = 框架标准模式"的判断，未逐个字段确认生成的 xmeta 是否足够安全。

### 按严重程度分布表

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 1    | `INopMetaDataProductBiz` 接口完全缺失（AR-01，cross 03-F01，未修复） |
| P1      | 2    | `INopMetaQualityResultBiz` 缺 approve/reject（AR-02，未修复）；`INopMetaDataContractBiz` 缺 approve/reject（AR-03，未修复） |
| P2      | 3    | 事件快照堆栈丢失（NF-01）；`IllegalStateException` 链式异常（NF-02，3 处）；接口完备性测试缺口（NF-05） |
| P3      | 4    | 空常量/配置接口存根（NF-03）；`.cause(e)` 链式风格（NF-04，7 处）；`nop-metadata-api` 死模块（AR-04）；DTO 未切换（第 3 轮 R-03，状态不变） |

<AI_STEP_RESULT>issues</AI_STEP_RESULT>
