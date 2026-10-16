# C5 内存有界性 — nop-batch 第 1 轮初审报告（盲审轮）

## 审计单元与范围

- **审计单元**: nop-batch 聚合器，11 个含 Java 的 jar 子模块（api/app/biz/core/dao/dsl/exp/gen/jdbc/orm/service），src/main 手写代码（不含 `_` 前缀生成物与 target/）。基线口径：218 files / 15,698 行。
- **仓库**: `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`，分支 `concurrency-audit-remediation`，HEAD `4b7c7897de`。
- **维度定义（C5 内存有界性）**: 无界集合/队列/缓存的累积；全量物化 vs 流式处理；OOM 风险路径；规模包络判断。owner doc（`docs-for-ai/03-modules/nop-batch.md`）声明批处理引擎定位「大数据量场景」、Chunk 模式「内存友好」——因此 **loader 层全量物化面是核心审计对象**。
- **盲审纪律声明**: 本轮为盲审第 1 轮，未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何既有记录；仅阅读了方法论文件（`ai-dev/skills/concurrency-resource-audit-prompt.md`、`deep-audit-prompts.md` 附录 A）、基线 `_tmp/cra-baseline-nop-batch.txt`（C5/C8/C9 节）、owner doc 与 live code。
- **检查范围说明**: 逐文件精读了 core 引擎（BatchTask/BatchTaskBuilder/BatchChunkProcessor/BatchTaskContextImpl/BatchChunkContextImpl/全部 loader 与关键 consumer）、PartitionDispatchQueue + 两个 dispatch provider、orm/jdbc loader、file/excel reader 接线（FileBatchSupport）及其底层读取实现（CsvRecordInput/ExcelRecordInput，位于 nop-core/nop-report，作为 nop-batch file-reader/excel-reader 的实现载体核对）、gen 模块 generator/request-generator、historyStore 三实现、exp 两工具及其 config 默认值、biz 导入导出链。为判定 oracle/jdbc 驱动缓冲语义，追读了 nop-dao 的 `JdbcHelper.prepareStatement/setFetchSize`、`OrmEntityDao.findNext`、`IEntityDao` 契约与 xdef 默认值（`batch.xdef`）。

## 基线对照

- 基线 C5 节：「unbounded candidates count = 0」，`readAllBytes/readString/readLines` 站点为空——grep 只能覆盖直接 API 调用。本轮证实：nop-batch 的全量物化面全部经由**库调用间接发生**（Excel 解析器 beforeRead 一次性物化、JDBC 驱动 executeQuery 时全量缓冲、provider 返回 List 常驻），基线 grep 口径无法命中，属预期盲区，已由人工逐行核对补齐。
- 基线 C8 节：`loadBatchMultiplyFactor=30`、`maxLockQueueCountPerThread`、Semaphore(capacity) 站点已核对（见 C5-005 判定与「已验证有界面」节）。
- 基线 C9 节：per-record 循环站点 81 处——本轮发现的 C9 形态线索登记为维度集外线索（CL-008/CL-011）。

## 已声明契约（判级校准基准）

| 契约 | 出处 | 对本轮的校准作用 |
|---|---|---|
| Chunk 模式「内存友好」 | owner doc 功能概览 | 物化路径违背该目标的立案依据；C5-001/C5-002/C5-003/C5-004 均以此为包络对照 |
| orm-reader 未配置 query 全表加载打 WARN（`nop.batch.orm-reader-no-query`） | owner doc + 代码注释 | 该 WARN 行为本身不重复立案；只审上游调用方与 WARN 后续路径（结论：有界，见已验证节） |
| at-least-once 断点续传 | owner doc | processingItems/completedIndex 相关内存面按重投递语义校准 |
| historyStore 同事务写入 | owner doc + BatchTaskBuilder 自动提升 | saveProcessed 的内存面按 chunk 界校准 |

---

## 发现清单

### [nop-batch-C5-001] excel-reader 全量物化：ExcelRecordInput.beforeRead 一次性将整个数据表物化为内存 List，违背「Chunk 内存友好」声明包络

- **文件**: `nop-batch/nop-batch-dsl/src/main/java/io/nop/batch/dsl/manager/FileBatchSupport.java:140-190`（接线）+ `nop-report/nop-report-core/src/main/java/io/nop/report/core/record/ExcelRecordInput.java:87-98`（物化点）
- **证据片段**:
  ```java
  // ExcelRecordInput.beforeRead —— 读任何一行之前先把整张 sheet 解析完
  public void beforeRead(Map<String, Object> map) {
      HeaderListRecordOutput<Map<String, Object>> collector = new HeaderListRecordOutput<>(...);
      ...
      this.output = new XlsxToRecordOutput(name -> collector);
      this.output.loadFromResource(resource);
      String sheetName = config.getDataSheetName();
      this.output.parseSheet(sheetName);
      list = collector.getResult();          // 全表 List<Map<String,Object>> 常驻
  }
  @Override
  public boolean hasNext() { return nextIndex < list.size(); }   // 之后只是内存下标推进
  ```
  nop-batch 侧接线：`FileBatchSupport.newExcelReader` → `ResourceRecordLoaderProvider.setRecordIO(newExcelIO(loaderModel))`，`ResourceRecordLoaderProvider.loadItems` 调用的 `state.input.readBatch(batchSize)` 只是对该已物化 list 的分片。
- **严重程度**: P1
- **并发场景**: 单线程加载路径（ResourceRecordLoaderProvider.readBatch 调用前）；与并发无关，是纯粹的输入规模→堆占用物化问题。多 chunk 线程共享同一 LoaderState 不放大该面（物化只发生一次）。
- **触发时序**: 非竞态问题，触发路径为确定性：批任务配置 `<excel-reader filePath="big.xlsx">` → setup → `newLoaderState` → `recordIO.openInput` → 首个 chunk 的 `loadItems` 触发 `input.beforeRead` → `parseSheet` 将整张 sheet（XLSX 单 sheet 上限 1,048,576 行）解析进 `collector.getResult()` 后才开始逐 chunk 分片。任何一行数据处理之前，全表数据已驻留堆内。
- **现状**: owner doc 声明「Chunk 处理模式……内存友好」且批处理引擎定位「大数据量场景」，但 excel-reader 的实际内存占用 = 整张 sheet 的物化体积，与 batchSize 无关；同时 `FileBatchSupport.newExcelReader` 中 `maxCountExpr` 支持被注释禁用（:150-151），excel-reader 连行数上限配置都不存在。
- **风险（规模判断）**: XLSX 单 sheet 上限 1,048,576 行。按每行 10 列、`LinkedHashMap`+装箱值每行约 1.5–3KB 估算，一个 50 万行 × 20 列的报表级文件物化约 1.5–3GB 堆，加上 XLSX 解析中间结构（`XlsxToRecordOutput` 的 OOXML 解析层），默认 1–2GB 堆配置下**确定性 OOM**；这是声明包络（大数据量导入）内的 OOM，不是超包络。对照 F7（OOM 同族：一次载入全量 vs 流式）。
- **建议**: 将 `ExcelRecordInput` 改造为游标式读取（解析层按行回调产出而非聚合到 collector，参照 `CsvRecordInput` 的迭代器式实现），或在 nop-batch 层给 excel-reader 强制要求 `maxCountExpr`/分片配置并文档声明 Excel 输入的规模上限。短期最小修复：恢复 `FileBatchSupport:150-151` 被注释的 `maxCountExpr` 接线并不能解决物化（beforeRead 仍全量），必须动 `ExcelRecordInput` 本身；至少应在 owner doc 明确声明「excel-reader 仅适用小文件、大数据量用 CSV」并给出文件大小阈值。
- **失效面枚举（F3）**: 读侧同族 = ①`FileBatchSupport.newExcelReader`（本条主体，缺陷）；②`CsvResourceRecordIO.openInput` → `CsvRecordInput`（逐行迭代器流式，已核对 `next()` 走 `it.next()`，无缺陷）；③`ModelBasedResourceRecordIO`（fileModelPath 路径，基于 record 模型逐行解析，流式，无缺陷）；④`ImportDbTool.newLoader`（仅 CSV 格式，流式，无缺陷）；⑤写侧同族 `ExcelRecordOutput`（逐行 `writeRow` + flush，`out.writeBatch(items)` 每 chunk 分片写，非本形态，无缺陷）；⑥`DefaultBizEntityExporter` 默认导出格式 xlsx——走写侧 `ExcelRecordOutput`，不命中读侧物化，无缺陷。
- **信心水平**: 确定（物化代码路径直接可读，规模测算为估算但量级可靠）
- **误报排除**: 不属于「有界声明存在的缓存/队列」误报——这里没有任何上限声明；也不属于「平台基础设施自身实现」（`ExcelRecordInput` 虽在 nop-report，但它是 nop-batch DSL `excel-reader` 的唯一内建实现载体，owner doc 将「Excel 文件作为数据源」列为批处理引擎的声明能力，缺陷面归属 nop-batch 的 loader 契约）。
- **复核状态**: 未复核
- **复核状态**：已复核（2026-10-16 独立复核）——保留 P1，新发现。特别指令四子项全部亲证成立（物化点唯一 / 量级可信 / maxCountExpr 无兜底 / 平凡配置可及），另补现状强证据与 F3 维度集外兄弟 1 处（不改判级），详见文末「维度复核结论」二节。

### [nop-batch-C5-002] jdbc-reader 非 streaming 模式下 DSL fetchSize 配置死配置化：参数门条件用 `&&` 导致 fetchSize 永不生效，驱动按缺省行为在 executeQuery 时全量缓冲结果集

- **文件**: `nop-batch/nop-batch-jdbc/src/main/java/io/nop/batch/jdbc/loader/JdbcBatchLoaderProvider.java:243-248`（缺陷点）；`nop-batch/nop-batch-dsl/src/main/java/io/nop/batch/dsl/manager/JdbcBatchSupport.java:27-28`（DSL 接线）；`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/batch.xdef:130-132`（streaming 缺省 false）
- **证据片段**:
  ```java
  // JdbcBatchLoaderProvider.newState —— 345 行处 prepareStatement 已由 JdbcHelper 应用 sql.fetchSize，
  // 此处的 provider 级 fetchSize 是唯一兜底，但门条件写成了 &&
  if (streaming && dialect.getStreamingFetchSize() != null) {
      ps.setFetchSize(dialect.getStreamingFetchSize());
  } else {
      if (sql.getFetchSize() != -1 && fetchSize != null)   // && ：sql 层无 fetchSize 时 provider 值被静默丢弃
          ps.setFetchSize(fetchSize);
  }
  ```
  DSL 侧：`JdbcBatchSupport.newJdbcReader` 将 `<fetchSize>` 装到 provider 字段（`loader.setFetchSize(loaderModel.getFetchSize())`）；而 `<query>` 路径生成的 SQL（`DaoQueryHelper.queryToSelectFieldsSql`）与 `<sql>` 文本路径的 SQL 对象均不携带 fetchSize（缺省 -1），门条件恒 false。
- **严重程度**: P1
- **并发场景**: 每个并发 chunk 线程共享同一 LoaderState（`synchronized(state)` 串行读），但物化发生在 `setup` → `ps.executeQuery()` 时点（单次、任务启动期），与并发度无关。
- **触发时序**: 确定性路径：DSL 配置 `<jdbc-reader fetchSize="1000"><query>…</query></jdbc-reader>`（streaming 取 xdef 缺省 false）→ `newState` 打开连接、`prepareStatement`（`JdbcHelper.setFetchSize(sql, ps)` 因 sql.fetchSize=-1 跳过）→ 进入 else 分支，`sql.getFetchSize() != -1` 为 false → `setFetchSize` 不执行 → `executeQuery()` 按驱动缺省行为执行。MySQL Connector/J 在 fetchSize 未设置（0）时把**整个结果集**读入客户端内存；PostgreSQL 在 autocommit 下同样全量拉取。
- **现状**: 用户显式配置的 fetchSize 上限在最常见的 DSL 形态（query/sql 文本 + streaming=false）下是死配置，没有任何报错或 WARN；系统的实际内存上界退化为「结果集总大小」，batchSize 分片只影响物化之后的读取节奏。
- **风险（规模判断）**: 1,000 万行 × 每行约 200B 的表在 executeQuery 时约 2GB+ 客户端堆占用（驱动层还会叠加行对象中间结构），默认堆下 OOM。触发配置平凡：jdbc-reader + 大表 + 忘配 streaming（缺省即 false）。注：`ExportDbTool` 路径不受此缺陷影响——它的 fetchSize 经 `ExportTableConfig.buildSQL` 写入 SQL 对象，由 `JdbcHelper.setFetchSize(sql, ps)` 生效；且 exp 工具 `streaming` 缺省 true。本缺陷面集中在 DSL `jdbc-reader`。
- **建议**: 将门条件改为 `if (fetchSize != null) ps.setFetchSize(fetchSize);`（`||` 语义；sql 层 fetchSize 已由 `JdbcHelper.setFetchSize` 处理，provider 层只需独立兜底）；同时在 streaming=false 且 fetchSize 未生效时打 WARN。参照平台惯例可引入 `Guard` 校验「非 streaming 必须显式 fetchSize 或声明小结果集」。
- **失效面枚举（F3）**: 同族 = ①DSL `<jdbc-reader><query>` + fetchSize（死配置，缺陷）；②DSL `<jdbc-reader><sql>` 文本 + fetchSize（SQL 对象无 fetchSize，同缺陷）；③DSL `<jdbc-reader sqlName>` + sql-lib 模型自带 fetchSize（SQL 层生效，功能可用，provider 值仅在同时设置时覆盖——语义冗余但无害）；④`ExportDbTool`（fetchSize 入 SQL，生效；未设 fetchSize 且 streaming=false 时依赖驱动缺省——风险同源但由 config 缺省 streaming=true 缓解）；⑤`JdbcPageBatchLoaderProvider`（findPage 每页 batchSize 行，页内即上界，不受影响）；⑥`orm-reader`（走 `findNext` keyset 分页，不经此路径）。
- **信心水平**: 确定（门条件与各路径的 fetchSize 传递链均已逐点核对；驱动全量缓冲语义为 MySQL/PG 官方文档化行为）
- **误报排除**: 不是「原语使用本身」误报——缺陷在复合条件（F3 形态：修一层漏一层；`JdbcHelper` 已修 sql 层，provider 层门条件使其兜底失效）；也不是「有界声明存在」——xdef 对 streaming 缺省 false 且未声明「fetchSize 仅在 xxx 组合下生效」。
- **复核状态**: 未复核
- **复核状态**：已复核（2026-10-16 独立复核）——保留 P1，新发现。门条件恒 false、变体 B 方言矩阵（实测 20 个 .dialect.xml 仅 mysql 定义 streamingFetchSize，计数修正 19→20）、autocommit 未关（openConnection 全链无 setAutoCommit）三重事实逐一亲证，终裁 P1，详见文末「维度复核结论」二节。

### [nop-batch-C5-003] useBatchRequestGenerator 路径下 chunk 输出集无上界：BatchSequentialProcessor 的 while(true) 请求生成绕过 batchSize 包络，全部输出在 consume 前驻留内存

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/consumer/BatchProcessorConsumer.java:61-71,105-107`（累积点）；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/processor/BatchSequentialProcessor.java:30-48`（无界生成环）；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/BatchTaskBuilder.java:399-402`（接线）
- **证据片段**:
  ```java
  // BatchProcessorConsumer.consume —— 输出集在整批 consume 前全量驻留
  Collection<R> outputs = newOutputs();            // ArrayList / async 时 ConcurrentLinkedQueue
  for (S item : items) {
      processor.process(item, outputs::add, batchChunkCtx);   // 每个 item 可 add 任意多次
      ...
  }
  consumeResult(outputs, batchChunkCtx);           // 全部输出累积完后才一次性下发
  ```
  ```java
  // BatchSequentialProcessor.process —— 单个 chunk item 的生成环仅由 generator 自身 hasNext 终止
  do {
      S request = seq.nextRequest(context);
      if (request == null) break;
      processor.process(request, response -> { seq.onResponse(response, context); consumer.accept(response); }, context);
  } while (true);
  ```
- **严重程度**: P2
- **并发场景**: chunk 线程内顺序累积；asyncProcessor=true 时输出经 ConcurrentLinkedQueue 跨线程追加，仍无上界。
- **触发时序**: 确定性：DSL 配置 `useBatchRequestGenerator="true"` + loader 产出 `IBatchRequestGenerator` 实例（如 `SequentialBatchRequestGenerator`，其子 case 的 `BatchGenState` 计数由 `totalCountExpr` 驱动）→ 单个 chunk item 在 process 中循环生成 `totalCount` 个请求 → 每个响应 `outputs::add` → `outputs` 增长到 totalCount 后才调用一次下游 consumer。batchSize 只约束 chunk **输入** item 数，不约束 item 的**展开输出数**。
- **现状**: 框架自带路径（xdef 声明的 `useBatchRequestGenerator` 特性）使「每 chunk 内存 ~ batchSize」的包络声明对输出侧失效；普通 xpl processor 多次调用 `consume()`（owner doc 明文允许「processor 中可调用多次 consume(item) 输出多个结果」）同理无框架级上限。
- **风险（规模判断）**: `totalCount=500万`、每输出为 Map/DTO 约 500B 时，单个 chunk 的 `outputs` 达约 2.5GB → OOM； totalCount=100 万、每输出 1KB 时约 1GB。触发配置为生成器型批任务（如批量报文生成）配大 totalCount——该特性正是为「一次加载、连续生成」设计的，大 totalCount 是其正常用法而非滥用。
- **建议**: 在 `BatchSequentialProcessor`/`BatchProcessorConsumer` 增加每 chunk 输出数上限（复用 `ResourceRecordLoaderProvider.maxProcessingItems` 的「等待+fail-fast」形态，`BatchErrors` 已有 `ERR_BATCH_TOO_MANY_PROCESSING_ITEMS` 可复用语义），或在 owner doc 对 request-generator 特性显式声明「totalCount 必须 ≤ 单 chunk 内存包络」。
- **失效面枚举（F3）**: 同族 = ①`useBatchRequestGenerator=true` 的 DSL 路径（本条主体）；②普通 xpl processor 多次 `consume()`（同一 `outputs::add` 汇聚点，无上限，同形态）；③`FieldsProcessor`（exp 工具用，1:1 映射，有界）；④`CastBatchProcessor`/`GetPropBatchProcessor`/`IdentityBatchProcessor`（1:1，有界）；⑤`SingleModeBatchConsumer`（逐条消费，反而缓解累积，无缺陷）。
- **信心水平**: 确定（代码路径确定；风险量级取决于用户 totalCount 配置，故 P2 而非 P1）
- **误报排除**: 不是「用户代码无限输出属用户责任」——累积容器 `outputs` 是框架结构，且 request-generator 的展开环是框架类；也不是「有界声明存在」——xdef 与 owner doc 均未对该路径声明输出上界。
- **复核状态**: 未复核
- **复核状态**：已复核（2026-10-16 独立复核）——保留 P2，新发现。累积点（BatchProcessorConsumer:62/:71/:102）、生成环（BatchSequentialProcessor:33-48）、接线（BatchTaskBuilder:399-402）亲证成立；P1/P2 边界情形注记：useBatchRequestGenerator 缺省 false 且 totalCount 为用户显式配置，非平凡可及，维持 P2。

### [nop-batch-C5-004] provider loader 返回 List 时全量常驻任务全程：ListBatchLoader 持有整表引用，无上限守卫亦无包络声明

- **文件**: `nop-batch/nop-batch-dsl/src/main/java/io/nop/batch/dsl/manager/ModelBasedBatchTaskBuilderFactory.java:460-474`（包装点）；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/loader/ListBatchLoader.java:17-37`（常驻点）
- **证据片段**:
  ```java
  // ModelBasedBatchTaskBuilderFactory.newLoaderProvider
  if (value == null || value instanceof List)
      return new ListBatchLoader<>((List<Object>) value);   // 整个 List 被任务持有直到结束
  ```
  ```java
  // ListBatchLoader —— list 字段存活期 = 整个任务
  public synchronized List<S> load(int batchSize, IBatchChunkContext context) {
      ...
      int n = Math.min(list.size() - offset, batchSize);
      List<S> ret = new ArrayList<>(list.subList(offset, offset + n));
      offset += n; ...
  }
  ```
- **严重程度**: P2
- **并发场景**: 多 chunk 线程共享同一 ListBatchLoader 实例（`synchronized` 方法串行取段），List 本体只有一份常驻——并发不放大，但常驻时长 = 任务全程。
- **触发时序**: 确定性：DSL `<loader><provider>` 函数返回 List（owner doc 明文「返回 `IBatchLoader` 或 `List`」）→ `newLoaderProvider` 包装为 `ListBatchLoader` → List 自 setup 起被 task 级引用，逐 chunk 切片直至任务结束才可回收。分片只是读取节奏，不减少驻留。
- **现状**: 这是声明能力（owner doc 常用 loader 表），但该形态的内存包络与「Chunk 内存友好」的总声明相悖且无任何文档区分；List 之外没有数量上限检查或 WARN。
- **风险（规模判断）**: provider 返回 500 万行 × 每行 400B 的 List ≈ 2GB 常驻堆，任务全程不释放；叠加 dispatch 模式下 dispatch 队列再缓存 capacity 份记录（见 C5-005）时峰值进一步上浮。属于「声明包络内可具体化的 OOM 路径」，但因数据由用户代码产出、规模完全自定，判 P2。
- **建议**: owner doc 在 provider 一节显式声明「返回 List 的形态将全量常驻内存，大数据量必须返回 IBatchLoader」；可选在 `newLoaderProvider` 对超大 List（如 > 10^6）打 WARN。
- **失效面枚举（F3）**: 同族 = ①DSL `<provider>` 返回 List（本条主体）；②DSL `<loader><bean>` 直接返回含 List 的自定义 `IBatchLoaderProvider`（等价常驻，用户自担，同形态无守卫）；③`newLoaderProvider` 返回 IEvalFunction 分支（逐 chunk 调用，有界）；④core 测试中的 `ListBatchLoader` 直用（测试代码，不是生产守卫对象）。
- **信心水平**: 确定
- **误报排除**: 不是「已声明的边界」误报——owner doc 声明了能力但未声明内存代价；将其按守卫缺失/声明缺口立案符合「守卫缺失、文档未声明才是缺陷」的口径。
- **复核状态**: 未复核
- **复核状态**：已复核（2026-10-16 独立复核）——保留 P2，新发现。包装点（ModelBasedBatchTaskBuilderFactory:460-474）与常驻点（ListBatchLoader:17-37，list 存活期=任务全程）亲证成立。

### [nop-batch-C5-005] dispatch 容量参数无守卫：loadBatchSize≤0 或 multiplyFactor≤0 时 capacity≤0，Semaphore(0) 使 fetch/消费双端永久挂起、数据静默丢弃，负值直接构造期 IAE

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/loader/PartitionDispatchQueue.java:78-87,337-350`；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/loader/PartitionDispatchLoaderProvider.java:61-62`；`AsyncFetchPartitionDispatchLoaderProvider.java:78-79`；`nop-batch/nop-batch-dsl/src/main/java/io/nop/batch/dsl/manager/ModelBasedBatchTaskBuilderFactory.java:356-381`（DSL 装配无校验）
- **证据片段**:
  ```java
  // PartitionDispatchQueue —— capacity 直接进 Semaphore，无下界校验
  public PartitionDispatchQueue(int capacity, Function<T, Integer> partitionFn, int fetchThreadCount) {
      this.semaphore = new Semaphore(capacity);   // capacity<=0: 0→永久拿不到许可; 负→IllegalArgumentException
      ...
  }
  private boolean acquirePermits(int permits) {
      try {
          while (!finished) {                      // capacity=0 时 tryAcquire 永不成功，仅 finished 可跳出
              if (semaphore.tryAcquire(permits, 500, TimeUnit.MILLISECONDS)) return true;
          }
          LOG.info("nop.batch.discard-batch-when-finished:size={}", permits);
          return false;                            // 数据被静默丢弃
  ```
  DSL 侧：`buildDispatchConfig` 将 `dispatcherModel.getLoadBatchSize()` 原样传入，无 `>0` 校验（xdef 缺省 100 仅覆盖「未配置」，不拦截显式 `loadBatchSize="0"`）。
- **严重程度**: P2
- **并发场景**: fetch 线程（AsyncFetch 模式，executor 执行）或 chunk 线程自身（同步模式，takeBatch 内联 fetcher）阻塞于 `acquirePermits` 的 500ms 重试环；消费端阻塞于 `notEmpty.await(500ms)` 环。
- **触发时序**: T1: DSL 写 `loadBatchSize="0"`（或程序化构造 `BatchDispatchConfig.setLoadBatchSize(0)`）→ setup 构造 `Semaphore(0)`；T2: 首页数据到达 `addBatch(list)` → `acquirePermits(list.size())` 进入 while 环，0 许可下 `tryAcquire` 永不成功；T3: `finished` 仅在任务 `onAfterComplete` 时置位，而任务结束依赖 chunk 循环退出，chunk 循环又在 `takeBatch` 等数据 → **环形等待，任务永久挂起**；若发生在任务收尾阶段（finished 已置位），则该批数据被 `discard-batch-when-finished` 静默丢弃（at-least-once 语义下表现为本次运行丢数据）。`loadBatchMultiplyFactor=0` 同型（AsyncFetch/Sync provider 的 setter 亦无校验）；负值在构造期抛 IAE（fail-fast，可接受）。
- **现状**: 容量公式 `capacity = loadBatchSize × multiplyFactor`（缺省 100×30=3000 条记录，队列内存上界良好）本身合理，但两端参数均无 `Guard.positive` 守卫，非法配置的失败模式是挂死或静默丢数，而非 fail-fast。
- **风险（规模判断）**: 非内存量级问题，而是「有界队列的边界配置缺陷」（判级口径 P2「有界但边界配置缺陷」）；挂死任务同时长期持有 jdbc-reader 的连接/ResultSet（见 CL-010 连带面）。
- **建议**: 在两个 dispatch provider 构造器与 `ModelBasedBatchTaskBuilderFactory.buildDispatchConfig` 加 `Guard.positiveInt(loadBatchSize)` / `Guard.positiveInt(loadBatchMultiplyFactor)`，并让 xdef 对 loadBatchSize 声明下界（如 `!int{min:1}`）。
- **失效面枚举（F3）**: 同族 = ①`PartitionDispatchLoaderProvider`（同步 dispatch，同缺陷）；②`AsyncFetchPartitionDispatchLoaderProvider`（异步 dispatch，同缺陷）；③`ModelBasedBatchTaskBuilderFactory.buildDispatchConfig`（DSL 装配点，无校验）；④`BatchDispatchConfig` setter（程序化入口，无校验）；⑤`setMaxLockQueueCountPerThread`/`setMaxLockQueueCountPerThread`（两个 provider 的同族 setter 同样无校验，≤0 时 `ret.size() >= maxLockQueueCountPerThread` 退化为永不 break，单个 takeBatch 可能搬空全部队列——有界但行为退化，登记不展开）；⑥xdef `loadBatchSize="!int=100"`（缺省值安全，仅缺下界约束）。
- **信心水平**: 确定（挂死环为静态可证的循环依赖；capacity=0 的许可语义为 `java.util.concurrent.Semaphore` 文档化行为）
- **误报排除**: 不是 C4 线程池问题（dispatch executor 是复用平台池），也不是「有界声明存在」——这里正是边界配置本身缺失守卫。
- **复核状态**: 未复核
- **复核状态**：已复核（2026-10-16 独立复核）——保留 P2，新发现；与 C8-001 同位点，登记关联、留 Phase 6/7.1 合并裁定（合并时以 C8-001 P1 为主处置项）。触发时序与 F3⑤ 两处事实精度修正不改判级，详见文末「维度复核结论」二、四节。

---

## 已验证有界面（负发现，不立案）

以下审计对象经逐行核对确认内存有界，登记判定依据，供复核轮免重复展开：

1. **orm-reader「全表加载」WARN 后续路径**（`OrmQueryBatchLoaderProvider.java:108-145`）：WARN（已声明行为）后以 `dao.findNext(lastEntity, filter, orderBy, batchSize)` keyset 分页逐批读取，每页 ≤ batchSize（`OrmEntityDao.findNext` → `orm().findPage(sql, 0, limit)`），内存按 chunk 有界；模块内未发现绕过 batchSize 的无界上游调用方（`BizExportTaskBuilder` 亦经此 provider 分页）。**结论：WARN 后继续是声明的合法用途，内存面有界，不立案**。遗留小风险：空 orderBy 下 keyset 游标依赖 `queryToFindNextSql` 的内部主键兜底序（nop-orm 职责，超出本单元）。
2. **csv file-reader**（`CsvRecordInput`）：迭代器逐行（`next()` → `it.next()`），`readBatch(batchSize)` 有界；`ResourceRecordLoaderProvider` 的 `processingItems` TreeMap 有 `maxProcessingItems=50000` + `overLimitWaitMillis` 等待 + `ERR_BATCH_TOO_MANY_PROCESSING_ITEMS` fail-fast 双重守卫（`:309-392`），是本模块背压做得最完整的面。
3. **filterProcessed in-clause 规模**：`DaoBatchRecordHistoryStore.filterProcessed:35-55` 与 `JdbcKeyDuplicateFilter.filterProcessed:48-81` 的 IN 列表均为**当前 chunk 记录集**（≤ batchSize），未发现 recordKey 列表跨 chunk 累积或无界物化；`recordMap`/`keyMap` 亦为 chunk 局部。注：batchSize 配置极大时 IN 参数数可达 batchSize（Oracle 1000 参数上限等方言约束会先于内存爆掉），属 batchSize 配置问题，不计缺陷。
4. **PartitionDispatchQueue 许可配对**：acquire（`addBatch`，data.size()）↔ release（`takeBatch:181` 按 实取数 / `removePartition:275` 按未投递数）配对完整；`acquirePermits` 在 finished 后丢弃数据不占许可（注释明确声明该设计）。队列内存上界 = capacity 条记录 + fetchThreadCount 个在途页。
5. **generator loader**（`BatchGenLoaderProvider.load:65-75`）：`for (i < batchSize)` 循环，有界（request-generator 展开面见 C5-003，为另一路径）。
6. **chunk 上下文与监听器**：`BatchTaskContextImpl` 的 12 类监听列表仅在 setup 期注册一次（`ResourceRecordLoaderProvider.newLoaderState:219-227` 有明确注释防止每 chunk 追加回调）；`newChunkContext()` 纯新建不回挂任务上下文；`BatchChunkContextImpl` 的 chunkItems/completedItems/historyItems/rowNumberMap 均为 chunk 局部。
7. **consumer 侧**：`ResourceRecordConsumerProvider.consume:170-193` 每 chunk `writeBatch + flush` 流式写文件；`OrmBatchConsumer`/`JdbcInsertBatchConsumer`/`JdbcUpdateBatchConsumer` 均 chunk 界批量写；`GenInsertSqlRecordIO` 逐行写 Writer。
8. **biz 导出链**：`DefaultBizEntityExporter:41-45` 强制 `maxConcurrency/maxBatchSize` 封顶（缺省 1/1000，`AbstractBizEntityService:18-19,40-48`），loader 走 orm-reader keyset 分页——有界且有上限封顶，是各 loader 面中守卫最完整者。
9. **状态存储**：`EtlTaskStateStore` 的 tableStores 映射以配置表数为界；`BatchTaskContextImpl.getCache()` 的 MapCache 为任务级、生命周期随任务。
10. **exp 分页/行缓冲**：`ImportDbTool` 读侧 CSV 流式 + chunk 界；`JdbcPageBatchLoaderProvider` 每页 OFFSET 分页（页内有界；OFFSET 的 O(n²) 扫描属 C9，见 CL 线索不展开）。

---

## 维度集外线索（不立案展开）

### [nop-batch-CL-007]（维度集外线索，不立案展开）
- **形态归属**: C3 异步链路正确性（候选 P1）。fetch 线程异常退出（`AsyncFetchPartitionDispatchLoaderProvider.java:133-137`）只置 `exception` 不置 `noMoreData`；消费线程已阻塞在 `PartitionDispatchQueue.takeBatch` 内时，退出条件 `count<=0 && fetchThreadCount==0 && noMoreData`（`PartitionDispatchQueue.java:190`）因 noMoreData=false 永不满足，且 exception 只在 loader 调用入口检查（`AsyncFetchPartitionDispatchLoaderProvider.java:89-91`）——任务中途 fetch 失败且消费者恰在等待时**永久挂起**而非抛出根因。一句话证据：异常线程 finally `exitFetchThread()` 后 countDown 归零，但 noMoreData 缺位使 takeBatch 的 500ms await 环无法退出。

### [nop-batch-CL-008]（维度集外线索，不立案展开）
- **形态归属**: C9 热路径（候选 P2）+ C7 日志放大。`ExportDbTool.runTask:277` 未加 `>0` 守卫直接 `builder.batchSize(config.getBatchSize())`，而 `ExportDbConfig` 缺省 batchSize=0（`_gen/_ExportDbConfig.java:31`）→ `BatchChunkProcessor.adjustBatchSize` 归一为 1 → 导出退化为每行一个 chunk：每行两次 `LOG.info`（process-chunk-begin/end）+ 一次列表分配；对照 `ImportDbTool:301-302` 有守卫（缺省落到 builder 的 100）。一句话证据：同仓两工具对同一缺省值 0 的处理不一致，导出路径产生 per-row chunk 病理。

### [nop-batch-CL-009]（维度集外线索，不立案展开）
- **形态归属**: C4 线程池治理（已知事实，候选 P2/P3）。`ExportDbTool.java:150` / `ImportDbTool.java:232` `DefaultThreadPoolExecutor.newExecutor(..., threadCount, Integer.MAX_VALUE)` 无界队列；积压面 = 配置表数（config 有界），每任务是整表导出/导入任务，实际 OOM 风险低，但无拒绝策略与上界校验。一句话证据：队列容量字面量 Integer.MAX_VALUE。

### [nop-batch-CL-010]（维度集外线索，不立案展开）
- **形态归属**: C6 资源生命周期（候选 P2）。`JdbcBatchLoaderProvider.newState:226-261` 在 setup 时即 `openConnection + executeQuery`，ResultSet/Connection 仅由 `context.onAfterComplete` 关闭——任务挂死（如 CL-007 场景或 C5-005 挂死环）时连接与游标长期占用；concurrency>1 时多线程共享单连接单 ResultSet（`synchronized(state)` 串行化读）。一句话证据：资源关闭唯一出口挂在任务完成回调上，与挂死路径复合为连接泄漏窗口。

### [nop-batch-CL-011]（维度集外线索，不立案展开）
- **形态归属**: C9 分配放大（候选 P3）。`DaoBatchRecordHistoryStore.filterProcessed:52-53` `new ArrayList<>(records); ret.removeAll(history)` 为 O(n×m)（`WithHistoryBatchConsumer:41` 已 HashSet 化优化，此处同族兄弟未同步修——F3 形态实证）；`JdbcPageBatchLoaderProvider.load:85-98` OFFSET 分页整表扫描 O(n²)。一句话证据：同族一处已优化一处未优化的 removeAll。

---

## 总计与结论

| 类别 | 数量 | 明细 |
|---|---|---|
| C5 发现 | 5 | P0=0；P1=2（C5-001 excel 全量物化、C5-002 jdbc fetchSize 死配置）；P2=3（C5-003 generator 输出无界、C5-004 provider List 常驻、C5-005 dispatch 容量无守卫）；P3=0 |
| 维度集外线索 | 5 | CL-007（C3 候选 P1）、CL-008（C9 候选 P2）、CL-009（C4 候选 P2/P3）、CL-010（C6 候选 P2）、CL-011（C9 候选 P3） |
| 已验证有界面（负发现） | 10 | 见「已验证有界面」节 |

**总体判断**: nop-batch 的 chunk 主干（load→process→consume）与 PartitionDispatchQueue 的容量/许可设计在缺省配置下是内存有界的，`ResourceRecordLoaderProvider` 的 50000 项背压 + fail-fast 是模块内最佳实践。**两个 P1 均发生在「声明的 loader 能力 + 缺省/平凡配置」组合上**（excel-reader 全量物化、jdbc-reader 非 streaming 的 fetchSize 死配置），直接对抗 owner doc「大数据量场景 + 内存友好」的包络声明；按校准口径两者都属「罕见但可具体化」与「常见条件」之间，因均需用户显式选择对应 loader 而未判 P0。C5 基线 grep 命中数为 0 与实际物化面的反差表明：本模块的 OOM 风险全部经由库调用间接发生，后续轮次的规模类审计不能依赖 readAllBytes 型 grep。

---

## 维度复核结论（独立复核子代理，2026-10-16）

> 复核人：C5 维度独立复核子代理（与初审代理相互独立，怀疑姿态逐条复核）。复核对象：本文件 C5-001~005 + CL-007~011 与 `C5-round2.md` C5-006~009 + CL-028~032，合计 9 条发现 + 10 条 CL 线索。复核允许面：两份报告、nop-batch src/main 与 test live code、历史审计对照面（`ai-dev/audits/cra-index.md`、历史审计目录、`ai-dev/plans/2461~2466-*.md`、`ai-dev/bugs/`、`ai-dev/lessons/`、owner doc `docs-for-ai/03-modules/nop-batch.md`）。
>
> **格式注记**：各条目原有 `- **复核状态**: 未复核` 字段按禁改规则保留原样，其下方追加的 `**复核状态**：…` 行为本次复核回填，以追加行为准。全部裁定基于复核时 live code（HEAD `4b7c7897de`）逐行亲证。

### 一、逐条裁定表

| 发现 ID | 裁定 | 现状 | 一句话理由 |
|---|---|---|---|
| nop-batch-C5-001 | 保留 P1（终裁） | 新发现 | 特别指令四子项全部亲证成立：物化点唯一、量级可信、maxCountExpr 无兜底、平凡配置可及 |
| nop-batch-C5-002 | 保留 P1（终裁） | 新发现 | 门条件恒 false / 变体 B 方言矩阵 / autocommit 未关三重事实亲证；显式 fetchSize 在常见 DSL 形态下静默死配置，后果 GB 级 OOM |
| nop-batch-C5-003 | 保留 P2 | 新发现 | 累积点/生成环/接线亲证；useBatchRequestGenerator 缺省 false + totalCount 用户显式配置，非平凡可及，P2 正确（P1/P2 边界情形注记） |
| nop-batch-C5-004 | 保留 P2 | 新发现 | 包装点/常驻点亲证；能力已声明但内存代价未声明、无守卫 |
| nop-batch-C5-005 | 保留 P2，与 C8-001 同位点关联登记 | 新发现 | 守卫缺失成立；两处事实精度修正（挂死面可达路径、F3⑤ 方向）不改判级，留 Phase 6/7.1 合并裁定 |
| nop-batch-C5-006 | 保留 P3 | 新发现 | xdef 无特性 / 注释引用已删 API / 先物化后截断三层全部亲证 |
| nop-batch-C5-007 | 保留 P3 | 新发现 | 无界点与 DSL 白名单守卫点亲证；平台内唯一调用点已守卫，「理论风险」标注正确 |
| nop-batch-C5-008 | 保留 P3 | 新发现 | 12 列表/persistVars/getCache 无容量守卫 + 注释自律（ResourceRecordLoaderProvider:219-220）亲证 |
| nop-batch-C5-009 | 保留 P3 | 新发现 | xdef 无范围/Builder 无 Guard/adjustBatchSize 仅 ≤0 归一 1/`<source>` 无截断四点亲证 |

### 二、特别复核指令终裁明细

**C5-001 终裁：P1 维持（四子项逐项）**
- a) **物化点唯一性：成立**。`XlsxToRecordOutput.OutputRowHandler.endRow`（nop-ooxml-xlsx `XlsxToRecordOutput.java:90-96`）逐行回调 `output.write(row)`——SAX 解析层本身流式；全链唯一累积器是 `HeaderListRecordOutput.records`（ArrayList，`HeaderListRecordOutput.java:22`/:100-104），经 `ExcelRecordInput.beforeRead:87-98` `list = collector.getResult()` 常驻；`hasNext/next`（:101-114）纯内存下标推进。第 2 轮修复面收窄结论（改 collector 为逐行下推即可保留解析层流式能力）确认。
- b) **量级估算：可信**。`CollectionHelper.toNonEmptyKeyMap`（nop-commons `CollectionHelper.java:272-284`）每行建 LinkedHashMap——20 列行开销 ≈1.1KB（map 结构 + Entry×20，key 为共享 header 引用）+ 装箱/String 值 0.5–1.1KB ≈1.6–2.2KB/行，50 万行终态 list 约 0.8–1.1GB；叠加 OOXML 解析中间结构（sharedStringsTable 常驻、逐行临时 List）后报告的 1.5–3GB 包络成立。量级判定（GB 级、缺省 1–2GB 堆确定性 OOM）无争议。
- c) **maxCountExpr 无兜底：成立**（三点独立核实）。①接线被注释（`FileBatchSupport.java:150-151`）；②注释引用的 `getMaxCountExpr()` 在 `BatchExcelReaderModel` 及其 `_gen` 基类 grep 零命中——「恢复接线」需 xdef 加特性 + codegen 重新生成 + 恢复接线三层联动，非取消注释即可；③即便全部恢复，`newLoaderState` 中 `input.beforeRead`（`ResourceRecordLoaderProvider.java:193`）先于 maxCount 求值与 `limit()` 包装（:208-212）——limit 仅截断处理行数，物化峰值不变。
- d) **触发平凡性：成立**。excel-reader 为 DSL 声明 loader（batch.xdef:113-114）；owner doc `nop-batch.md:232` 明文「大批量数据导入（CSV/Excel → 数据库）」——大数据量 Excel 属声明包络内用法。不升 P0 的理由维持：需显式选择 excel-reader（file-reader 缺省 CSV），非默认装配；后果为任务 OOM 失败，可见、可重试、无数据不可逆。**终裁 P1**。
- 现状补强证据：`ExcelRecordInput` 引入提交 bcd7ec4c40 的提交信息自述「目前的做法是先将所有数据都读取到内存中，然后再逐个返回」——全量物化是有意识的初始实现选择，此后（含 !105 优化提交 6f6d1f999a）未改。

**C5-002 终裁：P1 维持**
- **门条件事实：成立**。`JdbcBatchLoaderProvider.newState:243-248` 的 `sql.getFetchSize() != -1 && fetchSize != null` 在 `<query>` 路径恒 false（`DaoQueryHelper.queryToSelectFieldsSql` 全文无 fetchSize 写入，grep 实证）；`<sql>` 文本路径的 SQL 对象正常不携带 fetchSize。sql 层 fetchSize 由 `JdbcHelper.setFetchSize`（JdbcHelper.java:228-231，`>0 || ==MIN_VALUE` 门）在 `prepareStatement` 内应用——provider 层字段是唯一兜底，被 && 门静默丢弃。DSL 接线（`JdbcBatchSupport.java:27-28`）与 xdef `streaming="!boolean=false"`（缺省 false）亲证。
- **变体 B：成立**（计数修正）。实测唯一 `.dialect.xml` 文件名 **20 个**（含 geo-support/postgis/window-expr-support 等 test/support 方言；原报告「19 个」修正为 20），**仅 `mysql.dialect.xml` 定义 `streamingFetchSize`**（mysql5.7 亦无；mariadb 未定义——MySQL 兼容方言同样不可达流式，加重变体 B）。autocommit：`jdbcTemplate.openConnection → TransactionTemplateImpl:52 → DefaultTransactionManager:209 → transactionFactory.openConnection` 全链无 setAutoCommit（全仓 `setAutoCommit(false)` 仅 `JdbcBatcher:160` 与 nop-stream 2PC sink:676）——非 MySQL 流式三重不可达成立。
- **后果审度（指令问点）**：「死配置」定性准确——streaming=true 下 MySQL 可达流式，缺陷面是 **DSL 显式配置无效**（无 WARN、无报错）而非能力缺失；但用户显式配置 fetchSize（大表读取的自然防御动作）被静默吞掉 + 缺省 streaming=false 下 MySQL/PG 驱动按文档化缺省行为全量缓冲，构成「声明包络内 + 平凡配置」的确定性 GB 级 OOM，判级逻辑与 C5-001 同构。不升 P0：需显式选择 jdbc-reader；OOM 可见可重试。**终裁 P1**。

**C5-005 处置裁定：维持独立条目 + 同位点关联登记，留 Phase 6/7.1 合并裁定**
- 同位点实证：与 C8-001 为同一缺陷站点（`PartitionDispatchQueue` 容量参数无 `>0` 守卫 + `buildDispatchConfig:356-381` 无校验 + 两个 provider 构造传参，本轮全部亲证）。C8 轮已立案 C8-001（P1，含 loadBatchSize≤0「LIMIT 0 静默丢数/挂死」双面）。
- 处置：**维持独立条目、登记与 C8-001 关联、留合并裁定**——本条目独有内容（multiplyFactor 维度、负值 IAE 语义评估、maxLockQueueCountPerThread 退化登记、xdef 下界建议）对合并修复有增量价值；合并时以 C8-001（P1）为主处置项，本条守卫建议并入。本维度口径内判级维持 P2，合并时服从 C8-001 终裁。

### 三、维度集外线索处置（确认/驳回二态）

| 线索 | 处置 | 建议归属维度/严重度 | 一句话依据 |
|---|---|---|---|
| nop-batch-CL-007 | **驳回（确认 C8 中期终判）** | — | 独立核实 live 已闭合：takeBatch 返回 null 后 exception 二次检查重抛（`AsyncFetchPartitionDispatchLoaderProvider.java:95-99`）+ fetch 异常记根因（:133-138）+ finally `exitFetchThread`（:141）→ 终止条件（`PartitionDispatchQueue.java:190`）可达，「消费者永久挂起」前提不成立。修复来自本 campaign 之前的历史轮次 commit `7defae0e69`（2026-08-24，check-findings 轮「takeBatch二次检查exception再抛」，经 `git merge-base --is-ancestor` 证实为 CRA 绿色基线 b3a9c76bed 的祖先），残余 drain-then-fail 延迟有界，C8 轮已定性可接受 |
| nop-batch-CL-008 | 确认（沿用深化立案为 C8-002，P1） | C8 | `ExportDbTool:277` 无条件 `batchSize(config.getBatchSize())`、`_ExportDbConfig` 缺省 0（:31）、`ImportDbTool:301-302` 有 `>0` 守卫——不对称亲证 |
| nop-batch-CL-009 | 确认（沿用维持低危不立案） | C4 | `Integer.MAX_VALUE` 队列（`ExportDbTool:150`/`ImportDbTool:232`）亲证；积压面=表数（配置枚举有界）、executor finally destroy，低危维持 |
| nop-batch-CL-010 | 确认 | C6 / P2 | setup 即 openConnection+executeQuery（`JdbcBatchLoaderProvider:226-262`）、唯一关闭出口挂 `onAfterComplete`（:184-186）亲证；与挂死路径复合的连接/游标占用窗口成立 |
| nop-batch-CL-011 | 确认 | C9 / P3 | `DaoBatchRecordHistoryStore` `ret.removeAll(history)`（:52-53，ArrayList O(n×m)）+ `WithHistoryBatchConsumer` 已 HashSet 化（注释为修复痕迹）+ `JdbcPageBatchLoaderProvider` OFFSET 分页（:85-98）亲证 |
| nop-batch-CL-028 | 确认 | C5 邻接 / P3 | `_ExportDbConfig` fetchSize 缺省 null（:80）/streaming 缺省 true（:108）/仅 mysql 方言有 streamingFetchSize——非 MySQL 无任何 setFetchSize 兜底亲证 |
| nop-batch-CL-029 | 确认 | C7 / P3 | `JdbcKeyDuplicateFilter:62/:76` `StringHelper.join(list, ",")` 无截断亲证 |
| nop-batch-CL-030 | 确认 | C9 / P3 | `:64/:78` `keyMap.keySet().removeAll(list)`（ArrayList）O(n×m) 亲证；CL-011 同族第三处 |
| nop-batch-CL-031 | 确认 | C2/C8 候选 | 工厂 `:308` 白名单返回 null → `SplitBatchConsumer.consume:53-56` 静默跳过亲证；归属 C2/C8 裁定 |
| nop-batch-CL-032 | 确认 | C9 / P3 | `EtlTaskStateStore.saveTableState:108-121` → `saveStateFile:129-144` 每 chunk 全量 JSON 序列化（pretty=true）+ 临时文件 rename 亲证 |

### 四、精度修正与 F3 补全（均不改判级）

1. **C5-005 触发时序 T2 精化**：标准内建 loader 对 batchSize=0 均返回空页（`BatchGenLoaderProvider.load` for 循环、`ListBatchLoader` `Math.min(size-offset, 0)`、`IRecordInput.readBatch` 逐条循环 ≤maxCount）→ DSL `loadBatchSize="0"` 的实际表现是「空页 → 静默空任务」（即 C8-001 的 LIMIT 0 面），而非 Semaphore(0) 挂死。挂死面在 DSL 可达配置下需「`loadBatchSize="0"` + 自定义 `IBatchLoader` bean 不尊重 batchSize」合取；程序化路径 `setLoadBatchMultiplyFactor(0)` 可直达挂死，但 **xdef dispatcher 根本未声明 loadBatchMultiplyFactor 特性**（batch.xdef:81-84，仅有 fetchThreadCount/loadBatchSize/executor/partitionIndexField/partitionFn），DSL 无入口（与 C8 r2「幽灵旋钮」修正口径一致）。守卫缺失 + 失败模式非 fail-fast 的立案点不受影响，判级不变。
2. **C5-005 F3⑤ 方向修正**：`maxLockQueueCountPerThread≤0` 时 `ret.size() >= maxLockQueueCountPerThread` **恒真**（排空第一个非空队列后 ret.size()≥1 > 0）→ 每个 takeBatch 处理第一个非空队列后即 break（≈值取 1 的行为），原登记「退化为永不 break、单个 takeBatch 搬空全部队列」方向相反。该子项本为「登记不展开」，修正不影响任何判级。
3. **C5-002 方言计数修正**：19 → 20 个唯一 `.dialect.xml` 文件名（含 test/support 方言）；「仅 mysql.dialect.xml 定义 streamingFetchSize」经全量 grep 维持，方向结论不变。
4. **C5-001 F3 维度集外兄弟补全**：nop-ooxml-xlsx `ExcelHelper.readAllSheets/readSheet`（`ExcelHelper.java:74-107`）复用同一 `HeaderListRecordOutput` 全量收集模式——但其 API 契约即返回 List（调用方明知全量），在审计单元外且非批处理接线，登记为同族观察项，不改判级。
5. **F3 完备性总判定**：9 条发现 F3 节均非空，本轮独立 grep 验证闭合（C5-002 的 sqlName 生效路径与 ExportDbTool 路径、C5-003 的四汇点穷举、C5-006 的 file-reader 对照接线 `FileBatchSupport:51-52`、C5-009 的 maxProcessingItems 三重守卫对照均复核无误），除上述第 4 点外无补全项。

### 五、现状标注依据

- **新发现（9/9 条发现）**：`git log b3a9c76bed..HEAD -- nop-batch/` 为空——CRA campaign（含全部维度审计轮）对本单元零代码变更；WI 1.1–1.6（plans 2461~2466）审计对象为 stream/job/wf/task/retry/tcc，grep 证实六个 plan 全文零 `nop-batch` 交集——同族不同模块仍标新发现（同族关联：cra-index WI 1.1 FAM-1 materialization store 无界、lessons/01 批量内存累积）。
- **CL-007 已闭合（非本轮产物）**：修复 commit `7defae0e69`（2026-08-24）早于 CRA 绿色基线且为其祖先。
- 历史对照无「已知未修复」项：C8-002（ExportDbTool batchSize=0）系本轮 campaign C8 维度并行立案（同目录），CL-008 处置沿用其结论；本分支 nop-batch 近期 commit（fa6f992356/604ef29f5f 等）为更早 code-review 轮的记录面修复，与本维度发现零交集。

### 六、复核统计

| 项 | 数量 | 明细 |
|---|---|---|
| 发现裁定 | 9 | 保留 9（P1×2：C5-001/002 终裁维持；P2×3：C5-003/004/005；P3×4：C5-006/007/008/009）；降级 0；驳回 0 |
| CL 线索 | 10 | 确认 9；驳回 1（CL-007，确认 C8 中期终判）；其中 CL-008 归并 C8-002、CL-009 沿用不立案（独立行动项净减 2） |
| 现状标注 | 9 发现 + 10 CL | 发现：新发现 9（已知未修复 0 / 已修复 0）；CL：新发现确认 8、已闭合 1（CL-007）、沿用归并 2（CL-008/009） |
| 证据复核 | 9+10 | 全部 file:line 亲自打开核对，行号零漂移；精度修正 4 处（C5-005 两处、C5-002 计数、C5-001 现状补强），均不改判级 |
| F3 失效面 | 9 | 均非空且独立验证闭合；维度集外兄弟补全 1 处（C5-001 ExcelHelper，不改判级） |

---

## 最终保留项

| ID | 标题 | 最终严重度 | 现状 |
|---|---|---|---|
| nop-batch-C5-001 | excel-reader 全量物化：ExcelRecordInput.beforeRead 一次性将整个数据表物化为内存 List，违背「Chunk 内存友好」声明包络 | P1 | 新发现 |
| nop-batch-C5-002 | jdbc-reader DSL fetchSize 配置死配置化：&& 门条件使 provider 层 fetchSize 永不生效，非 MySQL 方言流式三重不可达，驱动缺省全量缓冲 | P1 | 新发现 |
| nop-batch-C5-003 | useBatchRequestGenerator 路径 chunk 输出集无上界：while(true) 生成环绕过 batchSize 包络 | P2 | 新发现 |
| nop-batch-C5-004 | provider loader 返回 List 全量常驻任务全程，无上限守卫亦无包络声明 | P2 | 新发现 |
| nop-batch-C5-005 | dispatch 容量参数无守卫（与 C8-001 同位点关联，合并裁定归属 C8-001） | P2 | 新发现 |
| nop-batch-C5-006 | excel-reader maxCountExpr 能力整体缺位：xdef 无特性 + 接线死代码 + 恢复亦不构成内存兜底 | P3 | 新发现 |
| nop-batch-C5-007 | SplitBatchConsumer activeConsumers 任务级只增不减，程序化使用路径无界（理论风险） | P3 | 新发现 |
| nop-batch-C5-008 | BatchTaskContextImpl 三类任务级容器注册/写入 API 无容量守卫（理论风险） | P3 | 新发现 |
| nop-batch-C5-009 | batchSize 全链路无上界强制：xdef 无范围、Builder 无 Guard、仅 ≤0 归一 1 | P3 | 新发现 |

### 维度集外线索（确认）

| ID | 形态归属 | 严重度 | 处置注记 |
|---|---|---|---|
| nop-batch-CL-008 | C8 | P1 | 并入 nop-batch-C8-002（C8 轮深化立案），不另立 |
| nop-batch-CL-010 | C6 | P2 | 独立行动项 |
| nop-batch-CL-011 | C9 | P3 | 独立行动项（与 CL-030 同族合并评估） |
| nop-batch-CL-028 | C5 邻接 | P3 | 独立行动项（与 C5-002 修复同批） |
| nop-batch-CL-029 | C7 | P3 | 独立行动项 |
| nop-batch-CL-030 | C9 | P3 | 与 CL-011 同族合并口径 |
| nop-batch-CL-031 | C2/C8 | P2/P3 | 独立行动项（归属维度由协调方裁定） |
| nop-batch-CL-032 | C9 | P3 | 独立行动项 |
| （nop-batch-CL-007） | — | — | **驳回**：live 已闭合（历史轮次 commit 7defae0e69），确认 C8 中期终判，不入台账 |
| （nop-batch-CL-009） | C4 | 低危 | 沿用 C8 终判维持不立案 |
