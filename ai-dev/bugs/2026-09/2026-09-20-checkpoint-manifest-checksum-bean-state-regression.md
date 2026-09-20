# 2026-09-20 Checkpoint Manifest Checksum Regression for Bean-Typed Keyed State

## Problem

- `nop-stream/nop-stream-fraud-example` 的恢复类 E2E（`TestS1CdcRecoveryE2E`、
  `TestDistributedScenarioSerialization`）在 `loadLatestEpochManifest` / 恢复路径上抛
  `nop.err.stream.checkpoint-checksum-mismatch`：durable manifest 无法通过自身的完整性校验。
- 症状确定性复现（epoch 0/1/2 全部失败），与并发/时序无关；standalone 运行与全量
  `mvn install` 均失败。
- 影响面：任何 keyed state 中含 `@DataBean` POJO 值（如 `EnrichedTransaction`）的作业，
  其写出的 manifest 都会在恢复时被判定为篡改而拒绝，作业实际不可恢复。

## Diagnostic Method

- 先做因果隔离：在改动前的 `HEAD~1` 上 install 基线 `nop-xlang`，重跑失败用例——同样失败，
  排除本次 xdef 约束改动，确认为 `nop-stream` 既有回归。
- 在 `CheckpointSerDe.verifyManifestChecksum` 与 `serializeEpochManifest` 临时打印两侧
  规范化 JSON 文本，定位到首个差异字符：store 侧 `"avgAmount":55.00`，load 侧 `"avgAmount":55`。
- 排除的假设：并发写盘/时序、JsonTool 数字语义整体变化、i18n。决定性证据是同一 manifest
  的 write-side 与 read-side canonical 文本在 bean 序列化字段上分歧。
- 用 `git log`（`normalizeNumbersDeep` / `computeCanonicalChecksumHex`）锁定引入提交。

## Root Cause

- 提交 `79250581fc`（2026-09-16）为修复数字规范化，把 `computeCanonicalChecksumHex` 中的
  JSON 文本往返（`serialize -> parseMap -> serialize`）替换为纯内存的
  `normalizeNumbersDeep(canonical)`。
- store 侧内存 map 中的 keyed state 可能是 POJO（`@DataBean`），`normalizeNumbersDeep`
  只遍历 `Map/List/Number`，不进入 POJO；`JsonTool.serialize` 会把 POJO 展开为
  `nopElementType/nopItems` 树并把整数值序列化为 `55.00`。
- load 侧是从落盘 JSON 重新 parse 的纯 map/list/number，整数被规范化为 `55`。
  两侧 canonical 文本从此不再收敛，checksum 必然不匹配。
- 方法 javadoc 一直声明"one JSON text round-trip"，但 `79250581fc` 删除了该往返，
  实现与文档契约脱节。

## Fix

- 恢复 JSON 文本往返并保留数字规范化：`computeCanonicalChecksumHex` 先
  `JsonTool.parseMap(JsonTool.serialize(canonical, false))` 把非 JSON-native 值
  （POJO 等）收敛为纯 map/list/number，再 `normalizeNumbersDeep` 固定数字表示，
  最后序列化取 SHA-256。store/load 两侧走同一函数，按构造收敛。
- 未改 `normalizeNumbersDeep`：它负责 Long map key 与 BigDecimal 定点的既有修复，
  与新往返正交。

## Tests

- `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/checkpoint/storage/TestCheckpointManifestChecksum.java`
  - 新增 `testBeanTypedKeyedStateHashConvergesAcrossJsonRoundTrip`：以 `@DataBean`
    POJO（`Double` + `BigDecimal("55.00")`）作 keyed state，断言
    `computeManifestChecksumHex(payloadOnly) == storedChecksum` 且 `deserializeEpochManifest`
    不抛错。已验证：去掉修复后该用例红，恢复后绿。
- 既有 `TestCheckpointManifestChecksum` / `TestCheckpointBodyChecksum` /
  `TestEpochManifestPersistence` 全绿。
- 端到端：`TestS1CdcRecoveryE2E`、`TestDistributedScenarioSerialization` 由失败转绿。

## Affected Files

- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/CheckpointSerDe.java`
- `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/checkpoint/storage/TestCheckpointManifestChecksum.java`

## Notes For Future Refactors

- checksum 的 canonical 定义必须保证 store-side 内存值与 load-side parsed 值收敛；
  不要以"只遍历已知容器类型"的优化替换 JSON 文本往返，否则任何 JSON 展开型值
  （POJO/带 serializer 的类型）都会破坏收敛。
- `taskSnapshots.keyedStates`/`operatorStates` 可承载任意业务对象，属于最易触发该类
  分歧的输入；修改 `serializeTaskStateSnapshot` 或 `normalizeValue` 时须保留往返语义，
  并让 `TestCheckpointManifestChecksum` 的 bean-typed 用例保持绿色（平台基线 tripwire）。
