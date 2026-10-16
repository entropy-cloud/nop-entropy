# C5 内存有界性 — nop-batch 第 2 轮深挖报告（盲审轮）

## 审计单元与范围

- **审计单元**: nop-batch 聚合器，11 个含 Java 的 jar 子模块（api/app/biz/core/dao/dsl/exp/gen/jdbc/orm/service），src/main 手写代码（不含 `_` 前缀生成物与 target/）。基线口径：218 files / 15,698 行。
- **仓库**: `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`，分支 `concurrency-audit-remediation`，HEAD `4b7c7897de`。
- **本轮输入**: 第 1 轮报告 `C5-memory-bounds.md`（C5-001~005：P1×2 / P2×3；CL-007~011；负发现 10 条）+ 基线 `_tmp/cra-baseline-nop-batch.txt`（复核：C5 unbounded candidates = 0，与第 1 轮判断一致）+ live code 逐行核对。
- **盲审纪律声明**: 本轮为盲审第 2 轮，未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件——唯一例外是本维度第 1 轮文件 `C5-memory-bounds.md`（任务指定的必要输入）；未读取同目录其他维度文件。
- **本轮目标（按深挖指令 ①~⑤）**:
  - ① excel-reader 失效面扩展：CSV 流式声明逐行复核；file-writer 写出缓冲；Excel 写出对称面；maxCountExpr 注释位点语义与兜底判定。
  - ② jdbc-reader fetchSize 死配置失效面扩展：`JdbcBatchLoaderProvider`/`BatchJdbcReaderModel` 全字段 DSL→装配→应用门接线完整性（死配置族全枚举）；maxItems/pageSize 类参数门条件核对；nop-batch-orm 侧 orm-reader 对照。
  - ③ chunk 内累积结构全扫：BatchChunkProcessor/BatchProcessorConsumer/BatchTaskContextImpl 及全部 loader/consumer/processor 站点逐一判定上界；consume() 多次调用与 generator while(true) 环（C5-003 已立案）的同族穷举。
  - ④ filterProcessed/history 累积：DaoBatchRecordHistoryStore/JdbcKeyDuplicateFilter/JdbcPageBatchLoaderProvider 的 recordKey 集合规模；NopBatchTaskVar（EAV）与 persistVars 写入累积面。
  - ⑤ EtlTaskStateStore/ExportDbTool/ImportDbTool 物化面：状态文件内存表示；两工具行缓冲与 Excel 导出路径（与 C5-001 是否同族）；分页导出页累积。

---

## 第 1 轮发现的第 2 轮复核结论（不翻案，补充证据）

| 第 1 轮发现 | 第 2 轮复核结论 |
|---|---|
| C5-001（excel-reader 全量物化，P1） | **维持**。且解析层 `XlsxToRecordOutput.OutputRowHandler.endRow`（`nop-ooxml-xlsx/.../parse/XlsxToRecordOutput.java:90-96`）本身是**逐行回调** `output.write(row)`——物化点唯一收敛在 `ExcelRecordInput.beforeRead:87-98` 的 `HeaderListRecordOutput` 收集器（`ExcelRecordInput.java:97` `list = collector.getResult()`）。修复面比第 1 轮判断的更窄：只需把 collector 换成逐行下推即可保留解析层流式能力。owner doc 新证据：`docs-for-ai/03-modules/nop-batch.md:232` 明文声明「大批量数据导入（CSV/Excel → 数据库）」——Excel 被显式纳入大批量包络，强化立案依据。 |
| C5-002（jdbc-reader fetchSize 死配置，P1） | **维持并扩展**：同一门条件（`JdbcBatchLoaderProvider.java:246`）存在第二个触发变体，详见下文「C5-002 扩展」。 |
| C5-003（generator 输出无界，P2） | **维持**。`BatchSequentialProcessor.java:33-48` 的 while(true) 环与 `BatchProcessorConsumer.java:62,71` 的 `outputs::add` 汇聚点复核无误；同族累积汇点穷举见「③ chunk 站点全扫」节。 |
| C5-004（provider List 常驻，P2） | **维持**。`ModelBasedBatchTaskBuilderFactory.newLoaderProvider` 包装点未变。 |
| C5-005（dispatch 容量无守卫，P2） | **维持**。`buildDispatchConfig`（同文件 :356-381）仍无 `>0` 校验。 |
| 反虚构预注 1（orm-reader WARN 后 keyset 有界） | **实测维持，未推翻**。`OrmQueryBatchLoaderProvider.java:131-144`：`load()` 严格 `dao.findNext(lastEntity, filter, orderBy, batchSize)` 逐页推进 `state.lastEntity`，页内 ≤ batchSize；`batchLoadProps` 对当前页 list 操作。无绕过分页的调用方。 |
| 反虚构预注 2（CSV 读取流式） | **逐行核实维持，未推翻**。`CsvRecordInput`（nop-kernel/nop-core）构造器 :67-70 `CSVParser.parse(reader, format.withHeader())` + `csvReader.iterator()` 为 commons-csv 惰性迭代器；`hasNext()/next()` :168-188 直接委托 `it`；`beforeRead` :148-150 仅做 header 规范化（有界）；批量截断在 `IRecordInput.readBatch(maxCount)` 默认实现（逐条循环 ≤ maxCount，`IRecordInput.java:97-99`）。全程无全量收集。 |

---

## 发现清单（新发现自 C5-006 起）

### [nop-batch-C5-006] excel-reader 的 maxCountExpr 上限能力整体缺位：xdef 未声明特性、DSL 接线为引用已删 API 的注释死代码，且即便恢复也不构成内存兜底（limit 发生在物化之后）

- **文件**: `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/batch.xdef:113-114`（xdef 无该特性）；`nop-batch/nop-batch-dsl/src/main/java/io/nop/batch/dsl/manager/FileBatchSupport.java:150-151`（注释死代码）；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/loader/ResourceRecordLoaderProvider.java:193,208-212`（语义判定点）
- **证据片段**:
  ```xml
  <!-- batch.xdef:97-99 file-reader 声明了 maxCountExpr -->
  <file-reader filePath="!t-expr" ... resourceLocator="bean-name" resourceIO="bean-name" maxCountExpr="expr" ...>
  <!-- batch.xdef:113-114 excel-reader 没有该特性 -->
  <excel-reader filePath="!t-expr" xdef:name="BatchExcelReaderModel" templatePath="v-path" headerRowCount="int"
                dataSheetName="string" headerSheetName="string" trailerSheetName="string">
  ```
  ```java
  // FileBatchSupport.newExcelReader:150-151 —— 注释引用的 getter 在 BatchExcelReaderModel 中已不存在
  //        if (loaderModel.getMaxCountExpr() != null)
  //            loader.setMaxCountExpr(loaderModel.getMaxCountExpr());
  ```
  ```java
  // ResourceRecordLoaderProvider.newLoaderState —— 顺序是先物化后限流
  input.beforeRead(context.getAttributes());        // :193 excel 在此全量物化（C5-001）
  ...
  if (maxCountExpr != null) {                        // :208-212 limit 是包装器截断
      Long maxCount = ConvertHelper.toLong(maxCountExpr.invoke(context));
      if (maxCount != null)
          input = input.limit(maxCount);             // LimitRecordInput 包装 hasNext/next，仅计数截断
  }
  ```
- **严重程度**: P3
- **并发场景**: 与并发无关的能力缺位/死代码问题。
- **触发时序**: 三个独立事实构成一个失效面：① 用户在 DSL 给 `<excel-reader>` 写 `maxCountExpr` 会直接 xdef 校验失败（特性未声明），即 excel-reader 不存在任何行数上限配置入口；② `FileBatchSupport:150-151` 注释代码调用 `loaderModel.getMaxCountExpr()`，而 `BatchExcelReaderModel` 及其 `_gen` 基类中 grep 无该方法——注释指向已删除的 API，「恢复接线」并非取消注释即可，需要 xdef 加特性 + 重新生成 model + 恢复接线三层联动；③ 即便全部恢复，`maxCountExpr` 的求值与 `input.limit()` 包装发生在 `newLoaderState` 中 `beforeRead` **之后**（:193 vs :208-212），而 excel 的全量物化恰恰发生在 `beforeRead` 内——故 maxCountExpr 只能截断**处理行数**（字段 javadoc :57-60「最多读取多少行数据（包含跳过的记录）」），不能降低物化峰值内存，对 C5-001 **无兜底作用**。
- **现状**: owner doc `nop-batch.md:69,177,232` 将 Excel 归入 file-reader 能力并声明「大批量数据导入（CSV/Excel → 数据库）」，但 excel-reader 实际既无行数上限配置面，其半成品接线又是死代码——「配置兜底」的表象与实际能力不符。
- **风险（规模判断）**: 不构成新的 OOM 路径（内存本体已由 C5-001 立案）。本条的风险是**修复误导**：若按第 1 轮建议的最小修复路径「恢复 maxCountExpr 接线」执行而不动 `ExcelRecordInput`，将得到一个看似有界实则仍全量物化的系统，且无任何报错。实测推演：50 万行文件 + `maxCountExpr=1000` 时峰值内存与不配置完全相同。
- **建议**: ① owner doc 明确「excel-reader 无行数上限、仅适用小文件，大数据量用 CSV」；② 若保留 excel-reader，修复 C5-001 时将 `ExcelRecordInput` 改为游标式（解析层已支持：`XlsxToRecordOutput.endRow:90-96` 本身逐行回调），并在游标化之后恢复 maxCountExpr 接线（那时 limit 才有真实的内存截断意义）；③ 删除或修复 `FileBatchSupport:150-151` 死注释。
- **失效面枚举（F3）**: 同族 = ①`<excel-reader>` DSL（本条主体，能力缺位）；②`<file-reader>` 的 maxCountExpr（接线完整 `FileBatchSupport:51-52`，对 CSV 流式 reader 是真实有效的读取上限——同参数在两个 reader 上内存语义完全不对称）；③`ImportDbTool.newLoader`（仅 CSV，无上限配置但流式，读取节奏由 batchSize 控制，无缺陷）；④`ResourceRecordLoaderProvider.skipCount`（:204-206，流式 skip，有界）。
- **信心水平**: 确定（xdef/model/接线/求值顺序四点均有行级证据）
- **误报排除**: 不是「有界声明存在」误报——恰恰是声明（owner doc 大批量导入）与能力（无上限入口）的缺口；也不与 C5-001 重复——C5-001 是物化路径本体，本条是「该路径无任何配置级缓解手段 + 半成品死代码 + 修复顺序陷阱」的独立失效面。
- **复核状态**: 未复核
- **复核状态**：已复核（2026-10-16 独立复核）——保留 P3，新发现。三层全部亲证成立：xdef excel-reader 无该特性（batch.xdef:113-114）、注释引用的 `getMaxCountExpr()` 在 BatchExcelReaderModel 及 _gen 基类 grep 零命中、`beforeRead`（ResourceRecordLoaderProvider:193）先于 maxCount 求值与 `limit()` 包装（:208-212）——恢复接线亦不构成 C5-001 兜底的判定正确。

### [nop-batch-C5-007] SplitBatchConsumer 的 activeConsumers 任务级 Map 以运行期 tag 为键、只增不减：公共类无键基数守卫，DSL 装配点靠静态白名单兜住——程序化使用路径无界

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/consumer/SplitBatchConsumer.java:42-45,59-70`（无界点）；`nop-batch/nop-batch-dsl/src/main/java/io/nop/batch/dsl/manager/ModelBasedBatchTaskBuilderFactory.java:300-311`（唯一平台内装配点，含守卫）
- **证据片段**:
  ```java
  // SplitBatchConsumer.setup —— activeConsumers 随任务存活，键集 = 出现过的全部 tag
  public IBatchConsumer<R> setup(IBatchTaskContext context) {
      Map<String, IBatchConsumer<T>> activeConsumers = new ConcurrentHashMap<>();   // :44
      return (items, ctx) -> consume(items, ctx, activeConsumers);
  }
  ...
  protected synchronized IBatchConsumer<T> getConsumer(String tag, ...) {
      IBatchConsumer<T> consumer = activeConsumers.get(tag);
      if (consumer != null) return consumer;
      consumer = consumerProvider.apply(tag, context);
      if (consumer != null) {
          activeConsumers.put(tag, consumer);        // :68 只增不减，无淘汰、无容量上限
      }
      return consumer;
  }
  ```
  ```java
  // ModelBasedBatchTaskBuilderFactory:307-309 —— DSL 装配点有静态白名单守卫
  SplitBatchConsumer<Object, Object> writer = new SplitBatchConsumer<>(splitter,
          (tag, ctx) -> !consumerMap.containsKey(tag) ? null :     // 键集 ⊆ DSL 声明的 consumer 数
                  consumerMap.get(tag).setup(ctx.getTaskContext()));
  ```
- **严重程度**: P3（理论风险，未具体化——平台内唯一调用点已守卫）
- **并发场景**: 多 chunk 线程共享同一 `activeConsumers`（ConcurrentHashMap + synchronized getConsumer），并发不放大键集，但键集随任务全程累积。
- **触发时序**: 程序化构造 `SplitBatchConsumer` 并传入不校验 tag 的 `BiFunction`（如 `(tag, ctx) -> consumerProvider.apply(tag, ctx)`）→ tagger 返回高基数字段值（如按 userId/订单号分流）→ 每个新 tag 构造并**永久持有**一个 consumer 实例。若 consumer 是文件 writer（`ResourceRecordConsumerProvider`），每 tag 还持有打开的输出流句柄。规模路径：100 万 distinct tag × 每个 consumer 实例约 2KB + 打开句柄 → 堆与文件句柄双重耗尽；任务级泄漏（Map 随 setup 返回的 lambda 存活到任务结束）。
- **现状**: DSL 路径安全（`consumerMap` 是 `<consumer forTag>` 静态配置，键集有界）；公共类 javadoc/字段均未声明「tag 基数必须有界」的契约。
- **风险（规模判断）**: 如上，堆 + 句柄双耗尽路径成立，但需要程序化误用——平台内全部调用点（仅 DSL 一处）已由白名单守卫。故按「理论风险，未具体化」标注 P3。
- **建议**: 在 `SplitBatchConsumer` 增加可选的 `maxActiveConsumers` 上限（超限抛 `BatchErrors` 语义错误）或 javadoc 声明 tag 基数契约；更优：将 DSL 的白名单守卫下沉为类的内建能力（构造器接受允许的 tag 集合）。
- **失效面枚举（F3）**: 同族（任务级运行期键控 Map）= ①本条主体；②`BatchTaskContextImpl.getCache()` MapCache（任务级，键由用户代码写入，同形态——并入 C5-008 一并判定）；③`EtlTaskStateStore.tableStores`（ConcurrentHashMap，键=表名，规模=配置表数，有界，无缺陷）；④`WithHistoryBatchConsumer` 无状态（每 chunk 重建，无缺陷）。
- **信心水平**: 确定（无界点与守卫点均为直接可读代码；规模量级为构造性推演）
- **误报排除**: 不是「有界声明存在」——公共类无任何基数声明；不因 DSL 有守卫而判无缺陷——`SplitBatchConsumer` 是 `IBatchConsumerProvider` 公共 SPI，程序化组合是框架明示用法（owner doc 的 provider 契约面向程序化扩展）。
- **复核状态**: 未复核
- **复核状态**：已复核（2026-10-16 独立复核）——保留 P3，新发现。无界点（SplitBatchConsumer:44/:68 只增不减）与 DSL 白名单守卫点（ModelBasedBatchTaskBuilderFactory:307-309）亲证成立；平台内唯一调用点已守卫，「理论风险，未具体化」标注正确。

### [nop-batch-C5-008] BatchTaskContextImpl 暴露三类任务级可变容器且注册/写入 API 无任何容量守卫：监听器 12 列表、persistVars、getCache——框架自身守「setup 期一次」纪律靠注释不靠机制

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/impl/BatchTaskContextImpl.java:46,69-81,104-113,392-493`
- **证据片段**:
  ```java
  private IVarSet persistVars = new MapVarSet();                    // :46 任务级，无上限
  private List<Runnable> onTaskBegins;                              // :69 起共 12 类监听器列表
  private final List<Consumer<IBatchChunkContext>> onChunkBegins = new CopyOnWriteArrayList<>();
  ...（onBeforeChunkEnds/onChunkEnds/onChunkTryBegin/onChunkTryEnd/onLoadBegins/onLoadEnds/onConsumeBegin/onConsumeEnd 等）
  public synchronized ICache<Object, Object> getCache() {           // :104-113
      ...
      cache = new MapCache<>("batch-task-cache", true);             // 任务级，键值均无界
  }
  @Override
  public void onChunkEnd(BiConsumer<IBatchChunkContext, Throwable> action) {  // :426-433
      synchronized (this) {
          if (isDone()) throw new IllegalStateException(...);        // 唯一守卫：任务已结束
          onChunkEnds.add(action);                                   // 无容量上限、无重复注册检测
      }
  }
  ```
- **严重程度**: P3（理论风险，未具体化——平台内全部调用点遵守 setup 期注册纪律）
- **并发场景**: 注册 API 有 synchronized 保护，无数据竞争；风险纯为累积量。
- **触发时序**: 框架自身调用点全部在 setup 期注册一次（`ResourceRecordLoaderProvider.newLoaderState:219-227` 有注释明示「只能在 setup 时注册一次，不能在 load 中注册，否则每个 chunk 都会向任务上下文追加一个回调，导致回调列表无界增长」——框架作者已知此风险并靠注释自律）。程序化 processor/consumer 若在 `process(item)` 内调用任一 `onXxx` 注册方法：每条记录 +1 个捕获上下文的回调对象 → 规模路径：100 万记录 × 每 lambda ~50B+捕获引用 ≈ 数百 MB 且 CopyOnWriteArrayList 每 add 全量复制，CPU/内存双重放大。`persistVars`（`IBatchTaskContext.setPersistentVar`）与 `getCache()` 同理：键值由用户代码决定，任务级存活，无上限无告警。
- **现状**: 平台内无写入者（本轮 grep 全仓：`getPersistVars` 在 nop-batch 内仅 accessor 自身；NopBatchTaskVar EAV 写入走 DB CRUD 服务，与该内存面无关）。API 契约（setup 期注册）存在于注释而非类型系统或运行时守卫。
- **风险（规模判断）**: 误用触发的任务级泄漏，量级随记录数线性；无平台内触发配置。按「理论风险，未具体化」P3。
- **建议**: 低成本守卫：在 fire 方法首次调用后冻结注册 API（已过 setup 期再注册即抛错），把注释纪律机制化；或对 12 列表加上限（如 1000）+ WARN。
- **失效面枚举（F3）**: 同族 = ①12 类监听器列表（本条主体）；②`persistVars` MapVarSet；③`getCache()` MapCache（C5-007 F3 ②同一形态）；④`BatchChunkContextImpl` 的 completedItems/historyItems/rowNumberMap（chunk 局部且随 chunk 丢弃，1:1 用法 ≤ batchSize——见 ③ 全扫表，无缺陷）。
- **信心水平**: 确定（无守卫注册点直接可读；「注释自律」有行级证据）
- **误报排除**: 不是「有界声明存在」误报——存在的是注释级纪律而非机制守卫；也不是「用户代码无限增长属用户责任」——容器是框架任务级结构，且框架作者已在注释中确认该增长模式为真实风险。
- **复核状态**: 未复核
- **复核状态**：已复核（2026-10-16 独立复核）——保留 P3，新发现。persistVars(:46)、12 监听器列表(:69-81)、getCache MapCache(:104-113)、注册 API 仅 isDone 守卫(:426-433) 全部亲证成立；「注释自律」证据（ResourceRecordLoaderProvider:219-220）亲证。

### [nop-batch-C5-009] batchSize 全链路无上界强制：xdef 声明 `!int` 无 min/max、Builder 赋值无 Guard、仅 ≤0 归一为 1——「每 chunk 内存 ≈ batchSize」的包络声明无机制支撑

- **文件**: `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/batch.xdef:9`；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/BatchTaskBuilder.java:273-274`；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/processor/BatchChunkProcessor.java:83-92`
- **证据片段**:
  ```xml
  <batch taskName="string" taskVersion="long" batchSize="!int" concurrency="!int=0" ...>
  ```
  ```java
  public BatchTaskBuilder<S, R> batchSize(int batchSize) {
      this.batchSize = batchSize;          // :273-274 无 Guard、无范围校验
      ...
  }
  protected int adjustBatchSize(int batchSize) {   // BatchChunkProcessor:83-92
      int ret = batchSize;
      if (jitterRatio > 0) { ... }
      if (ret <= 0) ret = 1;               // 唯一的「校验」：≤0 归一为 1（退化而非拒绝）
      return ret;
  }
  ```
- **严重程度**: P3
- **并发场景**: concurrency>1 时每个 chunk 线程各持有 ≤ batchSize 条记录，峰值 ≈ concurrency × batchSize × 行宽。
- **触发时序**: DSL 写 `batchSize="20000000"`（xdef 接受任意 int）→ builder 直通 → 各 loader（`readBatch(maxCount)` 系列均以 batchSize 为截断参数）尝试装满 2000 万条 → 单 chunk 物化全部命中内存。该配置是用户显式选择，但与 owner doc「内存友好」的总声明无任何冲突提示：无 WARN、无上限建议值、xdef 无 `!int{min:1,max:N}` 约束。`<source>` xpl 路径更弱：`safeLoad`（`ModelBasedBatchTaskBuilderFactory:456-457`）直接返回函数结果，函数返回多大 chunk 就多大，batchSize 仅作为参数传入不作为截断强制。
- **现状**: 对照同模块对 dispatch 参数的处理（C5-005：`loadBatchSize` 缺省 100 有默认值但同样无下界守卫），batchSize 是全部内存包络参数中唯一「无缺省保护 + 无范围约束 + 无文档建议」三缺的旋钮。
- **风险（规模判断）**: `batchSize=2×10^7` × 每行 400B ≈ 8GB 单 chunk 物化，缺省堆下确定性 OOM；但需要用户显式配置极大值，属「配置自由度对抗声明包络」，非缺省路径。判 P3。
- **建议**: xdef 声明合理范围（如 `!int{min:1}` + 文档建议 ≤ 10^4）；`BatchTaskBuilder.batchSize` 对超大值（如 > 10^6）打 WARN；`<source>` 路径在工厂层对返回 List 大小超 batchSize 时打 WARN 或截断。
- **失效面枚举（F3）**: 同族（包络参数无守卫）= ①`batchSize`（本条主体）；②`loadBatchSize`/`loadBatchMultiplyFactor`（C5-005 已立案，行为缺陷型）；③`maxProcessingItems`（有缺省 50000 + 等待 + fail-fast 三重守卫——模块内最佳实践，无缺陷）；④`ExportDbConfig.batchSize` 缺省 0（CL-008 已立案）；⑤`maxCountPerSheet`（写出侧缺省 20 万滚动，有界，无缺陷）。
- **信心水平**: 确定（三点证据均为直接可读代码）
- **误报排除**: 不是「用户配置极大值属用户责任」——本条的立案点是「包络声明的机制支撑缺位」（xdef/Builder/doc 三层均未设防），与 C5-005 判 P2 的「边界配置缺陷」同族但失败模式更温和（无挂死/丢数），且需显式极端配置，故降一档 P3。
- **复核状态**: 未复核
- **复核状态**：已复核（2026-10-16 独立复核）——保留 P3，新发现。xdef `batchSize="!int"` 无范围(:9)、Builder 赋值无 Guard(:272-275)、adjustBatchSize 仅 ≤0 归一 1(:82-92)、`<source>` 路径无截断（Factory:446/:456-458）四点亲证成立。

---

## C5-002 扩展（同门新变体，不占新编号）

### fetchSize 坏门的第二个触发变体：streaming=true + 非 MySQL 方言

第 1 轮枚举了 `streaming=false`（缺省）变体。第 2 轮全枚举证实同一门条件还有第二个入口：

```java
// JdbcBatchLoaderProvider.newState:243-248
if (streaming && dialect.getStreamingFetchSize() != null) {   // 非 MySQL 方言：第二条件恒 false
    ps.setFetchSize(dialect.getStreamingFetchSize());
} else {
    if (sql.getFetchSize() != -1 && fetchSize != null)         // && 门：sql 层无 fetchSize 时 provider 值丢弃
        ps.setFetchSize(fetchSize);
}
```

- **方言矩阵（实测 grep 19 个 dialect.xml）**: 仅 `mysql.dialect.xml:5` 定义 `streamingFetchSize="-2147483648"`（MySQL 驱动流式魔数）。`postgresql/oracle/mssql/db2/dm/h2/mariadb/es/tdengine/duckdb` 等 18 个方言文件均未定义 → `dialect.getStreamingFetchSize()` 返回 null（`DialectImpl.java:267-268` 直读 DialectModel，无缺省值）→ `streaming=true` 时落入 else 坏门分支。**变体 B**：`<jdbc-reader streaming="true" fetchSize="5000">` 在 PostgreSQL 上，用户同时做对了两件事（开流式 + 设 fetchSize），fetchSize 仍被静默丢弃。
- **加重事实（第 2 轮新证）**: `JdbcBatchLoaderProvider.newState:226-230` 经 `jdbcTemplate.currentConnection/openConnection` 取连接，**未关闭 autocommit**（全仓 `setAutoCommit(false)` 仅存在于 `JdbcBatcher`/`JdbcTransaction` 事务路径）。PostgreSQL 驱动在 autocommit=true 下即使 fetchSize 生效也忽略之——即非 MySQL 方言在当前装配下**没有任何路径可达流式读**：方言无 streamingFetchSize、provider fetchSize 被 && 门吃掉、autocommit 未关，三重不可达。此事实不改变 C5-002 的 P1 判级（已立案），但把修复建议从「改门条件」扩展为「门条件 + 非 MySQL 流式策略（autocommit/游标声明）+ 生效性 WARN」三项。
- **maxItems/pageSize 类参数核对（指令②指定）**: xdef `jdbc-reader` 全部 9 字段（querySpace/fetchSize/sqlName/maxRows/maxFieldSize/queryTimeout/partitionIndexField/rowMapper/streaming）在 `JdbcBatchSupport.newJdbcReader:20-66` **全部接线完整，无死配置遗漏**；应用门逐一核对：`maxRows>0`（:250-256，正确生效——且它是仅存的结果集行数硬上限，充当事实上的内存护栏）、`maxFieldSize>0`（:258-259，生效）、`queryTimeout>0`（:235-241，毫秒转秒，生效）。**门条件错误的家族成员只有 fetchSize 一个字段**，触发变体共 2 个（A：缺省 streaming=false；B：streaming=true + 18/19 方言）。orm-reader 对照：DSL 层（batch.xdef:125-128）根本不存在 fetchSize 类参数，内存包络唯一旋钮就是 batchSize，keyset 分页有界（见复核表）——无对应死配置面。

---

## ③ chunk 内累积结构全扫（站点逐一判定表）

BatchChunkProcessor 主链与全部 loader/consumer/processor 包装器的字段扫描结论（「任务级」指存活期=整个任务；「chunk 级」指随 chunk 丢弃）：

| # | 站点 | 结构 | 级别 | 上界判定 |
|---|---|---|---|---|
| 1 | `BatchChunkProcessor.loadItems:61-81` → `chunkContext.setChunkItems(items)` | List | chunk 级 | ≤ adjustBatchSize(batchSize)；≤0 归一 1；无上界强制（→ C5-009） |
| 2 | `BatchProcessorConsumer.outputs:62,71` | ArrayList / ConcurrentLinkedQueue | chunk 级 | 输入 ≤ batchSize，但 per-item 输出展开无界（**C5-003 维持**） |
| 3 | `BatchSequentialProcessor:33-48` while(true) | 生成环 | chunk 级 | 仅 generator hasNext 终止（**C5-003 维持**） |
| 4 | `BatchChunkContextImpl.completedItems/historyItems:86-116` | HashSet | chunk 级 | 1:1 用法 ≤ batchSize；展开用法随 C5-003 放大（同族汇点②） |
| 5 | `BatchChunkContextImpl.rowNumberMap:181-185` | IdentityHashMap | chunk 级 | 仅 load 期写入，≤ batchSize，有界 |
| 6 | `BatchTaskContextImpl` 12 监听器列表 + persistVars + getCache | List/Map/Cache | 任务级 | 公开 API 无守卫（**→ C5-008**）；平台内调用点守纪律 |
| 7 | `WithHistoryBatchConsumer:38-80` filtered/history | List/Set | chunk 级 | ⊆ 输入 items（有界；展开时随 C5-003 放大，同族汇点③） |
| 8 | `SingleModeBatchConsumer:19-30` | singletonList 逐条 | 即时 | 有界（反而缓解累积） |
| 9 | `SplitBatchConsumer.collector:49-51` | MultiMapCollector | chunk 级 | chunk 输入 × per-item 拆分因子；拆分因子无界时随 C5-003 同形态（同族汇点④） |
| 10 | `SplitBatchConsumer.activeConsumers:44,68` | ConcurrentHashMap | 任务级 | DSL 有白名单守卫；公共类无守卫（**→ C5-007**） |
| 11 | `ChunkSortBatchLoader.load:26-32` | 就地 sort | chunk 级 | ≤ batchSize，有界（负发现：非全量排序） |
| 12 | `RateLimitConsumer` / `BlockingSinkBatchConsumer` | 无集合 | 即时 | 有界（后者委托平台 sink 背压） |
| 13 | `AbstractRetryBatchConsumer` + `OrmBatchRecordSnapshotBuilder:27-38` | 快照 Map | consume 调用期 | ≤ chunk 输出，重试结束即弃，有界 |
| 14 | Retry*/Skip*/Filtered*/Invoker*/Multi*/Transformed*/Eval*/AddCompleted* 全部包装 consumer | 纯引用转发 | — | 字段扫描（见证据命令输出）：无跨 chunk 集合字段，有界 |
| 15 | Adapted/BlockingSource/Filtered/Invoker/PostProcess/Retry loader 包装器 | 纯引用转发 | — | 同上，有界 |
| 16 | Cast/GetProp/Identity/Composite processor、FieldsProcessor(exp:36-63) | per-item Map | 即时 | 1:1 流式，有界 |
| 17 | `BatchGenLoaderProvider.load:65-74` | ArrayList(batchSize) | chunk 级 | for 循环 ≤ batchSize，有界（第 1 轮负发现 #5 维持）；`BatchGenState` 的 subCases 列表=模型 case 数，模型级有界 |

**C5-003 同族汇点穷举结论**: per-item 无界展开的**根因**唯一（processor 的 `consume.accept` 可多次调用 / generator 环），**汇点**共 4 个——①`BatchProcessorConsumer.outputs`（主汇点，C5-003 已立案）；②`BatchChunkContextImpl.completedItems/historyItems`；③`WithHistoryBatchConsumer.history`（下游派生）；④`SplitBatchConsumer.collector`。②③④均以 chunk 输入为界、随展开因子线性放大，修复根因（展开上限）即同时覆盖四点——不单独立案，作为 C5-003 修复时的回归面清单。

---

## ④ filterProcessed / history / task var 累积面

1. **DaoBatchRecordHistoryStore.filterProcessed:35-55**（复核维持）: `recordMap` 为 chunk 局部 LinkedHashMap（≤ records ≤ batchSize）；IN 列表 = `recordMap.keySet()` ≤ batchSize；`findAllByQuery` 结果 ≤ 该批 key 的历史行数 ≤ batchSize。无跨 chunk 累积。`saveProcessed:90-115` 逐条 `dao.saveEntity`——历史行数增长在 **DB 侧**（NopBatchRecordResult 表），内存面为逐条短命对象，有界。
2. **JdbcKeyDuplicateFilter.filterProcessed:48-81**（复核维持 + 新兄弟线索）: 单键/复合键两分支的 `keyMap` 均 chunk 局部；IN 参数数 = chunk 记录数 ≤ batchSize（方言参数上限会先于内存约束，第 1 轮已注）。新增两条同文件线索：`LOG.info(...StringHelper.join(list,","))`（:62,:76）将整批重复键拼进单条日志（→ CL-029）；`keyMap.keySet().removeAll(list)` 对 ArrayList 为 O(n×m)（:64,:78，→ CL-030，CL-011 同族新兄弟）。
3. **JdbcPageBatchLoaderProvider.load:85-98**（复核维持）: 每页 `findPage(sql, offset, batchSize)`，页内有界；游标仅存 `state.range`（两个 long）。
4. **task var 面（指令④指定）**: ①`BatchTaskContextImpl.persistVars`（MapVarSet，任务级）——平台内**无写入者**（grep 全仓仅 accessor），纯用户 API，无界面并入 C5-008；②`NopBatchTaskVar` 实体（nop-batch-dao）+ `NopBatchTaskVarBizModel`（CrudBizModel）——DB 后端 EAV 行存储，经标准 CRUD 服务读写，批引擎运行期不自动写入，**非内存累积面**（负发现）；③EAV 表的 DB 行数随任务 var 数增长属存储容量问题，不属 C5 内存维度。

---

## ⑤ EtlTaskStateStore / ExportDbTool / ImportDbTool 物化面

1. **EtlTaskStateStore（:17-145）——计数器级状态，非记录级，有界**: `EtlTaskState` 的内存表示 = `Map<String, EtlTableState>`（每表一个含 7 个 long/boolean/timestamp 的计数器对象），规模 = O(配置表数)，与记录总量无关。`saveStateFile:129-144` 每 chunk 全量 JSON 序列化 + 临时文件原子 rename——「全量物化」的客体是常数级计数器，内存无风险（→ 每 chunk 序列化 CPU/IO 属 C9，登记 CL-032）。构造器 :26-27 `JsonTool.parseBeanFromResource` 一次性解析状态文件——文件规模=表数×常数，有界。
2. **ExportDbTool Excel 导出路径（:368-375）——与 C5-001 非同族（对称面负发现）**: 导出走 `ExcelResourceIO.openOutput` → `ExcelRecordOutput`，写侧逐行 `write`（`ExcelRecordOutput.java:119-135`）→ `ExcelSheetWriteSupport`（构造器 :25 `new BufferedWriter(sheetFile.getWriter(null))`）逐行 XML 直写**临时目录文件**（`ExcelRecordOutput.java:216-218`），close 时 zip 回目标资源（:254-276）；`maxCountPerSheet` 缺省 200,000 行滚动分 sheet（`ExcelIOConfig.java:15`，`ExcelRecordOutput.java:122-127`）。行对象 `makeRow:152-171` 单例复用。**内存峰值 = 常数级缓冲 + 单行对象，与导出行数无关**。结论：读侧全量物化（C5-001）与写侧流式落盘构成不对称——修复 C5-001 的流式化技术在同模块写侧已有完整先例（`ExcelSheetWriteSupport` 即逐行落盘范本）。
3. **ExportDbTool JDBC 读侧残余暴露（→ CL-028）**: `newLoader:326-334` 的 fetchSize 经 `tableConfig.buildSQL(config.getFetchSize(), ctx)`（`ExportTableConfig.java:40-63`）写入 SQL 对象，由 `JdbcHelper.setFetchSize:228-231` 生效——第 1 轮结论维持。但 `ExportDbConfig` 的 `fetchSize` 缺省 **null**（`_gen/_ExportDbConfig.java:78-80`）、`streaming` 缺省 true（:108）：非 MySQL 方言 + 未显式配 fetchSize 时（streaming=true 因方言无 streamingFetchSize 值而不产生任何 setFetchSize 调用），驱动按缺省行为在 executeQuery 缓冲——工具级缺省暴露，登记 CL-028。
4. **ImportDbTool（:358-377）——CSV-only 读侧流式（复核维持）**: `newLoader` 仅构造 `CsvResourceRecordIO`（格式固定取自文件扩展名，:361-366），无 Excel 导入路径 → 不命中 C5-001 族；`batchSize` 有 `>0` 守卫（:301-302，对照 CL-008 的 ExportDbTool 无守卫）。`checkKeyFields` 路径的 `JdbcKeyDuplicateFilter`/`JdbcUpdateBatchConsumer`（:311-322）均 chunk 界。
5. **分页导出**: ExportDbTool 不走 OFFSET 分页（用 streaming ResultSet + readBatch 节奏读取），无页累积面；`JdbcPageBatchLoaderProvider` 页界 batchSize（前述）。两工具的 executor 队列 `Integer.MAX_VALUE`（:150/:232）为 CL-009 已立案面，本轮不复述。

---

## 新增维度集外线索（自 CL-028 起）

### [nop-batch-CL-028]（维度集外线索，不立案展开）
- **形态归属**: C5 邻接（候选 P3）。`ExportDbTool` + 非 MySQL 方言 + `fetchSize` 未配置（config 缺省 null，`_gen/_ExportDbConfig.java:78-80`）时，`streaming=true` 缺省不产生任何 setFetchSize（仅 MySQL 方言有 streamingFetchSize 值），驱动按缺省行为全量缓冲结果集。一句话证据：19 个 dialect.xml 仅 mysql 定义 streamingFetchSize，exp 工具对非 MySQL 无 fetchSize 缺省兜底。

### [nop-batch-CL-029]（维度集外线索，不立案展开）
- **形态归属**: C7 日志放大（候选 P3）。`JdbcKeyDuplicateFilter.java:62,76` `LOG.info("...ids={}", ..., StringHelper.join(list, ","))` 将当前 chunk 全部重复键拼接为单条日志——batchSize=1000 且整批重复时产生数百 KB 日志行。一句话证据：join 无截断。

### [nop-batch-CL-030]（维度集外线索，不立案展开）
- **形态归属**: C9 分配/CPU 放大（候选 P3）。`JdbcKeyDuplicateFilter.java:64,78` `keyMap.keySet().removeAll(list)`（list 为 ArrayList）为 O(n×m)——CL-011（DaoBatchRecordHistoryStore 的 removeAll）同族第三处未优化位点（第二处为 WithHistoryBatchConsumer 已 HashSet 化）。一句话证据：同族三处，一处已优化两处未同步。

### [nop-batch-CL-031]（维度集外线索，不立案展开）
- **形态归属**: C2/C8 数据完整性语义（候选 P2/P3）。`ModelBasedBatchTaskBuilderFactory.java:308` 白名单守卫 `!consumerMap.containsKey(tag) ? null : ...` 使 tagger 产出的未匹配 tag 记录在 `SplitBatchConsumer.consume:53-56` 被静默跳过（null → 不消费）；xdef 对 forTag 的注释（batch.xdev `@forTag`）未声明「无匹配即丢弃」，与 owner doc at-least-once 断点续传声明存在语义张力。一句话证据：守卫返回 null 即丢数据，无 WARN。

### [nop-batch-CL-032]（维度集外线索，不立案展开）
- **形态归属**: C9 热路径（候选 P3）。`EtlTaskStateStore.saveTableState:108-122` 每 chunk 触发 `saveStateFile`（:129-144）——整任务状态 JSON 全量序列化（pretty=true）+ 临时文件写 + rename，chunk 频次下为 O(表数×chunk 数) 的序列化 CPU 与磁盘 IO。一句话证据：增量计数器却做全量序列化。

---

## 总计与结论

| 类别 | 数量 | 明细 |
|---|---|---|
| 本轮新增 C5 发现 | 4 | P0=0；P1=0；P2=0；**P3=4**（C5-006 maxCountExpr 能力缺位+死代码+无兜底、C5-007 SplitBatch activeConsumers 程序化无界、C5-008 任务级容器注册 API 无守卫、C5-009 batchSize 无上界强制） |
| 已立案发现复核 | 5 | C5-001~005 全部维持，C5-001 修复面收窄（解析层已流式）、C5-002 新增变体 B + 非 MySQL 流式三重不可达加重事实 |
| 反虚构预注 | 2 | orm-reader keyset 有界、CSV 流式——均实测维持，未推翻 |
| 维度集外线索 | 5 | CL-028（C5 邻接 P3 候选）、CL-029（C7 P3 候选）、CL-030（C9 P3 候选）、CL-031（C2/C8 候选）、CL-032（C9 P3 候选） |
| 新增负发现 | 14 | 见 ③ 全扫表（#1,4,5,8,9,11-17）与 ④⑤ 节（task var/EAV、状态计数器、Excel 写出对称面、CSV-only 导入、分页导出） |

**深挖继续规则判定——终止信号成立，建议不启动第 3 轮**：
1. 本轮无新增 P0/P1/P2；4 条新增全部 P3，且其中 3 条（C5-007/008/009）为「平台内调用点已守卫/无平台内触发配置」的理论风险面，1 条（C5-006）为能力缺位而非新增内存路径。
2. 指令 ①~⑤ 的全部枚举要求已完成且收敛：读侧物化族（excel 唯一，解析层流式）、门条件族（唯一 fetchSize，两变体）、展开族（唯一根因，四汇点已穷举）、任务级容器族（三站点已穷举）、状态物化族（计数器级有界）——每个家族的成员清单已闭合并给出行级证据。
3. 全部 34 个 core loader/consumer/processor 文件经字段扫描无遗漏的跨 chunk 集合；11 子模块的写路径、状态面、工具面逐点核对完毕。
4. 第 1 轮两个 P1 的修复输入已备齐：C5-001 的修复面收窄（解析层 `XlsxToRecordOutput` 已逐行回调，改 collector 下推即可）与 C5-002 的修复清单扩展（门条件 + 非 MySQL 流式策略 + 生效性 WARN）。
