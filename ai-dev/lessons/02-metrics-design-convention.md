# 02 - Nop 平台 Metrics 设计规范：禁止直接注入 MeterRegistry

**日期**：2026-05-11
**来源模块**：nop-job / `JobPlannerScannerImpl`
**严重度**：P2（架构一致性违反）
**状态**：已修复

## 错误做法

直接将 Micrometer 的 `MeterRegistry` 注入到业务组件中：

```java
// ❌ 错误
public class JobPlannerScannerImpl {
    @Inject
    private MeterRegistry meterRegistry;  // 直接注入

    void scanOnce() {
        meterRegistry.counter("nop.job.planner.due-count").increment(dueCount);
        // ↑ 每次调用都 lookup/create counter，性能差
    }
}
```

问题：
1. **绕过 Nop 的统一注册中心** — 没有走 `GlobalMeterRegistry.instance()`
2. **每次调用 `registry.counter(name)` 都做 map lookup**，没有复用 Counter 对象
3. **业务代码直接依赖 Micrometer API**，违反领域隔离
4. **IoC 容器中不一定有 MeterRegistry bean**，测试环境可能注入失败

## 正确做法：Nop Metrics 三件套

仿照 `IOrmMetrics` → `OrmMetricsImpl` → `EmptyOrmMetricsImpl` 的模式，每个需要 metrics 的子系统创建三件套：

### 1. 领域指标接口

在子系统的 `metrics` 子包中定义接口，方法命名体现业务语义，Javadoc 标注底层 meter 名称：

```java
package io.nop.job.coordinator.metrics;

public interface IJobPlannerMetrics {
    /** Counter: nop.job.planner.due-count */
    void onDueSchedules(int count);

    /** Counter: nop.job.planner.lock-conflict */
    void onLockConflicts(int count);
}
```

### 2. 真实实现（构造函数预创建 Counter）

使用 `GlobalMeterRegistry.instance()` 获取 registry，在构造函数中预创建所有 Counter/Timer/Gauge：

```java
public class JobPlannerMetricsImpl implements IJobPlannerMetrics {
    private final Counter dueCountCounter;
    private final Counter lockConflictCounter;

    public JobPlannerMetricsImpl() {
        this(GlobalMeterRegistry.instance());
    }

    public JobPlannerMetricsImpl(MeterRegistry registry) {
        this.dueCountCounter = registry.counter("nop.job.planner.due-count");
        this.lockConflictCounter = registry.counter("nop.job.planner.lock-conflict");
    }

    @Override
    public void onDueSchedules(int count) {
        dueCountCounter.increment(count);
    }
}
```

### 3. 空实现（no-op fallback）

```java
public class EmptyJobPlannerMetrics implements IJobPlannerMetrics {
    @Override
    public void onDueSchedules(int count) {}

    @Override
    public void onLockConflicts(int count) {}
}
```

### 4. 业务组件使用

业务组件持有接口类型字段，默认值为空实现，通过 beans.xml 注入真实实现：

```java
public class JobPlannerScannerImpl {
    private IJobPlannerMetrics plannerMetrics = new EmptyJobPlannerMetrics();

    public void setPlannerMetrics(IJobPlannerMetrics plannerMetrics) {
        this.plannerMetrics = plannerMetrics;
    }

    void scanOnce() {
        plannerMetrics.onDueSchedules(dueCount);       // 无 null 检查，空实现兜底
        plannerMetrics.onLockConflicts(conflictCount);  // 性能：直接 field access
    }
}
```

beans.xml 配置：

```xml
<bean id="nopJobPlannerScanner" class="io.nop.job.coordinator.engine.JobPlannerScannerImpl">
    <property name="plannerMetrics">
        <bean class="io.nop.job.coordinator.metrics.JobPlannerMetricsImpl"/>
    </property>
</bean>
```

## 判定规则

> **Nop 平台中禁止直接注入 `MeterRegistry` 到业务组件。**
>
> 必须通过三件套模式隔离：
> - `IXxxMetrics` 接口 → 业务组件只依赖接口
> - `XxxMetricsImpl` → 构造函数中使用 `GlobalMeterRegistry.instance()` 预创建 meter
> - `EmptyXxxMetrics` → no-op fallback，作为字段默认值
>
> `GlobalMeterRegistry` 是 Nop 管理的统一 MeterRegistry 入口，在 `nop-commons` 中定义。
> 它在运行时由 Quarkus/Spring 集成层替换为真实 registry。

## 已有参考实现

| 模块 | 接口 | 实现 | 空实现 |
|------|------|------|--------|
| nop-orm | `IOrmMetrics` | `OrmMetricsImpl` | `EmptyOrmMetricsImpl` |
| nop-task | `ITaskFlowMetrics` | `TaskFlowMetricsImpl` | `EmptyTaskFlowMetrics` |
| nop-batch | — | `BatchTaskMetricsImpl` | — |
| nop-job | `IJobPlannerMetrics` | `JobPlannerMetricsImpl` | `EmptyJobPlannerMetrics` |

## 审计检查模式

在 review 或实现 metrics 时检查：

1. ❌ 业务组件是否直接 `import io.micrometer.core.instrument.MeterRegistry`？
2. ❌ 是否在业务方法内调用 `registry.counter(name)` / `registry.timer(name)`（每次 lookup）？
3. ✅ Counter/Timer 是否在构造函数中预创建并存储为 final field？
4. ✅ 业务组件是否通过领域指标接口（如 `IOrmMetrics`）访问 metrics？
5. ✅ 是否提供了 Empty 空实现作为默认值？
6. ✅ beans.xml 是否配置了真实实现的注入？
