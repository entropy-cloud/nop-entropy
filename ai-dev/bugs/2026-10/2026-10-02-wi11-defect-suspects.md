# 2026-10-02 WI11 测试暴露的产品缺陷嫌疑清单（已修复，plan 2306 收口）

> 来源：plan 2303（WI11 可靠性外围）执行期发现。按 roadmap 硬边界记录，修复独立立项。

## 1. HealthStatus.merge 语义疑似反转（P1 嫌疑）

- Problem：`nop-cluster-core` 的 `HealthStatus.merge` 返回 ordinal 较小者——`merge(UP, DOWN) == UP`。单个 DOWN checker 永远不会拉低 `CompositeHealthChecker` 聚合状态，与常见 worst-wins 聚合语义相反。
- 证据：表征测试 `TestCompositeHealthChecker.testMergeIsFirstArgWhenOrdinalSmaller` 固化现状；修复时需同步改该测试。
- 影响：健康检查聚合在部分实例 DOWN 时仍报 UP，误导运维。

## 2. MultiRpcService 空服务映射未 fail-fast（P3 嫌疑）

- Problem：`Guard.notEmpty` 经 `isEmptyObject` 对 Map 只判 null，空 map 构造成功后所有调用在运行期以 unknown-service 失败，未能在构造期暴露配置错误。


## Fix（2026-10-03 plan 2306 回填）

- **1 HealthStatus.merge 反转：`fixed`**。worst-wins 聚合实现，严重度定序对齐 Spring Boot SimpleStatusAggregator 默认次序：DOWN > OUT_OF_SERVICE > UP > UNKNOWN（裁定点 OUT_OF_SERVICE 按此定序）。唯一消费方 CompositeHealthChecker（grep 证据）。回归：testMergeIsWorstWinsWithSpringBootSeverityOrder（全对序断言）+ testSingleDownCheckerPullsAggregateDown（聚合行为），原表征测试翻转。
- **2 MultiRpcService 空映射未 fail-fast：`fixed`**。构造器在 Guard.notEmpty 之后追加 isEmpty 检查（Guard.notEmpty 对 Map 只判 null——ApiStringHelper.isEmptyObject 不处理集合类型的既有语义，不在本 plan 扩改）。回归：TestMultiRpcService.testEmptyServicesMapRejectedAtConstruction。

## 既有项（本 WI 范围记录，不新增）

- TarjanSCC lowLink 缺陷（nop-graph-core）、retry 幂等键生命周期 P1——维持独立立项，修复时回归测试并入本 WI 记账。

## Affected Files

- nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/health/HealthStatus.java
- nop-network/nop-rpc/nop-rpc-core/src/main/java/io/nop/rpc/core/reflect/MultiRpcService.java
- 测试：TestCompositeHealthChecker / TestMultiRpcService
