# 维度06：Delta定制合规性

## 第 1 轮（初审）

### 检查范围

已检查 nop-job 模块所有子模块的 `src/main/resources/_vfs/` 目录：
- nop-job-app, nop-job-coordinator, nop-job-dao, nop-job-meta: 无 Delta 目录
- nop-job-retry-adapter, nop-job-service, nop-job-web: 无 Delta 目录
- nop-job-worker: 存在 1 个 Delta 文件

### 发现数量

**共发现 1 个问题（P2 级别）**

---

### 发现 1: Delta 文件路径对应原始文件不存在，可能造成跨模块覆盖

**文件路径**: `nop-job/nop-job-worker/src/main/resources/_vfs/_delta/default/nop/job/beans/app-engine.beans.xml`

**行号范围**: 1-19

**证据代码片段**:
```xml
<?xml version="1.0" encoding="UTF-8" ?>
<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       xmlns="http://www.springframework.org/schema/beans" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://www.springframework.org/schema/beans
        http://www.springframework.org/schema/beans/spring-beans-2.5.xsd"
       x:extends="super">

    <bean id="io.nop.job.worker.engine.IJobInvokerResolver"
          class="io.nop.job.worker.engine.DefaultJobInvokerResolver"/>

    <bean id="io.nop.job.worker.engine.IJobExecutionContextBuilder"
          class="io.nop.job.worker.engine.DefaultJobExecutionContextBuilder"/>

    <bean id="io.nop.job.worker.engine.IJobWorkerScanner"
          class="io.nop.job.worker.engine.JobWorkerScannerImpl"/>

    <bean id="io.nop.job.worker.engine.JobWorker"
          class="io.nop.job.worker.engine.JobWorker"/>
</beans>
```

**严重程度**: P2（维护成本）

**现状**: Delta 文件使用了 `x:extends="super"`（符合规范），但 Delta 路径 `_vfs/_delta/default/nop/job/beans/app-engine.beans.xml` 对应的原始文件 `/nop/job/beans/app-engine.beans.xml` 在 nop-job-worker 模块中不存在。唯一存在的 `app-engine.beans.xml` 位于 nop-retry 模块（`/nop/retry/beans/app-engine.beans.xml`）。

**风险**:
1. Delta 文件试图覆盖不存在的原始文件，运行时可能找不到基础配置
2. 跨模块覆盖（覆盖 nop-retry 配置）不符合 Delta 设计意图
3. 模块依赖关系不明确，升级时可能产生配置冲突

**建议**:
1. 确认 Delta 文件的实际用途。如需为 nop-job-worker 定义 beans 配置，应创建常规配置文件（`_vfs/nop/job/beans/app-engine.beans.xml`）而非 Delta
2. 如需覆盖 nop-retry 配置，应在 nop-retry 模块创建 Delta
3. 根据 Nop 文档，Delta 用于"在已有产品或基础模块上做差量覆盖"，当前场景应优先使用非 Delta 方式

**误报排除**: 已确认 Delta 文件使用了 `x:extends="super"`；已确认 nop-job-worker 模块不存在对应的原始文件。

---

## 其他检查项（已确认合规）

1. **x:extends="super" 使用**: 仅在 Delta 文件中使用，符合规范。
2. **x:override 属性使用**: NopJobTask/NopJobFire 视图文件使用了 `x:override="remove"`，但这些是非 Delta 文件，属于正常页面定制。
3. **x:override="remove" 正确性**: 用于移除生成文件中的按钮，属于正常定制。
4. **循环继承**: 未发现循环继承问题。
5. **tag="not-gen" 标记**: 未发现使用场景，无问题。
6. **不必要的 Delta 使用**: 除发现 1 外，无其他 Delta 文件。

### 确认无 Delta 的模块

- nop-job-app, nop-job-coordinator, nop-job-dao, nop-job-meta
- nop-job-retry-adapter, nop-job-service, nop-job-web
