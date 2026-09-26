# 03 JPath 兼容桥接契约

> Status: active
> Last Reviewed: 2026-09-26
> Source: plan `ai-dev/plans/361-nop-jq-deadcode-and-jpath-fix.md`

## 契约

nop-core 的 `io.nop.core.lang.json.jpath.JPath` 是历史兼容入口：**compile 面始终可用**（纯 core，无第三方依赖），**求值面经 SPI 委托**——由 nop-jq 在平台初始化时注册 `JPathEvaluator` 实现（绑定 `io.nop.jq.jsonpath.NopJsonPath`）。

- 注册机制：`JPath.registerEvaluator/unregisterEvaluator`（静态 volatile，最后注册者生效）；nop-jq 侧 `io.nop.jq.JqJPathInitializer implements ICoreInitializer`，经 `META-INF/services/io.nop.core.initialize.ICoreInitializer`（ServiceLoader）发现，order 取 `INITIALIZER_PRIORITY_REGISTER_COMPONENT + 1`（组件注册后、IOC 前），destroy 时注销。
- 快速失败：无注册 evaluator 时调用任何求值方法抛 `NopException(nop.err.core.jpath.no-evaluator)`——修复 nop-jq 前的无语义 `UnsupportedOperationException` 墓碑已移除。
- 异常透传：`NopJqException extends NopException`，SPI 边界不包装。
- 语义对齐：eval/evalOne 对应 `NopJsonPath.eval/evalOne`；set 不创建中间容器（父缺失返回 false）；remove 无命中返回 false。
- 历史命名保留：实例方法 `get(bean, value)` 实为 set 语义（兼容旧签名，javadoc 已注明）。
- **静态便捷方法已移除**（`get(bean,path)`/`get(bean,path,value)`/`delete(bean,path)`，全仓库零调用者）：它们与实例重载存在二义性——经实例引用调用时更具体的静态 `get(Object,String)` 恒胜出，set 值会被当 path 静默编译（实测复现）。执行时裁定随本计划移除。
- 类级 `@Deprecated` 已移除：功能恢复且是受支持的兼容入口；nop-jq 感知的代码应直接用 `NopJsonPath`。

## 消费方规则

- 依赖树中已有 nop-jq 的模块/应用：JPath 求值自动可用（ServiceLoader 注册）。
- 无 nop-jq 依赖但需要 JPath 求值的测试或应用：显式添加 nop-jq 依赖（如 `nop-ooxml-docx` 以 test-scope 引入，使 `TestWordTemplate` 的模板求值可用）。
- 不允许在 nop-core/nop-xlang 中反向依赖 nop-jq（依赖方向 core ← jq，见 01-architecture-baseline）。

## 被拒替代方案

1. **恢复 core 内 jayway JsonPath 实现**——与模块去第三方依赖的方向冲突，且双实现并存会让 jq 语义（430 官方用例基线）与 jayway 语义漂移。
2. **xlang 调用方直接改用 NopJsonPath**——nop-core 不依赖 nop-jq，依赖成环不可行。
3. **core 直接依赖 nop-jq 类型**——同上。
