# nop-metadata Vision

> Status: final
> Date: 2026-07-15（2026-07-22 实现注解：所有 4 Phase 已实现并 closure audit 通过。S1/G2 于 2026-07-22 同步为 done。详见 roadmap `nop-metadata-roadmap.md`）

## 定位

`nop-metadata` 是 Nop 平台的**联邦式元数据管理层**。它统一管理两个层面的信息：

1. **元数据目录** —— 存储、索引、版本化 Nop 平台内部（ORM 模型、API 模型等）和**外部系统**（MySQL、PostgreSQL、ClickHouse 等）的元数据
2. **数据访问** —— 通过 ORM 实体的 `querySpace` 字段路由到对应存储引擎，让元数据目录中的表可以跨越不同数据库被查询（不引入额外 Driver 抽象，见架构基线 §七）

它不是 BI 可视化工具，不是报表渲染引擎，不替代 `nop-report` 的 XPT 渲染能力。

一句话概括：**你问"这个字段在哪里、什么类型、属于哪个数据源、怎么查它"，nop-metadata 回答你**。至于查出来后怎么画图，不归它管。

## 与 nop-dyn 的关系

| | nop-dyn | nop-metadata |
|---|---|---|
| 数据来源 | 用户运行态自定义实体类型 | Nop ORM 模型（编译时）+ 外部数据源（运行时） |
| 存储方式 | VIRTUAL 共享 `nop_dyn_entity` 表 / REAL 自动建表 | 元数据存固定表，业务数据通过 ORM querySpace 路由到对应引擎 |
| 存储引擎 | 只能存到 Nop 自身数据库 | 任意引擎：MySQL、PostgreSQL、ClickHouse、ES、H2 …… |
| 跨源查询 | 不支持 | ORM querySpace 多路由（实体级路由，无额外 Driver 抽象） |
| 版本化 | 无 | MetaModel 版本化（long PK，发布后不可变） |
| 场景 | 运行时动态表单/实体 | 数据目录 + BI 语义层 + 联邦查询 |

两者的关系是**互补**：nop-dyn 解决"运行时用户定义新实体类型的数据存储"，nop-metadata 解决"对所有数据源进行统一目录和访问"。如果你有一张 MySQL 的表、一张 ClickHouse 的表，想在一个视图里把它们 JOIN 起来查询——这是 nop-metadata 的范围。nop-dyn 做不到。

## 解决的问题

| 当前痛点 | 解决方式 |
|---------|---------|
| ORM 模型散落在 `model/*.orm.xml`，无统一浏览入口 | `nop-metadata` 解析所有模块的 ORM 模型，写入结构化实体，提供搜索/浏览 API |
| 模型变更后无法判断影响范围（哪些报表/API 会挂） | 版本化的模块模型 + 引用追踪 |
| 多个项目/版本之间无法对比模型差异 | 模块版本体系 + Delta 感知的 diff |
| 缺乏 BI 语义层的指标/维度独立管理 | `MetaTableMeasure` + `MetaTableField` 作为一等实体 |
| 外部数据库（MySQL/ClickHouse 等）的表无法纳入 Nop 的统一元数据管理 | `MetaDataSource` + `MetaTable`，通过扫描或注册导入 |
| 跨数据源的查询需要手写代码拼接 | ORM querySpace 路由 + MetaTable 统一语义层，查询走现有 IOrmTemplate |

## 成功标准

1. 用户可以从 UI 上浏览任意模块的 ORM 实体、字段、关系、字典——不需要打开 XML 文件
2. 用户可以基于 MetaEntity（ORM）或原生 SQL 创建视图，定义指标和维度，视图可以跨数据源
3. 模块版本的变更历史可查询、可对比
4. 新模块接入只需注册 `orm.xml` 路径，不需要写代码
5. MySQL/PostgreSQL/ClickHouse 等数据源注册后，其表可自动被发现和查询
6. MetaModule 包含 Maven 坐标和 Git 信息，支持源码追溯

## Non-goals

- 不是 BI 可视化引擎（不画图）
- 不是报表运行时（不负责渲染 XPT）
- 不是通用的数据查询网关（不替代已有的 IOrmTemplate 查询路径）
- 不是 nop-dyn 的替代品——不解决"运行态动态实体"的问题
- 不是独立的数据质量平台——质量规则嵌入元数据目录，不替代专业数据质量工具

## 设计收敛路径

1. **Phase 1** — 平台 ORM 模型导入 + 版本化 + 搜索 + 血缘模型 + 质量规则
2. **Phase 2** — 外部数据源注册 + 外部表元数据同步 + 血缘采集 + 质量执行
3. **Phase 3** — 视图定义 + 指标/维度管理（BI 语义层）
4. **Phase 4** — 联邦查询执行（基于 ORM querySpace，非 Driver 抽象）
