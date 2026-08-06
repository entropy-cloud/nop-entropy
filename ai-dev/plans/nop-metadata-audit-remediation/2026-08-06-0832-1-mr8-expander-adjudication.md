# MR8 展开器裁决（R8.0：Follow-up Backlog AR-11~23 逐项裁决——提级安全/正确性类 + 终局归类其余）

> Plan Status: completed
> Last Reviewed: 2026-08-06
> Mission: nop-metadata-audit-remediation
> Work Item: MR8（R8.0 展开器）
> Source: `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`（Follow-up Backlog AR-11~23 段）、`ai-dev/audits/2026-08-05-2157-open-audit-nop-metadata-audit-remediation.md`（AR-11~23 P2 发现）、`ai-dev/audits/arm-index-nop-metadata.md`（§P2 追踪）
> Related: 执行顺序 `{1}` — 启动门禁：MR7 全部行 done + roadmap header v26 收口（MR7 R7.3 2026-08-06 完成）；本 plan 为**裁决文档计划**（roadmap 规则 1 的显式豁免通道，沿 MR6 R6.0 先例：「P2 不驱动 remediation plan」经裁决器显式豁免——安全与正确性类提级为 P1 执行），产物 = 裁决记录 + roadmap MR8 段 + arm-index §P2 更新；后续 R8.x 修复 plan 在本 plan 完成后另行起草（Deps = R8.0 done）。

## Purpose

对 Follow-up Backlog 剩余 13 条（AR-11~23，来源 `2026-08-05-2157-open-audit`，全部 `backlog` 无终态）逐项裁决：(a) **提级安全/正确性类为 P1 修复**（候选依据审计证据：AR-16 敏感 SQL 日志；AR-11/12/13/14/15/18/19/20/21/22 正确性与诊断性缺陷；AR-23 杂项批子项逐个甄别），提级结果归属 roadmap MR8 段新增的 R8.x 修复工作项行；(b) **其余终局归类**（watch-only residual / out-of-scope improvement / docs batch），每项附 Why Not Blocking Closure；(c) 裁决记录写入 roadmap MR8 段 + arm-index §P2，R8.0 → done，解除后续 R8.x 修复 plan 的 Deps 门禁。

## Current Baseline

经 2026-08-06 live repo 核对（finding 描述以审计报告为准；裁决依据以本 plan 执行时的 live 复核为准）：

- **roadmap 全部 MR1~MR7 / MV / MG 行 done**；Follow-up Backlog P2-01~27 + AR-06~10 共 32 条经 MR6 全部终态（12 提级已修复 + 20 终局归类）；**AR-11~23 共 13 条仍 `backlog`，无一终态**，是本路线图唯一剩余工作面
- 绿色基线：`./mvnw test -pl nop-metadata -am -T 1C` **970/0**（MR7 R7.3 收口口径，2026-08-06）
> 行号注记：下列行号沿用审计报告原始引用，2026-08-06 独立复核已确认多数漂移（AR-11 现 `MetaQualityRuleExecutor.java:725-733` / AR-13 现 `:720` / AR-15 freshness 现 `:215-257` 与 `ageMinutesFromNow:735` / AR-16 现 `:631,:647,:669` / AR-22 现 `MetaContractChecker.java:362-364`）；裁决依据一律以执行时的 live 代码为准。

- **提级候选（审计证据摘要，执行时逐条 live 复核）**：
  - 安全类 1 条：AR-16（`MetaQualityRuleExecutor.java:616,632,654`（现 :631,:647,:669）完整 SQL 含 custom_sql 字面量以 INFO 级写日志——R6.2 rawJdbcUrl 同类面）
  - 正确性/诊断类 10 条：AR-11（`MetaQualityRuleExecutor.java:710-718`（现 :725-733）judgeRegex 子串启发式过宽，MySQL 真实正则错误被误判 SKIP 静默消失）、AR-12（`MetaQualityCheckpointScheduler.java:201-207` cpId==null 在 try 外抛 ERR_CHECKPOINT_SCHEDULER_INVALID_CRON，违反 MA7.5-01 不外抛契约 + 错误码不符）、AR-13（`QualityErrors.java` `{ruleKey}` 占位恒无法解析 + `MetaQualityRuleExecutor.java:701-706`（现 :720）expectPassWhen 错误上下文为字面量占位符）、AR-14（`MetaQualityCheckpointExecutor.java:155-163` 抛异常规则不写 ERROR 结果行 → `MetaQualityScorer.java:129-145` autoScore 复用陈旧结果 + `CheckpointExecutionResultDTO` 从不填充 totalRuleCount/ruleResults 且缺 skipCount 字段）、AR-15（`MetaQualityRuleExecutor.java:720-725,238-241`（freshness 现 :215-257 + ageMinutesFromNow :735）负年龄恒 PASS）、AR-18（`OrmModelImporter.java:208-216` 手拼 JSON 不转义 + `MetaTableFieldResolver.java:340-359` buildSql 反序列化未类型化裸 ClassCastException）、AR-19（`MemoryFilterEvaluator.java:101-110,223-246,171-181` LIKE 正则转义缺失 / NULL 比较相反 / 空 or 节点相反 vs FilterToSqlTranslator 语义漂移）、AR-20（`AggregationHelper.java:342-345` MySQL 上无条件拼 NULLS FIRST/LAST + `CrossDbJoinMerger.java:122-159` join 键精确类比较 INT vs BIGINT 误拒）、AR-21（`AutoClassificationProcessor.java:260-271,218-228` + `LineageTagPropagationProcessor.java:177-188` catch-all 吞掉 R6.4 fail-loud + 全局回退 lexicographically-first）、AR-22（`MetaContractChecker.java:353-355`（现 :362-364）SLA 未知时间单位静默按毫秒解析）
- **归类候选**：AR-17（R6.3 per-key 锁 + REQUIRES_NEW 改变 syncExternalTables 原子性契约未文档化——docs/契约裁定项，可能需代码修复）、AR-23（杂项批 10 子项，逐项单独裁定：① delete 先摘 cron 后提交〔删除失败丢调度，`NopMetaQualityCheckpointBizModel.java:265-292`〕② 索引重建不清陈旧文档〔`NopMetaIndexBuilder.java:100-112`〕③ 索引 refresh 失败仅 warn〔同文件 :39-120〕④ 搜索 limit 负数直通引擎〔`NopMetaSearchBizModel.java:66-82`〕⑤ ExternalTableStructureReader 扫描异常统一报 ERR_DIALECT_NOT_SUPPORTED + NULL 精度归 0〔`ExternalTableStructureReader.java:81-84,155-158`〕⑥ reconciliation 每行全量候选池 + 无长度上限 levenshtein〔`LocalReconciliationProcessor.java:92-102,137-163`〕⑦ profiler 整列载入内存〔`MetaTableProfiler.java:260-275`〕⑧ 模块版本 read-then-insert 无唯一约束竞态〔`NopMetaModuleBizModel.java:545-558`（审计引用，live 竞态在 computeNextManifestVersion :591 + saveEntity :608，执行时以 live 复核为准）〕⑨ manifest 跨 DRAFTING 模块解析〔`MetaManifestBuilder.java:71-78`〕⑩ 事件快照 Map 分支跳过敏感列脱敏〔`MetaModelChangedEventPublisher.java:202-203`〕——正确性/安全类子项可提级，性能类按 live 证据归类）

## Goals

- 13 条（AR-11~23）逐项终态：提级（归属 roadmap MR8 段新增 R8.x 行）+ 终局归类，0 悬置
- 每条提级附 live 核实依据（代码路径 + 影响面）；每条归类附 Why Not Blocking Closure（依据 = 无消费方 / 非 live defect / 破坏性变更 / 无实际暴露 / 低概率自愈 / 纯文档，沿 MR4 与 MR6 先例）
- roadmap MR8 段（含 R8.0 裁决记录 + 新增 R8.x 行）+ arm-index §P2 同步更新，双向可追溯
- R8.0 → done，解除后续 R8.x 修复 plan 的 Deps 门禁

## Non-Goals

- 不执行修复（修复属后续 R8.x 各 plan，在本 plan 完成后另行起草）
- 不重新审计 Backlog 内容本身（以 2157 审计报告为输入，仅做终态裁决；live 复核仅用于确认/修正裁决依据，不扩面）
- 不修改 Follow-up Backlog 登记表结构（保留登记批次与来源路径；终态记录走 roadmap MR8 段 + arm-index §P2）

## Execution Plan

### Phase 1 - 提级候选 live 核实与裁决

Status: completed
Targets: `ai-dev/audits/2026-08-05-2157-open-audit-nop-metadata-audit-remediation.md`（AR-11~16/18~22）+ 相关源码 + `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`（MR8 段创建）

- Item Types: `Decision | Proof`

- [x] 核实安全类 1 条（AR-16：`MetaQualityRuleExecutor` 三处 LOG.info 是否确含完整 SQL/custom_sql 字面量、有无 sqlHash 替代或 DEBUG 降级先例），确认或修正提级依据
- [x] 核实正确性/诊断类 10 条（AR-11/12/13/14/15/18/19/20/21/22 的 live 行为与影响面：SKIP 误判触发条件、try 边界与错误码、占位符渲染路径、陈旧结果复用链、负年龄路径、JSON/类型转换崩溃面、内存 vs SQL 语义对照、方言拼接条件、catch-all 吞错路径、时间单位解析默认值），逐条确认或修正提级依据
- [x] 逐项记录裁决结果（提级 → 归属 MR8 段 R8.x 行分组；理由；不得有候选因"没时间"被降级为归类）
- [x] 为提级项设计后续 R8.x 修复 plan 的粗粒度分组建议（如：质量执行/评分正确性组、诊断与日志组、导入与内存过滤组、杂项正确性组），写入裁决记录供后续 plan 起草引用；分组建议须标注与既有文档契约的交互复核（如 AR-11 修复与 R6.6 已落地的 P2-08 方言 SKIP docs 例外说明——`docs-for-ai/03-modules/nop-metadata.md`——若行为收紧需同步文档）

Exit Criteria:

- [x] 11 条提级候选全部有 live 核实依据（代码路径 + 影响面），裁决一致（不因复核翻案或明确记录翻案原因）
- [x] 裁决记录写入 roadmap MR8 段（R8.0 裁决记录段 + R8.x 行提级项清单）
- [x] **无静默跳过**：无任何提级候选因"没时间"被降级为归类（见 Minimum Rules #15/#16 + Anti-Slacking Rule）
- [x] 本 Phase 为纯裁决 + 文档变更，无代码变更 → 构建验证由 Closure Gates 的 `./mvnw test` 承接
- [x] `ai-dev/logs/2026/08-06.md` 已更新

### Phase 2 - AR-17 契约裁定与 AR-23 杂项批逐子项裁决

Status: completed
Targets: `NopMetaDataSourceBizModel.java` + AR-23 涉及 9 文件 + roadmap MR8 段

- Item Types: `Decision | Proof`

- [x] AR-17 live 核实：`upsertExternalTableGuarded` 的 per-key 锁 + REQUIRES_NEW 是否确使 syncExternalTables 从"整批回滚"变为"部分持久化 + 异常上抛 + 事件缺失"；裁定：文档化语义（docs batch）还是需代码修复（如事件发布移到提交后 → 提级）——按 live 证据裁决，记录 Why Not Blocking Closure 或提级依据
- [x] AR-23 杂项批 10 子项（①②③④⑤⑥⑦⑧⑨⑩，见 Current Baseline 枚举）逐项 live 核实与单独裁决：正确性/安全类子项（① delete 先摘 cron 后提交 / ⑧ 模块版本 read-then-insert 竞态 / ⑩ 事件快照 Map 分支敏感列脱敏跳过 / ⑨ manifest 跨 DRAFTING 解析 / ⑤ ExternalTableStructureReader 错误归类 + NULL 精度 / ② 索引重建陈旧文档 / ③ refresh 仅 warn / ④ 搜索 limit 负数直通）→ 提级或归类；性能类子项（⑥ reconciliation 全量候选池 + 无上限 levenshtein / ⑦ profiler 整列载入）→ 按 live 证据归类（缺省 optimization candidate，除非证据表明存在活跃缺陷路径）；每条子项终态记录 Why Not Blocking Closure 或提级依据
- [x] 记录每条子项终态（提级归属 / 归类 + Why Not Blocking Closure），写入 roadmap MR8 段 + arm-index §P2

Exit Criteria:

- [x] AR-17 与 AR-23 全部子项终态（无悬置、无"后续再说"式模糊归类），归类项每条 Why Not Blocking Closure 可复核
- [x] 无已确认 live defect 被降级为终局归类（见 Minimum Rules #15/#16）
- [x] roadmap MR8 段与 arm-index §P2 一致（grep 可追溯）
- [x] 本 Phase 为纯裁决 + 文档变更，无代码变更 → 构建验证由 Closure Gates 承接
- [x] `ai-dev/logs/2026/08-06.md` 已更新

### Phase 3 - 收口

Status: completed
Targets: roadmap + arm-index + 文档

- Item Types: `Fix | Proof`

- [x] roadmap MR8 段状态同步：R8.0 → done，注明裁决统计（提级 19 条 / 归类 3 条 / 悬置 0）
- [x] roadmap `## 里程碑` 表新增 MR8 行（依赖 MR7 done，产出 = 裁决记录 + R8.x 修复工作项），与 MR6/MR7 先例一致
- [x] 更新 roadmap header 最后更新记录（MR8 R8.0 收口，v27）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 通过（0 errors）

Exit Criteria:

- [x] roadmap MR8 段与 arm-index §P2 追踪一致（13 条逐条可追溯）
- [x] 后续 R8.x 修复 plan 的 Deps（R8.0 done）已解除
- [x] check-doc-links --strict exit 0
- [x] `ai-dev/logs/2026/08-06.md` 已更新

## Closure Gates

> 关闭条件：本 section 所有条目与每个 Phase 的 Exit Criteria 全部 `[x]` 后，才能将 Plan Status 改为 `completed`。

- [x] 13 条（AR-11~23）全部终态（提级 19 + 归类 3），0 悬置
- [x] 无已确认 live defect / contract drift 被静默降级到非 blocking 区域
- [x] roadmap MR8 段 + arm-index §P2 双向一致
- [x] 独立子 agent closure-audit 完成并记录证据（Anti-Hollow：裁决记录与实际代码行为一致，无"纸面裁决"）
- [x] `./mvnw test -pl nop-metadata -am -T 1C`（本 plan 无代码变更，验证无回归）→ **970/0 全绿**（与 MR7 R7.3 基线一致）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0

## Deferred But Adjudicated

（执行时按裁决结果填写；本 plan 的 deferred 只允许 `watch-only residual | optimization candidate | out-of-scope improvement | docs batch` 四类归类，每条附 Why Not Blocking Closure + Successor Required。提级项不属于本节。）

### AR-23⑥ reconciliation 每行全量候选池 + 无长度上限 levenshtein

- Classification: `optimization candidate`
- Why Not Blocking Closure: `LocalReconciliationProcessor.java:92-102` loadCandidates 按 entityType+identifierSpace 全量加载候选、:137-163 levenshteinDistance O(n×m) 无长度上限——性能面 O(rows×candidates×len²)，但对账为用户显式触发的低频运维批量操作，元数据目录规模受限，无正确性缺陷；无规模实测证据表明活跃缺陷路径（计划默认：性能类缺省 optimization candidate）
- Successor Required: no

### AR-23⑦ profiler 整列载入内存

- Classification: `optimization candidate`
- Why Not Blocking Closure: `MetaTableProfiler.java:260-275` loadSortedDoubles 全列载入 List<Double>——profileTable 为用户显式单表触发，median/percentile 需排序值；超大表 OOM 时异常 fail-loud（无数据损坏），非默认自动执行路径
- Successor Required: no

### AR-23⑧ 模块版本 read-then-insert 竞态

- Classification: `watch-only residual`（**live 复核翻案**：审计"无唯一约束"主张过期）
- Why Not Blocking Closure: `UK_NOP_META_MANIFEST_MODULE_VER (metaModuleId, manifestVersion)`（orm.xml:2246 constraint=）+ `UK_NOP_META_MODULE_ID_VER (moduleId, moduleVersion)`（orm.xml:299 constraint=）R3.19 36 UK constraint 批已在位，三方言 DDL 实证（deploy/sql/{mysql,postgresql,oracle}/_create_*.sql）；并发 read-then-insert 败者由 DB UK fail-loud 拒绝——无静默重复无损坏，重试收敛；残余 = 并发败者收到显式错误（非幂等），非活跃静默缺陷路径
- Successor Required: no

## Non-Blocking Follow-ups

- AR-11~23 归类项的 watch-only 跟踪（arm-index §P2 终态即跟踪载体）
- 后续 R8.x 修复 plan 起草（Deps = R8.0 done，由后续 DRAFT_PLANS 轮承接）

## Closure

Status Note: 本 plan 为纯裁决 + 文档计划（MR8 R8.0 展开器）——Follow-up Backlog AR-11~23 共 13 条逐项 live 复核裁决全部终态（提级 19 + 归类 3，0 悬置），裁决记录写入 roadmap MR8 段 + arm-index §P2 双向一致；R8.0 → done 解除后续 R8.x 修复 plan 的 Deps 门禁；无代码变更，`./mvnw test -pl nop-metadata -am -T 1C` 970/0 全绿（无回归）；check-doc-links/check-plan-checklist 全 0；独立子 agent closure audit 实质全 PASS，本 plan 可关闭。
Completed: 2026-08-06

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，read-only，未修改任何文件）— task `ses_02b6c780affetUBmFL0tssj8vj`
- Evidence:
  - Phase 1 Exit Criteria PASS：11 条提级候选（AR-16 + AR-11/12/13/14/15/18/19/20/21/22）12/12 独立 spot-check 与 live 代码逐条相符（MetaQualityRuleExecutor.java:631/647/669 LOG.info 三处、:725-733 isRegexpUnsupported 子串启发式、MetaQualityCheckpointScheduler.java:203-207 try 外抛错、QualityErrors.java:16-22 `{ruleKey}` + throw 点不设 ruleKey、MetaQualityCheckpointExecutor.java:156-164 不写 ERROR 行 + MetaQualityScorer.java:315 陈旧复用 + DTO 字段不填充、ageMinutesFromNow :735-740 负值、OrmModelImporter.java:208-216 手拼 JSON + MetaTableFieldResolver.java:353 未类型化 cast、MemoryFilterEvaluator.java:108/:227-231/:172-173 三处语义漂移、AggregationHelper.java:342-345 + CrossDbJoinMerger.java:130/152、AutoClassificationProcessor/LineageTagPropagationProcessor catch-all + lexicographic 回退、MetaContractChecker.java:362-364 默认毫秒）
  - Phase 2 Exit Criteria PASS：AR-17（NopMetaDataSourceBizModel.java:508-519 per-key 锁 + REQUIRES_NEW + scan 级失败事件不达 + docs nop-metadata.md:211 未文档化原子性变化）与 AR-23 十子项 12/12 独立 spot-check 相符；**AR-23⑧ 翻案独立验证正确**——UK_NOP_META_MODULE_ID_VER（orm.xml:299 constraint=）+ UK_NOP_META_MANIFEST_MODULE_VER（orm.xml:2246 constraint=）R3.19 在位，mysql/oracle/postgresql 三方言 `_create_nop-metadata.sql` 实证（:24/:280）
  - Phase 3 Exit Criteria PASS：roadmap MR8 段（R8.0 done + R8.1~R8.4 行 Deps=R8.0 done）+ 里程碑表 MR8 行 + header v27 与 arm-index MR8 裁决记录段双向一致（13 条逐条可追溯）；check-doc-links --strict exit 0
  - Closure Gates PASS：13 条全部终态（提级 19 + 归类 3，0 悬置）；无已确认 live defect / contract drift 被静默降级（AR-17 契约 drift 提级至 R8.4，非归类）；`./mvnw test -pl nop-metadata -am -T 1C` 970/0 全绿（纯文档计划无回归）；`node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit 0（31/31 items checked，无未勾选项 + Closure Evidence 已写入）；`node ai-dev/tools/check-doc-links.mjs --strict` exit 0（0 errors）
  - Anti-Hollow：纯裁决 + 文档计划，无新代码 → 调用链检查不适用；audit 独立抽查 24 处裁决记录与 live 代码行为一致（含 AR-23⑧ UK/DDL 实证），无"纸面裁决"
  - Deferred 分类检查：3 项归类（AR-23⑥⑦ optimization candidate ×2 + AR-23⑧ watch-only residual）逐条复核无 in-scope live defect 被降级；6/7 归类依据均含代码可验证证据
- Audit Minor 观察（非阻塞，已处置）：① AR-22 行号微漂移（:358-361 vs live :362-364——plan header 行号注记已声明以 live 为准）；② check-plan-checklist 工具仅接受仓库相对路径（命令执行已按相对路径）；③ 日志/arm-index 先于 plan 文本声明 closure——本 Closure 段写入后全部兑现

Follow-up:

- 后续 R8.x 修复 plan 承接 19 项提级（Deps = R8.0 done，由后续 DRAFT_PLANS 轮起草，见 roadmap MR8 段 R8.1~R8.4 行）；AR-23⑥⑦ optimization candidate / AR-23⑧ watch-only 由 arm-index §P2 终态跟踪载体承接；no remaining plan-owned work
