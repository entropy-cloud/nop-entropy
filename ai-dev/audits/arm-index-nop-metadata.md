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
| `ai-dev/audits/2026-08-04-1234-arm-MA4.1-nop-metadata-error-handling.md` | MA4.1 | 09 错误处理与错误码 | nop-metadata | 0 | 0 | 2 P2 + 4 P3 + 7 项历史复核 | done（2026-08-04） |
| `ai-dev/audits/2026-08-04-1347-arm-MA4.2-nop-metadata-typesafety.md` | MA4.2 | 15 类型安全与泛型使用 | nop-metadata | 0 | 0 | 1 P2 + 4 P3 + 2 项复核（15-03 维持/07-004 维持 MR2） | done（2026-08-04） |
| `ai-dev/audits/2026-08-04-1355-arm-MA4.3-nop-metadata-test-coverage-core.md` | MA4.3 | 16 测试覆盖与质量-核心执行域 | nop-metadata | 0 | 0 | 2 P2 + 4 P3 + 16-01 复核维持 open | done（2026-08-04） |
| `ai-dev/audits/2026-08-04-1355-arm-MA4.4-nop-metadata-test-coverage-rest.md` | MA4.4 | 16 测试覆盖与质量-其余域 | nop-metadata | 0 | 1（P1-MA4-401 judgeByRuleId 空洞测试） | 3 P3 + 2 项复核（16-01 维持 watch-only/16-04 存在性闭包） | done（2026-08-04） |
| `ai-dev/audits/2026-08-04-1405-arm-MA4.5-nop-metadata-style.md` | MA4.5 | 17 代码风格与规范 | nop-metadata | 0 | 0 | 1 P2（*Service 命名复发）+ 9 P3 + 02-01 复核 | done（2026-08-04） |
| `ai-dev/audits/2026-08-04-1415-arm-MA4.6-nop-metadata-test-effectiveness-core.md` | MA4.6 | 21 单元测试有效性-核心域 | nop-metadata | 0 | 1（P1-MA4-601 18 个空壳测试） | 2 P2 + 4 P3 + 16-03 核心域部分关闭 | done（2026-08-04） |
| `ai-dev/audits/2026-08-04-1415-arm-MA4.7-nop-metadata-test-effectiveness-rest.md` | MA4.7 | 21 单元测试有效性-其余域 | nop-metadata | 0 | 1（P1-MA4-701 同 401） | 2 P2 + 3 P3 + 16-09 修复确认/16-04 质量维持 open | done（2026-08-04） |
| `ai-dev/audits/2026-08-04-1600-arm-MA5.1-nop-metadata-design-drift.md` | MA5.1 | 设计文档-代码 drift（17 篇） | nop-metadata | 0 | 0 | 19 P2 + 35 P3 + 3 watch-only（全部归 MR3） | done（2026-08-04） |
| `ai-dev/audits/2026-08-04-1605-arm-MA5.2-nop-metadata-doc-consistency.md` | MA5.2 | 18 docs-for-ai 一致性 | nop-metadata | 0 | 0 | 5 P2 + 1 P3（归 MR3） | done（2026-08-04） |
| `ai-dev/audits/2026-08-04-1610-arm-MA5.3-nop-metadata-naming.md` | MA5.3 | 19 命名与术语一致性 | nop-metadata | 0 | 0 | 1 P2（P2-MA5-301 dataSource 双拼写，MR2 plan-first）+ 6 P3 | done（2026-08-04） |
| `ai-dev/audits/2026-08-04-1615-arm-MA5.4-nop-metadata-cross-module.md` | MA5.4 | 跨模块契约一致性 | nop-metadata | 0 | 0 新增（P1-MA3-001/002 交叉核对确认归 MR2） | 1 P2 新增（P2-MA5-401）+ 2 P3 | done（2026-08-04） |
| `ai-dev/audits/2026-08-04-1748-arm-MA6.1-nop-metadata-hollow-scan.md` | MA6.1 | H01 空壳实现扫描 | nop-metadata | 0 | 1 新增（MA6.1-002 = P1-MA4-601 家族登记外实例；登记修正 6 类 18 → 7 类 21 个） | 2 P2（MA6.1-001 死 DTO / MA6.1-003 = P2-MA4-301 家族第 4 个）+ 1 P3（MA6.1-004） | done（2026-08-04） |
| `ai-dev/audits/2026-08-04-1748-arm-MA6.2-nop-metadata-silent-noop.md` | MA6.2 | H02 静默跳过检测 | nop-metadata | 0 | 0 新增 | 1 P2（MA6.2-002 MetaTableProfiler 空串统计失真）+ 6 P3（MA6.2-001/003..007）；已登记 7 项全部确认仍 open | done（2026-08-04） |
| `ai-dev/audits/2026-08-04-1530-arm-MA6.3-nop-metadata-wiring.md` | MA6.3 | H03 接线完整性验证 | nop-metadata | 0 | 0 新增 | 0 新增（5 调用链全 PASS；3 watch-only 均为已知项复核） | done（2026-08-04） |
| `ai-dev/audits/2026-08-04-1748-arm-MA6.4-nop-metadata-sensitive-leak.md` | MA6.4 | H05 敏感信息泄露扫描 | nop-metadata | 0 | 0 新增 | 0 P2（4 P3 + 2 informational；脱敏链无回归、硬编码凭据 0 命中） | done（2026-08-04） |
| `ai-dev/audits/2026-08-04-1748-arm-MA6.5-nop-metadata-test-isolation.md` | MA6.5 | H06 测试隔离性审查 | nop-metadata | 0 | 0 新增 | 2 P2（MA6.5-001 跨类共享 H2 库 / MA6.5-004 时钟 finally 缺失）+ 5 P3；16-05 复核真测试但浅（P3） | done（2026-08-04） |
| `ai-dev/audits/2026-08-04-1748-arm-MA6.6-nop-metadata-fix-verification.md` | MA6.6 | H07 既有修复验证 | nop-metadata | 0 | 0 新增（抽样 11 项 10 PASS + 20-01 open 按计划） | 1 P2 新增（MA6.6-001 deploy/sql DDL 零 UK 发射）+ RACE 终局维持 watch-only + 16-07 维持 watch-only | done（2026-08-04） |

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
| `P1-MA1-001` | MA1.3（2026-08-04） | NopMetaSearch.xmeta:7 schema type 引用不存在的 `io.nop.metadata.core.dto.SearchHitDTO`（DTO 已于 c3162d4da 迁至 api，xmeta 未同步；GraphQL `items` 字段类型解析失效） | **MR1**（一行 xmeta 修复 + GraphQL 字段选择回归测试）；**MA3.1/MA3.2 复核（2026-08-04）：确认仍 open，且文件位于 `/nop/metadata/NopMetaSearch/`（model/ 扫描根之外）实际不可达——MR1 修复必须同时移动文件位置（MA3.1-10/MA3.2 证据）** | fixed（plan-2026-08-04-1004-3 R1.1：xmeta 一行修复 + TestNopMetaSearchGraphQLSchema 回归测试（schema 类型解析 + 真实引擎 e2e）；接线验证另发现并修复 topic 非法缺陷（nop_meta_metadata）；xmeta 位置不可达问题归 P2-MA3-01/MR2 未移动文件） |
| `P1-MA3-001` | MA3.1（2026-08-04） | 3 个 xwf 部署在 `/nop/metadata/wf/`（解析器 resolveInDir=`/nop/wf` 不可达）+ `wf-approval:notifyResult` 未 import xlib + quality 流 appState 非法属性——3 条审批流全部不可用（合并 MA3.1-01/02/03） | **MR2**（文件迁移 + x:config import + 属性删除） | open（live 核实，源码级验证） |
| `P1-MA3-002` | MA3.1（2026-08-04） | NopMetaDataContract Java `approve`/`reject` 被 approval-support XPL 遮蔽（BizObjectBuildHelper merge 优先级），状态生命周期 DRAFT→ACTIVE→DEPRECATED→RETIRED 经 GraphQL 不可达（MA3.1-07） | **MR2**（单一事实源裁定 + 正路径测试） | open（live 核实） |
| `P1-MA4-401`（= `P1-MA4-701`） | MA4.4/MA4.7（2026-08-04） | judgeByRuleId 测试为空洞断言（`assertNotNull(resp)`，ruleId 用不存在的 `__not_exist__`）：核心逻辑改成抛异常或恒返回 FAIL 测试仍通过；16-04 质量部分维持 open（存在性已闭包——activateContract/deprecateContract/retireContract live 零命中已移除） | **MR2**（重写为行为断言：status 语义 + 错误码） | open（live 核实） |
| `P1-MA4-601` | MA4.6（2026-08-04） | 6 个 AggregationProcessor 测试类 18 个空壳测试（instanceof/canInstantiate/NPE-on-null），execute() 改错后全部仍通过——JOIN 分派/执行逻辑单元层零保护 | **MR2**（最小行为测试改造）——**MA6.1 复核（2026-08-04）：登记修正——实际 7 个测试类 21 个空壳测试**（第 7 类 TestCrossDbInMemoryAggregationProcessor.java:16-31 的 3 个未登记，MA6.1-002 并入家族） | open（live 核实） |

> P1 未闭包数：**9**（其中 2 项为已裁定 watch-only residual（MISSING-AUTH/16-01），6 项 open 待 MR（20-01/MA3-001/MA3-002/MA1-001/MA4-401/MA4-601）；MA4 复核完成 4 项历史登记：16-01 收敛 19→8 维持 watch-only、16-04 存在性闭包（质量转 P1-MA4-401）、16-09 已修复（无 sleep）、02-01 残余 2 处确认 MR2）。
>
> MA5 复核（2026-08-04）：0 新 P1（4 份报告 0 P1）；P1-MA3-001/002（xwf 不可达 + approve/reject 遮蔽）经 MA5.4 机制级交叉核对确认仍 open（归 MR2 不变）；MR1 Follow-up 两项 core.dto 陈旧引用确认归 MR3。
>
> MA6 复核（2026-08-04）：P1-MA4-601 登记修正 6 类/18 → 7 类/21 个（MA6.1-002，第 7 类 3 个空壳并入家族，归 MR2 不变）；0 新独立 P1 家族。

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
| `P2-MA2-01`（MA2.1 新增） | NopMetaTagLabel.tag/glossaryTerm 的 refPropName="tagLabels" 反向集合缺失（NopMetaTag/NopMetaGlossaryTerm 无 tagLabels to-many） | MR1 裁决：**fixed**（plan-2026-08-04-1004-3 R1.2：NopMetaTag/NopMetaGlossaryTerm 显式 tagLabels to-many，cascadeDelete=true + displayName/i18n；TestNopMetaTagLabelReverseNavigation DB-backed 反向导航 + 级联删除 + 模型声明测试） |
| `P2-MA2-02`（MA2.1 新增） | NopMetaDataProduct.businessDomain 的 refPropName="dataProducts" 反向集合缺失（NopMetaBusinessDomain 无 dataProducts to-many） | MR1 裁决：**fixed**（plan-2026-08-04-1004-3 R1.2：NopMetaBusinessDomain 显式 dataProducts to-many，cascadeDelete=true；TestNopMetaBusinessDomainDataProductCrud 增补反向导航 + 级联删除 + 模型声明测试） |
| `P2-MA2-03`（MA2.1 复核历史 07-20-1554#维度04-02 仍 open） | SQL 保留字 PRIMARY/CONSTRAINT 用作列 code（**实体归属修正：PRIMARY 在 NopMetaEntityField:747（primaryField 列）、CONSTRAINT 在 NopMetaEntityUniqueKey:920（constraintName 列）**；裁决依据以 Oracle DDL 未引号事实为准——Oracle `PRIMARY SMALLINT default 0` / `CONSTRAINT VARCHAR2(100)` 为语法错误，MySQL 反引号可解析） | MR1 裁决：**fixed**（plan-2026-08-04-1004-3 R1.3：code 改名 PRIMARY→IS_PRIMARY / CONSTRAINT→CONSTRAINT_NAME，Java name 不变无契约影响；deploy/sql 三方言 DDL 再生成验证 Oracle 无裸保留字；TestNopMetaReservedWordColumns 断言映射列名） |
| `P2-MA3-01`（MA3.1 新增） | NopMetaSearch.xmeta 位于 model/ 扫描根之外实际不可达（P1-MA1-001 修复前置条件 + 假 javadoc） | MR2（放置/javadoc；类型修复本身归 MR1/MA1-001） |
| `P2-MA3-02`（MA3.2 新增） | entity 路径数据查询绕过 data-auth 过滤合并（queryTableData/queryJoinData/queryAggregation 裸 DAO/EQL，对比 CrudBizModel.java:381） | MR2 |
| `P2-MA3-03`（MA3.4 新增） | upsertExternalTable schema 维度未进 DB UK——多 schema 同名表功能与模型冲突（RACE 复核新增，非并发也必然 UK 冲突） | MR2（需 ORM 模型变更，plan-first） |
| `2026-07-20-1554#RACE` | upsertExternalTable 读-写竞态 | **MA3.4 初步复核（2026-08-04）：UK_NOP_META_TABLE_MODULE_NAME 已阻止重复（07-19 a8eefeecb），并发败者无 catch-duplicate+re-read 非幂等；新增 P2-MA3-03；终局定论归 MA6.6**。**MA6.6 终局定论（2026-08-04）：维持 watch-only（并发面）**——标准供给路径（initDatabaseSchema=TRUE 模型生成）下 UK 有效阻止并发重复，败者非幂等不产生错误数据（完整性保住），仅浮出虚假错误；**新增 P2-MA6.6-001：deploy/sql 三方言 DDL 快照零 UK 发射**（模型 ~35 unique-key 全部缺失，静态 DDL 供给的部署失去全部 UK 保护）→ 归 MR3/DDL 管线；P2-MA3-03（schema ∉ UK）确认维持归 MR2 | 见上（watch-only + MR2/MR3） |
| `2026-07-20-1554#post-commit-SEMANTIC` | dispatchActions "post-commit" 语义 = runWithoutTransaction 同步执行 | watch-only（javadoc 已显式文档化隔离语义，设计有意）；**MA3.4 复核（2026-08-04）：维持 watch-only**——BizModel javadoc:354-364 准确且与实现一致；残留 P3 MA3.4-02（dispatcher javadoc 仍写 onAfterCommit，归 MR1 纯注释） |
| `P2-MA4-001/002`（MA4.1 新增/复核） | 静默吞异常 2 处：NopMetaTagLabelBizModel.getWfNameFromMeta catch-all 无日志、SqlViewFieldTypeInferrer.safeProductName 7 实现中唯一无日志（09-02/03/06 家族） | MR2（补 LOG.warn）；P3 3 处（MetaQualityRuleExecutor fallback 无日志 → trace） |
| `P2-MA4-101`（MA4.2 新增） | CheckpointExtConfig 强类型 DTO 生产死代码——15-03 迁移只做一半：main 零引用，2 个消费方仍手写 JsonTool.parse+Map 强转 | MR2（消费方改 parseBeanFromText 或撤 DTO javadoc 声明） |
| `P2-MA4-301`（MA4.3 新增） | 3 个 JOIN AggregationProcessor 单元层空壳（execute() 零保护，与 P1-MA4-601 同源）——**MA6.1 复核（2026-08-04）：登记修正——第 4 个 CrossDbInMemoryAggregationProcessor.java:36 同模式（JOIN 跨库 fallback）+ SqlAggregationProcessor.java:26 委托门面同模式（非 JOIN）** | MR2 |
| `P2-MA4-303`（MA4.3 新增） | 分页测试只验行数不验行集——offset 被忽略的 AR-04 类 bug 无法被捕获 | MR2（断言首行内容） |
| `P2-MA4-501`（MA4.5 新增） | `*Service` 命名违规 2 处存活（NopMetaSearchService 审计后复发 + QualityAlertWorkflowService 漏网；02-01 残余确认） | MR2（命名批量修复，随 02-01） |
| `P2-MA4-602`（MA4.6 新增） | helper 镜像测试跨 4 文件重复 30+ 方法（safeAlias/aggSqlOf 逐字复制） | MR2（收敛单一 helper 测试文件） |
| MA5 新增（2026-08-04，4 份报告） | **P2-MA5-101..186（设计文档 drift 19 项 + 2 项 MR1 Follow-up 确认）**：metaSchema 列名（baseline:189 等 + 05:212）、ConnectionProcessor 改名（baseline 7 处）、SysDaoMessageService 依赖链 2 处、错误码字符串 3 处、CrossDbJoinMerger 迁移、core.dto 陈旧引用 2 处（02-dto:73/api-dto-spec:213 确认归 MR3）、DTO 字段规格 3 项（QualityRuleExecuteResultDTO/QualityScoreResultDTO/ContractCheckResultDTO）、activateContract 族已删（12:269）、JdbcModelDiscoverer 不存在（09:37）、nop-batch 否决残留（09:56）、collectCatalog 返回 DTO、MetaDict 字段清单、事件实体已建模（03:179）、Errors 集中化（03:209）等——全部文档 drift 归 MR3（R3.0 展开器输入）；**P2-MA5-201..206（docs-for-ai）**：core/dto 29 DTO 陈旧 2 处、meta 无 xbeans、xbiz 在 service 非 web、META-001 锚点 plan 状态、syncExternalTables 示例字段——归 MR3；**P2-MA5-301（命名）**：dataSourceId/datasourceType 双拼写（orm.xml:383/:392，需 ORM 变更 plan-first）——MR2 裁决；**P2-MA5-401（跨模块）**：NopMetaTagLabelBizModel.getWfNameFromMeta getProp 恒 null 致自动提审静默失效（与 P2-MA4-001 不同根因）——MR2 | 见各报告（MR3 为主，301/401 归 MR2） |
| MA4 复核结论（P2 登记区更新） | 09-07 hyphen watch-only 维持（MA4.1）；16-01 AutoTest 5/97 维持 open 且发现 F16-302 名不副实（MA4.3）；16-04 存在性闭包、judgeByRuleId 质量 open（MA4.4/7）；16-09 已修复（MA4.7）；16-03 核心域部分关闭、其余域部分 TagLabelApproval 双文件重复 P3-MA4-704（MA4.6/7）；07-004 维持 MR2 且发现 AggregationRowDTO 零引用（MA4.2）；15-03 基线更新 157/98 裁定维持（MA4.2）；02-01 残余 2 处确认（MA4.5） | 见各报告 |
| MA6 新增（2026-08-04，6 份报告） | **P1 家族登记修正**：MA4-601 6类/18 → 7类/21 空壳测试（MA6.1-002）；**P2 新增 6 项**：MA6.1-001 AggregationRowDTO 死 DTO（P2-MA4-101 家族）、MA6.1-003 CrossDbInMemory processor 第 4 个零保护 execute、MA6.2-002 MetaTableProfiler 空串统计失真、MA6.5-001 跨类共享 H2 库 meta_q_sql（顺序相关）、MA6.5-004 假时钟缺 finally、MA6.6-001 deploy/sql DDL 零 UK 发射（RACE 终局伴随发现）；**P3 新增 16 项**：MA6.1-004、MA6.2-001/003..007、MA6.5-002/003/005/006/007、MA6.4-01/02/03/04（+2 informational：MA6.4-05/06）；**Decision**：RACE 终局维持 watch-only（并发面）+ P2-MA6.6-001（快照面）、16-07 data-auth 测试维持 watch-only（有意限制声明成立）；20-01 维持 open 归 MR2（OrmModelImporter:58,68）；16-05 复核真测试但浅（P3）；P3-MA4-705 MockMessageService static 原样存续 | 见各报告（MR2/MR3 为主） |
