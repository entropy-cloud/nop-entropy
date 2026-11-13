# C1 共享可变状态与可见性 — 第 2 轮深挖（blind round 2）

- **维度**: C1 共享可变状态与可见性
- **批次**: 深挖合并批（第 2 轮，全部 5 源码根）
- **仓库**: `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`）
- **盲审纪律声明**: 本轮为盲审第 2 轮。未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件；唯一例外为本目录 7 个维度初审文件（C1/C2/C3/C4/C5/C6/C9 第 1 轮，作为必要输入）。所有验证均基于当前 live code（grep/逐行阅读），未引入第 1 轮未验证的假设。
- **本轮目标**（按深挖指令）:
  1. C1-001/023（IContext get-or-create 竞态族）失效面扩展——全部 IContext 属性 get-or-create 站点穷举；
  2. C1-015（OrmComponentModel 先发布后填充）家族全扫——其他模型类的双字段惰性初始化；
  3. 静态单例槽族（C1-006/024）全枚举收口；
  4. 跨批次同族第 (1)(2) 组裁定（IContext get-or-create、静态单例槽）。
- **新发现编号**: 本轮 C1 维度**无新增发现**（穷举结果仅命中既有立案位点，无第 31 号新条目）。

---

## 深挖 1：IContext 属性 get-or-create 站点穷举（C1-001/023 失效面扩展）

对 5 源码根（nop-orm、nop-orm-eql、nop-orm-model、nop-dao、nop-orm-data）src/main 全量 grep `getAttribute|setAttribute|getAttrs|ContextualizedRegistry|getOrCreateContext|currentContext()`，结果矩阵：

| # | 站点 | 形态 | 判定 |
|---|------|------|------|
| 1 | `nop-orm/impl/OrmSessionRegistry.java:19-27`（`instance()`） | get→new→setAttribute 三步无同步 | **已立案 C1-001**（本轮穷举确认无第 3 处同类） |
| 2 | `nop-dao/txn/impl/TransactionRegistry.java:22-30`（`instance()`） | 同上，逐字节同构 | **已立案 C1-023** |
| 3 | `nop-dao/jdbc/impl/JdbcHelper.java:271`（`ContextProvider.currentContext()` → `getCallExpireTime`） | 只读上下文属性（SQL 超时裁剪），无写点 | 安全（非 get-or-create） |
| 4 | `nop-orm/session/OrmSessionImpl.java:121`（`ContextProvider.currentContext()` 构造期快照） | session 构造时一次性捕获 context 引用，final 字段 | 安全（final 冻结） |
| 5 | `ContextualizedRegistry`（nop-commons 基类，map 为 ConcurrentHashMap） | 容器原语安全；缺陷在两个子类的 `instance()` 复合调用层 | 与第 1 轮判定一致 |

**穷举结论**：5 源码根内 IContext 属性的 get-or-create 站点**有且仅有 2 处**（上表 #1/#2），均已在第 1 轮立案。C1-001/023 的 F3 失效面**已闭合**——不存在未登记的第 3 处同类位点，修复一个平台级「上下文属性原子 get-or-create」工具（如 `context.getAttrs().computeIfAbsent(KEY, k -> new T())`）即全量收敛。跨模块展望（不计入本轮范围）：api-core 的 `ContextProvider.registerContext` 族为平台设施，其内部属性表读写归 api-core 批次。

## 深挖 2：C1-015 家族全扫（先发布后填充 / 双字段惰性初始化）

对 nop-orm-model 全部手写模型类 + nop-orm 引擎类的「getter 内 null-check-then-assign」模式逐点核对：

| 站点 | 形态 | 判定 |
|---|---|---|
| `OrmComponentModel.colPropIds`（:44-53，引用先赋空 Map 后填充）+ `propIds`（:107-117，后赋引用） | 先发布后填充 + JMM 可见性双缺陷 | **已立案 C1-015**（本轮复核维持 P1：交错第一段为纯逻辑交错，无需硬件假设） |
| `OrmColumnModel.getJavaClass`（:96-103） | null-check 后**纯读返回**，无字段写 | 安全（非懒写，第 1 轮 F3 判定复核成立） |
| `OrmReferenceModel.isJoinOnNonPkColumn`（:124-135） | getter 内懒写但值恒 false | 已立案 C1-020（P3，良性） |
| `OrmEntityModel.pkColumnNames/pkColumns`（:72-73） | getter 纯读；写点全部在 init/freeze 期且 setPkColumns 带 `checkAllowChange` 守卫（:183-185） | 安全 |
| `OrmEntityModel.inited`（:108,:381-418）/ `OrmModel.init` 六索引字段（:133-146） | 解析期 init 守卫族 | 已立案 C1-019（P2 理论风险） |
| `LazyLoadOrmModel`（entityModelMap 锁内惰性 + volatile topo 字段） | 锁内单飞 | 正确（第 1 轮判定维持；锁内重活归 C2-018） |
| nop-orm 侧候选扫描（SqlLibManager/JsonOrmComponent/XmlOrmComponent/OrmEntityDao/KvTableVarSet 等 `== null` 命中文件逐个核对） | jsonMap/node 等惰性初始化均为**实体实例字段**（session 上下文封闭），非跨线程共享模型 | 安全（封闭用法下）；`SqlLibManager` 无实例可变缓存（`requireSqlLib` 走 ResourceComponentManager 平台缓存） |

**全扫结论**：「运行期共享对象上的先发布后填充」家族在 5 源码根内**仅 OrmComponentModel 一处已具体化**（C1-015），无新增家族成员。同族防御建议维持第 1 轮：解析期预计算根治。

## 深挖 3：静态单例槽族全枚举收口（C1-006/024）

对 5 源码根全量 grep「static 非 final 可变字段」（含 package-private，排除 static 方法/类/生成物），全集：

| 源码根 | 站点 | 判定 |
|---|---|---|
| nop-orm | `DataBaseSchemaInitializer.specifyQuerySpaces`（:41） | 已立案 C1-009（P3） |
| nop-orm | `OrmTimestampHelper._instance`（:21，package-private static 非 final） | 已立案 C1-006（P2） |
| nop-orm-eql | 仅 ANTLR 生成物 `EqlLexer.channelNames/modeNames` 等（静态初始化后只读 + 生成文件豁免） | 安全/豁免 |
| nop-orm-model | `OrmModelValidatorRegistry.serviceLoaderLoaded`（volatile + DCL） | 正确（对照面） |
| nop-dao | `DaoProvider._instance`（:11） | 已立案 C1-024（P2） |
| nop-dao | `AbstractSqlExecutor.FIND_FIRST_RANGE`（:19，protected static 非 final） | 已立案 C1-029（P3） |
| nop-dao | `DialectManager._instance`（:34，static 初始化器发布） | 安全（同族对照） |
| nop-dao | ThreadLocal 三处（`DynamicDataSource.s_dataSource`/`ReadWriteHint.s_hint`/`QuerySpaceEnv.s_querySpace`） | enter/leave 成对恢复，第 1 轮判定维持 |
| nop-orm-data | 无 static 可变字段 | 安全 |

**收口结论**：静态单例槽族**无第 33 号位点**，第 1 轮 F3 清单即为全集。另复核 `ConfigShardSelector.isFanoutEnabled()`（nop-dao:120-129）读 `DaoConfigs.CFG_*` 平台配置原语，非自管静态槽，不纳入本族。

---

## 跨批次同族裁定（第 1/2 组，按指令⑤）

### 同族组 (1)：IContext 属性 get-or-create 竞态 —— **裁定：同一缺陷合并**

- **成员**: C1-001（OrmSessionRegistry，nop-orm）+ C1-023（TransactionRegistry，nop-dao）。
- **裁定依据**: 本轮穷举证实 5 源码根内该形态有且仅有这 2 处；两者是**逐字节同构**的 check-then-act（get→new→setAttribute），存储介质相同（`BaseContext.attributes` CHM）、失效模式相同（迟到 setAttribute 覆盖并发分支已建实例）、触发前提相同（同上下文并发首触）。
- **修复归并建议**: 合并为**一个平台级修复项**——在 nop-api-core（或 nop-commons）提供 `ContextAttrs.getOrCreate(IContext, key, supplier)` 原子工具（内部 `context.getAttrs().computeIfAbsent`），两个注册表各改一行调用；禁止各模块自行 synchronized（会引入 per-class 锁与上下文生命周期的耦合）。台账保留两条（文件锚点不同、后续复核需要分模块验证），但 remediation 归并为单一 work item。

### 同族组 (2)：静态单例槽发布 —— **裁定：同族相邻（同形态、独立后果），修复归并为一个切片**

- **成员**: C1-006（OrmTimestampHelper，P2）+ C1-024（DaoProvider，P2）；邻接：C1-009（specifyQuerySpaces，P3）+ C1-029（FIND_FIRST_RANGE，P3）。
- **裁定依据**: 四处共享同一形态（init 期写一次、热路径无同步读、非 final 非 volatile），但**后果各不相同**（C1-006 静默策略失效；C1-024 不安全发布 NPE/null；C1-009 多实例覆盖；C1-029 身份比较语义脆弱），且写点/读者完全不同——不是同一缺陷的多个实例，而是同一反模式的四个独立位点。
- **修复归并建议**: 归并为**一个「静态槽收紧」切片**（一个 PR：两处 `volatile` + 一处 `private static final` + 一处实例化改造），不合并台账（四条判级与验收面独立）。正确对照面（`DialectManager._instance` 静态初始化、`EncAssistedHelper._instance` final）在 PR 描述中引用为形态依据。

### 同族组 (3)：缓存 invalidation-vs-repopulate —— 裁定归属 C2-round2（本轮 C1 视角互引）

C1 侧成员 C1-008（查询计划缓存跨代陈旧）与 C2 侧 C2-008（同一根因的锁/失效纪律视角立案）经本轮交叉核对确认为**同一位点同一缺陷的双重立案**（盲审轮两维度各自独立命中），合并裁定见 `C2-round2.md` 同族组 (3)；C1-027（JDBC 查询结果缓存回填竞赛）与 C2-004（全局实体缓存 read-repair 迟到 put）为**同族相邻**（不同缓存、同一竞态形状），裁定同样见 C2-round2。C1 台账四条均保留，不因合并删除。

---

## 终止判定

- **本轮新增**: 0 条（P0×0 / P1×0 / P2×0 / P3×0）；新增 CL：0（CL-700 归 C9 维度登记）。
- **终止信号**: (a) IContext get-or-create 全量穷举仅命中既有 2 站点；(b) 静态槽全量穷举仅命中既有 4 站点 + 2 个安全对照；(c) 惰性初始化家族全扫无新共享面；(d) 三项穷举均为闭合集合（grep 模式覆盖 getAttribute/setAttribute/static 字段/null-check-assign 三族，无残留未分类命中）。
- **判定**: C1 维度第 2 轮**终止**，不建议第 3 轮。后续工作转入修复轮（按同族组裁定归并执行）。
