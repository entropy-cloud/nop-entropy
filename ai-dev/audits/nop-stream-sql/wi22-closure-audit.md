# WI22 Closure Audit——29-wi22-metrics-and-invariants.md

- Audit 日期：2026-10-03（单轮独立 audit）
- Auditor：独立子 agent（fresh session，与实现者非同一 session/task_id；全部结论来自 live repo 实读/实跑与自建探针，未采信 plan 勾选、日志自述或 commit message）
- 审计对象：HEAD `d571512483`（分支 add-stream-sql，审计起止工作树 clean；WI22 提交 9 文件 +550/-336——两算子计数器 + 三注册表/catalog/roadmap/日志/plan，零 `_`-前缀生成物触碰，唯一新增文件为 plan 29 本身，无探针文件入库）
- **最终裁定：PASS——1 Major 收口必改项（runtime 失败的归因叙事与四象限实测不符，须随 Phase 2 收口改写 plan/日志/FU-10 的证据语义，不阻断翻转）+ 3 Minor（两注册表描述性注记遗留漂移 + Closure Gates 混合勾选态）。** roadmap WI22 完成判定成分全部实证成立：两新算子接入 observability 五层指标集（operator 层 W-M1 计数 + engine/task/io/state 层随既有挂点自动覆盖）；两 operator 类登记 gate-inventory.json modules 映射且 `sync` 反查 OK、TestInvariantTableCompleteness 双向反射等价绿；不变量注册表职责归 WI21 未被触动（WI22 仅重钉 stale 行号，未改注册结构）。

---

## 0. 裁定摘要

WI22 的交付物成立且判别力充分。两算子计数器（EquiJoinOperator `numJoinOutputsEmitted`、OverWindowOperator `numOverRowsEmitted`）严格照抄 W-M1 模式（plan 369 定案：StreamMetricsRegistries + open() 时点注册 + instance-identity scope tag + copyForSubtask 新实例重注册），逐一发射点接入经读码核验无遗漏；gate-inventory 与 invariant-catalog §4 双登记经 live 反射双向精确等价（core 11/11 含 EquiJoinOperator、runtime 参数化含 OverWindowOperator）与 `sync` 反查双门实证；output-contract V4 四处发射点与 wiring V3 七处重钉逐行与 live 对照全部命中，无参全量 0 violations 归零（WI17 audit MIN-1 关闭属实）；core 1669 / sql 78 零退化精确复现。

审计侧自建四象限实跑矩阵（主树 HEAD no-am ×2、主树 HEAD `-am`、父提交 worktree `-am`）将 runtime 侧失败钉死为 **no-am 构件解析模式的本地仓库构件态问题**（双层 local repo：head repo 缺 runtime/connector 构件 → 回落 tail `~/.m2` 异构时间戳构件集），与提交态无关：HEAD 在 reactor 一致构建下 **1233 全绿**，WI22 代码被更强证据开释。但 plan Exit Criteria / Closure Gates / 日志 / roadmap FU-10 四处声称的「stash 实证 HEAD 同败」与「疑似用例间状态污染或 H2 会话残留」两项叙事在受控实验下均不成立或不完整——此为本 audit 的 1 Major：**结论对、证据语义错，若不改写将随 `completed` 固化一组误导性根因**（FU-10 后续排查将被引向状态污染而非构件解析）。

## 1. 两算子 operator 层计数器（审计项 1）——PASS

### 1a. EquiJoinOperator `numJoinOutputsEmitted`（core）——读码核验全通过

- **W-M1 三要素**：`open()` 时点 `StreamMetricsRegistries.registry().counter(...)` 注册（`EquiJoinOperator.java:134-136`）；instance-identity scope（tag `"operator" → getClass().getSimpleName() + "@" + System.identityHashCode(this)`，location-less 回退语义与先例一致）；`copyForSubtask()` 返回 `new EquiJoinOperator<>(...)` 新实例（`:114-118`），新实例在自身 `open()` 重注册、不共享 meter。与先例 WindowOperator `numLateRecordsDropped`（`WindowOperator.java:438/:471`，`window@identityHashCode`）同构。
- **发射点接入**：`output.collect` 全文件恰 2 处——match 发射 `:276` 与补齐发射 `:291`，各自下一行即 `incOutputs()`（`:277/:292`）。补齐路径经 `completeUnmatched` 一个递增点覆盖全部 3 条调用路径（`processElement → emitMatches`；`processWatermark → completeAndTrimHash → completeUnmatched :212`；`processWatermark → fireReachedWindows → fireWindow → completeUnmatched :256`）——任务书所述「三处发射点」实为 3 条发射路径 2 个递增点 2 个 collect 点，**计数覆盖无遗漏、无虚计**。
- **未新增变更型方法**：WI22 diff 仅 +field/+注册/+`incOutputs()`/+2 调用；`incOutputs()` 为 **private**（`:420`），机械分类器排除私有方法（`InvariantTableCompleteness.isChangeTypeMethod` 首条即 `Modifier.isPrivate → false`），gate-inventory 方法清单保持精确——经 live 反射双向等价测试实证（§2）。

### 1b. OverWindowOperator `numOverRowsEmitted`（runtime）——读码核验全通过

- **W-M1 三要素**：`open()` 注册（`OverWindowOperator.java:115-117`，同一 instance-identity scope 形态）；`copyForSubtask()` 新实例（`:95-99`）。
- **发射点接入**：rn 行发射 `:190-192`、帧聚合行发射 `:232-234`——「rn+帧聚合两处」逐行命中，两处均 null 守卫后 `increment()`。`output.collect` 全文件恰此 2 处，计数无遗漏。

## 2. gate-inventory / catalog §4 登记与双向反射等价（审计项 2）——PASS

- **gate-inventory.json**：`io.nop.stream.core.operators.join.EquiJoinOperator`（10 方法）与 `io.nop.stream.runtime.operators.windowing.OverWindowOperator`（13 方法）已入 modules 映射；方法集与 live 源码声明逐一比对一致（含 `incOutputs` 正确排除）。
- **双向精确等价实跑**：`TestInvariantTableCompleteness`（core，数据驱动遍历 gate-inventory core 段全部类，`findViolations` 双向：表有码无 = ghost 红 / 码有表无 = 漏登红）**11/11 绿**；`TestRuntimeInvariantTableCompleteness` 同构参数化覆盖 OverWindowOperator，runtime 全量内绿（§4）。`ChangeTypeMethodClassifier` 语义镜像经 javadoc 与 `InvariantTableCompleteness.java:126-156` 实读确认与 mjs 解析器一致。
- **sync 反查**：`node ai-dev/tools/check-nop-stream-invariants.mjs sync` → `sync: OK`（exit 0）。反查语义经 mjs 源码实读确认（`syncCatalogWithTable`：catalog §4 类缺于 gate 表即红，类在而方法缺亦红）——两算子同入 catalog §4 与 gate 表，缺登记形态会被该门捕获，roadmap WI22 行的显式断言成立。
- **catalog §4 人类表**：两行已加（`invariant-catalog.md:212-213`），方法清单与 gate-inventory 集合一致（仅排序不同），「WI22 登记机械分类器全量」注记在案。

## 3. invariants 重钉逐行对照（审计项 3）——PASS

**无参全量**：`node ai-dev/tools/check-nop-stream-invariants.mjs` → exit 0，0 violations；分项 `inventory` / `sync` / `scan-iterations` / `scan-output-contract` / `scan-wiring` / `check-wildcard-imports` 六门逐一 exit 0；`mjs-pins.json` `pinnedViolations = []` 无陈旧钉。

### 3a. output-contract V4 四处发射点（逐行实读，均为真实 OutputTag emission）

| 注册表钉点 | live 行内容 | 语义 | 判定 |
|---|---|---|---|
| WindowOperator `:1327` | `output.collect(lateDataOutputTag, element);`（`sideOutput()` 内） | late-data sideOutput，field-form tag（`lateDataOutputTag` 实际声明 `:181`） | ✓ |
| WindowOperator `:1926` | `output.collect(outputTag, new StreamRecord<>(value, window.maxTimestamp()));` | 窗口 maxTimestamp side output，参数形 tag（方法声明 `:1922`） | ✓ |
| CepOperator `:875` | `output.collect(lateDataOutputTag, element);` | late-data sideOutput（field `:200`） | ✓ |
| CepOperator `:1356` | `output.collect(outputTag, record);`（`ContextFunctionImpl.output :1349` 内） | timed-out partial matches 经 `PatternTimeoutFlatSelectAdapter$SideCollector.collect :100`（实读命中 `ctx.output(timedOutPartialMatchesTag, record)`） | ✓ |

V5 重钉映射与旧注册表 diff 对照精确：Window `:1284→1327`、`:2128→1926`、Cep `:711→875`、`:1113→1356`（语义迁移注记「inner-context shape → ContextFunctionImpl.output :1349-1356」与 live 结构一致）。ProcessOperator `:124/:147` 两既有钉点实读命中。emission `line` 字段（scanner 校验对象）全部与 live 一致——V5 归零属实。

### 3b. wiring V3 七处重钉（逐行实读，全部命中）

| 旧钉点（baseline） | 新钉点 | live 行内容 | 判定 |
|---|---|---|---|
| StreamTaskInvokable `:536` | `:528` | `abstractOp.setProcessingTimeService(pts);` | ✓ |
| `:537` | `:529` | `abstractOp.setTimeServiceManager(tsm);` | ✓ |
| `:323` | `:266` | `op.setOutput(new RecordWriterOutput(fanOutWriters.get(0), taskMetrics));`（fan-out tail 单 writer） | ✓ |
| `:329` | `:272` | `op.setOutput(new BroadcastingRecordWriterOutput(outputs));` | ✓ |
| `:500` | `:492` | `op.setOutput(new RecordWriterOutput(outputWriter, taskMetrics));`（wireTailToRecordWriter） | ✓ |
| `:509` | `:501` | `((AbstractStreamOperator<?>) operators.get(i)).setSnapshotCallback(...)` | ✓（重钉但无 WI22 note，仅由 updated 头注记覆盖——Minor-3） |
| GMCE `:730` | TaskCheckpointWiring `:175` | `abstractOp.setStateBackend(stateBackend);`（`wireTaskCheckpointPipeline` 循环内，checkpointConfig 驱动、为无 backend 算子供给） | ✓ |

**GMCE 迁移注记准确性**：`TaskCheckpointWiring.java:132` 即 `wireTaskCheckpointPipeline`，`:175` 位于其 per-operator 供给循环；`GraphModelCheckpointExecutor.java:722` 委托 `TaskCheckpointWiring.wireTaskCheckpointPipeline(...)`，GMCE 主文件内已无 `setStateBackend` 调用——「WI14 状态后端供给重构迁入、同注入同语义」注记与 live 完全一致。

## 4. 实跑矩阵与 FU-10（审计项 4）——PASS（附 1 Major 证据语义修正）

### 4a. 计数与门禁实跑

| 模块 | 实跑 | 结果 |
|---|---|---|
| nop-stream-core 全量 | `./mvnw test -pl nop-stream/nop-stream-core` | **Tests run: 1669, Failures: 0, Errors: 0, Skipped: 1**——与声称 1669 精确一致，零退化 ✓ |
| nop-stream-sql 全量 | `./mvnw test -pl nop-stream/nop-stream-sql` | **Tests run: 78, Failures: 0, Errors: 0**——与声称精确一致 ✓ |
| TestOutputContractInvariant（core） | 定向实跑 | **14/14 绿**（4 参数化 implementationClasses + 10 具名）✓ |
| invariants 无参全量 + sync | 两次实跑 | exit 0 / sync OK ✓ |

runtime 计数 1233 与声称一致（WI22 在 gate-inventory runtime 段新增 OverWindowOperator 使 `TestRuntimeInvariantTableCompleteness` 参数化用例 +1：父提交 1232 → HEAD 1233，勾稽闭合）。

### 4b. 四象限矩阵（审计侧自建探针，裁定关键证据）

工作树 clean、WI22 已提交，故「stash 复核」以父提交 `924b4427e5` 的 git worktree（`_tmp/audit-wi22/wt-parent`，验证后已移除）为等价实现：

| 象限 | 提交态 | 构建模式 | runtime 全量结果 |
|---|---|---|---|
| 1 | HEAD（WI22） | no-am（双层 local repo 构件解析） | 1233 tests，**1F**：`TestStreamConnectorRegistryDiscovery.testCapabilityMatrixAlignedWithDesignAdjudication`（jdbc-2pc required params 期望 4 实得 2） |
| 2 | HEAD（WI22） | no-am 复跑 | **同败**（确定性，2/2） |
| 3 | 父提交（无 WI22） | `-am`（reactor 源码一致构建） | 1232 tests，**0F 0E 全绿** |
| 4 | HEAD（WI22） | `-am` | 1233 tests，**0F 0E 全绿，BUILD SUCCESS** |

**因果钉死**：象限 4 与象限 1/2 唯一变量是构建模式——`-am` 下 connector-jdbc 与 runtime 均从源码入 reactor（`mvn-runtime-head-am.log` reactor 列表 `[452/453] connector-jdbc、[453/453] runtime`）即全绿；no-am 下这些 test 依赖走 local repo（head repo `.nop/repository` 缺 runtime/connector-jdbc jar，仅 resolver-status 负缓存；tail `~/.m2` 构件时间戳异构：core Oct 3 05:15、runtime Sep 29、connector-jdbc Oct 3 10:03），jdbc-2pc capability 断言确定性失败。**失败与 WI22 提交态零相关**——「非本 WI 引入」结论成立，且由比声称更强的证据（HEAD 自身 reactor 全绿）直接证明。

### 4c. Major-1（收口必改，不阻断翻转）：FU-10 归因叙事与实测不符

plan Exit Criteria / Closure Gates 第 4 项 / 日志 / roadmap FU-10 四处一致声称：

1. 「runtime 1233 中 **1F+1E**」——审计 3 次 runtime 全量实测均为 **1F+0E**：`TestE2EJdbcTwoPhaseCommitSink.testSourceReplayLedgerEliminatesDuplicates` 在本机三种配置下均未复现（4/4 绿）；其失败若存在，同为环境绑定而非常态。
2. 「**stash 实证 HEAD 同败**，非本 WI 引入」——受控矩阵显示父提交 `-am` 全绿、HEAD `-am` 全绿；失败仅在 no-am 构件模式下复现。执行者当年的 stash 实验若在主树 no-am 模式进行，则「同败」为该模式下的构件态同败（父/HEAD 两提交在 no-am 下共享同一 local repo 状态，同败符合预期），**但该证据支撑的是「构件模式相关」而非「提交态既有」**——措辞需按 §4b 矩阵改写。
3. FU-10 的根因猜测「疑似**用例间状态污染或 H2 会话残留**」被四象限矩阵否定：失败对构建模式确定（no-am 2/2 败、-am 1/1 绿），非测试间随机污染。真正的排查方向是 **`.mvn/maven.config` 双层 local repo（head=`.nop/repository` 缺构件 → tail 异构）下 no-am 构建的类路径完整性**。

按 guide 规则 18/19，这些文本将被 `completed` 态固化并误导 FU-10 后续排查，须随 Phase 2 收口一并改写（不改不翻转）。

## 5. 门禁与 git 纪律（审计项 5/6）——PASS

- `node ai-dev/tools/check-doc-links.mjs --strict` → **exit 0**（0 errors，3 warnings 均在无关的 `ai-dev/plans/nop-bytecode/06-resource-leak-v1.md`，非本 WI 引入——`git log` 证实该文件早于 WI22）。
- `node ai-dev/tools/check-plan-checklist.mjs .../29-...md --strict` → exit 0（active 计划未勾项为 warning，Phase 2 待审计态合法）。
- roadmap：WI22 行 `todo`（待 audit 后翻转，正确）；FU-10 已登记 Follow-up Backlog（`:297`，故意置于 Work Item Status 块外不计分母，登记位置正确；行序插在 FU-9 之前——序号乱序，cosmetic）。
- 提交纪律：WI22 提交 9 文件（8 M + 1 A），唯一新增为 plan 29 本身；`_tmp/`（gitignored `.gitignore:120`）探针未入库；工作树 clean。
- plan/日志一致性：Phase 1 四项与 Exit Criteria 四项勾选与 live 证据一致（本 audit 逐项复核成立）；Phase 2 与 Closure Gates 待审计态；日志条目（`ai-dev/logs/2026/10-03.md`）与实测事实一致——除 §4c 三处 runtime 叙事外。

## 6. 发现清单

| # | 级别 | 发现 | 修复时点 |
|---|---|---|---|
| MAJ-1 | Major | runtime 失败归因叙事失实：`1F+1E`（实测 1F+0E，E 侧三次未复现）；「stash 实证 HEAD 同败」（四象限实测：父 `-am` 绿 / HEAD `-am` 绿，失败仅 no-am 构件模式复现）；「疑似用例间状态污染或 H2 会话残留」（实测为 no-am 双层 local repo 构件态问题，模式间确定性）。四处文本（plan Exit Criteria + Closure Gates + 日志 + FU-10）按 §4b 矩阵改写 | Phase 2 收口时（不阻断翻转） |
| MIN-1 | Minor | wiring-registry Output 服务 `:266` 双条目：其一（「wireOperators -> ChainingOutput」）语义陈旧（live `:266` 自父提交起即 RecordWriterOutput 接线，ChainingOutput 实际在 `:304` chainInnerOperators）——**WI22 之前已存在的遗留**（父提交实读证实），V3 行内容检查的盲区；`:304` 条目的方法归属注记（「wireOperators(fanOutWriters)」）同因 G4 重构而间接化 | 随 MAJ-1 一并清理或留 FU-10 排查时顺手修 |
| MIN-2 | Minor | output-contract-registry 描述性注记漂移：tagForm 内嵌行号（Cep lateDataOutputTag「:153」实际 `:200`、output 方法「:930」实际 `:1349`；Window「:179」实际 `:181`、「:1856」实际 `:1922`）——旧注册表原样携带的遗留；新增的 `:1356` 第二条目 tagForm 写「field-form」而实际为参数形 tag（与第一条目重复同点）——scanner 校验的 `line` 字段全部正确，纯注记噪声 | 同上，随手修 |
| MIN-3 | Minor | Closure Gates 混合勾选态（仅第 4 项勾选，其余 6 项留待收口）；wiring `:501` 重钉无 WI22 note（仅 updated 头注覆盖）；FU-10 行插于 FU-9 之前 | 收口翻转时统一勾齐即自然消解 |

## 7. 收口动作清单（PASS 后执行）

1. **改写 MAJ-1 四处叙事**（plan 29 Exit Criteria 第 3 项与 Closure Gates 第 4 项、`ai-dev/logs/2026/10-03.md` WI22 节、roadmap FU-10 行）：runtime 结论改为「1233 中 1F（no-am 构件解析模式确定性复现 ×2）；`-am` reactor 一致构建 HEAD 全绿 1233/0F/0E（含父提交 `-am` 1232 全绿对照）——失败为本地双层 local repo（head 缺 runtime/connector 构件）的构件态问题，与提交态无关；TestE2EJdbcTwoPhaseCommitSink 三次未复现，保留 FU-10 观察」；FU-10 排查方向从「状态污染/H2 残留」改为「`.mvn/maven.config` 双层 repo 的 no-am 类路径完整性」。
2. roadmap WI22 行 `todo` → `done`；解析器断言 items=31/milestones=7/WI22=done。
3. Plan 29 Phase 2 勾齐、Closure Gates 七项勾齐（MIN-3 消解）、Status → `completed`、Closure Status Note / Reviewer / Evidence 回填本 audit 路径。
4. `check-plan-checklist --strict` 与 `check-doc-links --strict` 双 exit 0 复跑确认。
5. MIN-1/MIN-2 注册表注记清理可随收口顺手做（不强制，scanner 不校验；如做则无参全量复跑归零）。
6. 提交并按惯例 commit message 注明 audit PASS + MAJ-1 叙事修正随收口。

## 证据索引

- 探针与日志（gitignored）：`_tmp/audit-wi22/`——`mvn-core-full.log`（1669/0F/1S）、`mvn-sql-full.log`（78/0F）、`mvn-runtime-full.log`（1233/1F）、`mvn-runtime-full-rerun.log`（1233/1F 同败）、`mvn-runtime-head-am.log`（1233 全绿 reactor）、`mvn-parent-fu10-am.log`（父提交两 FU-10 用例隔离 12/12 绿）、`mvn-parent-runtime-full.log`（父提交全量 1232 全绿）、`mvn-core-invariant-tests.log`（14/14 + 11/11）。
- 父提交 worktree（已移除）：`git worktree add _tmp/audit-wi22/wt-parent 924b4427e5`——等价 stash 实验（工作树 clean、WI22 已提交，worktree 为忠实等价物）。
