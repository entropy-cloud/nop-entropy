# nop-metadata 不变式目录（Invariant Catalog）

> 产出方：plan `2026-08-13-1930-1`（Cycle 1 / I0 — 不变式盘点与基线，Phase 2）；Cycle 2 / I1' 增补：plan `2026-08-15-0820-1`（silent-wrong-result 5 子族，见「Cycle 2 增补」节）
> 实测日期：2026-08-13（Cycle 1）/ 2026-08-15（Cycle 2 增补，live repo，非记忆）
> 上游 roadmap：`ai-dev/backlog/nop-metadata-invariant-loop-roadmap.md`（I0 / I1 / Cycle 2 I1'）
> 方法论：`ai-dev/skills/invariant-loop-audit-prompt.md`（关键机制 #1 不变式=可执行门禁入CI、#2 门禁集合单调棘轮）
> 配套：`audit-target-set.md`（目标集与覆盖率基准）、`initial-red-list-cycle2.md` + `baseline-cycle2/`（Cycle 2 快照与对账基线）

## 目的

把 nop-metadata 5 轮 multi+open 审计 + ARM MA1-MA7（21 维）+ MR1-MR8 反反复发的失败模式族，收口为**结构化的不变式目录**。每条不变式含四要素：① 陈述 / ② 覆盖的失败族 / ③ 历史 audit-finding-ID 证据 / ④ 检测方法（与 I1 落地形式对齐）。

## 元规则：不变式判定以 live 实测为准（虚假关闭教训）

> **判定不变式是否被违反，必须以 live repo 实测为准。commit message、旧 completion note、status 字段不足为据。**

实证先例（arm-index `ai-dev/audits/arm-index-nop-metadata.md:129` / `:72`）：

- **AR-06（R3.14 虚假关闭）**：commit `9b769490e` 标注 `"MA7.6-05：slaFresh=false"`，声称已修复。但 git 逐行核对 `MetaContractChecker.java` 的 diff **real diff lines = 0**（仅删 7 行版权头）——修复从未落地。直到 R7.3（plan-2026-08-06-0553-3 Phase 2）才实际补做。
- **unique-key constraint（本目录 INV-UK 实测）**：plan baseline 沿用旧值「1 个缺失」，但 live XML-aware 核对显示全部 UK 带 `constraint`（见 `audit-target-set.md` 基线校正记录；2026-08-16 P2-29 删 2 个冗余 per-scope FQN UK 后 = 35/35 全带）。以 live 0 为准。

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

**目标集覆盖率**：35 unique-key / 39 entity（见 `audit-target-set.md` §2.2；2026-08-16 plan-2026-08-16-0920-1 P2-29 删除 2 个冗余 per-scope FQN UK——全局 FQN UK 逻辑蕴含 per-scope，约束语义不变——清单 37 → 35）。**当前 red list = 0**（live 实测 35/35 全带属性，DDL 已物化——见 `audit-target-set.md` §2.3 + 基线校正记录）。本不变式当前为**防回退门禁**：保护「新增 unique-key 必带 constraint」不再复发，而非修现存违规。

---

### INV-LIMIT — limit 负值校验族

**① 陈述**（通用）：每个接受 `limit`（或同义分页参数）入参的 public 入口方法，必须在入口处显式拒绝负值（抛带 `ErrorCode` 的异常，如 `ERR_PAGINATION_LIMIT_INVALID` / `ERR_SEARCH_LIMIT_INVALID`）。**禁止**：负 limit 直通下游（SQL `LIMIT ?` 占位符把负值绑给 DB，得到不可诊断错误）或静默钳制到默认值（掩盖调用方 bug）。「null/0 → 缺省值」与「超上限 → 封顶或拒绝」的语义可为有意裁定（如 queryTableData 对超大正值静默封顶 vs queryJoinData 原样透传由截断层拒绝，MA7.4-03 / AR-09 已裁定），但**负值必须显式失败**——此规则统一适用于全部 4 个 limit-taking 入口（queryTableData 的历史负值静默封顶已由 plan 2026-08-13-1930-5 Phase 0 收敛为显式拒绝；MA7.4-03 的范围是「缺省值 + 上限」，不含负值）。

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

## Cycle 2 增补 — silent-wrong-result 族（plan `2026-08-15-0820-1` Phase 1）

> 5 个子族全部裁定为**静态扫描可机械化**，落地为单门禁 `ai-dev/tools/check-silent-wrong-result.mjs`（1 个扫描器 × 5 条规则，与 Cycle 1 `check-*.mjs` 同风格；子族标签区分命中），以模式 b（baseline 快照对账）接入 CI。裁定依据逐族见各 INV 条目；watch-only 候选重估见「候选不变式」节。

### INV-LOCALE — 默认 locale case-mapping 族

**① 陈述**：service/processor 层的字符串大小写转换若用于**机器比较语义**（registry 键、关键字/标识符匹配、集合归一化等影响匹配与查找结果的路径），必须使用 locale-insensitive 形式（`toLowerCase(Locale.ROOT)` / `toUpperCase(Locale.ROOT)`，或天然 locale-insensitive 的 `equalsIgnoreCase`），**禁止**默认 locale 的无参 `.toLowerCase()` / `.toUpperCase()`——tr-TR 下 `"I".toLowerCase()` → `"ı"`，同一数据在不同 JVM locale 得到不同匹配结果（silent-wrong-result）。display-only（人类可读文案）语义不在此列。**门禁口径**：静态扫描无法区分机器比较与 display-only 语义 → 全量报告无参调用点，由裁决表（I3'）逐条裁定；display-only 裁定终态 = `// invariant-ok: <裁决引用>` 放行注释（不驻留 baseline）或驻留 baseline（方式 a），两种终态机制均由门禁预实现。

**② 覆盖的失败族**：silent-wrong-result / locale 族。

**③ 历史 audit-finding-ID 证据**：

- AR-12（`LocalReconciliationProcessor.score` 默认 locale `toLowerCase`，Turkish-I 风险；plan 2026-08-14-1133-3 Phase 3 修复为 `Locale.ROOT`；`ai-dev/audits/2026-08-14-0707-open-audit-nop-metadata-invariant-loop.md` AR-12）
- 类残留实证：修复只落了报到的 1 处，live repo 同族 40 站点（扫描器口径，见 ④）——「修实例不修类别」的直接证据，也是本条不变式立项动机。

**④ 检测方法**：`ai-dev/tools/check-silent-wrong-result.mjs` 规则 `locale`——注释/字符串剥离后扫描无参 `.toLowerCase()` / `.toUpperCase()` 调用点（带 `Locale.` 参数的形态天然不命中）。**边界**：无法静态区分机器比较与 display-only——全量报告 + 裁决表消化（不引入恒 exit 0 的报告型形态）。

**目标集覆盖率**：40 站点 / 11 文件（2026-08-15 live 实测，见 `audit-target-set.md` §5.1；rg 原始口径 41 含 1 处 javadoc 伪站点 `LocalReconciliationProcessor.java:124`）。

---

### INV-NARROW — narrowing-cast 截断族（防回退）

**① 陈述**：`(long|int|short)` 强转**不得直接作用于浮点类型操作数后再参与算术运算**——截断先于算术 = 静默错算（`(long) amount * unit` 把 0.5 截成 0 再乘，≠ `(long)(amount * unit)` 的正确取整）。正确形态：先完成浮点算术、再对完整表达式取整（操作数加括号），或用 `Math.round/floor/ceil` 显式取整语义。

**② 覆盖的失败族**：silent-wrong-result / narrowing-cast 族。

**③ 历史 audit-finding-ID 证据**：

- AR-01（`MetaContractChecker` SLA 分数 amount 先 `(long)` 截断后乘 → `{"interval":0.5,"unit":"w"}` 算成 0ms 恒判过期；plan 2026-08-14-0707-2 修复为 `(long) (amount * unit)`；`ai-dev/audits/2026-08-14-0707-multi-audit-nop-metadata-invariant-loop.md` AR-01）

**④ 检测方法**：`check-silent-wrong-result.mjs` 规则 `narrowing-cast`——`(long|int|short)` 强转 + **未加括号的标识符操作数** + 紧随二元算术运算符（`+ - * / %`），且该操作数在同文件有 `double/Double/float/Float` 声明 → 命中。int/long 操作数的 widening 场景（如 `(long) from + limit`）与修复形态 `(long) (amount * x)`（操作数加括号）均不命中。**边界（显式声明）**：操作数为浮点返回值的方法调用（无本地 double 声明可静态判定）不在检测范围内——watch 边界，由周期对抗探查覆盖。

**目标集覆盖率**：live 0 命中（防回退零点；仅存的 2 处 `(long)` 站点 `MetaContractChecker.java:384/:395` 均为修复后的正确形态）。

---

### INV-CONTAINS-CLASSIFY — 子串匹配用作类型/类别分类族

**① 陈述**：类型/类别判定**不得使用 `String.contains` 子串匹配**——`"POINT"` 含子串 `"INT"` 式误分类会把值路由到错误统计/处理路径（silent-wrong-result）。分类判定必须用 exact-match（`Set.of(...)` 全等）或显式前缀/后缀语义（`startsWith`/`endsWith`）。集合元素全等查询（`Set/List.contains`）不在此列。**门禁口径**：报告全部 String-receiver 的 `.contains(` 站点（receiver 类型可静态判定者）；message 线索探测（infra 错误消息 substring 线索）等非分类用途由裁决表裁定。

**② 覆盖的失败族**：silent-wrong-result / contains-分类族。

**③ 历史 audit-finding-ID 证据**：

- AR-05（`MetaTableProfiler.isNumericType` 子串匹配：POINT 误归数值、BOOLEAN/BIT 误归数值 → 非法 SUM；plan 2026-08-14-1133-2 Phase 1 修复为 `NUMERIC_TYPE_NAMES` exact-match `Set.of`；`ai-dev/audits/2026-08-14-0707-open-audit-nop-metadata-invariant-loop.md` AR-05）
- 类残留实证（本次扫描发现，交 I3' 裁决）：同文件 `isStringType` 仍为 `upper.contains(kw)` 子串循环——AR-05 只修了 `isNumericType`，兄弟方法未随修。

**④ 检测方法**：`check-silent-wrong-result.mjs` 规则 `contains-classify`——`.contains(` 且 receiver 为 String 类型（同文件存在 `String <id>` 声明，或 receiver 为 `toLowerCase()/toUpperCase()/trim()` 等 String 变换调用链尾）；receiver 为集合类型（`Set/List/Collection/Map/Iterable` 声明）不命中；声明不可判定的 receiver（跨方法返回值等）不命中（watch 边界）。

**目标集覆盖率**：live 10 站点（2026-08-15，见 `audit-target-set.md` §5.3）。

---

### INV-DELIM-KEY — 分隔符拼接复合键族

**① 陈述**：map/set 的复合键**不得用分隔符拼接字符串**（`a + "|" + b`）——任一分量含分隔符（或空串边界）即产生键碰撞 → 错误分组/错误去重/错误 visited 判定（silent-wrong-result）。复合键必须用结构性键（record / 不可变对象 equals+hashCode）或显式防碰撞编码。

**② 覆盖的失败族**：silent-wrong-result / 分隔符-key 族。

**③ 历史 audit-finding-ID 证据**：

- AR-03（内存聚合 group-key `colId + "|" + aggType` 控制字符碰撞 → 错误分组；plan 2026-08-14-0707-2 修复改结构性 key；`ai-dev/audits/2026-08-14-0707-multi-audit-nop-metadata-invariant-loop.md` AR-03）
- 类残留实证（本次扫描发现，交 I3' 裁决）：6 处分隔符拼接访问键——`LineageTagPropagationProcessor` `entityType + "#" + entityId` ×3（`:85`/`:136`/`:152`）、`AutoClassificationProcessor:144` `warnKey`、`NopMetaLineageEdgeQueryAction:258` `key`、`NopMetaLineageEdgeQueryAction:487` `map.put(a + "|" + b + "|" + c, e)`（三段 `|` 拼接直接作 map 键，AR-03 同形）。

**④ 检测方法**：`check-silent-wrong-result.mjs` 规则 `delim-key`——① 集合成员/存取方法（`get/put/add/contains/computeIfAbsent/putIfAbsent/getOrDefault/merge/remove/containsKey`）实参跨度内的分隔符拼接 `+ "<sep>" +`（sep ∈ `| : ; # @ ~ $ % ^ & ,`）；② `String <name含Key> = <分隔符拼接 或 String.join("<sep>", ...)>` 赋值。**边界**：lambda key extractor 内联拼接（如 `groupingBy(e -> a + "|" + b)`）需流分析才能定位消费点，不在静态检测范围——watch 边界。

**目标集覆盖率**：live 6 站点 / 3 文件（2026-08-15，见 `audit-target-set.md` §5.4）。

---

### INV-BIGDEC — Number→BigDecimal 经 double 丢精度族

**① 陈述**：`Number` 值转 `BigDecimal` **不得经 `doubleValue()` 中转**——`Long > 2^53` 经 double 丢低位（silent-wrong-result）。必须先路由整数子类型 `longValue()`（`Long/Integer/Short/Byte/AtomicLong/AtomicInteger`），仅浮点子类型（`Float/Double`）走 `doubleValue()`；`new BigDecimal(<double>)` 同族禁止（引入二进制展开噪声）；`String` 数值不得静默跳过（解析或显式裁定）。

**② 覆盖的失败族**：silent-wrong-result / 精度族。

**③ 历史 audit-finding-ID 证据**：

- AR-10（`AggregationHelper.toBigDecimal` 经 double 丢精度 + String 静默跳过；plan 2026-08-14-1133-2 Phase 3 修复：整数 longValue 无损路由 + String 解析；`ai-dev/audits/2026-08-14-0707-open-audit-nop-metadata-invariant-loop.md` AR-10）
- **受保护正确形态（检测规则必须排除）**：`AggregationHelper.java:554` 的 `java.math.BigDecimal.valueOf(n.doubleValue())`——位于整数类型已先行路由 `longValue()`（`:551`）之后的剩余浮点分支，属合法浮点路径。朴素 pattern 扫描会把它与 2 处真违规一起命中（3 处）而与基线矛盾。
- 类残留实证（本次扫描发现，交 I3' 裁决）：`MemoryOrderByComparator.toBigDecimal`（`:132`）/ `MemoryFilterEvaluator.toBigDecimal`（`:356`）两个私有拷贝仍为缺陷形态（无整数路由 + String 静默跳过）。

**④ 检测方法**：`check-silent-wrong-result.mjs` 规则 `bigdec-precision`——`BigDecimal.valueOf(...)` / `new BigDecimal(...)` 实参含 `.doubleValue()`，且**所在方法体不含 `.longValue()` 整数路由信号** → 命中；含路由信号（AR-10 修复形态）不命中（受保护形态显式排除，不靠文件名白名单）。

**目标集覆盖率**：live 2 站点（2026-08-15，见 `audit-target-set.md` §5.5）。

---

## Cycle 3 增补 — error-param 族（plan `2026-08-15-1913-3` Phase 1/3）

> P1-6（识别性占位符漏传 11 活点）+ P1-7（方言白名单键错配）+ P1-9（INV-LIMIT 假绿行）修复后沉淀，落地为零命中 hard-gate `ai-dev/tools/check-error-param-consistency.mjs`（guard 6，2026-08-16 起 15 violations + 4 UNRESOLVED → 0 + 豁免面显式列出）。

### INV-ERROR-PARAM — 错误消息识别性参数占位符↔调用点一致性族

**① 陈述**（通用）：`NopMetadataException`（及同族 `NopException`）throw 点所用 ErrorCode 描述中声明的**识别性占位符** `{xxx}`，必须在同一构造语句链（含 builder 变量形态 `X e = new ...; e.param(...)`）上有对应 `.param()` 键——键可为字面量 / `NopMetadataErrors.ARG_X` / 裸 `ARG_X` 常量。违反时运行时（`ErrorMessageManager`）把占位符渲染为字面 `{xxx}`——失败对象的身份信息对最终用户丢失。**禁止**：传 null 凑键覆盖（渲染空串 = 空壳修复）；错误码为变量（方法参数/局部变量）时不经人工归类静默放过（UNRESOLVED 清单强制裁定或 `// invariant-ok:` 标注）。豁免面（2026-08-16 起）仅 `// invariant-ok:` 显式裁定——原 `{error}` 附注族豁免已随 P2-09 收口（7 处 throw 点补齐 `.param(ARG_ERROR, NopMetadataHelper.toErrorMessage(e))` 或文件内 messageOf 等价形态）。

**② 覆盖的失败族**：error-param 族——P1-6 识别性参数漂移家族（11 活点跨 9 文件）→ P1-7 方言白名单参数键错配（`{datasourceType}` 声明 vs `databaseProductName` 键，被拒产品名永不渲染）。

**③ 历史 audit-finding-ID 证据**：

- P1-6（2026-08-15 multi-audit confirmed live defect，fixed 2026-08-16）：11 活点三轨收口——轨 1 补齐（JoinBizModel:164 update 路径 joinId 下沉 / FieldResolver:383 elementIndex 下标循环 / MemoryFilterEvaluator:86 name 键）、轨 2 穿参（MetaTableQueryExecutor / AggregationHelper.requireName / CrossDbJoinMerger.firstNonNullKeyType 静态工具签名 +metaTableId/+joinId）、轨 3 换码（null 防御/值语义不存在分支新增 5 个无必需占位符错误码；削既有码占位符被"其他点位已传齐"裁定禁止）（`ai-dev/audits/2026-08-15-0559-multi-audit-nop-metadata-invariant-loop.md` P1-6）
- P1-7（同 audit confirmed contract drift，fixed 2026-08-16 方案 A：改传 `ARG_DATASOURCE_TYPE` 键 + 被拒产品名，错误码标识不变；`MetaDataSourceConnectionProcessor:216` 正确用法为先例）
- 防复发土壤：~~P2-10（17 个 define 声明与描述占位符漂移）~~（fixed 2026-08-16，plan `2026-08-16-0226-2` Phase 1：17 处对称差收敛 + 7 死码删除，define 面规则沉淀入 guard 6）+ P2-11（`.param()` 键字面量 vs ARG_* 双轨，已裁定 optimization candidate deferred）。

**④ 检测方法**：`ai-dev/tools/check-error-param-consistency.mjs`——解析 10 个 `*Errors.java`（经 `NopMetadataErrors` 组合）+ `NopMetadataArgs.java` 构建注册表；对 main 范围全部 `new NopMetadataException(` throw 点（含内联 `ErrorCode.define` 与 builder 变量形态）做占位符↔键交叉核对；catch 块 `e.param(...)` 重抛增补天然不命中（仅扫构造点）；变量形态错误码输出 UNRESOLVED 清单强制人工归类（未标注即红）。注毒自验：删除已知修点的 `.param` → 红；恢复 → 绿（2026-08-16 live 实测）。
**define 面扩展（P2-10，plan `2026-08-16-0226-2` Phase 1，2026-08-16 落地）**：新增两条规则——(1b) define 声明面对称差：每个 define 尾部声明的 ARG_* 常量**值**（经注册表解析，常量名 UPPER_SNAKE vs 值 camelCase，禁止名字比对）必须与描述占位符集合完全一致（对称差为空）；声明键无占位符 = throw 点传的参数永不渲染，占位符无声明 = define 面空洞。(1c) 死码检测：define 在模块 src/main（注释剥离语料，排除定义文件自身）零引用即红（`dead` = 全仓零引用；`test-only` = 仅测试镜像保活）；豁免走 define 行 `// invariant-ok:`（豁免清单形态，沿 check-silent-wrong-result baseline 先例），豁免面在输出中显式列出。fixture 自检覆盖对称差三形态（漏声明/漏占位符/不可解析 ARG）+ 死码三分类（dead/test-only/alive）。

**目标集覆盖率**：342 throw 点 / 226 ErrorCode define（2026-08-16 首跑）；修复后零命中、豁免面 6 条（2 死点 P2-23 + 4 变量形态人工归类）全部显式列出。define 面扩展首跑（2026-08-16）：17 对称差 + 7 死码（4 dead + 3 test-only）→ 全部修复/删除后零命中。

---

### INV-LIMIT 增补注记（P1-9 假绿行修复，plan `2026-08-15-1913-3` Phase 3）

INV-LIMIT 门禁自身的守卫测试曾存在区分力为零的假绿行：`inline-searchMetadata` 行只断言 `assertThrows(NopException.class)`——移除 `NopMetaSearchBizModel` 负 limit 检查后异常变为 `ERR_SEARCH_ENGINE_UNAVAILABLE`，宽断言仍绿。修复（2026-08-16）：4 行全部钉精确错误码（`nop.err.metadata.search-limit-invalid` / `nop.err.metadata.pagination-limit-invalid`），变异验证实证——临时移除负 limit 检查 → 该行变红（`expected: <search-limit-invalid> but was: <search-engine-unavailable>`），其余 3 行不受影响；恢复后全绿。教训入本目录：**守卫测试自身须按"移除被守卫检查后必须变红"标准变异验证，宽断言（仅类型）对错误码漂移零区分力**。

---

### KEYWORD_BLACKLIST 函数形态豁免裁定（P2-04，plan `2026-08-16-0226-1` Phase 4）

`ExpressionMeasureValidator.scanBlacklist` 对 FUNCTION_CALL token **只查 FUNCTION_BLACKLIST**（不查 KEYWORD_BLACKLIST）是**有意设计非漏洞**：KEYWORD_BLACKLIST 26 条目逐条枚举核对（2026-08-16 live），存在 callable 函数同形词的仅 `REPLACE`（字符串替换）/ `TRUNCATE`（数值截断）/ `INSERT`（MySQL 字符串函数）三项，其余 23 条目均为纯语句/子句/锁关键字（函数形态的 GET_LOCK/RELEASE_LOCK 是不同名条目，已在 FUNCTION_BLACKLIST）；三项同形词的函数调用形态是 SELECT 表达式合法用法（表达式上下文无 DML/DDL 逃逸路径：聚合包裹、`;`/注释在 tokenize 显式拒绝、字面量参数化完整），语句形态（`REPLACE INTO`/`TRUNCATE TABLE`/`INSERT INTO`）以 IDENTIFIER token 命中 KEYWORD_BLACKLIST 被拒。钉死测试：`TestExpressionMeasureValidator#testP204FunctionHomographsAllowed`（3 函数过）/ `#testP204StatementFormsStillRejected`（3 语句拒）/ `#testP204FunctionBlacklistStillRejected`（FUNCTION_BLACKLIST 拒 3）——防未来"修复"破坏合法用法（over-block 误伤）或审计者重复误报；勿在 scanBlacklist 添加 FUNCTION_CALL × KEYWORD_BLACKLIST 交叉检查。裁定全文见 `ExpressionMeasureValidator.KEYWORD_BLACKLIST` javadoc。

---

## 候选不变式（Non-Blocking Follow-up，留待 I6 裁定）

> Phase 1 枚举未发现超出首批 4 族的高频复发模式需立即沉淀。以下为低频观察，不展开，留待 I6 统计后裁定是否派生 Cycle 2 / I1。
>
> **2026-08-15 重估（plan `2026-08-15-0820-1` Phase 1 D2）**：两条候选经重估均**维持 watch-only**，裁定与理由如下——

- **类型/方言兼容性族**（AR-20 NULLS FIRST/LAST、AR-23⑧）：**维持 watch-only**。理由：① 复发证据——2026-08-13 至 2026-08-15（含 Cycle 2 再审计 0707/1133 两轮 + 本计划 Phase 1 全量站点扫描）未见新的同族兄弟命中，仍为单点命中且均已修复，未形成先例链；② 可机械化性——方言能力差异检测需运行时 DB 元数据（`DatabaseMetaData` / 版本探测），静态扫描不可机械化，JUnit 穷举需逐方言活库夹具（非确定性、维护成本高）；③ 替代防护——既有 `SUPPORTED_DIALECTS` exact-set 检查（`EntityAggregationProcessor` 等）已是门禁式 fail-fast，新增方言不入表即拒绝；周期对抗探查复探兜底。
- **并发竞态/UK 幂等族**（P2-MA3-03 upsertExternalTable、2026-07-20 RACE）：**维持 watch-only**。理由：① 复发证据——2026-08-13 至今零新竞态发现；② 兜底现状——DB unique-key 已 fail-loud 兜底（约束冲突显式报错非静默错算），且 INV-UK 门禁守护 constraint 完备性（新增 UK 缺 `constraint` 即 CI 红）；③ 可机械化性——竞态穷举需并发压力夹具，结果非确定性，静态扫描与 JUnit 穷举均不可机械化。

---

## 引用

- `audit-target-set.md`（目标集 + 覆盖率基准）
- `ai-dev/audits/arm-index-nop-metadata.md`（全部 finding-ID 可定位）
- `ai-dev/lessons/09-ddl-unique-key-silent-absence.md`（INV-UK lesson）
- `ai-dev/skills/invariant-loop-audit-prompt.md`（方法论 + 棘轮规则关键机制 #2）
- `ai-dev/backlog/nop-metadata-invariant-loop-roadmap.md`（I0/I1 规格）
