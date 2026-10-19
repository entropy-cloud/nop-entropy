# C1 深挖第 2 轮 — nop-persistence-peri（WI 2.5）

## 深挖范围与方法

第 1 轮 14 条（C1-001..014）集中在注册表/状态机可见性。本轮按任务书四条未扫面深挖，全部为逐文件通读 + 调用方 grep 复现：

| 深挖线 | 触达文件 | 结论 |
|---|---|---|
| ① dbtool 差异计算中间态（Differ 实例字段跨 run 状态） | `OrmModelDiffer.java`（全文）、`OrmDbDiffer.java`（全文）、`DataBaseUpgrader.java`（全文） | **阴性**：`OrmModelDiffer` 零实例字段（纯方法对象，每次 `new OrmModelDiffer().diffTables(...)`）；`OrmDbDiffer` 仅 3 个配置期字段（`dialect`/`ddlSqlCreator`/`shardSelector`，私有构造 + `forDialect` 工厂每调用新实例，`shardSelector` 为 fluent 注入后只读）；`DataBaseUpgrader` 持有的两个 final 依赖在构造器注入后不变。**无任何跨 run 可变中间态**——第 1 轮批次 1 对 dbtool 链"单线程触达、无竞态"的裁定在实例字段粒度上成立 |
| ② geo/pdm 解析器深部（PdmModelParser 11 实例 map 初始化序列与失败路径残留） | `PdmModelParser.java:146-194`、`PdmModelLoader.java`、`PdmModelHelper.java` | **阴性**：11 个实例 map/字段全部 `final` 且 per-call 新建（唯一调用方 `PdmModelLoader.loadObjectFromResource:16` 每次 `new PdmModelParser()`，另 `PdmModelHelper:22`/`CliGenOrmCommand:53` 同型）；`doParseResource` 内 `this.dialect` 覆盖写（`:166`）发生在解析起点，失败路径整个实例被丢弃、无共享残留。第 1 轮 S6「脆弱性备注」（per-instance 单次使用契约未成文）仍准确，无扩展证据，不升级 |
| ③ tdengine 类型映射注册表的运行期变更面 | `TdTableMeta.java`、`TdEntityPersistDriver.java`、`TdSqlHelper.java` | 无"注册表"形态：`TdTableMeta` 在 `init()`（`TdEntityPersistDriver.java:55`）一次性构建，无运行期 put/remove API；`binders` 数组同为 `init()` 期构建。**运行期变更面不存在**；但发现**可变集合泄漏**新面（C1-015，本条为 C1-014 的跨模块同族兄弟而非同根重复） |
| ④ nosql-lettuce 连接工厂/配置热更面 | `LettuceRedisConnectionProvider.java`（全文）、`RedisConfig` 消费点 | **阴性**：唯一运行期可变入口是 `refreshConfig():92-100`，仅 resize 连接池（其排空缺陷已由第 1 轮 C6-005/CL-020 立案，本维度不重复）；`codec` 字段 setter 为 IoC 装配期调用；URI/ClientOptions 全部在 `doStart` 单次读取。无新热更可变面 |

补充复现：六根（dbtool/db-migration/geo/pdm/tdengine/orm-rpc）`static 非 final 字段`/`ThreadLocal` 复扫仍为 0，与第 1 轮载体复现一致。

## 新增发现

### [C1-015] TdTableMeta 经 public getter 泄漏构造期填充的可变内部 List，被共享单例驱动的并发请求线程共同可达（C1-014 的跨模块同族兄弟）

- **文件**: `nop-persistence/nop-orm-drivers/nop-orm-tdengine/src/main/java/io/nop/orm/tdengine/model/TdTableMeta.java:34-36`（可变载体）、`:88-94`（泄漏 getter）、`:50-60`（构造期填充）；持有面 `driver/TdEntityPersistDriver.java:44`（`private TdTableMeta tableMeta`，prototype driver 的共享实例字段）、`:55`（init 期构建）
- **证据片段**:
  ```java
  // TdTableMeta.java:34-36 —— 引用 final，内容可变（构造器 :50-60 填充后无任何防御）
  private final List<IColumnModel> tagCols = new ArrayList<>();
  private final List<IColumnModel> dataCols = new ArrayList<>();
  // TdTableMeta.java:88-94 —— 直接返回内部可变集合引用，非视图非拷贝
  public List<IColumnModel> getTagCols() {
      return tagCols;
  }
  public List<IColumnModel> getDataCols() {
      return dataCols;
  }
  ```
- **严重程度**: P3（防御性缺陷：仓库内现有消费方全部只读，无现实篡改者；理论风险，未具体化——按 C1-014 同款降档口径）
- **并发场景**: `TdEntityPersistDriver` 经 `EntityPersisterImpl.init()`（容器启动期）构建后随 session factory 安全发布，全部业务请求线程共享同一 `tableMeta` 实例；任意拿到 `getTagCols()/getDataCols()` 返回引用的代码若执行 `add/remove/sort`，变更对所有并发读者即时可见且 `ArrayList` 在并发读写下无任何同步（可抛 `ConcurrentModificationException`、可读到撕裂的中间数组状态）
- **触发时序**: 理论风险，未具体化——需宿主代码主动修改返回的 List 才触发（当前仓库消费方 `TdSqlHelper.genInsertSubTableSql:79-92`、`appendValues:128` 等全部只读遍历，故按证据标准第 4 条降 P3）。一旦发生：T1 扩展点代码 `tableMeta.getTagCols().add(col)`（如"动态追加 tag 列"的错误用法）；T2 并发请求线程执行 `TdSqlHelper.appendValues` 遍历 `dataCols`/`tagCols` → ArrayList 扩容窗口内裸读可返回 null 元素或抛 CME，批量插入 SQL 组装中断且失败点随机
- **现状**: 与 `LettucePubSubService.CAPABILITIES`（C1-014）同族——"static/实例 final 引用"提供假性不可变暗示；本条是实例字段版本，泄漏面经 prototype driver 间接共享给全量请求线程
- **风险**: 宿主扩展或未来代码把返回值当作可自有集合修改时，产生跨线程共享可变状态且无任何同步机制承载；因篡改点与受害点分离（SQL 组装路径），故障表现为随机的 SQL 组装异常，排查面大
- **建议**: `getTagCols()/getDataCols()` 返回 `Collections.unmodifiableList(tagCols)`（Java 25 下亦可 `List.copyOf`）；或收敛为 `getTagColumns()` 返回防御性拷贝。与 C1-014 的 `Collections.unmodifiableSet` 修复合并为同一"共享集合只读出口"规范检查项
- **失效面枚举**（F3 同族清扫）: ① `getTagCols():88-90`——本条主对象；② `getDataCols():92-94`——同源同修；③ `getEntityModel():84-86`——泄漏 `IEntityModel` 引用，但 ORM 元模型按平台惯例构建后只读（全平台共享发布），排除；④ `TdTableMeta.SUB_TABLE_NAME_PATTERN:30`——static final Pattern 不可变，排除（第 1 轮已核）；⑤ 消费方清单：`TdSqlHelper` 5 处引用点全部只读（`:74-92`、`:122-135`、`TdEntityPersistDriver:81,102,166,176`）——无现实篡改者，降档依据
- **信心水平**: 确定（泄漏事实与可变性为代码级确定；危害触发依赖假想篡改者，已如实降档）
- **误报排除**: 非"final 字段即不可变"误报——`final` 只冻结引用；非把不可变误判为可变——`ArrayList.add/remove` 为 public 且 List 视角完全可用；亦非"无状态单例被并发调用"校准形态——对象自身有构造期状态且以可变容器承载
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜新发现（与 C1-014 合并为同一「共享集合只读出口」修复检查项）

## 维度集外线索（overlay）

（本轮无新增 CL 线索；四条深挖线中三条为阴性复现，阳性面已按发现立案。）

## 继续判定

**终止信号**。依据：本轮新增仅 1 条 P3（无 P0/P1/P2）；四条任务书深挖线中 dbtool Differ、PdmModelParser、lettuce 热更三条为确定性阴性（实例字段粒度复现），tdengine 线的阳性产出为防御性泄漏且无现实触发者。第 1 轮 14 条 + 本轮收敛证据表明 C1 维度在该模块的共享可变状态面已充分枚举，剩余风险集中在第 1 轮已立案的 orm-migration 编排面（修复验证归复核轮，非新深挖对象）。

---

## 复核结论（WI 2.5 C1 独立复核，2026-10-18，覆盖 C1-015）

- **判定**：C1-015 **保留，P3，新发现**。
- **证据核验**：`TdTableMeta.java:34-36`（final 引用 + ArrayList 可变内容）、`:88-94`（getTagCols/getDataCols 直接返回内部引用，非视图非拷贝）、构造器 `:50-60` 填充序列、`driver/TdEntityPersistDriver.java:44`（共享实例字段）与 `:54-55`（init 期 `new TdTableMeta`）逐行核实无误。
- **消费方复现**：全仓 grep `getTagCols()/getDataCols()` 仅 `model/TdSqlHelper.java:79,81,82,90,128` 五处（另 `TdEntityPersistDriver:81,102,166,176` 经 tableMeta 间接触达同两面），全部只读（isEmpty/size/get/遍历）——「无现实篡改者」的降档依据成立，P3 恰当，维持原判。
- **同族处置**：与本单元 C1-014（`LettucePubSubService.CAPABILITIES` static EnumSet 泄漏）合并为同一「共享集合只读出口」修复检查项；C1-014 为 static 版、本条为实例版经 prototype driver 间接共享，非同根重复，双保留、单修复项。
- **同案比对**：cra-index WI 1.1–2.4 P1 台账与历史 nosql 审计（2026-05-20 D01–D19）均无同案登记。

## 维度收口（C1 全维度复核统计）

- C1-001..015 共 15 条复核完毕：**保留 15 / 降级 0 / 驳回 0 / 待定 0**。
- 最终严重度分布：**P0×0、P1×7（C1-003/004/005/006/007/009/011）、P2×6（C1-001/002/008/010/012/013）、P3×2（C1-014/015）**。
- 现状标注分布：**新发现×14、已知未修复×1（C1-013，同案 2026-05-20 nosql D03-02）、已修复×0**。
- 逐条判定表、P1 子项复核说明与同案命中清单见 `C1-shared-mutable-state.md`「维度复核结论」节。
- **复核附带勘误**：第 1 轮批次口径 3a「写门读取面可见性合格（DefaultConfigProvider volatile+CHM）」经复核证伪——运行期读取走 usedRefs 常驻 `DefaultConfigReference`，其 `provider` 字段为普通字段、`updateConfigValue` 原地裸换（`AbstractConfigProvider.java:89-93`），无 volatile 边；该面归平台同案 nop-core-framework-C1-007。该勘误不影响任何条目的判定方向：C1-007 因无 HB 兜底维持 P1，C1-010「无保持机制」判定不变。
