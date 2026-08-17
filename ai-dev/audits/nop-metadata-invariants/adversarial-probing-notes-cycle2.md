# nop-metadata 不变式 Cycle 2 审计 — 盲区对抗探查笔记（Adversarial Blind-Spot Probing）

> 产出方：plan `2026-08-15-0820-2`（Cycle 2 / I2' — 不变式驱动审计，Phase 1 A2/A3）
> 实测日期：2026-08-15（live repo，对抗性 rg/读码核查）
> 方法论：`ai-dev/skills/invariant-loop-audit-prompt.md` 步骤 0 + open-ended 对抗审查精神
> 上游：`invariant-catalog.md`（检测规则与 watch 边界声明）、`audit-target-set.md` §5（目标集）、`formal-red-list-cycle2.md`（Phase 1 A1 正式 red list）
> 下游消费者：I3' 裁决（`adjudication-table-cycle2.md` — 对抗探查结论决定是否登记新族 → Cycle 3 / I1）
> 与 Cycle 1 `adversarial-probing-notes.md` 并列，不覆盖不追加。

## 目的与方向数核算

门禁是模式驱动的确定性扫描，catalog 显式声明的 watch 边界与 watch-only 族不在门禁覆盖内，由本节对抗性人工核查补盲。

**方向数核算**（plan Phase 1 A2）：分母 = 5 个 silent-wrong-result 子族中 I1' 裁定为 watch-only 者（**0**，I1' Phase 1 D1 五族全部裁定静态扫描可机械化）+ I1' D2 重估后维持 watch-only 的候选族（**2**：类型/方言兼容性族、并发竞态/UK 幂等族）= 2。要求方向数 = max(3, 2) = **3 起**，其中 2 个 watch-only 族各须 1 个专属方向。本轮实际执行 **10 方向**：2 专属 + Current Baseline 候选清单全 6 项（a/b/c/d/e/f，无排除项）+ catalog 声明 watch 边界 3 项（narrowing-cast 浮点方法调用操作数 / contains-classify 不可判定 receiver / delim-key lambda extractor）中前 2 项单列、第 3 项并入方向 9 + 跨子模块 1 项。

> **结论先行**：10 方向全部"已探查"，**0 个 live 新族命中、0 条并入 `formal-red-list-cycle2.md`**。1 项 latent-form 观察（方向 6，`MetaTableProfiler.toLong:548`）经调用面核查**当前不可达**，不构成 live 缺陷与新族，登记为 watch-only 观察（roadmap backlog 收录）。逐方向证据见下。

---

## 方向 1 —【专属：类型/方言兼容性 watch-only 族】方言特定 SQL 发射的兄弟站点

**探查方法**：rg 全模块扫方言特定 SQL 构造（NULLS FIRST/LAST、LIMIT/OFFSET 方言变体、DATE_FORMAT/DATE_TRUNC、SUPPORTED_DIALECTS 门），核查每个发射点是否有 exact-set fail-fast 门禁（catalog D2 重估记载的替代防护），并与 AR-20（NULLS FIRST/LAST）、AR-23⑧（方言兼容）已知修复点比对找兄弟。

**覆盖面/实测（live）**：

- `SUPPORTED_DIALECTS` exact-set fail-fast 门：`MetaAggregationExecutor.java:56`、`AggregationContext.java:23`、`GranularityBucketing.java:26`、`SqlViewFieldTypeInferrer.java:80` + 4 个 processor 消费点（`ExternalAggregationProcessor.java:71`、`MixedSameDbJoinAggregationProcessor.java:126`、`ExternalExternalJoinAggregationProcessor.java:92`、`EntityAggregationProcessor.java:180`）——新方言不入表即拒绝，防护在位。
- NULLS FIRST/LAST：仅 `AggregationHelper.java:317-364`（AR-20a 修复点：MySQL 无此语法时省略或显式 fail-fast，注释与 `AggregationErrors.java:141` 错误码在位）+ 2 处 lambda 内消费（`MixedSameDbJoinAggregationProcessor.java:131`、`ExternalExternalJoinAggregationProcessor.java:97`，均注明 AR-20a）。
- LIMIT/OFFSET 方言差异：仅 `SqlPagination.java`（AR-04 修复点：MySQL offset-only 补 `LIMIT 18446744073709551615`，H2/PG 保持 OFFSET；`MYSQL_MAX_LIMIT` 常量 :43）。
- 分桶模板：`GranularityBucketing.java:30-51` —— H2_PG_TEMPLATES / MYSQL_TEMPLATES 按 `SUPPORTED_DIALECTS` 门内分流（DATE_TRUNC vs DATE_FORMAT），无门禁外发射。

复现：`rg -n 'NULLS\s+(FIRST|LAST)|SUPPORTED_DIALECTS' nop-metadata/nop-metadata-service/src/main/java`

**结论**：**0 兄弟命中**。全部方言特定发射点都在 SUPPORTED_DIALECTS exact-set fail-fast 门内或为已修复先例（AR-04/AR-20a/AR-10 分桶注释在位），维持 watch-only 的三重理由（零新复发 + 不可机械化 + 替代防护在位）经本轮再证实。

---

## 方向 2 —【专属：并发竞态/UK 幂等 watch-only 族】静态可变状态与 check-then-act 兄弟

**探查方法**：rg 扫静态可变集合字段（static HashMap/ArrayList/HashSet/SimpleDateFormat）、upsert/check-then-act 模式（findFirst==null 后 save），逐个核查初始化后是否再写、是否有锁/独立事务防护，并与 P2-MA3-03（upsertExternalTable）已知修复点比对找兄弟。

**覆盖面/实测（live）**：

- 静态 Map 3 处：`GranularityBucketing` H2_PG_TEMPLATES/MYSQL_TEMPLATES（:30-51，仅 static 块内 put，读侧仅 get——类初始化安全发布，初始化后零写入）；`MetaQualityScorer.DEFAULT_WEIGHTS`（:70-73，同形态，读侧仅 :182 get）。零实例级/运行时写入 → 无竞态面。
- upsert 竞态：`NopMetaDataSourceBizModel` upsertExternalTable 族（:477/:547）——AR-07 修复形态在位（`EXTERNAL_TABLE_UPSERT_LOCKS` per-key 锁 ConcurrentHashMap :540 + REQUIRES_NEW 独立事务 + :533 注释显式记录 READ_COMMITTED 不可见竞态的防护理由）。该锁 map 按 key 增长有界（表数量级），benign。
- 其余 `findFirstByQuery` 命中点均无"查后插"写入路径（查询/校验用途）。

复现：`rg -n 'static.*(HashMap|ArrayList|HashSet|SimpleDateFormat)\s*=' nop-metadata/nop-metadata-service/src/main/java`；`rg -n 'upsert' nop-metadata/nop-metadata-service/src/main/java`

**结论**：**0 兄弟命中**。静态可变集合全部为 static-init-only 安全形态；唯一 upsert 竞态点已是 AR-07 修复形态且有 DB UK fail-loud 兜底（INV-UK 门禁守护）。维持 watch-only 再证实。

---

## 方向 3 —【候选 a】toUpperCase 与 toLowerCase 覆盖对称性

**探查方法**：门禁 locale 规则为 `\.(toLowerCase|toUpperCase)\(\)` 双 pattern（扫描器源码规则同体）；对正式 red list 40 命中按方向分型。

**实测**：40 = toLowerCase **33** + toUpperCase **7**（`MetaTableProfiler.java:461/:557/:565/:605/:632`、`CheckpointActionDispatcher.java:232`、`MetaQualityRuleExecutor.java:387`）。两方向同规则、同剥离口径、同 baseline 键归一化——**对称覆盖，无单侧盲区**。

**结论**：0 发现（已探查、未发现；证据 = 正式 red list §1 分型）。

---

## 方向 4 —【候选 b/c/d/f】String.format / equalsIgnoreCase / String.CASE_INSENSITIVE_ORDER / Collator 默认 locale 变体

**探查方法**：rg 逐项扫四种 locale 相关变体的使用与形态。

**实测（live）**：

- `String.format(`（无 Locale 参数形态）：service main **0 站点**——无 locale 敏感格式化面。
- `equalsIgnoreCase`：27 站点 / 12 文件（AutoClassificationProcessor 1、ExpressionMeasureValidator 4、AggregationHelper 5、CrossDbFieldResolver 6、CrossDbJoinMerger 2、GranularityBucketing 1、JoinExternalSideResolver 2、JoinFieldResolver 1、JoinMixedSideResolver 2、MemoryFilterEvaluator 1、MemoryOrderByComparator 1、LocalReconciliationProcessor 1）。JDK 语义 = `Character.toUpperCase/toLowerCase(char)` 逐字符比较，**不经过默认 locale 的 String 大小写映射**（catalog INV-LOCALE 陈述明示其为 locale-insensitive 合法形态）——全部安全。
- `String.CASE_INSENSITIVE_ORDER`：1 站点（`CrossDbJoinMerger.java:219`，TreeSet comparator）。该 comparator 同样基于 Character 级映射（JDK javadoc），locale-insensitive——安全。
- `Collator`：**0 站点**——无默认 locale 敏感排序面。

复现：`rg -n 'String\.format\(|CASE_INSENSITIVE_ORDER|Collator' nop-metadata/nop-metadata-service/src/main/java`；`rg -c 'equalsIgnoreCase' ...`

**结论**：0 发现（四变体逐一探查；equalsIgnoreCase/CASE_INSENSITIVE_ORDER 的 Character 级映射安全性有 JDK 语义与 catalog 陈述双重依据）。

---

## 方向 5 —【catalog watch 边界】contains-classify 不可判定 receiver（方法调用返回值）

**探查方法**：rg 扫 `xxx().contains(` 形态（receiver 为方法调用，静态类型不可判定故门禁不命中），人工读码判定 receiver 实际类型。

**实测**：2 站点——`CheckpointActionDispatcher.java:300`（`resolveAllowedWebhookHosts().contains(...)`）与 `MetaDataSourceConnectionProcessor.java:265`（`resolveAllowedInternalHosts().contains(...)`）。读码：两方法返回 `Set<String>`（:122-134 / :117-134 构建 HashSet）——**集合 receiver 全等成员查询**，本就不在 INV-CONTAINS-CLASSIFY 禁止范围（String 子串分类）。

复现：`rg -n '[a-zA-Z_$][a-zA-Z0-9_$]*\(\)\.contains\(' nop-metadata/nop-metadata-service/src/main/java`

**结论**：0 发现（watch 边界内无 String-receiver 漏网）。

---

## 方向 6 —【候选 e】数值解析静默 NFE 路径（AR-02 同族兄弟）

**探查方法**：rg 扫 `Integer.parseInt|Long.parseLong|Double.parseDouble|Float.parseFloat`，逐点核查 NFE 处置（裸逃逸 / ErrorCode / DEBUG+benign-miss formalize）与数字前置校验，与 AR-02（SLA NFE 逃逸，已修 ErrorCode 形态）比对找兄弟。

**实测（live，16 站点）**：

- **已修/合规形态 15 处**：`MetaContractChecker.java:345`（NFE→`ERR_CONTRACT_SLA_INVALID`，AR-02 修复形态）；`MetaQualityRuleExecutor.java:756-768`（NFE→`ERR_QUALITY_EXPECT_PASS_WHEN_INVALID`，AR-11 修复形态）与 `:875`（NFE→DEBUG+null，benign-miss formalized）；`AggregationHelper.java:564`（NFE→DEBUG+null，1448-2 formalized）；`MetaTableProfiler.java:497/:517`（NFE→DEBUG+null）；`NopMetaReconciliationResultBizModel.java:163`（NFE→ErrorCode）；`MetaDataSourceConnectionProcessor.java:564`（isdigit 逐字符前置校验 + NFE→DEBUG+return false fail-closed）；`HostSecurityUtil.java:271`（NFE→DEBUG+落入后续检查 fail-closed）。
- **latent-form 观察 1 处**：`MetaTableProfiler.java:548`（`toLong`：`s == null ? 0L : Long.parseLong(s.trim())` —— null→0L 伪造形态 + parseLong 裸 NFE 逃逸形态）。**可达性核查**：`toLong` 唯一调用链 = `queryLong(:475/:482)`，而 `queryLong` 的全部 5 个调用点（:176/:182/:184/:190/:429）均为 `SELECT COUNT(...)` 族查询——COUNT 语义下结果恒非 null 恒整数，**两风险路径当前均不可达**（javadoc :536 声明的"H2 NUMERIC 按 String 返回"回退场景在 COUNT 面不触发非整数文本）。**不构成 live 缺陷**，登记 watch-only 观察（roadmap backlog 收录，源路径见本文件）；若未来 queryLong 接入 SUM/MIN/MAX 等可空/非整数聚合，须先补 ErrorCode 化。

复现：`rg -n 'Integer\.parseInt|Long\.parseLong|Double\.parseDouble' nop-metadata/nop-metadata-service/src/main/java`；`rg -n 'queryLong\(|toLong\(' nop-metadata/.../MetaTableProfiler.java`（调用面核查）

**结论**：0 live 兄弟命中；1 latent-form 观察已登记（watch-only，不阻塞）。

---

## 方向 7 —【catalog watch 边界】narrowing-cast 浮点方法调用操作数

**探查方法**：rg 扫全部 `(long|int|short)` 强转形态（含方法调用 receiver 形态 `(long) xxx()` —— catalog INV-NARROW ④ 声明的静态不可判定边界），人工分型 widening / 整值函数 / 括号正确形态 / 违规。

**实测（live，11 候选形态）**：`MetaJoinExecutor.java:448`（`(long) intConfig + 1` widening）、`MetaTableProfiler.java:371/:372`（`(int) Math.floor/ceil(rank)`——floor/ceil 返回数学整值，强转无损）、`:409`（`(int) ((v-min)/width)` 括号完整）、`CrossDbJoinMerger.java:270` / `AggregationHelper.java:893`（`(int) Math.min(...)` 整值函数）、`AggregationContext.java:374`、`NopMetaTableBizModel.java:406/:434`（int 常量 widening）、`MetaContractChecker.java:384/:395`（AR-01 修复形态，括号在）。**0 违规**。

复现：`rg -n '\((long|int|short)\)\s' nop-metadata/nop-metadata-service/src/main/java --glob '*.java'`

**结论**：0 发现（watch 边界内无浮点方法调用操作数后随算术的违规形态；防回退零点维持）。

---

## 方向 8 —【catalog watch 边界 + 候选补充】BigDecimal 直构与 doubleValue 其他中转路径

**探查方法**：rg 扫 `new BigDecimal(` 全形态与 `.doubleValue()` 全站点，排除已 red-list 的 2 处与受保护形态后逐点分型。

**实测（live）**：`new BigDecimal(BigInteger)` ×2（`MemoryOrderByComparator.java:129`、`MemoryFilterEvaluator.java:353`——BigInteger 构造精确无损）；`AggregationHelper.java:554`（受保护正确形态，longValue 路由）；非 BigDecimal 路径的 `.doubleValue()` ×3：`MetaContractChecker.java:341`（SLA amount→double，AR-01 裁定域内）、`MetaQualityScorer.java:181`（质量维度分 0..1 double 语义域）、`MetaQualityRuleExecutor.java:872`（规则参数 getDouble，double 语义域）。**0 新违规**（int>2^53 精度风险仅存在于经 BigDecimal 中转的比较/聚合路径，即已 red-list 的 2 处）。

复现：`rg -n 'new BigDecimal\(|\.doubleValue\(\)' nop-metadata/nop-metadata-service/src/main/java`

**结论**：0 发现（watch 边界清洁；red list 2 处即为该族全量）。

---

## 方向 9 —【catalog watch 边界】delim-key lambda extractor 内联拼接 + String.join 键用途

**探查方法**：rg 扫 `groupingBy(|toMap(|String.join(`，逐点核查拼接产物是否作 map/set 键（catalog INV-DELIM-KEY ④ 声明的 lambda 流分析盲区）。

**实测（live）**：`String.join` 10 站点全部为 SQL 子句拼接（`GROUP BY` 表达式列表 ×7、`FilterToSqlTranslator.java:172` 过滤条件拼接、错误消息 ×1）或 DTO 转换（`toMap()` 为 Map 构建器方法名，非流 key extractor）；`groupingBy` 零命中（AR-03 修复点 `AggregationHelper.memoryGroupBy` 已改结构性 `List<Object>` 键，不在拼接形态）。**0 违规**。

复现：`rg -n 'groupingBy\(|toMap\(|String\.join\(' nop-metadata/nop-metadata-service/src/main/java`

**结论**：0 发现（lambda extractor 盲区清洁）。

---

## 方向 10 — 跨子模块盲区（gate 目标面完整性）

**探查方法**：对 gate 目标面（`nop-metadata-service/src/main/java`）之外的 nop-metadata 子模块（api/core/dao/app/web）rg 扫五子族代表 pattern（locale 大小写、BigDecimal.valueOf+doubleValue），核查门禁 scope 是否漏扫。

**实测（live）**：api/core/dao/app/web 全部 **0 命中**（含 `_gen` 排除口径）。门禁 scope = 目标集 = 实际风险面，无跨子模块盲区（与 Cycle 1 方向 4 catch 全集中 service 的结论同构）。

复现：`rg -n '\.toLowerCase\(\)|\.toUpperCase\(\)|BigDecimal\.valueOf.*doubleValue' nop-metadata/nop-metadata-{api,core,dao,app,web}/src --glob '*.java' | grep -v _gen`

**结论**：0 发现（scope 完整）。

---

## 汇总：对抗探查结论一览

| 方向 | 探查问题 | 方法 | 结论 | 并入 red list / 触发新族 |
|------|----------|------|------|------------------------------|
| 1【专属：方言族】 | 方言特定 SQL 无门发射兄弟 | rg 全扫 + 逐点读码 | 全部在 SUPPORTED_DIALECTS 门内或为 AR-04/AR-20a 修复点 | 否 |
| 2【专属：竞态族】 | 静态可变状态 / check-then-act 兄弟 | rg 全扫 + 写入点核查 | static-init-only 安全；upsert 已是 AR-07 修复形态 | 否 |
| 3【候选 a】 | toUpperCase 对称覆盖 | red list 分型 | 33/7 双向同规则覆盖，对称 | 否 |
| 4【候选 b/c/d/f】 | String.format/equalsIgnoreCase/CASE_INSENSITIVE_ORDER/Collator | rg 逐项 | 0/25（Character 级安全）/1（安全）/0 | 否 |
| 5【watch 边界】 | contains 不可判定 receiver | rg + 读码定型 | 2 处均为 Set receiver，出界正确 | 否 |
| 6【候选 e】 | 静默 NFE 兄弟（AR-02） | rg 16 点逐一核查 | 15 合规 + **1 latent-form 观察（toLong:548，COUNT-only 不可达）** | 否（观察登记 backlog） |
| 7【watch 边界】 | narrowing 浮点方法调用操作数 | rg 11 候选分型 | 全部 widening/整值/括号正确形态 | 否 |
| 8【watch 边界】 | BigDecimal 直构/doubleValue 其他路径 | rg 全站点分型 | BigInteger 精确 ×2 + 受保护 ×1 + double 语义域 ×3 | 否 |
| 9【watch 边界】 | lambda extractor / String.join 作键 | rg + 用途核查 | 全部 SQL 拼接/消息，零键用途 | 否 |
| 10 | 跨子模块盲区 | rg api/core/dao/app/web | 0 命中，scope 完整 | 否 |

**对抗探查总结论**：10 方向均**无 live 新族命中 / 无门禁漏扫 / 无目标集击穿**。`formal-red-list-cycle2.md` 的 67 项命中即为 I3' 裁决的完整输入，**对抗探查未并入任何新条目**。本轮**0 新族**，不触发 Cycle 3 / I1 派发。Current Baseline 候选清单 6 项全部本轮探查完毕，无剩余未选项（无"下轮复探候选"遗留；方向 6 的 latent-form 观察单独登记 backlog）。

> **Anti-Hollow 自检**：每方向有明确结论（方法 + 覆盖面 + 证据命令），0 发现方向均附"已探查、未发现"的具体证据，无"未发现问题"空话。方向 6 的可达性结论基于调用链核查（queryLong 5 调用点全为 COUNT 族）而非猜测。

---

## 引用

- `formal-red-list-cycle2.md`（Phase 1 A1 正式 red list —— 本文件证明其无对抗新增）
- `invariant-catalog.md`（检测方法与 watch 边界声明 —— 方向 4/5/7/8/9 的核查基准；候选节 D2 重估 —— 方向 1/2 的比对基准）
- `ai-dev/backlog/nop-metadata-invariant-loop-roadmap.md`（Follow-up Backlog —— 方向 6 latent-form 观察登记处）
- `ai-dev/skills/invariant-loop-audit-prompt.md`（方法论，步骤 0 + 对抗审查精神）
