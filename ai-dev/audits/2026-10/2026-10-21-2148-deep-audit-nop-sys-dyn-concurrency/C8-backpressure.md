# C8 背压、流控与过载行为 — 初审（第 1 轮）

- 生产-消费边界盘点：①生产者 `send/sendAsync/sendMultiAsync` → sys_event DB 表（唯一缓冲，容量=DB 空间）；②消费 `scheduleWithFixedDelay(500ms) → NonBroadcastEventProcessor.process（fetchSize*4 批 × maxScanLoops=1000 轮，串行单线程）`；③广播 `BroadcastEventProcessor（fetchSize 批 × 1000 轮）→ 逐订阅者同步分发`；④清理任务独立周期；⑤`backlog()` 探针（WI6 producer-side backpressure，:233-247）。

## 生产-消费边界清单

| 边界 | 缓冲 | 满行为 | 判定 |
|------|------|--------|------|
| send → DB 表 | DB（无应用侧队列） | DB 容量（由 retention 清理兜底） | 合理——DB 即天然有界缓冲+背压（插入失败即拒绝） |
| 表 → 轮询消费 | fetchSize*4×1000 上界 | 处理慢则积压，fixedDelay 防重叠 | 有界；消费速率无自适应（watch） |
| 分发 → 消费者 | 同步直调（无队列） | invokeConsumer 阻塞等待 | **无超时等待点 → C8-001（F5）** |
| 广播 → 订阅者 | 同步直调 | 单订阅者慢拖住整批扫描 | C8-002 |
| cleanup deleteByQuery | 无 LIMIT | 一次删除全保留期行 | watch（DB 压力面） |

## 发现条目

### [C8-001] invokeConsumer 以 FutureHelper.syncGet 无超时阻塞轮询线程：消费者 future 永不完成则事件管线永久停摆（F5 定向命中）

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/message/SysDaoMessageService.java:538-544`（对照周期入口 ：217-222 scheduleWithFixedDelay、执行器 globalWorker :214-215）
- **证据片段**:
  ```java
  protected Object invokeConsumer(IMessageConsumer consumer, String topic, Object message, ...) {
      DurableConsumeContext context = new DurableConsumeContext();
      Object ret = consumer.onMessage(topic, message, context);
      if (ret instanceof CompletionStage) {
          ret = FutureHelper.syncGet((CompletionStage<Object>) ret);   // 无超时阻塞
      }
  ```
- **严重程度**: P2（F5：静默挂死对健康检测不可见；触发前提是消费方返回未完成 future）
- **并发场景**: 轮询线程（timer.executeOn(globalWorker) 的 fixedDelay 任务）同步调用业务消费者；消费者返回异步 CompletionStage 但其内部链异常断裂/依赖的外部服务挂起
- **触发时序**: T1（轮询线程）在 claim 后对事件 E 调用 invokeConsumer → 消费者返回未完成 future → syncGet 永久阻塞 → **scheduleWithFixedDelay 语义下后续轮询永不开始（任务未返回）** → 全部 topic 的事件消费停摆、租约过期后事件可被他节点重复认领（若为多节点则表现为反复跨节点认领再挂死）→ 无异常无日志（syncGet 静默等待）→ 健康检测/心跳对此不可见（F5 定向形态：“不抛异常也不产出”）。
- **现状**: 等待点无超时是本模块唯一一处无界等待（orTimeout/ThreadLocal/BlockingQueue 全模块零命中，R1 基线一致）；backpressure probe（:240-247）只测 DB 积压，测不出“消费线程挂死”这种积压恒定形态的恶化。
- **风险**: 单个劣质消费者拖死平台全部事件驱动功能，且无告警面；多节点下形成“认领-挂死-租约过期-再认领”的螨虫循环。
- **建议**: syncGet 换带超时的等待（如 `FutureHelper.tryGet(future, leaseTimeout)`），超时按消费失败走 retryDelayProvider 路径；或为轮询任务加 stall 看门狗（最近一次 process 完成时间戳 + 周期对比告警）。
- **失效面枚举**: 消费分发点全集：`invokeConsumer` :538-559（本条，被 invokeDurableConsumers :535 与 dispatchBroadcastToSubscribers :400 两个入口共用——一处修复覆盖两入口）；广播分发同样经 ：400→:543 阻塞点。同模块其他等待点：SysDaoResourceLockManager backoffBeforeRetry :186-193（sleep 100ms 有界）✅、tryLockWithLease 循环受 waitTime :129-133 约束 ✅。
- **信心水平**: 确定（等待点无超时是代码事实）；触发依赖消费方行为故 P2
- **误报排除**: 非已声明边界——无任何文档声明“消费者 future 必须在有界时间内完成”的契约与违背后果；F5 是本领域定向检查项而非泛化担忧。
- **复核状态**: 已保留

### [C8-002] 广播分发串行同步：单订阅者慢/挂拖住整个广播扫描循环

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/message/BroadcastEventProcessor.java:54-72`（扫描循环）× `SysDaoMessageService.dispatchBroadcastToSubscribers :388-409`（同步逐订阅者）
- **证据片段**:
  ```java
  for (int i = 0; i < maxScanLoops; i++) {
      ...
      for (NopSysBroadcastEvent event : batch) {
          dispatchCallback.accept(event.getEventTopic(), event);   // 同步分发，异常仅记日志继续
      }
      lastEvent = batch.get(batch.size() - 1);
  }
  ```
- **严重程度**: P3
- **并发场景**: 广播轮询线程逐批逐事件逐订阅者同步调用；某订阅者消费慢（或命中 C8-001 挂死）
- **触发时序**: T1 订阅者 onMessage 耗时 10s → 批内其余事件与后续轮次全部排队 → 广播投递延迟无上界（受 maxScanLoops×batch×订阅者耗时乘积）；单订阅者异常不拖死（:405-407 catch 继续）✅——但“慢”无任何隔离或超时。
- **现状**: 广播语义=尽力而为实时通知，无每订阅者投递预算；backlog probe 不覆盖广播表（仅 sys_event :240-247），广播积压不可观测。
- **风险**: 一个慢订阅者拉长全员广播延迟；无指标暴露。
- **建议**: 每订阅者投递超时预算；广播表纳入 backlog 观测。
- **失效面枚举**: 分发入口：dispatchBroadcastToSubscribers :388-409（本条）、invokeDurableConsumers :509-536（队列语义单投递，慢的后果是消费延迟而非全员放大——不并列）；非广播 process() 串行同型但为声明语义（队列有序性），豁免。
- **信心水平**: 确定
- **误报排除**: 非“有界声明存在”——无投递预算声明；P3 因广播为实时通知语义，积压最终随行清理收敛。
- **复核状态**: 已保留

### [C8-003] sendMultiAsync 逐条 saveMessage 无事务：中途失败形成“半个成功”（F2）

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/message/SysDaoMessageService.java:434-445`
- **证据片段**:
  ```java
  public CompletionStage<Void> sendMultiAsync(Collection<TopicMessage> messages, MessageSendOptions options) {
      MessageOptionsValidator.validateSend(CAPABILITIES, options, "sysdao", WARN_ONCE);
      try {
          for (TopicMessage message : messages) {
              saveMessage(message.getTopic(), message.getMessage(), ...);   // 每条独立直插
          }
      } catch (Exception e) {
          return FutureHelper.reject(e);   // 前 k 条已持久化，调用方只知道整体失败
      }
      return FutureHelper.success(null);
  }
  ```
- **严重程度**: P3
- **并发场景**: 单线程调用（无并发竞态）；风险是多目标写入的失败语义
- **触发时序**: 第 k+1 条 saveMessage 抛 DB 异常 → 前 k 条已入库 → future reject → 调用方重试整批 → 前 k 条消息重复投递（至少一次语义下可接受，但未声明）；若调用方不重试则后段消息丢失且无失败清单可查（哪条失败不可知）。
- **现状**: 无部分成功标记、无逐条结果回传；与 sendAsync 单条语义衔接处未定义批量原子性。
- **风险**: 批量事件的重复或缺失，需调用方自行幂等；文档未声明 at-least-once 契约。
- **建议**: javadoc 声明“逐条独立、至少一次、需幂等消费”；或提供逐条结果列表的变体。
- **失效面枚举**: 批量入口仅 sendMultiAsync 一处；单条 sendAsync :423-432 失败即整条拒绝 ✅；invokeConsumer ack 回路 ：551/:555 单条 send ✅。
- **信心水平**: 确定
- **误报排除**: MQ 批量部分成功是常见可接受语义——按“缺陷 vs 已声明边界”口径，因**未声明**而登记 P3 文档/语义缺口，不按 P1 报。
- **复核状态**: 已保留

### [C8-004] cleanupExpiredEvents 无批量上限：单次 deleteByQuery 删除全保留期命中行

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/message/SysDaoMessageService.java:253-282`
- **证据片段**:
  ```java
  QueryBean eventQuery = new QueryBean();
  eventQuery.addFilter(...PROCESSED/FAILED...).addFilter(...lt eventTime expireBefore);
  dao().deleteByQuery(eventQuery);   // 无 LIMIT：一次删光 7 天内全部到期行
  ```
- **严重程度**: P3
- **并发场景**: 每小时周期任务 × 大吞吐事件流（高峰后首个清理周期）
- **触发时序**: 高峰 7 天后积累数百万到期行 → 单条 DELETE 全量执行 → 长事务/锁面/undo 膨胀，期间与消费扫描（同表）争用。非内存 OOM 面（DB 承载），是 DB 压力脉冲。
- **现状**: 0=禁用与天数可配，但无批次上限配置。
- **风险**: 清理任务自身成为周期性慢查询源（与“防止事件表无限膨胀拖垮轮询扫描”的初衷相反的二次伤害）。
- **建议**: 循环分批删除（每批 LIMIT fetchSize×N）直至影响行数为 0。
- **失效面枚举**: 三个 deleteByQuery :263/:273/:278 同型逐一登记；SysDaoNamingService.cleanup :97 deleteByQuery 同型（实例表量级小，影响轻，登记不立项）。
- **信心水平**: 确定
- **误报排除**: 非“有界声明存在”——无批处理上限；规模判断对照声明包络（事件表自身为高吞吐设计）。
- **复核状态**: 已保留

## 本维度不计入发现的验证结论

- `backlog()`（:239-247）producer-side 背压探针：基于 (eventTopic, eventStatus) 索引的 count，口径与消费扫描过滤一致（WAITING+CLAIMED），声明完备——正面验证通过，无发现。
- 无界输入面：8 根内无 readAllBytes/无 LIMIT findAll（除启动期 SysI18nMessageLoader.findAll 全量 i18n——启动一次、行数=翻译键规模，豁免）。
- 限流器/信号量：本模块零使用（无 acquire/release 泄漏面）。
- `QueueOverflowPolicy`/IBlockingQueue：未使用（DB 承载队列，天然背压）——非“该用未用”，量级合理。

## 维度复核结论（第 4 阶段独立复核）

- 复核方式：4 条逐条回码验证。C8-001 复核 ：538-544 无超时等待点与两处分发入口共用关系（invokeDurableConsumers :535 / dispatchBroadcastToSubscribers :400），并确认全模块 orTimeout/带超时 get 零命中；C8-004 复核三个 deleteByQuery 无 LIMIT。
- 三态判定：已保留 4 / 已降级 0 / 已驳回 0。
- 判级复核：C8-001 维持 P2——等待点无超时为代码事实，但触发前提（消费者返回永不完成的 future）为消费方违约，非平台自身主路径必然；与 R2 深挖 C2-102（租约不续期）存在**放大关系**：C2-102 修复前，C8-001 的挂死会让租约过期成为常态，裁定阶段两单互列关联。
- 现状标注：4 条全部**新发现**；C8-001 的 backpressure probe（:233-247）为前轮 WI6 交付物，其"测不出消费线程挂死"的盲区为本条新识别。

## 最终保留项

| 编号 | 标题 | 严重度 | 现状 | 复核状态 |
|------|------|--------|------|---------|
| C8-001 | invokeConsumer 无超时阻塞轮询线程（F5） | P2 | 新发现（与 C2-102 互为放大） | 已保留 |
| C8-002 | 广播分发串行无投递预算 | P3 | 新发现 | 已保留 |
| C8-003 | sendMultiAsync 半个成功未声明（F2） | P3 | 新发现 | 已保留 |
| C8-004 | cleanupExpiredEvents 无批量上限 | P3 | 新发现 | 已保留 |
