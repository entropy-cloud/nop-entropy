# nop-core-framework 配置刷新/订阅/插件/IoC 启动图/销毁链束（R4-15 G31，17 成员）

> Status: active
> Last Reviewed: 2026-11-09
> Source: plan 2549（`ai-dev/plans/2549-cra-phase8.55-r4-15-ioc-config-plugin-b.md`）Phase 1；批次定义 = `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二 :130 R4-15 行（17 finding ID / nop-core-framework / PA=框架核心 plan-first）；成员发现 = `ai-dev/audits/2026-10/2026-10-17-1725-deep-audit-nop-core-framework-concurrency/`
> Protected Area 授权：全部站点在 `nop-core-framework/nop-ioc`、`nop-config`、`nop-plugin-manager` = AGENTS.md「框架核心引擎」= **plan-first**。授权载体 = 本文档 + 独立 draft review + 回归面清单。
> 前置：R2-03（8.12/plan 2506）、R2-04（8.13/plan 2507）已 done——「P1 批先行、本批后置」已满足。
> 传导先例：plan 2506 的 lifecycleLock 全迁移与 2506 C6-001 的 try 收窄+失败释放 classLoader（本批 C2-005 重构同段 lambda 时**不得回改**其释放语义，台账裁定 10）；plan 2507 的 per-BD ReentrantLock、start join、startFailed 幂等终态、ProducedBeanInstance waitQuietly 有界化（本批 C2-009/C4-009 在其上叠修，不得回退）。
>
> **基线勘误 E-2549-1（fresh-session 取证 @ 2026-11-09）**：
> - **C2-009 部分覆盖**：2507 裁定③已把 waitQuietly 有界化（live `ProducedBeanInstance:216`）——「永久挂死」后果不成立。残余面 = **失败广播缺口**（catch Exception 不接 Error → 实例滞留 STATUS_CREATED，后续获取者走有界等待后得超时而非根因）。本批只修广播半边。
> - **C4-009 需按 2507 后语义重评**：join + startFailed 幂等已落地，但代际校验缺失（handleStartError 无 generation 检查），自毁新生代链本体仍在。
> - **C1-005 部分修复**：makeContainerScope 已 computeIfAbsent；残余 = containerScopes null→setContextAttr 覆写窗（窄窗，本批收口）。
> - **C1-019 窗口 live 仍在**（2506 提交未点名 reloadPlugin remove→put），按未修复立案。
> - **C2-005**：2506 C6-001 已落 lambda 内 try 收窄+失败释放；bin 锁长临界区本体未动。
> - **执行期勘误 E-2549-2（closure audit R1 Major-1 同步）**：裁定 F 的 C6-007 唯一形态修正——destroyBean 逐方法隔离后**首异常在末尾重抛**（初版吞异常破坏插件 deactivate 的 No-Silent 契约，TestPluginStateRecovery 2 red）；容器 stop/close 层已有收集。**裁定 F「不向 stop 调用方抛」的表述作废**，正确口径 = 「销毁回调层逐方法隔离+首异常重抛；容器 stop 层 try/catch 收集汇总」。
> - **执行期勘误 E-2549-3**：`handleStartError` 的代际校验必须**先于** `startFailed` 置位——顺序颠倒会让旧代失败污染新代终态（`whenComplete` 的 `!startFailed` 分支被跳过，`started` 永不翻转；载体 pin1b 红）。

## 一、设计结论（七条裁定）

1. **裁定 A（BeanContainer 生命周期互斥 + 代际令牌）**：成员 **C1-001、C4-009、C6-008**（三者同经 restart/stop 窗口，裁定互列）。
   - C1-001：`singletonScope` → `volatile`；`start()`/`stop()`/`restart()` 方法体加 `synchronized`（BeanContainer 实例锁；**不引入新锁字段**——2506 已把生命周期写迁入 `lifecycleLock`，此处只在容器公开入口收口，两锁不得嵌套：synchronized 方法内**不得**再获取 lifecycleLock）。
   - C4-009：新增 `private volatile long startGeneration`；`restart()`/`start()` 递增；`handleStartError` 捕获启动时快照的 generation（**joinStart 侧**：startFuture 配对存储 gen——新增 holder 字段，R1 m-1），**代际不匹配即放弃 stop/自毁**（旧图终态仅记录 WARN）。`getBeanScope` 读点维持现状（快照化已由 2507 完成）。
   - C6-008：(a) `BeanContainerImpl.stop()` 顺序改为「先执行销毁回调 → 再清 started/running」（**收益表述勘正（R1 m-2）**：scope 的 checkClosed 在 close 期间仍拦，本换序消除的是「销毁回调里 checkStarted 抛错」面，换序本身保留）；(b) `IocCoreInitializer.destroy()` 改为**先 `container.stop()` 再 `registerInstance(parent)`**（消灭「销毁期解析落 parent/新建孤儿 singleton」窗）。
2. **裁定 B（配置刷新链原子化 + 有界观测）**：成员 **C1-008、C2-008、C4-007**（同方法/同链）。
   - C1-008/C2-008：`DefaultConfigProvider.applyChange()` 加 `synchronized`（同方法两面：双 volatile 代际窗 + public 无同步复合操作一并收口）；`configSource` → `volatile`。**保持单线程 executor 契约**，synchronized 只防子类/直接调用方绕过。
   - C4-007：`ChangeSubscriptions.trigger` 与 BeanDefinition 的 refreshConfig 链加**观测**（每监听器计时，>1000ms WARN `nop.warn.config.listener-slow` 含 listener 描述与耗时）；**不改内联串行**。**顺序性声明（R1 MJ-2 勘正）**：live `triggered` 为 HashSet（hash 序），跨订阅注册顺序**本就不保证**——本批不改收集结构（LinkedHashSet 属范围外），Decision 只声明「串行内联 + 同订阅内注册序」，跨订阅顺序不声明。
3. **裁定 C（订阅清理原子化）**：成员 **C1-004、C2-007**。
   - C1-004：`subscriptionCleanup` → `volatile` + 清理动作收进同一 synchronized 域（锁外 CTA 消除）。
   - C2-007（R1 MJ-1 勘正）：`map.remove(key, list)` 两参**不闭合窗口**（B 线程 computeIfAbsent 拿旧 list add 后，A 的两参 remove 仍把 B 的新订阅连同旧 list 一起摘除）。充分形态 = **subscribe/unsubscribe 双侧 `listeners.compute`**：add/remove 皆在映射函数内（CHM per-key 原子），清理（remove→isEmpty）也在映射函数内完成，孤儿 list 结构性消失。
4. **裁定 D（插件索引/装载原子化）**：成员 **C1-018、C1-019、C2-005**。
   - C1-018：`unregisterNameIndex` 的 `remove→isEmpty→remove` 三步改为**单 map 原子**（对每 name 集合用 `compute(name, (k,v) -> ...)` 单域完成移除与判空清理）。
   - C1-019：`reloadPlugin` 的 `remove→put` 窗口改 **`compute(name, ...)` 单域内完成旧定义摘除+新定义回写**（无窗口）。
   - C2-005：`loadPluginFromJar` 映射函数瘦身——双检：先 getIfPresent，未命中再锁外构建 plugin 实例，构建成功后 `putIfAbsent` 回写。**竞争失败方语义（R1 MJ-3 勘正）**：失败方插件可能已 `start()`——按 2506 catch 语义镜像（aware→`unload()` / 非 aware→`stop()`）**回收插件后**再关闭自建 classLoader（只关 classLoader 会丢弃已启动插件）。HTTP+SHA256+start 全部移出 bin 锁临界区。**验收前置**：2506 的「N2 钉零回改」义务继承。
5. **裁定 E（IoC 启动图补边 + 失败广播）**：成员 **C2-013、C2-009**。
   - C2-013：`BeanDefinition.collectDepends` 增遍历 `ioc:interceptors` 依赖边（AOP 拦截器 bean 入拓扑图；沿 constructorArgs/props 现有写法）。
   - C2-009：`BeanDefinition` 的两处 `catch (Exception)`（:594 setupInstance / :641 prop assign）放宽为 `catch (Throwable)`（Error 也走 `markPropSetFailed` 失败广播）。（R1 m-4：getBean0 无 catch，第二站点即 :641）**不回退** 2507 waitQuietly 有界化。
6. **裁定 F（销毁序 + 销毁链异常聚合）**：成员 **C4-010、C6-007、C6-009**。
   - C4-010（←并 C6-006）：`BeanScopeImpl.close()` 改**拓扑降序销毁**——scope 条目皆容器内已定义 bean，经 `container.getBeanDefinition(name)`（含 parent 回溯）取 `beanTopoIndex` 从大到小逐 bean 销毁。**回退半边（R1 MJ-4 勘正，不可实施）**：`beans` 为 CHM 无序、`ProducedBeanInstance` 无创建序字段，「创建时间倒序近似」无数据支撑 → 删除；查不到定义（理论面）时 **WARN + 哈希序兜底**。
   - C6-007：销毁链异常**聚合**——同一 bean 的 destroyMethod 与 ioc:destroy 都执行（逐方法 try/catch 收集），close/stop/onContainerStop 三层收集全部异常后统一 `LOG.error` 汇总（**不**向 stop 调用方抛——停机路径抛错会中断其余容器销毁）；口径 `catch (Throwable)`（Error 不穿透中断销毁循环）。
   - C6-009：`BeanScopeContextImpl.onContainerStop` 在 close 后**摘除 scopes 条目 + 清 containerScopes**（restart 后自定义 scope 可重新绑定，死容器引用不滞留）。

## 二、逐成员裁定表（方法名锚；R3 轮后以 live 复核为准）

| Finding | 文件 / 方法 | 唯一形态 | 拒绝方案 |
|---|---|---|---|
| C1-001 | `BeanContainerImpl`（`singletonScope` 字段；`start`/`stop`/`restart`） | `volatile` + 三入口 `synchronized`（不嵌套 lifecycleLock） | 新增容器级 ReentrantLock（与 2506 lifecycleLock 双锁嵌套风险） |
| C1-004 | `BeanDefinition`（`subscriptionCleanup`；清理路径） | `volatile` + 清理动作 synchronized 同域 | 只 volatile 不收口 CTA |
| C1-005 | `BeanScopeContextImpl.makeContainerScope` | 残余 null→setContextAttr 覆写窗收口（synchronized 局部域或幂等覆盖） | 全方法 synchronized（热路径） |
| C1-008 / C2-008 | `DefaultConfigProvider.applyChange`；`configSource` | `applyChange` `synchronized` + `configSource` volatile | 双写合并为不可变 holder（2507 已裁定 holder 形态用于发布协议，此处是**应用端**收口，不在其范围） |
| C1-018 | `PluginManagerImpl.unregisterNameIndex` | **签名变化（R1 m-3）**：改为 `(pluginId, name)`（调用方 reload/unload 均持有 definition 可传 name）；单 map `compute` 原子完成移除+判空清理 | 加全局锁（与 register 竞争放大） |
| C1-019 | `PluginManagerImpl.reloadPlugin` | `compute(name, ...)` 单域旧摘+新写 | 先 remove 后 put（现状） |
| C2-005 | `PluginManagerImpl.loadPluginFromJar` | 双检 + 锁外构建（HTTP/SHA256/start）+ `putIfAbsent` 回写；竞争失败方按 2506 语义回收插件（aware→unload/非 aware→stop）后再关自建 classLoader（R1 MJ-3，与既有 finally 释放同 diff） | 映射内保留构建（现状）；SynchronizedMap；失败方只关 classLoader |
| C2-007 | `DefinitionConfigProvider.subscribeChange` 清理闭包 | **双侧 `listeners.compute`**（add/remove/isEmpty 清理皆在映射函数内，R1 MJ-1） | `remove(key,list)` 两参（不闭合窗口）；synchronized map 包装 |
| C2-009 | `BeanDefinition`（`setupInstance`/`getBean0` 的 catch 口径） | `catch (Throwable)` + 失败广播 `markPropSetFailed` | 只加 finally 广播（无根因语义） |
| C2-013 | `BeanDefinition.collectDepends` | 增遍历 `ioc:interceptors` 依赖边 | AOP 处理器侧补边（改动面更大） |
| C4-007 | `ChangeSubscriptions.trigger`；`BeanDefinition.onRefreshConfig` | 每监听器计时观测，>1s WARN；串行内联保持（Decision：**只声明同订阅内注册序，跨订阅 hash 序不保证**，R1 MJ-2） | 异步化监听器（破坏通知顺序）；每监听器超时强杀（不可中断面）；改 LinkedHashSet（范围外） |
| C4-009 | `BeanContainerImpl.restart`/`handleStartError` | `volatile long startGeneration` 代际令牌 + handleStartError 代际校验 | 全容器写锁（与裁定 A 重复） |
| C4-010 | `BeanScopeImpl.close` | beanTopoIndex 降序销毁；查不到定义 WARN+哈希序兜底（无创建序回退，R1 MJ-4） | 运行期重建拓扑（成本/竞态）；「创建时间倒序近似」（无数据结构支撑） |
| C6-007 | `BeanDefinition`（destroyMethod 调用点）；`BeanScopeImpl.close`；`BeanScopeContextImpl`/`BeanContainerImpl` 三层 | destroyBean 逐方法 try/catch(Throwable) 隔离 + **首异常末尾重抛**（E-2549-2：No-Silent 契约）；容器 stop/close 层 try/catch 收集汇总（口径含 Error——Error 收集后重抛，但**不得中断销毁循环/跳过状态清理**） | 销毁回调层静默吞（破坏插件 No-Silent）；Error 中断其余 bean 销毁 |
| C6-008 | `BeanContainerImpl.stop`；`IocCoreInitializer.destroy` | stop=先销毁后清状态；initializer=先 stop 后 registerInstance(parent) | 保持现状 |
| C6-009 | `BeanScopeContextImpl.onContainerStop` | close 后摘 scopes 条目 + 清 containerScopes | 只 close（现状） |

## 三、回归面清单（PA 授权必附；live grep @ 2026-11-09）

| 改动 | 真实消费方 | 必跑回归 |
|---|---|---|
| 裁定 A（BeanContainer 生命周期） | `nop-autotest` 两处 restart 调用方（live grep）；IoC 全量消费面 | `nop-core-framework` 全量（nop-ioc clean）+ `nop-autotest` 编译 |
| 裁定 B（配置刷新链） | 全平台配置监听（`ConfigChangeApplier` 消费面） | `nop-core-framework` 全量；`nop-config` 测试 |
| 裁定 C（订阅清理） | `DefinitionConfigProvider` 消费方（XLang 定义装载） | `nop-core-framework` 全量 + `nop-xlang` 全量 |
| 裁定 D（插件索引/装载） | `nop-plugin-manager`（零生产部署，测试树覆盖） | `nop-core-framework` 全量（含 plugin） |
| 裁定 E（启动图/广播） | 全平台 IoC bean 装载 + AOP 拦截器使用者 | `nop-core-framework` 全量 + `nop-core` 全量（IoC 下游） |
| 裁定 F（销毁序/异常） | 容器 stop 全消费方（`IocCoreInitializer`、autotest） | `nop-core-framework` 全量 + `nop-core` 全量 |

**E1 全量门禁**：`./mvnw -o clean install -DskipTests -T 1C -pl '!nop-runner/nop-cli-core,!nop-runner/nop-cli'` BUILD SUCCESS（沿 2547/2548 先例）。
**E3 分解通道**：`./mvnw test -pl nop-core-framework -am`（clean）+ 上表逐条。

## 四、实施顺序

1. 组 1（裁定 B/C：配置链，依赖最少）：C1-008/C2-008 → C1-004 → C2-007 → C4-007
2. 组 2（裁定 A：生命周期互斥 + 代际）：C1-001 → C4-009 → C6-008
3. 组 3（裁定 E：启动图）：C2-013 → C2-009
4. 组 4（裁定 F：销毁链）：C4-010 → C6-007 → C6-009 → C1-005
5. 组 5（裁定 D：插件，最后——需与 2506 语义同 diff 核对）：C1-018 → C1-019 → C2-005

## 五、Deferred But Adjudicated（预登记，实施中如推翻以执行期勘误记录）

- C4-007 的「监听器超时强杀」：不可中断面（监听器可能持锁），登记 watch-only。
- C1-001 synchronized 与 lifecycleLock 的锁序契约：owner doc 声明「容器公开入口 synchronized 不得再入 lifecycleLock」。
