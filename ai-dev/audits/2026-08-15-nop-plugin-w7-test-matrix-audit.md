# nop-plugin W7 测试矩阵审计（2026-08-15）

> Plan：`ai-dev/plans/2026-08-15-0013-1-sha256-tests-docs-sync.md`（Phase 2 `Proof` 项）
> 基线：W1-W6 各 plan 测试交付项（W3 quiescence/多实例、W4 命令隔离、W5 coeffect、W6 HMR/parent 链、W2 兼容回归）
> 验证基线：`./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` BUILD SUCCESS
> （api 6 + manager 80 + support 10）

## 摘要

逐项核对五类行为测试与 W3-W6 各 plan 测试交付项：五类行为全部已有测试且断言真实行为（实例状态 / 配置值 / registry 内容 / effect 清空 / 异常参数），仅发现 **1 个真实缺口**——W6 移交项"父链 IoC 错误码透传"的**总未命中**断言（成功回退/守卫/环均有覆盖，无"父链沿途全未命中 → 显式抛错"用例）。本审计后已补测试钉死（`TestParentHierarchy.testParentChainTotalMissThrowsExplicitError`）。

## 审计清单

### 1. quiescence / scope 契约（W3 交付）

| 项 | 测试证据 | 断言真实行为 |
|---|---|---|
| effects 注册（含 activator 返回值自动注册） | `TestPluginInstanceLifecycle.testQuiescenceAndScopeContract`（L172）、`testEndToEndChain`（L130） | `scope.effects().size()` 计数、disposable 顺序字符串列表 `["return-disposed","effect-disposed","bean-destroyed"]`（LIFO） |
| deactivate/destroy 后 effects 清空 | 同上 L155 | `assertEquals(0, scope.effects().size())` |
| close 后 effect 注册抛异常 | `testQuiescenceAndScopeContract` L187-189 | `assertThrows(IllegalStateException.class, ...)` |
| 单实例 effect 变更不影响其他实例 | `testMultiInstanceIsolation`（L193） | i1 注册 1 个 extra effect 后 `i1=3, i2=2`；i1 deactivate 后 i2 不变 |
| 重新 activate effect 重新注册 | `testLifecycleRoundtripAndDestroy`（L222） | 重激活后 `effects().size()` 恢复 2 |

**结论**：✅ 无缺口（不重写既有断言）。

### 2. 多实例隔离（agent-1/agent-2，W3/W4 交付）

| 项 | 测试证据 | 断言真实行为 |
|---|---|---|
| 同定义多实例独立 scope/effect/配置域 | `TestPluginInstanceLifecycle.testMultiInstanceIsolation`（L193-221） | 独立 effect 计数、独立 config 值（agent.timeout=10 vs 20） |
| 多实例命令隔离（per-instance 路由） | `TestPluginInstanceLifecycle.testMultiInstanceCommandIsolation`（L527） | 同命令名不同实例返回各自实例值（HelloCommand/IsolatedCommand） |
| 定义级 invokeCommand 多实例显式失败 | `testDefinitionInvokeCommandThrowsOnMultipleInstances`（L485） | 多实例时定义级命令抛明确异常（错误码断言） |
| 实例配置域合并视图 | `testConfigDomainMergedView`（L310） | 定义默认 + 实例配置合并断言 |
| coeffect 实例级隔离差异 | `TestCoeffectReconcile.testInstanceLevelIfPropertyIsolationAndRoundTrip`（L142） | agent-1 全局回退 true 激活 / agent-2 实例配置 false 去激活，updateConfig 往返后隔离差异保持 |

**结论**：✅ 无缺口。

### 3. coeffect 依赖链激活/去激活（W5 交付）

| 项 | 测试证据 | 断言真实行为 |
|---|---|---|
| requires 依赖链级联（激活与去激活双向） | `TestCoeffectReconcile.testReconcileWiringAndDependencyCascade`（L114） | destroy 父实例 → 子自动 DEACTIVATED；create 父实例 → 子自动 ACTIVATED；显式 reconcile 幂等 |
| 定义级+实例级评估、环检测 | `testCycleDetectionTerminatesAndReportsUnresolved`（L212） | unresolvedPluginIds 报告 + 终止性 |
| 配置订阅自动触发 reconcile | `testGlobalConfigSubscriptionTriggersReconcile`（L277） | 全局配置变更 → 实例状态真实翻转 |
| updateConfig 驱动往返 | `testUpdateConfigTriggersReconcile`（L326）、`testEndToEndConfigDrivenChain`（L344） | 定义配置变更 → 自动 reconcile → 实例状态往返 |
| 门控 null / 失败阈值暂停 | `testCreateInstanceGateReturnsNullWhenUnsatisfied`（L169）、`testFailThresholdPausesAutoActivation`（L245） | 门控不满足返回 null；连续失败超阈值暂停自动激活 |

**结论**：✅ 无缺口。

### 4. HMR reload 端到端（W6 交付）

| 项 | 测试证据 | 断言真实行为 |
|---|---|---|
| reload 端到端快照重建 | `TestReloadPlugin.testReloadEndToEndSnapshotRebuild`（L160） | 定义变更 → reload → 新定义对象 + 实例按快照重建（bean 属性新值可观测） |
| 门控实例 pending 重试 | `testReloadGatedInstanceBecomesPendingAndRetries`（L208） | pending 语义 + reconcile 重试 |
| 父链快照重建生命周期 | `testReloadPendingParentChainLifecycle`（L244） | 父链闭包实例按序重建 |
| 变更检测显式检查入口触发 reload | `TestChangeDetection.testCheckChangedAndReloadTriggersReload`（L151） | 可写资源内容变更 + mtime 推进 → `checkChangedAndReload()` → reloadPlugin 真实触发（新定义、primary 切换可观测） |
| 无变更 no-op | `testCheckChangedAndReloadNoChangeNoOp`（L179） | 定义/实例对象不变、lastModified 比对正确性 |
| 依赖方收敛 | `testReloadDependentConvergesViaReconcile`（L198） | requires 依赖方经 reconcile 收敛 |
| reload 失败路径显式 | `testReloadLoadFailureLeavesUnloadedWithSnapshotRetained`（L279） | load 失败 → UNLOADED + 快照保留；jar 轨 `testReloadJarTrackFailsExplicitly`（L306） |

**结论**：✅ 无缺口（W6 落地后复核项通过：`checkChangedAndReload()` 显式入口、P2-D 语义、reload 端到端均已落地且有测试，与本 plan 测试项交叉核对无重复）。

### 5. 兼容路径回归（W2/W4 交付）

| 项 | 测试证据 | 断言真实行为 |
|---|---|---|
| 非 aware jar 插件 start/stop | `TestPluginManager.testCompatPathCallsStartAndStop`（L111） | MockPluginRecorder.started/stopped + 坐标断言 + 保守状态 LOADED |
| start 失败清理 | `testCompatStartFailureCleansUpAndDoesNotKeepEntry`（L130） | stopped 调用 + map 无残留 entry |
| AbstractPlugin 兼容语义 | `TestAbstractPlugin.testCompatPathKeepsStartStopSemantics`（L298）、`testAwareStartFailsExplicitlyOnJarTrackInstanceCreation`（L276）等 10 用例 | start→load+createInstance 收敛、jar 轨实例化显式失败 |
| reconcile 跳过 jar 轨 | `TestPluginManager.testReconcileSkipsJarTrack`（L199） | jar 轨在册时 reconcile 正常终止、无 unresolved 报告 |

**结论**：✅ 无缺口。

### 6. W6 移交项：父链 IoC 错误码透传（本审计发现的真实缺口）

| 场景 | 原有覆盖 | 缺口 |
|---|---|---|
| 父链服务查找成功回退 | `TestParentHierarchy.testServiceFallbackWalksParentChain` | — |
| 父 DEACTIVATED / 环 / 重复 key 守卫 | `testCreateInstanceWithDeactivatedParentThrows` / `testParentChainCycleRejected` / `testDuplicateKeyCheckPrecedesParentState` | — |
| **父链沿途全未命中 → 显式抛错** | 无 | **真实缺口**（错误已显式抛出但无测试钉死；watch-only residual 裁定要求测试钉死现行为） |

**修复（本 plan Phase 2）**：新增 `TestParentHierarchy.testParentChainTotalMissThrowsExplicitError`——子容器 + 父链沿途 + 顶层均无候选（顶层 parentContainer=null 不扩展宿主）→ `getService(Callable.class)` 显式抛 `ApiErrors.ERR_IOC_UNKNOWN_BEAN_FOR_TYPE`（错误码 + beanType 参数断言，透传不翻译）。

**裁定**：`watch-only residual`——错误显式抛出、参数完整，不构成 silent no-op 或 contract gap；未发现跨 API 契约污染证据（插件实现方无对错误码字符串的依赖），不做错误码翻译。理由记录于 plan Closure。

## 结论

- 五类行为测试全部存在且断言真实行为：**不重写任何既有用例**。
- 唯一缺口（W6 移交项总未命中断言）已在本 plan Phase 2 补齐并通过（`TestParentHierarchy` 11/11 全绿）。
- 验证命令退出码 0（全量 96/96：api 6 + manager 80 + support 10）。
