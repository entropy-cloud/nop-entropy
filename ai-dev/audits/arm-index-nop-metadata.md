# 审计-修复报告索引（arm，nop-metadata）

> **M0.2 交付物** — mission `nop-metadata-audit-remediation` 的 M0.2 工作项（按 `ai-dev/skills/audit-remediation-roadmap-authoring-prompt.md` §6.1）。
> 状态：done
> 最后更新：2026-08-04
> 来源：9 个历史审计来源（07-19 ~ 07-23 五轮，multi+open 双轨）+ 本 mission 3 个 M0 交付物自身；本索引不登记 nop-ai 等其他 mission 的 arm 文件。

## 报告清单

| 报告 | 轮次/里程碑 | 维度 | 模块 | P0 数 | P1 数 | P2/P3 数 | 状态 |
|------|------------|------|------|-------|-------|----------|------|
| `ai-dev/audits/2026-07-19-1118-multi-audit-nop-metadata.md` | 07-19 multi | 01-21 | nop-metadata | 0 | 8 | 38 | 历史基线（已修复为主） |
| `ai-dev/audits/2026-07-19-1118-open-audit-nop-metadata.md` | 07-19 open | open-ended | nop-metadata | 2 | 5 | 7 | 历史基线（P0 已修复） |
| `ai-dev/audits/2026-07-20-1554-deep-audit-nop-metadata/`（23 文件，summary.md 为入口） | 07-20 deep | 01-21 + adversarial | nop-metadata | 0 | 2 | 32 | 历史基线（已修复为主） |
| `ai-dev/audits/2026-07-20-1816-multi-audit-nop-metadata/`（summary.md + 01/04/07） | 07-20 multi | 01/04/07 | nop-metadata | 0 | 4 | 84 | 历史基线（已修复为主） |
| `ai-dev/audits/2026-07-20-1816-open-audit-nop-metadata.md` | 07-20 open | open-ended | nop-metadata | 0 | 3 | 3 | 历史基线（已修复为主） |
| `ai-dev/audits/2026-07-21-2039-multi-audit-nop-metadata/`（8 维度文件） | 07-21 multi | 01/04/05/07/08/09/11/16 | nop-metadata | 1（11-01，已闭包） | 5 | 35 | 历史基线（已修复为主） |
| `ai-dev/audits/2026-07-21-2039-open-audit-nop-metadata.md` | 07-21 open | open-ended | nop-metadata | 0 | 0 | 7 | 历史基线（3 已修复、4 残余） |
| `ai-dev/audits/2026-07-23-0714-multi-audit-nop-metadata/`（7 维度 + summary.md） | 07-23 multi | 01/04/05/07/09/11/16 | nop-metadata | 0 | 1 | 35 | 历史基线（多数"待复核"→ M0.3 归集） |
| `ai-dev/audits/2026-07-23-0714-open-audit-nop-metadata.md` | 07-23 open | deployment + 状态复核 | nop-metadata | 0 | 1 | 11 | 历史基线（6 已修复、11 残余） |
| `ai-dev/audits/arm-audit-dimension-matrix-nop-metadata.md` | M0.1 | 维度矩阵 | nop-metadata | — | — | — | done（2026-08-04） |
| `ai-dev/audits/arm-unclosed-findings-nop-metadata.md` | M0.3 | 未闭包清单 | nop-metadata | 0 未闭包 | 3 | 已归集 | done（2026-08-04） |
| `ai-dev/audits/arm-index-nop-metadata.md` | M0.2 | 索引 | nop-metadata | — | — | — | done（2026-08-04） |
| `ai-dev/audits/2026-08-04-0900-arm-MA1.1-nop-metadata-dependency-graph.md` | MA1.1 | 01 依赖图与模块边界 | nop-metadata | 0 | 0 新增 | 3 P3 残留确认 | done（2026-08-04） |
| `ai-dev/audits/2026-08-04-0900-arm-MA1.2-nop-metadata-module-boundary.md` | MA1.2 | 02 模块职责与文件边界 | nop-metadata | 0 | 0 新增 | 1 P2 残留 + 3 P3 | done（2026-08-04） |
| `ai-dev/audits/2026-08-04-0900-arm-MA1.3-nop-metadata-api-contract.md` | MA1.3 | 03 API 表面积与契约一致性 | nop-metadata | 0 | 1 新增（P1-MA1-001，归 MR1） | 2 P2 确认 | done（2026-08-04） |
| `ai-dev/audits/2026-08-04-0900-arm-MA1.4-nop-metadata-delta.md` | MA1.4 | 06 Delta 定制合规性 | nop-metadata | 0 | 0 | 0 | done（2026-08-04） |
| `ai-dev/audits/2026-08-04-0935-arm-MA2.1-nop-metadata-orm-model.md` | MA2.1 | 04 ORM 模型与实体设计 | nop-metadata | 0 | 0 新增 | 2 P2 新增 + 12 P3（含 6 历史复核 open） | done（2026-08-04） |
| `ai-dev/audits/2026-08-04-0935-arm-MA2.2-nop-metadata-pipeline.md` | MA2.2 | 05 生成管线完整性 | nop-metadata | 0 | 0 | 2 P3 新增 + 1 P3 落实证据 | done（2026-08-04） |
| `ai-dev/audits/2026-08-04-0935-arm-MA2.3-nop-metadata-bizmodel.md` | MA2.3 | 07 BizModel 规范遵循 | nop-metadata | 0 | 0 新增 | 7 P3 新增 + 1 记录项 | done（2026-08-04） |
| `ai-dev/audits/2026-08-04-0935-arm-MA2.4-nop-metadata-ioc.md` | MA2.4 | 08 IoC 与 Bean 配置 | nop-metadata | 0 | 0 | 3 P3（1 stale javadoc + 2 INFO 记档） | done（2026-08-04） |
| `ai-dev/audits/2026-08-04-1136-arm-MA3.1-nop-metadata-xdsl.md` | MA3.1 | 10 XDSL 与 XLang 正确性（含 xbiz/xwf 盲区） | nop-metadata | 0 | 4 新增（归 MR2） | 5 P2 + 3 P3 | done（2026-08-04） |
| `ai-dev/audits/2026-08-04-1156-arm-MA3.2-nop-metadata-graphql.md` | MA3.2 | 12 GraphQL 与 API 层 | nop-metadata | 0 | 0 新增（MA1-001 复核确认归 MR1） | 1 P2 + 5 P3 + 5 项复核 | done（2026-08-04） |
| `ai-dev/audits/2026-08-04-1204-arm-MA3.3-nop-metadata-security.md` | MA3.3 | 13 安全与权限模型 | nop-metadata | 0 | 0 新增 | 1 P2 watch-only 维持 + 2 P3 + 2 复核关闭 | done（2026-08-04） |
| `ai-dev/audits/2026-08-04-1212-arm-MA3.4-nop-metadata-async-txn.md` | MA3.4 | 14 异步与事务模式 | nop-metadata | 0 | 0 | 1 P2 + 7 P3 + 2 项复核 | done（2026-08-04） |

> 后续 MA2-MA7 审计报告按 `YYYY-MM-DD-HHmm-arm-<milestone>-nop-metadata-<dimension>.md` 命名登记于此（roadmap 规则 2：产出即更新索引）。

## P0 发现追踪（即时通道）

| Finding ID | 报告 | 描述 | 修复路径 | 修复状态 |
|-----------|------|------|---------|---------|
| `2026-07-19-1118-open#AR-01` | 07-19 open | schemaPattern SQL 注入（3 执行器 6 judge） | 就地修复（`normalizeSchema` 补 `validateIdentifier`，07-19~07-21 间） | done（0721o 已验证） |
| `2026-07-19-1118-open#AR-02` | 07-19 open | JDBC URL/驱动无白名单（SSRF/RCE/DoS） | 就地修复（`MetaDataSourceConnectionProcessor` 协议白名单 + 危险参数黑名单 + 驱动白名单 + loginTimeout + 主机白名单） | done（0721o 已验证） |
| `2026-07-21-2039#维度11-01` | 07-21 multi | NopMetaSearch 无 xmeta（定级 P0） | 已补齐 `@BizModel("NopMetaSearch")` + `NopMetaSearch.xmeta`（service 资源，live 核实存在）；07-21-1200-2 B4 曾裁定 watch-only | done（xmeta 已存在） |

> 未闭包 P0：**0**。三个历史 P0 全部经 live 验证闭包（见 M0.3 清单）。

## P1 发现汇总（待 MR 批量修复 / 审计工作项复核）

> M0.3 归集回填（roadmap 规则 2"产出即更新索引"）。详见 `ai-dev/audits/arm-unclosed-findings-nop-metadata.md`。

| Finding ID | 报告 | 描述 | 归属 | 修复状态 |
|-----------|------|------|------|---------|
| `2026-07-19-1118#维度20-01` | 07-19 multi | `System.currentTimeMillis()` DDD-006 违规残余 2 处（`OrmModelImporter.java:58,68`） | MR2（机械修复，随 MA4.2/4.5 审计后） | open（live 核实） |
| `2026-07-20-1554#MISSING-AUTH` | 07-20 deep | 自定义 @BizMutation 缺细粒度 @Auth 注解 | watch-only（300 plan 裁定：action-auth 默认关闭 + 粗粒度兜底）；**MA3.3 复核（2026-08-04）：维持 watch-only**——全链路实证（ReflectionBizModelBuilder:330-336 兜底 + 双开关默认 false + app 未配置 + 4 个 action-auth 文件无增量），无升级证据；补充建议：MR2 为高危方法补 @Auth + 部署开启开关 | watch-only（已裁定，MA3.3 复核维持） |
| `2026-07-21-2039#维度16-01` | 07-21 multi | 19/40 BizModel 零测试（07 plan 已覆盖 5 个高风险，剩余 14 个） | watch-only（07/10 plan 裁定 out-of-scope）；MA4.4/4.7 审计复核 | watch-only（已裁定） |
| `P1-MA1-001` | MA1.3（2026-08-04） | NopMetaSearch.xmeta:7 schema type 引用不存在的 `io.nop.metadata.core.dto.SearchHitDTO`（DTO 已于 c3162d4da 迁至 api，xmeta 未同步；GraphQL `items` 字段类型解析失效） | **MR1**（一行 xmeta 修复 + GraphQL 字段选择回归测试）；**MA3.1/MA3.2 复核（2026-08-04）：确认仍 open，且文件位于 `/nop/metadata/NopMetaSearch/`（model/ 扫描根之外）实际不可达——MR1 修复必须同时移动文件位置（MA3.1-10/MA3.2 证据）** | open（待 MR1） |
| `P1-MA3-001` | MA3.1（2026-08-04） | 3 个 xwf 部署在 `/nop/metadata/wf/`（解析器 resolveInDir=`/nop/wf` 不可达）+ `wf-approval:notifyResult` 未 import xlib + quality 流 appState 非法属性——3 条审批流全部不可用（合并 MA3.1-01/02/03） | **MR2**（文件迁移 + x:config import + 属性删除） | open（live 核实，源码级验证） |
| `P1-MA3-002` | MA3.1（2026-08-04） | NopMetaDataContract Java `approve`/`reject` 被 approval-support XPL 遮蔽（BizObjectBuildHelper merge 优先级），状态生命周期 DRAFT→ACTIVE→DEPRECATED→RETIRED 经 GraphQL 不可达（MA3.1-07） | **MR2**（单一事实源裁定 + 正路径测试） | open（live 核实） |

> P1 未闭包数：**6**（其中 2 项为已裁定 watch-only residual（MISSING-AUTH/16-01），3 项 open 待 MR2（20-01/MA3-001/MA3-002），1 项 open 待 MR1（MA1-001）；MA3 复核完成 5 项历史登记：MISSING-AUTH 维持 / post-commit-SEMANTIC 维持 / RACE 初步复核（终局 MA6.6）/ 11-04 open 证据更新 / 07-03 open 确认）。

## P2 发现汇总（待 MA 审计复核 / MR 批量修复）

> 见 `arm-unclosed-findings-nop-metadata.md` §未闭包 P2/P3 登记区；本表为 P2 级未闭包索引（P3 不逐条索引，见清单尾部汇总）。

| Finding ID | 描述 | 归属 |
|-----------|------|------|
| `2026-07-21-2039-open#AR-25` | 血缘抽取 N+1 upsert | 已裁定 optimization candidate；MA7.4 复核后按需 MR3 |
| `2026-07-23-0714#维度07-003` | getEntityById 替代 requireEntity 残余（~10 处，DataSource/DataContract 已修复） | MR2（MA2.3 审计后）——MA2.3 复核：16 处/11 文件（B 类跨实体 6 处 + C 类 save 校验 10 处），含 MA2.3 P3-MA2-01 collectCatalogForTable:328 残留 |
| `2026-07-23-0714#维度07-004` | DTO 内 `List<Map<String,Object>>` 未类型化 | MR2（MA1.3 审计后） |
| `2026-07-23-0714#维度09-02/03/06` | 静默吞异常（MetaTableProfiler/MetaQualityRuleExecutor/TagLabelBizModel 等 5 处） | MR2（MA4.1 审计后） |
| `2026-07-23-0714#维度09-07` | ErrorCode hyphen 分隔符约定 | watch-only（NopMetadataErrors.java:22 有意裁定）；MA4.1 复核 |
| `2026-07-23-0714#维度11-04` | computeQualityScore 绕过 xmeta 验证 | MR2（MA3.2 审计后）——**MA3.2 复核（2026-08-04）：OPEN，证据指针更新**——computeQualityScore 本身已修复（802cf2361 经 doSave 管线），绕过本质存活于 `QualityResultWriter.java:50` `resultDao.saveEntity(row)`（结果表路径，规则+检查点双入口）；维持 MR2 |
| `2026-07-23-0714#维度16-01` | AutoTest 快照覆盖偏低（5/97 文件） | MA4.3 审计 + MR2 |
| `2026-07-23-0714#维度16-03/05/07/09` | 测试质量项（重复 CRUD/并发测试/sleep/data-auth 测试） | MA4.6/4.7 审计 + MR2 |
| `2026-07-21-2039#维度07-03` | queryAggregation 11 参数未用 @RequestBean | MR2（MA1.3 审计后）——**MA3.2 复核（2026-08-04）：OPEN 确认**（11 参签名被 TestNopMetaBizInterfaceCompleteness:54 钉死，无 DTO）；维持 MR2 |
| `2026-07-23-0714#维度05-08` | CRUD codegen 有意禁用 | watch-only（有意设计）；MA2.2 复核 | MA2.2 复核完成：维持 watch-only（有意设计），CRUD 契约由手工 INopMeta*Biz 接口承担 |
| `2026-07-19-1118#维度02-01`（残余） | `*Service` 命名违规残留 2 处（NopMetaSearchService / QualityAlertWorkflowService；另 2 处已改 Processor） | MR2（命名批量修复，随 MA4.5/MA5.3 审计后；MA1.2 复核确认） |
| `2026-07-21-2039#维度07-03` | queryAggregation 11 参数未用 @RequestBean | MR2（MA1.3 审计后——本审计已复核确认仍 open） |
| `P2-MA2-01`（MA2.1 新增） | NopMetaTagLabel.tag/glossaryTerm 的 refPropName="tagLabels" 反向集合缺失（NopMetaTag/NopMetaGlossaryTerm 无 tagLabels to-many） | MR1/MR2 裁决（model-first：改 orm.xml + codegen） |
| `P2-MA2-02`（MA2.1 新增） | NopMetaDataProduct.businessDomain 的 refPropName="dataProducts" 反向集合缺失（NopMetaBusinessDomain 无 dataProducts to-many） | MR1/MR2 裁决（model-first） |
| `P2-MA2-03`（MA2.1 复核历史 07-20-1554#维度04-02 仍 open） | SQL 保留字 PRIMARY/CONSTRAINT 用作列 code（NopMetaEntityUniqueKey:747 / NopMetaEntityRelation:920） | MR1/MR2 裁决（model-first） |
| `P2-MA3-01`（MA3.1 新增） | NopMetaSearch.xmeta 位于 model/ 扫描根之外实际不可达（P1-MA1-001 修复前置条件 + 假 javadoc） | MR2（放置/javadoc；类型修复本身归 MR1/MA1-001） |
| `P2-MA3-02`（MA3.2 新增） | entity 路径数据查询绕过 data-auth 过滤合并（queryTableData/queryJoinData/queryAggregation 裸 DAO/EQL，对比 CrudBizModel.java:381） | MR2 |
| `P2-MA3-03`（MA3.4 新增） | upsertExternalTable schema 维度未进 DB UK——多 schema 同名表功能与模型冲突（RACE 复核新增，非并发也必然 UK 冲突） | MR2（需 ORM 模型变更，plan-first） |
| `2026-07-20-1554#RACE` | upsertExternalTable 读-写竞态 | **MA3.4 初步复核（2026-08-04）：UK_NOP_META_TABLE_MODULE_NAME 已阻止重复（07-19 a8eefeecb），并发败者无 catch-duplicate+re-read 非幂等；新增 P2-MA3-03；终局定论归 MA6.6** |
| `2026-07-20-1554#post-commit-SEMANTIC` | dispatchActions "post-commit" 语义 = runWithoutTransaction 同步执行 | watch-only（javadoc 已显式文档化隔离语义，设计有意）；**MA3.4 复核（2026-08-04）：维持 watch-only**——BizModel javadoc:354-364 准确且与实现一致；残留 P3 MA3.4-02（dispatcher javadoc 仍写 onAfterCommit，归 MR1 纯注释） |
