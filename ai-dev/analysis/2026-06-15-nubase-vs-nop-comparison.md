# Nubase vs Nop：AI 全栈框架的范式对比

> Status: open
> Date: 2026-06-15
> Scope: Nubase (`~/ai/Nubase`, Spring Boot 3.2, 493 Java 文件) vs nop-entropy (自研 IoC + XLang + codegen, 20+ nop-ai 子模块, nop-dyn 运行时模型引擎)
> Conclusion: 两者在"AI 全栈"的表层相似之下，自动化的是软件生产的不同环节，核心接口和开发流程截然不同

---

## 1. Context

- **触发原因**：用户指出我上一版分析对 Nop 的理解不够深入，轻信文档而非阅读实际代码。同时指出 Nop 也支持 AI 自动化生产，是从模型设计到运行时脚本执行的全栈框架。要求基于**具体场景、完整开发流程、核心能力与接口**重新对比。
- **分析方法**：本报告直接读源码。Nop 侧深度挖掘了 `nop-biz/CrudBizModel`（事务、xbiz）、`nop-dyn`（运行时动态模型）、`nop-ai` 全子模块（MCP/CodeGen/Agent/Shell/Tools）、`nop-codegen`/`nop-xlang`（生成管线）、`nop-ioc`（IoC 容器）；Nubase 侧基于第一版 analysis 的数据（4 个 subagent 同步深挖）。
- **对比框架**：从**具体开发流程**出发——如果我是一个 AI agent，要完成一个全栈需求，在 Nop 上和在 Nubase 上分别要怎么做。比什么"功能列表"更有意义。

---

## 2. Executive Summary

**核心发现**：Nop 和 Nubase 的"AI 自动化"作用于**软件生产的不同环节**，两者有本质差异但也有深层互补。

- **Nop 的 AI 自动化是"模型驱动"的**。AI agent 读写结构化模型（`NopDynEntityMeta` 元数据 / `.orm.xml` / `.xbiz` 脚本 / `.xmeta` / `.view.xml`），平台根据模型自动派生 GraphQL API + 页面 + 验证规则。**Nop 同时支持 codegen 模式（mvn install）和 nop-dyn 在线模式（运行时即时生效，无重启）**。业务逻辑在 JVM 内以 xbiz XPL 脚本执行，零开销、可覆盖 Java。关键接口：`DynCodeGen`、`IBizObject`、`IEvalAction`、`IXDefinition`、`IGraphQLEngine`。
- **Nubase 的 AI 自动化是"资源直接操作"的**。AI agent 通过 MCP 工具直接操作运行时资源——SQL DDL 建表、edge function 代码部署、静态资产上传、cron 配置、memory 写入。平台是这些资源的容器，schema 由 PG catalog 提供。业务逻辑在独立 sandbox（edge function）以 TS/JS 执行，通过 HTTP 与平台通信。关键接口：`@Tool` + `SqlExecutionService` + `EdgeFunctionExecutor` + `MemoryService`。

**两者不是竞品**。两者都支持在线定义、即时生效，但选择了不同的抽象层次：
- **Nop 选择"高抽象"**：AI 改模型，平台生成其余（API/页面/验证/字典联动）——元数据丰富，但须遵循平台模型规范。
- **Nubase 选择"低抽象"**：AI 直接改 SQL/代码/资产——灵活自由，但字段验证/表单/字典联动需手写。

**一个具体的应用开发流程对比**（20 分钟即可读懂的对比骨架）：

| 步骤 | 在 Nop 上 | 在 Nubase 上 |
|------|-----------|-------------|
| 1. 定义数据模型 | 写 `*.orm.xml`（codegen 模式）**或** 在线 nop-dyn 改 `NopDynEntityMeta`（运行时模式） | 通过 `sql_execute` MCP 工具直接创建 PG 表 + RLS 策略 |
| 2. 暴露 API | 自动：CrudBizModel + GraphQL（无论 codegen 还是 nop-dyn 都自动派生） | 自动：每张表自动获得 PostgREST 风格 `rest/v1/<table>` |
| 3. 写业务逻辑 | xbiz 脚本（XPL，JVM 内执行，可覆盖 Java）或 Java BizModel 方法 | edge function（TS/JS，独立 sandbox，通过 `functions_deploy` 部署） |
| 4. 前端 | Nop 页面 DSL（`view.xml`/`page.yaml`，自动生成 + Delta 定制） | 独立 HTML/CSS/JS 通过 `assets_upload` 发布到 CDN |
| 5. 认证/权限 | `nop-auth` + 数据权限 + 菜单权限（三层模型） | JWT + RLS（行级安全） |
| 6. 上线 | codegen 模式：Maven 打包 + JVM；**nop-dyn 模式：无部署步骤，运行时即时生效** | 平台已运行，MCP 操作即时生效 |
| 7. AI 辅助 | AI 通过 `AiFileTool` 读写 DSL / 调 GraphQL 改 nop-dyn 元数据 / 写 xbiz 脚本 | AI 通过 MCP 操作平台建表/部署/记忆 |

---

## 3. Nop 的核心能力图谱（从源码出发，纠正上一版错误）

### 3.1 Nop 的三层 AI 自动化管线（上一版只看到了第一层）

#### 第一层：模型驱动代码生成（Codegen）
- **入口**：`CodeGenTask.main()` → `XCodeGenerator(tplRootPath, targetRootPath).execute()`
- **核心接口**：`XCodeGenerator` 以 `XPL` 模板引擎驱动，目录中的 `{EntityName}` 模式自动生成迭代。
- **数据源**：`*.orm.xml` / `*.api.xml` / `*.orm.xlsx` 通过 `ResourceComponentManager` 加载 → `DslNodeLoader`（处理 `x:extends`/`x:gen-extends`）→ `DslModelParser` 解析为 Java 模型对象。
- **产出**：`_*.java`（Entity/DAO）、`_*.xmeta`（元数据）、`_*.view.xml`（页面）、`_app.orm.xml`（合并后 ORM 模型）。
- **AOP 源码生成**：`GenAopProxy` 在编译后扫描 `target/classes`，为带注解的 bean 生成 AOP 代理 Java 源码并再编译——这是源码级 AOP，**不是** Spring 的运行时 CGLIB 代理。

#### 第二层：运行时动态模型（nop-dyn）——重要，上一版我完全低估了
- **不是"在线代码生成"**，而是在内存中**构造运行时模型**，不需要重启进程。
- **核心架构**：
  - `DynCodeGen` implements `IDynamicEntityModelProvider`, `ITenantBizModelProvider`, `ITenantResourceProvider` —— 四个接口描述了动态模型的四种能力。
  - `InMemoryCodeCache`：每租户一个，持有生成的 `InMemoryResourceStore` + `GraphQLBizModel` + `OrmModel` 缓存。
  - `DynOrmModelProvider`：`IOrmModelProvider` 返回 `LazyLoadOrmModel` —— 基础实体是静态的，动态实体在首次访问时延迟解析。
  - `DynResourceStore`：VFS 资源查找的装饰器——当请求一个 `.xmeta`/`.xbiz`/`.view.xml` 路径时，如果不存在，则**即时生成**。
- **两种存储模式**（`NopDynEntityMeta.storeType`）：
  - `VIRTUAL`（默认）：所有动态实体共享 `nop_dyn_entity` 宽表（8 个泛用类型列 + key-value 扩展子表 `NopDynEntityExt`）。
  - `REAL`：每实体一张物理表，`DynamicOrmEntity` 作为实体基类。
- **AI 触发闭环**：`NopDynModuleBizModel.generateByAI()` → `GptCodeGen.parseGptResponse()` → `OrmModelToDynEntityMeta` 转换 → `DynCodeGen.generateForModule()` → 运行时生效。

#### 第三层：xbiz 脚本运行时（最重要的"在线 AI 操作"层）
- `.xbiz` 文件定义 `<query>`、`<mutation>`、`<action>`、`<loader>` 等业务操作。
- **脚本语言**：XPL（Nop 的 XML 模板语言），通过 `EvalServiceAction` 包装为 `IExecutableExpression`。
- **事务控制**：`TransactionActionDecoratorCollector` 检测：mutation 型操作默认 `transactional=true`（`BizConstants.BIZ_ACTION_TYPE_MUTATION.equals(actionModel.getType())`），query 型默认 `false`。可在 xbiz 中显式覆盖 `<txn transactional="false"/>`。
- **xbiz 覆盖 Java**：`BizObjectBuilder.buildBizObject()` 先加载 Java 注解派生的操作，再合并 xbiz 定义——xbiz 优先级更高。这意味着 AI 可以通过写 `.xbiz` 文件**覆盖任何 Java 方法的行为**。

### 3.2 AI Agent 框架（nop-ai-agent）—— Nubase 没有对等物

Nubase 的 "agent 操作平台" 模式是扁平的（MCP 工具调用），Nop 提供了一个**完整的 agent 框架**在平台内部。

- **ReAct 循环**：`ReActAgentExecutor.execute()` 驱动 LLM 推理 + 工具调用的迭代：
  1. Skills consultation（动态加载 skill 注入 system prompt）
  2. Talents consultation（注入代码感知/安全感知等能力）
  3. Model routing（`IModelRouter` 多模型路由）
  4. LLM call → LLM-turn checkpointing
  5. Completion judgement（`ICompletionJudge` 判断完成/继续/升级）
  6. Context compaction（三层压缩：微压缩 → 轮次修剪 → LLM 总结）
  7. Tool call dispatch（23+ 内置工具）

- **三层安全模型**：
  - 第一层：`IToolAccessChecker`（工具级）+ `IPermissionProvider`（权限）+ `IPathAccessChecker`（路径）
  - 第二层：`ISecurityLevelResolver` + `IPermissionMatrix`（安全级别 × 通道矩阵）
  - 第三层：`IApprovalGate`（人工审批）+ `IDenialLedger`（阈值暂停）+ `IPostDenialGuard`（盲目重试阻断）

- **Session 管理**：`ISessionStore`（in-memory / file-backed / DB）、`ICheckpointManager`（每 LLM 轮次 checkpointing）、fork/pause/resume。

- **MCP Server**（`nop-ai-mcp-server`）：`AiFileTool` 提供三个操作：`loadNopFile(path)`（读 DSL 文件，支持 XDef 指导的简化）、`saveNopFile(path, content, merge)`（写 DSL 文件，**支持 Delta 合并**）、`loadNopFileXDef(fileType)`（获取 XDef schema 供 AI 理解 DSL 结构）。

### 3.3 Nop 的核心接口体系

这是开发者在 Nop 平台上编程面对的关键抽象，也是 Nubase 没有的：

| 接口 | 包/模块 | 作用 |
|------|---------|------|
| `IBizObject` | nop-biz | 业务对象的运行时表示，封装操作+元数据+加载器 |
| `CrudBizModel<T>` | nop-biz | 实体 CRUD 的通用基类，所有业务实体服务继承它 |
| `IOrmEntity` | nop-orm | ORM 实体基接口 |
| `IGraphQLEngine` | nop-graphql-core | GraphQL 执行引擎 |
| `IXplCompiler` | nop-xlang | XPL 模板编译器 |
| `IEvalAction` | nop-xlang | 可执行表达式的接口 |
| `ITransactionTemplate` | nop-dao | 事务模板（`runInTransaction / runWithoutTransaction`） |
| `IDeltaResourceStore` | nop-core | Delta 资源存储（多层叠加 VFS） |
| `ICodeGenerator` | nop-codegen | 代码生成器 |
| `IOrmModelProvider` | nop-orm | ORM 模型提供者（nop-dyn 的关键切入点） |
| `IDynamicEntityModelProvider` | nop-dyn | 动态实体模型提供者 |
| `IGraphQLSchemaLoader` | nop-graphql-core | GraphQL schema 加载器（BizModel 自动注册的入口） |
| `IComponentModel` | nop-core | 组件模型基接口 |

### 3.4 Nop 上的完整开发流程（AI agent + 人类开发者）

**场景**：做一个"客户管理"模块，有客户实体、表单、列表、按条件查询。

**方式 A — 传统 codegen 模式**：

1. **定义模型**：写 `model/_Customer.orm.xml`（或 Excel XLSX），定义 `Customer` 实体、字段、关系。
2. **运行 codegen**：`mvn install` 或 `nop-cli gen` → `XCodeGenerator` 输出：
   - `_Customer.java`（实体）、`_CustomerDao.java`（DAO）
   - `_Customer.xmeta`（元数据）、`_Customer.view.xml`（默认页面）
3. **定制业务逻辑**：在手改层（非 `_` 前缀文件）写 `CustomerBizModel extends CrudBizModel<Customer>`，添加 `@BizQuery("findByGrade")`。
4. **定制页面**：写 `Customer.view.xml` 用 `x:extends="_Customer.view.xml"` 覆盖表单布局。
5. **部署**：Maven 打包 → JVM。

**方式 B — 在线动态模式（nop-dyn）**：

1. **运行时定义实体**：通过 GraphQL 调 `NopDynEntityMeta__save` 创建实体元数据，`NopDynPropMeta__save` 定义字段。
2. **发布模块**：调 `NopDynModule__publish` → `DynCodeGen.generateForModule()` → `InMemoryCodeCache` 即时生成 `.xmeta`/`.xbiz`/`.view.xml` → `VFS.updateInMemoryLayer()` 生效。
3. **运行时即有 CRUD**：自动获得 GraphQL `Customer__findPage`、`Customer__save` 等接口（由 CrudBizModel 派生到动态实体）。
4. **定制**：写 `xbiz`（AI 通过 MCP 写 XPL 脚本）+ 页面 DSL。
5. **无部署步骤**：所有改动在 DB 中，运行时立即生效。

**方式 C — AI 驱动模式**：

1. AI agent 通过 MCP 调 `AiFileTool.saveNopFile("model/Customer.orm.xml")` 写 ORM 模型（`merge=true` 时自动 Delta 合并）。
2. AI agent 触发 `NopDynModuleBizModel.generateByAI()` 或直接运行 codegen。
3. AI agent 调 `GraphQLToolProvider` 把 Nop GraphQL 操作暴露为 AI 工具，实现测试验证闭环。

### 3.5 Nubase 上没有的对等能力

| Nop 能力 | Nubase 上如何实现 |
|----------|-----------------|
| 模型驱动 codegen（ORM→Java→GraphQL→页面） | 无。Nubase 是手写 JPA Entity + Spring Controller + PostgREST 兼容层 |
| xbiz 脚本覆盖 Java 方法 | 无。只能用 edge function 替代，edge function 是外部 TS/JS，不在 JVM 内 |
| Delta 定制（`x:extends` 覆盖任意平台文件） | 无。只能通过 feature flag `nubase.<x>.enabled` 开关模块 |
| 多层安全模型（操作权限+数据权限+菜单） | 仅 RLS（行级）+ 三角色 |
| Agent Session 管理（checkpoint/fork/resume） | 无。agent 是无状态的 MCP 工具调用 |
| 源码级 AOP（无运行时开销） | Spring AOP（运行时 CGLIB 代理） |
| GraphQL API（自动从 BizModel 派生） | 仅 PostgREST REST |
| 工作流/规则/任务编排引擎 | 无。edge function + cron 只适合简单场景 |

---

## 4. Nubase 的核心能力图谱（源码层面）

### 4.1 Nubase 的 AI 自动化管线

与 Nop 不同，Nubase 的 AI 自动化不操作"开发环境"，而是操作"平台运行时"：

```
AI Agent → MCP (Spring AI @Tool) → Nubase Platform
                                      ├── Database: sql_execute → PG table creation + RLS
                                      ├── Auth: auth_* → user/session management
                                      ├── Functions: functions_deploy → edge function at /functions/v1
                                      ├── Assets: assets_upload → static CDN at /assets/v1
                                      ├── cron: cron_create → scheduled jobs
                                      ├── Memory: memory_write → fact extraction + hybrid retrieval
                                      ├── AI Gateway: gateway_* → LLM proxy + token tracking
                                      └── deploy_app: manifest-driven full-app deployment
```

### 4.2 Nubase 的核心接口

| 接口 | 位置 | 作用 |
|------|------|------|
| `@Tool` (Spring AI) | mcp/tools/ | MCP 工具注解，9 个工具类暴露 50+ 操作 |
| `EdgeFunctionExecutor` | functions/executor/ | 边缘函数执行器抽象（local / Cloudflare WfP） |
| `PostgrestRequestContext` | postgrest/auth/ | RLS 上下文管理（SET LOCAL ROLE + GUC 推送） |
| `SchemaCache` | postgrest/schema/ | 每 DB 实例的 schema 缓存（双缓冲 + LISTEN pgrst） |
| `RoutingDataSource` | postgrest/multidb/ | 多数据库连接路由（AbstractRoutingDataSource） |
| `MemoryService` | mem/service/ | 记忆写入/检索（事实抽取+混合检索） |
| `ScheduledJobRunner` | cron/service/ | 控制面 cron tick（CAS claim 无锁协调） |
| `SecurityConfig` | common/config/ | Spring Security 配置 |
| `MultiTenancyContext` | common/multitenancy/ | 租户上下文（ThreadLocal，贯穿请求生命周期） |
| `SqlRiskClassifier` | mcp/safety/ | SQL 风险分级（前缀匹配，仅标注不做阻断） |

### 4.3 Nubase 上的完整开发流程

**场景**：做一个"笔记"应用（todos 表 + 边缘函数 + 前端页面 + 定时任务）。

**Agent 驱动的流程**（`deploy-app` manifest）：

1. `/rest/v1/todos` 表的创建：`sql_execute` → DDL（CREATE TABLE + RLS policies）
2. Auth 用户管理：CLI 或 Studio 创建用户，前端 JWT 登录
3. 后端逻辑：`functions_new` scaffold → 写 TS/JS → `functions_deploy` → 在 `/functions/v1/summarize` 可用
4. 前端发布：`assets_upload` 把 HTML/CSS/JS 上传到 CDN → 返回 `publicUrl`
5. 定时任务：`cron_create` 设置 `nightly-digest` 每晚 7 点跑一次
6. 部署记录：`memory_write` 写入持久化记忆

**关键技术点**：
- 无 codegen 步骤——业务逻辑是 edge function（TS/JS），不是 Java。
- 无模型层——直接 SQL DDL 操作数据库。
- 无部署步骤——platform 已在运行中，所有操作即时生效。

### 4.4 Nop 上没有的对等能力

| Nubase 能力 | Nop 上如何实现 |
|-------------|---------------|
| 物理数据库级多项目隔离 | 共享 schema + delFlag 多租户（隔离强度不同） |
| 记忆（Memory）一等公民 | 需要自建（nop-ai 无平台级记忆原语） |
| AI Gateway（LLM 代理+token 计费+多供应商 failover） | **schema 有字段但 runtime 不写入**——`NopAiChatResponse` 表定义了 `model_id`+`prompt_tokens`+`completion_tokens`，`NopAiSession` 定义了 `cost`+6 个 token 分项列，但 agent runtime 只在内存维护单个 `totalTokensUsed` 标量（`ReActAgentExecutor.java:645-648`），**从不创建 `NopAiChatResponse` 行，从不更新 `NopAiSession.cost`**。多模型 session 的 per-model 计费完全未实现（`model-switched` 消息类型常量已定义但零引用）。详见 §7.1.4。 |
| 边缘函数（Cloudflare WfP dispatch） | 无。业务逻辑在 BizModel 或 xbiz 中<br>但 nop-ai-shell 提供了完整的 bash 沙箱 |
| 静态资源 CDN（发布 AI 生成的前端） | 无专门模块。前端打包进 JVM |
| 一键 deploy_app manifest | 无 manifest 级的部署管线<br>但有 `feature-implementation-checklist.md`（人工） |
| nubase_cli（npm MCP 桥） | nop-ai-mcp-server 直接是 backend MCP（不是 stdio 桥） |

---

## 5. 深度维度对比

### 5.1 AI 自动化的"对象"不同

这个对比是最核心的差异：

```
Nop:     AI → MODEL FILES (DSL) → codegen → app code → runtime
         AI agent writes abstract models, platform generates the rest
         
Nubase:  AI → PLATFORM API (MCP) → runtime → live app
         AI agent operates an existing platform, no code generation
```

具体来说：
- 在 Nop 上，AI agent 调用 `loadNopFile`/`saveNopFile` 读写 DSL 模型文件，然后触发 codegen。AI 生产的是**模型和生成物**。
- 在 Nubase 上，AI agent 调用 `sql_execute`/`functions_deploy`/`assets_upload` 直接操作运行时。AI 生产的是**已上线的服务**。

### 5.2 具体场景：用 AI 实现"客户反馈管理系统"

**需求**：客户提交反馈（content/category/contact），客服处理（status: pending→processing→resolved，handler 记录 reply），每天生成汇总，前端有列表+表单，客户只能看自己的反馈。

下面逐步对比两个平台**具体怎么做**、用哪些**核心接口**。**重点**：Nop 有 codegen 和 nop-dyn 两种模式，都能让实体在运行时变得可访问，不是只有"开发环境"一种路径。

---

#### 步骤 1：定义数据模型

##### Nop 路径 A —— 静态 codegen 模式（生产推荐）

**入口**：写 `model/CustomerFeedback.orm.xml`（或 `.orm.xlsx`），由 `ResourceComponentManager.instance().loadComponentModel()` 解析为 `OrmModel`。
- **核心元素**：`<entity name="CustomerFeedback" tableName="customer_feedback" className="...CustomerFeedback">`，内含 `<column>` 和 `<to-many>` 关系。
- **触发**：`mvn install` → `CodeGenTask.main()` → `XCodeGenerator.execute()` 遍历 `/nop/templates/orm/` 模板。
- **产出**：`_CustomerFeedback.java`（实体，继承 `_OrmEntity`）、`_CustomerFeedbackDao.java`、`_CustomerFeedback.xmeta`（字段元数据）、`_app.orm.xml`（合并模型）。
- **关键约束**：`_` 前缀文件**禁止手改**（`AGENTS.md` 硬规则）。

##### Nop 路径 B —— 在线 nop-dyn 模式（运行时即时生效，**无需重启**）

这是用户特别强调的能力——Nop **同时支持**在线定义。流程（源码追踪）：

1. **GraphQL 调用 `NopDynEntityMeta__save`**（`CrudBizModel.save()` `nop-biz/crud/CrudBizModel.java:525-553`）创建实体元数据：写入 `nop_dyn_entity_meta` + `nop_dyn_prop_meta` 表（字段定义）。
2. **发布模块**：调用 `NopDynModule__update` 把 `status` 改为 `MODULE_STATUS_PUBLISHED=10`（`_NopDynDaoConstants.java:24`）。
3. **触发即时生成**：`DynCodeGen.generateForModule(module)`（`nop-dyn-service/codegen/DynCodeGen.java:219-224`）：
   - `InMemoryCodeCache.addModule()`（`InMemoryCodeCache.java:121-127`）创建 `XCodeGenerator` 指向 `InMemoryResourceStore`，按 `/nop/templates/dyn-gen/` 模板生成 `.xbiz`/`.xmeta`/`.view.xml`/`app.orm.xml` 到**内存**。
   - `genOrmModel()`（`InMemoryCodeCache.java:192-206`）生成 ORM 模型 XML 并用 `OrmModelLoader.loadFromResource()` 加载回 `OrmModel` 对象。
4. **推送运行时**：`DynCodeGen.reloadModel()`（`DynCodeGen.java:349-358`）执行**四件事**：
   - `VirtualFileSystem.instance().updateInMemoryLayer(store)` —— 内存资源作为顶层覆盖磁盘文件（`DeltaResourceStore.java:86-101` 的 `OverrideResourceStore`）。
   - `ModuleManager.instance().updateDynamicModules(...)` —— 动态模块注册，使 `findModuleResources()` 能发现新的 `.xbiz`/`.xmeta`。
   - `bizObjectManager.setDynamicBizModels(...)` —— 把 `GraphQLBizModel` 推入 `BizObjectManager`（`BizObjectManager.java:81-83`）。
   - `ormTemplate.reloadModel()` —— 清 ORM 缓存，新实体由 `LazyLoadOrmModel` 在首次访问时延迟加载（`LazyLoadOrmModel.java:137-158`）。

**路径 B 的关键事实**：从"用户点发布"到"`CustomerFeedback__save` GraphQL 接口可用"**无重启、无重新编译、无部署**。所有生成物在内存中，由 `DynResourceStore`（`DynResourceStore.java:21-28`）按需触发懒生成。

##### Nop 路径 C —— AI 驱动模式

`NopDynModuleBizModel.generateByAI(response)`（`NopDynModuleBizModel.java:96-115`）：AI 输出 XML → `GptCodeGen.parseGptResponse()` 解析为 `OrmModel` → `OrmModelToDynEntityMeta.transformModule()` 转 DB 元数据 → `DynCodeGen.generateForModule()` + `reloadModel()`。一步到位。

##### Nubase 路径 —— 直接 SQL DDL

1. MCP 工具 `sql_execute` → `DatabaseMcpTools.executeSql()`（`mcp/tools/DatabaseMcpTools.java:191`）→ `SqlExecutionService.executeSql()` 通过 raw JDBC 执行 DDL。
2. **Schema 自动刷新**：DDL 触发 PG 事件触发器 `pgrst_watch`（`init_roles.sql:398-406`）→ `NOTIFY pgrst` → `PostgreSQLSchemaWatcher.ListenerThread` 接收 → `SchemaCacheManager.reloadSchemaCache(dbKey)`（`postgrest/schema/PostgreSQLSchemaWatcher.java:378`）。
3. **RLS 策略**：再调一次 `sql_execute` 写 `CREATE POLICY "owner reads" ON customer_feedback FOR SELECT USING (auth.uid() = user_id)`。

##### 步骤 1 对比

| 维度 | Nop 路径 A (codegen) | Nop 路径 B (nop-dyn) | Nubase |
|------|---------------------|---------------------|--------|
| **输入形式** | 结构化 ORM XML/XLSX 模型 | 结构化元数据（GraphQL mutation） | 原始 SQL DDL |
| **元数据丰富度** | 高（含 displayName/dict/domain/validation/关系） | 高（同上，元数据存 DB） | 低（只有列名+类型，约束在 SQL 中） |
| **生效方式** | mvn install + 重启 | 立即（内存生成 + VFS 更新） | 立即（schema cache 通过 NOTIFY 刷新） |
| **API 类型派生** | 是（xmeta 自动生成 GraphQL 类型） | 是（同上，运行时派生） | 否（PostgREST 直接用 PG schema） |
| **核心接口** | `ResourceComponentManager`, `XCodeGenerator` | `DynCodeGen`, `InMemoryCodeCache`, `LazyLoadOrmModel` | `SqlExecutionService`, `SchemaCache`, `PostgreSQLSchemaWatcher` |

---

#### 步骤 2：暴露 API

##### Nop —— 自动从 BizModel 派生 GraphQL

无论路径 A 还是 B，最终都通过同一机制暴露 API。完整调用链（源码追踪）：

1. **HTTP 入口**：`POST /r/CustomerFeedback__findPage` → `QuarkusGraphQLWebService.restQuery()`（`nop-quarkus-web/.../QuarkusGraphQLWebService.java:78-88`）→ `GraphQLWebService.runRest()`。
2. **GraphQL 路由**：`GraphQLEngine.executeRpcAsync()`（`graphql-core/engine/GraphQLEngine.java:492-516`）解析 operationName `CustomerFeedback__findPage`，按 `__` 分隔符取出 bizObjName 和 action（`BizObjectManager.getOperationDefinition()` `BizObjectManager.java:202-218`）。
3. **延迟构建 BizObject**：`BizObjectManager.getBizObject("CustomerFeedback")` 触发 `BizObjectBuilder.buildBizObject()`（`BizObjectBuilder.java:96-185`）：
   - `loadBizObjFromModel()`（`BizObjectBuilder.java:203-258`）从 VFS 读 `.xmeta`（字段定义）和 `.xbiz`（动作定义）。对 nop-dyn 实体，VFS 查询命中 `DynResourceStore` 触发懒生成（`DynResourceStore.java:21-28`）。
   - `ObjMetaToGraphQLDefinition.toGraphQLObjectDefinition()`（`graphql-core/.../ObjMetaToGraphQLDefinition.java:52-83`）把 xmeta 字段转 `GraphQLFieldDefinition`。
   - `BizObjectBuildHelper.addDefaultAction()`（`BizObjectBuildHelper.java:29-44`）把 Java `@BizQuery`/`@BizMutation` 注解的方法合并进去。**xbiz 定义的 action 优先于 Java 注解**。
4. **执行**：`ServiceActionFetcher.get()`（`graphql-core/fetcher/ServiceActionFetcher.java:31-33`）→ `action.invoke(args, selection, context)`。

**自动获得的 CRUD 操作**（CrudBizModel 提供）：`findPage`、`findList`、`findFirst`、`findCount`、`get`、`save`、`update`、`delete`、`batchUpdate`、`batchDelete`、`recoverDeleted`、`copyForNew`（`CrudBizModel.java:150-151, 274-282, 471-480, 524-553`）。**xbiz 可覆盖任何这些方法**。

##### Nubase —— PostgREST 自动从表 schema 派生 REST

调用链（源码追踪）：

1. **HTTP 入口**：`GET /rest/v1/customer_feedback?select=id,content,status` → `PostgrestController.handleRequest()`（`auth/controller/PostgrestController.java:131-192`）。
2. **请求解析**：`ApiRequestParser.parse()`（`postgrest/api/ApiRequestParser.java:33-115`）解析 `?select=`/`?filter=`/`Range` header 成 `ApiRequest`。
3. **Schema 查找**：`QueryExecutor` 用当前 `MultiTenancyContext.getDatabaseKey()` 从 `SchemaCacheManager` 取该租户的 `SchemaCache`（`SchemaCacheManager.java:36-68`），里面缓存了 `customer_feedback` 表的列、PK、FK 元数据（来自 PG `information_schema`）。
4. **查询计划**：`QueryPlanner.plan()` 构造 `QueryPlan`，`QueryExecutor.execute()`（`postgrest/query/QueryExecutor.java:75-100`）生成 SQL 字符串（严格 `quote()` 标识符，`QueryExecutor.java:1174`）。
5. **执行**：`jdbcTemplate.queryForList(sql)`，PG RLS 自动过滤行。

**自动获得的操作**：每张表都有 `GET`（select/filter/order/paginate）、`POST`（insert）、`PATCH`（update）、`PUT`（upsert）、`DELETE`。无"方法"概念，全靠 URL + query string + body。

##### 步骤 2 对比

| 维度 | Nop | Nubase |
|------|-----|--------|
| **API 协议** | GraphQL + RESTful 别名（`/r/{op}`）+ JSON-RPC | PostgREST 风格 REST |
| **API 元数据来源** | xmeta（结构化字段元数据，含验证规则） | PG catalog（列名+类型） |
| **可表达的业务操作** | 任意命名方法（`findByGrade`、`assignHandler`、`replyAndNotify`） | 通用 CRUD + RPC 函数（`/rpc/<fn>`） |
| **字段级验证** | xmeta 内置（mandatory、长度、域、字典） | 需要在 DDL 中加 CHECK 或在 edge function 中校验 |
| **嵌套查询** | GraphQL field selection + DataLoader 批量加载 | PostgREST resource embedding（FK 驱动） |
| **延迟加载 BizObject** | `BizObjectManager.bizObjCache.get(name)` 首次访问时构建 | 无（schema cache 已预热） |

---

#### 步骤 3：写业务逻辑

**需求**：反馈创建时 status 自动设为 "pending"；客服回复时 status 变 "resolved" 并记录处理时间。

##### Nop —— xbiz 脚本（在线可改，覆盖 Java）

xbiz 是 XML 格式的业务脚本，运行在 XPL 引擎中。一个示例（覆盖默认 `save` 的前置钩子）：

```xml
<!-- CustomerFeedback.xbiz -->
<biz x:extends="_CustomerFeedback.xbiz" xmlns:x="/nop/schema/xdef.xdef">
  <actions>
    <mutation name="save">
      <source>
        <c:script>
          // XPL 脚本，运行在 JVM 内
          if (data.status == null) data.status = 'pending';
          if (data.status == 'pending') data.createdAt = $now;
        </c:script>
      </source>
    </mutation>
    <mutation name="replyFeedback">
      <arg name="id" type="String" mandatory="true"/>
      <arg name="reply" type="String" mandatory="true"/>
      <txn transactional="true" propagation="REQUIRED"/>
      <source>
        <c:script>
          entity = dao.getEntity("CustomerFeedback", id);
          entity.reply = reply;
          entity.status = 'resolved';
          entity.handledAt = $now;
          dao.saveEntity(entity);
          // 可以调任何 JVM 服务、IoC bean
        </c:script>
      </source>
    </mutation>
  </actions>
</biz>
```

**执行机制**（源码追踪）：
- `BizObjectBuilder.loadBizModel()`（`BizObjectBuilder.java:270-272`）解析 xbiz → `BizModel` 含 `BizActionModel`。
- `BizModelToGraphQLDefinition.buildAction()`（`BizModelToGraphQLDefinition.java:126-138`）从 `actionModel.getSource()` 取 `IEvalAction`（XPL 编译产物），包装成 `EvalServiceAction`。
- 调用时 `EvalServiceAction.invoke()`（`EvalServiceAction.java:29-37`）创建子作用域，注入参数，执行 `action.invoke(scope)` —— XPL 脚本在 JVM 内运行，可直接访问 `dao`、`biz`、`$context`、任何注入的 bean。
- **事务自动包裹**：`TransactionActionDecoratorCollector.collectDecorator()`（`TransactionActionDecoratorCollector.java:43`）检测到 `<mutation>` 默认 `transactional=true`，应用 `TransactionActionDecorator`（`TransactionActionDecorator.java:38-49`）调用 `transactionTemplate.runInTransaction(txnGroup, propagation, txn -> action.invoke(...))`。

**关键能力**：
- xbiz 在线可改（nop-dyn 模式下，AI 通过 `AiFileTool.saveNopFile(path, content, merge=true)` 写入并 Delta 合并）。
- XPL 是图灵完备的，可写复杂逻辑（循环、条件、宏、调用 Java）。
- 同 JVM 内执行，零序列化开销，可直接复用所有 Nop 服务。

##### Nubase —— Edge Function（外部 TS/JS）

**需求实现**：写一个 `reply-feedback` edge function：

```typescript
// nubase/functions/reply-feedback/index.js
export default {
  async fetch(request, env) {
    const { id, reply } = await request.json();
    const res = await fetch(`${env.NUBASE_URL}/rest/v1/customer_feedback?id=eq.${id}`, {
      headers: { apikey: env.NUBASE_SERVICE_ROLE_KEY },
    });
    const feedback = (await res.json())[0];
    await fetch(`${env.NUBASE_URL}/rest/v1/customer_feedback?id=eq.${id}`, {
      method: 'PATCH',
      headers: { apikey: env.NUBASE_SERVICE_ROLE_KEY, 'Content-Type': 'application/json' },
      body: JSON.stringify({ reply, status: 'resolved', handled_at: new Date().toISOString() }),
    });
    return new Response(JSON.stringify({ success: true }));
  }
};
```

**部署机制**：
- MCP `functions_new({name: "reply-feedback"})` → CLI 在本地 `nubase/functions/reply-feedback/` 脚手架。
- MCP `functions_deploy({name: "reply-feedback"})` → CLI 用 esbuild 打包 → `functionsDeployBundle(sourceBundleBase64)` → `CloudflareEdgeFunctionExecutor.deploy()` 上传到 Cloudflare WfP（`CloudflareEdgeFunctionExecutor.java:174-198`）。
- 上传时 `buildWorkerModule()`（`CloudflareEdgeFunctionExecutor.java:299-320`）把用户的 `export default` 包装一层，注入 `NUBASE_PROJECT_REF`/`NUBASE_FUNCTION_NAME`，**禁止 secret 覆盖这两个保留变量**。
- 调用：`POST /functions/v1/reply-feedback`，HMAC-SHA256 端到端签名（`CloudflareEdgeFunctionExecutor.java:357-375`）防止路由 header 篡改。

##### 步骤 3 对比

| 维度 | Nop (xbiz) | Nubase (edge function) |
|------|-----------|------------------------|
| **运行位置** | JVM 进程内 | 独立 sandbox（本地进程 / Cloudflare WfP） |
| **语言** | XPL（XML 脚本，图灵完备）+ 嵌入 XScript + 嵌入 Java | TypeScript / JavaScript |
| **能否覆盖默认行为** | 是（xbiz 覆盖 Java 注解方法） | 否（edge function 是独立端点，不覆盖 `/rest/v1`） |
| **事务** | 自动（mutation 默认 `transactional=true`，`ITransactionTemplate`） | 无跨调用事务；只能在函数内串行多个 REST 调用 |
| **状态/上下文** | 同 JVM，可直接访问 IoC 容器、ORM、所有服务 | 无状态；每次调用独立，需通过 env 注入或 REST 查询 |
| **性能** | 零序列化、同进程调用 | HTTP 调用 + 可能的网络往返（除非用 Cloudflare 内部调度） |
| **在线修改** | AI 通过 `AiFileTool.saveNopFile` + Delta 合并即可 | 重新 `functions_deploy`（秒级，但有部署开销） |
| **核心接口** | `IEvalAction`、`EvalServiceAction`、`TransactionActionDecorator` | `EdgeFunctionExecutor`、`EdgeFunctionInvocationService` |

---

#### 步骤 4：前端

##### Nop —— 平台 DSL 渲染

`view.xml` 定义表单/列表布局，`page.yaml` 定义路由。由 Nop 平台服务端渲染（基于 AMIS）。

```xml
<!-- CustomerFeedback.view.xml -->
<view x:extends="_gen/_CustomerFeedback.view.xml">
  <grids>
    <grid id="list">
      <cols>
        <col id="content" width="200"/>
        <col id="category" dict="feedback-category"/>  <!-- 自动绑定字典 -->
        <col id="status" dict="feedback-status"/>
        <col id="handledAt"/>
      </cols>
    </grid>
  </grids>
  <forms>
    <form id="view">
      <layout>
        <row><col>content</col></row>
        <row><col>category</col><col>status</col></row>
      </layout>
    </form>
  </forms>
</view>
```

**关键能力**：DSL 由 Nop 平台解析渲染，字典/域/权限自动应用，AI 可通过 `AiFileTool.saveNopFile` 改 DSL 即时生效。

##### Nubase —— 独立前端发布到 CDN

AI 写 HTML/CSS/JS → MCP `assets_upload({path:"index.html", content:"<html>..."})` → `AssetsService.upload()`（`assets/service/AssetsService.java:82-126`）写 R2 → 返回 `publicUrl`。前端用 fetch 调 `/rest/v1/customer_feedback`。

##### 步骤 4 对比

| 维度 | Nop | Nubase |
|------|-----|--------|
| **前端形态** | 平台 DSL（view.xml + page.yaml，AMIS 渲染） | 独立 SPA/MPA（任意框架） |
| **生成方式** | codegen 自动生成 + Delta 定制 | 手写或 AI 生成 |
| **字段联动** | DSL 声明式（dict 自动下拉、domain 自动控件） | 手写 JS 逻辑 |
| **权限菜单** | 平台管理（nop-auth 菜单权限） | 前端自管（JWT 解码判断角色） |

---

#### 步骤 5：认证与数据权限

##### Nop —— 多层应用级权限

1. **登录**：`POST /r/LoginApi__login` → `LoginApiBizModel.loginAsync()`（`LoginApiBizModel.java:47`）→ `LoginServiceImpl.loginAsync()`（`LoginServiceImpl.java:172-244`）验证 `NopAuthUser` 表凭据 → `JwtAuthTokenProvider.generateAccessToken()`（`JwtAuthTokenProvider.java:52-54`）用 HMAC-SHA256 签发 JWT。
2. **请求鉴权**：`AuthHttpServerFilter`（`AuthHttpServerFilter.java:103-171`）从 `Authorization: Bearer` 取 token → `JwtHelper.parseToken()` 验证 → 构建 `IUserContext` 绑定 ThreadLocal。
3. **操作权限**：`@BizQuery`/`@BizMutation` 自动生成权限键 `CustomerFeedback:query` / `CustomerFeedback:save`，`GraphQLActionAuthChecker.check()` 在执行前校验。
4. **数据权限**：`CrudBizModel.checkDataAuth()`（`CrudBizModel.java:547`）+ `DefaultDataAuthChecker`（Java 实现）按用户角色/部门过滤行 —— **应用层过滤，不依赖 DB RLS**。

##### Nubase —— JWT + PostgreSQL RLS

1. **登录**：`POST /auth/v1/token?grant_type=password` → 验证 `auth.users` 表 → 签发 JWT（per-project `jwt_secret`）。
2. **请求鉴权**：`UnifiedMultiTenancyFilter`（`UnifiedMultiTenancyFilter.java:173-333`）从 `apikey` header 解析 project + role，从 `Authorization: Bearer` 解析用户。
3. **RLS 强制**：`PostgrestRequestContext.apply()`（`postgrest/auth/PostgrestRequestContext.java:28-110`）在事务内 `SET LOCAL ROLE "authenticated"` + 推送 `request.jwt.claims` GUC。**Fail-closed**：`SET ROLE` 失败立即抛 `IllegalStateException`（line 62-66）。`auth.uid()` 函数读取 GUC 的 `sub` 字段作为 RLS 策略的判定依据。

##### 步骤 5 对比

| 维度 | Nop | Nubase |
|------|-----|--------|
| **权限层次** | 操作权限 + 数据权限 + 菜单权限（三层） | JWT role + PG RLS（行级） |
| **强制位置** | 应用层（Java 检查 + ORM 查询增强） | DB 层（`SET LOCAL ROLE` + RLS policy） |
| **细粒度** | 字段级（xmeta 控制可见性） + 行级（数据权限） | 行级（RLS policy），字段级需前端/edge function 自行实现 |
| **数据库依赖** | 无关（MySQL/Oracle/PG 均可） | 强依赖 PostgreSQL（RLS 是 PG 特性） |
| **所需权限** | 普通应用连接（只需 DML 权限） | DBA/schema owner 权限（建 policy、`SET ROLE`、`CREATE ROLE`） |
| **DBA 被攻破的影响** | 仅数据本身泄露（安全规则在应用层，DBA 无法改） | 安全规则可被 DBA `DROP POLICY`/`ALTER ROLE ... BYPASSRLS` 直接废除 |
| **绕过风险** | 应用层漏洞可能绕过（但 CrudBizModel 是统一入口，集中审查即可） | `service_role` 带 `BYPASSRLS`，一旦密钥泄露 RLS 形同虚设；与"应用漏洞"风险等级相同 |
| **调试难度** | 低（Java 代码，断点/日志可追踪） | 高（RLS policy 是 DB 对象，SQL 里隐式过滤，`SET ROLE` 行为不透明） |
| **跨租户** | 表级 delFlag + tenant_id 过滤 | 物理数据库隔离（每项目一个 PG） |

**对 RLS 的客观评价**：DB 层 RLS 是 **defense-in-depth（纵深防御）的第二层**，适合作为应用层权限的补充兜底，但不适合作为**唯一**的安全机制。Nubase 把权限完全沉到 DB 层（`SET LOCAL ROLE` + GUC + RLS policy）是 Supabase 兼容性的设计约束，不是更优架构。原因：
1. **耦合数据库引擎**——RLS 是 PG 特性，换数据库全部重写。
2. **需要 DBA 权限**——应用开发者通常不该有建 policy/`SET ROLE` 的权限。
3. **`service_role` 是上帝密钥**——一旦泄露，所有 RLS 失效，和应用层单点失守风险等级相同。
4. **DBA 攻破影响更大**——DBA 可以直接 `DROP POLICY` 或 `ALTER ROLE ... BYPASSRLS`，而 Nop 的应用层权限规则 DBA 改不了。
5. **`SET LOCAL ROLE` 本身是"在数据库中操作应用概念"**——把角色/JWT claims 下沉到 DB 的 GUC 变量，是典型的职责泄漏。

Nop 的应用层方案（`DefaultDataAuthChecker` + ORM 查询增强）虽然"理论上可能被绕过"，但 CrudBizModel 是所有数据访问的统一入口，集中审查这一个类即可覆盖绝大部分风险。两者各有取舍，不能简单说谁"更安全"。

---

#### 步骤 6：定时任务

##### Nop —— nop-job（本地 + 分布式）

声明式定义，支持重试/补偿/checkpoint。通过 `@BizMutation` 或 xbiz action 触发，可在脚本中直接调 `dao`/`biz`。

##### Nubase —— 控制面 cron

MCP `cron_create({name:"daily-summary", cronExpression:"0 7 * * *", targetType:"edge_function", functionSlug:"gen-summary"})`。`ScheduledJobRunner`（`cron/service/ScheduledJobRunner.java:60-84`）每 30s tick，行级 CAS claim 实现多实例无锁协调。Target 类型：`edge_function`（callerRole=ROLE_CRON 绕过 verify_jwt）或 `db_function`（调具名 PG 函数）。

##### 步骤 6 对比

| 维度 | Nop | Nubase |
|------|-----|--------|
| **执行位置** | JVM 内（同 biz 上下文） | edge function（独立 sandbox）或 PG db_function |
| **能访问的服务** | 全部（dao/biz/IoC/工作流/规则引擎） | 仅通过 REST/MCP 或 PG 内函数 |
| **复杂编排** | nop-task（逻辑流）/ nop-wf（工作流） | 无（cron 只触发单一 target） |

---

#### 步骤 7：上线

##### Nop 的两条上线路径

**路径 A（codegen）**：`mvn package` → 单个 fat jar → `java -jar` 或 Docker。改模型需重新打包。

**路径 B（nop-dyn）**：**无上线步骤**。所有改动在运行时立即生效（DB 元数据 → 内存生成 → VFS 更新 → BizObjectManager 刷新 → GraphQL 即时可调）。这是 Nop 区别于传统框架的核心能力。

##### Nubase 的上线路径

`deploy_app({manifest:{migrations, functions, assets, cron, memory}})` → CLI 编排（`deploy-app.ts:47-244`）：
1. 创建 deployment record（status=running）
2. SQL migrations 循环（每条 `sql_execute` + 记 step）
3. Functions 循环（deploy + secrets + verify invoke）
4. Assets 上传（支持 release 模式 + SPA fallback + secret 扫描）
5. Cron 创建
6. Memory 记录部署摘要
7. completeDeploymentRecord

**回滚**：`AppDeploymentRollbackService.rollback()`（`deploy/service/AppDeploymentRollbackService.java:47`）——Assets 和 cron 可逆，SQL/functions/secrets/memory 明确跳过并记录原因。

##### 步骤 7 对比

| 维度 | Nop 路径 A | Nop 路径 B | Nubase |
|------|-----------|-----------|--------|
| **是否需要部署** | 是（mvn package + JVM 重启） | **否**（运行时即时生效） | 否（平台已运行） |
| **回滚机制** | Delta 回退（`x:extends` 层次可撤销） | DB 元数据回退 + reloadModel | deploy_app rollback（部分可逆） |
| **AI 可触达** | AI 写 orm.xml → 触发 mvn | AI 调 GraphQL 保存元数据 → 自动 reload | AI 调 MCP 工具链 |

---

#### 整体流程的本质差异

| 维度 | Nop | Nubase |
|------|-----|--------|
| **AI 操作的对象** | **结构化模型**（ORM 元数据 / xbiz 脚本 / xmeta / view DSL） | **运行时资源**（SQL DDL / edge function 代码 / 静态资产 / cron 配置） |
| **元数据丰富度** | 高（每实体有完整 xmeta：验证/字典/域/权限/显示名） | 低（PG catalog 仅列名+类型；逻辑散在 DDL/edge function/RLS policy） |
| **业务逻辑位置** | JVM 内（xbiz 脚本，零开销，可覆盖 Java） | 独立 sandbox（edge function，HTTP 通信，不可覆盖 REST 端点） |
| **运行时改动生效** | 即时（nop-dyn 模式，内存生成 + VFS 更新） | 即时（MCP 工具调用，平台立即执行） |
| **前端形态** | 平台 DSL（声明式，服务端渲染，权限/字典联动） | 独立前端（任意框架，发布到 CDN） |
| **部署/回滚** | 路径 B 无部署 / 路径 A 传统部署 / Delta 回退 | deploy_app manifest / 部分可逆回滚 |
| **多租户模型** | 表级（共享 schema + delFlag） | 物理数据库级（每项目一个 PG） |

**核心结论**：两个平台都支持"在线定义、即时生效"。差异在于：
- **Nop 的"在线"是结构化元数据驱动**——AI 改的是模型，平台根据模型自动生成 xbiz/xmeta/view，业务逻辑在 JVM 内执行，与平台服务无缝集成。
- **Nubase 的"在线"是资源直接操作**——AI 改的是 SQL/代码/资产，平台是这些资源的容器，业务逻辑在独立 sandbox 执行，通过 HTTP 与平台通信。

### 5.3 开发流程六步对比表

| 步骤 | Nop（接口/方式） | Nubase（接口/方式） |
|------|-----------------|-------------------|
| **1. 定义数据模型** | 写 `*.orm.xml` / 在线 nop-dyn / AI 通过 `AiFileTool.saveNopFile` | `sql_execute` DDL / AI 调 MCP `sqlExecute` |
| **2. 暴露 API** | 自动：`CrudBizModel` + `@BizQuery`/`@BizMutation` → GraphQL | 自动：每张表 `GET/POST/PUT/DELETE /rest/v1/<table>` |
| **3. 写业务逻辑** | Java `@BizMutation`/xbiz XPL 脚本 / JS (nop-js GraalVM) | TS edge function (local / Cloudflare) / PG function |
| **4. 前端** | `*.page.yaml` + `*.view.xml`（页面 DSL） | 独立 HTML/CSS/JS 发布到 Assets CDN |
| **5. 认证** | `nop-auth`：JWT/Session + 操作权限 + 数据权限 + 菜单权限 | `JWT + RLS`：三角色（anon/authenticated/service_role） |
| **6. 上线** | Maven 打包 → JVM 进程（或 Docker） | `deploy_app` manifest 一键上线 / 无部署步骤 |
| **AI 操作入口** | `AiFileTool` (MCP) + 23 个 toolkit 工具 | `nubase_cli` (MCP stdio 桥) + 9 个 @Tool 类 |
| **扩展方式** | `x:extends` Delta + xbiz 脚本 + Java | feature flag + edge function + Spring bean |

### 5.4 核心接口形态对比

| 方面 | Nop | Nubase |
|------|-----|--------|
| **API 定义** | 声明式（注解/DSL）：`@BizQuery` / xbiz / xmeta | 声明式（自动）：每张表 → REST API |
| **业务逻辑** | `<mutation><source>xpl</source></mutation>` 脚本 + Java | edge function TS/JS |
| **事务** | 自动（mutation 默认 `transactional=true`，Nop `ITransactionTemplate`） | `@Transactional`（Spring 控制，无自动推断） |
| **前端** | `view.xml` CRUD 页面（自动生成 + `x:extends` 定制） | 独立前端发布到 CDN |
| **多租户** | 表级 `delFlag` + `tenant_id` 自动过滤 | 物理 PG DB 隔离（每项目一个数据库） |
| **元编程** | XDef + XLang（图灵完备 DSL 引擎） | PostgREST 兼容层（Java 实现 + schema cache） |
| **AOP** | 源码生成式 AOP（`GenAopProxy` + `JdkJavaCompiler`） | Spring AOP（`@Aspect`/`@Around` 运行时 CGLIB） |

### 5.5 MCP 能力对比

| 维度 | Nop (nop-ai-mcp-server) | Nubase (Spring AI MCP) |
|------|------------------------|-----------------------|
| **协议** | 基于 Nop `@BizModel` GraphQL（可适配到 MCP） | 原生 Spring AI MCP（SSE endpoint `/mcp`） |
| **工具数量** | 3 个 DSL 操作 + 23+ toolkit 工具 | 9 个 @Tool 类 + CLI `deploy_app` manifest |
| **核心工具** | `loadNopFile`/`saveNopFile`（DSL 读写 + Delta 合并） | `sql_execute`/`functions_deploy`/`assets_upload`/`memory_write` |
| **操作对象** | DSL 模型文件（ORM/API/XBiz → 间接影响运行时） | 平台运行时（表/函数/资产/记忆 → 直接影响运行时） |
| **AI agent 框架** | 完整 ReAct 循环 + Session + 三层安全 | 无（agent 框架在客户端，如 Claude Code/Codex） |

---

## 6. 两者可以相互借鉴的设计

### 6.1 Nubase 值得 Nop 借鉴

| 设计 | 现状 | 借鉴可能 |
|------|------|---------|
| **Memory 一等原语** | nop-ai 有 `ReadMemoryExecutor`/`WriteMemoryExecutor`，但无事实抽取+混合检索（无 pgvector HNSW、无 LLM 决策 ADD/UPDATE/DELETE）。 | 可参考 `mem.* schema` + `ScoreFusion` 算法 + `mem_update_memory.txt` prompt 设计。对 nop-ai-agent 的会话记忆持久化有价值。 |
| **AI Gateway 多供应商 failover + per-model token 计费** | nop-ai-agent 的 schema 有 `NopAiChatResponse`（含 `model_id`+`prompt_tokens`+`completion_tokens`）和 `NopAiSession`（含 `cost`+6 个 token 分项列），**但 runtime 从不写入这些表**——`ReActAgentExecutor` 只在内存维护单个 `totalTokensUsed` 标量（`ReActAgentExecutor.java:645-648`），把 prompt+completion tokens 求和后丢失了 model 维度和 input/output 分项。多模型 session 的 per-model 计费完全未实现（`model-switched` 消息常量已定义但零引用，`NopAiModel` 无 price 列，全仓库无 Pricing 逻辑）。详见 §7.1.4。 | ① agent runtime 在每次 LLM 调用后创建 `NopAiChatResponse` 行（记录 model_id + tokens + duration）；② 按 model_id 聚合 session 级 cost；③ 给 `NopAiModel` 加 input_price/output_price 列；④ 参考 Nubase `ai_gateway.api_usage_logs` + `daily_token_usage` + `model_pricing` 的完整计费链路。 |
| **物理 DB 多租户** | nop-dyn 每租户一个 `InMemoryCodeCache`，但共享物理 schema。对隔离性要求高的 SaaS 场景，可参考 `RoutingDataSource` + `GuardianDataSource` + `REVOKE CONNECT FROM PUBLIC`。 | 场景有限（共享 schema 更省资源），但可作为可选模式。 |
| **SQL 风险分级 + 声明式 gate** | nop-ai-agent 有 `IToolAccessChecker` + `ICommandChecker`，但 SQL 执行无独立风险分级。 | 参考 `SqlRiskClassifier` + TS 侧 `NUBASE_ALLOW_DANGEROUS_SQL` env gate。 |
| **多项目控制面** | 无。Nop 是单应用框架。如果有 Nop 应用托管平台需求，可参考 metadata DB + per-project DB 架构。 | 目前不适用 Nop 单体定位。 |

### 6.2 Nop 值得 Nubase 借鉴

| 设计 | 现状 | 借鉴可能 |
|------|------|---------|
| **xbiz 脚本覆盖 Java 方法** | Nubase 业务逻辑只有 edge function（远程执行）或 Java 方法不可覆盖。 | 引入轻量脚本引擎（如 GraalVM JS），允许在 Nubase 平台上内联脚本覆盖 REST 端点的行为。 |
| **Delta 定制机制** | Nubase 只有 feature flag + 环境变量。 | 参考 `x:extends` + `DeltaMerger`，允许在不 fork 源码的情况下覆盖平台行为。Spring 生态的 `application-{profile}.yml` 是弱化版。 |
| **声明式 GraphQL API** | PostgREST 适合简单 CRUD，但复杂业务逻辑（条件写入、跨表校验、聚合查询）需要手写 edge function。 | 引入 GraphQL 层（可选），让边缘函数直接作为 GraphQL field resolver。 |
| **代码生成管线** | Nubase 没有模型层，写 JPA Entity + Controller 是手动的。 | 对 Nubase 上的"业务模块"（如 auth、storage 等）可引入模型 → 生成工具。 |
| **开发过程文档体系** | `docs/` 偏用户文档。 | 参考 `ai-dev/`（plans/logs/analysis/design/bugs）让 AI 协作可追溯。 |
| **nop-dyn 运行时动态模型** | Nubase 没有运行时元模型修改。改 schema 需要 `sql_execute` DDL + 手动重建 schema cache。 | 引入一个可选的元模型层，让实体的增删改无需直接 DDL（类似 nop-dyn 的 `VIRTUAL` 宽表模式）。 |

---

## 7. Conclusion

### 7.1 两者的本质关系

**Nop 和 Nubase 都支持在线定义、即时生效**——这是用户特别强调要澄清的事实。两者不是"开发环境 vs 运行时"的二分关系。真正的差异在于 **AI 操作的"对象"不同**：

```
                Nop                                   Nubase
                ↓                                     ↓
AI 操作对象：   结构化模型（元数据 + DSL 脚本）         运行时资源（SQL + 代码 + 资产）
                ↓                                     ↓
平台机制：      元数据 → 内存 codegen → 自动派生        资源直接落地 → schema cache 刷新
                ↓                                     ↓
业务逻辑：      JVM 内（xbiz XPL，零开销，可覆盖 Java）   独立 sandbox（edge function，HTTP 通信）
                ↓                                     ↓
生效方式：      DynCodeGen.reloadModel() 即时生效      MCP 工具调用即时生效
                ↓                                     ↓
派生产物：      xmeta（字段元数据）+ view.xml（页面）   无（PostgREST 直接用 PG catalog）
```

**不是"不同阶段"，而是"不同抽象层次"**：

- **Nop 选择"模型驱动"抽象**：AI 改的是高层的结构化模型（`NopDynEntityMeta` + `NopDynPropMeta` + xbiz 脚本 + xmeta + view DSL），平台自动派生出 xbiz/xmeta/view，业务逻辑在 JVM 内执行。**优点**：元数据丰富（字段验证/字典/域/权限自动应用）、业务逻辑与平台服务无缝集成、Delta 定制可逆。**代价**：必须遵循平台的模型规范。
- **Nubase 选择"资源直接操作"抽象**：AI 改的是底层的运行时资源（SQL DDL + TS 代码 + HTML/CSS/JS + cron 配置 + memory），平台是这些资源的容器。**优点**：灵活（任意 SQL/任意前端/任意 TS 逻辑）、技术栈自由、Supabase 兼容。**代价**：元数据贫乏（PG catalog 只有列名+类型）、业务逻辑在独立 sandbox（HTTP 通信开销、不能覆盖 REST 端点）、无 Delta 定制。

**具体场景印证**（见 §5.2 的逐步对比）：
- 在 Nop 上加一个"客户反馈"实体，AI 改 `NopDynEntityMeta` → 平台自动生成 `CustomerFeedback.xmeta`（含字段验证）+ `CustomerFeedback.xbiz`（含默认 CRUD）+ `CustomerFeedback.view.xml`（含表单/列表）→ GraphQL `CustomerFeedback__findPage`/`__save` 立即可用。
- 在 Nubase 上加同样的实体，AI 写 `CREATE TABLE` → PostgREST `GET /rest/v1/customer_feedback` 立即可用 → 但字段验证、表单、字典联动都需要额外手写（edge function 或前端 JS）。

两者各有适用场景，**不是谁替代谁**。但从功能完整性和可扩展性两个硬指标看，差距是明确的（见 §7.1.1 和 §7.1.2）。

### 7.1.1 功能完整性对比（硬指标）

Nop 有 **20+ 业务模块**，Nubase 大部分没有：

| 能力域 | Nop | Nubase | 差距说明 |
|--------|:---:|:------:|---------|
| 工作流引擎（BPMN） | ✅ nop-wf | ❌ | Nubase 无任何流程编排能力 |
| 规则引擎 | ✅ nop-rule | ❌ | Nubase 无决策引擎 |
| 任务编排 | ✅ nop-task | ❌ | Nubase cron 只能触发单一 target |
| 报表引擎 | ✅ nop-report | ❌ | Nubase 无报表 |
| 批处理 | ✅ nop-batch | ❌ | |
| 分布式重试 | ✅ nop-retry | ❌ | |
| TCC 分布式事务 | ✅ nop-tcc | ❌ | edge function 无跨服务事务 |
| 代码分析 | ✅ nop-code | ❌ | |
| 动态表单/实体 | ✅ nop-dyn | ❌ | Nubase 改 schema 只能 sql_execute DDL |
| 多数据库支持 | ✅ MySQL/Oracle/PG | ❌ 仅 PG | RLS/pgvector 强绑定 PostgreSQL |
| GraphQL | ✅ 自动从 BizModel 派生 | ❌ 仅 PostgREST REST | 表达力差距大 |

Nubase 有 **6 个** Nop 没有的（但都是基础设施层，非业务层）：

| 能力 | Nop 现状 | 差距性质 |
|------|---------|---------|
| Memory 一等原语（事实抽取+混合检索） | nop-ai 仅有简单工作记忆 | **可补**——nop-ai 已有 IVectorStore 抽象 |
| AI Gateway（多供应商+token 计费） | **schema 有字段但 runtime 不写入**——`NopAiChatResponse` 表定义了 `model_id`+`prompt_tokens`+`completion_tokens`，`NopAiSession` 定义了 `cost`+6 个 token 分项列，但 agent runtime 只在内存维护单个 `totalTokensUsed` 标量，从不写 DB。多模型 session per-model 计费未实现。详见 §7.1.4 | **需补**——agent runtime 写入 per-response 记录 + per-model 聚合 + model_pricing 表 |
| Edge Functions（Cloudflare WfP） | 无（有 nop-ai-shell bash 沙箱） | 架构方向不同 |
| 静态 CDN | 无 | 可对接外部 CDN |
| 物理 DB 多租户 | 表级共享 schema | 设计取向不同 |
| deploy_app manifest | 无 manifest 级管线 | **可补**——参考实现 |

**功能完整性结论**：**Nop 是完整的企业应用平台，Nubase 是聚焦的 BaaS + 部署层。** 覆盖面不在一个量级。Nubase 缺的（工作流/规则/任务/报表/批处理/TCC）都是**业务核心引擎**，补齐成本极高；Nop 缺的（Memory/AI Gateway/CDN）都是**基础设施补丁**，补齐成本低。

### 7.1.2 可扩展性对比（硬指标）

| 扩展机制 | Nop | Nubase | 差距 |
|----------|-----|--------|------|
| **覆盖平台默认行为** | `x:extends` Delta 合并——覆盖任意平台文件，无需 fork | ❌ 只有 feature flag 开关 | **架构代差** |
| **运行时改业务逻辑** | xbiz XPL 脚本覆盖 Java 方法，JVM 内即时生效 | ❌ edge function 不能覆盖 REST 端点行为 | Nubase 无法在线改平台行为 |
| **运行时定义新实体** | nop-dyn：DB 元数据 → 内存 codegen → GraphQL + 页面自动派生 | sql_execute DDL → PostgREST 可用（但无元数据派生，验证/表单/字典需手写） | Nop 派生产物远多于 Nubase |
| **元编程能力** | XLang/XPL（图灵完备 DSL 引擎，三阶段求值 `%{}`/`#{}`/`${}`） | ❌ 无 | Nubase 无任何元编程机制 |
| **代码生成可定制** | XCodeGenerator 模板可自定义 | ❌ 无 codegen | |
| **IoC bean 定义覆盖** | beans.xml 支持 `x:extends` Delta 合并 | 标准 Spring（profile/primary，无 Delta） | Nop 升级不覆盖定制，Spring 做不到 |
| **AOP 机制** | 源码级生成（`GenAopProxy`，无运行时开销） | Spring AOP（CGLIB 运行时代理） | Nop 更透明、可调试 |
| **AI 工具扩展** | `IToolExecutor` 接口 + `*.tool.xml` 声明式定义 | Spring `@Tool` 注解（需改 Java 代码） | Nop 可声明式新增工具 |

**可扩展性结论**：**Nop 的 Delta 定制（`x:extends`）是核心架构创新，Nubase 没有任何对等物。** Nop 升级平台不会覆盖用户定制（Delta 层独立），AI 改 Delta 即可定制而不需要改源码。Nubase 扩展只能 fork 源码或写独立的 edge function。

### 7.1.3 总评分

| 维度 | 胜者 | 差距等级 |
|------|------|---------|
| **功能完整性** | **Nop** | 🔴 大——20+ 业务模块 vs 8 个基础设施模块 |
| **可扩展性** | **Nop** | 🔴 大——Delta/xbiz/XLang vs feature flag/edge function |
| **架构成熟度** | **Nop** | 🔴 大——可逆计算理论 + 完整 XDef/XDSL 体系 |
| **AI 自动化深度** | **Nop** | 🟡 中——完整 agent 框架（ReAct+安全+session）vs 仅 MCP 工具 |
| **快速上线** | **Nubase** | 🟡 中——deploy_app 一键 vs codegen+打包 |
| **AI 原语丰富度** | **Nubase** | 🟡 中——Memory + Edge Functions + CDN + **per-model token 计费**（Nop schema 有字段但 runtime 不写入，详见 §7.1.4） |
| **技术栈自由度** | **Nubase** | 🟢 小——前端任意框架、TS 逻辑自由 |
| **Supabase 生态兼容** | **Nubase** | 🟢 小——Nop 不兼容 Supabase 客户端 |

**最终结论**：**Nop 是更完整、更可扩展、更成熟的平台。** Nubase 在"AI 原生快速上线"这个细分场景有差异化优势（Memory + Edge Functions + CDN + deploy_app + per-model 计费），但整体功能覆盖面、扩展机制、架构深度远不及 Nop。Nubase 缺的是业务核心引擎（工作流/规则/任务/报表/批处理/TCC），补齐成本极高；Nop 缺的是少数基础设施能力（Memory 事实抽取/Edge Functions/CDN/per-model 计费 runtime 写入），其中 token 计费 schema 已就绪但 runtime 未实现（详见 §7.1.4）。

### 7.1.4 修正：Token 计费的实际现状（schema 有字段但 runtime 不写入）

**前情**：我之前声称"Nop 已有 token 统计，SQL SUM 即可聚合"。经用户追问多模型 session 场景后深入核查源码，发现这个说法**过于乐观**，实际存在显著缺口。

#### 现状分层

| 层次 | 现状 | 证据 |
|------|------|------|
| **DB Schema** | ✅ 字段齐全 | `NopAiChatResponse` 有 `model_id`+`prompt_tokens`+`completion_tokens`+`response_duration_ms`+4 个质量评分；`NopAiSession` 有 `cost`(DECIMAL(10,6))+`tokens_input`+`tokens_output`+`tokens_reasoning`+`tokens_cache_read`+`tokens_cache_write`+`total_bytes` |
| **Runtime 内存** | ❌ **只有一个标量** | `AgentSession.totalTokensUsed`（`AgentSession.java:17`）是单个 `long`。`ReActAgentExecutor.java:645-648` 把 `promptTokens + completionTokens` 求和塞进去——**input/output 分项丢失，model 维度丢失，cost 不计算** |
| **写入 DB** | ❌ **从不写入** | 全仓库 `new NopAiChatResponse(` 零业务代码引用（仅生成代码的 `newInstance()` 克隆助手）；`session.setCost`/`setTokensInput`/`setTokensOutput` 等零业务代码引用 |
| **model 维度** | ❌ **路由器不记录决策** | `IModelRouter.route()` 返回 `RoutingResult`（含选中的 model options），但 executor 只用 options，**丢弃路由记录**。没有任何"第 N 轮用了模型 X"的持久化 |
| **`model-switched` 消息** | ❌ **常量已定义但零引用** | `MESSAGE_TYPE_MODEL_SWITCHED = 80`（`_NopAiDaoConstants.java:44`）全仓库零引用——从不产生 |
| **定价表** | ❌ **不存在** | `NopAiModel` 无 price 列。全仓库 grep `Pricing`/`price`/`costPerToken`/`billing` 在 `nop-ai/**/*.java` 零业务代码匹配 |

#### 多模型 session 计费的具体问题

一个 agent session 可能在不同 turn 用不同模型：
- Turn 1-3：用 `deepseek-chat`（便宜，$0.14/1M input）
- Turn 4：遇到复杂推理，router 升级到 `deepseek-reasoner`（贵，$0.55/1M input）
- Turn 5：需要看图，router 切到 `gpt-4o`（更贵，$2.50/1M input）

**Nop 当前状态**：这三段的 token 全部累加到一个 `totalTokensUsed` 标量，**无法区分**哪段用了哪个模型。`NopAiSession` schema 上只有一对 `model_provider` + `model_name`——设计上**隐式假设一个 session 只用一个模型**。

**Nubase 当前状态**：`ai_gateway.api_usage_logs` 每次调用一行（含 `model`+`input_tokens`+`output_tokens`+`cost_usd`），`daily_token_usage` 按 `(api_key_id, usage_date, model)` 聚合。`model_pricing` 表存每模型单价。**per-model 计费完整实现**。

#### 需要补的工作（按优先级）

1. **【P0·低成本】agent runtime 写入 `NopAiChatResponse` 行**：在 `ReActAgentExecutor.java:645-648` 的 token 累加处，同时创建 `NopAiChatResponse` 实体并持久化（记录 `model_id` + `prompt_tokens` + `completion_tokens` + `response_duration_ms`）。schema 已就绪，只需加写入代码。
2. **【P0·低成本】保留 input/output 分项**：`AgentSession` 和 `AgentExecutionContext` 增加 `tokensInput`/`tokensOutput` 字段（替代或补充 `totalTokensUsed`），避免求和后丢失分项。
3. **【P1·低成本】给 `NopAiModel` 加定价列**：`input_price_per_1m`(DECIMAL)、`output_price_per_1m`(DECIMAL)、`reasoning_price_per_1m`(DECIMAL)。
4. **【P1·中成本】session 级 per-model 聚合**：`NopAiSession` 的单 `cost` 列改为通过 SQL 聚合 `NopAiChatResponse` 计算，或增加 `nop_ai_session_model_usage` 子表记录 `(session_id, model_id, tokens_input, tokens_output, cost)`。
5. **【P2·低成本】产生 `model-switched` 消息**：在 `IModelRouter.route()` 返回后，如果模型与上一轮不同，写一条 `MESSAGE_TYPE_MODEL_SWITCHED=80` 的 session message。
6. **【P2·中成本】上游 failover 优先级链**：参考 Nubase `ai_gateway.upstream_configs` 的 `priority` 字段，实现同模型多供应商的 failover。

#### 与 Nubase 的差距评估

| 维度 | Nop | Nubase | 差距 |
|------|-----|--------|------|
| Per-response 记录 | schema 有，runtime 不写 | ✅ `api_usage_logs` 每次调用一行 | **P0 需补写入代码** |
| Per-model 聚合 | 无 | ✅ `daily_token_usage` 按 model 聚合 | **P1 需补聚合逻辑** |
| Model 定价表 | 无 | ✅ `model_pricing` 表 | **P1 需加表+列** |
| Cost 计算 | 无 | ✅ `PricingService.computeCost()` | **P1 需补计算逻辑** |
| Failover 优先级 | 无 | ✅ `upstream_configs.priority` | **P2 需补** |
| model-switched 记录 | 常量已定义，零引用 | N/A（Nubase agent 无状态，不需） | **P2 需补产生逻辑** |

**修正后的结论**：我之前说"token 统计已有，SQL SUM 即可"是**错误的**。schema 确实有字段，但 runtime 不写入。Nubase 在 per-model token 计费上**确实领先**——它有完整的调用记录→model 维度聚合→定价计算链路。Nop 需要补 P0（runtime 写入）+ P1（定价表+聚合）才能达到 Nubase 的计费能力。

### 7.2 我的上一版分析犯的错误

1. **误以为 Nop 只有"build-time codegen"**：忽略了 `nop-dyn` 的运行时动态模型能力，也忽略了 `nop-ai-mcp-server` 的 DSL 读写 + Delta 合并。
2. **误以为 Nop 是"单应用框架"，Nubase 才是"多项目控制面"**：Nop 有 `nop-dyn` 的每租户 `InMemoryCodeCache` + `ITenantResourceProvider`，多租户架构完整。
3. **误以为 Nop 的 AI 自动化不如 Nubase 直接**：实际上 Nop 有一套完整的 AI agent 框架（nop-ai-agent），包括 ReAct 循环、三层安全模型、session checkpointing、context compaction——这些远远超出了 Nubase 提供的纯 MCP 工具层。
4. **说 Nop "BizModel 是有状态长事务"**（用户特别指出这个错误）：CrudBizModel 的事务由 `TransactionActionDecorator` 控制，mutation 默认 `REQUIRED`，并不是"长事务"。事务边界就是一个请求的范围。
5. **把 Nop 和 Supabase 联系起来**：Nop 不和 Supabase 竞争。Nop 是完整的全栈开发框架，不是 BaaS。

### 7.3 对 Nop 团队的建议（基于完整理解修正后）

更正上一轮的建议，基于真实差距：

1. **【高价值·中成本】补 nop-ai 的 Memory 一等原语**：nop-ai-agent 已经有 `ReadMemoryExecutor`/`WriteMemoryExecutor`，但它们是简单的 key-value 工作记忆，不是持久的、基于 LLM 事实抽取的长期记忆。可参考 Nubase 的 `mem.* schema` + `ScoreFusion.fuse()` 混合检索 + `mem_fact_retrieval.txt` prompt 模板。这对 nop-ai-agent 的跨 session 上下文保持非常重要。

2. **【高价值·低成本】加固 nop-ai-mcp-server 的 MCP 协议层**：目前 `AiFileTool` 暴露为 `@BizModel("AiTool")`，不是原生 MCP 协议端点。如果 AI agent 需要标准 MCP 工具发现（`tools/list`/`tools/call`），需要一个适配层。`nop-spring-mcp-server` 已经部分做了这件事（`GraphQLToolCallbackProvider` 桥接 Nop GraphQL 到 Spring MCP），但部署和文档不足。

3. **【中价值·中成本】引入 AI 开发流程的 manifest 驱动部署**：`feature-implementation-checklist.md` 当前是人工 checklist，可参考 Nubase 的 `deploy_app` manifest（migrations + functions + assets + cron + memory 一个 JSON 搞定）模式，把"加一个字段/实体/页面"的流程机械化。

4. **【探索性】nop-dyn 的 `GptCodeGen` 需要更完善的 prompt 链路**：当前 `NopDynModuleBizModel.generateByAI()` 从 AI response 解析 XML，但缺少对话式的迭代设计流程。可参考 nubase 的 `deploy_app` 管线设计一个"需求 → 模型 → 代码 → 验证"的主动式 agent 流程。

### 7.4 对 Nubase 团队的建议

1. **引入类似 xbiz 的内联脚本覆盖机制**：让 edge function 可以用轻量脚本内联定义（类似 AWS Lambda URL 的 inline code），而不是必须部署一个独立的 TS 包。
2. **Delta 定制**：feature flag 不够细粒度。可参考 `x:extends`，允许对 PostgREST 端点行为做逐层覆盖。
3. **GraphQL 层（可选）**：PostgREST 适合 CRUD，但复杂业务逻辑场景需要 GraphQL 的 field resolver 能力。
4. **AI agent 框架内置**：目前 agent 框架在客户端（Claude Code/Codex），Nubase 有资源在平台内嵌一个 agent 引擎（参考 nop-ai-agent 的 `ReActAgentExecutor` + 3 层安全）。

### 7.5 后续工作

本分析为 `open` 状态。如要落地具体借鉴建议，建议拆出：

1. `ai-dev/design/nop-ai-memory/nop-memory-primitive-design.md`（Memory 一等原语设计）
2. `ai-dev/plans/XX-nop-mcp-server-consolidation.md`（统一 MCP 协议层）
3. `ai-dev/analysis/2026-06-XX-nop-dyn-vs-nubase-dynamic-model-comparison.md`（两个平台动态模型机制的专门对比，本报告未充分展开）

---

## 8. Open Questions

- [ ] nop-ai-agent 的 session memory（`ReadMemoryExecutor`/`WriteMemoryExecutor`）当前实现是简单的 in-memory 还是持久化？需要看 `InMemorySessionStore` vs `FileBackedSessionStore` vs `DBSessionStore` 的使用场景。
- [ ] Nubase 的 `ScoreFusion.fuse()` 混合检索算法是否可以移植到 nop-ai 的 `IVectorStore` 上？目前 `nop-ai-rag` 子模块是空的。
- [ ] Nop 的 `DeltaMerger`（`x:extends` + `x:gen-extends` + `x:post-extends` 三阶段）能否在 Spring 生态中作为一个独立库发布？
- [ ] Nubase 的 `deploy_app` manifest 中的 `security-scan`（扫描私钥/API key）是否可以移植到 nop-codegen 的 postcompile 步骤中？
- [ ] Nop 的 `IToolAccessChecker`（23 工具分组管控）和 Nubase 的 `SqlRiskClassifier`（SQL 风险分级）是否可以合并为统一的"AI 操作风险矩阵"？

---

## 9. References

### Nop 源码锚点（本报告直接读取的证据）

- `nop-biz/crud/CrudBizModel.java`：`CrudBizModel<T>` 基类，泛型 CRUD + xbiz 钩子
- `nop-biz/decorator/TransactionActionDecoratorCollector.java`：mutation 默认事务化，`BizConstants.BIZ_ACTION_TYPE_MUTATION.equals(actionModel.getType())`
- `nop-biz/decorator/TransactionActionDecorator.java`：`transactionTemplate.runInTransaction(txnGroup, propagation, txn -> action.invoke(...))`
- `nop-api-core/annotations/biz/@BizModel.java`、`@BizQuery.java`、`@BizMutation.java`、`@BizLoader.java`：声明式 API 定义的核心注解
- `nop-xlang/xdsl/DeltaMerger.java`：Delta 合并引擎核心（MERGE/REPLACE/PREPEND/APPEND 策略）
- `nop-xlang/xdsl/XDslExtender.java`：`x:extends` + `x:gen-extends` + `x:post-extends` 三阶段展开
- `nop-xlang/xpl/XplCompiler.java`：XPL 模板编译器（c:for/c:if/c:script 等内置标签）
- `nop-codegen/XCodeGenerator.java`：代码生成引擎（数据驱动模板遍历）
- `nop-core/resource/component/ResourceComponentManager.java`：组件模型加载注册中心
- `nop-core/resource/store/DeltaResourceStore.java`：Delta 多层 VFS
- `nop-dyn/nop-dyn-service/codegen/DynCodeGen.java`：运行时动态模型生成引擎
- `nop-dyn/nop-dyn-service/codegen/InMemoryCodeCache.java`：每租户内存代码缓存
- `nop-dyn/nop-dyn-dao/model/DynEntityMetaToOrmModel.java`：动态实体元数据 → ORM 模型转换
- `nop-dyn/nop-dyn-service/codegen/GptCodeGen.java`：AI 响应 → ORM 模型解析
- `nop-ai/nop-ai-agent/engine/ReActAgentExecutor.java`：ReAct 循环驱动
- `nop-ai/nop-ai-agent/engine/AgentExecutionContext.java`：agent 执行上下文
- `nop-ai/nop-ai-mcp-server/AiFileTool.java`：DSL 文件 MCP 工具（load/save + Delta merge）
- `nop-ai/nop-ai-toolkit/tools/*.java`：23 个内置工具执行器（从 BashExecutor 到 ApplyDeltaExecutor）
- `nop-ai/nop-ai-tools/file/FileToolBizModel.java`：文件操作 BizModel
- `nop-ai/nop-ai-core/service/ChatServiceImpl.java`：Chat 服务实现（dialect-based LLM 调用）
- `nop-ai/nop-ai-core/dialect/OpenAiDialect.java`：OpenAI API 方言
- `nop-ai/nop-ai-code/`（代码分析）、`nop-ai/nop-ai-web/`（前端）、`nop-ai/nop-ai-shell/`（bash 沙箱）
- `nop-ioc/impl/BeanContainerImpl.java`：IoC 容器核心（支持并发启动）
- `nop-ioc/loader/AopBeanProcessor.java`：AOP bean 处理器
- `nop-js/engine/JavaScriptService.java`/`JavaScriptWorker.java`：GraalVM JS 执行引擎

### Nubase 源码锚点

- 完整锚点见本 repo `ai-dev/analysis/2026-06-15-nubase-vs-nop-comparison.md`（已删除，但 key files 已在第一版列出）
- Nubase PostgREST 实现：`auth/controller/PostgrestController.java` + `postgrest/query/QueryExecutor.java`
- Nubase MCP 工具注册：`mcp/tools/McpConfig.java`（9 个 @Tool 类）
- Nubase Memory 引擎：`mem/service/MemoryService.java` + `mem/llm/` + `prompts/mem_fact_retrieval.txt`
- Nubase SQL 分级：`mcp/safety/SqlRiskClassifier.java`（纯前缀匹配，不阻断）

### 文档参考

- `AGENTS.md`（Nop 开发规范）
- `docs-for-ai/INDEX.md`（Nop 文档总入口）
- `nop-dyn-dao/src/main/resources/_vfs/nop/dyn/orm/_app.orm.xml`（动态元模型的定义——16 个元实体）
