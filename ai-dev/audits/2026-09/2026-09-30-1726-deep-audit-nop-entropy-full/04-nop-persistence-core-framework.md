# G4: nop-persistence + nop-core-framework 深度审计（首轮）

- **审计日期**: 2026-09-30
- **审计维度**: 09（错误处理与错误码）、13（安全与权限模型—SQL 注入面）、14（异步与事务模式/资源泄漏/IoC 生命周期）、15（类型安全与泛型）、16（测试覆盖与质量）
- **复核状态**: 未复核（首轮初审，待维度复核）

## 审计范围

- `nop-persistence/` 全部子模块（nop-dao、nop-orm、nop-orm-eql、nop-orm-model、nop-db-migration、nop-dbtool/nop-dbtool-core、nop-nosql、nop-orm-* 等），重点深读：nop-dao（SQL 生成/dialect/txn/jdbc）、nop-orm（persister/session/dao/工厂装配）、nop-dbtool（连接与元数据发现）。
- `nop-core-framework/` 全部子模块（nop-config、nop-ioc、nop-boot、nop-plugin、nop-security、nop-log），重点深读：nop-ioc 容器装配与生命周期、nop-security 加密助手、nop-boot。
- 排除：`target/`、`_` 前缀生成文件（如 `_gen/_SqlItemModel.java`）、`.m2-repo-2275/`、`_tmp/`。
- 基线核实：主 agent 给出的裸异常/System.out 计数含 src/test；逐个核实后，main 代码中 **printStackTrace 为 0 处**、System.out 仅 2 处且均为 nop-boot 有意的 banner/成功标志输出（`NopBanner.java:26`、`NopApplication.java:67`，不报）。main 裸异常约 40 处，其中大部分是接口 default 方法的 `UnsupportedOperationException`（optional-operation 惯例，不报），少数构成下述 [G4-09-01] 集群。

### 深读文件清单（节选）

`DaoQueryHelper.java`、`JdbcTemplateImpl.java`、`JdbcBatcher.java`、`JdbcTransaction.java`、`TransactionTemplateImpl.java`、`AbstractTransaction.java`、`DefaultTransactionManager.java`、`DialectImpl.java`、`LimitOffsetPaginationHandler.java`、`EntityPersisterImpl.java`、`OrmSessionImpl.java`、`SessionFactoryImpl.java`、`GenSqlHelper.java`、`JdbcEntityPersistDriver.java`、`SqlLibManager.java`、`JdbcMetaDiscovery.java`、`DataBaseUpgrader.java`、`BeanContainerImpl.java`、`BeanScopeImpl.java`、`DelegateInvocationHandler.java`、`NopApplication.java`、`SecurityHelper.java`、`RsaHelper.java`、`KeySetHelper.java`、`DefaultKeyManager.java`、`DefaultConfigValueEnhancer.java`、`OrmConfigs.java`、`orm-defaults.beans.xml`。

### SQL 注入面总评（维度 13）

nop-dao/nop-orm 的注入面防御整体**扎实**，未发现 P0/P1 注入漏洞：

- 动态排序/过滤字段：`DaoQueryHelper.checkFieldName/checkOwnerName/checkEntityName/checkFuncName`（`isValidPropPath`/`isValidSimpleVarName` 白名单）+ `FilterBeanToSQLTransformer` 的 `checkVarName` 双层校验；`queryToUpdateSql` 对 props key 逐个校验并有防注入注释。
- 分页：`LimitOffsetPaginationHandler` 用 `LIMIT ?`/`OFFSET ?` 参数化，非拼接。
- 标识符：`DialectImpl.escapeSQLName` 对非合法 var-name 统一 `quoteDupEscapeString` 转义；`tryExistsByTemplate` 的名字经 `dialect.getStringLiteral`（`escapeSql`）转义。
- EQL 路径：用户可控的 QueryBean 字段走 EQL 编译器（按实体模型注册表解析），非法名在编译期失败。
- dbtool：`JdbcMetaDiscovery` 全部 try-with-resources / `IoHelper.safeCloseObject`；`DataBaseUpgrader` 明确不生成 drop 语句。
- 剩余两个 P3 为纵深防御不一致（见 [G4-13-01]/[G4-13-02]）。

### 事务与资源管理总评（维度 14）

`TransactionTemplateImpl`（同步/异步、传播行为全实现、rollback 失败 as suppressed）、`AbstractTransaction`（listener 竞态、commit 失败终态通知）、`JdbcBatcher`（回调恰好一次契约、forceTxn 原子性、残留命令清理）、`JdbcTemplateImpl`（finally + safeCloseObject + metrics）均显示近期被系统性加固过，资源泄漏与吞异常问题未发现。发现项为 IoC 销毁顺序（[G4-14-01]）、全局缓存一致性（[G4-14-02]）与同步/异步 catch 口径不对称（[G4-14-03]）。

---

## 发现

### [G4-09-01] 框架核心多处以裸 IAE/ISE 抛出错误码格式字符串，绕过 NopException + ErrorCode 两档策略

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/sql_lib/SqlLibManager.java:252`、`nop-persistence/nop-orm/src/main/java/io/nop/orm/component/XmlOrmComponent.java:156`、`nop-persistence/nop-orm/src/main/java/io/nop/orm/interceptor/XplOrmInterceptor.java:73`、`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/DelegateInvocationHandler.java:34`、`nop-persistence/nop-orm/src/main/java/io/nop/orm/dao/DaoQueryHelper.java:127`、`nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/impl/JdbcTemplateImpl.java:593`
- **证据片段**（6 处代表，均为 main 代码）:
  ```java
  // SqlLibManager.java:251-253
  } else {
      throw new IllegalArgumentException("nop.err.orm.validate-input-result-not-map");
  }

  // XmlOrmComponent.java:155-157
  if (propMeta == null) {
      throw new IllegalArgumentException("nop.err.invalid-child-name:" + childName);
  }

  // XplOrmInterceptor.java:72-74
  } else {
      throw new IllegalArgumentException("nop.err.orm.unsupported-event:" + event);
  }

  // DelegateInvocationHandler.java:33-35
  if (handler == null)
      throw new IllegalStateException("nop.err.ioc.bean-proxy-not-initialized:" + method);

  // DaoQueryHelper.java:126-127
  if (query == null)
      throw new IllegalArgumentException("null query");
  ```
- **严重程度**: P2
- **现状**: nop-orm/nop-dao/nop-ioc 均为框架核心（AGENTS.md 与 error-handling.md 规定框架核心必须 NopException + ErrorCode + `.param()`），且这些模块自身已有 `OrmErrors`/`DaoErrors`/`IocErrors` 错误码体系；但这 6 处用裸 `IllegalArgumentException`/`IllegalStateException` 抛出 `nop.err.*` 格式的字符串消息，其中 `"nop.err.orm.validate-input-result-not-map"`、`"nop.err.invalid-child-name"`、`"nop.err.orm.unsupported-event"`、`"nop.err.ioc.bean-proxy-not-initialized"` 在对应 Errors 类中**均未定义**（grep 无命中），属于"半迁移"状态。
- **风险**: 这些异常会穿透到 GraphQL/HTTP 边界：丢失 errorCode 字段（前端无法按码处理）、无法走 i18n 翻译、无 `.param()` 上下文（如 `bean-proxy-not-initialized` 只有 method 名，缺 beanName）；`DelegateInvocationHandler` 是运行期懒代理初始化失败路径，生产排查时只能拿到裸字符串。也违反 error-handling.md「不要这样写」表中"裸 RuntimeException 反模式"条目的同族约束。
- **建议**: 在 `OrmErrors`/`IocErrors`/`DaoErrors` 中补定义对应 ErrorCode（含 ARG_* 参数常量），改抛 `new NopException(ERR_...).param(...)`；`DaoQueryHelper:127` 与同文件其余 NopException 风格对齐。
- **信心水平**: 确定（逐处核实行号与 Errors 类定义缺失）。
- **误报排除**: 不是接口 default 方法中的 `UnsupportedOperationException`（optional-operation 惯例已排除）；这些是真实运行/启动路径（sql-lib 校验、组件 setChildValue、拦截器事件分发、懒代理 invoke、查询构建参数检查）。接口 default 的 UOE（IJdbcTemplate:51-67 等）未计入本条。
- **复核状态**: 未复核

### [G4-09-02] EQL 字面量解析错误消息混入全角标点（非 ASCII），且为裸 IAE

- **文件**: `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/ast/SqlHexadecimalLiteral.java:26-29`
- **证据片段**:
  ```java
  } else if (str.startsWith("X")) {
      str = str.substring(1);
  } else {
      throw new IllegalArgumentException("invalid hex value；" + getValue());
  }
  ```
- **严重程度**: P3
- **现状**: 错误消息 `"invalid hex value；"` 使用中文全角分号 `；`（U+FF1B），消息主体是英文但混入非 ASCII 标点；同时该处位于框架核心 nop-orm-eql 的 EQL 解析路径，按两档策略应使用 `NopEvalException`/`NopException` 体系。
- **风险**: 违反 AGENTS.md「Error messages must be in English」的字面要求；全角标点在某些日志/终端编码下显示为乱码，且此消息会被 AI/开发者直接阅读。
- **建议**: 改为半角 `:` 并迁移到带定位信息的异常（`NopEvalException` + location），与同文件所在 AST 体系的错误风格对齐。
- **信心水平**: 确定（直接读源码核实字符）。
- **误报排除**: 不是注释或文档中的中文（已用 perl 全量扫描 main 代码异常消息，其余异常消息无 CJK；本处是唯一混入非 ASCII 标点的异常消息）。
- **复核状态**: 未复核

### [G4-13-01] DaoQueryHelper 五个 queryTo*Sql 中仅两个校验 entityName，注入防御不对称

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/dao/DaoQueryHelper.java:76-78、243-246、257-263（缺校验） vs 148、267（有校验）`
- **证据片段**:
  ```java
  // :76-78 queryToSelectObjectSql —— entityName 直接拼接，无 checkEntityName
  public static SQL queryToSelectObjectSql(String entityName, QueryBean query) {
      SQL.SqlBuilder sb = newSQL(query);
      sb.append("select o from ").append(entityName).as("o");

  // :243-246 queryToCountSql —— 同样无校验
      sb.append("select count(1) from ").append(entityName).as("o");

  // :269-274 queryToUpdateSql —— 有显式校验和防注入注释
      // props的key会被原样拼接到set子句的SQL文本中，必须做合法性校验，
      // 避免恶意字段名(如带注释符)被拼入后吞掉参数占位符
  // :148 queryToSelectFieldsSql 内部有 checkEntityName(query.getSourceName());
  ```
- **严重程度**: P3
- **现状**: 同一个 public static 工具类中，`queryToSelectFieldsSql`（:148）和 `queryToUpdateSql`（:267）对 entityName 做了 `checkEntityName`（isValidClassName 白名单），而 `queryToSelectObjectSql`（:78）、`queryToCountSql`（:246）、`queryToDeleteSql`（:260，`sb.deleteFrom(entityName)`）三个方法把 entityName 原样拼进 EQL 文本，不做任何校验。
- **风险**: 当前仓库内三个方法的调用方只有 `OrmEntityDao`（传入固定的 `getEntityName()`，来自已注册实体模型），**现阶段不可利用**；但这是 public 工具类，任何新调用方（如把 `query.getSourceName()` 等外部输入直接传入）即可将任意文本拼入 EQL。EQL 编译器虽按实体注册表解析，可拦截大部分恶意名，但防御纵深与同文件兄弟方法不一致，属于明显的校验遗漏而非设计差异。
- **建议**: 在三个方法中补 `checkEntityName(entityName)`，与 `queryToUpdateSql`/`queryToSelectFieldsSql` 对齐（一行改动 × 3 处）。
- **信心水平**: 确定（不对称性由源码直接可见，调用方已全量 grep 核实）。
- **误报排除**: 不是"所有 queryTo* 都没校验"（有两个校验了的兄弟方法，证明校验是该类的预期基线）；不是生成代码（手写文件）；不是 nop-core 的 `FilterBeanToSQLTransformer` 问题（那是另一层，已有 checkVarName）。
- **复核状态**: 未复核

### [G4-13-02] appendOrderBy 对 orderField.getOwner() 校验后丢弃：与 appendGroupBy 语义相反，属"校验了但未生效"

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/dao/DaoQueryHelper.java:213-226`（消费侧）；根因在 `nop-kernel/nop-core/src/main/java/io/nop/core/lang/sql/SQL.java:600-608`（范围外，此处仅记录消费契约）
- **证据片段**:
  ```java
  // DaoQueryHelper.java:217-225
  sb.br().orderBy();
  for (int i = 0, n = orderBy.size(); i < n; i++) {
      OrderFieldBean orderField = orderBy.get(i);
      if (i != 0)
          sb.append(',');
      checkOwnerName(orderField.getOwner());   // <-- 校验了 owner
      checkFieldName(orderField.getName());
      sb.orderField(defaultOwner, orderField); // <-- 但 orderField 内部忽略 owner
  }

  // SQL.java:600-604（nop-core，根因）
  public SqlBuilder orderField(String defaultOwner, OrderFieldBean orderField) {
      String owner = defaultOwner;
      if (owner == null)
          owner = orderField.getOwner();  // defaultOwner 非空时字段自身 owner 被忽略
      owner(owner);

  // 对比 DaoQueryHelper.appendGroupBy:200-205 —— 字段 owner 优先，default 仅兜底（正确语义）
      String owner = groupField.getOwner();
      if (owner == null)
          owner = defaultOwner;
  ```
- **严重程度**: P3
- **现状**: `SQL.SqlBuilder.orderField(defaultOwner, field)` 在 defaultOwner 非空时**忽略**字段自身的 owner；而 `appendOrderBy` 传入的 defaultOwner 恒为 "o"（所有调用方），因此 `OrderFieldBean.owner` 在 ORDER BY 生成中被静默丢弃。同文件的 `appendGroupBy` 是"字段 owner 优先"的正确 default 语义，两处相反；`checkOwnerName(orderField.getOwner())` 校验了一个随后不会被使用的值。
- **风险**: 当前所有调用方（queryToSelectObjectSql/queryToFindNext/PrevSql 等）均为单别名 "o" 查询，行为偶然正确，故**现在无错误输出**；但任何未来调用方在多别名/join 场景带 owner 排序（如 `left join o.b` 后按 b.x 排序）会静默生成 `order by o.x`（排序列错误，结果集顺序错），且校验通过不会报错，属于隐蔽的契约陷阱。
- **建议**: 在 `DaoQueryHelper.appendOrderBy` 中改为与 `appendGroupBy` 一致的"字段 owner 优先、default 兜底"显式拼接（消费侧即可修复，不依赖 nop-core）；或向 nop-core `orderField` 提交语义修正并同步注释。
- **信心水平**: 确定（两处语义对比与调用链均已读源码核实；影响为潜伏性）。
- **误报排除**: 不是 nop-core 审计的重复上报——发现锚定在 in-scope 的 `DaoQueryHelper.appendOrderBy` 消费契约与同文件内部不一致；不是"未校验"问题（校验存在但被架空，恰是双向断言检查中"校验了但未生效"的变体）。
- **复核状态**: 未复核

### [G4-14-01] BeanScopeImpl 单例销毁顺序不确定（ConcurrentHashMap 遍历序），容器已有拓扑序却未用于销毁

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanScopeImpl.java:29、96-118`；`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java:489、595-617`
- **证据片段**:
  ```java
  // BeanScopeImpl.java:29
  private final Map<String, ProducedBeanInstance> beans = new ConcurrentHashMap<>();

  // BeanScopeImpl.java:103-112 —— 按哈希桶序遍历销毁
  Exception e = null;
  for (Map.Entry<String, ProducedBeanInstance> entry : beans.entrySet()) {
      String beanName = entry.getKey();
      try {
          remove(beanName, entry.getValue());  // remove 内部调用 container.destroyBean
      } catch (Exception ex) {
          LOG.error("nop.err.ioc.destroy-bean-fail:beanName={}", beanName, ex);

  // BeanContainerImpl.java:489 —— 启动时用的是拓扑序 orderedBeans
  for (BeanDefinition bean : orderedBeans) { ... startBean(startBeans, bean); }
  // BeanContainerImpl.java:605-607 —— 停止时只调 singletonScope.close()，未用 orderedBeans 的逆序
      try {
          singletonScope.close();
  ```
- **严重程度**: P2
- **现状**: 容器启动严格按 `BeanTopologySorter` 拓扑序创建 bean（`orderedBeans`），但销毁走 `BeanScopeImpl.close()` 对 `ConcurrentHashMap.entrySet()` 的遍历——顺序由 bean 名哈希决定，与创建顺序、依赖关系均无关。容器明明持有 `orderedBeans`（含依赖信息），却没有用其逆序销毁。
- **风险**: bean A 依赖 bean B（如业务 bean 依赖连接池/线程池/ORM sessionFactory）时，B 可能先被 destroy，A 的 `@PreDestroy`/destroy-method 随后执行时其依赖已失效——产生 shutdown 期报错（`nop.err.ioc.destroy-bean-fail`）、清理不彻底（缓冲未 flush、资源未释放）。顺序还随 bean 名集合与哈希容量变化，表现为"偶现"的关闭期故障，排查成本高。与 Spring 的 reverse-creation-order 销毁契约不一致，也从 Spring 迁移而来的用户预期相悖。
- **建议**: `BeanContainerImpl.stop()` 改为按 `orderedBeans` 逆序（且跳过未实例化的 bean）逐个 destroy，`BeanScopeImpl` 只负责清除残留；至少先为 `close()` 换成记录创建序的容器（如LinkedHashMap+同步）以获得确定性的逆创建序。
- **信心水平**: 确定（代码行为）；影响表述"很可能"（多数 @PreDestroy 对依赖失效不敏感，但连接池/executor 类依赖是真实场景）。
- **误报排除**: 不是有意设计——容器专门维护拓扑序用于启动/delay-method/lazy-prop（:489/:571/:583），销毁不使用与整体设计不一致；nop-boot/其它模块也无补偿机制（ShutdownHook 只调 CoreInitialization.destroy）。
- **复核状态**: 未复核

### [G4-14-02] ORM 实体全局缓存一致性缺口：并发读复活旧值、UNKNOWN 提交状态不驱逐、默认本地缓存无跨实例失效

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/EntityPersisterImpl.java:523-542、550-566`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/OrmConfigs.java:47-48`；`nop-persistence/nop-orm/src/main/resources/_vfs/nop/orm/beans/orm-defaults.beans.xml:26-29`
- **证据片段**:
  ```java
  // EntityPersisterImpl.java:535-541 —— 读路径回填缓存
  Object[] values = OrmAssembly.getPropValues(entity, entityModel.getAllPropIds());
  globalCache.putAsync(getCacheKey(entity), values);

  // EntityPersisterImpl.java:556-565 —— 写路径延迟驱逐（仅 onAfterCommit）
  if (env.txn().isTransactionOpened(querySpace)) {
      this.env.txn().addTransactionListener(querySpace, new ITransactionListener() {
          @Override
          public void onAfterCommit(ITransaction txn) {
              globalCache.removeAsync(getCacheKey(entity));
          }
      });
  } else {
      globalCache.removeAsync(getCacheKey(entity));
  }
  ```
  另：`orm-defaults.beans.xml:26` 默认 `nopOrmGlobalCacheProvider = LocalCacheProvider`；`OrmConfigs.java:47-48` `CFG_ENTITY_GLOBAL_CACHE_ENABLED` 默认 `true`，TTL 默认 10 分钟。
- **严重程度**: P3
- **现状**: 三个叠加的一致性缺口：(a) **读复活竞态**——并发读线程在 T1 提交前从 DB 读到旧值，在 T1 的 `onAfterCommit` removeAsync 之后执行 `putAsync` 旧值，缓存被污染直至 TTL；(b) **UNKNOWN 状态不驱逐**——listener 只实现 `onAfterCommit`，`AbstractTransaction.commit()` 失败路径发送 `CompleteStatus.UNKNOWN`（可能已部分生效）时不驱逐；(c) **跨实例无失效**——默认 `LocalCacheProvider` 为 JVM 本地缓存，多实例部署时 A 实例提交后的驱逐对 B 实例缓存无任何通知，B 最长 10 分钟持续返回旧实体。
- **风险**: 开启 `useGlobalCache` 的实体在并发写+读、提交失败、多实例部署场景下读到过期数据。缓解因素：仓库内 source orm.xml 仅 1 个文件启用 `useGlobalCache`（grep 全仓核实），TTL 10 分钟兜底，缓存 provider 可替换为分布式实现。
- **建议**: 在 `orm.xml` 的 `useGlobalCache` 文档/校验中强制提示多实例需分布式缓存 provider；读回填改为"读时若发现版本落后则不回填"或驱逐时同步版本号；UNKNOWN 终态时保守驱逐（`onAfterCompletion(status != ROLLBACK)` 驱逐）。
- **信心水平**: 很可能（三条路径均由源码推出；未构造并发复现，故不标确定）。
- **误报排除**: 不是"缓存就该容忍短暂陈旧"的常规语义之争——(a)(b) 是单实例内也可触发的竞态/终态遗漏，(c) 是默认装配与多实例部署的组合陷阱，均无文档提示；`TestGlobalCache` 已覆盖基本命中/集合驱逐，但未覆盖这三条路径（见 [G4-16-01]）。
- **复核状态**: 未复核

### [G4-14-03] runInTransaction 同步版 catch(Exception) 与异步版 catch(Throwable) 口径不对称

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/txn/impl/TransactionTemplateImpl.java:192-194、237-259`
- **证据片段**:
  ```java
  // :192-194 runInTransactionAsync —— 捕获 Throwable
  } catch (Throwable e) {
      future = FutureHelper.reject(e);
  }

  // :237-244 runInTransaction —— 只捕获 Exception
  } catch (Exception e) {
      if (executed || NopException.shouldRollback(e)) {
          try {
              rollbackTransaction(state, e);
  ```
- **严重程度**: P3
- **现状**: 同一事务模板的两个入口对任务抛出物的捕获口径不同：异步版把 `Error`（如 StackOverflowError、NoClassDefFoundError）也纳入回滚决策；同步版 `catch (Exception e)` 不捕获 Error，Error 直接穿透到 `finally cleanupTransaction`（注销+close，连接被释放回池），跳过显式 `rollbackTransaction` 及 rollback listener 通知。
- **风险**: 任务抛 Error 时：未调用 `ITransaction.rollback`，`onBeforeCompletion`/`onAfterCompletion(ROLLBACK)` 不触发（依赖池在归还时回滚，JDBC 连接关闭时数据库端会回滚，数据层面通常安全，但依赖 listener 做清理/指标/缓存逻辑的组件收不到回滚通知）；与异步版行为不一致，同一业务逻辑同步/异步迁移后故障行为不同。
- **建议**: 同步版改 `catch (Throwable e)`，在处理 `NopException.shouldRollback`（需适配 Throwable 重载或先行包装）后 `throw NopException.adapt(e)` 保持传播；或至少在 Javadoc 标注两版差异。
- **信心水平**: 很可能（不对称确定；实际数据损坏风险低，主要是回调契约不一致）。
- **误报排除**: 不是"永远不该 catch Error"的教条场景——同文件异步版已捕获 Throwable，说明作者意图是统一口径，同步版属遗漏；finally 清理虽兜底了连接释放，但 rollback listener 契约缺口真实存在。
- **复核状态**: 未复核

### [G4-15-01] ISqlExecutor.findFirst/findAll 的 `<T>` 泛型承诺与 SmartRowMapper 运行时行为脱钩，错误延迟到使用点爆发

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/api/ISqlExecutor.java:64-73、161`；`nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/impl/JdbcTemplateImpl.java:145-147`；行为源 `nop-kernel/nop-dataset/src/main/java/io/nop/dataset/rowmapper/SmartRowMapper.java:28-33`
- **证据片段**:
  ```java
  // ISqlExecutor.java:64-73 —— T 完全由调用点推断，无任何运行时约束
  default <T> T findFirst(SQL sql) {
      return findFirst(sql, getDefaultRowMapper());
  }
  default <T> List<T> findAll(final SQL sql) {
      return findAll(sql, getDefaultRowMapper());
  }

  // JdbcTemplateImpl.java:145-147 —— 无检查强转
  public <T> IRowMapper<T> getDefaultRowMapper() {
      return (IRowMapper<T>) SmartRowMapper.INSTANCE;
  }

  // SmartRowMapper.java:28-33 —— 单列返回标量，多列返回 Map
  public Object mapRow(IDataRow row, long rowNumber, IFieldMapper colMapper) {
      if (row.getFieldCount() == 1) {
          return colMapper.getValue(row, 0);
      }
      return baseMapper.mapRow(row, rowNumber, colMapper);
  }
  ```
- **严重程度**: P3
- **现状**: `findFirst`/`findAll` 的 `<T>` 从赋值目标推断，而实际返回物由结果集**列数**决定（1 列→标量，多列→`Map`）。调用方写 `List<String> names = findAll(sql)` 而 SQL 选了 2 列时，返回的是 `List<Map>`，CCE 在元素被使用时才爆发，距错误调用点可能很远。
- **风险**: 公共 API 的泛型签名承诺了它无法保证的类型关系；报错点远离根因，增加排查成本。平台内已有类型安全替代（`findLong/findString/findInt` 用显式 binder；多列应显式传 mapper 或用 `findListByQuery` 的 ColumnMapRowMapper 语义）。
- **建议**: 至少在 Javadoc 标注"单列返回标量/多列返回 Map，T 需与结果形状匹配"；或长期把无参重载收敛为返回 `Object`/`List<Map<String,Object>>`，把 `<T>` 版本要求显式传 mapper。
- **信心水平**: 确定（签名与 mapper 行为均直接核实）。
- **误报排除**: 不是对平台动态边界的苛求——这是公开 DAO API 的静态签名问题（编译器背书了类型却运行时违约），非 XDSL/动态 schema 场景；也不属于"内部接口高性能返回 Object"豁免（ISqlExecutor 是面向所有业务模块的公共执行器接口）。
- **复核状态**: 未复核

### [G4-16-01] 实体全局缓存的一致性缺口路径无测试覆盖（读复活竞态 / UNKNOWN 提交 / 集合驱逐外的终态）

- **文件**: `nop-persistence/nop-orm/src/test/java/io/nop/orm/dao/TestGlobalCache.java:30-154`（现有覆盖）；对应被测缺口 `nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/EntityPersisterImpl.java:523-566`
- **证据片段**:
  ```java
  // TestGlobalCache.java 现有方法（grep 核实）：
  // :30  testGlobalCache()
  // :62  testBatchLoadErrorPropagates()
  // :93  testTenantBatchLoadGlobalCacheHit()
  // :129 testCollectionGlobalCacheEvictedAfterChange()
  // —— 无并发读写复活、无 commit 失败(UNKNOWN)终态、无多写连续驱逐场景
  ```
- **严重程度**: P3
- **现状**: nop-orm 测试总体质量高（flush 失败注册、逻辑删除、租户、批量加载错误传播均有专测），但 `EntityPersisterImpl` 全局缓存的三个脆弱路径（[G4-14-02] 的 (a)(b)(c) 中前两条可在单实例内用模拟并发/失败事务测试）没有任何测试触达；现有 `testCollectionGlobalCacheEvictedAfterChange` 只覆盖"变更后驱逐"的直线路径。
- **风险**: [G4-14-02] 的修复无法回归验证；未来对 `evictGlobalCache`/`updateGlobalCache` 的重构（如改 listener 时机）可能无声破坏缓存一致性。
- **建议**: 增补：1) 提交失败（`doCommit` 抛异常 → UNKNOWN 终态）后断言缓存行为（当前是"不驱逐"，修复后应驱逐）；2) 用两线程/手动编排 `loadFromGlobalCache` miss→DB 读→`updateGlobalCache` 与 `evictGlobalCache` 的交错，固化复活竞态的已知行为或验证修复；3) rollback 后缓存保持旧值（现有正确行为）的守护测试。
- **信心水平**: 确定（测试方法清单已穷举核实）。
- **误报排除**: 不是"数量不足"式泛泛而谈——是具体高危路径的定向缺失；也不是要求对 AutoTest 快照文件本身审计（快照机制是平台标准模式，此处只看测试方法覆盖面）。
- **复核状态**: 未复核

### [G4-09-03] checkValid(entity) 用 ERR_ORM_SESSION_CLOSED 表达"实体属于另一个 session"，错误码语义漂移

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:236-240`
- **证据片段**:
  ```java
  void checkValid(IOrmEntity entity) {
      checkValid();
      if (entity.orm_enhancer() != this)
          throw new OrmException(ERR_ORM_SESSION_CLOSED);
  }
  ```
- **严重程度**: P3
- **现状**: 实体未绑定到当前 session（可能绑定到另一个仍活着的 session）时抛 `ERR_ORM_SESSION_CLOSED`，且不带任何 `.param()`（无实体名/实体标识），调用方与排查者会误判为"session 已关闭"并往 session 生命周期方向排查。
- **风险**: 错误码语义与真实故障不符 + 零上下文参数，跨 session 使用实体这一常见误用（如把懒加载实体传出 session 边界后再访问）的排障成本被放大；错误码是稳定契约，后续修正需同步前端/文档映射。
- **建议**: 新增专用错误码（如 `ERR_ORM_ENTITY_BOUND_TO_OTHER_SESSION`），`.param(ARG_ENTITY_NAME, entity.orm_entityName()).param(ARG_ENTITY_ID, entity.orm_idString())`；过渡期可在现有码上先补参数。
- **信心水平**: 确定（代码直接可见）。
- **误报排除**: 不是"所有错误码都要带参数"的过度要求——这是错误码**语义**错配（闭合 vs 归属），且同文件 `checkValid(IOrmEntitySet coll)`（:242-246）存在同款问题，属同族缺陷；OrmErrors 体系本身成熟（420 行），此处是局部漂移。
- **复核状态**: 未复核

---

## 机械基线核实结论（供主 agent 汇总）

| 基线项 | 主 agent 数字（含 test） | main 代码核实结果 | 定级 |
|---|---|---|---|
| 裸异常 nop-persistence 37 处 | — | main 约 30 处；多为接口 default `UnsupportedOperationException`（惯例，不报）；构成发现的 6 处归入 [G4-09-01]/[G4-09-02] | 见上 |
| 裸异常 nop-core-framework 17 处 | — | main 1 处有实质问题（DelegateInvocationHandler，归入 [G4-09-01]），其余在 test | 见上 |
| System.out/printStackTrace nop-persistence 39 处 | — | main 0 处（全部在 test） | 无发现 |
| System.out/printStackTrace nop-core-framework 35 处 | — | main 2 处 System.out，均为 nop-boot 有意输出（banner、成功标志开关） | 无发现 |
| @Inject private 全仓 0 处 | — | 复核确认本组 0 处 | 无发现 |

## 零/低发现维度说明

- **维度 13（SQL 注入面）主体零高危**：检查了 DaoQueryHelper 全部拼接出口、FilterBeanToSQLTransformer 校验、分页 handler、DialectImpl（escapeSQLName/getStringLiteral/模板渲染）、JdbcTemplateImpl（existsTable/tryExistsByTemplate）、GenSqlHelper（join 左值 getValueLiteral 来自开发者模型）、JdbcMetaDiscovery、DataBaseUpgrader、AlterColumnExecutor（DDL 拼接仅来自模型 diff）。SSRF 主机规范化检查项与本组无 URL 白名单场景，不适用；白名单双向断言检查在 escapeSQLName/isValidPropPath 上通过（非法输入被拒绝/引用转义，合法输入原样通过，无"未命中即透传"回退）。
- **维度 16（测试覆盖）整体强于基线**：事务模板全传播行为 + 回滚失败 suppressed（TestTransactionTemplate/Async）、JdbcBatcher 回调恰好一次契约与 forceTxn 原子性（TestJdbcBatcher 17 个方法）、JdbcTemplate exists 模板/转义/缓存（TestJdbcTemplate）、方言族、DaoQueryHelper 校验错误路径（TestEntityDaoQuery:96-102、276-284）、db-migration executors（TestChangeExecutors）、dbtool differ 跨模块覆盖（nop-autotest-dbtool）。唯一实质缺口见 [G4-16-01]。nop-orm-eql 模块本地 221 main/8 test，但 EQL 手写核心（编译器/AST 优化/集合算子）在 nop-orm-eql 与 nop-orm 两处共有 14 个定向测试类，判断为可接受，不单列发现。
- **维度 15（类型安全）主体克制**：main 代码 `@SuppressWarnings` 315 处，抽样集中于 XDSL/反射/行映射等平台动态边界（文档口径允许）；`(IOrmEntity)`/`(T) entity` 等实体强转由 entityModel 保证类型一致，属平台标准模式。实质发现仅 [G4-15-01]。

## 最终保留项（待复核）

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|---|---|---|---|
| G4-09-01 | P2 | SqlLibManager.java:252 等 6 处 | 框架核心裸 IAE/ISE 抛 nop.err.* 字符串，绕过 ErrorCode 体系 |
| G4-09-02 | P3 | SqlHexadecimalLiteral.java:28 | 错误消息混入全角标点且为裸 IAE |
| G4-09-03 | P3 | OrmSessionImpl.java:236-246 | ERR_ORM_SESSION_CLOSED 语义错配 + 无参数 |
| G4-13-01 | P3 | DaoQueryHelper.java:76/243/257 | 三个 queryTo*Sql 缺 entityName 校验，防御不对称 |
| G4-13-02 | P3 | DaoQueryHelper.java:213-226 | orderBy owner 校验后被丢弃，与 groupBy 语义相反 |
| G4-14-01 | P2 | BeanScopeImpl.java:96-118 | 单例销毁顺序不确定，未用 orderedBeans 逆序 |
| G4-14-02 | P3 | EntityPersisterImpl.java:550-566 | 全局缓存读复活竞态/UNKNOWN 不驱逐/本地缓存无跨实例失效 |
| G4-14-03 | P3 | TransactionTemplateImpl.java:192 vs 237 | 同步 catch(Exception) 与异步 catch(Throwable) 口径不对称 |
| G4-15-01 | P3 | ISqlExecutor.java:64-73 | findFirst/findAll 泛型 `<T>` 与列数决定的行为脱钩 |
| G4-16-01 | P3 | TestGlobalCache.java | 全局缓存一致性路径零测试覆盖 |

统计：P0=0，P1=0，P2=2，P3=8，共 10 条。

## 子项复核结论

复核人：独立复核代理 R4（2026-09-30）

| 发现编号 | 判定 | 复核说明 |
|---|---|---|
| [G4-09-01] | 保留（维持 P2）| 逐处核对 6 个点名位点：`SqlLibManager.java:252`（IAE `nop.err.orm.validate-input-result-not-map`）、`XmlOrmComponent.java:156`（IAE `nop.err.invalid-child-name:`+childName）、`XplOrmInterceptor.java:73`（IAE `nop.err.orm.unsupported-event:`+event）、`DelegateInvocationHandler.java:34`（ISE `nop.err.ioc.bean-proxy-not-initialized:`+method）四处行号与消息逐字相符；对 4 个码在 nop-persistence + nop-core-framework 全树反向 grep（java/yaml，排除 throw 语句自身）均零命中——`OrmErrors.java`/`IocErrors.java` 存在且无对应 define，"半迁移伪错误码"成立。校正一处定性偏差：其余两点 `DaoQueryHelper.java:127`（`"null query"`）与 `JdbcTemplateImpl.java:593`（`"unsupported exists template param:"`+name）为英文自然语言裸 IAE，并非错误码格式字符串——标题"以裸 IAE/ISE 抛出错误码格式字符串"严格说只覆盖 4/6 处，但该集群的立案基础（框架核心模块已有 Errors 体系却绕过两档策略）对 6 处全部成立，两条非伪码位点同属 nop-orm/nop-dao 框架核心裸异常，合并立案不影响 P2 判级。|
| [G4-14-01] | 保留（维持 P2）| 逐行核对 `BeanScopeImpl.java`：:29 确为 `ConcurrentHashMap<String, ProducedBeanInstance>`；`close()`（:96-118）确对 `beans.entrySet()` 直接遍历并经 `remove()` → `container.destroyBean`（:81-87）销毁，顺序为哈希桶序，与创建序/依赖序无关。销毁路径封闭性核实：grep nop-ioc main 全部 `destroyBean` 调用点，唯一入口即 BeanScopeImpl.remove，无任何其他逆序销毁补偿。对照侧核实：`BeanContainerImpl.start()`（:489）确按 `orderedBeans`（BeanTopologySorter 拓扑序，:98）创建；`stop()`（:595-617）确只调 `singletonScope.close()`（:607），未使用 orderedBeans 逆序。依赖 bean（连接池/executor/sessionFactory 类）先于依赖者销毁的 shutdown 风险推论成立，与 Spring 逆创建序契约相悖属实。影响限于关闭期（清理不彻底/关闭期报错），不破坏正常运行，P2 恰当。|
