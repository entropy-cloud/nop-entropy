# 未闭包发现清单（nop-metadata）

> **M0.3 交付物** — mission `nop-metadata-audit-remediation` 的 M0.3 工作项（按 `ai-dev/skills/audit-remediation-roadmap-authoring-prompt.md` 步骤 2）。
> 状态：done
> 最后更新：2026-08-04
> 遍历范围：9 个历史审计来源（07-19 / 07-20×2 / 07-21×2 / 07-23×2，multi+open 双轨 + 07-20 deep 目录）+ 全部 nop-metadata 相关已 completed 修复 plan（`ls ai-dev/plans/ | grep -i "nop-metadata"` 全量 86 份，逐份核对 `Deferred But Adjudicated` / `Closure` 段）。
> **Finding ID 轮次限定约定**（本清单强制）：`<YYYY-MM-DD-HHmm>#<来源内编号>`（如 `2026-07-19-1118#维度01-01`），杜绝跨轮裸编号冲突；roadmap 后续新发现使用 `P<级别>-<里程碑>-<序号>`。

## 汇总统计（2026-08-04 live repo 核对）

| 指标 | 数量 |
|------|------|
| 未闭包 P0 | **0**（历史 3 个 P0 全部 live 验证闭包，见 §P0 即时通道裁定） |
| 未闭包 P1 | **3**（1 项 live 确认 open 待 MR 修复；2 项已裁定 watch-only residual） |
| 未闭包 P2 | 16（已归集登记，标注归属；部分已裁定 watch-only/optimization） |
| 未闭包 P3 | 15（deferred successor，roadmap 规则 1 不处理） |
| 覆盖审计来源 | 9/9（逐一核对，无遗漏） |
| 修复 plan 遍历 | 86/86（核对记录见 §遍历清单） |

## 未闭包 P0/P1 汇总表（含归属）

| 轮次限定 ID | 标题 | 严重性 | 状态（2026-08-04） | 归属 |
|------------|------|--------|-------------------|------|
| `2026-07-19-1118#维度20-01` | `System.currentTimeMillis()` 违反 DDD-006 锚点，残余 2 处（`nop-metadata-dao/.../OrmModelImporter.java:58,68`） | P1 | **open**（live 核实，主代码 10 处已修 8 处） | MR2 机械修复（随 MA4.2/4.5 审计后）；MA6.6 复核 |
| `2026-07-20-1554#MISSING-AUTH` | 自定义 @BizMutation 缺少细粒度 @Auth 注解 | P1 | watch-only（plan 300 裁定：action-auth 默认关闭 + 粗粒度 mutation 权限兜底；产品策略待定） | MA3.3 审计复核；产品策略决策后起 successor |
| `2026-07-21-2039#维度16-01` | 19/40 BizModel 零测试（07 plan 已补 5 个高风险；剩余 14 个 CRUD-only BizModel） | P1 | watch-only（plan 07/10 裁定 out-of-scope improvement，CRUD-only 风险由 DAO 层测试 + 框架继承覆盖兜底） | MA4.4/MA4.7 审计复核 |

> 说明：roadmap 规则 1 只处理 P0/P1。P1 中 2 项为已裁定 watch-only residual（有明确 `Why Not Blocking` 记录），1 项 live 确认 open 且为机械修复（MR2 归属）。

## P0 即时通道裁定

| 历史 P0 | 修复路径 | 状态 |
|---------|---------|------|
| `2026-07-19-1118-open#AR-01`（schemaPattern SQL 注入） | 就地修复（3 个执行器 `normalizeSchema` 补 `validateIdentifier`） | **已闭包**（0721o 逐文件验证：MetaTableProfiler:557-565 / MetaQualityRuleExecutor:670-678 / MetaCatalogCollector:164-172） |
| `2026-07-19-1118-open#AR-02`（JDBC URL/驱动无白名单 SSRF/RCE/DoS） | 就地修复（`MetaDataSourceConnectionProcessor`：协议白名单 line 59、危险参数黑名单 line 63、驱动白名单 line 79、loginTimeout line 86、主机白名单 line 109） | **已闭包**（0721o 验证） |
| `2026-07-21-2039#维度11-01`（NopMetaSearch 无 xmeta，定级 P0） | 就地修复（`@BizModel("NopMetaSearch")` 已加；`NopMetaSearch.xmeta` 已存在于 service 资源，live 核实；07-21-1200-2 B4 曾裁定 watch-only，实际已补齐） | **已闭包**（live 核实） |

> 未闭包 P0 = 0，无需异步注入修复 plan。若后续 MA 审计发现新 P0，按步骤 3 规范注入 `ai-dev/plans/YYYY-MM-DD-HHmm-arm-fix-<finding-id>.md`。

## 未闭包 P2 登记区（deferred successor / residual）

| 轮次限定 ID | 标题 | 状态（2026-08-04） | 归属 / 后继 |
|------------|------|-------------------|------------|
| `2026-07-21-2039-open#AR-25`（= `2026-07-19-1118-open#AR-10`） | 血缘抽取 N+1 upsert（3 个 upsert 方法逐候选 SELECT+INSERT） | open；已裁定 optimization candidate（07-23-0900-2：不产生错误结果，规模>1000 边才需优化） | MA7.4 复核后按需 MR3 |
| `2026-07-23-0714#维度07-003`（= `2026-07-23-0714-open#07-003`） | `dao().getEntityById()` 替代 `requireEntity()`（DataSource/DataContract 已修复；NopMetaTable/Module/QualityRule/ReconciliationConfig ~10 处残余） | open（live 核实 DataSource/DataContract 已用 requireEntity） | MR2（MA2.3 审计后批量修复） |
| `2026-07-23-0714#维度07-004` | DTO 内 `List<Map<String,Object>>` 未类型化（QueryJoinDataResultDTO/AggregationResultDTO.items） | open | MR2（MA1.3 审计后） |
| `2026-07-23-0714#维度09-02/09-03/09-06`（= `2026-07-21-2039#维度09-01/02/03` + `2026-07-19-1118#维度09-09`） | 静默吞异常（MetaTableProfiler:485、MetaQualityRuleExecutor:599,606、MetaDataSourceConnectionProcessor:285、CheckpointActionDispatcher:323、AggregationContext.safeProductName、TagLabelBizModel.getWfNameFromMeta） | open | MR2（MA4.1 审计后统一补 LOG.warn） |
| `2026-07-23-0714#维度09-07` | ErrorCode 子域分隔符 hyphen（`nop.err.metadata.aggr-no-measure`）非点号 | watch-only（NopMetadataErrors.java:22 显式文档化有意选择） | MA4.1 复核；跨模块工具需要时再迁移 |
| `2026-07-23-0714#维度11-04` | computeQualityScore 经 `dao().saveEntity()` 绕过 xmeta insertable 验证 | open（未复核） | MR2（MA3.2 审计后） |
| `2026-07-23-0714#维度16-01`（= `维度16-F2`） | AutoTest 快照覆盖偏低：5/97 测试文件（live 核实 `_cases/` 现有 5 个 AutoTest 类） | open（已从 1 增至 5） | MA4.3 审计 + MR2 增量 |
| `2026-07-23-0714#维度16-03` | 多实体重复 CRUD 测试反模式 | open | MA4.6/4.7 审计 + MR2 |
| `2026-07-23-0714#维度16-04` | 4 个接口方法无测试（judgeByRuleId/activateContract/deprecateContract/retireContract） | open | MA4.6 审计 + MR2 |
| `2026-07-23-0714#维度16-05` | 并发测试无共享状态验证（TestCheckpointActionDispatcherConcurrency） | open | MA6.5 审计 + MR2 |
| `2026-07-23-0714#维度16-07` | data-auth 测试只验证 XML 结构不验证框架强制 | open（注释声明有意限制） | MA6.6 审计 + MR2 |
| `2026-07-23-0714#维度16-09` | TestNopMetaQualityRuleBizModel Thread.sleep(1100ms) | open | MA4.6 审计 + MR2 |
| `2026-07-21-2039#维度07-03`（= `2026-07-20-1816-multi#维度07-03`） | queryAggregation 11 参数未用 @RequestBean（live 核实签名仍 11 参数） | open | MR2（MA1.3 审计后） |
| `2026-07-23-0714#维度05-08` | CRUD API 代码生成有意禁用（gen-crud-api.xgen 注释） | watch-only（有意设计；所有 BizModel 手写） | MA2.2 复核 |
| `2026-07-20-1554#post-commit-SEMANTIC` | dispatchActions "post-commit" 语义 = runWithoutTransaction 同步执行 | watch-only（javadoc 已显式文档化隔离语义，设计有意） | MA3.4 审计复核 |
| `2026-07-20-1554#RACE` | upsertExternalTable 读-写竞态 | 待复核（相关唯一键已补：UK 体系 35+ 键） | MA6.6 复核后定论 |

## 未闭包 P3 汇总（deferred successor，不处理）

| 轮次限定 ID | 标题 | 备注 |
|------------|------|------|
| `2026-07-23-0714-open#AR-28` + `2026-07-20-1816-open#NF-03` | 空接口 NopMetadataConstants / NopMetadataConfigs（live 核实仍存在） | 2 分钟机械删除；低优先 |
| `2026-07-23-0714-open#AR-38` | 空 `_dao.beans.xml` 被 import（live 核实存在） | 同上 |
| `2026-07-21-2039-open#AR-29` | NopMetaSearchService 吞异常（WARN 级） | 搜索降级设计；可加 drift 检测 |
| `2026-07-21-2039-open#AR-30` | TableReferenceExecutor RuntimeException pass-through | ErrorCode 体系外传播 |
| `2026-07-23-0714-open#AR-40` | AggregationContext 集合字段默认 null | 新调用路径 NPE 隐患 |
| `2026-07-21-2039#维度05-01` | gen-orm.xgen 缺 orm-model 第 3 步（live 核实仍 2 步） | 对齐标准管线 |
| `2026-07-23-0714#维度04-004/05/06/07` | extConfig stdDomain 缺失 / 3 字典未引用 / dict value 大小写风格不一 / Module 自引用级联缺注释 | ORM 维护项 |
| `2026-07-23-0714#维度11-02/03` | 重复常量 STATUS_MANUAL / 38/42 空 retention xmeta | 11-03 已裁定 watch-only（15 plan） |
| `2026-07-19-1118#维度15-03` | `@SuppressWarnings("unchecked")` 50+ 处 | 07-22-1500-2 已裁定边界保留（≤25 目标外剩余） |
| `2026-07-19-1118#维度02-03/02-04` | TableReference 类名冲突 / OrmModelImporter 位置 | 待复核 |
| `2026-07-19-1118#维度04-08/09/10/11` | delFlag 未启用 / DEL_VERSION 列名 / DictItem isDelta / deleteVersionProp 命名 | ORM 维护项 |
| `2026-07-19-1118#维度12-01` | FieldSelectionBean 未完全下推 | queryAggregation 已加；queryTableData/queryJoinData 待复核 |
| `2026-07-23-0714#维度16-02/06/08/10` | 纯 getter/setter 测试 / assertNotNull 前置 / 聚合测试无快照 / 大测试文件 | 测试维护项 |
| `2026-07-21-2039#维度01-03` | dao test-scope codegen 依赖但无测试代码 | 依赖清理项 |
| `2026-07-23-0714#维度07-04` | AutoClassificationService / LineageTagPropagationService 命名 | MA5.3 复核 |

## 前序计划 deferred 项登记（M0.3 登记状态，不重新触发）

| deferred 项 | 来源 plan | 触发条件判定 | Why Not Blocking |
|------------|-----------|-------------|------------------|
| UK_NOP_META_DS_QUERY_SPACE 重命名（DS→DATASOURCE） | `2026-07-19-1250-2`（已 completed） | 触发条件未满足——已裁定默认不重命名；runtime 多匹配检测 + UK 已存在 | 重命名破坏既有约束名引用；`resolveActiveOrThrow` 多匹配已检测；watch-only residual |
| JOIN 上下文 measure 血缘（`l.`/`r.` 限定列） | `2026-07-18-1800-1`（已 completed） | 未到触发条件——等待用户反馈 JOIN 上下文 measure 血缘需求 | 当前进 unresolved（标 `join-context-deferred`）不静默丢弃不伪造；Successor Required: yes（用户反馈时新建 plan） |
| ErrorCode hyphen→dot rename | `15-nop-metadata-test-and-code-quality.md`（已 completed） | 未到触发条件——NopMetadataErrors.java:22 已文档化 hyphen 为有意选择 | 纯命名约定无功能影响；watch-only residual；Successor Required: no |
| 38/42 空 retention xmeta 覆盖 | `15-nop-metadata-test-and-code-quality.md`（已 completed） | 未到触发条件——空 `<props/>` 表示全部继承生成默认 | 无功能回归；security-hardening 项非 live defect；watch-only residual；Successor Required: no |
| DTO 迁移模块依赖重构（307 Phase 1 blocked） | `307-nop-metadata-dto-migration-data-auth.md`（header completed，Phase 1 blocked） | **触发条件已满足**——`311-nop-metadata-dto-module-restructure.md` 已实施（DTO 移入 `nop-metadata-api`，11 个高频方法已迁移，live 核实：queryTableData→QueryTableDataResultDTO / queryAggregation→AggregationResultDTO / testConnection→TestConnectionResultDTO 等） | 307 的 blocked 项由 311 承接并已闭包；残余（非高频 BizModel Map 返回 + DTO 内 List\<Map\>）在 P2 登记区（07-004） |

## 遍历清单（traversal manifest，可追溯）

### 9 个历史审计来源（逐一核对，无遗漏）

| # | 来源 | 轮次 | finding 数（初审） | 未闭包 P0/P1（本清单） | 核对方式 |
|---|------|------|-------------------|----------------------|---------|
| 1 | `2026-07-19-1118-multi-audit-nop-metadata.md` | 07-19 multi | 46 | 1（维度20-01） | 全文精读 + live 抽查 |
| 2 | `2026-07-19-1118-open-audit-nop-metadata.md` | 07-19 open | 14 | 0（AR-10 N+1 归 P2） | 全文精读 + live 抽查 |
| 3 | `2026-07-20-1554-deep-audit-nop-metadata/`（summary + 23 文件） | 07-20 deep | 34 唯一 | 1（MISSING-AUTH，watch-only） | summary 精读 + 代表性文件 |
| 4 | `2026-07-20-1816-multi-audit-nop-metadata/`（summary + 01/04/07） | 07-20 multi | 88 | 0（16-F1/16-F2 归 07-23 16-01/16-03 家族） | summary + 3 维度文件全文 |
| 5 | `2026-07-20-1816-open-audit-nop-metadata.md` | 07-20 open | 6 | 0 | 全文精读 + live 抽查 |
| 6 | `2026-07-21-2039-multi-audit-nop-metadata/`（8 维度文件） | 07-21 multi | 41 | 1（维度16-01，watch-only） | 8 文件全文 |
| 7 | `2026-07-21-2039-open-audit-nop-metadata.md` | 07-21 open | 7 | 0（AR-25 N+1 归 P2） | 全文精读 |
| 8 | `2026-07-23-0714-multi-audit-nop-metadata/`（summary + 7 维度文件） | 07-23 multi | 36 | 0（全部 P2/P3；P1 维度01-01 已修复） | 全部文件全文 |
| 9 | `2026-07-23-0714-open-audit-nop-metadata.md` | 07-23 open | 11+2 新 | 0 | 全文精读 + live 抽查 |

### 修复 plan 遍历（86/86）

- 命令：`ls ai-dev/plans/ | grep -i "nop-metadata"` → 86 份（04-18 编号 16 份 + 292-313 共 13 份 + 2026-07-16~07-23 各阶段 57 份；同目录 nop-stream/nop-ai 等 mission 的 plan 按文件名 `*nop-metadata*` + 内容 Mission/标题过滤后排除）
- 核对内容：每份 plan 的 `Deferred But Adjudicated` 段（逐条记录 Classification / Why Not Blocking / Successor）与 `Closure` 段（Status Note / Follow-up）
- 重点核对：`307-nop-metadata-dto-migration-data-auth.md`（Phase 1 blocked 残余 → 已由 311 承接闭包）、`2026-07-21-1200-1-nop-metadata-p1-runtime-defects.md`（无 deferred 项，全部 P1 Fix）
- 核对结果：**deferred 项 0 条为 in-scope live defect 降级**；全部符合 allowed deferred classifications（optimization candidate / watch-only residual / out-of-scope improvement）或已由后继 plan 承接；详见表 §前序计划 deferred 项登记 与 §未闭包 P2/P3
- 排除项：`ai-dev/plans/2026-07-31-1446-2/3` 属 nop-ai mission（Mission: audit-remediation），不纳入

## 回填说明（arm-index）

- 已按 roadmap 规则 2（"产出即更新索引"）将未闭包 P0/P1/P2 摘要回填至 `ai-dev/audits/arm-index-nop-metadata.md` 的 P0 发现追踪表 / P1 发现汇总表 / P2 发现汇总表（见该文件 §P0 / §P1 / §P2，与本节一致）
- 文档变化：`No owner-doc update required`（audits 为证据层非规范性文档；MA5 里程碑负责 docs-for-ai 审计）
- `ai-dev/logs/2026/08-04.md` 已更新（见 Phase 4 收口记录）
