# Cycle 2 / I4+I5+I6 — nop-metadata silent-wrong-result 类别清扫修复、全量验证与循环收口

> Plan Status: completed
> Last Reviewed: 2026-08-15
> Mission: nop-metadata-invariant-loop
> Work Item: Cycle 2 / I4（修复执行：实例 + 类别清扫）+ I5（全量验证与门禁零命中）+ I6（循环收口与下一轮触发判定）
> Source: `ai-dev/backlog/nop-metadata-invariant-loop-roadmap.md`（I4–I6 步骤定义、Loop Rule、复触发条件）；Cycle 1 先例 plans `2026-08-13-1930-4` / `2026-08-13-1930-5`
> Related: 前置 `2026-08-15-0820-1`（I1' 门禁）、`2026-08-15-0820-2`（I2'+I3' 正式 red list 与裁决表，**硬前置**）

## Purpose

把 Cycle 2 不变式驱动的审计与裁决结果收口为**修复后的稳态基线**：P1 缺陷类别清扫修复、门禁终态达成（模式 a 零命中 / 模式 b ⊆ 已批准豁免清单）、模块全绿，并把 Cycle 2 收口（统计、稳态判定、复触发条件登记、独立 closure audit）。本计划完成后，silent-wrong-result 族与 Cycle 1 四族一样处于"门禁防回退"稳态。

## Current Baseline

> 事实为 2026-08-15 live repo 实测；P1 项精确清单以 I3' 裁决表为准。

- **前置依赖（硬）**：`2026-08-15-0820-2` 已完成——正式 red list（`formal-red-list-cycle2`）+ 零悬挂裁决表（`adjudication-table-cycle2`）存在，P1 集非空（若 P1 集为空，Phase 1 自动缩小为"No P1 items"显式记录，直接进入 Phase 2/3，plan 不作废）。
  - **取消条款（显式）**：若 I1'/I2'/I3' 链条整体取消（I1' 全族不可机械化），本计划同样 `cancelled` + Supersession Note。
- **已知修复先例（同族已修形态，I4' 按类别沿用）**：
  - AR-12 → `LocalReconciliationProcessor`：`toLowerCase(Locale.ROOT)`（live main 代码已有 10 处 Locale.ROOT 代码站点 + 1 处 javadoc 引用，惯例兼容）
  - AR-01 → `MetaContractChecker`：先乘后取整
  - AR-03 → `AggregationHelper.memoryGroupBy`：结构性 key（`List<Object>`）
  - AR-05 → `MetaTableProfiler.isNumericType`：exact-match `Set.of`
  - AR-10 → `AggregationHelper.toBigDecimal`：整数 longValue 无损 / 浮点 doubleValue / String 解析
- **类别清扫预期面（以 I3' 裁决表为唯一权威分母）**：locale 族 41 rg 站点（含 1 处 javadoc 伪站点，`LocalReconciliationProcessor.java:124`——扫描器口径 40）中裁决为 P1 的子集 + 精度族 2 站点（`MemoryOrderByComparator.java:132` / `MemoryFilterEvaluator.java:356`，若 I3' 推翻 plan `2026-08-14-1133-2` closure 的 optimization-candidate 旧裁定）+ 其余子族 P1 项。**恒等式权威分母 = `adjudication-table-cycle2` 清单**（其自身恒等式：red list = P1 + FP + 优化候选 + 新族登记）；rg/grep 仅作清扫辅助——rg 命中出现裁决表未收录的站点时，必须显式上报并归因（门禁盲区或裁决遗漏），不得静默放过。
- **验证基线**：`./mvnw test -pl nop-metadata -am -T 1C` 最近全绿记录 = 1175 tests / 0 failures（plan `2026-08-14-1448-3` closure）。
- **门禁基线与终态语义（与 0820-1 关键约束 B 预声明一致）**：4（Cycle 1）+ N（I1' 新增）条；模式 b 门禁以 `--baseline` 对账（⊆ 语义，渐进修复期 CI 保持绿）。**I5' 终态 = baseline 重写为"已批准豁免清单"**（= I3' B1b 方式 a 的 FP 条目 + B1 维持的优化候选条目）**或清空**：清空且无放行注释 → 升级模式 a 零命中阻断式；仅剩已批准豁免条目 → 保持模式 b（任何新增命中即红）。**豁免条目是 baseline 的合法驻留项，不是终态异常**——因此本计划"门禁零命中"的准确语义 = "命中集 ⊆ 已批准豁免清单"（无 P1 残留、无新增违规），而非字面零命中。
- **错误处理两段式**（AGENTS.md）：模块内部实现类错误用模块异常类 + 英文消息；对 public ErrorCode 契约有影响的按 NopException + ErrorCode。
- **ORM 模型为保护区域**：本计划预期不改 ORM 模型；若 I3' 裁决出现需 ORM 变更的 P1 项，该项按 roadmap Cross-Cutting 授权规则执行前需人工确认（改源模型非 `_gen/`）。

## Goals

- I3' 裁决表全部 P1 项修复落地，每项有**test-first 回归测试**钉死（新行为先红后绿，或对不可行项显式记录理由并以等价验证替代）。
- **类别清扫完整性**：每个被修子族，同族全部站点一次清完（locale 族：修 1 处 = 按裁决表核对全部站点处置状态；不允许"修 3 处留 38 处且无裁定"）。
- false-positive 站点按 I3' B1b 裁定方式落地处置（baseline 驻留或注释放行，可追溯）。
- 全量验证：模块测试全绿 + 全部门禁（4+N）达到终态（模式 a 零命中 / 模式 b 命中集 ⊆ 已批准豁免清单，按 0820-1 模式归属表）+ 棘轮记录（Cycle 2 red list → 终态）。
- Cycle 2 收口：统计（门禁数 / red list / 修复数 / 新族数）、稳态判定、复触发条件登记、独立 fresh-session closure audit。

## Non-Goals

- **改动门禁检测规则以清零 red list** —— 清零只能来自修复或经 I3' 裁定的 false-positive 处置（B1b 两种方式均使用 0820-1 Phase 2 预实现的机制——baseline 驻留或放行注释——不改检测规则；baseline 终态重写为已批准豁免清单属预声明终态机制，非规则放宽）。
- **Cycle 3 新族门禁实现** —— 若 I3' 登记了新族，其沉淀归 Cycle 3 / I1（复触发后另行起草）。
- **全仓清扫** —— 仅 nop-metadata 模块组。
- **性能优化类非缺陷项** —— I3' 裁定为 optimization candidate 且维持者，不在本计划修复（其状态已在裁决表闭环）。

## Scope

### In Scope

- P1 项修复 + 回归测试 + 类别清扫核对。
- false-positive 站点处置落地（B1b 方式 a 驻留 baseline / 方式 b 放行注释）。
- 全量验证 + 门禁终态（模式 a 零命中 / 模式 b ⊆ 已批准豁免清单）+ 棘轮记录 + CI 门禁模式升级（如适用）。
- Cycle 2 统计、稳态判定、复触发登记、closure audit、roadmap Work Item Status 表同步。

### Out Of Scope

- 门禁规则变更、Cycle 3 工作、全仓扩展、非缺陷优化实现。

## Execution Plan

### Phase 1 — I4' 类别清扫修复（test-first）

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/...`（I3' 裁决 P1 站点）、对应 `src/test/java/...` 回归测试、false-positive 标注

- Item Types: `Fix`

- [x] **F1 test-first**：每个 P1 子族先落回归/对抗测试（如 locale 族：tr-TR 默认 locale 下 lineage registry 键不漂移的测试，**locale 切换机制钉死为测试内 `Locale.setDefault(Locale.forLanguageTag("tr"))` + `finally` 恢复**——先例 `TestLocalReconciliationProcessorLocale`；端到端先例 `TestNopMetaLineageEdgeBizModel`（@NopTestConfig + GraphQL 路径）；不使用 `-Duser.language` argLine 全局注入），确认对当前实现红或以"现状基线断言 + 修复后翻转"双段记录 → **修复前红实测 10 项断言失败**（P1-A 安全 2 处：`allowloadlocalinfile` 全小写变体绕过不抛异常、`insert into` 小写不被 sandbox 拦截；allowed-hosts 混合大小写误拒 ×2；AR-06 消息线索 CONNECTION 失配；CTE 名 I/i 失配误报物理表；SLA 单位 `unknown sla time unit: mınute`（错误消息可见无点 ı）；isStringType 子串误分类 ×2 + `tinyint` tr 归一化失配；e2e `{edgeCount=0, unresolved=[big_item]}` 静默 lineage 缺失）；新增测试清单（Exit Criteria 第 5 条逐项列出）
- [x] **F2 类别清扫修复**：按同族已修先例形态修复全部 P1 站点（locale 族 → `Locale.ROOT`（机器比较语义）或按裁决的其他处置；精度族 → 沿 AR-10 无损转换先例）；每修一个子族即以**裁决表（权威分母）**核对全部同族站点处置状态（已修 / FP 已按 B1b 裁定处置 / 裁决表豁免理由 / successor 拆分），rg 命中裁决表未收录站点时显式上报归因（见 Current Baseline 权威分母条） → **46/46 修复**：locale 40 → `Locale.ROOT`（10 文件）；C8 `isStringType` → exact-match `Set.of`（沿 AR-05 形态，STRING_TYPE_NAMES 17 词条含 `CHARACTER VARYING`/`VARCHAR_IGNORECASE` 复合词条）；D1/D5/D6 → 结构性 `List` 键（沿 AR-03 形态：`AutoClassificationProcessor` warn 去重键、`NopMetaLineageEdgeQueryAction` seenKeys + existingEdgeMap 键类型 `Map/List<String>`）；E1/E2 → 委托 `AggregationHelper.toBigDecimal`（AR-10 路由形态）。**类别清扫核对（权威分母 = 裁决表）**：locale 40 = 修复 40 + FP 0 + 豁免 0 + successor 0 ✓；contains 19 = 修复 1（C8）+ FP 已处置 18 ✓；delim 6 = 修复 3（D1/D5/D6）+ FP 已处置 3（D2-D4）✓；bigdec 2 = 修复 2 ✓；恒等式 67 = 46 修复 + 21 FP ✓。rg 辅助复扫：残余原始 `.toLowerCase()/.toUpperCase()` 仅 `LocalReconciliationProcessor.java:124` javadoc 伪站点（裁决表/计划已收录归因——注释非代码，扫描器剥离）——**零未收录站点**。D1 探查注记：单一 classificationId 单次调用内拼接键碰撞数学不可达（碰撞需 cid 含 `|`，sys ID 格式排除）——修复依据为类别立场（格式假设依赖 = AR-03 同机理），行为由结构性键保持等价
- [x] **F3 false-positive 处置落地**：按 I3' B1b 裁定方式落地——方式 (a) baseline 驻留：FP 条目保留在 baseline（不删条目，I5' 时 baseline 重写为该清单）；方式 (b) 放行注释：加 `// invariant-ok: <裁决引用>`（扫描器识别能力由 0820-1 Phase 2 预实现，本计划仅使用该机制）。**P1 集为空时 F1/F2/F4 跳过，F3 仍须按 B1b 执行（如存在 FP 条目）**。保证后续审计可追溯 → **方式 (a) 落地零源码扰动**：修复后 live 命中集 = 21 条（contains 18 + delim 3，均 ⊆ 既有 baseline 61 键，对账 exit 0）；21 键与裁决表 §4/§5 FP 清单一一对应（Phase 2 V2 将 baseline 重写为该 21 键豁免清单）
- [x] **F4 超时保护**：单个 P1 项修复若超时/受阻，按 Cycle 1 I4 先例拆 successor plan 并在裁决表标注归属，不阻塞其余项 → **机制未触发**：46/46 项全部当批修复落地，零 successor 拆分、零超时

Exit Criteria:

- [x] I3' 裁决表 P1 集内每项：已修复（含回归测试）或已显式拆 successor（超时机制，附理由）——零第三态；P1 集为空时显式记录 "No P1 items"（F3 如有 FP 条目仍执行，F1/F2/F4 跳过，不作废 plan） → 46/46 修复 + 回归测试，零第三态（修复清单见 F2）
- [x] 每个被修子族有类别清扫核对记录（**权威分母 = 裁决表清单**，恒等式：裁决表该族条数 = 修复数 + FP 已处置数 + 豁免数 + successor 拆分数；rg 辅助发现的未收录站点已上报归因） → 见 F2 条（逐族恒等式 + rg 零未收录）
- [x] **端到端验证**（P1 集非空时适用；P1 集为空时显式标注 N/A）：至少一条测试从用户可见入口（BizModel/GraphQL 路径）到受影响输出走通修复语义（如 lineage 生成在测试内 tr-TR 默认 locale 下端到端正确）——组件级单测不能替代 → `TestNopMetaLineageEdgeLocaleAndKeys`（@NopTestConfig localDb）4 例：tr-TR 下 GraphQL mutation `extractLineageFromSql`（`BIG_ITEM` 表 + 小写 SQL 引用 → 修复前 `{edgeCount=0, unresolved=[big_item]}`，修复后 edgeCount=1 + 落库断言）、tr-TR 列级抽取归属、D5 引号 pipe 列名两边各自落库（解析器保引号归一化实测）、D6 重复抽取幂等
- [x] **无静默跳过**：修复不得以吞异常/空实现/静默钳制替代显式失败语义；新增分支未实现时抛异常而非返回默认值 → 修复均为变换/键形态替换（Locale.ROOT / exact-match 集合 / 结构性键 / 规范实现委托），无新增未实现分支、无吞异常、无默认值占位
- [x] **新功能测试规则**：每个修复项对应的新增测试逐项列出（测试类名 + 验证的行为）；纯标注/处置类 FP 项注明 "No new test required: 标注不改行为" → ① `TestMetaDataSourceConnectionProcessorLocale`（4）：tr-TR blocklist 大小写变体绕过拦截（P1-A #2/#4）、allowed-hosts 混合大小写匹配（#3/#5）、IPv4-mapped 内网判定路径（#6 latent 钉住）、默认 locale 基线；② `TestMetaQualityRuleExecutorSandboxLocale`（3）：tr-TR 小写 `insert into` sandbox 拦截（P1-A #37）、良性 SELECT 不误拦、默认 locale 基线；③ `TestCheckpointActionDispatcherLocale`（2）：tr-TR webhook allowed-hosts（#32/#35）、默认基线；④ `TestMetaTableProfilerLocale`（3）：tr-TR 全大写消息 CONNECTION/PERMISSION/COMMUNICATION 线索仍判 infra（#26，AR-06 分类漂移防回退）、良性不误报、SQLState 码路径回归；⑤ `TestMetaTableProfilerClassification`（+4）：isStringType exact-match 标准族全量（防过度收缩）、子串复合词条不误分类（C8）、大小写/空白不敏感、tr-TR 小写类型名归一化（#28/#29）；⑥ `TestSqlExtractorsLocale`（3）：tr-TR CTE 名大写声明+小写引用仍排除（#23/#24 + 列级 #13-#22 机制代表）、tr-TR 列级抽取归属、默认基线；⑦ `TestMetaContractCheckerSlaUnitLocale`（2）：tr-TR `MINUTE`/`HOUR`/`DAY`/`Minute` 单位 token 解析（#7；配套 `toDurationMillis` private → package-private 可见性放宽，先例 AR-12 package-private access）、默认基线；⑧ `TestExternalTableStructureReaderLocale`（2）：tr-TR 方言路由不漂移（#40 latent 钉住）；⑨ `TestMemoryFilterAndOrderBy`（+4）：Long>2^53 等值/大于不塌缩（E1）、String 数值字面量数值接线（E1）、排序超精度严格分序（E2）、排序 String 数值接线（E2）；⑩ `TestNopMetaLineageEdgeLocaleAndKeys`（4，端到端）：见上条。**FP 处置 21 项：No new test required: 标注不改行为（方式 (a) baseline 驻留，零源码变更）**。合计新增 31 tests（1175 → 1207 断言级 32 处含分类测试既有重计数）
- [x] 受影响 owner-doc（`docs-for-ai/03-modules/nop-metadata.md` 等）同步，或显式写 No owner-doc update required（逐 Phase 裁定） → `nop-metadata.md` 两处：① AR-10 段 Non-Goal（比较路径留作 optimization candidate）改写为 E1/E2 已对齐段落（旧裁定被推翻的事实同步）；② AR-12 条后新增 INV-LOCALE 全量 locale-insensitive 段（40 处清扫 + 2 处安全缺陷 + C8 exact-match + D1/D5/D6 结构性键，防回退门禁指路）
- [x] `ai-dev/logs/` 对应日期条目已更新 → `ai-dev/logs/2026/08-15.md` Phase 1 小节

### Phase 2 — I5' 全量验证与门禁棘轮收口

Status: completed
Targets: `ai-dev/tools/run-nop-metadata-invariants.sh`、`.github/workflows/maven.yml`（如需模式升级）、baseline 文件、`formal-red-list-cycle2`（.md，新建于 `ai-dev/audits/nop-metadata-invariants/`）（棘轮记录）、`docs-for-ai/02-core-guides/invariant-guards.md`

- Item Types: `Proof`

- [x] **V1 模块全量**：`./mvnw test -pl nop-metadata -am -T 1C` 全绿（0 failures；上游模块 flaky 如实记录，不掩盖） → **BUILD SUCCESS，service 模块 1207 tests / 0 failures / 0 errors**（web 模块 1/1 绿；基线 1175 → +32），全 8 子模块 reactor SUCCESS，无 flaky
- [x] **V2 门禁收口（按 0820-1 模式归属表逐门禁执行）**：修复后 codebase 上 4+N 门禁达到终态——Cycle 1 四门禁保持零命中；模式 a 新门禁零命中；**模式 b 新门禁完成 baseline 终态重写**（baseline = 已批准豁免清单 = 方式 a FP 条目 + 优化候选维持条目，或清空）。baseline 清空且无放行注释 → 升级模式 a（零命中阻断式）并**同步更新模式归属表**（升级后归属表仍与 CI 接线一致）；baseline 仅剩已批准豁免条目 → 保持模式 b ⊆ 对账（新增命中即红）。升级/重写后做 hard-gate proof（注入 baseline 外违规样例 → 门禁红 → 还原 → 绿，Cycle 1 I5 先例）；**终态 codebase 上聚合入口 `run-nop-metadata-invariants.sh` 整体退出码 0（含模式 b 豁免驻留时）并留证** → Cycle 1 四门禁零命中（聚合输出 [1/5]-[4/5] 全绿）；gate 5 **baseline 终态重写为已批准豁免清单**（61 键零点 → 21 FP 命中/16 计数感知键 = contains 18/13 + delim 3/3；`--emit-baseline` 于修复后 codebase 生成 + note 记终态语义）；优化候选维持条目 = 0（I3' 推翻旧裁定）→ **豁免驻留非空，保持模式 b**（无模式升级，workflow 零变更）；hard-gate proof：注入 baseline 外 `s.toLowerCase()` → scanner exit 1（EXCESS: new key not in baseline）+ 聚合 exit 1 → `git checkout` 还原 → 聚合 exit 0，`git status --porcelain -- nop-metadata/` 净零；终态聚合入口整体 exit 0 实测（"All 5 nop-metadata invariant guards passed (guards 1-4 zero hits; guard 5 within baseline)"）；`initial-red-list-cycle2.md` 模式归属表同步（baseline 状态列 + 终态仍模式 b 说明）
- [x] **V3 棘轮记录**：`formal-red-list-cycle2` 记录 Cycle 2 棘轮（初始命中数 → 终态：P1 修复数清零 + successor 拆分数 + 豁免驻留数（FP baseline 驻留/放行注释 + 优化候选维持）），恒等式可复核 → 新增 §棘轮终态记录：逐族表（locale 40→0 修40 / narrowing 0→0 / contains 19→18 修1驻18 / delim 6→3 修3驻3 / bigdec 2→0 修2；合计 67→21 = 修复 46 + 驻留 21 + successor 0）与裁决表 §1 恒等式逐项对齐；终态机制与注入 proof 记录在内
- [x] **V4 owner-doc 同步**：`docs-for-ai/02-core-guides/invariant-guards.md` 同步 Cycle 2 门禁集合、模式 b 语义与终态（该文档描述"非零即阻断"的既有叙事须与新终态一致）；CI workflow 与本地聚合入口行为一致（如模式升级，workflow 同步且无静默放行） → 门禁表 gate 5 行更新（终态 live 0/0/18/3/0 = 21 全豁免 + 46 P1 已修说明）；模式 b 专节新增"终态已达（Cycle 2 / I5'）"条（46 修复形态、21/16 豁免清单、保持模式 b、"零命中"准确语义 = ⊆ 豁免清单）；CI 零变更核实（`invariant-gate` job :45 直接调用聚合入口 :73，rg 证实无 continue-on-error / || true，与本地聚合行为一致——无模式升级故 workflow 无需变更）

Exit Criteria:

- [x] V1 命令输出 BUILD SUCCESS 且 0 failures（记录测试总数） → 1207 tests / 0 failures（service）
- [x] V2 终态达成且与模式归属表一致（含升级场景下归属表同步更新）：无 P1 残留命中（命中集 ⊆ 已批准豁免清单或为空）；聚合入口终态整体退出码 0 已留证；注入/还原 proof 有证据 → 命中集 21 = 豁免清单（无 P1 残留）；聚合 exit 0 ×2（clean + restored 等价双跑）；注入 proof EXCESS→exit 1→还原 exit 0 证据在 V2 与 formal-red-list-cycle2 §棘轮终态记录
- [x] **接线验证**：CI `invariant-gate` job 与本地聚合入口行为一致（模式 a 阻断式 / 模式 b baseline 对账式与归属表一致，无静默放行配置） → job 调用同一聚合入口脚本（MVN=mvn），gate 5 经聚合入口以 `--baseline` 模式 b 调用 = 归属表一致；无静默放行配置（rg 证实）
- [x] 棘轮记录含可复现计数（初始 → 终态对比，含豁免处置清单与 successor 拆分） → formal-red-list-cycle2 §棘轮终态记录（逐族复现命令 + 恒等式 67 = 46 修复 + 21 驻留 + 0 successor）
- [x] `invariant-guards.md` 已同步（或显式记录无需更新的理由） → 已同步（两处：门禁表 + 模式 b 终态条）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0 → exit 0（无 high/critical 发现）
- [x] `ai-dev/logs/` 对应日期条目已更新 → `ai-dev/logs/2026/08-15.md` Phase 2 小节

### Phase 3 — I6' 循环收口与稳态判定

Status: completed
Targets: `ai-dev/backlog/nop-metadata-invariant-loop-roadmap.md`、`ai-dev/audits/nop-metadata-invariants/invariant-catalog.md`（如需）、本 plan Closure 节

- Item Types: `Decision` | `Proof`

- [x] **C1 统计**：Cycle 2 门禁数（4+N）/ red list 数 / 修复数 / 新族数，来源可追溯（formal-red-list + adjudication-table + 本 plan） → **门禁 5**（Cycle 1 四条 + gate 5 silent-wrong-result 单门禁×5 规则，聚合入口/CI 生效）；**red list 67**（formal-red-list-cycle2 §汇总：40/0/19/6/2）；**修复 46**（本 plan Phase 1，adjudication-table §10 移交清单全收）；**新族 0**（adjudication-table §7 显式 0，对抗探查 10 方向）；终态命中 21（全豁免驻留）
- [x] **C2 稳态判定**：按 roadmap Loop Rule 判定（全部门禁零命中 + 零新族 → 稳态暂停；有新族 → 登记 Cycle 3 触发） → **稳态暂停**：门禁 1-4 零命中；gate 5 终态 = 命中集 ⊆ 已批准豁免清单（21 FP 驻留，0820-1 关键约束 B 预声明终态机制内的合法驻留——"零命中"准确语义见 invariant-guards.md 终态条）；新族 0 → 不派发 Cycle 3 / I1
- [x] **C3 复触发条件登记**：CI 变红 / 新增或重命名 processor / bizmodel / ORM entity / 周期复探——更新至 roadmap（如措辞已存在则核对仍准确） → 核对 `docs-for-ai/02-core-guides/invariant-guards.md` §复触发条件（三条件措辞含模式 b 语义"baseline 外新命中"）与 roadmap Loop Rule（结构变更触发条件 = 新增/重命名 processor / bizmodel / ORM entity）**仍准确**，无需改写；Cycle 3 / I1 新族门禁实现不触发（0 新族）
- [x] **C4 roadmap 同步**：Work Item Status 表新增 "Cycle 2 / I1'–I6'（invariant-loop 第二轮）" 行并标状态——**消歧声明**：与既有三行 "Cycle 2 / 再审计 remediation"（2026-08-14 审计修复系列）为不同系列，行名须带 "invariant-loop" 标注或表头注明两系列关系；Follow-up Backlog 核对无未标注残留 → 新增行 "Cycle 2 / I4+I5+I6（invariant-loop）. 修复执行 + 全量验证 + 循环收口" ✅ done（行名带 invariant-loop 标注，与既有 "Cycle 2 / I2+I3（invariant-loop）" 行同约定——与三行 "Cycle 2 / 再审计 remediation" 系列消歧）；Follow-up Backlog 核对：全部条目带 ✅ Fixed / ⚠️ False positive / ⚠️ watch-only 标注，无未标注残留
- [x] **C5 独立 closure audit**：fresh-session 子 agent 按 Closure Gates 逐项核验 live repo（含 Anti-Hollow：修复在运行时路径被真实调用、端到端测试真实走通、无空壳/静默跳过），证据写入本 plan Closure 节 → 独立 closure auditor 子 agent（fresh session `ses_ffc56693effemooCIKleNl6BNR`，review-only 零文件修改）**verdict approved，0 Blocker / 0 Major / 3 Minor**（Minor 均为非本计划引入的既有观察：其他 mission 的 doc-links 基线错误、MDCP:114 既有非静默 SecurityException catch、surefire 报告时间戳一致性观察）；逐项 15/15 PASS + Anti-Hollow (a)(b)(c)(d) PASS（证据全文见 Closure 节）

Exit Criteria:

- [x] 统计四元组记录且来源可追溯 → C1（门禁 5 / red list 67 / 修复 46 / 新族 0，三源可溯）
- [x] 稳态判定有结论 + 依据；复触发条件在 roadmap 中最新 → C2 稳态暂停（依据：gate 1-4 零命中 + gate 5 终态 ⊆ 豁免清单 + 0 新族）；C3 复触发条件核对仍准确（invariant-guards.md §复触发条件 + roadmap Loop Rule）
- [x] roadmap Work Item Status 表与实际状态一致（无完成项未标、无未完成项虚标） → C4 新增 "Cycle 2 / I4+I5+I6（invariant-loop）" ✅ done 行（invariant-loop 标注消歧）；closure audit spot-check PASS（roadmap.md:42）
- [x] 独立 closure audit 完成，证据写入 plan 文件（Reviewer/session ID + 逐条 PASS/FAIL） → session `ses_ffc56693effemooCIKleNl6BNR` verdict approved，证据见 Closure 节
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0 → 收口后复跑 exit 0（见 Closure 节证据）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 对本次修改文件 0 新增 broken link → 16 errors 全部为其他 mission 文件的既有基线错误，本次修改文件（red lists / roadmap / plan / invariant-guards.md / nop-metadata.md / 08-15.md）0 命中（closure audit 独立复核一致）
- [x] `ai-dev/logs/` 对应日期条目已更新（含 Cycle 2 收口记录） → `ai-dev/logs/2026/08-15.md` Phase 3 小节

## Closure Gates

- [x] Phase 1~3 全部 Exit Criteria 勾选，各 Phase Status = completed
- [x] I3' 裁决表 P1 集零残留（修复 / 显式 successor 拆分，无第三态） → 46/46 修复零 successor；closure audit check #2 PASS（scanner 终态 locale 0 / bigdec 0，命中 21 全 FP ⊆ 豁免清单）
- [x] 类别清扫恒等式成立（权威分母 = 裁决表；每族：裁决条数 = 修复 + FP 已处置 + 豁免 + successor 拆分） → closure audit check #2 PASS（67 = 46 修复 + 21 驻留 + 0 successor ≡ 裁决表 §1 恒等式）
- [x] 模块全绿 + 4+N 门禁终态达成（按模式归属表：零命中或 ⊆ 已批准豁免清单）+ 棘轮记录可复核 → 1207/0 + 聚合入口 exit 0（closure audit check #5/#9 独立复跑 PASS）+ formal-red-list-cycle2 §棘轮终态记录
- [x] Cycle 2 统计与稳态判定完成，复触发条件已登记 → C1/C2/C3
- [x] 无 confirmed live defect 被降级（deferred 区仅有已裁定 non-blocking 项） → Deferred But Adjudicated 为空；closure audit check #7 PASS
- [x] 受影响 owner-doc（含 `invariant-guards.md`）已同步或逐项裁定 No update required → closure audit check #6a/#6b PASS
- [x] 独立子 agent closure-audit 已完成并记录证据 → session `ses_ffc56693effemooCIKleNl6BNR` verdict approved（0 Blocker / 0 Major）
- [x] **Anti-Hollow Check**：closure audit 验证 (a) 修复在运行时路径被真实调用（非仅编译通过），(b) 端到端测试从入口到输出走通（P1 非空时），(c) 无空方法体/静默跳过/no-op → audit Anti-Hollow (a) validateJdbcUrl←buildDataSource←withConnection/testConnect、BizModel @BizMutation L121-140 委派固定后的 QueryAction、validateCustomSqlShell:323 调用链确认；(b) e2e 测试真实驱动 GraphQL mutation + tr locale + finally 恢复 + 落库断言确认；(c) 46 修复 diff 区域逐一抽读无空壳/静默模式 + 全仓残余裸 toLowerCase 仅 javadoc 伪站点；(d) `scan-hollow-implementations --severity high` exit 0
- [x] `./mvnw test -pl nop-metadata -am -T 1C` 全绿 → BUILD SUCCESS（145 模块 reactor 全绿；service 1207/0/0；一次 -T 1C 并行运行曾现 nop-metadata 之外的上游瞬时失败，完整复跑 145/145 SUCCESS——如实记录不掩盖，nop-metadata 全模块两次运行均 SUCCESS）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0 → 收口后复跑 exit 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0 → 0 findings（closure audit 独立复跑一致）

## Deferred But Adjudicated

（起草时无。执行中产生的延期项必须按 Allowed Deferred Classifications 分类并附 Why Not Blocking Closure；I3' 裁定的 optimization candidate 维持者属裁决表闭环状态，不在此重复登记。）

## Non-Blocking Follow-ups

- Cycle 3 / I1 新族门禁实现（仅当 I3' 登记新族且 C2 判定触发时启动，非本计划拥有）。
- 全仓门禁扩展 —— 归各自 mission。

## Closure

Status Note: Cycle 2 不变式闭环全链收口——裁决表 46 P1 全量修复（locale 40 → Locale.ROOT 含 2 处安全语义缺陷、C8 exact-match、D1/D5/D6 结构性键、E1/E2 AR-10 路由）+ 21 FP 按 B1b 方式 (a) baseline 驻留；门禁终态达成（gate 1-4 零命中 + gate 5 命中集 ⊆ 已批准豁免清单 21 命中/16 键，保持模式 b，hard-gate 注入 proof 双向）；棘轮 67 = 修复 46 + 驻留 21 + successor 0 可复核；模块 1207 tests / 0 failures；Cycle 2 统计（门禁 5 / red list 67 / 修复 46 / 新族 0）与稳态判定（稳态暂停，0 新族不派 Cycle 3）完成，复触发条件核对最新；roadmap Work Item 表同步（invariant-loop 消歧行）。silent-wrong-result 族与 Cycle 1 四族一致处于"门禁防回退"稳态。
Completed: 2026-08-15

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure auditor 子 agent（fresh session，review-only 零文件修改），session `ses_ffc56693effemooCIKleNl6BNR`
- Evidence:
  - **Verdict: approved，0 Blocker / 0 Major / 3 Minor**（Minor 均非本计划引入：① doc-links 16 errors 全为其他 mission 文件既有基线；② MDCP:114 既有 SecurityException catch（非静默，plan 前已存在）；③ surefire 报告时间戳一致性观察）
  - Phase 1 Exit Criteria 6/6 PASS（audit check #1 文本一致性 + #3a-#3f 修复 spot-check 六处逐一 live code 证实：MDCP 5 站点 Locale.ROOT L71/129/232/266/496、MQRE sandbox L388 + regexp L809、MTP isStringType exact-match L569-575 + STRING_KEYWORDS 全仓零残留、NMLEQA `Set<List<String>>`/`Map<List<String>,…>` L258/263/479/492 且 `+ "|" +` 零匹配、MFE/MOBC 委托 AggregationHelper L353-354/L130-131、ACP warnKey L148）
  - Phase 2 Exit Criteria 7/7 PASS（audit check #2/#5/#6a/#6b：scanner baseline 对账 exit 0、逐族 0/0/18/3/0=21 ⊆ 16 键、聚合入口独立复跑 exit 0 "All 5 guards passed"、CI maven.yml invariant-gate 调用同一聚合入口且无 continue-on-error/|| true、invariant-guards.md 终态条 L68、nop-metadata.md AR-10 段 E1/E2 对齐无遗留 deferral）
  - Phase 3 Exit Criteria 7/7 PASS（audit check #1/#6c/#7/#8：C1-C4 勾选一致、roadmap.md:42 新行 ✅、Deferred 空 + Follow-ups 仅 Cycle 3 条件项与全仓扩展、check-plan-checklist 收口前状态 = 预期（20 未勾 = C5+EC+Gates+占位）、doc-links 16 errors 与基线一致且本次修改文件 0 命中）
  - Closure Gates 12/12 PASS（含本 audit 自身；恒等式 67=46+21+0 与裁决表 §1 逐项对齐；1207/0/0 surefire 131 份报告聚合）
  - **Anti-Hollow 检查 PASS**：(a) 运行时调用链确认——`validateJdbcUrl` L230 ← `buildDataSource` L198 ← `withConnection`/`testConnect`（真实建连路径）；`extractLineageFromSql`/`extractColumnLineageFromSql` 为 `NopMetaLineageEdgeBizModel` @BizMutation GraphQL 暴露方法（L121-140）委派修复后的 QueryAction；`validateCustomSqlSandbox` 于 MetaQualityRuleExecutor:323 custom SQL 执行前真实调用；(b) 端到端测试真实走通——`TestNopMetaLineageEdgeLocaleAndKeys`（@NopTestConfig localDb）4 例 GraphQL mutation 驱动 + 测试内 tr locale + finally 恢复 + edgeCount/落库双断言（含引号 pipe 列两边界 + 幂等重抽）；(c) 46 修复 diff 区域逐一抽读无空方法体/吞异常/no-op（结构键接真实 batchSave/batchUpdate L264-285、warn 去重接真实 LOG.warn L148-153）；(d) `scan-hollow-implementations --module nop-metadata --severity high` exit 0（0 findings）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 收口后复跑退出码 0（无未勾选项 + Closure Evidence 已写入）
  - Deferred 项分类检查：Deferred But Adjudicated 为空；Non-Blocking Follow-ups 仅 Cycle 3 条件触发项（0 新族未触发）与全仓扩展（归各 mission）——无 in-scope live defect 被降级（裁决表 §9 亦零 deferred）

Follow-up:

- Cycle 3 / I1 新族门禁实现——仅当复触发条件满足（CI 变红 / 新增或重命名 processor/bizmodel/ORM entity / 周期复探）时启动，非本计划拥有（本轮 0 新族未触发）。
- 全仓门禁扩展（nop-metadata 之外）—— 归各自 mission。
- 无剩余 plan-owned work。
