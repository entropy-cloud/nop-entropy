# 2026-11-15 ChatServiceImpl 流式首包静默丢失（SubmissionPublisher pre-onSubscribe 丢元素窗口）

## Problem

- 满载全量构建下 `TestChatServiceFailoverAdapterStreaming.outOfWindowFailureBreaksStreamWithoutSwitch` 随机失败（build4/build11 两轮复现）：`texts=[] error=null completed=true`、耗时 6-9ms——流正常完成但零元素交付。
- 推演到生产面：HTTP 流式路径下，IdP 首批 SSE 数据在消费者 onSubscribe 落地前到达时同样丢元素——**真实生产数据丢失缺陷**，不限于测试。

## Diagnostic Method

- 先证伪环境说：solo 单测/整类/整模块套件 10+ 次全绿 → 需要负载复现。合成负载（yes burner ×16）+ 整模块套件 3/4 失败 → 稳定复现。
- 断言消息补 `sub.error` + `sub.completed` → 决定性证据 `error=null completed=true`：不是错误路径，是**裸 onComplete**（正常完成 + 零交付）。
- 第一轮假设（commonPool 饿死）被证伪：fake 发射从 commonPool 换虚拟线程后仍失败。
- 主代码/测试基建临时探针（fake 发射点、AttemptSubscriber onSubscribe/onNext、handleItemLocked、drain、下游收集）复现轮取证，铁证序列：
  1. `attempt 1 subscribing AttemptSubscriber`（subscribe 同步注册）
  2. gate 放行 → c1..c12 全部发射（impl onNext → publisher.offer ×12）
  3. `attempt 1 onSubscribe`（**异步投递，晚于全部 offer**）
  4. 零个 handleItem（12 元素被 publisher 丢弃）→ 429 被判窗口内失败 → 重订阅
  5. attempt 2 拿到空队列默认空成功场景 → `completed=true texts=0`
- 根因锚点：JDK `SubmissionPublisher` 的 `BufferedSubscriber` 在 onSubscribe 任务未落地前 `sub==null`，offer 走静默丢弃分支；`onSubscribe` 又经 executor 异步投递——满载下投递延迟 > 发射延迟即丢。plan 2514 的 gate 只堵了"fake 早于 impl 订阅 fake"那半个竞态，"impl offer 早于 AttemptSubscriber onSubscribe 落地"这半个未覆盖。

## Root Cause

- `ChatServiceImpl.callStream`（nop-ai-core）以 `SubmissionPublisher(GlobalExecutors.cachedThreadPool(), 256)` 承载流式桥接：best-effort 语义 + onSubscribe 异步投递 = 首包丢失窗口。
- 修复探针确证丢弃发生在 publisher 内部（AttemptSubscriber.onNext 从未被调用，非 flow 侧丢弃）。

## Fix

- 新增 `nop-ai-core/.../service/BoundedStreamPublisher.java`：单订阅者 publisher，
  - **同步 onSubscribe**（`subscribe()` 内直接发，返回时下游 request 已落地——消除窗口）；
  - offer 一律入自有 FIFO 缓冲（订阅前后都不丢），经受管 executor（GlobalExecutors.cachedThreadPool）**异步 demand 门控投递**——保留 plan 2505 3.1-C3-001 ③ 裁定（流载体必须是平台受管池线程 + 慢下游 fail-loud 不阻塞读线程，第一版同步投递方案因违反此裁定被否，见 Notes）；
  - 缓冲满 = onDrop fail-loud（既有溢出语义）；异常终态立即交付不等 demand（C3-001 契约），成功终态等缓冲清空（不吞尾包）；单飞 drain + 出锁投递（与 flow drain 同型）。
- `ChatServiceImpl` 换用新 publisher，缓冲 256/溢出 fail-loud/取消闭合语义不变。

## Tests

- `TestStreamCancelDeadlineAndDelivery_C3_001`（既有 13 例）全绿——含两枚既有钉：`streamCarrierIsManagedPoolNotCommonPool`（投递线程必须是受管池，钉住同步投递方案不可行）与 `slowDownstreamFailsFastInsteadOfBlocking`（demand=0 溢出必须 fail-loud 终态）。
- `nop-ai-gateway` 整模块套件在合成满载下 6/6 绿（修复前同条件 3/4 红）。
- 本缺陷的定向满载钉 = 既有 `outOfWindowFailureBreaksStreamWithoutSwitch`（断言消息已补 `error=`/`completed=` 证据字段，供未来失败取证）。

## Affected Files

- `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/BoundedStreamPublisher.java`（新增）
- `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatServiceImpl.java`（publisher 换用）
- `nop-ai/nop-ai-gateway/src/test/java/io/nop/ai/gateway/failover/TestChatServiceFailoverAdapterStreaming.java`（断言消息补证据字段）
- `nop-ai/nop-ai-gateway/src/test/java/io/nop/ai/gateway/failover/FailoverTestSupport.java`（fake 发射 commonPool → 虚拟线程：gate.await 阻塞型任务不再占共享池 worker）

## Notes For Future Refactors

- JDK `SubmissionPublisher` 是 best-effort：**subscribe() 注册 ≠ 消费者就绪**，pre-onSubscribe 窗口内 offer 会静默丢失。任何"上游发射节奏不受控 + 首包必须不丢"的桥接场景不得直接用它。
- 投递载体必须走受管 executor 是 plan 2505 裁定（钉：streamCarrierIsManagedPool）——修复不得回退为同步内联投递（第一版方案即因此被否）。
- 慢下游 fail-loud 契约：demand=0 时缓冲满必须显式终态化而非阻塞读线程（钉：slowDownstreamFailsFast）。
- 同期发现并同批修复的 JWK 单飞竞态见 `TestJwkLocatorSingleflight`（lastRequestTime 推进锚点从等待方移到 sendRequest 发起侧）；`ShellCommandExecutor.rejectSynchronousExecutor` 探针误判修复见其源内注释。
