# 在 Nop 平台基础上构建 Palantir 式本体层（Ontology）的可行性分析

> Status: open
> Date: 2026-09-07
> Scope: Palantir Foundry Ontology（对象类型/链接类型/动作类型/函数/接口）+ Nop 平台（nop-core / nop-xlang / nop-orm / nop-graphql / nop-biz / nop-dyn / nop-metadata / nop-rule / nop-wf / nop-ai）
> Conclusion: open — 初步结论：Nop 已具备 Palantir Ontology 的大部分**语义层底座**（模型即代码 + nop-metadata + 动作/审批/规则），核心缺口在**显式的一等本体 DSL（interface/action/object-set 抽象）、运行时对象索引与统一读写服务、TS/Python SDK 生成、动态安全与变更治理、AIP 式 AI 面**。可行性总体为"分层可行"，详见文末。

## Context

- 需要回答：能否在 Nop 平台上构建一个 Palantir Foundry Ontology 式的"业务本体层"？nop-metadata 等既有机制能承担什么角色、缺什么？
- 背景：仓库内已有产品化研究流（`ai-dev/analysis/2026-09/2026-09-01-competitor-productization-synthesis-and-p-req.md`、`2026-09-06-nop-platform-deepening-analysis.md`、`ai-dev/analysis/metadata-survey/`、`ontology-driven-agent-vs-nop-code-index.md`），本分析把 Palantir 当作"本体式运营层"的基准对标物做拆解映射，为后续 design/plan 提供决策输入。
- 约束：只做分析与映射，不改代码；结论用于决定"是否/如何/分几步"构建一个 `nop-ontology` 类能力。

---

## 一、Palantir Ontology 究竟是什么（精确拆解）

### 1.1 官方定位

Palantir 官方定义（`palantir.com/docs/foundry/ontology/overview/`）：Ontology 是组织的 **operational layer**，架在已接入的数字资产（datasets、virtual tables、models）之上，把它们连接到真实世界对象（设备/订单/客户）。官方刻意与"数据目录 / 语义层 / 图数据库"划清界限——它同时包含：

- **语义要素（semantic）**：objects、properties、links（"描述世界"）
- **动能要素（kinetic）**：actions、functions、dynamic security（"改变世界"）

一句话：**不是把表改名，而是把名词、关系、规则、动作、权限放进同一个可查询/可计算/可执行/可治理的运营层**。

### 1.2 原语（Primitives）

| 原语 | 定义 | 关键机制 |
|---|---|---|
| Object type | 真实世界实体/事件的 schema 定义；instance=object；集合=object set（类比 dataset/row/过滤行） | 主键 + typed properties + **backing datasource**（一个 object type 可被多个数据源供给 → MDO multi-datasource objects） |
| Property | object type 的特征 | base types（string/long/date/… 官方注明受 RDF/OWL/XSD 启发）、struct、数组、media/attachment、geopoint、timeseries、**derived properties**、property reducers、value/conditional formatting、**edit-only/required/mandatory-control**、shared properties |
| Link type | 两个 object type 间的关系 schema；link=实例 | **双向**（每个 link type 有两侧，各带 display/API name，两侧都可独立遍历）；1-1/1-many/**many-many**（m2m 由独立 join dataset 支撑） |
| Object set | 单类型对象集合 | filter / orderBy / take / **search-around（沿 link 遍历）** / aggregate 一等查询面 |
| Action type | 一次事务内对 objects/properties/links 的一组修改 + 副作用；**是 Ontology 写入的唯一受控通道** | typed parameters、**submission criteria（写入前校验）**、side effects（notification/webhook/调度触发）、per-action 权限、**完整审计**、undo/revert、action log/metrics |
| Function | 服务器端业务逻辑（TypeScript/Python），直接操作 Ontology 对象 | 读属性/遍历 link/做 Ontology Edits；可被 action/应用/AIP agent 消费；带版本与发布 |
| Interface | 描述 object type 的"形状 + 能力"的抽象类型 → **对象类型多态** | 多个 object type implement 同一 interface；interface-level typed query / link / action |

### 1.3 定义方式与治理（Schema 即代码 + 变更管理）

- **Ontology Manager**（低代码 GUI）：定义 object/link/action/interface，**save changes → review → restore**，export/import，maturity status（Experimental/Active/Deprecated），groups。
- **Ontology-as-code**：代码仓库（TS/Python）+ **@osdk/maker**（`defineObject({apiName, primaryKeyPropertyApiName, properties, ...})`）+ SuperRepo（本体 + functions + 前端同仓演进），CI/CD 作为一等版本化产物发布；OSDK 本地按定义再生成。
- **Branching ontology**：分支 / 提案审查 / shared ontology / 跨 ontology 迁移（官方明示 link 不支持跨 ontology）。
- 定位：**Ontology 变更 = 业务运营层的 schema 变更**，需与生产数据库迁移同等管控。

### 1.4 运行时架构（微服务，非单一数据库）

| 服务 | 职责 |
|---|---|
| OMS（Ontology Metadata Service） | schema 的唯一事实源，定义/版本化/全局完整性 |
| Object databases（Phonograph OSv1 → OSv2） | 对象索引存储 |
| OSS（Object Set Service） | 高吞吐读层，LLM/应用经此读 |
| Funnel（Object Data Funnel） | 摄取/索引编排：从 Foundry dataset 同步 → 索引到对象库，保持新鲜度 |
| Actions service | 写入：校验 + 副作用 + 提交；OSv1 落到 writeback dataset，OSv2 经 Actions 应用 + 可选物化 dataset |

关键点：**数据不原地查询**，先经 Funnel 索引进对象库，再被 OSS 读——这是与"直接查仓库/湖仓"的本质区别，也带来集成步与新鲜度成本。

### 1.5 安全与审计（内建，非叠加）

- 角色（Role）可在 Ontology 级或单资源级授权；object security policies；**property-level security**（shared properties 的安全）。
- restricted-view-backed object types、MDO（多数据源对象）；Gotham 系 marking/classification（密级标签）。
- **Dynamic security**：在 object/field/action 级动态约束读与执行。
- user edit history、action permission checks、action log（记录动作、参数、波及对象、操作者身份）。

### 1.6 消费面（Consumption surface）

- 应用：Object Explorer（搜索/分析）、Workshop（低代码）、Quiver（分析）、Object Views、Vertex（图）、Map、Machinery（流程挖掘）、Dynamic Scheduling、Foundry Rules。
- 编程：**Ontology SDK（OSDK）**：TypeScript(npm)/Python(pip)/Java(Maven)/OpenAPI；从你的 Ontology 子集**生成强类型 client**（fetchPage/fetchOne/applyAction/executeFunction/aggregate + link 遍历 + object set）；React hooks；本地生成 / Developer Console / marketplace 打包。REST Ontology APIs v2（objects get/list/search/aggregate/count…）。
- **AI**：OAG（Ontology-Augmented Generation，让 LLM 先取结构化受控对象再推理）、AIP Logic（无代码 LLM 函数，可回写 Edits）、AIP Agent Studio/Chatbot Studio、**Ontology MCP（2026-01 上线，外部 agent 经 MCP 发现并消费本体资源）**、Palantir MCP（IDE 内 AI 辅助）。社区总结为五层：Context → Query(Object Query Tool) → Logic(Function Tool) → Action(Action Tool) → Governance(端到端权限/审计)。

### 1.7 它不是什么（边界）

- 不是 OWL/RDF/SPARQL 栈：数据 type "受 RDF/OWL/XSD 启发"但**不可导出为 OWL**，无 SHACL——跨平台语义互操作是公开缺口（Ontology MCP 是"门"，语义协调层尚未存在）。
- 不是数据工程环境（清洗/join 应在管道层）；不是 BI 语义层/metrics store（那些只读）。

---

## 二、Nop 平台候选底座盘点（已实证）

> 依据：`docs-for-ai/02-core-guides/{model-first-development,api-and-graphql,xdef-and-xdsl,delta-customization,orm-model-design,dql-query,auth-and-permissions}.md`、`docs-for-ai/03-modules/{nop-metadata,nop-dyn,nop-sys,nop-rule,nop-ai,nop-wf}.md`、`ai-dev/design/nop-metadata/*`、`ai-dev/design/nop-graph-design.md`、`ai-dev/articles/grc-universal-software-construction-theory.md`。

### 2.1 模型即代码（比 Palantir 更彻底的本位）

- `model/*.orm.xml` 是唯一手编源 → codegen（`gen-orm.xgen`/`gen-meta.xgen`）→ `_app.orm.xml` + `_gen/_Nop*.java` + `_{shortName}.xmeta` + i18n + view 页面。改模型 → 重装重生成。
- **XDef 元语言**（`/nop/schema/*.xdef`）：声明一个 DSL 即自动获得 XML/JSON 互转、差量合并、编译期元编程、IDE 补全、生成解析器/模型类。`registry.xdef`/`register-model.xdef` 注册新 DSL。
- **Delta / 可逆计算**（`x:extends`/`x:override`/`x:gen-extends`/`x:post-extends`）：VFS Tenant→Delta→Base 分层解析，`_dump` 可审计合并来源。模型经 `ResourceComponentManager` 加载、`removeCachedModel` 热更新。
- 公式（仓库自有理论）：`App = Delta x-extends Generator<DSL>`。这天然支持"本体定义为 XDSL + 差量继承 + 模型热更新"，是 Palantir Ontology-as-code 的上位替代方向。

### 2.2 对象/类型层能力（编译期 + 运行期）

- **编译期**：orm `<entity>` 的 `<to-one>/<to-many>+<join>`（多 on、可常量 leftValue/rightValue 做多态判别）、`refPropName`（反向属性）、m2m 由 join 实体表达、`<computes>`（实体级计算属性，getter 每次重算）、`<column sqlText>`（SQL 视图列）、`fixedValue`（大宽表+判别列=多态种子）、`<aliases>`、`tagSet="disp"` → displayProp 路径属性、xmeta prop 的 `<getter>/<setter>/autoExpr/transformIn/Out`、`bizObjName`+`graphql:*`。
- **运行期**：**nop-dyn**（`NopDynEntityMeta/PropMeta/RelationMeta`；VIRTUAL/REAL 存储；`DynCodeGen` 运行时为每个 bizObjName 生成 `xbiz/xmeta` 并注册进 `GraphQLBizModels`；底层 `DynamicOrmEntity` + `IDynamicEntityModelProvider`）——这是"运行期定义对象类型"的现成答案。nop-sys `NopSysExtField` 提供 EAV 扩展。

### 2.3 语义层底座（nop-metadata — 最接近"本体数据面"）

- **Catalog**：`NopMetaDataSource`/`NopMetaTable`（tableType=`entity/external/sql`）统一逻辑表抽象；`syncExternalTables` 自动同步外部表。
- **语义层**：`NopMetaTableMeasure`（aggFunc+expression）/`Dimension`/`Join`/`Filter` 一等实体；`queryAggregation`/`queryJoinData`/`queryTableData` 联邦查询入口；`MetaAggregationExecutor` 7 路径分派（同库原生 JOIN / 跨库内存合并）。
- **治理**：血缘（`NopMetaLineageEdge` 列级）、质量（`NopMetaQualityRule/Checkpoint/Result`）、对账（`NopMetaReconciliationConfig/Entity/Result`）、Glossary/Classification/Tag/TagLabel（业务语义标注 + 提审/审批流）、`NopMetaDataContract`（质量+SLA）、`NopMetaBusinessDomain/DataProduct`、`NopMetaModule`（版本化模块）、`NopMetaManifest`（自包含 JSON 快照）、`NopMetaModelChangedEvent`（模型变更 before/after 事件）。

### 2.4 动作/函数层

- `CrudBizModel`（findPage/save/update/batchModify…）+ `@BizModel/@BizQuery/@BizMutation/@BizLoader` + xbiz XML 覆盖；`IGraphQLEngine` 五入口（/graphql、/r/、/p/、/px/、/jsonrpc）。
- **审批/可审批实体**：`use-approval` + `approval-support.xbiz`（submitForApproval/approve/reject，按 `wf:wfName` 启 wf）+ nop-wf 15 个示例流；nop-sys `NopSysCheckerRecord` maker-checker（requestAction/requestData 快照/cancelAction，审批通过自动调用）。nop-metadata 的 TagLabel/DataContract 状态机即"审批驱动模型状态翻转"先例。
- **规则**：nop-rule 决策树/矩阵，`rule:Execute` 可被动作/流程调用；validator.xdef/table-validator + xmeta 约束。
- **审计/事件**：`IOrmInterceptor` 8 hook、`OrmEntityChangeLogInterceptor`（tagSet=audit 字段级 old→new）、`NopSysEvent` DB outbox。

### 2.5 安全

- 行级：`*.data-auth.xml`（data-auth.xdef）+ `DefaultDataAuthChecker`，`CrudBizModel.prepareFindPageQuery` 自动追加过滤。
- 列级：xmeta `published/internal/insertable/updatable/queryable/<auth for=...>`；ORM `tagSet="enc"`（列加密）/`masked`（日志脱敏）。
- 身份：nop-auth（角色/权限/JWT/MFA）。

### 2.6 消费面 / SDK / AI

- 客户端：`*.api.xml` → Java typed API（4 变体 + SPI）；CRUD typed API 由 xmeta 生成 Input/OutputBean；任意语言经 GraphQL/RPC。**TS/Python SDK 生成缺失**（`nop-code-lang-typescript` 是解析器非生成器）。
- 前端：xmeta 驱动 AMIS/Flux 页面（view.xml/grid_crud.xpl 引用 objMeta），前端 schema 同源。
- AI：`nop-ai-agent`（ReAct/Team/Guardrails）、**`GraphQLToolSetFactoryBean`（GraphQL schema → LLM 工具，白名单配置）**、`nop-ai-mcp-server`（MCP 服务端）、nop-ai-rag；`ai-dev/design/nop-metadata/07-ai-integration.md`（draft）提出"GraphQL schema 即本体供 AI 学习"。

---

## 三、逐概念映射表（Palantir → Nop）

| # | Palantir 概念 | Nop 对应机制 | 拟合度 | 主要缺口 / 所需工作 |
|---|---|---|---|---|
| 1 | Object type（编译期） | orm `<entity>` → xmeta/xbiz codegen | 高 | 无"对象类型 = 独立语义层概念"，与物理表强耦合；需"逻辑对象类型 + 多数据源映射(MDO)"抽象 |
| 2 | Object type（运行期） | nop-dyn（VIRTUAL/REAL + DynCodeGen 生成 xbiz/xmeta） | 中高 | 文档薄（`docs-for-ai/03-modules/nop-dyn.md` 61 行）；无数据源映射/索引 |
| 3 | Property / base types | xmeta prop + stdDomain；`<column sqlText>`/`<aliases>`/struct(component) | 高 | derived property、shared property、media/timeseries/geopoint 需自建组件 |
| 4 | Derived / 计算属性 | orm `<computes>`（getter 级）；DQL 聚合；nop-metadata Measure | 中 | **无实体级 to-many 聚合属性**；需在 getter/DQL 或新 DSL 上建 |
| 5 | Link type（双向/1-N/M-N） | orm `<to-one>/<to-many>` + `refPropName` + m2m join 实体 + `<join>` 多 on | 高 | link 不是一等类型（无两侧 API name、无独立 link 元数据）；search-around 生成器缺失 |
| 6 | Object set / 过滤 / 聚合 | QueryBean/EQL findPage + link 遍历；nop-metadata queryAggregation | 中高 | 无 OSDK 式 set 一等 API（filter/orderBy/take/search-around/aggregate 组合的生成代码） |
| 7 | Interface（多态） | XDef union + `fixedValue` 判别列可模拟 | **低** | 无现成"interface → 实现类型 → 接口级 typed query/link/action"机制，需新 DSL 设计 |
| 8 | Action type（受控写入） | `@BizAction`/xbiz + CrudBizModel + validator + nop-rule + maker-checker + `approval-support.xbiz` + IOrmInterceptor 审计 | 中高 | 无"动作类型一等 DSL"（typed params + submission criteria + per-action 权限 + action log/metrics/revert）；当前逻辑散在 xbiz/Java |
| 9 | Function | `@BizQuery`/`@BizLoader`/service 层 + XLang + nop-rule | 高 | 无"函数仓库 + 发布/版本/marketplace"治理面 |
| 10 | 校验（submission criteria） | xmeta 约束 + validator.xdef + nop-rule + wf | 高 | 无"动作参数级校验 schema"的显式抽象 |
| 11 | 动态安全（object/field/action 级） | data-auth 行级 + xmeta published/auth 列级 + enc/masked | 中 | 无运行时对象/字段/动作级策略引擎 + restricted-view/MDO |
| 12 | 变更治理（版本/分支/审查/maturity） | nop-metadata NopMetaModule 版本 + 导入快照 + Manifest + ModelChangedEvent + Delta + wf 审批 | 中 | 无 branch/review/restore/maturity status 治理 UI；**跨版本模型迁移缺失** |
| 13 | 摄取/索引（Funnel + OSS + object DB） | nop-metadata syncExternalTables + MetaTable external + ORM；nop-stream/nop-batch 管道 | 中 | **无独立对象索引**；读直接走 DB，无 OSS 级低延迟读面（规模/性能路径） |
| 14 | OSDK 强类型 client | Java api.xml/crud-api codegen；GraphQL/RPC 五入口 | 中 | **TS/Python SDK 生成空白** |
| 15 | Ontology Manager UI | xmeta 驱动 AMIS 页面 + view.xml；nop-dyn 页面；nop-metadata web | 中 | 需"本体浏览器/编辑器"应用（对象/链接/动作/接口/变更审查） |
| 16 | OAG / AIP Logic / Agent | nop-ai-agent + GraphQLToolSetFactoryBean + nop-ai-mcp-server + RAG；07-ai-integration（draft） | 中 | 无 OAG 式"先取结构化受控对象再推理"；无 AIP Logic 无代码层；无 eval |
| 17 | Ontology MCP（外部 agent 接入） | nop-ai-mcp-server（通用 MCP 服务端） | 中 | 需把本体资源（对象/动作/函数）按白名单暴露为 MCP tools + 认证 |
| 18 | Marketplace / 打包分发 | nop-plugin + Delta + IoC beans；connector 市场裁定"本体分发不适用"（`ai-dev/design/nop-stream/connector-design.md` §8.8 D8） | 低 | 本体产品打包/安装/版本治理需新机制（Palantir Marketplace 类比） |

---

## 四、可行性总体评估与分层落地方案

### 4.1 结论（初步）

- **语义半区（semantic）可行性高**：Nop 的 model-first + XDef/Delta + nop-metadata + xbiz/wf/rule 已经覆盖 object/property/link、语义层、血缘/质量/对账、审批与规则。若只做"本体目录 + 语义层 + 受控动作"，Nop 几乎开箱。
- **动能半区（kinetic）可行性中高**：action/function 是 Nop 服务层的自然形态，但缺"动作类型一等抽象 + 动态安全 + 动作审计/撤销/指标"。
- **平台化与规模面（OSS/Funnel、SDK、治理 UI、AI 面）可行性中低**：缺独立对象索引、TS/Python SDK 生成、本体管理器/治理 UI、分支审查、跨版本迁移、OAG/AIP Logic。
- **战略判断**：不应 1:1 复刻 Palantir（它本质是 20 年平台化+工程化整合 + 单一云/私有化绑定）；**应把 Palantir 当需求清单与对标基准**，以"Nop 本体 = XDSL 定义 + Delta 差量 + codegen + nop-metadata 语义数据面 + xbiz/wf 动能 + GraphQL/MCP AI 面"为自己的构型——这个构型在"模型即代码、差量继承、热更新、前后端 schema 同源、AI 工具自动暴露"上反而**比 Palantir 更彻底**（Palantir 的 Ontology-as-code 仍要重发 SDK、无差量继承）。

### 4.2 分层落地方案（建议，待 design/plan）

| 层 | 内容 | 现成基础 | 主要新工作 |
|---|---|---|---|
| L0 DSL | `ontology.xdef`：ObjectType/LinkType/ActionType/Function/Interface 声明 | XDef/registry/codegen 管线 | 新 xdef + `x:post-extends` 展开到 orm/xmeta/xbiz 生成 |
| L1 数据面 | 对象类型 ↔ 数据源映射（单/多数据源） | nop-metadata MetaTable(entity/external/sql) + 联邦查询 | "backing mapping"实体（类比 Palantir backing datasource/MDO） |
| L2 动能 | ActionType 一等抽象（typed params + 校验 + 副作用 + 权限 + 审计） | xbiz + validator + nop-rule + maker-checker/wf + IOrmInterceptor | 动作 DSL + 动作日志/撤销/指标 + per-action 权限 |
| L3 查询面 | ObjectSet 一等 API（filter/orderBy/search-around/aggregate） | QueryBean/EQL + 关系遍历 + nop-metadata 聚合 | set 抽象 + link search-around codegen |
| L4 多态 | Interface + 实现类型 + 接口级查询/链接/动作 | XDef union + fixedValue 判别列 | interface.xdef + 接口注册表 + typed 查询 |
| L5 SDK | 从本体生成强类型 client | Java api.xml/crud-api codegen | **TS/Python 生成器**（或输出 OpenAPI） |
| L6 安全 | object/field/action 级动态策略 | data-auth + xmeta auth + enc/masked | 运行时策略引擎 + restricted-view/MDO 类比 |
| L7 治理 | 版本/分支/审查/maturity/迁移 | nop-metadata Module/Manifest/Event + wf | 变更治理 UI + 跨版本迁移器 |
| L8 AI | OAG 式对象上下文 + 动作/函数工具 + MCP | GraphQLToolSetFactoryBean + nop-ai-mcp-server + RAG | 对象级上下文注入 + 动作确认 + eval 面 |
| L9 运行时规模 | 对象索引（Funnel/OSS 类比） | ORM/EQL 直接读 | 独立索引/物化读面（可后续，先由 ORM+联邦查询扛） |

### 4.3 建议的优先路径（MVP 顺序）

1. **L0+L2 最小闭环**（本体 DSL → 生成 orm/xmeta/xbiz + 动作类型）——证明"模型即代码 + 动能层"成立。
2. **L1+L3 语义数据面**（挂 nop-metadata MetaTable，提供 ObjectSet/聚合/联邦查询）——nop-metadata 立即升值。
3. **L5+L8 消费面**（Java SDK 已有 → 补 TS/Python/OpenAPI + GraphQL→MCP 暴露）——对接 nop-ai-agent。
4. L4/L6/L7/L9 视需求分期。

### 4.4 被否决/不建议的路线

- **照搬 Palantir 架构（OMS/OSS/Funnel/Actions 五服务 + Phonograph 对象库）**：与 Nop 单机可嵌、模型驱动的轻量哲学冲突；规模路径可后置。否决理由：工程投入大且与现有 ORM 直读优势重叠。
- **引入 OWL/RDF/SPARQL 栈**：仓库既有裁定已多次否（如 `agent-survey/2026-08-01-trustgraph-context-graph-analysis.md`：RDF 栈成本高）。Palantir 自身也不用 OWL；**可保留 RDF/OWL 往返导出**作为互操作面（nop-metadata 11 号设计已预留 namespaces）。
- **把 nop-metadata 直接改名为"本体"**：语义层/目录 ≠ 运营层；动能层缺失时名不副实。

---

## 五、nop-metadata 在其中的角色（总结）

- **Catalog + 语义层 = 本体的数据面**（MetaTable 逻辑表 = backing 抽象；Measure/Dimension/Join = ObjectSet 聚合与跨对象遍历的数据等价物）。
- **血缘/质量/对账 = 本体的运维面**（数据可靠性随对象暴露，而非藏在管道）。
- **Glossary/Classification/DataContract/Module/Manifest/ChangedEvent = 本体的治理与版本面**（术语/密级、变更事件、模块版本——Palantir Ontology 治理的弱实现）。
- **缺的是"把以上声明成 ObjectType/ActionType/Interface"的显式一层**——这正是 L0 的 `ontology.xdef` 要补的，nop-metadata 是其落地数据面而非替代品。

---

## Open Questions

- [ ] ObjectType 是否复用一个"逻辑对象"模型（独立于 orm 实体、多数据源映射、派生属性、接口实现）来定义？它与 nop-metadata MetaTable 的边界在哪？
- [ ] ActionType 应落在 xbiz 之上（生成层）还是独立 DSL + 运行时分发？per-action 权限/审计/撤销的最小可行形态？
- [ ] 是否先做 TS/Python SDK 生成器还是先走 OpenAPI 通道？
- [ ] 对象索引（Funnel/OSS 类比）在什么数据规模/延迟要求下才值得做（当前 ORM 直读 + 联邦查询的拐点）？
- [ ] 与既有 `nop-dyn`（运行期实体）的关系：本体 DSL 应"编译期为主、dyn 为辅"，还是统一为运行时模型注册？

## References

- Palantir 官方：`palantir.com/docs/foundry/ontology/overview/`、`core-concepts`、`object-link-types/{object-types,link-types,type-reference}`、`action-types/overview`、`functions/{overview,types-reference,api-object-sets}`、`interfaces/interface-overview`、`ontology-sdk/overview`、`ontologies/branching-ontology`、`object-backend/overview`、`object-permissioning/overview`
- 第三方分析：BD Emerson "The Palantir Ontology, Explained"（2026-07）、PuppyGraph "Palantir Ontology: Architecture & Benefits"（2025-09）、Towards AI "Inside Palantir AIP"（2026-05）、Towards AI "Foundry Ontology: how it works…"（2026-03，含 Ontology MCP 2026-01）
- Nop：`docs-for-ai/02-core-guides/{model-first-development,xdef-and-xdsl,delta-customization,api-and-graphql,orm-model-design,dql-query,auth-and-permissions}.md`、`docs-for-ai/03-modules/{nop-metadata,nop-dyn,nop-sys,nop-rule,nop-ai,nop-wf}.md`、`ai-dev/design/nop-metadata/{00-vision,11-enterprise-semantic-layer,07-ai-integration}.md`、`ai-dev/design/nop-graph-design.md`、`ai-dev/articles/grc-universal-software-construction-theory.md`
- 本仓库既有对比：`ai-dev/analysis/metadata-survey/`、`ontology-driven-agent-vs-nop-code-index.md`、`2026-08/2026-08-14d-tis-vs-nop-data-integration-comparison.md`、`2026-09-06-nop-platform-deepening-analysis.md`
