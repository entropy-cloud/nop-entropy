# nop-autotest 实现代码检查报告

- 检查日期: 2026-08-21
- 模块路径: nop-autotest
- 文件数: 41（src/main/java；nop-autotest-core 34 + nop-autotest-junit 7；nop-autotest-dbtool 的 src/main/java 为空，仅含测试代码）
- 覆盖范围声明: 41/41 全部逐文件深读（core 全部 34 个 + junit 全部 7 个）。对候选问题交叉验证了外部依赖语义：`OrmEntityModel.getColumnByCode(code, ignoreUnknown)`（false=抛异常、true=返回 null，位于 nop-persistence/nop-orm-model）、`CsvRecordOutput` 按 headers 逐列取值的写入行为（nop-kernel/nop-core）、`TestCaseJsonDataSplitter`/`outputHex`/`AutoTestWrapException`/`MockScheduledExecutor`/`setUseGlobalVars` 在全仓库的调用方。D2（资源泄漏）未发现真实问题：直接 IO 均走 `FileHelper`/`CsvHelper`/`IoHelper.safeCloseObject`，无裸流。D6 未发现值得报告的问题（全量序列化对比是测试框架的固有代价，无热点放大）。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 3 |
| P2 | 6 |
| P3 | 5 |

## 发现列表

### [P1] variant 表数据文件路径两套约定并存，导致变体输出表永不被校验（假绿）、仅变体独有的输入表被静默忽略

- **文件**: `nop-autotest/nop-autotest-core/src/main/java/io/nop/autotest/core/data/AutoTestCaseData.java:240-256`
- **维度**: D1 / D8
- **证据**:
```java
// 单文件访问器：variants 布局
public String getInputFileName(String fileName, String variant) {
    ...
    String path = "variants/" + variant + "/input/" + fileName;   // L196
public String getOutputFileName(String fileName, String variant) {
    ...
    return "variants/" + variant + "/output/" + fileName;          // L205
public List<Map<String, Object>> readInputTableData(...) {
    List<Map<String, Object>> ext = readTableData("variants/" + variant + "/input/tables/" + tableName + ".csv");  // L332

// 列表访问器：另一套布局，且 output 竟从 input 目录取
public List<File> getInputTableFiles(String variant) {
    ...
    List<File> varFiles = getFiles("input/" + variant + "/tables", ".csv");   // L245
public List<File> getOutputTableFiles(String variant) {
    ...
    List<File> varFiles = getFiles("input/" + variant + "/tables", ".csv");   // L254（注意：还是 input/）
public List<File> getInputSqlFiles(String variant)  -> getFiles("input/" + variant, ".sql")   // L271
public List<File> getInitSqlFiles(String variant)   -> getFiles("init/" + variant, ".sql")    // L280
```
- **现状**: 同一个类里对 variant 数据目录存在两套互斥约定：`getInputFileName`/`getOutputFileName`/`readInputTableData` 使用 `variants/<v>/input|output/...`；而供批量扫描使用的 `getInputTableFiles`/`getOutputTableFiles`/`getInputSqlFiles`/`getInitSqlFiles` 使用 `input/<v>/...`、`init/<v>/...`。录制与校验走的是不同约定：`AutoTestCaseDataSaver.saveOutputTable` 经 `getOutputTableFile(tableName, variant)` 把变体输出表写到 `variants/<v>/output/tables/<t>.csv`；而回放时 `AutoTestCaseResultChecker.checkOutputTables` 经 `getOutputTableFiles(variant)` 只扫 `output/tables/` + `input/<v>/tables/`。
- **风险**: 非 default variant 下，(1) 录制产生的 `variants/<v>/output/tables/*.csv` 在校验阶段**永远不会被扫描**——该表的数据库终态完全不校验，错误测试照样通过（假绿）；(2) 若 default 目录恰好存在同名 `output/tables/<t>.csv`，变体回放会拿 default 的期望数据校验变体行为（错比）；(3) 仅在 `variants/<v>/input/tables/` 提供的表（基础 input/tables 无同名文件）不会出现在 `getInputTableFiles(variant)` 列表中，建表与插入被静默跳过（漏初始化）；(4) `getOutputTableFiles(variant)` 从 `input/<v>/tables` 取"输出"文件，自身语义即自相矛盾。variants 是框架文档化特性（`@EnableVariants` + `VariantsArgumentProvider`），路径现实可达。
- **建议**: 统一 variant 布局为 `variants/<v>/...`：`getInputTableFiles`/`getOutputTableFiles`/`getInputSqlFiles`/`getInitSqlFiles` 的 varFiles 路径改为 `variants/<v>/input/tables`、`variants/<v>/output/tables` 等，并补一条 variant 表数据端到端（录制→回放校验）回归测试。
- **误报排除**: 已读全部 4 个调用方（`AutoTestCaseDataBaseInitializer.createTables/loadInputData`、`AutoTestCaseResultChecker.checkOutputTables`、`MigrationOperation.forTable`），确认单文件与列表访问器路径确实不同且同时被主流程使用；非 default variant 时 `isDefaultVariant` 短路不生效，问题路径可达。

### [P1] 变体录制模式误删 default 的 input/tables 基础数据文件

- **文件**: `nop-autotest/nop-autotest-core/src/main/java/io/nop/autotest/core/execute/AutoTestCaseDataSaver.java:61-69, 82-97`
- **维度**: D1
- **证据**:
```java
if (!loadedRows.isEmpty() || changedRows.isEmpty()) {
    saveInputTable(entityModel, loadedRows);
} else if (ormHook.getLoadedTables().contains(entityModel.getName())) {
    saveInputTable(entityModel, Collections.emptyList());
} else {
    removeInputTable(entityModel);            // 无 variant 保护
}
...
private void saveInputTable(IEntityModel entityModel, List<EntityRow> rows) {
    // variant下的input数据目前需要手工编写，不会自动生成
    if (AutoTestDataHelper.isDefaultVariant(variant)) {   // 写入有保护
        ...
}
private void removeInputTable(IEntityModel entityModel) {
    File file = caseData.getInputTableFile(entityModel.getTableName(), null);  // null => input/tables/<t>.csv（default 目录）
    if(file.delete()){ ... }
}
```
- **现状**: 录制非 default variant 时，`saveInputTable` 明确跳过（注释说明 variant 下 input 需手工维护），但 `removeInputTable` 没有对应的 variant 保护，且固定传 `null` variant，指向 default 目录 `input/tables/<t>.csv`。当某实体在本次执行中只有 insert 变更（`postSave`）而无 `postLoad`（`loadedRows` 为空、`changedRows` 非空、表不在 `loadedTables` 中）时，走到 `removeInputTable` 分支。
- **风险**: 一次变体录制（`@EnableSnapshot(saveOutput=true)` + variant 运行）会静默删除 default 用例（以及其他变体）依赖的 `input/tables/<t>.csv`，下次回放时初始化数据缺失，测试行为漂移甚至假红，且破坏用户手工维护的数据文件。
- **建议**: 在 `removeInputTable` 前加与 `saveInputTable` 相同的 `isDefaultVariant(variant)` 判断（非 default 变体录制一律不删 default input 文件）。
- **误报排除**: 已核对 `AutoTestOrmHook`：`onRead` 才加入 `loadedTables`，`onSave` 不加；`EntityRow.isLoadData` 依赖 `postLoad`。insert-only 场景（业务上最常见的新增记录用例）确定落入删除分支。

### [P1] TestCaseJsonDataSplitter 表数据写入路径颠倒（`tables/input/`、`tables/output/`），产出物永远无法被框架加载

- **文件**: `nop-autotest/nop-autotest-core/src/main/java/io/nop/autotest/core/split/TestCaseJsonDataSplitter.java:128, 143, 186, 236`
- **维度**: D1
- **证据**:
```java
private File getInputFile(File dir, String name) {
    File inputDir = new File(dir, "input");       // dir/input/<name>
    ...
// 调用方：
File entityFile = getInputFile(new File(dir, "tables"), entityModel.getTableName() + ".csv");
// 实际结果: <dir>/tables/input/<table>.csv     期望: <dir>/input/tables/<table>.csv
File entityFile = getOutputFile(new File(dir, "tables"), entityModel.getTableName() + ".csv");
// 实际结果: <dir>/tables/output/<table>.csv    期望: <dir>/output/tables/<table>.csv
```
- **现状**: `savaInputEntity`/`savaOutputEntity`/`saveInputTableData`/`saveOutputTableData` 四处都把 `"tables"` 当作目录参数传给 `getInputFile/getOutputFile`，而这两个方法会在参数目录下再拼 `input/`/`output/` 前缀。生成结果落在 `<dir>/tables/input|output/<t>.csv`，与 `AutoTestCaseData` javadoc 及 `getInputTableFile`/`getOutputTableFile` 约定的 `<dir>/input|output/tables/<t>.csv` 完全错位。
- **风险**: 使用该公共工具拆分出的用例数据，`AutoTestCaseDataBaseInitializer`（扫描 `input/tables/*.csv`）与 `AutoTestCaseResultChecker`（扫描 `output/tables/*.csv`）都找不到，表初始化静默缺失，录制数据"消失"。非 entities/tables 的普通 json 文件路径（`getInputFile(dir, name)` 正确用法）不受影响，使问题更隐蔽——用户会看到 json 快照存在而表数据全部丢失。
- **建议**: 改为 `caseData` 同款路径拼接：`new File(new File(dir, "input"), "tables/" + tableName + ".csv")`；并为该类补一条"拆分产物可被 initializer 加载"的测试。
- **误报排除**: 已核对仓库内唯一调用方（`nop-sys/nop-sys-service/src/test/java/io/nop/sys/service/TestCaseDataSplitter.java`，写到 target/test 的一次性转换）不依赖 `tables/input` 布局；`AutoTestCaseData.getFiles("input/tables", ...)` 只列一层目录，不会读到 `tables/input`。路径错位确认。

### [P2] createTable 吞掉非语法错误的建表异常，掩盖真实根因

- **文件**: `nop-autotest/nop-autotest-core/src/main/java/io/nop/autotest/core/execute/AutoTestCaseDataBaseInitializer.java:111-126`
- **维度**: D4
- **证据**:
```java
try {
    jdbcTemplate.executeUpdate(new SQL(sql));
} catch (NopException e) {
    if (jdbcTemplate.existsTable(null, entityModel.getTableName()))
        return;

    if (e.getErrorCode().equals(DaoErrors.ERR_SQL_BAD_SQL_GRAMMAR.getErrorCode())) {
        LOG.debug("nop.create-table-fail:{}", entityModel.getTableName(), e);
        return;
    }
    LOG.error("nop.create-table-fail:{}", entityModel.getTableName(), e);   // 吞掉，不 rethrow
}
```
- **现状**: 建表失败时（表不存在且非 BAD_SQL_GRAMMAR，例如连接失败、权限、方言不支持），仅记录 error 日志后方法正常返回，异常被吞。
- **风险**: 后续 `loadInputData` 插入该表必然失败，用户看到的是插入报错而非建表报错，根因被掩盖，排查成本高；日志与异常分离也不满足平台"异常带上下文传播"的两档策略精神。
- **建议**: 最后一个分支应 `throw e.param(ARG_TABLE_NAME, ...)`（保留 existsTable/BAD_GRAMMAR 幂等跳过分支）；同时 `e.getErrorCode()` 可能为 null，用 `DaoErrors.ERR_SQL_BAD_SQL_GRAMMAR.getErrorCode().equals(e.getErrorCode())` 顺序可避免 NPE 掩盖原始异常。
- **误报排除**: 已确认 catch 内三个分支控制流：最后一支无 return、无 throw，方法吞异常结束。

### [P2] outputHex 双重拼接 output 前缀，读写落到 output/output/ 路径

- **文件**: `nop-autotest/nop-autotest-core/src/main/java/io/nop/autotest/core/AutoTestCase.java:391-393`
- **维度**: D1
- **证据**:
```java
public void outputHex(String fileName, ByteString bytes) {
    outputText(caseData.getOutputFileName(fileName, variant), StringHelper.bytesToHex(bytes.toByteArray()));
}
// 而 outputText 内部：
public void outputText(String fileName, String text) {
    String path = caseData.getOutputFileName(fileName, variant);   // 再次拼接
```
- **现状**: `outputHex` 先把 `fileName` 转成 `output/<f>`（或 `variants/<v>/output/<f>`）再传给 `outputText`，后者内部又做一次同样转换。对比同文件 `outputBytes`（只转换一次，正确）与 `inputHex`（读 `input/<f>`，正确）。
- **风险**: 录制模式写入 `output/output/<f>`（或 `variants/<v>/output/variants/<v>/output/<f>`），回放模式从同错误路径读取并抛 `unknown-file`，该公共 API 一旦被使用即坏。当前仓库内无调用方，故降为 P2。
- **建议**: 与 `outputBytes` 对齐：`outputText(fileName, hex)` 直接传原始 `fileName`，或拆一个内部方法接收已转换路径。
- **误报排除**: 已核对 `outputText`/`getOutputFileName` 源码，双重拼接确定；无调用方使其未在现场暴露。

### [P2] AutoTestVars 全局静态状态 + 每用例 clear()，并行测试下 mock 变量互相污染

- **文件**: `nop-autotest/nop-autotest-core/src/main/java/io/nop/autotest/core/data/AutoTestVars.java:34-36, 159-163`
- **维度**: D3
- **证据**:
```java
private static final ThreadLocal<VarsMap> t_vars = new ThreadLocal<>();
private static boolean useGlobalVars = true;
private static final VarsMap g_vars = new VarsMap();

public static void clear() {
    t_vars.remove();
    g_vars.clear();          // 清空全 JVM 共享变量
}
```
- **现状**: `useGlobalVars` 默认 true，所有测试线程共享 `g_vars`（方法级 synchronized 只保证单操作原子）。`AutoTestCase.initDao()` 用 `AutoTestVars.clear()+setVars()` 重置、`complete()` 在 finally 中再 `clear()`。JUnit 并行执行（same-thread 并发或 `junit.jupiter.execution.parallel.enabled=true`）时，用例 A 结束的 `clear()` 会清掉正在运行的用例 B 的变量。
- **风险**: `replaceValueByVarName`/`getNameByValue` 找不到变量导致录制快照写入具体随机值（录制数据损坏）、回放时 `@var:name` 解析为 null（假红），跨用例变量值串扰；失败表现随机、难归因。设计上全局共享是为了业务异步线程能记录变量，但缺少"测试作用域"边界。
- **建议**: 为 `g_vars` 增加测试作用域标识（如 owner test token），`clear()` 只清当前用例登记的变量；或文档明确要求 autotest 禁止并行执行，并在检测到并行时给出明确报错。
- **误报排除**: 已确认 `useGlobalVars` 在仓库内无置 false 的调用方（恒为 true 走全局路径）；`complete()` 的 finally `clear()` 与并行执行的交叠路径成立。

### [P2] VarsMap.setVar 与 addVar 的值规范化不一致，setVar 存入的复杂类型永远无法被变量匹配命中

- **文件**: `nop-autotest/nop-autotest-core/src/main/java/io/nop/autotest/core/data/AutoTestVars.java:53-60, 128-134, 84-99`
- **维度**: D1
- **证据**:
```java
public synchronized void setVar(String name, Object value) {
    if (!(value instanceof String)) {
        value = JsonTool.serialize(value, false);   // 存为紧凑 JSON 字符串
    }
    vars.put(name, value);
}
...
public static Object normalizeValue(Object value) {   // 查找时用 beanToJsonObject
    ...
    return JsonTool.beanToJsonObject(value, false);
}
public synchronized String getNameByValue(Object value) {
    ...
    if (varValueEquals(entry.getValue(), value))    // String vs Map/Number 恒 false
```
- **现状**: `addVar`（经 `normalizeValue` 存 `beanToJsonObject` 结果）与 `getNameByValue`（查找侧同样 `normalizeValue`）格式一致；但 `setVar` 把非 String 值序列化为 JSON 字符串存储。回放时 `init_vars.json5` 经 `setVars`→`setVar` 落入此路径。输出侧出现同值对象时，`normalizeValue(v)` 生成 Map，与存储的 String 用 `Objects.equals` 比较恒为 false。
- **风险**: `replaceValueByVarName` 对 `setVar` 设置的复杂值（Map/bean，包括数字也会被序列化成 `"123"` 字符串）永远替换失败，录制快照落入具体值而非 `@var:` 引用；`resolveVarName` 回读时也返回 JSON 字符串而非对象，类型语义漂移。直接后果是录制数据不可稳定回放（假红）与变量机制静默失效。
- **建议**: `setVar` 复用 `normalizeValue` 统一存储格式；或对 String 存储值在 `varValueEquals` 中做 JSON 等价比较。
- **误报排除**: 已追踪 `AutoTestCase.setVar`→`AutoTestVars.setVar` 调用链与 `getInitVars()`→`setVars()` 初始化链，两条路径都经过 `setVar`；`varValueEquals` 仅 `Objects.equals` 无类型转换。

### [P2] MockScheduledExecutor 忽略 TimeUnit 参数，数值一律按毫秒处理，混合单位时触发顺序错乱

- **文件**: `nop-autotest/nop-autotest-core/src/main/java/io/nop/autotest/core/mock/MockScheduledExecutor.java:25-33, 36-45` 及 `mock/MockTask.java:27-29`
- **维度**: D1
- **证据**:
```java
public synchronized <V> CompletableFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
    CompletableFuture<V> promise = new CompletableFuture<>();
    MockTask task = new MockTask(callable, delay, 0, MockTaskKind.ONCE, promise);  // unit 被丢弃
    ...
// MockTask：
public long getDelay(TimeUnit unit) {
    return unit.convert(initialDelay, TimeUnit.MILLISECONDS);   // 内部固定毫秒语义
```
- **现状**: `schedule/scheduleAtFixedRate/scheduleWithFixedDelay` 接口的 `unit` 参数被完全忽略，`delay` 数值直接当作毫秒进入 `MockTask`。例如 `schedule(task, 5, TimeUnit.SECONDS)` 被当作 5ms 排序，而 `schedule(other, 500, MILLISECONDS)` 排在其后。
- **风险**: 同一 mock executor 上混合使用不同 TimeUnit 的任务时，`triggerNext()` 的消费顺序（`DelayQueue` 按 delay 排序）与真实调度顺序不一致，依赖顺序的回放测试结果错误。仓库内当前无装配点（供外部项目使用），故 P2。
- **建议**: 构造 MockTask 前统一 `unit.toMillis(delay)`。
- **误报排除**: 三处 schedule 方法均无换算逻辑；`MockTask.getDelay` 固定 MILLISECONDS 源已核对。

### [P2] @EnableVariants 显式声明的 variant 若数据目录不存在则被静默过滤，变体测试不执行且无任何提示

- **文件**: `nop-autotest/nop-autotest-junit/src/main/java/io/nop/autotest/junit/VariantsArgumentProvider.java:47-64`
- **维度**: D8
- **证据**:
```java
List<String> ret = new ArrayList<>();
ret.add("_default");
variantsDir.mkdirs();
if (variantsDir.exists()) {
    String[] names = variantsDir.list();
    ...
}
if (variants.length != 0) {
    return ret.stream().filter(a -> ArrayHelper.indexOf(variants, a) >= 0).map(Arguments::of);
}
```
- **现状**: 候选集只由磁盘 `variants/` 目录内容决定；`@EnableVariants("vip")` 而 `variants/vip/` 未创建（或拼写错误、数据文件漏提交）时，过滤后只剩 `_default`，无警告、无失败。
- **风险**: 用户显式要求运行的变体被静默跳过，测试套件"全绿"，覆盖面实际缺失——与注解 javadoc"可以指定只运行某个Variant"的契约不符，属于静默缩小测试范围。
- **建议**: 当 `variants.length != 0` 时检查每个声明值是否存在于磁盘目录，缺失时抛出明确异常或至少 LOG.warn。
- **误报排除**: 已核对 `EnableVariants` 注解定义与 `JunitAutoTestCase.initVariant` 的 displayName 解析，`_default` 由 filter 前固定添加，声明值缺失时确实只剩 default 参数化实例。

### [P3] 多处直接抛 bare JDK 异常，违背平台错误处理两档策略

- **文件**: `nop-autotest/nop-autotest-core/src/main/java/io/nop/autotest/core/migration/operations/RenameTableMigration.java:29`；`nop-autotest/nop-autotest-core/src/main/java/io/nop/autotest/core/split/TestCaseJsonDataSplitter.java:123,138,179,229`；`nop-autotest/nop-autotest-junit/src/main/java/io/nop/autotest/junit/JunitAutoTestCase.java:85,138`
- **维度**: D4 / D7
- **证据**:
```java
throw new IllegalStateException("nop.rename-file-fail:" + file);            // RenameTableMigration
throw new IllegalArgumentException("nop.err.autotest.invalid-dao:" + name); // TestCaseJsonDataSplitter
throw new IllegalArgumentException("Classes inheriting from JunitAutoTestCase must be annotated with @NopTestConfig."); // JunitAutoTestCase
```
- **现状**: 消息里伪造了错误码样式（`nop.err.autotest.invalid-dao`）却未走 `ErrorCode`/`NopException`/模块异常类体系。
- **风险**: 错误码无法被统一 i18n/归因，与 `AutoTestErrors` 既有定义脱节（如 `ERR_AUTOTEST_NO_DAO_FOR_TABLE` 已覆盖同类语义却未复用）。
- **建议**: 改用 `AutoTestException(ErrorCode)` 并复用/补充 `AutoTestErrors` 定义。
- **误报排除**: 直接源码证据，无需排除。

### [P3] TransformTableMigration.deleteCol 在行 Map 中残留 null key 与旧列名

- **文件**: `nop-autotest/nop-autotest-core/src/main/java/io/nop/autotest/core/migration/operations/TransformTableMigration.java:59-61, 133-143`
- **维度**: D1
- **证据**:
```java
public TableMigrationConfig deleteCol(String col) {
    return renameCol(col, null);            // value 为 null
}
private void transformHeaders(Map<String, Object> row) {
    for (Map.Entry<String, String> entry : renameCols.entrySet()) {
        ...
        if (row.containsKey(name)) {
            Object value = row.get(name);
            row.put(entry.getValue(), value);   // deleteCol 时执行 row.put(null, value)，且旧 key 未 remove
        }
    }
}
```
- **现状**: `deleteCol` 复用 rename 逻辑，行级转换时把值挂到 `null` key 下且不删除旧 key。由于 `CsvHelper.writeCsv` 按 headers 逐列取值（已核对 `CsvRecordOutput`），最终 CSV 输出不受污染，但用户传入的 `transformRow` 消费器会看到旧列与 `null` key 的脏数据。
- **风险**: 迁移脚本中 `transformRow` 遍历 entries 时行为异常（null key 触发 NPE 或被误处理）；API 契约脏。
- **建议**: `transformHeaders` 在 deleteCol（newCol 为 null）分支改为 `row.remove(name)`。
- **误报排除**: 已核对 `CsvRecordOutput.write`（按 headers 取 `BeanTool.getComplexProperty`）确认落盘无污染，风险仅限行转换器可见的内存态。

### [P3] AutoTestCaseDataSaver.saveOutputTable 存在冗余 putAll（疑似漏写逻辑）

- **文件**: `nop-autotest/nop-autotest-core/src/main/java/io/nop/autotest/core/execute/AutoTestCaseDataSaver.java:102-105`
- **维度**: D1（维护性）
- **证据**:
```java
Map<String, Object> row = new HashMap<>(r.getChangedData());
row.putAll(r.getChangedData());      // 完全冗余
row = varCollector.replaceVars(entityModel.getTableName(), row);
```
- **现状**: 新建 HashMap 拷贝后立即对同一数据源再做一次 putAll，第二行无任何效果。
- **风险**: 疑似本欲 put 其他数据（如 initData 列）写错；至少是误导性死代码。
- **建议**: 删除冗余行，或补上真正想要的合并源。
- **误报排除**: `r.getChangedData()` 两次调用间无状态变更（同步方法、同一线程），冗余确定。

### [P3] JsonDataDiffer 为空类

- **文件**: `nop-autotest/nop-autotest-core/src/main/java/io/nop/autotest/core/diff/JsonDataDiffer.java:10-11`
- **维度**: D1（维护性）
- **证据**:
```java
public class JsonDataDiffer {
}
```
- **现状**: 公共类无任何成员，与 `CsvDataDiffer` 命名对称但无实现。
- **风险**: 误导使用者以为存在 JSON diff 能力。
- **建议**: 删除或实现。
- **误报排除**: 全文即如上所示。

### [P3] 快照录制无敏感数据脱敏钩子（设计层风险提示）

- **文件**: `nop-autotest/nop-autotest-core/src/main/java/io/nop/autotest/core/execute/AutoTestCaseDataSaver.java:99-113`（及 `AutoTestCaseData.writeDeltaJson`）
- **维度**: D5
- **证据**:
```java
List<Map<String, Object>> data = rows.stream().map(r -> {
    Map<String, Object> row = new HashMap<>(r.getChangedData());
    ...
    row = varCollector.replaceVars(entityModel.getTableName(), row);   // 仅做变量替换，无字段脱敏
    ...
CsvHelper.writeCsv(new FileResource(file), CSVFormat.DEFAULT, headers, data);
```
- **现状**: 录制管线把实体全列原样写入源码库中的 CSV/JSON 快照，没有按列名/标签（如 password/secret）过滤或掩码的钩子；`TagVarCollector` 只处理 var/seq/clock 标签。
- **风险**: 在连接真实/脱敏不完整数据库执行录制（`force-save-output`）时，敏感字段会随测试数据提交进版本库。本地 H2 mock 场景风险低，故仅 P3 设计提示。
- **建议**: 提供 `@autotest-mask` 类标签或列名黑名单，录制写出前统一脱敏。
- **误报排除**: 已检查 saver/`AutoTestCaseData` 全部写出路径，确认无任何脱敏逻辑存在。
