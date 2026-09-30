# G5: nop-service-framework 深度审计（首轮）

- **审计日期**: 2026-09-30
- **执行维度**: 07（BizModel 规范遵循——审 CrudBizModel/GraphQL 引擎本体）、09（错误处理与错误码）、12（GraphQL 与 API 层）、13（安全与权限模型）、14（异步与事务模式）
- **子代理**: 首轮初审子代理（维度 07/09/12/13/14 合并执行，本文即首轮完整发现）

## 审计范围

- **模块**: `nop-service-framework/` 全部子模块（nop-biz 111、nop-graphql 236、nop-biz-auth-api 28、nop-biz-auth-core 74、nop-biz-file-core 19、nop-gateway 62 个手写 Java 文件，共 530；含 nop-graphql-core/-grpc/-message/-orm）。
- **深读文件**（节选）: `nop-biz/src/main/java/io/nop/biz/crud/CrudBizModel.java`（全文 2272 行）、`crud/ObjMetaBasedValidator.java`、`crud/ObjMetaBasedFilterValidator.java`、`crud/ManyToManyTool.java`、`proxy/BizProxyInvocationHandler.java`、`dev/DevDocBizModel.java`、`dev/DevStatBizModel.java`、`dev/DevToolBizModel.java`、`decorator/TransactionActionDecorator(Collector).java`、`service/BizActionInvoker.java`、`impl/BizObjectBuilder.java`、`impl/DefaultBizAuthChecker.java`、`_vfs/nop/biz/beans/biz-defaults.beans.xml`；`nop-graphql-core` 的 `engine/GraphQLEngine.java`、`engine/GraphQLExecutor.java`、`engine/GraphQLActionAuthChecker.java`、`engine/GraphQLArgumentValidator.java`、`engine/GraphQLSelectionResolver.java`、`engine/GraphQLTransactionOperationInvoker.java`、`engine/CancelTokenManager.java`、`schema/BuiltinSchemaLoader.java`、`web/GraphQLWebService.java`、`web/SysBizModel.java`、`command/GraphQLCommandExecutor.java`、`jsonrpc/JsonRpcService.java`、`subscription/GraphQLSubscriptionManager.java`、`reflection/GraphQLBizModel.java`（关键节）；`nop-graphql-orm` 的 `fetcher/OrmEntityPropConnectionFetcher.java`；`nop-biz-auth-core` 的 `filter/AuthHttpServerFilter.java`、`filter/AuthFilterConfig.java`、`filter/StateCookieHelper.java`、`jwt/JwtHelper.java`、`login/LocalLoginAttemptStore.java`；`nop-gateway` 的 `http/GatewayHttpFilter.java`、`core/executor/StreamingProcessor.java`、`core/executor/BufferedStreamingPublisher.java`、`core/streaming/StreamingResponse.java`；`nop-graphql-grpc` 的 `server/GraphQLServerCallHandler.java`。
- **排除**: `target/`、`_` 前缀生成文件、`_dump/`、`.m2-repo-2275/`、`_tmp/`；BCrypt/Base32/TOTPAuthenticator 等 vendored 第三方实现按误报校准排除。
- **机械基线核对**（主 agent grep：裸异常 33 处 / System.out+printStackTrace 22 处，含 test）:
  - main 代码裸异常真实命中：`BizProxyInvocationHandler.java:227`、`CrudBizModel.java:253`、`DevDocBizModel.java:121`、`JwtHelper.java:244/252`（并入 [G5-09-01]）；`StreamingResponse.java:64`、`ForwardProcessor.java:56`、`BufferedStreamingPublisher.java:97/100/153`、`AiBackendMessageConverter.java:44/57`、`TypeRegistry.java:127`、`GraphQLASTVisitor/Processor/Optimizer` 的 `invalid ast kind` 属内部参数/AST 断言守卫（低风险，未单列）；BCrypt/Base32/TOTP/BCryptPasswordEncoder 为 vendored 代码（误报排除）。
  - main 代码 System.out：仅 `GraphQLCommandExecutor.java:54`（见 [G5-09-02]）；BCrypt 命中为 javadoc 示例文本（误报）；printStackTrace main 代码 0 处。
  - `@Inject private`：本模块组 0 处，与全仓基线一致。

### 零发现维度说明

- **维度 12（GraphQL 与 API 层）本轮零 P0-P2 发现**。已核实：`GraphQLSelectionResolver` 有 fragment 链式展开深度限制 + 循环引用检测（DoS 加固，`GraphQLSelectionResolver.java:117-165`）；`ObjMetaBasedFilterValidator` 运算符白名单双向行为正确（命中放行/未命中抛 `ERR_BIZ_PROP_NOT_SUPPORT_FILTER_OP`，无"未命中透传"回退分支），`DEFAULT_ALLOW_FILTER_OP` 位置与 `docs-for-ai/02-core-guides/api-and-graphql.md` 给出的锚点 `ObjMetaBasedFilterValidator.java:38` 一致；`OrmEntityPropConnectionFetcher` 对 `first/last` 以 `maxFetchSize` 钳制后再 +1 探测（`OrmEntityPropConnectionFetcher.java:174-183`）；`/graphql`、`/r/`、`/p/`、`/jsonrpc`、grpc 均汇入同一 `IGraphQLEngine`，`GraphQLExecutor.executeAsync/executeOneAsync` 与 `GraphQLEngine.subscribeGraphQL/subscribeRpc` 均统一先过 `GraphQLActionAuthChecker` + `GraphQLArgumentValidator`；RPC 路径 `checkOperationArgs` 拒绝未知顶层参数。引擎本体未见绕过 selection/权限的手动序列化路径。
- **维度 07（BizModel 规范遵循）引擎侧无结构性违规**：`CrudBizModel implements ICrudBiz<T>`、action 注解（`@BizQuery`/`@BizMutation`/`@BizAction`）分层与文档一致；`GraphQLBizModel` 对同名 action 按优先级覆盖、同优先级抛 `ERR_GRAPHQL_DUPLICATE_ACTION`，与 `service-layer.md` 命名规则一致。唯一公开面问题归入 [G5-07-01]（afterEntityChange 扩展契约漂移）。
- **维度 14 事务边界本体正确**：`GraphQLTransactionOperationInvoker` 仅对 mutation 包事务（query 不包），`BizActionInvoker` 对非 query operation 包 `runInTransaction`，与"`@BizQuery` 场景不可用 `txn().afterCommit`"的文档约束自洽；模块 main 代码无 `afterCommit` 误用。异步问题见 [G5-14-01]/[G5-14-02]。

---

## 发现

### [G5-13-01] DevDoc/DevTool BizModel 全部操作无 @Auth，debug 部署下任意已登录用户可导出 IoC 容器与配置内部结构

- **文件**: `nop-service-framework/nop-biz/src/main/java/io/nop/biz/dev/DevDocBizModel.java:52-63,206-232`；`nop-service-framework/nop-biz/src/main/java/io/nop/biz/dev/DevToolBizModel.java:22-40`；注册见 `nop-service-framework/nop-biz/src/main/resources/_vfs/nop/biz/beans/biz-defaults.beans.xml:124-134`
- **证据片段**:
  ```java
  // DevDocBizModel.java
  @Locale("zh-CN")
  @BizModel("DevDoc")
  public class DevDocBizModel {
      @BizQuery
      @Description("graphql模型定义")
      public String graphql() { ... }

      @Description("Ioc容器中的bean定义")
      @BizQuery
      public WebContentBean beans() {
          IBeanContainerImplementor container = (IBeanContainerImplementor) BeanContainer.instance();
          return WebContentBean.xml(container.toConfigNode().xml());
      }
      @Description("所有配置变量的当前值")
      @BizQuery
      public List<ConfigVarBean> configVars() { ... }
  ```
  ```xml
  <!-- biz-defaults.beans.xml:124-134 -->
  <bean id="nopDevDocBizModel" class="io.nop.biz.dev.DevDocBizModel">
      <ioc:condition><if-property name="nop.debug"/></ioc:condition>
  </bean>
  <bean id="io.nop.biz.dev.DevToolBizModel" ioc:type="@bean:id">
      <ioc:condition><if-property name="nop.debug"/></ioc:condition>
  </bean>
  ```
- **严重程度**: P2
- **现状**: `DevDoc__beans`（导出整个 IoC 容器 bean 定义 XML）、`DevDoc__configVars`（全部配置项当前值，secret 名有掩码）、`DevDoc__graphql`（全量 schema 源码）、`DevDoc__globalFunctions/globalVars/dependsSet`，以及 `DevTool__clearComponentCache`/`DevTool__refreshVirtualFileSystem`（破坏性 @BizMutation）全部未标注 `@Auth`。平台语义下 auth==null 即公开访问（`GraphQLActionAuthChecker.isAllowAccess` 对 `auth == null` 返回 true），这些 operation 对任何能到达 GraphQL 端点的调用者开放。同包 `DevStatBizModel` 已被整改为每个方法 `@Auth(roles = "admin")` 且类注释明确"auth==null时平台按公开访问处理"，说明该风险模型在仓库内已被认知，但 DevDoc/DevTool 未同步整改。
- **风险**: `nop.debug=true` 的测试/预发环境（往往是面网范围更广的环境）中，普通用户即可拉取完整 IoC 结构、全部配置键值与 schema，扩大攻击面侦察；`DevTool__clearComponentCache` 可被任意用户触发全量缓存清空（性能 DoS）。`beans()` 输出未做任何脱敏（仅 `configVars()` 有 `maskSecretVar`）。
- **建议**: 为 DevDoc/DevTool 所有方法补 `@Auth(roles = "admin")`（对齐 DevStatBizModel 的整改模式）；`beans()` 输出增加敏感 property 脱敏。
- **信心水平**: 确定（bean 注册条件与 auth 语义均已核实）。
- **误报排除**: 不是"开发期工具本就该公开"——同包 DevStatBizModel 的显式 @Auth 整改与注释证明平台语义是 auth==null 即公开，且 nop.debug 门控只是装配条件而非访问控制。
- **复核状态**: 未复核

### [G5-13-02] Sys__cancel 公开查询无归属校验，CancelTokenManager 可跨用户取消/替换在途请求

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/web/SysBizModel.java:20-33`；`nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/engine/CancelTokenManager.java:23-49`；注册见 `biz-defaults.beans.xml:100`
- **证据片段**:
  ```java
  @BizModel("Sys")
  public class SysBizModel {
      @BizQuery
      public boolean cancel(@RequestBean CancelRequestBean cancelBean, IServiceContext ctx) {
          if (cancelBean == null) return false;
          String id = cancelBean.getReqId();
          if (!StringHelper.isEmpty(id))
              return graphQLEngine.cancel(id);
          return false;
      }
  }
  ```
  ```java
  // CancelTokenManager.java
  public void register(String reqId, ICancellable cancelToken) {
      ICancellable oldToken = cancelTokens.put(reqId, cancelToken);
      if (oldToken != null && oldToken != cancelToken)
          oldToken.cancel("replace");   // 撞 id 时直接取消先到请求
  }
  public boolean cancel(String reqId) {
      ICancellable cancelToken = cancelTokens.remove(reqId);   // 无归属/租户校验
      ...
  }
  ```
- **严重程度**: P2
- **现状**: `SysBizModel` 无条件注册（beans.xml 第 100 行，无 ioc:condition），`cancel` 是无 `@Auth` 的 `@BizQuery`。reqId 取自请求头（`CancelTokenManager.wrap` 经 `ApiHeaders.getIdFromHeaders`），`cancel(reqId)` 全局 Map remove，没有任何"该 reqId 是否属于当前用户/会话"的校验；`register` 在两个在途请求撞 id 时还会主动取消先到请求。
- **风险**: 客户端自选、非强随机 reqId（或 reqId 泄漏/可枚举）场景下，任一调用者可取消其他用户的长耗时查询（跨租户干扰/DoS 面）；并发请求复用同一 id 会互相取消。
- **建议**: cancel 前校验 token 归属（注册时记录 userId/sessionId，cancel 时比对）；或至少要求 `@Auth` 并将 reqId 与当前会话绑定。
- **信心水平**: 很可能（归属缺失确定；实际可利用性取决于前端 reqId 生成方式，未在本次范围内取证）。
- **误报排除**: 不是"客户端取消自己的请求本就合法"——缺失的是归属校验这一访问控制维度，而非功能本身。
- **复核状态**: 未复核

### [G5-14-01] 网关流式链路非 demand-driven，BufferedStreamingPublisher 在 demand==0 时无界缓冲，慢消费者可致内存膨胀

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/executor/StreamingProcessor.java:137-167`；`nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/executor/BufferedStreamingPublisher.java:246-267`；`nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/http/GatewayHttpFilter.java:181-215`
- **证据片段**:
  ```java
  // StreamingProcessor.createMappedPublisher —— 无视下游 demand 自驱动拉取
  public void onNext(IServerEventResponse item) {
      try {
          Object element = item.getData();
          ...
          if (element != null) {
              subscriber.onNext(element);   // 不检查下游 demand 即转发
          }
          subscription.request(1);          // 无条件继续拉上游
      } catch (Exception e) { subscriber.onError(e); }
  }
  ```
  ```java
  // BufferedStreamingPublisher.handleItem —— 窗口越过后 demand==0 时无界入队
  if (demand > 0) {
      demand--;
      state.forwarded = true;
      subscriber.onNext(item);
  } else {
      state.buffer.add(item);   // ArrayDeque，无容量上限
  }
  ```
  ```java
  // GatewayHttpFilter.writeStreamingResponse 适配器 —— 转发成功路径不再 request
  public void onNext(Object item) {
      if (item != null) {
          String data = serializeStreamElement(item, ct);
          if (data != null) {
              subscriber.onNext(data);
              return;              // 正常路径缺少 subscription.request(1)
          }
      }
      subscription.request(1);
  }
  ```
- **严重程度**: P2
- **现状**: SSE 上游 → 映射链（自驱动 `request(1)`）→ BufferedStreamingPublisher（demand 由下游 `request(n)` 补充）→ HTTP 写出适配器（转发后不 request）→ `IHttpServerContext.sendStreamingResponse`。整条链的上游拉取速率与客户端消费速率完全解耦：缓冲层注释自认"映射链自驱动……缓冲层不追加请求"。当缓冲层 engage（AI 网关注入 lifecycle 监听器或 `bufferEnabled=true` 即 engage）且下游消费慢于上游产出时，越窗后元素持续进入无上限的 `ArrayDeque`；非缓冲路径则积压转移到 HTTP 写出层。
- **风险**: LLM 长输出 + 慢客户端（移动网络）场景下网关堆内存无界增长，多路并发时可 OOM；`GatewayHttpFilter` 适配器在转发成功路径依赖下游直接向源 subscription request 才不中断/不积压，Flow 反压契约在两层被违反，未来任何一方改成合规 publisher 都会导致断流。
- **建议**: `createMappedPublisher` 的 `request(1)` 改为按下游 demand 驱动（或在 BufferedSubscription 内以 demand 门控向映射链发 request）；`state.buffer` 增加容量上限并触顶时向下游施加反压/报错。
- **信心水平**: 很可能（机制由代码确定；最终积压落点取决于 `sendStreamingResponse` 底层实现是否预先 request Long.MAX_VALUE，该实现在 nop-http 层、本次范围外）。
- **误报排除**: 不是"Flow 实现普遍如此"——`BufferedStreamingPublisher` 自身已实现 demand 计数与 pendingComplete 语义，说明意图就是 demand-driven，缺口在映射链自驱动与无上限 buffer 的组合。
- **复核状态**: 未复核

### [G5-09-01] 框架公共路径残留 4 处"错误码字符串内嵌裸 IllegalArgumentException"，违反两档错误处理策略

- **文件**: `nop-service-framework/nop-biz/src/main/java/io/nop/biz/proxy/BizProxyInvocationHandler.java:227`；`nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/CrudBizModel.java:251-255`；`nop-service-framework/nop-biz/src/main/java/io/nop/biz/dev/DevDocBizModel.java:119-121`；`nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/jwt/JwtHelper.java:239-253`
- **证据片段**:
  ```java
  // BizProxyInvocationHandler.java:227 —— I*Biz 跨模块代理调用路径
  throw new IllegalArgumentException("unsupported-method:" + method);
  ```
  ```java
  // CrudBizModel.java:251-255 —— CrudBizModel 引擎初始化路径
  public void setEntityName(String entityName) {
      if (this.entityName != null && !this.entityName.equals(entityName))
          throw new IllegalArgumentException("nop.err.biz.entity-name-not-allow-change:oldEntityName=" + this.entityName + ",newEntityName=" + entityName);
      this.entityName = entityName;
  }
  ```
  ```java
  // DevDocBizModel.java:121 —— @BizLoader 可经 GraphQL 触达
  throw new IllegalArgumentException("nop.err.graphql.invalid-global-var::" + varDef.getName());
  ```
  ```java
  // JwtHelper.java:244/252 —— 认证验签路径
  throw new IllegalArgumentException("nop.err.unsupported-key:" + key);
  ```
- **严重程度**: P2
- **现状**: 四处都把形如 `nop.err.xxx` 的错误码拼进 IAE 消息而非使用 `NopException + ErrorCode + .param()`。按 `docs-for-ai/02-core-guides/error-handling.md` 两档策略，框架核心/跨模块公共 API 必须 ErrorCode 模式；`BizErrors`/`GraphQLErrors`/`AuthCoreErrors` 中均无对应 ErrorCode 定义（`nop.err.biz.entity-name-not-allow-change` 无 define）。同文件相邻代码已用 NopException（如 `BizProxyInvocationHandler:136` 的 `ERR_GRAPHQL_METHOD_PARAM_NO_REFLECTION_NAME_ANNOTATION`），风格割裂。`docs-for-ai/02-core-guides/service-layer.md` 还把 `unsupported-method` 当作既有错误语义引用。
- **风险**: 这些异常到达 GraphQL/HTTP 边界后被归为 internal error（无 errorCode、无 i18n、无结构化 param），调用方无法程序化区分；错误码字符串不进 i18n 体系，属"假装有错误码"的契约漂移，也会误导后续开发模仿该写法。
- **建议**: 补 `ErrorCode.define(...)` 并改抛 `NopException(...).param(...)`（JwtHelper 两处可合并为一个 `ERR_JWT_UNSUPPORTED_KEY`）；全模块组 grep `throw new (Runtime|IllegalArgument|IllegalState)Exception\("nop\.err` 防回归。
- **信心水平**: 确定。
- **误报排除**: 不是 vendored 代码（BCrypt/Base32 等已排除）；不是内部断言守卫（消息明确冒充错误码，且位于跨模块代理/认证公共路径）。
- **复核状态**: 未复核

### [G5-07-01] afterEntityChange 扩展契约三方漂移：2-arg 已 @Deprecated、文档仍强制覆写 2-arg、行内迁移注释指向不存在的方法签名

- **文件**: `nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/CrudBizModel.java:838-846`（对照 `:1264`、`:2105/2133/2165`）；文档 `docs-for-ai/02-core-guides/service-layer.md:225-231`
- **证据片段**:
  ```java
  // CrudBizModel.java:838-846
  protected void afterEntityChange(@Name("entity") T entity, @Name("action") String action, IServiceContext context) {
      afterEntityChange(entity, context);
  }

  // 使用afterEntityChage(entity, context, action)方法来代替
  @Deprecated
  protected void afterEntityChange(@Name("entity") T entity, IServiceContext context) {

  }
  ```
  ```java
  // :1264 —— delete 路径实际调用 3-arg（与文档"delete 路径直接调用 2-arg"相反）
  afterEntityChange(entity, BizConstants.METHOD_DELETE, context);
  ```
- **严重程度**: P2
- **现状**: live 代码中 save/update/delete/ICrudBiz 实体操作四条路径全部先调 3-arg，3-arg 委托 2-arg；因此无论覆写哪个重载当前都能覆盖全路径。但三方互相矛盾：(1) `service-layer.md`「afterEntityChange 重载陷阱」断言"delete 路径直接调用 2-arg、必须覆写 2-arg"，与 live 代码（delete 调 3-arg）不符；(2) 2-arg 已标 `@Deprecated`，文档却在引导用户覆写它；(3) 迁移注释推荐的方法名 `afterEntityChage(entity, context, action)` 拼写错误且参数顺序与真实 3-arg `(entity, action, context)` 相反，该方法不存在。
- **风险**: BizModel 作者按文档覆写 @Deprecated 方法会持续产生弃用告警并阻碍未来删除 2-arg 的清理；按行内注释"迁移"则找不到目标方法；文档描述的失效模式（"只覆写 3-arg → delete 不触发"）在当前代码中不成立，排查派生汇总丢失类 bug 时会误导方向。
- **建议**: 三方收敛——要么撤销 2-arg 的 @Deprecated 并修正文档为"当前 delete 也走 3-arg"；要么完成迁移（3-arg 不再委托 2-arg，删除 2-arg）并同步文档与注释拼写。
- **信心水平**: 确定（行为由代码路径直接可证）。
- **误报排除**: 不是"文档维度越界"——被审计对象是 CrudBizModel 引擎的公开扩展钩子契约本身，@Deprecated 与实际委托关系属于引擎公开面。
- **复核状态**: 未复核

### [G5-13-03] ObjMetaBasedValidator.doCheckAuth 未判空 userContext，匿名写路径触发 NPE 而非语义化鉴权错误

- **文件**: `nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/ObjMetaBasedValidator.java:327-357`
- **证据片段**:
  ```java
  protected void doCheckAuth(String objTypeName, IObjPropMeta propMeta, ActionAuthMeta auth) {
      IActionAuthChecker authChecker = this.context.getActionAuthChecker();
      if (authChecker == null || auth == null)
          return;
      if (auth.isPublicAccess())
          return;
      if (auth.getRoles() != null && !auth.getRoles().isEmpty()) {
          if (this.context.getUserContext().isUserInAnyRole(auth.getRoles()))   // getUserContext() 可能为 null
              return;
      }
      ...
  ```
- **严重程度**: P3
- **现状**: 对照 `GraphQLActionAuthChecker.checkAuth`（对 `userContext == null` 显式抛 `ERR_AUTH_NO_USER_CONTEXT`），字段级 writeAuth 检查在 `roles` 非空且 `context.getUserContext()` 为 null（启用了 action-auth 但匿名调用，如登录前表单提交）时直接 NPE。失败方向是 fail-closed（请求仍失败），但错误语义退化为 internal error。
- **风险**: 匿名场景下 500 内部错误掩盖真实原因（无权限），排障与前端提示体验受损。
- **建议**: 对齐 GraphQLActionAuthChecker：`userContext == null` 时抛 `ERR_AUTH_NO_USER_CONTEXT`（带 fieldName/objTypeName param）。
- **信心水平**: 很可能（NPE 路径确定；触发条件依赖匿名 + 字段配置 writeAuth 角色的组合出现频率）。
- **误报排除**: 不是误报——同仓 `GraphQLActionAuthChecker.java:95-99` 对同一语义已有正确处理，两处行为不一致即为证据。
- **复核状态**: 未复核

### [G5-13-04] JwtHelper 将完整凭证 token 写入异常 param，与"凭证不得落日志"的既有整改方向不一致

- **文件**: `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/jwt/JwtHelper.java:87-97,128-160`；对照 `AuthHttpServerFilter.java:404-421`
- **证据片段**:
  ```java
  // JwtHelper.parseToken —— token 原文作为异常参数
  if (!isVerified)
      throw new NopException(ERR_JWT_INVALID_TOKEN).param(ARG_TOKEN, token);
  ...
  } catch (Exception e) {
      throw new NopException(ERR_JWT_INVALID_TOKEN, e).param(ARG_TOKEN, token);
  }
  ```
  ```java
  // AuthHttpServerFilter.parseAuthToken —— 同一 token 的既有脱敏决策
  // 凭证原文不得落日志（过期 token 仍是凭证，且完整 token 模式便于离线爆破弱 enc-key）：
  // 只记录长度辅助诊断（如客户端截断），异常详情保留
  LOG.debug("nop.invalid-auth-token:tokenLength={}", token.length(), e);
  ```
- **严重程度**: P3
- **现状**: `NopException.getMessage()` 会拼接 params（见 error-handling.md 的消息格式），因此任何对该异常做 `LOG.error(..., e)` 的下游都会把 token 原文打进日志。HTTP 过滤器路径自身已捕获并只记长度，但 RPC/内部调用方（`BizActionInvoker.invokeGraphQLAsync`、grpc `onError` 等）直接进入 `buildRpcResponse`/`buildGraphQLResponse` 的 `NopException.logIfNotTraced(LOG, ...)` 错误级日志。
- **风险**: 无效/过期凭证在非 HTTP 入口或异步失败路径被完整记录，违背仓库已确认的凭证日志策略，便于日志读取者离线爆破。
- **建议**: 移除 `ARG_TOKEN` 传参或改为传 token 长度/指纹（如 SHA-256 前 8 位），与 AuthHttpServerFilter 的口径统一。
- **信心水平**: 很可能。
- **误报排除**: 不是"异常 param 本就该带上下文"——error-handling.md 明确敏感值需掩码后再入 param，且同模块已有相反决策的先例注释。
- **复核状态**: 未复核

### [G5-13-05] introspection 启用后全量 schema 导出无字段级权限过滤（默认关闭，属启用后语义）

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/schema/BuiltinSchemaLoader.java:115-137,177-262`；开关默认值 `GraphQLConfigs.java:52-53`
- **证据片段**:
  ```java
  // GraphQLConfigs.java:52-53 —— 默认关闭（fail-closed，正确）
  IConfigReference<Boolean> CFG_GRAPHQL_SCHEMA_INTROSPECTION_ENABLED = varRef(s_loc,
          "nop.graphql.schema-introspection.enabled", Boolean.class, false);
  ```
  ```java
  // BuiltinSchemaLoader —— __schema/__type 直接全量导出，无 per-field auth 过滤
  void initQueryFetcher(GraphQLObjectDefinition query) {
      setFetcher(query, "__schema", env -> new __Schema());
      setFetcher(query, "__type", this::fetchType);
  }
  List<__Type> fetchTypes(IDataFetchingEnvironment env) {
      ...
      for (GraphQLTypeDefinition objDef : types.values()) {
          if (objDef.getName().startsWith("__")) continue;
          ret.add(toType(objDef));       // 所有业务类型/字段，含受限字段的存在性
      }
      return ret;
  }
  ```
- **严重程度**: P3
- **现状**: introspection 默认关闭且未启用时对 `__` 前缀 operation 抛 `ERR_GRAPHQL_INTROSPECTION_NOT_ENABLED`（`GraphQLEngine.java:361-367`），门是 fail-closed 的。但一经启用（运维为 GraphQL 工具/调试打开），`__schema` 对任何能通过登录门槛的用户导出全部类型与字段元数据，不按 `GraphQLFieldDefinition.getAuth()` 过滤受限字段的存在性（运行期 selection 有字段级鉴权，schema 导出没有）。
- **风险**: 启用后受限字段、内部对象结构（含 `not-pub` 之外的所有对象）对全体登录用户可见，辅助攻击面侦察。
- **建议**: 在 `fetchFields`/`fetchTypes` 按 `fieldDef.getAuth()` + 当前 `IActionAuthChecker` 过滤，或在文档中明示"启用 introspection = 向全体登录用户公开 schema"的运维语义。
- **信心水平**: 确定（机制确定；严重度取决于部署是否启用）。
- **误报排除**: 不是"GraphQL 惯例如此"——平台已有字段级鉴权体系，introspection 路径绕过它是引擎内不一致，且默认关闭说明平台认可暴露风险。
- **复核状态**: 未复核

### [G5-14-02] GraphQLSubscriptionManager.registerSubscription 存在 check-then-act 竞态，订阅上限与去重可被并发绕过

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/subscription/GraphQLSubscriptionManager.java:84-104`
- **证据片段**:
  ```java
  public void registerSubscription(SubscriptionInfo subscription) {
      String operationId = subscription.getOperationId();

      if (subscriptionsById.size() >= maxActiveSubscriptions) {        // 检查
          throw new NopException(ERR_GRAPHQL_SUBSCRIPTION_TOO_MANY)
                  .param(ARG_OPERATION_ID, operationId);
      }
      if (subscriptionsById.containsKey(operationId)) {                // 检查
          throw new NopException(ERR_GRAPHQL_SUBSCRIPTION_DUPLICATE_OPERATION)
                  .param(ARG_OPERATION_ID, operationId);
      }
      subscriptionsById.put(operationId, subscription);                // 动作（非原子）
      ...
  ```
- **严重程度**: P3
- **现状**: 上限判断与 `containsKey`/`put` 之间无同步，两个并发注册同一 operationId 时都会通过检查，后者静默覆盖前者（前一个 WebSocket 订阅失去 operationId 索引、不再收到推送且 unregister 时清理不完整）；并发风暴下 `maxActiveSubscriptions` 上限可被短暂突破。
- **风险**: 订阅泄漏（被覆盖的 SubscriptionInfo 仍留在 `subscriptionsByOperation`，只能靠会话断连清理）与上限失真；多实例/多 ws 场景难排查。
- **建议**: 用 `subscriptionsById.putIfAbsent(operationId, subscription)` 原子化，size 上限改为 `ConcurrentHashMap` 计数或在锁内完成检查-写入。
- **信心水平**: 确定（竞态窗口由代码结构直接可见）。
- **误报排除**: 不是低价值风格问题——覆盖后旧订阅失联是真实行为缺陷，且该类其余并发结构（ConcurrentHashMap + CopyOnWriteArrayList）表明作者意图就是线程安全。
- **复核状态**: 未复核

### [G5-09-02] GraphQLCommandExecutor 用 System.out.println 输出响应（CLI 上下文边缘案例）

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/command/GraphQLCommandExecutor.java:40-56`
- **证据片段**:
  ```java
  @Override
  public int execute(String command, Map<String, Object> params) {
      ...
      ApiRequest<Object> request = buildRequest(params);
      IGraphQLExecutionContext graphqlContext = graphQLEngine.newRpcContext(null, command, request);
      ApiResponse<?> response = graphQLEngine.executeRpc(graphqlContext);
      System.out.println(JSON.stringify(response));
      return response.getStatus();
  }
  ```
- **严重程度**: P3
- **现状**: `ICommandExecutor` 是命令行入口，向 stdout 打印执行结果属于 CLI 语义而非日志语义；但直接用裸 `System.out` 而非命令输出抽象（或至少 `LOG.info` 分离）使其无法被重定向/静默策略统一管理。类中已有 `LOG.info` 用于"not-allowed-command"，输出通道混用。
- **风险**: 低；主要是可维护性与规范一致性（基线 grep 的 22 处 System.out 中 main 代码唯一真实命中）。
- **建议**: 若框架有 console writer 约定则改用之；否则保留但在注释中显式声明"CLI 输出，非日志"。
- **信心水平**: 确定。
- **误报排除**: 已按上下文降级——不是服务路径日志滥用（不在请求处理链上），故 P3 而非 P2。
- **复核状态**: 未复核

### [G5-13-06] （补充核实项，不计新缺陷）AuthHttpServerFilter/AuthFilterConfig 安全加固现状确认

- **文件**: `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/filter/AuthHttpServerFilter.java:90-101,216-258,285-294`；`filter/AuthFilterConfig.java:205-233`
- **证据片段**:
  ```java
  // AuthFilterConfig.isAllowedRedirectUri —— 白名单边界校验（维度13标准检查项）
  for (String prefix : this.allowedRedirectPrefixes) {
      if (uri.startsWith(prefix) && endsAtUriBoundary(uri, prefix))
          return true;
  }
  return false;   // 未命中即拒绝，无透传回退分支
  ```
  ```java
  // handleError —— 非登录异常统一 500 "Server Error"，无内部细节泄漏
  } else {
      routeContext.sendResponse(500, "Server Error");
  }
  ```
- **严重程度**: 不适用（零发现核实记录）
- **现状**: 按维度 13 标准检查项逐项核实：重定向 URI 白名单带 URI 结构边界判定（防 host 后缀拼接/userinfo/端口伪装，`endsAtUriBoundary`），未命中原样拒绝，无 SSRF 白名单旁路；`isRelativePath` 拒绝协议相对/反斜杠/控制字符；cookie 启用 Secure 时自动 `__Host-` 前缀；servicePublic 路径不信任客户端租户头（需显式开 `trust-forwarded-tenant`）；错误响应不泄漏堆栈与内部消息；错误消息经 `ErrorMessageManager.buildErrorMessage(locale, err, includeStack=false, onlyPublic=true)` 出站。
- **风险**: 无（本条为正向核实，供复核 agent 免重复取证）。
- **建议**: 无。
- **信心水平**: 确定。
- **误报排除**: 不适用。
- **复核状态**: 未复核

---

## 发现统计

| 维度 | P0 | P1 | P2 | P3 | 条目 |
|------|----|----|----|----|------|
| 07 BizModel 规范 | 0 | 0 | 1 | 0 | G5-07-01 |
| 09 错误处理 | 0 | 0 | 1 | 1 | G5-09-01, G5-09-02 |
| 12 GraphQL 与 API 层 | 0 | 0 | 0 | 0 | 零发现（见审计范围说明） |
| 13 安全与权限 | 0 | 0 | 2 | 3 | G5-13-01..05（另附 G5-13-06 正向核实记录，不计严重度） |
| 14 异步与事务 | 0 | 0 | 1 | 1 | G5-14-01, G5-14-02 |
| **合计** | **0** | **0** | **5** | **5** | 10 条发现 + 1 条正向核实记录 |

## 审计盲区自评

- 未运行构建/测试（纯静态审计）；`sendStreamingResponse` 底层（nop-http 层）与 nop-ai-gateway 的 lifecycle 注入行为在范围外，G5-14-01 的最终积压落点与 engage 频率需复核时补证。
- `nop-biz-file-core`（19 文件）、`nop-graphql-message`、gateway 的 `conversion/ai` 消息转换器仅做抽样扫描（裸异常/日志/权限 grep），未逐行深读。
- GraphQL 订阅推送（`GraphQLSubscriptionManager.pushToClient`）推送内容的数据权限时效性（订阅期间用户权限变化）未深入取证。
- gRPC 入口（`GraphQLServerCallHandler`）的用户上下文装配链（是否有等价 AuthHttpServerFilter）未完全追溯，仅确认异常出口经 `buildResponseForException`。

## 子项复核结论

复核人：独立复核代理 R3（2026-09-30）

| 发现编号 | 判定 | 复核说明 |
|---|---|---|
| [G5-13-01] | 保留（维持 P2）| 逐项核实：`DevDocBizModel.java` 全文无 @Auth（import 列表亦无），`beans()`（206-211 行）导出整个 IoC 容器 `toConfigNode().xml()` 无脱敏，`configVars()`（215-232 行）有 `maskSecretVar`；`DevToolBizModel.java:27-40` 两个破坏性 @BizMutation（清缓存/刷 VFS）同样无 @Auth。注册条件核实 `biz-defaults.beans.xml:124-134`：两 bean 均以 `<if-property name="nop.debug"/>` 门控——是装配条件而非访问控制，与报告表述一致。对照基线核实 `DevStatBizModel.java:20-57`：类注释明写"auth==null时平台按公开访问处理"且全部方法 `@Auth(roles="admin")`；`GraphQLActionAuthChecker.isAllowAccess`（`GraphQLActionAuthChecker.java:118-120`）对 `auth == null` 返回 true。debug 环境任意已登录用户可拉取 IoC/配置/schema 并触发缓存清空，同包先例已认定该风险模型，维持 P2。 |
| [G5-13-02] | 保留（维持 P2）| 逐项核实：`SysBizModel.java:25-33` 的 `cancel` 为无 @Auth 的 @BizQuery，reqId 直通 `graphQLEngine.cancel(id)`；注册点 `biz-defaults.beans.xml:100` 无 ioc:condition（无条件装配）；`CancelTokenManager.java:29-42` 的 `register`/`cancel` 为全局 `ConcurrentHashMap` 操作，无任何归属/会话/租户校验，`register` 撞 id 时 `oldToken.cancel("replace")` 主动取消先到请求；reqId 来源核实 `CancelTokenManager.wrap`（46 行）取自 `ApiHeaders.getIdFromHeaders(ctx.getRequestHeaders())` 客户端头。跨用户取消/干扰面成立。原报告已诚实标注可利用性依赖 reqId 生成方式（信心水平"很可能"），判级恰当，维持 P2。 |
| [G5-14-01] | 保留（维持 P2）| 三处代码现场逐一核实：(1) `StreamingProcessor.createMappedPublisher`（`StreamingProcessor.java:137-167`）`onNext` 转发后无条件 `subscription.request(1)`（162 行）、`onSubscribe` 即 `request(1)`（144 行），完全无视下游 demand；(2) `BufferedStreamingPublisher.handleItem`（`BufferedStreamingPublisher.java:246-267`）越窗（`state.passed`）后 `demand == 0` 分支 `state.buffer.add(item)` 无容量检查（窗口内缓冲有 `bufferSize` 上限、越窗后没有）；(3) `GatewayHttpFilter.writeStreamingResponse` 适配器（`GatewayHttpFilter.java:194-203`）转发成功路径 `return` 不再 request，仅 data==null 时补 request。engage 条件核实 `StreamingProcessor.java:85-90`（retryCallback/lifecycle/bufferEnabled 任一即 engage）。原报告对"最终积压落点取决于 sendStreamingResponse 底层实现"的盲区自评诚实，机制层证据完整。LLM 长输出 + 慢客户端 + 多路并发场景的 OOM 风险成立，维持 P2。 |
| [G5-09-01] | 保留（维持 P2）| 四处现场逐一核对原文：`BizProxyInvocationHandler.java:227`、`CrudBizModel.java:251-253`（`setEntityName` 内嵌 `nop.err.biz.entity-name-not-allow-change`）、`DevDocBizModel.java:121`、`JwtHelper.java:244/252`（两处 `nop.err.unsupported-key`）与报告引文一致。错误码无定义核实：nop-service-framework 全模块 grep 这四个错误码字符串，唯一命中即四处 throw 点本身，`BizErrors`/`GraphQLErrors`/`AuthCoreErrors` 均无对应 `ErrorCode.define`。按 error-handling.md 两档策略，框架公共路径（CRUD 引擎初始化、JWT 验签、I*Biz 代理、@BizLoader）必须 ErrorCode 模式，"假装有错误码"的定性成立；异常到边界后无 errorCode/i18n/结构化 param 属真实契约缺陷，维持 P2。 |
| [G5-07-01] | 保留（维持 P2）| 三方漂移全部复核成立：live code 侧 `CrudBizModel.java:838-840` 3-arg 委托 2-arg；全部 6 个调用点（835 save、1019 update、1264 delete、2105/2133/2165）均调 3-arg——delete 路径（1264 行 `afterEntityChange(entity, BizConstants.METHOD_DELETE, context)`）与文档断言相反；842-846 行迁移注释 `使用afterEntityChage(entity, context, action)方法来代替` 拼写错误且参数顺序与真实 3-arg `(entity, action, context)` 不符、该方法不存在，2-arg 同时标 `@Deprecated`。文档侧 `docs-for-ai/02-core-guides/service-layer.md`「afterEntityChange 重载陷阱」节（约 225-231 行）仍断言"delete 路径直接调用 2-arg"并引导覆写 @Deprecated 的 2-arg。文档、代码、行内注释三方互相矛盾属实，按文档开发会持续产生弃用告警、按注释迁移会找不到目标，维持 P2。 |
