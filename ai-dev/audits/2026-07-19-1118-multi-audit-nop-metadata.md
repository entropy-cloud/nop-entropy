> Audit Status: planned
> Audit Type: multi-dimensional
> Mission: nop-metadata

# nop-metadata 多维度审计报告

- **审核模块**: `nop-metadata/`
- **审核日期**: 2026-07-19
- **审计基线**: live code (HEAD)，按 `ai-dev/skills/deep-audit-prompts.md` 第 1-1292 行口径执行
- **执行维度**: 01-21（共 21 个维度，分 4 批并行派发独立 explore 子 agent + 主 agent 复核合并）
- **目标范围**: 8 个 Maven 子模块（api/codegen/core/dao/meta/service/web/app）、33 个 ORM 实体、32 个 BizModel、约 80 个 service 主代码 Java 文件、~28 个测试文件

---

## 执行统计

| 维度 | 深挖轮次 | 初审发现数 | 保留 | 降级 | 驳回 |
|------|---------|-----------|------|------|------|
| 01 依赖图与模块边界 | 1 | 2 | 2 | 0 | 0 |
| 02 模块职责与文件边界 | 1 | 4 | 4 | 0 | 0 |
| 03 API 表面与契约一致性 | 1 | 2 (与 07/11/15 部分合并) | 2 | 0 | 0 |
| 04 ORM 模型与实体设计 | 1 | 11 | 11 | 0 | 0 |
| 05 生成管线完整性 | 1 | 1 (信息项) | 1 | 0 | 0 |
| 06 Delta 定制合规性 | 1 | 0 (clean) | 0 | 0 | 0 |
| 07 BizModel 规范遵循 | 1 | 3 (1 与 03/11 合并) | 3 | 0 | 0 |
| 08 IoC 与 Bean 配置 | 1 | 0 (clean) | 0 | 0 | 0 |
| 09 错误处理与错误码 | 1 | 10 (1 与 19 合并) | 10 | 0 | 0 |
| 10 XDSL 与 XLang 正确性 | 1 | 0 (clean) | 0 | 0 | 0 |
| 11 XMeta 与 BizModel 对齐 | 1 | 1 (与 03/07 合并) | 1 | 0 | 0 |
| 12 GraphQL 与 API 层 | 1 | 1 | 1 | 0 | 0 |
| 13 安全与权限模型 | 1 | 4 | 4 | 0 | 0 |
| 14 异步与事务模式 | 1 | 3 (1 与 13 合并) | 3 | 0 | 0 |
| 15 类型安全与泛型使用 | 1 | 2 (1 与 03 合并) | 2 | 0 | 0 |
| 16 测试覆盖与质量 | 1 | 0 (正向) | 0 | 0 | 0 |
| 17 代码风格与规范 | 1 | 2 | 2 | 0 | 0 |
| 18 文档-代码一致性 | 1 | 5 | 5 | 0 | 0 |
| 19 命名与术语一致性 | 1 | 2 (1 与 09 合并) | 2 | 0 | 0 |
| 20 跨模块契约一致性 | 1 | 1 (其余正向) | 1 | 0 | 0 |
| 21 单元测试有效性 | 1 | 0 (正向) | 0 | 0 | 0 |

去重后**独立发现**数：约 **46 条**（含跨维度合并去重）。

## 按严重程度分布（去重后）

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | — |
| P1 | 8 | IBiz 接口空 / Map 返回值 / ORM 关系缺失 / 索引缺失 / LineageEdge 无 relations / DataSource 凭据明文 / 文档缺失 / System.currentTimeMillis / 表名缩写 |
| P2 | 22 | 大类拆分、命名违规、错误码集中化、连接池/事务、SSRF、自定义 SQL、import 顺序、roadmap 过时等 |
| P3 | 16 | 死代码、命名细节、行宽、跨模块文档锚点缺失等 |

---

## 维度 01：依赖图与模块边界

### 内部依赖图

```
api    → nop-api-core                          [src=0, ZERO content]
core   → nop-api-core                          [src=2: 常量, 406 行]
codegen→ nop-ooxml-xlsx, nop-orm,              [src=2: xgen 脚本]
          nop-graphql-core, nop-xlang-debugger
dao    → nop-api-core, nop-orm,                [src=99]
          nop-metadata-core (compile!),
          nop-metadata-codegen (test)
meta   → nop-metadata-codegen (test),          [src=0, 仅 xmeta/资源]
          nop-metadata-dao (test)
service→ dao, meta, codegen(test),              [src=80]
          nop-biz, nop-http-api, nop-biz-file-core,
          nop-config, nop-ioc, nop-sys-dao,
          nop-job-api (compile), nop-job-local (test)
web    → meta, service, codegen(test), nop-web [src=页面资源]
app    → service, web,                          [quarkus 启动]
          nop-quarkus-web-orm-starter, nop-auth-web,
          nop-auth-service, nop-web-amis-editor,
          nop-web-site, quarkus-jdbc-{mysql,h2}
```

### [维度01-01] `nop-metadata-api` 为零内容死模块

- **文件**: `nop-metadata/nop-metadata-api/pom.xml:14-25`、`nop-metadata/nop-metadata-api/src/`（目录不存在）
- **证据片段**:
  ```xml
  <artifactId>nop-metadata-api</artifactId>
  <properties><java.version>11</java.version></properties>
  <dependencies>
      <dependency>
          <groupId>io.github.entropy-cloud</groupId>
          <artifactId>nop-api-core</artifactId>
      </dependency>
  </dependencies>
  ```
  全仓 `grep -r "nop-metadata-api" --include=pom.xml` 仅命中 `nop-metadata/pom.xml`（声明）和 `nop-metadata-api/pom.xml` 自身，**没有任何其他模块引用**。打包产物 `nop-metadata-api-2.0.0-SNAPSHOT.jar` 仅 996 字节，只含 `META-INF/`，无任何 `.class`。
- **严重程度**: P2
- **现状**: api 子模块在 `nop-metadata/pom.xml:24` 中声明、被构建、被打包、被 install/deploy 到本地仓库，但 0 个源文件、0 个消费者。对照同仓库 `nop-auth-api`（48+ 文件）、`nop-job-api`（36+ 文件）、`nop-task-api`（12 文件），"空 api"并非本仓库主流形态。
- **风险**: 每次构建白白多一个 Maven 模块；新人或 AI 看到该模块会误以为需要扩展 typed RPC 接口而写入；CI 失败时干扰定位。
- **建议**: 二选一：(a) 若短期不打算提供 typed RPC，从 `<modules>` 移除并删除该子目录；(b) 若计划补齐，至少为高频外部调用（`importOrmModel`、`queryAggregation`、`testConnection`）抽出 typed 接口。
- **信心水平**: 确定
- **误报排除**: 与 [维度01-02] 不同——后者讨论 dao→core 的依赖关系；本条专门讨论 api 模块自身零内容。
- **复核状态**: 已保留

### [维度01-02] dao 对 core 的 compile 依赖偏离 nop-auth 形态

- **文件**: `nop-metadata/nop-metadata-dao/pom.xml:24-28`
- **证据片段**:
  ```xml
  <dependency>
      <artifactId>nop-metadata-core</artifactId>
      <groupId>io.github.entropy-cloud</groupId>
      <version>2.0.0-SNAPSHOT</version>
  </dependency>
  ```
  对照 `nop-auth/nop-auth-dao/pom.xml:15-37`：仅依赖 `nop-biz-auth-api + nop-api-core + nop-orm`，**无 `-core` 子模块依赖**（nop-auth 根本没有 `-core`）。
- **严重程度**: P3
- **现状**: nop-metadata 自建 `-core` 子模块但仅放常量（`NopMetadataCoreConstants` 5 行 + `_NopMetadataCoreConstants` 401 行）。dao 通过 compile 依赖消费这些常量。
- **风险**: 引入额外的 Maven 子模块带来 pom 维护成本与依赖图复杂度；与最贴近的标准业务模块样板 nop-auth 不一致；对 `-core` 的"应有规模"形成错误示范（nop-job-core/nop-task-core 都是数百文件级别）。
- **建议**: 二选一：(a) 把 `_NopMetadataCoreConstants` 合并到 dao 模块（`io.nop.metadata.dao._NopMetadataDaoConstants` 现已存在但为空，正好可填充），移除 `-core` 子模块；(b) 若坚持保留 `-core`，文档化其"仅常量"的定位差异。推荐 (a)。
- **信心水平**: 很可能
- **误报排除**: 与 nop-job-core/nop-task-core 不同——后两者含实质领域逻辑（trigger/calendar/scheduler/flow 等），单独成模块有明确收益；本模块 `-core` 仅 2 文件常量，收益不抵开销。
- **复核状态**: 已保留

### 阴性发现（无问题）

- **无循环依赖**：依赖图严格 DAG，符合 `domain-module-pattern.md`。
- **meta→dao/codegen 仅 test scope**：与 `domain-module-pattern.md` 描述一致（meta 子模块只需资源 + 测试期 codegen）。
- **service→meta 的 compile 依赖**：与 nop-auth-service 形态一致（service 在运行时需读取 xmeta 资源以构建 GraphQL schema）。

---

## 维度 02：模块职责与文件边界

### [维度02-01] service 模块内含 `*Service` 命名类，违反平台命名约定

- **文件**:
  - `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/connection/MetaDataSourceConnectionService.java`
  - `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/connection/IMetaDataSourceConnectionService.java:15`
  - `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/reconciliation/LocalReconciliationService.java`
  - `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/reconciliation/IReconciliationService.java:16`
  - 注册位置：`nop-metadata/nop-metadata-service/src/main/resources/_vfs/nop/metadata/beans/app-service.beans.xml:1-38`
- **证据片段**:
  ```xml
  <bean id="io.nop.metadata.service.connection.MetaDataSourceConnectionService"
        class="io.nop.metadata.service.connection.MetaDataSourceConnectionService" ioc:default="true"/>
  <bean id="io.nop.metadata.service.reconciliation.LocalReconciliationService"
        class="io.nop.metadata.service.reconciliation.LocalReconciliationService" ioc:default="true"/>
  ```
- **严重程度**: P2
- **现状**: 4 个 `*Service` / `I*Service` 命名的内部 IoC bean，承担 Processor 职责（连接管理、对账匹配），但使用了平台文档明令回避的命名。`docs-for-ai/02-core-guides/service-layer.md:191,236` 明确："Nop 平台回避 Controller / Service 这类命名...不要在 Nop 模块中创建 `*Controller` 或 `*Service` 类"。同模块的 `MetaContractChecker`、`MetaModelChangedEventPublisher` 等用 Checker/Publisher 后缀未违规，反衬出 `*Service` 后缀的不一致。
- **风险**: AI 与新开发者读到 `*Service` 会按 Spring 心智模型假设其有 `@Transactional`/AOP 行为或前端可达；新人复制该模式会扩散违规命名。
- **建议**: 改名为 Processor 风格：`MetaDataSourceConnectionProcessor` / `LocalReconciliationProcessor`（或保留语义的 `*Manager`/`*Provider`/`*Helper`）；同步更新 `app-service.beans.xml` 中的 bean id 与 class，以及所有 `@Inject` 字段。
- **信心水平**: 很可能
- **误报排除**: 这些类确实是内部 helper bean（不暴露 GraphQL/REST 端点），并非"伪 BizModel"；本条专门讨论命名违规。
- **复核状态**: 已保留

### [维度02-02] `MetaAggregationExecutor` 单类 3474 行，应拆 Processor

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/MetaAggregationExecutor.java:1-3474`
- **证据片段**:
  ```java
  public class MetaAggregationExecutor {
      // 唯一 public 入口：
      public Map<String, Object> executeAggregation(NopMetaTable table, List<String> measureNames,
                                                     List<String> dimensionNames, TreeBean userFilter, String joinId,
                                                     Long limit, Long offset, TreeBean having,
                                                     List<OrderFieldBean> orderBy, MetaQueryContext ctx) { ... }
      // 内部 14 个嵌套类（CrossDbField/CrossDbMeasureSpec/.../JoinFieldResolver/JoinExternalSideResolver
      //   /JoinMixedSideResolver/SumAcc/CountAcc/AvgAcc/MinAcc/MaxAcc/CountDistinctAcc
      //   /MeasureSpec/DimensionSpec/JoinMeasureSpec/JoinDimensionSpec/JoinField）
      // 96 个方法、12+ 显式 ErrorCode 常量
  }
  ```
- **严重程度**: P2
- **现状**: 单一文件 3474 行，含 entity-聚合 / external-聚合 / sql-聚合 / entity-entity JOIN / external-external JOIN / mixed-same-db JOIN / cross-db 内存聚合 7 条执行路径 + 6 个内存聚合 Accumulator + 3 个 JOIN 字段解析器 + 12+ ErrorCode。已是该模块最大文件，远超第二名 `MetaJoinExecutor`（926 行）。
- **风险**: 违反 `service-layer.md:243-254`「何时拆 Processor」全部 4 条触发条件（多步骤编排、复用需求、外部交互拆分、BizModel 方法不可读）；高复杂度分支路径集中在单文件，单测覆盖率难提升，重构时回归风险极高；ErrorCode 内联在执行器里分散错误来源；AI 阅读该文件 token 消耗大、修改时易引入回归。
- **建议**: 按执行路径拆为同包多 Processor：`EntityAggregationProcessor` / `ExternalAggregationProcessor` / `SqlAggregationProcessor` / `EntityEntityJoinAggregationProcessor` / `ExternalExternalJoinAggregationProcessor` / `MixedSameDbJoinAggregationProcessor` / `CrossDbInMemoryAggregationProcessor`；6 个 Accumulator 提为独立文件或合并为 `MemAggAccumulator` + factory；3 个 SideResolver 提为独立 helper 类；12+ ErrorCode 上移到 `NopMetadataErrors`。
- **信心水平**: 很可能
- **误报排除**: 不是 API 契约类型问题，是组织结构问题。
- **复核状态**: 已保留

### [维度02-03] 同模块内 `TableReference` 类名冲突（两个不同含义）

- **文件**:
  - `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/lineage/TableReference.java:10`（fullName + simpleName，SQL 源表抽取结果）
  - `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/tableref/TableReference.java:27`（kind + dataSource + entity + fields，查询执行时的表引用）
- **证据片段**:
  ```java
  // lineage/TableReference.java
  public final class TableReference {
      private final String fullName;
      private final String simpleName;
  }
  // tableref/TableReference.java
  public final class TableReference {
      public enum Kind { EXTERNAL, ENTITY, SQL }
      private final Kind kind;
      private final NopMetaDataSource dataSource;
      // ... 8 个字段
  }
  ```
  `NopMetaLineageEdgeBizModel.java:26` import 前者；`NopMetaTableBizModel.java:42` / `MetaAggregationExecutor.java:26` / `MetaCatalogCollector.java:6` / `MetaTableProfiler.java:6` / `MetaQualityRuleExecutor.java:6` 等 9 处 import 后者。
- **严重程度**: P3
- **现状**: 同一 Maven 模块内两个 `TableReference` 同名类，含义完全不同（SQL 静态分析 vs 运行时执行引用）。
- **风险**: IDE 自动 import 易选错包；代码评审时易混淆；Java 静态分析工具报告同 simple name 在同 artifact 内冲突时会产生噪音。
- **建议**: 重命名 `lineage.TableReference` 为 `SqlTableReference`（更具体），保留 `tableref.TableReference` 不变（消费方更多）。
- **信心水平**: 很可能
- **误报排除**: 两个类不是同一职责的重复实现，字段集合完全不重叠、消费场景不重叠，纯属命名冲突。
- **复核状态**: 已保留

### [维度02-04] `dao/model/OrmModelImporter.java` 手写 importer 放 dao 层（边界轻微偏离）

- **文件**: `nop-metadata/nop-metadata-dao/src/main/java/io/nop/metadata/dao/model/OrmModelImporter.java:45-248`
- **证据片段**:
  ```java
  /**
   * 将平台 {@link IOrmModel}（解析自 model/*.orm.xml）拆解为 nop_metadata 的结构化实体。
   * 仅负责 new + 填充属性，不负责持久化与外键串联（由调用方 BizModel 负责）。
   */
  public class OrmModelImporter {
      public NopMetaModule buildModule(IOrmModel ormModel, long moduleVersion) { ... }
      public NopMetaOrmModel buildOrmModel(IOrmModel ormModel, String sourceContent, boolean isDelta) { ... }
      // 12 个 build* 方法
  }
  ```
- **严重程度**: P3
- **现状**: 该类是纯 builder（new + 填字段，无持久化），但语义上属于"业务导入逻辑"而非"DAO 派生产物"。放在 dao 层使 dao 承担了本应由 service 层承担的"导入编排"语义；对照 `domain-module-pattern.md:46` 中 `-dao/` 的典型产物是 `_app.orm.xml`、Entity、I*Biz 接口，importer 不在此列。
- **风险**: 边界含糊：未来 importer 演化需要追加"日志/事件/校验"时会被强行写进 dao（因 dao 无 BizModel 上下文，会绕过平台管道）。
- **建议**: 移到 `nop-metadata-service/.../service/importer/OrmModelImporter.java`；或保留在 dao 但显式重命名为 `NopMetaEntityAssembler` / `OrmModelAssembler`。
- **信心水平**: 很可能
- **复核状态**: 已保留

### 阴性发现

- `_NopMetadataCoreConstants.java` 的"双层常量"模式（`_` 前缀 + 非 `_` 空继承接口）符合 Nop 平台 codegen 留存层约定，对照 `nop-job-core` 同构，非异常。
- `_gen/` 下无手写代码混入；`_service.beans.xml`、`_dao.beans.xml`、`_app.orm.xml`、`_*.xmeta`、`_*.xbiz` 均为生成产物，手写扩展分别对应非下划线留存层文件，符合 `domain-module-pattern.md`。

---

## 维度 03 + 07 + 11 + 15：API 契约与 BizModel 一致性（跨维度合并）

### [维度03-01 / 07-01 / 11-01] `I*Biz` 接口全部为空，BizModel 自定义方法未同步

- **文件**: `nop-metadata/nop-metadata-dao/src/main/java/io/nop/metadata/biz/INopMeta*Biz.java`（全部 32 个接口均为 11 行）
- **证据片段**:
  ```java
  // INopMetaTableBiz.java（全文）
  package io.nop.metadata.biz;
  import io.nop.orm.biz.ICrudBiz;
  import io.nop.metadata.dao.entity.NopMetaTable;
  public interface INopMetaTableBiz extends ICrudBiz<NopMetaTable>{
  }
  ```
  而 `NopMetaTableBizModel` 的 7 个自定义 public 方法（`profileTable`、`createSqlTable`、`previewSqlFields`、`resolveTableFields`、`queryTableData`、`queryJoinData`、`queryAggregation`）均未声明在 `INopMetaTableBiz`。同理 NopMetaDataSourceBizModel 4 个、NopMetaModuleBizModel 4 个、NopMetaLineageEdgeBizModel 8 个等。
- **严重程度**: P1
- **现状**: 全部 32 个 `INopMeta*Biz` 接口仅 `extends ICrudBiz<T>` 无自定义方法声明；至少 10 个 BizModel 共 37+ 个自定义 `@BizQuery`/`@BizMutation` 方法都不在对应接口上。违反 `service-layer.md:121-127` 的强制规则："BizModel 上新增的每一个 `public` 方法，都必须在对应的 `I*Biz` 接口上声明"。
- **风险**: `BizProxyFactoryBean` 生成的动态代理仅识别接口上的方法，跨模块通过 `@Inject INopMetaTableBiz` 调用 `queryAggregation(...)` 会抛 `unsupported-method`。当前模块内已用「注入 BizModel 实现类」绕过（见 [维度07-02]），但跨模块消费方完全无法访问这些能力。
- **建议**: 对每个有自定义 public 方法的 BizModel，把方法（含 `@BizQuery`/`@BizMutation` 注解与 `@Name`）同步到对应 `INopMeta*Biz` 接口；至少先补 NopMetaTableBizModel（7）、NopMetaDataSourceBizModel（4）、NopMetaModuleBizModel（4）、NopMetaLineageEdgeBizModel（8）四个高频 BizModel。
- **信心水平**: 确定
- **复核状态**: 已保留

### [维度03-02 / 15-01] 20+ BizModel 方法返回 `Map<String, Object>`，违反 DTO 约定

- **文件**:
  - `nop-metadata-service/.../entity/NopMetaTableBizModel.java:278,323,386,417,496,541,578`（7 处）
  - `nop-metadata-service/.../entity/NopMetaDataSourceBizModel.java:108,145,228,307`（4 处）
  - `nop-metadata-service/.../entity/NopMetaLineageEdgeBizModel.java:98,166,233,470`（4 处）
  - `nop-metadata-service/.../entity/NopMetaModuleBizModel.java:344`（`List<Map<String,Object>> importOrmModels`）
  - `nop-metadata-service/.../entity/NopMetaQualityRuleBizModel.java:134,190`
  - `nop-metadata-service/.../entity/NopMetaQualityScoreBizModel.java:53`
  - `nop-metadata-service/.../entity/NopMetaQualityCheckpointBizModel.java:130`
  - `nop-metadata-service/.../entity/NopMetaProfilingRuleBizModel.java:90`
  - `nop-metadata-service/.../entity/NopMetaDataContractBizModel.java:120`
- **证据片段**（`NopMetaTableBizModel.queryAggregation`）:
  ```java
  @BizQuery
  public Map<String, Object> queryAggregation(@Name("metaTableId") String metaTableId,
                                                 @Name("measures") List<String> measures,
                                                 @Name("dimensions") List<String> dimensions,
                                                 @Optional @Name("filter") TreeBean filter,
                                                 /* ... 6 个参数 */
                                                 IServiceContext context) {
      return aggregationExecutor.executeAggregation(table, measures, dimensions, filter, joinId, limit, offset,
              having, orderBy, buildQueryContext());
  }
  ```
  返回结构（`MetaAggregationExecutor.java:3091-3095`）:
  ```java
  private static Map<String, Object> buildResult(List<Map<String, Object>> items) {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("items", items != null ? items : new ArrayList<>());
      return result;
  }
  ```
- **严重程度**: P1
- **现状**: 至少 20 个 BizModel public 方法返回 `Map<String, Object>`（或 `List<Map<String,Object>>`），全部是 `@BizQuery`/`@BizMutation` 暴露给 GraphQL/REST 的对外 API。全模块未定义任何 `@DataBean` DTO（`grep -rn "@DataBean"` 在 service 层 0 命中）。文档 `service-layer.md:74` 与 `api-and-graphql.md:10,175-176` 明确禁止。
- **风险**: GraphQL schema 无法强类型推导返回字段，前端 selection 写法退化为 `{ items }` 后取子字段无补全；API 文档生成器（GraphQL introspection / OpenAPI）无法表达字段约束；前端 TypeScript 类型生成失效；返回字段集（`syncedTableCount` / `errors` / `collectedCount` / `items` / `connected` / `databaseProductName` 等）对调用方完全不透明；测试只能 `assertEquals(map.get("xxx"))` 字符串 key，重构时静默漏改。
- **建议**: 每个返回 Map 的方法替换为 `@DataBean`：
  - `queryAggregation` → `AggregationResultDTO { List<AggregationRowDTO> items }`，`AggregationRowDTO { Map<String,Object> dimensions; Map<String,Object> measures }`（聚合行字段名动态，DTO 内部用 `Map` 是合理的，但外层 envelope 应强类型）
  - `profileTable` → `ProfileResultDTO { String metaTableId; List<ProfilingColumnStatsDTO> columns; List<ErrorDTO> errors }`
  - `testConnection` → `TestConnectionResultDTO { boolean connected; String databaseProductName; String error; ... }`
  - `syncExternalTables` → `SyncExternalTablesResultDTO { int syncedTableCount; List<ErrorDTO> errors }`
  放置位置：`nop-metadata-dao/.../dto/`（多个 BizModel 共享时）或 `nop-metadata-service/.../dto/`（仅单个 BizModel 使用时）。
- **信心水平**: 确定
- **复核状态**: 已保留

### [维度07-02] 直接注入 BizModel 实现类（非 `I*Biz` 接口）

- **文件**:
  - `nop-metadata-service/.../entity/NopMetaReconciliationConfigBizModel.java:70-71`
  - `nop-metadata-service/.../entity/NopMetaQualityCheckpointBizModel.java:71-72`
  - `nop-metadata-service/.../quality/MetaQualityCheckpointScheduler.java:101-102`
- **证据片段**:
  ```java
  // NopMetaReconciliationConfigBizModel.java:70-71
  /** B2 方案 b：注入 NopMetaTableBizModel 具体类（NopIoC bean）调 queryTableData 取数。 */
  @Inject
  protected NopMetaTableBizModel tableBizModel;

  // NopMetaQualityCheckpointBizModel.java:71-72
  @Inject
  protected NopMetaQualityScoreBizModel scoreBizModel;
  ```
- **严重程度**: P2
- **现状**: 三处直接 `@Inject` BizModel 实现类（非接口）。注释里写明是 "B2 方案 b"，本质是因为 `I*Biz` 接口为空（见上）而被迫选择的工作绕过。
- **风险**: 违反 `service-layer.md` "直接注入其他 BizModel 实现类 → 降低跨模块可替换性"。
- **建议**: 先补全 `I*Biz` 接口，然后改为 `@Inject protected INopMetaTableBiz tableBiz` / `INopMetaQualityScoreBiz scoreBiz` / `INopMetaQualityCheckpointBiz checkpointBiz`。
- **信心水平**: 确定
- **复核状态**: 已保留

### [维度07-03] 跨服务调用未透传 `IServiceContext`

- **文件**: `nop-metadata-service/.../quality/MetaQualityCheckpointScheduler.java:189-199`
- **证据片段**:
  ```java
  public Map<String, Object> executeScheduledCheckpoint(Map<String, Object> params) {
      Object cpId = params.get(PARAM_CHECKPOINT_ID);
      if (cpId == null) {
          throw new NopException(ERR_CHECKPOINT_SCHEDULER_INVALID_CRON)
                  .param("checkpointId", "<null>")
                  .param("cron", "<n/a>");
      }
      String checkpointId = String.valueOf(cpId);
      // null context 安全：computeQualityScore 内部从不解引用 context（架构基线 §2.7.3.1 D3 R2 核实）
      return checkpointBizModel.executeCheckpoint(checkpointId, null, null);
  }
  ```
- **严重程度**: P2
- **现状**: 调用 `executeCheckpoint(checkpointId, null, null)` 透传 null `IServiceContext`。注释自辩 "computeQualityScore 内部从不解引用 context"，但 `executeCheckpoint` 实际还会触发 `triggerAutoScoring` → `scoreBizModel.computeQualityScore(metaTableId, context)`，链路较深。
- **风险**: 违反 `service-layer.md:232` "跨服务调用必须把 context 透传给下游"。cron 触发的检查点执行将完全跳过数据权限检查（`checkDataAuth`）和审计身份记录。
- **建议**: cron 触发场景应构造 system/service account 的 `IServiceContext`（参考平台其它模块的定时任务模式），或显式在文档/ErrorCode 中声明"cron 触发跳过权限校验是设计决策"。
- **信心水平**: 很可能
- **复核状态**: 已保留

### [维度07-04] `NopMetaTableBizModel` 体量过大（984 行），编排/SQL 拼接/事务逻辑未抽 Processor

- **文件**: `nop-metadata-service/.../entity/NopMetaTableBizModel.java:1-984`
- **证据片段**:
  ```java
  // 单表数据查询 queryTableData（架构基线 §4.4 D1/D2）
  // ---- entity 分派：经 IOrmTemplate（架构基线 §4.4 D1）----
  private Map<String, Object> queryEntityTable(NopMetaTable table, TreeBean filter, Long limit, Long offset) { ... }
  // ---- external 分派 ----
  private Map<String, Object> queryExternalTable(...) { ... }
  private static String buildExternalSelectSql(...) { ... }  // SQL 拼接
  private static List<Map<String, Object>> executeQuery(Connection conn, String sql, ...) { ... }  // JDBC 执行
  ```
- **严重程度**: P3
- **现状**: 单 BizModel 984 行，混合 5 类职责：基线 CRUD + profiling 入口 + SQL 视图创建/字段解析 + 单表三路查询（entity/external/sql 含 JDBC 执行）+ JOIN/聚合代理。
- **风险**: `service-layer.md:246-250` "何时拆 Processor" 列出 4 个判据，本类命中 (1) "明显多步骤编排流程" 和 (4) "单个 BizModel 方法已经难以阅读和测试"。
- **建议**: 抽出 `MetaTableQueryExecutor`，承载 `queryEntityTable`/`queryExternalTable`/`querySqlTable` + SQL 拼接 + JDBC 执行；BizModel 仅做表加载、entityName 校验、错误上下文附加。
- **信心水平**: 很可能
- **复核状态**: 已保留

---

## 维度 04：ORM 模型与实体设计

### [维度04-01] 父实体普遍缺失 to-many 关系声明（与 nop-auth 约定不一致）

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:472-491`（NopMetaEntity），以及全部 32 个实体
- **证据片段**:
  ```xml
  <entity className="io.nop.metadata.dao.entity.NopMetaEntity" ... versionProp="version" ext:icon="table">
      <columns> ... </columns>
      <relations>
          <to-one displayName="ORM模型" name="ormModel"
                  refEntityName="io.nop.metadata.dao.entity.NopMetaOrmModel" tagSet="pub,ref-pub">
              <join><on leftProp="ormModelId" rightProp="ormModelId"/></join>
          </to-one>
      </relations>
  </entity>
  ```
  对比 `nop-auth/model/nop-auth.orm.xml:153-160`（NopAuthUser）:
  ```xml
  <to-many cascadeDelete="true" displayName="角色映射" name="roleMappings"
           refEntityName="io.nop.auth.dao.entity.NopAuthUserRole" refPropName="role"
           tagSet="pub,cascade-delete,insertable,updatable"/>
  ```
- **严重程度**: P1
- **现状**: 全模块 32 个实体中，所有 parent-child 关系都只在子方声明 to-one，父方一律没有反向 to-many。应补的对应关系（部分）：
  - `NopMetaEntity` → `NopMetaEntityField`、`NopMetaEntityRelation`、`NopMetaEntityIndex`、`NopMetaEntityUniqueKey`
  - `NopMetaOrmModel` → `NopMetaEntity`、`NopMetaDomain`、`NopMetaDict`
  - `NopMetaModule` → `NopMetaOrmModel`、`NopMetaTable`、`NopMetaPipeline`、`NopMetaManifest`、`NopMetaQualityCheckpoint`、`NopMetaReconciliationConfig`
  - `NopMetaTable` → `NopMetaTableDimension`、`NopMetaTableMeasure`、`NopMetaTableFilter`、`NopMetaTableJoin`、`NopMetaCatalog`、`NopMetaProfilingRule`、`NopMetaQualityScore`、`NopMetaDataContract` 等
  - `NopMetaDict` → `NopMetaDictItem`
  - `NopMetaPipeline` → `NopMetaLineageEdge`
- **风险**: (1) 无法从父实体级联删除子记录（孤儿数据堆积）；(2) xmeta/GraphQL 不暴露反向导航，前端无法用 `entity.fields` 这种路径查询；(3) 与 nop-auth 等 platform 模块约定不一致。
- **建议**: 在源 `model/nop-metadata.orm.xml` 中为每个父子关系补 `<to-many cascadeDelete="true" ...>`，并给被引用的 to-one 加 `refPropName="..."` 反向指针。
- **信心水平**: 确定
- **复核状态**: 已保留

### [维度04-02] `NopMetaLineageEdge` 完全缺失 `<relations>` 块

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:1428-1487`
- **证据片段**:
  ```xml
  <entity className="io.nop.metadata.dao.entity.NopMetaLineageEdge" ... ext:icon="waypoints">
      <columns>
          <column code="SOURCE_TABLE_ID" ... name="sourceTableId" .../>
          <column code="TARGET_TABLE_ID" ... name="targetTableId" .../>
          ...
          <column code="PIPELINE_ID" ... name="pipelineId" .../>
      </columns>
      <indexes>
          <index name="IX_NOP_META_LINEAGE_SOURCE" .../>
          <index name="IX_NOP_META_LINEAGE_TARGET" .../>
      </indexes>
      <!-- 注意：完全没有 <relations> 块 -->
  </entity>
  ```
- **严重程度**: P1
- **现状**: `sourceTableId`、`targetTableId`、`pipelineId` 三个外键列在 ORM 模型层未声明任何 to-one 关系，无法在 ORM/GraphQL/xmeta 中通过 `sourceTable`、`targetTable`、`pipeline` 等关联属性导航到目标实体。
- **风险**: 血缘模块的核心场景就是"沿边遍历"，缺关系使 ORM 层无法 eager-fetch、GraphQL 无法 selection-set、xmeta 无法 disp 显示源/目标表名。
- **建议**: 补三个 to-one（`sourceTable`、`targetTable` → `NopMetaTable`；`pipeline` → `NopMetaPipeline`），并在 `NopMetaTable` 上补 `lineageAsSource`、`lineageAsTarget` 反向 to-many。
- **信心水平**: 确定
- **复核状态**: 已保留

### [维度04-03] 多个外键列缺失索引

- **文件**: `nop-metadata/model/nop-metadata.orm.xml`，多处
- **证据片段**:
  ```xml
  <!-- NopMetaTableJoin：4 个 FK 列，只在 metaTableId 上有索引 -->
  <indexes>
      <index name="IX_NOP_META_JOIN_TABLE" unique="false"><column name="metaTableId"/></index>
      <!-- leftTableId / rightTableId / leftEntityId / rightEntityId 均未建索引 -->
  </indexes>

  <!-- NopMetaLineageEdge: pipelineId 没有索引 -->
  <indexes>
      <index name="IX_NOP_META_LINEAGE_SOURCE" ...><column name="sourceTableId"/></index>
      <index name="IX_NOP_META_LINEAGE_TARGET" ...><column name="targetTableId"/></index>
      <!-- pipelineId 没有索引 -->
  </indexes>

  <!-- NopMetaModule: baseModuleId 自引用 FK，无索引 -->
  <!-- NopMetaReconciliationConfig: metaModuleId 没有索引 -->
  <!-- NopMetaReconciliationResult: metaTableId 没有索引 -->
  ```
- **严重程度**: P1
- **现状**: 至少 7 个 FK 列没有索引：`NopMetaModule.baseModuleId`、`NopMetaTableJoin.leftTableId/rightTableId/leftEntityId/rightEntityId`、`NopMetaLineageEdge.pipelineId`、`NopMetaReconciliationConfig.metaModuleId`、`NopMetaReconciliationResult.metaTableId`。
- **风险**: `SKILL.md §7.1` 明文规定"外键列必须建索引"。大型数据集下"按子查父"会全表扫描；外键约束触发锁表。
- **建议**: 为上述每个 FK 列补单列普通索引 `IX_NOP_META_*_<col>`。
- **信心水平**: 确定
- **复核状态**: 已保留

### [维度04-04] `mediumtext` domain precision=16777216 实际映射为 LONGTEXT 而非 MEDIUMTEXT

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:149`
- **证据片段**:
  ```xml
  <domain name="mediumtext" precision="16777216" stdSqlType="VARCHAR"/>
  ```
  对比 `nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/mysql.dialect.xml:160`:
  ```xml
  <sqlDataType name="MEDIUMTEXT" precision="16777215" allowPrecision="false" stdSqlType="VARCHAR"/>
  <sqlDataType name="LONGTEXT" stdSqlType="CLOB"/>
  ```
- **严重程度**: P2
- **现状**: MySQL MEDIUMTEXT 最大字节长度为 2^24 − 1 = 16,777,215。`mediumtext` domain 使用 16,777,216（恰好 +1），按 `SqlDataTypeMapping.stdToNativeSqlType` 的最小精度匹配算法，会跳过 MEDIUMTEXT 落到下一个候选 `LONGTEXT`（即 CLOB）。
- **风险**: 域名"mediumtext"误导，实际生成的 DDL 是 LONGTEXT。涉及 14 个字段（`NopMetaOrmModel.sourceContent`、`NopMetaTable.sourceSql/buildSql`、`NopMetaPipeline.sourceSql` 等）。
- **建议**: 二选一：(a) 真要 MEDIUMTEXT：precision 改 `16777215`；(b) 有意用 LONGTEXT：domain 名改为 `longtext`。
- **信心水平**: 确定
- **复核状态**: 已保留

### [维度04-05] `IX_NOP_META_TABLE_DEDUP` 索引名为"去重"但声明 `unique="false"`

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:1062-1066`
- **证据片段**:
  ```xml
  <indexes>
      <index name="IX_NOP_META_TABLE_MODULE" unique="false"><column name="metaModuleId"/></index>
      <index name="IX_NOP_META_TABLE_DEDUP" unique="false">
          <column name="metaModuleId"/>
          <column name="schema"/>
          <column name="tableName"/>
      </index>
  </indexes>
  ```
- **严重程度**: P2
- **现状**: 索引名为 `DEDUP`（去重）暗示用作唯一性约束，但 `unique="false"` 是普通索引。同一模块版本下逻辑表名仍可重复。
- **风险**: "DEDUP"语义与实际约束不匹配。
- **建议**: 改为 `<unique-key name="UK_NOP_META_TABLE_DEDUP" columns="metaModuleId,schema,tableName"/>`，或重命名为 `IX_NOP_META_TABLE_LOOKUP` 避免误导。
- **信心水平**: 很可能
- **复核状态**: 已保留

### [维度04-06] 多个实体缺少自然键唯一约束

- **文件**: `nop-metadata/model/nop-metadata.orm.xml`，多个实体
- **证据片段**:
  ```xml
  <!-- NopMetaEntity: 同一 ormModelId 下 entityName 应唯一，但没有 UK -->
  <!-- NopMetaDictItem: 同一 metaDictId 下 itemValue 应唯一 -->
  <!-- NopMetaOrmModel: 同一 metaModuleId 下 modelName 应唯一 -->
  <!-- NopMetaDomain, NopMetaDict, NopMetaTableDimension, NopMetaTableMeasure, NopMetaPipeline, ... -->
  ```
- **严重程度**: P2
- **现状**: 整模块只有 3 个 `<unique-key>`（NopMetaModule、NopMetaDataSource、NopMetaSemanticType），其余 29 个实体均无自然键唯一约束。
- **风险**: 应用层容易插入重名数据；导入/重算时无法 upsert。
- **建议**: 在 `model/*.orm.xml` 各实体 `<unique-keys>` 中补自然键 UK。
- **信心水平**: 很可能
- **复核状态**: 已保留

### [维度04-07] Dict 选项普遍缺失 `i18n-en:label` 翻译

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:9-135`
- **证据片段**:
  ```xml
  <dict label="模块状态" name="meta/module-status" ...>
      <option code="DRAFTING" label="编辑中" value="DRAFTING"/>   <!-- 缺 i18n-en:label -->
      <option code="RELEASED" label="已发布" value="RELEASED"/>   <!-- 缺 -->
      <option code="DEPRECATED" label="已废弃" value="DEPRECATED"/><!-- 缺 -->
  </dict>
  ```
- **严重程度**: P2
- **现状**: 23 个 dict 共约 80 个 option，仅 `meta/join-side` 的 2 项和 `meta/checkpoint-action-type` 的 2 项有 `i18n-en:label`。其余全部缺失。
- **风险**: 英文界面前端下拉框只能回退到中文 label 或 value。
- **建议**: 给全部 dict option 补 `i18n-en:label`，重新构建以刷新 `_nop-metadata.i18n.yaml`。
- **信心水平**: 确定
- **复核状态**: 已保留

### [维度04-08] `delFlag` domain 声明但全模块从未使用；实体均未启用逻辑删除

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:148`
- **证据片段**:
  ```xml
  <domain name="delFlag" stdDomain="boolFlag" stdSqlType="TINYINT"/>   <!-- 全模块无任何 column 引用 -->
  ```
- **严重程度**: P3
- **现状**: `delFlag` domain 定义存在，但 32 个实体没有任何 `delFlag` 列、也没有 `useLogicalDelete="true"` 或 `deleteFlagProp="..."` 声明。所有删除都是物理删除。
- **风险**: 元数据是高价值审计数据（变更事件、血缘、质量结果、对账结果等），物理删除会丢失追溯能力。
- **建议**: 至少为审计/时序类实体（NopMetaModelChangedEvent、NopMetaQualityResult、NopMetaProfilingResult、NopMetaCatalog、NopMetaReconciliationResult、NopMetaManifest）增加 `delFlag` 列并启用 `useLogicalDelete="true"`；或从 domains 中移除未使用的 `delFlag` domain。
- **信心水平**: 确定
- **复核状态**: 已保留

### [维度04-09] `version` 字段 DB 列名误用 `DEL_VERSION`

- **文件**: `nop-metadata/model/nop-metadata.orm.xml`，全部 32 个实体的 audit version 列
- **证据片段**:
  ```xml
  <column code="DEL_VERSION" displayName="数据版本" domain="version" mandatory="true" name="version"
          propId="16" stdDataType="long" stdSqlType="BIGINT" i18n-en:displayName="Version"/>
  ```
  对比 `nop-auth/model/nop-auth.orm.xml`:
  ```xml
  <column code="VERSION" displayName="数据版本" domain="version" mandatory="true" name="version" .../>
  ```
- **严重程度**: P3
- **现状**: Java 属性名为 `version`（标准乐观锁字段），但 DB 列名为 `DEL_VERSION`。整个模块 32 个实体一致使用此命名，共 32 处。
- **风险**: SKILL.md §4.3 中 `del_version` 是软删除版本字段，语义与"乐观锁版本"完全不同。DBA/跨模块开发者读到 `DEL_VERSION` 列会误解为软删除相关字段，与其他模块（nop-auth/nop-job 等用 `VERSION`）的列名约定不一致。
- **建议**: 全局把 `code="DEL_VERSION"` 改为 `code="VERSION"`（需重跑 codegen 并做数据迁移）。
- **信心水平**: 确定
- **复核状态**: 已保留

### [维度04-10] `NopMetaDictItem` 缺失 `isDelta` 列，与同簇兄弟实体不一致

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:935-993`
- **证据片段**: NopMetaDict 父表有 isDelta，NopMetaDictItem 子表无 isDelta。
- **严重程度**: P3
- **现状**: 元数据采用 full/delta 双轨制，dict 父表能区分是否 delta，dict item 却不能。
- **建议**: 给 NopMetaDictItem 补 `isDelta` 列（propId 顺延），保持与同簇实体一致。
- **信心水平**: 很可能
- **复核状态**: 已保留

### [维度04-11] `NopMetaEntity` 内 `deleteVersionProp` 与 `delFlagProp` 命名前缀不一致

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:444-447`
- **证据片段**:
  ```xml
  <column code="DEL_FLAG_PROP" displayName="删除标记属性" name="delFlagProp" .../>
  <column code="DEL_VERSION_PROP" displayName="删除版本属性" name="deleteVersionProp" .../>
  ```
- **严重程度**: P3
- **现状**: 同实体的两个描述被解析 ORM 元数据的字段，一个用 `del` 前缀，另一个用 `delete` 前缀。
- **建议**: 把 `name="deleteVersionProp"` 改为 `name="delVersionProp"`。
- **信心水平**: 很可能
- **复核状态**: 已保留

---

## 维度 05：生成管线完整性

### 整体结论：clean（无违规发现）

#### 已验证的合规项

- `nop-metadata-codegen/postcompile/gen-orm.xgen` 两步管线符合 `model-first-development.md:170-187`：第 1 步从 `model/nop-metadata.orm.xml` 用 `/nop/templates/orm` 模板生成 `_app.orm.xml`，第 2 步从 `app.orm.xml`（`x:extends="_app.orm.xml"`）用 `/nop/templates/orm-entity` 生成 `_gen/_*.java`。
- `gen-meta.xgen` / `gen-i18n.xgen` / `gen-crud-api.xgen`（已禁用，符合默认）/ `gen-page.xgen` / `precompile2/gen-i18n.xgen` 路径与产物均对齐。
- 32 套 xmeta、32 套 xbiz、32 套页面、32 套 `_gen/_*.java` 实体，全部生成产物存在并与源 32 实体一一对应。

### [维度05-01] `_templates/_*.json` (32 个) 未被本模块任何 .xgen / .java 引用（信息项）

- **文件**: `nop-metadata/nop-metadata-meta/_templates/_Nop*.json` (32 files)
- **证据片段**: `grep -rn "_templates" nop-metadata/` 返回 0 命中（仅 `find` 命中文件本身）。
- **严重程度**: P3（信息性）
- **现状**: 32 个 `_Nop*.json` 文件位于 `_templates/`，但本模块的 `gen-meta.xgen` 只调用 `/nop/templates/meta` 平台模板（不是 `_templates/` 本地目录），且没有任何脚本读取这些 JSON。
- **风险**: 既不是源也不是生成物，维护成本（每次加字段需要同步更新 JSON）但收益不明。
- **建议**: 确认这些 JSON 的实际消费方（可能是 `nop-cli gen` 首次生成骨架时使用）。若属于约定俗成的"被外部工具消费"，则不算 bug。
- **信心水平**: 有趣的猜测
- **复核状态**: 已保留

---

## 维度 06：Delta 定制合规性

### 整体结论：clean — 无 Delta 文件，符合 in-source 模块约定

`find nop-metadata -path "*/_vfs/_delta/*" -type f` 返回 0 个结果。nop-metadata 是 in-source 模块（`model/nop-metadata.orm.xml` 在自己模块里），按 `delta-customization.md` 的判断："如果是在自己的模块里扩展生成保留层，优先非下划线文件"——本模块就是 owner，所以无需 Delta。已验证非下划线扩展文件（`app.orm.xml`、`Nop*.xmeta`、`Nop*.xbiz`、`Nop*.view.xml`）均符合"手写留存层"约定。

---

## 维度 08：IoC 与 Bean 配置

### 整体结论：clean（无违规发现）

#### 已验证的合规项

- **4 个 `_module` 文件均为 0 字节**：符合 `ioc-and-config.md:130` "启用模块通过 `/{moduleId}/_module` 被发现（零字节标记文件）"。
- **`app-service.beans.xml` 内容正确**：import `_dao.beans.xml` + `_service.beans.xml`；5 个手写 service bean，bean id 使用全限定类名（4 个）和 `metaQualityCheckpointScheduler`（非 `nop*` 前缀，符合 `ioc-and-config.md:134`）。
- **`_service.beans.xml` 为标准 codegen 产物**：32 对 `<bean>` + `<biz:biz_NopMetaXxx proxy>` 定义，无手改痕迹。
- **`@Inject` 字段可见性全部合规**：`grep -rn "@Inject" nop-metadata --include="*.java" | grep "private "` 返回 0 命中。所有 `@Inject` 字段均为 `protected` 或经构造器/setter 注入。
- **无 Spring 专有注解**：`grep -rn "@Autowired|@Value\b|@Component|@Aspect|@Around|org.springframework"` 在 main 代码中返回 0 命中。
- **`@InjectValue` 用法正确**（仅 1 处）：`@InjectValue("@cfg:nop.metadata.platform-version|2.0.0-SNAPSHOT")`。

---

## 维度 09 + 19：错误处理与命名一致性（合并）

### [维度09-01 / 19-02] 错误码命名不符规范（缺 `nop.err.` 前缀）

- **文件**: 全部 `nop-metadata-service` 下含 `ErrorCode.define(...)` 的 20+ 文件
- **证据片段** (代表性):
  ```java
  // NopMetaDataSourceBizModel.java:58-60
  static final ErrorCode ERR_DATASOURCE_NOT_FOUND =
          ErrorCode.define("metadata.datasource-not-found",
                  "DataSource not found: {dataSourceId}", "dataSourceId");

  // NopMetaModuleBizModel.java:82-85
  static final ErrorCode ERR_RESOURCE_NOT_FOUND =
          ErrorCode.define("metadata.orm-resource-not-found", "ORM资源不存在", "path");

  // MetaAggregationExecutor.java:90-213
  ErrorCode.define("metadata.aggr-no-measure", ...);
  ```
  对照 `nop-auth/nop-auth-service/.../NopAuthErrors.java`:
  ```java
  ErrorCode ERR_AUTH_LOGIN_CHECK_FAIL = define("nop.err.auth.login-check-fail", ...);
  ```
- **严重程度**: P2
- **现状**: 全部 178 处 `ErrorCode.define()` 调用使用 `metadata.<子域>.<错误>` 命名，不符合 `error-handling.md:99` 规范 `nop.err.[模块].[子域].[错误]`。
- **风险**: 偏离仓库其它模块（nop-auth、nop-job、nop-batch、nop-task）的统一前缀；i18n 字典按 `nop.err.*` 前缀加载时找不到 nop-metadata 的错误码；前端按错误码匹配/i18n 查找会失败。
- **建议**: 全量改名为 `nop.err.metadata.*`（保留子域结构）；改名可与 09-02 的集中化合并执行。
- **信心水平**: 确定
- **复核状态**: 已保留

### [维度09-02] ErrorCodes 散布 20+ 文件；`NopMetadataErrors.java` 为空壳

- **文件**:
  - `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/NopMetadataErrors.java:1-5` (空接口)
  - `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/NopMetadataConstants.java:1-5` (空接口)
  - `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/NopMetadataConfigs.java:1-5` (空接口)
- **证据片段**:
  ```java
  // NopMetadataErrors.java (全文 5 行)
  package io.nop.metadata.service;
  public interface NopMetadataErrors{
  }
  ```
- **严重程度**: P2
- **现状**: 错误码本应集中定义在 `NopMetadataErrors.java`（参考 `nop-auth` 的集中模式 + `ARG_*` 常量），但本模块该接口为空。错误码散落在每个 BizModel/Executor/Helper 顶部。`NopMetadataConstants.java`/`NopMetadataConfigs.java` 同样为空。
- **风险**: (1) 同一错误码可能被多处重复定义（见 09-03）；(2) 跨文件复用错误码需要先 grep；(3) 违反 `error-handling.md:69-77` 集中定义 + `ARG_*` 参数常量约定。
- **建议**: 把所有 ErrorCode 集中到 `NopMetadataErrors.java`，按子域分组，引入 `ARG_*` 常量。
- **信心水平**: 确定
- **复核状态**: 已保留

### [维度09-03] 同一 ErrorCode 字符串在多文件重复 define

- **文件**:
  - `nop-metadata-service/.../entity/NopMetaDataSourceBizModel.java:58-60`
  - `nop-metadata-service/.../entity/NopMetaQualityRuleBizModel.java:90-92`
- **证据片段**:
  ```java
  // NopMetaDataSourceBizModel.java:58-60
  static final ErrorCode ERR_DATASOURCE_NOT_FOUND =
          ErrorCode.define("metadata.datasource-not-found",
                  "DataSource not found: {dataSourceId}", "dataSourceId");

  // NopMetaQualityRuleBizModel.java:90-92
  static final ErrorCode ERR_DATASOURCE_NOT_FOUND =
          ErrorCode.define("metadata.datasource-not-found",
                  "DataSource not found: {dataSourceId}", "dataSourceId");
  ```
- **严重程度**: P2
- **现状**: `"metadata.datasource-not-found"` 字符串在 2 个文件被独立 `ErrorCode.define()` 两次。两处常量虽然字符串相同（运行时 i18n 仍能工作），但是两份独立的 ErrorCode 对象，身份比较 `==` 会失败。
- **建议**: 集中到 `NopMetadataErrors.ERR_DATASOURCE_NOT_FOUND`，两处引用同一常量。
- **信心水平**: 确定
- **复核状态**: 已保留

### [维度09-04] 在 throw 处 inline `ErrorCode.define(...)`（每次抛出构造新实例）

- **文件**:
  - `nop-metadata-service/.../reconciliation/ReconciliationExecutor.java:101-103`
  - `nop-metadata-service/.../quality/MetaQualityRuleExecutor.java:457-461, 463-475, 477-489`
  - `nop-metadata-service/.../quality/CheckpointActionDispatcher.java:156-162, 164-174`
- **证据片段**:
  ```java
  // ReconciliationExecutor.java:101-103
  default:
      throw new NopException(ErrorCode.define("metadata.recon-unknown-status",
              "Reconciliation produced unknown status: {status}", "status"))
              .param("status", status);
  ```
- **严重程度**: P2
- **现状**: 在 throw 表达式内调用 `ErrorCode.define(...)`，每次抛异常都构造一个新的 ErrorCode 对象。
- **建议**: 把这些 inline define 提到文件顶部的 `static final ErrorCode ERR_* =` 区域，或集中到 `NopMetadataErrors`。
- **信心水平**: 确定
- **复核状态**: 已保留

### [维度09-05] 无模块级异常类 `NopMetadataException`

- **文件**: 全模块
- **严重程度**: P3
- **现状**: 模块未定义 `NopMetadataException extends NopException`。所有抛出均用裸 `new NopException(errorCode)`。
- **风险**: `error-handling.md:113-167` 推荐模块内部实现使用模块异常类。当前模块有大量内部 Executor/Helper（MetaAggregationExecutor、MetaJoinExecutor、ReconciliationExecutor、MetaQualityRuleExecutor、MetaTableProfiler 等），缺乏统一模块异常类型。
- **建议**: 新增 `io.nop.metadata.service.NopMetadataException extends NopException`，提供 `(String)`/`(String, Throwable)`/`(ErrorCode)`/`(ErrorCode, Throwable)` 四构造器。
- **信心水平**: 很可能
- **复核状态**: 已保留

### [维度09-06] `throw new IllegalArgumentException`（非 NopException）

- **文件**: `nop-metadata-service/.../manifest/MetaManifestBuilder.java:59-62`
- **证据片段**:
  ```java
  public ManifestBuildResult build(NopMetaModule module, NopMetaOrmModel fullOrmModel, ...) {
      if (module == null)
          throw new IllegalArgumentException("module must not be null");
      if (fullOrmModel == null)
          throw new IllegalArgumentException("fullOrmModel must not be null (module has no full ORM model)");
  }
  ```
- **严重程度**: P3
- **现状**: 在内部 builder 抛 `IllegalArgumentException`（继承自 `RuntimeException`），绕过框架异常体系。
- **建议**: 改为 `throw new NopException(NopMetadataErrors.ERR_MANIFEST_MODULE_NULL).param(...)` 或 `throw new NopMetadataException("module must not be null")`（配合 09-05）。
- **信心水平**: 确定
- **复核状态**: 已保留

### [维度09-07] `throw new UnsupportedOperationException` 用于不支持的数据源类型

- **文件**:
  - `nop-metadata-service/.../connection/MetaDataSourceConnectionService.java:114-118`
  - `nop-metadata-service/.../sync/ExternalTableStructureReader.java:127-135`
- **证据片段**:
  ```java
  // MetaDataSourceConnectionService.java:114-118
  private void requireJdbcType(String datasourceType) {
      if (!_NopMetadataCoreConstants.DATASOURCE_TYPE_JDBC.equals(datasourceType)) {
          throw new UnsupportedOperationException(
                  "Connection building not yet implemented for datasourceType: " + datasourceType);
      }
  }
  ```
- **严重程度**: P3
- **现状**: 这两处异常虽然 javadoc 大量提到（多个 BizModel 注释说"非 jdbc 类型 → 由 withConnection 抛 UnsupportedOperationException"），但本质上仍是 `RuntimeException` 子类，绕过 ErrorCode 模式。`MetaDataSourceConnectionService` 顶部已定义 `ERR_DATASOURCE_TYPE_NOT_SUPPORTED`，但 `requireJdbcType` 没用它。
- **建议**: 改为 `throw new NopException(ERR_DATASOURCE_TYPE_NOT_SUPPORTED).param("datasourceType", datasourceType)`。
- **信心水平**: 确定
- **复核状态**: 已保留

### [维度09-08] 用 `throw new SQLException` 表达业务控制流

- **文件**:
  - `nop-metadata-service/.../quality/MetaQualityRuleExecutor.java:336, 379`
  - `nop-metadata-service/.../catalog/MetaCatalogCollector.java:104`
  - `nop-metadata-service/.../profiling/MetaTableProfiler.java:417`
- **证据片段**:
  ```java
  // MetaCatalogCollector.java:104
  throw new SQLException("COUNT(*) returned no row for: " + fromClause);
  // MetaTableProfiler.java:417
  throw new SQLException("aggregate returned no row: " + sql);
  ```
- **严重程度**: P2
- **现状**: 用 `SQLException`（应当用于真实 JDBC 层故障）表达 "SQL COUNT(*) 不返回行" 这种本质上不可能发生（COUNT 永远返回 1 行）的逻辑断言。
- **风险**: 误导调用方以为是连接/语法问题；绕过 ErrorCode 模式；上游 `catch (SQLException e)` 可能误捕获并将其作为可恢复 SQL 错误处理。
- **建议**: 改为 `throw new NopException(ERR_*_AGGREGATE_NO_ROW).param("sql", sql)` 或 `NopMetadataException("...")`。
- **信心水平**: 确定
- **复核状态**: 已保留

### [维度09-09] 静默吞异常（既不 rethrow 也不 LOG）

- **文件**: `nop-metadata-service/.../reconciliation/LocalReconciliationService.java:171-179`
- **证据片段**:
  ```java
  @SuppressWarnings("unchecked")
  private static java.util.Map<String, Object> parseProperties(String propertiesJson) {
      if (propertiesJson == null || propertiesJson.trim().isEmpty()) {
          return Collections.emptyMap();
      }
      try {
          Object parsed = JsonTool.parse(propertiesJson);
          if (parsed instanceof java.util.Map) {
              return (java.util.Map<String, Object>) parsed;
          }
      } catch (Exception ignored) {
          // properties JSON 解析失败不阻断匹配，记空（不伪造）
      }
      return Collections.emptyMap();
  }
  ```
- **严重程度**: P2
- **现状**: `catch (Exception ignored)` 既不 rethrow with cause 也不 `LOG.warn`，违反 `error-handling.md:36` "丢弃异常前必须留证"。
- **建议**: 至少加 `LOG.warn("parseProperties failed: {}", e.getMessage(), e)`。
- **信心水平**: 确定
- **复核状态**: 已保留

### [维度09-10] 手写 `try { rs.close() } catch` 资源关闭（应使用 `IoHelper`）

- **文件**: `nop-metadata-service/.../profiling/MetaTableProfiler.java:401-408`
- **证据片段**:
  ```java
  } finally {
      if (rs != null) {
          try {
              rs.close();
          } catch (SQLException ignored) {
              // ignored
          }
      }
  }
  ```
- **严重程度**: P2
- **现状**: 手写 `try { rs.close() } catch` 关闭 ResultSet，且 catch 体为空。`error-handling.md:25-27` 明确："资源关闭 try { x.close() } catch 禁止手写，必须用 `IoHelper.safeClose*`"。模块其它地方（`MetaDataSourceConnectionService.java:85-87`）已正确使用 `IoHelper.safeCloseObject(conn)`，本处是疏漏。
- **建议**: 改为 `IoHelper.safeCloseObject(rs);`，删除 finally 块。
- **信心水平**: 确定
- **复核状态**: 已保留

### [维度09-11] 错误参数语义错配（metaTableId 写入 dataSourceId 参数）

- **文件**: `nop-metadata-service/.../entity/NopMetaDataSourceBizModel.java:307-314`
- **证据片段**:
  ```java
  @BizMutation
  public Map<String, Object> collectCatalogForTable(@Name("metaTableId") String metaTableId, ...) {
      IEntityDao<NopMetaTable> tableDao = daoFor(NopMetaTable.class);
      NopMetaTable table = tableDao.getEntityById(metaTableId);
      if (table == null) {
          throw new NopException(ERR_DATASOURCE_NOT_FOUND).param("dataSourceId", metaTableId);
      }
  ```
- **严重程度**: P2
- **现状**: 当 `metaTableId` 找不到表时，抛 `ERR_DATASOURCE_NOT_FOUND`（描述 "DataSource not found: {dataSourceId}"），并把 `metaTableId` 作为 `dataSourceId` 参数传入。错误消息和参数语义完全不匹配——业务方看错误响应会去找数据源问题，但实际是表不存在。
- **建议**: 新增 `ERR_TABLE_NOT_FOUND` ErrorCode（描述用 `{metaTableId}`），本处改用之。
- **信心水平**: 确定
- **复核状态**: 已保留

### 阴性发现（错误处理）

- 所有 BizModel 公开方法失败路径均用 `throw new NopException(ErrorCode).param(...)` 模式 ✓
- `.cause(e)` 用于包装底层异常（如 `NopMetaTableBizModel.java:815`、`NopMetaModuleBizModel.java:553`、`MetaDataSourceConnectionService.java:170`），异常链保留 ✓
- 所有日志使用 SLF4J（19 处），无 `System.out/System.err`，无 `e.printStackTrace()` ✓
- ErrorCode.description 内允许中文（如 `"ORM资源不存在"`，框架走 i18n）；非 ErrorCode 路径的字符串均为英文 ✓

---

## 维度 10 + 11 + 12：XDSL/XMeta/GraphQL 层

### 整体结论：clean（合规项已验证，发现项已在维度 03/07 合并）

#### 已验证的合规项

- 32 个非下划线 xmeta 都 `x:schema="/nop/schema/xmeta.xdef"` + `x:extends="_NopMeta*.xmeta"`
- 32 个非下划线 xbiz 都 `x:schema="/nop/schema/biz/xbiz.xdef"` + `x:extends="_NopMeta*.xbiz"`
- `_service.beans.xml` 与 `app-service.beans.xml` 都 `x:schema="/nop/schema/beans.xdef"`
- 无 `_delta/` 文件（本模块不需要 delta 定制）
- xmeta props 与 ORM 列一一对应（codegen 生成）
- displayName 全部带 `i18n-en:displayName` 本地化
- `version` 字段统一标记 `internal="true" insertable="false" updatable="false"`
- 32 个 IBiz 接口的泛型参数 `<NopMetaXxx>` 全部正确
- 32 个 BizModel 全部 `extends CrudBizModel<NopMetaXxx> implements INopMetaXxxBiz`，泛型一致

### [维度12-01] `queryTableData` / `queryJoinData` / `queryAggregation` 未注入 `FieldSelectionBean`

- **文件**: `nop-metadata-service/.../entity/NopMetaTableBizModel.java:496-520`（queryTableData）、`541-553`（queryJoinData）、`578-595`（queryAggregation）
- **证据片段**:
  ```java
  @BizQuery
  public Map<String, Object> queryTableData(@Name("metaTableId") String metaTableId,
                                             @Optional @Name("filter") TreeBean filter,
                                             @Optional @Name("limit") Long limit,
                                             @Optional @Name("offset") Long offset,
                                             IServiceContext context) {
      ...
      return buildQueryResult(_NopMetadataCoreConstants.TABLE_TYPE_ENTITY, items);
  }
  ```
- **严重程度**: P3
- **现状**: 三个查询方法都返回 `Map<String, Object>`，不接收 `FieldSelectionBean`，因此前端 GraphQL selection 无法下推——服务端永远全字段返回。`api-and-graphql.md` 默认模板要求 `@BizQuery` 复杂查询带 `FieldSelectionBean selection`。
- **风险**: 大宽表场景下网络/序列化浪费；GraphQL selection 语义被绕过。
- **建议**: 加 `@Optional @Name("selection") FieldSelectionBean selection` 末参；或用 `@DataBean` 表达行类型 + 让 GraphQL 引擎自动 fetchSelections。
- **信心水平**: 很可能
- **复核状态**: 已保留

---

## 维度 13 + 14：安全/权限/事务（合并）

### [维度13-01] `NopMetaDataSource.connectionConfig` 明文存储数据库凭据且通过 GraphQL 暴露

- **文件**:
  - `nop-metadata-meta/src/main/resources/_vfs/nop/metadata/model/NopMetaDataSource/_NopMetaDataSource.xmeta:45-49`
  - `nop-metadata-dao/src/main/resources/_vfs/nop/metadata/orm/_app.orm.xml:253-255`
  - `nop-metadata-service/.../connection/MetaDataSourceConnectionService.java:42-44`
  - `nop-metadata-web/.../pages/NopMetaDataSource/_gen/_NopMetaDataSource.view.xml:37`
- **证据片段**:
  ```xml
  <!-- _NopMetaDataSource.xmeta line 45-49 -->
  <prop name="connectionConfig" displayName="连接配置" propId="6" i18n-en:displayName="Connection Config"
        queryable="true" sortable="true" insertable="true" updatable="true"
        graphql:jsonComponentProp="connectionConfigComponent">
      <schema stdDomain="json" domain="json-4000" type="java.lang.String" precision="4000"/>
  </prop>
  <!-- service/connection/MetaDataSourceConnectionService.java line 42-44 -->
  private static final String CFG_JDBC_URL = "jdbcUrl";
  private static final String CFG_USERNAME = "username";
  private static final String CFG_PASSWORD = "password";   // 明文存于 connectionConfig JSON
  <!-- _NopMetaDataSource.view.xml line 37 (生成物，列于列表页) -->
  <col id="connectionConfig" sortable="true"/>
  ```
- **严重程度**: P1
- **现状**: `connectionConfig` 字段以 JSON 字符串形式存储 `{jdbcUrl, username, password, driverClassName}`，password 为明文。xmeta 配置 `queryable="true" insertable="true" updatable="true"`，`published` 默认 `true`（见 `nop/schema/schema/obj-schema.xdef:88` `published="!boolean=true"`）。该 prop 未配置 `ui:maskPattern`、未配置 `published="false"`、未配置自定义 mapper 做脱敏，且生成的 view 把它作为列表列展示。任何具有 `NopMetaDataSource:query` 权限的用户调 `query { NopMetaDataSource__findPage { connectionConfig } }` 即可拿到所有数据源的明文密码。
- **风险**: 凭据泄露面过大；按 ORM 默认配置存明文，无加密无脱敏；`data-auth.xml` 为空（见 [维度13-02]），任何 query 权限即可读全部行；事件发布器 `MetaModelChangedEventPublisher.buildEntitySnapshot` 也会把所有列（含 connectionConfig）序列化进 `NopMetaModelChangedEvent.afterSnapshot`，凭据会随事件行二次落盘。
- **建议**: 三选一（推荐组合）：
  1. xmeta 上设 `published="false"` 或 `ui:show="!$action == 'add' || !$action == 'edit'"`，禁止列表/查询返回该字段。
  2. 用 `ui:maskPattern` 或自定义 `transformOut` 在 GraphQL 出口对 password 字段做掩码。
  3. 引入加密 column（在 ORM 层做 `stdDomain="encrypted"` 类似机制）落盘前加密、运行时按需解密；分离 password 到独立受限字段。
- **信心水平**: 确定
- **复核状态**: 已保留

### [维度13-02] `data-auth.xml` 为空，所有元数据实体无行级数据权限

- **文件**: `nop-metadata-service/src/main/resources/_vfs/nop/metadata/auth/nop-metadata.data-auth.xml:1-5`
- **证据片段**:
  ```xml
  <data-auth x:schema="/nop/schema/data-auth.xdef" xmlns:x="/nop/schema/xdsl.xdef">
      <objs/>
  </data-auth>
  ```
- **严重程度**: P2
- **现状**: service 层的 `nop-metadata.data-auth.xml` 是空 `<objs/>`，整个 nop-metadata 模块对 33 个实体均未配置任何行级数据权限规则。所有权限控制仅依赖 action-auth 的 FNPT（`NopMetaXxx:query` / `NopMetaXxx:mutation`），即"全表读 / 全表写"的二值粒度。
- **风险**: 涉及敏感数据的实体（NopMetaDataSource 凭据、NopMetaQualityCheckpoint 的 actions webhook URL、NopMetaModelChangedEvent 的快照内容、NopMetaReconciliationResult 的对账数据）一旦用户拿到 query 权限即可读全部行，无法按业务线/模块/owner 隔离；多租户场景完全失效。
- **建议**: 至少对 NopMetaDataSource / NopMetaQualityCheckpoint / NopMetaModelChangedEvent 等敏感实体补 data-auth 规则（按 createdBy/tenantId/查询空间等做行级过滤）；或将 `connectionConfig` 类敏感字段独立 restricted，配合 [维度13-01] 的字段级处理。
- **信心水平**: 很可能
- **复核状态**: 已保留

### [维度13-03 / 14-03] `MetaQualityRuleExecutor.custom_sql` 规则执行任意用户 SQL，无沙箱无审计

- **文件**: `nop-metadata-service/.../quality/MetaQualityRuleExecutor.java:199-227`
- **证据片段**:
  ```java
  private QualityRuleJudgment judgeCustomSql(Connection conn, String sqlExpression,
                                             Map<String, Object> params, QualityRuleJudgment j) {
      String sql = sqlExpression;
      if (sql == null || sql.isEmpty()) {
          sql = getString(params, "sql");
      }
      // custom_sql 为用户显式提供（已知显式风险，§2.7.1 D3），直接执行不解析不改写
      j.getDetails().put("sql", sql);

      Double value;
      try {
          value = querySingleValue(conn, sql);   // 直接 Statement.executeQuery(sql)
  ...
  private static Double querySingleValue(Connection conn, String sql) throws SQLException {
      LOG.info("qualityRule custom_sql: {}", sql);
      try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
  ```
- **严重程度**: P2
- **现状**: `NopMetaQualityRule.ruleType=custom_sql` 时，规则 `sqlExpression` 列或 `params.sql` 的内容被原样 `Statement.executeQuery(sql)` 执行在外部数据源连接上。注释明确"已知显式风险"，调用 `NopMetaQualityRule:mutation` 权限可写规则即可触发任意 SQL。`querySingleValue` 用 `Statement` 而非 `PreparedStatement`。
- **风险**: (a) 任何具有 `NopMetaQualityRule:mutation` 权限的用户可在目标数据源上执行任意 SQL（潜在数据破坏 / 数据外泄）；(b) 规则 sql 文本里若引入外部输入可拼接；(c) 外部数据源连接使用的 jdbc 账户权限可能远大于"质量检查"所需。
- **建议**: (a) 至少改为 `PreparedStatement` 并禁止 sql 内含分号（多语句）；(b) 限制外部数据源连接账户为只读；(c) 给 custom_sql 增加白名单（只允许 SELECT）或显式 audit log；(d) 在 action-auth 中把 NopMetaQualityRule:mutation 独立成受保护资源（admin-only）。
- **信心水平**: 很可能
- **复核状态**: 已保留

### [维度13-04 / 14-04] `CheckpointActionDispatcher` webhook URL/HTTP 调用无超时配置、无 SSRF 防护

- **文件**: `nop-metadata-service/.../quality/CheckpointActionDispatcher.java:131-171`
- **证据片段**:
  ```java
  private void dispatchWebhook(NopMetaQualityCheckpoint cp, Map<String, Object> summary,
                                Map<String, Object> config) {
      ...
      Object urlObj = config == null ? null : config.get("url");
      String url = urlObj == null ? null : String.valueOf(urlObj).trim();
      if (url == null || url.isEmpty()) {
          throw new NopException(ERR_CHECKPOINT_WEBHOOK_NO_URL)...;
      }
      String method = ...;
      HttpRequest request = new HttpRequest();
      request.setUrl(url);   // 用户配置的 URL，无白名单
      request.setMethod(method);
      request.setHeader("Content-Type", "application/json");
      request.setBody(JsonTool.stringify(summary));

      IHttpResponse response = httpClient.fetch(request, null);   // 无显式超时参数
  ```
- **严重程度**: P2
- **现状**: `NopMetaQualityCheckpoint.actions` JSON 配置中 webhook action 可指定任意 URL，由 `IHttpClient.fetch(request, null)` 发起请求。无 URL 白名单（内网/元数据接口可被 SSRF）、无方法白名单（method 来自 config，未校验）、无超时参数。
- **风险**: (a) SSRF：具有 `NopMetaQualityCheckpoint:mutation` 权限的用户可让服务器向内网/云元数据接口（169.254.169.254 等）发请求；(b) 长时间 webhook 拖慢检查点执行；(c) HTTP method 不限制，可发起 DELETE/PUT 等。
- **建议**: (a) URL 白名单（http/https + 域名 allowlist）；(b) method 限制为 POST/PUT；(c) 显式传入超时（如 30s）；(d) 把 webhook 配置审计入 `NopMetaModelChangedEvent`。
- **信心水平**: 很可能
- **复核状态**: 已保留

### [维度14-01] `@BizMutation` 内部混合外部 JDBC 与 ORM 操作，长事务 + 外部连接持有时间过长

- **文件**: `nop-metadata-service/.../entity/NopMetaDataSourceBizModel.java:144-202`（syncExternalTables）、`227-282`（collectCatalog）
- **证据片段**:
  ```java
  // syncExternalTables：@BizMutation 默认事务
  @BizMutation
  public Map<String, Object> syncExternalTables(...) {
      ...
      connectionService.withConnection(dataSource.getDatasourceType(), dataSource.getConnectionConfig(),
              (Connection conn, DatabaseMetaData metaData) -> {
                  List<ExternalTableInfo> tables = structureReader.read(conn, metaData, schemaPattern);
                  for (ExternalTableInfo table : tables) {
                      try {
                          upsertExternalTable(externalModuleId, dataSource, table);   // ORM 写
                          orm().flushSession();
                          syncedCount.incrementAndGet();
                      } catch (Exception e) {
                          ...
                          orm().clearSession();
                      }
                  }
              });
      // 外部连接退出 callback 后才关闭；ORM 事务依然在 @BizMutation 包装内
  }
  ```
- **严重程度**: P2
- **现状**: `@BizMutation` 默认事务，整个方法运行在平台事务内。`withConnection` 打开外部 JDBC 连接后，对每条表循环执行 ORM 写 + flushSession。外部连接 + 平台事务连接同时长时间被占用。
- **风险**: (a) 外部数据源连接池被占用过久；(b) 平台 ORM 事务连接被占用过久；(c) 外部 DB 慢或挂时平台事务一直挂起；(d) `flushSession` 不提交，仅在 `@BizMutation` 退出时提交，若期间任何异常导致事务回滚，"syncedTableCount"返回值与实际持久化数据不一致。
- **建议**: (a) `syncExternalTables`：先在 `withConnection` 内只读取出 `tables`，关闭外部连接后再做 ORM upsert 循环。(b) `collectCatalog`：每表独立 `withConnection`（短连接），或把外部 SQL 收集放进无事务的子调用（`txnTemplate.runWithoutTransaction`）。(c) 批量场景考虑分批提交。
- **信心水平**: 很可能
- **复核状态**: 已保留

### [维度14-02] `NopMetaModuleBizModel.save`/`delete` override 在事务内同步发布事件，未使用 `txn().afterCommit`

- **文件**: `nop-metadata-service/.../entity/NopMetaModuleBizModel.java:120-137`（save）、`145-159`（delete）；同样模式见 `NopMetaTableBizModel:219-235`、`242-256`
- **证据片段**:
  ```java
  @Override
  public NopMetaModule save(@Name("data") Map<String, Object> data, IServiceContext context) {
      String id = ...;
      NopMetaModule before = id != null ? dao().getEntityById(id) : null;
      NopMetaModule saved = super.save(data, context);
      ...
      eventPublisher.publishEventWithSnapshots(eventType, ..., context);  // 同事务内写 NopMetaModelChangedEvent
      return saved;
  }
  ```
- **严重程度**: P3
- **现状**: save/delete override 在 `super.save/super.delete` 后直接调用 `eventPublisher.publishEventWithSnapshots`，事件行与业务行在同事务内：业务回滚→事件也回滚（无幽灵事件，good）；但下游订阅者（未来）若用 `txn().afterCommit` 期望"已提交才通知"，本模块没有该机制。整模块对 `txn().afterCommit` 0 使用。
- **建议**: 注释明确"事件随事务提交/回滚统一"；若计划未来接消息系统，把出队部分挪到 `txn().afterCommit(...)`。
- **信心水平**: 很可能
- **复核状态**: 已保留

### 阴性发现（事务）

- 33 个实体均配 `versionProp="version"`，乐观锁机制生效 ✓
- `MetaQualityCheckpointScheduler` 与 nop-job 集成正确：`@Inject @Nullable IJobScheduler`（宿主未注册时跳过）、`@PostConstruct init()` 全量扫描、save/delete override 经 `BeanContainer.tryGetBean` 做运行时增量 ✓
- `CheckpointActionDispatcher.dispatch` 经 `txnTemplate.runWithoutTransaction` 在事务外投递，per-action try/catch 隔离 ✓
- 外部连接通过 `IoHelper.safeCloseObject(conn)` 在 finally 关闭 ✓

---

## 维度 15：类型安全与泛型使用（其余条目已在维度 03 合并）

### [维度15-02] `NopMetaTableBizModel.queryEntityTable` 使用 raw type + unchecked cast 绕过泛型

- **文件**: `nop-metadata-service/.../entity/NopMetaTableBizModel.java:629-631`
- **证据片段**:
  ```java
  @SuppressWarnings({"rawtypes", "unchecked"})
  io.nop.orm.dao.IOrmEntityDao<io.nop.orm.IOrmEntity> targetDao =
          (io.nop.orm.dao.IOrmEntityDao<io.nop.orm.IOrmEntity>) (io.nop.orm.dao.IOrmEntityDao) daoProvider().dao(entityName);
  ```
- **严重程度**: P3
- **现状**: 由于实体名运行时才知，必须 raw type + 反射式拿到 DAO。
- **建议**: 此模式为运行时动态实体的边界场景，cast 不可避免。建议补充单元测试覆盖"运行时实体名→DAO→列名集合"链路。
- **信心水平**: 确定
- **复核状态**: 已保留

### [维度15-03] 大量 JSON 解析后 `@SuppressWarnings("unchecked")` cast `Map<String,Object>`

- **文件**: 多处，例如 `quality/MetaQualityCheckpointScheduler.java:238-256`、`quality/CheckpointActionDispatcher.java:74-90, 205-213`、`entity/NopMetaQualityCheckpointBizModel.java:212, 263`、`event/MetaModelChangedEventPublisher.java:146`、`connection/MetaDataSourceConnectionService.java:121`
- **证据片段**:
  ```java
  // MetaQualityCheckpointScheduler.readScheduleCron
  @SuppressWarnings("unchecked")
  private static String readScheduleCron(NopMetaQualityCheckpoint cp) {
      String json = cp.getExtConfig();
      ...
      Object parsed = JsonTool.parse(json);
      if (!(parsed instanceof Map)) {
          return null;
      }
      Object val = ((Map<String, Object>) parsed).get(EXT_CONFIG_SCHEDULE_KEY);
  ```
- **严重程度**: P3
- **现状**: extConfig/actions/validations 等 JSON 字段以 `String` 存储，每次消费都要 `JsonTool.parse(...)` 后 cast 为 `Map<String,Object>`。代码中先 `instanceof Map` 校验再 cast，模式正确，但 `@SuppressWarnings("unchecked")` 全文 50+ 处。
- **建议**: 引入 `@DataBean` 表达 extConfig/actions/validations 结构（如 `CheckpointExtConfig { String schedule; boolean autoScore; }`），让 JsonOrmComponent 直接反序列化为强类型 bean，消除大部分 cast。
- **信心水平**: 很可能
- **复核状态**: 已保留

---

## 维度 16 + 21：测试覆盖与有效性（正向）

### 整体结论：clean — 测试质量为仓库内最高水平之一

#### 测试映射表（关键引擎类全部覆盖）

| 引擎类 | 测试文件 | 行数 |
|--------|---------|------|
| `MetaAggregationExecutor` (3474) | `TestNopMetaAggregationBizModel` + `TestHavingArithmeticPreprocess` + `TestExpressionMeasureValidator` + `TestMemoryFilterAndOrderBy` | 2591+ |
| `MetaJoinExecutor` (926) | `TestNopMetaJoinBizModel` + `TestNopMetaAggregationBizModel` | — |
| `FilterToSqlTranslator` | `TestFilterToSqlTranslator` | 172 |
| `SqlColumnLineageExtractor` | `TestSqlColumnLineageExtractor` | 335 |
| `ExternalTableStructureReader` | `TestExternalTableStructureReader` | 57 |
| `MetaQualityRuleExecutor` | `TestNopMetaQualityRuleBizModel` + `TestSqlTableExecution` + `TestEntityTableExecution` | 601+ |
| `ReconciliationExecutor` | `TestNopMetaReconciliationBizModel` | 473 |
| `MetaQualityCheckpointExecutor` | `TestNopMetaQualityCheckpointBizModel` | 779 |
| `MetaQualityCheckpointScheduler` | `TestMetaQualityCheckpointScheduler` | 331 |
| `CheckpointActionDispatcher` | `TestCheckpointActionDispatcher` | 124 |

#### Dimension 21 — 单元测试反模式核查（依据 `ai-dev/skills/unit-test-antipatterns.md` P-1..P-8）

- **P-1 纯 getter/setter 往返测试**：未发现。
- **P-2 测试元数据属性而非行为**：未发现。
- **P-3 只测 Happy Path**：未发现。每个测试文件都包含错误路径测试（`testNoRulesFails`、`testRecordLineageSourceNotFound`、`testInvalidIdentifierRejected` 等）。
- **P-4 测试与实现高度耦合**：未发现。`TestNopMetaQualityScoreBizModel.testMixedDimensionsAndOverallScore` 显式注释"手工计算：completeness=50 ... overall = 55.0"。
- **P-5 过度使用 assertNotNull**：未发现滥用。所有测试都用 `assertEquals(具体值, ...)`。
- **P-6 方法名不表达意图**：未发现。方法名一致采用 `testXxxWhenYyyThenZzz` 格式。
- **P-7 测试间隐式依赖**：未发现。所有测试自构造数据，无共享可变 static 状态（mock 文件的 static 字段有 `@BeforeEach reset()`）。
- **P-8 无效的负面测试**：未发现。

**有效测试比例估计**：~95%+ 高价值。所有测试均符合 "Anti-Hollow" 模板：真实 H2 造数 → 真实执行 → 断言具体业务字段值。**模块测试是仓库内最高质量的测试之一，可作为其他模块的参考样板**。

---

## 维度 17：代码风格与规范

### [维度17-01] Import 分组顺序与 `code-style.md` 文档约定相反

- **文件**: `nop-metadata-service/src/main/java/io/nop/metadata/service/event/MetaModelChangedEventPublisher.java:1-17`，`.../entity/NopMetaModuleBizModel.java:10-75`，`.../quality/MetaQualityRuleExecutor.java:3-17`，`.../quality/MetaQualityCheckpointScheduler.java:3-25`，`.../query/MetaJoinExecutor.java:3-41`
- **证据片段** (`MetaQualityCheckpointScheduler.java`):
  ```java
  import io.nop.api.core.beans.FilterBeans;        // io.nop.*
  import io.nop.api.core.beans.query.QueryBean;
  ...
  import io.nop.metadata.dao.entity.NopMetaQualityCheckpoint;
  import io.nop.metadata.service.entity.NopMetaQualityCheckpointBizModel;
  import jakarta.annotation.Nullable;              // jakarta.*
  import jakarta.annotation.PostConstruct;
  import jakarta.inject.Inject;
  import org.slf4j.Logger;                         // third-party
  import org.slf4j.LoggerFactory;

  import java.util.Collections;                    // java.*
  import java.util.HashMap;
  ```
- **严重程度**: P2
- **现状**: `docs-for-ai/02-core-guides/code-style.md:17` 明确规定 import 顺序：`java.* → jakarta.* → third-party → io.nop.*`。但 nop-metadata 全模块一致采用 IDE 默认的字母序：`io.nop.* → jakarta.* → third-party → java.*`，与文档约定相反。检查 5 个手写文件全部违反。注：其他模块（nop-auth、nop-batch）也有类似问题，说明这是仓库级 IDE 默认结果。
- **风险**: 文档与实际不一致 → AI 跟随文档时会被警告"风格错误"，或 AI 跟随实际代码时违反文档。
- **建议**: 二选一：(a) 全模块批量重排 import；(b) 更新 `code-style.md:17` 反映实际仓库惯例。建议先做 (b) 的一次性盘点再决定。
- **信心水平**: 确定
- **复核状态**: 已保留

### [维度17-02] `NopMetadataErrors.java` 是空 placeholder 接口（死代码）

- **文件**: `nop-metadata-service/src/main/java/io/nop/metadata/service/NopMetadataErrors.java:1-5`
- **证据片段**:
  ```java
  package io.nop.metadata.service;

  public interface NopMetadataErrors{
  	
  }
  ```
- **严重程度**: P2
- **现状**: 该 interface 仅含 `public interface NopMetadataErrors{}`，无任何成员、无任何 import 引用（`grep "import.*NopMetadataErrors"` 返回 0 命中）。其他 BizModel 类内 JavaDoc 明确说："ErrorCode 按模块惯例内联于本类顶部（不写入空 interface `NopMetadataErrors`）"。该文件是 codegen 残留或废弃 placeholder。
- **风险**: 误导后续开发者以为应该在此 interface 中集中定义 ErrorCode。
- **建议**: 删除该空文件；或与 [维度09-02] 一并：把所有 ErrorCode 集中到此 interface。
- **信心水平**: 确定
- **复核状态**: 已保留

### 阴性发现（风格）

- `System.out` / `System.err` / `e.printStackTrace()` 零使用，完全 SLF4J ✓
- 命名（PascalCase / camelCase / UPPER_SNAKE_CASE）一致正确 ✓
- 注释密度高但均有语义价值，不属于"AI 风格过度注释" ✓

---

## 维度 18 + 19 + 20：文档与跨模块一致性

### [维度18-01] **CRITICAL** nop-metadata 在 `module-groups.md` 完全缺席

- **文件**: `docs-for-ai/01-repo-map/module-groups.md:9-23`
- **证据片段**:
  ```
  | 典型业务模块 | `nop-auth/`、`nop-job/`、`nop-task/`、`nop-wf/` | 最标准的业务骨架样板 |
  | 可复用业务模块 | `nop-sys/`、`nop-report/`、`nop-rule/`、`nop-batch/`、`nop-dyn/`、`nop-file/`、`nop-retry/`、`nop-tcc/` | ... |
  ```
  nop-metadata 不在任何一行；全文 `grep "nop-metadata"` 在 module-groups.md 返回 0 命中。
- **严重程度**: P1
- **现状**: nop-metadata 已有 32 个 ORM 实体、80 个 main Java 文件（18504 行）、~28 个测试文件、独立 design 文档体系（11 份 + roadmap），并已加入根 pom.xml `<modules>`。规模已超过若干"可复用业务模块"（nop-tcc/nop-retry）。但 module-groups.md（仓库模块分组的权威入口）完全未提它。
- **风险**: AI 在做"找标准业务实现参考"或"判断一个业务模块怎么分层"时，不会去看 nop-metadata，错过它的 32 实体 + GraphQL 自动暴露 + cron 调度集成 + BI 语义层等参考价值；新 AI 在跨模块搜索时会无法定位此模块。
- **建议**: 在 `module-groups.md` 的"可复用业务模块"行新增 `nop-metadata/`，并加一段独立小节描述其用途（联邦式元数据 / BI 语义层 / 血缘 / 质量 / 对账）。同步在 `INDEX.md` 加入 `03-modules/nop-metadata.md` 入口。
- **信心水平**: 确定
- **复核状态**: 已保留

### [维度18-02] nop-metadata 在 `docs-for-ai/03-modules/` 无对应模块文档

- **文件**: `docs-for-ai/03-modules/` 目录
- **证据片段**: `ls docs-for-ai/03-modules/` 含 `nop-auth.md`、`nop-batch.md`、`nop-code.md`、`nop-dyn.md`、`nop-file.md`、`nop-job.md`、`nop-report.md`、`nop-retry.md`、`nop-rule.md`、`nop-sys.md`、`nop-task.md`、`nop-tcc.md`、`nop-wf.md`、`reusable-modules-overview.md` — 无 `nop-metadata.md`。
- **严重程度**: P1
- **现状**: INDEX.md 的快速路由表也无 nop-metadata 条目。`reusable-modules-overview.md` 同样未列。这与 nop-metadata 已实现的成熟度严重不匹配。
- **风险**: AI 在"选择可复用业务模块"路由时不会考虑 nop-metadata；需要元数据 / 血缘 / 质量评分能力的应用项目会重复造轮子。
- **建议**: 新增 `docs-for-ai/03-modules/nop-metadata.md`，参考 `nop-batch.md` / `nop-rule.md` 的结构（定位、核心概念、典型使用场景、与 design 文档的链接）。同步在 INDEX.md 路由表添加一行：「理解 nop-metadata（元数据目录/BI 语义层/血缘/质量）→ `03-modules/nop-metadata.md`」。
- **信心水平**: 确定
- **复核状态**: 已保留

### [维度18-03] nop-metadata 在 `source-anchors.md` 完全缺席

- **文件**: `docs-for-ai/04-reference/source-anchors.md`
- **证据片段**: `grep "nop-metadata" source-anchors.md` 返回 0 命中。
- **严重程度**: P2
- **现状**: source-anchors.md 列了 `BIZ-001` 到 `BIZ-007`、`AUDIT-001/002`、`CODE-001..004`、`REPORT-001..003`、`SYS-001` 等共 ~80 个实现锚点，覆盖几乎所有可复用模块；nop-metadata 内有多个值得作为锚点的核心类（`MetaAggregationExecutor`、`MetaTableReferenceResolver`、`MetaQualityRuleExecutor`、`SqlColumnLineageExtractor`、`MetaQualityCheckpointScheduler` 展示 nop-job-api 可空注入模式），全部缺席。
- **风险**: AI 在做"跨表 JOIN 聚合执行"、"SQL 列级血缘抽取"、"cron 调度器可空注入模式"等查询时，无锚点指引。
- **建议**: 在 source-anchors.md 增加 `META-001..005` 锚点条目。
- **信心水平**: 很可能
- **复核状态**: 已保留

### [维度18-04] design 文档 roadmap 文本"21 实体"过时，实际为 32 实体

- **文件**: `ai-dev/design/nop-metadata/nop-metadata-roadmap.md:61-62, 79`
- **证据片段**:
  ```
  - 21 实体完全建模（`nop-metadata.orm.xml`），覆盖：模块/版本管理（Module/OrmModel）、数据源（DataSource）、ORM 拆解（Entity/Field/Relation/UniqueKey/Index/Domain/SemanticType/Dict/DictItem）、BI 语义层（Table/Measure/Dimension/Filter/Join）、血缘（LineageEdge/Pipeline）、数据质量（QualityRule/QualityResult）
  | P1-1 | 确认 21 实体 CRUD 通过 xbiz 自动暴露 | done |
  ```
- **严重程度**: P2
- **现状**: 实际 `nop-metadata/model/nop-metadata.orm.xml` 已含 **32 实体**（多出 11 个：QualityCheckpoint、Manifest、Catalog、ProfilingRule、ProfilingResult、DataContract、ReconciliationConfig/Result/Entity、QualityScore、ModelChangedEvent）。设计文档其他章节确实提到这些新实体，但 roadmap 的"21 实体"总结未同步更新。
- **风险**: AI 阅读 roadmap 时获得错误的实体数量与覆盖范围认知。
- **建议**: 更新 roadmap.md 第 61-62、79 行的"21 实体"为"32 实体"，并补全实体列表。
- **信心水平**: 确定
- **复核状态**: 已保留

### [维度18-05] docs-for-ai/ 中仅 code-style.md 与 project-context.md 提及 nop-metadata

- **文件**: `docs-for-ai/02-core-guides/code-style.md:79`、`docs-for-ai/00-start-here/project-context.md:17`
- **严重程度**: P3
- **现状**: code-style.md 用 nop-metadata 作为"option code 禁带中横线"等教训的反例（这是好的），但缺少正向的"如何使用 nop-metadata"文档。
- **建议**: 与 18-02 一并解决。
- **信心水平**: 确定
- **复核状态**: 已保留

### [维度19-01] **CRITICAL** 表名 `nop_meta_recon_*` 与实体名 `NopMetaReconciliation*` 不一致（缩写 vs 全称）

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:2027, 2106, 2178`
- **证据片段**:
  ```xml
  <entity className="io.nop.metadata.dao.entity.NopMetaReconciliationConfig" ...
          name="io.nop.metadata.dao.entity.NopMetaReconciliationConfig" registerShortName="true"
          tableName="nop_meta_recon_config" ...>
  ...
  <entity className="io.nop.metadata.dao.entity.NopMetaReconciliationResult" ...
          tableName="nop_meta_recon_result" ...>
  ...
  <entity className="io.nop.metadata.dao.entity.NopMetaReconciliationEntity" ...
          tableName="nop_meta_recon_entity" ...>
  ```
- **严重程度**: P1
- **现状**: `code-style.md:47` 明确规范：`tableName: nop_{模块}_{实体snake_case}` + `entity shortName: Nop{模块Pascal}{实体Pascal}`。按此规范，实体 `NopMetaReconciliationConfig` 应映射表名 `nop_meta_reconciliation_config`（全称 snake_case）。但实际表名是 `nop_meta_recon_config`（用 `recon` 缩写）。其他 29 个实体都遵守全称映射（如 `nop_meta_data_source` → `NopMetaDataSource`、`nop_meta_quality_checkpoint` → `NopMetaQualityCheckpoint`），仅这 3 个 Reconciliation 实体使用缩写。变更此命名属于 ORM 结构变更（Protected Area，需 plan-first + 迁移）。
- **风险**: 命名不一致让 AI / 开发者难以从表名反推实体名（无法机械应用 snake_case → PascalCase 规则）；DBA 直接查表时也需记忆特殊缩写。
- **建议**: 起一份 plan（Protected Area），将 `nop_meta_recon_*` 三张表重命名为 `nop_meta_reconciliation_*`，并在 nop-metadata 自身的测试库 + design 文档中同步。变更需评估是否有外部应用已依赖现有表名（grep 显示无外部模块依赖 nop-metadata，迁移风险可控）。
- **信心水平**: 确定
- **复核状态**: 已保留

### [维度19-02] 见 [维度09-01]（合并）

ErrorCode 前缀 `metadata.*` 违反平台约定 `nop.err.{module}.*`，已合并至维度 09-01。

### [维度20-01] **HIGH** 主代码 10 处使用 `new Timestamp(System.currentTimeMillis())` 违反 DDD-006 锚点

- **文件**:
  - `event/MetaModelChangedEventPublisher.java:105`
  - `entity/NopMetaProfilingRuleBizModel.java:160`
  - `entity/NopMetaReconciliationConfigBizModel.java:138`
  - `entity/NopMetaTableBizModel.java:954`
  - `profiling/ProfilingSnapshot.java:102`
  - `entity/NopMetaQualityScoreBizModel.java:60`
  - `entity/NopMetaModuleBizModel.java:479`
  - `entity/NopMetaDataSourceBizModel.java:377, 486`
  - `quality/MetaQualityRuleExecutor.java:544`
  - `quality/QualityResultWriter.java:34`
- **证据片段**:
  ```java
  // MetaModelChangedEventPublisher.java:105
  event.setChangeTime(new Timestamp(System.currentTimeMillis()));

  // NopMetaReconciliationConfigBizModel.java:138
  result.setExecuteTime(new Timestamp(System.currentTimeMillis()));
  ```
- **严重程度**: P1
- **现状**: `docs-for-ai/04-reference/source-anchors.md:46` (`DDD-006`) 明确规定："获取当前时间一律使用 `CoreMetrics`，禁止 `System.currentTimeMillis()`/`System.nanoTime()`/`LocalDateTime.now()`/`LocalDate.now()`/`new Date()`/`new Timestamp(...)`。按返回类型选 `currentTimeMillis`/`currentTimestamp`/`currentDateTime`/`currentDate`/`nanoTime`"。模块共 10 处主代码违反。
- **风险**: (1) `CoreMetrics` 在测试中可被 mock 固定时间，便于断言；绕过它导致测试无法控制时间。(2) 单元测试与集成测试的时间行为不一致。(3) 多机器集群时间漂移时无法统一基准。(4) 违反明确的 source-anchor 规则。
- **建议**: 全模块批量替换 `new Timestamp(System.currentTimeMillis())` → `CoreMetrics.currentTimestamp()`，`System.currentTimeMillis()` → `CoreMetrics.currentTimeMillis()`。需在主代码 import `io.nop.api.core.time.CoreMetrics`。
- **信心水平**: 确定
- **复核状态**: 已保留

### 阴性发现（跨模块契约）

- **nop-job-api 集成模式正确**：compile + test scope 分离，`@Inject @Nullable IJobScheduler` 处理宿主未启用 job 的场景。`TestMetaQualityCheckpointScheduler.testCronJobFireNowWritesResultsAndScores` 是平台测试 nop-job 集成的优秀参考。
- **nop-metadata 是叶子业务模块**：无外部消费者，命名重构和 System.currentTimeMillis 修复的迁移成本可控。
- **nop-metadata-app 与 nop-auth-web/nop-auth-service 集成正确**：application.yaml 配置 JWT、site-map、data-auth 路径均符合平台约定。
- **nop-biz/nop-biz-file-core/nop-http-api/nop-ioc/nop-config/nop-sys-dao 依赖使用合规**：通过平台公共 API 使用，未发现绕过 internal 类的情况。

---

## 总评

### 模块整体质量评估

nop-metadata 是一个**功能完整、测试优秀、但工程规范存在系统性偏差**的中型业务模块（~67K Java 行，32 实体，覆盖元数据目录 / BI 语义层 / 血缘 / 数据质量 / 对账五大子域）：

**优势**：
- **测试质量是仓库内最高水平之一**：关键引擎类全覆盖、错误路径覆盖优秀、~95% 测试为有效高价值，符合"Anti-Hollow"模板。
- **生成管线完整自洽**：codegen 模板、xmeta/xbiz/page 产物、`_gen/` 与手写留存层边界清晰。
- **IoC 与事务模式正确**：无 Spring 专有注解、`@Inject` 字段全为 protected、`txnTemplate.runWithoutTransaction` 正确隔离外部 IO。
- **nop-job-api 可空注入模式**：是跨模块测试 nop-job 集成的优秀参考样板。

**主要问题**：
- **API 契约层薄弱**：32 个 `I*Biz` 接口为空、20+ BizModel 方法返回 `Map<String, Object>`、未使用 `FieldSelectionBean` —— 共同导致 GraphQL schema 类型推导失效、跨模块调用会抛 `unsupported-method`。
- **错误处理体系分散**：178 处 ErrorCode.define 散落 20+ 文件、命名前缀 `metadata.*` 违反平台 `nop.err.metadata.*` 约定、4 处错误码重复定义、4 处 inline define、SQLException/IllegalArgumentException/UnsupportedOperationException 多种非 NopException 抛出。
- **ORM 模型与索引不完整**：所有父子关系缺 to-many 反向指针、`NopMetaLineageEdge` 完全无 relations 块、7+ 个 FK 列缺索引、29 个实体缺自然键 UK、`mediumtext` domain precision 错误（实际生成 LONGTEXT）。
- **安全敏感字段未受控**：`NopMetaDataSource.connectionConfig` 明文存密码且通过 GraphQL 暴露、data-auth.xml 为空、custom_sql 规则可执行任意 SQL、webhook URL 无 SSRF 防护。
- **文档体系严重缺失**：module-groups.md / 03-modules/ / source-anchors.md 三处官方文档完全未提 nop-metadata；roadmap.md "21 实体"已过时。
- **命名一致性偏差**：表名 `nop_meta_recon_*` 用缩写违反全称映射规则、`DEL_VERSION` 列名误用、`deleteVersionProp`/`delFlagProp` 命名前缀不一致。
- **大类未拆 Processor**：`MetaAggregationExecutor` 3474 行、`NopMetaTableBizModel` 984 行，混合多类职责。

**优势与问题对比**：测试质量明显高于平均，但 API 契约 + 错误处理 + ORM 完整性三项系统性偏差需要排期整改。

## 优先修复建议

### P1（高优先级，与对外契约/安全/数据完整性相关）

1. **[维度13-01]** `connectionConfig` 明文密码 GraphQL 暴露 — 安全违约，立即修复（xmeta 设 `published="false"` 或加密落盘）。
2. **[维度03-01/07-01/11-01]** 32 个 `I*Biz` 接口补齐自定义方法 — 跨模块契约基础设施。
3. **[维度03-02/15-01]** 20+ BizModel 方法返回 `Map<String,Object>` 改造为 `@DataBean` DTO — GraphQL 类型契约。
4. **[维度04-02]** `NopMetaLineageEdge` 补 `<relations>` 块 — 血缘模块核心能力。
5. **[维度04-03]** 7+ 个 FK 列补索引 — 性能 / 锁问题。
6. **[维度04-01]** 父实体补 to-many — 与 nop-auth 平台约定一致。
7. **[维度18-01]** 在 `module-groups.md` 加入 nop-metadata 条目 — 文档基础设施。
8. **[维度18-02]** 新增 `docs-for-ai/03-modules/nop-metadata.md` — 文档基础设施。
9. **[维度20-01]** 主代码 10 处 `System.currentTimeMillis()` → `CoreMetrics` — DDD-006 锚点违规，机械替换。
10. **[维度19-01]** 表名 `nop_meta_recon_*` → `nop_meta_reconciliation_*`（plan-first，ORM 结构变更）。

### P2（中优先级，工程规范系统性整改）

- [维度09-01/19-02] ErrorCode 前缀全量改为 `nop.err.metadata.*`
- [维度09-02/17-02] ErrorCode 集中到 `NopMetadataErrors.java`
- [维度02-02] 拆分 `MetaAggregationExecutor` 3474 行
- [维度13-02] 补 data-auth.xml 行级权限规则
- [维度13-03/14-03] custom_sql 沙箱化（PreparedStatement + 白名单）
- [维度13-04/14-04] webhook URL SSRF 防护
- [维度14-01] 长事务 + 外部连接持有优化
- [维度04-04] `mediumtext` precision 修正
- [维度04-05] `IX_NOP_META_TABLE_DEDUP` 改为 UK 或重命名
- [维度04-06] 29 个实体补自然键 UK
- [维度04-07] dict option 补 `i18n-en:label`
- [维度17-01] import 顺序全模块重排或更新文档
- [维度18-03] source-anchors.md 补 META-001..005 锚点
- [维度18-04] roadmap.md "21 实体"→"32 实体"

### P3（低优先级，细节与命名）

- [维度01-02] 移除 nop-metadata-core 子模块（合并到 dao）
- [维度02-03] `TableReference` 类名冲突重命名
- [维度02-04] `OrmModelImporter` 移到 service 层
- [维度04-08] 启用 `useLogicalDelete` 或移除未使用 domain
- [维度04-09] `DEL_VERSION` 列名改为 `VERSION`
- [维度04-10] NopMetaDictItem 补 `isDelta`
- [维度04-11] `deleteVersionProp` → `delVersionProp`
- [维度09-05] 新增 `NopMetadataException`
- [维度09-06] IllegalArgumentException 改 NopException
- [维度09-07] UnsupportedOperationException 改 NopException
- [维度01-01] 处置 `nop-metadata-api` 死模块

## 本次审核盲区自评

- **未运行 Maven 构建/测试**：审计基于源码静态阅读 + grep，未实际跑 `./mvnw test -pl nop-metadata -am` 验证测试是否通过、未跑 checkstyle 验证风格规则。建议在落地修复时先跑一次完整构建。
- **未做完整 32 实体逐一审计**：维度 04 抽查 ~12 个核心实体（NopMetaModule/Table/DataSource/Entity/EntityField/OrmModel/LineageEdge/Dict/DictItem/TableJoin/ReconciliationConfig/ReconciliationResult），其余 20 个实体仅做快速 grep 校验，可能漏掉局部问题。
- **未做完整 32 BizModel 逐一审计**：维度 07 抽查 8 个 BizModel，其余通过 grep 验证签名一致性，可能漏掉个别 BizModel 的特殊问题。
- **未深挖 codegen 模板**：维度 05 只确认产物存在与路径对齐，未深挖 `/nop/templates/orm` / `/nop/templates/meta` 平台模板内容（属于平台核心，超出本模块审计范围）。
- **未审计前端页面（amis view.yaml）**：维度 12 仅检查列表字段可见性，未审计 picker/main/lib 等前端交互逻辑。
- **未审计 SQL 注入的所有路径**：维度 13 抽查了 custom_sql / buildExternalSelectSql / webhook URL 三条路径，可能漏掉其他 SQL 构造点（如 catalog 收集、profiling SQL）。
- **未做跨大型数据集的性能验证**：维度 04 报告的"缺索引"问题基于静态规则校验，未实际造数据验证查询性能。
- **跨模块调用图未完整构建**：仅确认 nop-metadata 是叶子模块，未审计同仓库未来可能消费 nop-metadata 的潜在场景。

<AI_STEP_RESULT>issues</AI_STEP_RESULT>
