# C8 背压、流控与过载行为 — 深挖第 2 轮

- 方法：本轮核对“声明语义与实现的差值”（队列单投递语义、租约时长与消费时长的配比）与清理任务的过载行为；初审四条不重复。

## 发现条目

### [C8-101] process() 单轮清扫无时间预算：积压时单任务可占用轮询线程分钟级，三大周期任务与全池任务互相饥饿

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/message/NonBroadcastEventProcessor.java:66-83`（maxScanLoops=1000 外循环 × 逐事件串行 process）；执行器共享 `SysDaoMessageService :214-215`（默认 globalWorker）
- **证据片段**:
  ```java
  public void process() {
      Set<Integer> processedPartitions = new HashSet<>();
      for (int i = 0; i < maxScanLoops; i++) {          // 默认 1000 轮
          ...
          for (NopSysEvent event : claimed) {
              process(event);                            // 逐事件同步（含 C8-001 无超时等待点）
              processedPartitions.add(event.getPartitionIndex());
          }
      }
  }
  ```
- **严重程度**: P3
- **并发场景**: 积压清扫（1000 轮 × fetch/claim/逐事件消费）× 同池的 broadcast 轮询、cleanup 周期任务 × globalWorker 上其他平台任务
- **触发时序**: 事件突发积压 → 单轮 process 持续数分钟（1000 轮 × 每事件 DB 往返+消费）→ scheduleWithFixedDelay 语义下 broadcast 轮询（独立 future 但同一 executor）与 cleanup 任务在 globalWorker 排队 → 广播延迟、清理滞后与积压形成正反馈。
- **现状**: 每事件逐 partition 限速（processedPartitions ✅ 公平性设计），但**整轮无时间预算/无让出**；fixedDelay 只防自身重叠，不防跨任务饥饿。
- **风险**: 过载时清理与广播被饿死的窗口不可控；无“清扫让位”指标。
- **建议**: 轮内加时间预算（如单轮 process 上限 = checkInterval×0.5，超时即返回留待下轮）；或清扫与广播隔离 executor。
- **失效面枚举**: 周期任务清单：processNonBroadcastEvent（本条主体）、processBroadcastEvent（扫描循环 ：54-72 同型无预算，同族）、cleanupExpiredEvents（C8-004 已并）——三个周期任务逐一登记。
- **信心水平**: 确定
- **误报排除**: 非声明语义——maxScanLoops 是防死循环上限而非吞吐声明；“处理不过来就慢慢处理”的收敛性成立，故 P3。
- **复核状态**: 已保留

### [C8-102] 队列“单投递”语义与 suspended 订阅跳过之间存在投递饥饿：唯一订阅者长期挂起时事件靠 30 天兜底清理处置

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/message/SysDaoMessageService.java:522-535`（全部挂起→ConsumeLater 回 WAITING）、`NonBroadcastEventProcessor.java:165-171`（ConsumeLater→WAITING+minProcessDelay 重排）；兜底 `:115` cleanupStaleWaitingDays=30
- **证据片段**:
  ```java
  if (target == null) {
      LOG.debug("nop.message.all-durable-subscribers-suspended:topic={}", topic);
      return new ConsumeLater(0);     // debug 级日志 + 回 WAITING
  }
  ```
- **严重程度**: P3
- **并发场景**: 队列 topic 唯一订阅者被 suspend 后长期不 resume；事件持续到达
- **触发时序**: 每轮 process 认领→发现全挂起→ConsumeLater→回 WAITING（retryTimes 每次 +1，无上限判断）→ minProcessDelay(10s) 后再认领——**每 10s 对每条积压事件空转一次认领-回退写**（每事件 2 次 DB 写/10s/条）；topic 积压期间 DB 写放大与 (eventTopic,eventStatus) 热点行更新；30 天后被 stale cleanup 静默删除（语义已声明）。
- **现状**: 挂起语义有意识处理（不丢弃 ✅，初审确认改进）；但“全挂起”态的退避只有固定 minProcessDelay，无挂起态短路（可在 dispatch 前检查 subscription.isSuspended 跳过扫描）。
- **风险**: 长期挂起 + 持续生产 = 稳态 DB 写放大（每条每 10s 两次 UPDATE）；日志仅 debug 不可观测。
- **建议**: 订阅者全挂起时把 topic 移出轮询集合（resume 时恢复），或对 ConsumeLater 使用指数退避。
- **失效面枚举**: 挂起处理点：invokeDurableConsumers :524-534（队列语义，本条）；dispatchBroadcastToSubscribers :396-398（广播挂起跳过——跳过后事件仍按已处理置 PROCESSED？否——广播无状态行更新，事件保留至 7 天清理，语义为“错过即错过”，声明边界豁免）。
- **信心水平**: 确定
- **误报排除**: 非已声明边界——30 天清理声明的是兜底，未声明空转成本；P3 因有界（retryTimes 无上限但写路径有界、清理兜底存在）。
- **复核状态**: 已保留

## 第 2 轮排查后不立项的项

- `fetchCandidates` fetchSize*4 上限 + partition-head 过滤：单轮内存面 O(fetchSize*4) 有界 ✅。
- 广播 startGap=5s：节点停机期间事件不补投——广播实时通知语义（对照其无 ack/无重投设计），按已声明边界豁免，登记为文档改进建议。
- backlog() 每次调用一次 count：probe 调用方为 producer-side 检查，频率受控，有索引支撑 ✅。
- 事件 retryTimes 无上限：默认 RetryPolicy 决定 FAILED 时机，FAILED/WAITING 行均有 7d/30d 清理兜底——有界 ✅（终态依赖 RetryPolicy 配置，kernel 侧不在本审计重审范围）。
