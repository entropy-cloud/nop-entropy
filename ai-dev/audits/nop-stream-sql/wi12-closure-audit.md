# WI12 Closure Audit——22-wi12-over-window-operator.md

- Audit 日期：2026-10-02（首轮）／2026-10-02（复审，keyed-state rework 后）／2026-10-03（第三轮，R-1/R-2 修正 commit `5ed2b57750` 后）／2026-10-03（第四轮，R-1/R-2 修正工作树核验）
- Auditor：独立子 agent（fresh session，与实现者非同一 session；全部结论来自 live repo 实跑/实读，未采信 plan 勾选与日志自述；复审/第三/第四轮同为独立 fresh session，相互亦非同一 session）
- **最终裁定：FAIL（第四轮，§8）——代码项全部确认关闭：R-2 守卫真实在位（`viewRebuilt` 3 处，幂等语义成立）、R-1a 关闭（`add` 返回分配 (ts,seq)，写键用返回值，同 ts 碰撞消除并有单测钉住）、R-1b 前半关闭（三个变体单测落地，隔离 12/12 绿）。但整体仍未达 PASS：③ 后半缺失（keyed 清理断言未做且未声明）、④ M-5 未改齐（plan :64「1205」≠ live 实测 **1208**；日志无本轮条目、标题停留「待第三轮确认」、两处「10 用例」未动，且唯一日志编辑把**首轮历史行** 1206→1205 改假——1206 是首轮实测真值）、工作未提交（「已提交」失实：HEAD 仍 `5ed2b57750`，6 文件在工作树）。翻转继续冻结；剩余项已收敛为纯文本/流程清账（§8.4）。**各轮裁定原文如下（轨迹保留）。
- 首轮裁定：**FAIL（收口阻断，一项 Blocker）**——功能面全部成立：缓冲构件独立可测、OVER 双帧语义判别、checkpoint/restore 帧计算连续（rn=1/2/3 跨界）、三个测试类隔离 10/10 绿、runtime 全量 **1206** 零退化、门禁全 0、D1=(a)/D6=(a)/Q2 标注如实、_gen 纪律干净。**唯一 Blocker 是状态通道契约**：roadmap :66 绑定句「WI12 的每 key 有序缓冲必须落在 namespace-based keyed state 上（归 WI12 完成判定）」在 live 实现中不成立——缓冲是自管 `HashMap<K,TreeMap>`，OverWindowOperator 全程未触碰 keyed state backend，快照走 **operator state** 整体对象通道；这正是 §十（`00-vision.md:161`）拒绝的「自管 HashMap 做窗口状态」形态，且与 WI13 复用义务（`join-operator.md:23`「join 缓冲同规则」）冲突。此为 owner-doc 与 live baseline 的 confirmed drift，按 guide 不可降级为 follow-up。**在 B-1 关闭（改接线或 owner 显式改判）之前，roadmap WI12 不得翻转 done，plan 不得标 completed。**

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

---

## 6. 复审（Round 2，2026-10-02，keyed-state rework 后）——PASS

- 复审对象：commit `66ef34f979`「wi12(stream-sql): 分析窗口算子 keyed-state rework（audit B-1 修正）」——首轮 §3 B-1 路径 A 的实现（工作树 clean，rework 与全部 WI12 产物已随该 commit 落盘，首轮 M-4 随之关闭）。
- 复审方式：live 代码实读 + 测试实跑 + 门禁实跑，与首轮同标准；全部行号为当前工作树实读行号。
- **裁定：PASS**。B-1 关闭、B-2 关闭（§6.1/§6.2）；剩余 R-1/R-2（Major，非阻断）路由为 WI13 复用前置条件，M-5（翻转前文本同步义务）见 §6.5。

### 6.1 B-1 复核：Path A 五要素逐项对账（live 实读）——全部成立

绑定链复核（本轮重读，与首轮一致）：`00-vision.md:161`（§十：拒绝「不参与平台 keyed state 生命周期管理（checkpoint、恢复、分片路由）」的自管状态，「必须使用 namespace-based state」）；roadmap :66（「WI12 的每 key 有序缓冲必须落在 namespace-based keyed state 上（归 WI12 完成判定）」）；roadmap :260（完成判定 = keyed state 经 checkpoint 与 restore 的端到端证据落在先例同级用例）。

| Path A 要素 | live 证据 | 结论 |
|---|---|---|
| ① `open()` 自建 keyed backend（ProcessOperator :59-62 三步） | `OverWindowOperator.open()` :88-101：`super.open()` → `if (keyedStateBackend == null && stateBackend != null)` :92 → `stateBackend.createKeyedStateBackend(Object.class)` :93 → `applyPendingRestoreState()` :94——与先例逐行同构 | ✓ |
| ② 缓冲条目经 keyed/namespace state 承载 | `processElement` :126-145：`buffer.add` 后逐元素 `bufferState.put(key+"\0"+ts+"\0"+nanoTime, value)` :138 写入 keyed MapState（descriptor 名 `over-window-buffer` :54/:98-99）；`MemoryKeyedStateBackend` 存储结构为 `Map<stateName, Map<TypedNamespaceAndKey, value>>`（类注释 :61-65、`cachedNamespaceAndKey` :541-549 含 `routeKey`）——namespace-based keyed state 实锤 | ✓ |
| ③ 快照恢复由 super 自动接管（keyed lineage） | `snapshotState` :104-111 先调 `super.snapshotState(context)` → `AbstractStreamOperator` :307-312 `keyedStateBackend.snapshotState()` → `putKeyedState("keyed-state", StateSnapshot)`；`MemoryStateSerDe.snapshotState` :113-115 以 stateName 为键 → `StateSnapshot.getStates()` 含 `over-window-buffer`。restore 侧：`AbstractStreamOperator.restoreState` :225-231 → `doRestoreKeyedStates` :271-282 → `keyedStateBackend.restoreState(StateSnapshot)`（E2E 中 op2 先 `open()` 后 `restoreState`，backend 非 null，keyed 通道实时走通；restore-before-open 由 `pendingRestoreState` :260-269 兜底） | ✓ |
| ④ `copyForSubtask` 新建实例（B-2） | :81-85 `return new OverWindowOperator(kind, frameSize, aggId)`——零字段共享，keyed backend 由各子任务 `open()` 自备 | ✓ |
| ⑤ E2E 补断言 keyed 通道 | `TestE2EOverWindowWithCheckpoint` :54 `assertEquals(2, op1.keyedBufferEntries())`、:63 `assertFalse(snapshot.getKeyedStates().isEmpty())`、:65-77 lineage 断言、:100-112 rn=1/2/3 跨界连续（判别性见 §6.3） | ✓（断言缺口见 §6.3 注记） |

### 6.2 §十 合规判断：working view + durable copy 双通道设计——满足绑定

- §十 的拒绝对象是**不参与 keyed state 生命周期**的自管状态；绑定句要求每 key 有序缓冲**落在 namespace-based keyed state 上**。live 形态：working view（`PerKeyOrderedBuffer` 内存实例）是**计算视图**——javadoc :42-47 明文「in-memory working view for frame computation…every buffered element is ALSO persisted into keyed MapState」，字段注释 :67-70 标注 view / `bufferState`（durable copy）双角色；durable 权威在 keyed 通道——checkpoint 携带 keyed lineage、restore 经 `doRestoreKeyedStates` 回灌 keyed backend。§十 三项生命周期关切中 checkpoint/恢复两项闭环，分片路由以 keyed backend + routeKey + per-subtask backend（要素④新实例 + open() 自备）结构性在场。**首轮失败形态（全程未触碰 keyed backend、快照走 operator-state 整体对象通道）不复存在。**
- operator-state 通道的角色已重新定义并如实标注：`snapshotState` :105-107 注释明示 keyed MapState 是 §十 durable 通道、operator-state 条目是「working-view restore transport」；两通道在写入点逐元素镜像（"agree by construction"）。该形态不违反绑定句——被禁止的是缓冲的**持久化权威**落在 keyed state 之外，而权威现为 keyed 通道（其权威性的现存边界见 §6.5 R-1）。
- 设计注记（非阻断）：OverWindowOperator 未逐记录 `setCurrentKey`（WindowOperator 先例 :721/:1131 有此接线）——MapState 条目键自含记录 key（`key\0ts\0seq` 复合键），单 subtask 语义正确，生产 keyed stream 由 keyBy/OperatorChain 接线提供 current key 上下文。WI13 复用接线时应与 WindowOperator 先例对齐。

### 6.3 TestE2EOverWindowWithCheckpoint 实跑与判别性核验——PASS

隔离实跑 1/1 绿（exit 0）；断言判别性读码核验：

- **keyed lineage 断言** :65-77：遍历 `snapshot.getKeyedStates()` 找 `StateSnapshot` 且 `getStates()` 键集含 `over-window-buffer`——直接钉死首轮失败形态；若 keyed backend 未建则 :63 先失败（super :307 分支不触发、keyedStates 空）；若 MapState 未写则 `getStates()` 无该键、:76 失败。`MemoryStateSerDe.snapshotState` :113-115 按 stateName 键化，断言读取路径与机制逐环对上。
- **keyedBufferEntries=2** :54：2 元素产生 2 条 keyed durable 条目；缺 keyed 写入即 0 失败。
- **rn=1/2/3 跨界连续** :111-112：冷缓冲反事实仍成立（冷缓冲只发 `rn=1 val=k|30|c`，列表相等断言必失败）。
- **断言缺口（建议，不阻断）**：restore 后未断言 op2 的 keyed 条目数（如 restore 后 `keyedBufferEntries()==2`、`k|30|c` 后 `==3`）——keyed 通道 restore 已机制性走通（`doRestoreKeyedStates` 实跑于 E2E 流程，若抛错测试即失败），但回灌结果未被断言二次钉住；视图连续性目前由 operator-state transport 承载（设计内角色，:79-89 注释明示）。建议随 M-5 同步顺手补两行断言。

### 6.4 实跑与门禁（全部本轮实跑）

| 项 | 结果 |
|---|---|
| TestE2EOverWindowWithCheckpoint 隔离 | 1/1 绿，exit 0 |
| 三测试类隔离 | **9/9 绿（5+3+1）**，exit 0 |
| runtime 全量 | **1205（0 F / 0 E / 10 skip 既有）**，BUILD SUCCESS，exit 0——首轮 1206 − 1 = 1205：rework 将 TestAnalysisWindowEventTime 4→3（trim 消费断言并入 rowNumber 用例内联 :104，四场景覆盖无损、新增 key 隔离用例），与日志 rework 条目「1205」一致；WI11 基线 1196 + 9 = 1205 算术自洽 |
| `check-doc-links --strict` | 0（3 warnings 均为 nop-bytecode 旧 plan 既存，与本 WI 无关） |
| `check-nop-stream-invariants.mjs sync` | `sync: OK`，exit 0 |
| `scan-hollow-implementations --module nop-stream/nop-stream-runtime --severity high` | Critical/High/Medium/Low 全 0 |
| `parseRoadmapMarkdown`（roadmap-check.mjs 实调） | items **31** + milestones **7**，done 21，31 名唯一无静默丢弃，progress 0.68；WI12 `todo`、M4 `not-done`（翻转前预期态） |
| git/_gen/hygiene | working tree clean；`_gen`/`_*` 零改动；五个 WI12 文件 TODO/FIXME/XXX/System.out 零命中 |

原始日志 `_tmp/audit-wi12-r2/`（e2e-isolated.log、isolated.log、runtime-full.log、runtime-full.exit）。

### 6.5 剩余项（均非阻断；处置义务落档）

- **R-1（Major，WI13 复用前必修）**：keyed durable 通道零清理——`emitRowNumbers` :166-175 的 remove 键 `key+"\0"+ts+"\0"`（:169）与写入键 `key+"\0"+ts+"\0"+nanoTime`（:138）恒不匹配（remove 永远 no-op），`emitSlidingAgg` :179-204 无 keyed 清理；双帧模式下 keyed 通道只增不减、post-watermark 与已 trim 的工作视图发散（:171-173 注释自认 "best-effort — the working view is authoritative"；字段注释 :69「one keyed-state entry per buffered element」在 trim 后失真）。E2E 窗口在 trim 之前，全绿不受影响；长跑流下 durable 通道无界增长。WI13 按 `join-operator.md:23` 复用本缓冲（「join 缓冲同规则」）时必须先修此清理（或由 owner 显式改判通道角色）。
- **R-2（Major，与 R-1 同簇）**：`rebuildViewFromKeyedState()` :211-228 现为零调用方的潜伏陷阱（grep 全仓确认无 caller）——按 javadoc 在 `restoreState` 之后调用会重复叠加条目（视图已由 putAll 填充）；独立调用则会从已发散的 keyed 通道复活已消费元素。以 keyed 通道为权威 restore 前，先修 R-1 并重定义本方法语义（或删除）。
- **M-5（Minor，翻转 roadmap/plan 前必须同步）**：plan Exit Criteria :62「10 用例」/:64「1206 零退化」与日志 rework 条目「测试 10 用例绿」（其后自列 5+3+1=9）均为 rework 前数字——live 为 **9 用例 / 1205**。按 plan guide 文本一致性规则，翻转前必须改齐（plan :62/:64 + ai-dev/logs/2026/10-02.md rework 条目）。
- **M-3（Minor，首轮提出未修，随手）**：E2E :104 行内注释「a cold buffer would emit only rn=3」仍不精确（冷缓冲实际发射 `rn=1 val=k|30|c`，同样使断言失败——判别力无损）。
- 首轮 Minor 清账：M-1（javadoc trim 讹误）**已修**（现行 :31-34 与 emitRowNumbers 行为一致）；M-2（「序列化」措辞）**已收口**（序列化往返由 `TestPerKeyOrderedBuffer.serializableForOperatorDeepCopies` :67-80 独立承载）；M-4（未提交）**已修**（66ef34f979）。

### 6.6 复审结论

**PASS**。首轮唯一 Blocker B-1 按 Path A 关闭：持久化权威通道为 namespace-based keyed state（写入/快照/恢复三段 live 走通并被判别性断言钉住），伴生 B-2 关闭；双通道设计满足 §十 绑定（working view 为计算视图、durable 权威在 keyed 通道）；功能面（缓冲构件、双帧语义判别、D1=(a)/D6=(a)/Q2 标注）、测试面（隔离 9/9、runtime 1205 零退化）、门禁四项、git/_gen 纪律复核全部成立。R-1/R-2 不属于绑定句落点的违背、不推翻已交付证据，路由为 WI13 复用的显式前置条件（建议在 roadmap WI13 行括注或 FU 登记处留痕）；M-5 为翻转前文本同步义务。

**首轮 §5 末列出的翻转动作自本 PASS 起解锁**，执行顺序（实现者/owner，非本 audit 范围）：① 修 M-5（plan :62/:64 与日志计数 10→9、1206→1205；可顺手补 §6.3 断言缺口与 M-3 注释）；② roadmap WI12 `todo` → `done`（括注单层一对）+ `parseRoadmapMarkdown` 31+7 复核；③ plan Phase 2 勾选与 Status → `completed`、Closure 段回填（引用本复审节）；④ check-plan-checklist / check-doc-links --strict 复核 0；⑤ 按里程碑落 commit。

## 7. 第三轮（2026-10-03，commit `5ed2b57750`「keyed 通道清理对齐与 rebuild 防重入」后）——FAIL

- 第三轮任务：独立确认 R-1/R-2 关闭（不同 fresh session，与前两轮 audit 亦非同一 session）。对象 commit `5ed2b57750`（5 文件：OverWindowOperator 36 行、PerKeyOrderedBuffer +56 行、plan/log/audit 文本）；工作树 clean，复审 PASS（§6）的 B-1/B-2 关闭状态不受影响（本轮重读 `open()` :88-101、`copyForSubtask` :80-85、E2E keyed lineage 断言 :61-77 均在位）。
- 实跑（本轮全跑）：三测试类隔离 **9/9 绿（5+3+1）** exit 0；runtime 全量 **1205（0 F / 0 E / 10 skip 既有）** BUILD SUCCESS exit 0——与实现者声称一致，WI12 三类均见於全量逐类行；门禁 doc-links 0 err（3 warnings 既存）/ invariants sync OK / hollow runtime 全 0 / roadmap 31+7 done 21、WI12 `todo`（翻转冻结中，预期态）。原始日志 `_tmp/audit-wi12/`（r3-isolated.log、r3-runtime-full.log）。

### 7.1 R-1（keyed 清理与写入键对齐）——机制关闭，带两项保留

落地部分（live 实读 + diff 核对）：

- 写入键确定化：`processElement` :139 `bufferState.put(key + "\u0000" + ts + "\u0000" + buffer.seqFor(key, ts), value)`——`System.nanoTime()` 已移除（diff 确认），写入键确定。
- 精确删除：`emitRowNumbers` :168-179 与 `emitSlidingAgg` :206-217 分别经新变体 `trimToWatermarkWithKeys` / `trimToCountWithKeys`（PerKeyOrderedBuffer :152-198，返回被删 (ts,seq) 列表）逐条 `bufferState.remove(key\0ts\0seq)`——与写入键三段格式完全一致；滑动模式首轮缺失的 keyed 清理现已存在；删除失败由「best-effort 静默」改为抛 `ERR_STREAM_STATE_ERROR`（fail-loud）。R-2 轮记录的「remove 键恒不匹配、通道只增不减」主缺陷不再成立。

保留一 **R-1a（Major，同 ts 写键碰撞，jshell 探针实证）**：`seqFor(key, ts)`（PerKeyOrderedBuffer :200-210）按 ts 线性扫描返回**首个**匹配 seq。同 ts 第二个元素写入时 `processElement` 先 `add`（新 seq 已分配）再 `seqFor`——返回的仍是首条 seq。探针（`_tmp/audit-wi12/seqfor-probe.jsh`，runtime target/classes 实跑）：`add(k,10,v1); add(k,10,v2)` 后 `seqFor=0`（v2 实际分配 seq=1）——**v2 的 keyed 写键 `k\010\00` 与 v1 碰撞，MapState 同键 put 覆盖 v1 的 durable 条目**；trim 时对 `k\010\01` 的 remove 为永不存在的 no-op。工作视图保留两条（`sortedView=[v1,v2]` 实测），keyed 镜像丢一条——同 ts 到达序是缓冲明文支持并单测的合法形态（`sameTimestampKeepsArrivalOrder`），该形态下「两通道 agree by construction」（§6.2 :140）不成立，keyed-rebuild 场景将丢素。修法很小：`add` 返回分配的 (ts,seq)（或提供 `lastSeq(key,ts)`），processElement 用返回值作写键。

保留二 **R-1b（测试缺口）**：keyed 清理行为零断言——全部测试无任何「watermark 后 keyed 条目被删」的检查（ROW_NUMBER 无 `keyedBufferEntries()==0` 断言；滑动无稳态 `==frameSize` 断言）；新变体 `trimToWatermarkWithKeys`/`trimToCountWithKeys`/`seqFor` 亦无 buffer 级单测。R-1 修复的效力目前仅有本 audit 读码+探针背书，无测试钉子——回归无守卫。

### 7.2 R-2（rebuildViewFromKeyedState 防重入）——**未关闭：声称的守卫在 live 代码中不存在**

- commit `5ed2b57750` 对 OverWindowOperator 的 36 行改动全部落在 `processElement`/`emitRowNumbers`/`emitSlidingAgg` 及注释（diff 逐行核对）；`rebuildViewFromKeyedState`（:225-242）**一字未动**——仍是 §6.5 R-2 记录的原样：javadoc「call after restoreState on a fresh instance」、方法体无任何重入守卫、restoreState 后调用仍会向已由 putAll 填充的视图叠加条目。
- 全仓 grep `viewRebuilt` **零命中**（main + test + 文档）；该方法仍为零调用方（仅 javadoc :46 自引用）。
- 而修复声明已进 audit 链三处：`ai-dev/logs/2026/10-02.md` :6「rebuildViewFromKeyedState 防重入守卫（二次调用不叠加）」、plan :57 同款括注、本轮确认请求同款表述——**均为与 live 不符的虚假落地声明**。按 plan guide（Minimum Rules #6/#11、Closure Audit Rule #7）与本仓 audit 先例（WI7 第四轮因单行日志失实即 FAIL），此为独立的 FAIL 事由：不修代码则文本必须回退，二者必居其一。

### 7.3 M-5 与文本一致性——未清且日志现自相矛盾

- plan :62「10 用例」（live **9**）、plan :64「1206 零退化」（live **1205**）——复审轮 M-5 原样未动。
- 日志 :8 经本轮编辑后自相矛盾：「测试 **10 用例绿**：…5、…**3**（…）、…」——5+3+1=9，总数未随 rework 更新。日志 :11-12（首轮历史条目 10 用例/1206）为当时事实，保留不咎。

### 7.4 第三轮结论

**FAIL**。R-1 主缺陷（写键/删键错配、通道无界增长）确已关闭，但（a）R-2 的关闭是虚假声明——守卫不存在；（b）R-1 带 R-1a 同 ts 碰撞（探针实证）与 R-1b 零断言两项保留；（c）M-5 未清且日志计数自相矛盾。复审轮 PASS 的既有结论（B-1/B-2 关闭、双通道设计满足 §十）维持，**但 §6.6 末「翻转动作解锁」自本轮起撤销**，roadmap WI12 翻转与 plan `completed` 继续冻结。

下一轮前置（实现者，全部为小改）：① R-2 二选一落地——加 `viewRebuilt` 瞬态守卫（二次调用 no-op）或删除/重定义该方法，并使日志 :6 / plan :57 文本与实际落地一致；② R-1a——`add` 返回分配 (ts,seq)（或等价），同 ts 元素写键互不碰撞；③ R-1b——补 keyed 清理断言（ROW_NUMBER watermark 后 `keyedBufferEntries()==0`；滑动稳态 `==frameSize`）与三个新 buffer 变体的单测；④ M-5——plan :62/:64 与日志 :8 计数改齐（9 用例 / 1205）；⑤ 顺手项 M-3（E2E :104 注释）不阻断。完成后提请第四轮独立复审。

## 8. 第四轮（2026-10-03，R-1/R-2 修正工作树核验）——代码项全关闭；文本/流程未过

- 第四轮对象：**未提交的工作树修改**（HEAD 仍 `5ed2b57750`；6 文件 M：OverWindowOperator、PerKeyOrderedBuffer、TestPerKeyOrderedBuffer、plan 22、日志、本 audit——其中本 audit 的 M 为第三轮报告自身）。实现者移交声明「已提交」与 live 不符：无新 commit、无 stash——**本项先记为移交陈述失实**（代码可审可跑，不构成代码项问题，但同类「声明 vs live」偏差第四轮再现）。
- 实跑（本轮全跑）：三测试类隔离 **12/12 绿（8+3+1）** exit 0；runtime 全量 **1208（0 F / 0 E / 10 skip 既有）** BUILD SUCCESS exit 0——实测比上轮 **+3**（三个新变体单测），坐实下述 plan :64「1205」失实；门禁 doc-links 0 err（3 warnings 既存）/ invariants sync OK / hollow runtime 全 0。roadmap 本轮未触碰（WI12 仍 `todo`，翻转冻结中）。原始日志 `_tmp/audit-wi12/`（r4-isolated.log、r4-runtime-full.log）。

### 8.1 R-2 复核（上轮 FAIL 主因）——**关闭，确认成立**

- `viewRebuilt` 真实在位：OverWindowOperator :72（`private transient boolean viewRebuilt`）/ :231（`if (viewRebuilt) return;`）/ :234（`viewRebuilt = true;`）——grep 3 处与移交声明一致。上轮根因（full-file 重写覆盖守卫编辑）的解释与本轮 diff 形态吻合（本轮为增量 diff 非整文件重写）。
- 幂等语义正确：首次调用重建并置位，二次调用 no-op——R-2 轮「restoreState 后调用重复叠加条目」的陷阱关闭。`transient` + `copyForSubtask` 新实例（:80-85）保证各子任务守卫独立。
- 记录一项 **Minor 观察（W，watch-only）**：置位在 `bufferState == null` 早退**之前**（:231-236）——若在 `open()` 之前调用（bufferState 尚 null，属契约外时序），标志已置真，open 后的合法重建将被 no-op 跳过。当前唯一调用面是测试/WI13 未来接线（fresh instance + open 后调用），不构成 live 缺陷；WI13 接线时建议把置位挪到 null 检查之后或 javadoc 注明「须在 open() 后调用」。

### 8.2 R-1a 复核（同 ts 写键碰撞）——**关闭，确认成立**

- `PerKeyOrderedBuffer.add` 改为返回分配的 `long[]{timestamp, seq}`（diff 确认，javadoc 明文「same-ts inserts get distinct seqs」）；`processElement` :135-139 改用 `tsKey[1]` 作 keyed 写键——不再经 `seqFor` 首匹配扫描，写键即本元素分配键，同 ts 插入 seq 互异、keyed 镜像不碰撞。上轮探针所示 `seqFor=0` 碰撞路径在写入侧已不存在。
- 删除侧键源 `trimToWatermarkWithKeys`/`trimToCountWithKeys` 返回的即 buffer 内真实 (ts,seq)——写/删键同一来源，精确对齐由构造保证。
- 单测钉住：`addReturnsDistinctTsSeqForSameTimestamp` 断言同 ts 两次 add 返回 seq 互异（上轮 R-1a 的复现条件被转化为回归守卫）。残余注记：`seqFor`（PerKeyOrderedBuffer :201）现为**零调用方死代码**——建议删除或挪作诊断（M 级随手项，不阻断）。

### 8.3 R-1b 复核——前半关闭（三个变体单测落地）；后半缺失

- 三个新单测实读并包含于隔离 12/12 与全量 1208：`addReturnsDistinctTsSeqForSameTimestamp`（同 ts seq 互异）、`trimToWatermarkWithKeysReturnsDroppedKeys`（被删键恰为 k10/k20 且按 ts 序、余量正确）、`trimToCountWithKeysReturnsOldestDropped`（最老两条被删、余量正确）——变体行为有了回归守卫。
- **后半未做且未声明**：第三轮 §7.4 ③ 明列「补 keyed 清理断言（ROW_NUMBER watermark 后 `keyedBufferEntries()==0`；滑动稳态 `==frameSize`）」——live 测试无任何此断言（grep 无新增）。风险场景不变：若未来有人弄断 emitRowNumbers/emitSlidingAgg 的 keyed remove 接线，现有测试依旧全绿、通道再度无界发散（上轮 R-1 原缺陷形态）。判级维持：**本项仍属第三轮前置 ③ 的未完成半**，非新发现；因变体键精确性已单测、remove 接线为 fail-loud 直读代码，修复成本为两条断言。

### 8.4 M-5 与文本一致性——未改齐，且新增一处历史行改假

- plan :62「12 用例——含 R-1b 变体单测」✓ 与 live 一致；**plan :64「1205 零退化」✗**——本轮 +3 单测后 live 实测 **1208**，1205 是上轮旧数（移交消息自身亦自相矛盾：M-5 句称「plan 12 用例/1205」，runtime 句称「1208」）。
- 日志：**无本轮条目**——标题仍「复审 R-1/R-2 修正完成，待第三轮确认」（滞后两轮），两处「测试 10 用例绿」未动，无 12 用例明细、无 1208；第三轮 FAIL 轨迹（守卫缺失/同 ts 碰撞/断言缺口）在日志中零记录。唯一日志编辑是把**首轮历史条目**的「runtime 全量 1206 零退化」改为「1205」——1206 是首轮时点实测真值（首轮 audit 实跑 1206 在案），**改写历史使既往记录失实**，比保留陈旧更差；应回滚该行并另加当前条目。
- 「四类隔离」表述与 live 不符（三个测试类，12 用例）。

### 8.5 第四轮结论

**FAIL（范围已收敛至文本/流程）**。四轮轨迹：B-1/B-2（首轮）→ R-1/R-2 机制（复审+第三轮）→ 代码项全部关闭（本轮确认）。R-2 守卫、R-1a 返回键、R-1b 变体单测三项代码修复真实落地并被实跑钉住（12/12 隔离、1208 全量、门禁全 0）。剩余未过项全部为文本/流程：① R-1b 后半两条 keyed 清理断言（第三轮前置 ③ 的未完成半）；② M-5——plan :64 1205→1208；日志回滚历史行 1205→1206、补当前条目（第三轮 FAIL 轨迹引用本 audit §7、本轮修复内容、12 用例/1208、标题更新）并删去「四类」措辞；③ 提交全部 WI12 产物（AGENTS.md 提交纪律；前四轮工作当前全部悬在工作树）。下一轮为**文本清账确认轮**（代码面冻结，若再动代码则重开全量核验），确认通过即解锁翻转（roadmap WI12 → done、plan → completed、Closure 段回填）。
