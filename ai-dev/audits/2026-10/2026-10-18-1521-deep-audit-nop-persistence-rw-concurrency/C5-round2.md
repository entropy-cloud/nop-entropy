# C5 内存有界性 — 第 2 轮深挖（blind round 2）

- **维度**: C5 内存有界性（数据规模包络内任何执行路径内存有上界；缓存容量与淘汰、批量写中间集合、查询结果物化面）
- **批次**: 深挖合并批（第 2 轮，全部 5 源码根）
- **仓库**: `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`）
- **盲审纪律声明**: 本轮为盲审第 2 轮。未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件；唯一例外为本目录 7 个维度初审文件（第 1 轮，必要输入）。所有验证基于当前 live code 逐行阅读。
- **本轮目标**（按深挖指令）: C5-001/002（全量物化族）F3 收口——findAll 族全枚举。
- **新发现编号**: C5-005（P2×1）。本轮无新增 CL。

---

## 深挖：findAll 族全枚举（C5-001 F3 收口 + 新成员）

以 `ISqlExecutor.findAll`（`nop-dao/api/ISqlExecutor.java:180-182`，`executeQuery(sql, null, RowMapperAllExtractor)`——**range=null 即无 LIMIT**）为汇聚点，对 5 源码根全量枚举调用链：

| # | 物化站点 | 到达 findAll(sql, null) 的链路 | 上限机制 | 判定 |
|---|---------|------------------------------|---------|------|
| ① | `JdbcEntityPersistDriver.findAllByExample:353-365` | 直调 `jdbc().findAll` | 无 | 第 1 轮 C5-001 本体 |
| ② | `EntityPersisterImpl.deleteByExample:692-698`（entityMode） | findAllByExample→逐个 delete | 无 | 已立案（C5-001 F3 ②） |
| ③ | `EntityPersisterImpl.updateByExample:739-747`（entityMode） | 同上 | 无 | 已立案（C5-001 F3 ③） |
| ④ | `findPageByExample/findFirst/countByExample` | findPage 带 range / 单行聚合 | 有 | 安全（第 1 轮判定维持） |
| ⑤ | `ShardFanoutEngine` | fanout-max-rows 增量计数 | 有 | 安全 |
| ⑥ | `DaoEntityBlockingSource.loadItems` | `query.setLimit(maxCount)` | 有 | 安全 |
| ⑦ | **`MdxQueryExecutor` 主查询**（`:113-117`） | `query.getLimit()>0` 走 findPage；**否则 `ormTemplate.findAll(sql, rowMapper)` 无界物化** | 无 | **新成员（本轮）** |
| ⑧ | **`MdxQueryExecutor` 子表查询**（`addSubQueryResult:210`） | `ormTemplate.findAll(sql, rowMapper)`——**无论主查询是否带 limit，子查询恒全量物化**（`:120` 的 filterSub 只影响内存对齐过滤，不下推 SQL limit） | 无 | **新成员（本轮）** |
| ⑨ | **sql-lib `findAll` 方法**（`QuerySqlItemModel.java:61-65`） | `orm.findListByQuery(query, mapper)` → `OrmTemplateImpl:440-442` → `new MdxQueryExecutor(this).findList(...)` → ⑦⑧ 同一分支 | 无（除非调用方在 QueryBean 设 limit 且 ⑦ 生效——见 C5-005 的 limit 丢弃问题） | **新成员（本轮）** |
| ⑩ | **`OrmEntityDao.findAllByQuery`**（`:674-682`） | `queryToSelectSql(query)` → `orm().findAll(sql)`（range=null）；**QueryBean 已设的 getLimit() 不进入 SQL 也不进入 range** | 无 | **新成员（本轮，见 C5-005）** |
| ⑪ | `OrmEntityDao.findAll:700-701` | findAllByExample(newEntity()) → ① | 无 | 已立案（同族，第 1 轮口径内） |
| ⑫ | `JdbcDataSet.readAll` | 调用方契约 | — | 第 1 轮 F3 ⑧ 维持（5 根内调用点未变） |
| ⑬ | `MigrationJobStateStore.findAll`（nop-orm-migration） | jdbcTemplate.findAll 全量 | 作业状态表（天然有界） | 范围外（5 根之外）且有界，登记不计 |

**收口结论**：第 1 轮 F3 清单 ①-⑧ 之外存在**第 ⑦-⑩ 号新成员**，其中 ⑦-⑨（MDX 分析查询/sql-lib findAll/子表查询）是现实的大结果集路径（MDX 面向分析场景，行数天然大于 OLTP 单查询），⑩ 存在「调用方显式设置 limit 却被静默丢弃」的契约缺口。据此立案 C5-005。

---

## 发现条目（本轮新增）

### [nop-persistence-rw-C5-005] findAll 物化族第 ⑦-⑩ 号成员：MDX 主/子查询与 sql-lib findAll 无行数上限；findAllByQuery 静默丢弃 QueryBean 已设 limit——「设置了分页的查询」仍全量物化

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/mdx/MdxQueryExecutor.java:113-117（主查询 limit<=0 分支）,200-210（addSubQueryResult 子查询恒 findAll）`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/sql_lib/QuerySqlItemModel.java:59-65（sql-lib findAll 方法）`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/dao/OrmEntityDao.java:674-682（findAllByQuery）`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/dao/DaoQueryHelper.java:122-160（queryToSelectFieldsSql 不注入 limit）`；`nop-persistence/nop-dao/src/main/java/io/nop/dao/api/IEntityDao.java:251（findAllByQuery 无 javadoc 边界声明）`
- **证据片段**:
  ```java
  // MdxQueryExecutor:113-117 —— limit<=0 时无界物化（含一级缓存双重驻留，同 C5-001 机制）
  if (query.getLimit() > 0) {
      ret = ormTemplate.findPage(sql, query.getOffset(), query.getLimit(), rowMapper);
  } else {
      ret = ormTemplate.findAll(sql, rowMapper);      // 全量
  }
  // MdxQueryExecutor:210 —— 子表查询：主查询即使 limit=100，子查询仍全量拉回
  ormTemplate.findAll(sql, rowMapper);
  // OrmEntityDao.findAllByQuery:677-682 —— QueryBean.limit 被静默丢弃
  SQL sql = queryToSelectSql(query);                  // queryToSelectFieldsSql 不读 getLimit()
  return orm().findAll(sql);                          // range=null，无 LIMIT
  ```
- **严重程度**: P2
- **并发场景**: 非竞态，规模触发（同 C5-001 口径）：单请求线程的 MDX/sql-lib/DAO 查询；并发请求数 × 各自一级缓存叠加。
- **触发时序**: (a) 业务以 `QueryBean` 发起查询并**显式设置 limit=1000**（防御性分页是常规习惯）→ 走 `findAllByQuery`/sql-lib `findAll` → limit 既不进 SQL 也不进 range → 全表行全量物化 → 每行经 `OrmAssembly.readEntity` 进入一级缓存（2× 驻留，C5-001 机制）→ 大表上 100 万行 ≈ 250-500MB/请求。(b) MDX 查询未设 limit（分析型默认全量）→ 主查询全量 + **每个子表查询再各自全量**（N 维 → N 次无界物化），内存 ≈ 主表 + Σ子表匹配行，放大倍数 = 维度数。(c) sql-lib 中标记 `method="findAll"` 的语句对同一 SQL 形态常驻无界。
- **现状**: ⑦-⑨ 为第 1 轮 F3 清单遗漏的现实路径（MDX 执行器与 sql-lib findAll 方法均未进入第 1 轮枚举）；⑩ 的 limit 丢弃无任何 javadoc 声明（`IEntityDao.findAllByQuery:251` 无边界注释），调用方无从知晓「设置了 limit 也不生效」——这不是「声明的全量语义」而是**契约缺口**（对照：`findPageByQuery` 同入参生效 limit，同名族 API 行为分裂）。
- **风险**: 量级——并发 10 个 ⑦/⑩ 型百万行请求 ≈ 2.5-5GB（同 C5-001 估算链），堆预算内 Full GC 雪崩乃至 OOM；(b) 的子表乘数使同一查询的内存随维度数线性放大；⑩ 的静默丢弃使「防御性 limit」这一最常见的安全习惯失效，危害隐蔽性高于 ①（调用方自以为已有保护）。
- **建议**: (1) 与 C5-001 修复合并执行：`findAllByExample`/`findAllByQuery`/MDX findAll 分支统一增加可配置行数上限（`nop.orm.find-all-max-rows`，超限抛 `ERR_ORM_FIND_ALL_ROW_LIMIT_EXCEEDED`，对齐 `fanout-max-rows` 先例）；(2) `findAllByQuery` 二选一并落于契约：尊重 `query.getLimit()`（limit>0 时自动转 findPage 语义）或 javadoc 钉死「忽略 limit」+ 调用方 lint 告警——推荐前者（行为与 findPageByQuery 对齐，消除同族 API 分裂）；(3) MDX 子表查询随主查询 limit 下推（子查询按主表 limit 倍数上限截断）或纳入同一行数上限；(4) sql-lib `findAll` 方法文档声明仅适用于有界结果集。
- **失效面枚举（F3）**: 见本文件开头全枚举表 ①-⑬——本轮闭合后 findAll 族**全集合=13 项**：①②③⑪ 为 C5-001 已立案无界面；④⑤⑥ 有界安全；⑦⑧⑨⑩ 为本条新立案面（⑨⑩ 共享 ⑦ 的执行分支，一处行数上限守卫即覆盖 ⑦⑧⑨，⑩ 需单独的 limit 传递修复）；⑫ 调用方契约维持；⑬ 范围外有界。修复覆盖矩阵：行数上限守卫放在 `ISqlExecutor.findAll` 汇聚点可一次性覆盖 ①②③⑦⑧⑨⑩⑪ 全部无界面（单点修复最优解，代价是对确需全量的合法场景需配置豁免开关）。
- **信心水平**: 确定（limit 丢弃与子查询恒全量均为代码直读事实；内存估算沿 C5-001 已核实机制）
- **误报排除**: 非「findAll 语义即全量、调用方责任」——⑩ 的调用方**已显式设置 limit**，静默丢弃是契约违背而非声明边界；⑧ 的子表全量在主查询已分页时违背最小物化直觉且无文档；不按 P1 报——需要大表/无界结果集这一规模前提，非长时运行必然触发（对齐 C5-001 的 P2 判级口径）。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P2，附 F3 勘误**。limit 丢弃与子表恒全量亲验成立（DaoQueryHelper.queryToSelectFieldsSql 全函数无 getLimit 注入、OrmEntityDao.findAllByQuery:674-682 直调 orm().findAll(sql)、MdxQueryExecutor:113-117/:210 分支亲验）；**复核勘误**：本条 F3 表 ⑥「DaoEntityBlockingSource.loadItems——query.setLimit(maxCount) 有界安全」判定**错误**——loadItems 经 dao.findAllByQuery（即 ⑩ 的 limit 丢弃路径），setLimit 同样被静默丢弃，⑥ 应改判为同缺陷受累面（后果：每 tick 物化全部积压行、drainTo 返回数可超 maxElements，攒批契约破坏）；修复面并入本条（ISqlExecutor.findAll 汇聚点行数上限守卫即覆盖 ⑥）

---

## 终止判定

- **本轮新增**: 1 条（P0×0 / P1×0 / P2×1：C5-005 / P3×0）。
- **终止信号**: (a) findAll 族 13 项全枚举闭合（汇聚点唯一：`ISqlExecutor.findAll` range=null；全部调用链已追至文件行级）；(b) 新成员均已立案且修复可单点汇聚；(c) C5-002（cacheRef 条目体积）本轮抽验无新成员（`DataSetCacheHelper.toCacheData` 唯一调用方仍为 `saveCacheData`，grep 确认）。
- **判定**: C5 维度第 2 轮**终止**，不建议第 3 轮。修复轮：C5-001 + C5-005 合并为「全量物化防线」单一 work item（汇聚点守卫 + limit 契约对齐），C5-002/003/004 按第 1 轮建议独立执行。
