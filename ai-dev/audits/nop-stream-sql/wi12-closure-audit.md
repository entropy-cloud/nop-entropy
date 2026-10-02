# WI12 Closure Audit——22-wi12-over-window-operator.md

- Audit 日期：2026-10-02
- Auditor：独立子 agent（fresh session，与实现者非同一 session；全部结论来自 live repo 实跑/实读，未采信 plan 勾选与日志自述）
- 裁定：**FAIL（收口阻断，一项 Blocker）**——功能面全部成立：缓冲构件独立可测、OVER 双帧语义判别、checkpoint/restore 帧计算连续（rn=1/2/3 跨界）、三个测试类隔离 10/10 绿、runtime 全量 **1206** 零退化、门禁全 0、D1=(a)/D6=(a)/Q2 标注如实、_gen 纪律干净。**唯一 Blocker 是状态通道契约**：roadmap :66 绑定句「WI12 的每 key 有序缓冲必须落在 namespace-based keyed state 上（归 WI12 完成判定）」在 live 实现中不成立——缓冲是自管 `HashMap<K,TreeMap>`，OverWindowOperator 全程未触碰 keyed state backend，快照走 **operator state** 整体对象通道；这正是 §十（`00-vision.md:161`）拒绝的「自管 HashMap 做窗口状态」形态，且与 WI13 复用义务（`join-operator.md:23`「join 缓冲同规则」）冲突。此为 owner-doc 与 live baseline 的 confirmed drift，按 guide 不可降级为 follow-up。**在 B-1 关闭（改接线或 owner 显式改判）之前，roadmap WI12 不得翻转 done，plan 不得标 completed。**

## 1. 逐条审计核验（对应审计指令 1-9）

### 1.1 PerKeyOrderedBuffer 独立构件（审计项 1）——PASS

落点 `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/PerKeyOrderedBuffer.java`（package `io.nop.stream.runtime.operators.windowing`，与既有 WindowOperator 同包，plan「runtime/windowing」口径一致）。

- **API 六项全数在位**：`add(key,ts,value)` :67、`sortedView(key)` :75、`trimToWatermark(key,wm)` :108（`headMap({wm,Long.MAX_VALUE}).clear()`，≤ wm 全清、空树摘 key）、`trimToCount(key,count)` :127（保最新 count 条）、`keys()` :147、`putAll(other)` :56（restore 合并路径）；另有 `sortedTimestamps`/`valueAt`/`size` 辅助。
- **有序性机制**：`TreeMap<long[], V>` 以 (ts, insertionSeq) 双键排序 :40/:69——乱序插入按事件时间就位、同 ts 保到达序；`insertionSeq` 单调 :37。
- **Serializable 与具名 comparator**：类实现 `Serializable` :32（`serialVersionUID` :34）；比较器为具名静态嵌套类 `TsSeqComparator implements Comparator<long[]>, Serializable` :43-53（`INSTANCE` 单例）——plan L55 括注的执行期修正（lambda 比较器不可序列化）与代码一致；`TestPerKeyOrderedBuffer.serializableForOperatorDeepCopies` :67-80 以真实 `ObjectOutputStream/ObjectInputStream` 往返钉住。
- **standalone 可测**：构造零依赖，测试直接以 key 参数驱动（5 用例全绿，§2）。

### 1.2 OverWindowOperator 双帧语义与 snapshot/restore（审计项 2）——语义/机制 PASS；**状态通道 FAIL（B-1）**

落点 `.../operators/windowing/OverWindowOperator.java`。

- **双帧分支**：`FrameKind` :48-53（ROW_NUMBER / FRAME_SLIDING_AGG）；`processWatermark` :111-122 对每个缓冲 key 求值后转发 watermark——watermark 触发成立；`emitRowNumbers` :134-141 全帧 1-based 序号发射后 `trimToWatermark` 消费帧；`emitSlidingAgg` :143-168 取末 `frameSize` 条（`from=max(0,size-frameSize)` :145）做 sum/count/avg/min/max，发射 `agg= n=` 后 `trimToCount` 保留滑动历史——「ROW_NUMBER 消费帧 trim、滑动帧保留历史」双轨与代码一致。
- **snapshot/restore**：`snapshotState` :77-83 `putOperatorState("over-window-buffer", buffer)`；`restoreState` :87-97 `buffer.putAll((PerKeyOrderedBuffer) restored)` 合并进新实例。机制存在且被测试证明有效（§1.4）。**但通道是 operator state**：`putOperatorState` 与 keyed state 在 `OperatorSnapshotResult` 中是分立通道（`putKeyedState` 并存）；OverWindowOperator 无 `open()` 覆写、从未创建 `keyedStateBackend`，`AbstractStreamOperator.open()`（:75-79）只建 operatorStateBackend，keyed 快照分支 :308-313 因 `keyedStateBackend == null` 恒不触发。**「keyed state 经 checkpoint 与 restore」的完成判定按 roadmap :66 的绑定口径未被满足**（详见 §3 B-1）。
- **语义标注**：javadoc :25 标 D6=(a) 事件时间；:34-36 标 D1=(a) last-value-wins 终值、无 retract 标记、frame 重开=事件时间重算（Q2）——三处标注齐备且与 `sql-subset-and-semantics.md` §5（Q2 归属）、roadmap D1=(a) re-scope 行（:213「WI12/WI13 保持终值输出」）一致。javadoc :39-41 一句 trim 描述与代码矛盾（见 §3 M-1）。

### 1.3 TestAnalysisWindowEventTime 实跑与判别性（审计项 3）——PASS

隔离实跑 4/4 绿（§2），且包含于全量 1206。判别性读码推演：

- **乱序重排判别（核心）**：`rowNumberEmitsOrderedPerKeyNumbering` :105-122 到达序为 ts30(c)→ts10(a)→ts20(b)，断言 `["rn=1 val=k|10|a","rn=2 val=k|20|b","rn=3 val=k|30|c"]` :119-120。**反事实**：若缓冲按到达序（如 ArrayList 直 append）而非事件时间排序，发射为 `rn=1 val=k|30|c, rn=2 val=k|10|a, rn=3 val=k|20|b`——rn↔value 配对全错，断言必失败。arrival order ≠ event-time order 被该断言钉死。
- **trim 消费判别**：`assertEquals(0, buf.size("k"))` :121——watermark 后帧被消费；若漏 trim 则 size=3 失败。
- **滑动帧判别**：`slidingSumEmitsRunningFrameAggregate` :125-142，frameSize=2 三次 watermark 发射 `[1]→agg=1、[1,2]→agg=3、[2,3]→agg=5`。**反事实**：若第三步按全缓冲（无 trimToCount 历史/或反向 trim 到水位）求值得 agg=6 n=3 或 agg=3 n=1，均 ≠ `agg=5 n=2`——第三拍单独判别「保留 frameSize 历史」。第一/二拍（size≤frameSize）与全缓冲同值，判别力集中在第三拍，成立。

### 1.4 TestE2EOverWindowWithCheckpoint 实跑与判别性（审计项 4）——PASS（测试级；通道问题归 B-1）

隔离实跑 1/1 绿（§2），包含于全量。形态与先例 `TestE2EWindowOperatorWithCheckpoint` 同级同款：runtime 模块、`processBarrier` → `getLastSnapshotResult` 非空且 `isEmpty()==false`（:54-58，缓冲确参与快照）→ 新 operator `restoreState(snapshot)`（:67）→ 后续元素 + watermark 帧计算连续。

- **判别性（读断言确认）**：op2 挂**全新空缓冲** buf2 :61；restore 若无效（putAll 未合并/快照为空），缓冲仅含 c(30)，watermark 100 后发射只有一条 `rn=1 val=k|30|c`，而断言要求恰三条 `["rn=1 val=k|10|a","rn=2 val=k|20|b","rn=3 val=k|30|c"]` :80-81——长度与 rn↔value 配对双重失配，**冷缓冲必失败**。（行内注释 :73「a cold buffer would emit only rn=3」措辞不精确：冷缓冲实际发射 `rn=1 val=k|30|c`；判别力不受影响，见 §3 M-3。）
- **先例对照的精确 delta**：先例用例内显式 `createKeyedStateBackend` + ValueState/MapState 恢复断言（TestE2EWindowOperatorWithCheckpoint :86/:112-131/:170-178）；本用例全程无 keyed backend、恢复物为整体缓冲对象。**「同级」形态成立，通道不同**——这正是 B-1 的落点。

### 1.5 runtime 全量实跑（审计项 5）——PASS

`./mvnw test -pl nop-stream/nop-stream-runtime`：**Tests run: 1206, Failures: 0, Errors: 0, Skipped: 10**，BUILD SUCCESS，exit 0——与 plan L64 / 日志「1206 零退化」逐字吻合；WI11 收口后基线 1196 + 新增 10 = 1206 算术自洽。三个 WI12 测试类均见於全量逐类 `Tests run` 行（5+4+1）。

### 1.6 门禁（审计项 6）——PASS（实跑记录）

| 命令 | 退出码 | 结果 |
|---|---|---|
| `node ai-dev/tools/check-doc-links.mjs --strict` | 0 | 0 errors / 3 warnings（均为 nop-bytecode 旧 plan 既存，与本 plan 无关） |
| `node ai-dev/tools/check-nop-stream-invariants.mjs sync` | 0 | `sync: OK`（OverWindowOperator 尚不在 invariant-catalog，注册职责归 WI22，sync 不受影响） |
| `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-runtime --severity high` | 0 | Critical/High/Medium/Low 全 0 |
| `parseRoadmapMarkdown`（`tools/mission-driver/src/roadmap-check.mjs` 实调） | — | **items 31 + milestones 7，done 21，31 名唯一无静默丢弃，progress 0.68**；WI12 仍 `todo`、M4 `not-done`（预期——翻转在 audit 通过之后） |
| `check-plan-checklist --strict`（备案） | 0 | active plan、Phase 2/Closure Gates 未勾 → warnings only，符合收口前预期态 |

### 1.7 plan 文本一致性（审计项 7）——事实面 PASS；约束面见 B-1

- Phase 1 四项勾选 live 全部落地：PerKeyOrderedBuffer（§1.1）、OverWindowOperator（§1.2）、三测试类（§1.3/§1.4/§2）、当日日志条目（§1.8）。Exit Criteria 四项（隔离 10 用例绿、E2E rn=1/2/3 跨界、runtime 1206、日志更新）逐一实测成立。
- 执行期修正注记属实：L55「TreeMap comparator 须具名 Serializable 类」与 :43-53 一致。
- **缺口**：plan 的 Source 引 roadmap「WI12 行、D6=(a)、Q2」，未纳入 roadmap :66 绑定句；Goals L23 以「内部用 keyed state 语义（…standalone 形态）」收窄了「必须落在 namespace-based keyed state 上」，全程无 Deferred/adjudication 记录——属 plan 对 owner 绑定约束的未声明改写（§3 B-1）。
- 日志（10-02.md WI12 条目 :3-8）逐句与 live 吻合：构件细节、双帧语义、10 用例分布（5/4/1）、1206、restore putAll——无虚报；唯同样未标注状态通道与 roadmap :66 的偏差。

### 1.8 git 纪律（审计项 8）——PASS（一处流程注记）

- `git status --porcelain` 全量实读：tracked 改动仅 `ai-dev/logs/2026/10-02.md`；untracked 全部为本 WI 新增（plan 22 + windowing 包 2 主类 + 3 测试类）。`_gen`、`_*` 前缀生成物、任何生成目录**零改动**（porcelain grep 零命中）。
- 新增五文件 `TODO|FIXME|XXX|System.out.print` 零命中；无探针残留。
- M-4（流程）：WI12 全部产物当前**未提交**（前序 WI 均按里程碑落 commit）；建议 B-1 处置落定后一并提交，避免工作丢失。

### 1.9 D1=(a) 语义合规（审计项 9）——PASS

- 发射即当前帧结果：ROW_NUMBER 模式每 watermark 对当前有序缓冲发射一次、随即消费（元素只发一次且 rn 即终值，无对已发行号的修正流）；滑动聚合每 watermark 发射当前聚合值，下游 last-value-wins 覆盖——两者均无 retract/RowKind/修正标记表述，javadoc :34-36 明文「no retract markers; frame re-open is event-time recomputation per Q2」。
- 与 roadmap D1=(a) re-scope 行（:213「WI12/WI13 保持终值输出（不得标为 append-only 的义务归 WI11 标注面」）及 §4a 清单第 3 行（retract 全链路排除）一致。trimToWatermark javadoc :102-104「final-value semantics, no retract」同口径。

## 2. 实跑证据汇总

- 三个测试类隔离：`-Dtest='TestPerKeyOrderedBuffer,TestAnalysisWindowEventTime,TestE2EOverWindowWithCheckpoint'` → **10/10 绿**（5+4+1），exit 0
- runtime 全量：**1206 绿（0 F / 0 E / 10 skip 既有）**——exit 0，BUILD SUCCESS
- 门禁：doc-links 0（3 既有 warnings）/ invariants sync OK / hollow runtime 全 0 / roadmap 31+7（done 21，WI12 todo 预期态）/ plan-checklist 0（warnings only）
- 全部原始日志存 `_tmp/audit-wi12/`（isolated.log、runtime-full.log、runtime-full.exit）

## 3. 发现

### B-1（Blocker，收口阻断）：有序缓冲未落在 roadmap 绑定要求的 namespace-based keyed state 上

- **绑定约束链**（全部 live 实读）：
  1. `00-vision.md:161`（§十）：「自管 HashMap 做窗口状态 | 不参与平台 keyed state 生命周期管理（checkpoint、恢复、分片路由）。必须使用 namespace-based state」；
  2. roadmap :66（「**另两条同样具约束力**」段）：「§十 明确拒绝『自管 HashMap 做窗口状态』，故 **WI12 的每 key 有序缓冲必须落在 namespace-based keyed state 上（归 WI12 完成判定）**」；
  3. roadmap :260（完成判定）：「keyed state 经 checkpoint 与 restore 的端到端证据落在 TestE2EWindowOperatorWithCheckpoint 同级用例」——先例同级的判定基准是**真实 keyed backend** 的快照恢复（先例 :86/:112-131）；
  4. `join-operator.md:23`（WI13 复用义务）：「双侧缓冲以 **keyed state** 承载（§三 #8 拒绝自管 HashMap；§十 明确窗口状态走 namespace-based keyed state——**join 缓冲同规则**）」，且 WI13 完成判定（roadmap :261）要求「必须复用 WI12 的每 key 有序缓冲构件而非另建」。
- **live 偏差**：`PerKeyOrderedBuffer.java:40` 内部即 `HashMap<K, TreeMap<long[],V>>`（自管）；OverWindowOperator 无 keyed backend（`AbstractStreamOperator.open()` 只建 operator backend :75-79；keyed 快照分支 :308 因 backend 恒 null 不触发）；快照通道为 `putOperatorState` 整体对象（:81，MemoryStateBackend 下按引用）。现有 E2E 证明的是「operator state 可整体携带缓冲」，不是 A5（roadmap :191）点名待证的「keyed state 承载每 key 有序缓冲」用法。
- **定级理由**：这是 owner-doc 绑定约束与 live baseline 的 confirmed 不一致，且直接波及 WI13 的复用义务与 §十点名的分片路由风险；按 plan guide（Closure Audit Rule #9、Non-Degradable Items）不得降级为 follow-up，也不能以 plan 单方措辞（L23「standalone 形态」）替代 owner 改判——plan 无权静默收窄 roadmap「必须」句。
- **处置路径（二选一，均可解阻）**：
  - **路径 A（改接线，推荐）**：保留 PerKeyOrderedBuffer 为独立排序/trim 构件，但其持久化改走 keyed state——OverWindowOperator 按 ProcessOperator :59-62 先例（WI14 custom 用例已验证同款三步）在 `open()` 自建 keyed backend，缓冲条目经 keyed/namespace state 承载（快照恢复由 `AbstractStreamOperator.snapshotState` :308-313 自动接管），`copyForSubtask` 改建新缓冲实例（顺带修复 B-2）；E2E 补断言恢复走 keyed 通道。
  - **路径 B（owner 改判）**：由 owner 显式修订 roadmap :66 与 `join-operator.md:23`/§6，记录「standalone Serializable 构件 + operator-state 整体快照视为满足 §十 意图」的裁定及理由（§十 三项生命周期关切中 checkpoint/恢复已覆盖、分片路由如何豁免须写明）；plan 补 Deferred/adjudication 条目。**在 owner 落判前，本 audit 不接受此路径视为已满足。**

### B-2（Major，与 B-1 同簇修复）：`copyForSubtask` 跨子任务共享同一缓冲实例

`OverWindowOperator.copyForSubtask` :71-74 返回 `new OverWindowOperator(buffer, …)`——传入**同一** buffer 引用。对照同仓契约：`StreamReduceOperator.copyForSubtask` :62-64 新建实例并明文「Per-subtask reduction state is independent」(:57-59)；`OperatorChain` :256 确按子任务逐份拷贝算子。并行度 > 1 时各子任务副本将共享一份缓冲（重复发射/状态互踩），且键入状态不经 key-group 路由——正是 §十 点名的「分片路由」风险的具体形态。WI12 测试未覆盖 copyForSubtask/并行度。与 B-1 一并修复（keyed backend per subtask + 新缓冲实例）。

### Minor（均非阻塞，随处置顺手处理）

- **M-1**：OverWindowOperator javadoc :39-41「ROW_NUMBER mode trims nothing below the watermark unless the frame mode says so」与代码矛盾（emitRowNumbers :140 恰恰 trim 到水位；滑动模式 trim 到条数）——句子讹误，改写为与 :134-168 一致。
- **M-2**：plan L56/日志「snapshotState/restoreState **序列化**缓冲」措辞偏松——E2E 快照在 MemoryStateBackend 下按引用传递（:92-94 代码注释自认）；序列化能力仅由 `TestPerKeyOrderedBuffer.serializableForOperatorDeepCopies` 独立证明。建议措辞改「快照携带缓冲（独立序列化往返已单测）」。
- **M-3**：E2E :73 行内注释「a cold buffer would emit only rn=3」不精确（冷缓冲实际发射 `rn=1 val=k|30|c`）；断言判别力不受影响，顺手更正注释。
- **M-4**：WI12 产物未提交（见 §1.8）；B-1 处置落定后按 AGENTS.md 提交纪律落 commit。

## 4. 无静默跳过检查

新增主代码 2 文件：PerKeyOrderedBuffer（全方法实实现，trim/排序/合并均有行为与测试）；OverWindowOperator（双分支均实求值实发射，无空方法体/no-op/TODO）；测试 3 文件断言均为行为断言（列表相等/计数/快照非空），无恒真式。scan-hollow runtime 全 0。判别性反事实推演（§1.3/§1.4）均给出「若实现缺失则哪条断言以何值失败」。唯一契约级问题是 B-1 的**通道选择**（实现了功能但走了 owner 未放行的形态），非静默跳过。

## 5. 结论

WI12 的功能交付面在 live repo 全部成立：独立可测缓冲构件（六 API + 具名 Serializable comparator + 序列化往返单测）、OVER 双帧语义（乱序重排与滑动历史的断言判别均为真）、watermark 触发、D1=(a)/D6=(a)/Q2 标注如实、无 retract 语义合规、checkpoint/restore 帧计算连续（rn=1/2/3 跨界，冷缓冲必失败）、隔离 10/10 + runtime 1206 零退化、四项门禁全 0、_gen 纪律干净。

**裁定 FAIL（一项 Blocker）**：缓冲状态通道未满足 roadmap :66 绑定句「必须落在 namespace-based keyed state 上（归 WI12 完成判定）」，live 为自管 HashMap + operator-state 整体快照，与 §十（00-vision.md:161）及 WI13 复用义务（join-operator.md:23）冲突，且伴生 copyForSubtask 共享实例缺陷（B-2）。本 audit 构成 plan Phase 2 第一项的执行证据（证据落档两处之 audit 档半边），但**结论为不通过**——后续收口动作（实现者/owner 执行，非本 audit 范围）：① 按 §3 B-1 路径 A 或路径 B 处置（含 B-2、M-1/M-2/M-3 顺手项）；② 处置后复核（若走路径 A：三测试类隔离 + runtime 全量 + 新增 keyed 通道断言；若走路径 B：roadmap/join-operator/plan 三处改判文本一致）；③ 再启独立 closure audit 复核；④ 通过后方翻转 roadmap WI12 `todo` → `done`（括注单层一对）+ `parseRoadmapMarkdown` 31+7 复核、plan Phase 2 勾选与 Status → `completed`、Closure 段落回填、check-plan-checklist/check-doc-links --strict 复核 0。当前证据下上述翻转动作均不得执行。
