# 05 · nop-metadata 深审发现

> 范围：`nop-metadata/`（api/core/dao/meta/service/web/app），排除 src/test、_gen、target。
> 规则编号 R1-R16 见 `01-checklist-and-baseline.md`。✅verified 条目已经主审计源码复核（计数经主审计以 `rg -o | wc -l` 重新核定：BizModel 层 `daoFor(` 66 处 + `getEntityById(` 19 处 = **85 处 / 15 个 BizModel 文件**）。

## A. 做对的部分（多维度模范级）

1. **40/40 BizModel 规范**（R4/R7/R8）：全部 `extends CrudBizModel<T> implements INopMetaXBiz`（唯一无接口的 `NopMetaSearchBizModel` 为 owner doc 登记例外，有 xmeta）；程序化全量校验 0 个 `@BizQuery/@BizMutation` 方法缺 `IServiceContext` 末参；0 处 `@Inject private`、0 处 `@BizMutation+@Transactional`、0 个 `*Service/*Controller`。
2. **异常与失败路径 ≈95%**（R13）：0 处裸 RuntimeException 族；`NopMetadataException` 352 处 vs 直接 `NopException` 1 处；ErrorCode 全部 `ErrorCode.define` 集中声明；批量路径 per-row try/catch + errors 收集，无静默吞错——owner doc「失败路径显式化原则」模范落实。
3. **工具约定 ≈95%**（R14）：0 处 `new ObjectMapper`/Commons StringUtils/Files.readString/FileInputStream/无字符集 getBytes；CoreMetrics 已在 16 文件采用（残留 3 处见 MD-4）。
4. **平台能力复用 ≈90%**（R3）：调度用 `IJobScheduler`（nop-job-api 可空注入）；通知用 `IMessageService`+`IHttpClient`；审批用 nop-wf（3 个 xwf + approval-support.xbiz）；搜索用 nop-search-api；聚合走 EQL。无自建调度器/JSON/连接池。
5. **拆分落位正确**（R10）：16 Processor + 7 Executor + 大量编排支撑类（service 模块 129 文件中非 BizModel 编排类 ≈72）；超长方法 15 个中仅 1 个在 BizModel 内（87 行），其余全部位于 Processor/Executor——**拆分落位是三个深审模块中最规范的**。

## B. 发现清单

**MD-1 ✅verified · BizModel 层系统性直用 DAO 访问跨聚合实体（85 处 / 15 个 BizModel），I*Biz 注入仅 1 处——P1（R6/R5）**
代表：`NopMetaQualityRuleBizModel.java:193`、`NopMetaTableMeasureBizModel.java:81-115`、`NopMetaDataSourceBizModel.java:777-1014`、`NopMetaModuleBizModel.java:179-631` 等，形态为：
```java
NopMetaDataSource dataSource = daoFor(NopMetaDataSource.class).getEntityById(dataSourceId);
```
对照：全 service 模块仅 `NopMetaReconciliationConfigBizModel.java:63` 一处 `@Inject INopMetaTableBiz`，证明替代路径可行但未推广；owner doc 定义了 14 个 I*Biz 契约却基本未被内部消费；85 处无一注释降级理由。
危害：静默跳过 `checkDataAuth/checkMetaFilter` 数据权限管道；orm.xml 已具备租户 UK 变体派生，多租户场景下这 85 处将整体成为权限旁路面。
修复：跨聚合读改注入 `INopMetaXBiz.get()/findList()` 或 ORM 关系 getter；若整体裁定为「元数据图谱查询引擎 = infra 边界」，须在 owner doc 显式登记并在代码注释（二选一，逐文件收口）。

**MD-2 · 纯贫血模型：39 个实体 0 领域方法，稳定状态判断散落 BizModel/Resolver——P2（R10，对照基线 §2.2）**
39 个实体全部 11 行空壳（`NopMetaTable.java` 等）；`DATASOURCE_STATUS_DISABLED` 判断散落 `MetaDataSourceResolver:75`、`NopMetaDataSourceBizModel:168/611/725`；tableType 三分派 if-else 在 `NopMetaTableBizModel.queryTableData:271-277`。
修复：`NopMetaDataSource.isDisabled()`、`NopMetaTable.isEntityTable()/isExternalTable()/isSqlTable()` 等稳定只读判断下沉实体；或在 owner doc 登记「贫血实体 + Processor 承载」显式裁定（owner doc 为其他维度均有裁定，唯此维度未裁定，属未裁定偏离）。

**MD-3 ✅verified · 3 个凭证管理 public @BizMutation 未同步 I*Biz 接口 + 返回 `Map<String,Object>`——P2（R7/R16）**
`NopMetaDataSourceBizModel.java:280/324/398` `bindCredential/unbindCredential/migrateDataSourcesCredential`；`INopMetaDataSourceBiz` 仅声明 4 方法，owner doc 14 接口清单亦未含此 3 个（W16 凭证迁移新增未同步）；接口完整性守卫测试未拦截实现类新增方法。
修复：接口补声明；定义 `CredentialBindResultDTO`/`CredentialMigrationResultDTO`（owner doc 已列出返回字段，DTO 化成本低）。

**MD-4 · 裸时间 API 3 处（双标准残留）——P2（R14）**
`MetaContractChecker.java:67/122`（`new Date()`，timestamp 进对外输出）、`NopMetaModuleBizModel.java:605`（`new Timestamp` 进持久化实体）。模块其余 16 文件已正确用 CoreMetrics。
修复：`CoreMetrics.currentDate()/currentTimestamp()`。

**MD-5 · 质量状态字面量硬编码 ~20 处，绕过已有常量与字典——P3（R12）**
`MetaQualityRuleExecutor.java:200-558` 多处 `setStatus("PASS"/"FAIL")`、`QualityResultWriter.java:32` `Set.of("PASS","FAIL","ERROR","SKIP")`；同模块 `MetaQualityScorer:126-134` 正确用 `_NopMetadataCoreConstants` 且 dict yaml 已存在——双标准。修复：统一常量（sed 级替换）。

**MD-6 · AutoClassificationProcessor 未登记 nop-rule 评估结论——P3（R3）**
标签自动分类（325 行自建）是典型规则式决策；质量规则需方言感知 SQL 检查不适用 nop-rule 可接受，但应在 owner doc 登记「nop-rule 不适配性」裁定避免复读。

**MD-7 · assertCredentialAdmin 自建 CSV 角色判定——P3（R4 权限约定）**
`NopMetaDataSourceBizModel.java:206-226`，无登录态直接放行削弱静态可审计性。修复：`@Auth` 静态兜底 + 保留动态判定双层。

**MD-8 · 内部组件间 `Map<String,Object>` 交接——P3（R16）**
`MetaContractChecker.check`、`MetaQualityCheckpointExecutor.execute`、`joinExecutor.executeJoin`。缓解：BizModel 出口均已 DTO 化，Map 仅内部边界。修复：内部交接面 DTO 化。

**MD-9 · owner doc 与实现误差——P3（文档）**
`docs-for-ai/03-modules/nop-metadata.md:282` 称 ErrorCode 集中在单文件，实际为 10 文件分组聚合接口（实现合理，文档表述过时）。

## C. 统计

| 指标 | 数值 |
|---|---|
| BizModel / Processor / Executor | 40 / 16 / 7（编排支撑类 ≈72） |
| 超长方法 >80 行 | 15（BizModel 内仅 1；拆分落位正确） |
| 实体领域方法 | **0 / 39 实体** |
| BizModel 层 DAO 直用 | **85 处 / 15 文件**（✅verified）；I*Biz 注入 1 处 |
| I*Biz 接口 | 39（14 含方法 / 25 空标记）+ 缺口 3 方法 |
| 裸异常 / 裸工具违例 | 0 / 3（裸时间） |
| 自建重复能力 | 0 项 |

## D. 小结

**总体合规率 ~85-88%**：BizModel 规范、异常、工具、能力复用均高分，架构自觉性（裁定登记文化、完整性守卫测试、dict 常量镜像）显著高于平均。**最大且唯一 P1：MD-1 系统性 DAO 直用**——当前多面向管理员影响可控，但多租户化后将成为整体权限旁路面。整改顺序：MD-1 逐文件收口（或一次性 owner doc 边界裁定）→ MD-3 接口补齐 → MD-4/MD-5 机械替换。
