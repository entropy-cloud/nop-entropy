# nop-plugin 增强设计修正过程（吸收 Cordis 思想）

> Status: resolved
> Date: 2026-08-14
> 结论沉淀：`ai-dev/design/nop-plugin/`（00-vision / 01-architecture-baseline / 02-dsh-usage-coverage / 03-coeffect-and-agent-example / 04-interface-comparison）

---

## 第 1 轮：需求提出

### 用户原始表述

> 在 nop 平台可逆计算实现的基础上，提供 plugin 插件系统，实现类似的设计呢？

（此前多轮已讨论 dsh/Cordis 与可逆计算的关系：可逆计算是结构层可逆，dsh 是运行时 effect 可逆；dsh 配置层是可逆计算标准方案；loader 被动模式 + 依赖追踪缓存失效等）

### AI 复述

- 目标：在 Nop 可逆计算基础（结构层 Delta、loader 被动模式、子容器模式）之上，增强现有 `nop-plugin` 模块，吸收 Cordis 的加载/激活两态、revertible effects、reactive coeffects、多实例等思想。
- 调研确认：Nop 已有 `nop-core-framework/nop-plugin`（api/manager/support），`AbstractPlugin` 已用"plugin=子容器、卸载=stop"模式。

### AI 分析

调研 Nop IoC 现状后给出三条实现路径评估（改 IoC / 不改 IoC + 编排层 / 独立入口），结论：不改 IoC，plugin=子容器模式。

### 待澄清问题

1. 是否修改 nop-ioc？（用户答：不需要——后续"plugin 与 IoC 解耦"澄清）

---

## 第 2 轮：设计文档初稿

### 用户原始表述

> 现在写 plugin 系统的增强设计，目标就是吸收 cordis 的思想，把 plugin 系统改造好

### AI 产出

- `ai-dev/design/nop-plugin/`：00-vision、01-architecture-baseline、02-dsh-usage-coverage、03-coeffect-and-agent-example。
- 初始设计：加载/激活两态、IPluginScope effect 系统化（聚合三源 effect）、coeffect 条件激活、HMR、多实例（IPluginInstance=fiber）、getService 代理、不暴露子容器。

---

## 第 3 轮：独立子 agent 反复审查

### 用户原始表述

> 感觉现在的设计不太对，你用独立子 agent 反复审查改进直到达成共识

### AI 执行

**第一轮审查（pangu）**：4 P0 + 8 P1。裁决：方向成立，当前形态不可实施。P0 包括：BeansModel 进 api 破坏零依赖、effect 观测与"不改 nop-ioc"冲突、多实例配置隔离缺失、03 用虚构 API `app.mount` + "dsh 裸引用悬空"结论错误。

### 用户回答（关键决策）

- "放宽：加最小只读观测面"（后因 plugin/IoC 解耦洞察实际不需要）
- "推迟多实例 (推荐)"（后在第 7 轮被用户自己推翻）

### AI 整理

- 多实例降级为 non-goal；effect 观测问题保留。

### 待澄清问题

1. 多实例推迟是否合理？（第 7 轮用户质疑）

---

## 第 4 轮：用户系列架构质疑

### 用户原始表述 1

> 事件总线与 plugin 有关系吗？这是不是一个独立问题？

### AI 整理

- 认同：事件总线是独立基础设施（与 IoC 同级），plugin 只是消费者，不属 plugin 设计范围。评估范围修正：只评 plugin 核心职责（生命周期/effect/配置/多实例/服务获取/隔离）。

### 用户原始表述 2

> plugin 不可能暴露内部的 beancontainer，完全没有必要

### AI 整理

- 认同：`getContainer()` 从 IPluginInstance 移除——子容器是实现细节，暴露它破坏封装且绕过 getService 代理安全。

### 用户原始表述 3

> 现在是有 plugin 模块的

### AI 整理

- 调研确认 `nop-plugin` 已有：load/unload、类隔离（PluginClassLoader）、远程加载、IPluginCancelToken（命令级取消）、plugin=子容器（AbstractPlugin）。修正"需新建"判断为"需增强"。

### 用户原始表述 4

> plugin 其实是分成了加载和激活两种状态？加载只是加载，激活是条件满足时激活？

### AI 整理

- 确认 dsh loaded≠activated（assertEntriesLoaded vs assertEntriesActivated，coeffect activating/deactivating/neutral）；Nop 现状 load 即 start（耦合）；Nop IoC delay-start 是启动阶段化非条件激活。

---

## 第 5 轮：plugin 与 IoC 解耦（关键洞察）

### 用户原始表述

> plugin与ioc有关系吗？ioc最多是内部的一种实现机制，与plugin框架没有任何关系

### AI 整理（承认根本性框架错误）

- **plugin 框架是独立概念**（生命周期/状态/effect/服务抽象/隔离），IoC 只是内部实现机制（可替换）。
- `nop-plugin-api` 零依赖 IoC 类型；子容器/bean destroy/subscription 都是实现细节，非 API 契约。
- 据此撤销"放宽 nop-ioc 加观测面"决策——IPluginScope 自管理 effect，不观测 IoC。
- 修订 00/01：核心原则、effect 自管理、IPluginContext 收敛（registry+reconcile）、移除 resolveKey/getHostContainer。

---

## 第 6 轮：三大质疑（dsh 用法 / 参数传递 / spring batch）

### 用户原始表述 1

> 仔细检查dsh中所有对于plugin的使用方式了吗？ctx.tools这就是全部用法吗？

### AI 整理

- explore 全量调研 dsh：注册原语全集（Service 子类 / provide / accessor / set 4 种 + 事件 on/emit/waterfall/parallel/serial 5 种）、**apply(ctx, config) 参数传递是主通道**、70+ ctx key、createScope/isolate/intercept。
- 02 补充 J（注册原语全集）/K（apply 参数传递）项。

### 用户原始表述 2

> 另外注入plugin scope的方式不对。通过参数传递有什么问题？

### AI 整理

- 认同：@Inject 字段注入是 SpringBatch 式坏设计（成员变量保存 context）。改为参数传递。

### 用户原始表述 3

> 你阅读 theory 目录下 spring batch 的分析文章了吗？

### AI 整理

- 读 `docs/theory/why-springbatch-is-bad.md`：核心洞察 = context 应**参数传递**而非保存为类成员（Class Component 类比 + Hooks 闭包传参）；NopBatch `setup(context)` + Provider 模式。
- 新增 `IPluginActivator.activate(scope)`（参数传递）。

---

## 第 7 轮：多实例恢复（推翻推迟决策）

### 用户原始表述

> 为什么多实例要延迟？此前不是说这一阶段就要做吗？怎么回事？

### AI 澄清

- 推迟是用户此前在决策问题中选择的（审查建议）。但用户继续质疑：

### 用户原始表述 2（关键纠正）

> 多实例和全局配置有什么关系？与ioc没有任何关系刚才不是澄清了吗？在plugin自己的接口层面多实例如何处理梳理不清楚吗？

### AI 整理（承认错误）

- **推迟理由不成立**：把 IoC 实现细节（全局配置污染、BeanDefinition 共享）当成了多实例障碍，违背刚澄清的"plugin 与 IoC 解耦"原则。
- 多实例在 plugin 接口层面完全清晰：`createInstance(pluginId, instanceKey, config, parent)`、独立 scope/effect/配置域、实例级 coeffect、parent 层级。
- **恢复多实例**：00（目标第 5 项）、01（§三状态模型、接口契约、§五实例级 coeffect、§九拒绝项）、02（E/I 项）、03（Step 4/7）。
- 实例配置域：`instance.getConfig()` 随实例持有，不写全局 AppConfig。

### 后续审查

- 第六轮审查：多实例恢复方向正确自洽（dsh `Runtime.fibers` 证实一定义多实例是原生语义），5 P1 闭环（createInstance 补 parent、parent 继承语义、per-instance 命令路由、实例配置域实现路径、getConfig 落点）。
- 第七轮审查：**达成共识，可进入 plan 拆分**。

---

## 第 8 轮：接口对比文档

### 用户原始表述

> 文档中详细对比nop plugin与dsh的接口设计了吗

### AI 整理

- 此前只有用法评估（02）和场景对照（03），缺接口级逐项对比。新增 `04-interface-comparison.md`（8 项：apply/activator、ctx.\<key\>/getService、ctx.effect/scope.effect、fiber/createInstance、配置、inject/coeffect、scope/实例隔离、注册原语）。

---

## 第 9 轮：activate 双参数对齐

### 用户原始表述

> apply(ctx, config) 一个两个参数，一个一个参数，这是怎么回事

### AI 整理

- 承认：config 被折叠进 `scope.getConfig()` 导致不对称。修正：`IPluginActivator.activate(scope, config)` **双参数**，config 显式参数传递；`scope.getConfig()` 移除；实例配置权威在 `instance.getConfig()`。

---

## 第 10 轮：apply 返回值与重复调用

### 用户原始表述

> 如果config是一个独立参数，那么apply两次会是什么结果？apply有返回值吗

### AI 整理（查证 dsh 源码）

- **apply 两次**：dsh `ctx.plugin()` 每次调用创建**新 fiber**（=新实例）；`ctx.inject` 依赖变化时 apply 卸载重跑。Nop 对应：同 key 两次 `createInstance` 抛异常；deactivate→activate 重跑 activator；并发重复幂等。
- **apply 有返回值**：返回 `Effect<T>`（disposer/promise/iterable），**返回值自动注册为 effect**（fiber.ts `safeCollect`）。修正：`activate` 返回 `void` → **`Disposable`（可空）**，实现层自动 `scope.effect(returned)`。

---

## 第 11 轮：返回值语义澄清

### 用户原始表述

> 什么意思？到底是不是应该返回IPluginInstance？

### AI 澄清

- 两层职责不同：**createInstance → IPluginInstance**（创建实例，对应 dsh `ctx.plugin() → Fiber`）；**activator.activate → Disposable**（激活函数体清理，对应 dsh `apply → Effect`）。实例不由 activator 返回。

---

## 第 12 轮：注册机制

### 用户原始表述

> 那activate 返回的disposable到底是怎么注册到effect上的？在dsh中apply如何使用？

### AI 整理

- dsh：apply 返回值经 `safeCollect` 收集进 fiber 的 effect 列表（逆序回退）。
- Nop：实现层在 `activator.activate(scope, config)` 返回后**自动执行 `scope.effect(returned)`**（等价显式注册）；回退时机 `scope.close()` LIFO。
- 补充到 01 §四。

---

## 第 13 轮：dsh 示例纠错

### 用户原始表述

> // dsh:apply 返回 disposer,自动注册为 fiber 的 effect ... 这个对吗？tools中注册的内容谁负责清除？

### AI 整理（承认示例错误）

- 我给的 FileToolPlugin 示例只 `return () => fh.close()`，漏了 `ctx.tools.register` 返回的 disposer——**register 返回 disposer，调用方必须收集进 effect，不收集就不注销**（"Registrations are effects"）。
- 正确写法：`return function* () { yield register(...); yield () => fh.close() }` 或收集数组返回。
- 确认 03 §5.1 的 dsh 示例（ctx.effect 收集 register disposer）正确。

---

## 第 14 轮：示例全面核查

### 用户原始表述

> 你再仔细核查一下文档。把nop plugin和dsh的例子都要确保正确

### AI 执行（修正 6 处）

1. 01 接口定义 `void activate` 与实现 `Disposable activate` 矛盾 → 接口改 Disposable（@FunctionalInterface，零依赖）。
2. 03 createInstance 3 参/4 参不一致 + `parent=agent1Tools`（Java 不支持命名参数）→ 统一位置参数 4 参。
3. 03 getService 单值多候选自相矛盾 → `tool.bash` 加 `primary="true"`（beans.xdef 合法属性）。
4. 03 reconcile 伪代码过期（单实例视角）→ 更新为定义级+实例级。
5. 03 dsh 示例：apply 缺 config 参数、Service 类型标注不准、硬编码注册与 patch 不联动 → `apply(ctx, config)` 双参数 + config 驱动注册 + 返回值收集 disposer。
6. 03 Step 6/Step 3 描述同步。

---

## 纠正汇总（用户对 AI 理解偏差的纠正）

> 以下为整个过程中用户纠正 AI 的关键点，均已落实到设计中：

1. **事件总线是独立问题**，不属于 plugin 职责（第 4 轮）。
2. **plugin 不暴露内部 bean container**——子容器是实现细节（第 4 轮）。
3. **plugin 与 IoC 解耦**——IoC 只是内部实现机制，与 plugin 框架没有任何 API 关系（第 5 轮，最根本的纠正）。
4. **多实例不应推迟**——推迟理由（配置隔离/BeanDefinition 共享）是把 IoC 实现细节当障碍；多实例是 plugin 接口层面概念（第 7 轮，推翻 AI 之前采纳的"推迟"决策）。
5. **scope 应参数传递**而非 @Inject 字段注入——依据 `why-springbatch-is-bad.md`（第 6 轮）。
6. **activate 应为双参数** `(scope, config)`，对齐 `apply(ctx, config)`（第 9 轮）。
7. **apply 有返回值**（Effect/disposer 自动注册），activate 对应返回 Disposable（第 10 轮）。
8. **返回 IPluginInstance 的是 createInstance**，activator 返回 Disposable，两层职责不同（第 11 轮）。
9. **register 返回的 disposer 必须收集**，不收集不注销（第 13 轮）。
10. **示例必须可运行/语义自洽**（第 14 轮，6 处修正）。

---

## 第 15 轮：plugin 专属 XDef（推翻 beans.xdef 扩展方案）

### 用户原始表述

> 不在beans.xdef上扩展。plugin应该有自己的xdef

### AI 整理

- 推翻此前"`plugin:` 命名空间属性写入 beans.xdef 根元素"方案（该方案曾作为 plan Phase 0 Protected Area 前置）。
- **plugin.xdef 方案**：plugin 定义是独立 DSL（`/nop/schema/plugin/plugin.xdef`，定义文件 `*.plugin.xml`），`requires`/`if-property`/`activator` 是其**原生属性**，beans 定义为 `<beans>` 子元素（复用 bean 定义模型）；走标准 XDSL Delta/校验管线。
- 理由：① beans.xdef 是全平台共享 schema（blast radius 大），不改；② "plugin 属性寄生在 beans 根上"与"plugin 框架独立"原则不符；③ 新增 schema 附加式低风险，plan 不再是 Protected Area 前置。
- 修正：00（范围）、01 §7.6（载体决策 + 决策记录 + 源码锚点 + 双轨表 + 7.5 声明）、03（§1.2/Step 1/Step 2 Delta/Step 6/组装图/映射表）、04（声明式外壳/载体）。

---

## 第 16 轮：artifact 加载与类加载设计

### 用户原始表述

> plugin可以直接使用底层nop平台的全部类，但是它自己的类可能是从远程一个加载器通过plugin id加载得到一个jar包，从中加载。这要补充一个设计，加载器是通过http/https之类的url下载到本地的一个repository中，有sha之类的hash校验。这个加载器需要有一个接口，缺省提供一个IHttpClient的实现。

### AI 整理

- 新增 `05-artifact-loading-design.md`：类加载模型（PluginClassLoader parent=平台 classloader，plugin 直接用平台全部类、自己类从 jar）+ 加载器接口（IPluginResourceResolver 契约增强：URL 下载→本地 repository→SHA256 校验→本地 URL）+ 本地 repository（Maven 风格布局、缓存幂等）+ 缺省实现（HttpPluginResourceResolver，基于 IHttpClient）。
- **重要发现**：现有 `HttpPluginResourceResolver` 的 Javadoc 声称"完整性有SHA256校验码保证"，但代码**未实现校验**——设计补齐（下载后 move 前校验、fail-fast、`.sha256` 文件、缓存重验）。
- 拒绝：远程 URL 直载、无校验下载、接口进 api（零依赖）、另起新接口名（增强现有契约）。

---

## 总结

### 最终设计（见 `ai-dev/design/nop-plugin/`）

- **plugin 框架与 IoC 解耦**：`nop-plugin-api` 零依赖，子容器只是内部实现（可替换）。
- **两层状态 + 多实例**：定义级（UNLOADED→LOADED）+ 实例级（ACTIVATED⇄DEACTIVATED）；`createInstance(pluginId, instanceKey, config, parent)` 派生 N 实例，独立 scope/effect/配置域。
- **参数传递激活入口**：`IPluginActivator.activate(scope, config)`（对齐 `apply(ctx, config)`），返回值 Disposable 自动注册为 effect。
- **effect 自管理**：`IPluginScope.effect/effects/close`，LIFO 回退、quiescence 可断言；不观测 IoC 内部。
- **coeffect（定义级 + 实例级）**：定义级决定能否派生实例，实例级（基于实例配置域）决定该实例是否激活；reconcile 迭代评估。
- **实例配置域**：`instance.getConfig()` 随实例持有（任何态可读），不写全局 AppConfig（实现层独立 IConfigProvider）。
- **HMR**：reloadPlugin（unload + load + 重建实例）。
- **七轮独立审查**（pangu）达成共识，可进入 plan 拆分。

### 关键决策

1. plugin API 与 IoC 解耦（api 零依赖）。
2. 多实例为设计目标（非推迟项）。
3. 参数传递（scope + config）替代字段注入。
4. activate 返回 Disposable（自动注册 effect）。
5. 实例配置域随实例持有（不写全局）。
6. 事件总线/agent 框架层不属于 plugin 范围。

### 待定事项

- ~~plan 拆分输入约定：Phase 0 = beans.xdef 变更（Protected Area）~~（已被第 15 轮推翻：plugin 专属 plugin.xdef，不再改 beans.xdef、无 Protected Area 前置）；P2-A（HMR 重建实例配置快照）、P2-B（重复 key 已定：抛异常）、P2-C（updateConfig × 实例合并视图刷新）、P2-D（deactivate 父实例语义）作为切片输入——已落入 `ai-dev/backlog/nop-plugin-enhancement-roadmap.md` 的 cross-cutting concerns。

### 后续行动

- ~~`ai-dev/plans/` 拆分实施计划（含 Phase 0 beans.xdef 前置）~~ → 已由 roadmap + mission 接管：`ai-dev/backlog/nop-plugin-enhancement-roadmap.md` + `missions/nop-plugin-enhancement.json`（plugin.xdef，无 beans.xdef 前置），经两轮独立审查达成共识。
- 实施后关键结论同步到 `docs-for-ai/`。
