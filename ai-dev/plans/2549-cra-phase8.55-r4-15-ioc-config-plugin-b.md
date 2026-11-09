# 2549 CRA Phase 8.55 — R4-15 2.2 配置刷新/订阅/插件/IoC 启动图/销毁链束（G31，17 工单）

> Plan Status: active
> Last Reviewed: 2026-11-09
> Source: roadmap 工作项 8.55（`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` :226，Status=todo）；批次定义 = `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二 :130 R4-15 行；成员发现 = `ai-dev/audits/2026-10/2026-10-17-1725-deep-audit-nop-core-framework-concurrency/`
> Related: plan 2506（R2-03，completed——lifecycleLock 全迁移、C6-001 try 收窄+失败释放）；plan 2507（R2-04，completed——per-BD 锁、start join、startFailed 幂等、waitQuietly 有界化）。本批在其上叠修，**不回退**其语义。设计文档 = `ai-dev/design/concurrency/ioc-startup-graph-config-refresh-plugin-lock-b.md`
> **Protected Area 授权**：nop-core-framework（nop-ioc/nop-config/nop-plugin）= **plan-first**。授权载体 = 设计文档 + 独立 draft review + §三 回归面。

## Purpose

关闭 R4-15 的 **17 个 nop-core-framework P2 工单**：BeanContainer 生命周期互斥/代际（3：C1-001/C4-009/C6-008）、配置刷新链（3：C1-008/C2-008/C4-007）、订阅清理（2：C1-004/C2-007）、插件索引/装载（3：C1-018/C1-019/C2-005）、IoC 启动图/广播（2：C2-013/C2-009）、销毁序/异常/清理（4：C4-010/C6-007/C6-009/C1-005）。判级全 P2（台账复核一致）。

**基线勘误 E-2549-1**（fresh-session 取证 @ 2026-11-09）：C2-009 挂死后果已被 2507 有界化收敛（残余=失败广播缺口）；C4-009 代际校验缺失本体仍在；C1-005 残余为窄窗；C1-019 live 仍在（2506 未点名）；C2-005 bin 锁长临界区本体未动（2506 只落了释放语义）。

## Baseline（live 基线事实，2026-11-09 亲读）

- `BeanContainerImpl`：`singletonScope` 非 volatile（:93）；start/stop/restart 无互斥；`handleStartError` 无代际校验（:643-646）；`stop` 先灭 started/running。
- `IocCoreInitializer.destroy`：先 `registerInstance(parent)` 后 `container.stop()`（:64-65）。
- `DefaultConfigProvider.applyChange`：public 无 synchronized（:255）；`configSource` 非 volatile（:50）。
- `BeanDefinition.subscriptionCleanup` 非 volatile（:155）；collectDepends 不遍历 ioc:interceptors（:289-298）；`catch (Exception)`（:594/641）。
- `DefinitionConfigProvider.subscribeChange` 清理闭包 `isEmpty→remove`（:248+）。
- `ChangeSubscriptions.trigger` 内联逐监听器无观测（:58-88）。
- `PluginManagerImpl`：unregisterNameIndex 三步（:628-633）；reloadPlugin remove→put（:796-808）；loadPluginFromJar computeIfAbsent 内 HTTP/SHA256/start（:662）。
- `BeanScopeImpl.close`：无排序 + `catch (Exception)` 末异常覆盖（:96-117）。
- `BeanScopeContextImpl.onContainerStop`：close 后无摘除（:83-98）。
- **基线测试签名**：`nop-core-framework -am` 全量以 Phase 2 起点实际复跑为准（本批首次触及该模块树）。

### Phase 1 - 设计与授权载体

Status: completed（设计文档 v1 + fresh-session 取证 + draft review R1 PASS-with-revisions 已修订闭环）

- [x] 设计文档（七裁定 A-G + 17 成员唯一形态 + §三 回归面 + E-2549-1）
- [x] 成员取证（fresh-session Explore：11 条缺陷在、4 条部分覆盖、2 条关联确认）
- [x] 独立 draft review 复审（R1 PASS-with-revisions，0B/4M/5m，修订后授权进入 Phase 2）

### Phase 2 - 17 工单实现 + 回归测试

Status: in_progress（**17 工单代码全部落地**（组 1-5，提交 77c6400..1f4f477）；执行期勘误 E-2549-2：C6-007 初版吞 destroy 异常破坏插件 No-Silent 契约（TestPluginStateRecovery 2 red）→ 改逐方法隔离+首异常重抛；载体 4/14 已建绿（pin2a/pin3/pin5/pin11/pin12），**9 个并发载体待建**（pin1a/1b/4/6/7/8/8b/9/10/13））
Targets: nop-ioc / nop-config / nop-plugin-manager + 测试树

- Item Types: `Fix`（15）、`Fix+Decision`（1：C4-007 观测+串行声明）、`Proof`（1）

**实施顺序（设计 §四）**
- 组 1（配置链）：C1-008/C2-008 → C1-004 → C2-007 → C4-007
- 组 2（生命周期）：C1-001 → C4-009 → C6-008
- 组 3（启动图）：C2-013 → C2-009
- 组 4（销毁链）：C4-010 → C6-007 → C6-009 → C1-005
- 组 5（插件，最后）：C1-018 → C1-019 → C2-005

**工单 × 载体表**（**全部 14 个测试类为新建**（R1 grep 全仓 0 命中）；实施纪律：每建一个载体类后 grep 校验）

| 工单 | 载体（测试类 / 方法 / 断言目标） | 类型 |
|---|---|---|
| C1-001/C4-009 | `TestBeanContainerLifecycleRace#pin1a`（并发 start×getBean 无孤儿：双线程 barrier 后 start/getBean，断言 getBean 返回实例与 start 后 singleton 一致）/ `pin1b`（restart 代际：启动期抛错的旧图 handleStartError 不 stop 新生代——慢启动任务 + restart 竞争，断言新代 bean 可用） | 行为钉 |
| C6-008 | `TestIocCoreInitializerStop#pin8`（initializer destroy 后容器 stop 前置：destroy 期间解析不落 parent 新建孤儿——销毁后 require 路径断言） | 行为钉 |
| C1-008/C2-008 | `TestConfigProviderApplyChange#pin2a`（并发 applyChange×getVar 无撕裂：监听器计数守恒 + 键值对一致） | 行为钉 |
| C4-007 | `TestConfigListenerObservation#pin3`（慢监听器 >1s → WARN 日志含耗时；**同订阅内注册序**断言，跨订阅顺序不声明——live HashSet hash 序，R1 MJ-2） | 行为钉 + Decision |
| C1-004 | `TestSubscriptionCleanupRace#pin4`（stop 与异步订阅启动竞争：清理后无泄漏订阅——监听器计数=0） | 行为钉 |
| C2-007 | `TestSubscribeChangeOrphan#pin5`（订阅/退订交错后 map 无孤儿 list：退订最后一个是剩 remove(key,list) 生效） | 行为钉 |
| C1-018 | `TestPluginNameIndexRace#pin6`（并发 register×unregisterNameIndex 后索引集无孤儿：requires 门控仍生效） | 行为钉 |
| C1-019 | `TestReloadPluginAtomicity#pin7`（并发 reloadPlugin×loadPlugin 无双活定义：同 name 定义数=1） | 行为钉 |
| C2-005 | `TestPluginLoadThinLambda#pin8b`（装载期间另一线程 get 同 name 不被 bin 锁长阻塞：锁外构建计时差断言）/ 结构钉：computeIfAbsent 内无 HTTP/start 调用（反射源码断言或行为差分） | 行为钉 + 结构钉 |
| C2-013 | `TestAopInterceptorTopology#pin9`（拦截器 bean 未 init 时宿主 bean 不先创建：init 顺序断言） | 行为钉 |
| C2-009 | `TestBeanSetupErrorBroadcast#pin10`（setupInstance 抛 Error → markPropSetFailed 广播：后续 getBean 得快速失败而非滞留） | 行为钉 |
| C4-010 | `TestBeanScopeDestroyOrder#pin11`（依赖方销毁先于被依赖方：拓扑降序断言） | 行为钉 |
| C6-007 | `TestDestroyChainExceptionAggregation#pin12`（destroyMethod 抛错后 ioc:destroy 仍执行 + close 三层异常汇总 ERROR 日志） | 行为钉 |
| C6-009/C1-005 | `TestScopeContextRestart#pin13`（onContainerStop 后 scopes 条目摘除；restart 后自定义 scope 重新绑定成功） | 行为钉 |
| 回归测试 | nop-core-framework 全量 + nop-core + nop-xlang（裁定 C 下游） | — |
| **无静默跳过**（rule 24） | | |

## Exit Criteria

- [ ] 17 工单全部实施
- [ ] 载体表逐条存在且断言目标行为
- [ ] 关键项先红后绿或变异论证（≥3：C4-009 代际、C2-005 瘦身、C4-010 拓扑序）
- [ ] E1 全量编译门禁 BUILD SUCCESS
- [ ] nop-core-framework clean 全量 0F/0E + nop-core + nop-xlang
- [ ] **端到端验证**：restart 竞争路径（pin1b）真实并发
- [ ] **不回退 2506/2507**：既有钉全绿（lifecycleLock/释放语义/waitQuietly 有界化）
- [ ] owner doc 落账：`ioc-and-config.md` + `source-anchors.md`
- [ ] 工具门禁：check-doc-links EXIT=0；scan-hollow `--module nop-core-framework --severity high` EXIT=0
- [ ] `ai-dev/logs/` 已更新

## Deferred But Adjudicated

### C4-007 监听器超时强杀
- Classification: `watch-only residual`
- Why Not Blocking Closure: 监听器可能持锁，强杀产生不可中断面；本批做观测（>1s WARN），异步化破坏通知顺序。
- Successor Required: `no`。

### 锁序契约声明
- Classification: `watch-only residual`
- 内容：容器公开入口 synchronized 不得再入 lifecycleLock（裁定 A 的嵌套约束），owner doc 声明。
- Successor Required: `no`。

## Review 记录

**Draft Review 记录**

- R1（fresh session，24 次工具调用）：**PASS-with-revisions**，0 Blocker + 4 Major + 5 Minor。全部随本版落盘（见下表）。17 成员锚点全部命中、判级与台账一致、E-2549-1 五条勘误与 live/2506/2507 提交吻合、载体 14 类全仓 0 命中（全新建）、双门禁 EXIT=0。
- 修订后待 R2 复审。

### Draft Review R1 修订清单

| R1 项 | 结论与修订 |
|---|---|
| **MJ-1** C2-007 两参 remove 不闭合窗口 | 改**双侧 `listeners.compute`**（add/remove/isEmpty 清理皆在映射函数内，CHM per-key 原子） |
| **MJ-2** pin3 注册序断言现状即假（HashSet hash 序） | Decision 收窄：「同订阅内注册序 + 跨订阅顺序不声明」；不改 LinkedHashSet（范围外，显式拒绝） |
| **MJ-3** C2-005 失败方只关 classLoader 丢弃已 start 插件 | 失败方按 2506 catch 语义回收插件（aware→unload/非 aware→stop）后再关 classLoader，同 diff 共存 |
| **MJ-4** C4-010「创建时间倒序」无数据结构支撑 | 删回退；查不到定义 → WARN + 哈希序兜底 |
| m1-m5 | joinStart 侧代际快照存储形态（startFuture 配对 holder）；C6-008 理由链勘正（scope checkClosed 仍拦，换序收益=消除销毁回调 checkStarted 抛错面）；C1-018 签名变化声明 `(pluginId, name)`；C2-009 第二站点措辞勘正（:594/:641）；载体表标注全部新建 |

## Closure

Status Note: （收口时填写）
Completed: （YYYY-MM-DD）
