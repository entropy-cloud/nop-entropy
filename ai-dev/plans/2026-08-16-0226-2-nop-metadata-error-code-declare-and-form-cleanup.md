# nop-metadata 错误码声明面与诊断形态清扫（P2-09 / P2-10 / P2-13 / P2-14 / P2-23 + P2-11 裁定）

> Plan Status: active
> Last Reviewed: 2026-08-16
> Mission: nop-metadata-invariant-loop
> Work Item: 2026-08-15 multi-audit Follow-up Backlog — 错误处理/诊断族（P2 批次清扫）
> Source: `ai-dev/backlog/nop-metadata-invariant-loop-roadmap.md` Follow-up Backlog（错误处理/诊断族）；审计源 `ai-dev/audits/2026-08-15-0559-multi-audit-nop-metadata-invariant-loop.md`（P2-09/P2-10/P2-11/P2-13/P2-14/P2-23）
> Related: 先行计划 `2026-08-15-1913-3`（P1-6/P1-7 错误码识别性参数 + INV-ERROR-PARAM 门禁沉淀；其 Follow-up 显式将 P2-09/P2-10/P2-11/P2-23 移交 backlog）。本计划承接声明面（define 侧）与形态面收口，执行顺序在 `2026-08-16-0226-1` 之后（两者触碰文件不重叠，仅 MetaQualityRuleExecutor 不同方法区域，顺序执行避免冲突）。

## Purpose

把 follow-up backlog"错误处理/诊断族"5 个修复项 + 1 个裁定项一次性收口：6 处 throw 点补 `{error}` 附注参数、17 处 ErrorCode define 声明参数与描述占位符不一致修复 + 2 个死错误码删除、`throw new SQLException` 控制流哨兵消除、并发拒绝 WARN 补异常末参、ReconciliationResultBizModel 死码删除（消除 2 处 `invariant-ok` 豁免）、`.param()` 双轨制显式裁定。收口后错误码三方（define 声明 ↔ 描述占位符 ↔ throw 点参数）一致，诊断形态统一。

## Current Baseline

> 事实为 2026-08-16 live repo 实测（1913-3 落地后）。1913-3 已修复 throw 点识别性参数（11 活点 + INV-ERROR-PARAM 门禁零命中），**define 声明面（P2-10）与形态面未动**。

- **P2-09 `{error}` 附注参数缺失**：`-- {error}` 结尾的 ErrorCode 描述现存于 MiscErrors（12 处：:50/:67/:89/:96/:171/:175/:178/:183/:191/:208/:213/:217）、JoinErrors（:69）、ReconErrors（:34/:54）、QualityErrors（7 处）、ModuleErrors（3 处）、AggregationErrors（3 处）、DataSourceErrors（5 处）——全集以执行期 `rg -- '-- \{error\}'` 重扫为准。throw 点缺 `error` 键 **live 实测 7 处**（2026-08-16 独立复核：AutoClassificationProcessor:290、LineageTagPropagationProcessor:204、NopMetaModuleBizModel:349/:689、NopMetaTagLabelBizModel:138、MetaModelChangedEventPublisher:167、AggregationHelper:543）。INV-ERROR-PARAM 门禁当前零命中——矛盾根因已定位：门禁源码 `check-error-param-consistency.mjs:77` `EXEMPT_PLACEHOLDERS = new Set(['error'])` **硬编码豁免 `{error}` 占位符**（Phase 2 以 live 门禁源码复核确认后收口豁免）。
- **P2-10 define 声明面漂移**：17 个 define 声明参数与描述占位符不一致 + 2 个死错误码。**死码已独立定位**（2026-08-16 实测）：`ERR_AGGR_TYPE_PROBE_FAILED`（AggregationErrors:170）+ `ERR_PROFILING_RULE_OPERATION_FAILED`（MiscErrors:215），各带 1 处 `TestSilentSwallowFormalization` 测试引用需同步；audit 锚点 `LineageErrors.java:44-48`（`ERR_LINEAGE_GRAPH_TOO_LARGE`）实为 17 处不一致之一（占位符多于声明），非死码。**检测方法警告**：占位符是 camelCase 键（`{metaTableId}`），声明的 ARG 常量**名**是 UPPER_SNAKE（`ARG_META_TABLE_ID`）、**值**才是键（`NopMetadataArgs.java` 中 `ARG_META_TABLE_ID = "metaTableId"`）——必须经 ARG 值解析后比对（naive 名字比对会产生数百假阳性，2026-08-16 实测验证；独立复核已用 ARG 值解析 + 对称差方法精确复现 17 处 + 2 死码）。1913-3 新增 5 个定点错误码后清单需重扫。
- **P2-11 双轨制**：`.param(` 字面量键 578 处 vs ARG 常量键 220 处（qualified 205 + bare 15；2026-08-16 实测，`-o` 匹配数口径；audit 时点 454/199，增长来自后续修复）。当前键值一致零错配（INV-ERROR-PARAM 零命中佐证）。
- **P2-13 SQLException 哨兵**：`MetaQualityRuleExecutor` :551（range COUNT(*) no row）/ :596（regex COUNT(*) no row）`throw new SQLException(...)` 作方法内控制流，由同 try 的 catch 捕获 → LOG + ERROR 判定。无泄漏无静默（audit 裁定"形态一致性"问题），但 `SQLException` 语义 = SQL 层故障，被挪用为业务分支信号，与模块"错误码显式建模"惯例相悖。
- **P2-14 WARN 缺异常末参**：`MetaQualityCheckpointScheduler` :223-224 并发拒绝降级 `LOG.warn("...checkpointId={} (already running, ...)", checkpointId)` —— 未把异常对象作为 logger 末参数（R4.3 裁定内容合规、形式违约；对照同文件 :226-228 ERROR 分支 `..., e)` 正确形态）。
- **P2-23 死码**：`NopMetaReconciliationResultBizModel` :154-177 `toInt(Object)`/`toStr(Object)`（main 零调用）+ 不可达 throw 分支，带 2 处 `// invariant-ok: dead code P2-23` 豁免注释（1913-3 显式移交：删除后豁免自然消除）。**关键事实（2026-08-16 独立复核）**：`ERR_RECON_INVALID_SELECTION` 全仓仅 3 处引用 = ReconErrors.java:45（定义）+ toInt 死码内 :162/:170——**无其余活点**（confirmMatch 用的是 `ERR_RECON_DETAILS_EMPTY`/`ERR_RECON_ROW_INDEX_OUT_OF_RANGE`）；删除 toInt 后该错误码即零引用死码，其定义与 `ARG_VALUE` 常量一并处置（见 Phase 4）。

## Goals

- 7 处 `{error}` 缺参 throw 点补齐（live 重扫全集为准；各点均有 in-scope cause，`.param(ARG_ERROR, NopMetadataHelper.toErrorMessage(e))` 形态），并移除门禁对 `{error}` 的豁免使该形态进入 hard-gate 覆盖。
- 17 处 define 声明/占位符不一致收敛（声明补 ARG 或描述对齐，语义取现场真实值）；2 个死错误码删除（含测试引用同步）；define 面一致性沉淀为可复验的 check 规则。
- `throw new SQLException` 哨兵消除：range/regex no-row 分支显式建模（ERROR 判定 + message），judgment 输出不变、日志形态按明确定义的等价标准核验。
- 并发拒绝 WARN 补异常末参。
- ReconciliationResultBizModel 死码删除（含随之死码化的 `ERR_RECON_INVALID_SELECTION` 定义与孤儿 `ARG_VALUE` 处置）+ 2 处 invariant-ok 豁免消除。
- P2-11 双轨制显式裁定：接受双轨为 non-blocking 结构形态（gate 已守护一致性），全量常量化登记为 optimization candidate。

## Non-Goals

- **P2-12（ErrorCode 描述 i18n 化）** —— ask-first 裁定项（error-handling.md「define 描述用中文」规则冲突需人工裁定），留待裁定轮。
- **`.param()` 578 处字面量全量常量化** —— optimization candidate，显式裁定 deferred（见 Deferred But Adjudicated），不在本计划执行。
- **错误码语义/文案重写** —— 只收敛声明面一致性，不改任何已渲染消息的语义内容（除补齐 `{error}` 后端到渲染真实值）。
- 其余 P2 族（安全族 → 计划 1；BizModel 行为族 → 计划 3）。

## Scope

### In Scope

- 6 处 throw 点 `{error}` 补参（P2-09）+ 门禁盲区核实与（可行时）收口。
- define 声明面 17 处不一致修复 + 2 死码删除（P2-10）。
- `MetaQualityRuleExecutor` :551/:596 哨兵重构（P2-13）。
- `MetaQualityCheckpointScheduler` WARN 末参（P2-14）。
- `NopMetaReconciliationResultBizModel` 死码 + 豁免消除（P2-23）。
- P2-11 裁定记录（Decision，无代码变更）。

### Out Of Scope

- P2-12 i18n；字面量全量常量化；错误码新增（define 数量净变化 = -3：2 个 define 面死码 + 1 个 P2-23 连带死码）。
- 其余 P2 族与 `_gen/` 产物。

## Execution Plan

### Phase 1 — define 声明面一致性清单与修复（P2-10）

Status: planned
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/{Aggregation,DataSource,Field,Join,Lineage,Misc,Module,Quality,Recon,Sql}Errors.java`；测试引用同步

- Item Types: `Fix | Proof`

> **检测方法（硬性）**：占位符集合 = define 描述字符串拼接后 `\{(\w+)\}` 全提取；声明参数集合 = ARG 常量**值**（经 `NopMetadataArgs.java` 与各 Errors 文件内局部 `String ARG_X = "..."` 声明解析）；不一致 = 对称差。禁止用常量名比对。门禁 `check-error-param-consistency.mjs` 的 registry-build（步骤 1）已实现 ARG 值解析与占位符提取，但**不解析 define 尾部 ARG 声明**——define 尾参提取需在门禁脚本中新增（见下）。
>
> **落地形态（防复发门禁，非一次性脚本）**：为 `check-error-param-consistency.mjs` 新增 define 面一致性规则（声明占位符 ↔ ARG 值对称差 + define 级死码检测的基线机制），修复完成后该规则零命中入 hard-gate 聚合链——define 面自此有守护，Closure Gates 可机械复验。
>
> 修复取向：现场真实值优先——throw 点实际传的键是什么，声明面就收敛成什么（补 ARG 声明或描述占位符对齐）；两侧都无消费的（死码）删除。死码删除前 `rg` 全仓引用（含测试），引用同步更新。

- [ ] 门禁扩展：`check-error-param-consistency.mjs` 新增 define 面规则（ARG 值解析 + 对称差；死码经基线/豁免清单机制管理，沿 check-silent-wrong-result 的 baseline 先例形态）
- [ ] 重扫清单：以扩展后门禁产出 live 不一致全集（基线 17 处 + 2 死码；1913-3 后可能漂移，以重扫为准），写入 daily log
- [ ] 逐处修复：声明补 ARG / 描述占位符对齐 / 死码删除（`ERR_AGGR_TYPE_PROBE_FAILED`/`ERR_PROFILING_RULE_OPERATION_FAILED` + `TestSilentSwallowFormalization` 引用同步；如重扫发现其他死码一并处理）
- [ ] 每处修复记录修复取向依据（throw 点真实键 vs 描述文案，二者取一的理由）
- [ ] 如实核对：是否存在"描述占位符 + 声明 ARG 都有但键值不同"的第三形态，一并收敛

Exit Criteria:

- [ ] 扩展后门禁 define 面规则零命中（死码删除后全量复跑）
- [ ] `TestNopMetadataErrorsCentralized` / `TestSilentSwallowFormalization` 等错误码镜像测试全绿
- [ ] `./mvnw compile -pl nop-metadata -am -T 1C` 通过
- [ ] **无静默跳过**：不允许"改脚本让它不报"式收敛；每处修复有取向记录
- [ ] No owner-doc update required（声明面内部一致性，不改对外契约语义——执行时复核 owner doc 错误处理段是否引用被删死码，如引用则同步）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — `{error}` 附注参数补齐与门禁豁免收口（P2-09）

Status: planned
Targets: 7 处 throw 点（live 重扫全集为准：AutoClassificationProcessor:290、LineageTagPropagationProcessor:204、NopMetaModuleBizModel:349/:689、NopMetaTagLabelBizModel:138、MetaModelChangedEventPublisher:167、AggregationHelper:543）；`ai-dev/tools/check-error-param-consistency.mjs`（:77 `EXEMPT_PLACEHOLDERS` 收口）

- Item Types: `Fix`

> 豁免根因已在 Current Baseline 定位（门禁 :77 硬编码豁免 `{error}`）。主路径 = 补齐全部缺参点 → 移除 `EXEMPT_PLACEHOLDERS` 中的 `error` → 门禁零命中（独立复核证实 7 处均有 in-scope cause `e`，补参可行）。若个别点复核后确无 in-scope throwable（与现测不符），该点显式裁定：描述删 `-- {error}` 尾（诊断价值损失记录），不允许保留豁免。

- [ ] live 重扫全集（`rg -- '-- \{error\}'` + 门禁去掉豁免试跑），定稿清单写入 daily log
- [ ] 全部缺参点补齐 `.param(ARG_ERROR, NopMetadataHelper.toErrorMessage(e))`（或等价；无 throwable 的点按上述裁定改描述）
- [ ] 移除门禁 `EXEMPT_PLACEHOLDERS` 中对 `error` 的豁免；同步改写门禁头注释豁免面 (b) 的表述（"hard requirements from the plan"记载随之更新）
- [ ] 同步门禁自检 fixture 样例 `error-placeholder-exempt`（:684-690）：其 `expectHits: 0` 依赖豁免，移除后改期望为 1（样例语义随之改为"豁免已收口、缺参即命中"），`node ai-dev/tools/check-error-param-consistency.mjs --fixture` 自检全绿
- [ ] 测试：补参后各点异常消息渲染含真实 error 值（无字面 `{error}` 残留）——按 1913-3 先例用断言错误码 + param 的 focused test（覆盖 7 处中可触发路径）

Exit Criteria:

- [ ] live grep：`-- {error}` 描述对应的全部 throw 链渲染端无字面 `{error}` 残留（测试断言覆盖可触发路径）
- [ ] 门禁豁免已移除且 `node ai-dev/tools/check-error-param-consistency.mjs --module nop-metadata` exit 0（`{error}` 形态进入 hard-gate 覆盖）
- [ ] `./mvnw test -pl nop-metadata/nop-metadata-service -am -T 1C` 全绿
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — SQLException 哨兵消除（P2-13）

Status: planned
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/MetaQualityRuleExecutor.java`（:545-600 区域 range/regex no-row 分支）

- Item Types: `Fix`

> 目标形态：COUNT(*) 无行是**可预期业务分支**（表空），不是 SQL 故障。消除 `throw new SQLException("range COUNT(*) returned no row")` 哨兵：将查询返回值可为空的信号显式建模（如查询方法返回 null/Optional 由调用方分支处理），no-row 分支直接产出 ERROR 判定，真实 SQLException 仍走既有 catch。
>
> **行为等价的明确定义**（消除歧义）：(1) judgment 输出（status/message/details 字段值）逐字段不变——硬约束；(2) 日志等价 = 同 logger、同级别（ERROR）、同消息模板，message 语义保持 "range/regex SQL execution failed: [code] COUNT(*) returned no row" 可诊断；**堆栈消失是预期变化**（现状堆栈来自哨兵异常对象本身，重构后不存在该对象，不伪造异常）——no-row 分支保留一条无 throwable 的显式 ERROR 日志即可。对照记录按此标准逐项核验。

- [ ] 查询路径改造：no-row 显式返回信号，删除 2 处 `throw new SQLException` 哨兵（:551/:596）
- [ ] no-row 分支直接构造 ERROR 判定 + 一条无 throwable 的显式 ERROR 日志（按上述等价标准）
- [ ] 行为等价验证：按明确定义逐项对照重构前后输出（judgment 字段逐字段 + 日志 logger/级别/模板/消息），对照记录写入 daily log；no-row 路径 focused test（现状无直接覆盖，新增）

Exit Criteria:

- [ ] `rg -n 'throw new SQLException' nop-metadata/nop-metadata-service/src/main` 零命中
- [ ] no-row 路径 focused test 通过且对照记录符合上述等价标准（堆栈消失为预期，其余逐项等价）
- [ ] **无静默跳过**：no-row 分支产出显式 ERROR 判定，非降级 PASS/SKIP
- [ ] `./mvnw test -pl nop-metadata/nop-metadata-service -Dtest='TestMetaQualityRuleExecutor*' -Dsurefire.failIfNoSpecifiedTests=false` 通过
- [ ] No owner-doc update required（内部形态重构，判定输出不变——执行时复核 owner doc 质量规则段无哨兵表述引用）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 — WARN 异常末参 + 死码删除（P2-14 + P2-23）

Status: planned
Targets: `MetaQualityCheckpointScheduler.java`（:223-224）；`NopMetaReconciliationResultBizModel.java`（:154-177）；`ReconErrors.java`（`ERR_RECON_INVALID_SELECTION` 定义 :45）

- Item Types: `Fix`

- [ ] P2-14：并发拒绝 `LOG.warn(...)` 追加 `, e` 末参（对齐同文件 :226-228 ERROR 分支与 R4.3 形态）
- [ ] P2-23：删除 `toInt`/`toStr` 死方法（:154-177）+ 不可达 throw 分支；删除 2 处 `// invariant-ok: dead code P2-23` 豁免注释
- [ ] P2-23 连带处置：`ERR_RECON_INVALID_SELECTION` 删除 toInt 后零引用（独立复核已证实无其余活点）——定义随死码一并删除；核对 `ARG_VALUE` 常量是否随之孤儿（无其他消费者则一并删除，有则保留并留证）；删除前 `rg` 全仓引用留证（预期仅定义 + 死码内 2 处）
- [ ] `TestSilentSwallowFormalization` 等测试引用同步（如引用被删错误码）

Exit Criteria:

- [ ] `rg -n 'invariant-ok: dead code P2-23' nop-metadata/` 零命中；`toInt`/`toStr` 定义与引用零残留；`ERR_RECON_INVALID_SELECTION` 在 `nop-metadata/` 代码范围（`-g '*.java'`）零残留——ai-dev 文档中的历史记载不算（或代码保留时有留证的明确理由）
- [ ] 并发拒绝路径测试通过（如既有测试未断言 throwable 末参，补 ListAppender 断言异常对象出现在 WARN 事件——按 R6.5 先例形态）
- [ ] `node ai-dev/tools/check-error-param-consistency.mjs --module nop-metadata` exit 0（豁免消除 + define 面规则 + 死码删除后仍零命中）
- [ ] `./mvnw test -pl nop-metadata/nop-metadata-service -Dtest='TestNopMetaReconciliation*,TestMetaQualityCheckpointScheduler*,TestSilentSwallowFormalization' -Dsurefire.failIfNoSpecifiedTests=false` 通过
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 — P2-11 双轨制显式裁定（Decision，无代码变更）

Status: planned
Targets: 本 plan `Deferred But Adjudicated` 段 + roadmap backlog 条目更新

- Item Types: `Decision`

- [ ] 复核实测计数（字面量 vs ARG 常量）写入裁定记录；确认 INV-ERROR-PARAM 门禁覆盖 throw 点占位符一致性（键值错配会被捕获）
- [ ] 裁定写入本 plan Deferred 段（classification = optimization candidate + Why Not Blocking）+ roadmap P2-11 条目标注裁定结果

Exit Criteria:

- [ ] 裁定记录完整（分类、理由、successor 路径）；roadmap 条目已标注
- [ ] 无代码变更（本 Phase 纯裁定）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 本计划为错误处理/诊断形态清扫（define 面一致性 + 形态统一 + 死码），不改对外 API 契约（消息渲染补齐 `{error}` 真实值属诊断增强）。

- [ ] P2-09/P2-10/P2-13/P2-14/P2-23 五项落地；P2-11 裁定记录完整
- [ ] `./mvnw compile -pl nop-metadata -am -T 1C` 通过
- [ ] `./mvnw test -pl nop-metadata -am -T 1C` 全绿（0 failures）
- [ ] `node ai-dev/tools/check-error-param-consistency.mjs --module nop-metadata` exit 0（含新增 define 面规则 + `{error}` 豁免移除后的 throw 点全覆盖）
- [ ] `node ai-dev/tools/check-silent-swallow.mjs --module nop-metadata` 0 新增命中
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [ ] 不存在被静默降级到 deferred 的 in-scope live defect（P2-11 裁定须满足 Allowed Deferred 分类）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 验证（a）P2-13 重构后 no-row 路径真实产出 ERROR 判定（跑测试非读签名）；（b）P2-09 补参后消息端到渲染真实值；（c）死码删除后豁免清单不残留；（d）门禁 define 面规则真实存在且非空转（构造临时不一致 fixture 验证会红——沿 1913-3 门禁沉淀的 fixture 验证先例）
- [ ] 门禁脚本变更已同步其头注释与 owner doc（invariant catalog / nop-metadata.md 门禁清单——沿 1913-3 的 6-guard 聚合链记录形态）
- [ ] roadmap Follow-up Backlog 对应条目标注处置结果

## Deferred But Adjudicated

### P2-11 `.param()` 键字面量/ARG 常量双轨全量常量化

- Classification: `optimization candidate`
- Why Not Blocking Closure: 当前键值一致零错配（INV-ERROR-PARAM hard-gate 在 CI 守护 throw 点占位符覆盖，任何键值漂移即红）；双轨是形态不一不是行为缺陷，用户可见行为零差异。全量常量化 = ~578 处机械改写，churn 大、行为收益为零。
- Successor Required: `no`（如未来某轮需要，可从 roadmap backlog P2-11 条目派生机械 codemod 计划）

## Non-Blocking Follow-ups

- P2-12（ErrorCode 描述 i18n）—— ask-first 裁定项，留待裁定轮。
- 安全族 / BizModel 行为族 / ORM / IoC / 文档测试卫生族 P2 项 —— 归计划 1、计划 3 与后续批次。

## Closure

Status Note: （待收口时填写）
Completed: （待收口时填写）

Closure Audit Evidence:

- Reviewer / Agent: （待收口时填写）
- Evidence: （待收口时填写）

Follow-up:

- （待收口时填写）
