# GitHub 本体论（Ontology）项目全景调研 vs nop-metadata（完善版）

> Status: open
> Date: 2026-09-12（2026-09-14 增补裁定补记，见下）
> Scope: 15 个 GitHub 本体相关开源项目（本地 clone 于 `~/sources/ontology/`）+ 本仓库 `nop-metadata` 模块 + `nop-ai` AI 集成面
> Conclusion: open — 初步结论：本体技术栈在 GitHub 上分为六条路线（RDF/OWL 全家桶、本体工程工具链、类型化知识图谱、虚拟知识图谱、属性图桥接、schema 建模语言），加上 AI+KG 融合新路线。**nop-metadata 的"目录+语义层+治理"底座已覆盖各路线中价值密度最高的部分（逻辑表抽象、Measure/Dimension、Glossary/Classification/TagLabel、联邦查询、模块版本化）**，真正的差距集中在四处：①本体不是一等公民（无显式 ObjectType/LinkType/ActionType/Interface 声明层）②schema 版本工程（语义 diff / 迁移操作语言 / branch-merge）③约束校验未下沉且无结构化校验报告 ④AI 面只有"GraphQL schema 自动暴露"单通道，缺本体注入 AI 与 AI 反哺本体的双向机制。AI 介入后，本体应成为"AI 的约束源 + 检索结构 + 上下文供给 + 治理边界"四重角色，nop 已具备落地组件，缺的是把它们串起来的三个集成件。详见 §七、§八。

## 裁定补记（2026-09-14）

本分析的"后续工作"路径已被用户裁定深化/取代，以本补记为权威结论：

1. **Entity/Table 双树确认为结构债，NopMetaTable（逻辑表）概念裁定冗余**。依据（用户给出的 NopORM 不变式）：NopORM 以物理表设计为基础、补充关联语义标注获得 ORM 模型——任一 table 都有对应的 ORM 模型，任一 ORM 模型本质上都对应物理表设计（外部表 = querySpace 路由实体、SQL 视图 = sqlText 列实体、聚合口径 = 计算属性/关联）。独立"逻辑表"分支重复 ORM 已统一的能力，只制造双资产树。§7.2/§7.3 中"Object Type ≈ MetaEntity/MetaTable"的骑墙映射据此修正为：**ObjectType（nop-ontology）是唯一资产身份，ORM 实体是其唯一绑定形态**。
2. **已立项独立 `nop-ontology` 模块**（worktree 分支 `feature/nop-ontology`）：定义面 8 实体入库（DB 唯一权威 + NopOntDefinitionVersion 不可变版本快照，支撑 Palantir Ontology Manager 等价的实体定义设计器）+ 动能面 3 实体（动作留痕/对象变更明细/跨源链接边）+ 三绑定路径统一归一 ORM 实体。详见该分支 `ai-dev/backlog/nop-ontology-roadmap.md`（21 WI）与 `ai-dev/design/nop-ontology/01-data-model.md`。
3. 本文档 §Conclusion 中"在 nop-metadata 上补声明层（ontology.xdef）"的路径**废止**，由独立模块方案取代（nop-metadata 瘦身为治理运行时，定义面废弃）；"语义 diff + weakening migration"与"AI 四角色"结论继续有效（分别由本体发布版本管线与 M4 消费面承接）。

## Context

- 本仓库已有两份 ontology 初步分析：`ai-dev/analysis/ontology-driven-agent-vs-nop-code-index.md`（2026-07-10，单一项目 vs nop-code 索引）与 `ai-dev/analysis/2026-09/2026-09-07-palantir-ontology-on-nop-feasibility.md`（2026-09-07，Palantir Ontology 概念映射）。本次按新要求做**更完善**的调研：把 GitHub 上的本体论开源项目全景拉通，与 `nop-metadata` 逐一对比，并回答"AI 介入后 ontology 如何应用"。
- 与 `ai-dev/analysis/metadata-survey/`（DataHub/Atlas/OpenMetadata/Amundsen 等 13 份元数据平台深分析）互补：metadata-survey 覆盖"数据目录/治理平台"，本次覆盖"本体建模/知识图谱/语义技术"本身。
- 调研方法：不是网页速览，全部项目 **git clone 到 `~/sources/ontology/`**（脚本 `~/sources/ontology/clone-ontology-repos.sh`，带多轮重试，共 15 个仓库），由多个分析代理逐仓库阅读源码/文档取证，所有结论带仓库内文件路径锚点。
- 约束：只做分析与映射，不改代码；结论供 `ai-dev/design/nop-metadata/` 后续演进决策参考。

## 调研对象总览

15 个项目、六条技术路线 + AI 融合路线：

| # | 项目 | 路线 | 语言 | 一句话定位 |
|---|------|------|------|-----------|
| 1 | apache/jena | RDF/OWL 全家桶 | Java | 语义网全栈框架：RDF + SPARQL + OWL API + 规则推理 + TDB/Fuseki |
| 2 | eclipse-rdf4j/rdf4j | RDF/OWL 全家桶 | Java | 模块化 RDF 框架：Repository/SAIL 分层可插拔存储栈 |
| 3 | owlcs/owlapi | RDF/OWL 全家桶 | Java | OWL 2 规范 Java 参考实现：公理级 API + 变更对象化（Protégé 的底座） |
| 4 | protegeproject/protege | 本体工程工具链 | Java | 斯坦福 OWL 本体桌面编辑器，插件化架构 |
| 5 | ontodev/robot | 本体工程工具链 | Java | 本体自动化 CLI（merge/extract/template/reason/verify/report/diff） |
| 6 | rdflib/rdflib | 本体工程工具链 | Python | 纯 Python RDF 库：Graph + SPARQL 1.1 + 图语义 diff |
| 7 | pwin/owlready2 | 本体工程工具链 | Python | Pythonic OWL 本体库（Owlready2 镜像，官方开发在 Bitbucket jibalamy/owlready2） |
| 8 | linkml/linkml | schema 建模语言 | Python | YAML 单文件 schema → 40+ 生成目标（JSON Schema/OWL/SHACL/Java/TS/GraphQL/SQL…） |
| 9 | vaticle/typedb | 类型化知识图谱 | Rust | entity/relation/attribute 三根类型 + 角色接口的类型化数据库 |
| 10 | terminusdb/terminusdb | 类型化知识图谱 | Prolog/Rust | "git for data"：JSON-LD schema + branch/merge/diff/migration |
| 11 | ontop/ontop | 虚拟知识图谱 | Java | OBDA 引擎：关系库虚拟化为 KG，SPARQL 翻译成 SQL 下推 |
| 12 | neo4j-labs/neosemantics | 属性图桥接 | Java | Neo4j 插件：RDF 无损导入导出 + SHACL 校验 + 微推理 |
| 13 | microsoft/graphrag | AI+KG | Python | LLM 从文本抽图 + 社区摘要 + global/local 检索（维护模式） |
| 14 | HKUDS/LightRAG | AI+KG | Python | 轻量双层（图+向量）RAG，增量更新 + custom KG 注入 |
| 15 | neo4j-labs/llm-graph-builder | AI+KG | Python | Neo4j Labs：LLM 把非结构化文档构建为 Neo4j 图谱（含运行时 schema 约束） |

> 调研执行注记：15 个仓库全部以 `git clone --depth 1` 下载（脚本多轮重试）。执行中发现三个仓库初始 URL 组织名有误（`owlcollab/owlapi`→`owlcs/owlapi`、`pyonto/owlready2`→`pwin/owlready2` 镜像、`neo4j/llm-graph-builder`→`neo4j-labs/llm-graph-builder`），GitHub 对不存在的仓库返回 "Repository not found"（表现为要求认证），修正后一次通过——排查教训见 §六.3 前注。owlready2 官方开发仓库在 Bitbucket（`bitbucket.org/jibalamy/owlready2`，其 README.rst "Links" 节自证），按任务要求从 GitHub 镜像 `pwin/owlready2` clone。

## 一、RDF/OWL 全家桶：jena / rdf4j / owlapi

### 1.1 Apache Jena

- **定位**：Java 语义网全栈（约 21 个主模块，主源码约 4700 个 Java 文件，`README.md`、`archived-modules.md`）。
- **本体原语**：两套 OWL API——经典 `jena-core/.../ontology/OntModel.java`（OntClass/OntProperty/Individual/Restriction/UnionClass 约 40 接口）与新一代 `jena-ontapi/.../OntModelFactory.java`（含 SWRL 规则 `CreateSWRL.java`、本体头部 `model/OntID.java`）。约束层有 SHACL（`jena-shacl/.../GraphValidation.java`）与 ShEx 双引擎。
- **schema-as-code 与版本化**：RDF 天然文本化（RIOT 全格式读写 `RDFDataMgr.java`）；`OntDocumentManager` 自动递归解析 `owl:imports`；`OntID` 显式建模 `owl:versionIRI`；**`jena-rdfpatch` 的 `RDFPatch.java`/`RDFChanges.java` 把变更表示为可序列化、可回放、可审计的补丁流**。无专门 schema 迁移框架。
- **治理**：Fuseki 数据级授权（`jena-fuseki-access/.../AuthorizationService.java`）+ 系统化 `THREAT_MODEL.md`。
- **AI/LLM**：无。仅 `AGENTS.md` 面向 AI agent 的仓库文档实践。
- **对 nop 的启示**：借方法学不借引擎——①RDFPatch 模式 ≈ 元数据变更审计/回放流（nop 有 `NopMetaModelChangedEvent` 事件，但无补丁式变更流）；②import 管理 + ja: Assembler 声明式装配 ≈ 目录模块化加载；③SHACL 约束与数据分离、返回结构化校验报告。不适用：TDB/Fuseki/SPARQL/OWL 推理整套引擎栈。

### 1.2 Eclipse RDF4J

- **定位**：模块化 RDF 框架（core 约 20 子模块，约 2600 个 Java 文件）。
- **架构模式（最大价值）**：SAIL 可插拔存储栈（`core/sail/`：memory/lmdb/nativerdf/inferencer/shacl 逐层叠加）+ Repository 统一门面（`core/repository/api/RepositoryConnection.java`）——存储、推理、验证皆为可插叠层。
- **验证下沉**：`core/sail/shacl/ShaclSail.java` 把 SHACL 校验做成存储层组件，**写入事务内拦截**并返回结构化验证报告（`package-info.java` 自述；扩展文档 `site/content/shacl-extensions.md`）。
- **版本化**：弱。无 `owl:imports` 解析机制；有模型级 diff（`RepositoryUtil.difference`）与声明式存储配置（`RepositoryConfig` + `ConfigTemplate`）。
- **AI/LLM**：无（仅 `site/content/about.md` 引用商业产品营销语）。
- **对 nop 的启示**：①验证做成可插拔层并在写入路径拦截（对比 nop 目前 xmeta 约束散在应用层）；②"存储实例用声明式模板配置"与 nop Delta 装配同构；③模型 diff 基元。不适用：其 SPARQL/联邦/序列化引擎。

### 1.3 OWL API（owlcs/owlapi）

- **定位**：OWL 2 规范的 Java 参考实现与 API（Protégé 底座），1835 个 Java 文件、api/impl/contract/parsers 等多模块（`README.md`、根 `pom.xml`）。
- **公理级建模**：`OWLAxiom`（`api/.../model/OWLAxiom.java:32`）+ `AxiomType.java:47-85` 枚举 OWL 2 全部约 38 种公理类型（SubClassOf/EquivalentClasses/DisjointClasses/PropertyDomain/Range/SubPropertyChain/HasKey/SWRLRule…，每个带 isLogical 元标志）——本体语义的全部构造都有强类型 Java 接口。
- **变更对象化（独特资产）**：所有修改走变更对象——`AddAxiom/RemoveAxiom/AddImport/AnnotationChange`，抽象基类 `OWLOntologyChange.java:27` 提供 `reverseChange()`(:170)；`OWLOntologyChangeRecord`/`ChangeDetails` 可序列化，带变更监听器与 veto 体系——**"修改"是一等数据而非副作用**，天然是审计日志/undo/审批回滚模型。
- **版本化**：`OWLOntologyID.java:218` 双 IRI 机制（本体 IRI + versionIRI）；imports 自动加载链（`makeLoadImportRequest`）。核心 API 无一等 diff/patch 工具（仅 OBO 格式专用 `OBODocDiffer.java`），但变更记录可序列化可自建流水。
- **与 Jena 路线差异**：owlapi 是"公理为中心"的强类型闭世界 API（RDF 只是序列化形式），Jena OntModel 是"三元组为中心"的弱类型图视图。
- **AI/LLM**：无。
- **对 nop 的启示**：①变更对象化 + 可逆变更记录（`reverseChange`）是 P0 借鉴（元数据改动审计/审批回滚把"修改"建模为一等数据）——与 jena RDFPatch、robot Diff 共同构成"变更即数据"三重证据；②公理类型注册表（AxiomType 枚举 + 按类型索引）是元模型约束/关系分类注册的范式；③双 IRI 版本寻址。不适用：OWL 开放世界语义与 tableaux 推理（isSatisfiable/isEntailed）对 CRUD 型目录过重；内存实现无持久化。

## 二、本体工程工具链：protege / robot / rdflib / owlready2

### 2.1 Protégé

- **定位**：OWL 2.0 桌面编辑器，模块 protege-common/editor-core/editor-owl/desktop（`pom.xml`），OWL 能力构建在 OWL API 上。
- **三个可借鉴机制**：①推理器插件 SPI（`protege-editor-owl/.../model/inference/ProtegeOWLReasonerPluginLoader.java`）——校验/推理引擎可插拔范式；②`model/ChangeListMinimizer.java` 以变更操作列表（而非快照）记录演化，是 undo/审计的基础；③`ui/explanation/` 推理解释 UI——对语义层"派生结果为什么是这样"的答案面。
- **不适用**：Swing 桌面 UI、OSGi/Felix 运行时（`protege-desktop/src/main/felix/conf/config.xml`）、无 CLI/headless、无 SKOS 专门支持、无 AI。

### 2.2 ROBOT（本体界的构建工具链，本次调研性价比最高的项目之一）

- **定位**：OBO 生态的本体自动化 CLI + Java 库；Command（CLI/IO）与 Operation（纯逻辑）分离架构（`README.md` Design 节），命令 `--input/--output` 管道可链式（`docs/chaining.md`）。
- **命令集**（`robot-command/.../robot/`）：Annotate/Merge/Extract（SLME 模块抽取）/Filter/Reason/Materialize/Relax/Reduce/Query（SPARQL + Jena TDB）/Diff/Template/Export/Verify/Report/Measure/Mirror/Convert/Python（Py4J 网关）。
- **四大 killer 能力**：
  1. **Template/Export 双向表格通道**（`docs/template.md`）：CSV/TSV 按模板串批量生成本体，错误输出为结构化表（单元格坐标 + 规则 CURIE + 消息）；Export 反向导出 csv/tsv/xlsx（`docs/export.md`）——"业务人员用 Excel 维护术语表"的完整闭环。
  2. **分级质检 + CI 门禁**：`ReportCommand` 内置 30+ 条 ERROR/WARN/INFO 分级质检查询（`docs/report_queries/`）；`VerifyCommand` "SPARQL SELECT 即规则，查出行即违规、CI 挂掉"（`docs/verify.md`）。
  3. **语义 diff + edit/release 双阶段**：`DiffCommand` 忽略序列化差异做语义 diff、输出 markdown/HTML（`docs/diff.md`，典型用法 `edit.owl` vs `release.owl`）；`AnnotateCommand` 写 version-iri；发布由 Make/ODK 编排（`docs/make.md`）。
  4. **Reason 前置 incoherency 校验**，失败非零退出 + `-D` 抽最小不可满足调试模块（`docs/reason.md`）。
- **AI/LLM**：无。
- **对 nop 的启示**：直接映射到 nop-metadata 治理实体——①Glossary/Classification 走"表格批量导入 + 单元格级错误报告"，补齐 `05-metadata-import.md` 之外的业务语义批量维护路径；②质量规则体系（已有 `NopMetaQualityRule`）可吸收"查询即规则 + 严重级别 + CI 门禁"模式扩展到**元数据本身的质检**；③MetaModule 已有 DRAFTING/RELEASED 状态（`nop-metadata.orm.xml` dict `meta/module-status`），但缺"发布前自动生成语义级变更报告"这一环。

### 2.3 rdflib

- **定位**：纯 Python RDF 全栈库（Graph/Dataset/命名图 + SPARQL 1.1 完整实现 + 可插拔 Store，`rdflib/graph.py:431`）。
- **两个独有资产**：①`rdflib/compare.py:545-605` 的 `graph_diff`——规范化图语义 diff（规避 blank node 排序），返回 in_both/in_first/in_second；②`rdflib/plugins/serializers/patch.py` RDF Patch 序列化器——diff 可落为 add/remove 补丁。
- **SKOS 词汇表**：`rdflib/namespace/_SKOS.py` 等 30+ DefinedNamespace（`rdflib/namespace/__init__.py`）——GlossaryTerm 的 `broader/narrower/synonym/exactMatch` 全有标准词汇。
- **AI/LLM**：无。
- **对 nop 的启示**：①把 nop-metadata 目录**导出为 SKOS/OWL 图**作为对外交换格式（`NopMetaGlossary.namespaces`/`NopMetaGlossaryTerm.iri`/`conceptMappings` 字段已建模预留，见 `ai-dev/design/nop-metadata/11-enterprise-semantic-layer.md` §3.2.3/3.2.4，且 Phase 4-C 已把 SKOS/RDF 往返注册为 watch-only residual——本项目分析支持"低成本先做导出侧"）；②语义 diff + 补丁流用于跨环境元数据发布。

### 2.4 owlready2（pwin/owlready2 镜像；官方 Bitbucket jibalamy/owlready2）

- **定位**：Python 3 本体编程库 + 优化 RDF quadstore（SQLite）。README 自述可当 ORM/图对象库使用；已测试 10 亿级三元组（`README.rst`）。
- **核心形态："本体即 ORM 对象"**：OWL 类/属性/实例映射为 Python 对象（类可挂方法、`equivalent_to` 等声明式赋值）；存储层 `triplelite.py:141-175` 直接建 SQLite 表 `objs/datas/resources` + FTS，storid 整数压缩；`namespace.py:439 set_backend`。
- **推理与推断**：`reasoning.py:107/212` `sync_reasoner_hermit/pellet`（导出 NTriples → 起 Java 子进程跑推理机 → 回写推断）；**产品化取舍亮点**：`prop.py:1054/1113` 的 `indirect()`——不跑推理机、用 SQLite RECURSIVE 做传递/对称/自反的常用推断；属性链 `property_chain`（`prop.py:236-246`）直接映射 owl:propertyChainAxiom。
- **周边**：`namespace.py:493 as_rdflib_graph`（rdflib 作 SPARQL 前端）、`observe.py` 修改回调、`ntriples_diff.py` 轻量 diff（容忍 blank node 命名差异）、`rule.py` SWRL。
- **与 rdflib 差异**：rdflib 是通用 RDF 图库（不理解 OWL 语义）；owlready2 是"OWL 语义的 Pythonic ORM"，rdflib 只是它的 SPARQL 出口。
- **AI/LLM**：无。
- **对 nop 的启示**：①"本体即 ORM 对象"的 API 形态与 nop ORM 实体同构——GlossaryTerm/实体关系挂行为/校验的直接参考；②关系型库上存图/层级（自引用实体）+ RECURSIVE 传递闭包查询的极简样板，适用于 nop 血缘/标签层级的祖先链查询；③`indirect()` 表明"常用推断不必上推理机"的取舍与 §7.3"不引入推理"结论互相印证。不适用：单进程 SQLite 后端（0.12 弃用 PostgreSQL）；推理靠外部 Java 进程临时文件交换；无变更审计流。

## 三、类型化知识图谱：typedb / terminusdb

### 3.1 TypeDB

- **定位**：强类型关系型数据库，`entity/relation/attribute` 三根类型 + 继承 + 角色接口（`README.md`）。
- **建模原语**：`relation mentorship, relates mentor/trainee` + `entity plays mentorship:mentor`——**关系一等公民、实体经"角色接口"挂接**（`concept/type_/relates.rs`、`plays.rs`）；属性 `owns` + 约束注解体系 `@key/@unique/@card/@regex/@values/@doc`（`concept/type_/annotation.rs:34-46`）；TypeQL 全变量化多态查询（`$user isa user` 自动含子类型）。
- **重要事实核查**：3.x CE 版**无 rules 推理**——全仓库无 `*rule*.rs`，规则系统仅存于归档的 2.x 规范（`docs/archived/spec_old.md:563`）。引用 TypeDB "推理" 卖点必须注明版本。
- **版本化**：无 branch/diff/快照链，仅 define/redefine DDL + 整库导出迁移。
- **AI/LLM**：无。
- **对 nop 的启示**：①`relates/plays` 角色建模与 Palantir Link type 同构（`2026-09-07-palantir-ontology-on-nop-feasibility.md` 映射表 #5 的缺口），nop 语义关系目录可吸收"角色命名"进入 `NopMetaEntityRelation` 元模型；②`@values` 受控词表注解值得 nop 字段元数据纳入；③多态查询是 Delta 继承在查询侧的收益示范。不适用：自研 TypeQL+RocksDB 全栈。

### 3.2 TerminusDB（与 nop 可逆计算理念最同构的项目）

- **定位**：带 git 式协作模型的文档知识图数据库，schema 即 JSON-LD 文档，每提交即版本（`README.md`）。
- **schema 原语**：`Class/Enum/TaggedUnion/OneOf` + `@inherits`/`@subdocument`/`@abstract`/`@cardinality`/`@key`/`@documentation`/`@metadata`（`src/core/document/json.pl`，4700+ 行中枢；`src/core/document/validation.pl` 提交时校验）。自举 schema（`src/terminus-schema/README.md`："It's schemas all the way down"）。
- **版本工程（最强项）**：branch/rebase/fast-forward/squash/reset/rollup 全套（`src/core/api/db_branch.pl`、`api_squash.pl`…）；带代价函数的结构化 diff（`src/core/document/diff.pl` 的 `best_diff/patch_cost`）；patch 可 bundle/push/pull 传输（`api_bundle.pl`）；**专门的 schema migration 操作语言**（`src/core/document/migration.pl`）：`delete_class/create_class/expand_enum/replace_context...`，且区分 **weakening migration**（只放宽不破坏、可自动推断应用）与 arbitrary migration（需人工）——并对存量实例数据执行迁移。
- **派生面**：schema frames 自动生成 GraphQL（含 mutation）（`src/rust/terminusdb-community/src/graphql/{frame,schema,mutation}.rs`）+ `@documentation` 自文档化。
- **AI/LLM（实验性但有启发）**：`src/rust/terminusdb-community/src/embedding.rs`——schema 中 `@metadata.embedding {query, template}` 声明每类型的 GraphQL 抽取查询 + Handlebars 文本模板，生成 embedding 素材交外部 indexer。**"schema 驱动的向量化素材生成"，AI 能力以 @metadata 挂在类型上而不侵入内核**。
- **对 nop 的启示**：①migration.pl 是"机器可判定的 Delta 合并策略"的数据库级模板——weakening/arbitrary 分层可直接映射为 nop Delta 合并的"可自动应用 / 需审批"两级；②验证了"模型即资源 + 差量覆盖链 + 补丁可传输"在数据内核层成立，Nop 可逆计算不是孤例；③schema→GraphQL→文档→embedding 模板全派生链与 nop xmeta 派生路线同向，其 `@metadata` 扩展机制值得 XDef 扩展点参照。不适用：Prolog 内核、JSON-LD 风格、Datalog 推理。

## 四、虚拟知识图谱与 RDF 桥接：ontop / neosemantics

### 4.1 Ontop

- **定位**：OBDA 引擎——数据不搬家，SPARQL 在线翻译成 SQL 下推（`README.md` L17-20）。
- **核心机制**：mapping-as-code（`.obda` 纯文本：target 三元组模板 + source SQL，或 W3C R2RML，两套互转）；SPARQL→SQL 翻译管线 `QuestQueryProcessor.java`：解析 → 统一中间表示 IQ → **本体推理下的查询重写**（TreeWitness 算法 + 饱和 TBox）→ unfold 成源端 SQL → 优化下推；`/ontop/reformulate` 调试端点直接返回翻译后 SQL（`ReformulateController.java`）——**翻译过程完全可解释**；`DirectMappingBootstrapper.java` 连库自动生成初始 mapping + 本体再人工精修；DB 元数据离线提取序列化缓存（`RDBMetadataExtractorAndSerializerImpl.java`）。
- **AI/LLM**：无。
- **对 nop 的启示**（对照 nop-metadata 联邦查询）：`MetaAggregationExecutor` 七路径分派是**执行期**分派；Ontop 补的是上游**编译期语义展开层**——"先按本体/语义层展开查询，再整条下推，可缓存、可输出最终 SQL"。三个直接可落地借鉴：①从 `NopMetaDataSource/NopMetaTable` 元数据自动生成语义映射初稿（bootstrap 模式）；②源库 schema 离线序列化缓存；③语义展开层可观测（输出下推 SQL）。不适用：单源假设、SPARQL/RDF 栈；nop 已有跨库 JOIN/内存合并仍是独立必要能力。

### 4.2 neosemantics (n10s)

- **定位**：Neo4j 属性图上的 RDF 桥：无损导入导出 + SHACL 校验 + 本体导入 + 微推理（`README.md`）。
- **机制**：URI→属性图映射收敛为枚举策略（`handleVocabUris: SHORTEN/KEEP/MAP/IGNORE`，`RDFToLPGStatementProcessor.handleIRI()` L223-238）；**"无损往返"是明确验收标准**（导出用存档配置重放原三元组，`docs/.../export.adoc` L389）；SHACL 编译成 Cypher 在图上执行、**只校验本次写入的 touched nodes**（`validation/SHACLValidator.java`）；映射元数据存图内节点 `_MapDef`（"元数据即数据"，动态热更但无版本化）；诚实的产品边界：mapping 只支持 1:1 术语改名（`docs/.../mapping.adoc` L13-14 WARNING）。
- **AI/LLM**：无（相似度是符号图距离，非向量）。
- **对 nop 的启示**：①语义层↔物理模型的命名/多值/类型映射应收敛为**目录级可配置策略**并以无损往返为验收；②约束校验编译进查询/下推条件而非执行后内存过滤；③简单字典式映射（可热更）与表达式级映射（走 DSL + 执行器）分层，勿一上来做通用对应引擎。不适用：物化到 LPG 与 nop 虚拟联邦相反；映射存库无文件化版本化与 Delta-as-code 理念相悖。

## 五、schema 建模语言：linkml

- **定位**：YAML 单文件 schema 唯一事实源 → 40+ 生成目标（`packages/linkml/pyproject.toml:152-192` 的 `gen-*` 注册表：jsonschema/owl/shacl/shex/rdf/pydantic/**java**/**typescript**/**graphql**/sqlalchemy/openapi/excel/doc/...）。
- **建模原语**（元模型 `packages/linkml_runtime/.../linkml_model/meta.py`）：
  - **槽（Slot）一等公民**：`SlotDefinition`（meta.py:2581）独立命名，类经 `slots` 引用 + `slot_usage` 逐类覆写；槽自身有 `is_a/mixins` 复用——"字段定义一次、逐类精化"。
  - **约束字典极全**：required/multivalued/精确基数/pattern/**structured_pattern**（syntax+settings 模板正则）/min-max value/equals_*/none_of-any_of-all_of/has_member/array（N 维）/unit（QUDT 单位）。
  - **语义映射内置进元模型**：Element 基类统一携带 `exact_mappings/close_mappings/broad_mappings/narrow_mappings`（直接绑定 SKOS，`linkml_model/mappings.py`）+ `deprecated`（字符串，含 replacement 指引字段）+ `in_subset` + 枚举 PV 的 `meaning`（CURIE 指向本体词条）与 `reachable_from`（本体图可达性动态值集）。
- **生成器架构三层**：`Generator` visitor 基类（`utils/generator.py:84`）→ `OOCodeGenerator` 中间表示（`generators/oocodegen.py:101`，OOClass/OOField 供 java/ts/go/rust 共用）→ `LifecycleMixin` 生成钩子（`generators/common/lifecycle.py`）；`generators/projectgen.py` 一份配置驱动多 generator 批量产出。
- **版本化**：自由 `version` 字符串 + 元模型 SemVer 流程（`ModelChanges.md`）；**本仓库无 schema diff 工具**（SchemaView 只是只读遍历；diff 在外部生态）。
- **AI/LLM**：`docs/howtos/generate-ai-prompts.md` 教"schema → 结构化提示词 → LLM 抽取回填 schema 校验"；`AI_COVENANT.md`/`AGENTS.md` 社区规范。定位是文档级用例而非运行时。
- **对 nop 的启示**（直接校准 XDef 路线）：①**slot 一等化 + slot_usage** 是 xmeta 可演进方向——可全局命名的字段定义 + 实体级覆写 + 字段 mixin，Glossary/治理标注可天然挂到字段粒度；②**约束投影框架**：visitor + OO 中间层 + 钩子三段式，把 XDef 约束到 GraphQL/表单/ORM 校验的翻译收敛为一个投影框架（对应 nop "一个 xmeta 多目标产物"的工程化样板）；③**SKOS 映射字段做进元模型标准属性段**（而非外挂表），使 nop 模型未来可零成本导出 RDF/SKOS。不适用：RDF-first 世界观全盘化、Python 工具链、无 diff/lifecycle（nop 反而更完整）。

## 六、AI+KG 融合：graphrag / LightRAG / llm-graph-builder

### 6.1 Microsoft GraphRAG

- **定位**：LLM 从叙事文本抽图 + Leiden 层次社区 + 社区报告 + global/local 双检索（`README.md`；已进入维护模式）。
- **管线**：standard/fast/update 三工作流（`index/workflows/factory.py`）；抽取 prompt 带 `{entity_types}` 占位符 + few-shot + gleaning 循环（`prompts/index/extract_graph.py`）；**实体类型可配置但极弱**——只是拼进 prompt 的字符串列表，无关系类型、无属性 schema、无输出校验（`config/models/extract_graph_config.py`）。
- **检索**：local search（实体描述向量 → 邻域子图 + 社区报告 + 源文本混装 prompt）；global search（社区报告分批 map-reduce）；嵌入对象固定三类（`config/embeddings.py`）。无图数据库——parquet/csv KV 存储抽象。
- **本体角色：schema 不是一等公民**。taxonomy 是被推导物（甚至可 `--discover-entity-types` 让 LLM 自动发现）而非约束物；后果可观察：社区报告模板通用化，跨领域只能靠 `--domain` 文案微调。
- **对 nop 的两个高价值借鉴**：①**BYOG 表契约**（`docs/index/byog.md`）：外部结构化图只需 entities/relationships 两张表 + 只跑 summarize 工作流即可获得 global search——"关系型元数据目录 → 旁路 GraphRAG 摘要层"成本为零；②社区检测 + LLM 社区报告作为**预摘要层**，承接语义层"概览类/全局类"问题。不适用：对结构化元数据做 LLM 抽取纯属浪费；无在线图存储与事务。

### 6.2 LightRAG

- **定位**：轻量双层（图+向量）RAG：六种查询模式（local/global/hybrid/naive/mix/bypass，`lightrag/base.py:93`）、文档级增量更新 + 崩溃恢复 journal、四类可插拔存储抽象（`lightrag/kg/__init__.py`）。
- **两个结构化本体注入点（GraphRAG 没有）**：
  1. `entity_type_prompt_file` YAML profile（`lightrag/prompt.py` `resolve_entity_extraction_prompt_profile`；样例 `prompts/samples/entity_type_prompt.sample.yml`）：可版本化地替换实体类型 guidance + few-shot 示例——**"目录驱动的抽取约束"的现成入口**（但类型仍不进白名单，`operate.py:662` 只清洗不拒收）。
  2. `ainsert_custom_kg(custom_kg)`（`lightrag/lightrag.py:4308`）：调用方直写自构实体/关系，与抽取图同一存储层融合——**外部本体融入图谱的正式通道**。
- **查询路由**：查询先拆 high-level/low-level 关键词（`prompt.py:484`），local=实体向量+邻域子图，global=关系向量，mix+reranker 兜底。
- **对 nop 的启示**：①Glossary/Classification 编译成 YAML profile 注入抽取；②元数据目录（表/字段/术语/标签）经 `ainsert_custom_kg` 直写进 AI 检索图——结构化数据走注入而非抽取；③语义层请求按意图路由到不同检索模式。不适用：其抽取-合并管线对结构化元数据反而引入不精确（归一化破坏精确标识符）。

### 6.3 neo4j-labs/llm-graph-builder

- **定位**：Neo4j Labs 官方"非结构化文档 → LLM 抽取 → 知识图谱"FastAPI 服务（README；要求 Neo4j ≥5.23 + APOC）。链路：多源上传 → 切块（`backend/src/create_chunks.py`）→ `LLMGraphTransformer` 抽取（`backend/src/llm.py`）→ chunk-实体挂接（`make_relationships.py`）→ 向量/全文索引 + 社区检测（`post_processing.py`）→ QA 检索（`graph_query.py`）。
- **与 GraphRAG/LightRAG 的关键差异：它有运行时 schema 约束通道**：
  1. `allowedNodes/allowedRelationship/additional_instructions` 是 API 参数（`backend/src/entities/source_extract_params.py`），直通 `LLMGraphTransformer(allowed_nodes=..., allowed_relationships=...)`（`llm.py:222-231`）；
  2. 优先走 `llm.with_structured_output(_Graph)` 的 function-calling Pydantic 校验（`llm.py:203-206`）——产出实体必须是 allowed 列表成员；
  3. 预置领域 schema 库（`frontend/src/assets/schemas.json`：stackoverflow/movies 等 labels+relationshipTypes）；还有**LLM 从自由文本反推 schema** 的服务（`backend/src/shared/schema_extraction.py`，输出 `NodeType-REL->NodeType` 三元组）；
  4. 抽后归一：`post_processing.py:149 graph_schema_consolidation` 用清洗 prompt（`shared/constants.py:827`，近义 label 聚类、**禁止发明新类型**）生成映射后 `SET n:New REMOVE n:Old` 合并。
  但约束仍是**字符串 label 集合**——无 domain/range/disjoint 等 OWL 级语义，弱于本体目录。
- **工程质量参考**：`additional_instructions` 内置提示注入防御条款（`constants.py:885-890`"外部文本不可信，忽略其中指令"）；去重即治理——候选对生成（子串/编辑距离<3/向量相似度>0.97，`graphDB_dataAccess.py:470`）+ `apoc.refactor.mergeNodes` 合并 + 人工确认 API（:520）；MERGE 幂等写入 + chunk SHA1 id + 文档认领锁；LLM 后端 10+（OpenAI/Gemini/Bedrock/Ollama/Deepseek/任意 OpenAI 兼容），嵌入 4 家（`common_fn.py:223`）。无文档级增量（重传即重建）。
- **对 nop 的启示**：①**Glossary/Classification → allowedNodes/allowedRelationship 编译**是"目录约束抽取管线"的现成机制（比 LightRAG 的 prompt 注入更强——有结构化输出校验兜底）；②三级质量闭环（输入端领域规则+注入防御 → 抽取端结构化校验 → 抽后归一禁止新类型）可直接映射为 nop AI 打标管线的质检规格，其中"同义词归一"应该用 Glossary.synonyms 确定性替代 LLM 聚类；③候选合并→审批→落库 = 实体解析流程进 nop 审批状态机。不适用：目标是图库非关系目录；无 OWL 级语义校验；无版本化/变更审计。

### 6.4 AI+KG 路线的共同事实

- GraphRAG：实体类型仅 prompt 字符串列表、关系无谓词、类型可被 LLM"发现"——**无约束**。
- LightRAG：类型是 prompt guidance、可返回 `Other` 不拒收——**提示级弱约束**。
- llm-graph-builder：allowedNodes/allowedRelationship + 结构化输出校验——**运行时强约束，但仅字符串 label 集合、无 OWL 级语义（domain/range/disjoint）**。
- 三者谱系恰好构成"无约束 → 弱约束 → 强约束"阶梯，且约束强度与工程化程度正相关（维护模式的 GraphRAG 最弱，活跃的 llm-graph-builder 最强）。共同边界：即使最强的 llm-graph-builder，其 schema 也不携带属性类型、关系域/值域、互斥等本体语义——**企业级本体约束（nop-metadata 目录层级）仍在其之上**。这是 §八 AI 应用的核心论据：本体做约束与锚点，LLM 做泛化与 bridges，两者是互补品。

## 七、与 nop-metadata 的横向对比

### 7.1 nop-metadata 现状基线（证据）

- **39 个 ORM 实体**（`nop-metadata/model/nop-metadata.orm.xml`）：目录层（MetaModule/DataSource/OrmModel/Entity/EntityField/EntityRelation/EntityIndex/Dict/Domain/SemanticType）+ 语义层（MetaTable/Measure/Dimension/Filter/Join/Pipeline + `MetaAggregationExecutor` 七路径联邦分派）+ 治理层（Glossary/GlossaryTerm/Classification/Tag/TagLabel/BusinessDomain/DataProduct/DataContract/QualityRule/Checkpoint/Result/Score/Profiling/Reconciliation/LineageEdge/Manifest/ModelChangedEvent）。
- **定位**（`ai-dev/design/nop-metadata/00-vision.md`）：联邦式元数据管理层——"这个字段在哪里、什么类型、属于哪个数据源、怎么查它"。
- **语义层设计**（`11-enterprise-semantic-layer.md`）：吸收 OpenMetadata 的 Classification/Glossary/TagLabel（含 Suggested/Confirmed 审批态、labelType=Manual/Propagated/Automated/Derived、血缘传播引擎设计、规则式 AutoClassification 裁定）。
- **AI 现状**（`07-ai-integration.md`，draft）：单通道——"GraphQL schema 自动暴露即 AI 接口"，`getMetadataContext(question, tableName)` 概念入口；平台侧组件已就位：`nop-ai/nop-ai-tools/.../GraphQLToolSetFactoryBean.java`（GraphQL schema → LLM 工具）、`nop-ai-mcp-server`、`nop-ai-agent`、`nop-ai-rag`。
- **Palantir 映射**（`2026-09-07-palantir-ontology-on-nop-feasibility.md`）：语义半区可行性高，缺显式 ObjectType/LinkType/ActionType/Interface 声明层（L0 `ontology.xdef` 提案）。

### 7.2 本体原语覆盖矩阵

| 本体原语 | jena/rdf4j/owlapi | protege/robot | linkml | typedb | terminusdb | ontop/n10s | graphrag/lightrag | **nop-metadata** |
|---|---|---|---|---|---|---|---|---|
| 类型/实体定义 | OntClass | △(编辑器) | Class | entity | Class | △(mapping) | 无约束 | MetaEntity/MetaTable ✅ |
| 关系/链接类型 | OntProperty | △ | Slot(一等) | **relation+角色** | property | △ | 无谓词 | MetaEntityRelation(外键级) ⚠️ |
| 属性/槽 | △ | △ | **Slot+全约束字典** | attribute+注解 | @cardinality 等 | △ | 无 | EntityField(基础约束) ⚠️ |
| 抽象/接口/多态 | UnionClass | △ | abstract/mixins | abstract | @abstract/OneOf | ✗ | ✗ | ✗（XDef union 可模拟） |
| 约束校验+结构化报告 | SHACL/ShEx | verify/report | **生成到 SHACL** | 注解 | 提交时校验 | SHACL→Cypher 增量 | ✗ | xmeta 约束+validator（无报告规范）⚠️ |
| 推理 | 规则/OWL | ELK/HermiT 插件 | ✗ | ✗(3.x 已移除) | Datalog | 微推理 | ✗ | ✗（nop-rule 决策树在旁路） |
| 语义 diff | RDFPatch(流) | **DiffCommand** | ✗ | ✗ | **best_diff+patch_cost** | 无损往返验收 | ✗ | Module 版本+ChangedEvent（无 diff）⚠️ |
| branch/merge | ✗ | edit/release(Make) | ✗ | ✗ | **全套** | ✗ | ✗ | Drafting/Released 两态 ⚠️ |
| schema 迁移操作语言 | ✗ | ✗ | ✗ | 整库导出 | **migration.pl(weakening)** | ✗ | ✗ | ✗ |
| 术语表/SKOS | 词汇表 | template/export | **元模型内置 mappings** | ✗ | ✗ | SKOS 相似度 | ✗ | Glossary/Classification/TagLabel ✅（缺 SKOS 往返） |
| 生成器矩阵 | ✗ | ✗ | **40+ 目标** | ✗ | GraphQL 派生 | SPARQL endpoint | ✗ | xmeta→GraphQL/AMIS/Java（缺 TS/Python） |
| mapping-as-code | ✗ | ✗ | ✗ | ✗ | ✗ | **.obda/R2RML** | ✗ | MetaTable 外部表注册 ⚠️ |
| AI 注入点 | ✗ | ✗ | prompt 生成文档 | ✗ | @metadata.embedding | ✗ | prompt/custom_kg | 仅 GraphQL 自动暴露 ⚠️ |

**结论**：nop-metadata 在"治理/术语/分类"列是全场最强之一（与 OpenMetadata 同级，超过本次 15 个项目）；在"槽/约束字典""语义 diff/迁移""AI 双向集成"三列存在结构性缺口；"推理"列全场都弱（连 TypeDB 3.x 都删了），nop 不必补 OWL 推理，nop-rule + 查询级约束已够。

### 7.3 综合借鉴清单（按优先级）

**P0（补结构性缺口，与既有 design 方向一致）**
1. **元数据语义 diff + 变更报告**（来源：robot DiffCommand、terminusdb diff.pl、rdflib graph_diff、owlapi 变更对象化）：MetaModule 发布前自动生成"上版 vs 本版"结构化变更报告（新增/删除/改名/约束收紧），作为 `meta/module-status` DRAFTING→RELEASED 流转的强制产物。变更应建模为一等可序列化对象（owlapi `OWLOntologyChange.reverseChange()`、jena RDFPatch 双重证据），支撑审计/undo/审批回滚。对应 Palantir 文档缺口 #12"变更治理"。
2. **weakening/arbitrary 迁移分层**（来源：terminusdb `migration.pl`）：定义元模型变更的操作语言，"只放宽不破坏"类变更可自动应用，破坏类走审批——机器可判定的 Delta 合并策略，同时反哺 nop 全平台的 Delta 机制。
3. **本体声明层（ontology.xdef）**（来源：typedb relates/plays、terminusdb Class、linkml Class/Slot、Palantir 文档 L0 提案）：在 orm/xmeta 之上显式声明 ObjectType/LinkType（含角色命名）/ActionType/Interface，codegen 展开到既有管线。nop-metadata 提供数据面，不替代。

**P1（提升治理/AI 面质量）**
4. **表格双向通道**（来源：robot Template/Export + 单元格级错误报告）：Glossary/Classification/Tag 的 Excel/CSV 批量导入导出，单元格坐标+规则 ID 的结构化错误报告。
5. **元数据质检规则 CI 化**（来源：robot verify/report）：查询即规则 + ERROR/WARN/INFO 分级 + 门禁，扩展 `NopMetaQualityRule` 到"元数据本身的质检"（缺 label、命名不合规范、悬空引用等）。
6. **SKOS/RDF 导出**（来源：rdflib SKOS 词汇、linkml mappings 字段）：Glossary=skos:ConceptScheme、Term=skos:Concept、broader/narrower/exactMatch；`iri/conceptMappings/namespaces` 字段已预留，先做导出侧（Phase 4-C residual 降风险启动）。
7. **联邦查询编译期语义展开层**（来源：ontop 先展开后下推 + bootstrap + 离线元数据缓存）：在 `MetaAggregationExecutor` 上游补"语义展开 → 可解释下推 SQL 输出"。
8. **约束投影框架 + TS/Python 生成器**（来源：linkml 三层 generator 架构）：收敛 XDef→多目标翻译；补 Palantir 文档缺口 #14。

**P2（方向性）**
9. 槽一等化 + slot_usage（linkml）——xmeta 长期演进。
10. 校验下沉写入路径 + 增量校验（rdf4j ShaclSail、n10s touched-nodes）。
11. `@metadata` 式 AI 挂载扩展点（terminusdb embedding.rs）——AI 配置挂类型上不侵入内核。

**不采纳/否决（延续既有裁定并新增证据）**
- **引入 RDF/OWL/SPARQL 运行时栈**：15 个项目中 8 个（jena/rdf4j/owlapi/protege/robot/rdflib/owlready2/n10s）本质都在维护 RDF 生态，但无一提供 nop 缺失的能力；推理全场弱（TypeDB 3.x 移除 rules）；与既有裁定一致（`agent-survey/2026-08-01-trustgraph-context-graph-analysis.md`、Palantir 文档 §4.4）。只借"词汇/方法学"，不引"存储/引擎"。
- **物化型本体索引（n10s 路线）**：与 nop 虚拟联邦、数据不搬家前提相反；GraphRAG 的 parquet 批处理同理不适合作常驻目录底座。
- **本体映射元数据存数据库**（n10s `_MapDef`）：无版本化/离线 review/可 diff，与 Delta-as-code 相悖；映射应文件化走 codegen。
- **LLM 无约束抽图替代目录**：GraphRAG/LightRAG 均证明缺本体约束导致泛化模板化；方向应是"目录约束 + LLM 泛化"互补（§八）。

## 八、AI 介入后 ontology 如何应用（核心增量章节）

### 8.1 总命题：本体从"人读的文档"变为"AI 的操作系统接口"

无 AI 时，本体的消费者是人（分析师/治理者）；AI 介入后，**本体成为 LLM/Agent 的四重基础设施**——约束源、检索结构、上下文供给、治理边界。15 个项目中 AI+KG 路线（graphrag/lightrag/llm-graph-builder）与传统路线的融合点全部落在这四角色上。

### 8.2 角色一：本体作为 AI 的约束源（Constrain）

LLM 抽取/生成最大问题是泛化无界。本体提供硬约束：

| 机制 | 来源项目 | nop 落地方式 |
|---|---|---|
| 实体类型白名单注入抽取 prompt | LightRAG `entity_type_prompt_file` YAML profile（可版本化、可 diff、fail-fast）；GraphRAG 反例（仅字符串列表、可 Other） | 把 `NopMetaGlossaryTerm`（含 synonyms）+ `NopMetaClassification/Tag` 编译成抽取 profile——"目录驱动的抽取约束"，随目录版本演进 |
| 运行时 schema 约束 + 结构化输出校验 | llm-graph-builder `allowedNodes/allowedRelationship` 直通 `LLMGraphTransformer` + `with_structured_output(_Graph)` Pydantic 校验（`llm.py:203/222`） | 约束不止进 prompt，还要进输出校验器：AI 产出的实体/关系必须落在目录白名单内才入库 |
| 抽后归一 + 禁止新类型 | llm-graph-builder `graph_schema_consolidation`（清洗 prompt 禁止发明新类型，`constants.py:827`） | 归一用 Glossary.synonyms 确定性替代 LLM 聚类；AI 不得新建目录中不存在的类型（新类型走候选→审批） |
| 提示注入防御 | llm-graph-builder `additional_instructions`（`constants.py:885-890`，外部文本不可信） | AI 处理外部文档/数据源描述时，元数据抽取 prompt 需内置不可信内容隔离条款 |
| 结构化图谱直写通道 | LightRAG `ainsert_custom_kg` | 元数据目录（表/字段/术语/标签/血缘边）以 custom KG 直写 AI 检索图；结构化数据走注入不走 LLM 抽取（GraphRAG BYOG 同结论） |
| 输出回填 schema 校验 | linkml `generate-ai-prompts.md`（LLM 输出必须过 schema 校验） | AI 生成的元数据（建议标签/建议术语/建议血缘）必须过 xmeta 约束校验再入库 |
| 图谱构建的 schema 约束 | TerminusDB 提交时校验；n10s SHACL 导入校验 | AI 写入路径与人工写入同一校验面，无特权通道 |
| 实体解析（去重合并）进审批 | llm-graph-builder 候选对（子串/编辑距离/向量相似）→ merge → 人工确认（`graphDB_dataAccess.py:470/520`） | "候选合并项 → 审批 → 落库"的实体解析流程复用 nop 审批状态机 |

### 8.3 角色二：本体作为 AI 的检索结构（Structure）

- **旁路摘要层**（GraphRAG）：对 nop 元数据图（表/字段/外键/血缘/Glossary 关联）跑社区检测 + LLM 社区报告，形成预摘要；语义层"概览类"问题（这个域有哪些主题域、表间怎么关联）直接命中报告，避免每次现场遍历。BYOG 契约让接入成本≈0。
- **查询路由**（LightRAG）：精确实体/字段级问题 → 实体向量+邻域子图；跨域聚合/血缘概览 → 关系向量/社区报告；GraphQL 语义层入口按意图路由。
- **符号检索兜底**（本次 15 个项目共同背景）：AI 检索永远叠加确定性通道——`NopMetaSearchBizModel` 全文搜索、GraphQL 精确查询——向量只做召回不做仲裁。

### 8.4 角色三：本体作为 AI 的上下文供给（Context / OAG）

Palantir 文档已论证 OAG（Ontology-Augmented Generation）是 AIP 的核心；本次调研补充三个开源侧证：

- **GraphQL schema 即工具**：nop 已有 `GraphQLToolSetFactoryBean`（nop-ai-tools）把 GraphQL schema 自动暴露为 LLM 工具，`07-ai-integration.md` 的 `getMetadataContext(question, tableName)` 即 OAG 入口雏形——**这条单通道应升级为"本体上下文包"**：给定问题，从目录取"相关对象类型+链接+术语定义+质量/密级标签+血缘邻域"打包成受控上下文，而不是裸 schema。
- **schema 驱动的向量化素材**（TerminusDB `@metadata.embedding`）：每类型声明 `{query, template}`，由 schema 生成 embedding 文本流——nop 对应物是"xmeta/xdef 声明 AI 投影（面向 LLM 的类型摘要模板）"，挂模型上不侵入运行时。
- **MCP 作为本体出口**：Palantir Ontology MCP（2026-01）验证了"外部 agent 经 MCP 发现并消费本体资源"是行业标准方向；nop `nop-ai-mcp-server` + GraphQLToolSetFactoryBean 已具备等价能力，缺的是把本体资源（对象/动作/术语）按白名单组织为 MCP tool 目录。

### 8.5 角色四：本体作为 AI 的治理边界（Govern）

AI 介入后本体同时是"限权器"：

- **AI 建议走人工既有审批流**：nop TagLabel 已有 `Suggested→Confirmed` 状态机 + `use-approval` 审批流 + labelType=Automated/Derived 追溯设计（`11-enterprise-semantic-layer.md` §3.3.3/Phase 4-B 已裁定"规则建议 → G2 审批确认"）——**AI 自动打标应复用同一通道**（`appliedBy` 记录模型标识，`reason` 记录 prompt/模型/置信度），而不是新开 AI 特权路径。这与 OntoAgent 分析（`ontology-driven-agent-vs-nop-code-index.md`）的 provenance+confidence 每关系携带的结论一致。
- **动作经本体权限面**：LLM 对数据的写操作必须走 ActionType（Palantir"动作是写入唯一受控通道"）——对应 nop 的 xbiz action + validator + data-auth + 审计拦截器，Agent 侧由 nop-ai-agent guardrails 兜底。
- **推理可解释**（Protégé explanation UI 启发）：语义层派生结果（继承的标签/传播的血缘/聚合口径）对 AI 的回答要能给出"为什么"——TagLabel.reason + LineageEdge 已存证据链，缺的是把它组装进 AI 回答的引用面。

### 8.6 AI 反哺本体（Reverse Direction）

AI 不只消费本体，也生产本体候选：

1. **自动分类/打标**：`NopMetaClassification.autoClassificationConfig` 已建模规则引擎（Phase 4-B 裁定：规则 only、用户显式触发、产出 Suggested 态）——LLM 可作为第二 recognizer 并入同一通道（列名+样本+术语定义 → 建议标签），审批流不变。
2. **术语候选发现**：对 AI 抽取图与目录做差集（GraphRAG/LightRAG 抽到的实体类型、高频概念 vs `NopMetaGlossaryTerm`）→ 新术语/同义词候选 → 人工确认进 Glossary（对应 OntoAgent ConceptAligner 的 exact→alias→vector→graph 四步对齐）。
3. **血缘/关系补全**：LLM 从 SQL/代码/文档建议 `NopMetaLineageEdge` 候选（transformType 标记 LLM_INFERRED），走确认流。

三条共同原则：**AI 产出永远以 Suggested/候选态进入既有审批状态机；模型与 prompt 版本写进 reason/provenance；确认后的知识回流目录成为下一轮 AI 的约束源**——形成"目录约束 AI → AI 反哺目录"的飞轮。

### 8.7 nop 落地路径建议（AI 面，结合既有组件）

| 步骤 | 内容 | 复用的既有组件 | 新增工作 |
|---|---|---|---|
| A1 | 本体上下文包（OAG 入口） | `getMetadataContext` 设计 + MetaTable/Glossary/TagLabel 查询 API | 上下文组装器（问题→相关对象+术语+标签+血缘邻域） |
| A2 | 目录 → AI 检索图注入 | nop-ai-rag；LightRAG custom_kg 模式参考 | 目录变更事件（`NopMetaModelChangedEvent`）触发的增量注入器 |
| A3 | AI 打标进审批流 | TagLabel Suggested/Confirmed + use-approval + Phase 4-B 规则通道 | LLM recognizer + appliedBy/reason 规范 |
| A4 | MCP 本体出口 | nop-ai-mcp-server + GraphQLToolSetFactoryBean | 本体资源白名单 → MCP tool 目录 |
| A5 | 摘要旁路（可选） | GraphRAG BYOG 两表契约参考 | 元数据图导出器 + 社区报告任务 |

## Conclusion

- **总体判断**：GitHub 本体技术栈六条路线中，与 nop-metadata 定位真正重叠的是"类型化 KG + 建模语言 + 虚拟化"三条；RDF/OWL 栈提供方法学但不提供 nop 缺失的能力；AI+KG 路线反证了本体约束的价值。nop-metadata 的"目录+语义层+治理"底座在本次 15 个项目中没有等价物（Glossary/Classification/TagLabel 治理深度超过全部 15 个项目），**不需要重建，需要在其上补"本体声明层 + 版本工程 + AI 双向集成"三个结构**。
- **被否决方向**（否决理由见 §7.3 末）：引入 RDF/OWL/SPARQL 运行时栈；物化型本体索引；映射元数据存数据库；LLM 无约束抽图替代目录。
- **后续工作**：①`ontology.xdef` 本体声明层 → 升级 `ai-dev/design/nop-metadata/`（衔接 Palantir 文档 L0 提案）；②语义 diff + weakening migration → `11-enterprise-semantic-layer.md`/`03-version-management.md` 演进；③AI 四角色落地 A1-A5 → `07-ai-integration.md` 从 draft 升级为 design。三者均需先立 design/plan 再实施。

## Open Questions

- [ ] ontology.xdef 的 ObjectType 与 nop-dyn（运行期实体）、nop-metadata MetaTable（逻辑表）三者的边界与优先级（Palantir 文档 Open Question 1 的延续）？
- [ ] 语义 diff 的粒度：实体/字段级 diff（结构化）是否足以替代模型快照 diff？`NopMetaManifest` 快照是否应升级为含 schema 语义的 manifest？
- [ ] AI 打标的置信度是否需要独立字段（如 confidence 列），还是全部进 reason JSON？（影响 TagLabel 索引与审计查询）
- [ ] 上下文包（A1）的 token 预算策略：目录可能很大，按问题路由选多少对象/术语/血缘层？
- [ ] SKOS 导出（P1-6）是否要等 ontology.xdef 落地后再做（避免导出格式二次返工）？
- [ ] ~~owlapi/owlready2/llm-graph-builder 三个仓库 clone 完成后是否需要修订本文结论~~（已全部 clone 并分析完毕，结论已合入正文；仅补充证据强度，未改变方向性结论）

## References

### 本地调研仓库（`~/sources/ontology/`，git clone）

- jena, rdf4j, owlapi, protege, robot, owlready2, rdflib, linkml, typedb, terminusdb, ontop, neosemantics, graphrag, LightRAG, llm-graph-builder（15 个全部 clone 完成，`git -C <repo> rev-parse HEAD` 均验证通过）
- clone 脚本：`~/sources/ontology/clone-ontology-repos.sh`（多轮重试 + 坏仓库自愈，应对 GitHub 连接不稳定；执行教训：GitHub 对不存在仓库返回 404 并表现为要求认证，三个初始 URL 组织名有误已修正——`owlcs/owlapi`、`pwin/owlready2`（官方 Bitbucket jibalamy/owlready2 的镜像）、`neo4j-labs/llm-graph-builder`）

### 仓库内既有分析（本次的初步分析基线）

- `ai-dev/analysis/ontology-driven-agent-vs-nop-code-index.md`（2026-07-10）
- `ai-dev/analysis/2026-09/2026-09-07-palantir-ontology-on-nop-feasibility.md`（2026-09-07）
- `ai-dev/analysis/metadata-survey/`（DataHub/Atlas/OpenMetadata 等 13 份）
- `ai-dev/analysis/agent-survey/2026-08-01-trustgraph-context-graph-analysis.md`（RDF 栈成本裁定）

### nop 侧设计/实现锚点

- `nop-metadata/model/nop-metadata.orm.xml`（39 实体）
- `ai-dev/design/nop-metadata/00-vision.md`、`07-ai-integration.md`、`11-enterprise-semantic-layer.md`、`03-version-management.md`
- `nop-metadata/nop-metadata-service/.../MetaAggregationExecutor.java`
- `nop-ai/nop-ai-tools/.../GraphQLToolSetFactoryBean.java`、`nop-ai/nop-ai-mcp-server/`、`nop-ai/nop-ai-agent/`、`nop-ai/nop-ai-rag/`
