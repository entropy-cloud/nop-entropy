# 术语表（Glossary）

> 本页只**定义术语**，不教写法。写法见对应 core-guide。
> 出现的类名/接口名（如 `CrudBizModel`、`IGraphQLEngine`）是概念指代，不是源码定位；查实现请走 `source-anchors.md` + LSP。

## 易混术语对照（先看这里）

| 易混点 | 区别 |
|--------|------|
| **XBiz action** vs **BizModel method** | BizModel method 是 Java 里的业务方法；XBiz action 是 `.xbiz` XML 里声明的动作，可在不改 Java 的情况下扩展/覆盖业务方法。 |
| **xmeta** vs **orm model** | orm model（`.orm.xml`）描述数据库表与实体映射；xmeta（`.xmeta`）描述实体的对外元数据（字段、权限、GraphQL 暴露）。 |
| **CrudBizModel** vs **普通 BizModel** | CrudBizModel 是内置标准 CRUD 基类；普通 BizModel 可不带 CRUD，仅自定义 action。 |
| `/r/` vs `/p/` vs `/px/` | `/r/` 返回 JSON；`/p/` 内容感知（文件/二进制/流）；`/px/` 跨服务分布式代理。三者底层都经 GraphQL 引擎分发到同一 BizModel 方法。 |

---

## 一、核心概念

- **Nop** — 基于"可逆计算"的语言导向编程（LOP）平台；nOt Programming 的缩写，强调以声明式模型为主、命令式代码为辅。
- **可逆计算** — 把软件构造视为模型差量的可逆合成，使定制与升级可在同一框架内统一处理的理论。
- **`App = Δ x-extends Generator<DSL>`** — Nop 核心公式：应用 = 生成器对 DSL 求值后，再叠加 Delta 差量。
- **DSL** — Domain-Specific Language，由 XDef 定义语法的结构化模型（XML/JSON/YAML），提供稳定的"语义坐标系"。
- **Generator** — 把源模型确定性变换为基础代码/模型的生成器（如 codegen）。
- **Delta（差量）** — 对基础模型的增量修改，通过 `x:extends` 合并，不破坏基线，可叠加/移除。
- **`_delta/`** — VFS 中存放 Delta 差量层的目录，按层名（如 `default`）组织。
- **`_gen/`** — 代码生成产物目录；下划线前缀表示可被重新生成覆盖，禁止手改。
- **保留层** — 非下划线、可手工编辑的源层文件，与生成物相对（手写代码与配置落于此）。
- **XLang** — Nop 元语言套件总称，包含 XDef / XPL / XScript / XDSL。
- **XPL** — Nop 模板标签语言，用于代码生成与 DSL 转换。
- **XScript** — Nop 脚本语言，类 JS 语法，用于表达式与逻辑片段。
- **tombstone（删除占位）** — 差量合并中标记"已删除坐标"的占位符，使删除可逆且保持合并的结合律。

## 二、DSL 与元模型

- **XDef** — Nop 元模型定义语言，为 DSL 提供 schema（语法 + 校验），即"语义坐标系"的定义。
- **XDSL** — 任何由 XDef 定义、支持 `x:extends`/`x:override` 的 Nop DSL 的统称。
- **XMeta** — 实体元数据 DSL（`.xmeta`），描述字段、权限、GraphQL 暴露等对外属性。
- **`x:extends`** — 差量合并算子，把当前模型叠加到基线之上。
- **`x:override`** — 控制差量合并策略的属性（merge / remove / remove-child 等）。
- **`x:gen-extends`** — 编译期生成式扩展，在合并前用 XPL 生成节点再参与 `x:extends`。
- **`x:post-extends`** — 合并后再执行的生成式扩展，用于对最终模型做后处理。
- **objMeta** — BizModel 关联的对象元数据，驱动 GraphQL schema 生成。
- **S-N-V 加载管线** — 模型加载三阶段：Structure Merge（结构合并）→ Normalization（规范化）→ Validation（校验）。

## 三、服务与 API

- **BizModel** — 业务对象模型，承载业务 action 的核心载体，标注 `@BizModel`。
- **CrudBizModel** — 内置标准 CRUD 实现基类，提供 findPage/findList/save/delete 等。
- **XBiz** — 用 XML DSL（`.xbiz`）声明的 action 集合，可不改 Java 扩展业务方法。
- **BizAction** — BizModel 中的一个业务操作单元。
- **BizLoader** — 字段加载器，按需计算/装配返回字段（`@BizLoader`）。
- **`@BizQuery`** — 标注读操作 action（支持 GET/POST）。
- **`@BizMutation`** — 标注写操作 action（POST），自动开启事务。
- **`@BizLoader`** — 标注字段加载方法（按需装配返回数据）。
- **`@BizAction`** — 标注自定义业务 action。
- **I\*Biz 接口** — 跨模块业务接口约定（如 `IXxxBiz`），定义模块对外可调用的业务契约。
- **IGraphQLEngine** — 框架无关的 GraphQL 执行引擎，统一分发 REST/GraphQL/RPC 请求到 BizModel。
- **`/r/{opName}`** — REST 风格路径，返回 JSON `ApiResponse`。
- **`/p/{opName}`** — 内容感知路径，支持文件/二进制/流/自定义 contentType（下载、导出等）。
- **`/px/{svc}/{opName}`** — 分布式 RPC 代理路径，转发到远程服务。
- **EQL** — Entity Query Language，Nop 实体查询语言。
- **DQL** — Dimensional Query Language，基于 QueryBean 的结构化维度查询。
- **QueryBean** — 结构化查询对象，承载过滤/排序/分页/字段选择。
- **beans.xml** — Nop IoC 的 bean 定义文件（非 Spring 注解扫描，所有 bean 显式声明）。
- **自动事务边界** — `@BizMutation` 方法自动进入事务，无需手动叠加事务注解。

## 四、横切机制

- **tenant** — 多租户标识，ORM 层据此自动过滤与填充。
- **logical deletion（逻辑删除）** — 通过 `deleted`/`delVersion` 字段标记删除，而非物理删除行。
- **module** — Nop 的模块单元，由 `_module` 文件标识。
- **moduleId** — 模块唯一标识，遵循命名规则。

### 文件扩展名族

- **`.xmeta`** — 实体元数据。
- **`.orm.xml`** — ORM 模型（表与实体映射）。
- **`.beans.xml`** — IoC bean 定义。
- **`.xlib`** — XPL 标签库。
- **`.xpt`** — 报表模板（Excel）。
- **`.xwf`** — 工作流定义。
- **`.xbnf`** — 语法描述。
- **`.xbiz`** — XBiz action 声明。
