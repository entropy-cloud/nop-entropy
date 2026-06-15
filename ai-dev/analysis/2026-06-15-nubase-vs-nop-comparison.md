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

- **Nop 的 AI 自动化**作用于**应用开发环境**。AI agent 读写 DSL 模型文件（ORM/API/XBiz）、触发 codegen、运行时执行 XPL/XBiz 脚本。Nop 提供了完整的"模型 → 生成 → 运行时执行"闭环，AI agent 在此闭环中可以扮演架构师+开发者的角色。关键接口是 `IXDefinition`、`IBizObject`、`IEvalAction`、`IDslModel`、`IGraphQLEngine`。
- **Nubase 的 AI 自动化**作用于**平台运行时操作**。AI agent 通过 MCP 工具操作一个已运行的平台——建表、部署边缘函数、发布前端、调度 cron、写记忆。Nubase 不生成业务源码，业务逻辑由 agent 写成 edge function 或 SQL 在平台上直接执行。关键接口是 `@Tool` + `SqlExecutor` + `EdgeFunctionExecutor` + `MemoryService`。

**两者不是竞品**。如果要用一个比喻：
- **Nop 是一个完整的"开发工具链"**（类似于 JetBrains + Maven + Git 的合体，但全部模型驱动且可逆）。
- **Nubase 是一个"平台运行时"**（类似于省略了开发阶段的 Supabase + Vercel + AI Memory 三合一）。

**一个具体的应用开发流程对比**（20 分钟即可读懂的对比骨架）：

| 步骤 | 在 Nop 上 | 在 Nubase 上 |
|------|-----------|-------------|
| 1. 定义数据模型 | 写/改 `*.orm.xml` 或在线 `nop-dyn` 界面 → codegen 派生 DAO/Meta/Service | 通过 `sql_execute` MCP 工具直接创建 PG 表 + RLS 策略 |
| 2. 暴露 API | 自动：BizModel + GraphQL（`@BizQuery` 注解即可） | 自动：每张表自动获得 PostgREST 风格 `rest/v1/<table>` |
| 3. 写业务逻辑 | 写 xbiz 脚本（XPL）或 Java BizModel 方法 | 写 edge function（TS/JS）通过 `functions_deploy` 部署 |
| 4. 前端 | Nop 页面 DSL（`view.xml`/`page.yaml`）自动生成 | 独立 HTML/CSS/JS 通过 `assets_upload` 发布到 CDN |
| 5. 认证/权限 | `nop-auth` + 数据权限 + 菜单权限（三层模型） | Supabase 风格 JWT + RLS（行级安全） |
| 6. 上线 | Maven 打包 → JVM 部署（传统方式） | 通过 MCP deploy_app 一键上线 |
| 7. AI 辅助 | AI 通过 MCP 读写 DSL 文件 / 调用 codegen / 操作 nop-dyn | AI 通过 MCP 操作平台建表/部署/记忆 |

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
| AI Gateway（LLM 代理+token 计费+多供应商 failover） | nop-ai 直接调 LLM provider，无计费层 |
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

### 5.2 如果你是一个 AI agent：两个平台上的"hello world"

**Nop 方式**（AI 帮助开发者做模型→生成→定制）：

```
→ AI: 用户需要"客户管理"功能
→ AI: 写 model/_Customer.orm.xml（定义实体 Customer、字段 name/phone/grade）
→ AI: 运行 mvn install（触发生成 _Customer.java, _CustomerDao.java, _Customer.xmeta 等）
→ AI: 写 CustomerBizModel.java（extends CrudBizModel<Customer>，加 @BizQuery("findByGrade")）
→ AI: 写 Customer.view.xml（x:extends 重写表单布局，加 grade 下拉框）
→ AI: 注意：_ 前缀的文件不能手改，改模型源文件重新生成
→ AI: 通过 GraphQL 调 NopAuthUser__findPage 验证权限
→ 人类开发者: 在 Studio 里看页面、调接口、确认正确
```

**Nubase 方式**（AI 直接操作平台上线）：

```
→ AI: 用户需要"客户管理"功能
→ AI: sql_execute → CREATE TABLE customers (id uuid, name text, phone text, grade text, user_id uuid)
→ AI: sql_execute → ALTER TABLE ENABLE ROW LEVEL SECURITY + CREATE POLICY ...
→ AI: 前端：自己写或生成 HTML/JS → assets_upload → CDN URL
→ AI: 后端：写 edge function → functions_deploy → /functions/v1/customers
→ AI: 如果 CRUD 够简单：直接前端调 /rest/v1/customers（PostgREST 自动支持）
→ AI: 调 auth_createUser 创建初始用户
→ 人类开发者: 在 Studio 里查 SQL 执行记录、调 memory 看部署决策
```

**本质差异**：
- Nop 的流程中 AI 扮演**架构师**——设计模型、定制逻辑、让平台生成骨架。
- Nubase 的流程中 AI 扮演**运维+开发**——直接创建资源、部署代码、串联服务。

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
| **AI Gateway 多供应商 failover + token 计费** | nop-ai 通过 `ILlmDialect` 支持多供应商，但无 failover 优先级、无 token/成本追踪。 | 参照 `ai_gateway.api_usage_logs` + `daily_token_usage` + `model_pricing` 设计 Nop 的计费层。 |
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

**Nop 和 Nubase 不是竞品。** 它们解决软件开发过程中**不同阶段**的自动化问题：

```
[需求] ──→ 模型设计 ──→ 代码生成 ──→ 业务实现 ──→ 部署 ──→ 运维
              ↑                      ↑               ↑
           nop-dyn       Nop codegen        Nubase deploy_app
           xbiz 脚本     AiFileTool MCP     sql_execute
           ai-agent      23 toolkit tools   functions_deploy
                                              memory_write
```

- **Nop** 为"模型设计 → 代码生成 → 业务实现"阶段提供了 AI 自动化。它的核心能力是让 AI agent 操作 DSL 模型、触发 codegen、写 xbiz 脚本、使用完整的 agent 框架。
- **Nubase** 为"部署 → 运维"阶段提供了 AI 自动化。它的核心能力是让 AI agent 操作一个已运行的平台——建表、部署函数、发布前端、调度任务、写记忆。

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
