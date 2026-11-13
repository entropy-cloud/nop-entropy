# C9 请求热路径 — 第 2 轮深挖（blind round 2）

- **维度**: C9 请求热路径（并发热路径上可量化的性能缺陷：锁争用、分配放大、串行化瓶颈）
- **批次**: 深挖合并批（第 2 轮，全部 5 源码根）
- **仓库**: `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`）
- **盲审纪律声明**: 本轮为盲审第 2 轮。未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件；唯一例外为本目录 7 个维度初审文件（第 1 轮，必要输入）。所有验证基于当前 live code 逐行阅读（含 nop-kernel/nop-commons 的 IntArray 家族源码核对）。
- **本轮目标**（按深挖指令）: C9 各 P3 条量化抽验 2 项（选定 C9-003 单槽缓存、C9-002 每查询常数因子）。
- **新发现编号**: C9-006（P3×1）；CL-700（平台集外线索 1 条）。

---

## 抽验 1：C9-003（单槽 SQL 缓存）—— **机制判定被修正：缓存命中不依赖「交替/并发」，而是恒不命中（除两条单例路径）**

抽验过程与证据（live code 逐行核对）：

1. **equals 语义**：`JdbcEntityPersistDriver` 四处槽位判定均为 `loadSql.propIds.equals(propIds)`（:130/:155/:193/:288）。`EntitySQL.propIds` 为 `ImmutableIntArray`（`EntitySQL.java:19`，构造器 `propIds.toImmutable()`，`ImmutableIntArray.toImmutable()` 返回 this——`ImmutableIntArray.java:66-68`）。关键事实：**`IntArray` 接口（nop-commons/collections/IntArray.java）与 `ImmutableIntArray`、`MutableIntArray` 均未覆写 `equals`/`hashCode`**（全文件 grep equals 计数为 0）——`equals` 即 Object 引用相等。
2. **入参稳定性矩阵**（各槽位 propIds 的实际来源，本轮逐调用点追证）：

| 槽位 | propIds 来源 | 实例稳定性 | 命中率 |
|---|---|---|---|
| `loadSql`（懒加载主场景） | `OrmSessionImpl` 懒加载路径 `:858-867`：`propIds.toMutable().add(propId)` 或 `new MutableIntArray().add(propId)`——**每次调用新建** | 每次新实例 | **0%** |
| `loadSql`（实体首次全量加载） | `OrmSessionImpl:364`：`entityModel.getEagerLoadProps()`（init 期 ImmutableIntArray 单例） | 稳定单例 | 首次 miss 后命中 |
| `lockSql` | `OrmSessionImpl:618`（eagerLoadProps）/`EntityPersisterImpl:257`（`getAllPropIds()` 返回 init 期 `allPropIds` 字段，`OrmEntityModel.java:164-166`） | 稳定单例 | 命中 |
| `batchLoadSqlPart` | `OrmBatchLoadQueueImpl:83`（getEagerLoadProps/getMinimumLazyLoadProps 单例）→ 命中；`:138-149` 经 `merge()` 派生（**每次 merge 返回新实例**）→ 0% | 混合 | 单例路径命中；merge 路径 0% |
| `lastUpdateSql` | `OrmEntity.orm_dirtyPropIds()`（`OrmEntity.java:415-418`）= `oldValues.keySet()`；`IntArrayMap.keySet()` **每次调用 new MutableIntArray 并逐元素填充**（`IntArrayMap.java:112-119`） | 每次新实例 | **0%** |

3. **量化修正**：C9-003 原「交替 propIds/并发混访下退化」的前提不必要——**懒加载与批量写两条主路径在单线程、propIds 完全相同的理想场景下也恒 miss**（每次比较的都是不同的 MutableIntArray 实例）。`lastUpdateSql` 的注释意图（「避免逐条访问时反复生成 SQL」）与实际行为（100% 重新生成）完全相反。单次代价维持第 1 轮估算（genUpdateSql/genLoadSql ≈ 2-10μs），故总量级不变：万级 flush ≈ 1-10 万次生成 ≈ 20-100ms/flush 额外 CPU；但**修复方向改变**：原建议「换 2-4 槽/per-propIds map」不解决问题（键相等性本身失效），必须先修 equals（内容比较）或改用内容规范化键。

**抽验结论**：C9-003 的量级结论维持、机制判定修正、修复方向变更——据此将机制修正登记为新条目 C9-006（与 C9-003 互引，C9-003 条目保留作为量级来源；修复轮以 C9-006 的方案为准）。

## 抽验 2：C9-002（每查询常数因子）—— **抽验通过，量化维持**

- `QueryPlanCacheKey.hashCode`（`QueryPlanCacheKey.java:58-66`）核实：`h = sqlText.hashCode() * 31 + h`——`sqlText` 为每次查询新构建的 String（SQL builder 产物），其内部 hash 缓存冷，**每次缓存查找确实支付 O(|sqlText|)** 全文哈希；L=200 字符 ≈ 0.1-0.2μs、L=2000 ≈ 1-2μs，与第 1 轮估算一致。`equals` 命中后再付一次全文比较（`:69-79` 核实）——合计维持「键开销 0.2-2μs」量级。
- `transformEQL`（`JdbcQueryExecutor:191-195`）核实：每查询 `new GenSqlTransformer`（5 参构造）+ 全 AST 变换，无共享可变态、无缓存点——设计内必要（租户/分片按请求状态注入），量级 5-20μs 维持。
- **抽验结论**：C9-002 抽验**通过**，量化链与 P3 判级维持，JMH 基准缺口登记维持（修复轮若做 SUPPORTS 短路优化，按第 1 轮基准形态验收）。

---

## 发现条目（本轮新增）

### [nop-persistence-rw-C9-006] 单槽 SQL 缓存的 propIds.equals 为引用相等（IntArray 家族无值相等语义）：懒加载与批量更新两条主路径命中率恒 0%，缓存机制与其注释意图相反（C9-003 机制判定修正）

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/driver/jdbc/JdbcEntityPersistDriver.java:130,155,193,288`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/driver/jdbc/JdbcCollectionPersistDriver.java:80,109`；根因载体：`nop-kernel/nop-commons/src/main/java/io/nop/commons/collections/IntArray.java`、`ImmutableIntArray.java`、`MutableIntArray.java`（均未覆写 equals/hashCode，grep 计数 0）；入参不稳定源：`nop-orm/session/OrmSessionImpl.java:855-867`（懒加载新建 MutableIntArray）、`nop-orm/support/OrmEntity.java:415-418` + `nop-kernel/nop-commons/.../IntArrayMap.java:112-119`（keySet() 每次新建）
- **证据片段**:
  ```java
  // JdbcEntityPersistDriver:286-292 —— 注释意图「避免反复生成」，实际恒生成
  IntArray propIds = entity.orm_dirtyPropIds();      // = IntArrayMap.keySet() → 每次 new MutableIntArray
  EntitySQL sql = this.lastUpdateSql;
  if (sql == null || dialect != this.dialect || !sql.propIds.equals(propIds)) {   // 引用相等 → 恒 false
      sql = GenSqlHelper.genUpdateSql(dialect, entityModel, binders, propIds);
      this.lastUpdateSql = sql;                      // 单槽被反复覆写，读取方永远 miss
  }
  // OrmSessionImpl:861-867 —— 懒加载路径同样每次新建
  propIds = new MutableIntArray().add(propId);       // → loadSql 槽判定恒 miss
  ```
- **严重程度**: P3（沿 C9 校准：代价 μs 级、无串行化点、不掩盖正确性——与 C9-003 判级一致）
- **并发场景**: 非并发特有——**单线程即成立**：同一实体连续两次相同 propIds 的懒加载/两次相同脏属性集的 update，第二次仍重新生成 SQL。并发维度仅叠加（多线程交叉覆写 volatile 单槽，第 1 轮已判良性）。
- **触发时序**: T1 实体 A 懒加载属性 p（propIds=Mutable[5]，新实例）→ miss → genLoadSql → 槽=EntitySQL{Immutable[5]}；T2（或 T1 后续）再加载同一属性 p（propIds=**另一个** Mutable[5] 实例）→ `Immutable[5].equals(Mutable[5])` → Object.equals 引用比较 → false → 再次 genLoadSql。update 路径同构：`orm_dirtyPropIds()` 每次经 `IntArrayMap.keySet()` 新建集合，同一脏属性集的两次比较也是不同实例。
- **现状**: `IntArray`/`ImmutableIntArray`/`MutableIntArray` 三类均未覆写 equals（本轮逐文件核实，nop-commons 源码）；6 处 `.propIds.equals(...)` 调用点（两 driver）全部受累；`EntitySQL` 的 javadoc 与 driver 内注释（「单槽缓存，避免……反复生成SQL」，`JdbcEntityPersistDriver.java:64-66`）表达的意图与实际行为相反。第 1 轮 C9-003 将退化归因于「交替 propIds/并发混访」，本轮修正为**恒退化**（结构性，非负载性）。
- **风险**: 量化——万级 flush 全部实体支付 genUpdateSql（≈20-100ms/flush 额外 CPU，Young GC 压力同 C9-003 估算）；每个懒属性首次访问支付 genLoadSql（2-10μs）；无正确性影响（各线程使用本地生成的正确 EntitySQL）。属「缓存机制静默失效」族：无任何指标暴露命中率，修复前无法从运行态发现。
- **建议**: (1) 最小修复：槽位判定改内容比较（propIds 长度 ≤ 属性数 ≤ 数十，逐元素比较 ≈ 十几 ns）：`propIds.size()==sql.propIds.size() && IntArray 内容一致`，或给 `ImmutableIntArray` 覆写 `equals/hashCode`（值语义，一处修复全仓受益——但需评估该类其他以引用语义依赖 equals 的站点，本轮 grep 5 根内 `.equals(` 对 IntArray 的调用仅此 6 处，风险可控）；(2) 与 C9-003 的多槽建议合并执行：内容相等修复后，再评估 per-propIds 小 map 是否仍有收益（混访场景仍有踢槽，但主路径已收敛）；(3) 补充可观测性：driver 内增加 SQL 生成计数指标（命中率可查），防同族缓存静默失效复发。
- **失效面枚举（F3）**: 6 处 equals 判定点全清单——① `JdbcEntityPersistDriver.loadAsync:130`（懒加载 0% / 全量加载单例路径命中）；② `lock:155`（单例路径，命中）；③ `batchLoadAsync:193`（单例路径命中、merge 派生路径 0%——`OrmBatchLoadQueueImpl:138-149` 的 merge 每次`新实例`）；④ `buildUpdateSql:288`（**恒 0%，最热点**——每 flush 每实体一次）；⑤ `JdbcCollectionPersistDriver.loadSql:80`（集合懒加载，propIds 源同构，0% 面）；⑥ `batchLoadSqlPart:109`（同 ③ 同构）。根因修复（equals 值语义或内容比较工具方法）一处覆盖全部 6 点；`ImmutableIntArray` 覆写 equals 的波及面=全仓 IntArray equals 调用点（5 根内 grep 仅此 6 处，平台其他模块修复轮需另行 grep 确认，登记 CL-700）。
- **信心水平**: 确定（equals 缺失为源码事实；入参不稳定链逐点追证到行级；单线程即可复现）
- **误报排除**: 不属于「缓存缺失是设计取舍」——代码注释明示缓存意图，实际行为与意图相反（机制失效非设计选择）；不属于 C9-003 重复计数——该条量级来源保留，本条修正其机制判定并变更修复方向，remediation 以本条为准、C9-003 标注「量级见 C9-006 修正」；不升 P2——单次代价 2-10μs 相对 DB 往返占比 <10%，沿 C9 校准维持 P3。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P3**。机制修正亲验成立：IntArray/ImmutableIntArray/MutableIntArray 均未覆写 equals/hashCode（grep 计数 0 + 类层次确认 implements IntArray 接口无值语义父类）；6 处 .propIds.equals 引用比较点逐一核对（两 driver 130/155/193/288/80/109）；懒加载每次新建 MutableIntArray（OrmSessionImpl.java:861-866）、toImmutable 返回 this 亲验——恒 miss（结构性、单线程即成立）判定**确认**；根因归 nop-commons（CL-700 移交）确认

---

## CL 线索（本轮新增，CL-700 段）

- **CL-700**:（平台集外线索，不立案展开）`IntArray` 家族（nop-commons/collections）无值相等语义：`IntArray`/`ImmutableIntArray`/`MutableIntArray` 均未覆写 `equals/hashCode`。5 源码根内受累点=6 处 SQL 槽位判定（C9-006 F3）；平台其他模块（nop-report/nop-chart/nop-batch 等）凡以 IntArray 作缓存键/去重键的位点同病。修复建议：在 nop-commons 层给 `ImmutableIntArray` 覆写值语义 equals/hashCode（`MutableIntArray` 维持引用语义或同样覆写需全仓评估），并全仓 grep `IntArray` 类型字段的 `.equals(` 调用点补齐回归面。登记状态：维度集外线索，归 nop-commons 修复轮，不计入 C9 发现计数。

## 终止判定

- **本轮新增**: 1 条发现（P0×0 / P1×0 / P2×0 / P3×1：C9-006）+ 1 条 CL（CL-700）。
- **终止信号**: (a) 两项指定抽验完成：C9-002 通过（量化维持）、C9-003 机制修正并立案 C9-006（根因载体在 nop-commons，已按集外线索移交）；(b) 抽验过程发现的唯一新缺陷已立案且修复点单一（equals 语义一处修复覆盖 6 点）；(c) 无剩余未抽验 P3 条目中的高风险项（C9-004 日志税为配置问题、C9-005 为确认性登记，均无需量化修正）。
- **判定**: C9 维度第 2 轮**终止**，不建议第 3 轮。修复轮入口：C9-001（O(n²) 监听器，与 C2-014 同点位）+ C9-006/CL-700（equals 值语义，归 nop-commons 切片）+ C9-002 的 SUPPORTS 短路优化（可选，带 JMH 验收）。
