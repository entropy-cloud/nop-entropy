> Audit Status: planned
> Audit Type: multi-dimensional
> Mission: nop-metadata

# 深度审核汇总报告

## 基本信息

- **审核模块**: nop-metadata
- **审核日期**: 2026-07-21
- **执行维度**: 01(依赖图), 04(ORM模型), 05(生成管线), 07(BizModel规范), 08(IoC配置), 09(错误处理), 11(XMeta对齐), 16(测试覆盖)
- **目标范围**: nop-metadata/ 全部 8 个子模块（core/api/dao/codegen/meta/service/web/app）的代码、配置、测试和公开契约

## 模块概览

nop-metadata 是 Nop 平台的联邦式元数据中心，承担元数据目录、BI 语义层、血缘追踪、数据质量、数据对账五类职责。共 39 个 ORM 实体，40 个 BizModel，~88K 行 Java 代码，~70+ 测试类。

## 执行统计

| 维度 | 深挖轮次 | 初审发现数 | 追加发现数 |
|------|---------|-----------|-----------|
| 01-依赖图 | 1 | 7 | 0 |
| 04-ORM模型 | 1 | 7 | 0 |
| 05-生成管线 | 1 | 6 | 0 |
| 07-BizModel规范 | 1 | 7 | 0 |
| 08-IoC配置 | 1 | 0（全部正向） | 0 |
| 09-错误处理 | 1 | 8 | 0 |
| 11-XMeta对齐 | 1 | 6 | 0 |
| 16-测试覆盖 | 1 | 5 | 0 |
| **合计** | 8 | **46** | 0 |

## 按严重程度分布

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 1 | 架构违规（NopMetaSearch 无 xmeta） |
| P1 | 5 | 字典缺失、测试模式违规、权限属性、测试覆盖缺口 |
| P2 | 16 | 类型安全、API 契约、异常静默、测试质量问题 |
| P3 | 12 | 命名风格、scope 优化、命名不一致、本地化瑕疵 |
| 正向 | 12 | IoC 全部合规 + 管线对齐 + 部分 BizModel 正确 |

## 关键发现摘要

### P0 发现

- **[维度11-01]** `NopMetaSearchBizModel` 声明了 `@BizModel("NopMetaSearch")` 但缺少对应的 xmeta。40 个 `@BizModel` 中唯此无 xmeta。前端 GraphQL 调用失败，仅 `/r/` REST 路径可用。违反 service-layer.md "每个 @BizModel 必须有 xmeta" 规则。**文件**: `NopMetaSearchBizModel.java:28`

### P1 发现

- **[维度04-01]** NopMetaModelChangedEvent 实体的 `changeSource` 字段引用 `ext:dict="meta/change-source"`，但 `<dicts>` 段未定义此字典。启动期可能校验失败。**文件**: `nop-metadata.orm.xml:2814`
- **[维度11-05]** NopMetaDataSource.connectionConfig 保留层的 `sortable` 属性未覆盖，从生成层继承 `sortable="true"`，与 `queryable="false"` 语义冲突。**文件**: `NopMetaDataSource.xmeta:14`
- **[维度16-01]** 约 19/40 个 BizModel 类（40%）零测试覆盖，包括 EntityField、Classification、ReconciliationEntity 等关键实体。**文件**: 多个 BizModel
- **[维度16-03]** 三个聚合测试文件（~1830 行）直接调用 BizModel 方法而非通过 `IGraphQLEngine.executeRpc`，违反 testing.md 第25-48行规则。**文件**: `TestAggregationCategoricalAndTemporal.java:71`

### P2 发现（精选）

- **[维度01-01]** nop-metadata-api 的 parent 指向 nop-entropy 而非 nop-metadata，与其他 api 子模块模式不统一。**文件**: `nop-metadata-api/pom.xml:5-12`
- **[维度01-05]** nop-metadata 全模块未注册到 nop-bom。**文件**: `nop-bom/pom.xml`
- **[维度04-02]** NopMetaGlossary 到 NopMetaGlossaryTerm 缺失 cascadeDelete，与 Classification->Tag 不一致。**文件**: `nop-metadata.orm.xml:2917-2923`
- **[维度04-04]** NopMetaTagLabel 缺少唯一约束，存在重复标注风险。**文件**: `nop-metadata.orm.xml:3192-3287`
- **[维度07-02]** `recordLineage` 接受 `List<Map<String, Object>>` 代替类型安全 DTO。**文件**: `NopMetaLineageEdgeBizModel.java:123-125`
- **[维度07-03]** `importOrmModels` 返回 `List<Map<String, Object>>` 而非 DTO。**文件**: `NopMetaModuleBizModel.java:361-386`
- **[维度07-04]** DataSourceBizModel 3 个 mutation 使用 `dao().getEntityById()` 绕过 `requireEntity()`。**文件**: `NopMetaDataSourceBizModel.java:120,175,254`
- **[维度09-01/02/03]** 7 处 catch 块静默吞异常无日志。**文件**: `AggregationContext.java:1413` 等多处
- **[维度11-04]** 7 个 CrudBizModel 子类无 I*Biz 接口。**文件**: `NopMetaQualityCheckpointBizModel.java:65` 等
- **[维度16-02]** 搜索测试使用 Mockito mock 替代真实搜索引擎。**文件**: `TestNopMetadataSearchIntegration.java:33`
- **[维度16-04]** `releaseModule`, `generateManifest`, `activateContract` 等核心方法无测试。**文件**: `NopMetaModuleBizModel.java:444`
- **[维度16-05]** 5 个 JOIN 处理器测试每个 < 50 行，零保护力。**文件**: `TestEntityAggregationProcessor.java:28`

### 正向发现

- **[维度08]** IoC 与 Bean 配置质量优秀：所有 @Inject 字段为 protected，无 Spring 注解，@InjectValue 语法全部正确，生成文件未被手动修改，循环依赖正确规避。
- **[维度05]** 生成管线完整：39 个实体在各层（model/entity/xmeta/xbiz/page/BizModel/I*Biz）数量完全一致。
- **[维度07-06]** 标准 CRUD BizModel（DictBizModel, TagBizModel）正确遵循规范。

## 文档-代码一致性检查

对比 `docs-for-ai/03-modules/nop-metadata.md` 与实际代码：

| 文档声明 | 实际状态 | 一致性 |
|---------|---------|--------|
| "每个 BizModel 都实现了对应的 I*Biz 接口" | 39/39 中 32/39 实现，7 个缺失 | ⚠️ 不一致（维度11-04） |
| "nop-metadata-api 为跨模块 API 接口定义" | 模块为空（无 src/） | ⚠️ 模块存在但无内容 |
| "ErrorCode 已集中到 NopMetadataErrors.java" | 37 处 .param() 使用字符串字面量而非常量 | ⚠️ 部分不一致（维度09-04） |
| "失败路径显式化" — "显式抛 NopException + ErrorCode" | 7 处 catch 静默吞异常无日志 | ⚠️ 部分违反（维度09-01/02/03） |
| API 契约列出 INopMetaTableBiz 等接口方法 | 接口存在且签名正确 | ✅ 一致 |
| "每个 BizModel 都有对应接口声明全部自定义方法" | 7 个 BizModel 无 I*Biz 接口 | ⚠️ 不一致 |

## 总评

nop-metadata 是一个成熟的元数据中心模块，整体架构质量良好。模块严格遵循 Nop 平台标准分层模式（core→dao→meta→service→web→app），依赖图无循环依赖，生成管线完整，IoC 配置优秀。

**主要风险区域**：

1. **测试覆盖缺口（P1）**：40% 的 BizModel 零测试覆盖；聚合测试绕过推荐测试模式直接调用 BizModel；核心业务流程（releaseModule、合同状态机）无端到端测试。这是当前最大的质量风险。

2. **"伪 BizModel"违规（P0）**：`NopMetaSearchBizModel` 缺少 xmeta，禁止前端 GraphQL 调用。虽然 `/r/` 路径可以工作，但这形成了隐式的 API 不一致。

3. **类型安全反模式（P2）**：3 处 `Map<String, Object>` 替代 DTO 的使用违背了 service-layer.md 的类型安全原则。

4. **异常静默丢弃（P2）**：7 处 catch 块静默吞异常无日志，违反"丢弃前必留证"规范。

**次要改进项**（P3）：nop-metadata-api parent 指向错误、BOM 未注册、ORM 模型中 `meta/change-source` 字典缺失、部分 ErrorCode 命名不一致等。

## 优先修复建议

1. **P0**: 为 NopMetaSearchBizModel 创建 xmeta 或重构为 Processor
2. **P1**: 补全 `meta/change-source` 字典定义
3. **P1**: 修改聚合测试，使用 `IGraphQLEngine.executeRpc` 而非直接调用 BizModel
4. **P1**: 为 19 个零测试 BizModel 中高优先级的 5 个添加集成测试
5. **P1**: 在 retention xmeta 中补上 `connectionConfig` 的 `sortable="false"`
6. **P2**: 统一 nop-metadata-api parent 为 nop-metadata
7. **P2**: 注册 nop-metadata 子模块到 nop-bom
8. **P2**: 用 `@DataBean` DTO 替换 `Map<String, Object>` 输入/输出
9. **P2**: 修复 7 处异常静默（加日志）
10. **P2**: 为 7 个 CrudBizModel 子类创建 I*Biz 接口

## 本次审核盲区自评

- 未执行 Maven 构建验证（依赖树等基线使用文件分析而非 `mvn dependency:tree`）
- 未对 web 模块的页面资源进行深度检查（xview、page.yaml 与 BizModel 对齐）
- 未检查安全相关注解（@Auth）的配置完整性
- 未执行跨模块调用链分析（哪些外部模块调用了 nop-metadata 的哪些接口）
- 未对模块性能、SQL 查询效率进行审计

> **Audit Status**: open — 发现的问题建议修复后关闭。
> **Issues found**: 34 个发现（1 P0 + 5 P1 + 16 P2 + 12 P3），12 个正向发现。

<AI_STEP_RESULT>issues</AI_STEP_RESULT>
