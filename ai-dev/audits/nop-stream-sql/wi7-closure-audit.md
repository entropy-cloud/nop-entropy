# WI7 Closure Audit——20-wi7-multi-input-regressions.md

- Audit 日期：2026-10-02
- Auditor：独立子 agent（fresh session，与实现者非同一 session；全部结论来自 live repo 实跑/实读，未采信 plan 勾选与日志自述）
- 裁定：**FAIL（首轮）**——四类齐备、端到端形态、水位 min 合并判别、self-join 约束 2 判别、manifest 存在断言、隔离与全量实跑（1196 零退化）、门禁全部成立；但 **B-1（barrier 对齐判别断言空洞——aligned/unaligned 双通，plan 勾选与 Goals 文本超出 live 事实）** 与 **B-2（§八 6 双证据绑定未落地——restore 断言为恒真析取，offset/epoch 绑定零断言）** 两项阻塞。roadmap WI7 与 M2 **不得翻转**，补救后复审。
- 复审（第二轮，2026-10-02）：**FAIL**——B-1 的 roadmap 侧 FU-9 路由诚实且 live defect 经独立探针复现成立，但 plan 20 successor 记录缺失、plan/log 未回写；**B-2 未修且未路由**（恒真析取与三项零断言原样，javadoc 反新增失实声明）。详见 §6。roadmap WI7 与 M2 仍不得翻转。
- 复审（第三轮，2026-10-02）：**FAIL（差距收窄至文本一致性最后一公里）**——B-2 机制级修复成立（恒真析取已除、manifest 绑定/RESTORE_PROBE/零重发三断言落地、javadoc 如实，隔离实跑验证）；B-1 路由在 roadmap/plan Deferred 节/代码三处成立、javadoc 如实、悬挂引用消解；但 §6.5 前置 ②尾项与 ④ 未执行（plan Goals/勾选/修订记录未回写、日志无裁定与 FU-9 记录），且 Deferred 节重复插入两份。详见 §7。roadmap WI7 与 M2 仍不得翻转。
- 复审（第四轮，2026-10-02）：**FAIL（唯一阻塞项 = 日志一处误标标题，一行修复）**——清账五项中 ①②④⑤ 成立（plan 回写与 live 逐点对齐、Deferred 重复节已删、改名与死代码清理落地、隔离 6/6 + 全量 1196 + 三门禁全绿）；但本轮日志编辑将旧 WI7 节标题误改为「### WI16 收口」，旧 WI7 内容错位归因于已收口的 WI16，构成日志（guide 规则 5 元素）新增文本缺陷。详见 §8。roadmap WI7 与 M2 仍不得翻转。
- 复审（第五轮，2026-10-02）：**PASS**——§8.2 剩余两处文本逐一核验成立（日志 :10 旧 WI7 节标题恢复且内容措辞同步对齐 live；plan :10 收口裁定注记落地、Deferred 仍计 1）；doc-links strict 0（复跑实测）；本轮零代码变更（mtime 佐证），第四轮隔离 6/6 + 全量 1196 结果继续有效。四轮累计全部 Blocker 与清账项关闭。详见 §9。**WI7 具备翻转条件**——由实现者按 §9.3 翻转序列执行 roadmap WI7 → done + M2 翻转 + plan Phase 2 回填。

## 1. 逐条审计核验（对应审计指令 1-8）

### 1.1 四类齐备与隔离实跑（审计项 1）——PASS

四个具名类全部实存 `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/execution/`：`TestMultiInputBarrierAlignment`（2 用例）、`TestMultiInputWatermarkMinMerge`（1）、`TestMultiInputExactlyOnceCheckpoint`（2）、`TestMultiInputSelfJoinEdgeDedup`（1）+ 辅助类 `MultiInputTestSupport`。隔离实跑：

```
./mvnw test -pl nop-stream/nop-stream-runtime -Dtest='TestMultiInputBarrierAlignment,TestMultiInputWatermarkMinMerge,TestMultiInputExactlyOnceCheckpoint,TestMultiInputSelfJoinEdgeDedup'
→ Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS（exit 0）
```

### 1.2 判别性推演（审计项 2）——(b)(c) PASS，(a) **FAIL（B-1）**

**(a) TestMultiInputBarrierAlignment 用例 1（对齐判别）——断言空洞，阻塞。**

plan Goals #1 与审查修订记录 M3 明文承诺的判别断言是：「fast 源在 barrier(e) 后发出的元素严格出现在观测序列的 barrier(e) 之后（aligned gate 阻塞 fast channel——若泄漏则断言失败）」。live 代码核对：

- `Observation`（MultiInputTestSupport.java:27-36）把 events（纯元素）、barriers（纯 id）、watermarks **分列三张表，无合并交错序列**——barrier 位置信息在观测面即丢失。
- `TestMultiInputBarrierAlignment.java:148`：`int firstBarrierIdx = events.size();` **计算后从未使用**，行内注释自认「barrier positions are tracked separately; use element ordering invariant instead」——屏障锚定断言被放弃的直接证据。
- 实际断言面（:141-159）仅为：barriers ≥ 1、sCount==3、fCount==40（无丢失无重复）、每通道内有序（f-1<f-2、s-1<s-2）。
- 反事实推演：若 gate 泄漏为 AT_LEAST_ONCE（`InputGate.barrierAlignment=false`，不阻塞 fast channel），两源元素仍全部恰好交付一次、通道内序不变——**上述全部断言原样通过**。aligned/unaligned 双通，判别性为零。
- 非不可实现：`TaskExecutor` 为固定线程池（TaskExecutor.java:116），两源真并发交错；`TestProcessingGuaranteeBehavior.java:78` 已在 InputGate 单元级判别「Aligned gate must block records after barrier until alignment completes」——WI7 的义务恰是把该属性以两源端到端形态守住，而用例 1 没有守住。观测算子只需像同类 `SequencedObserver`（:260-310，第二用例已在用）那样把 barrier 标记混入单一序列即可承载位置断言。

用例 1 的剩余价值（如实记录）：在 STRICT_EXACTLY_ONCE 默认模式下证明 union 路径两源无丢失/无重复/通道内有序 + barrier 可达下游——真实但弱于 plan 声明；对齐维度的回归守卫实际由 core 单元测试承担，多输入端到端守卫缺位。

**(b) TestMultiInputWatermarkMinMerge——判别性成立。**

反事实：若合并为 max，快侧 900/950 会抬升合并值 → `mergedData.get(0)==100`（:146，min(100,900)=100）失败，且 `noneMatch(m>=900)`（:153-154）失败——双断言钉死 min。慢侧推进跟随由 get(1)==200||300（:148-149）与单调性（:150-152）承载。EOS 陷阱防护真实：快源 `CountDownLatch(0)` 立即 finish 后其通道 MAX_WATERMARK 由 :144 过滤；慢源 latch 驻留防全通道 MAX 提前拉满。局限（非阻塞观察）：「丢弃快侧水位」的另一种缺陷形态同样能通过本组断言（merged=[100,200,300] 不变）——plan 反事实清单只声明了 min-vs-max，故不构成缺口，仅记录。

**(c) TestMultiInputSelfJoinEdgeDedup——判别性成立。**

约束 2 缺陷回退（平行边塌成一条）时 sink 收 3 元素 → `assertEquals(6, sink.size())`（:38）与逐元素恰两份（:39-43）失败——6→3 可区分。独立类承载，符合 roadmap「WI7 强制含 self-join 拓扑用例」（R6 行）。

### 1.3 §八 4 断言评估（审计项 3）——部分成立，需注明局限（M-1）

`barrierOnlyInjectsOnSourceReadThreadDuringCollect`（:163-257）实做核验：

- **真实部分（时序）**：`b > p1`（首个 barrier 在探针首元素之后，:255）与 anomaly 旗标（barrier 不得先于 e:p-1，:294-296）是真实断言。注意 `sameThread` 变量实为 SequencedObserver 的 `anomaly` 参数别名（:225 → :265），**会被写入**（barrier 先于首元素时 set(true)），故 :256 非恒真——但它验证的是时序，与线程同一性无关，变量名 `sameThread` 有误导性。
- **缺失部分（线程同一性）**：plan 声明「source 记录 collect 线程，断言 snapshotState 回调线程与之相同」——`probingSource.readThread` 已记录（:181,185）但**无人比较**；`Observation.threads` 字段与 `MultiInputTestSupport.SinkCollector` 均为零引用死代码（线程捕获断言被放弃的残留）。「pause 窗口内 snapshotState 未回调」亦被放弃（:219-221 注释自认「assert via barrier timing instead」）——该断言在 mailbox 模型下本可实现（pause 期无 collect → 无 mail drain → 无 snapshotState）。「pause 窗口内下游无 barrier」在两源拓扑下不可实现（filler 源持续产 barrier），plan 文本该句本身失当。
- **时序断言对线程模型不敏感**：独立注入线程若存在，首 barrier 出现在 50ms 触发点，仍晚于 ~0ms 发出的 p-1——该断言在线程无关注入下同样通过。故本用例只构成「注入尊重 collect 边界次序」的行为证据，**不构成「只由 source 读取线程注入」的线程同一性证据**。
- **补偿性既有证据（live 实读）**：core `TestSourcePullBarrierInjection.java:168-169` 明文断言 `assertEquals("source-task-thread", barrierThreadName.get(), "snapshotState/emitBarrier must run on the source task thread (mailbox drain), not the injector thread")`，及 finished-source 例外断言（:427）。结构链 live 核实：`barrier-injector-<jobId>` 调度线程（TaskCheckpointWiring.java:271-284）→ `CheckpointBarrierTracker.triggerCheckpoint`（:111-151）→ `StreamSourceOperator.offerBarrier` 投递 control mail（:150-185）→ `SourceContext.collect()` 内 `drainControlMails()` 在任务线程执行 injectBarrier（:222-223,291-300）。
- **裁定**：可接受为「collect 边界时序」的端到端行为证据，但 §八 4 的线程同一性维度在 WI7 用例内未被验证（由 core wire 级既有用例补位）——必须在 audit 记录与 roadmap 括注中注明该局限；`sameThread` 命名应更正或落实真实线程断言（M-1）。

### 1.4 §八 6 断言评估（审计项 4）——双证据绑定未落地，**FAIL（B-2）**

plan Goals #3 与 M2 修订声明的绑定：「manifest 记录的 source offset == snapshotState 写入值；第二跑 restore 后 initializeState 收到的 offset == manifest 值、输出无重复、epoch 严格递增」。live 核对：

- **证据层 1（manifest 存在）**：`twoSourceUnionIsExactlyOnceAndRestoresFromDurableState` :113-116 `loadLatestEpochManifest` 非 null 断言——真实。但 **manifest 内记录的 operator state 值从未读出比对**（`"wi7-offset:a"==6`、`"wi7-offset:b"==6` 零断言）——「manifest offset == snapshotState 写入值」未落地。
- **证据层 2（restore 绑定）**：`secondRunRestoresFromDurableOffsetsWithoutDuplication` 唯一断言（:143-144）：
  ```java
  assertTrue(sink.isEmpty() || countOf(sink, 7) == 0, "restored run must not re-emit the consumed range");
  ```
  `CountingSource` count=6，值 7 **不可能被发出**，`countOf(sink,7)==0` 恒真 → 整个析取恒真——**该断言在任何行为下（包括 restore 完全失败、全量重发 1..6 共 12 元素）都通过**，判别性为零。`initializeState` 收到的 offset、输出无重复、epoch 严格递增均无断言（"epoch" 仅出现于 :114 的 manifest 加载）。
- 机制侧事实（live 代码核实）：`execute()` 对显式 storage path **确实自动 restore**（GraphModelCheckpointExecutor.java:242-243 → restoreFromCheckpoint :945-985），第二跑真实走了 restore 路径——机制被**执行**了，但未被**验证**。单输入的 epoch-id 推进已有 `TestE2EManifestRestoreIdAdvance` 覆盖，与两源 offset 绑定不同维度，不能替代。
- **裁定**：plan 声称的双证据绑定三要素（manifest↔snapshot 值等式、initializeState offset 等式、epoch 严格递增）落地数为 0，且交付的 restore 断言为恒真式——与本仓审计先例（WI14 M-2 恒真探针须删、WI10 M-D 非判别断言判 FAIL）同性质，判阻塞。

### 1.5 端到端形态（审计项 5）——PASS

四类全部 `env.addSource → union → transform/sink → env.execute` 完整路径（grep 实证：TestMultiInputBarrierAlignment.java:131-138/222-240、TestMultiInputWatermarkMinMerge.java:122-140、TestMultiInputExactlyOnceCheckpoint.java:98-102/136-139/155-158、TestMultiInputSelfJoinEdgeDedup.java:32-35）。四文件 grep `InputGate|processWatermark1|processWatermark2` 零命中（唯一 `processBarrier` 命中为观测算子覆写回调，属管线内节点非 wire 级直调）。与既有 wire 级资产（TestWatermarkMultiInputCombineWire 直调 processWatermark1/2）形态分界清晰。

### 1.6 实跑汇总（审计项 6）——PASS

| 命令 | 实测结果 |
|---|---|
| 四类隔离（-Dtest 四类） | **Tests run: 6, Failures: 0, Errors: 0** — BUILD SUCCESS |
| `./mvnw test -pl nop-stream/nop-stream-runtime` | **Tests run: 1196, Failures: 0, Errors: 0, Skipped: 10** — BUILD SUCCESS |

计数裁定：WI14 审计基线 1190 + 本 WI 新增 6 = 1196，与日志声称一致，零退化成立（10 skipped 为既有跳过）。

### 1.7 门禁（审计项 7）——PASS（实跑记录）

| 命令 | 退出码 | 结果 |
|---|---|---|
| `node ai-dev/tools/check-doc-links.mjs --strict` | 0 | 0 errors / 3 warnings（均为 nop-bytecode 旧 plan 既存，与本 plan 无关） |
| `node ai-dev/tools/check-nop-stream-invariants.mjs sync` | 0 | `sync: OK` |
| `parseRoadmapMarkdown`（tools/mission-driver/src/roadmap-check.mjs 实调） | — | **items 31 + milestones 7，done 19，progress 0.61，名字全唯一无静默丢弃**；WI7=`todo`、M2=`not-done`（预期态——翻转在 audit 通过之后） |

### 1.8 plan 文本一致性与日志（审计项 8）——不一致两处（见 B-1/B-2），执行期修正记录齐备

- Phase 1 勾选与 live 逐条对照：四类齐备 ✓、日志更新 ✓、全量绿 ✓、SelfJoin 独立类 ✓；但「对齐判别断言」勾选项 live 不成立（§1.2a），Exit Criteria「§八 4 与 §八 6 断言落地且形态可核」对 §八 6 不成立（§1.4）——**勾选超出 live 事实**。
- 日志 `ai-dev/logs/2026/10-02.md` WI7 条目：两条执行期修正**如实记录** ✓——「执行期修正：manifest 键=execute job name 而非 setJobId」（与 live :114 `sanitizeJobId("wi7-eos-run-1")` 一致）与「观测算子需 copyForSubtask 构造拷贝（Observation 非 Serializable）」（live 四观测算子均实现 copyForSubtask）。第一条目对用例 1 的描述（「无丢失无重复 + 双通道有序 + §八 4 barrier-after-element 时序断言」）如实；但「restore 不重发」表述超出 live 断言能力（恒真式，见 B-2）。
- git 纪律：tracked 改动仅 `ai-dev/logs/2026/10-02.md`；untracked 为 plan + 5 个测试文件；生产代码与 `_gen/`/`_` 前缀生成物零改动——「不修改内核代码」Non-Goal 成立。

## 2. 实跑证据汇总

- 隔离：四类 6/6 绿（exit 0）
- runtime 全量：1196 绿（0 F / 0 E / 10 skip 既有）——exit 0
- 门禁：doc-links 0 / invariants sync OK / roadmap 31+7（done 19，WI7 与 M2 todo 预期态）
- 原始日志：`_tmp/audit-wi7/`（isolation 与全量输出，见下方附录路径）

## 3. 发现清单

### 阻塞（翻转 roadmap 前必须补救）

- **B-1 对齐判别断言空洞**：用例 1 断言面（计数 + 通道内序）在 aligned 与 unaligned 双模式下均通过；plan 承诺的 barrier 锚定位置断言未实现（`firstBarrierIdx` 未使用、Observation 三表分列无交错序列）。补救方向（实现者定夺）：观测算子把 barrier 标记混入单一事件序列（同文件 SequencedObserver 已有形态），配合慢源驻停窗口断言「慢侧驻停期间 fast 元素不得越过已对齐屏障」（aligned 阻塞下 fast 元素在屏障转发前不得出现在屏障之后的位置）；或以源侧标记 barrier 注入跨越的元素序号做位置等式断言。补救后 plan Goals/勾选文本与 live 对齐。
- **B-2 §八 6 双证据绑定未落地**：manifest operator state 值零比对；restore 断言 `sink.isEmpty() || countOf(sink,7)==0` 恒真（值 7 不可达）；initializeState offset、run-2 无重复、epoch 严格递增全无断言。补救方向：读 manifest 的 operatorStates 断言 `"wi7-offset:a"==6 && "wi7-offset:b"==6`；run-2 改 `assertTrue(sink.isEmpty())` 并让 CountingSource 暴露 restore 后 offset（断言 ==6）或以 restore 后新元素（count 提到 8，断言 run-2 恰发 7..8 各一次）做正判别；断言 run-2 结束后 `loadLatestEpochManifest().getEpochId() > run-1 epoch`。

### Minor（非阻塞，建议随补救顺手处理）

- **M-1 `sameThread` 命名与线程同一性局限**：该变量实为 barrier-after-element anomaly 旗标（经 SequencedObserver 第三参写入），与线程无关；WI7 用例不验证线程同一性。要么落实真实线程断言（源记录 collect 线程 + `setSnapshotCallback` 包装记录回调线程比对），要么改名（如 `barrierPrecedesFirstElement`）并在类 javadoc 与 roadmap 括注注明「线程同一性由 core TestSourcePullBarrierInjection:168 承载」。`Observation.threads` 与 `SinkCollector` 为零引用死代码，应删或启用。
- **M-2 日志措辞**：「restore 不重发」应随 B-2 修复改为可核表述；修复前该句超出断言能力。
- **M-3 plan 文本回写**：B-1/B-2 补救后，plan Goals #1/#3 的实现描述与 Phase 1 勾选需与 live 逐句对齐（guide 规则 #18：勾选即事实）。
- **M-4 水位用例盲区（记录）**：「丢弃快侧水位」缺陷形态不被现断言捕获（merged=[100,200,300] 不变）；plan 反事实清单未含该形态，不阻塞，记录备查。

## 4. 无静默跳过 / Anti-Hollow 检查

- 新增文件全为测试与辅助类，生产代码零改动（git porcelain 实证），scan-hollow 不适用面；无 TODO/FIXME 残留于交付文件。
- 端到端调用链连通性：四类均经 execute() → 任务线程 → union InputGate → 观测算子/sink 真实流转（隔离跑日志含 coordinator、barrier-injector、persist 全链 DEBUG 证据）；`TestMultiInputExactlyOnceCheckpoint` 隔离跑日志见 `Stored epoch manifest 0/1` 与 `Completed checkpoint` 全链——机制链路无空洞。
- 但 hollow 的定义含「断言空洞」：B-1（对齐断言不判别）与 B-2（restore 断言恒真）属**验证层 hollow**——机制被执行、验收不设防，正是本 audit 应拦下的形态。

## 5. 结论

WI7 的交付面（四个具名类齐备、全端到端形态、水位 min 合并与 self-join 去重的判别性证据、manifest 持久化断言、1196 零退化、门禁全过、git 干净、执行期修正如实记录）大部分成立；但两项核心判别断言——barrier 对齐（本 WI 首要目的）与 §八 6 restore 双证据绑定——在 live 代码中**空洞/恒真**，plan 与日志的相应声明超出 live 事实。

**裁定 FAIL（首轮）**。本 audit 即 plan Phase 2 第一项的证据与门槛：剩余收口动作（实现者执行）——① 按 B-1/B-2 补救测试断言并复跑隔离 + 全量；② 按 M-1/M-2/M-3 处理命名、日志措辞与 plan 文本回写；③ 复审（可由独立子 agent 对增量复核）通过后，方执行 roadmap WI7 `todo → done`（括注单层一对，建议注明「线程同一性由 core TestSourcePullBarrierInjection 承载」局限）+ `parseRoadmapMarkdown` 31+7 复核 + M2 翻转；④ plan Phase 2 勾选、Closure 段落回填、`check-plan-checklist --strict` 与 `check-doc-links --strict` 退出码 0。

## 6. 复审（第二轮）——2026-10-02

- 复审人：独立子 agent（fresh session，与首轮 auditor、实现者均非同一 session；全部结论来自 live repo 实跑/实读/独立探针，未采信 plan 勾选与日志自述）
- 复审指令判定规则：PASS = B-1 路由合规 + B-2 已修或路由合规；FAIL = B-2 未修且未路由。
- 裁定：**FAIL（复审）**。B-1 部分合规（roadmap 侧成立、plan/代码侧 successor 记录缺失）；B-2 未修且未路由（Blocker 维持）。

### 6.1 B-1 路由复核——roadmap 侧合规，plan/代码侧 successor 记录缺失

**(a) roadmap FU-9 登记——合规。** `ai-dev/backlog/nop-stream-sql-roadmap.md:297` FU-9 五要素齐备：证据（f-4→f-5 转发间隔 8ms≈源速率而非对齐窗口 ≈80ms）、缺口定位（union 顶点 barrier wiring/tracker 路径）、佐证（单算子级 gate 阻塞已被 TestProcessingGuaranteeBehavior 证明）、规则 15 合规（「须 Fix 单独立项」）、修复后判别断言启用路径。`git diff` 实证 FU-9 为本轮 roadmap 唯一改动；解析器 31+7 不受影响（FU-n 属 Follow-up Backlog，在动态状态块外，符合 roadmap :220/:417 自身约定）。

**(b) live defect 独立实证——成立。** 复审计以临时探针（同拓扑复刻用例 1：STRICT_EXACTLY_ONCE + enableCheckpointing(50) + 慢源 120ms/快源 10ms，跑后已删）独立复现：观测序列 `e:f-1..f-4, b:0, e:s-2, e:f-5..e:f-40`——barrier(b:0) 之后 fast 元素**无阻塞持续转发**，f-4→f-5 gap 实测 **5ms**（≈源 10ms 速率，与 FU-9 的 ~8ms 同量级，远小于对齐阻塞下的 ~80ms 期望）；且整跑（~940ms、50ms interval）仅 **1 个** barrier 抵达观测算子。union 顶点 E2E 对齐阻塞缺位为真，判别断言按 live defect 路由 Fix 单独立项的决定本身诚实。

**(c) successor 记录与文本回写——不完整（三项缺失）。**

1. **plan 20 无「Deferred But Adjudicated」节**：grep `Deferred|FU-9|successor` 对 plan 20 零命中；任务声称的「moved to explicit successor ownership」条目未落地（该措辞为仓内既有惯例，如 plans/2026-08-01-1440-2:146，plan 20 未采用）。
2. **悬挂引用**：TestMultiInputBarrierAlignment.java:231 行内注释「see plan 20 Deferred」指向不存在的节。
3. **plan/log 未随裁定回写**：plan 20 Goals #1 与审查修订记录 M3 仍把对齐判别断言声明为交付内容、Phase 1 对应勾选仍 `[x]`——该断言已实证不可交付（live defect），首轮 M-3 未处理，guide 规则 18（勾选即事实）仍被违反；日志 10-02.md WI7 条目无裁定与 FU-9 路由记录（grep FU-9 零命中）。

**(d) 用例 1 当前断言面——如实降级 ✓。** :236-243 断言 barriers≥1、sCount==3、fCount==40、双通道内有序，与声明的「当前契约」（无丢失无重复 + 双通道有序 + barrier 送达）一致；环境切换真实（`new StreamExecutionEnvironment()` 默认 STRICT_EXACTLY_ONCE，CheckpointConfig.java:50；`createTestEnvironment()` 强制 AT_LEAST_ONCE，StreamExecutionEnvironment.java:113-115——原 createTestEnvironment 形态下判别无从谈起，此修正在方向上必要）。

**(e) 恶化项（M-1 升级）**：TestMultiInputBarrierAlignment.java:44-46 javadoc 新增声明「The source also records its collect thread and asserts the snapshotState callback ran on the SAME thread」——live 代码 `readThread` 仅赋值（:265,269）从未比对；`sameThread` 仍是 barrier-before-element anomaly 旗标（:378-380 写入 → :340 断言，消息自述 "no barrier-before-element anomaly"）。线程同一性断言仍不存在，javadoc 把首轮 M-1 指出的缺口写成了已交付。:41-43「during the pause...snapshotState is not called」同属无断言支撑的声明（:303-305 注释自认 assert via barrier timing instead）。

### 6.2 B-2 复核——未修且未路由（Blocker 维持）

TestMultiInputExactlyOnceCheckpoint 首轮三项阻塞点**原样**：

1. **恒真析取在位**：:143-144 `assertTrue(sink.isEmpty() || countOf(sink, 7) == 0, "restored run must not re-emit the consumed range")`——CountingSource count=6，值 7 任何行为下不可达，第二支恒真，整个析取对任何行为（含 restore 完全失败全量重发 12 元素）通过，判别性仍为零。
2. **manifest offset 绑定仍零断言**：test 1 :113-116 仅 `loadLatestEpochManifest` 非 null；`EpochManifest.getTaskSnapshots()`（EpochManifest.java:124）→ `TaskStateSnapshot.getOperatorStates()` 中的 `"wi7-offset:a/b"==6` 从未读出比对。
3. **restore 绑定三要素仍全无**：initializeState 收到 offset、run-2 无重复、epoch 严格递增（`EpochManifest.getEpochId()`（:118）run-2 > run-1）零断言。

**且未路由**：无 FU 条目、无 Deferred 记录、plan/log 无裁定——不满足「已修」也不满足「路由合规」。

**恶化项**：类 javadoc（:31-34）新增声明「the manifest's recorded source offsets equal what snapshotState wrote, and a second run restored from the same durable state continues from those offsets (epoch strictly advancing)」——三个零落地绑定被 javadoc 写成已交付，doc-vs-code 失实（与 §6.1(e) 同性质）。

**最小修法（给实现者）**：

1. test 1 读出绑定：遍历 `manifest.getTaskSnapshots().values()` 的 `getOperatorStates()`，断言 `"wi7-offset:a"==6 && "wi7-offset:b"==6`，并锁存 `long epoch1 = manifest.getEpochId()`。
2. CountingSource.initializeState 将收到的 offset 写入并发收集器，run-2 结束断言两源 restore offset 均 ==6（证明 restore 真实吃到 manifest 值，而非碰巧从 0 起跑）。
3. :143-144 改 `assertTrue(sink.isEmpty(), "restored run must not re-emit the consumed range")`（有界源 run-1 已耗尽，restore 正确时 run-2 必零输出；原第二支不可达恒真必须移除）；或更强判别：CountingSource count 提到 8，断言 run-2 恰发 7..8 各一次。
4. run-2 结束重载 `loadLatestEpochManifest`，断言 `getEpochId() > epoch1`。
5. javadoc 与断言对齐（修断言或删失实声明）。

### 6.3 判别性与零退化复核——成立

- **水位 min 合并（首轮已认可，确认未削弱）**：TestMultiInputWatermarkMinMerge 与首轮认可版逐行一致（:146 `mergedData.get(0)==100`、:148-149 慢侧驱动、:150-152 单调、:153-154 `noneMatch(m>=900)`）；M-4 盲区维持记录态，无变化。
- **Self-join**：TestMultiInputSelfJoinEdgeDedup 与首轮认可版一致（:38 恰 6、:41-43 逐元素恰两份）。
- **隔离实跑**：四类 `Tests run: 6, Failures: 0, Errors: 0` — exit 0（`_tmp/audit-wi7-reaudit/isolation.log`）。
- **runtime 全量**：`Tests run: 1196, Failures: 0, Errors: 0, Skipped: 10` — exit 0（`_tmp/audit-wi7-reaudit/full.log`），与首轮 1196 持平，零退化保持。

### 6.4 门禁复审——全部维持通过

| 门禁 | 实测 |
|---|---|
| runtime 全量 | 1196 / 0 F / 0 E / 10 skip 既有 — exit 0 |
| `check-doc-links.mjs --strict` | exit 0（0 errors / 3 warnings，nop-bytecode 旧 plan 既存，与本 plan 无关） |
| `parseRoadmapMarkdown` | items 31 + milestones 7，done 19，progress 0.61，名字全唯一；WI7=`todo`、M2=`not-done`（预期态）；FU-9 在 Follow-up Backlog 不进解析块（roadmap :220 约定） |

### 6.5 复审结论

**裁定 FAIL（复审）**。按判定规则：B-2 未修且未路由 → FAIL（B-1 的 roadmap 侧合规不足以翻转）。机制面、端到端形态、零退化、门禁全部维持首轮结论；阻塞全部集中在验证层与文本一致性：

- **Blocker-1（B-2 原样）**：§6.2 三项 + javadoc 失实。按 §6.2 最小修法落地，或以同等判别力路由 successor 并在 plan Deferred 节 + FU backlog + 日志三处登记。
- **Blocker-2（B-1 路由不完整）**：roadmap FU-9 成立但 plan 20 缺「Deferred But Adjudicated」节（「moved to explicit successor ownership」条目未落地）、代码注释悬挂引用、plan Goals #1/勾选/M3 记录与日志未随裁定回写。
- **Minor（清账项）**：M-1 升级为 javadoc 失实声明两处（TestMultiInputBarrierAlignment:41-46 线程同一性与 pause 窗口、TestMultiInputExactlyOnceCheckpoint:31-34 双证据绑定）——落实断言或改述；M-2 日志「restore 不重发」措辞随 B-2 修复同步。

**翻转前置条件（实现者执行，第三轮复审核验）**：① Blocker-1 修复；② plan 20 补 Deferred But Adjudicated 节（B-1 判别义务 → FU-9，moved to explicit successor ownership）并回写 Goals/勾选/修订记录与 live 一致（用例 1 降级为当前契约 + live defect 路由记录）；③ javadoc 失实声明更正、悬挂引用消解；④ 日志 WI7 条目补裁定与 FU-9 路由；⑤ 复跑隔离 + 全量 + 三门禁。三者齐备且第三轮复审 PASS 后，方执行 roadmap WI7 `todo → done`（括注单层一对，注明对齐 E2E 判别由 FU-9 承接、线程同一性由 core TestSourcePullBarrierInjection 承载）+ M2 翻转 + plan Phase 2/Closure 回填。

## 7. 复审（第三轮）——2026-10-02

- 复审人：独立子 agent（fresh session，与前两轮 auditor、实现者均非同一 session；全部结论来自 live repo 实跑/实读，未采信 plan 勾选与日志自述）
- 复审范围：实现者按复审 §6.2/§6.5 声称的三项修正——B-2 重写（manifest 绑定 + RESTORE_PROBE + sink.isEmpty + javadoc）、B-1 补全（plan 20 Deferred 节 + alignment javadoc）、WI14 侧恒真探针删除
- 裁定：**FAIL（第三轮）**——机制与代码侧全部成立，差距收窄至文本一致性：§6.5 前置 ②尾项（plan Goals/勾选/修订记录回写）与 ④（日志裁定 + FU-9 记录）未执行，plan 20 Deferred 节重复插入两份。roadmap WI7 与 M2 仍不得翻转。

### 7.1 B-2 修复核验——机制级成立（§6.2 最小修法 1/2/3/5 落地，隔离实跑验证）

live 代码 `TestMultiInputExactlyOnceCheckpoint.java`（隔离 2/2 绿，断言对真实持久化状态生效）：

1. **manifest offset 绑定断言落地**：`secondRunRestoresFromDurableOffsetsWithoutDuplication` :143-163 读出 `loadLatestEpochManifest` 后遍历 `getTaskSnapshots()` 各 `getOperatorStates()`，按后缀匹配 `wi7-offset:a`/`wi7-offset:b` 且值==6，`assertTrue(foundA && foundB, "...must bind both sources' durable offsets to 6")`（:162-163）——缺键或值≠6 即失败，判别性成立。复审 §6.2 第 1 项「manifest offset 绑定零断言」消除（绑定断言落在本测试而非 test 1，义务面等价）。
2. **RESTORE_PROBE restore 绑定落地**：CountingSource :52-62 增静态 RESTORE_PROBE，initializeState（:91-99）收到 Integer offset 时写入；run-2 前 :175 清空、run-2 后 :184-187 断言 `restoreProbe("a")==6 && restoreProbe("b")==6`——冷启动（无 restore）时探针保持 -1 即失败，证明 restore 真实消费 manifest lineage 而非从 0 起跑。§6.2 三要素之「initializeState offset」落地。
3. **恒真析取已除**：:188-189 `assertTrue(sink.isEmpty(), "restored run must not re-emit the consumed range (offsets were complete)")`——`countOf(sink,7)==0` 不可达第二支移除；restore 失败重发 1..6 时 sink 有 12 元素、断言失败。§6.2 三要素之「run-2 无重复」落地。
4. **javadoc 如实**：类 javadoc :28-37 声明面=无丢失无重复 + durable manifest 的 task snapshots 绑定 a=6/b=6 + 同 jobId 第二跑 RESTORE（initializeState 收 6、不再重发）——与 live 断言逐一对应；复审指认的「epoch strictly advancing」失实声明已移除，doc-vs-code 一致性恢复。§6.2 第 5 步完成。
5. **残留（记录）**：§6.2 第 4 步（run-2 后 `getEpochId() > run-1` 递增断言）未实施；plan Goals #3（:29）仍把「epoch 严格递增」列在 §八 6 断言清单内——plan-vs-live 单点失实保留（代码 javadoc 已不声称；归入 §7.5 清账项 1）。cosmetic：`setRestoreProbe` 形参未用（仅 clear）；RESTORE_PROBE 为 static 跨用例共享，靠 run-2 前 :175 清空保证正确（当前形态成立）。

### 7.2 B-1 补全核验——路由三处成立、javadoc 如实；文本回写与日志未执行

**(a) plan 20 Deferred But Adjudicated 节——存在、内容与 roadmap FU-9 一致，但重复插入两份。** :90-97 四要素齐备：Classification `moved to explicit successor ownership`（roadmap FU-9 + 规则 15）、Why Not Blocking（Proof 类 + 内核缺陷单独立项 + 8ms vs ≈80ms 证据 + TestProcessingGuaranteeBehavior 佐证）、Successor Required: yes、Successor Path: roadmap Follow-up Backlog FU-9 → Fix plan——与 roadmap :297 FU-9 行逐点一致。复审 §6.1(c)1（successor 记录缺失）与 (c)2（alignment test :231 "see plan 20 Deferred" 悬挂引用）就此消解。**本轮新缺陷**：同一节在 Closure Gates 之后 :111-118 原样再插一份——编辑事故，须删重复份（保留一处）。

**(b) TestMultiInputBarrierAlignment javadoc——如实。** :33-50 三段声明与 live 逐一对应：①当前 E2E 契约=无丢失无重复 + 双通道有序 + barrier 送达下游（:239-246 断言面一致）；②aligned blocking 明文声明 NOT holding E2E through union vertex、FU-9 路由、时序判别 deliberately disabled（与复审 §6.1(b) 独立探针结论一致）；③§八 4=collect 边界行为断言（:334-343 `b > p1`，:343 消息如实 "no barrier-before-element anomaly"），线程同一性归 core TestSourcePullBarrierInjection（live 复核 :168-169 `source-task-thread` 断言仍在）。首轮 M-1/复审 §6.1(e) 指认的两处 javadoc 失实声明全部更正——代码侧诚实性成立。

**(c) 未执行项（§6.5 前置 ②尾项 + ④——本轮 FAIL 的唯一 blocker 簇）**：

1. **plan 20 文本未回写（guide 规则 18 继续违反，与复审 §6.1(c)3 完全同款、零改动）**：Goals #1（:27）仍声明「fast 源在 barrier(e) 后发出的元素严格出现在观测序列的 barrier(e) 之后（aligned gate 阻塞——若泄漏则断言失败）」与「source 的 snapshotState 在 injectBarrier 内同步回调记录 epoch-e 已 emit 数」——live 均不存在（判别已路由 FU-9、snapshotState 断言被放弃）；§八 4 goal（:31）仍声明「pause 窗口内下游无 barrier 且 snapshotState 未回调」「断言 snapshotState 回调线程与之相同」——均无断言支撑；Phase 1 首勾选项（:58）仍 `[x] ... + 对齐判别断言 + ...`——勾选超出 live 事实。WI7 翻转前置的 plan completed 需文本一致（guide 硬规则 5），该项不清则翻转即违规。
2. **日志未回写**：`ai-dev/logs/2026/10-02.md` WI7 条目（:3-7）无裁定与 FU-9 路由记录（grep FU-9 零命中）——§6.5 前置 ④ 未执行。首轮 M-2 的「restore 不重发」措辞现已由真实断言背书（该项了结）。
3. **同簇失实（一并清账）**：测试方法名 `alignedBarrierBlocksFastChannelAndBarrierInjectsOnSourceThread`（:209）与本类新 javadoc 直接矛盾（aligned blocking 不成立是 javadoc 明文）——命名级失实，随文本批改；`sameThread` 命名（:257/:343）维持 M-1 原状（消息已如实，命名仍误导）；probe source `readThread` 仍赋值未比对（:268,272，无害残留）；`MultiInputTestSupport.SinkCollector` 仍为零引用死代码（首轮 M-1 尾项未处理）。

### 7.3 WI14 侧恒真探针删除——确认

`TestDimTableLookupFunction.interfaceIsSerializableContract`（nop-stream-core）现为直接真断言 `assertTrue(java.io.Serializable.class.isAssignableFrom(ITableLookup.class))`（:42-47），assertThrows 探针已删——与 WI14 audit M-2 处置一致，无新增恒真断言。

### 7.4 实跑与门禁（第三轮全量复跑）

| 项 | 实测 |
|---|---|
| 四类隔离（-Dtest 四类） | **Tests run: 6, Failures: 0, Errors: 0, Skipped: 0** — BUILD SUCCESS exit 0（`_tmp/audit-wi7-r3/isolation.log`；ExactlyOnceCheckpoint 2/2 含新断言，BarrierAlignment 2/2） |
| runtime 全量 | **Tests run: 1196, Failures: 0, Errors: 0, Skipped: 10** — BUILD SUCCESS exit 0（`_tmp/audit-wi7-r3/full.log`），与两轮基线 1196 持平，零退化 |
| `check-doc-links.mjs --strict` | exit 0（0 errors / 3 warnings——nop-bytecode 旧 plan 既存，与本 plan 无关） |
| `check-nop-stream-invariants.mjs sync` | exit 0（`sync: OK`） |
| `parseRoadmapMarkdown`（tools/mission-driver/src/roadmap-check.mjs 实调） | items 31 + milestones 7，done 19，progress 0.61，名字全唯一无静默丢弃；WI7=`todo`、M2=`not-done`（预期态）；FU-9 在 Follow-up Backlog 不进解析块 |

### 7.5 第三轮结论

**裁定 FAIL（第三轮）——机制面与代码面全部达标，剩余为最后一公里文本一致性。** 判定规则沿复审：B-2 已修成立（§7.1）；B-1 路由在 roadmap/plan 节/代码三处成立且 javadoc 如实（§7.2ab），唯 §6.5 前置 ②尾项与 ④ 未执行（§7.2c）——此即复审 Blocker-2 第 3 项组成，原样保留，按既有标准不得 PASS（与轮次间标准一致性：第一轮 B-1/B-2、第二轮 Blocker-2 均属「声明超出 live 事实」同类）。

剩余清账清单（实现者执行，第四轮复审只验文本与门禁，无需重推机制）：

1. **plan 20 文本回写（blocker）**：Goals #1 与 §八 4 goal 改述为当前契约——对齐判别 → 指向 Deferred 节/FU-9；线程同一性 → core TestSourcePullBarrierInjection；删除「snapshotState 在 injectBarrier 内同步回调记录」「pause 窗口内下游无 barrier 且 snapshotState 未回调」等无断言支撑的声明（或落地真实断言，二选一）；Goals #3 删「epoch 严格递增」或补 run-2 `getEpochId()` 递增断言（二选一）；Phase 1 :58 勾选项措辞与 live 对齐；:9 修订记录追加裁定注记（M3 判别义务 → FU-9、M4 线程同一性 → core，标注按 FU-9 路由修订，不重写历史）。
2. **删除 :111-118 重复的 Deferred But Adjudicated 节**（保留 :90-97 一份）。
3. **日志 10-02.md WI7 条目补裁定与 FU-9 路由记录**（blocker）。
4. **同批顺手（minor）**：:209 测试方法名改为与契约一致（如 `barrierFlowsThroughUnionWithDeliveryIntegrity`）；`sameThread` → `barrierPrecedesFirstElement` 或落实真实线程断言；删 `SinkCollector` 死代码与 `setRestoreProbe` 未用形参。
5. **复跑四类隔离 + runtime 全量 + 三门禁**（改动仅文本与命名，预期全绿不变；`check-doc-links --strict` 因 ai-dev 文件改动必须复跑）。

**翻转前置条件（维持 §6.5 尾段，第四轮复审核验）**：上述 1-5 完成且第四轮复审 PASS 后，方执行 roadmap WI7 `todo → done`（括注单层一对，注明对齐 E2E 判别由 FU-9 承接、线程同一性由 core TestSourcePullBarrierInjection 承载）+ `parseRoadmapMarkdown` 31+7 复核 + M2 翻转 + plan Phase 2/Closure 回填 + `check-plan-checklist --strict` 退出码 0。

## 8. 复审（第四轮）——2026-10-02

- 复审人：独立子 agent（fresh session，与前三轮 auditor、实现者均非同一 session；全部结论来自 live repo 实跑/实读，未采信实现者自述）
- 复审范围（约定只验文本与门禁）：第三轮 §7.5 清账五项
- 裁定：**FAIL（第四轮）**——清账五项中 ①②④⑤ 成立；③ 的要求内容（裁定 + FU-9 路由 + 轨迹）已落地，但同一编辑将旧 WI7 节标题误改为「### WI16 收口」，旧 WI7 内容错位归因——本轮新引入的日志文本缺陷（guide 规则 5 元素），为唯一阻塞项。

### 8.1 清账五项逐项核验

**① plan 20 文本回写——成立（一处尾项见 8.2-2）。** 逐点对照 live：

- Goals #1（:27）：「当前 E2E 契约断言：无丢失、无重复、双通道有序、barrier 送达下游」+「执行期发现（已路由 FU-9）……时序判别断言待 FU-9 修复后启用」+「§八 4 行为断言……线程同一性由 core TestSourcePullBarrierInjection 承载」——与 live 断言面（TestMultiInputBarrierAlignment :239-246、:334-343）及 FU-9 登记完全一致；首轮 B-1 指认的「aligned gate 阻塞判别断言」「snapshotState 在 injectBarrier 内同步回调记录」失实声明已全部移除。
- Goals #3（:29）：「manifest task snapshots 的 operator states 绑定两源 durable offset（a=6/b=6）；第二跑 restore 后各源 initializeState 收到 durable offset（RESTORE_PROBE=6）、消费区间不重发」——「epoch 严格递增」已删，表述与 live 断言逐一对应。
- §八 4 goal（:31）：行为断言 + 线程同一性归 core——与 live 一致。
- Phase 1 勾选（:58）：改述为当前契约 + FU-9 路由——guide 规则 18 违反消除。

**② Deferred 重复节——成立。** `grep -c "## Deferred But Adjudicated"` = 1（:90-97 保留，:111-118 重复份已删）。

**③ 日志回写——要求内容成立，但引入新缺陷（本轮唯一阻塞项）。** 新 WI7 条目（10-02.md :3-8）逐句核对 live 全部如实：四类断言面表述、执行期发现（gap=8ms vs ≈80ms）、FU-9 路由（规则 15）、三轮 audit 轨迹、Doc-sync 裁定。**但**原 WI7 节（首轮 Phase 1 条目）的标题「### WI7 多输入回归三件套（Phase 1 完成，待收口审计）」被误改为「**### WI16 收口**」（:10），其下 3 条 bullet 仍是旧 WI7 内容原文（Plan 20 + 「aligned 模式……时序断言」+「restore 不重发；执行期修正：manifest 键=execute job name」）——旧 WI7 标题已 grep 不复存在。后果：日志出现两个 WI16 收口节，:10 节以 WI16 之名承载 WI7 内容，紧邻 :16 的真实 WI16 收口节，归因错位且与上方新 WI7 条目陈旧重复。git diff 实证该节为本轮新增行。

**④ Minor 清理——成立（一项可选残留）。** 测试方法改名 `multiInputBarrierFlowDeliversIntactUnderStrictGuarantee`（:209，与契约一致）；`sameThread` → `barrierBeforeElement`（:257/:312/:343，`assertTrue(!barrierBeforeElement.get(), "no barrier-before-element anomaly")` 语义清晰）；`MultiInputTestSupport.SinkCollector` 死代码已删（grep 仅存 Observation）。可选残留：`CountingSource.setRestoreProbe` 形参仍未用（:55-56 仅 clear——实现者已预留列入；下次触碰该文件时可简化为无参 `clearRestoreProbe()`，非阻塞）。

**⑤ 实跑与门禁（独立复跑，非采信自述）**

| 项 | 实测 |
|---|---|
| 四类隔离 | **Tests run: 6, Failures: 0, Errors: 0** — BUILD SUCCESS exit 0（`_tmp/audit-wi7-r4/isolation.log`；改名后无破坏） |
| runtime 全量 | **Tests run: 1196, Failures: 0, Errors: 0, Skipped: 10** — BUILD SUCCESS exit 0（`_tmp/audit-wi7-r4/full.log`），与基线持平，零退化 |
| `check-doc-links.mjs --strict` | exit 0（0 errors / 3 warnings 既存） |
| `check-nop-stream-invariants.mjs sync` | exit 0（`sync: OK`） |
| `parseRoadmapMarkdown` | items 31 + milestones 7，done 19，progress 0.61，名字全唯一；WI7=`todo`、M2=`not-done`（预期态） |

### 8.2 判定与剩余项

**裁定 FAIL（第四轮）**。机制、代码、plan Goals/勾选、门禁、零退化全部达标；阻塞集中于本轮日志编辑引入的一处归因错位——按贯穿四轮的同一标准（声明/记录须与 live 及真实归属一致，日志为 guide 规则 5 明列的一致性元素），不得带缺陷翻转。

剩余项（第五轮只验以下文本，无需重跑 maven——无代码变更）：

1. **（阻塞，一行）** 10-02.md :10「### WI16 收口」节：恢复原标题「### WI7 多输入回归三件套（Phase 1 完成，待收口审计）」（推荐——保留历史、最小改动），或整节删除（其信息已被 :3-8 新条目完全取代）。二选一。
2. **（随批，第三轮 §7.5-1 尾句）** plan 20 :9 修订记录追加一句裁定注记（如「后续裁定：M3 判别义务按 FU-9 路由、M4 线程同一性由 core TestSourcePullBarrierInjection 承载——见 Deferred 节与 Goals 当前契约」），不重写历史。第三轮曾列入 blocker 清单、本轮未执行；因其为草期审查要求的历史记录（非交付声明）且裁定已在 Goals/勾选/Deferred/日志四处承载，维持随批必改但不再单独构成多轮往返理由。
3. **（可选）** `setRestoreProbe` → 无参 `clearRestoreProbe()`；Goals #2「两源 latch 驻留」为既有机制措辞 imprecision（快源 latch(0) 实际不驻留，首轮 §1.2b 已记录），不要求改。

上述 1-2 完成后第五轮复审核验两处文本 + `check-doc-links --strict` 复跑（ai-dev 文件改动），PASS 即执行 §6.5/§7.5 尾段翻转序列（roadmap WI7 → done 括注单层一对 + 解析器 31+7 复核 + M2 翻转 + plan Phase 2/Closure 回填 + `check-plan-checklist --strict` 0 + 日志翻转条目）。

## 9. 复审（第五轮）——2026-10-02

- 复审人：独立子 agent（fresh session，与前三轮 auditor、实现者均非同一 session；全部结论来自 live repo 实读/实跑，未采信实现者自述）
- 复审范围（约定只验文本与门禁，无代码变更故 maven 免复跑）：§8.2 剩余项 1-2
- 裁定：**PASS**

### 9.1 剩余两处文本核验

1. **日志 :10 标题恢复——成立，且内容同步对齐。** `grep` 实证：:3（新 WI7 收口轨迹条目）、**:10「### WI7 多输入回归三件套（Phase 1 完成，待收口审计）」（原标题恢复，保留历史）**、:16（真实 WI16 收口节）——「### WI16 收口」误标标题不复存在。第四轮指认的归因错位消解。附带核验：恢复节的 bullet 措辞亦已与 live 对齐（「STRICT env 下无丢失无重复 + 双通道有序 + barrier 送达；对齐阻塞 E2E 缺位已路由 FU-9」——旧「aligned 模式……时序断言」超实表述移除；「restore 不重发」由真实断言背书）。
2. **plan :10 收口裁定注记——成立。** 新增注记逐字核对：「M3 的判别义务路由 FU-9（union 顶点 E2E 对齐阻塞缺位为 live defect，须 Fix 单独立项）；M4 的线程同一性由 core TestSourcePullBarrierInjection 既有断言承载——两项裁定的完整推理在 Goals #1/§八 4 goal、Deferred But Adjudicated 与日志 WI7 条目四处承载」——与 §7.5-1 要求一致，历史记录未被改写（:9 原文保留，注记另起一行）；「四处承载」指认逐处 grep 实证在位。`## Deferred But Adjudicated` 仍计 1（编辑未引入重复）。

### 9.2 门禁与有效性

| 项 | 实测 |
|---|---|
| `check-doc-links.mjs --strict` | exit 0（0 errors / 3 warnings——nop-bytecode 旧 plan 既存，与本 plan 无关）（`_tmp/audit-wi7-r5/doc-links.log`） |
| 代码变更面 | 本轮零 Java 变更（execution 包 mtime 最新 22:34，早于第四轮隔离 6/6 + 全量 1196 绿跑）——第四轮实跑结果继续有效，maven 免复跑成立 |
| roadmap 解析态 | 未触碰（第四轮实测 31+7、done 19、WI7=`todo`、M2=`not-done` 维持） |

### 9.3 判定：PASS——翻转序列（实现者执行）

五轮累计全部 Blocker（B-1 对齐判别空洞 → FU-9 路由闭合、B-2 恒真析取/三绑定 → 机制修复）与清账项（plan 回写、Deferred 去重、日志裁定轨迹、标题误标、改名/死代码）关闭；四类判别性证据（水位 min 合并、self-join 恰两份、exactly-once manifest 绑定 + RESTORE_PROBE、多输入交付完整性）与门禁、零退化全部成立。

**WI7 具备翻转条件。** 实现者按序执行（翻转批次涉及 roadmap/plan/log 改动，批完成后需再次复跑 doc-links）：

1. roadmap WI7 `todo` → `done`（括注单层一对，注明：对齐 E2E 判别由 roadmap FU-9 承接、线程同一性由 core TestSourcePullBarrierInjection 承载）
2. `parseRoadmapMarkdown`（tools/mission-driver/src/roadmap-check.mjs）复核 items 31 + milestones 7、done 20、名字全唯一
3. M2 里程碑 `not-done` → `done`
4. plan 20 Phase 2 勾选、Closure 段落回填（Evidence 指向本档 §9）、Plan Status → `completed`；`check-plan-checklist --strict` 退出码 0
5. 日志 10-02.md 补 WI7 翻转条目；`check-doc-links --strict` 复跑退出码 0
