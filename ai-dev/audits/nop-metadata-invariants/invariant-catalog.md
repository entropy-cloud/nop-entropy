# nop-metadata 不变式目录（Invariant Catalog）

> 产出方：plan `2026-08-13-1930-1`（Cycle 1 / I0 — 不变式盘点与基线，Phase 2）
> 实测日期：2026-08-13（live repo，非记忆）
> 上游 roadmap：`ai-dev/backlog/nop-metadata-invariant-loop-roadmap.md`（I0 / I1）
> 方法论：`ai-dev/skills/invariant-loop-audit-prompt.md`（关键机制 #1 不变式=可执行门禁入CI、#2 门禁集合单调棘轮）
> 配套：`audit-target-set.md`（目标集与覆盖率基准）

## 目的

把 nop-metadata 5 轮 multi+open 审计 + ARM MA1-MA7（21 维）+ MR1-MR8 反反复发的失败模式族，收口为**结构化的不变式目录**。每条不变式含四要素：① 陈述 / ② 覆盖的失败族 / ③ 历史 audit-finding-ID 证据 / ④ 检测方法（与 I1 落地形式对齐）。

## 元规则：不变式判定以 live 实测为准（虚假关闭教训）

> **判定不变式是否被违反，必须以 live repo 实测为准。commit message、旧 completion note、status 字段不足为据。**

实证先例（arm-index `ai-dev/audits/arm-index-nop-metadata.md:129` / `:72`）：

- **AR-06（R3.14 虚假关闭）**：commit `9b769490e` 标注 `"MA7.6-05：slaFresh=false"`，声称已修复。但 git 逐行核对 `MetaContractChecker.java` 的 diff **real diff lines = 0**（仅删 7 行版权头）——修复从未落地。直到 R7.3（plan-2026-08-06-0553-3 Phase 2）才实际补做。
- **unique-key constraint（本目录 INV-UK 实测）**：plan baseline 沿用旧值「1 个缺失」，但 live XML-aware 核对显示 37/37 全带 `constraint`（见 `audit-target-set.md` 基线校正记录）。以 live 0 为准。

推论：I2 跑门禁产出 red list 时，每一条违规必须由可复现的 `rg`/扫描器/测试输出支撑；「声称已修」不抹除违规。这与 roadmap 的反模式「基线节凭记忆写"X 未修"」双向呼应——既不凭记忆写"未修"，也不凭记忆写"已修"。

---

## CI 门禁基线（零点）

**当前状态（2026-08-13 live 实测）= 零 nop-metadata 专属不变式门禁。**

| 维度 | live 实测 | 复现 |
|---|---|---|
| `ai-dev/tools/check-silent-swallow.mjs` | **不存在** | `ls ai-dev/tools/check-silent-swallow.mjs` |
| `ai-dev/tools/check-orm-unique-key-constraint.mjs` | **不存在** | `ls ai-dev/tools/check-orm-unique-key-constraint.mjs` |
| `ai-dev/tools/check-sensitive-literal-leak.mjs` | **不存在** | `ls ai-dev/tools/check-sensitive-literal-leak.mjs` |
| nop-metadata 内 invariant 主题 JUnit | **不存在** | `rg -l 'invariant\|Invariant' nop-metadata --glob '*.java'`（空） |
| `ai-dev/audits/nop-metadata-invariants/` 目录 | **本批新建**（此前不存在） | — |

**部分重叠（诚实声明，不计入 nop-metadata 专属门禁）**：`ai-dev/tools/rules/` 下有 3 条通用 Java ast-grep 规则——`java-lint-empty-catch.yml`（空 catch 体）、`java-lint-getmessage-only.yml`（catch 仅 `e.getMessage()`）、`java-lint-bare-runtimeexception.yml`。其中前两条与 INV-SILENT-SWALLOW 部分重叠，但它们是**全仓通用 lint 规则**，非 nop-metadata 专属、无表完备性门禁、未与 130 catch 块穷举集绑定。I1 沉淀 INV-SILENT-SWALLOW 时须明确：是扩展这两条通用规则为 nop-metadata 表完备门禁，还是新增独立扫描器——避免与通用规则重复或冲突。

**I5 收口目标门禁清单**（与本目录 INV-* 一一对应）：

| 不变式 | 目标门禁形式（I1 落地） |
|---|---|
| INV-SILENT-SWALLOW | `ai-dev/tools/check-silent-swallow.mjs` 静态扫描（扫描 130 catch 块）+ 可选 JUnit/ArchUnit |
| INV-UK | `ai-dev/tools/check-orm-unique-key-constraint.mjs`（扫描 orm.xml unique-key） |
| INV-LIMIT | JUnit 5 `@ParameterizedTest` + `@MethodSource`（4 limit-taking 入口方法表驱动穷举）+ 表完备性门禁 |
| INV-SENSITIVE | `ai-dev/tools/check-sensitive-literal-leak.mjs`（扫描 `.param(...)`/`LOG.*(...)` 字面量） |

**棘轮规则**（roadmap 关键机制 #2）：已沉淀的不变式只增不减；弱化/删除/豁免需人工确认 + 留痕 + committed 回归测试同步。详见 `ai-dev/skills/invariant-loop-audit-prompt.md` 关键机制 #2（line 67）。本目录即该棘轮的零点基线——I1 之后任何新增/重命名变更型方法或 ORM entity 必须被门禁覆盖，否则表完备性门禁变红。

---

## 不变式

### INV-SILENT-SWALLOW — 静默吞异常族（fail-loud）

**① 陈述**（通用，不绑死 nop-metadata）：每个 service/processor/bizmodel 层的 `catch` 块必须满足以下之一——(a) rethrow（原样或包装为 `NopException` 并保留 cause）；(b) 在 catch 内构造带 `ErrorCode` 的诊断信息（LOG.error/warn 含 throwable 末参 + 错误码上下文参数），且该 catch 的「静默降级返回默认值」语义为有意裁定（可追溯的 per-edge 隔离 / 已接受成本）。**禁止**：空 catch 体、仅 `e.getMessage()` 不 rethrow 不记录、catch 后 `return null/0/false/emptyList` 当正常值且无日志。

**② 覆盖的失败族**：静默吞异常族（silent-swallow / fail-loud）——≥7 个兄弟实例横跨多轮。

**③ 历史 audit-finding-ID 证据**（全部 `ai-dev/audits/arm-index-nop-metadata.md` 可定位）：

- P2-06 `AggregationHelper.java:496-506` catch→LOG.warn+false（R6.4 fixed，arm-index:101）
- P2-07 `NopMetaModuleBizModel.java:217-234` parseDeltaModel catch→降级（R6.4 fixed，arm-index:102）
- P2-09 `NopMetaTagLabelBizModel.java:128-129` 提审失败仅 LOG.warn（R6.4 fixed，arm-index:103）
- P2-01 `NopMetaSearchProcessor.java:56-66/:77-87` throw 不带 cause 无日志（R6.5 fixed，arm-index:104）
- P2-02 `AutoClassificationProcessor.java:129-134` 正则编译失败 catch→continue 无日志（R6.5 fixed，arm-index:105）
- P2-04 `MetaQualityCheckpointExecutor.java:349-358` 等 4 处 catch 静默返回默认值（R6.5 fixed，arm-index:106）
- AR-21 `AutoClassificationProcessor.doCreateAutomatedLabel` + `LineageTagPropagationProcessor.java:177-188` catch-all（R8.4a fixed，arm-index:45）

**④ 检测方法**（与 I1 对齐）：`ai-dev/tools/check-silent-swallow.mjs` 静态扫描器（Node + ast-grep），扫描目标集 = §覆盖率所列 130 catch 块。规则形态参考已有 `ai-dev/tools/rules/java-lint-empty-catch.yml` + `java-lint-getmessage-only.yml`，但须升级为 nop-metadata 表完备扫描（绑定 130 catch 块穷举集，而非全仓通用规则）。

**目标集覆盖率**：130 catch 块 / 46 文件（见 `audit-target-set.md` §1.3 逐文件分布）。历史命中 7 兄弟实例已修；I2 待审面 = 全部 130 catch 块（含未被单独审计的内部方法 catch）。

---

### INV-UK — unique-key constraint 完备性族

**① 陈述**（通用）：每个 ORM 模型中的 `<unique-key>` 元素必须同时带 `name=`、`columns=`、`constraint=` 属性。缺 `constraint=` 则 DDL 发射门（`ddl.xlib:81-82`）静默跳过该唯一约束，部署层数据完整性保护丢失。陈述不绑死 nop-metadata——I1 扫描器可按需扩到全仓 `*.orm.xml`。

**② 覆盖的失败族**：DDL / unique-key 静默缺失族——Lesson 09 记录 36 个 `<unique-key>` 曾全缺 `constraint`，DDL 三方言零 UNIQUE 发射。

**③ 历史 audit-finding-ID 证据**：

- MA7.3-01 / P2-MA6.6-001：零 UK 发射根因 = `constraint` 属性门（`ai-dev/audits/arm-index-nop-metadata.md:300`）
- R3.19（plan-2026-08-05-0746-2 Phase 4）：36 UK 补 constraint（model-first）+ DDL 三方言再生成 + DdlSqlCreator 断言测试
- Lesson 09：`ai-dev/lessons/09-ddl-unique-key-silent-absence.md`（建议自动化为 CI 门禁，本族正是该建议的闭环落地）

**④ 检测方法**：`ai-dev/tools/check-orm-unique-key-constraint.mjs`（XML-aware，逐 `<unique-key>` 元素核对 `constraint` + `columns`，跨行元素须整体匹配——见 `audit-target-set.md` §3 复现脚本）。

**目标集覆盖率**：37 unique-key / 39 entity（见 `audit-target-set.md` §2.2）。**当前 red list = 0**（live 实测 37/37 全带属性，DDL 已物化——见 `audit-target-set.md` §2.3 + 基线校正记录）。本不变式当前为**防回退门禁**：保护「新增 unique-key 必带 constraint」不再复发，而非修现存违规。

---

### INV-LIMIT — limit 负值校验族

**① 陈述**（通用）：每个接受 `limit`（或同义分页参数）入参的 public 入口方法，必须在入口处显式拒绝负值（抛带 `ErrorCode` 的异常，如 `ERR_PAGINATION_LIMIT_INVALID` / `ERR_SEARCH_LIMIT_INVALID`）。**禁止**：负 limit 直通下游（SQL `LIMIT ?` 占位符把负值绑给 DB，得到不可诊断错误）或静默钳制到默认值（掩盖调用方 bug）。「null/0 → 缺省值」与「超上限 → 封顶或拒绝」的语义可为有意裁定（如 queryTableData 静默封顶 vs queryJoinData 显式拒绝，MA7.4-03 / AR-09 已裁定），但**负值必须显式失败**。

**② 覆盖的失败族**：limit 负值校验族——AR-09 → AR-23④ 显式「沿先例」交叉引用。

**③ 历史 audit-finding-ID 证据**：

- AR-09（R6.6 docs / R7.3 fixed）：`NopMetaTableBizModel` queryJoinData/queryAggregation → `normalizeJoinQueryLimit` 抛 `ERR_PAGINATION_LIMIT_INVALID`（`ai-dev/audits/arm-index-nop-metadata.md:130`，plan-2026-08-06-0553-3）
- AR-23④（R8.2 fixed）：`NopMetaSearchBizModel.java:66-70` searchMetadata → `ERR_SEARCH_LIMIT_INVALID`，审计原文「沿 AR-09 先例」（`ai-dev/audits/arm-index-nop-metadata.md:51`，plan-2026-08-06-0914-2）

**④ 检测方法**：JUnit 5 `@ParameterizedTest` + `@MethodSource`（方法表驱动穷举），表 = 4 limit-taking 入口方法；每个方法参数化 `limit = -1` 断言抛指定 ErrorCode。配套**表完备性门禁**：参数化方法表 == 从 `@Name("limit")` 反查的全部入口方法集（当前 4），新增 limit-taking 方法不入表即门禁红。

**目标集覆盖率**：4 limit-taking 入口方法（见 `audit-target-set.md` §1.4）。当前 4/4 已显式拒绝负值（live 已修）；本不变式为**防回退 + 防新入口遗漏**门禁。

---

### INV-SENSITIVE — 敏感字面量脱敏族

**① 陈述**（通用）：error message（`.param(...)`）与 log（`LOG.info|warn|error(...)`）中不得出现敏感字面量原文——raw JDBC URL（可含 `user:pass@`）、内嵌 SQL 原文（可含业务数据/凭据）、明文凭据串。须用脱敏形式（如脱敏 jdbcUrl、`sqlHash`、`sqlHashOf(sql)`）替代。陈述不绑死 nop-metadata——可扩到全仓 error/log 构造点。

**② 覆盖的失败族**：敏感字面量脱敏族——R6.2 P2-12 → R8.2 AR-16 显式「与 R6.2 P2-12 脱敏一致」交叉引用。

**③ 历史 audit-finding-ID 证据**：

- P2-12（R6.2 fixed）：`MetaDataSourceConnectionProcessor.java:225-247` 三处 `.param(ARG_RAW_JDBC_URL, jdbcUrl)` 原始 URL（含凭据）→ 全部移除 + 删 `ARG_RAW_JDBC_URL` 常量（`ai-dev/audits/arm-index-nop-metadata.md:98`，plan-2026-08-05-2157-2）
- AR-16（R8.2 fixed）：`MetaQualityRuleExecutor.java:631,647,669` 三处 `LOG.info` 完整 SQL 字面量 → `sqlHash`（`ai-dev/audits/arm-index-nop-metadata.md:36`，plan-2026-08-06-0914-2）
- AR-13（R8.1 fixed）：ErrorCode 声明 `{sqlHash}` 替代 `{sql}`（同族，arm-index R8.1 收口段）
- AR-23⑩（R8.4b fixed）：`MetaModelChangedEventPublisher.java:201-203` Map 分支敏感列脱敏（`ai-dev/audits/arm-index-nop-metadata.md:54`，plan-2026-08-06-1228-2）

**④ 检测方法**：`ai-dev/tools/check-sensitive-literal-leak.mjs`（Node + ast-grep），扫描 `.param(...)` / `LOG.*(...)` 字面量入参，命中 JDBC URL 模式（`jdbc:` + `@`）/ 内嵌 SQL 原文 / 已知凭据哨兵即红。

**目标集覆盖率**：error/log 构造点（见 `audit-target-set.md` §1.5）。历史命中点 = `MetaDataSourceConnectionProcessor`、`MetaQualityRuleExecutor`、`MetaModelChangedEventPublisher`。精确穷举面由 I1 扫描器定义。

---

## 候选不变式（Non-Blocking Follow-up，留待 I6 裁定）

> Phase 1 枚举未发现超出首批 4 族的高频复发模式需立即沉淀。以下为低频观察，不展开，留待 I6 统计后裁定是否派生 Cycle 2 / I1。

- **类型/方言兼容性族**（AR-20 NULLS FIRST/LAST、AR-23⑧）：单点命中，未见同族兄弟复发，暂不沉淀为不变式。
- **并发竞态/UK 幂等族**（P2-MA3-03 upsertExternalTable、2026-07-20 RACE）：已裁定 watch-only residual，DB UK 已 fail-loud 兜底，非静默缺陷，暂不沉淀。

---

## 引用

- `audit-target-set.md`（目标集 + 覆盖率基准）
- `ai-dev/audits/arm-index-nop-metadata.md`（全部 finding-ID 可定位）
- `ai-dev/lessons/09-ddl-unique-key-silent-absence.md`（INV-UK lesson）
- `ai-dev/skills/invariant-loop-audit-prompt.md`（方法论 + 棘轮规则关键机制 #2）
- `ai-dev/backlog/nop-metadata-invariant-loop-roadmap.md`（I0/I1 规格）
