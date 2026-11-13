# C4 线程池与执行器治理 — nop-host 第 2 轮深挖（WI 4.4）

- 审计单元：nop-host（宿主集成层）C4 维度深挖轮
- 审计日期：2026-10-21（深挖 round 2，盲审口径）
- 前置：R1 发现 C4-001～C4-003 + CL-005（见 `C4-thread-pools.md`），本轮不重复立案。
- 深挖任务简报点名目标：@EventListener onStart/onClose 回调阻塞面穷举、QuarkusIntegration.start 的 caller 线程阻塞时长、NopBeansRegistrar 注册回调面。
- 降档口径沿用 R1 C4 专属声明（启动期单线程站点按站点校准降档；请求面不降档）。

## 深挖结论总览

**本轮 1 条新立案（C4-004，P2）+ 0 条 CL**。三个点名目标中，阻塞面穷举与 QuarkusIntegration.start 时长核算均收敛于 R1 已立案项（负结果）；NopBeansRegistrar 注册回调面挖出 spring-delta 两处"注册期触达未初始化平台"的时序缺陷（含孪生 NopMybatisSessionFactoryCustomizer，F3 孪生同修）。

## 发现

### [C4-004] spring-delta 两个注册回调在平台初始化前触达 ModuleManager：标准 Spring Boot 启动路径下静默空发现，契约未声明且无 fail-fast

- **文件**: `nop-spring/nop-spring-delta/src/main/java/io/nop/spring/delta/beans/NopBeansAutoConfiguration.java:35-53`；`nop-spring/nop-spring-delta/src/main/java/io/nop/spring/delta/mybatis/NopMybatisSessionFactoryCustomizer.java:30-33`
- **证据片段**:
  ```java
  // NopBeansAutoConfiguration.NopBeansRegistrar（ImportBeanDefinitionRegistrar，refresh 期执行）
  public void registerBeanDefinitions(AnnotationMetadata md, BeanDefinitionRegistry registry) {
      List<IResource> resources = ModuleManager.instance().findModuleResources(false,"/beans", "beans.xml");
      if (resources.isEmpty())
          return;                       // 静默吞掉：空发现与"无 spring-*.beans.xml"不可区分
  // NopMybatisSessionFactoryCustomizer（SqlSessionFactoryBeanCustomizer，singleton 实例化期执行）
  List<IResource> resources = ModuleManager.instance().findModuleResources(false, "/mapper", ".mapper.xml");
  if (!resources.isEmpty()) { ... factoryBean.addMapperLocations(...); }   // 同样静默
  ```
- **严重程度**: P2
- **并发场景**: Spring 启动 main 线程上的两个时序阶段竞态——`ImportBeanDefinitionRegistrar`/`SqlSessionFactoryBeanCustomizer` 回调运行在 `refresh()` 内（ConfigurationClassPostProcessor / singleton 预实例化），而平台模块发现（`VirtualFileSystemInitializer`→`ModuleManager.discover()`）运行在 `NopSpringCoreAutoConfig.onStart`（ApplicationStartedEvent）→ `NopApplication.run` → `CoreInitialization.initialize()`，**严格晚于 refresh**。两阶段由启动线程串行分隔，但注册回调对平台状态的读取没有初始化门禁，结果取决于 app main 是否在 `SpringApplication.run` 之前手工预初始化。
- **触发时序**: 变体 A（标准路径，`SpringDemoMain.java:19-21` 形态——main 仅 `SpringApplication.run`）：T1 refresh 期 registrar 执行 → `ModuleManager.instance()` 返回默认实例（modules 为空 TreeMap，ModuleManager.java:44）→ `getEnabledModules` 空 → `findModuleResources` 空循环体不触 VFS → `resources.isEmpty()` → **return，spring-*.beans.xml 全部漏注册、mapper XML 全部漏装载**；若应用 @Autowired 注入 mapper，随后 singleton 预实例化抛 NoSuchBeanDefinitionException（启动响亮失败）；若仅经 BeanContainer 按名取用，则运行期 `ERR_BEAN_NOT_INITIALIZED`。变体 B（预初始化路径，`SpringMCPServerMain.java:9-12` 形态——main 先 `CoreInitialization.initialize()`）：T0 平台先满级 → T1 refresh 期 registrar 正常发现 → 功能成立。同一模块两种装配路径结果不同，差异点未被任何注释/javadoc 声明。
- **现状**: 注册回调以"平台已初始化"为隐含前提，但契约无声明、空结果无告警、无 `CoreInitialization.isInitialized()` 守卫（对照同模块 `GraphQLToolCallbackProvider` 刻意把平台访问推迟到请求期惰性执行——同一代码族对同一约束的两种处理并存）。
- **风险**: 按 SpringDemoMain 模板新建的宿主应用集成 spring-delta 时功能整体静默失效或启动失败，排障面大（空发现无任何日志）；与 R1 C4-003（HTTP 先于平台初始化）同属"宿主回调与平台初始化序不匹配"族，但站点在注册回调面、后果是装配数据丢失而非请求面 500，独立计数。
- **建议**: 两回调入口增加门禁：`if (!CoreInitialization.isInitialized()) throw new NopException(...)`（fail-fast 把变体 A 从静默空发现转为明确报错，提示先 initialize）；或在空发现时 `LOG.warn` 点名前置条件；长期方向是把发现型装配移出 refresh 期（如挂到 onStart 之后的 BeanDefinitionRegistryPostProcessor 顺序外钩子或 platform-ready 监听）。
- **失效面枚举**: 11 根内 `ModuleManager.instance()` 调用点全量 grep 复现仅 2 处——(a) `NopBeansRegistrar.registerBeanDefinitions`（本条主站点）；(b) `NopMybatisSessionFactoryCustomizer.customize`（本条孪生，F3 孪生同修：同一前置条件、同一静默形态，后果为 mapper 缺失后 "Invalid bound statement" 运行期错误）；(c) `NopMapperFactoryBean`/`MapperProxyFactoryEx`：仅在 (a) 成功产出 bean 定义后才实例化，非独立暴露面；(d) 11 根内其余 refresh 期回调：无（Quarkus 侧 `QuarkusIntegration.start`/`NopApplication.run` 均在 StartupEvent 观察者内串行执行于服务可用前，无注册期触达面）；(e) 平台侧 `ModuleManager._instance` 默认实例的空模块语义（ModuleManager.java:44 `new AtomicReference<>(new TreeMap<>())`）为缺省态依据。
- **信心水平**: 确定（refresh 期与 ApplicationStartedEvent 的先后由 Spring Boot 启动序直接可证；两阶段触达站点与空发现路径均有行级证据）
- **误报排除**: 非"低频启动路径粗粒度同步合理"类误报——缺陷不是并发互斥缺失而是**回调对平台状态的读取无门禁且失败静默**；也未按"已声明的边界"豁免——全模块无任何 javadoc/spring.factories 声明"必须预初始化"，且空列表 early-return 使失败不可观测（F9 静默形态在启动装配面的投影）。
- **复核状态**: 已保留｜P2 维持（复核自证：ModuleManager 调用点全仓 grep 2 处闭合、refresh 期先于 ApplicationStartedEvent 由 Spring Boot 启动序可证、ModuleManager 默认空 modules 实测 ModuleManager.java:44、SpringMCPServerMain 预初始化对照成立；台账查重无同案——cra-index 对 nop-spring-delta 零登记）

## 点名目标验证记录

### 1. @EventListener onStart/onClose 回调阻塞面穷举 — 负结果（收敛于 R1 已立案项）

| 回调 | 站点 | 阻塞内容 | 时长 | 判定 |
|---|---|---|---|---|
| `onStart(ApplicationStartedEvent)` | NopSpringCoreAutoConfig.java:27-34 | registerInstance + `NopApplication().run`（全平台初始化：模块扫描/模型装载/初始化器链） | 秒级 | 请求面暴露已由 **C4-003** 立案；失败路径（run 抛出 → Spring 启动中止 → ContextClosedEvent → onClose → destroy）经 CoreInitialization 幂等短路核验为安全（`initializers==null` return，CoreInitialization.java:160-163），**无新增发现** |
| `onClose(ContextClosedEvent)` | NopSpringCoreAutoConfig.java:36-39 | `CoreInitialization.destroy()`（逆序 initializer destroy + cleanups + 清全局缓存） | 有界（由 initializer 集合决定） | 无条件 destroy 的归属守卫缺失已由 **C6-003** 立案；关闭线程阻塞本身属停机路径合理形态（误报校准第 3 条），负结果 |
| `@PostConstruct/@PreDestroy`（AgroalDataSourceFactoryBean） | AgroalDataSourceFactoryBean.java:29-40 | init: newDataSource（按 initialSize 建连）；destroy: dataSource.close() | 有界（DB 往返） | 配对完整（R1 配对表已核），低频启动/停机路径，负结果 |
| Quarkus `@Observes Filters`（setupSysFilter/setupAppFilter/setupFilter） | HttpServerFilterRegistrar.java:59-64 / ZipContentEncodingFilterRegistrar.java:32 | 注册 lambda（非阻塞） | 微秒级 | 请求期求值窗口已由 C1-006/C4-003 立案，负结果 |
| JVM ShutdownHook（NopApplication.run 每次 run 注册 1 个 destroy disposable） | NopApplication.java:41-44 | destroy | 有界；幂等（二次调用短路） | 累积面已由 R1 C6-002 失效面 (d) 登记为同族伴随项；本轮核验 `ShutdownHook.getInstance().addDisposable` 为全局单例追加式——Spring 测试反复建毁上下文时按上下文数线性累积 disposable（每项持 lambda 无重资源引用），幅度有界，维持伴随项不升级 |

### 2. QuarkusIntegration.start caller 线程阻塞时长 — 负结果

- 站点：`nop-quarkus/nop-quarkus-core-starter/src/main/java/io/nop/quarkus/core/QuarkusIntegration.java:26-52`（全文核算）
- 验证：start() 体内仅 `ConfigProvider.getConfig()` 读取（内存态）、2 次 System.setProperty、2 次 `AppConfig.updateConfigValue`、`BeanContainer.registerInstance`、2 次 Arc 查找、`GlobalMeterRegistry.registerInstance`——**无 I/O、无锁等待，微秒-毫秒级**；caller（app main 的 StartupEvent 观察者）的真实阻塞大头是其后的 `NopApplication.run`（秒级），该窗口的请求面后果已由 C4-003 立案。dev 热重载重复 start() 的全局配置重复写入为幂等覆盖（updateConfigValue 替换语义），meter 重复注册面已由 C6-005 覆盖。**无新增发现。**

### 3. NopBeansRegistrar 注册回调面 — 立案 C4-004（见上）

补充核验：registrar 体内在平台可用前提下的行为（DslNodeLoader XLang 编译、XmlBeanDefinitionReader 装载）为启动单线程重操作——低频路径粗阻塞合理（误报校准第 3 条），不另立案；`reader.loadBeanDefinitions` 每资源新建 reader，无跨上下文共享态。

## 继续判定

**C4 终止，不派第 3 轮。** 依据：(i) 三个点名目标全部闭环——阻塞面穷举表覆盖两宿主全部生命周期回调（8 处）且无一遗漏站点；(ii) 唯一新材料（注册回调面）已立案 C4-004 并完成 F3 孪生清扫；(iii) 宿主层线程创建/池占用面在 R1（零自建 + 三类借用面）与 R2（生命周期回调全表）之后无剩余未审计载体；C4-001～003 的修复与复核价值高于继续挖掘。

## 复核结论（任务 B，独立复核员）

**C4-004 维持 P2 保留。** 复核自证要点：(i) `ModuleManager.instance()` 在 11 根内全仓 grep 仅 2 处（registrar + mybatis customizer），失效面闭合；(ii) 两回调执行期（ConfigurationClassPostProcessor / singleton 预实例化，均在 refresh 内）严格早于 `NopSpringCoreAutoConfig.onStart`（ApplicationStartedEvent），由 Spring Boot 启动序直接可证；(iii) 未初始化态依据实测——`ModuleManager._instance` 默认 modules 为空 TreeMap（ModuleManager.java:44），`discover()` 唯一调用点为 `VirtualFileSystemInitializer`（CoreInitialization 链内）；(iv) 预初始化对照 SpringMCPServerMain.java:9-12 成立；(v) 驳回面检查——若标准路径全量失效则 demo 必然启动失败，实际 nop-spring-demo 含 spring-delta 依赖但其 spring-demo.beans.xml 仅定义 mapper（由本机制导入）——**这恰证明该机制只在预初始化/特定装配下被验证过，契约缺失的判断成立**。阻塞面穷举与 QuarkusIntegration.start 时长核算两项负结果经复核维持（NopApplication.run 的 ShutdownHook 追加面维持 R1 C6-002(d) 伴随项不升级）。**C4 终止判定维持。**
