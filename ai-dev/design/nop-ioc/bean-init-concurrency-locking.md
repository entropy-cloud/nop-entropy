# nop-ioc Bean 并发初始化加锁纪律设计

**日期**：2026-08-20
**范围**：`nop-core-framework/nop-ioc` —— 并发 bean 初始化/生命周期推进的加锁纪律
**状态**：active

---

## 一、设计结论

1. **核心原则：任何容器锁（`BeanCreationContext`、`ProducedBeanInstance`、`BeanDefinition`）在持有时不得回调用户代码（init 方法、属性赋值、延迟方法），持锁期间只做短临界区（状态检查/登记/推进）。** 回调一律在锁外执行。

2. **`ProducedBeanInstance.runUntil` 改为 owner 线程 + wait/notify 状态机**：
   - 第一个到达未完成状态 bean 的线程成为该轮推进的 **owner**；owner 在**锁外**执行 init/lazy/delay 回调。
   - 其他线程遇到未完成状态的 bean 时在条件变量上 `wait()`（释放 bean 的 monitor），等待 owner 推进完成，**绝不参与初始化执行**。
   - 同线程重入（循环依赖/自引用）通过 owner 判定（`initThread == 当前线程`）继续推进，保持 Java 可重入语义。
   - owner 执行回调异常时记录异常并 `notifyAll`，等待者被唤醒后重新抛出（不会永久挂起）。
   - 新增中间状态 `PROPERTY_SET`（属性赋值完成）置于 `INITIALIZED` 之前：`checkBeanInitialized` 在属性赋值完成前等待，保证"属性先于 init"的既有顺序在不同线程路径下都被满足。
   - **阶段所有权**：`PROPERTY_SET` 阶段的回调（属性赋值）**仅由创建线程在创建路径内同步执行**（不在 `runUntil` 内，创建路径独占，无需 owner 协议）；`INITIALIZED`/`LAZY_PROPERTY_SET`/`DELAY_ACTION_RUN` 阶段回调按 first-comer owner 协议执行。`runUntil` 对 `INITIALIZED` 及以上的等待**隐式包含**对 `PROPERTY_SET` 的等待。
   - **异常传播覆盖所有阶段回调**：`PROPERTY_SET` 回调（创建线程的 setupInstance）抛异常时同样记录 `initError` + `notifyAll`（状态保持未完成），等待者醒来检查并重抛——任何失败路径都不得让等待者永久挂起。
   - **bean 结果写回**：init 回调（可能为 beanMethod 产物/代理处理器）在锁外执行后，owner 必须在短临界区内把返回值写回 `bean` 字段再推进状态 + `notifyAll`，保证等待者经 monitor happens-before 读到最终对象。

3. **`BeanCreationContext` 的 `flushInit` / `flushActions` / `run*Actions` 改为"锁内快照、锁外执行"**：锁内完成按拓扑序的 drain（每个 action 恰好被取出一次），锁外运行 action。**drain 与登记（`add*Action`）都必须同步**（短临界区；写入侧用 `synchronized` 或 `ConcurrentSkipListMap`），只有 action **执行**移到锁外。任何线程都不再"持有 ctx 等待 bean"，环中的 ctx 侧被消除。

4. **`BeanContainerImpl.getBean0` 的 `synchronized(beanDef)` 块收缩**：锁内只做 `scope.get` + 实例创建 + 注册到 scope；属性赋值与 init 动作登记移到锁外。并发线程看到 bean 在 scope 后走 `getBeanInstance` 路径时，由 `PROPERTY_SET` 状态门控等待属性赋值完成。

5. **约束保留**：bean 在初始化完成前保持对容器可见（`newObject` 创建后立即加入 scope）——这是循环依赖/自引用/生产者 bean 的既有语义，不可更改；并发访问者从"加入初始化"变为"等待初始化完成"。

## 二、背景与动机

### 2.1 死锁实况（2026-08-20 现场取证）

`nop-metadata-service` 测试 fork JVM 中发生真实 Java 死锁，jstack 死锁检测明确报告：

```
main:  waiting to lock ProducedBeanInstance(ce50e508)，held by nop-job-local-scheduler-15-1
nop-job-local-scheduler-15-1: waiting to lock BeanCreationContext(ce50c570)，held by main
```

两条线程路径（锁获取顺序相反）：

- **main 线程**（容器启动 `BeanContainerImpl.start` → `startBean`）：`getBean0` → `getBeanInstance` → `flushInit(ctxA)`（先持 ctxA）→ 运行 init action `P::checkBeanInitialized` → `runUntil(P)`（要持 P）→ 阻塞。
- **scheduler 线程**（容器启动期间 `LocalJobScheduler` 已开始执行定时任务，任务回调容器）：自己的 ctx 路径 `getBean` → `checkBeanInitialized` → `runUntil(P)`（先持 P，因为 P 已提前可见于 scope）→ `initFunc`（捕获的闭包持有 ctxA）→ `initBean` → `getBean(dep, false, ctxA)` → `getBeanInstance` → `flushInit(ctxA)`（要持 ctxA）→ 阻塞。

构成循环等待：main 持 ctxA 等 P；scheduler 持 P 等 ctxA。

### 2.2 根因

1. **回调持锁**：`runUntil` 在持有 P 的 monitor 时执行 `initFunc`；`flushInit` 在持有 ctxA 的 monitor 时执行 init action。回调会重入容器并可能阻塞在其他 monitor 上，形成锁序反转。
2. **ctx 跨线程共享**：`BeanDefinition.createInstance` 把 `beanCtx` 捕获进 init 闭包，任何线程执行该 bean 的初始化都使用创建时的 ctx。
3. **提前可见**：`createInstance` 在初始化完成前把 `ProducedBeanInstance` 放入 scope，另一线程能拿到该实例并"加入"初始化（在旧实现里是持锁重入）。

三点共同作用才能形成此环；前两点是缺陷，第三点是必须保留的语义。

### 2.3 触发场景

容器 `start()` 尚未完成（`started=false`）时，`LocalJobScheduler` 等 bean 的 delay-method / 启动逻辑已让调度线程跑起来，其任务执行路径回调容器 `getBean`，撞进 main 线程正在初始化的 bean 生命周期内。在测试中叠加 surefire 并行 fork 共享 Lucene 索引目录的文件锁，最终表现为永久挂起。

## 三、核心设计

### 3.1 状态机（ProducedBeanInstance）

生命周期状态扩展为：`CREATED → PROPERTY_SET → INITIALIZED → LAZY_PROPERTY_SET → DELAY_ACTION_RUN`。

- 每次状态推进由 owner 线程在锁外执行回调后于短临界区内更新状态并 `notifyAll`。
- 非 owner 线程在条件变量上等待当前阶段推进到目标状态。
- 异常传播：owner 捕获回调异常 → 记录到实例 → `notifyAll` → 重抛给 owner 调用者；等待者被唤醒后检测异常字段并抛出。

### 3.2 回调全链路锁外执行

所有可能重入容器的回调（init 方法、lazy 属性赋值、delay 方法、属性 ref 解析）都不在容器锁内执行。容器锁只保证：状态登记、scope 注册/读取、action 快照的原子性。

### 3.3 拒绝的替代方案

1. **把提前可见改为"初始化完成前不可见"**：拒绝。循环依赖、自引用、生产者 bean 依赖该语义，是硬约束。
2. **`BeanCreationContext` 改为线程私有（thread-local）**：拒绝。ctx 承载拓扑序的 action 队列，跨线程拆分会破坏顺序保证，且 init 闭包捕获的 ctx 本就跨线程，改动面失控。
3. **为 bean 初始化加全局串行锁**：拒绝。放大串行化，违背 IoC 并发初始化目标，且不解决"回调持锁"这一根因。
4. **仅靠测试侧规避（推迟 job 调度器启动）**：只消除当前触发场景，不修复框架级锁缺陷；作为附加加固而非核心方案。
5. **仅在 `flushInit` 释放 ctx 锁（不动 `runUntil`）**：不完整。`runUntil` 持 P 调 initFunc 是环的另一半，且 P↔P' 之间（两个 bean 互相等待）的锁序反转依然存在。必须两侧同时改。

## 四、与已有设计的关系

本文档是 `bean-dependency-semantics.md`（依赖语义/拓扑序/强制创建）的并发安全补充：依赖语义定义"创建顺序与强制创建"，本文档定义"并发推进同一 bean 生命周期时的加锁纪律"。两者共同约束 `BeanDefinition.createInstance` / `setupInstance` / `getBeanInstance` / `BeanCreationContext` / `ProducedBeanInstance` 的行为。

### 源码锚点

| 职责 | 位置 |
|------|------|
| 生命周期状态机（runUntil/checkBean*） | `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/ProducedBeanInstance.java` |
| 动作队列与推进（flushInit/flushActions/run*Actions） | `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanCreationContext.java` |
| bean 创建入口与 beanDef 锁 | `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java`（getBean0，约 367-397 行） |
| init 闭包捕获 ctx + 属性赋值 + init 动作登记 | `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanDefinition.java`（createInstance，约 512-557 行；setupInstance，约 559-598 行；initBean，约 600-641 行；getBeanInstance，约 710-723 行） |
| 回归测试参照（resolvedDepends 重入语义） | `nop-core-framework/nop-ioc/src/test/java/io/nop/ioc/TestReentrantCircularRepro.java` |

## 五、行为契约（不变项）

- bean 生命周期推进的先后顺序（属性 → init → lazy 属性 → delay）在任何线程路径下都成立。
- `getBean(name, false)` 返回**完整初始化**的 bean（等待而非加入）。
- `getBean(name, true)` 返回**可提前可见**的 bean（循环依赖/自引用场景）。
- 依赖/强制创建/拓扑序语义与 `bean-dependency-semantics.md` 一致，不变。
- 并发初始化同一 bean 不再死锁；异常被完整传播。
- 同线程在自身 init/lazy/delay 阶段回调中再次 `getBean(自身, false)` 属循环自依赖，抛出 `ERR_IOC_BEAN_INIT_SELF_WAIT`（旧实现为重复执行 init 的 bug 行为；正常依赖路径经依赖拓扑序/`includeCreating=true` 不可达）。