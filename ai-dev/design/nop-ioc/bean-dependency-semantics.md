# nop-ioc Bean 依赖语义设计

**日期**：2026-08-07
**范围**：`nop-core-framework/nop-ioc` —— 依赖图构建、拓扑排序、Bean 实例化的强制创建语义
**状态**：active

---

## 一、设计结论

1. **`depends-on`、`ioc:before`、`ioc:after` 是同一依赖关系的三种声明形式**，归一化统一到一个**解析后的运行期集合** `resolvedDepends`（`BeanDefinition` 上的新字段）。声明形式不改动 model 的 `dependsOn`（声明即用户写的 `depends-on`）：

   | 声明 | resolvedDepends 贡献 | 语义 |
   |------|--------|------|
   | `A depends-on B` | A 包含 B | A 的前置是 B（在依赖方声明） |
   | `A ioc:before B` | B 包含 A | A 是 B 的前置（在前置方声明） |
   | `A ioc:after B` | A 包含 B | B 是 A 的前置（在依赖方声明，与 depends-on 等价） |

   `<ref>` 注入是独立的第三种形式：ref 目标若**拓扑序在 X 之前**（已确定先初始化），也加入 X 的 `resolvedDepends`，纳入"必须完整初始化"保证；否则仅作为属性引用，不保证完整初始化（并发下可能观察中间态）。声明 `ioc:after`/`ioc:before` **不再写入 model 的 `dependsOn`**——它们只在解析阶段归入 `resolvedDepends`。

2. **单一机制**：运行时强制创建只有一条路径——`BeanDefinition` 在 init-method 前遍历 `resolvedDepends`（替代原来的 `getBeanModel().getDependsOn()`）。创建 X 时强制创建 X 的全部在 resolved 集合中的前置（"依赖方拉动前置方"）。该集合**同时**驱动异步启动的任务排序（`asyncStartBeans`），两个消费方不再各持一份结构。

3. **`nextBeans` 机制整体移除**：`BeanContainerImpl` 构造函数不再据 `ioc:after` 设置 `nextBeans`，`getBean0` 不再前向强制创建，`BeanDefinition.nextBeans` 字段及其方法删除。它是与 `resolvedDepends` 方向相反、表达同一信息的冗余第二机制，与 dependsOn 强制创建形成双向强关联。

4. **`dependBeanIds` 字段删除、合并进 `resolvedDepends`**：`BeanTopologySorter` 不再 `setDependBeanIds(graph.getSourceVertexes(...))`；排序阶段直接读声明（`dependsOn` + `ioc:before`/`ioc:after`）+ refs（`collectDepends`）建图，排序后计算 `resolvedDepends` 并赋值。`asyncStartBeans` 改读 `getResolvedDepends()`。

5. **`ioc:before`/`ioc:after` 引用的 beanId 不存在时静默跳过**（DEBUG 日志），不报错。引用目标可能被 `ioc:condition` 禁用、位于父容器、或由可选模块提供。

6. **顺序约束必须得到保证**：拓扑排序后、填充 `resolvedDepends` 之前，显式校验每条 before/after 约束——两个 bean 都存在时，最终顺序必须满足声明，否则报错。**不能依赖环检测**：`nop.ioc.bean-depends-graph.allow-cycle` 默认 `true`（`IocConfigs.java:65-66`），环被静默容忍。校验在补充集合之前执行，二者互不干扰。

## 二、背景与动机

### 2.1 双机制冗余

当前 `ioc:after` 存在**两条**强制创建路径，表达同一信息、方向相反：

- **路径 A（dependsOn fold）**：`BeanDependsBuilder` 把 `A ioc:after B` 归一化为 `A.dependsOn += B`。创建 A 时经 `BeanDefinition` 的 dependsOn 循环强制创建 B。
- **路径 B（nextBeans 前向联动）**：`BeanContainerImpl` 构造函数读 `ioc:after` 设置 `B.nextBeans += A`。创建 B 时经 `getBean0` 前向强制创建 A。

结果 `ioc:after` 形成**双向强制创建**（创建任一方都会创建另一方），而 `ioc:before` 只有单向（经 fold 的 `B.dependsOn += A`）。两份结构、不一致的方向、冗余的运行时路径，是需要消除的机制重复。

### 2.2 顺序约束无保证

排序阶段 `BeanTopologySorter` 的环检测受 `allow-cycle` 配置控制，而该配置默认 `true`。因此 before/after 误配置（如 `A ioc:before B` 与 `B ioc:before A` 同存）时，排序不报错、静默给出违反约束的顺序。顺序约束若需要"必须保证"，就必须有独立于环检测的显式校验。

## 三、核心设计

### 3.1 统一语义契约

所有非引用依赖归一化到一个**运行期解析集合** `resolvedDepends`，行为一致：

- **图边**：排序阶段吸收 `dependsOn` + `ioc:before`/`ioc:after` + refs（`collectDepends`），每条前置 B 引入边 B→X（B 先于 X 初始化）。排序**不写** model 的 `dependsOn`。
- **resolvedDepends**：排序完成后计算 = 声明的 `depends-on` + before/after 推导 + 拓扑序在 X 之前的 ref 目标。计算后不再依赖 model 的 `dependsOn`（声明保持用户原始写法）。
- **强制创建**：创建 X 时，init-method 前遍历 `resolvedDepends` 强制创建每个前置（在 eager 与 lazy 模式都成立）。
- **声明侧糖**：`ioc:before` 允许在前置方声明"我是谁的前置"；`ioc:after` 允许在依赖方声明"我的前置是谁"（等价 `depends-on`）。归一化后无行为差异。
- **ref 的分级语义**：ref 目标拓扑序在 X 之前的，加入 `resolvedDepends`（保证完整初始化）；不在之前的只作属性引用（不保证完整初始化，对应并发下可能观察中间态的既有行为）。

### 3.2 单一强制创建路径

```
对每个 bean X 实例化时：
  遍历 resolvedDepends(X) 强制创建目标        // 唯一声明性来源（新）
  属性注入时强制创建 ref 目标               // 引用依赖（保留）
  // 不存在基于 ioc:before/ioc:after 的第二条路径
  // nextBeans 机制已整体移除
```

图边（排序）与强制创建（运行时）解耦但同源：排序阶段图边来源 = `dependsOn` + `ioc:before`/`ioc:after` + ref（`collectDepends`）；排序后 `resolvedDepends` = declared depends + before/after 推导 + 拓扑靠前的 ref，供强制创建循环与 `asyncStartBeans` 共用。

### 3.3 排序后顺序校验 + resolvedDepends 填充（必须保证）

拓扑排序产出最终顺序后，依次执行（**均不受 `allow-cycle` 配置影响**）：

```
1. 顺序校验：对每个 bean X、对 X.iocBefore 中的每个 B 与 X.iocAfter 中的每个 B（B 存在时）：
      X.iocBefore  → pos(X) < pos(B) 必须成立，否则报错
      X.iocAfter   → pos(X) > pos(B) 必须成立，否则报错

2. 填充 resolvedDepends：
    resolvedDepends(X) =
        声明的 dependsOn(X)
      ∪ {B | B ∈ X.iocAfter}                    // B 是 X 的前置
      ∪ {A | X ∈ A.iocBefore}                   // A 是 X 的前置
      ∪ {R | R 是 X 的 ref 目标 且 pos(R) < pos(X)}
```

> 方向速记：`A ioc:before B` ⇒ A 先于 B ⇒ A 进入 B 的 resolvedDepends；`A ioc:after B` ⇒ B 先于 A ⇒ B 进入 A 的 resolvedDepends。即"谁被声明先创建"，谁进入后创建者的 resolvedDepends。

- 校验先于填充执行：声明的 before/after 恶意环（如 `A ioc:before B` + `B ioc:before A`）在步骤 1 被捕获，环中必有一条约束被违反，不会进入步骤 2。
- **存在性校验不对称**：`depends-on` 是**强声明**——目标缺失在加载期直接报错（`ERR_IOC_UNKNOWN_DEPEND_REF`，`BeanDefinitionBuilder.checkDependRef`），因为 `resolvedDepends` 的强制创建循环在运行时必然依赖它，早报错优于运行时失败。`ioc:before`/`ioc:after` 是**弱声明**——目标缺失静默跳过（被条件禁用/父容器/可选模块提供时不报错）。但无论强弱，只要目标存在，其顺序约束都必须成立（步骤 1 校验覆盖三种声明）。
- ref 目标的拓扑序过滤使循环 ref（并发下无初始化保证的既存行为）不被误提升为"必须完整初始化"。

### 3.4 生产用法影响评估

仓库中 `ioc:before`/`ioc:after` 共 8 处生产用法，**全部位于 eager 初始化 bean**（相关 `beans.xml` 均未设 `default-lazy-init`）：`nop-biz` `biz-defaults.beans.xml`、`nop-sys-dao` `app-dao.beans.xml`、`nop-orm-geo` `nop-orm-geo.beans.xml`、`nop-orm` `orm-defaults.beans.xml`（4 处，部分带 `ioc:condition`）、`nop-dbtool` `dbtool-defaults.beans.xml`。

- **排序保证**：eager 模式下 `container.start()` 按拓扑序创建，顺序约束由排序满足；新增校验为显式保障，行为不变。
- **强制创建**：`resolvedDepends` 在 eager 模式下与旧 `dependsOn` 强制创建结果一致（ref/拓扑过滤在同层全量创建下同样被满足）。
- 唯一行为变化在 lazy 模式：`nextBeans` 前向联动消失（创建 B 不再连带创建 A）。这些 bean 均非 lazy，无生产可观测变化。
- 现有 8 处用法均为单向约束、无环，新增校验不会误报——**但校验落地时在 `nop-orm-geo` 暴露了一个既有缺陷并已修复**：`H2GisInitializer ioc:before="nopOrmSessionFactory"` 指向的是 `ioc:default` bean（实际 id 带 `$DEFAULT$` 前缀，声明用别名）。旧代码在 `BeanTopologySorter` 建边与 `resolvedDepends` 填充时用**原始字符串** `other.getIocBefore().contains(bean.getId())` 反向匹配，别名与归一化 id 不相等导致 before 边**从未建立**，`H2GisInitializer` 实际顺序错位（字母序上 `$DEFAULT$` 排在前）。已改为对声明值逐个 `normalizeBeanId` 后与 `bean.getId()` 比较（`BeanTopologySorter` 三处：校验、`fillResolvedDepends`、`sortBeans`）。`ioc:after`/`depends-on` 走 `deps.addAll` + 后续 `normalizeBeanId` 路径，本不受影响。

### 3.5 约束与边界

- **强制创建的声明性来源是 `resolvedDepends` 与 ref 属性赋值**；二者是唯一强制创建路径。
- **`ioc:before`/`ioc:after` 不修改 model 的 `dependsOn` 声明**——只在排序读取与 `resolvedDepends` 填充中生效，避免改写用户声明的副作用。
- **缺失目标的处理不对称**：`depends-on` 缺失必须报错（强声明，运行时强制创建必然失败）；`ioc:before`/`ioc:after` 缺失不报错（弱声明，与 `ioc:condition`、父容器、可选模块的宽松语义一致）。存在性校验发生在加载期（`BeanDefinitionBuilder`），顺序校验发生在排序后（`BeanTopologySorter`）。
- **顺序约束必须保证**：目标存在且顺序不满足即报错（三种声明一致），不受 `allow-cycle` 配置影响。
- **`depends-on` 的强制创建环节必须保留**——lazy 模式下无字段数据依赖得以实例化的唯一机制。

## 四、拒绝了什么

1. **保留 `nextBeans` 作为第二机制（或改名为 `beforeBeans` 继续存在）。**
   拒绝理由：`nextBeans` 与前置依赖归一化表达同一信息、方向相反，形成双向强制创建与不一致的语义；`beforeBeans` 需要第二套存储结构与第二个强制创建循环，等于再造一个机制。归一化到单一机制才是本质解。

2. **继续把 before/after 写进 model 的 `dependsOn`。**
   拒绝理由：改写用户声明后，dump 出的 merged 配置会把用户未写明的依赖混入 `depends-on`，排查与 delta 叠加都会失真。改为填充运行期字段 `resolvedDepends`，model 保持用户原样。

3. **把 `ioc:before`/`ioc:after` 降级为纯排序约束（无强制创建）。**
   拒绝理由：`ioc:after` 的语义是"A 依赖 B"（B 是 A 的前置），创建 A 时必须保证 B 已初始化——这与 `depends-on` 同源；降级后 lazy 模式下创建 A 不再联动 B，违背前置依赖契约。统一为强制创建是正确语义。

4. **仅依赖环检测保证 before/after 顺序。**
   拒绝理由：`nop.ioc.bean-depends-graph.allow-cycle` 默认 `true`，环被静默容忍，before/after 环给出违反约束的任意顺序。必须增加独立于该配置的排序后显式校验。

5. **对缺失的 `ioc:before`/`ioc:after` 目标报错。**
   拒绝理由：目标常被 `ioc:condition` 禁用（如 `orm-defaults.beans.xml` 中带 `ioc:condition` 的 initializer）、位于父容器、或由可选模块提供。报错会与条件 Bean、跨容器引用的宽松语义冲突。仓库中曾定义 `ERR_IOC_UNKNOWN_IOC_BEFORE`/`ERR_IOC_UNKNOWN_IOC_AFTER`，从未被引用，应删除。

6. **把 `depends-on` 降级为纯图元数据（移除强制创建循环）。**
   拒绝理由：`depends-on` 是"没有字段的 ref"，表达数据依赖，运行时必须强制创建目标。降级后 lazy 模式下无字段的数据依赖无法联动创建。

7. **保留 `dependBeanIds` 与 `resolvedDepends` 两个近似字段。**
   拒绝理由：信息重合（都是"X 创建前必须已创建的 bean 集"），双字段必然漂移；且 `dependBeanIds` 名称无法表达"前置方进后创建者"的解析方向。统一为 `resolvedDepends`，供 `newObject` 强制创建与 `asyncStartBeans` 任务排序共用，单一存储。

## 五、与已有设计的关系

本设计把三种声明统一为一种前置依赖机制（`resolvedDepends`），删除 `nextBeans` 与 `dependBeanIds`，补齐独立于 `allow-cycle` 的顺序校验。不引入新对外接口；`beans.xdef` 注释更新并重新生成，`BeanContainerDumper` 输出 `ext:resolved-depends`。

### 源码锚点

| 职责 | 位置 |
|------|------|
| before/after 读取与 resolvedDepends 填充（替代原 BeanDependsBuilder：不再写 model/nextBeans） | `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanTopologySorter.java` |
| nextBeans 设置循环（待移除） | `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java`（构造函数，约 100-109 行） |
| nextBeans 前向强制创建（待移除） | `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java`（getBean0，约 411-415 行） |
| nextBeans 字段与方法 + dependBeanIds 字段（待移除） | `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanDefinition.java`（121/197-206 行；119/175-181 行） |
| resolvedDepends 字段（待新增） + 强制创建循环改读它 | `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanDefinition.java`（newObject，init-method 前） |
| 依赖图构建 + 排序 + 排序后校验 + resolvedDepends 计算赋值 | `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanTopologySorter.java` |
| 环检测配置（默认 true，校验不可依赖它） | `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/IocConfigs.java`（CFG_IOC_BEAN_DEPENDS_GRAPH_ALLOW_CYCLE） |
| asyncStartBeans 任务排序改读 resolvedDepends | `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java`（asyncStartBeans，约 566-581 行） |
| ref 强制创建 | `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/resolvers/InjectRefValueResolver.java` |
| 未用错误码删除 + 新顺序校验错误码添加 | `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/IocErrors.java` |
| dump 合并定义输出 `ext:resolved-depends` | `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerDumper.java` |
| ioc:before/ioc:after 语义注释更新 + 重新生成模型 | `beans.xdef`（生成 `BeanValue.java`） |
