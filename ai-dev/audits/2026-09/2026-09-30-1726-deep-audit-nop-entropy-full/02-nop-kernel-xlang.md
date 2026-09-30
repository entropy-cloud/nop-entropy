# G2: nop-kernel XLang 体系深度审计（首轮）

- **审计日期**: 2026-09-30
- **审计维度**: 09（错误处理与错误码）、10（XDSL 与 XLang 正确性）、15（类型安全与泛型使用）、16（测试覆盖与质量）
- **审计对象**: nop-kernel 下的 nop-xlang（优先深读）、nop-xdefs（优先深读）、nop-codegen、nop-dataset、nop-javac、nop-jpath、nop-kernel-cli、nop-markdown、nop-record-mapping、nop-xlang-java、nop-xlang-truffle（抽查）、nop-antlr4（抽查）
- **排除**: target/、`_` 前缀生成文件、.m2-repo-2275/、_tmp/；nop-core/nop-commons/nop-api-core 归其他子代理

## 审计范围

### 手工深读的代码与模型

- **nop-xlang 引擎核心（全文精读）**: `XDslExtender.java`、`DeltaMerger.java`、`OverrideHelper.java`、`XDslValidator.java`、`XDefConstraintValidator.java`、`DslNodeLoader.java`、`XDslKeys.java`、`XDslCleaner.java`、`XLang.java`（后端路由 choke point）、`EvalBackendRouter.java`、`EvalBackendObservation.java`、`XPathHelper.java`、`JsPromise.java`、`RegisterModelDiscovery.java`、`XplModel.java`
- **nop-xlang 抽查**: `XplCompiler.java`、`XDefToObjMeta.java`、`XLangSemantics.java`（delete/attr 分派）、`SimpleStdDomainHandlers.java`（catch/wrap 模式）、`ObjMetaToXDef.java`、`JaninoParser.java`、`DeltaExtendsGenerator.java`、`EvalHelper.java`、`JsDate.java`、`IStdDomainHandler.java`
- **nop-xdefs（114 个 xdef 全量机械校验 + 重点精读）**: `xdsl.xdef`、`xdef.xdef`、`xpl.xdef` 全文精读；全量校验 `xdef:ref` 文件引用（相对/绝对路径共 171 项全部可解析）、`enum:` 类引用的默认值与枚举常量匹配（91 个类、11 个带默认值全部匹配、类文件全部存在）
- **nop-xlang-java**: `GeneratedEvalBinding.java`、`GeneratedClassBindingBinder.java`、`XlangJavaGenTask.java`、`ExecToJavaTranslator.java`（裸异常位点上下文）
- **nop-xlang-truffle 抽查**: `XLangContextPool.java`、`EvalHandoff.java`
- **小模块**: nop-record-mapping（beans.xml/autoconfig/register-model 一致性）、nop-jpath（NopJqException/NopJqErrors/JsonPathParser）、nop-dataset（LimitRecordInput 等）、nop-markdown（MarkdownCodeBlockParser 全文）、nop-codegen（CodeGenTask 全文）、nop-kernel-cli、nop-javac、nop-antlr4-common
- **测试盘点**: nop-xlang（99 个测试文件，按包与 main 对照）、nop-xlang-java（77）、nop-xlang-truffle（26）、nop-jpath（12）、nop-markdown（9）、nop-dataset（5）、nop-record-mapping（5）、nop-javac（3）、nop-kernel-cli（2）、nop-codegen（10）；nop-xdefs 无 Java 测试（其 xdef 由 nop-xlang `TestXDefParse.testParse` 遍历 `/nop/schema` 全量解析覆盖，见维度 16 说明）

### 机械基线核实（区分 main/test）

- 裸异常（`throw new RuntimeException|IllegalArgumentException|IllegalStateException|UnsupportedOperationException|NullPointerException`）：本范围 **main 代码 71 处**（分布：nop-xlang-truffle 19、nop-xlang-java 16、nop-xlang 22、nop-markdown 4、其他 ≤2），逐一查看上下文判级；test 代码不报。
- `System.out`/`printStackTrace`：本范围 **main 代码 15 处**（CodeGenTask 11、KernelCliValidateCommand 4，均为 CLI/构建入口输出，其中 CodeGenTask `debug()` 判级见 [G2-09-04]）；test 代码不报。
- `@Inject private`：全仓 0 处，本范围复核为 0。

### 零发现维度说明

- **维度 15（类型安全与泛型使用）：零发现**。检查内容：(1) 全范围 raw type 扫描（`(Map)`/`(List)`/`(Set)` 等 cast）——nop-dataset/nop-markdown/nop-jpath/nop-record-mapping/nop-kernel-cli main 代码 0 命中；nop-xlang 命中集中于 `XDefRefResolver`（xdef 解析内部的可变结构搬运）与 `XLangSemantics`（动态求值的 Map/List 分派），两处均为共享前缀「常见误报校准」明确要求克制的动态边界，且有运行时类型检查兜底。(2) `@SuppressWarnings("unchecked")` 命中基本在 `_gen/` 生成代码（不审）。(3) `GeneratedClassBindingBinder` 的 `cause instanceof RuntimeException → throw (RuntimeException) cause` 重抛链保留 cause，无信息丢失。(4) `JsPromise.asFunction` 的函数适配 cast 前均有 instanceof 分派。未发现可导致运行时 ClassCastException 的非边界泛型误用，按「宁缺毋滥」不凑数。

## 发现

### [G2-09-01] nop-xlang main 代码中存在「伪错误码字符串嵌入裸异常」的半迁移模式（6 处）

- **文件**: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/initialize/RegisterModelDiscovery.java:287,299`；`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpl/impl/XplCompiler.java:307-308`；`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdsl/json/DeltaExtendsGenerator.java:43`；`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xmeta/xjava/JaninoParser.java:146`；`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/utils/EvalHelper.java:65`
- **证据片段**（RegisterModelDiscovery.java:282-300）:
  ```java
  IComponentTransformer<Object, Object> newTransformer(String className) {
      IClassModel classModel = ReflectionManager.instance().loadClassModel(className);
      if (IComponentTransformer.class.isAssignableFrom(classModel.getRawClass()))
          return (IComponentTransformer<Object, Object>) classModel.newInstance();

      throw new IllegalArgumentException("nop.err.core.invalid-transformer-type:" + className);
  }

  IResourceObjectLoader<Object> newLoader(String className, ...) {
      ...
      throw new IllegalArgumentException("nop.err.core.invalid-loader-type:" + className);
  }
  ```
  同模式位点：`XplCompiler.java:307` `throw new IllegalStateException("nop.err.xlang.no-default-compiler-for-outputMode:" + scope.getOutputMode() + ",node=" + node);`；`DeltaExtendsGenerator.java:43` `throw new IllegalArgumentException("nop.err.json.source-not-string:" + source);`；`JaninoParser.java:146` `throw new IllegalStateException("nop.err.janino.not-supported-type:" + type);`；`EvalHelper.java:65` `throw new UnsupportedOperationException("nop.eval.invalid-binary-operator:" + operator);`
- **严重程度**: P2
- **现状**: nop-xlang 是框架核心，按 `docs-for-ai/02-core-guides/error-handling.md` 两档策略必须走 `NopException + ErrorCode + .param()`。这 6 处把形如 `nop.err.xxx:` 的字符串拼进 `IllegalArgumentException/IllegalStateException/UnsupportedOperationException` 的 message，看起来像错误码但并非 `ErrorCode` 对象——无 `.param()` 结构化参数、无 i18n、上层无法按错误码编程消费。且这些伪错误码 ID（如 `nop.err.core.invalid-transformer-type`、`nop.err.json.source-not-string`）在 `XLangErrors`/`CoreErrors`/`ApiErrors` 中均无对应 `ErrorCode.define`（已 grep 核实），属于未完成的错误码迁移残留。
- **风险**: (1) 错误处理契约漂移：调用方（如 `initLoader` 的 `catch (NoClassDefFoundError | NopException e)`，RegisterModelDiscovery.java:254）只捕获 `NopException`，这些 IAE 会击穿 optional loader 的降级逻辑直接炸初始化；(2) XplCompiler 位点位于所有 XPL 编译路径（outputMode 无对应 handler 时触发），异常类型裸、无 loc/param，排障信息劣化；(3) 后续 AI/开发者会模仿该写法继续扩散。
- **建议**: 在 `XLangErrors` 中为 6 处补 `ErrorCode.define(...)` + ARG_* 参数常量，统一改为 `throw new NopException(ERR_...).param(...)`（EvalHelper 位点同时补 operator 上下文）；顺带核对 `initLoader/initTransformer` 的 catch 谓词覆盖新异常类型。
- **信心水平**: 确定
- **误报排除**: 不是「模块内部实现用英文字符串」的合法二档场景——这些位点显式伪装成错误码 ID（`nop.err.` 前缀 + 冒号拼接），表明作者本意就是 ErrorCode 模式；且 nop-xlang 是框架核心不是模块内部实现。
- **复核状态**: 未复核

### [G2-09-02] ObjMetaToXDef 变换不变量用裸 IllegalStateException，无错误码、无定位参数

- **文件**: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xmeta/impl/ObjMetaToXDef.java:63,82,138,185`
- **证据片段**（ObjMetaToXDef.java:60-66, 182-188）:
  ```java
  XDefNode node = toNode(objMeta.getRootSchema());
  if (node == null)
      throw new IllegalStateException("root schema transform to null node");
  ...
  case child: {
      XDefNode child = toNode(prop.getSchema());
      if (child == null)
          throw new IllegalStateException("prop schema transform to null node:" + prop.getName());
  ```
- **严重程度**: P3
- **现状**: ObjMeta→XDef 反向变换（register-model 中 xdef→xmeta transformer 的对偶路径，供工具链从 xmeta 生成 xdef）的 4 处不变量断言全部用裸 `IllegalStateException`，仅 1 处附带 prop 名，其余无任何上下文（哪个 objMeta、哪个 schema）。
- **风险**: 触发时（上游 schema 结构异常）只能靠堆栈定位，无法程序化区分错误类别；与同文件族 `XDefToObjMeta` 全量使用 `NopException + ErrorCode` 的风格不一致。
- **建议**: 迁移到 `XLangErrors` 的 ErrorCode + `.param(ARG_...)`；至少为 4 处补 objMeta/prop 定位参数。
- **信心水平**: 确定
- **误报排除**: 不是「不可能到达的防御分支」——toNode 对若干 schema kind（如 union 未命中）确实返回 null，输入来自用户提供的 xmeta 文件。
- **复核状态**: 未复核

### [G2-09-03] nop-jpath 错误码 ID 使用 `NOP_JQ-00N` 编号风格，偏离 `nop.err.[模块].[错误]` 命名约定

- **文件**: `nop-kernel/nop-jpath/src/main/java/io/nop/jpath/NopJqErrors.java:10-38`
- **证据片段**:
  ```java
  ErrorCode ERR_JQ_INVALID_PATH = define("NOP_JQ-001",
          "Invalid JsonPath expression: {path}");

  ErrorCode ERR_JQ_COMPILE_ERROR = define("NOP_JQ-002",
          "Failed to compile JsonPath expression: {expr}");
  ... // 共 8 个：NOP_JQ-001 ~ NOP_JQ-008
  ```
- **严重程度**: P3
- **现状**: `docs-for-ai/02-core-guides/error-handling.md` 规定错误码 ID 命名为 `nop.err.[模块].[子域].[错误]`。nop-kernel 内其余全部模块（nop-core/nop-xlang/nop-commons/nop-api-core/nop-dataset/nop-markdown/nop-codegen/nop-record-mapping/nop-javac/nop-antlr4，已逐一核实）均为 `nop.err.*` 风格；仅 nop-jpath（新迁入 nop-kernel 的模块）用 `NOP_JQ-00N`。
- **风险**: 错误码 ID 是持久化/日志标识，消费方（前端映射、告警规则）无法按统一前缀聚合 jq 路径错误；跨模块运维口径不一致。
- **建议**: 若 NOP_JQ-00N 尚无外部消费方，趁早重命名为 `nop.err.jq.*`（参考 nop-stream 全英文新模块族做法）；若已有消费方则登记为例外并在 owner doc 注明。
- **信心水平**: 确定
- **误报排除**: 不是 nop-metadata 式「连字符分隔」已登记例外——error-handling.md 的例外名单只有 nop-metadata 与 nop-ai/nop-stream 模块族，不含 nop-jpath。
- **复核状态**: 未复核

### [G2-09-04] CodeGenTask.debug() 全量打印系统属性与环境变量，触发门槛是「日志属性被设置」而非 debug 级别

- **文件**: `nop-kernel/nop-codegen/src/main/java/io/nop/codegen/task/CodeGenTask.java:120-148`
- **证据片段**:
  ```java
  static void debug() {
      System.out.println("========properties==========");
      for (Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
          System.out.println(entry.getKey() + "=" + entry.getValue());
      }
      System.out.println("==========env=========");
      for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
          System.out.println(entry.getKey() + "=" + entry.getValue());
      }
  }

  static LogLevel getLogLevel() {
      String level = System.getProperty("org.slf4j.simpleLogger.defaultLogLevel");
      if (level == null)
          return null;
      return LogLevel.fromText(level);
  }
  // main(): if (logLevel != null) debug();
  ```
- **严重程度**: P2
- **现状**: codegen 独立 main 入口在 `org.slf4j.simpleLogger.defaultLogLevel` 属性**存在**时（不比较级别——设为 `info`/`warn` 也会触发；该属性是 Maven/CI 调日志的常规配置项）就把全部系统属性与环境变量打印到 stdout。环境变量与 -D 属性中常含凭证（DB 密码、token、MAVEN_ARGS 等）。
- **风险**: CI 构建日志信息泄露：任何为排查而设置过 simplelogger 级别属性的环境，都会在下次 codegen 任务执行时把 secrets 明文落进日志；同时违反「用 SLF4J 不用 System.out」的仓库规范。
- **建议**: 删除 `debug()` 或改为按白名单 key 打印并经 `LOG.debug` 输出；门槛至少收紧为 `level == debug/trace` 判断。
- **信心水平**: 确定
- **误报排除**: 不是「CLI 工具写 stdout 是惯例」——常规进度输出（`skip CodeGenTask...` 等）属惯例可保留，本条针对的是全量 env/properties dump 的敏感信息泄露面与非级别判断的触发条件。
- **复核状态**: 未复核

### [G2-10-01] JsPromise 错误路径三处偏离 JS Promise 语义：executor 抛错永不 settle、rejection 被 then 解析为正常值、finally 回调抛错被吞

- **文件**: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/utils/JsPromise.java:33-39,70-92,101-128`（机制证据：`nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/ContinuationExecutor.java:58-66`，模块外仅作行为引用）
- **证据片段**（JsPromise.java）:
  ```java
  // (1) 构造：executor 抛错不会 completeExceptionally —— 任务被 ContinuationExecutor 吞掉
  public JsPromise(IEvalScope scope, BiFunction<JsPromise, JsPromise, Void> executor) {
      try {
          ContinuationExecutor.INSTANCE.execute(() -> executor.apply(this, this));
      } catch (Throwable e) {
          completeExceptionally(e);   // 只能捕获调度本身的错误，捕获不到任务内部抛错
      }
  }

  // (2) then 无 onRejected 时 identity 把 rejection reason 当正常值 complete
  BiFunction<Object, Throwable, Object> onR = onRejected == null
          ? (v, e) -> v
          : asFunction(scope, onRejected);
  ...
  Object result = onR.apply(reason, null);
  next.complete(result);   // JS 语义应保持 rejected 并向下游传播

  // (3) finally 回调抛错被静默吞掉
  } catch (Throwable e) {
      // ignore      // JS 语义：finally 中 throw 应使返回的 promise rejected
  }
  ```
  ContinuationExecutor.runLoop 机制：`try { tasks.pop().run(); } catch (Throwable e) { LOG.error(...); }` —— 任务内异常不会回传给 JsPromise。
- **严重程度**: P1
- **现状**: `JsPromise` 类 Javadoc 声明目标是「JavaScript Promise 全局对象兼容」，且经 `LexicalScopeAnalysis.java:143` 注册为 XScript 全局 `Promise`。但三条错误路径均偏离 JS 契约：(1) `new Promise(executor)` 中 executor 抛错 → promise 永不 settle（JS 应 auto-reject），下游 `.then/.catch` 链悬挂、`.get()` 永久阻塞，错误只在日志；(2) 已 reject 的 promise 接 `.then(onF)`（无第二参）→ 下游以 rejection reason 为**正常值** resolve（JS 应保持 rejected 透传），脚本拿到 Throwable/错误值继续算，产出静默错误结果；(3) `.finally(cb)` 中 cb 抛错 → 被吞，promise 维持原结局（JS 应转为 rejected）。
- **风险**: XScript 中所有用到 Promise 的异步编排，一旦进入错误路径就出现「挂死」或「错误被当数据」两类静默失效——比直接报错更难排查；同时 (3) 违反 error-handling.md「catch 后丢弃 throwable 前必须 rethrow with cause 或 LOG.warn 以上」的硬约束。测试（TestJsPromise 仅 5 个 happy path）恰好全部避开这三条路径，缺陷不会被现有测试捕获。
- **建议**: (1) 构造处把任务改为 `() -> { try { executor.apply(this, this); } catch (Throwable e) { completeExceptionally(e); } }`（或给 ContinuationExecutor 增加带异常回调的 execute 变体）；(2) err 分支在 onR 为 identity 时改走 `next.completeExceptionally(err)`；(3) finally 的 catch 改为 `next.completeExceptionally(e)`。三处各补一条对齐 JS 语义的回归测试。
- **信心水平**: 确定（行为均由代码直接推导，机制链已核对 ContinuationExecutor 实现）
- **误报排除**: 不是「平台有意简化」——类 Javadoc 明示 JS 兼容目标，且 `catch`/`finally`/microtask 队列等其余语义都认真对齐过；(2)/(3) 两处与 error-handling.md 的吞异常红线冲突，非设计自由度。
- **复核状态**: 未复核

### [G2-10-02] xdsl.xdef 声明的 `<x:prototye-super>` 为拼写错误，引擎实际使用 `x:prototype-super`

- **文件**: `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xdsl.xdef:78`（对照 `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdsl/XDslKeys.java:79`、`DeltaMerger.java:240-243`）
- **证据片段**:
  ```xml
  <!-- xdsl.xdef:75-80（DslNode 定义内） -->
  <x:gen-extends>xpl-node</x:gen-extends>

  <x:super xdef:internal="true"/>
  <x:prototye-super xdef:internal="true"/>   <!-- 拼写：prototye -->

  <xdef:unknown-tag xdef:ref="DslNode"/>
  ```
  ```java
  // XDslKeys.java:79 —— 引擎使用正确拼写
  this.PROTOTYPE_SUPER = getFullName(ns, "prototype-super");
  // DeltaMerger.getSuperNode 按 keys.PROTOTYPE_SUPER 查找 super 节点
  ```
- **严重程度**: P3
- **现状**: 平台最基础的元模型 `xdsl.xdef` 把原型合并的内部 super 节点声明为 `x:prototye-super`（少一个 t），而引擎（`XDslKeys.PROTOTYPE_SUPER`、`DeltaMerger.getSuperNode`、`normalizeMergeSuper`）生成与消费的都是 `x:prototype-super`。全仓 grep `prototye` 仅此一处。由于 DslNode 定义带 `xdef:unknown-tag`，正确的 `x:prototype-super` 落入 unknown-tag 分支不报错，运行时行为无恙。
- **风险**: schema 声明与引擎事实漂移：IDEA 插件/xdef 文档据此补全出错误元素名；xdef 声明了一个永不存在的元素、漏声明了真实元素，误导后续维护者在 xdef 层做相关扩展。
- **建议**: 将 xdsl.xdef:78 改为 `<x:prototype-super xdef:internal="true"/>`（xdef 内部元素，无实例文件引用，改名零迁移成本）。
- **信心水平**: 确定
- **误报排除**: 不是「历史约定的奇怪命名」——XDslKeys/OverrideHelper/DeltaMerger 全链路均为正确拼写 `prototype-super`，仅 xdef 声明侧拼错。
- **复核状态**: 未复核

### [G2-10-03] EvalBackendRouter 加载期与运行期的降级观测 reason tag 口径不一致，稀释文档化的指标契约

- **文件**: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/backend/EvalBackendRouter.java:158-163,207-212`（对照同文件 `:249-255` 与 `EvalBackendObservation.java:110-116`）
- **证据片段**:
  ```java
  // bindLoadedUnit / bindTagFunction（加载期）—— reason tag 带 detail 后缀
  if (!staticBackend.isAvailable()) {
      EvalBackendObservation.onDegradation(staticBackend.getBackendId(),
              EvalBackendObservation.REASON_UNAVAILABLE + ":" + staticBackend.getUnavailableReason(),
              resourcePath, tree.getLocation());
      ...
  // decide()（运行期）—— 同一语义事件用裸枚举值
  if (!staticBackend.isAvailable()) {
      EvalBackendObservation.onDegradation(staticBackend.getBackendId(),
              EvalBackendObservation.REASON_UNAVAILABLE, resourcePath, ...);
  ```
  `onDegradation → count(backendId, reason)` 直接把 reason 作为指标 tag 值。
- **严重程度**: P3
- **现状**: docs-for-ai 与 `EvalBackendObservation` Javadoc 将指标 reason tag 契约固定为枚举集合（`unavailable`/`config-disabled`/...）。加载期两个绑定入口却把 detail 拼进 tag（`unavailable:xxx`），运行期 `decide()` 用裸 `unavailable`——同一后端不可用事件按路径不同记成两种 tag 值。
- **风险**: 按 `degradationCount(backendId, "unavailable")` 或按文档枚举值做告警/看板会漏计加载期事件；detail 拼接进 tag 还引入基数不确定性。
- **建议**: 统一 tag 用裸 `REASON_UNAVAILABLE`，detail 放进 WARN 日志消息或独立 tag（如 `unavailableReason`），并补一条断言 tag 枚举值的观测测试。
- **信心水平**: 确定
- **误报排除**: 不是有意的「加载期更细粒度」设计——同文件 decide() 对同一信息采用了另一种拼法，两处口径自相矛盾才是问题本质。
- **复核状态**: 未复核

### [G2-16-01] MarkdownCodeBlockParser 无任何单元测试，而它处于 LLM 响应解析生产路径

- **文件**: `nop-kernel/nop-markdown/src/main/java/io/nop/markdown/simple/MarkdownCodeBlockParser.java`（消费方：`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/response/CodeResponseParser.java:23`、`nop-kernel/nop-record-mapping/src/main/java/io/nop/record_mapping/md/MappingBasedMarkdownParser.java`）
- **证据片段**:
  ```java
  // CodeResponseParser.java:23 —— AI 响应中抽取代码块
  return new MarkdownCodeBlockParser().parseCodeBlockForLang(null, content, lang);

  // MarkdownCodeBlockParser.java:106-123 —— 边界密集的解析核心（无测试）
  String endMark = "\n" + StringHelper.repeat("`", tickCount);
  int endPos = text.indexOf(endMark, langEnd);
  if (endPos < 0) { return null; }
  ...
  String lang = text.substring(markEnd, langEnd).trim();
  String code = text.substring(langEnd + 1, endPos).trim();   // trim 会吃掉代码首行缩进
  ```
- **严重程度**: P2
- **现状**: 全仓搜索（含 nop-kernel/nop-ai 各 test 目录）确认 `MarkdownCodeBlockParser` 没有任何直接单元测试；nop-markdown 现有 9 个测试类覆盖 Document/Section/Table/List 等，均不触及 code-block 解析。仅有 nop-ai-coder 的 `TestAiCoderHelper` 间接经过。而该解析器是 nop-ai-core 解析 LLM 返回 markdown 代码块的唯一入口，边界条件密集：多 tick 围栏（````）、行首判定（`findQuoteStart` 的 `\n```` 双路径）、未闭合块、CRLF、`code` 的 `trim()`（会剥掉代码块首行缩进——对 YAML/Python 类缩进敏感内容有真实保真风险）。
- **风险**: LLM 输出格式天然不稳定（围栏长度变化、前置文本、未闭合），解析器回归只会表现为 AI 工具静默取不到/取错代码；`parseCodeBlockForLang` 的 lang 匹配循环与 `getEndPos` 推进逻辑改动时无保护网。
- **建议**: 在 nop-markdown 增加表驱动单测：多 tick 围栏、块前有正文、lang 不匹配跳到下一块、未闭合返回 null、代码缩进保留性（先裁定 trim 是否预期行为，把现行行为或修正后行为都固化下来）。
- **信心水平**: 确定（测试缺失与调用链已核实；trim 影响为很可能）
- **误报排除**: 不是「快照测试已在别处覆盖」——nop-markdown 与 nop-ai-core 均无该类测试；间接经过的 TestAiCoderHelper 不覆盖上述边界。
- **复核状态**: 未复核

### [G2-16-02] TestJsPromise 仅覆盖 happy path，JsPromise 全部错误路径零测试（与 [G2-10-01] 直接耦合）

- **文件**: `nop-kernel/nop-xlang/src/test/java/io/nop/xlang/expr/TestJsPromise.java:17-40`（对照被测类 `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/utils/JsPromise.java`）
- **证据片段**:
  ```java
  @Test
  public void testPromiseResolve() {
      assertEquals(42, eval("Promise.resolve(42).get()"));
  }
  ...
  @Test
  public void testFinally() {
      assertEquals(1, eval("Promise.resolve(1).finally(() => 'cleanup').get()"));
  }
  // 全部 5 个用例均为 resolve 链 happy path，无一条 rejection 传播/executor 抛错/finally 抛错用例
  ```
- **严重程度**: P3
- **现状**: JsPromise 是 XScript 全局 `Promise` 兼容层，其错误路径（executor 抛错、rejection 经 then 透传、finally 回调抛错）恰是 [G2-10-01] 三处语义缺陷所在——现有 5 个用例全部从 resolve 出发，任何一条错误路径回归都无法被捕获。`catch` 用例只验证了「捕获后返回正常值」，未验证「未捕获 rejection 应继续传播」。
- **风险**: JS 兼容契约的错误路径无守护，重构 JsPromise（如换调度器）时静默破坏；这正是 P-3（只测 happy path）反模式的实例。
- **建议**: 补：`Promise.reject('x').then(v=>v+1)` 应保持 rejected（现在会错resolve）；executor 抛错后 `.get()` 应快速失败而非悬挂（需配超时断言）；finally 抛错应传播；rejection 未捕获经多级 then 链的传播。
- **信心水平**: 确定
- **误报排除**: 不是「AutoTest 快照已覆盖」——该测试类为普通 JUnit 断言，无快照附件覆盖错误路径。
- **复核状态**: 未复核

### [G2-16-03] record-mapping 的 Markdown DSL 解析/生成测试错放在 nop-kernel-cli 模块，按模块跑测试时回归信号丢失

- **文件**: `nop-kernel/nop-kernel-cli/src/test/java/io/nop/kernel/cli/TestMappingBasedMarkdownParser.java:26-43`（被测类在 `nop-kernel/nop-record-mapping/src/main/java/io/nop/record_mapping/md/`）
- **证据片段**:
  ```java
  package io.nop.kernel.cli;   // 测试位于 nop-kernel-cli

  import io.nop.record_mapping.IRecordMappingManager;
  import io.nop.record_mapping.impl.RecordMappingManagerImpl;
  import io.nop.record_mapping.md.MappingBasedMarkdownGenerator;
  import io.nop.record_mapping.model.RecordMappingConfig;
  ...
  public static File getVfsDir() {
      File projectDir = MavenDirHelper.projectDir(TestMappingBasedMarkdownParser.class);
      File vfsDir = new File(projectDir, "demo/_vfs");   // 依赖 nop-kernel-cli 的 demo/_vfs fixture
      return vfsDir;
  }
  ```
- **严重程度**: P3
- **现状**: `MappingBasedMarkdownParser/Generator` 是 nop-record-mapping 的核心 Markdown DSL 往返实现，其集成测试却放在 nop-kernel-cli（且依赖该模块 `demo/_vfs` fixture）。nop-record-mapping 自身 5 个测试类不含 markdown 往返用例。
- **风险**: `./mvnw test -pl nop-kernel/nop-record-mapping -am`（AGENTS.md 规定的单模块验证命令）不会执行该测试；改 record-mapping 的 markdown 解析只在偶然构建到 nop-kernel-cli 时才暴露回归；测试归属也误导后来者寻找 fixture。
- **建议**: 将该测试（连同所需 fixture）迁移到 nop-record-mapping/src/test；若 fixture 确需跨模块共享，说明 fixture 布置本身应上移。
- **信心水平**: 确定
- **误报排除**: 不是「必须依赖 kernel-cli 才能跑」——测试仅用 BaseTestCase/CoreInitialization/VFS 与 demo 资源，这些在 nop-record-mapping 测试作用域同样可用（其自身测试已用同套设施）。
- **复核状态**: 未复核

## 维度小结

| 维度 | 发现数 | P0 | P1 | P2 | P3 |
|------|-------|----|----|----|----|
| 09 错误处理与错误码 | 4 | 0 | 0 | 2 | 2 |
| 10 XDSL 与 XLang 正确性 | 3 | 0 | 1 | 0 | 2 |
| 15 类型安全与泛型使用 | 0（零发现说明见上） | 0 | 0 | 0 | 0 |
| 16 测试覆盖与质量 | 3 | 0 | 0 | 1 | 2 |
| **合计** | **10** | **0** | **1** | **3** | **6** |

整体观察：nop-xlang 引擎主干（XDslExtender/DeltaMerger/XDslValidator/XDefConstraintValidator）错误处理纪律良好（465 处 NopException 族、324 个 ErrorCode define，catch 均正确 wrap + param + 保 cause），xdef 资源完备性机械校验全部通过（enum 引用/默认值/xdef:ref 路径 100% 可解析）。主要缺陷集中在：错误码迁移残留（09-01/02）、JS Promise 兼容层的错误路径语义（10-01，本轮唯一 P1）与「生产路径解析器无测试」（16-01）。

## 子项复核结论

复核人：独立复核代理 R1（2026-09-30）

| 发现编号 | 判定 | 复核说明 |
|---|---|---|
| [G2-10-01] | 保留（维持 P1）| 逐行打开 `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/utils/JsPromise.java` 与 `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/ContinuationExecutor.java` 全文核对。三条错误路径全部属实：(1) 构造器 33-39 行的 `completeExceptionally` 只能捕获 `execute()` 调度本身抛错——任务 lambda 的异常在 `ContinuationExecutor.runLoop`（51-53 行 `catch (Throwable e) { LOG.error("nop.err.executor.run-continuation-fail", e) }`）被吞，promise 永不 settle，`.get()` 永久阻塞；(2) `thenJs` 72-74 行 `onRejected == null` 时 onR 为恒等 `(v,e)->v`，83-85 行 err 分支 `next.complete(reason)` 把 rejection reason 当正常值 resolve，下游 catch 永不触发；(3) `finallyDo` 117-119 行 `catch (Throwable e) { // ignore }` 后按原结局 complete，JS 语义应转 rejected。类 Javadoc（22 行）明示「JavaScript Promise 全局对象兼容」；全局注册点核实为 `nop-xlang/.../xlang/compile/LexicalScopeAnalysis.java:143`（JS_GLOBAL_VARS）与 :157（TYPE_NAME_ALIAS），报告所引行号准确。`TestJsPromise.java` 实读确认仅 5 个用例且全部从 resolve 出发（testCatch 也只验证捕获后返回正常值，未验证未捕获 rejection 传播）。一点范围备注：双参构造器（`new Promise(executor)` 入口）在仓内无调用方与测试，路径 (1) 的可达性依赖 XScript `new` 表达式对 (IEvalScope, BiFunction) 构造器的绑定；但路径 (2)/(3) 经 `Promise.resolve/reject` + `then/finally` 公开工厂直接可达，不影响判定。P1（框架核心公开契约漂移 + 静默错误结果）成立。 |
| [G2-09-01] | 保留（维持 P2）| 打开全部 6 个位点核对行号与原文：`RegisterModelDiscovery.java:287,299`、`XplCompiler.java:307-308`、`DeltaExtendsGenerator.java:43`、`JaninoParser.java:146`、`EvalHelper.java:65`，逐处均为伪错误码字符串拼接进裸 IAE/ISE/UnsupportedOperationException，与引文逐字一致。grep `XLangErrors`/nop-core errors 包确认 6 个伪码 ID 均无 `ErrorCode.define`。`RegisterModelDiscovery.initLoader`（250-262 行）实读确认 catch 谓词为 `NoClassDefFoundError \| NopException`，IAE 击穿 optional loader 降级逻辑的风险成立。P2 合理（触发前提是配置错误，但属框架核心契约漂移残留）。 |
| [G2-09-04] | 保留（维持 P2）| 打开 `nop-kernel/nop-codegen/src/main/java/io/nop/codegen/task/CodeGenTask.java` 120-148 行核对：`debug()` 全量打印 `System.getProperties()` + `System.getenv()` 到 stdout；`getLogLevel()` 仅判断 `org.slf4j.simpleLogger.defaultLogLevel` 属性**存在**（`fromText` 可解析即返回非 null），`main()` 147-148 行 `if (logLevel != null) debug()` 不比较级别——设为 info/warn 同样触发，报告的触发条件描述准确。构建期 secrets 落日志的风险与 P2 判级成立（CLI/构建入口、需属性存在才触发，非运行时常态暴露）。 |
| [G2-16-01] | 保留（维持 P2）| `find` 列举 nop-markdown 全部 9 个测试文件（TestMarkdownDocument/Section/TableHelper、MarkdownHelperTest、MarkdownSectionTest、MarkdownListParserTest、MarkdownDocumentParserTest、MarkdownTableParserTest、TestTableViewToMarkdownTableConverter），无一触及 code-block 解析；全仓 grep `MarkdownCodeBlockParser` 仅 3 个 main 引用 + 1 个测试间接引用（nop-ai-coder TestAiCoderHelper）。消费方 `nop-ai/nop-ai-core/.../CodeResponseParser.java` 23 行 `new MarkdownCodeBlockParser().parseCodeBlockForLang(...)` 实读确认；被测类 106-123 行（endMark 定位、`code = text.substring(langEnd + 1, endPos).trim()`）与引文一致。生产路径解析器零直接测试，P2 成立。 |
