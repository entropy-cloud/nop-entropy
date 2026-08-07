# 337 nop-ioc before/after 与 depends-on 语义统一

> Plan Status: draft
> Last Reviewed: 2026-08-07
> Source: `ai-dev/design/nop-ioc/bean-dependency-semantics.md`
> Related: —

## Purpose

把 `ioc:before`/`ioc:after`/`depends-on` 统一为**单一前置依赖机制**（`dependsOn` 归一化 + 单一强制创建路径）：移除 `nextBeans` 第二机制，新增独立于 `allow-cycle` 配置的顺序保证校验，并清理从未被引用的错误码。详见设计文档 `ai-dev/design/nop-ioc/bean-dependency-semantics.md`。

## Current Baseline

- 模型中 `ioc:before`、`ioc:after`、`depends-on` 是三个**独立字段**（`_BeanValue.java:88`/`116`/`388`，`_gen` 生成）。
- **fold（保留）**：`BeanDependsBuilder`（`loader/BeanDependsBuilder.java:19-43`）把 before/after 归一化为 `dependsOn`（`A ioc:after B`→`A.dependsOn+=B`；`A ioc:before B`→`B.dependsOn+=A`）。方向正确，**本计划不改动**。
- **nextBeans（待移除）**：`BeanContainerImpl` 构造函数（`impl/BeanContainerImpl.java:100-109`）据 `ioc:after` 设置 `B.nextBeans+=A`；`getBean0`（`:411-415`）新建 bean 后前向强制创建其 `nextBeans`；`BeanDefinition.nextBeans` 字段（`:121`）与 `getNextBeans`/`addNextBean`（`:197-206`）。`addNextBean` 全仓库唯一调用点是 `BeanContainerImpl.java:105`。
- **顺序无保证**：`BeanTopologySorter.sortBeans`（`impl/BeanTopologySorter.java:115-128`）的环检测受 `CFG_IOC_BEAN_DEPENDS_GRAPH_ALLOW_CYCLE` 控制，而该配置**默认 `true`**（`IocConfigs.java:65-66`），环默认被静默容忍。
- `BeanDefinition.newObject`（`impl/BeanDefinition.java:532-538`）在 init-method 前遍历 `getBeanModel().getDependsOn()` 强制创建（**保留**）。
- `InjectRefValueResolver.resolveValue`（`resolvers/InjectRefValueResolver.java:72-78`）属性注入时经 `getBean(ref, true)` 强制创建 ref 目标。
- `IocErrors.java:225-229` 定义 `ERR_IOC_UNKNOWN_IOC_BEFORE`/`ERR_IOC_UNKNOWN_IOC_AFTER`，全仓库**无任何引用**；`ARG_BEFORE`/`ARG_AFTER` 仅被这两处使用（`:226`/`:229`）。
- 现有测试**没有**覆盖 `ioc:before`/`ioc:after`（`src/test` 下无引用）；lazy 测试模式：`MyLazyInitBean` 系列 + `createdCount` 静态计数 + `AppBeanContainerLoader.loadFromResource` + `container.start()`。
- **生产用法**：8 处 `ioc:before`/`ioc:after`，全部位于 eager bean（相关 `beans.xml` 均未设 `default-lazy-init`）：`nop-biz`、`nop-sys-dao`、`nop-orm-geo`、`nop-orm`（4 处，部分带 `ioc:condition`）、`nop-dbtool`。均为单向约束、无环。
- 设计文档 `ai-dev/design/nop-ioc/bean-dependency-semantics.md` 已落地。
- 真正剩余的 gap：存在 `nextBeans` 冗余第二机制；before/after 顺序约束无保证（`allow-cycle` 默认 true）；两个错误码是死代码；缺少 before/after 回归测试。

## Goals

- `ioc:before`/`ioc:after`/`depends-on` 统一经 `dependsOn` 归一化，强制创建只经 dependsOn 循环（**不再有 nextBeans 前向联动**）。
- before/after 顺序约束**必须保证**：排序后显式校验，存在且顺序不满足即报错（独立于 `allow-cycle`）。
- `depends-on` 的强制创建语义**保持不变**。
- before/after 引用的 beanId 不存在时静默跳过，不报错。
- 删除两个从未引用的错误码；新增顺序校验错误码。
- 新增回归测试，可观测地证明上述行为；8 处生产用法所在模块测试不回归。

## Non-Goals

- 不改变 `depends-on`、`<ref>` 注入的语义与强制创建行为（fold 原样保留）。
- 不改变依赖图拓扑排序算法本身（只在其产出顺序后加校验）。
- 不修改 `allow-cycle` 配置的默认值或语义。
- 不重写 `nop-ioc` 子系统或调整 `BeanContainerBuilder` 构建步骤顺序。
- 不重新评估/修改 8 处生产 `beans.xml`。

## Scope

### In Scope

- `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java`：移除构造函数中据 `ioc:after` 设置 `nextBeans` 的循环（约 100-109 行）；移除 `getBean0` 中遍历 `nextBeans` 的循环（约 411-415 行）。
- `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanDefinition.java`：移除 `nextBeans` 字段（`:121`）及 `getNextBeans`/`addNextBean` 方法（约 197-206 行）。
- `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanTopologySorter.java`：排序产出最终顺序后新增 before/after 顺序校验（见设计 §3.3），无条件执行（不受 `allow-cycle` 影响）。
- `IocErrors.java`：删除 `ERR_IOC_UNKNOWN_IOC_BEFORE`/`ERR_IOC_UNKNOWN_IOC_AFTER` 与 `ARG_BEFORE`/`ARG_AFTER`；新增顺序校验错误码（如 `ERR_IOC_BEAN_BEFORE_AFTER_ORDER_VIOLATION`，含 bean 与目标参数）。
- `beans.xdef`：更新 `ioc:before`/`ioc:after` 语义注释（统一为"前置依赖"描述，移除"创建之后立刻创建"旧语义），并重新生成 `_gen/` 对应文件。
- `src/test`：新增 before/after 与 depends-on 的对比回归测试。

### Out Of Scope

- 修改 `_gen` 生成文件（只改 `beans.xdef` 源并通过生成流程刷新，禁止手改生成产物 `BeanValue.java`）。
- 变更 `nop.ioc.bean-depends-graph.allow-cycle` 默认值或语义。
- 为 `depends-on` 增加顺序校验（其契约是强制创建而非顺序；before/after 顺序校验覆盖"顺序必须保证"需求）。
- `docs-for-ai/` 无 ioc:before/after 相关内容（已核查）。

## Execution Plan

### Phase 1 - 移除 nextBeans 机制

Status: planned
Targets: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java`, `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanDefinition.java`, `src/test/`

- Item Types: `Fix | Proof`

- [ ] **Fix**：移除 `BeanContainerImpl` 构造函数中据 `ioc:after` 设置 `nextBeans` 的循环（约 100-109 行）。
- [ ] **Fix**：移除 `getBean0` 中 `for (String nextId : beanDef.getNextBeans())` 前向强制创建循环（约 411-415 行）。
- [ ] **Fix**：移除 `BeanDefinition` 的 `nextBeans` 字段及 `getNextBeans`/`addNextBean` 方法。
- [ ] **Proof**：全仓库搜索 `nextBeans`/`getNextBeans`/`addNextBean` 无残留引用（已核实唯一消费者在 nop-ioc 内，删除后复查）。
- [ ] **Proof**：`ioc:after` 归一化为 dependsOn——beanA(`lazy-init=true`, `ioc:after="beanB"`) + beanB(`lazy-init=true`)；`getBean("beanA")` 后断言 beanB.createdCount==1（创建 A 强制创建前置 B）。
- [ ] **Proof**：`nextBeans` 前向联动已移除——同一配置下 `getBean("beanB")` 后断言 beanA.createdCount==0（创建 B 不再连带创建 A）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 全仓库搜索 `nextBeans`/`getNextBeans`/`addNextBean` 无残留引用（代码可 grep）。
- [ ] `BeanContainerImpl` 构造函数不再设置 nextBeans；`getBean0` 无 `getNextBeans` 调用（代码可 grep）。
- [ ] 两个 Proof 测试通过，断言与上文一致。
- [ ] **端到端验证**：`container.start()` → 按需 `getBean()` 的完整路径已验证（lazy 前置强制创建 / 无前向联动）。
- [ ] **接线验证**：`ioc:after`→`dependsOn` 归一化后强制创建确实走 `BeanDefinition` dependsOn 循环（"getBean(A)→beanB==1"证明，而非仅类型/字段存在）。
- [ ] **无静默跳过**：本 Phase 不引入空方法体/`continue` 绕过；被移除机制无残余半实现。
- [ ] `_gen/` 未修改（本 Phase 不涉及）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 顺序校验与错误码

Status: planned
Targets: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanTopologySorter.java`, `IocErrors.java`, `src/test/`

- Item Types: `Fix | Proof`

- [ ] **Fix**：在 `BeanTopologySorter.sort()` 产出最终顺序后，对每个 bean 校验 `iocBefore`/`iocAfter` 约束：目标存在时 `pos(X) < pos(B)`（before）/ `pos(X) > pos(B)`（after）必须成立，否则抛新错误码。校验无条件执行（不受 `allow-cycle` 影响）。
- [ ] **Fix**：新增顺序校验错误码（含 bean 与目标参数）。
- [ ] **Fix**：删除 `ERR_IOC_UNKNOWN_IOC_BEFORE`、`ERR_IOC_UNKNOWN_IOC_AFTER` 与 `ARG_BEFORE`、`ARG_AFTER`（已确认无引用）。
- [ ] **Proof**：正常顺序用例不误报——beanZ(`ioc:before="beanA"`) + beanA(非 lazy)，id 字母序与声明相反；断言最终顺序 beanZ 先于 beanA（只有约束边被真正消费时成立）。
- [ ] **Proof**：顺序违例报错——误配置 before 环（`beanA ioc:before="beanB"` + `beanB ioc:before="beanA"`），在 `allow-cycle` 默认 true 时加载容器仍抛新错误码。
- [ ] **Proof**：缺失目标不报错——beanM(`ioc:before="nonExistentBean"`)，容器加载与 `start()` 均不抛异常。

Exit Criteria:

- [ ] 排序后校验在 `allow-cycle` 默认（true）下仍执行（"违例报错"测试通过即为证据）。
- [ ] 正常顺序用例不误报（"正常顺序"测试通过）。
- [ ] 新错误码已定义并抛出；旧错误码删除后全仓库无残留引用。
- [ ] 三个 Proof 测试通过，断言与上文一致。
- [ ] **无静默跳过**：校验路径在违反时显式抛错误码，而非 `continue`/忽略。
- [ ] `ai-dev/design/nop-ioc/bean-dependency-semantics.md` 与实现一致（本会话已落地，Phase 内复核）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 注释同步与跨模块回归构建

Status: planned
Targets: `beans.xdef`, `_gen/` 生成物, 受影响模块测试, `ai-dev/logs/`

- Item Types: `Fix | Proof | Follow-up`

- [ ] **Fix**：更新 `beans.xdef` 中 `ioc:after`/`ioc:before` 的语义注释（统一为"前置依赖"描述，移除"创建之后立刻创建"旧表述）。
- [ ] **Fix**：重新生成 `_gen/` 模型（运行相应代码生成流程，禁止手改生成文件）。
- [ ] **Proof**：跨模块回归——`./mvnw test -pl nop-persistence/nop-orm -am` 与 `./mvnw test -pl nop-core-framework/nop-ioc -am` 全绿（覆盖 8 处生产用法所在模块）。
- [ ] **Proof**：`./mvnw clean install -T 1C -DskipTests` 编译通过（删除错误码、生成文件刷新、新错误码注册均不破坏编译）。
- [ ] **Follow-up**：确认 `docs-for-ai/` 无 ioc:before/after 内容需同步，Phase 记录显式写 `No owner-doc update required`。

Exit Criteria:

- [ ] `beans.xdef` 注释已更新且 `_gen/` 已重新生成（git diff 可观测）。
- [ ] `nop-orm`、`nop-ioc` 模块测试全绿（含新增 Proof 测试）。
- [ ] 编译通过，无对已删错误码/ARG 常量的残留引用。
- [ ] `docs-for-ai/` 同步裁定已显式记录（`No owner-doc update required`）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0。

## Closure Gates

> **关闭条件**：本 section 所有条目及每个 Phase 的 Exit Criteria 全部 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] `nextBeans` 机制已整体移除，全仓库无残留引用。
- [ ] before/after 顺序约束已保证（排序后校验，独立于 `allow-cycle`），由 Proof 测试锁定。
- [ ] `depends-on` 强制创建语义保持不变，由 Proof 测试锁定。
- [ ] 两个未用错误码删除、新顺序校验错误码已使用，全仓库无残留。
- [ ] 8 处生产用法所在模块（至少 nop-orm）测试不回归。
- [ ] 必要 focused verification（Proof 测试 + 跨模块回归）已完成。
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope 项。
- [ ] owner docs 已同步：设计文档已落地；`docs-for-ai/` 显式 `No owner-doc update required`；`beans.xdef` 注释与实现一致。
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）`ioc:after`→`dependsOn` 强制创建在运行时确实被触发（beanB==1）；（b）`getBean` 无前向创建残留；（c）顺序校验在默认 `allow-cycle=true` 下确实报错。
- [ ] `./mvnw compile -pl nop-core-framework/nop-ioc -am`
- [ ] `./mvnw test -pl nop-core-framework/nop-ioc -am`
- [ ] `./mvnw test -pl nop-persistence/nop-orm -am`
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无）

## Non-Blocking Follow-ups

- 若未来希望对 `depends-on` 也提供"顺序必须保证"的校验，可扩展 §3.3 校验覆盖 `dependsOn` 边；当前其契约为强制创建而非顺序保证，非阻塞。
- 未来可考虑把 `ioc:before`/`ioc:after` 在 xdef 中直接标记为 `depends-on` 的别名声明，进一步收敛声明语法（非本计划）。

## Closure

Status Note: （执行完成后填写）
Completed: （未完成）

Closure Audit Evidence:

（执行完成后由独立子 agent 填写）

Follow-up:

- （执行完成后填写；confirmed live defect 不得出现在这里）