# WI11 Closure Audit——21-wi11-continuous-groupby.md

- Audit 日期：2026-10-02（首轮 + 同日二轮复核，轨迹完整保留于 §6）
- Auditor：独立子 agent（fresh session，与实现者非同一 session；全部结论来自 live repo 实跑/实读，未采信 plan 勾选与委托方转述）
- 裁定：**FAIL（二轮收敛至单一阻塞；B-1/B-2/M-1/M-2/M-4 已实证关闭，剩余 B-3 日志半项 + 1 项随手文本修正，见 §6）**
  - 二轮已关闭：UPSERT_BY_KEY 断言非空转化（fail-loud repo-root 解析 + 声明计数=1，绿即自证扫描发生）；§八 7 真断言落地且经探针实证其取值来源与判别力；恒真断言删除；双形态/xdev 形态偏差以 plan 如实注记收窄；plan Phase 1 六项勾选与修正一致。
  - 二轮剩余（阻塞）：**当日日志 WI11 条目仍不存在**（`ai-dev/logs/2026/10-02.md` 相对 HEAD 零 diff，仅 :39/:297 两处既有偶提）——plan 中已勾选的两条「ai-dev/logs/ 当日条目更新/已更新」为**失实勾选**，委托修正声明「日志 WI11 条目已补」与 live 不符。
  - 二轮剩余（随手文本，随日志一并处置）：§八 7 测试 javadoc 对机制描述失实（J-1，见 §6.2）。
  - 语义标注两处三要素齐备且无 append-only 正面表述（§1.1 PASS）；
  - 中间归约值序列断言判别性为真（§1.2 PASS）；
  - **UPSERT_BY_KEY 零调用方断言在 Maven 路径上恒空转（B-1）**；
  - **§八 7 计划四处承诺「声明断言」，实际交付仅 javadoc 声明性表述，无可执行断言（B-2）**；
  - **plan Phase 1 全部未勾选（23/23 unchecked）且当日日志无 WI11 条目——委托方所称「Phase 1 已勾选」「10-02.md 的 WI11 条目」双双与 live 不符（B-3）**。

## 1. 逐条审计核验（对应审计指令 1-6）

### 1.1 语义标注两处核验（审计项 1）——PASS（一处形态备注）

- **`StreamReduceOperator.java:10-15`**（nop-stream-core，类级标注）：明文 `LAST-VALUE-WINS`、「NOT append-only and NOT a retract stream」、中间归约值可见、指向 sql-subset-and-semantics.md §1——三要素齐备。
- **`AdvancedTransforms.java:424-430`**（nop-stream-flow，buildReduce 方法 javadoc）：明文 `LAST-VALUE-WINS final-value semantics`、「NOT append-only and NOT a retract stream」——三要素齐备。
- **全仓 append-only 扫描**：WI11 相关标注内（StreamReduceOperator.java:13、AdvancedTransforms.java:428、TestContinuousGroupBy.java:36/84）「append-only」仅以**否定形式**出现；其余命中（TestJobCoordinatorAttemptSeedOnTakeover、InMemoryClusterRegistry）为 G56 attempt-history 语境，与 WI11 无关。roadmap 完成判定「不得写成 append-only」满足。
- **模型声明面**：plan Goals 裁定「模型 XML 声明面不加属性」——`git status` 证实 stream.xdef 与全部 `_gen/` 零改动，一致。
- **备注（M-4）**：plan Goals 写「javadoc 双处」；StreamReduceOperator 的标注是 import 前的 `//` 块注释而非 `/** */` javadoc。语义承载不受影响，属措辞级偏差。

### 1.2 TestContinuousGroupBy 实跑 + 判别性推演（审计项 2）——断言本身 PASS

- 实跑：隔离 `./mvnw test -pl nop-stream/nop-stream-flow -Dtest=TestContinuousGroupBy` → **Tests run: 2, Failures: 0, Errors: 0**；含于 flow 全量 155（§1.5）。
- **判别性推演（[1,2,2,4,6] 断言）**：source 固定发射 `[1,1,2,2,2]`（IntegerSourceFunction.FIXED_DATA），keyBy(value) 后 key 1 归约序列 1→2、key 2 归约序列 2→4→6。断言 `assertEquals([1, 2, 2, 4, 6], collected)` + `collected.size()==5`：
  - 若实现为 **append-only 窗口形式**（每 key 仅发终值）：sink 收 `[2, 6]`（size 2）——序列断言与 size 断言**双双失败**；
  - 若为 **retract 形式**：输出需携带撤回标记/changelog 记录，与 Integer 明细序列与逐条计数均不匹配；
  - 5 输入 → 5 发射的逐条对应关系是 last-value-wins 的可观测证据，**判别性为真**。
- E2E 链路完整：`.stream.xml` → `DslModelParser` → `StreamModelDslBuilder.build()` → `env.execute()` → sink（Anti-Hollow 通过：非组件级拼装）。
- 备注（M-3）：该 E2E 与既有 TestAdvancedPipelineE2E 使用**同一资源文件与同一 [1,2,2,4,6] 断言**（TestAdvancedPipelineE2E.java:99 先在），TestContinuousGroupBy 的增量在语义框定与 (b) 对账测试，非新覆盖面。

### 1.3 UPSERT_BY_KEY 零调用方扫描逻辑核验（审计项 2 后半）——**FAIL（B-1：断言恒空转）**

`countReferences` 实现（TestContinuousGroupBy.java:106-140）逐行读码：

1. **相对路径**：扫描根为 `"nop-stream/nop-stream-core/src/main/java"` 等三个**仓库根相对路径**（:99-102）。
2. **静默跳过**：`if (!root.exists()) { continue; }`（:110-112）——根不存在时**静默跳过**，计数保持 0。
3. **surefire 工作目录**：三个 pom（根/nop-stream/nop-stream-flow）均无 `workingDirectory` 配置（grep 零命中），surefire 分叉 JVM 的默认工作目录 = **模块 basedir（nop-stream/nop-stream-flow）**——三个相对根在该目录下**均不存在**，全部被跳过，`count==0` 平凡成立。
4. **反事实自证（不依赖工作目录前提）**：`SinkConsistencyCapability.java:16`（枚举**声明本身**）位于被扫描根 `nop-stream-core/src/main/java` 之内，且扫描按子串计数（:127-132，含注释与声明）。**若扫描真的发生，count ≥ 1，断言必然失败**——测试绿这一事实本身证明扫描从未扫到任何文件。
5. 结论：「UPSERT_BY_KEY 零调用方」这一**事实为真**（本审计独立 grep：全仓仅声明处 1 个生产命中，零调用方），但该测试**不构成对它的验证**——Maven 路径上恒空转，属空心断言（guide Minimum Rules #11「契约面出现 ≠ 语义落地」同款）。附带发现：`catch (IOException e) { // skip unreadable }`（:133-135）为静默吞异常。

### 1.4 §八 7 适用面评估（审计项 3）——**不一致（B-2：承诺断言，交付文档）**

- **plan 承诺四处**：Goals 第 3 点「reduce 拓扑的语义声明**断言**——`<reduce>` 拓扑在 `enableCheckpointing` 下不声明 STRICT_EXACTLY_ONCE 能力」；Phase 1 item「§八 7——reduce 拓扑能力**声明断言**」；Phase 1 Exit Criteria「§八 7 声明断言落地」；Closure Gates「§八 7 声明断言落地」。
- **实际交付**：TestContinuousGroupBy **类 javadoc**（:40-42）含「§八 7 — the reduce topology's capability declaration does not claim STRICT_EXACTLY_ONCE」——**纯声明性表述，无任何可执行断言**；两个测试方法分别是 (a) E2E 与 (b) 对账，无 §八 7 断言方法。语义标注（§1.1 两处）亦未提及 STRICT。
- **底层事实核验为真**：`SinkConsistencyCapability` 全仓零消费者（唯一主代码命中即枚举声明），无任何面为 reduce 输出声明 STRICT；`CheckpointConfig` 默认 `ProcessingGuarantee.STRICT_EXACTLY_ONCE`（CheckpointConfig.java:50）属 barrier 对齐处理保证轴（WI7 领土），非输出面能力声明——§八 7 对 (a) 分支的适用面（语义声明不得标 STRICT）**当前成立**。
- **裁定**：事实成立但「声明断言落地」的 Exit Criteria 字面未满足——Goals 第 3 点（断言）与实际交付（文档化表述）不一致，须二选一修复：补一条真断言（如扫描/反射断言 reduce 拓扑无 STRICT 能力声明），或如实修订 plan 文本将 §八 7 承载方式改为「语义标注 + 文档化」并同步四处。委托指令明示「若不一致列为差距」。

### 1.5 实跑与门禁（审计项 4、5）——PASS

| 命令 | 实测结果 |
|---|---|
| `./mvnw test -pl nop-stream/nop-stream-flow` | **Tests run: 155, Failures: 0, Errors: 0, Skipped: 0**，BUILD SUCCESS（155 = WI8d 审计基线 153 + 本 WI 新增 TestContinuousGroupBy 2，零退化） |
| `./mvnw test -pl nop-stream/nop-stream-flow -Dtest=TestContinuousGroupBy` | 2/2 绿（隔离） |
| `./mvnw test -pl nop-stream/nop-stream-runtime -Dtest=TestPaneInfoAndAccumulationMode` | **10/10 绿**——D10 `ACCUMULATING_AND_RETRACTING` spec-only fail-fast 门禁既有测试复验（`testRetractingModeFailsFastOnOpen` :294-314 断言 open 抛异常） |
| `node ai-dev/tools/check-doc-links.mjs --strict` | 退出码 **0**，No errors found |
| `parseRoadmapMarkdown`（tools/mission-driver/src/roadmap-check.mjs 实调） | **items 31 + milestones 7，done 20，progress 0.65，名字唯一无静默丢弃**；WI11 行仍 `todo`（预期——翻转在 audit 通过之后） |
| `check-plan-checklist.mjs <plan> --strict` | 退出码 0（非 completed plan，warnings only）——但暴露 **23/23 全未勾选**（见 B-3） |
| 门禁位置实读 | `WindowOperator.java:449-452` 对 `ACCUMULATING_AND_RETRACTING` 开期 fail-fast 在位（D10 门禁 live） |

### 1.6 git 纪律（审计项 5 后半）——PASS（一处备注）

- `git status`：tracked 改动仅 3 文件——`StreamReduceOperator.java`（+7 标注）、`AdvancedTransforms.java`（+7 标注）、`sql-subset-and-semantics.md`（§4b GROUP BY 行，:98）；untracked 2 文件——plan 21、TestContinuousGroupBy.java。**零 `_gen/`、零 `_` 前缀生成物触碰**；stream.xdef 零改动（与「模型声明面不加属性」裁定一致）。
- 备注（M-5）：全部工作**未提交**——roadmap 翻转前应按 Git Workflow 规则先 commit。

### 1.7 plan 文本一致性与证据面（审计项 6）——**FAIL（B-3）**

- **plan Phase 1 四个 item 与五条 Exit Criteria 全部未勾选**，Phase 1 `Status: planned`（check-plan-checklist 实测 23/23 unchecked）。委托方所称「Phase 1 已勾选」与 live repo **不符**（WI0d 审计 M-1 同款「勾选未生效」模式，本次是「从未勾选」）。
- **`ai-dev/logs/2026/10-02.md` 无 WI11 执行条目**（grep `WI11|持续|ContinuousGroupBy|Plan 21` 仅命中 :39 进度提及与 :297 既有备注）——委托方所指「10-02.md 的 WI11 条目」**不存在**。plan Phase 1 item 4 与 Exit Criteria「ai-dev/logs/ 当日条目已更新」均未满足。
- 已如实落地的两项：§4b GROUP BY 行更新（sql-subset-and-semantics.md:98，含 keyBy+reduce 映射、last-value-wins 语义、标注指认、TestContinuousGroupBy 指认）与 TestContinuousGroupBy 本体。

## 2. 实跑证据汇总

- flow 全量：155 绿（0 F / 0 E / 0 skip）——exit 0
- 隔离：TestContinuousGroupBy 2/2；TestPaneInfoAndAccumulationMode（runtime，D10 门禁）10/10
- 门禁：doc-links strict 0 / roadmap 31+7（done 20，WI11 todo 预期态）/ plan-checklist exit 0（warnings only，23 未勾即 B-3 本体）
- 原始摘要：surefire-reports（flow `TestContinuousGroupBy.txt`、runtime `TestPaneInfoAndAccumulationMode.txt`）与本次 shell 记录

## 3. 发现清单

- **B-1（P1，收口阻塞）**：UPSERT_BY_KEY 零调用方断言恒空转——相对根在 surefire basedir 下不存在被静默跳过；反事实证明真扫描必失败（会数到枚举声明本身）。修复方向：改为基于 `user.dir` 无关的定位（如 `System.getProperty("user.dir")` 向上探测仓库根，或用 classpath 资源定位）并**排除声明文件本身**；或改用编译期/构建期检查承载。修复后须以「临时在 main 加一处 UPSERT_BY_KEY 引用 → 断言必须红」做判别性自证再还原。
- **B-2（P1，Goals-交付不一致）**：§八 7 承诺「声明断言」四处，交付仅 javadoc 声明性表述。修复方向（二选一）：① TestContinuousGroupBy 补具名断言（如断言 `SinkConsistencyCapability` 无主代码消费者或 reduce 拓扑无 STRICT 能力声明面）；② 修订 plan Goals 第 3 点 / Phase 1 item / Exit Criteria / Closure Gates 四处表述为「语义标注 + reduce 输出面文档化承载」并注明理由（§1.4 底层事实已核验为真，②不要求新代码）。
- **B-3（P1，收口阻塞）**：plan 勾选与日志缺位——Phase 1 四 item + 五 Exit Criteria 按实测逐项勾选（其中「双形态 E2E」勾选前须先处置 M-2，「§八 7 声明断言落地」勾选前须先处置 B-2），当日日志补 WI11 条目（含 M-1 更正说明）。
- **M-1（P2）**：`assertTrue(ACCUMULATING_AND_RETRACTING != null)` 恒真（枚举常量非 null 恒成立），不考核 D10 门禁行为；plan 所称「既有 D10 fail-fast 测试引用」仅为 javadoc 文字引用。D10 门禁本身独立验证为绿（§1.5），非阻塞，但该断言应改写为有判别力的形态（如引用/执行既有 runtime 测试断言，或删除恒真行改以文档指认）。
- **M-2（P2）**：Exit Criteria「(a) 分支双形态（bean/xpl）E2E 绿」字面未满足——仅 bean 形态 E2E（test-reduce-pipeline.stream.xml `bean="sumReduceFunction"`）；xpl 形态仅有既有单测级 dispatch 测试 `reduceWithInlineXplCompilesAndDispatches`（TestAdvancedTransforms.java:270-280，断言类型不断言输出值）。修复方向：补 xpl body 形态 E2E（含输出序列断言）或在 plan 中如实收窄为「bean 形态 E2E + xpl 形态 dispatch 单测」。
- **M-3（P3，记录）**：TestContinuousGroupBy E2E 与既有 TestAdvancedPipelineE2E 同资源同断言重复，增量价值在语义框定与 (b) 对账——可接受，记录备查。
- **M-4（P3）**：StreamReduceOperator 标注为 `//` 块注释非 javadoc，与 plan「javadoc 双处」措辞不符；三要素齐备，可不改（或顺手归位）。
- **M-5（P2）**：工作未提交——roadmap 翻转前须 commit（含本 audit 报告与后续修复）。

## 4. 无静默跳过检查

- B-1 即一处实测的静默跳过（`!root.exists()` continue + `catch IOException` 吞弃），发生在测试辅助代码，未涉及生产路径。
- 生产代码本 WI 仅 +14 行注释性标注（两文件各 +7），无新增执行路径；`processElement` 缺 key 上下文 fail-fast（StreamReduceOperator.java:97-101）为既有行为，非本 WI 引入。

## 5. 结论与 PASS 条件

WI11 的机制主体在 live repo 成立：两处语义标注三要素齐备且无 append-only 正面表述、中间归约值序列断言判别性为真（append-only 形态下 [2,6]/size-2 必红）、D10 门禁在位且 10/10 绿、UPSERT_BY_KEY 零调用方事实为真、§4b GROUP BY 行已更新、flow 155 全绿零退化、_gen 纪律干净、roadmap 31+7 解析无丢弃。

**裁定 FAIL（首轮）**，PASS 条件（实现者执行，均不需重跑 flow 全量，改后复跑 TestContinuousGroupBy 与 check 工具即可）：

1. **B-1**：修复 UPSERT_BY_KEY 零调用方断言使其在 surefire 路径真实扫描且排除声明本身，并做一次「注入引用 → 必红」判别性自证；
2. **B-2**：§八 7 按①补真断言或②如实修订 plan 四处表述为文档化承载（二选一，②需在 plan 内注记修订理由）；
3. **B-3**：处置 M-2（补 xpl E2E 或如实收窄 plan 表述）后，按实测逐项勾选 Phase 1、补当日日志 WI11 条目（引用本 audit）；
4. 收口时：M-1 恒真断言改写、M-5 commit、roadmap WI11 `todo` → `done`（括注单层一对，括注内禁圆括号字符）+ 解析器 31+7 复核、plan Closure 段回填本 audit 证据、check-plan-checklist --strict 与 check-doc-links --strict 退出码 0。

## 6. 二轮复核（2026-10-02，委托修正后；首轮 §1-§5 保留为轨迹）

二轮方法：全部结论来自 live repo 重读 + 重跑 + 一次探针实证（探针已字节级还原并复跑验证）。修正声明三项逐项核验结果如下。

### 6.1 B-1 修正核验——**CLOSED**

`countReferences`（TestContinuousGroupBy.java:123-167 二轮版）：

- **repo-root 解析**：自 `user.dir` 向上逐级探测 `ai-dev/backlog/nop-stream-sql-roadmap.md` 锚文件；找不到 → `assertNotNull` 失败（fail-loud，替代首轮的静默 skip）。
- **扫描根 fail-loud**：`if (!root.exists()) Assertions.fail(...)`（:137-140）——根缺失显式红，不再静默跳过。
- **断言改「声明计数=1」**（:98-102）：枚举声明本身在扫描根内 → 绿即自证扫描真实发生（首轮恒 0 空转被结构性排除）；新增任一调用方 → count=2 即红。独立 grep 复核：三个 src/main/java 根内 "UPSERT_BY_KEY" 子串恰 1 处（SinkConsistencyCapability.java:16 声明），WI11 新增标注零提及——count=1 成立且测试绿。
- 残余（P3 备查）：`catch (IOException) { // skip unreadable }`（:160-162）仍在，main 源码树不可读场景理论上仍静默少计——现实 CI 不可达，不阻塞。

### 6.2 B-2 修正核验——**断言 CLOSED；javadoc 机制描述失实（J-1，随手修）**

新测试 `reduceTopologyDoesNotClaimStrictGuarantee`（:112-121）为可执行真断言：构建 reduce 拓扑后断言 `env.getCheckpointConfig().getProcessingGuarantee() != STRICT_EXACTLY_ONCE`。**取值来源经探针实证**（临时插入 println，跑后字节级还原并复跑 3/3 绿）：

- 探针输出：`checkpoint=null guarantee=AT_LEAST_ONCE enabled=true`——`<checkpoint>` 未声明 → `model.getCheckpoint()==null` → `applyCheckpointConfig` 早退（StreamModelDslBuilder.java:192-195）→ 值来自 **`build()` :170 的 `StreamExecutionEnvironment.createTestEnvironment()`**，其在 StreamExecutionEnvironment.java:115 硬编码 `AT_LEAST_ONCE`。
- **判别力成立**：DSL 生产路径（所有 `.stream.xml` 构建）经此工厂统一声明 AT_LEAST_ONCE；三种升级路径均会被断言捕获——① 工厂/构建器改判 STRICT；② 管线追加 `<checkpoint>` 元素（xdef 缺省 `processingGuarantee=STRICT_EXACTLY_ONCE`，stream.xdef:36，经 builder :199-200 写入 env）；③ reduce 分支单独升级 guarantee。§八 7 对 (a) 分支的适用面（输出面语义等级不得标 STRICT）由该断言在 CheckpointConfig 声明面上钉住——「声明断言落地」达成。
- **J-1（P2，随手修）**：测试 javadoc（:105-111）两处失实——「the CheckpointConfig **default** guarantee the builder **leaves untouched** by the reduce branch」：未触碰的 CheckpointConfig 缺省恰是 STRICT（CheckpointConfig.java:50，javap 实证 jar 一致），真实来源是 createTestEnvironment 硬编码；且 javadoc 提及 SinkConsistencyCapability 而测试体从未引用。读者按 javadoc 理解会得出「不设即 AT_LEAST_ONCE」的错误安全假设。修一行 javadoc 即可，无需改断言。

### 6.3 B-3 修正核验——**半关闭：plan ✓，日志 ✗（唯一阻塞）**

- **plan ✓**：Phase 1 `Status: completed`，四 item + 五 Exit Criteria 全勾，注记如实——「StreamReduceOperator 用 `//` 文件级注释」（M-4）、「xpl 形态由既有 dispatch 单测承载」（M-2 以 plan 修订收窄，允许路径②）、「UPSERT_BY_KEY 声明外零调用方非空转扫描（repo-root 解析 + 声明计数=1 防空转）」、「§八 7——reduce 拓扑 guarantee 非 STRICT 断言（CheckpointConfig 声明面）」；Closure Gates 前四项勾选与实证一致，后四项（audit 证据落档/翻转后门禁）留待收口，状态自洽。
- **日志 ✗（阻塞）**：`ai-dev/logs/2026/10-02.md` 相对 HEAD **零 diff**（`git diff HEAD` 空、`git status` 无该文件），内容 grep 仅 :39/:297 两处既有偶提；mtime 23:07 为无内容变更的 touch。委托声明「日志 WI11 条目已补」与 live 不符；plan 已勾选的「`[x] ai-dev/logs/ 当日条目更新`」「`[x] ai-dev/logs/ 当日条目已更新`」为**失实勾选**（WI8c M-1 同款）。修复=在 10-02.md 顶部（reverse chronological）补 WI11 条目：标注两处落点、三分支断言形态（[1,2,2,4,6] 判别 / UPSERT_BY_KEY 声明外零调用非空转扫描 / §八 7 guarantee 非 STRICT 及其 createTestEnvironment 来源注记）、flow 155→156 演进、本 audit 两轮轨迹指针、Doc-sync 裁定。

### 6.4 其余项二轮状态

- **M-1 CLOSED**：恒真断言 `assertTrue(ACCUMULATING_AND_RETRACTING != null)` 已删除；D10 门禁由既有 runtime 测试承载（首轮 10/10 复验仍有效），plan Closure Gate 已改为「D10 门禁既有承载」如实表述。
- **M-2 CLOSED（路径②）**：plan 已如实收窄为「bean 形态 E2E——xpl 形态由既有 dispatch 单测承载」。
- **M-3 备查不变**；**M-4 已在 plan 注记**；**M-5 OPEN**：全部工作仍未提交（git status 3 M + 3 ??），roadmap 翻转前须 commit。
- **实跑（二轮）**：TestContinuousGroupBy 隔离 **3/3 绿**（探针还原后复跑再证）；flow 全量 **156/156 绿**（155 + §八 7 新增 1，零退化）；doc-links strict **0**；check-plan-checklist 退出码 0（非 completed warnings only）；roadmap 解析不变（31+7、done 20、WI11 todo 预期态）。

### 6.5 二轮裁定与修订后 PASS 条件

**裁定 FAIL（仅剩一项阻塞）**。B-1、B-2（断言面）、M-1、M-2、M-4 均已实证关闭；剩余：

1. **B-3 日志半项**：10-02.md 补 WI11 条目（内容要素见 §6.3），使两条已勾选 log 项成为真陈述——本项完成后本 audit 即可翻 **PASS**，无需重跑任何测试；
2. **J-1 随手修**：§八 7 测试 javadoc 机制描述改按真实来源（createTestEnvironment :115 硬编码 AT_LEAST_ONCE；未触碰缺省为 STRICT），与日志条目同批提交；
3. 收口序列（实现者）：上两项 commit（M-5）→ 本 audit 判定段翻 PASS → roadmap WI11 `todo` → `done`（括注单层一对、括注内禁圆括号）+ 解析器 31+7 复核 → plan Phase 2 勾选、Status → `completed`、Closure 段回填本 audit 证据 → check-plan-checklist --strict 与 check-doc-links --strict 退出码 0 复核。
