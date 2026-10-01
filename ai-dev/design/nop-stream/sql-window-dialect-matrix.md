# sql-window-dialect-matrix——窗口 frame 能力实跑矩阵（WI3）

> Status: active
> 日期：2026-10-02
> 负责人：仓库 owner（委托链同 `ai-dev/design/nop-stream/sql-vision-conflict-resolution.md` §2）
> 承载 plan: ai-dev/plans/nop-stream-sql/08-wi3-dialect-window-matrix.md
> D15 协议：`ai-dev/design/nop-stream/sql-subset-and-semantics.md` §6

## 1. D15 触发证据与实跑范围

- **docker 可用性实测（2026-10-02）**：`docker info` 退出码 0（daemon 运行中）——D15 主路径部分可用。
- **实跑范围**：H2（内存，无 docker 依赖）与 PostgreSQL（Testcontainers，自备镜像 postgres:16-alpine）真实执行；其余方言快照比对 + 未实测标注。
- **镜像备注**：既有 TestPostgreDialect 钉的 postgres:9.6.12 镜像已从 Docker Hub 移除（拉取超时实测）；矩阵用 postgres:16-alpine（本地经 daocloud 镜像源已有，docker tag 后供 testcontainers 使用）。PG 16 支持 GROUPS（9.6 不支持）——版本差异在此记录。

## 2. 实跑矩阵（H2 2.4.240 与 PostgreSQL 16）

| 组 | 查询形态 | H2 2.4.240 | PostgreSQL 16 |
|---|---|---|---|
| rows inline | `sum(val) over (partition by grp order by id rows between unbounded preceding and current row)` | PASS | PASS |
| range inline | `range between unbounded preceding and current row` | PASS | PASS |
| groups inline | `groups between 1 preceding and current row` | PASS | PASS |
| rows named | `over w` + `window w as (... rows ...)` | PASS | PASS |
| range named | `window w as (... range ...)` | PASS | PASS |
| groups named | `window w as (... groups ...)` | PASS | PASS |
| **对照轴：named 无 frame** | `window w as (partition by grp order by id)` | PASS | PASS |

- 证据：`TestH2WindowFrameMatrix`（nop-dao，内存 H2，输出 `WI3 matrix h2: ...` 全 PASS）与 `TestPostgresWindowFrameMatrix`（nop-dao，docker opt-in，输出 `WI3 matrix postgresql:16-alpine: ...` 全 PASS）；断言含分区累计值的精确核对。
- 对照轴结论：H2 与 PG 16 均支持 WINDOW 子句语法（独立于 frame 单位）。

## 3. features 填写结论

| dialect.xml | features 值 | 依据 |
|---|---|---|
| h2.dialect.xml | rows/range/groups = **true** | §2 H2 实跑 7/7 PASS |
| postgresql.dialect.xml（postgis、duckdb 经继承同值） | rows/range/groups = **true** | §2 PG16 实跑 7/7 PASS |
| db2/dm/mariadb/mssql/mysql/mysql5.7/oracle | 保持 false（缺省关闭），**执行未实测**（快照比对通过） | D15 降级标注 |
| h2gis | **经继承生效 true×3**（extends h2 无覆盖），执行未实测 | 继承外溢（WI3 实测 h2 开启后） |
| es/tdengine | 保持 false；排名族窗口函数登记缺失（WI4 范围外），聚合 over 编译不受影响，执行未实测 | D15 降级标注 |

## 4. 逐文件裁定（18 个 dialect.xml + selector，M3）

| 文件 | 裁定 |
|---|---|
| h2.dialect.xml | **填值 true×3**（实跑） |
| postgresql.dialect.xml | **填值 true×3**（实跑；postgis/duckdb 经继承同值） |
| db2.dialect.xml | **独立加载失败**（缺 driverClassName 等必备节点，生产经 selector 机制注入）——快照不适用、执行未实测、features 保持 false |
| dm / mariadb / mssql / mysql / mysql5.7 / oracle / h2gis .dialect.xml | 快照通过（`__snapshot/<name>.window.sql`）+ 执行未实测 + features 保持 false |
| duckdb.dialect.xml | 快照通过 + 执行未实测；features **经继承生效 true×3**（extends postgresql 无覆盖，继承外溢）；函数验证归 duckdb-integration roadmap WI8 |
| postgis.dialect.xml | 快照通过 + 执行未实测；features 经继承同 postgresql（true×3） |
| es / tdengine .dialect.xml | 排名族窗口函数登记缺失（WI4 范围外），聚合 over 编译不受影响，执行未实测，features 保持 false |
| default.dialect.xml | 基文件（承载三能力位集中缺省声明 false），不可独立加载，不适用 |
| geo-support.dialect.xml / window-expr-support.dialect.xml | 基文件，不适用（window-expr-support 仅函数登记） |
| oracle-reverse.dialect.xml | 反向工程工具文件，不适用 |
| selector/（9 个 yaml） | 选择器目录，无能力位声明，不适用 |

## 5. 快照证据

`nop-persistence/nop-orm-eql/src/test/resources/__snapshot/<dialect>.window.sql`（10 方言，文件内 `-- query: over` / `-- query: window` 分隔），断言 `TestDialectWindowSqlSnapshot`（contains 关键片段）。postgis/mysql5.7/h2gis/mariadb/duckdb 的窗口函数登记经 window-expr-support 传递继承（12 方言传递面），聚合 `sum() over` 不依赖方言登记。

## 6. 待复核清单（docker/镜像环境具备时）

- PG 9.6-（历史版本）与 MySQL/Oracle/MariaDB/MSSQL/db2/dm 的 frame 执行实测；dm（达梦）为国产库，GROUPS 支持需实测。
- es/tdengine 补窗口函数登记后重跑快照与执行。
