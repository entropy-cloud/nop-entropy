# C1 共享可变状态与可见性 — 初审报告（nop-code REST 审计单元）

- **审计单元**: nop-code/nop-code-lang-{java,python,typescript,go,rust,csharp}/src/main/java（22f/5,041）+ nop-code/nop-code-api/src/main/java（75f/6,967）+ nop-code/nop-code-dao/src/main/java（28f，手写 17f；`_NopCodeDaoConstants.java` 与 `entity/_gen/` 为生成物只读）+ nop-code/nop-code-app（1f/35）。全单元 126f/12,614。
- **维度**: C1 共享可变状态与可见性（`ai-dev/skills/concurrency-resource-audit-prompt.md` C1 正文 + `ai-dev/skills/deep-audit-prompts.md` 共享前缀）
- **盲审声明**: 本初审为盲审。审计过程中未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/` 下任何历史记录；现状标注（新发现/已知未修复/已修复）留待复核阶段。未修改任何产品代码/模型/生成文件。
- **批次**: 全单元单批·抽查模式（126f 全部纳入 static/实例字段/共享面扫描；热点文件 JavaFileAnalyzer、CSharpCodeFileAnalyzer、MethodCallFilter、六个 ImportResolver、convention 包、LanguageAdapterRegistry 消费链逐行核读）
- **基线**: `_tmp/cra-baseline-nop-code-rest.txt`（并发原语九类全零；C1 static mutable=0；C1 static final 集合=5）
- **CL 前缀**: `nop-code-rest-CL-0NN`（维度集外线索）
- **发现编号前缀**: `C1-001` 起（本轮未使用，见终止声明）

---

## 一、计数口径（共享前缀第 10 条，可复现）

| 口径 | 命令/方法 | 排除项 | 结果 |
|---|---|---|---|
| static 非 final 字段 | `grep -rn --include="*.java" -E "static [A-Za-z]" <9 个 src/main 根> \| grep -v "static final" \| grep -v "@"`，再人工排除 static 方法/嵌套类声明 | `/test/`、`_gen/`、static 方法、`static class` | **1**（`NopCodeApplication.java:18` `static String[] globalArgs`） |
| static final 集合 | `grep -rn -E "static final (Map\|List\|Set\|HashMap\|ArrayList\|HashSet\|...)" <同上>` | 无 | **5**（与基线一致） |
| 交叉验证口径 B | 逐文件读取字段声明区（六 Adapter/六 Resolver/六 Analyzer/convention 3 文件/dao 手写 17f/app 1f） | 同上 | 与口径 A 一致 |

**基线差异披露**: 基线 "C1 static mutable: 0" 与本轮复核 "1" 存在 1 处差异（`globalArgs`）。差异原因：基线 grep 管道的 `grep -v "@"` 无法过滤该行且该行可命中 `static [A-Za-z]`，推测基线脚本对无修饰符（package-private）行做了额外排除。两个独立口径（grep + 逐文件读）交叉验证均为 1，按共享前缀第 10 条以可复现口径为准。该处判定为平台标准启动器模式（见载体表 S-01），**非缺陷，不改变零发现结论**。

---

## 二、五类载体清单表（逐项判定）

### 载体 1：singleton bean 的实例字段

| # | 载体 | 位置 | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|---|---|
| I-01 | 六语言 `XxxLanguageAdapter`（IoC 单例，经 `_lang-*.beans.xml` 装配为 `ILanguageAdapter`） | 各模块 `XxxLanguageAdapter.java`（34–37 行，全文核读） | 无（0 实例字段，全部方法本地变量） | `LanguageAdapterRegistry` 转发调用 | 不需要（无状态） | **安全**。注意：任务假设"Analyzer 为 IoC 单例"不成立——`getFileAnalyzer()` 每次 `new`，analyzer 是**每文件新建**实例（见 I-03） |
| I-02 | `LanguageAdapterRegistry.adapters`（可变 HashMap，单元外 nop-code-core，作消费链上下文核读） | `nop-code-core/.../LanguageAdapterRegistry.java:13` | `registerAdapter/setAdapters`（仅 IoC 初始化期，`app-service.beans.xml` 一次性注入六 adapter） | `getAdapter/getAnalyzer/getSupportedLanguages`（请求期只读迭代） | NopIoC 初始化完成→请求服务的平台初始化 happens-before；写入期单线程 | **安全**（启动期写、运行期只读的标准装配生命周期） |
| I-03 | 六语言 `XxxCodeFileAnalyzer` 实例字段：JavaFileAnalyzer `typeSolver`/`parserConfiguration`(final) + `methodCallFilter`/`routeConvention`/`enableSymbolResolution`(可变)；CSharpCodeFileAnalyzer `source`/`lastNamespace`(可变) | `JavaFileAnalyzer.java:70-85`、`CSharpCodeFileAnalyzer.java:41-42` | 同一 analyze() 调用内 | 同一调用内（IndexVisitor/WalkContext 亦每调用新建） | 实例封闭（thread confinement）：全部 7 个获取点（ProjectAnalyzer L173/256/441/592/667、CodeIndexService L382/458/1122）均经 `registry.getAnalyzer()` → `adapter.getFileAnalyzer()` 每文件 `new`，全库（排除 test）无 analyzer 缓存点 | **安全**。CSharp 的 `source`/`lastNamespace` 实例态设计在"每调用新建"契约下封闭；若未来有人把 analyzer 改为单例注入即成数据竞争——登记为结构脆弱性备注，非现行缺陷 |
| I-04 | `ConfigDrivenRouteConvention.model`（final 引用） | `convention/ConfigDrivenRouteConvention.java:12-14` | 构造后无写点（生产代码 0 个构造点，仅测试 `TestFrameworkPatternDslEquivalence` 构造）；`mappingAnnotations()/classPrefixAnnotations()` 对外做 `new HashSet<>(...)` 防御拷贝 | `httpMethodFor` 只读 `getOrDefault` | 无共享（生产未装配） | **安全** |

### 载体 2：static 可变字段

| # | 载体 | 位置 | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|---|---|
| S-01 | `NopCodeApplication.globalArgs`（`static String[]`，非 final，全单元唯一） | `nop-code-app/.../NopCodeApplication.java:18` | `main()` 写一次（L31，main 线程） | `start(@Observes StartupEvent)`（L23） | 写发生在 `Quarkus.run()` 之前，StartupEvent 在 Quarkus 启动序列内发布；write-once 后无二次写 | **安全**（平台标准启动器模式；F3 同族清扫：`nop-auth-app/NopAuthApplication`、`nop-oauth-app/NopOauthApplication`、`nop-job-app/NopJobApplication` 同型同判定，非本单元缺陷） |

static 线程不安全工具（SimpleDateFormat/Random/Calendar）：全单元 **0** 处（grep 口径见第一节，覆盖 9 个 src/main 根）。

### 载体 3：static final 集合与共享 static 对象（基线 5 处全部核实 + 1 类补充载体）

| # | 载体 | 位置 | 内容可变性 | 暴露面 | 判定 |
|---|---|---|---|---|---|
| SF-01 | `SpringFrameworkRouteConvention.SPRING_MAPPING_ANNOTATIONS` | `convention/SpringFrameworkRouteConvention.java:11` | `Set.of` 不可变 | `public static final` 暴露但不可变 | **安全** |
| SF-02 | `MethodCallFilter.DEFAULT_IGNORED_PACKAGES` | `analyzer/MethodCallFilter.java:15` | `new HashSet(...)` 内容可变 | `private`；全库 grep 无任何 `.add/.remove/.clear` 写点，仅 `contains/for` 只读（L131） | **安全**（P3 观察项：未用 `Collections.unmodifiableSet` 包裹属防御纵深缺失，无现行写者，不立案） |
| SF-03 | `MethodCallFilter.DEFAULT_IGNORED_METHODS` | `analyzer/MethodCallFilter.java:20` | 同上 | 同上（只读点 L114） | **安全**（同 SF-02 观察项） |
| SF-04 | `MethodCallFilter.JAVA_LANG_TYPES` | `analyzer/MethodCallFilter.java:148` | `Set.of` 不可变 | private | **安全** |
| SF-05 | `TypeScriptCodeFileAnalyzer.EXTENSIONS` | `typescript/analyzer/TypeScriptCodeFileAnalyzer.java:43` | `Arrays.asList` 定长（`set()` 可写元素） | L52 `getFileExtensions()` 直接返回共享 static 引用；全库无 `set()` 调用点；生产 `getFileExtensions()` 调用方（LanguageAdapterRegistry:21、ProjectAnalyzer:324/703、CodeIndexService:2282）走的均为 **adapter** 的每调用新建 `Arrays.asList`，analyzer 侧 getter 主链路未被调用 | **安全**（F3 同族清扫：其余五语言 analyzer 的 `getFileExtensions` 返回 `Collections.singletonList`/等价每调用新建，唯 TS 用共享 static——一致性弱点，无写者不立案） |
| SF-06 | 五语言 `static final TreeSitterXxx TS_LANGUAGE`（补充载体：共享 static 单例对象，基线"集合"口径外） | `GoCodeFileAnalyzer.java:37`、`RustCodeFileAnalyzer.java:39`、`CSharpCodeFileAnalyzer.java:39`、`PythonCodeFileAnalyzer.java:30`、`TypeScriptCodeFileAnalyzer.java:41` | 包装类无状态（单方法 `language()` 返回 static `Language`）；背后 `io.nop.treesitter.language.Language` 的可变字段为：`volatile externalScannerFactory`（static init 写一次）、`volatile scannerProgramValidated`（幂等校验记忆化，L777-784，重复执行无害）、`ConcurrentHashMap validSymbolCache`、`volatile float observedNodesPerByte`（单调 max，L762-769，丢失更新无害） | 每个 analyze() 经 `parser.setLanguage(TS_LANGUAGE)` 共享 | **安全**（使用面）。`Language` 内部实现归 nop-treesitter 单元自身审计，本单元不重审（误报校准：平台基础设施只审使用方式） |

### 载体 4：ThreadLocal

全单元 **0** 处（`grep -rn "ThreadLocal" <9 个 src/main 根>` 零命中，含 `NamedThreadLocal`）。

### 载体 5：集合/数组/可变对象参数逃逸（构造器/getter 泄漏）

| # | 载体 | 位置 | 逃逸面 | 判定 |
|---|---|---|---|---|
| E-01 | `JavaFileAnalyzer.getTypeSolver()` 返回内部可变 `CombinedTypeSolver` | `JavaFileAnalyzer.java:109-111` | 全库主代码无调用点；实例本身每调用新建 | **安全**（逃逸接口存在但无消费方，实例封闭） |
| E-02 | api DTO（`dto/` 28 个 `@DataBean`）getter 返回内部 `List/Map/Set` 引用未拷贝 | 如 `IndexStatsDTO.java:40-46`、`GraphWikiDTO.java:22`、`FileTreeNode.java:15` | 每请求内 service 方法构建 → GraphQL/REST 序列化后即弃；无 static DTO 缓存（service 层交叉核查：仅 `static ... from(Xxx)` 工厂方法每调用新建 DTO） | **安全**（平台标准 `@DataBean` JavaBean 模式，属共享前缀误报校准项；无跨线程共享路径） |
| E-03 | `beans/` 包 InputBean/OutputBean（32 个） | 文件头 `//__XGEN_FORCE_OVERRIDE__` | 生成物 | **不在审计对象**（生成文件硬停规则；模板层无可变 static） |
| E-04 | 构造器启动线程/注册监听器（不安全发布） | 全单元 `new Thread|.start()|addListener|registerListener` 扫描 | 唯一命中 `QuarkusIntegration.start()`（框架生命周期调用，非线程构造） | **安全** |
| E-05 | dao 手写层：`NopCodeDaoConstants`（空接口 extends 生成的常量接口，隐式 `public static final String` 全不可变）、11 个 `INopCode*Biz` 标记接口、11 个 entity 空壳子类 | `dao/NopCodeDaoConstants.java`、`biz/`、`entity/` | 无字段无状态 | **安全** |

### 复合操作 / 非原子原语（C1 步骤 4–5）

- `computeIfAbsent`/`containsKey+put` 检查-然后-行动：全单元 **0** 处。
- `++` 复合递增共享计数器、long/double 拆分写、可变 BigDecimal/Date 共享：全单元 **0** 处（`synchronized|volatile|Atomic*` 零命中佐证单元内无任何显式同步需求点）。
- ID 生成：`UUID.randomUUID()`（Go/Rust/C# 的 `newId()` 与 Java/Python 直调）为线程安全静态工厂，无共享状态。

---

## 三、风险立案

### 终止声明（零发现）

**本轮 C1 维度初审在审计单元全量范围内未发现达到立案标准（P3 及以上）的共享可变状态缺陷：P0=0，P1=0，P2=0，P3=0，立案数=0。**

依据：
1. 基线预注的"并发原语九类全零"经本轮独立复核成立（ThreadLocal/synchronized/volatile/Atomic*/并发容器/锁/线程池/异步链/调度器在 9 个 src/main 根全部零命中，口径见第一节）。
2. 全单元唯一的 static 非 final 字段（S-01）与唯一的共享 static 对象族（SF-06）均判定安全，理由与同族清扫记录见载体表。
3. 基线标注的 5 处 static final 集合逐项核实：2 处 `Set.of` 不可变、2 处 HashSet 无写者、1 处 `Arrays.asList` 无写者且主链路未被消费；三处防御纵深弱点（SF-02/03/05）均为"无写者的理论暴露面"，按共享前缀通用审计口径第 3 条（必须有结构性原因或可量化风险）不立案，留载体表为后续变更的对照基线。
4. 本单元的并发安全本质上由两条结构性契约保证：**analyzer 每文件新建**（adapter 工厂模式，7 个获取点全核验）与**装配期一次写入、运行期只读**（registry + static 面）。这两条契约中的任何一条被后续重构打破（如 analyzer 改单例注入、registry 运行期热注册），I-03/SF 系列将立即转化为真实竞争面——建议复核阶段将此两条契约列入 watch-only。

### 维度集外线索（CL）

#### [nop-code-rest-CL-001] JavaFileAnalyzer 每文件重建 JavaParser 符号解析器栈（C9 分配放大形态）

- **文件**: `nop-code/nop-code-lang-java/src/main/java/io/nop/code/lang/java/analyzer/JavaFileAnalyzer.java:87-95,140`
- **证据片段**:
  ```java
  public JavaFileAnalyzer() {
      this.typeSolver = new CombinedTypeSolver();
      this.typeSolver.add(new ReflectionTypeSolver(true));
      ...
  }
  ...
  ParseResult<CompilationUnit> parseResult = new JavaParser(parserConfiguration).parse(sourceCode);
  ```
- **形态**: C9（并发高性能·热路径）。analyzer 每文件新建 → 构造器中 `CombinedTypeSolver + ReflectionTypeSolver` 与每次 `analyze()` 的 `new JavaParser(...)` 均随文件数线性重建；索引千文件级仓库时为可量化的重复分配（量化和基准形态归 C9 维度裁定）。
- **移交建议**: C9 维度核对 owner doc 声明的索引规模包络，估算每次构造代价，判断是否值得将 type solver 栈提升为不可变共享单例（JavaParser/ParserConfiguration 与 CombinedTypeSolver 的线程安全性需先核实）。
- **信心水平**: 很可能（结构事实确定，收益量化未做）

#### [nop-code-rest-CL-002] Go/Rust/C# ImportResolver 按 import 全量线性扫描项目文件集（C9/C5 形态，F3 同族三连）

- **文件**: `nop-code/nop-code-lang-go/src/main/java/io/nop/code/lang/go/GoImportResolver.java:70-79`；`.../rust/RustImportResolver.java:65-89`；`.../csharp/CSharpImportResolver.java:64-90`
- **证据片段**（Go 为例，Rust/C# 同构）:
  ```java
  private String matchProjectPackage(String importPath, Set<String> projectFiles) {
      String packagePrefix = importPath + "/";
      for (String file : projectFiles) {
          int idx = file.indexOf(packagePrefix);
  ```
- **形态**: C9（分配/复杂度放大）+ C5 边缘。每个文件的每个 import 对 `projectFiles` 全集做 O(n) 扫描（C# 版为逐段对齐的双重循环，放大更明显），索引整仓时 O(files × imports × projectFiles)。Java/Python/TS 版为 `Set.contains` 常量路径——同族六 resolver 三种复杂度档位并存。
- **移交建议**: C9 维度量化 `projectFiles` 规模下的扫描代价；若立案，修复方向是预处理项目文件索引（前缀 Map），三语言同族一并修（F3 清扫要求）。
- **信心水平**: 确定（结构事实）；收益量化归 C9

#### [nop-code-rest-CL-003]（跨单元·弱信号·watch-only）nop-treesitter `Language` 的幂等记忆化与单调 max 为良性竞争形态

- **文件**: `nop-tools/nop-treesitter/src/main/java/io/nop/treesitter/language/Language.java:762-784`
- **证据片段**:
  ```java
  public byte[] validatedScannerProgram() {
      byte[] program = scannerProgram;
      if (!scannerProgramValidated) {
          io.nop.treesitter.scanner.ScannerProgram.validate(program);
          scannerProgramValidated = true;
      }
      return program;
  }
  ```
- **形态**: C1（检查-然后-行动）形态，但已核验为良性：validate 幂等且输入不可变，最坏重复执行；`noteNodeCount` 的 volatile 单调 max 丢失更新无害（javadoc 已声明设计意图）。归 nop-treesitter 单元自身的 C1 审计范围，本单元仅因共享 `TS_LANGUAGE` 而顺带核验。
- **移交建议**: 无需行动；若 nop-treesitter 单元开展 CRA，此两点作为"已判定良性"的前置结论可引用。
- **信心水平**: 确定（良性）

---

## 四、复核移交要点

1. 本报告零发现结论依赖两条结构性契约（analyzer 每文件新建、装配期一次写），建议复核阶段独立验证 7 个 `getAnalyzer` 获取点（ProjectAnalyzer L173/256/441/592/667、CodeIndexService L382/458/1122）与六 adapter `getFileAnalyzer()` 的 `new` 语义。
2. 基线 "static mutable: 0" 与本轮 "1" 的差异（S-01 `globalArgs`）需复核确认口径归因，但不影响结论方向。
3. CL-001/CL-002 的量化与立案裁定归 C9 维度，本轮仅登记结构事实。

（初审完 · 盲审 · 待独立复核）

---

## 五、维度复核结论（C1 独立复核）

- **复核方式**: 独立复核子代理对初审抽样重读源码逐项核实；以下全部 file:line 为复核者独立 grep/读取所得，非转抄初审。
- **抽样清单（6 项载体 + 2 项专项）**:
  1. **I-01 adapter 无状态** — 成立。`CSharpLanguageAdapter.java`（全文 34 行）、`GoLanguageAdapter.java`（全文）零实例字段，仅 4 个接口方法返回局部值。
  2. **I-02 registry 启动期写/运行期读** — 成立。`LanguageAdapterRegistry.java:13` 可变 HashMap，写点仅 :34-42（registerAdapter/setAdapters）；`nop-code-service/src/main/resources/_vfs/nop/code/beans/app-service.beans.xml:18-19` registry 为 `ioc:default` 单例，六 adapter 经 :11-16 import 的 `_lang-*.beans.xml` 注册（如 `_lang-go.beans.xml:7-9`）。
  3. **I-03 analyzer 实例封闭（每文件新建契约）** — 成立。独立 grep 实测 `registry.getAnalyzer(` 调用点 **8 处**（ProjectAnalyzer.java:173/256/441/592/667 + CodeIndexService.java:382/458/1122），逐点核读均为逐文件获取后立即 `analyze` 即用即弃；六 adapter `getFileAnalyzer()` 全部 `return new XxxCodeFileAnalyzer()`（如 CSharpLanguageAdapter.java:18-20）；全库无 `ICodeFileAnalyzer` 字段持有、无绕过 adapter 的 analyzer 构造点。**勘误**：初审"7 个获取点"实为 8 个调用点（初审自身所列行号即 8 个），计数标注笔误，不影响结论。
  4. **I-04 ConfigDrivenRouteConvention** — 成立。:13 final 字段、:21/:26 防御拷贝、:28 只读 getOrDefault；生产代码 0 构造点（独立 grep 复现，仅测试构造）。
  5. **E-01 getTypeSolver 逃逸** — 成立。全库主代码 0 调用方（JavaFileAnalyzer.java:109 定义为唯一本单元命中；nop-ai 命中为异类同名方法）。
  6. **E-02 DTO getter 未拷贝** — 成立（误报校准维持）。IndexStatsDTO.java:39-41 确返回内部 Map 引用；service 层 grep 无 static DTO 缓存字段。
  7. **专项：S-01 唯一 static 可变字段** — 成立。NopCodeApplication.java:18 非 final 非 volatile 声明，:31 main 线程写一次（`Quarkus.run` 前），:22-25 StartupEvent 读；write-once 启动模式。
  8. **专项：CL-101 可达性（是否升级 C1 立案）** — **不升级**。registry（LanguageAdapterRegistry.java:19-28）每次转发 `adapter.getFileAnalyzer()` 无缓存，8 获取点全为每文件新建，无任何复用点；CSharp `source`/`lastNamespace` 在现行接线下等效方法局部态。
- **抽样「安全」判定失败数：0，无需立案 C1-R-001。初审零发现结论可信。**
- **CL 裁定**:
  - **CL-001 确认**（构造器 JavaFileAnalyzer.java:87-95 CombinedTypeSolver+ReflectionTypeSolver、:140 每次 analyze `new JavaParser`，独立核实）；归 C9，维持移交。
  - **CL-002 确认**（Go GoImportResolver.java:70-79 indexOf 循环、Rust RustImportResolver.java:65-89 双循环、C# CSharpImportResolver.java:64-90 逐段对齐三重循环，三处独立核读）；归 C9，维持移交。
  - **CL-003 确认（良性）**（Language.java:762-769 单调 max、:777-784 幂等记忆化，与初审引用一致）。
  - **CL-101 确认（watch-only，不立案）**：字段事实与"同族孤例"核实成立（CSharpCodeFileAnalyzer.java:41-42 字段、:69/:76 每调用写、:602-612 nodeText 实例方法读 `this.source`；Python/Rust/Go/TS 四同族 grep 零实例字段）；当前接线无交错序列，按"须有结构性原因或可量化风险"口径不立案，与初审 I-03 备注一致。若后续重构缓存 analyzer 或并行复用即升级 P1。
  - **G11-03-01 已修事实确认**：app-service.beans.xml:11-16 六个 `_lang-*.beans.xml` import 齐全，六语言 adapter 全部装配激活，无休眠降级口径。
- **两条结构性契约（watch-only）经独立复核成立**：① analyzer 每文件新建（8 获取点 + 6 工厂全核验）；② 装配期一次写、运行期只读（registry + beans.xml 一次性注入）。任一被打破，I-03/SF 系列即转化为真实竞争面。

## 最终保留项

最终保留项：0（watch-only 备注 3 项不属保留缺陷：CL-101 analyzer 状态性脆弱契约、SF-02/03/05 防御纵深弱点、"7 获取点"计数勘误已记录于本节第 3 条）
