# nop-ioc Bean 依赖语义设计

**日期**：2026-08-07
**范围**：`nop-core-framework/nop-ioc` —— 依赖图构建、拓扑排序、Bean 实例化的强制创建语义
**状态**：active

---

## 一、设计结论

1. **`depends-on`、`ioc:before`、`ioc:after` 是同一依赖关系的三种声明形式**，统一归一化到"前置依赖"关系（`dependsOn`：本 bean 初始化前必须已初始化的 bean 集合）：

   | 声明 | 归一化 | 语义 |
   |------|--------|------|
   | `A depends-on B` | `A.dependsOn += B` | A 的前置是 B（在依赖方声明） |
   | `A ioc:before B` | `B.dependsOn += A` | A 是 B 的前置（在前置方声明） |
   | `A ioc:after B` | `A.dependsOn += B` | B 是 A 的前置（在依赖方声明，与 depends-on 等价） |

   `<ref>` 注入是独立的第四种形式：进依赖图 + 强制创建 + **保存引用**；其余三种不保存引用。

2. **单一机制**：运行时强制创建只有一条路径——`BeanDefinition` 在 init-method 前遍历 `dependsOn`。创建 X 时强制创建 X 的全部前置依赖（"依赖方拉动前置方"）。`ioc:before`/`ioc:after` 与 `depends-on` 行为完全一致，不再有第二套前向联动机制。

3. **`nextBeans` 机制整体移除**：`BeanContainerImpl` 构造函数不再据 `ioc:after` 设置 `nextBeans`，`getBean0` 不再前向强制创建，`BeanDefinition.nextBeans` 字段及其方法删除。原因是它与 `dependsOn` fold 表达同一信息（方向相反），双机制造成冗余且互相矛盾的双向强制创建。

4. **`ioc:before`/`ioc:after` 引用的 beanId 不存在时静默跳过**（DEBUG 日志），不报错。引用目标可能是被 `ioc:condition` 禁用、位于父容器、或由可选模块提供。

5. **顺序约束必须得到保证**：拓扑排序后显式校验每条 before/after 约束——两个 bean 都存在时，最终初始化顺序必须满足声明，否则报错。**不能依赖环检测**：`nop.ioc.bean-depends-graph.allow-cycle` 默认值为 `true`（`IocConfigs.java:65-66`），环检测默认不生效，误配置造成的 before/after 环会被静默容忍并给出违反约束的任意顺序。

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

所有非引用依赖归一化为"前置依赖"（`dependsOn`），行为一致：

- **图边**：`dependsOn` 中的每个前置 B 引入边 B→X（B 先于 X 初始化）。
- **强制创建**：创建 X 时，init-method 前遍历 `dependsOn` 强制创建每个前置（与 `depends-on` 语义一致，在 eager 与 lazy 模式都成立）。
- **声明侧糖**：`ioc:before` 允许在前置方声明"我是谁的前置"；`ioc:after` 允许在依赖方声明"我的前置是谁"（等价 `depends-on`）。归一化后无行为差异。

### 3.2 单一强制创建路径

```
对每个 bean X 实例化时：
  遍历 dependsOn(X) 强制创建目标        // 唯一声明性来源（保留）
  属性注入时强制创建 ref 目标            // 引用依赖（保留）
  // 不存在基于 ioc:before/ioc:after 的第二条路径
  // nextBeans 机制已整体移除
```

图边（排序）与强制创建（运行时）解耦：图边来源 = `dependsOn` + ref（`collectDepends`）；强制创建来源 = `dependsOn` 循环 + ref 属性注入。`ioc:before`/`ioc:after` 不引入任何额外运行时机制。

### 3.3 排序后顺序校验（必须保证）

拓扑排序产出最终顺序后，执行显式校验（**不受 `allow-cycle` 配置影响**）：

```
对每个 bean X：
  对 X.iocBefore 中的每个 B：
    若 X 与 B 都存在：pos(X) < pos(B) 必须成立，否则报错
  对 X.iocAfter 中的每个 B：
    若 X 与 B 都存在：pos(X) > pos(B) 必须成立，否则报错
```

- 缺失目标（被条件禁用/父容器/可选模块）不校验、不报错。
- 校验在排序完成后进行，使用最终全局顺序（含跨 `iocInitOrder` 分层顺序）。
- 违反时报错（新增错误码），给出 bean 与目标。
- 该校验同时保证：before/after 形成的环（如 `A ioc:before B` + `B ioc:before A`）必然被捕获——环中必有一条约束被违反。

### 3.4 生产用法影响评估

仓库中 `ioc:before`/`ioc:after` 共 8 处生产用法，**全部位于 eager 初始化 bean**（相关 `beans.xml` 均未设 `default-lazy-init`）：`nop-biz` `biz-defaults.beans.xml`、`nop-sys-dao` `app-dao.beans.xml`、`nop-orm-geo` `nop-orm-geo.beans.xml`、`nop-orm` `orm-defaults.beans.xml`（4 处，部分带 `ioc:condition`）、`nop-dbtool` `dbtool-defaults.beans.xml`。

- **排序保证**：eager 模式下 `container.start()` 按拓扑序创建，顺序约束由排序满足；新增校验为显式保障，行为不变。
- **强制创建**：fold 归一化保留，创建顺序与现状一致（`nextBeans` 前向联动在 eager 模式下本就冗余——被依赖方创建后，依赖方在排序中紧随其后，无论如何都会被创建）。
- 唯一行为变化在 lazy 模式：`nextBeans` 前向联动消失（创建 B 不再连带创建 A）。这些 bean 均非 lazy，无生产可观测变化。
- 现有 8 处用法均为单向约束，无环，新增校验不会误报。

### 3.5 约束与边界

- **强制创建的唯一声明性来源是 `dependsOn`**；`<ref>` 的强制创建由属性赋值机制保证。
- **`ioc:before`/`ioc:after` 与 `depends-on` 行为一致**，不再有独立的 nextBeans 前向联动。
- **缺失的顺序约束目标不报错**，与 `ioc:condition`、父容器、可选模块的宽松语义一致。
- **顺序约束必须保证**：存在且顺序不满足即报错，不受 `allow-cycle` 配置影响。
- **`depends-on` 的强制创建循环必须保留**——lazy 模式下无字段数据依赖得以实例化的唯一机制。

## 四、拒绝了什么

1. **保留 `nextBeans` 作为第二机制（或改名为 `beforeBeans` 继续存在）。**
   拒绝理由：`nextBeans` 与 `dependsOn` fold 表达同一信息、方向相反，形成双向强制创建与不一致的语义；`beforeBeans` 方案需要第二套存储结构与第二个强制创建循环，等于再造一个机制。归一化到 `dependsOn` 才能真正"避免两个机制"。

2. **把 `ioc:before`/`ioc:after` 降级为纯排序约束（无强制创建）。**
   拒绝理由：`ioc:after` 的语义是"A 依赖 B"（B 是 A 的前置），创建 A 时必须保证 B 已初始化——这与 `depends-on` 同源；降级后 lazy 模式下创建 A 不再联动 B，违背前置依赖契约。统一为 dependsOn 式强制创建是正确语义。

3. **仅依赖环检测保证 before/after 顺序。**
   拒绝理由：`nop.ioc.bean-depends-graph.allow-cycle` 默认 `true`，环被静默容忍，before/after 环给出违反约束的任意顺序。必须增加独立于该配置的排序后显式校验。

4. **对缺失的 `ioc:before`/`ioc:after` 目标报错。**
   拒绝理由：目标常被 `ioc:condition` 禁用（如 `orm-defaults.beans.xml` 中带 `ioc:condition` 的 initializer）、位于父容器、或由可选模块提供。报错会与条件 Bean、跨容器引用的宽松语义冲突。仓库中曾定义 `ERR_IOC_UNKNOWN_IOC_BEFORE`/`ERR_IOC_UNKNOWN_IOC_AFTER`，从未被引用，应删除。

5. **把 `depends-on` 降级为纯图元数据（移除强制创建循环）。**
   拒绝理由：`depends-on` 是"没有字段的 ref"，表达数据依赖，运行时必须强制创建目标。降级后 lazy 模式下无字段的数据依赖无法被联动创建。

## 五、与已有设计的关系

本设计将三种声明归一化为单一前置依赖机制（`dependsOn`），移除 `nextBeans` 第二机制，并新增独立于 `allow-cycle` 的顺序保证校验。不引入新对外接口。

### 源码锚点

| 职责 | 位置 |
|------|------|
| before/after 归一化为 dependsOn（fold，**保留**） | `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/loader/BeanDependsBuilder.java` |
| nextBeans 设置循环（待移除） | `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java`（构造函数，约 100-109 行） |
| nextBeans 前向强制创建（待移除） | `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java`（getBean0，约 411-415 行） |
| nextBeans 字段与方法（待移除） | `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanDefinition.java`（约 121/197-206 行） |
| 依赖图构建与拓扑排序（待加排序后校验） | `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanTopologySorter.java` |
| 环检测配置（默认 true，校验不可依赖它） | `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/IocConfigs.java`（CFG_IOC_BEAN_DEPENDS_GRAPH_ALLOW_CYCLE） |
| dependsOn 强制创建循环（保留） | `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanDefinition.java`（newObject，init-method 前） |
| ref 强制创建 | `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/resolvers/InjectRefValueResolver.java` |
| 未用错误码（待删除）/ 新错误码（待添加） | `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/IocErrors.java` |
| ioc:before/ioc:after 语义注释（待更新） | `beans.xdef`（代码生成产物 `BeanValue.java` 的来源） |
