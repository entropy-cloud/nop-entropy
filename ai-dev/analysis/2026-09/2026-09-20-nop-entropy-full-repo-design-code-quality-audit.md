# nop-entropy 全仓库设计与代码质量深度审核

> Status: resolved
> Date: 2026-09-20
> Scope: 全仓库（447 个 Maven 模块、43 个顶层分组、约 105 万行手写 main 代码）——架构与模块边界、核心引擎（nop-kernel）、服务框架与持久化、业务模块与安全（nop-auth/wf/job/task）、工程质量横向、测试体系、新子系统（nop-stream/nop-ai/nop-code/nop-rg/nop-graph）
> Conclusion: 工程质量显著高于同类开源框架平均水平，架构分层纪律近乎典范（447 个 pom 四类硬性分层规则零违规），SQL 注入防线扎实，未发现 P0 级缺陷；核心风险集中在 5 项 P1——动作/数据权限引擎默认关闭、字段级权限缺省放行、deleteByQuery/updateByQuery 静默截断、多方言 SQL 测试未进 CI、平台基座测试密度与基座地位不匹配——以及三个 P2 级结构性主题：认证体系的单机假设、移植代码的规范洼地、核心层上帝类聚集。

## Context

- 对 nop-entropy 全仓库做一次整体的设计与代码质量评估，回答两个问题：① 平台主张的"可逆计算/模型驱动"架构在代码中的真实落地程度；② 作为可替代 Spring 的全栈框架，其工程质量是否达到可承载生产使用的水平。
- 涉及全部顶层模块分组；历史单模块审计（nop-stream、nop-metadata、nop-ai 等）已有记录，本次为全仓库视角的横向综合。
- 本报告为 AI 单方面评估（analysis 定位），结论可被推翻；按项目审计方法论，修复应拆 plan 并引用本报告作为 baseline。

## 审计方法

- **7 个并行只读子代理**分维度深挖：①架构与依赖边界（全量 447 pom 机械扫描）②核心引擎代码质量 ③服务框架与持久化 ④业务模块与安全 ⑤全仓库工程纪律横向统计（ripgrep 全量 + 热点深读）⑥测试体系 ⑦新子系统（stream/ai/code/rg/graph）。
- **主代理亲自复核了全部承重结论**：权限默认值（`ApiConfigs.java:88,92`）、deleteByQuery 截断（`CrudBizModel.java:1477-1526`）、字段权限 fail-open（`engine/GraphQLActionAuthChecker.java:118-121`）、CI 未开方言测试开关（`.github/workflows/maven.yml:43`）。
- 未运行构建/测试/覆盖率（时间与上下文预算原因）；抽样深读约 130 个关键文件。除已复核项外，其余发现为单轮初审，标注"待验证"者需独立确认后才可立项修复。
- 生成产物（`_` 前缀、`_gen/`、ANTLR parser）与 demo 不作为审计对象；平台标准模式（BizModel 返回实体、`@Inject` protected 字段等）按误报校准排除。

## 量化基线

| 指标 | 数值 |
|---|---|
| Maven 模块（pom） | 447（根 reactor 43 个顶层分组） |
| 手写 main Java 文件 / LOC | 约 9,589 / 1,054,025（另生成代码 1,186 文件） |
| src/test Java 文件 | 3,268（test:main 文件比 ≈ 0.34） |
| xdef 元模型定义 | 602 个（23 个顶层分组；另 demo 88） |
| `_vfs` 目录 / `_delta` 目录 | 303 / 21（其中生产主资源仅 nop-job-worker 1 处） |
| 提交总数 / 近 3 个月 | 7,109 / 3,004 |
| 异常纪律 | throw 中 NopException/模块异常占比 ≈ 87%（约 4,900 处）；裸 RuntimeException 全库 4 处真实代码；中文错误消息 3 处真实代码 |
| 硬编码密钥 | 0 处真实命中 |
| 2,000+ 行手写核心类 | 4 个（StringHelper 4,932、XNode 2,829、ExecToJavaTranslator 2,656、TypeInferenceProcessor 2,096） |

## 一、架构与设计评估

### 1.1 模块组织与版本管理：优秀

- nop-bom 统一管理 245 个 nop 构件，对全部非聚合叶子模块 diff 验证**零缺失**；revision 统一 2.0.0-SNAPSHOT。
- 游离项极少且有理由：`tests/` 被注释出根 reactor（但 sonar 覆盖率聚合路径仍指向它，见 P2-T6）；nop-entropy-e2e 为独立 pnpm 工作区；demo/app-templates 为模板工程。
- JDK 门控（nop-utils java21-modules、nop-rg JDK22 profile）与文档声明一致。

### 1.2 分层纪律：近乎典范

对全部 447 个 pom 做四类机械扫描，**硬性违规为零**：

| 规则 | 结果 |
|---|---|
| `*-api` 依赖 service/dao/web/app | 0 处 |
| `*-web` 依赖任何 `*-dao` | 0 处 |
| `*-core`/`*-dao` 依赖 service/web/app | 0 处 |
| Quarkus/Spring 泄漏到框架层 | 0 处（仅存在于 app/nop-spring/nop-quarkus/集成模块/benchmark） |

nop-auth-service 对 nop-ai 模块的依赖全部为 test scope 且带 exclusions 与注释（`nop-auth/nop-auth-service/pom.xml:115-155`），说明边界是有意识维护的。灰色偏差集中在两类：跨模块 service→dao 直连 5 处（nop-auth-service→nop-file-dao、nop-ai-service→nop-sys-dao、nop-task-service/ext→nop-sys-dao、nop-ai-gateway→nop-auth-dao），以及 nop-sys-dao 在 dao 层引入 nop-cluster-core/nop-message-core（`nop-sys/nop-sys-dao/pom.xml:26-34`）。

### 1.3 可逆计算主张的落地程度：机制真实，第一方消费面偏窄

- **XDSL/xdef 元数据驱动是真实的**：602 个 xdef 定义覆盖 23 个分组（kernel 135、ai 102、credential 59、auth 43……），不是口号。
- **_vfs 虚拟文件系统是运行时底座**：303 个 `_vfs` 目录 + `VirtualFileSystem`/`IVirtualFileSystem`/`DeltaJsonLoader`（`nop-kernel/nop-core/.../resource/`）。
- **Delta 差量合并引擎完整**：`nop-kernel/nop-xlang/.../delta/` 下 DeltaMerger/DeltaDiffer 完整且有测试；但仓库内 21 个 `_delta` 目录中 13 个在 test 资源、5 个在 demo/模板，**生产主资源仅 nop-job-worker 一处**。"系统级差量化"的兑现更多依赖下游应用工程（经 nop-cli 创建），第一方代码自身消费面窄——这是"平台能力"与"平台自用"之间的落差，非缺陷（待验证：需检查基于 nop-cli 的下游工程才能量化）。

## 二、代码质量评估

### 2.1 核心引擎（nop-kernel）：高于同类开源框架平均水平

- **并发设计是突出亮点**：`ReflectionManager` 显式规避 CHM computeIfAbsent 嵌套修改（`ReflectionManager.java:116-120` 有因果注释）；`ExecutorHelper` 所有提交路径 finally 统一 `detachContext()` 清理 ThreadLocal；`XNodeParser` 实体白名单（无 XXE）+ 最大嵌套深度保护（`XNodeParser.java:660`）。
- 异常体系高度统一：nop-xlang 主源码 NopException 201 处 vs 裸 RuntimeException 18 处；正则统一经 RegexHelper 缓存编译；资源关闭全库一致。
- **澄清一个易误判点**：`XLangASTOptimizer.java`（3,028 行）带 `//__XGEN_FORCE_OVERRIDE__` 标记，是 XGEN 生成产物而非手写上帝类（但它与手写类同目录且无 `_` 前缀，见 P3 清单）。真正的手写大文件分层是清晰的（visitor 模式、每节点一个 process 方法，"大而不散"）。
- 主要风险：`XNode.freeze()` 只包装 children 不包装 attributes，且 `attrValueLocs()`/`getAttrNames()` 直接暴露内部可变引用（`XNode.java:289-307,739-741`）——进缓存的 frozen 共享模型存在被外部改写的窗口。

### 2.2 服务框架与持久化：设计扎实，默认姿态偏松

- **SQL 注入防线扎实（正面确认）**：所有名称拼接点有 `isValidPropPath`/`isValidSimpleVarName` 白名单（`DaoQueryHelper.java:102-123`）；filter 值全部走 PreparedStatement 参数；EQL 字面量 `escapeSql`；未知列编译期报错。未发现可利用注入路径。
- **GraphQL DoS 防护真实存在**：深度限制（默认 7，含 fragment 链）、操作数/directive/解析长度限制、内省默认关闭。
- **事务边界三层职责分离正确**：mutation 由 `GraphQLTransactionOperationInvoker` 包单事务、session 由 `SingleSessionFunctionInvoker` 包整请求、`OrmTransactionListener` commit 前 flush。
- ORM 层乐观锁（version 递增 + 懒加载版本一致性检查）、租户校验、逻辑删除、分片均有实现且边界意识强。
- 短板在**默认值**：见 P1-1/P1-2 与 P2-T1。

### 2.3 业务模块（nop-auth/wf/job/task）：安全加固接近生产级，样板有负面示范

- 认证链路纵深：BCrypt(SHA256(password+salt)) 复合编码、登录失败锁定（默认 10 次）、短信/邮件三层限流、MFA 五因子（TOTP/SMS/Email/WebAuthn/恢复码）、改密后吊销全部会话、JWT 按 access/refresh/code 三用途独立 KID 派生。
- 授权 **deny-by-default 已确认闭环**：未声明 `@Auth` 的方法自动生成权限要求（`ReflectionBizModelBuilder.java:363-366`），未映射权限返回 false。四模块内联 SQL 全部参数绑定。
- nop-job 用乐观锁抢占调度计划 + CAS 认领 + misfire 策略；nop-task 有 continuation-skip 与 persistVars 恢复——分布式一致性做得扎实。
- 代码中大量 plan 编号注释与多轮审计痕迹，已知竞态（恢复码双花、TOTP 回滚等）均有针对性修复。
- **主要问题**：认证防线全部是单机本地实现（见 P2-T2）；`NopAuthUserBizModel` 2,068 行把 MFA 全部领域逻辑堆进实体 BizModel（见 P2-T3）。

### 2.4 新子系统（stream/ai/code/rg/graph）：成熟度远高于典型 WIP

- 真实 TODO/FIXME 为零；所有 `UnsupportedOperationException` 是文档化 fail-loud 契约。
- 测试密度极高（nop-stream 637 test/713 main；nop-ai-agent 432/535）；plan 驱动开发痕迹遍布，修复叙事注释本身即质量证据。
- nop-stream 的 exactly-one 链路抽查完整（barrier 对齐 + 2PC + checkpoint id 单调推进 + 拓扑指纹 fast-fail）；nop-rg 的 FFM Arena 用法与 SIMD 边界推导正确；nop-ai 的可靠性栈（错误四分类 + 重试 + 熔断 + failover）正交分层。
- 与平台核心集成良好：复用 nop-cluster 选举、VFS 资源加载、ErrorCode 体系，未自造轮子。
- WIP 边界显式声明：K8s/YARN 编排与 HPA 未实现（README 明示）；nop-ai-rag 是唯一空壳模块。

### 2.5 横向工程纪律：规范执行是真实的而非纸面的

- 异常占比 87% 合规；空 catch 仅 32 处且多为 expected 模式；`Thread.sleep` 抽读全部为有界退避设计；`new Thread` 全部走命名守护 ThreadFactory；未发现真实资源泄漏（5 处非 TWR 候选深读全部正确关闭）。
- **系统性洼地：移植第三方代码**。IAE/ISE 合计 746 处中约 41% 来自 nop-treesitter（132）、nop-format（131，POI/tabula 衍生）、nop-stream（Flink 移植，也贡献了 unchecked 抑制的绝大部分）、nop-benchmark。这些模块同时是 System.out/printStackTrace 的集中地。

## 三、安全专项

### 已验证的强项

- SQL 注入：名称白名单 + 全参数化，未发现可利用路径（2.2 节）。
- 授权：动作级 deny-by-default 闭环；密码哈希/salt 在 xmeta `published=false` + `not-pub` tagSet（GraphQL 不可选）。
- XXE：XNodeParser 实体白名单 + 深度限制。
- nop-ai Bash 工具：fail-closed（无后端即拒绝）、工作目录 jail（`..` 拒绝 + realpath + 白名单）、危险环境变量黑名单。
- 逻辑删除数据读取/恢复入口 fail-closed（`CrudBizModel.java:1017-1023`）。

### 风险（按主题）

- **默认姿态 fail-open 集合**（P1-1/P1-2 + P2-T1）：权限引擎开关默认关、字段 auth 缺省放行、无 xmeta 的 biz 对象跳过查询防御、匿名请求在 dataAuth 开启时绕过行级过滤（`AuthHelper.java:29`）、未映射异常默认对外公开 params（`CoreConfigs.java:225-226`，且 `checkMetaFilter` 抛错时 `.param(ACTION_ARG_ENTITY, entity)` 挂整个实体——错误响应可绕过 selection 泄露字段，待验证实际序列化形态）。
- **单机假设 vs 集群部署错位**（P2-T2）：防爆破状态全本地、JWT 密钥缺省每实例随机。
- 管理员重置密码无 must-change 标志（`NopAuthUserBizModel.java:1991-2007`）；wf 引擎层旁路方法不鉴权仅靠 javadoc 约定（`IWorkflowStep.java:146`）；wf action 全量参数落 INFO 日志（`WorkflowEngineImpl.java:1203`）。

## 四、测试体系评估

- **三层分工清晰**：BaseTestCase 纯逻辑（约 188 类）→ JunitBaseTestCase IoC+H2（约 298 类）→ JunitAutoTestCase 快照（约 66 类）+ Playwright E2E（12 spec/约 80 用例）+ ArchUnit 不变式门禁。文档与实现高度一致。
- **测试是缺陷驱动生长的**：大量测试注释引用具体缺陷编号（如 `TestGenSqlDefects.java:33-42`）；855 个测试类使用 `assertThrows`；抽读测试绝大多数是行为断言式（断言 SQL 文本、errorCode、权限计数），非"只断言不抛异常"。
- **AutoTest 快照机制优于常见 golden-file 方案**：每方法重启 IoC + 全新 H2 内存库（无顺序依赖）；`ignoreNullUnknown` 只放过多余 null 字段。弱化点：`@var:` 自引用使 token/ID 类字段事实不断言；单方法重录即新基线、无强制 review。
- **四个高危路径均有直接测试**：ORM SQL 生成（断言 SQL 文本+绑定值）、GraphQL 字段权限（`TestCheckFieldAuth`）、登录链路（防爆破/MFA 数十个）、CrudBizModel 事务回滚。
- **密度倒挂**：业务层 0.4-0.9 vs 平台基座 0.09-0.11（nop-core 758/65、nop-xlang 895/96）——被全部 448 模块依赖的层测试最薄（P1-5）。nop-excel 332/11（外部文件解析面）同样偏低。

## 五、问题清单

> 严重程度：P0=错误行为/安全违约/数据损坏（本次 0 项）；P1=高概率回归/契约漂移/误导开发的公开面问题；P2=真实维护成本或局部缺陷；P3=低优先级。

### P1（5 项）

1. **动作权限与数据权限引擎默认关闭** — `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/ApiConfigs.java:88,92`（`enable-action-auth`/`enable-data-auth` 缺省 false）+ `nop-biz/src/main/resources/_vfs/nop/biz/beans/biz-defaults.beans.xml:13-16`。关闭时 `GraphQLActionAuthChecker.check` 因 checker==null 直接跳过（`engine/GraphQLActionAuthChecker.java:37-39`），CrudBizModel 所有 dataAuth 检查静默放行——未显式配置的应用等于无权限体系。**主代理已复核。**
2. **字段级读权限缺省放行（fail-open）** — `engine/GraphQLActionAuthChecker.java:118-121`（`auth == null` 返回 true）+ `nop-xlang/.../xmeta/impl/ObjPropMetaImpl.java:31-57`（无 auth 配置返回 null）。敏感列只要不在 xmeta 配 auth 即对所有可访问用户可见——字段级权限是 opt-in 而非默认拒绝。**主代理已复核。**
3. **deleteByQuery/updateByQuery 静默截断** — `nop-biz/.../crud/CrudBizModel.java:1477-1526`。受 maxPageSize（默认 1,000）封顶后仅 `LOG.warn`，剩余行不处理且返回值误导（返回的是已处理数而非命中数）；并发插入新行时同样漏处理。语义上"按条件全部删除/更新"，实际部分执行。**主代理已复核。**
4. **多方言 SQL 数据库级测试未进 CI** — `nop-persistence/nop-dao/src/test/java/io/nop/dao/dialect/TestMySQLDialect.java:17` 等（`@EnabledIfSystemProperty(named="nop.test.docker.enabled")`）+ `.github/workflows/maven.yml:43` 只跑 `mvn -B package` 未传开关。MySQL/PG/Oracle 方言回归只能由最终用户环境暴露。**主代理已复核。**
5. **平台基座测试密度与基座地位不匹配** — nop-core 758/65、nop-xlang 895/96、nop-commons 403/40、nop-api-core 322/33（main/test 文件比约 0.09-0.11）。XLang 编译器/反射核心/资源缓存被全部模块依赖而测试最薄；并非无测试（抽读质量好），但复杂求值路径分支覆盖存疑（待验证：需跑覆盖率）。

### P2（按主题分组）

**T1 fail-open 默认姿态（与 P1-1/2 同根，独立可修的项）**

- 批量入参无上限 → 超大单事务/巨型 IN — `CrudBizModel.java:1028,1324,1352`（filter 内 IN 有 100 上限但 ids 直传不受限；GraphQLArgumentValidator 无大小限制）。
- 无 xmeta 的 biz 对象跳过全部查询防御 — `CrudBizModel.java:430-431`（filter 操作符白名单/orderBy/IN/join 上限全失效）。
- 错误响应可能携带完整实体 params — `CrudBizModel.java:974-976` + `nop-core/.../exceptions/ErrorMessageManager.java:226` + `CoreConfigs.java:225-226`（未映射异常默认 public；实体 JSON 序列化不经 selection，待验证形态）。
- dataAuth 开启时匿名请求完全绕过行级过滤 — `nop-biz-auth-api/.../utils/AuthHelper.java:29`。

**T2 认证单机假设（集群部署陷阱）**

- 防爆破状态全本地：`loginFailCountLock` JVM 内 synchronized、失败计数存 LocalUserContextCache、限流本地 Caffeine — `LoginServiceImpl.java:254,857-866` + `auth-core-defaults.beans.xml:46-48`。N 实例暴破预算放大 N 倍。
- JWT encKey 缺省空 → 每实例随机派生 — `auth-core-defaults.beans.xml:11` + `JwtAuthTokenProvider.java:91-94`。负载均衡下跨节点校验失败、重启全员掉线；且诱导填弱共享密钥。
- 登录用户定位无租户消歧 — `LoginServiceImpl.java:1394-1459`（`findFirstByExample` 无租户上下文，`nop_auth_user` 为 `no-tenant` 全局表）——同 userName 跨租户重复时串登风险（待验证）。

**T3 上帝类与职责过重**

- `NopAuthUserBizModel` 2,068 行/13 个注入：MFA 绑定/确认/解绑、WebAuthn 三 ceremony、TOTP 防爆破、admin 管理全在一个实体 BizModel — 作为官方样板直接示范"领域逻辑堆进实体 CRUD"。
- `StringHelper` 4,932 行约 300 个静态方法，且 nop-commons 继承 nop-api-core 的 ApiStringHelper 形成反向耦合（`StringHelper.java:66`）——全库级影响面。
- `ExecToJavaTranslator.genValueExpr` 约 550 行单方法 if-else 分派链（`ExecToJavaTranslator.java:487-1036`）。
- `resetUserPassword` 管理员设明文新密码无 must-change 标志 — `NopAuthUserBizModel.java:1991-2007`（对照 `changeSelfPassword:2013-2033` 完备，差异即缺口）。

**T4 移植代码规范洼地**

- nop-treesitter/nop-format/nop-stream/nop-benchmark 贡献约 41% 的 IAE/ISE、大部分 unchecked 抑制与 System.out。
- 公共批量工具中文消息 + 裸 IAE 双违规 — `nop-batch-core/.../RangeSplitUtils.java:122,151,194`（全库仅存 3 处真实中文错误消息）。
- SSL 初始化路径 IAE 携带错误码字符串 — `nop-netty/.../NettySslEngineFactory.java:40,46,51`。
- 库热路径无条件 stdout — `nop-pdf/.../tabula/ProjectionProfile.java:110` + 同文件群唯一真实 printStackTrace（`NurminenDetectionAlgorithm.java:128`）。

**T5 并发与资源缺口**

- wf 并发推进同一实例无显式互斥，仅靠实体乐观锁兜底（后提交者失败，无重试语义，对照 nop-job 的 `updateWithRetry(5)`）— `DefaultWorkflowExecutor.java:31-49`。
- 异步 ORM session 关闭竞态窗口 — `OrmTemplateImpl.java:255-268`（跨线程误用时泄漏，待验证触发条件）。
- `ResourceTenantManager` 懒初始化无同步 + 配置快照永不刷新 — `ResourceTenantManager.java:100-111`。
- `CodeIndexService.indexLocks` 只增不减 — `CodeIndexService.java:113`。
- `XNode.freeze()` 不包装 attributes 且访问器暴露 live 引用 — `XNode.java:289-307,739-741`。

**T6 其他**

- nop-sys-dao 在 dao 层引入 cluster/message 框架 — `nop-sys/nop-sys-dao/pom.xml:26-34`；跨组 service→dao 直连 5 处（1.2 节）。
- `tests/` 游离根 reactor 但 sonar 覆盖率路径仍指向它 — 根 `pom.xml:36-38,559`。
- nop-ai-rag 空壳模块挂在默认构建 — `nop-ai/nop-ai-rag/`（仅 pom+README）。
- nop-graph 14 个算法类仅 3 个测试文件；nop-orm-eql 221 main/8 test（行为测试寄居 nop-orm）。
- 快照 `@var:` 屏蔽 token/ID 断言 + 单方法重录即新基线无 review 门禁 — `JunitAutoTestCase.java:109-116`。
- K8s/YARN 编排与 HPA 未实现（README 声明的 WIP 边界）；`DefaultAgentEngine` Builder 40+ 选项、安全组件全 NoOp 默认（有 `AgentStartupWarnings` 缓解）。

### P3（摘要，代表性项）

- 版本管理：3 处 pom 绕过 BOM 硬编码 SNAPSHOT 版本（nop-sys-dao、nop-auth-dao、nop-code-service→nop-graph-core）。
- 依赖卫生：nop-commons 携带 logback-classic compile scope 进库类路径（库绑定具体日志实现反模式；真正的零依赖底座是 nop-api-core）；nop-commons 字节码 15 与全局 17 混杂无注释；benchmark 钉死 EOL Spring 5.2.8。
- 错误码纪律：nop-auth 45 处复用 `ERR_AUTH_INVALID_LOGIN_REQUEST` + `param("msg", 英文串)` 绕过 i18n；wf `WorkflowServiceImpl.java:279-282` 硬编码角色字符串与常量体系并存。
- 认证细节：密码 xmeta maxLength=20 限制 passphrase（`NopAuthUser.xmeta:9-11`）；nop/123 缺省用户种子（开关默认关）。
- 可观测性：wf action 全量 args 落 INFO（PII 暴露面）。
- 语义陷阱：`notRollback` 异常部分提交（`TransactionTemplateImpl.java:237-247`）；逻辑删除行不参与唯一性检查（"一活一删"重复）；like 值未转义 `%`/`_`；用户 filter 值以 `@biz:` 开头误触解析异常。
- 工程细节：XGEN 生成文件（`XLangASTOptimizer.java` 等）无 `_` 前缀与手写混放；nop-code/nop-graph 缺编号 `<name>`；`JobCoordinator.stop()` javadoc 与实现不符（无注销）；SIMD ignoreCase 仅 ASCII 折叠；HostBashSandbox 不执行 CPU/内存/网络限制（文档已声明）；IAiChatService 双管线并存（受控迁移）；`DateHelper.formatTimestamp` 每次 new SimpleDateFormat。

## 六、改进建议（按优先级）

1. **[立即] 权限默认姿态翻转评估**：将 `enable-action-auth`/`enable-data-auth` 缺省改 true（或至少在 nop-demo/app-templates 与文档中默认开启 + 应用启动时检测未开启则 WARN）；字段级 auth 对敏感域（password/secret/token 类 domain）缺省拒绝。这是把"平台能力"变成"平台默认安全"的最大杠杆。
2. **[立即] deleteByQuery/updateByQuery 语义修复**：循环分页处理直至清空，或截断时抛错强制调用方显式确认；同步修复返回值语义（命中数 vs 已处理数）。
3. **[短期] 认证分布式化**：为 loginFailCountLock/限流器提供 cache-backed 实现（nop-cluster/nop-sys 分布式锁设施已存在）；JWT encKey 为空且非单机模式时启动 fail-fast。
4. **[短期] CI 补方言门禁**：workflow 增加 testcontainers/service-container job 启用 `nop.test.docker.enabled`。
5. **[中期] 平台基座测试补强**：nop-core/nop-xlang 关键路径（XNode 解析边界、编译器求值、缓存失效）定向补测；跑一次 JaCoCo 覆盖率把"待验证"变实数。
6. **[中期] 移植模块规范隔离**：为 treesitter/format/stream/benchmark 建独立 checkstyle 规则集（豁免而非稀释主仓规则）；顺手修 RangeSplitUtils 中文 IAE、NettySslEngineFactory 错误码、tabula stdout（三者均为小时级工作量）。
7. **[中期] 上帝类治理**：`NopAuthUserBizModel` 拆出 MFA 领域服务（WfUserMfaService 之类）；`StringHelper` 按域拆分（保持门面兼容）。
8. **[长期] 安全细节收敛**：ErrorBean.params 脱敏/白名单化；`XNode.freeze()` 完整包装；wf 引擎旁路方法补鉴权；管理员重置密码加 must-change。

## Conclusion

- **总评**：nop-entropy 的工程成熟度显著高于同类开源框架平均水平，且明显高于一般"平台型开源"的纪律水准——447 个模块的分层纪律零硬性违规、异常/错误码/i18n/资源管理纪律真实执行、测试缺陷驱动生长、新子系统带 plan 痕迹的多轮审计修复。可逆计算主张（602 xdef + VFS + Delta 引擎）在机制层真实完整，第一方 delta 消费面偏窄属于"能力 vs 自用"落差而非缺陷。
- **核心风险不在代码正确性，而在默认姿态与部署假设**：权限体系默认关闭 + 字段权限 opt-in + 认证防线单机实现，意味着"开箱即用"的默认配置在多实例生产环境下安全强度会静默降级。这是 5 项 P1 中 3 项的共同根因，也是与"可替代 Spring"定位之间最需要对齐的差距（Spring Security 默认也是需显式配置，但 nop 的文档主张了字段级权限能力，缺省姿态应与主张一致）。
- **被否决的观点**："核心引擎存在手写上帝类失控"——XLangASTOptimizer 等 3,000+ 行文件实为 XGEN 生成产物，真正的手写大类（StringHelper/XNode/TypeInferenceProcessor）是 visitor/工具门面结构，内聚度可接受，属 P2 维护成本而非设计失控。
- **后续工作**：本报告 P1 项建议拆 plan（`ai-dev/plans/`）逐项修复并引用本报告为 baseline；权限默认姿态翻转涉及跨模块公共 API 行为变更，属 plan-first 区域，需先过 plan audit。

## Open Questions

- [ ] 多租户登录消歧：租户列过滤在登录路径是否实际生效（P2-T2 第 3 条，需构造跨租户同 userName 用例验证）
- [ ] ErrorBean.params 中 IOrmEntity 的实际 JSON 序列化形态（决定 P2-T1 第 3 条的实际泄露面）
- [ ] nop-commons 字节码 15 是否为兼容性考虑（有无消费者依赖）
- [ ] Delta 机制下游使用广度（需检查 nop-cli 生成的应用工程）
- [ ] `OrmTemplateImpl.runInNewSessionAsync` session 泄漏的实际触发条件（跨线程误用场景是否存在调用方）
- [ ] nop-kernel 分支覆盖率的真实数值（JaCoCo 未跑，P1-5 目前基于文件比推断）

## 盲区自评

- 未运行构建/测试/覆盖率，所有"测试通过率"结论来自 CI 配置与测试代码静态审查。
- 抽样深读约 130 文件 / 全库 9,589，覆盖率约 1.4%；未覆盖模块（nop-report/nop-datav/nop-dyn/nop-file/nop-rule/nop-batch DSL 层/nop-integration/nop-quarkus/nop-spring 细节、nop-migration、nop-cluster 细节）只有统计层结论。
- 7 个子代理中除主代理复核的 4 项外，其余发现为单轮初审，未经完整独立复核流程（符合 analysis 定位；立项修复前应按 deep-audit 方法论补复核）。
- 安全审计为代码审查 + 设计推断，未做动态验证/fuzz；GraphQL 深度限制等数值的合理性未做压测评估。

## References

- `docs-for-ai/01-repo-map/module-groups.md`（模块分组基线）
- `ai-dev/skills/deep-audit-prompts.md`（严重程度判级与误报校准方法论）
- 关键证据文件：`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/ApiConfigs.java`、`nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/CrudBizModel.java`、`nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/engine/GraphQLActionAuthChecker.java`、`nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/LoginServiceImpl.java`、`.github/workflows/maven.yml`
