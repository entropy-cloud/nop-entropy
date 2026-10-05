# nop-orm 缺陷审计遗留项 Backlog

> Status: partially resolved（遗留项 1/2/4 已处理，见各项标注；3/5 维持待定）
> Date: 2026-10-05
> Source: `ai-dev/analysis/2026-10/2026-10-05-orm-query-optimizer-landscape-vs-nop-orm.md` 及三轮计划审计的核实结论
> 已落地部分: 见 `ai-dev/plans/39-orm-cache-contract-remediation.md`

## 已核实并修复（2026-10-05，plan 39）

1. `OrmSessionFactoryBean.refreshConfig()` 守卫恒假 → global cache 配置热刷新全部署失效。**已修**：守卫改为 `sessionFactory.getGlobalCache() instanceof LocalCacheProvider`，非 LocalCacheProvider 时 `LOG.warn`。红证：旧代码 `expected: <777> but was: <1000>`。
2. `ICache.putIfAbsent` 默认实现违反自身 javadoc 的 check-then-act 原子契约。**已修**：`synchronized` 默认实现。红证：旧代码并发 16 线程出现 2 个"胜者"。
3. `ICache.computeIfAbsent` 默认实现非原子。**已修**：走原子 `putIfAbsent`，单一胜者语义。红证：旧代码并发线程拿到 2 个不同值。
4. `JavaxCache` 缺 `computeIfAbsent` 覆写。**已修**：委托 JCache 原子 `getAndPut`。

## 已核实为"非缺陷"并关闭（不再追）

| 项 | 关闭理由（均经代码/文档一手核实） |
|---|---|
| `default.orm.xml` `deleteFlagProp="delVersion"` "笔误" | 是 `logical-deletion.md` 方案 A（推荐）的明文要求 + `OrmEntityModelInitializer:287-288` 自动同化机制；且 `NopDefaultCols` 无任何运行时消费方（`OrmModelLoader` 不加载 defaults） |
| `xdef:default-extends` 悬空声明（6 处） | `XDefinition.java:73` 按设计软失败（trace + return null），有 `x:extends="none"` 显式抑制；补文件反而导致双重加载 |
| `checkLockOptions` 抛 `IllegalArgumentException` 无错误码 | WI12 §1.5 已裁定：程序化构造的矛盾输入属模块内部契约违反，不走 `NopException` 错误码（`2026-10-05-wi12-phase0-adjudications.md:74`） |
| 空 `IN ()` 行为未定义 | 文法不可达（`BaseRule.g4:200-201` 要求 ≥1 个表达式），生产代码无程序化构造方 |
| 编译计划缓存无容量/过期 | 实为有界 Caffeine（`OrmSessionFactoryBean:95-96`）+ 配置热刷新 |

## 待做（按优先级，均未立项）

### 1. EQL 长条件链栈深度测量 —— ✅ 已处理（2026-10-05，结论：watch-only）

- **实测结果**（`TestEqlAndChainDepth`，1MB 栈线程）：2000 项 AND 链编译成功，**4000 项首次溢出**。
- 判定：阈值远超现实业务谓词数量（通常 <100），按预案**降级为 watch-only**，不做树平衡改写（改写会变更 SQL 文本与参数顺序，风险大于收益）。
- 保留物：`nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/compile/TestEqlAndChainDepth.java`（确定性下限断言 1000/2000 + 阈值观测打印），若未来出现真实长链场景可据此重启。

### 2. `nop-orm-model` 测试补齐 —— ✅ 部分完成（2026-10-05）

- **已落地**：`TestOrmEntityModelInitializerBasics` 12 个测试（34 → 46），覆盖：propId 稳定性（删列后不复用空洞，`entity.xdef:86-89` 承诺）、隐式 propId 顺序分配、重复/超限 propId 报错、**deleteFlagProp 自动同化（方案 A 锁定测试，防再被误判为缺陷）**、domain 未知/类型冲突/精度继承/显式优先、stdSqlType 必填或继承、stdDataType 推导、列反向引用。
- **未覆盖**（后续）：关系拓扑排序、alias/component/compute 派生、`minPropId` delta 场景。

### 3. 方言能力位 `Context` 维度（原 plan 43 主题，纯增强）

- 无正确性后果。仅当出现真实的"按场景区分"需求（如 values-list 的 QUERY vs INSERT）再立项。
- 前置：须实跑 `DslModelParser` 判定 `<features>` 能否同时承载属性与子节点。

### 4. 工具缺陷：`scan-hollow-implementations.mjs` —— ✅ 已修复（2026-10-05）

- 修复：main() 入口增加路径存在性校验（不存在 → stderr 报错 + exit 2）；每次运行输出 `[scan-hollow] scanned N java files`。
- 验证：`--module __不存在__` → exit 2；`--module nop-kernel/nop-commons` → "scanned 403 java files" + 按 highCount 决定退出码。**可恢复为 Closure Gate**（判据=exit 0 且 stderr 的 scanned 数 > 0）。

### 5. L2 实体缓存"开启即强制全属性加载"

- `EntityPersisterImpl:156/:235` 用 `getAllPropIds()`，保守正确但宽表场景缓存体积大。需先设计部分属性缓存的装配回填语义。
