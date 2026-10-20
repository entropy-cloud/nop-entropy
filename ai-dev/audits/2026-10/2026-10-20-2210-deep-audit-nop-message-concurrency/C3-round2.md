# C3 深挖第 2 轮 — nop-message（WI 4.1）

- 审计维度：C3（异步链路正确性）· 深挖第 2 轮（合并批 C1/C3/C5/C6/C8 之 C3 分卷）
- 日期：2026-10-20
- 盲审声明：同 C1-round2.md。本轮另对平台设施 `FutureHelper.java`（nop-api-core，仅审使用方式）核读了 `futureRun:131-138`/`futureCall:122-129`/`syncGet:158-175`/`getResult:283-288`/`waitResult:262-280` 的执行语义。

## 深挖范围与方法

按派发指令对第 1 轮 8 条之外的四个定向面复核：

| 定向面 | 复核对象 | 结论 |
|---|---|---|
| DebeziumEventConverter 转换链的其他异常路径 | `DebeziumEventConverter.java:33-124` 逐行 + `DebeziumEngineWrapper.handleDebeziumEvent:185-190` 屏障 | parseKey CCE / ts_ms 裸转换两个新路径 → **并入「扩展 C3-002」**（与已立案的无屏障根同根，不另编号） |
| Pulsar negativeAcknowledge/reconsumeLater 异步面 | `PulsarConsumeTask.java:190-212,221-317,319-382,397-427,449-466,482-489` | nack 丢失尾部单条路径同族 → **并入「扩展 C3-005」**；ConsumeLater(0) 无预算面重裁 → **并入 C8 分卷「扩展 C8-005」**；null 响应 NPE 面 → **立案 C3-010** |
| Kafka commitAsync 回调 | 全模块 grep `commitAsync` = **0 处**（仅 `commitSync` 5 处：KafkaConsumeTask:421,545,584,725 及其调用链） | 指令面落空，examined-empty（commitSync 吞异常面已由第 1 轮 C3-008 立案） |
| MessageConsumeFailureBridge 的 onConsumeFailure 异步传播 | `MessageConsumeFailureBridge.java:74-114` + 两后端 dispatchConsumeFailure/runFailureEndpoint 调用点 | `submit` 为 fire-and-forget 且声明"辅助通道、永不抛"——传播语义闭合；残留"无等待上界/无 pending 可观测"缺口 → **登记 CL-025**（不立案） |

另发现全模块统一性缺陷（第 1 轮未覆盖的新失效面）：**全部异常屏障只捕获 `Exception`**（grep `catch (Throwable`/`catch (Error` 均 0 处）→ 立案 C3-009。

## 新增发现

### [C3-009] 消费循环与引擎回调的异常屏障只覆盖 Exception——Error 逃逸使订阅僵尸化（isActive 误报）且 Debezium 终局失败通知被绕过

- **文件**: `nop-message/nop-message-kafka/src/main/java/io/nop/message/kafka/KafkaConsumeTask.java:301-317`（runTask 双 catch）；`nop-message/nop-message-pulsar/src/main/java/io/nop/message/pulsar/PulsarConsumeTask.java:107-132`；`nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/DebeziumEngineWrapper.java:159-173`（worker lambda `catch (Exception e)`）；`nop-message/nop-message-core/src/main/java/io/nop/message/core/local/LocalMessageService.java:232-234,253-262`
- **证据片段**:
  ```java
  // KafkaConsumeTask.runTask（Pulsar 同型）
  } catch (WakeupException we) {
      break;
  } catch (Exception e) {          // ← Error 不是 Exception：直接穿出 while 循环
      consecutiveErrors++;
      ...
  }
  // DebeziumEngineWrapper worker lambda
  } catch (Exception e) {
      LOG.error(...);
      notifyEngineFailure(config.getName(), e);   // ← Error 死亡不走此通知
  } finally {
      running.set(false);                          // finally 会跑，但通知不会
  }
  ```
- **严重程度**: P2
- **并发场景**: 单一消费线程（Kafka/Pulsar 每订阅单线程 executor；`executor.execute(this::runTask)` 提交一次、循环体不重提）或 Debezium globalWorker 线程 vs 用户 handler / codec 内抛出的 `Error`（StackOverflowError、OutOfMemoryError、NoClassDefFoundError 均为负载上升下的现实形态）。
- **触发时序**: T1 消费线程执行 `consumerFn.onMessage`（Kafka :355 / Pulsar :332）→ 用户 handler 深递归抛 `StackOverflowError` → :304（Pulsar :118）的 `catch (Exception)` 不匹配 → Error 穿出 runTask → ThreadPoolExecutor worker 线程异常终止（processWorkerExit 会补一条空闲 worker，但 runTask 循环**不会被重新提交**）→ `active` 恒为 true、`isCancelled()` 为 false、无 onConsumeFailure、无 seek、无日志以外的任何痕迹 → T2 健康检查调 `isActive()` 返回 true——订阅成为永久僵尸（F5：挂死对健康检测不可见的"死亡"变体）。Debezium 面：T1' 订阅者闭包抛 Error → `dispatchEvent:159` 的 `catch (Exception)` 不匹配 → 穿出 `handleDebeziumEvent` → `engine.run()` 抛 Error → worker lambda :164 不匹配 → **`notifyEngineFailure` 不触发** → 已注册的 FAILURE_LISTENERS 收不到通知（running 标志仍被 finally 复位），nop-stream 侧 task 永远等不到 latch——R5-CON-03 机制对其设计要防的"终局死亡不可见"形态在 Error 面失守。
- **现状**: 全模块 grep `catch (Throwable`/`catch (Error` = 0 处；Kafka/Pulsar 的有界停止设计（MSG-F-54、wakeup、awaitTermination 预算）全部只覆盖"线程活着但阻塞/循环"的形态，未覆盖"线程已死"的形态；Debezium 面与 C3-002 的 Exception 逃逸同点位，但 C3-002 未涵盖 Error 分支的通知缺口。
- **风险**: 长时运行中一次 Error（OOM 压力、依赖升级缺类）即让一个订阅静默退出消费（多成员组内表现为容量缩水且该成员已 LeaveGroup 后不再回归），Debezium 面表现为 CDC 流死亡但任务 RUNNING——两类都与 C8-001（挂死）症状一致却无法通过 cancel 之外的任何机制自愈。
- **建议**: runTask/dispatchEvent 的屏障改 `catch (Throwable)` 并在分支内区分：Error 记日志后走与 Exception 相同的失败上报路径（dispatchConsumeFailure / notifyEngineFailure）再决定退出；或至少把 `active` 置 false + 类型化 WARN，使 isActive 不再误报。对照同文件 `dispatchConsumeFailure:625-630` 已有的回调守卫形态补齐外层。
- **失效面枚举**（F3：全模块 Exception-only 屏障逐一登记）: ① KafkaConsumeTask.runTask:304 —— 本条主证 ✗；② PulsarConsumeTask.runTask:118 —— 同缺陷 ✗；③ LocalMessageService.invokeSubscription:232 —— Error 穿到发送方线程（同步 send 链），红投调度线程内则任务静默死亡（该条延迟消息丢失无 WARN）✗；④ DebeziumEngineWrapper worker:164 —— Error 死亡无 notifyEngineFailure ✗（本条）；⑤ DebeziumMessageSource.dispatchEvent:159 —— 同 ④ 传导 ✗；⑥ dispatchConsumeFailure/runFailureEndpoint 的 onConsumeFailure 守卫（Kafka :628/:670、Pulsar :420/:463）—— Error 穿出回调守卫杀消费线程 ✗（与 ①② 同轮次放大）；⑦ MessageConsumeFailureBridge.submit:111、notifyEngineFailure listener 守卫 :86 —— 同为 Exception-only ✗（登记）。无一处使用 Throwable，属模块级统一缺口而非孤立点。
- **信心水平**: 确定（屏障形态与线程死亡后果为代码事实；Error 触发源在长时运行负载下现实但非必然）
- **误报排除**: 非"用户代码抛 Error 是用户的责任"——handler 是 SPI 一等扩展点，框架对其余四类返回值协议均有失败闭环，Error 是唯一无闭环的出口；非 C3-002 重复——C3-002 的根是 convert 异常 + 无屏障 + DebeziumMessageSource 未接监听，本条根是**屏障类型宽度**（Exception vs Throwable）及其在 Kafka/Pulsar/Local 消费循环上的全新僵尸面。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜现状:新发现（7 个 Exception-only 屏障逐点回码确认——Kafka:304/Pulsar:118/Local:232/wrapper:164/dispatchEvent:159/failure 回调守卫/bridge:111，grep catch Throwable|catch Error=0 复现；僵尸 isActive 面与 Error 死亡绕过 notifyEngineFailure 面实测成立。判级注记：与 WI 2.1 C3-001 平台绝缘层（P1，29 站点）/WI 1.7 C6-001（P1）同族，维持 P2 的依据是修复归属统一绝缘层批次（族内吸收、避免同族双 P1 计数），非否认后果；7.1 统一族内判级时可随之升格）

### [C3-010] Pulsar 批量路径对 onMessageBatch 返回 null 无显式处置——NPE 顶替类型化诊断，与 Kafka 的显式契约不对称

- **文件**: `nop-message/nop-message-pulsar/src/main/java/io/nop/message/pulsar/PulsarConsumeTask.java:237-246`；对照 `nop-message/nop-message-kafka/src/main/java/io/nop/message/kafka/KafkaConsumeTask.java:509-512`
- **证据片段**:
  ```java
  // PulsarConsumeTask.batchConsume:237-246 —— null 直接 NPE
  List<Object> responses = consumer.onMessageBatch(list, context);
  Iterator<Message<Object>> it = messages.iterator();
  List<CompletionStage<?>> futures = new ArrayList<>(responses.size());  // ← NPE
  // 对照 KafkaConsumeTask.batchConsume:509-512 —— null 是显式契约
  List<Object> responses = consumerFn.onMessageBatch(list, ctx);
  if (responses == null) {
      throw new IllegalStateException("onMessageBatch returned null");
  }
  ```
- **严重程度**: P3
- **并发场景**: 单消费线程顺序路径；不涉及线程交错——契约完备性问题（handler 返回 null 是协议未定义为合法/非法的灰色输入）。
- **触发时序**: T1 handler 对批量订阅返回 `null` → :239 `responses.size()` 抛 NPE → :302 catch 捕获（NPE 是 Exception）→ 整批 `negativeAcknowledge(messages)` + rethrow → run-loop 退避。最终投递语义（整批重投）与 Kafka 一致，但失败被记为 `nop.err.message.pulsar.batch-consume-fail` 下的裸 NPE，且 WI25 的 arity WARN 逻辑（:289-300）不会执行。
- **现状**: Kafka 侧在 WI11/F-06 中把 null 显式裁定为"整批失败"（javadoc :481-483 "A null response list from the handler is treated as a full-batch failure"）；Pulsar 侧无任何声明，行为正确纯属 NPE 恰好落入 catch 的巧合。
- **风险**: 排障面失真（NPE 栈指向框架集合操作而非 handler 契约违约）；两后端对同一 handler 缺陷的可诊断性不一致；未来若 :239 之前的代码结构变化（如 null 检查被重构掉）行为无保护测试锚定。
- **建议**: 对齐 Kafka：`if (responses == null) throw new IllegalStateException("onMessageBatch returned null");` 于 :238 前置，并在 PulsarConsumeTask 批路径 javadoc 补该契约行。
- **失效面枚举**（F3：onMessageBatch 返回值处置逐一）: ① Kafka batchConsume:510 —— 显式 IllegalStateException ✓；② Pulsar batchConsume:239 —— 隐式 NPE（本条）✗；③ 单条路径两端（onMessage 返回 null）—— null 是合法"完成"语义，无需处置 ✓；④ `FutureHelper.getResult(responses.get(i))` 对 responses 元素为 null —— getResult:283-288 对非 CompletionStage 原样返回，null 走 ack 分支 :280-282，安全 ✓。
- **信心水平**: 确定（NPE 路径与两端不对称均为代码直证）
- **误报排除**: 非 error-handling 维度越界——报的是同一 SPI 契约在两后端对同一输入的**行为可诊断性分叉**与未声明面，属异步契约完备性；后果（整批 nack）虽安全，但该安全性未被任何一方声明为契约。
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜现状:新发现（:237-239 null→responses.size() NPE vs Kafka :509-512 显式 IllegalStateException 对照逐字实测确认；NPE 落 :302 catch 整批 nack 后果安全但 arity WARN 不执行、诊断失真的判定成立）

## 扩展已立案条目（第 1 轮）

### 扩展 C3-002（转换链新异常路径，同根不另立案）

`DebeziumEventConverter.java` 补充两处第 1 轮证据清单之外的抛点，均落入同一无屏障根（handleDebeziumEvent 无 try/catch）：
- **parseKey 非对象 JSON CCE（:112-115）**：`new String(keyBytes)` + `JsonTool.parseNonStrict(keyJson)` 后直接 `(Map<String, Object>)` 强转——key 为标量 JSON（如 `"abc"`、`123`，文件/部分 log 连接器的键形态）时 ClassCastException。第 1 轮仅登记了 value 侧 :41 CCE 与 key 侧 :113 NPE，parseKey 的类型转换面是遗漏的第三个抛点。
- **ts_ms 裸转换与 asLong 的 F3 残留（:59）**：`Long tsMs = (Long) payload.get("ts_ms")`——同文件 :84-85 注释自证"JSON 数值经解析可能落到 Integer/Long 任一宽度"并为 `pos`/`lsn`/`commit_lsn` 统一引入 `asLong`（:98-110），唯 `ts_ms` 保持裸 `(Long)` 强转：修实例不修类别的同族残留（epoch 毫秒实际恒超 Integer 宽度，触发概率极低，故随 C3-002 计，不独立定级）。

### 扩展 C3-005（nack 丢失尾部的单条路径同族）

第 1 轮 C3-005 登记 Pulsar **批量** catch 的整批 `negativeAcknowledge`（:306）在连接中断时可能丢失、且未配置 ackTimeout 时无重投兜底。本轮确认**单条**路径同族：`fail():482-489` 对失败消息的 `negativeAcknowledge(messageId)`（:484）同为 fire-and-forget（无 future、无重试、无完成回调），nack 请求丢失 + `ackTimeout` 缺省 0（`PulsarConsumerConfig.ackTimeout` 默认 0，doSubscribe 仅在 >0 时装配）时该消息在本消费会话内不再重投。与 C3-005 同根（恢复语义依赖单次 nack 的送达性），并入不另编号。

### 扩展 C3-007（DLQ 发布超时后的 ghost 副本面）

`KafkaConsumeTask.publishToDeadLetterIfConfigured:689-695` 经 `FutureHelper.syncGet(service.sendAsync(dlqTopic, ...))` 同步发布；sendAsync 的 `orTimeout(sendTimeoutMs)`（:311-313）超时后 syncGet 抛 TimeoutException 进入失败路径（seek 重投），而底层 ghost send 仍可能最终落盘 → 重投轮再次发布 → **DLQ 出现重复副本**。javadoc（:682-684）声明"the DLQ write must be durable before the commit"——超时面上"先持久后提交"的序退化为"结果未知即重试"，与 C3-007 已立案的 fire-and-forget ghost 语义同根，属其在 WI21 DLQ 路径上的具体化。at-least-once 残留，随 C3-007 计。

## 维度集外线索（overlay）

### [nop-message-CL-025]（C3）MessageConsumeFailureBridge.submit 转发无等待上界且无 pending 可观测

`MessageConsumeFailureBridge.java:102-110`：`task.callAsync(request, null).whenComplete(...)` 纯 fire-and-forget；若 nop-retry 引擎的 callAsync future 永不完成（下游 replay 目标挂死且策略无期限），whenComplete 永不执行，该 ConsumeFailure 的第三层转发**静默丢失**，且无 pending 计数/超时告警（仅"未配置"有 WARN，"已提交未完成"不可见）。契约注释声明其为辅助通道、永不抛——方向正确，但"提交后不可观测"未声明。建议 C7 复核：为 callAsync 施加期限（复用 retry 策略 deadline）或暴露 pending gauge。

### [nop-message-CL-026]（C3）PulsarConsumeTask.batchConsume 的 catch 清理 lastFailure* 不完整（与 WI21 注释自相矛盾）

`PulsarConsumeTask.java:303-306`：批量 catch 仅置 `lastFailureLocation = null; lastFailureMessage = null;`，未清 `lastFailureTopic`——与本任务 `dispatchConsumeFailure:450-452` 的 WI21 注释（"clear ALL context fields together — a stale topic ... must not leak"）及 Kafka 同位路径的清理纪律不一致。当前两处调用序（fail() 设置后必经 dispatchConsumeFailure 全量清理）使其无现行后果，属防御不对称的潜伏面；修复成本一行，建议与下次触碰该文件的变更顺带补齐。

## 继续判定

**终止信号。** 依据：本轮新增 C3-009（P2×1）+ C3-010（P3×1），无 P0/P1；四个指令面中 commitAsync 面落空（0 处）、bridge 传播面语义闭合（仅 CL-025 观测缺口）、converter 与 nack 面全部归并为第 1 轮条目的扩展；C3-009 虽为模块级统一缺口但根因单一（屏障类型宽度），修复形态一致（catch Throwable + 失败上报），收敛证据充分。第 1 轮 8 条 + 本轮 2 条 + 3 项扩展后，异步入口清单（7 文件 × 全部回调/future 站点）已两遍覆盖，无需第 3 轮。

## 复核结论（WI 4.1 独立复核，2026-10-20）

- **C3-009 复核保留（P2）**、**C3-010 复核保留（P3）**：状态行已写于本文件各自条目末尾。三项扩展（C3-002 的 parseKey CCE/ts_ms 裸转换、C3-005 的单路径 nack 同族、C3-007 的 DLQ ghost）逐点回码确认，同根归并「不另编号」的处置正确。
- 本轮终止信号判定成立：新增仅 P2×1+P3×1、四个指令面两落空两归并，C3 维度两轮+扩展覆盖可收口的裁定成立。CL-025/CL-026 登记为独立线索，处置随 C7/C4 复核轨。
- 全维度复核结论（判定统计/跨单元对照/最终保留项）见 `C3-async-chains.md`「维度复核结论」节：10 条全部保留，最终 P1×1/P2×6/P3×3；WI 2.3 CL-022 移交落点闭环确认（C3-007），WI 1.2 同族（MA-06/MA-07/R5-CON-03）均为使用面/本体面双轨或新失效面，零重复立项。
