# 计划 2261 变更集深度检查报告（NopMetaTable 概念缩减）

> Status: resolved（两轮独立核验收敛：第 1 轮确认 C1-C6 + 新增 N1-N9；实现修正后第 2 轮验证 9/10 PASS，剩余收尾项已完成并经机械核对）
> Date: 2026-09-17
> Scope: 分支 `feat-nop-metadata-entity-unification` 上 ea39a4560f..HEAD 的变更集（343 文件，+8157/−9984）：NopMetaTable 概念缩减的模型/服务层/测试/文档全部改动
> Method: 实现者自查（本文档 §二 候选问题）→ 独立子代理对抗性核验（§四 核验记录）→ 确认属实的问题自动修正（§五 修正记录）
> Related: `ai-dev/plans/2261-nop-metadata-entity-unification.md`（已 completed）、`ai-dev/design/nop-metadata/13-entity-unification.md`

## 一、检查范围与方法

- 变更集：`git diff ea39a4560f..HEAD`（5 提交：31b43e7d8c / e7d3df6148 / f2e782a0ae / f1079d8218 + 计划文档）。
- 重点精读区：NopMetaEntityBizModel（合并后最大新增面）、NopMetaEntityQueryAction、NopMetaDataSourceBizModel.syncExternalTables（重写）、MetaEntityFieldResolver/MetaEntityReferenceResolver/MetaJoinExecutor/MetaAggregationExecutor（端点语义）、OrmModelImporter/NopMetaModuleBizModel（双写删除与级联）、orm.xml 模型、deploy/sql、测试夹具语义。
- 机制：符号级 grep 清点 + 逐方法读码 + 针对性命令验证（每条问题附验证方式）。

## 二、候选问题清单（实现者自查产出，待核验）

### C1 外部实体名生成规则缺冲突消解（Medium）

- 位置：`nop-metadata-service/.../entity/NopMetaDataSourceBizModel.java` `externalEntityName()` / `upsertExternalTable()`
- 现象：实体名规则为 `{schema}_{tableName}`（schema 空则 tableName）。`(schema=S1, table=X_Y)` 与 `(schema=S1_X, table=Y)` 生成同名 `S1_X_Y`。upsert 按 `(ormModelId, entityName)` 匹配——碰撞时第二次同步会**静默 UPDATE 首行**，改写其 dbSchema/externalColumns（元数据腐蚀，无报错）。
- 佐证：设计文档 `13-entity-unification.md` §3.1 裁定"冲突时追加序号"，实现缺失该消解分支。
- 验证方式：读 `externalEntityName`（无冲突处理代码）；构造两次 sync 断言（当前测试未覆盖碰撞场景）。

### C2 Catalog 统计 extras 的 entityKind 词表漂移（Low-Medium）

- 位置：`service/catalog/MetaCatalogCollector.java:76`
- 现象：`ref.getKind().name().toLowerCase()` 产出 `entity/external/sql`（TableReference.Kind 枚举名小写），写入 catalog 统计 extras JSON 的 `entityKind` 键；与新字典 `meta/entity-kind`（PHYSICAL/SQL_VIEW/EXTERNAL）词表不一致——消费者按字典值过滤/解析会失配。
- 验证方式：读 Kind 枚举（`tableref/TableReference.java:30`：`EXTERNAL, ENTITY, SQL`）与 :76 的拼接。

### C3 17 个变更文件残留重复 import（Low，系统性残留）

- 现象：sed 将原 `import ...NopMetaTable` 与既有 `import ...NopMetaEntity` 合并后留下重复行；涉及 NopMetadataHelper/NopMetaDataSourceBizModel/NopMetaModuleBizModel/MetaQualityCheckpointExecutor/AggregationHelper/EntityEntityJoinAggregationProcessor/MetaJoinExecutor/NopMetaIndexBuilder/MetaEntityReferenceResolver 等 17 文件（另有 INopMetaEntityBiz 一处）。
- 影响：合法 Java（编译/测试不受影响），属切换残留；checkstyle 未配置重复 import 规则故未拦截。
- 验证方式：`git diff ea39a4560f..HEAD | grep '\.java$'` 文件集逐文件 `grep '^import ' | sort | uniq -d`。

### C4 AggregationHelper.endpointTypeOf 双词表并存（Low）

- 位置：`service/query/AggregationHelper.java:425-431`
- 现象：entity 形态恒返回 `"entity"`；table 形态返回 `ep.table.getEntityKind()`（新词表 `EXTERNAL`/`SQL_VIEW`）。同函数两种词表；且 PHYSICAL 端点在该函数下不会返回 `PHYSICAL`（与字典值不对齐）。
- 验证方式：读函数体；确认消费方（聚合结果元数据/错误信息）。

### C5 NopMetadataArgs.ARG_BASE_ENTITY_ID 死常量（Low）

- 位置：`service/NopMetadataArgs.java:19`
- 现象：唯一语义消费者（baseEntityId 悬空错误族）已随概念删除，常量成死定义（INV-ERROR-PARAM 只扫描 ErrorCode 定义，不覆盖 Args 常量，故未被门禁拦截）。
- 验证方式：全仓 grep 仅命中定义行。

### C6 createSqlView 与 sync 的 OrmModel ensure 逻辑重复且并发语义不一致（Low）

- 位置：`NopMetaEntityBizModel.ensureOrmModelForModule` vs `NopMetaDataSourceBizModel.ensureExternalOrmModel`
- 现象：两段近似代码（查/建容器行），前者无锁后者有 per-key 锁体系；并发首次创建靠 `UK_NOP_META_ORM_MODEL_MODULE_NAME (metaModuleId,modelName,isDelta)` DB UK fail-fast（无腐蚀），但风格不一致且逻辑重复。
- 验证方式：读两处实现与 UK 定义。

## 三、已核验的非问题（排除记录）

- **Endpoint 双形态 NPE**：分派器保证 entity-form 只读 `.entity`、table-form 只读 `.table`（MetaJoinExecutor:106-124/MetaAggregationExecutor:130-139 分派块逐分支核对）；CrossDbInMemory 仅从同形态组合进入（mixed 走 MixedSameDb）。
- **7 路聚合分派语义**：PHYSICAL→entity 路径、EXTERNAL/SQL_VIEW→table 路径（裸 JDBC），与原 tableType 分派一一对应；unsupported kind 显式抛错（无静默默认分支）。
- **Module 级联/索引**：ormModel 链覆盖全部实体（sync/createSqlView 实体均持 ormModelId），collectModuleIndexedIds 无遗漏。
- **死错误码**：9 个死定义已删除并由 INV-ERROR-PARM 复核 exit 0。
- **deploy/sql**：三方言由构建自动再生（新列就位、nop_meta_table DDL 消失）。
- **测试覆盖缩水**：1339 全绿与基线持平；抽查 TestNopMetaEntityMultiSchemaUpsert/TestMetaEntityFieldResolverBuildSql 为新语义实质断言。

## 四、独立核验记录（迭代）

### 第 1 轮

- Reviewer: 独立子代理 agent_3e8b3224（全新会话，86 次工具调用）
- 结论: **C1-C6 全部确认属实**（C3 计数修正为 32 文件/33 行——原 17 文件只统计了 main 树；C6 机制性修正：sync 的 per-key 锁键含 tableName，不同表并发首同步时 ensure 并不互斥，两处并发保护实质同级靠 DB UK fail-fast；C4 系死代码中的不一致——endpointTypeOf 全仓无调用方）。新增发现 9 项：
  - **N1（Major，写路径回归）**：Join save 校验对 EXTERNAL/SQL_VIEW 端点恒失败——validateEntityEndpoint 统一走 resolveEntityFieldsByEntityId（按 NopMetaEntityField 行查询），而 sync/createSqlView 从不产字段行 → 空集抛 ERR_FIELD_RESOLVE_NO_FIELDS；执行路径（executeJoin）仍支持这些端点，写读断裂。基线按端点形态分派（table 端点走 resolveFieldNames）。
  - N2（Minor）：NopMetaIndexBuilder 默认类型表 "NopMetaEntity"/"MetaEntity" 双别名都映射 buildMetaEntityDocs → 全量重建实体文档构建两次；部分重建传 "NopMetaEntity" 时 purgeStaleDocsForType 按文档 tag "MetaEntity" 枚举失效（幽灵残留）；TestNopMetaIndexBuilder 把双构断言为例行。
  - N3（Minor）：resolveTableEndpointOrThrow 改造后成死方法，连带 ERR_JOIN_TABLE_DANGLING/ERR_JOIN_TABLE_TYPE_NOT_ALLOWED 孤儿错误码。
  - N4（Minor）：NopMetaModuleBizModel 死 @Inject 字段 tableBiz（合并残留）。
  - N5（Minor，文档决策项）：存量库升级路径缺失（upgrade 脚本删除、ENTITY_KIND NOT NULL 无回填）——需显式登记"无迁移路径"裁定。
  - N6（Minor）：测试名/注释/resolver javadoc 语义脱节（引用已删除的 baseEntityId 错误码叙事）。
  - N7（Minor，需裁定）：SQL 视图实体进入 manifest（ensure 复用已导入模块的 full OrmModel 容器）——设计未裁定归属，倾向"属模块内容应导出"。
  - N8（Minor，并入 C1）：schema 非法标识符字符替换未实现；超长 entityName 触发 DB 错误（per-table 隔离非静默）。
  - N9（Info）：ARG_TABLE_TYPE 等常量名/错误码名保留 TABLE 字样的命名噪音（契约稳定取舍，接受）。
  - 改进建议（已采纳）：补写路径回归检查维度、死代码机械化扫描、量化附可复现命令、补升级/迁移维度、设计裁定→实现对照表、非问题绑定范围声明、测试固化缺陷盲区问句。

### 第 2 轮（修复验证 + 报告审查）

- Reviewer: 独立子代理 agent_7114e488（全新会话，68 次工具调用，实测 8 个受影响测试类 + 6 守卫 + hollow scan）
- 结论: **10 项修正 9 项 PASS 且为语义级修复**；N6 部分完成（测试改名落地，MetaEntityReferenceResolver javadoc 未重写）；修复未引入功能性新问题（probe 终止性/收敛比较/UK fail-fast 前提均经代码追踪证实）；"登记为记录不修正"四项裁定均合理无掩盖。收尾清单 6 点已全部执行（见 §五 补充）。

## 五、确认问题的修正记录

第 1 轮核验后已自动修正（2026-09-17，修复后 `./mvnw test -pl nop-metadata` 1337 tests 全绿 + 6 不变式守卫 exit 0 + hollow scan exit 0 + 主源码清零维持）：

| # | 修正内容 |
|---|---|
| N1 | `NopMetaEntityJoinBizModel.validateEntityEndpoint` 字段归属解析改为按 entityKind 分派（`fieldResolver.resolve(entity, fieldDao)`），EXTERNAL/SQL_VIEW 端点经 externalColumns/sourceSql 解析——恢复写路径与执行路径同源；对应 2 个测试（testJoinSaveSqlTableEndpointValid/testJoinSaveExternalTableEndpointValid）改回成功语义 |
| C1+N8 | `upsertExternalTable` 实体名解析重写：schemaToken 清洗（非法标识符字符→_）+ 碰撞探测循环（基础名被占且 (dbSchema,tableName) 不同 → 追加序号 _2.._32），耗尽显式抛新错误码 `ERR_SYNC_ENTITY_NAME_EXHAUSTED`（INV-ERROR-PARAM 合规）；同 (schema,table) 重同步收敛为 update |
| C2 | `MetaCatalogCollector` extras entityKind 改用新词表映射（Kind.ENTITY→PHYSICAL / SQL→SQL_VIEW / EXTERNAL→EXTERNAL） |
| C3 | 32 个变更文件重复 import 去重（main 16 + test 16，NopMetaModuleBizModel 含两处：NopMetaEntity/INopMetaEntityBiz 各一） |
| C4 | 死方法 `AggregationHelper.endpointTypeOf` 删除（全仓无调用方） |
| C5 | 死常量 `NopMetadataArgs.ARG_BASE_ENTITY_ID` 删除 |
| N2 | `NopMetaIndexBuilder` 移除 "NopMetaEntity"/"MetaEntity" 双别名（默认类型表 6→5、switch 合并、重复 import 删除）；对应测试计数 6→5 修正（TestNopMetaIndexBuilder 两处 + TestNopMetadataSearchIntegration 三处），消除双构建与 purge 失效 |
| N3 | 死方法 `resolveTableEndpointOrThrow` 删除；孤儿错误码 `ERR_JOIN_TABLE_DANGLING`/`ERR_JOIN_TABLE_TYPE_NOT_ALLOWED` 删除；`TestNopMetadataErrorsCentralized` 对应断言移除 |
| N4 | `NopMetaModuleBizModel` 死 @Inject 字段 tableBiz 删除 |
| N6 | `MetaEntityFieldResolver` 类 javadoc 按最终契约重写；测试改名两处（testResolveEntityFieldsFailsOnPhysicalWithoutFields / testResolveTableFieldsUnregisteredEntityNameFails）；`MetaEntityReferenceResolver` javadoc 于第 2 轮收尾补齐（第 1 轮修正遗漏，见 §四 第 2 轮 N6 PARTIAL → 已补） |
| N1 伴生 | `TestNopMetaBiSemanticBizModel` join 测试族处置：删除 testJoinSaveBothEndpointsSetFails / testJoinSaveTableEndpointEntityTypeFails（双端点概念失效，类内登记）；testJoinSaveTableEndpointFieldNotInTableFails 重写为纯实体端点 + EXTERNAL 端点 unresolvable-field 语义 |
| N2 补充 | TestNopMetaIndexBuilder 计数断言 6→5 共 12 行（跨多个测试方法）；TestNopMetadataSearchIntegration 3 行 |
| 新增（F4） | 补碰撞消解回归测试 `TestNopMetaEntityMultiSchemaUpsert.testEntityNameCollisionResolvedBySequenceSuffix`：构造 S1_X+Y 与 S1+X_Y 双碰撞场景，断言 _2 后缀行共存、dbSchema/tableName 各自正确、重同步不漂移（3/3 绿） |
| 收尾（F1/F2/F3/F5） | MetaEntityReferenceResolver javadoc 补齐 + FieldResolver @throws 措辞修正；误提交的临时文件 `_tmp_d.files`（及历史残留 `nop-ai/nop-ai-agent/_tmp-cp.txt`）从 git 移除；DataSourceErrors 缩进规范化；`ai-dev/logs/2026/09-17.md` 补写 |

### 登记为"记录不修正"的项

| # | 处置 |
|---|------|
| N5 | 存量库升级路径缺失——已在 `13-entity-unification.md` 补"无迁移路径"裁定注记（dev 阶段 2.0.0-SNAPSHOT 重建语义，计划 Deferred 首条一致） |
| N7 | SQL 视图实体进入 manifest——裁定为**预期行为**（SQL 视图实体属模块内容，manifest 应导出）；已在 `13-entity-unification.md` 补归属说明 |
| N9 | ARG_TABLE_TYPE/ERR_*_TABLE_TYPE 等命名噪音——接受（常量/错误码名为已发布契约标识符，改名收益低于 churn） |
| 测试局部变量名 leftTableId 等 | 表面性残留，登记不修正 |

## Open Questions

- [x] C1 消解策略：已按设计文档"追加序号 _2.._32"落地，耗尽显式抛 ERR_SYNC_ENTITY_NAME_EXHAUSTED（第 2 轮核验 PASS）
