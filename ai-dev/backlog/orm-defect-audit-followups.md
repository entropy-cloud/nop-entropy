# nop-orm 缺陷审计遗留项 Backlog

> Status: open
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

### 1. EQL 长条件链栈深度测量（原 plan 42 主题）

- 事实：`BaseRule.g4:155` 左递归使解析期即产出左深树；`AbstractVisitor:22-25` 递归深度 = 树深。
- 未证实：N 项 AND 链在真实栈配置下首次 `StackOverflowError` 的 N 值。**先测量再决定是否修**——若 N 远超实际业务条件数，降级为 watch-only。
- 若需修：落点候选为 `EqlTransformVisitor` 解析后归一化（打印器无 parent 引用，不适合）。

### 2. `nop-orm-model` 测试补齐（原 plan 44 主题）

- 事实：91 main / 6 test（34 `@Test`），`OrmEntityModelInitializer`（730 行）是 ORM 语义核心。
- ⚠️ propId 稳定性测试必须走 `.orm.xml` 加载路径；`SchemaToOrmModel:66-68` 的 `resetPropId` 是 codegen 路径的顺序重排，属固有语义非缺陷。

### 3. 方言能力位 `Context` 维度（原 plan 43 主题，纯增强）

- 无正确性后果。仅当出现真实的"按场景区分"需求（如 values-list 的 QUERY vs INSERT）再立项。
- 前置：须实跑 `DslModelParser` 判定 `<features>` 能否同时承载属性与子节点。

### 4. 工具缺陷：`scan-hollow-implementations.mjs` 零路径区分能力

- 实测：正确路径与 `--module __不存在__` 报告逐字相同、退出码均 0（退出码只看 `highCount`，脚本 `:472`）。
- 修复方向：校验路径存在 + 报告实际扫描文件数。修复后可恢复为 Closure Gate。

### 5. L2 实体缓存"开启即强制全属性加载"

- `EntityPersisterImpl:156/:235` 用 `getAllPropIds()`，保守正确但宽表场景缓存体积大。需先设计部分属性缓存的装配回填语义。
