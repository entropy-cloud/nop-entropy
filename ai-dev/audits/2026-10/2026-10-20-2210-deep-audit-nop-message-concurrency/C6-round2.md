# C6 深挖第 2 轮 — nop-message（WI 4.1）

- 审计维度：C6（资源生命周期与泄漏）· 深挖第 2 轮（合并批 C1/C3/C5/C6/C8 之 C6 分卷）
- 日期：2026-10-20
- 盲审声明：同 C1-round2.md。第 1 轮 6 条（C6-001…C6-006）与配对表 14 行为本轮排查基线，全部视为已知，不重复立案。

## 深挖范围与方法

按派发指令对第 1 轮之外的三个定向面复核：

| 定向面 | 复核对象 | 结论 |
|---|---|---|
| Kafka/Pulsar 配置构建期资源（SSL 上下文等） | `KafkaMessageService.buildProducerProps/buildConsumerProps`（仅 Properties 装配，:167-233）、`PulsarMessageService.createClient/doStart:130-159`、`PulsarHelper`（无连接面） | 构建期 0 直接资源获取：SSL/SASL 上下文由 KafkaProducer/KafkaConsumer/PulsarClient 内部创建，随 `doStop` 的 producer.close/client.close 配对释放（配对表 #1/#4 维持）；`PulsarClient` 缺省 IO 线程随 client.close 释放。examined-empty |
| ChannelBacklogProbe 的客户端/AdminClient 生命周期 | `ChannelBacklogProbe.java`（纯接口，模块内 0 实现 0 调用；javadoc 指明实现方在 nop-sys-dao、消费方在 stream runtime） | 本模块不存在任何 probe 客户端可管理，examined-empty（实现方生命周期在集外模块，需该模块自己的审计覆盖） |
| DebeziumEngineConfig 的 schema history 文件句柄 | `DebeziumEngineConfig.applySchemaHistory:184-224`（仅 setProperty 装配 jdbc/file 两模式）、`DebeziumConfig.schemaHistory*` 字段 | 模块自身 **0 文件/连接句柄获取**：FileSchemaHistory/JdbcSchemaHistory 的句柄由 Debezium 引擎在 run() 内创建、随 engine 生命周期释放——但"engine 生命周期"存在第 1 轮未覆盖的缺口面（异常死亡路径无人 close）→ 立案 C6-007 |

全量复扫：模块内全部 `Closeable/AutoCloseable` 获取点、`new *Client/*Producer/*Consumer/*Engine` 点、`Executors.new*` 点与第 1 轮配对表逐一重对，未发现表外获取点；`DebeziumEngineWrapper.start` 的 RejectedExecutionException 窗口（engine 已 build 未 close、startRequested 已置位）为 C6-005(d) 已立案面，不重复。

## 新增发现

### [C6-007] DebeziumEngineWrapper 的 close 只存在于 stop() 路径——引擎异常终局死亡后 engine 对象永不 close，DebeziumEngine 的 AutoCloseable 契约在死亡路径缺位

- **文件**: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/DebeziumEngineWrapper.java:159-180`（worker lambda：run() 抛出 → 仅日志 + notifyEngineFailure，无 engine.close()）、`:195-215`（close 唯一存在点：stop()）；`nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/DebeziumMessageSource.java:137-153`（startEngineIfNeeded 不注册 failure listener、不 stop）、`:168-180`（stop 仅经用户显式调用触达）
- **证据片段**:
  ```java
  // DebeziumEngineWrapper —— worker lambda（:159-173）
  GlobalExecutors.globalWorker().execute(() -> {
      try {
          running.set(true);
          engine.run();                       // 抛出 = 引擎终局死亡
      } catch (Exception e) {
          LOG.error(...);
          notifyEngineFailure(config.getName(), e);   // 通知有，close 无
      } finally { running.set(false); }
  });
  // stop():206-212 —— 全类唯一 engine.close() 调用点
  if (engine != null) { engine.close(); }
  ```
- **严重程度**: P3
- **并发场景**: 引擎 worker 线程（globalWorker）执行 run() 异常死亡 vs 调用方线程是否/何时调用 stop()；`DebeziumMessageSource` 路径下无人注册 failure listener（第 1 轮 CL-011 已立案其可观测缺口），死亡后 source 的 `engineWrapper` 字段仍持死引擎直至下一次 startEngineIfNeeded 重建或显式 stop。
- **触发时序**: T1 引擎 run() 因 broker 不可达/连接器故障抛出 → worker catch :164-168 通知（无监听者则空转）→ finally 复位 running → **方法返回，engine 对象从未 close**；T2 嵌入方（DebeziumMessageSource）`isStarted()` 仍 true、无任何内部路径触发 `stop()`/`engine.close()`——直到外部显式 stop 或同名 source 重新 subscribe（`startEngineIfNeeded:143` 新建 wrapper 顶替字段引用）前，死亡引擎对象的释放完全依赖 Debezium `run()` 内部自清理。`DebeziumEngine` 实现 `AutoCloseable`，官方契约要求 run() 返回后调用 close() 释放引擎级资源（如 FileSchemaHistory 持有的文件通道、类加载器引用）——本模块在正常 stop 路径遵守、在死亡路径缺失。
- **现状**: close 与 run 的配对是"条件配对"：仅当所有者主动 stop 才闭合；死亡路径的通知机制（R5-CON-03）只上报不回收。对照同模块 Kafka/Pulsar 订阅的 cancel 路径（await 后 close，配对表 #2/#6"close-after-termination"），Debezium 侧缺同款"终局后回收"。
- **风险**: run() 内部自清理覆盖大部分资源（worker 线程随 lambda 返回释放、连接器 task 随 run 退出停止），故判 P3 非 P2；残余面是引擎级对象（schema history 句柄、引擎内部 ExecutorService 的边缘形态）依赖版本实现细节，死亡后长期驻留时不可枚举、不可观测（无 close 日志锚点）。
- **建议**: worker lambda 的 catch/finally 中补 `engine.close()`（幂等，close-after-run 是 Debezium 官方推荐形态）；或 notifyEngineFailure 语义扩展为"死亡即回收"——通知监听者后本地执行与 stop() 等价的 close 序列（置 stopped 防重入）。
- **失效面枚举**（F3：引擎/长驻任务终局回收逐一）: ① DebeziumEngineWrapper 死亡路径 —— **未配对（本条）**；② DebeziumEngineWrapper stop 路径 —— close 在位（配对表 #8，除 C6-005 的不 join 面）✓；③ KafkaMessageSubscription.cancel —— task.stop → await → consumer.close ✓（#2/#3）；④ PulsarMessageSubscription.doOnCancel —— 同构 ✓（#6/#7）；⑤ LocalMessageService 调度器 —— 无 close（C6-001 已立案，不重复）；⑥ nop-stream 侧 DebeziumCdcSourceFunction 的 finally 注销/取消路径 —— 集外，登记关联（其收到 notify 后的 stop 调用可间接补 close，但独立使用 DebeziumMessageSource 的路径无此兜底）。
- **信心水平**: 很可能（close 缺位是代码直证；泄漏后果依赖 Debezium 版本内部实现，未实测 2.4.x run() 自清理的完备边界）
- **误报排除**: 非 C6-005 重复——C6-005 立案的是 stop 语义三缺口（不 join/isRunning 过早翻转/一次性），本条是**死亡路径上 close 调用本身不存在**，属 F8 配对缺失的新位点；非平台设施面——engine 生命周期完全由本 wrapper 拥有。
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜现状:新发现（engine.close 全类唯一调用点在 stop :206-212（grep 独立复现）、worker lambda :159-173 catch 仅日志+notifyEngineFailure 无回收、DebeziumMessageSource 零 listener 接线（CL-011/C3-002 面）逐点回码确认；与 C6-005 修复收敛于同一改点（死亡即回收+quiesce 同批）判定成立；「很可能」信心自评恰当）

## 维度集外线索（overlay）

（本轮 C6 分卷无新增集外线索。）

## 继续判定

**终止信号。** 依据：本轮新增仅 C6-007（P3×1，条件配对缺口、后果依赖第三方版本内部），无 P0/P1；三个指令面中配置构建期资源、ChannelBacklogProbe、schema history 文件句柄（模块自身面）均 examined-empty——模块内资源获取点全集已被第 1 轮 14 行配对表覆盖，本轮仅补死亡路径一处配对缺位，且与 C6-005 的修复方向（死亡即回收）收敛于同一改点。第 1 轮 6 条 + 本轮 1 条后，资源生命周期面收敛证据充分，无需第 3 轮。

## 复核结论（WI 4.1 独立复核，2026-10-20）

- **C6-007 复核保留（P3）**：状态行已写于该条目末尾。本轮终止信号判定成立——新增仅 1 条 P3、三指令面 examined-empty，C6 维度两轮覆盖可收口的裁定成立。
- 配对表 14 行全量重对核验通过（关键抽查行：#2/#6 的 close-after-termination 序、#10 REGISTRY 不配对、#11 Local executor 不配对、#12 FAILURE_LISTENERS 配对——与回码一致）。
- 本维度复核全量结论（判定统计/跨单元对照/最终保留项）见 `C6-resource-lifecycle.md`「维度复核结论」节：7 条 = 保留 7（含 C6-005 吸收 C1-011 主登记），零降级零驳回，最终 P1×1/P2×4/P3×2。
