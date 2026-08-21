# demo-migration 实现代码检查报告

- 检查日期: 2026-08-21
- 模块路径: nop-demo + nop-migration
- 文件数: 实测 `src/main/java` 共 102 个 Java 文件（nop-demo 99 + nop-migration 3），其中 7 个位于 `_gen/` 生成目录、18 个带 `//__XGEN_FORCE_OVERRIDE__` 生成标记（demo-ddd-api 全部 beans/crud 类），按约定排除后人工需覆盖约 77 个手写文件。任务描述的"约 163 文件"与实测不符（可能按含测试/资源的口径统计），以实测为准。
- 覆盖范围声明:
  - nop-migration 3 个 main Java 文件全部逐行阅读。
  - nop-demo 全部手写 main Java 文件已读（BizModel / Main / 安全类 / 拦截器 / 领域类逐个 Read，简单 bean/常量类批量 cat 核对）。
  - grep 全量扫描（两模块 main 源码，排除 `_` 前缀）: 空 catch、`new RuntimeException`、`printStackTrace`、password/secret/token、synchronized/Thread/Executor、private `@Inject`、`System.out.println`。命中点均逐一 Read 验证。
  - D7 专项: 核对各模块 `_vfs/**/beans/*.beans.xml` 与全部 `@BizModel`/`@Inject` 类的注册与字段可见性（quarkus-demo、rpc-client-demo、spring-security-demo、spring-simple-demo、spring-demo-no-orm、gateway-demo-service、delta-demo）。
  - D5 补充（超出 src/main/java 字面范围，如实声明）: 对 demo 各模块 `src/main/resources/application.yaml` 做了凭证/密钥 grep，发现条目已列入报告并标注超出范围。
  - 平台机制取证（用于验证而非审计对象）: `IUserContext.get()`（nop-api-core）、`GraphQLActionAuthChecker.checkAuth/isAllowAccess`（nop-graphql-core）、`HttpRpcService`（nop-rpc-http）、`StringHelper.replace`（nop-commons）、`NopAuthUser.xmeta` 的 password `published="false"`（nop-auth-meta）。
  - 测试代码（nop-ofbiz-migration src/test）不在范围内。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 2 |
| P2 | 4 |
| P3 | 4 |

## 发现列表

### [P1] quarkus-demo `testGraphql` 匿名调用必现 NPE：`IUserContext.get()` 无空值防护

- **文件**: `nop-demo/nop-quarkus-demo/src/main/java/io/nop/demo/biz/DemoBizModel.java:105-112`
- **维度**: D1（另涉 D8：该函数未声明任何 @Auth，公共暴露与"取当前用户 token"的实现假设不匹配）
- **证据**:
```java
@BizQuery
public Map<String, Object> testGraphql() {
    ApiRequest<GraphQLRequestBean> request = ApiRequest.build(new GraphQLRequestBean());
    request.setBearerToken(IUserContext.get().getAccessToken());

    request.getData().setQuery("query{ NopAuthUser__findList{id,userName}}");
    GraphQLResponseBean response = graphQLApi.api_invoke(request, null);
```
- **现状**: `IUserContext.get()` 实现为 `ContextProvider.getContextAttr("userContext")`（nop-api-core `IUserContext.java:21-23`），未登录时返回 null，`.getAccessToken()` 直接 NPE。该 biz 函数未加 `@Auth`，而平台 `GraphQLActionAuthChecker.isAllowAccess` 对 `auth == null` 直接放行（nop-graphql-core `GraphQLActionAuthChecker.java:118-120`），即匿名请求可直达此函数。
- **风险**: 未登录调用 `/r/Demo__testGraphql` 得到 NopException 包装的 NPE 500 而非有意义的 `ERR_AUTH_NO_USER_CONTEXT`（若声明 @Auth 平台会给出该错误）。这是使用范例中被用户复制的坏模式：从上下文取用户信息前不判空。
- **建议**: 函数加 `@Auth` 声明（或入口处 `IUserContext.require()`/判空后抛带错误码的 NopException）；或改用 `IUserContext.getOrNull()` + 显式空值分支。
- **误报排除**: 已核实该方法所在 BizModel 与类上均无 `@Auth`；quarkus-demo 的 `app.action-auth.xml` 也未对 Demo 操作做限制；已排除"平台默认拦截匿名"的可能（isAllowAccess 对 null auth 返回 true，且 checker 为 null 时整体跳过检查）。

### [P1] nop-ofbiz-migration `addCatalog` 序号列 off-by-one：目录首行显示 2

- **文件**: `nop-migration/nop-ofbiz-migration/src/main/java/io/nop/ofbiz/migration/transform/EntityDefDirTransformer.java:123-131`
- **维度**: D1
- **证据**:
```java
int index = 1;
String styleId0 = table.getCell(1, 0).getStyleId();
...
for (IEntityModel entityModel : tables) {
    ExcelRow row = table.makeRow(index++);
    row.makeCell(0).setValue(index);
```
- **现状**: `makeRow(index++)` 先用 1 建行再自增，随后 `setValue(index)` 写入的是"下一行"的行号。第 1 个实体行写入 2，第 n 个写入 n+1。每次运行迁移工具都会在生成的 `.orm.xlsx` "目录" sheet 序号列产生整体偏移的错误数据。
- **风险**: 迁移产物（作为代码生成源模型）自带错误序号列；属于确定性输出数据错误，但仅影响目录导航列，不影响实体/表结构本身，故定 P1 而非 P0。
- **建议**: `row.makeCell(0).setValue(index - 1);` 或先 `ExcelRow row = table.makeRow(index); ... setValue(index); index++;`。
- **误报排除**: 已逐行核对自增顺序（后缀 `++` 与读序），非 makeRow 语义误读——无论 makeRow(1) 是否为首个数据行，写入值 2 与行位置 1 必然错位。

### [P2] nop-ofbiz-migration `loadFromDir` 对 `dir.listFiles()` 无空值防护

- **文件**: `nop-migration/nop-ofbiz-migration/src/main/java/io/nop/ofbiz/migration/transform/EntityDefDirTransformer.java:48-61`
- **维度**: D1/D4
- **证据**:
```java
public void loadFromDir(File dir) {
    // 先加载所有的entitydef模型，记录未识别的relation实体引用
    for (File file : dir.listFiles()) {
```
- **现状**: `dir` 不存在或不是目录时 `listFiles()` 返回 null，for-each 直接 NPE，调用者拿到的是无上下文的 `NullPointerException` 而非"目录不存在"的明确错误。
- **风险**: 迁移工具入口（`transformDir` 先调 `loadFromDir`）传错路径时排障困难；工具类典型坏模式。
- **建议**: 入口校验 `dir.isDirectory()`，不存在时抛带路径参数的异常；或对 `File[] files = dir.listFiles()` 判 null 后抛明确错误。
- **误报排除**: 该类无其他调用方做前置校验（仓库内唯一调用方是 `transformDir` 与测试代码），NPE 路径现实可达。

### [P2] gateway-demo `testTcc` 用裸 `IllegalStateException` 表达业务失败

- **文件**: `nop-demo/nop-spring-gateway-demo-service/src/main/java/io/nop/demo/gateway/service/DemoServiceBizModel.java:40-46`
- **维度**: D4/D7
- **证据**:
```java
String currentState = RESOURCE_STORE.get(resourceId);
if ("reserved".equals(currentState)) {
    LOG.warn("Resource already reserved: resourceId={}", resourceId);
    throw new IllegalStateException("Resource is already reserved: " + resourceId);
}
```
- **现状**: TCC Try 幂等冲突用 JDK 裸 `IllegalStateException` 抛出。该类是 `@BizMutation` 的 GraphQL 服务方法，违背平台两档错误策略（公共 API 应 `NopException` + `ErrorCode` + `.param(...)`；至少用模块异常类），`AGENTS.md` 明令禁止 bare RuntimeException 同族用法。
- **风险**: 这是演示 TCC 分布式事务的范例代码，用户会复制该异常模式；GraphQL 层无法把它映射为带错误码的业务错误。
- **建议**: 定义 `DemoServiceErrors`（参照 `nop-spring-simple-demo/DemoErrors.java` 的正确示范）并用 `new NopException(ERR_...).param(ARG_RESOURCE_ID, resourceId)`。
- **误报排除**: 已确认该方法经 `app-demo-service.beans.xml` 注册、由网关 TCC 流程真实调用，非死代码；同模块其余异常路径均为正常返回。

### [P2] gateway-demo `testTcc` 预留资源为 check-then-act，非原子（TCC 幂等示范存在竞态）

- **文件**: `nop-demo/nop-spring-gateway-demo-service/src/main/java/io/nop/demo/gateway/service/DemoServiceBizModel.java:40-46`（配合 28 行 `ConcurrentHashMap`）
- **维度**: D3
- **证据**:
```java
private static final Map<String, String> RESOURCE_STORE = new ConcurrentHashMap<>();
...
String currentState = RESOURCE_STORE.get(resourceId);   // check
if ("reserved".equals(currentState)) { ... throw ... }
RESOURCE_STORE.put(resourceId, "reserved");             // act
```
- **现状**: 选了 `ConcurrentHashMap` 说明作者有并发意识，但 get+put 是复合操作：同一 `txnId:branchId` 的两个并发 Try（超时重试与慢请求重叠）都能通过检查，各自执行预留，幂等保证失效。
- **风险**: 作为"TCC 资源预留"教学示例教了错误的并发写法；竞态窗口窄、且影响仅限 demo 的内存 store，故定 P2。
- **建议**: 用 `RESOURCE_STORE.putIfAbsent(resourceId, "reserved") != null` 单步完成判断+写入。
- **误报排除**: 已确认无 synchronized/锁保护（grep 全模块无 synchronized）；并发触发路径（RPC 超时重试重叠）现实存在。

### [P2] demo 配置文件硬编码 JWT enc-key 与 SSO client-secret（将被用户复制的密钥模式）

- **文件**（超出 src/main/java 范围，属 D5 重点维度的补充扫描，如实标注）:
  - `nop-demo/nop-quarkus-demo/src/main/resources/application.yaml:8,27`
  - `nop-demo/nop-spring-demo/src/main/resources/application.yaml:8,20`
  - `nop-demo/nop-spring-demo2/src/main/resources/application.yaml:8,18`
- **维度**: D5
- **证据**（quarkus-demo）:
```yaml
nop:
  auth:
    sso:
      enabled: false
      client-secret: qpgEjwXqd1TpgaA3aIi1jd4AVTLCrs8o
    ...
    jwt:
      enc-key: dij3)(4ldt[]erq=2mfKID
```
- **现状**: 同一 JWT 加密密钥与 OAuth2 client-secret 明文出现在三个 demo 模块的 application.yaml 中；quarkus-demo 另有 `enable-action-auth: false`、`enable-data-auth: false`、`allow-create-default-user: true`（自动建 nop/nop-test 账户）。
- **风险**: 这些值随开源仓库公开，本身已无秘密价值；真正的风险是坏模式传播——用户复制 demo 配置到生产时 JWT 密钥公网可知，token 可被伪造。任务 D5 明确将"会被用户复制的硬编码密钥"列为重点。
- **建议**: demo 配置改为从环境变量/外部配置读取并给注释示范（如 `enc-key: ${NOP_JWT_ENC_KEY:demo-only}`），并在 yaml 内注释"生产环境必须替换"。
- **误报排除**: 已核对 Java 源码中无任何硬编码密码/密钥（grep password/secret/token/apikey 仅命中 `UserIdToken` 类名）；`sso.enabled: false` 说明当前未启用该 secret，风险定位为模式传播而非现网泄露。

### [P3] spring-security-demo 整链路"模拟放行"：无认证过滤器 + 恒真权限评估器 + 构造期 setAuthenticated(true)

- **文件**:
  - `nop-demo/nop-spring-security-demo/src/main/java/io/nop/demo/spring/security/WebAuthFilter.java:38-59`
  - `nop-demo/nop-spring-security-demo/src/main/java/io/nop/demo/spring/security/SpringPermissionEvaluator.java:14-24`
  - `nop-demo/nop-spring-security-demo/src/main/java/io/nop/demo/spring/security/UserIdToken.java:10-14`
- **维度**: D5（D6 附带：`SpringActionAuthChecker.java:24` 与 evaluator 每次鉴权打 `LOG.info`，生产复制会产生日志噪音）
- **证据**:
```java
// WebAuthFilter: 对每个请求无条件设置已认证身份
String userId = "nop";
String userName = "123";
...
secureContext.setAuthentication(new UserIdToken(userId));

// SpringPermissionEvaluator:
public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
    LOG.info("nop.check-permission:{}", permission);
    return true;
}

// UserIdToken: 构造函数内
setAuthenticated(true);
```
- **现状**: 三个环节合起来使该"安全集成示例"的鉴权完全装饰性：任何请求都被认证为 nop（roles: manager/checker），任何权限检查都返回 true。代码注释有"这里模拟登录成功设置user上下文""仅起示例作用"，意图明确。
- **风险**: 意图虽有注释，但无任何醒目的"生产禁用"警告；`WebAuthFilter.java:42` 注释"这里应该按照具体框架要求设置token，这里仅仅是"语句截断，示例不完整。复制者容易保留恒真 evaluator。
- **建议**: 类级 Javadoc 加显著警告；在 evaluator 返回处留 TODO 模板（如演示从角色表判断）；补全截断注释。
- **误报排除**: 已确认这是演示集成机制的刻意设计（注释多处声明），且 `/error` permitAll 等配置合理，故不定 P2；风险仅在"被原样复制"。

### [P3] nop-ofbiz-migration 视图实体二次处理时可追加重复 `columns` 节点

- **文件**: `nop-migration/nop-ofbiz-migration/src/main/java/io/nop/ofbiz/migration/transform/EntityDefModel.java:185-199`（配合 `EntityDefDirTransformer.java:63-79`）
- **维度**: D1（边缘输入条件）
- **证据**:
```java
private void transformViewAlias(XNode viewEntityNode, XNode entityNode, Function<String, XNode> entityResolver) {
    XNode columns = entityNode.childByTag("columns");
    // 如果已经具有columns，则视图对应的alias已经被解析
    if (columns != null && columns.hasChild())
        return;
    ...
    columns = entityNode.addChild("columns");   // 已存在空columns时会再追加一个
```
- **现状**: 幂等保护条件是"columns 存在且非空"。若某 view-entity 无 alias/alias-all 子元素（或别名全部被跳过/报错），首轮处理只留下空 columns；当该视图被其他模块视图跨模块引用时 `resolveViewEntity` 会再次进入处理，`addChild("columns")` 产生第二个 columns 节点，输出不符合 orm.xdef 约束。
- **风险**: 仅在"空别名视图 + 跨模块视图引用视图"的罕见输入下触发；触发时生成非法 orm XML。
- **建议**: 幂等判断改为 `if (columns != null) return;`（transformViewEntity 阶段保证不会预建 columns），或 `makeChild` 语义替代 `addChild`。
- **误报排除**: 已完整追踪两条触发路径（transformDir 首轮 + resolveViewEntity 递归重入），确认重入时仅有空 columns 的场景未被守卫；正常 OFBiz 模型中别名非空，故概率低，定 P3。

### [P3] nop-ofbiz-migration 错误处理风格：伪错误码字符串 + 任意对象拼进异常消息 + 模块内同名 view 去重缺失

- **文件**: `nop-migration/nop-ofbiz-migration/src/main/java/io/nop/ofbiz/migration/transform/EntityDefModel.java:49,94,104-106,226,320,327`、`EntityDefDirTransformer.java:96`、`EntityDefModel.java:322-327`（null 判断次序）
- **维度**: D4（附带 D1 边缘）
- **证据**:
```java
throw new IllegalArgumentException("nop.err.ofbiz.duplicate-entity-name:name=" + name + "," + pair.getSecond());
// pair.getSecond() 是 XNode，toString 会输出整个实体XML子树
...
String name = col.attrText("name");
if (excludes.contains(name))      // 先用 name 做 contains
    continue;
if (name == null)                 // null 判断在使用之后，且 excludes 可含 null 导致空名列静默跳过
    throw new IllegalArgumentException("nop.err.null-col-name:" + col);
```
- **现状**:
  1. 多处以 `IllegalArgumentException` + 形如 `nop.err.ofbiz.xxx:` 的伪错误码字符串抛错，既非注册的 `ErrorCode` 也非模块异常类，不符合两档错误策略；
  2. 异常消息直接拼接 `XNode` 对象，大模型会输出整段 XML，异常消息爆炸；
  3. `transform()` 中 view-entity 循环只检查与 entity 重名（104 行 `entityNodes.containsKey`），不检查 viewEntityNodes 内部重名，同文件内同名 view 会先 append 到 entities 节点再静默覆盖 map，产出重复 `<entity>` 且无报错；
  4. `makeAliasAll` 中 null 检查排在 `excludes.contains(name)` 之后，且 `<exclude>` 缺 field 属性时 excludes 含 null，空名列列会被静默跳过而非触发本应抛出的 `nop.err.null-col-name`。
- **风险**: 迁移失败时排障体验差；异常消息可能达数十 KB；畸形输入静默产出重复实体。工具模块、影响面有限，定 P3。
- **建议**: 定义 `OfbizMigrationErrors`（ErrorCode + param），消息只带实体名不带 XML dump；view 循环补 `viewEntityNodes.containsKey` 检查；`makeAliasAll` 把 null 检查移到 contains 之前并过滤 excludes 中的 null。
- **误报排除**: 逐条核对行号与短路顺序；确认 `StringHelper.replace(null,...)` 返回 null（transformRelation 的 null 分支正确，未列入）。

### [P3] quarkus-demo 业务路径使用 `System.out.println` 而非 Logger

- **文件**: `nop-demo/nop-quarkus-demo/src/main/java/io/nop/demo/biz/DemoBizModel.java:97`、`nop-demo/nop-quarkus-demo/src/main/java/io/nop/demo/interceptors/SendEmailInterceptor.java:17`
- **维度**: D6/D7（示例质量）
- **证据**:
```java
@BizQuery
@SendEmail
public void testMethod1(@RequestBean MyRequest request) {
    System.out.println("doSomething");
}
// SendEmailInterceptor:
System.out.println("sendEmail:message=" + ((MyRequest) arg).getMessage());
```
- **现状**: 同一 BizModel 其余方法均用 `LOG`（如 `TestRpcBizModel`），仅 AOP 演示相关两处走 stdout。
- **风险**: 示例教坏日志习惯（stdout 不可分级、不可收集）；`SendEmailInterceptor` 把请求消息打到 stdout，复制到生产会泄露业务数据到日志管道外。
- **建议**: 改 `LOG.info`，与模块内其余代码一致。
- **误报排除**: 已区分 main() 诊断输出（`SpringSecurityDemoMain` 等 maxMem 打印属启动诊断，不列）；仅列 biz 请求路径上的两处。

## 已核实无问题的检查点（负面结论摘录）

- D7 `@Inject` 字段可见性: 全部命中点（quarkus `DemoBizModel`、rpc-client `TestRpcBizModel`、delta `LoginApiBizModelDelta`、security `SpringActionAuthChecker`、simple-demo `DemoEntityBizModel`）均为 package-private/protected，无 private 注入；`DemoEntityBizModel` 还带教学注释示范。beans.xml 注册核对全部通过（quarkus/rpc-client/security/simple/no-orm/gateway/delta 七处）。
- 无空 catch、无 `new RuntimeException`、无 `printStackTrace`、无 synchronized/裸线程使用（gateway demo 的 ConcurrentHashMap 见上条竞态发现）。
- `findUsers` 返回 `NopAuthUser` 实体集合不构成敏感信息泄露: `NopAuthUser.xmeta` 中 `password` 为 `published="false"`。
- rpc client/server 契约: 客户端 `EchoService` 仅 `@Path` 无方法注解，`HttpRpcService` 默认 POST（`HttpRpcService.java:71-73`），与服务端 `@PostMapping` 匹配，无 D8 问题。
- `ReportController` 临时文件管理正确: 失败即删、成功后 5 分钟定时删，异常经 `NopException.adapt(e)` 保留 cause。
- `FeignTestRpc` 的 `@RequestParam("%40selection")` URL 编码行为依赖 Feign/Servlet 双方实现，无法在静态检查中确证为 bug，按"宁缺毋滥"原则不列发现，建议后续运行时验证。
