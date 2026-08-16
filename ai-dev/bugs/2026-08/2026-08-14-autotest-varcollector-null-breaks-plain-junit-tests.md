# 2026-08-14 Autotest VarCollector Null Breaks Plain JUnit Tests

## Problem

- `TestChannelScanBindLoginE2E.testScanLoginFullChainE2E`（nop-auth-service，普通 JUnit 5 测试，非 autotest）偶发 NPE：
  `Cannot invoke "VarCollector.collectVar(String, Object)" because the return value of "VarCollector.instance()" is null`
- 同一命令行同一代码两次运行，一次通过一次 NPE（order-dependent flaky）
- 任何触发 `LoginApiBizModel.getLoginResultAsync` 生产链路的非 autotest 测试都可能命中

## Diagnostic Method

- 难点：同 JVM 内测试类执行顺序决定成败，失败不可稳定复现
- 先查 `LoginApiBizModel.java:99/110`——生产代码直接 `VarCollector.instance().collectVar(...)`，无 null 防护
- 再查 `VarCollector` 本体：`static VarCollector _instance = new VarCollector()` 初始值非 null，说明有代码注册了 null
- 全库检索 `registerInstance`：唯一注入点在 `AutoTestCase.complete()`（`nop-autotest-core`）teardown 时 `VarCollector.registerInstance(null)`
- 决定性证据：任何 autotest 测试（JunitBaseTestCase 体系）跑完后全局 collector 即为 null；后续普通测试类再走登录链路即 NPE

## Root Cause

- `AutoTestCase.complete()`（`nop-autotest/nop-autotest-core/src/main/java/io/nop/autotest/core/AutoTestCase.java`）在每个 autotest 用例结束时 `VarCollector.registerInstance(null)`，把"停止录制"实现成全局 null
- 生产代码（`LoginApiBizModel` 等多处）假设 `instance()` 永不返回 null；null 破坏了该假设，且破坏范围跨模块、依赖类执行顺序

## Fix

- `AutoTestCase.complete()` teardown 改为 `VarCollector.registerInstance(new VarCollector())`：恢复 no-op 收集器（与类初始静态值一致），保留"停止录制"语义，`instance()` 永不返回 null
- 不改 `VarCollector` 本体（nop-core 框架核心，plan-first 保护区）也不改 nop-auth 生产代码（ask-first 保护区）

## Tests

- `nop-auth/nop-auth-service/src/test/java/io/nop/auth/service/TestChannelScanBindLoginE2E.java` - 既有 E2E 即回归测试：修复前 order-dependent NPE，修复后无论是否排在 autotest 测试之后均通过（`./mvnw test -pl nop-datav -am -T 1C` 全链路 414 模块 BUILD SUCCESS）

## Affected Files

- `nop-autotest/nop-autotest-core/src/main/java/io/nop/autotest/core/AutoTestCase.java`

## Notes For Future Refactors

- 不要在任何 teardown 中把全局静态服务注册为 null；用 no-op 实例表达"停用"
- 新增普通 JUnit 测试（不继承 JunitBaseTestCase）若触发含 `VarCollector.instance().collectVar(...)` 的生产链路，此前提下已安全；但生产代码新增调用点时仍应意识到该全局可能被替换
