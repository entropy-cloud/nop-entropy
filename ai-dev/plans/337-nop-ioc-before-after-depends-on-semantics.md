# 337 nop-ioc before/after 与 depends-on 语义统一

> Plan Status: completed
> Last Reviewed: 2026-08-07
> Source: `ai-dev/design/nop-ioc/bean-dependency-semantics.md`
> Related: —

## Purpose

把 `ioc:before`/`ioc:after`/`depends-on` 统一为**单一前置依赖机制 `resolvedDepends`**（单一强制创建路径 + 单一任务排序来源）：移除 `nextBeans` 与 `dependBeanIds` 两个机制/字段，`ioc:before`/`ioc:after` 不再改写 model 的 `dependsOn`，新增独立于 `allow-cycle` 配置的排序后顺序校验，并清理从未被引用的错误码。详见设计文档 `ai-dev/design/nop-ioc/bean-dependency-semantics.md`。

## Current Baseline

- 模型中 `ioc:before`、`ioc:after`、`depends-on` 是三个**独立字段**（`_BeanValue.java:88`/`116`/`388`，`_gen` 生成）。
- **fold（待重构，不再写 model）**：`BeanDependsBuilder`（`loader/BeanDependsBuilder.java:19-43`）当前把 before/after 归一化为 `dependsOn`（`A ioc:after B`→`A.dependsOn+=B`；`A ioc:before B`→`B.dependsOn+=A`），**改写 model 声明**。本计划改为：model 保持原样，before/after 只在排序阶段读取并归入 `resolvedDepends`。
- **nextBeans（待移除）**：`BeanContainerImpl` 构造函数（`impl/BeanContainerImpl.java:100-109`）据 `ioc:after` 设置 `B.nextBeans+=A`；`getBean0`（`:411-415`）新建 bean 后前向强制创建其 `nextBeans`；`BeanDefinition.nextBeans` 字段（`:121`）与 `getNextBeans`/`addNextBean`（`:197-206`）。`addNextBean` 全仓库唯一调用点是 `BeanContainerImpl.java:105`。
- **dependBeanIds（待删除，合并进 resolvedDepends）**：`BeanTopologySorter`（`impl/BeanTopologySorter.java:109`）在 `sortBeans` 中给 beanDef 赋 `dependBeanIds`（依赖图源顶点的 id 集）；`BeanContainerImpl.asyncStartBeans`（`:576`）读 `getDependBeanIds()` 决定异步任务序；`BeanDefinition.dependBeanIds` 字段（`:119`）与 getter/setter（`:175-181`）。与计划中的 `resolvedDepends` 信息重合，统一为单一字段。
- **顺序无保证**：`BeanTopologySorter.sortBeans`（`impl/BeanTopologySorter.java:115-128`）的环检测受 `CFG_IOC_BEAN_DEPENDS_GRAPH_ALLOW_CYCLE` 控制，而该配置**默认 `true`**（`IocConfigs.java:65-66`），环默认被静默容忍。
- `BeanDefinition.newObject`（`impl/BeanDefinition.java:532-538`）在 init-method 前遍历 `getBeanModel().getDependsOn()` 强制创建（**保留，但改读 `getResolvedDepends()`**）。
- `InjectRefValueResolver.resolveValue`（`resolvers/InjectRefValueResolver.java:72-78`）属性注入时经 `getBean(ref, true)` 强制创建 ref 目标；ref 目标按拓扑序过滤后部分进入 `resolvedDepends`（见设计 §3.3）。
- `IocErrors.java:225-229` 定义 `ERR_IOC_UNKNOWN_IOC_BEFORE`/`ERR_IOC_UNKNOWN_IOC_AFTER`，全仓库**无任何引用**；`ARG_BEFORE`/`ARG_AFTER` 仅被这两处使用（`:226`/`:229`）。
- 现有测试**没有**覆盖 `ioc:before`/`ioc:after`（`src/test` 下无引用）；lazy 测试模式：`MyLazyInitBean` 系列 + `createdCount` 静态计数 + `AppBeanContainerLoader.loadFromResource` + `container.start()`。
- **生产用法**：8 处 `ioc:before`/`ioc:after`，全部位于 eager bean（相关 `beans.xml` 均未设 `default-lazy-init`）：`nop-biz`、`nop-sys-dao`、`nop-orm-geo`、`nop-orm`（4 处，部分带 `ioc:condition`）、`nop-dbtool`。均为单向约束、无环。
- 设计文档 `ai-dev/design/nop-ioc/bean-dependency-semantics.md` 已落地（resolvedDepends 版，2026-08-07）。
- 真正剩余的 gap：存在 `nextBeans` 冗余第二机制；`dependBeanIds` 与 `resolvedDepends` 双存储；before/after 顺序约束无保证（`allow-cycle` 默认 true）；fold 改写 model 声明；两个错误码是死代码；缺少 before/after 回归测试。

## Goals

- `ioc:before`/`ioc:after`/`depends-on` 统一为 `resolvedDepends`（= 声明的 dependsOn ∪ before/after 推导 ∪ ref 目标中拓扑序更小者），强制创建与异步任务排序共用。
- `ioc:before`/`ioc:after` **不再改写 model 的 `dependsOn`**；model 保持用户原样声明。
- before/after 顺序约束**必须保证**：排序后显式校验，存在且顺序不满足即报错（独立于 `allow-cycle`）。
- `depends-on` 的强制创建语义**保持不变**。
- before/after 引用的 beanId 不存在时静默跳过，不报错。
- 删除 `nextBeans` 与 `dependBeanIds`；删除两个从未引用的错误码；新增顺序校验错误码。
- 新增回归测试，可观测地证明上述行为；8 处生产用法所在模块测试不回归。

## Non-Goals

- 不改变 `depends-on`、`<ref>` 注入的语义与成功路径上的强制创建行为（`resolvedDepends` 在 eager 全量创建下与旧行为一致）。
- 不改变依赖图拓扑排序算法本身（只在其产出顺序后加校验与 `resolvedDepends` 填充）。
- 不修改 `allow-cycle` 配置的默认值或语义。
- 不重写 `nop-ioc` 子系统或调整 `BeanContainerBuilder` 构建步骤顺序。
- 不重新评估/修改 8 处生产 `beans.xml`。

## Scope

### In Scope

- `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java`：移除构造函数中据 `ioc:after` 设置 `nextBeans` 的循环（约 100-109 行）；移除 `getBean0` 中遍历 `nextBeans` 的循环（约 411-415 行）；`asyncStartBeans`（约 566-581 行）改读 `getResolvedDepends()`。
- `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanDefinition.java`：移除 `nextBeans`（`:121`/`:197-206`）与 `dependBeanIds`（`:119`/`:175-181`）；新增 `resolvedDepends` 字段与存取方法；`newObject` 强制创建循环改读 `getResolvedDepends()`。
- `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/loader/BeanDependsBuilder.java`：**删除**（其 before/after 与 nextBeans fold 逻辑不再需要；职责并入 `BeanTopologySorter`）。
- `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanTopologySorter.java`：排序产出最终顺序后，先对每个 bean 校验 before/after 顺序约束（见设计 §3.3，无条件执行、不受 `allow-cycle` 影响），再计算并填充各 bean 的 `resolvedDepends`（= 声明的 dependsOn ∪ before/after 推导 ∪ ref 目标中拓扑序更小者），替代现有 `setDependBeanIds`（`:109`）。
- `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerDumper.java`：合并定义输出 `ext:resolved-depends`。
- `IocErrors.java`：删除 `ERR_IOC_UNKNOWN_IOC_BEFORE`/`ERR_IOC_UNKNOWN_IOC_AFTER` 与 `ARG_BEFORE`/`ARG_AFTER`；新增顺序校验错误码（如 `ERR_IOC_BEAN_BEFORE_AFTER_ORDER_VIOLATION`，含 bean 与目标参数）。
- `beans.xdef`：更新 `ioc:before`/`ioc:after` 语义注释（统一为"前置依赖"描述，移除"创建之后立刻创建"旧语义），重新生成 `_gen/` 对应文件。
- `src/test`：新增 before/after、ref 拓扑过滤、resolvedDepends 的对比回归测试。

### Out Of Scope

- 修改 `_gen` 生成文件（只改 `beans.xdef` 源并通过生成流程刷新，禁止手改生成产物 `BeanValue.java`）。
- 变更 `nop.ioc.bean-depends-graph.allow-cycle` 默认值或语义。
- 为 `depends-on` 增加顺序校验（其契约是强制创建而非顺序；before/after 顺序校验覆盖"顺序必须保证"需求）。
- `docs-for-ai/` 无 ioc:before/after 相关内容（已核查）。

## Execution Plan

### Phase 1 - 移除 nextBeans/dependBeanIds，引入 resolvedDepends 单一机制

Status: completed
Targets: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java`, `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanDefinition.java`, `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/loader/BeanDependsBuilder.java`(删除), `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanTopologySorter.java`, `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerDumper.java`, `src/test/`

- Item Types: `Fix | Proof`

- [x] **Fix**：`BeanDefinition` 新增 `resolvedDepends` Set 字段与 `getResolvedDepends`/`addResolvedDepend`/`setResolvedDepends`；新增 dump 映射 `ext:resolved-depends`。
- [x] **Fix**：`BeanTopologySorter` 排序后遍历各 bean，填充 `resolvedDepends = 声明的 dependsOn(X) ∪ {B | B∈X.iocAfter} ∪ {A | X∈A.iocBefore} ∪ {R | R 是 X 的 ref 目标且 pos(R) < pos(X)}`；原 `setDependBeanIds`（`:109`）被该逻辑取代并删除。
- [x] **Fix**：删除 `BeanDependsBuilder`（不再 fold before/after 进 model 的 `dependsOn`，也不再为 `nextBeans` 提供数据）；before/after 原样留在 model，供排序阶段读取归入 `resolvedDepends`。model.dependsOn 保持用户声明的 `depends-on` 原值。
- [x] **Fix**：`BeanDefinition.newObject` 强制创建循环从 `getBeanModel().getDependsOn()` 改读 `getResolvedDepends()`；其余逻辑不变。
- [x] **Fix**：`BeanContainerImpl.asyncStartBeans`（约 566-581 行）从 `getDependBeanIds()` 改读 `getResolvedDepends()`。
- [x] **Fix**：移除 `BeanContainerImpl` 构造函数中据 `ioc:after` 设置 `nextBeans` 的循环（约 100-109 行）。
- [x] **Fix**：移除 `getBean0` 中 `for (String nextId : beanDef.getNextBeans())` 前向强制创建循环（约 411-415 行）。
- [x] **Fix**：移除 `BeanDefinition` 的 `nextBeans` 字段及 `getNextBeans`/`addNextBean` 方法；移除 `dependBeanIds` 字段及 getter/setter。
- [x] **Fix**：`BeanContainerDumper` 在合并定义中输出 `ext:resolved-depends`。
- [x] **Proof**：全仓库搜索 `nextBeans`/`getNextBeans`/`addNextBean`/`dependBeanIds`/`getDependBeanIds` 无残留引用（已核实唯一消费者在 nop-ioc 内，删除后复查）。
- [x] **Proof**：`resolvedDepends` 归并——beanA(`lazy-init=true`, `ioc:after="beanB"`) + beanB(`lazy-init=true`)；`getBean("beanA")` 后断言 beanB.createdCount==1（创建 A 强制创建前置 B）。
- [x] **Proof**：`nextBeans` 前向联动已移除——同一配置下 `getBean("beanB")` 后断言 beanA.createdCount==0（创建 B 不再连带创建 A）。
- [x] **Proof**：`ioc:before` 归并——beanZ(`lazy-init=true`, `ioc:before="beanA"`) + beanA(`lazy-init=true`)；`getBean("beanA")` 后断言 beanZ.createdCount==1（前置方进后创建者）。
- [x] **Proof**：ref 拓扑过滤——beanX(`lazy-init=true`, `<property>` ref=beanY) + beanY(`lazy-init=true`)；`getBean("beanX")` 后断言 beanY.createdCount==1（pos(Y)<pos(X) 时 ref 目标进 resolvedDepends，保证完整初始化）。
- [x] **Proof**：`depends-on` 强制创建保持不变——beanC(`lazy-init=true`, `depends-on="beanD"`) + beanD(`lazy-init=true`)；`getBean("beanC")` 后断言 beanD.createdCount==1。
- [x] **Proof**：asyncStartBeans 任务序改读 resolvedDepends——eager beanA(`ioc:after="beanB"`) + eager beanB，`container.start()` 后断言 beanB 先于 beanA 完成（async 排序仍尊重前置约束）。
- [x] **Proof**：model 不被改写——加载含 `ioc:after` 的配置后，`getBeanModel().getDependsOn()` 不包含 before/after 推导出的目标；`container.dump`（或 BeanContainerDumper）合并定义含 `ext:resolved-depends` 且值与断言一致。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 全仓库搜索 `nextBeans`/`getNextBeans`/`addNextBean`/`dependBeanIds` 无残留引用（代码可 grep）。
- [x] `BeanContainerImpl` 构造函数不再设置 nextBeans；`getBean0` 无 `getNextBeans` 调用；`asyncStartBeans` 读 `getResolvedDepends()`（代码可 grep）。
- [x] `resolvedDepends` 填充正确：before/after/ref 拓扑过滤三个 Proof 测试通过，断言与上文一致。
- [x] **端到端验证**：`container.start()` → 按需 `getBean()` 的完整路径已验证（lazy 前置强制创建 / 无前向联动 / async 排序）。
- [x] **接线验证**：强制创建确实走 `BeanDefinition.newObject` 的 `resolvedDepends` 循环（"getBean(A)→beanB==1"证明，而非仅类型/字段存在）。
- [x] **无静默跳过**：本 Phase 不引入空方法体/`continue` 绕过；被移除机制无残余半实现。
- [x] `_gen/` 未修改（本 Phase 不涉及）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 顺序校验与错误码

Status: completed
Targets: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanTopologySorter.java`, `IocErrors.java`, `src/test/`

- Item Types: `Fix | Proof`

* [x] **Fix**：在 `BeanTopologySorter.sort()` 产出最终顺序后（resolvedDepends 填充之前），对每个 bean 校验 `dependsOn`/`iocBefore`/`iocAfter` 三种声明：目标存在时 `pos(X) > pos(dependsOn)`/`pos(X) > pos(iocAfter)`、`pos(X) < pos(iocBefore)` 必须成立，否则抛新错误码。校验无条件执行（不受 `allow-cycle` 影响）。**存在性不对称**：depends-on 缺失在加载期报 `ERR_IOC_UNKNOWN_DEPEND_REF`（强声明，`BeanDefinitionBuilder.checkDependRef`）；before/after 缺失静默跳过（弱声明，目标可能被条件禁用/父容器/可选模块提供）。
* [x] **Fix**：新增顺序校验错误码（含 bean 与目标参数）。
* [x] **Fix**：删除 `ERR_IOC_UNKNOWN_IOC_BEFORE`、`ERR_IOC_UNKNOWN_IOC_AFTER` 与 `ARG_BEFORE`、`ARG_AFTER`（已确认无引用）。
* [x] **Proof**：正常顺序用例不误报——beanZ(`ioc:before="beanA"`) + beanA(非 lazy)，id 字母序与声明相反；断言最终顺序 beanZ 先于 beanA（只有约束边被真正消费时成立）。
* [x] **Proof**：顺序违例报错——误配置 before 环（`beanA ioc:before="beanB"` + `beanB ioc:before="beanA"`），在 `allow-cycle` 默认 true 时加载容器仍抛新错误码；depends-on 环同理。
* [x] **Proof**：缺失 before 目标不报错——beanM(`ioc:before="nonExistentBean"`)，容器加载与 `start()` 均不抛异常；缺失 depends-on 目标报 `ERR_IOC_UNKNOWN_DEPEND_REF`。

Exit Criteria:

* [x] 排序后校验在 `allow-cycle` 默认（true）下仍执行（"违例报错"测试通过即为证据）。
* [x] 正常顺序用例不误报（"正常顺序"测试通过）。
* [x] 新错误码已定义并抛出；旧错误码删除后全仓库无残留引用。
* [x] 三个 Proof 测试通过，断言与上文一致。
* [x] **无静默跳过**：校验路径在违反时显式抛错误码，而非 `continue`/忽略。
* [x] `ai-dev/design/nop-ioc/bean-dependency-semantics.md` 与实现一致（本会话已落地，Phase 内复核）。
* [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 注释同步与跨模块回归构建

Status: completed
Targets: `beans.xdef`, `_gen/` 生成物, 受影响模块测试, `ai-dev/logs/`

- Item Types: `Fix | Proof | Follow-up`

* [x] **Fix**：更新 `beans.xdef` 中 `ioc:after`/`ioc:before` 的语义注释（统一为"前置依赖"描述，移除"创建之后立刻创建"旧表述）。
* [x] **Fix**：重新生成 `_gen/` 模型（运行相应代码生成流程，禁止手改生成文件）。
* [x] **Proof**：跨模块回归——`./mvnw test -pl nop-persistence/nop-orm -am` 与 `./mvnw test -pl nop-core-framework/nop-ioc -am` 全绿（覆盖 8 处生产用法所在模块）。
* [x] **Proof**：`./mvnw clean install -T 1C -DskipTests` 编译通过（删除错误码、生成文件刷新、新错误码注册均不破坏编译）。
* [x] **Follow-up**：确认 `docs-for-ai/` 无 ioc:before/after 内容需同步，Phase 记录显式写 `No owner-doc update required`。

Exit Criteria:

* [x] `beans.xdef` 注释已更新且 `_gen/` 已重新生成（git diff 可观测）。
* [x] `nop-orm`、`nop-ioc` 模块测试全绿（含新增 Proof 测试）。
* [x] 编译通过，无对已删错误码/ARG 常量的残留引用。
* [x] `docs-for-ai/` 同步裁定已显式记录（`No owner-doc update required`）。
* [x] `ai-dev/logs/` 对应日期条目已更新。
* [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0。

## Closure Gates

> **关闭条件**：本 section 所有条目及每个 Phase 的 Exit Criteria 全部 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `nextBeans` 与 `dependBeanIds` 已整体移除，全仓库无残留引用。
- [x] before/after 顺序约束已保证（排序后校验，独立于 `allow-cycle`），由 Proof 测试锁定。
- [x] `resolvedDepends` 单一机制已就位：newObject 强制创建与 asyncStartBeans 任务序共用，model 的 `dependsOn` 不被改写，由 Proof 测试锁定。
- [x] `depends-on` 强制创建语义保持不变，由 Proof 测试锁定。
- [x] 两个未用错误码删除、新顺序校验错误码已使用，全仓库无残留。
- [x] 8 处生产用法所在模块（至少 nop-orm）测试不回归。
- [x] 必要 focused verification（Proof 测试 + 跨模块回归）已完成。
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope 项。
- [x] owner docs 已同步：设计文档已落地；`docs-for-ai/` 显式 `No owner-doc update required`；`beans.xdef` 注释与实现一致。
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [x] **Anti-Hollow Check**：closure audit 已验证（a）`ioc:after`→`resolvedDepends` 强制创建在运行时确实被触发（beanB==1）；（b）`getBean` 无前向创建残留；（c）顺序校验在默认 `allow-cycle=true` 下确实报错；（d）dump 输出 `ext:resolved-depends`。
- [x] `./mvnw compile -pl nop-core-framework/nop-ioc -am`
- [x] `./mvnw test -pl nop-core-framework/nop-ioc -am`
- [x] `./mvnw test -pl nop-persistence/nop-orm -am`（主会话收口复测 BUILD SUCCESS，nop-orm 138 tests 0 failures；审计 session 的失败未复现，详见 Closure 证据）
- [x] checkstyle / 代码规范检查通过（注：根 pom 中 checkstyle 插件整体注释禁用，非构建门禁；已手工核对改动文件 import 分组与风格一致）

## Deferred But Adjudicated

（暂无）

## Non-Blocking Follow-ups

- Phase 3 校验落地时暴露并修复既有缺陷：`ioc:before` 指向 `ioc:default` 别名 bean（`$DEFAULT$` 前缀）时，旧 `BeanTopologySorter` 反向匹配用原始字符串 `contains(bean.getId())`，别名与归一化 id 不相等导致 **before 边从未建立**（`nop-orm-geo` 的 `H2GisInitializer` 实际顺序错位）。已修复：建边与 `resolvedDepends` 填充均改为对声明值逐个 `normalizeBeanId` 后与 `bean.getId()` 比较，回归测试 `testBeforeAliasOfDefaultBean` 锁定（Anti-Hollow 已验证：回退即失败）。用例：`nop-ai` plan-329 closure 同理未覆盖下游消费者 `nop-wf-ai`（HEAD 编译破坏），本次已同步修复 `WfAiHelper` 的 `setResponseFormat` → `setResponseFormatConfig(ResponseFormat.jsonObject())` 并清理 `ChatOptions` 失效注释——此为 329 的 closure 覆盖漏洞，非本 plan 范围。
- 若未来希望对 `depends-on` 也提供"顺序必须保证"的校验，可扩展 §3.3 校验覆盖 `dependsOn` 边；当前其契约为强制创建而非顺序保证，非阻塞。
- 未来可考虑把 `ioc:before`/`ioc:after` 在 xdef 中直接标记为 `depends-on` 的别名声明，进一步收敛声明语法（非本计划）。

## Closure

Status Note: 独立 closure audit（fresh session，非实现 session 复用）于 2026-08-07 执行：逐文件 live-code 审查（BeanTopologySorter/BeanDefinition/BeanContainerImpl/IocErrors/BeanContainerDumper/beans.xdef/_gen/_BeanValue/WfAiHelper/ChatOptions/TestWfAiHelper/TestBeanDepends 及其 7 个 beans.xml 资源），全仓库 grep 残留扫描，6 组验证命令 + 2 组补充命令。**所有 plan 声称的代码/测试/文档状态与 live code 一致**。审计期间唯一未满足项 Gate 14（`test -pl nop-orm -am`）在审计 session 中报 28 errors；主会话收口时**独立复测该命令 → BUILD SUCCESS（nop-orm 138 tests，0 failures；整个 -am reactor 无失败）**，失败未复现。审计当时归因的 `nop-orm/pom.xml` 修改**实际不存在**（`git status` 无该文件改动），工作树唯一相关的其他-session WIP 为 `DataInitInitializer.java`/`TestDataInitInitializer.java`/`BaseTestCase.clearTestConfig`/nop-orm-geo 未跟踪测试目录——主会话复测下不影响 nop-orm 测试。故 16 个 Closure Gates 全部独立核验通过。
Completed: VERDICT: PASS（实现/测试/文档全部核验通过；16/16 Closure Gates 已核验，含主会话对 Gate 14 的独立复测）

Closure Audit Evidence:

**命令与结果（独立重跑）**：
1. `./mvnw test -pl nop-core-framework/nop-ioc` → PASS，**53 tests, 0 failures**（TestBeanDepends **13/13** 全绿）
2. `./mvnw test -pl nop-persistence/nop-orm-geo` → PASS，**2 tests, 0 failures**（真实生产配置 `$DEFAULT$` 别名回归）
3. `./mvnw test -pl nop-wf/nop-wf-ai` → PASS，**4 tests, 0 failures**（TestWfAiHelper:61 断言 `getResponseFormatConfig().getType()=="json_object"`）
4. `./mvnw test -pl nop-persistence/nop-orm -am` → 审计 session 报 FAIL（28 errors，`nop.err.ioc.empty-config-var`），**主会话收口复测 → PASS**（BUILD SUCCESS；nop-orm 138 tests 0 failures，reactor 无失败）。审计时归因"nop-orm/pom.xml 未提交改动"经核实**不存在**（`git status`/`git diff` 均无该文件）；工作树另有其他-session WIP（DataInitInitializer/TestDataInitInitializer 迁移、BaseTestCase 加 clearTestConfig、nop-orm-geo 未跟踪测试），复测下不影响 nop-orm 测试。非本 plan 回归。
5. `./mvnw compile -pl nop-core-framework/nop-ioc` 与 `-am` 变体 → PASS，BUILD SUCCESS；precompile 重跑后 `_gen/_BeanValue.java` git diff 保持稳定（仅 4 行 javadoc 注释与 xdef 同步，无手改/抖动）
6. `node ai-dev/tools/check-doc-links.mjs --strict` → exit 0，**0 errors，2 warnings**（审计时 plan 文件引用已删除的 `BeanDependsBuilder.java`）；主会话收口后 Plan Status 转 `completed`，checker 自动跳过 completed plan 的 broken-link 检查——收口复跑确认 **0 errors 0 warnings**
7. 补充：`./mvnw test -pl nop-service-framework/nop-biz` → PASS **27/0**（生产用法模块）；`./mvnw compile -pl nop-sys/nop-sys-dao,nop-persistence/nop-dbtool` → PASS（另两个生产用法模块编译）

**Grep 残留扫描（全仓库，排除 target/ai-dev）**：
- `nextBeans|getNextBeans|addNextBean|dependBeanIds|getDependBeanIds` → **0 代码残留**（仅 ai-dev 文档/plan 行文与 TestBeanDepends.java:86 描述性注释）
- `ERR_IOC_UNKNOWN_IOC_BEFORE|ERR_IOC_UNKNOWN_IOC_AFTER|ARG_BEFORE|ARG_AFTER` → **0 代码残留**（仅 ai-dev 文档过去式描述）
- `setResponseFormat(` → 仅 `nop-ai/nop-ai-core/.../AiChatOptions.java`（legacy `@Deprecated(forRemoval=true)`，自有 String 字段，按 plan-329 Deferred 裁定保留）；`ChatOptions.java` 无 String 委托残留（仅 `getResponseFormatConfig/setResponseFormatConfig`）

**Live-code 审查（逐项比对 plan/设计/日志）**：
- `BeanTopologySorter`：`verifyOrderConstraints`（:83-131）在 `sort()` :74 无条件调用、先于 `fillResolvedDepends`(:75)；dependsOn/iocAfter 违例判定 `pos<=targetPos`、iocBefore 违例判定 `pos>=targetPos`；缺失目标经 `normalizeBeanId==null` 跳过；`normalizeBeanId` 三处使用齐备（校验 :94/:107/:120、填充 :162/:174/:185、建边 :216/:230）——与设计 §3.4 "三处" 一致
- `BeanDefinition`：`resolvedDepends` 字段 :123、getter/setter :177-186；`newObject` :526-533 遍历 `getResolvedDepends()` 以 `getBean(depend,false)` 强制创建；无 nextBeans/dependBeanIds 任何痕迹
- `BeanContainerImpl`：构造函数 :90-99 无 nextBeans 循环；`getBean0` :357-393 无前向创建；`asyncStartBeans` :552 读 `bean.getResolvedDepends()`
- `IocErrors`：`ERR_IOC_BEAN_ORDER_CONSTRAINT_VIOLATED` :222-224（ARG_BEAN_NAME+ARG_DEPEND）；旧码已删
- `BeanContainerDumper` :90-92 输出 `ext:resolved-depends`
- `beans.xdef` :111-112 注释为新"前置依赖"语义；`_gen/_BeanValue.java` javadoc 与 xdef 逐字一致（再生成产物，非手改）
- `WfAiHelper.java:78` 用 `setResponseFormatConfig(ResponseFormat.jsonObject())`；`ProducedBeanInstance` latch/awaitGetBean 已删；`BeanContainerBuilder` 中 `BeanDependsBuilder` 调用步已删；`BeanDependsBuilder.java` 已删除（git status D）

**Anti-Hollow 逐项**：(a) `testAfterForceCreate` 断言 B.createdCount==1 且通过；(b) `getBean0` 代码审查 + `testNoForwardCreation`（A==0）通过；(c) `testBeforeCycleReported`/`testDependsOnCycleReported` 在默认 allow-cycle=true 下断言新错误码且通过；(d) `testModelNotRewritten` 断言 dump `ext:resolved-depends=="afterB"` 通过；(e) `testBeforeAliasOfDefaultBean` 通过 + nop-orm-geo 真实配置 2/2；(f) 错误码在 `BeanTopologySorter` 引用并在两个环测试中抛出

**不一致/发现**：
1. Phase 3 Exit Criteria "`-pl nop-orm -am` 全绿" 勾选在日志中无对应记录（日志仅记 nop-ioc 53/nop-orm-geo 2/nop-biz 27 + skipTests install），且当前工作树该命令不绿——根因非本 plan（见上），但该 checkbox 的支撑证据不足，建议主会话复核
2. 测试资源 `test_bean_depends_missing_depends_on.beans.xml:4` 注释写"不报错"，实际行为/断言是**报** `ERR_IOC_UNKNOWN_DEPEND_REF`——复制粘贴残留的误导性注释（行为正确，仅注释问题）
3. 工作树含与 plan-337 无关的其他 session 未提交改动（nop-orm/pom.xml、DataInitInitializer.java 部分回退、TestDataInitInitializer.java 删例、untracked application.yaml），是 nop-orm 测试失败的根因

Follow-up:

- 工作树含与 plan-337 无关的其他-session WIP（`DataInitInitializer.java`/`TestDataInitInitializer.java` 测试迁移、`BaseTestCase.clearTestConfig`、nop-orm-geo 未跟踪测试目录）——主会话复测 `test -pl nop-orm -am` 为 BUILD SUCCESS，不影响本 plan 收口；这些改动不属于 plan-337 提交边界，提交时须排除。
- `test_bean_depends_missing_depends_on.beans.xml` 注释与断言矛盾——**已修正**（改为"报 `ERR_IOC_UNKNOWN_DEPEND_REF`"，注释级修正，不影响行为）。