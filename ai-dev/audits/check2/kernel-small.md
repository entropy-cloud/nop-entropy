# kernel-small 实现代码检查报告（check2）

- 检查日期: 2026-08-23
- 模块路径: nop-kernel 下 codegen/javac/dataset/antlr4/markdown/record-mapping/kernel-cli
- 文件数: 194（src/main/java，含 antlr4-common 9 + antlr4-tool 18、codegen 44、javac 14、dataset 59、markdown 25、record-mapping 20、kernel-cli 5）
- 覆盖范围声明: 深读约 95 个文件（codegen: XCodeGenerator/CodeGenTask/GenAopProxy/CodeGenTracer/ClassRenamer/XplToVueTransformer/graalvm 全部 11 个/maven 全部 7 个/common 全部 5 个/FormatHelper/initializers/AntlrParserConfig；javac: JdkJavaCompiler/JavaCompileResult/JavaCompileTool/DynamicURLClassLoader/Janino 全部/jdk 全部；dataset: record 流全部 14 个（含 support 3 个）/rowmapper 全部 11 个/binder 全部 5 个/impl 核心 20 个/接口 6 个；antlr4: common 全部 9 个/tool 的 GrammarLoader/CustomTool/CustomErrorManager/AstGrammarBuilder/AstGrammar/AstRule/RuleRef 等 8 个；markdown: simple 全部 11 个/table 全部 4 个/utils 全部 3 个/model 核心MarkdownSection；record-mapping: 全部非生成类 15 个；kernel-cli 全部 5 个），其余约 100 个文件（antlr4 tool 的 model 剩余小类、markdown model 剩余、dataset 少量纯接口/extractor、codegen 少量常量类）经通读或模式扫描（流关闭/并发原/@Inject private/命令执行/异常吞噬等 grep）覆盖，模式扫描覆盖 100% 文件。未深读区域: nop-antlr4-tool/model 的 block 小类（OptionalBlock/OrBlock/PlusBlock/SeqBlock/SetBlock/StarBlock/TerminalNode，均为 15-60 行数据类）、markdown model 剩余 DataBean 类。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 1 |
| P2 | 6 |
| P3 | 6 |

## 发现列表

### [P1] DataParameterBinders.FLOAT 声明 StdDataType.DOUBLE 但以 Float 强转换读写，Double 值触发 ClassCastException、读取精度降级

- **文件**: `nop-kernel/nop-dataset/src/main/java/io/nop/dataset/binder/DataParameterBinders.java:206-223`
- **维度**: D1 / D8
- **证据**:
```java
public static final IDataParameterBinder FLOAT = new IDataParameterBinder() {
    @Override
    public StdDataType getStdDataType() {
        return StdDataType.DOUBLE;                // 声明为 DOUBLE
    }
    @Override
    public Object getValue(IDataParameters params, int index) {
        return params.getFloat(index);           // 实际读 Float
    }
    @Override
    public void setValue(IDataParameters params, int index, Object value) {
        params.setFloat(index, (Float) value);   // 强转 Float
    }
};
```
- **现状**: `StdSqlType.FLOAT` 的 StdDataType 是 `DOUBLE`（StdSqlType.java:25 `FLOAT(false, false, Types.FLOAT, StdDataType.DOUBLE)`）。DialectImpl.createBinder（DialectImpl.java:575-591）在 `stdType == sqlType.getStdDataType()`（即 DOUBLE==DOUBLE）时不会包裹 AutoConvertDataParameterBinder，直接返回此 FLOAT binder。
- **风险**: (1) 写路径：JdbcHelper.setParameters（JdbcHelper.java:281）直接 `binder.setValue(jst, index, valueMarker.getValue())`，EQL/ORM 对 FLOAT 列的 DOUBLE 语义参数（Java Double）在 `(Float) value` 处抛 ClassCastException，导致查询/写入失败；(2) 读路径：JDBC FLOAT 列（SQL 语义近似 double）经 `toFloat` 强降为 float 精度，与声明的 DOUBLE 类型不符。
- **建议**: FLOAT binder 改用 `getDouble/setDouble/(Double)`（与 StdDataType.DOUBLE 对齐）；或与 REAL 对调语义注释并修正声明。REAL binder（FLOAT↔Float）是当前唯一自洽的实现可作参照。
- **误报排除**: 已读 StdSqlType 定义确认 FLOAT→StdDataType.DOUBLE 映射；已读 DialectImpl.createBuilder 确认无 AutoConvert 包装路径；已读 JdbcHelper.setParameters 确认 setValue 前无类型归一化；已读 IDataParameters.getFloat/setFloat 确认签名（Float），强转发生在 binder 层而非 params 层。

### [P2] ReflectClass.remove 复制粘贴错误（allDeclaredMethods 分支写成 allPublicMethods）且 methods 移除键不匹配，delta 反射配置扣除失效

- **文件**: `nop-kernel/nop-codegen/src/main/java/io/nop/codegen/graalvm/ReflectClass.java:54-73`
- **维度**: D1
- **证据**:
```java
if (reflectClass.allDeclaredMethods)
    allPublicMethods = false;          // BUG: 应为 allDeclaredMethods = false

if (reflectClass.allPublicMethods) {
    allPublicMethods = false;
}

reflectClass.fields.forEach(field -> {
    this.fields.removeByKey(field.getName());     // fields 键为 name，正确
});

reflectClass.methods.forEach(method -> {
    this.methods.removeByKey(method.getName());   // BUG: methods 键为 getSignature()
});
```
- **现状**: methods 的 KeyedList 以 `ReflectMethod::getSignature`（即 "name(paramTypes)"）为键（ReflectClass.java setMethods/addMethod），remove 时用 `method.getName()` 查键永远查不到。同文件 merge（约 176 行）中 `methods.getByKey(method.getName())` 同样键不匹配。
- **风险**: `ReflectConfigGenerator.generateDeltaToResource` 中 `config.remove(loadDefaultConfig())` 无法扣除默认配置已声明的 methods 与 allDeclaredMethods 标志，生成的 delta reflect-config.json 比预期宽（native image 暴露更多反射成员），且 isEmpty() 判定失效导致应删除的空条目残留、随每次增量生成累积变大。仅 `nop.codegen.trace.enabled=true` 时触发（构建期工具）。
- **建议**: 第三处改为 `allDeclaredMethods = false`；remove/merge 中的键统一改为 `method.getSignature()`。
- **误报排除**: 已读 KeyedList.getByKey/removeByKey 实现（基于构造时 keyFn 生成的 map 键，getKey=String.valueOf(keyFn.apply(obj))），确认 signature 与 name 不可能相等；已读 ReflectConfigGenerator.generateDeltaToResource 的调用链确认 remove 的语义意图。

### [P2] MarkdownSectionMerger.merge 的 summary 用自身覆盖自身，B 的 summary 丢失

- **文件**: `nop-kernel/nop-markdown/src/main/java/io/nop/markdown/simple/MarkdownSectionMerger.java:29-31`
- **维度**: D1
- **证据**:
```java
if (!StringHelper.isEmpty(sectionB.getSummary())) {
    sectionA.setSummary(sectionA.getSummary());   // BUG: 应为 sectionB.getSummary()
}
```
- **现状**: 公开 API `MarkdownSection.mergeWith(section)`（MarkdownSection.java:109）委托本方法。与上下文三段 title/text 的写法（`sectionA.setXxx(sectionB.getXxx())`）对照，summary 行是复制粘贴错误。
- **风险**: 合并两个 Markdown 章节树时，B 侧的 summary 永远无法覆盖到 A，合并结果静默丢失数据。当前仓库内未发现 mergeWith 的活跃生产调用方，属公开 API 契约缺陷。
- **建议**: 改为 `sectionA.setSummary(sectionB.getSummary());`
- **误报排除**: 已读 merge 方法全量与 MarkdownSection.mergeWith 调用点；全仓库 grep 确认 MarkdownSectionMerger 仅经 mergeWith 暴露。

### [P2] TableViewToMarkdownTableConverter 用 CollectionHelper.set(..., null) 补齐列表，规则表格时覆盖最后一列头/值为 null

- **文件**: `nop-kernel/nop-markdown/src/main/java/io/nop/markdown/table/TableViewToMarkdownTableConverter.java:60-66、83-88`
- **维度**: D1
- **证据**:
```java
headerRow.forEachCell(0, (cell, rowIndex, colIndex) -> {
    ...
    headers.add(escapeCell(cellText));
    return ProcessResult.CONTINUE;
});

CollectionHelper.set(headers, cols - 1, null);    // BUG
```
- **现状**: `CollectionHelper.set(list, index, value)`（CollectionHelper.java:350-355）在 list 已达到该大小时执行 `list.set(index, value)` 覆盖。ITableView.getColCount 是全表最大列数（AbstractTable.java:92-96），而 forEachCell 按 header 行实际 cell 数遍历（IRowView.forEachCell 遍历 iterator）。规则表格（每行 cell 数 == 全表列数，最常见情形）下 headers 已有 cols 个元素，set 把最后一个表头覆盖为 null；数据行 88 行同样处理。
- **风险**: `MarkdownTable.fromTableView` 公开 API 转换规则表格时最后一列表头/数据全部丢失（escapeCell(null) 返回 " "）。当前仓库内无生产调用方。
- **建议**: 意图是补齐稀疏行，应使用 `CollectionHelper.setSize(headers, cols)` 或以 null 填充后仅当 `headers.size() < cols` 时补齐。
- **误报排除**: 已读 CollectionHelper.set、AbstractTable.getColCount（max 语义）、AbstractRow.getColCount（cells.size()）、IRowView.forEachCell（iterator 遍历），确认规则表格下覆盖路径成立。

### [P2] PomModelMerger.merge 丢弃子模块独有依赖，且把 parent 的 modules 合入 child 后以 child 目录解析

- **文件**: `nop-kernel/nop-codegen/src/main/java/io/nop/codegen/maven/parse/PomModelMerger.java:49、56-64`
- **维度**: D1
- **证据**:
```java
ret.setModules(mergeList(parentModel.getModules(), model.getModules()));  // parent modules 传入 child

Map<PomArtifactKey, PomDependencyModel> deps = new LinkedHashMap<>();
deps.putAll(parentModel.getDependencies());

for (PomDependencyModel dep : model.getDependencies().values()) {
    PomDependencyModel oldDep = deps.get(dep.getArtifactKey());
    if (oldDep != null) {
        deps.put(dep.getArtifactKey(), mergeDep(oldDep, dep));
    }
    // BUG: oldDep == null 时（child 独有依赖）未 put，依赖丢失
}
```
- **现状**: (1) child 独有依赖（不在 parent 中）不会进入合并结果；(2) Maven 语义中 modules 不继承，此处 mergeList 将 parent 的 modules 并入 child，随后 PomModelResolver._resolve（PomModelResolver.java:64-73）以 `new File(model.getModuleDir(), module)` 即 child pom 所在目录解析 parent 的 module 相对路径，通常指向不存在的 pom.xml。
- **风险**: 一旦经 PomModelResolver 解析多层工程（当前仓库内该类无生产调用方，仅测试使用；MavenModelHelper.getProjectArtifactKey 不走此路径），合并结果依赖列表缺失、聚合模块解析报错。属休眠的代码路径缺陷。
- **建议**: 循环补 else 分支 `deps.put(dep.getArtifactKey(), dep)`；modules 不应从 parent 继承，`ret.setModules(model.getModules())` 即可。
- **误报排除**: 已读 PomModel/PomModelResolver 全文确认 getDependencies 为 child 自身依赖、_resolve 以 merged model 的 modules 递归 resolveModel；全仓库 grep（含 xpl/xgen/xml）确认 PomModelResolver 无生产调用方，故定 P2 而非 P1。

### [P2] CodeBlock.append 第三次及以后的调用丢失中间追加内容

- **文件**: `nop-kernel/nop-codegen/src/main/java/io/nop/codegen/common/CodeBlock.java:38-49`
- **维度**: D1
- **证据**:
```java
public CodeBlock append(String text) {
    if (StringHelper.isEmpty(text))
        return this;

    if (this.text == null) {
        this.text = text;
    } else {
        this.buf = new StringBuilder();   // 每次新建，覆盖旧 buf
        this.buf.append(this.text);       // this.text 从未更新，仍是第一次的值
        this.buf.append(text);
    }
    return this;
}
```
- **现状**: append("a") → text="a"；append("b") → buf="ab"（text 仍为 "a"）；append("c") → buf 重建为 "a"+"c"="ac"，"b" 丢失。getText 优先返回 buf。
- **风险**: 同一 CodeBlock 追加 3 次以上时生成代码内容错误。当前 MethodBlock.addCodeBlock(loc, text) 每次 append 一次、GenJava/GenJs/CodeBlock 在仓库内无生产调用方（仅测试引用 ClassRenamer 而非此类），属休眠 API 的真实缺陷，一旦启用即触发。
- **建议**: else 分支改为复用已有 buf（首次才 new），或 `this.buf.append(text)` 前先把 text 字段同步。
- **误报排除**: 已读全类确认 text 字段在 append 路径无其他赋值；grep 全仓库（java/xpl/xgen）确认无生产调用链。

### [P2] RecordFieldMappingConfig.getObjectConstructor 可返回 null，RecordMappingTool.makeTargetObject 未判空导致 NPE

- **文件**: `nop-kernel/nop-record-mapping/src/main/java/io/nop/record_mapping/model/RecordFieldMappingConfig.java:124-145`；`nop-kernel/nop-record-mapping/src/main/java/io/nop/record_mapping/impl/RecordMappingTool.java:253-269`
- **维度**: D1
- **证据**:
```java
// RecordFieldMappingConfig
public Supplier<Object> getObjectConstructor(boolean collection, ...) {
    if (ctx.isForceUseMap()) { ... }
    if (getNewInstanceExpr() != null) return ...;
    if (collection && getKeyProp() != null) return ...;
    if (classModel != null) return classModel::newInstance;
    if (collection) return ArrayList::new;
    return null;                       // 非 collection 且无 classModel/newInstanceExpr
}

// RecordMappingTool.makeTargetObject
Supplier<Object> constructor = field.getObjectConstructor(false, source, target, ctx);
if (field.getVarName() != null) {
    Object toValue = constructor.get();   // constructor 为 null 时 NPE
```
- **现状**: 字段声明了 `mapping=xxx`（对象映射）但未定义 type（classModel 为 null）、无 newInstanceExpr、ctx 未设 forceUseMap 时，getObjectConstructor(false,...) 返回 null。varName 分支 `constructor.get()` 直接 NPE；非 varName 分支将 null supplier 传入 BeanTool.makeComplexProperty → BeanPropHelper.makeIn，行为取决于底层实现。
- **风险**: 服务端 record-mapping（ModelBasedRecordMapping.mapObjectField）对"有子映射但无类型声明"的字段执行映射时抛 NPE，错误信息无法定位到字段配置。md 解析路径（MarkdownDslResourceLoader）设置了 forceUseMap=true 不受影响。
- **建议**: getObjectConstructor 非 collection 兜底返回 `LinkedHashMap::new`（与 init 中 toClassModel 为空时的 newTarget 行为一致），或 makeTargetObject 对 null constructor 抛带字段位置信息的 NopException。
- **误报排除**: 已读 makeTargetObject 两个分支与 mapObjectField 调用链；已读 init() 确认 classModel 仅在 type != null 时赋值；已读 RecordMappingContext 确认 forceUseMap 默认 false。

### [P3] SingleColumnRow.setObject 静默丢弃写入，与 BaseDataRow/MapDataRow 的只读异常行为不一致

- **文件**: `nop-kernel/nop-dataset/src/main/java/io/nop/dataset/impl/SingleColumnRow.java:33-35`
- **维度**: D8 / D1
- **证据**:
```java
@Override
public void setObject(int index, Object value) {
    // 空实现，静默丢弃
}
```
- **现状**: isReadonly() 恒为 true，但 BaseDataRow.setObject 对只读行抛 `ERR_DATASET_IS_READONLY`，SingleColumnRow 一声不吭。
- **风险**: 调用方依赖只读异常检测写入错误时得到假成功，数据静默丢失。
- **建议**: 与 BaseDataRow 一致抛 ERR_DATASET_IS_READONLY。
- **误报排除**: 已对照 BaseDataRow.setObject（抛异常）与 MapDataRow.setObject（同抛异常），确认行为漂移。

### [P3] JdkJavaCompiler 固定 -source/-target 1.8，Java 21 平台上已弃用且限制生成代码语法

- **文件**: `nop-kernel/nop-javac/src/main/java/io/nop/javac/jdk/JdkJavaCompiler.java:84-87`
- **维度**: D8
- **证据**:
```java
options.add("-source");
options.add("1.8");
options.add("-target");
options.add("1.8");
```
- **现状**: 项目基线 Java 21（AGENTS.md），JDK 21 javac 对 source/target 8 仅弃用支持；AopCodeGenerator 生成的代理代码若引用 Java 21 语法（如 record、var）将编译失败。
- **风险**: 当前生成代码为传统语法可编译；未来 JDK 移除 8 目标或生成模板升级语法时构建中断。
- **建议**: 按运行 JDK 动态选择（如 `Runtime.version().feature()`）或改用 `--release`。
- **误报排除**: 已读 compile() 全文确认选项硬编码无覆盖入口；确认调用方 GenAopProxy 未传额外 options。

### [P3] JdkJavaCompiler 构造源文件 URI 失败时静默吞异常并以 null URI 继续

- **文件**: `nop-kernel/nop-javac/src/main/java/io/nop/javac/jdk/JdkJavaCompiler.java:69-73`
- **维度**: D4
- **证据**:
```java
URI uri = null;
try {
    uri = URI.create("string:///" + className.replace('.', '/') + JavaFileObject.Kind.SOURCE.extension);
} catch (Exception e) { // NOPMD -- no error
}
```
- **现状**: className 含 URI 非法字符时 uri 保持 null，SimpleJavaFileObject 仍以 null URI 构造，错误推迟到 javac 内部使用 URI 时以更难定位的形式爆发。
- **风险**: 触发概率低（类名字符集受限于 Java 标识符），但异常吞噬违背平台错误处理两层策略。
- **建议**: 捕获后抛 NopException 并携带 className。
- **误报排除**: 已读该匿名类后续使用确认 uri 无二次校验。

### [P3] KernelCliValidateCommand verbose 模式使用 e.printStackTrace() 绕过日志框架

- **文件**: `nop-kernel/nop-kernel-cli/src/main/java/io/nop/kernel/cli/commands/KernelCliValidateCommand.java:47-50`
- **维度**: D4
- **证据**:
```java
} catch (Exception e) {
    errorCount++;
    System.err.println("[FAIL] " + inputFile);
    System.err.println("       " + e.getMessage());

    if (verbose) {
        e.printStackTrace();
    }
}
```
- **现状**: printStackTrace 直写 stderr，绕过 SLF4J（对比 CodeGenTask.genAopProxy 已改 LOG.error 并留有注释说明统一走日志框架）。
- **风险**: 日志采集/级别控制失效；与仓库既有整改方向不一致。
- **建议**: 改为 `LOG.error("nop.cli.validate-fail:{}", inputFile, e)`。
- **误报排除**: 已对照 CodeGenTask.java:215-218 的同类修复注释确认平台约定。

### [P3] RecordMappingConfig.requireField 的 ARG_ALLOWED_FIELD_NAMES 参数误传字段数量而非名称集合

- **文件**: `nop-kernel/nop-record-mapping/src/main/java/io/nop/record_mapping/model/RecordMappingConfig.java:78-83`
- **维度**: D4
- **证据**:
```java
public RecordFieldMappingConfig requireField(String name) {
    RecordFieldMappingConfig field = getField(name);
    if (field == null)
        throw new NopException(ERR_RECORD_UNKNOWN_FIELD)
                .param(ARG_FIELD_NAME, name)
                .param(ARG_ALLOWED_FIELD_NAMES, this.getFields().size());  // int，非名称集合
    return field;
}
```
- **现状**: 同类方法 requireFieldByFrom（96 行）正确传 `fromFields.keySet()`。
- **风险**: 错误提示中的允许字段名列表显示为一个数字，排错信息无效。
- **建议**: 改传 `this.getFieldNames()`。
- **误报排除**: 已对照同文件 requireFieldByFrom 的正确写法。

### [P3] RowNumberRecordInput.adapt 将记录强制包装为 SimpleRowNumberRecord 的 unchecked cast

- **文件**: `nop-kernel/nop-dataset/src/main/java/io/nop/dataset/record/impl/RowNumberRecordInput.java:72-77`
- **维度**: D8
- **证据**:
```java
protected T adapt(T record, long readCount) {
    if (record instanceof IRowNumberRecord) {
        ((IRowNumberRecord) record).setRecordRowNumber(readCount);
    } else {
        record = (T) new SimpleRowNumberRecord(readCount, record);  // unchecked
    }
    return record;
}
```
- **现状**: T 为具体类型（非 Object/IRowNumberRecord）时，返回的 SimpleRowNumberRecord 在调用方按 T 使用会 ClassCastException；readBatch 的 adaptList 还会把列表元素类型静默替换。
- **风险**: 泛型 API 的类型契约依赖调用方仅以 Object/IRowNumberRecord 消费结果，误用时报错位置远离根因。
- **建议**: 类或方法层文档标注约束，或在构造时校验泛型用途。
- **误报排除**: 已读 readBatch/readAll/adaptList 全部路径确认替换行为；确认类注释已声明包装意图但未声明类型约束。

## 附注（已验证无误报的区域）

- GenAopProxy：URLClassLoader 已 try-with-resources 关闭（含 Windows 目录锁注释）；JdkJavaCompiler 的 fileManager 在 task.call 后 finally close；FileHelper.writeText/writeBytes 均 assureParent/mkdirs。
- RecordInputImpls.defaultReadBatch 的 maxCount=0 提前返回、TransformRecordInput/LimitRecordInput/DelegateRecordInput 的委托语义、RowNumberRecordInput 三种 readBatch 行号起点均逐条推演无误。
- MarkdownDocumentParser.parseSection 的 `nextUntil('\n','#',true)` 经 TextScanner 源码确认匹配的是 "\n#" 双字符序列（`sc.cur==c1 && sc.peek()==c2`），标题切分逻辑正确，不存在行中 '#' 死循环/丢字符问题。
- AbstractParseTreeParser.twoPhaseParse 第二阶段前 parser.reset() 会 seek(0) 重置 token 流（ANTLR Parser.reset 实现），BailErrorStrategy 抛出的 ParseCancellationException 恒带 cause。
- D3/D5/D7 维度：范围内 src/main/java 无 @Inject/@Value/SimpleDateFormat/new Random/命令执行/SQL 拼接命中；GraalvmConfigGenerator.generateVfsIndex 的 Set 来源为 DeltaResourceStoreBuilder 的 TreeSet，输出顺序稳定。
- KeyedList.getByKey/add 的去重语义已读实现，ReflectConfig.merge 的 methods 键不匹配仅导致替换而非重复条目（add 走 map.put 覆盖），已并入 P2-ReflectClass 条目。
