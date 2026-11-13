# C1 共享可变状态与可见性 — nop-core-framework 六子模块 + nop-utils 第 3 轮收口（盲审）

- **审计单元**: `nop-core-framework/nop-ioc`、`nop-config`、`nop-plugin`（api/manager/support）、`nop-security`、`nop-log`（core/java/log4j2/logback）、`nop-boot` + `nop-utils`（src/main 口径）
- **仓库**: `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`，HEAD 6dceb20dc3）
- **本轮输入**: 第 1 轮报告 `C1-shared-mutable-state.md`（C1-001~016 + CL-001~003）、第 2 轮报告 `C1-round2.md`（C1-017~020 + CL-014/015 + CL-001 升格建议）——二者为本轮唯一允许读取的 ai-dev/ 文件；其余全部结论来自本轮对 live code 的直接取证。nop-kernel 侧类（api-core config 包 15 文件、nop-commons DefaultThreadPoolExecutor、StaticValue）按前两轮既定口径作**越界对照**读取，不立案字段本体。
- **盲审纪律声明**: 本轮为盲审第 3 轮（收口轮）。除上述两份前序 C1 文件外，**未读取** `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何其他文件；未读取同目录其他维度文件（C2/C4/C6 及 dispatch-log 均未打开）。
- **本轮目标（逐条执行完毕）**:
  ① C1-017/C1-007 修复面合并完备性（api-core 三字段 × 两类 holder 为载体的写点/读站/绕行路径穷举复核 + PlaceholderConfigReference volatile 先例适用性）→ 见 §一；
  ② CL-001 升格裁定（tryTriggerChange 位置与可达异常源 / AbstractFileConfigSource 兄弟对比 / 生产回调链后果 / 终裁）→ 见 §二，终裁 **升格 P1 立案 C1-021**；
  ③ 跨定义门控 TOCTOU 闭合验证（站点与 C1-010 修复面覆盖关系）→ 见 §三，判定**无需独立立案**；
  ④ 快扫（api-core 之外「运行期任意线程写配置协议字段」第三入口，updateConfigValue 调用方全枚举）→ 见 §四，**无第三写族**。
- **判级口径**: 沿第 1/2 轮 2468 口径——框架层默认并发调用判级；仅启动期单线程触达的站点条件化降档；P0 = 默认用法 + 常见条件即触发且不可逆。

---

## 一、目标①：C1-017/C1-007 修复面合并完备性复核

### 1.1 写点独立复核计数（grep 全仓 src/main 穷举，非沿用前轮口径）

`.updateValue(` 全仓 src/main 直呼站点 = **8 处**，收敛于 5 个方法；按方法粒度即第 2 轮的 W1~W7 七写点（W7 为 AppConfig.hostId 经 W6 的转运入口）：

| 直呼站点 | 归属写点 | 线程（本轮独立核实） |
|---|---|---|
| `DefaultConfigProvider.applyChangeToUsed` L305（删除键分支 `updateValue(null, StaticValue.nullValue())`） | W1 | configExecutor 刷新线程（运行期） |
| `DefaultConfigProvider.applyChangeToUsed` L308 | W1 | 同上 |
| `DefaultConfigProvider.updateRefs` L144（删除键分支） | W2 | main（changeConfigSource 唯一调用方 = ConfigStarter L484 doStart） |
| `DefaultConfigProvider.updateRefs` L147 | W2 | 同上 |
| `DefaultConfigProvider.updateRefs` L166（BaseTestCase.isTestRunning 分支） | W3 | **main 启动期**（见下「精度修正」） |
| `AbstractConfigProvider.reset` L76 | W4 | 测试框架 afterAll 线程（运行期） |
| `AbstractConfigProvider.assignConfigValue` L86 | W5 | 任意调用线程（运行期实证：LlmConfigHelper L145 秘钥解析） |
| `AbstractConfigProvider.updateConfigValue` L92 | W6 | 任意调用线程（运行期实证见 §四） |

第三字段（defaultValue）裸写点：`DefaultConfigProvider.setConfigModel` L117 `ref.setDefaultValue(...)`——全仓对配置引用的 `setDefaultValue` 唯一外呼（其余 22 个 grep 命中均为 GraphQL field/ORM column/BeanPropertyModel 等无关类型）；`setLocation` 对配置引用的**外部调用为零**（仅 DefaultConfigReference.updateValue L55 内部调用；其余 setLocation 命中均为 XLang AST node/biz 对象的同名方法）。构造期 defaultValue 写（buildFromDefined L231 / createRef L242 / makeRef L369 / DefinitionConfigProvider.getConfigReference L170）全部经 CHM computeIfAbsent 或 volatile configRefs 表安全发布——条件安全，不扩面。

**精度修正（对第 2 轮 §1.1 表）**：W3 所在的 `updateRefs` 唯一调用方是 `changeConfigSource`（L128），后者唯一调用方是 `ConfigStarter.updateConfigSource`（L484，doStart 第 9 步）——W3 实际执行线程是**启动 main 线程**，第 2 轮标注「任意测试线程（运行期）」不成立。按 campaign「启动期 = main 线程序列化」口径，W2/W3 应为**条件安全**档（仍属同一修复载体，无需改判级：C1-017 的 P1 由 W1/W4/W5/W6/W7 的运行期写点独立支撑）。本轮将 W3 从「运行期写点」移入「启动期条件安全」档，C1-017 失效面枚举随之收窄一行，其余三类面（defaultValue 第三字段、写-写竞争、跨字段撕裂）不受影响。

### 1.2 第三类 holder / 直接字段访问绕行路径排查（问题①核心）

- 全仓 `IConfigReference` 实现类 grep（src/main）= **恰 5 个，全部在 api-core**：`AbstractConfigReference`（可变 holder 1：location L13 / defaultValue L16）、`DefaultConfigReference`（可变 holder 2：provider L15）、`CastTypeConfigReference`（final ref + final targetType，纯委托，无可变状态）、`OverrideConfigReference`（final 双 ref，纯委托）、`PlaceholderConfigReference`（自有缓存字段已 volatile，L32-33）。**「两类 holder」分类成立，无第三类可变 holder。**
- 委托型 wrapper（CastType/Override/Placeholder）的读路径全部收敛到两类 holder 的裸字段（CastType L50/L65、Override L44-68、Placeholder L41-44/L73-79）——不构成独立裸面，但**扩大回归面**：修复验证必须覆盖经 wrapper 链的读（getConfigReference L171-185 的 makeDefault/castType 包装链）。
- 绕行路径封口证据：字段全 private（反射面不计）；public 变异方法仅 setLocation / setDefaultValue / updateValue 三个，其全仓外呼已在 §1.1 穷举（8 + 1 + 0）。**无绕行写路径。**

### 1.3 读站复核

R1（`AbstractConfigProvider.getConfigValue` L101）、R3（collectUsedRefs L127）、R4（collectUnusedValues L78）、R5（traceConfigVars L191 getLocation → L196 isDynamic=provider.isDynamic() → L197 getDefaultValue → L201 get() 连读）、R6（castType L319/L321 + AppConfig.withPlaceholder L99）、R7（IConfigReference.map L53-55）、R8（AppConfig.hostId L47）——七站逐行确认。R2（静态生成引用直读）以本轮回测口径在六子模块计 **19 处**（第 2 轮称 20 处，±1 为 grep 模式粒度差，非族缺口）；**补全**：nop-utils 另有 2 文件（`Jdk21CommonsCoreInitializer`、`ShellCommands`）同族直读，第 2 轮 R2 的「遍及 nop-ioc/nop-config/nop-boot」漏列 nop-utils。同族补记两个诊断读面：`AbstractConfigReference.toString` L33-40（getAssignedValue+defaultValue 连读）、`DevDocBizModel` L231-233（文档展示读）——均被同一修复覆盖，登记不立案。

### 1.4 PlaceholderConfigReference volatile 先例适用性裁定

- **作为可行性证据：成立。** 其 L30-31 注释（「配置引用会被多线程并发get()，缓存字段的发布/更新必须保证跨线程可见，且refValueHash先于actualValue写入」）是同包内对「配置引用默认并发读」前提的成文认可——DefaultConfigReference/AbstractConfigReference 的 volatile 化/holder 化与既有代码判断一致，非新设计决策。
- **作为修复模式：不充分，且原因有二。** (1) 该先例是**逐字段 volatile + 写序纪律 + 读者侧校验协议**的组合：读者先用 refValueHash 校验缓存有效性再决定用 cached 还是 value——读者协议补偿了双字段无原子性的缺口。DefaultConfigReference 的读者（get()、R5）**没有**任何有效性校验协议，逐字段 volatile 后 R5 仍可观测「新 loc + 旧 value」撕裂对（读者先读 location 后读 value，恰与写序同向，落进两写之间即撕裂）——**撕裂面只有单一不可变 holder + 单 volatile 引用才能闭合**，证实第 2 轮修复建议（三元组 record + 单引用替换）是**最小完备修复**，逐字段 volatile 是不达标替代方案。(2) 该类自身的 `get()` 在值变更后走 `valueHash != refValueHash` 分支**返回未替换占位符的原始值且永不重建缓存**（L56-58）——先例自身的刷新失效路径就有缺陷（见 CL-016），不宜作为协议范本直接套用。

**① 结论**：第 2 轮「7 写点 × 8 读站」计数独立复核**成立**（8 直呼站点 = 7 逻辑写点；8 读站全确认 + 3 处同族补记）；两类 holder 分类成立、无第三 holder、无绕行写路径；C1-007 与 C1-017 为**同一修复载体**（api-core 两类 holder 的字段面 = location/provider/defaultValue，推荐单一不可变 holder 方案），合并修复一次闭环，nop-config 侧无需逐点改写、回归面 = W1/W4/W5/W6/W7（运行期五写点）× R1~R8 + wrapper 链读。

---

## 二、目标②：CL-001 升格终裁

### a) tryTriggerChange 位置与可达异常源（独立核实）

- 位置属实：`JdbcConfigSource.refreshConfig`（L71-98，synchronized）中 try 块 L76-94 仅覆盖 `loadConfig` + 异步重试；`tryTriggerChange(vars)` 位于 L96-97 **try 之外**。`tryTriggerChange`（L100-108，synchronized 可重入）体内无任何 try：`ConfigSourceHelper.isChanged`（L109-125，纯 size 比较 + Objects.equals，对 JDBC String 值不抛）→ `this.vars = vars`（不抛）→ **`task.run()` 循环（唯一可抛点）**。
- **可达异常源修正（对第 2 轮的表述纠偏）**：生产 `addOnChange` 全仓唯一注册点是 `ConfigStarter L486` 的 `changeApplier::requestUpdate`。第 2 轮称「PluginManagerImpl 的 `(p,v)->reconcile()`（订阅回调，RuntimeException 直冲任务体）」**不成立**——插件 reconcile 回调运行在 `subscriptions.trigger`（applyChange L294 内、configExecutor 线程），**不在** tryTriggerChange 的同步回调里，且 ChangeSubscription.trigger 具异常隔离（第 1 轮正面确认）。真实可抛面收敛为三： (i) `requestUpdate` → `SingleThreadConfigExecutor.execute`（L28-30 → DefaultThreadPoolExecutor.execute L96-98 裸 ThreadPoolExecutor.execute）在 executor `destroy()`（L91-93 shutdown）后抛 RejectedExecutionException——可达窗口 = doStop 序列 L529-547 中「L533-535 executor.stop 之后、L547 closeableSources 关闭之前」在途刷新撞上；**但该窗口内任务体被杀的后果被随即到来的 close（L547 cancel future）吞没，属近似空转，不构成 P1 依据**；(ii) **宿主可插拔 IConfigExecutor**（`SysServiceLoader.loadConfigExecutor`，ConfigStarter L267-273）——自带界池/拒绝策略的自定义 executor 可在**运行期正常负载下**抛 REE，此时 scheduleWithFixedDelay 任务体异常 = 后续刷新被 JDK 永久静默取消，后果全额成立；(iii) `IConfigSource.addOnChange` 是**公开 SPI**（JdbcConfigSource L144-149），宿主/插件注册的任意回调 RuntimeException 同样直杀定时器且零日志。内置布线（SingleThreadConfigExecutor + 唯一内置回调）的名义运行期不可达性由 `shouldUpdate` CAS 门保证（队列深度 ≤1，队满拒绝不可达）——已核实。

### b) AbstractFileConfigSource 兄弟保护对比

`AbstractFileConfigSource.refreshConfig`（L53-70）将 **loadConfig + isChanged + vars 回写 + task.run() 循环全量**包进单一 try-catch(Exception)，并在 L64-67 注释明写「scheduleWithFixedDelay 任务一次未捕获异常会永久取消后续执行……只记录，保留下一轮刷新重试的机会」——作者明确知晓该 JDK 语义并已在文件轨设防。同模块两轨防护不对称属实，且文件轨把「回调抛异常」当作**预期瞬态条件**处理，JDBC 轨（DB 后端，故障面更宽）反而裸奔。

### c) 生产回调链后果

被杀后静默死亡面 = 该 JdbcConfigSource 的全部后续刷新 → `changeApplier.requestUpdate` 不再被触发 → `applyChange` 不再执行 → jdbc 源配置值热更新失效、`@r-cfg` 响应式 bean 属性重绑定失联、依赖 jdbc 配置的插件 if-property 门控（reconcile 链）失联。**全程零日志、不可自愈、重启前不可恢复**——可观测性比 C1-009（P1，泄漏尚有 DB 侧会话可查）更差。

### d) 终裁

**升格 P1，立案 C1-021**（完整立案见 §五）。裁量说明：不按 P0——默认布线（SingleThreadConfigExecutor、无第三方回调）名义运行期无抛出点，不满足 P0 的「常见条件即触发」；按 P1——后果等级（永久静默不可逆）为模块内最重，触发面 = 进程生命周期内任意一次回调抛出（可插拔 executor / 公开 SPI 回调使其成为真实运营面，非理论路径），且兄弟类成文注释证明平台已认定此类异常为必须存活的瞬态条件（F10 守卫缺失族）。修复为守卫级一行改（trigger 调用包独立 try-catch + LOG.error，对齐文件轨；不建议并入现有 try——其 catch 是 loadConfig 重试语义，回调失败会触发无谓重载；更优形态为 per-task 隔离使单坏回调不拖累同轮其余回调）。

---

## 三、目标③：跨定义门控 TOCTOU 闭合验证（随 C1-010 修复）

**站点核实**（第 2 轮 §2.3 matrix 补登项）——跨定义门控共 3 个评估面，全部逐行确认：
1. `PluginManagerImpl.doReconcile` L289 `def.isDefinitionGateSatisfied()` → `CoeffectEvaluatorImpl.isRequiresSatisfied` L695-702 → `hasActivatedPluginByName` L772-774 —— reconcileLock 内（reconcile L227）；
2. `isGateSatisfiedHypothetically` L331-339 → `hasActivatedPluginByNameExcluding` L780-795（读 `namesToPluginIds` CHM + `plugins` CHM + `holder.plugin.getState()` 跨定义复合读）—— 同在 reconcileLock 内的去激活组闭包计算；
3. **def 侧复读**（本轮补全核实）：`VfsPluginDefinition.activate` L398 在 lifecycleLock 内复读 `isDefinitionGateSatisfied()`——该复读仅经 manager 入口 `reconcile`（L291 activateIfInactive）或 `activatePlugin`（L170）可达。

**竞态写方核实**：翻转门控所读状态的写方恰为三个**不持 reconcileLock** 的生命周期操作——`deactivatePlugin` L177-185、`unloadPlugin` L145-165、`activatePlugin` L168-174（评估通过后被评估方被翻转 → 消费方带失效依赖激活，运行期 INACTIVE 异常漂移）。

**覆盖关系判定**：第 1 轮 C1-010 修复建议「unloadPlugin/activatePlugin/deactivatePlugin 统一纳入 reconcileLock」恰好覆盖全部窗口——站点 1/2 直接在锁内；站点 3 的两条到达路径在同一修复下入锁；入锁后「门控评估（读 getState）→ 激活迁移」与「deactivate/unload 翻转」全序化，评估-行动窗口闭合。pass 内一致性另由既有设计独立保证（去激活组先用假设态排除变体计算 + 先去激活后激活的循环序，L276-294）。残余面：宿主持 `IPlugin` 引用**绕过 manager 直调** activate/deactivate 仍在闭包外——与前轮「接口直调可达（宿主误用面）」同档，非缺陷，建议 owner doc 补一句「生命周期操作必须经 IPluginManager」。

**③ 结论**：该 TOCTOU 的根因（生命周期操作未与 reconcile 串行化）、修复载体（reconcileLock 序列化）与 C1-010 完全同源，C1-010 修复即闭合，**无需独立立案**——第 2 轮 matrix 登记口径经本轮站点级核实成立。

---

## 四、目标④：快扫——updateConfigValue/assignConfigValue 调用方全枚举

**实现方穷举**：`IConfigProvider` 实现全仓 4 个——api-core 的 AbstractConfigProvider/SimpleConfigProvider、nop-config 的 DefaultConfigProvider、nop-plugin-manager 的 **DefinitionConfigProvider**（单元内唯一 override 方）。`DefinitionConfigProvider.updateConfigValue`（L201-207）：键在定义级 mergedView → `setDefinitionValue`（synchronized(mergedView) 锁内写，**非协议字段**）；未命中 → 委托 `globalProvider.updateConfigValue`（即 W6）。`assignConfigValue`（L210-213）只写定义级视图。**不构成第三写族**；其 mergedView 全锁化（第 1 轮正面确认）本轮复读维持。

**调用方全枚举（src/main 23 处）按写族归类**：
- **W6（updateConfigValue → 裸协议写）运行期调用方**（全部在 api-core 之外，同族移交面）：`OrmMigrationWriteGate.setClosed` L63（nop-orm，**运维运行期翻转写停止关**；其读侧 `checkWriteAllowed` 是**每次 DML 提交的热路径读**——javadoc 宣称「原子生效于本 JVM 所有 DML 提交入口」，而裸字段协议恰恰不能保证该可见性契约，是 C1-017 族后果的最锐利跨单元实证，回归面必测）；`OrmMigrationOrchestrator` L388（CFG_ORM_SHARD，搬迁编排线程）；`WebPageExporter` L63（CFG_WEB_RENDER_MODE，web 线程，可按次导出触发）；`XlangJavaGenTask` L176-233、`CodeGenTask` L99-196（构建期）；`BaseTestCase` L157（测试期）；benchmark × 2、QuarkusIntegration L35-44、NopAppListener L44（启动/工具期）。
- **W5（assignConfigValue → 裸协议写）运行期调用方**：`LlmConfigHelper.resolveApiKey` L145（nop-ai-core，**请求线程**首解 API key 时在 secretCache CHM computeIfAbsent **锁内**写全局配置——运行期写点实证，且持 CHM bin 锁做配置写放大了锁内操作面，登记移交）；`CoreInitialization` L310/L314（启动期系统属性导入）；`BaseTestCase` L98/L107（测试期）。
- **api-core 域内**：AppConfig.hostId L53（已知 W7）。

**④ 结论**：全部调用方收敛于 W5/W6 两条既有写路径（或 DefinitionConfigProvider 锁内视图写），**不存在第三「运行期任意线程写配置协议字段」入口族**。快扫产出为回归面扩容（W6/W5 的跨单元运行期消费者清单）+ 一处锐利后果实证（OrmMigrationWriteGate 门禁契约 vs 裸协议可见性），无新立案。

---

## 五、发现条目（本轮新增）

### [nop-core-framework-C1-021] JdbcConfigSource 定时刷新回调无异常防护：任一 onChange 回调抛出即永久静默杀死 DB 配置热更新链（CL-001 升格立案）

- **文件**: `nop-core-framework/nop-config/src/main/java/io/nop/config/source/jdbc/JdbcConfigSource.java:71-98, 100-108, 144-149`、`nop-core-framework/nop-config/src/main/java/io/nop/config/starter/ConfigStarter.java:267-273, 486, 525-551`（对照兄弟 `nop-core-framework/nop-config/src/main/java/io/nop/config/source/file/AbstractFileConfigSource.java:53-70`、回调体 `ConfigChangeApplier.java:40-46`、`SingleThreadConfigExecutor.java:23-30`、kernel 对照 `DefaultThreadPoolExecutor.java:91-98`）
- **证据片段**:
  ```java
  // JdbcConfigSource.refreshConfig —— tryTriggerChange 在 try 外
  private synchronized void refreshConfig() {
      if (closed) return;
      Map<String, ValueWithLocation> vars = null;
      try {
          vars = loadConfig(); ...                       // L76-94 仅覆盖 load+重试
      } catch (Exception e) { ... }
      if (vars != null)
          tryTriggerChange(vars);                        // L96-97 —— try 外！
  }
  private synchronized void tryTriggerChange(Map<String, ValueWithLocation> vars) {
      if (this.vars == null || ConfigSourceHelper.isChanged(this.vars, vars)) {
          this.vars = vars;
          for (Runnable task : onChanges) { task.run(); }   // L104-106 无任何防护
      }
  }
  // 兄弟类 AbstractFileConfigSource.refreshConfig L53-70：load+回写+task.run() 全量 try-catch，
  // L64-67 注释明写「scheduleWithFixedDelay 任务一次未捕获异常会永久取消后续执行」
  ```
- **严重程度**: P1（CL-001 按第 2 轮建议升格；本轮独立复核确认并修正其触发面表述后落格）
- **并发场景**: 配置了 `nop.config.jdbc.refresh-interval` 的生产部署：globalTimer 线程周期执行 refreshConfig；任务体同步回调唯一生产注册点 `changeApplier::requestUpdate`（ConfigStarter L486）→ `SingleThreadConfigExecutor.execute`。触发面三类：(i) 宿主经 `SysServiceLoader.loadConfigExecutor`（L267-273）注入自带界池/拒绝策略的 IConfigExecutor，运行期负载下 execute 抛 RejectedExecutionException；(ii) 宿主/插件经公开 SPI `IConfigSource.addOnChange`（L144-149）注册的任意回调抛 RuntimeException；(iii) 停机窗口 doStop L533-535（executor stop）与 L547（source close）之间在途刷新撞 executor 已销毁（此窗口后果被随即 close 吞没，单独不构成 P1，列出以求完备）。
- **触发时序**: T1（globalTimer）refreshConfig 完成 loadConfig（DB 值有变更）→ 进入 tryTriggerChange → isChanged 判真 → 逐个 `task.run()` → requestUpdate 内 `executor.execute` 因 (i)/(ii) 抛出 → 异常冲出 tryTriggerChange（无 try）→ 冲出 refreshConfig → 冲出 scheduleWithFixedDelay 任务体 → 按 JDK 语义该定时任务**被永久取消且无任何日志**。
- **现状**: 同模块文件轨已全量设防并成文注释该陷阱（AbstractFileConfigSource L64-67），JDBC 轨遗漏；`isChanged` 与 `this.vars` 写本身不抛，唯一点是回调执行，而平台兄弟实现已认定「回调抛异常 = 必须存活的瞬态条件」。第 2 轮表述纠偏：插件 reconcile 回调运行在 subscriptions.trigger（configExecutor 线程、有异常隔离），**不是**本任务体的同步回调——真实触发面为本条 (i)/(ii)/(iii)。
- **风险**: DB 配置热更新自此永久静默失效：jdbc 源值停止刷新、`@r-cfg` 响应式 bean 重绑定失联、依赖 jdbc 配置的插件 if-property 门控失联；零日志、零可观测信号、重启前不可自愈——同类后果中可观测性最差的一档（对比 C1-009 泄漏尚有 DB 会话可查）。触发条件 = 进程生命周期内任意一次回调抛出，可插拔 executor/公开回调 SPI 使其成为真实运营面。
- **建议**: `refreshConfig` 将 L96-97 包入**独立** try-catch(Exception) + `LOG.error("nop.config.trigger-change-fail", e)`（勿并入现有 try——其 catch 是 loadConfig 重试语义，会引入无谓重载）；更优形态：tryTriggerChange 内 per-task 隔离（单坏回调不拖累同轮其余回调），与文件轨语义对齐。回归测试：注册抛 RuntimeException 的回调后断言下一轮刷新仍触发。
- **失效面枚举**: `IConfigSource.addOnChange` 生产注册点全查 = ConfigStarter L486 唯一处（grep 实证）；CompositeConfigSource/RouterConfigSource/ProfileConfigSource 仅委托转发 addOnChange、不自设定时器（无独立暴露面）；AbstractFileConfigSource 已设防（正面对照）；StaticConfigSource/DynamicConfigSource 无定时器（无暴露面）。模块内裸奔定时回调仅 JdbcConfigSource 一处，无未立案兄弟。
- **信心水平**: 确定（结构事实逐行核实；触发面 (i)/(ii) 为代码可达的真实运营路径，(iii) 已标注后果吞没；JDK scheduleWithFixedDelay 语义为规范行为且被兄弟类注释成文认可）
- **误报排除**: 非把「回调可能抛异常」泛化为缺陷——缺陷是**兄弟类已设防并注释**的防护不对称（F10 守卫缺失）；非「停机异常无害化」误报——(i)/(ii) 是运行期路径；非第 2 轮 reconcile 表述的沿用——该表述本轮证伪并已在现状栏纠偏。
- **复核状态**: 未复核
- **复核状态（终裁 2026-10-17 独立复核）**: **维持 P1**——全部结构事实独立复核成立：tryTriggerChange 位于 try 外（L96-97）、task.run() 无防护（L104-106）、兄弟类 AbstractFileConfigSource 全量设防+成文注释（L64-67）、addOnChange 生产唯一注册点 ConfigStarter L486（其余命中均为 IConfigSource 委托实现，grep 穷举）、IConfigExecutor 经 SysServiceLoader 可插拔（initConfigExecutor L266-273）、ChangeSubscriptions.trigger per-listener 异常隔离（L78-84）、默认布线 shouldUpdate CAS 门使 REE 不可达（ConfigChangeApplier L40-46，非 P0 判定正确）。第 3 轮对第 2 轮 reconcile 回调表述的纠偏核实成立。P1 依据=可插拔 executor+公开 SPI 回调的真实运营面+永久静默不可逆+F10 防护不对称。跨单元备注：nop-kernel-concurrency-C3-001（周期任务无统一异常绝缘层，cra-index 已登记）的平台级绝缘方案应吸收本条独立 try-catch 语义，勿双改。现状=新发现（CL-001 升格立案）。

---

## 六、维度集外线索（本轮新增）

### [nop-core-framework-CL-016]（维度集外线索，不立案展开）

- **形态归属**: C5（功能正确性）/ F9（刷新失效路径缺陷）——非 C1 共享状态缺陷
- **严重度候选**: P2（若被接线；当前潜伏）
- **一句话证据**: `PlaceholderConfigReference.get()`（api-core L40-60）在底层配置值刷新后走 `valueHash != refValueHash` 分支**直接返回未替换占位符的原始值**（L56-58 注释「若值已被更新，则返回新值」——但返回的是未经 `replace()` 的模板串），且缓存 `actualValue`/`refValueHash` 永不重建——占位符配置项一经刷新即永久退化为原始模板输出。当前全仓 src/main `withPlaceholder`/`PlaceholderConfigReference` **零调用方**（潜伏面，公开 API 宿主可达）；同包 volatile 先例的引用价值不受影响（可见性纪律本身正确），但该类不宜作为 DefaultConfigReference 修复的协议范本。
- **归属单元**: nop-kernel/nop-api-core（越界单元移交，建议 nop-kernel 审计轮或功能缺陷轨跟进）
- **复核处置（2026-10-17 独立复核）**: **确认**——get() L56-58 hash 不匹配分支返回未替换原始模板值（与 L55 注释「返回新值」语义不符）、actualValue/refValueHash 永不重建、生产零调用方（grep 穷举：仅 AppConfig.withPlaceholder 自身包装）亲核证实；潜伏功能缺陷（非 C1 共享状态），移交 nop-kernel/nop-api-core 功能缺陷轨；「该类不宜作为 DefaultConfigReference 修复协议范本」的裁定维持（holder 方案仍为终裁修复模式）。

---

## 七、总计与终止信号判定

| 判级 | 数量 | 本轮条目 |
|---|---|---|
| P0 | 0 | — |
| P1 | 1 | C1-021（CL-001 升格终裁落格：JdbcConfigSource 刷新回调无防护，永久静默杀死 DB 配置热更新链） |
| P2 | 0 | — |
| P3 | 0 | — |
| **新增发现总计** | **1** | （累计 C1-001~021；C1-021 为第 1 轮 CL-001 既有候选的终裁立案，非新缺陷面） |
| CL 线索 | 1 | CL-016（PlaceholderConfigReference 刷新后返回未替换原始值且缓存永不重建；api-core 移交，潜伏面） |

**收口三问终裁汇总**：① C1-007/C1-017 修复面合并**完备**——7 写点（8 直呼站点）× 8 读站复核成立 + 3 处同族补记（nop-utils 2 文件直读、toString/DevDoc 诊断读、wrapper 链读），两类 holder 分类成立、无第三可变 holder、无绕行写路径；PlaceholderConfigReference volatile 先例仅作可行性证据成立，作为修复模式不充分（读者校验协议不可移植），单一不可变 holder 方案为最小完备修复；一处精度修正（W3 实为启动期 main 线程，条件安全档，不影响判级）。② CL-001 **升格 P1 立案 C1-021**（触发面表述纠偏：reconcile 非同步回调；P1 依据 = 可插拔 executor + 公开回调 SPI 的运行期抛出面 + 永久静默不可逆后果 + 兄弟类成文设防的不对称）。③ 跨定义门控 TOCTOU **随 C1-010 修复闭合**（3 评估面 × 3 无锁写方覆盖关系逐站核实），无需独立立案。④ 快扫**无第三写族**（23 调用方全收敛于 W5/W6/锁内视图写），产出跨单元回归面扩容（OrmMigrationWriteGate 门禁契约 / LlmConfigHelper 秘钥 / WebPageExporter / OrmMigrationOrchestrator 四个运行期消费者）。

**正面确认（本轮新增，供复核轮对照）**：StaticValue 不可变（final value，castType/reset 链sound）；DefinitionConfigProvider mergedView 全锁化复读维持、其 getConfigReference 每次新建 ref 无发布问题；DefaultConfigReference 构造期 defaultValue 写全部安全发布（CHM computeIfAbsent / volatile configRefs）；`staticValues` 生产构造全为 CHM（ConfigStarter L448-449 经 SimpleConfigProvider 传递）；ConfigChangeApplier 的 shouldUpdate CAS 门使内置回调队列深度 ≤1（队满拒绝不可达）；doStop 的 deactivate→executor.stop→close 次序使停机 REE 窗口后果被 close 吞没。

**终止信号判定**：
- 收口三问全部闭合：①修复面合并完备性确认（含精度修正与读站补记，均为既有族的登记级补充）；②CL-001 终裁落格 C1-021（第 1 轮既有的「严重度候选 P1」线索的裁定兑现，非新探索面产出）；③TOCTOU 随 C1-010 闭合确认。
- 快扫无新族：updateConfigValue/assignConfigValue 调用方全枚举收敛于既有写路径，无第三入口。
- 本轮无新 P0/P1 缺陷面（C1-021 为既登记线索的升格落格）、无新族、无未登记同族站点——C1 维度在 nop-core-framework + nop-utils 单元内的盲挖与收口信号**均已终止**。

**终止信号：C1-007/C1-017 族闭合。** 不派第 4 轮。后续工作属修复轨：C1-021 按本条建议修复（trigger 独立 try-catch，回归测试含坏回调存活断言）；C1-007/C1-017 按 §一结论以 api-core 单一不可变 holder 方案一次修复，回归面 = W1/W4/W5/W6/W7 × R1~R8 + wrapper 链 + §四跨单元运行期消费者清单（OrmMigrationWriteGate 必测）。
