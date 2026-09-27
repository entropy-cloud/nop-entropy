# 新发现：CEP operator + RocksDB 状态后端组合不可用（plan 2279 Phase 1 基准实测发现）

> 发现日期：2026-09-27（CepOperatorBench ROCKSDB 档 setup 实测复现）
> 严重度：**P1（组合功能空壳）**——组件级各有测试、组合级零覆盖且链路断裂（plan guide "Lessons From History #8 空壳实现" 的实例）
> 归属：正确性专项（perf 计划 2279 不修此缺陷；其 Q1 优化以 MEMORY 档测量，机制与后端无关）

## 缺陷链（三层，全部实测复现）

1. **SharedBuffer 节点不可序列化**：`SharedBuffer.registerEvent`（SharedBuffer.java:328-331）向 RocksDB MapState 写 `Lockable<NodeId, SharedBufferNode>`；`RocksDBMapState.put`（:161-171）走 JSON 序列化，`Lockable` 非 `@DataBean` → `nop.err.core.json.only-data-bean-is-serializable` → `registerEvent` 即失败。事件时间/处理时间的任何匹配都无法开始。
2. **elementQueueState 无元素类型信息**：`CepOperator`（:390-394，注释自认 "MapStateDescriptor does not support generic type tokens for value class"）以 `(Class) List.class` 创建描述符；RocksDB 读回为 `LinkedHashMap`（ContainerValueCodec "no element type info; keeping JSON-native form"）→ NFA 过滤器 `ClassCastException`。缓冲事件的 drain 路径断裂。
3. **ClassNameValidator 白名单拒绝用户事件类**：白名单（ClassNameValidator.java:14-38）仅放行 `io.nop.stream./commons./core./dao. + JDK` 前缀——第三方用户事件类（`com.acme.MyEvent` 形态）即使前两层修复也会被 `resolveType` 拒绝降级为 JSON-native。

## 覆盖缺口证据

- `grep -rln RocksDBStateBackend nop-stream/nop-stream-cep/src/test/java` → 0 文件
- `grep -rln CepOperator nop-stream/nop-stream-rocksdb/src/test/java` → 0 文件
- fraud-example 不调 `setStateBackend`（默认 Memory）；生产文档未声明 CEP 不支持 RocksDB

## 裁定

- **Fix 归属**：successor 正确性专项（需 SharedBuffer 节点存储格式设计——`__java_bytes__` serializer 路径或 DataBean 化节点 + 描述符元素类型信息通道 + 白名单契约裁定；涉及 checkpoint 兼容，不可在 perf 计划内顺手改）
- **对 plan 2279 的影响**：CepOperatorBench 的 ROCKSDB 档被本缺陷阻塞（setup 失败），Q1 的台账驱动 drain 优化以 MEMORY 档实测（`getSortedTimestamps` 全扫与台账机制与后端无关；MEMORY 基线 1.27→3.2µs 随 keys/buckets 缩放已可测量）。WindowOperator 的 ROCKSDB 档不受影响（其走 Aggregating/ListState 描述符路径，值类型为 Long/DataBean 形态）
- 基准事件类已移至 `io.nop.stream.bench` 包（过白名单），第一层缺陷在基准侧规避；第二/三层为生产代码缺陷，无法在基准侧规避
