# 2026-08-28 时序断言的时钟基准错位（retry 退避 / stream 对齐超时两例）

## Problem

- 例 1（nop-retry-engine，全量 reactor 第六轮）：`testExecuteTask_shouldComputeDeterministicBackoffWithoutJitter` 断言 `backoff delay should be ~100ms but was -110ms`——**负值**。
- 例 2（nop-stream-core，全量 reactor 第三轮）：`TestInputGateAlignmentStarvationFix.testAlignmentTimeoutFiresWithContinuousTraffic` 断言墙钟 `elapsed >= 300ms` 失败——超时异常在墙钟 300ms 未到时就抛出，数学上"不可能"（实现侧起点 ≥ 测试侧起点）。
- 两例共同点：模块隔离均绿、全量 reactor 偶发红；断言都比较**两个由不同层/不同采样点产生的时钟值**。

## Diagnostic Method

- 例 1：负值是决定性线索——`delay = nextTriggerTime - updateTime`，读实现确认 `nextTriggerTime` 由引擎时钟（`recordStore.getCurrentTime() + interval`）计算，而 `updateTime` 是 ORM flush 时刻；负载下 flush 延迟 > interval（100ms）时推导必然为负。产品语义无恙（nextTriggerTime 略早只意味着提前可重发），纯测试推导基准选错层。
- 例 2：先证伪"实现提前触发"——梳理触发条件（`now - align.startTime > timeout`，`align.startTime` 在首次 read 时采样、晚于测试的 `start` 采样），数学上墙钟 elapsed ≥ timeout 恒成立；唯一能违反的是两次采样间 `System.currentTimeMillis()` 回拨（NTP 步进）。结论：下界墙钟断言把"实现不提前触发"的验证建立在一个非单调时钟上。
- 该测试此前已被加固过一轮（注释自述观察到全量 -T 1C 负载下单发 ratio 异常），说明这类断言在负载下反复出问题。

## Root Cause

- 例 1：跨层时钟基准——用 A 层（ORM flush）的时间戳推导 B 层（引擎调度）的时间差，两层写入时刻的间隔在负载下无界。
- 例 2：非单调墙钟上的下界断言——测试与实现各自采样 `currentTimeMillis`，时钟回拨使"实现不早于阈值"的数学保证失效。

## Fix

- 例 1（commit b27c529f0d）：基准改最近 attempt 的 `endTime`——与 `nextTriggerTime` 同为引擎时钟采样（间隔微秒级），50..300 断言窗口**不放宽**，抖动检测保留。
- 例 2（commit 3f7132f1fb）：主判定改用异常携带的 `timeoutMs` 参数（网关自身 elapsed 样本，即触发条件本身），墙钟断言降级为 sanity 并加 200ms 容忍带。

## Tests

- `nop-retry/nop-retry-engine/src/test/java/io/nop/retry/engine/impl/TestRetryEngineImpl.java` - 引擎时钟基准。
- `nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/execution/TestInputGateAlignmentStarvationFix.java` - 三处同型断言统一处理。

## Affected Files

- 同上两文件（实现均未改）。

## Notes For Future Refactors

- 时序断言的基准必须是**与被测逻辑同一时钟源**的样本（异常参数/记录字段），墙钟只做带容忍带的 sanity；"推导时间差"禁止跨层取时间戳。
- 负值 elapsed/delay 是基准错位的强信号（正常抖动只会偏大，跨层滞后/时钟回拨才会穿零）。
