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

> 后续 MA1-MA7 审计报告按 `YYYY-MM-DD-HHmm-arm-<milestone>-nop-metadata-<dimension>.md` 命名登记于此（roadmap 规则 2：产出即更新索引）。

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
| `2026-07-20-1554#MISSING-AUTH` | 07-20 deep | 自定义 @BizMutation 缺细粒度 @Auth 注解 | watch-only（300 plan 裁定：action-auth 默认关闭 + 粗粒度兜底）；MA3.3 审计复核 | watch-only（已裁定） |
| `2026-07-21-2039#维度16-01` | 07-21 multi | 19/40 BizModel 零测试（07 plan 已覆盖 5 个高风险，剩余 14 个） | watch-only（07/10 plan 裁定 out-of-scope）；MA4.4/4.7 审计复核 | watch-only（已裁定） |

> P1 未闭包数：**3**（其中 2 项为已裁定 watch-only residual，1 项 live 确认 open 待 MR 修复）。

## P2 发现汇总（待 MA 审计复核 / MR 批量修复）

> 见 `arm-unclosed-findings-nop-metadata.md` §未闭包 P2/P3 登记区；本表为 P2 级未闭包索引（P3 不逐条索引，见清单尾部汇总）。

| Finding ID | 描述 | 归属 |
|-----------|------|------|
| `2026-07-21-2039-open#AR-25` | 血缘抽取 N+1 upsert | 已裁定 optimization candidate；MA7.4 复核后按需 MR3 |
| `2026-07-23-0714#维度07-003` | getEntityById 替代 requireEntity 残余（~10 处，DataSource/DataContract 已修复） | MR2（MA2.3 审计后） |
| `2026-07-23-0714#维度07-004` | DTO 内 `List<Map<String,Object>>` 未类型化 | MR2（MA1.3 审计后） |
| `2026-07-23-0714#维度09-02/03/06` | 静默吞异常（MetaTableProfiler/MetaQualityRuleExecutor/TagLabelBizModel 等 5 处） | MR2（MA4.1 审计后） |
| `2026-07-23-0714#维度09-07` | ErrorCode hyphen 分隔符约定 | watch-only（NopMetadataErrors.java:22 有意裁定）；MA4.1 复核 |
| `2026-07-23-0714#维度11-04` | computeQualityScore 绕过 xmeta 验证 | MR2（MA3.2 审计后） |
| `2026-07-23-0714#维度16-01` | AutoTest 快照覆盖偏低（5/97 文件） | MA4.3 审计 + MR2 |
| `2026-07-23-0714#维度16-03/05/07/09` | 测试质量项（重复 CRUD/并发测试/sleep/data-auth 测试） | MA4.6/4.7 审计 + MR2 |
| `2026-07-21-2039#维度07-03` | queryAggregation 11 参数未用 @RequestBean | MR2（MA1.3 审计后） |
| `2026-07-23-0714#维度05-08` | CRUD codegen 有意禁用 | watch-only（有意设计）；MA2.2 复核 |
