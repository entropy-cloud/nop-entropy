# C6 资源生命周期与泄漏 — nop-host（WI 4.4）

- 审计单元：nop-host（宿主集成层，合并批 C1+C4+C6 三维度中的 C6 部分）
- 审计日期：2026-10-21（初审，盲审口径）

## 审计范围与口径

**合并批三维度覆盖声明**：见 `C1-shared-mutable-state.md` 同名节。本文件承载 C6 维度发现；容器启停关闭序/重启残留/资源流关闭/资源生命周期等任务简报点名站点全部覆盖，逐一给出发现或"核验无缺陷"结论。

**降档口径声明（C6 专属）**：**泄漏与残留形态一律不适用启动期单线程降档**——即使在启动期单线程触达，凡生命周期不对称（注册无注销、获取无释放、destroy 缺失/误伤）均按全价判级。请求面路径不降档。

### C6 资源获取点配对表（获取 × 释放路径 × 异常/取消路径 × 判定）

| 资源 | 获取点 | 正常释放 | 异常/取消释放 | 判定 |
|---|---|---|---|---|
| MultipartFile/InputStream（上传） | SpringFileService.java:46 | DaoResourceFileStore.saveFile:241（消费端 finally） | **校验失败（ext/bizObj/auth/size）先于 saveFile 抛出 → 无释放** | **C6-001** |
| RESTEasy InputPart InputStream（上传） | QuarkusFileService.java:76 | 同上（消费端） | 同上；缓冲区背书，无 fd | **C6-001 失效面** |
| AgroalDataSource | AgroalDataSourceFactoryBean.init:29-33 | destroy:36-40 @PreDestroy close | init 抛出时 bean 不进入销毁链（新建失败无池可关，合规） | 配对合规；指标侧见 **C6-005** |
| Micrometer Gauge 绑定 | AgroalDataSourceFactory.java:108-183（.register） | **无反注册** | 数据源重建后旧 Gauge 留存 | **C6-005** |
| AsyncContext（servlet 流式） | ServletHttpServerContext.java:237/:313 startAsync | complete():284/:336（CAS 守卫幂等） | fail/completeWithError CAS 守卫；**无 setTimeout/AsyncListener** | **C6-004** |
| ClassPathResource 流（zip 过滤器） | ZipContentEncodingFilter.java:53-71 / ZipContentEncodingFilterRegistrar.java:44-62 | exists() 内部打开即关（Spring/平台实现）；转发后由资源 handler 关闭 | 无持有窗口 | 核验无缺陷（见下方核验记录） |
| 动态代理（Mapper） | MapperProxyFactoryEx.java:28 | 无需释放（GC 托管） | — | 核验无缺陷 |
| ByteArrayOutputStream→ByteArrayResource | NopBeansRegistrar.java:55-63 | 内存对象，GC 托管 | — | 核验无缺陷 |
| SpringResource.getInputStream 委托 | SpringResource.java:78-80 | 调用方关闭（Spring Resource 契约） | 调用方契约 | 核验无缺陷 |
| WS handler/keepalive 定时任务 | 两宿主端点 onOpen | onClose/onThrowable cancel | onOpen 中途抛错 → 滞留 | 归 C4-002（跨维度引用） |
| WebClientHolder/webClients CHM | SpringWebProxy.java:160-177 | 常驻缓存（设计意图） | 无 close 需求（WebClient 无显式资源句柄持有） | 核验无缺陷（长驻形态见 C6-002 附注） |
| CoreInitialization 全局态 | onStart / NopApplication.run | Spring: onClose→destroy；**Quarkus: 仅靠 app main 自觉** | 初始化失败：initializeTo catch→destroy（自清理合规） | **C6-002 / C6-003** |
| ResponseOutputStream（vertx 流拷贝） | VertxHttpServerContext.java:197 | 随请求 GC | — | **C6-006**（write(int) 吞字节） |

## 发现

### [C6-001] 上传流在"先校验后消费"失败路径无释放：校验异常直接抛出，InputStream 永不关闭

- **文件**: `nop-spring/nop-spring-file/src/main/java/io/nop/file/spring/web/SpringFileService.java:44-60`；消费端证据 `nop-file/nop-file-dao/src/main/java/io/nop/file/dao/store/DaoResourceFileStore.java:212-241`、校验链 `nop-service-framework/nop-biz-file-core/src/main/java/io/nop/file/core/NopFileStoreBizModel.java:102-107`
- **证据片段**:
  ```java
  // SpringFileService.java:46-58 —— 打开后即交出所有权，本地无 close/finally
  InputStream is = file.getInputStream();
  ...
  UploadRequestBean input = buildUploadRequestBean(is, fileName, ...);
  res = uploadAsync(buildApiRequest(request, input));   // 异步交给 GraphQL 引擎
  ...
  // NopFileStoreBizModel.java:102-107 —— 消费(saveFile)之前的同步校验
  checkMaxSize(record.getLength());      // 超限 → NopException
  checkFileExt(record.getFileExt());     // 非法扩展名 → NopException
  checkBizObjName(record.getBizObjName());
  checkUploadAuth(record, context);      // 鉴权失败 → NopException
  String fileId = fileStore.saveFile(record, maxFileSize);  // 仅此路径内 finally 关闭流
  ```
- **严重程度**: P1
- **并发场景**: HTTP 工作线程打开流；GraphQL 引擎异步链（可能跨线程）消费流；校验失败发生在引擎入口同步段（消费开始前），此时打开流的工作线程已返回、流句柄仅存于 `UploadRequestBean`。
- **触发时序**: T1（工作线程）：`file.getInputStream()` 打开 multipart 临时文件的 FileInputStream → 组装请求 → `uploadAsync` 提交；T2（引擎线程）：`uploadFile` 校验段 `checkFileExt`（用户可控字段，`.exe`/空 bizObjName/无权限均可命中）抛 `NopException` → 异常沿 `executeRpcAsync` 传播为错误响应；T3：`DaoResourceFileStore.saveFile`（唯一 finally 关闭点 :241）从未执行 → 流对象随请求完成不可达，fd 直到 GC 终结才释放。交叠后果：**每个被校验拒绝的上传请求泄漏 1 个文件描述符**，可被外部稳定重复触发（恶意或误配置客户端）。
- **现状**: 获取（controller）与释放（DAO 消费端）之间隔着完整异步引擎调用与 4 道校验，任何一道先行失败即形成"半个获取"——清理责任没有绑定到获取侧（对照 F8 清理不对称）。
- **风险**: fd 持续累积至进程 ulimit → accept 失败/全服务 IO 失效（P1 判级依据：错误路径资源泄漏 + 明确触发链 + 外部可稳定复现）。
- **建议**: 在 SpringFileService 包裹 try/finally：`res.whenComplete((v,e) -> IoHelper.safeCloseObject(input))` 不够（校验失败时 input 未进入消费链仍可达）——最直接的是让 `UploadRequestBean` 实现 AutoCloseable 并在 controller 返回前注册 `res.whenComplete(...)` 关闭；或把 `checkMaxSize/checkFileExt/checkBizObjName/checkUploadAuth` 前移到 `getInputStream()` 之前（先用元数据校验再开流）。
- **失效面枚举**: (a) Spring 宿主 `SpringFileService.upload`（本条主形态，file-backed fd 泄漏）；(b) Quarkus 孪生 `QuarkusFileService.uploadFileAsync` :76-90（`inputPart.getBody(InputStream.class, null)` 同样先开流后进校验链——RESTEasy 缓冲区背书，泄漏面为堆内流而非 fd，同根因同修）；(c) 平台内其他 UploadRequestBean 构造点（`NopDatavExportTaskBizModel`、`ReportDeliveryExecutor`）——不在本单元 11 根内，登记为跨单元同族提示，未逐验；(d) 下载路径（Spring :88-100 / Quarkus :163-178）：无本地获取点（响应体由资源 handler/StreamingOutput 自管），合规；(e) 成功路径：saveFile:241 finally 兜底，合规。
- **信心水平**: 确定
- **误报排除**: 非"Spring 容器会清理 multipart 临时文件"型误报——临时文件删除与 fd 关闭是两回事，泄漏的是打开的 FileInputStream 句柄；也不是平台基础设施内部问题（saveFile 配对正确），缺陷定位在宿主 controller 的获取-交接契约。
- **复核状态**: 已保留｜P1 维持。回码验证（本轮逐行复核）：SpringFileService.java:46 getInputStream 后无任何 finally；NopFileStoreBizModel.java:102-107 四道校验（行号实测吻合）先于 :109 saveFile；DaoResourceFileStore.java:240-241 `IoHelper.safeCloseObject(is)` 为唯一关闭点且仅在消费路径——校验拒绝时流随 UploadRequestBean 不可达。台账查重：cra-index P1 表无 nop-file/nop-spring 同案（nop-network C6-005 为 BASE64 上传不同站点，家族不重复计数）。现状：新发现未修复。

### [C6-002] Quarkus 宿主集成只 start 不 stop：平台 destroy 责任下放给每个 app main 自觉，dev 热重载残留无清理

- **文件**: `nop-quarkus/nop-quarkus-core-starter/src/main/java/io/nop/quarkus/core/QuarkusIntegration.java:26-52`
- **证据片段**:
  ```java
  public class QuarkusIntegration {
      public static void start() {
          ... // 注册 BeanContainer、GlobalMeterRegistry、写入全局配置
          BeanContainer.registerInstance(new NopQuarkusBeanContainer());
          ...
      }
      // 文件内无任何 stop/destroy/ShutdownEvent 观察——对比 Spring 宿主：
      // NopSpringCoreAutoConfig.java:36-39
      // @EventListener public void onClose(ContextClosedEvent event) { CoreInitialization.destroy(); }
  ```
- **严重程度**: P2
- **并发场景**: 宿主生命周期单线程（启动 main / ShutdownEvent 分发线程）；跨实例残留影响运行期请求线程（读全局缓存/注册表）。按 C6 降档口径，**残留形态不降档**。
- **触发时序**: 变体 A（进程内热重载，Quarkus dev/live-reload）：T1 第一轮启动：`QuarkusIntegration.start` + `NopApplication.run` → `CoreInitialization.initialize` 至满级，`GlobalCacheRegistry`/`ReflectionManager`/模型缓存充填；T2 dev 改码触发 Quarkus 重启：新 classloader 重跑 `start`+`run` → `CoreInitialization.initializeTo`（CoreInitialization.java:82-84）因 `initializationLevel >= level` **直接返回**，旧静态态全量存续 → 旧资源模型/缓存条目/已关闭数据源引用残留；变体 B（整进程）：宿主集成层未注册 ShutdownEvent→destroy，关闭清理完全依赖每个 app main（QuarkusDemoMain.java:29-31 模式）自行 `@Observes ShutdownEvent`——遗漏该观察者的部署进程退出前无任何平台回收（daemon 线程兜底使进程能退出，但 close 钩子/flush 类清理全部跳过）。
- **现状**: Spring 宿主在集成层内建 onClose→destroy 配对，Quarkus 宿主同等集成面缺失 stop 侧——宿主间生命周期对称性破缺（F8）。
- **风险**: 热重载后语义漂移（模型/缓存陈旧、meter 重复注册——与 C6-005 叠加）；生产部署若复用 demo 装配模板漏写 destroy，则停机路径资源回收（执行器、连接池 close 钩子、临时资源）静默缺失。
- **建议**: 在 QuarkusIntegration 增加配对方法并在集成层注册 `@Observes ShutdownEvent`（或提供可复用的 Observer 类）：`public static void stop() { CoreInitialization.destroy(); }`；热重载场景在 start 前检测 `CoreInitialization.isInitialized()` 并先 destroy（或调 `reinitialize()`）。
- **失效面枚举**: (a) `QuarkusIntegration.start`（本条）；(b) Spring 宿主 `NopSpringCoreAutoConfig.onClose`：已配对（但其自身缺陷见 C6-003）；(c) `SpringMCPServerMain` :9-12：手动 initialize、**无 destroy**（进程退出即终结，daemon 兜底，登记低风险伴随项）；(d) Spring `NopApplication.run`（nop-boot）注册 JVM ShutdownHook→destroy：Spring/Quarkus 宿主各次 run 均追加一个 destroy 任务——destroy 幂等（CoreInitialization.java:161-163 `initializers==null` 短路），多次注册无害但计数膨胀，列为同族伴随项；(e) `AgroalDataSourceFactoryBean.destroy`：独立配对，合规（见配对表）。
- **信心水平**: 确定（不对称结构直接可证；变体 A 的陈旧程度依赖 Quarkus 重启后静态类是否同 loader，按 base-classloader 装载的平台 kernel jar 判定残留成立）
- **误报排除**: 不报"daemon 线程导致无法验证泄漏"——立项点是集成层生命周期配对缺失这一结构性不对称，有 Spring 宿主对照形态与具体残留链；也不与 C6-003 重复计数（两者是互为镜像的两个宿主缺陷）。
- **复核状态**: 已保留｜P2 维持。回码验证：QuarkusIntegration.java:26-52 全文确认无 stop/ShutdownEvent 观察（仅 start）；Spring 对照 NopSpringCoreAutoConfig.java:36-39 onClose→destroy 配对属实；app main 自觉模式实测（NopTaskApplication.java:17-19 `@Observes ShutdownEvent → CoreInitialization.destroy()`——预 init/destroy 责任确在各 app main）。destroy 幂等短路（CoreInitialization.java:160-163）使 (d) 项多次注册无害判定成立。台账查重：与 WI 4.2 C6 面（cluster）零同案；WI 2.2 C6-002（插件状态机）同名异站。现状：新发现未修复。

### [C6-003] Spring onClose→CoreInitialization.destroy 无归属守卫：多 Spring 上下文共 JVM 时先关闭者拆掉在役上下文的地板

- **文件**: `nop-spring/nop-spring-core-starter/src/main/java/io/nop/spring/core/autoconfig/NopSpringCoreAutoConfig.java:36-39`
- **证据片段**:
  ```java
  @EventListener
  public void onClose(ContextClosedEvent event) {
      CoreInitialization.destroy();   // 无条件：清 initializers 链 + GlobalCacheRegistry.clearAllCache()
  }                                   // + ReflectionManager.clearCache()（CoreInitialization.java:196-205）
  ```
- **严重程度**: P2
- **并发场景**: 同 JVM 多 Spring 上下文（spring-test 套件常态：@SpringBootTest 多配置类/多 profile 并存；@DirtiesContext 反复建毁）——上下文 B 在役服务请求（工作线程读全局缓存/反射元数据），上下文 A 关闭触发 destroy。
- **触发时序**: T1：测试上下文 A 启动 → onStart 注册容器 + 初始化平台（已满级）；T2：上下文 B 启动 → onStart 再次注册容器（覆盖）、initializeTo 短路返回；T3：A 结束触发 ContextClosedEvent → `destroy()` 清空 initializers 链、`GlobalCacheRegistry.instance().clearAllCache()`、`ReflectionManager.instance().clearCache()`、`ResourceTenantManager.clearAllTenants()`（CoreInitialization.java:196-205）；交叠后果：仍在跑的上下文 B 的请求线程此后访问被清空的全局缓存（模型/ORM 元数据按需重载可自愈，但**已销毁的 initializer 资源**——如数据源、执行器——不可自愈）→ B 的后续请求不可预测失败。反向时序（destroy→restart 重启窗口）：destroy 后 `initializationLevel=-1`，重新 initialize 可行——重启语义本身闭合，缺陷集中在共存拆毁。
- **现状**: destroy 与 BeanContainer 注册一样是全局单例操作，但注册侧有"后者覆盖"语义，destroy 侧没有"仅最后一个使用者关闭"的守卫（无引用计数/无 ownership 校验）。
- **风险**: 测试套件级联失败（red herring 掩盖真实缺陷）；生产中父子上下文（actuator 独立 context 等）场景同类拆毁。
- **建议**: onClose 增加 guard：仅当 `BeanContainer.instance()` 即本上下文容器（引用相等）时才 destroy；或以平台侧计数器（initialize/destroy 配对计数）替代布尔态。
- **失效面枚举**: (a) `NopSpringCoreAutoConfig.onClose`（本条）；(b) `NopApplication.run` ShutdownHook destroy（nop-boot）：JVM 退出期执行，无共存拆毁面，伴随项；(c) `CoreInitialization.destroy` 自身：synchronized + 幂等短路，合规；(d) Quarkus 侧对应问题为 C6-002（不 destroy），二者同根因（全局态与宿主生命周期无绑定）不同形态，分开计数。
- **信心水平**: 很可能（多上下文共存触发链具体；生产发生频率低，测试基建中常见）
- **误报排除**: 未按"单线程启动回调"降档漏报（C6 口径：残留/拆毁形态不降档）；也未报 destroy 本身非幂等（已验证幂等）——立项点是守卫缺失而非实现缺陷。
- **复核状态**: 已保留｜P2 维持 + 机制补证。回码验证：NopSpringCoreAutoConfig.java:36-39 无条件 destroy 属实；destroy 全局清理面（initializers 链/GlobalCacheRegistry/ReflectionManager/ResourceTenantManager，CoreInitialization.java:158-206）属实。复核补证：IocCoreInitializer.destroy 会 `BeanContainer.registerInstance(parentContainer)` 把 facade 回写为宿主桥（nop-ioc IocCoreInitializer.java:56-61）——多上下文场景下先关闭者触发 destroy 后，在役上下文的请求不仅失去 initializer 资源，BeanContainer facade 还会落在宿主桥上（nop-IoC bean 查找面收窄），C6-003 的拆毁后果链比 R1 描述更闭合。现状：新发现未修复。

### [C6-004] servlet 流式响应 startAsync 未设超时且无 AsyncListener/onTimeout：容器默认超时截断长流，超时后写路径抛错链未闭合

- **文件**: `nop-spring/nop-spring-web-starter/src/main/java/io/nop/spring/web/filter/ServletHttpServerContext.java:228-301,303-373`
- **证据片段**:
  ```java
  // :237-239（Flow.Publisher 变体；:313-315 writerFn 变体同构）
  AsyncContext asyncCtx = request.startAsync();   // 未 setTimeout，无 addListener
  CompletableFuture<Void> future = new CompletableFuture<>();
  AtomicBoolean completed = new AtomicBoolean(false);
  ```
- **严重程度**: P2
- **并发场景**: 容器异步超时管理线程 vs 流式写线程（publisher 信号线程 / writerFn 调用线程）。长 SSE/代理流（分钟级）超过容器默认异步超时（Tomcat 默认 30s，未显式配置时）。
- **触发时序**: T1：`startAsync()` 无 setTimeout → 容器 30s 计时；T2：流仍在写 → 超时到期，容器无监听器可通知，直接完成响应；T3：后续 `out.write`/`resp.write` 抛 IllegalStateException → `completeWithError` CAS 后再调 `asyncCtx.complete()`（:294）——对容器已完成的 async 再 complete 抛 ISE 且此处未捕获，异常沿 publisher onError 链外泄。交叠后果：长流被无提示截断 + 二次异常噪声；Quarkus 孪生（VertxHttpServerContext :205-343）无此机制（vertx 以 idle timeout 管理），两宿主流式语义不对称。
- **现状**: CAS 幂等守卫（completed）设计正确（complete/fail 双路径已核验），缺失的是**超时策略显式化**：既未 `setTimeout(0)` 声明长流意图，也未挂 AsyncListener 感知容器超时。
- **风险**: 以 Spring servlet 宿主承载流式场景（nop-gateway `GatewayHttpFilter.java:236` 经此路径）时，>30s 的流必然截断——按部署配置可能升级为主路径功能性故障。
- **建议**: `asyncCtx.setTimeout(0)`（显式长流）并注册 `AsyncListener.onTimeout` 走 completeWithError 统一收口；在 completeWithError 内层 try 包裹 `asyncCtx.complete()`（幂等容错）。
- **失效面枚举**: (a) `sendStreamingResponse(Flow.Publisher)` :228-301（本条）；(b) `sendStreamingResponse(writerFn)` :303-373（同构，:313-315）；(c) 非 streaming 路径 `sendResponse` :202-225：同步完成无 async，不涉及；(d) Quarkus 孪生 `VertxHttpServerContext.sendStreamingResponse` :205-343/:271-343：无 AsyncContext 机制，豁免但登记宿主间语义差异；(e) 平台消费方 `GatewayHttpFilter`（nop-gateway）：受此行为影响的上游使用者，登记跨单元提示。
- **信心水平**: 很可能（超时截断链具体；容器默认值依赖部署配置，Tomcat 默认 30s 为基准）
- **误报排除**: 未把 CAS 复合态本身当缺陷（已判定守卫正确）；立项点是生命周期参数缺失（无超时声明、无监听器）与超时后的错误收口漏洞，均有具体 T1/T2/T3。
- **复核状态**: 已保留｜P2 维持。回码验证：ServletHttpServerContext.java:237-239/:313-315 startAsync 无 setTimeout 无 addListener 属实；Quarkus 孪生 :205-343 无 AsyncContext 机制对照属实。关联订正：同函数错误路径调用 complete() 而非 completeWithError() 的错误截断面已另立 C6-008（R2，不同根因不重复计数）。台账查重：与 WI 3.1 C7-001（流式聚合无全程期限）家族关联、异站零重复。现状：新发现未修复。

### [C6-005] 数据源指标 Gauge 与数据源生命周期不对称：重建同名数据源后 Gauge 仍绑定旧池，Micrometer register 幂等返回旧表

- **文件**: `nop-quarkus/nop-quarkus-core-starter/src/main/java/io/nop/quarkus/core/dao/AgroalDataSourceFactory.java:34-36,108-183`
- **证据片段**:
  ```java
  if (config.isMetricsEnabled()) {
      exportMetrics(GlobalMeterRegistry.instance(), config.getName(), dataSource);  // 每次新建即注册
  }
  // :112-116（×17 个 Gauge 同构）
  Gauge.builder("agroal.active.count", metrics::activeCount)   // metrics = 旧池的 getMetrics()
          .tag("datasource", tagValue)
          .register(metricsFactory);                            // Micrometer：同名同标签 → 返回既有 Gauge，新绑定被丢弃
  ```
- **严重程度**: P2
- **并发场景**: 平台重初始化线程（`CoreInitialization.reinitialize`/模型热更新触发数据源重建，经 `IDataSourceFactory.newDataSource`）与指标抓取线程（micrometer 定期轮询 Gauge lambda）。
- **触发时序**: T1：首建数据源 `ds#1`，17 个 Gauge 绑定 `ds#1.getMetrics()` 注册进 GlobalMeterRegistry；T2：重初始化关闭 `ds#1`（AgroalDataSourceFactoryBean.destroy）并新建 `ds#2` → `exportMetrics` 再次 `.register(...)`；T3：Micrometer 对相同 name+tag 返回**既有 Gauge**（绑定 ds#1 闭包），ds#2 的新绑定被静默丢弃。交叠后果：监控面板从此读已关闭池的指标（active/available 恒 0 或僵值），无任何告警；与 C6-002 dev 热重载场景叠加必然发生。
- **现状**: 注册（register）无配对反注册/重绑定——典型 F8 注册不对称；Micrometer 幂等语义掩盖了失效。
- **风险**: 长时运行/热更新部署下连接池可观测性静默失效；容量决策与泄漏排查（agroal.leak.detection.count）失真。
- **建议**: 注册前按 `metricsFactory.find("agroal.active.count").tag("datasource", tagValue)` 判存，存在则先 `remove`；或 Gauge lambda 改持 `AtomicReference<AgroalDataSourceMetrics>` 由工厂重建时替换引用。
- **失效面枚举**: (a) `exportMetrics` 17 个 Gauge 全部同构（:112-182），一修全修；(b) `QuarkusIntegration.start:48-50` 的 `GlobalMeterRegistry.registerInstance`：覆盖式静态注册，`registerInstance` 语义为替换（平台侧），合规；(c) 本单元其余 micrometer 注册点：无（grep 复现）。
- **信心水平**: 确定（Micrometer register 幂等返回既有 meter 是规范语义；触发条件为数据源重建）
- **误报排除**: 非"有界声明内的合理缓存"误报——Gauge 是注册型资源，重建场景客观存在（reinitialize API 为平台公开能力）；未与 C6-002 重复计数（此处是指标资源的独立不对称点）。
- **复核状态**: 已保留｜P2 维持。回码验证：AgroalDataSourceFactory.java:34-36 每次新建即 exportMetrics、:112-182 十七个 Gauge.builder().register() 同构属实；Micrometer 同名同标签 register 返回既有 meter 为规范语义。台账查重：WI 2.5 计数口径有"C6-005 双轨"登记（per 索引），本单元 C6-005 为宿主数据源指标面独立站点，维持独立。现状：新发现未修复。

### [C6-006] ResponseOutputStream.write(int) 静默吞字节：单字节写入路径数据丢失（当前调用面安全，防御性缺陷）

- **文件**: `nop-quarkus/nop-quarkus-web/src/main/java/io/nop/quarkus/web/filter/VertxHttpServerContext.java:425-440`
- **证据片段**:
  ```java
  static class ResponseOutputStream extends OutputStream {
      @Override
      public void write(int b) throws IOException {}    // 空实现：字节被丢弃
      @Override
      public void write(byte[] b, int off, int len) throws IOException {
          Buffer buf = Buffer.buffer().appendBytes(b, off, len);
          resp.write(buf);
      }
  }
  ```
- **严重程度**: P3
- **并发场景**: 无（生命周期/正确性缺陷）；调用点为 `sendResponse(InputStream)` :197 经 `IoHelper.copy`（已核验 IoHelper.java:190-201 仅用 `write(buf,0,n)` 字节数组重载）。
- **触发时序**: 当前在库调用面不触发（copy 全走数组重载）；任何未来调用方（或 InputStream.transferTo 等按字节退化的拷贝实现）写入单字节即静默丢失，无异常无日志。
- **现状**: 防御性实现缺陷；命名与 OutputStream 契约相悖。
- **风险**: 潜伏数据丢失点；作为公共宿主工具类被复用时扩大。
- **建议**: `write(int b)` 实现 `write(new byte[]{(byte) b}, 0, 1)`，或直接复用 `Buffer.buffer().appendByte((byte) b)`。
- **失效面枚举**: (a) 本类两 write 重载（本条）；(b) 同文件其余输出路径（:182-202 send 系列）不经该类，合规；(c) Spring 宿主对应流式 :216-225 用 `IoHelper.copy(body, response.getOutputStream())` 原生 servlet 流，无自定义 OutputStream，无同族缺陷。
- **信心水平**: 确定（缺陷形态确定；当前无触发调用）
- **误报排除**: 虽当前不可达，仍按防御性代码缺陷登记 P3 而非剔除——属"资源/输出契约不对称"类别，且修复成本一行；未夸大为 P1/P2。
- **复核状态**: 已保留｜P3 维持。回码验证：VertxHttpServerContext.java:432-433 write(int) 空实现属实；IoHelper.copy 仅用数组重载（R1 :169 已核，本轮维持）。现状：新发现未修复。

### 核验无缺陷记录（任务简报点名站点的显式排除结论）

1. **ZipContentEncodingFilter 资源流关闭**（Spring :53-88 / Quarkus Registrar :44-63）：过滤器自身从不打开资源流——`exists()` 由 Spring ClassPathResource / 平台 ClassPathResource 实现在内部打开-关闭（try-with-resources 语义）；`forward`/`reroute` 后的实际资源流由容器 ResourceHandler 打开并在写完响应后关闭；`IndexHtmlProvider.getIndexHtml()` 返回 String。**无泄漏点。**
2. **MapperProxyFactoryEx 代理生成**（:23-29）：每次 `getObject()` 新建代理、代理类生成锚定 `getMapperInterface().getClassLoader()`，无缓存无注册表；Spring singleton 契约下单次调用；MapperProxy 内部 methodCache 为 CHM（MyBatis 自身）。**无生命周期缺陷。**
3. **WebClientHolder/WebClientConfig 资源生命周期**：WebClient 实例为轻量句柄集合（连接池由 reactor-netty 全局设施管理），无 close 语义；CHM 常驻缓存按 config-dir 文件数有界。凭据并发问题归 C1-001，不在此重复计数。**无资源泄漏。**
4. **NopBeansRegistrar 字节流**（:55-63）：ByteArrayOutputStream 内存态，无关闭需求；`reader.loadBeanDefinitions` 注册期单线程。**合规。**
5. **NopSpringTransactionFactory 关闭序**（:108-116）：afterCompletion 中 `TransactionRegistry.remove(group, txn)`（双参 remove 防误删）→ afterCompletion 通知 → finally `txn.close()`；Spring 事务同步机制保证每线程的 synchronization 单次回调，close 与 doClose 内 `txn=null` 幂等。TransactionRegistry 经 ContextProvider 为请求上下文作用域（TransactionRegistry.java:22-30），无跨请求互扰。**配对合规。**
6. **两 WS 端点 onClose**（Spring :131-137 / Quarkus :132-137）：handler 经 userProperties 取出调用 `handler.onClose()`；session 关闭后整体由容器回收；onError+onClose 双回调下 handler 侧 cancel/清理幂等（JsonRpcWebSocketHandler cancel(false)、remove 双参）。**配对合规**（keepalive 装配窗口除外，归 C4-002）。

## 维度集外线索（overlay C2/C3/C5/C7/C8/C9）

### [nop-host-CL-006] webClients 常驻缓存无失效与刷新通道：配置文件修改后需重启生效；holder 凭据无 TTL（C7）
- 站点：`nop-spring/nop-spring-proxy/src/main/java/io/nop/spring/proxy/SpringWebProxy.java:159-177`。`computeIfAbsent` 后条目永驻，`{provider}.cfg.yaml` 的 baseUrl/headers 修改不可达；authorization/cookie 运行期被 refresh 更新后也无持久化/失效策略。长时运行下的陈旧配置源（非单调增长，C7 归档为陈旧态线索）。建议：接入平台 ICache（TTL/容量）或提供管理端点触发 reload。
- 现状标注：新线索，已确认（复核回码属实；无同案）。

### [nop-host-CL-007] WS 端点 onError 与 onClose 双回调叠加于同一 handler；Quarkus onOpen 中途失败致 userProperties 无引用（C3 回调序列）
- 站点：`nop-spring/nop-spring-web-starter/.../ws/JsonRpcWebSocketEndpoint.java:131-145`；`nop-quarkus/nop-quarkus-web/.../ws/JsonRpcWebSocketEndpoint.java:131-146`。容器异常断链时 onError→onClose 顺序投递，handler 侧 `onThrowable`（cancel keepalive）+`onClose`（cancel + activeOperations 清理 + session.close）二次执行——已核验幂等无害；但 Quarkus onOpen :70 `getBeanByType(IUserContextExtractor.class)` 抛错时 handler 已构造、userProperties 未 put，onClose 取不到 handler（与 C4-002 滞留窗口同一时序，本条登记其 C3 回调契约面）。建议：两端点统一 tryGet + 装配失败兜底清理。
- 现状标注：新线索，已确认（复核回码属实；无同案）。

（C1 维度已登记 CL-001～CL-004，C4 维度续接 CL-005；本维度自 CL-006 起续接至 CL-007。）

## 维度复核结论（任务 B，独立复核员，2026-10-21）

**判定统计**：6 条初审 + 2 条 CL 全部回码验证；保留 6、降级 0、驳回 0。R2 新增 C6-007/008/009 + CL-008 经复核全部维持（见 C6-round2.md 复核结论）。

**逐条裁定要点**：
- **C6-001（保留 P1）**：本轮对关键证据行做了精确回码——SpringFileService.java:46 开流无 finally、NopFileStoreBizModel.java:102-107 四道校验行号实测吻合且先于 saveFile、DaoResourceFileStore.java:240-241 safeCloseObject 为唯一关闭点。外部可稳定触发的 fd 泄漏判级成立。
- **C6-002（保留 P2）**：QuarkusIntegration 无 stop 侧确认；app main 自觉模式实测（NopTaskApplication.java:17-19）；destroy 幂等短路使 (d) 伴随项判定成立。
- **C6-003（保留 P2 + 机制补证）**：复核新证 `IocCoreInitializer.destroy` 会把 BeanContainer facade 回写为宿主桥（registerInstance(parentContainer)），多上下文拆毁后在役上下文的查找面进一步收窄——后果链比 R1 描述更闭合，判级不变。
- **C6-004（保留 P2）**：startAsync 无 setTimeout/无 AsyncListener 属实；同函数错误路径 complete-vs-completeWithError 面已拆分为 C6-008（不同根因），互不重复计数。
- **C6-005（保留 P2）**：十七 Gauge 同构 + Micrometer register 幂等语义成立；cra-index 记载的 WI 2.5"C6-005 双轨"为本单元独立站点，维持独立。
- **C6-006（保留 P3）**：write(int) 空实现 + 零触发调用面复现，防御性 P3 判级恰当。

**台账查重与家族命中清单（cra-index WI 1.1–4.3）**：
1. **C6-007（R2）命中"future 永不完成族"**——台账已计 4 例（WI 4.2 C3-001 重试回调异常吞没、WI 4.3 C3-001 OkHttp onResponse 吞没、WI 3.2 C1-001 executeNow.get 同步抛出 future 失联【与 C6-007 机制完全同构】、WI 1.7 C6-001 Error 逃逸 future 永不完成；另有 WI 1.6 C8-012 返回 future≠挂接 future 变体）→ **C6-007 为该族第 5 例（宿主单元成员）**，平台根因 ContextTaskQueue 吞异常关联 WI 2.3 CL-018（ContextTaskQueue 承重原语→nop-kernel 台账）与 WI 2.1 C3-001（周期任务异常绝缘层）。零同站点重复。
2. **C6-008（R2）命中"错误以成功交付族"**——台账已计 3 例（WI 3.4 C3-005 RagSynthesize 错误折叠 200、WI 3.1 C7-002 流中断误判 onComplete、WI 4.3 C3-005 无校验跳过 rename 报成功）→ **C6-008 为传输层成员（chunked 截断洗白）**，家族关联单列、零同站点重复。
3. 任务简报点名的 **WI 4.3 MA-02（JDK 取消语义失效）/MA-03（sidecar reactor 阻塞）同族复查**：本维度无同案（宿主无 cancelToken 绑定面；C4-001 与 MA-03 家族关联已在 C4 文件登记）；**WI 2.1 CL-021 漂移复查**：宿主零 raw 线程载体，无漂移。

## 最终保留项（C6，合并 R1+R2）

| 编号 | 标题 | 判级 | 复核裁定 |
|---|---|---|---|
| C6-001 | 上传流校验失败路径永不关闭 | P1 | 保留 |
| C6-002 | Quarkus 宿主集成只 start 不 stop | P2 | 保留 |
| C6-003 | Spring onClose→destroy 无归属守卫 | P2 | 保留（补证） |
| C6-004 | startAsync 无超时无 AsyncListener | P2 | 保留 |
| C6-005 | 数据源 Gauge 重建后旧绑定 | P2 | 保留 |
| C6-006 | write(int) 吞字节 | P3 | 保留 |
| C6-007（R2） | withRoutingContext 同步异常被吞→请求悬挂 | P1 | 保留（future 永不完成族第 5 例） |
| C6-008（R2） | servlet 错误路径 complete() 洗白截断响应 | P2 | 保留（错误以成功交付族成员） |
| C6-009（R2） | sendResponse(InputStream) 释放契约未声明 | P3 | 保留（潜伏） |
| CL-006/007/008 | webClients 无失效通道 / WS 双回调 / isResponseSent 不对称 | CL | 3 条全确认 |

R2 深挖（C6-round2.md）：3 条新发现（P1×1/P2×1/P3×1）+ 1 条 CL + destroy 幂等/双调用核验无缺陷；C6 终止，不派第 3 轮。
