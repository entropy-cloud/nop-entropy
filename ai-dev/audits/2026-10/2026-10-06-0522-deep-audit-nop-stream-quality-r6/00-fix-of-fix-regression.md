# 维度 00：修复批次回归审计（fix-of-fix）

> 审核日期：2026-10-06（0522 批次 R6）
> 基线：R5 收口后落地的行为性修复提交（402f108bb0 / 8e028f78ee / 032137dda5 / 58895e4e95 / e600de4058 / c79be63506 / 3a271f8880 / 058ca2fcab）+ 治理抽查（a41a6abfd3 / 970f45a0ba）
> 方法：`git show <sha>` 逐 hunk 审查 + live code 交叉验证（全部结论基于当前 HEAD 的 git show / grep 证据，未引用修复声明文本作为事实）+ 修复声明基线（R5 summary + plans 2310-2317，按本维度唯一例外权限读取）
> 只读审计：未运行构建/测试；变异推演基于静态代码阅读

## 审计范围与方法

对 8 个行为性修复提交逐 hunk 回答三问：(a) 修复是否引入新回归或语义偏差；(b) 修复对缺陷族全部入口（配置面/部署形态/恢复路径/对称方法）的覆盖面是否成立；(c) 配套测试是否具备"回退必红"保护力。治理提交按声明抽查。发现按初审（逐提交）+ 深挖第 2 轮（族入口/对称面盲区）两轮组织。

### 总体结论

- R5 修复批次整体质量良好：ST-01 P0 修复的机制（任务线程 freezeRawValue + 每次 snapshot 新建 SerDe + 头覆盖仅限 Object 声明 + 外来快照 fail-fast 保留）、ST-11 timer 持久化的 deferred-to-open 纪律、KR-7 自描述 form、REG-1 subtask-0 统一 deepCopy、FLOW-2 反向 BFS、CROSS_JOIN 五枚举补全等核心修复经逐 hunk 验证成立，多数配有"回退必红"真保护钉子。
- 但发现 13 项保留问题：1 项修复本身携带缺陷（REG-01 CC-11 估计器在乱序事件时间下失效——恰是该 buffer 的招牌场景）；1 项修复落地不完整（REG-02 CC-02 预锁激活点未移除）；1 项守卫存在前缀族旁路（REG-03 SEC-01）；1 项修复引入生命周期退化（REG-04 CON-02 启动失败闩锁）；1 项守卫枚举漏掉 24 小时前 2312 刚新增的同族状态键（REG-05）；1 项无迁移说明的升级面改名（REG-06）；1 批修复完全无测试钉（REG-12，跨 2315/2316 共 7 项）。
- 治理"行为保持"声明抽查全部成立（970f45a0ba 抽查 4 项删除/迁移 0 残留引用、语义超集有据；a41a6abfd3 代码面 hunks 逐行确认为纯注释/javadoc）。

## 第 1 轮（初审）

### [REG-01] CC-11 putAll 最大 seq 估计器在乱序事件时间下失效，碰撞窗口重开
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/buffer/PerKeyOrderedBuffer.java:60-73`
- **证据片段**:
  ```java
  public void putAll(PerKeyOrderedBuffer<K, V> other) {
      long maxMergedSeq = -1L;
      for (Map.Entry<K, TreeMap<long[], V>> e : other.buffers.entrySet()) {
          TreeMap<long[], V> tree = buffers.computeIfAbsent(e.getKey(), k -> new TreeMap<>(TsSeqComparator.INSTANCE));
          tree.putAll(e.getValue());
          if (!e.getValue().isEmpty()) {
              maxMergedSeq = Math.max(maxMergedSeq, e.getValue().lastKey()[1]);
          }
      }
      if (maxMergedSeq >= 0) {
          insertionSeq = Math.max(insertionSeq, maxMergedSeq + 1);
      }
  }
  ```
- **严重程度**: P2
- **现状**: `TreeMap` 按 (ts, seq) 双键排序（TsSeqComparator :44-54，类 javadoc :24-25 明言 "out-of-order elements are sorted by event time"），`lastKey()` 返回 ts 最大者——**不是 seq 最大者**。乱序流（buffer 存在的全部意义）下该估计器系统性低估：合并条目 add(ts=10)→seq0、add(ts=5)→seq1 后，lastKey=(10,0)，insertionSeq 只推进到 1，而真实最大 seq=2。恢复后第 2 次 add 分配 seq=1，若其 ts=5 即与合并条目 (5,1) 撞键，`TreeMap.put` **静默覆盖**工作视图条目——这正是 CC-11 描述的缺陷场景，被修复自身的实现重新打开。恢复管道（2312 KR-7 fromSerializableForm 恢复 → EquiJoinOperator.open/OverWindowOperator.open 的 `buffer.putAll(restored)`）使该路径从"被 ST-12 遮蔽的不可达"变为常规可达。
- **风险**: restore + 迟到数据（事件时间乱序为常态）下 join/over-window 工作视图静默丢条目 → 补全/帧计算结果错误且无任何日志。`other.insertionSeq` 字段本身已被 fromSerializableForm 精确重建（:186-189 逐 item Math.max），putAll 弃之不用反而走了有缺陷的 lastKey 估计。
- **建议**: `putAll` 改为 `insertionSeq = Math.max(insertionSeq, other.insertionSeq)`（一行），或在循环内对每 tree 逐 entry 取 `Math.max(maxMergedSeq, te.getKey()[1])`；并补乱序 ts 钉子（add ts=5、add ts=10 → putAll → add ts=5 断言不覆盖）。
- **信心水平**: 确定（比较器语义 + put 语义可直接推导；数值例见上）
- **误报排除**: 这不是"TreeMap 语义误读"类误报——比较器主键是 ts 不是 seq（:50-53 `Long.compare(a[0], b[0])` 在前），故 lastKey 的 seq 分量无任何下界保证；同类误报通常混淆"按值排序的插入序"，此处排序键是显式 long[] 复合键，证据即代码。
- **复核状态**: 未复核

### [REG-02] CC-02 修复落地不完整：预锁激活点 `this.active = true` 未移除，激活点"移入锁内"只做了一半
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:2124-2126, 2138-2144`
- **证据片段**:
  ```java
  this.currentLeadership = epoch;
  this.recoveryGen.set(0);
  // Mark active BEFORE rebuilding so the internal assignment materialization
  // (prepareAssignmentsLocked) observes the active state.
  this.active = true;                      // ← 预锁激活点（修复未移除，:2126）
  ...
  recoveryLock.lock();
  try {
      // R5 CC-02: mark active inside the lock after the rotation step so
      // ...
      this.active = true;                  // ← 2314 新增的锁内激活点（:2141）
  ```
- **严重程度**: P2
- **现状**: 2314 声明"activateAsLeader 激活点移入锁内 + rebuild 失败回滚"，但 pre-image 中的预锁激活行被保留，形成双重置位：锁外窗口内 `active=true` 先于 rebuild 对所有无锁读者（`assignTasks()` 的 `!active` 门、健康检查）可见。竞态：T1 activateAsLeader 预锁置 active=true → T2 assignTasks 通过三道门（running/active/FAILED）后在 recoveryLock 上阻塞 → T1 rebuild 失败回滚 active=false 并抛出 → T2 获锁继续 `prepareAssignmentsLocked` **照常签发部署**——半激活 leader 的孤儿部署窗口（CC-02 缺陷族本体）仍可达，只是窗口从"永久"缩为"单次 dispatch"。锁内置位的注释理由（"prepareAssignmentsLocked 观察 active"）与保留的预锁注释互相矛盾，说明该 hunk 属半途修改。
- **风险**: rebuild 失败 + 并发 assignTasks 时签发一次基于失败 rebuild 状态的任务部署；且 catch 仅回滚 `RuntimeException`（`activateAsLeader` 为 `private void` 无 throws，StreamException 是 RuntimeException，故该面闭合——但部署签发面未闭合）。同 epoch 重试活性目标（回滚使 elector 重授予可重试）已达成。
- **建议**: 删除 :2124-2126 的预锁置位及其注释（锁内 :2141 已先于 prepareAssignmentsLocked 执行，功能等价）；补半激活重试钉子（plan 2314 Non-Blocking Follow-up 已自认缺）。
- **信心水平**: 确定（两处置位同时存在于 live code；竞态序列为锁语义直接推演）
- **误报排除**: 不是"冗余赋值无害"类误报——危害不在赋值本身而在它与 assignTasks 无锁门控的交错时序；也不是"closure audit 已审过"——2314 的 Anti-Hollow 声明"激活点移入锁内（代码路径审计）"与 live code 不符，恰是本次审计要抓的 fix-of-fix 面。
- **复核状态**: 未复核

### [REG-03] SEC-01 钉定守卫只拒精确键，schema-history/offset 参数前缀族可绕过覆盖 nop 装配值
- **文件**: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/DebeziumEngineConfig.java:144-163`（守卫+合并）、`:182-202`（前缀族装配）
- **证据片段**:
  ```java
  // R5 SEC-01: ... Merge order below puts extraProperties LAST, ...
  for (String pinned : new String[] {"connector.class", "schema.history.internal"}) {
      if (config.getExtraProperties().containsKey(pinned)) {   // ← 仅精确键
          throw new NopException(DebeziumErrors.ERR_DEBEZIUM_CONFIG_INVALID)...
      }
  }
  // 额外属性
  for (Map.Entry<String, String> entry : config.getExtraProperties().entrySet()) {
      props.setProperty(entry.getKey(), entry.getValue());      // ← extraProperties 最后合并、last-write-wins
  }
  ```
- **严重程度**: P2
- **现状**: 守卫拒绝的 `"schema.history.internal"` 只是 store **类名**键；nop 装配的真实参数面是前缀族——`schema.history.internal.jdbc.url` / `jdbc.user` / `jdbc.password` / `jdbc.table.name` / `file.filename`（applySchemaHistory :182-202），`offset.storage` 同理存在 `offset.storage.file.filename` 前缀键（:98-99，精确键被 WI7 守卫但前缀键无守卫）。extraProperties 合并在最后（:160-163），部署方传入 `extraProperties={"schema.history.internal.jdbc.table.name": "...", "schema.history.internal.jdbc.url": "..."}` 即可把 schema history 重定向到任意库表/任意凭据，静默击穿"schemaHistoryStore 装配由 nop 管理"不变量——守卫注释宣称的不变量只闭合了类型选择面。
- **风险**: schema history（恢复期 DDL 上下文的唯一来源）与 offset 文件路径可被部署配置静默改道：跨租户表重定向、凭据改写、指向攻击者可控库；且因为守卫的存在造成"已钉死"的假象，后续开发者不会再加防护。
- **建议**: 守卫改为前缀判定（`key.startsWith("schema.history.internal")` / `key.startsWith("offset.storage")` / `key.equals("connector.class")`），或合并循环内对 pinned 前缀键跳过+WARN；补前缀绕过钉子。
- **信心水平**: 确定（Properties.setProperty last-write-wins + 合并顺序在装配之后，均为本文件可直接核验的事实）
- **误报排除**: 不是"前缀键不是真实 Debezium 属性"类误报——`schema.history.internal.jdbc.url` 是 Debezium JDBC schema history 的官方配置键，且本文件自己在 :183-191 就在写这些键；也不是"extraProperties 是部署者自己的信任域"——同文件对 `offset.storage`（WI7）与 `record.processing.threads` 已建立"信任域内仍钉死"的先例口径，本守卫自我声明同一纪律却未达成。
- **复核状态**: 未复核

### [REG-04] CON-02 startRequested 闩锁在启动失败后永不复位，包装器实例变为一次性
- **文件**: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/DebeziumEngineWrapper.java:134-146, 189-193`
- **证据片段**:
  ```java
  public synchronized void start() {
      if (running.get() || startRequested.get()) {
          LOG.warn("Debezium engine is already running");
          return;                                   // ← startRequested 残留 true 时永久拒绝重试
      }
      startRequested.set(true);
      try {
          ...
          engine = DebeziumEngine.create(...)....build();
          ...
      } catch (Exception e) {
          throw new NopException(DebeziumErrors.ERR_DEBEZIUM_ENGINE_START_FAILED)... // ← startRequested 未复位
      }
  }
  ```
- **严重程度**: P2
- **现状**: 修复前 start 门控在 `running` 上——引擎构建失败（如启动瞬间 DB 不可达）后 running=false，同一包装器可再次 start 重试。修复后门控在 `startRequested` 上且 catch 路径不复位：一次构建失败 → startRequested 恒 true → 后续 start 全部被 "already running" WARN 吞掉；即使先调 stop()（会执行，stopped 置位），startRequested 仍为 true，实例永久不可用。
- **风险**: 瞬态故障（连接器启动期数据库抖动）导致 CDC source 在该实例上永久沉默；若宿主生命周期复用同一函数/wrapper 实例做 in-JVM 重试（无 copyForSubtask 重建的路径），故障从"可自愈"退化为"需整体重启作业"。窗口内泄漏修复本身（stop 不再早退）是正确的。
- **建议**: catch 分支复位 `startRequested.set(false)`（或改为在 `running` 置位成功后才视作请求成立）；补"start 失败→重试 start 成功"钉子。
- **信心水平**: 确定（catch 不复位可直读；重试可达性取决于宿主是否复用实例——按 P2 而非 P1 定级即基于此不确定）
- **误报排除**: 不是"已运行重复 start 幂等保护"类误报——被拒的是**失败后**的合法重试，门控条件把"请求过"与"在运行"混为一谈；stop() 后 start 不可用是 pre-existing（stopped 闩锁），本次新增的退化仅是失败路径，范围界定清楚。
- **复核状态**: 未复核

### [REG-05] WIN-2/4 Phase 1 缩容守卫的五键清单漏掉 `process-internal-timers`（2312 刚新增的同族状态）
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/RescaleStateAssembler.java:137-141`
- **证据片段**:
  ```java
  private static boolean isPerKeyAuxiliaryOperatorState(String stateKey) {
      return stateKey.equals("internal-timers") || stateKey.equals("cep-event-time-timers")
              || stateKey.equals("trigger-accumulators") || stateKey.equals("pane-tracking")
              || stateKey.equals("reduce-state");
  }
  // TimerStateKeys.java:25  public static final String PROCESS_INTERNAL_TIMERS = "process-internal-timers";  ← 不在清单
  ```
- **严重程度**: P2
- **现状**: 守卫按状态键名枚举 per-key 辅助 operator state，缩容遇之 typed 拒绝。但 2312（早于 2316 数小时落地）刚给 ProcessOperator 引入的 timer 快照键 `process-internal-timers`（TimerStateKeys:25）不在清单：KeyedProcessFunction 的 timer 快照与 window timer 同构（per-key 的 InternalTimerService 快照），缩容 1:1 拷贝后，键组重分布搬走的 key 其 timer 留在旧 subtask（或反向）——fire 落在错误属主，keyed 状态访问 miss，正是本守卫要封死的静默分裂家族的残余入口。同函数 javadoc（:119-121）仍写着 "Scale-down (newParallelism < oldParallelism) never trips this guard"，与同提交引入的缩容拒绝行为直接矛盾（陈旧注释）。
- **风险**: 带 KeyedProcessFunction timer 的作业缩容恢复：timer 静默错置/丢失，onTimer 内 keyed 读写命中空状态——恰好是 WIN-2/4 在 window 算子上已封死的行为在 process 算子上的复活。
- **建议**: 清单加入 `TimerStateKeys.PROCESS_INTERNAL_TIMERS`（直接引用常量而非字符串字面量，顺带消除五处字面量漂移面）；更新 javadoc。
- **信心水平**: 确定（键值常量与清单均可在 live code 直查；错置后果与 WindowOperator 同构推演）
- **误报排除**: 不是"ProcessOperator timer 已有其他守卫"类误报——scale-up 路径的非空 operator-state 全量拒绝（:178-187）覆盖所有键，但 scale-down 路径只走五键清单；本条是该维度"对称方法覆盖面"检查的直接产物。
- **复核状态**: 未复核

### [REG-06] REG-3 schema history 表名后缀算法更换无升级/迁移说明，存量部署恢复面断层
- **文件**: `nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java:667-677`
- **证据片段**:
  ```java
  // R5 REG-3: suffix with the FULL sanitized instance name — the previous
  // lastSegment(instance) collapsed "cdcA-2" and "cdcB-2" to the same "2", ...
  config.setSchemaHistoryJdbcTable(
          (table == null || table.isEmpty() ? "nop_cdc_schema_history" : table)
                  + "_" + sanitizeIdentifier(instance));   // ← 旧: lastSegment(instance)
  ```
- **严重程度**: P3
- **现状**: 修复消除了 `cdcA-2`/`cdcB-2` 坍缩碰撞（正确），但**所有**存量部署的 history 表名同步改变（`base_2` → `base_cdcA_2`）。升级后首次恢复：offsets 指向既有 binlog 位点而新表为空 → schema history 无法重建 DDL 上下文 → Debezium 按空 history 处理（重快照或恢复失败，取决于 snapshot.mode）。2317 文档批次与 cdc-cookbook 改动均未见迁移注记（docs-for-ai/03-modules/nop-stream-cdc-cookbook.md 在 a41a6abfd3 仅 2 行改动，与表名迁移无关）。
- **风险**: 升级触发一次性非预期重快照（数据重投递、下游重复处理）或恢复失败告警；单实例（本无碰撞）部署也被波及。
- **建议**: owner doc 加一节升级说明（升级前导出旧 history 表内容到新表名，或接受一次重快照）；或对旧命名做一次回退读取兼容。
- **信心水平**: 很可能（表名变化的直接后果可从 Debezium history 语义推出；具体行为取决于 snapshot.mode 配置）
- **误报排除**: 不是"所有改名都要迁移说明"类过度报警——`REG-3` 恢复路径的 schema history 与 offset 位点是强耦合的（offset 有位点而 history 为空 = 无法重建 schema），这与普通缓存表改名不同；且受害面包含并未受害于原缺陷的单实例部署。
- **复核状态**: 未复核

### [REG-07] CON-04/CON-08 修复半回退为已登记的"已知未修复"：截断提交面无守卫、跨运行键碰撞仅 WARN（已知未修复）
- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java:240-249（snapshot 侧仅剩 forcePath）, :300-330（commit 侧无行数自检）, :582-625（键仍为 (epoch, subtaskIndex)）`
- **证据片段**:
  ```java
  // live 状态（2316 回退后）：
  writeLines(tempPath, currentBuffer);
  forcePath(tempPath);                      // CON-04 fsync 增量保留
  getPendingCommits().put(epochId, new FilePendingCommit(tempPath.toString(), count, subtaskIndex));
  // ... doCommitLocked 内：2316 移除了 recordCount 行数自检；
  // manifestKey/tempPath/finalPath 均为 (epoch, subtask) 形状——CON-08 键折叠已回退，
  // 跨运行碰撞仅剩两条 WARN（:318-323, :329-334）
  ```
- **严重程度**: P2
- **现状**: 2315 落地的 CON-08 键折叠与 CON-04 recordCount 行数校验在恢复混键场景引入 exactly-once 回归（TestStreamConnectorRegistryE2E 40→39），2316 回退仅保留 WARN 与 fsync 前移。plan 2315 addendum 与 2316 Deferred 均已诚实登记（successor：独立连接器身份传递计划）。本轮确认 live code 与该登记一致：截断的 epoch 文件仍可被静默 commit（fsync 只保证写入位持久，不防短写），跨运行 outputDir 复用的 guard 命中只告警不拒绝。
- **风险**: OS 级短写/满盘下截断文件以 exactly-once 名义提交；两个作业复用同一 outputDir 时后者输出被前者 manifest 静默吞掉（现至少有 WARN 可查）。
- **建议**: 按 Deferred successor 路径立项恢复路径身份传递；在此之前可考虑把 recordCount 校验做成"恢复混键检测后的条件校验"而非整体回退。
- **信心水平**: 确定（回退 hunks 在 3a271f8880 可逐行核对，live 状态已验证）
- **误报排除**: 这是任务书第 2 条口径的典型情形——"上轮已立案、本轮确认仍在"，照常报告并标注已知未修复；不作为新缺陷计入 R6 新增。
- **复核状态**: 未复核

### [REG-08] ST-01 点时快照残留：无自定义 serializer 时 List 元素与 Map value 叶子仍是活引用（已知未修复，Map 侧未登记）
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateSerDe.java:230-237（writeListPayload）, :247-256（writeMapPayload）, :818-828（serializeWithSerializer null 分支）`
- **证据片段**:
  ```java
  private static void writeListPayload(Map<String, Object> entry, Object storedValue,
                                       IStreamSerializer<Object> valueSer) {
      List<Object> serializedList = new ArrayList<>();
      for (Object v : (List<?>) storedValue) {
          serializedList.add(serializeWithSerializer(v, valueSer));  // ser==null → 原样返回活引用
      }
  ...
  Object value = serializeWithSerializer(me.getValue(), valueSer);   // Map value 同样活引用
  pair.add(valueSer == null ? ContainerValueCodec.encode(value) : value); // encode 不拷贝叶子（ContainerValueCodec:121）
  ```
- **严重程度**: P3
- **现状**: ST-01 修复覆盖了裸值（freezeRawValue）、appending 元素、map 键、state key、namespace，但 list/map 载荷**元素/值**经 `serializeWithSerializer` 的 null 分支原样放行（注释声明 ContainerValueCodec 需要活运行时类型）。barrier 后任务线程对 ListState 元素 / MapState value 叶子的原地变异仍会泄入异步持久化 JSON。List 面已在 plan 2311 Non-Blocking Follow-ups 登记（"现有生产算子无可变 ListState 元素原地变异用法"）；**Map value 叶子面同等活引用却未见于登记文本**。当前生产算子核查（window acc clone、CEP ledger 值为新建 TreeSet\<Long\>）暂无原地变异用法，故维持 watch 级。
- **风险**: 未来任何算子以 MapState/ListState 携带可变 POJO 并 barrier 后原地修改，即复现 ST-01 型快照撕裂，且当时修复声明"点时快照契约"（CheckpointConfig javadoc）会给开发者错误安全感。
- **建议**: 在 plan 2311 follow-up 文本中补登 Map value 面；长期方向是 ContainerValueCodec.encode 时对叶子做 typedCopy（接受类型信息改从包装头读取的代价）。
- **信心水平**: 确定（代码路径可直读；"无生产变异用法"经枚举核查但非穷举证明）
- **误报排除**: 不是把"容器本身已拷贝"误读为"元素未拷贝"——本轮证据精确到叶级（encode 的 pair.add(e.getKey())、序列化列表元素均原样），与 R5 已澄清的"容器级保护"分层一致。
- **复核状态**: 未复核

### [REG-09] ST-11 的 setKeyType 接线仅覆盖 Memory 后端，RocksDB 形态下恢复的 timer 键重材料化仍为恒等（部署形态覆盖缺口）
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/ProcessOperator.java:75-83`
- **证据片段**:
  ```java
  if (keyedStateBackend instanceof io.nop.stream.core.common.state.backend.memory.MemoryKeyedStateBackend) {
      internalTimerService.setKeyType(
              ((io.nop.stream.core.common.state.backend.memory.MemoryKeyedStateBackend<?>) keyedStateBackend)
                      .getSnapshotKeyType());
  }
  ```
- **严重程度**: P3
- **现状**: setKeyType 数据源取自 `MemoryKeyedStateBackend.getSnapshotKeyType()`（2312 F1 的显式 carve-out："接口无访问器，不做接口扩展"）。RocksDB 后端管线中 ProcessOperator 恢复的 timer 快照照常回放（CheckpointSerDe 的 TimerSnapshot 分支与后端无关），但无 keyType → HeapInternalTimerService 重材料化为恒等 → 非 String/非原始类型 key 的 timer 触发时 `setCurrentKey(degradedKey)`，onTimer 内 keyed 状态访问 miss。修复前 ProcessOperator 完全不持久化 timer，此为能力残缺而非回归；但 ST-11 的缺陷族口径是"恢复后 timer 正确触发"，RocksDB 形态只达成一半。
- **风险**: RocksDB + KeyedProcessFunction + bean/Date 键组合下，checkpoint 恢复后 timer "触发了但算错 key"，比不触发更难排查。
- **建议**: 在 IKeyedStateBackend（或 runtime 侧 restore 装配点）补 keyType 访问器，RocksDB 恢复路径同样回填 noteRestoredKeyType 语义。
- **信心水平**: 很可能（接线面为直读事实；RocksDB 恢复管线对 TimerSnapshot 的消费路径与 memory 共享已核对 CheckpointSerDe 分派）
- **误报排除**: 不是"RocksDB 场景 ProcessOperator 不可达"类臆测——stateBackend 配置是作业级选择，ProcessOperator 的 keyed backend 由 `stateBackend.createKeyedStateBackend(Object.class)` 按配置后端创建（:70-73），RocksDB 形态真实存在。
- **复核状态**: 未复核

### [REG-10] KR-3/REG-7 部署守卫存在第三个 JobGraph 构建入口旁路（当前不可达漂移形态，watch）
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:279-297`
- **证据片段**:
  ```java
  private static JobGraph buildJobGraphFromStreamModel(StreamModel streamModel) {
      io.nop.stream.core.graph.StreamGraphGenerator graphGenerator = new io.nop.stream.core.graph.StreamGraphGenerator();
      ...
      io.nop.stream.core.graph.StreamGraph streamGraph = graphGenerator.generate(sinkList);
      io.nop.stream.core.jobgraph.JobGraphGenerator jobGraphGenerator = new io.nop.stream.core.jobgraph.JobGraphGenerator();
      return jobGraphGenerator.generate(streamGraph);   // ← 无 validateKeyedRoutingParity
  }
  ```
- **严重程度**: P3
- **现状**: 2313 的 M-parity/P≤M 守卫只挂在 `StreamExecutionEnvironment.compilePlans`（:482）与 `buildJobGraph`（:780）。`GraphModelCheckpointExecutor.buildJobGraphFromStreamModel` 是第三个 JobGraph 构建入口（executeWithCheckpoint 两个重载使用）。经核查 `env.execute()` 在分派到 checkpoint 引擎前必经 compilePlans（StreamExecutionEnvironment.java:384），savepoint 入口也全走 buildJobGraph（:613/:628/:645），故当前所有 env 驱动流已被守卫覆盖；旁路仅对**程序化直调** `executeWithCheckpoint(StreamModel,...)` 的内部调用方开放。且 DSL 今日无 backend 声明（plan 2313 F-8 前提注记），漂移形态当前不可达。
- **风险**: 未来 DSL 支持 backend 声明或新增直调入口时，守卫不生效——AR-01 族以"第四入口"复活，且无人会想起这里。
- **建议**: 把 `validateKeyedRoutingParity` 从 private env 方法提为核心建图收口（StreamGraphGenerator/JobGraphGenerator 或 GraphModelCheckpointExecutor 入口同点调用），或在 buildJobGraphFromStreamModel 加同款断言。
- **信心水平**: 确定（入口枚举经 grep 全量核对；"不可达漂移"依据 plan 2313 自述的 DSL 现状）
- **误报排除**: 不是"入口旁路=必然缺陷"类过度立案——已按任务书口径核实部署形态覆盖并降为 watch 级 P3，明确写出当前不可达的依据。
- **复核状态**: 未复核

### [REG-11] CEP-2① 无 watermark WARN 扫描全模型而非 CEP 节点上游，存在假阴性（与假阳性）
- **文件**: `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java:755-766`
- **证据片段**:
  ```java
  boolean hasWatermarkSupply = owner.model().getTransforms() != null && owner.model().getTransforms().stream()
          .anyMatch(x -> x instanceof io.nop.stream.flow.model.StreamTimestampsAndWatermarksModel);
  if (!hasWatermarkSupply) {
      LOG.warn("CEP pattern [{}] runs in event-time mode but the pipeline declares no"
              + " <timestampsAndWatermarks> node — ...", m.getId());
  }
  ```
- **严重程度**: P3
- **现状**: 判定是"模型里**任何位置**存在 T&W 节点"，不是"该 CEP 节点的上游存在"。模型在无关分支放了一个 T&W 节点时，真正缺水的 CEP 不再告警——CEP-2 要抓的静默无输出恰恰复发且更隐蔽（开发者以为有守卫）。反向：processing-time 模式的 CEP（无需 watermark）也会收到假阳性 WARN（消息硬编码 "runs in event-time mode"）。
- **风险**: WARN 作为 typed 终态前的唯一过渡防线，假阴性使其对目标场景失明；日志噪声损害后续告警可信度。
- **建议**: 改为沿 streamRegistry/upstream 边反向走 CEP 节点可达集判定（buildCep 已持有 upstreamIds）；按 timeBehaviour 条件触发消息。
- **信心水平**: 确定（判定表达式直读；WARN 为既定过渡态、typed 终态已登记 Deferred）
- **误报排除**: 不以"WARN 而已"降格为不立案——本维度 (b) 问的是覆盖面；该 WARN 的判定面与其宣称保护的缺陷面（本节点缺水）不重合，属结构性覆盖错位而非风格问题。
- **复核状态**: 未复核

### [REG-12] 修复批次测试保护力缺口：7 项行为性修复零配套钉子（回退不可检）
- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:472-482（CEP-1 守卫）, :411-425（CEP-7 重挂）; nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java:838-848（FLOW-4 守卫）; nop-message/nop-message-debezium/.../DebeziumEngineConfig.java:148-153（SEC-01）; .../DebeziumEngineWrapper.java:134-146（CON-02）; .../DebeziumCdcSourceFunction.java:667-677（REG-3 命名）; .../FileTwoPhaseCommitSink.java:248（CON-04 fsync）`
- **证据片段**:
  ```java
  // 全仓测试面 grep 证据（模式：
  //   grep -rln "not support the RocksDB|R5 CEP-1" nop-stream/*/src/test
  //   grep -rln "R5 CEP-7" nop-stream/*/src/test
  //   grep -rln "ERR_STREAM_REQUIRED_BODY|watermarkStrategyBean / timestampAssigner" nop-stream/nop-stream-flow/src/test
  //   grep -rn "extraProperties" nop-message/nop-message-debezium/src/test
  //   grep -rn "startRequested|ERR_DEBEZIUM_ENGINE_START_FAILED" nop-message/nop-message-debezium/src/test
  // 全部 0 命中）
  ```
- **严重程度**: P2
- **现状**: 2316 仅给 FLOW-2（TestSinkReachabilityGuard 2 用例）与 WIN Phase 1（TestScaleDownPerKeyStateGuard）落了钉子；CEP-1 RocksDB 拒绝、CEP-7 PT 重挂、FLOW-4 三空拒绝**全仓测试面 0 引用**。2315 中 SEC-01 守卫、CON-02 闩锁语义、CON-04 fsync、CON-05 WARN、REG-3 命名同样无任何断言（该提交的测试改动仅 CON-03 replace 语义与 CON-06 矩阵对齐）。这些修复回退后 CI 仍全绿——2316 提交消息对钉子面的披露是诚实的（"TestSinkReachabilityGuard(2)/TestScaleDownPerKeyStateGuard + 回退探针 flow2/win"），但 R5 审计确立的"每项修复回退必红"惯例在这 7 项上未兑现。
- **风险**: 最易被后续重构回退的恰是守卫与 WARN 类小 hunk（如 REG-05 的清单、REG-03 的守卫、CEP-7 的重挂块）；无钉即无回退检测，修复存活完全靠代码评审记忆。
- **建议**: 每项补最小钉子：CEP-1（RocksDB backend + CEP build 期断言 typed 错）、CEP-7（PT 模式 snapshot→restore→断言 registerProcessingTimeTimer 被调用/触发）、FLOW-4（三空 DSL 断言 ERR_STREAM_REQUIRED_BODY）、SEC-01（extraProperties 前缀键断言拒绝）、CON-02（start 失败→重试语义）、REG-3（表名后缀断言）。
- **信心水平**: 确定（0 命中经多模式 grep 交叉验证）
- **误报排除**: 不把"间接覆盖"算作保护——CEP 的 TestCepCheckpointRestoreE2E 全程 event-time（grep PT 关键词 0 命中），不能防 CEP-7 回退；矩阵钉定测试只锁描述符不锁守卫。
- **复核状态**: 未复核

### [REG-13] KR-7 恢复后 JoinSideRecord.payload 退化为 LinkedHashMap（已披露边界，下游类型化消费面未量化）
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/buffer/PerKeyOrderedBuffer.java:196-207（toSerializableForm valueClassName 取 JoinSideRecord 类）`、`nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/common/buffer/TestPerKeyOrderedBufferForm.java:71-75（边界声明）`
- **证据片段**:
  ```java
  // 测试内文档化边界（TestPerKeyOrderedBufferForm:71-75）：
  // Boundary (documented): the nested generic payload stays in its
  // JSON-native shape — the buffer layer can only re-hydrate to the
  // declared value class, and T is erased. Join behavior reads
  // equiKey/left/matched (all re-hydrated); payload typing is the
  // user's keyed-state concern.
  ```
- **严重程度**: P3
- **现状**: form 只带 `valueClassName=JoinSideRecord`，内层泛型 payload 因擦除不水合：checkpoint 恢复后继续运行的 join，其 emitMatches 产出的 `JoinMatch` 载荷侧为 LinkedHashMap（活路径为用户 POJO）。下游 ProcessFunction/窗口对 payload 做类型化读取即 CCE。keyed 镜像路径（bufferState MapState）修复前即有同样退化，两者现在一致；测试对边界有诚实声明。
- **风险**: 带恢复的 join 作业在重启后输出类型漂移；"payload typing is the user's concern"的说法对 join 算子自身产出（非用户 keyed state）并不准确。
- **建议**: JoinSideRecord 增加 `payloadClassName` 字段随 form 携带，fromSerializableForm/rehydrate 链路对 payload 做二段水合；或 owner doc 明确"恢复后 join 输出 payload 为 JSON 原生形态"契约。
- **信心水平**: 确定（边界行为有钉子实证；下游后果为类型系统直接推演）
- **误报排除**: 不与 REG-08 混淆——REG-08 是快照写入侧活引用（撕裂），本条是恢复侧再水合深度（类型保真），分属写/读两侧不同缺陷族。
- **复核状态**: 未复核

## 深挖第 2 轮追加

- **REG-03（SEC-01 前缀族旁路）**、**REG-05（守卫五键清单漏 process-internal-timers）**、**REG-09（setKeyType 仅 Memory）**、**REG-10（第三建图入口）**、**REG-11（CEP-2 判定面错位）**、**REG-12（零钉批次量化）**、**REG-13（payload 边界下游面）**均为第 2 轮基于第 1 轮文件/模式的族入口与对称面排查产出（计时器家族全量枚举：registerEventTimeTimer/registerProcessingTimeTimer 生产调用方仅 ProcessOperator/WindowOperator/CepOperator 三处，ST-11 族覆盖除 REG-05/REG-09 两面外闭合；setOffsets 生产调用方唯一且为全量恢复图，CON-03 REPLACE 语义成立；FLOW-2 的 `incoming.get(e.getTo())` 无 NPE——validateDag :485/:491 先行拒绝悬空引用）。
- 除上述外，深挖第 2 轮未发现新的高价值问题。

## 治理批次"行为保持"声明抽查

- **970f45a0ba（WI11 UpsertDialect 迁移 nop-dao）**：抽查 4 项，全部成立——
  1. 私有 `UpsertDialect` 枚举删除：`grep -rn "UpsertDialect" --include="*.java|*.xml"` 全仓（排除 ai-dev/注释与 target）仅剩 TestJdbcCheckpointStorage:352 的 javadoc 历史注记，0 代码引用；
  2. 三使用点迁移：JdbcCheckpointStorage.java:97/:309/:493（storeCheckPoint/storeSavepoint/storeEpochManifest）均改 `upsertBatch(..., UpsertPolicy.FALLBACK_INSERT_UPDATE)`，可复现 grep：`grep -n "FALLBACK_INSERT_UPDATE" nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/JdbcCheckpointStorage.java`；
  3. `TestJdbcDuplicateKeyDetection` 删除：全仓 0 引用（`grep -rn TestJdbcDuplicateKeyDetection --include="*.java"` 空），断言面迁至 nop-persistence/nop-dao/src/test/java/io/nop/dao/jdbc/TestUpsertStatements.java（存在且含 2601/1451/消息字样不判定用例）；
  4. 重复键判定语义：nop-dao UpsertStatements.java:48（SQLState 23505）/:221-228（vendor 1062/1022、2627/2601 band）为旧判定表超集，javadoc 自述与代码一致。
- **a41a6abfd3（2317 文档批次）**：对 KeyGroupAssignment.java、TwoPhaseCommitSinkFunction.java 两个代码文件的全部 +/- 行做非注释过滤（`grep -E "^[+-]" | grep -vE "^[+-]{3}" | grep -vE "^[+-]\s*(\*|/\*|//)"`）结果为空——纯 javadoc/注释改动，行为保持成立。

## 正面结论（抽查通过，不立案）

- ST-01 核心机制正确：SerDe 每次 snapshotState/restoreState 现场构造（MemoryKeyedStateBackend:635/:640），首键捕获时点可见；头覆盖仅限 `declared == Object.class`（:324），具体类-具体类冲突保留 fail-fast（TestMemoryStateSerDeNumericKeyRestore 钉）；only-data-bean 错误前移至快照期有 javadoc 契约。
- ST-13 的 SourceReaderOperator 浅容器拷贝成立：SimpleSourceSplit/FileSplit 字段全 final（FileSplit 光标推进走新建实例），快照点容器级冻结充分。
- 2312 deferred-to-open 纪律在 EquiJoin/OverWindow 两侧对称落地（restoreState buffer!=null 即时应用 / null 捕获，open() 统一 putAll），engine restore-before-open 与直调 open→restore 双序兼容。
- 2313 RocksDB 便捷构造默认 M 1→128 的行为变更安全：P=1 时键组到 subtask 映射不变；P>1 键控作业旧默认下本就首记录即败；stamped checkpoint 失配走 KR-2 typed 迁移指引，非静默。
- CC-01 的 assignTasks FAILED 门（:842-846）为 pre-existing（pre-image :832-834 可查），commit message 将其计为修复项属表述夸大而非代码缺陷；cancelAllAssignedTasks 无锁遍历建立于 ConcurrentHashMap（:487）之上，与 stop() 既有暴露一致。
- 058ca2fcab CROSS_JOIN：`joinTypeName` 为 nop-stream-sql 内唯一 SqlJoinType switch（grep 核实），5 值显式分支 + 全值 pin，回退必红（真保护）。

## 修复配套测试保护力矩阵

| 修复项 | 提交 | 配套测试 | 变异结论（回退修复→测试红？） |
|---|---|---|---|
| CON-01 矩阵对齐 | 402f108bb0 | TestStreamConnectorRegistryDiscovery.assertDescriptor | 真保护：断言即护栏，工厂/测试任一回退即红 |
| ST-01 点时固化 | 8e028f78ee | TestMemoryStateSerDePointInTimeSnapshot | 真保护（移除 freezeRawValue 拷贝点即红）；List/Map 叶面不在断言面（REG-08 无保护） |
| ST-02 effectiveKey 捕获/头覆盖 | 8e028f78ee | TestObjectDeclaredKeyRestore | 真保护；`warnForeignNativeKeys` 无断言（弱保护） |
| ST-13 算子容器固化 | 8e028f78ee | TestStreamReduceOperator / TestSourceReaderOperatorSnapshotIsolation | 真保护 |
| ST-11 timer 持久化 | 032137dda5 | TestProcessOperatorTimerCheckpointRestore | 真保护（移 snapshotState 覆写→红；移 setKeyType→非 String 用例红） |
| KR-7 缓冲传输 | 032137dda5 | TestPerKeyOrderedBufferForm / TestEquiJoinBufferJsonRoundTrip | 真保护（移 SerDe 双向分支→红） |
| CC-11 seq 推进 | 032137dda5 | testPutAllAdvancesInsertionSeqAgainstCollisions | 真保护但盲区：同 ts 钉不到 lastKey 估计器缺陷（REG-01） |
| ST-03 信封 keyType | 032137dda5 | TestRescaleEnvelopeKeyTypePreserved | 真保护 |
| KR-3/REG-7 建图守卫 | 58895e4e95 | TestKeyedRoutingParityGuard / TestRocksDbDefaultMaxParallelism | 真保护 |
| KR-1 descriptor M | 58895e4e95 | TestKeyGroupMaxParallelismExtraction | 部分保护：抽取器有钉；TM 供给消费 3 行构造性无直接断言（计划已披露 by-construction） |
| KR-2 stamped-M 断言 | 58895e4e95 | TestStampedMaxParallelismConsistency | 真保护（三态：mismatch/match/legacy 跳过） |
| REG-1 subtask-0 身份 | 58895e4e95 | TestRemoteDeploySubtask0Identity（assertNotSame 双 subtask） | 真保护（回退复用原始链→红） |
| FLOW-5 并行度守卫 | 58895e4e95 | TestKeyedStageParallelismGuard | 真保护 |
| CC-01 failJob 终态取消 | e600de4058 | TestFailJobCancelsAssignedTasks | 真保护（cancel 计数/running=false/再部署拒绝） |
| CC-02 激活回滚 | e600de4058 | 无钉（代码路径审计） | **假保护**：无任何测试可检回退；且修复本身不完整（REG-02） |
| CC-06 drainLoop containment | e600de4058 | 无钉 | 无保护 |
| CC-07 sweep containment | e600de4058 | TestGovernanceSweepContainment | 真保护 |
| CON-02 startRequested | c79be63506 | 无钉 | 无保护（REG-04 缺陷不可检） |
| CON-03 REPLACE | c79be63506 | TestNopStreamOffsetBackingStore（幸存键消失断言） | 真保护 |
| CON-04 fsync / CON-05 WARN / CON-08 WARN | c79be63506(回退后) | 无钉 | 无保护 |
| SEC-01 钉定守卫 | c79be63506 | 无钉 | 无保护（REG-03 旁路不可检） |
| REG-3 表名后缀 | c79be63506 | 无命名断言 | 无保护 |
| CON-06 SINGLE_INSTANCE | c79be63506 | TestBatchConnectorFactories / TestStreamConnectorRegistryDiscovery | 真保护 |
| FLOW-2 可达性守卫 | 3a271f8880 | TestSinkReachabilityGuard（2 用例） | 真保护 |
| FLOW-4 三空拒绝 | 3a271f8880 | 无钉 | 无保护 |
| CEP-1 RocksDB 拒绝 | 3a271f8880 | 无钉 | 无保护 |
| CEP-2① 无水线 WARN | 3a271f8880 | 无钉 | 无保护 |
| CEP-7 PT 重挂 | 3a271f8880 | 无钉（TestCepCheckpointRestoreE2E 全 event-time） | 无保护 |
| WIN-2/4 Phase 1 | 3a271f8880 | TestScaleDownPerKeyStateGuard | 部分真保护：仅 internal-timers 一键可检；清单缺 process-internal-timers 不可见（REG-05） |
| CROSS_JOIN 分支 | 058ca2fcab | joinTypeNameCoversAllFiveEnumValuesExplicitly | 真保护（移 case→default 抛错→红） |

外推口径：8 个行为性提交共 29 个修复面，真保护 14、部分保护 4（CC-11/KR-1/WIN/ST-02 WARN 面）、无保护 11（含本报告立案的 7 项零钉批次）。约 62% 强保护，与 R5 全库 70-75% 口径相比，守卫/连接器批次的保护力明显偏弱。

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|---|---|---|---|
| REG-01 | P2 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/buffer/PerKeyOrderedBuffer.java | putAll 以 lastKey()[1] 估最大 seq，乱序事件时间下碰撞窗口重开（CC-11 半失效） |
| REG-02 | P2 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java | CC-02 预锁 active=true 未移除，激活点"移入锁内"半途，半激活窗口仍可达且无钉 |
| REG-03 | P2 | nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/DebeziumEngineConfig.java | SEC-01 仅拒精确键，schema-history/offset 前缀族参数可经 extraProperties 旁路覆盖 |
| REG-04 | P2 | nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/DebeziumEngineWrapper.java | CON-02 startRequested 闩锁失败后不复位，引擎实例一次失败即永久禁用 |
| REG-05 | P2 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/RescaleStateAssembler.java | 缩容守卫五键清单漏 process-internal-timers，ProcessOperator timer 缩容仍静默错置 |
| REG-06 | P3 | nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java | REG-3 表名后缀算法更换无升级迁移说明，存量部署恢复面断层 |
| REG-07 | P2 | nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java | CON-04/08 半回退（已知未修复）：截断提交面无守卫、跨运行碰撞仅 WARN |
| REG-08 | P3 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateSerDe.java | ST-01 残留（已知未修复）：无 serializer 时 List 元素/Map value 叶子仍活引用，Map 侧未登记 |
| REG-09 | P3 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/ProcessOperator.java | ST-11 setKeyType 仅接 Memory 后端，RocksDB 形态恢复 timer 键恒等退化 |
| REG-10 | P3 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java | M-parity 守卫第三建图入口旁路（当前不可达漂移形态，watch） |
| REG-11 | P3 | nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java | CEP-2① WARN 扫描全模型而非 CEP 上游，假阴性使守卫对目标场景失明 |
| REG-12 | P2 | （跨文件，见条目） | 7 项行为性修复（CEP-1/CEP-7/FLOW-4/SEC-01/CON-02/REG-3/CON-04fsync）零配套钉子，回退不可检 |
| REG-13 | P3 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/buffer/PerKeyOrderedBuffer.java | KR-7 恢复后 payload 退化为 LinkedHashMap（已披露边界），下游类型化消费 CCE 面未量化 |

严重程度分布：P2 × 7（REG-01/02/03/04/05/07/12），P3 × 6（REG-06/08/09/10/11/13），无 P0/P1。

## 维度复核结论

> 复核日期：2026-10-06。复核员与初审者无关，独立复核。
> 复核方法：对 REG-01..13 逐条亲自读取被引文件相关行号范围（Read 全文/区段），核对证据片段真实性；对 REG-01..05、REG-07、REG-12（P2 及以上）从代码独立推导问题成立性（不限于核对引用）；用 `git show`（032137dda5 / e600de4058 / c79be63506 / 3a271f8880）核对修复前像与回退 hunk；用 grep 独立重跑 REG-12 的全仓测试面探针；以 R5 summary.md 为唯一历史记录做现状对照。未运行构建/测试。
> 证据核验总说明：初审引用的代码片段全部与 live code 一致，无断章取义；部分行号存在小幅漂移（初审多用提交时行号，HEAD 因后续提交偏移数行），均不影响定位与结论——已逐条在复核要点中标注实际行号。

| 发现 | 判定 | 现状归属 | 复核要点（证据核实结果/降级或驳回理由） |
|---|---|---|---|
| REG-01 | 保留 | R5 已修复的新残余（2312 CC-11 同步修复自身携带，R5 summary 曾明确预警"修 KR-7 族必须同步修 putAll"） | 比较器 TsSeqComparator:50-53 确认 ts 主键在前；putAll:62-74 确认用 lastKey()[1]（ts 最大者的 seq，非全局最大 seq）；fromSerializableForm:160-163 确认恢复侧已精确重建 other.insertionSeq 却被 putAll 弃用；可达性确认：EquiJoinOperator.open:131-135 每次 open 新建空 buffer（insertionSeq=0）后 putAll(restored)。数值例复核：add(ts=10)→seq0、add(ts=5)→seq1 后 lastKey=(10,0)，insertionSeq 仅推进到 1（应为 2），下次 add(ts=5) 分配 seq=1 与 (5,1) 撞键，TreeMap.put 静默覆盖——初审"真实最大 seq=2"措辞不精确（真实最大 seq=1，应推进到 2），碰撞结论不受影响。行号漂移：实际 :62-74（初审 :60-73）。 |
| REG-02 | 保留 | R5 已修复的新残余（2314 CC-02 修复半途） | 双置位确认：预锁 active=true 在 :2126，锁内 active=true 在 :2141（e600de4058 hunk 证实锁内置位为 2314 新增、预锁行为 pre-image 保留）；回滚 :2161 仅置 active=false。竞态独立推导成立：assignTasks 的 active 门在 :837（锁外），recoveryLock.lock() 在 :862，锁内 prepareAssignmentsLocked:864 无 active 复查——T2 过门后阻塞在锁上，T1 回滚后 T2 照常签发。删除预锁置位功能等价性确认：锁内 :2141 先于 rotateFencingEpochCoreLocked/prepareAssignmentsLocked 执行。两处注释理由互斥（:2124-2125 vs :2137-2140），半途修改判定成立。 |
| REG-03 | 保留 | R5 已修复的新残余（2315 SEC-01 覆盖面缺口） | 守卫 :148-154 确认仅 containsKey 精确匹配 "connector.class"/"schema.history.internal"；applySchemaHistory :182-202 确认装配前缀族键（jdbc.url/.jdbc.user/.jdbc.password/.jdbc.table.name/.file.filename）；extraProperties 合并在 :159-161（最后、Properties last-write-wins）确认可覆盖。`schema.history.internal.jdbc.url` 等 Debezium 官方键真实存在（本文件自写）；同文件对 offset.storage（:128-133）与 record.processing.threads（:135-141）已确立"信任域内仍钉死"先例，本守卫自称同一纪律（:143-147 注释）却只闭合类型选择面——旁路成立。行号漂移：守卫实际 :148-154（初审 :144-163 区间内）、offset.file.filename 实际 :95-96（初审 :98-99）。 |
| REG-04 | **降级 P2→P3** | R5 已修复的新残余（2315 CON-02 修复引入） | 代码缺陷成立：start 门控 :136 含 startRequested，catch :175-179 不复位；stop :200 也不清 startRequested——同一 wrapper 实例失败后永久拒绝重试，且为相对修复前的行为退化（修复前门控在 running 上）。**降级理由（生产可达性复核）**：全仓唯一生产调用方 DebeziumMessageSource.startEngineIfNeeded:137-153 每次 attempt `new DebeziumEngineWrapper(...)` 且仅成功才置 started=true——start 失败后 started=false，下次 subscribe 构造全新 wrapper，中毒实例被丢弃；DebeziumCdcSourceFunction.run:258 每次 run 也新建 DebeziumMessageSource。故"引擎永久沉默/实例永久不可用"在现有生产接线上无活路径，缺陷仅限 wrapper API 契约（一次性实例化语义），无生产 caller 做同实例 in-JVM 重试。初审自认"重试可达性取决于宿主是否复用实例"——复核裁定该不确定性已可闭合（不复用），降为 P3；修复建议（catch 复位）仍然有效。 |
| REG-05 | 保留 | R5 已修复的新残余（2316 WIN Phase 1 守卫漏 2312 新增键） | 五键清单 :137-141 确认；TimerStateKeys.PROCESS_INTERNAL_TIMERS="process-internal-timers" 实际在 operators/TimerStateKeys.java:26（初审写 :25，漂移 1 行）；ProcessOperator.snapshotState:122 确认以 operator state 落盘、内容为 per-key 的 TimerSnapshot（与 window internal-timers 同构）；缩容路径 :146-172 只走五键清单、扩容路径 :178-192 才全量拒绝——非对称面确认。javadoc :120-121 "Scale-down never trips this guard" 与 :146-172 的缩容拒绝直接矛盾（陈旧注释）确认。 |
| REG-06 | 保留 | R5 已修复的新残余（2315 REG-3 无迁移说明） | git pre-image（c79be63506）确认 `base_lastSegment(instance)` → `base_sanitizeIdentifier(instance)` 的算法更换（"cdcA-2"→"2" vs "cdcA_2"）；cdc-cookbook 无迁移注记确认（仅 :33 的 Debezium 大版本升级语义，与本改名无关）。**证据修正（不影响立案）**：qualifySchemaHistoryPerInstance 仅在 totalParallelism > 1 时调用（:685-698），单实例（P=1）部署前后均无后缀、不受波及——初审"单实例部署也被波及"不成立，受害面精确为 B-layer 多实例部署（恰为原碰撞缺陷的同一部署形态）；另 cookbook :33 已确立"升级=重快照、键控 upsert 幂等兜底"的平台升级姿态，进一步支撑维持 P3 而非升格。 |
| REG-07 | 保留 | 已知未修复（R5 CON-04/CON-08；2315 addendum + 2316 Deferred 诚实登记） | 回退 hunk（3a271f8880）逐行核对：runNamespace 键折叠与 recordCount 行数自检整体删除、fsync 前移保留；live 状态确认：saveState:248 有 forcePath、doCommitLocked:284-344 无行数自检、guard 命中仅 WARN+继续/修复（:295-315）、manifestKey 仍为 (subtaskIndex, epochId) 形状（:584-588）。与登记文本一致，属任务书第 2 条口径的"上轮已立案、本轮确认仍在"。 |
| REG-08 | 保留 | 已知未修复（R5 ST-01 follow-up；List 面已登记，Map value 叶面为补充登记主张） | 代码链独立核实：serializeWithSerializer null 分支 :823-828 原样返回活引用（注释自认）；writeListPayload:230-237 列表元素经此路径；writeMapPayload:247 键已 defensiveCopy、:254-255 值经同路径后 ContainerValueCodec.encode——encode :121 对非容器叶子 `return value` 原样透传（:92/:113 递归也不拷贝）。叶级活引用成立。Map 面未登记的主张因计划文本不在本次允许阅读范围而未能独立核实，标注为待查但不影响立案（R5 summary 仅澄清"容器级有拷贝保护"，与叶级残留不矛盾）。 |
| REG-09 | 保留 | R5 已修复的新残余（2312 ST-11 setKeyType 接线） | ProcessOperator:79-83 确认 instanceof MemoryKeyedStateBackend 才 setKeyType；HeapInternalTimerService:320 确认 keyType==null（或 Object.class）时 rematerialize 恒等返回；onEventTime/onProcessingTime:146-155 直接 setCurrentKey(timer.getKey())——RocksDB 形态下 bean/Date 键恢复后 keyed 访问 miss 的推导成立。"修复前完全不持久化 timer，属能力残缺而非回归"的定界诚实，P3 恰当。 |
| REG-10 | 保留 | R5 已修复的新残余（2313 KR-3 守卫的入口覆盖面） | grep 全量核对：validateKeyedRoutingParity 仅 StreamExecutionEnvironment:482（compilePlans）/:780（buildJobGraph）两处；buildJobGraphFromStreamModel（GraphModelCheckpointExecutor:279-294）无守卫，被 executeWithCheckpoint 的两个 StreamModel 重载（:142/:161）使用；env.execute 确经 compilePlans（:384）、savepoint 三入口确走 buildJobGraph（:613/:628/:645）——当前 env 驱动流全覆盖、旁路仅对程序化直调开放确认。DSL 无 backend 声明独立核实（StreamModelDslBuilder 与 flow xdef 均 0 命中 stateBackend），"当前不可达漂移形态"成立，watch 级 P3 恰当。 |
| REG-11 | 保留 | R5 已修复的新残余（2316 CEP-2① WARN 判定面错位） | :759-765 确认 anyMatch 扫描 owner.model() 全部 transforms 而非 CEP 节点上游可达集（buildCep:752 手头就有 upstreamIds 参数，修复可行）；假阴性（无关分支放 T&W 节点即全网沉默）成立。假阳性半边也成立：CepOperator 存在 processing-time 模式（:884-888 PT 模式检查、CEP-7 的 PT 重挂），WARN 消息硬编码 "runs in event-time mode"——PT 模式 CEP 无 T&W 节点时会收到语义错误的 WARN。 |
| REG-12 | 保留 | 新发现（R5 后修复批次的测试保护力缺口） | 独立重跑全部 grep 探针，结果与初审一致：CEP-1（RocksDB 拒绝断言 0 命中）、FLOW-4（ERR_STREAM_REQUIRED_BODY 在 flow 测试 0 命中，TestStreamModelDslBuilderFailFast 仅 javadoc 提及 timestampsAndWatermarks）、SEC-01（extraProperties 0 命中）、CON-02（startRequested/START_FAILED 0 命中）、REG-3 命名（sanitizeIdentifier/qualifySchemaHistory/nop_cdc_schema_history 0 命中）、CON-04 fsync（forcePath/fsync 0 命中）。CEP-7：无任何 cep 测试引用 registerProcessingTimeTimer，TestCepOperatorProcessingTimeBucketDrain 无 restore/snapshot 引用。**证据精化**：初审"TestCepCheckpointRestoreE2E grep PT 关键词 0 命中"不准确——该文件有 12 处 ProcessingTimeService 引用，但均为 mock PTS 基础设施注入（事件时模式也要求 PTS 实例），不构成 PT 模式 CEP 语义覆盖，"不能防 CEP-7 回退"的实质结论维持。FLOW-2/WIN 钉子存在（TestSinkReachabilityGuard/TestScaleDownPerKeyStateGuard）反证"2316 仅两项落钉"叙述准确。 |
| REG-13 | 保留 | R5 已修复的新残余（2312 KR-7 已披露边界） | 测试边界声明 TestPerKeyOrderedBufferForm:71-75 逐字核实；toSerializableForm:94-99 仅记 valueClassName=JoinSideRecord、无 payloadClassName 确认；下游消费面确认：EquiJoinOperator.matchOf:319 `new JoinMatch<>((L) left.getPayload(), (R) right.getPayload())`——恢复后 payload 为 LinkedHashMap，下游对 JoinMatch 类型化读取 CCE 推导成立。与 REG-08 的写/读侧定界正确。P3 恰当（边界有测试文档化、keyed 镜像路径修复前即同样退化）。 |

### P2 及以上重点复核详情

**REG-01（保留 P2，独立推导）**
比较器语义：`TsSeqComparator.compare`（:50-53）先 `Long.compare(a[0], b[0])`（ts）后比 seq，故 `TreeMap.lastKey()` 返回 ts 最大的条目；其 seq 分量无全局下界保证。乱序写入序：`add` 按 `insertionSeq++` 单调分配（:198），与 ts 无关——同一 key 先 add(ts=10) 得 seq0、再 add(ts=5) 得 seq1，树中 lastKey=(10,0)，`lastKey()[1]=0`，`insertionSeq` 推进到 1 而真值应为 2。恢复路径可达性：`fromSerializableForm` 在 :160-163 逐 item `Math.max(maxItemSeq, seq)` 精确重建 `insertionSeq`，但 EquiJoinOperator.open:131-135 是新建空 buffer 后 `buffer.putAll(restored)`，putAll（:62-74）弃 `other.insertionSeq` 而用有缺陷的 lastKey 估计。撞键后果：恢复后首个 add(ts=5) 分配 seq=1，`tree.put((5,1), v)` 按比较器与合并条目 (5,1) 相等 → 静默覆盖工作视图条目，且 keyed 镜像以 add 返回的 (ts,seq) 为后缀同步覆盖——join/over-window 结果错误且零日志。**裁定**：CC-11 缺陷族的核心场景（restore + 乱序事件时间，即该 buffer 存在的意义）被修复实现重新打开，且 R5 summary 曾明文预警此同步修复点；维持 P2（与 R5 对 CC-11 的 P2 裁定一致：需 restore 与迟到数据交汇，非默认配置即触发）。修复建议（一行改 `other.insertionSeq`）经推导确认充分。

**REG-02（保留 P2，独立推导）**
锁语义推导：`activateAsLeader` :2126 在 recoveryLock 外置 `active=true`；并发线程 T2 调 `assignTasks()`，:837 的 `!active` 门在锁外，若 T2 在 T1 获取锁前读过 active=true，则 T2 阻塞于 :862；T1 rebuild 失败走 :2156-2162 置 active=false 并 rethrow；T2 获锁后 :864 `prepareAssignmentsLocked` 无任何 active 复查，照常物化 assignment 并在 :871 执行 fan-out——半激活状态（rebuild 失败、checkpoint 视图未重建）下签发一轮部署。功能等价性：锁内 :2141 的置位先于 :2145 rotate 与 :2153 prepareAssignmentsLocked，删除 :2124-2126 不影响 prepareAssignmentsLocked 观察 active——预锁置位无功能必要，仅剩危害。**裁定**：修复声明"激活点移入锁内"与 live code 不符（移入未移除），缺陷窗口从 CC-02 本体的"永久半激活"缩为"单次 dispatch 签发"，且同 epoch 重试活性已达成；维持 P2（并发签发错误部署 + 2314 Anti-Hollow 声明与代码不符的 fix-of-fix 性质）。

**REG-03（保留 P2，独立推导）**
合并时序推导：buildProperties 的装配顺序为 connector.class(:53) → offset 三分支(:89-101) → applySchemaHistory(:104，写前缀族键) → WI7/threads 守卫(:128-141) → SEC-01 精确键守卫(:148-154) → extraProperties 全量合并(:159-161)。`Properties.setProperty` 语义为哈希覆盖（last-write-wins），故 extraProperties 中任何与已装配键同名的键必然获胜。守卫仅 `containsKey("schema.history.internal")` 精确匹配，而真实装配面是五枚前缀键（:183/:185/:188/:191/:202）——传 `extraProperties={"schema.history.internal.jdbc.url":"jdbc:mysql://attacker/..."}` 即完成 history 重定向，无任何告警。同文件先例口径（offset.storage 精确键守卫 + record.processing.threads 值守卫）证明"部署者信任域内仍钉死"是本类自设纪律，SEC-01 注释自宣同一纪律却只闭合类型面。**裁定**：维持 P2——这不是新增权限（部署者本可配 database.user/password），而是对已声明不变量（schemaHistoryStore 装配由 nop 管理、无静默改道）的静默击穿，且守卫的存在使后续开发者不会再加防护；受害场景（恢复期 DDL 上下文改道/指向他人表）与原 SEC-01 缺陷族同severity。

**REG-04（降级 P2→P3，独立推导）**
缺陷本体推导：:136 门控 `running.get() || startRequested.get()`，:143 置 startRequested=true，:175-179 catch 抛 NopException 未复位；stop :200 门控 `!startRequested.get()` 在失败后为真（可执行）但仅置 stopped，不清 startRequested——同一实例后续 start 恒被 :137 WARN 吞掉。相对退化确认：修复前门控仅在 running 上，构建失败后 running=false 可重试。**可达性复核（降级依据）**：枚举全仓生产 caller，`new DebeziumEngineWrapper` 仅 DebeziumMessageSource:143 一处；`startEngineIfNeeded` 以 `started` 标志门控且仅成功置位（:145），start 抛出后 started 保持 false，下次调用 :143 重新 `new DebeziumEngineWrapper`——中毒 wrapper 被整体替换，异常沿 subscribe→run 传播触发任务级 failover（CON-03 的 R5-CON-02 路径），故障仍可自愈。上游 DebeziumCdcSourceFunction.run:258 每次 run 新建 DebeziumMessageSource，region restart 复用 source function 实例（:230 注释）但 rebuild 消息源。**裁定**：代码级契约退化成立（wrapper 变一次性实例），但"引擎永久沉默"无生产活路径，实际后果收敛为"失败 wrapper 实例被丢弃、由任务重启自愈"；按后果定级降为 P3，修复建议（catch 复位）维持。

**REG-05（保留 P2，独立推导）**
状态分类推导：ProcessOperator.snapshotState:122 将 `PROCESS_INTERNAL_TIMERS`（TimerStateKeys:26）作为 **operator state** 落盘，其值为 HeapInternalTimerService 的 per-key TimerSnapshot——与 WindowOperator 的 "internal-timers"、CepOperator 的 "cep-event-time-timers" 同构（本轮另行确认 timer 家族生产调用方仅 Process/Window/Cep 三处）。rescale 语义：keyed 状态按 KeyGroupRange 重分布，operator state 缩容按 index 1:1 保留——清单内五键会被 :146-172 typed 拒绝，`process-internal-timers` 不在清单则 1:1 拷贝照常进行，搬走的 key 其 timer 留守旧 subtask，onTimer（:146-155）setCurrentKey 后 keyed 访问 miss。扩容路径 :178-192 的全量拒绝反证缩容路径确为清单枚举。**裁定**：守卫族成员遗漏使 WIN-2/4 在 process 算子上保持可达，维持 P2（缩容是运维常态操作、后果为静默错置，但守卫主体已封死 window/CEP/reduce 四键，残余面窄于原 WIN-2/4 的 P1）。附带确认 javadoc :120-121 陈旧矛盾。

**REG-07（保留 P2，已知未修复确认）**
回退 hunk 逐行核对（3a271f8880 对 FileTwoPhaseCommitSink 的 57 行删除）：runNamespace/copyForSubtask(TaskLocation)/runKeyComponent/finalPathNamespaced/recordCount 行数自检全部删除；live code 确认 saveState:248 与 doCommitLocked:322 双 forcePath 保留、guard 命中仅 WARN（:298/:309）后继续或修复 manifest、manifestKey 仍为 (subtaskIndex, epochId)（:584-588）。2315 addendum/2316 Deferred 登记与 live 状态一致。**裁定**：按任务书口径作为"上轮已立案、本轮确认仍在"保留，P2 与 R5 对 CON-04/CON-08 原裁定一致；风险表述核实——fsync 不防短写、WARN 不拒绝，exactly-once 名义下的截断提交与跨运行吞写残余成立（successor：身份传递计划）。

**REG-12（保留 P2，探针独立重跑）**
全部 grep 模式独立重跑，7 项零钉逐一确认（详见上表复核要点）；两项精化：(a) TestCepCheckpointRestoreE2E 存在 12 处 ProcessingTimeService 引用但均为 mock PTS 注入（事件时模式的基础设施要求，CepOperator:884-888），不构成 PT 模式覆盖——"CEP-7 无钉"实质成立，初审括号内"grep PT 关键词 0 命中"表述应修正为"无 PT 模式 CEP timer 语义覆盖"；(b) TestStreamModelDslBuilderFailFast 对 timestampsAndWatermarks 仅有 javadoc 提及，无三空拒绝断言。FLOW-2/WIN 钉子存在核实无误，"2316 仅 2 项落钉"的量化基准可信。**裁定**：29 面中 11 面无保护、守卫类小 hunk 最易被无感回退，维持 P2（治理面缺口，非行为缺陷）。

## 最终保留项（复核后）

| 编号 | 严重程度（复核后） | 文件路径 | 一句话摘要 | 现状归属 |
|---|---|---|---|---|
| REG-01 | P2 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/buffer/PerKeyOrderedBuffer.java | putAll 以 lastKey()[1] 估最大 seq，乱序事件时间下碰撞窗口重开（CC-11 半失效） | R5 已修复的新残余（2312） |
| REG-02 | P2 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java | CC-02 预锁 active=true 未移除，激活点"移入锁内"半途，半激活窗口仍可达且无钉 | R5 已修复的新残余（2314） |
| REG-03 | P2 | nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/DebeziumEngineConfig.java | SEC-01 仅拒精确键，schema-history/offset 前缀族参数可经 extraProperties 旁路覆盖 | R5 已修复的新残余（2315） |
| REG-04 | **P3（降级）** | nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/DebeziumEngineWrapper.java | CON-02 startRequested 闩锁失败后不复位，wrapper 实例一次性化（生产接线每次新建 wrapper，无活路径） | R5 已修复的新残余（2315） |
| REG-05 | P2 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/RescaleStateAssembler.java | 缩容守卫五键清单漏 process-internal-timers，ProcessOperator timer 缩容仍静默错置 | R5 已修复的新残余（2316×2312） |
| REG-06 | P3 | nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java | REG-3 表名后缀算法更换无升级迁移说明（受害面为 B-layer 多实例部署，单实例不受影响） | R5 已修复的新残余（2315） |
| REG-07 | P2 | nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java | CON-04/08 半回退（已知未修复）：截断提交面无守卫、跨运行碰撞仅 WARN | 已知未修复（R5 CON-04/CON-08） |
| REG-08 | P3 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateSerDe.java | ST-01 残留（已知未修复）：无 serializer 时 List 元素/Map value 叶子仍活引用 | 已知未修复（R5 ST-01 follow-up） |
| REG-09 | P3 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/ProcessOperator.java | ST-11 setKeyType 仅接 Memory 后端，RocksDB 形态恢复 timer 键恒等退化 | R5 已修复的新残余（2312） |
| REG-10 | P3 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java | M-parity 守卫第三建图入口旁路（当前不可达漂移形态，watch） | R5 已修复的新残余（2313） |
| REG-11 | P3 | nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java | CEP-2① WARN 扫描全模型而非 CEP 上游，假阴性使守卫对目标场景失明 | R5 已修复的新残余（2316） |
| REG-12 | P2 | （跨文件，见条目） | 7 项行为性修复（CEP-1/CEP-7/FLOW-4/SEC-01/CON-02/REG-3/CON-04fsync）零配套钉子，回退不可检 | 新发现 |
| REG-13 | P3 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/buffer/PerKeyOrderedBuffer.java | KR-7 恢复后 payload 退化为 LinkedHashMap（已披露边界），下游类型化消费 CCE 面未量化 | R5 已修复的新残余（2312） |

复核后严重程度分布：P2 × 6（REG-01/02/03/05/07/12），P3 × 7（REG-04/06/08/09/10/11/13），无 P0/P1。
复核统计：保留 11、降级 1（REG-04 P2→P3）、驳回 0。
