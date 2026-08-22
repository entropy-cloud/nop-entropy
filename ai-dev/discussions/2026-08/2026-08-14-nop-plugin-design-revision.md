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

---

## 第 17 轮：定位反转——删除多实例、依赖收敛到插件级（2026-08-22）

> 本轮推翻第 7 轮恢复的多实例决策与 W8 预留的服务级依赖扩展点。用户亲自发起反转，AI 分析论证后确认方向成立。

### 用户原始表述

> 重新考虑nop-plugin的设计，本身nop平台已经提供了编译期也就是模型加载器的可逆计算支持，定制可以在非常细的粒度发生，另外它偏向于服务端应用，倾向于无状态设计。在这种情况下是否还有必要考虑plugin的实例化问题，是否依赖关系只需要定义在plugin级别，不需要内部的service级别？这样整体设计思想比较明确，只针对部分粗粒度内容使用plugin引入，并远程加载，通过依赖关系控制激活？

### AI 分析（基于 `ai-dev/articles/dsh-architecture-from-reversible-computation.md` 的理论框架）

1. **认识论边界判据（文章 §4.6）**："凡是在结构空间中可确定的内容，就提到结构空间；凡是在运行期才可确定的内容，才留在运行时并用可逆机制管理。"dsh 需要 fiber/scope/realm 是因为它是客户端交互式 harness——每个 agent 会话的注册内容依赖运行期信息；Nop 是服务端平台，插件层注册内容全部加载时可知。
2. **变化类型归属检验**：租户差异 = `IContext` 数据 + nop-dyn 租户 delta 层（编译期）；会话差异 = agent 层 session/contribution 机制；字段级定制 = Delta x-extends（已有）。没有任何一类变化要求"同一定义派生 N 个容器"。多实例没有认识论需求支撑。
3. **实证**：grep 确认 multi-instance 机制的全部消费者都在 nop-plugin 内部（impl+tests），nop-ai-agent 不依赖 nop-module（无 pom 依赖、设计文档零引用）；W9 注解早已把 agent 层 scope/realm 划给 nop-ai-agent 立项。
4. **服务级依赖终裁**：W8 曾把 `requires-service` 列为扩展点并指出"插件内服务替换不触发 reconcile"缺口；单层模型下该缺口自然消解（插件内服务变更走 HMR 重载重新门控）。dsh 服务级 inject 的存在理由是服务坐标即其组合坐标系；Nop 组合坐标是 VFS 路径 + DSL 节点，已由 DeltaLoader 被动追踪覆盖——重建服务级响应式是重复建设。
5. **保留清单**：load/activate 两态分离（Cordis 思想中真正有价值的部分）、IPluginScope effect 系统、activator 参数传递、getService 强类型代理（绑定对象改激活态）、双轨来源、SHA256 artifact 校验、plugin.xdef、HMR。

### 用户回答

确认执行范围：**全面修订设计文档**（修订 ai-dev/design/nop-plugin/ 全部文档为单层状态机+插件级依赖，追加本轮讨论记录，更新 roadmap 增加重构 work items；实现代码重构另行立项）。

### AI 整理（已执行）

- [00-vision.md](../../design/nop-plugin/00-vision.md)：新增 §〇 定位反转裁决（认识论边界表）；目标 5→3；约束新增"插件粒度唯一性"；non-goals 新增 6（多实例）/7（服务级依赖）；成功标准改写。
- [01-architecture-baseline.md](../../design/nop-plugin/01-architecture-baseline.md)：两层状态机坍缩为单层六态（UNLOADED/LOADED/ACTIVATING/ACTIVATED/DEACTIVATING/FAILED）；§7.2 记录 IPluginInstance 及关联 API 删除去向表；coeffect 仅插件级（R 终裁关闭 requires-service 扩展点）；HMR 去掉实例快照重建；接口契约重写（IPlugin 承载 activate/deactivate/getService）；源码锚点标注 R1 重构去向。
- [02-dsh-usage-coverage.md](../../design/nop-plugin/02-dsh-usage-coverage.md)：E/I/H 三项从"plugin 层支持"改判为"非 plugin 层职责"；F 收敛为纯插件级；差距表更新（原"instanceKey/parent 作 agent 层基座"表述废止）。
- [03-coeffect-and-agent-example.md](../../design/nop-plugin/03-coeffect-and-agent-example.md)：示例改写为单激活流；新增 §2.8 会话级差异的 agent 层分工示意。
- [04-interface-comparison.md](../../design/nop-plugin/04-interface-comparison.md)：§2.4 改判"无 fiber 对应物（有意拒绝）"及完整论证；语义差异总表更新。
- [README.md](../../design/nop-plugin/README.md)：定位基调更新。
- roadmap：R1-R4 重构 work items（API 收缩 / manager 编排简化 / 兼容路径调整 / 测试与文档同步）。

### 本轮关键裁决汇总

1. **多实例删除**（推翻第 7 轮）：一个定义至多一个激活；instanceKey/parent 层级/实例配置域/实例级 coeffect/级联销毁/HMR 快照重建全部移除。
2. **依赖只声明在插件级**（关闭 W8 扩展点）：requires/if-property 为完整依赖面，属性集冻结。
3. **保留 Cordis 有价值部分**：两态分离、effect 可逆账本（绑定激活）、参数传递激活、INACTIVE 快速失败代理、HMR。
4. **职责分工定式**：细粒度定制→编译期 Delta；粗粒度引入与门控→plugin；请求级变化→上下文数据/领域层。

### 后续行动

- roadmap R1-R4 重构实施（`nop-plugin-api` 公共 API 收缩，Protected Area plan-first，须独立 plan audit）。
- 实施后同步 `docs-for-ai/03-modules/nop-plugin.md`、INDEX、source-anchors。

### 第 17 轮独立审计（2026-08-22，三个并行子 agent）

三个独立审查（内部一致性 / 理论忠实度 / 代码现实核查）结论：**反转裁决成立、无翻案项**；共修复 1 Blocker + 10 Major：

1. **Blocker（一致性）**：roadmap 头部 Sources/Main-gaps 仍宣称"多实例为设计目标/两层状态机"——已改写为反转后表述并加历史记录横幅。
2. **理论限定**："注册内容全部加载时可知"降格为带限定的定位声明（per-load 而非 per-boot；运行期才知道内容的注册由领域层外部注册表承载，effect 只保资源回退）。
3. **事实纠正**：02-E 的 agent 层承接机制更正为 tag 可见性系统（activeTags/denyTags）；ContributionRegistry 是全局装配期注册表而非 sessionId 键控视图合成。scope/realm 两维标注为 open handoff（接收方未立项）。
4. **机制补强**：01 §三 新增跨插件去活性拓扑序不变量（消费者先于提供者退出，对应 cordis Theorem 63 ordering）。
5. **已知限制明文化**：updateConfig 热应用不触发依赖方重门控；编程式 provider swap 在本装配模型下不存在；requires 合取不可表达析取。
6. **归属对齐**：公共 API 面收缩归 R1（含 IPluginContext 重写与 IPluginManager 抽象方法删除——编译阻塞面），编排实现清理归 R2（ICoeffectEvaluator 实例条件/parentToChildren/快照重建/实例错误码族退役）。
7. **虚假陈述更正**：05 §七 "ServiceLoader 发现 IPlugin 实现"不属实，实际为 plugin.json + 反射实例化；链路终点 createInstance 已随反转删除。
8. **jar 轨契约裁决**：无 xdef 载体时视为无条件激活、activator 缺省跳过（01 §二）。
9. **失效语义统一**：03/04 对 dsh 非活跃访问行为的矛盾表述统一为双路径描述 + R4 对照 cordis 源码终核。
10. **supersession 交代**：01 §十 注明 08-21 调研参考 §9 结论被本轮反转取代（机制事实部分仍有效）。
