# G10: nop-lint/refactor/jq 深度审计（首轮）

- **审计日期**: 2026-09-30
- **审计子代理**: G10 首轮初审（维度 01 / 09 / 15 / 21）
- **审计范围**: nop-lint/ 全部（core/java/js/nop/graphql/maven-plugin）、nop-refactor/ 全部（core/java/graphql）、nop-jq/，live code，纯静态审计（未运行 mvn/test）

## 审计范围

### 覆盖的模块与文件

- **nop-lint-core**（126 个 main 类，深读）：`SourcePatternCompiler`、`PatternMatcher`（匹配内核全文）、`MetaVarEnv`/`MetaVarSyntax`/`MetaVarNode`、`CompiledRule`（全文）、`RuleDslParser`（全文）、`RuleSetRunner`（全文）、`LintEngine`（全文）、`LanguageRegistry`（全文）、`EditPlanApplier`（全文）、`Fixer`、`BaselineEngine`、`RuleResultCache`、`NopLintCli`、`NopLintLanguageServer`、`XScriptCompiler`、`LintDeadlineExecutor`、`RuleTestRunner`、`NopLintException`
- **nop-lint-java**：依赖面 + `JavaSuppressWarningsProvider` 抽样（main 代码 0 处裸异常）
- **nop-lint-nop**：main 仅 `package-info.java` + 70 个 `.rule.yml`；测试侧 `TestNopRuleSuites`、`TestProductionRuleCount`（全文）
- **nop-lint-js**：`NodeTscBridge`（全文）、`TscProtocol`、模块结构
- **nop-lint-graphql**：`NopLintBizModel`（全文）、beans 配置、pom
- **nop-lint-maven-plugin**：pom + 依赖面
- **nop-refactor-core**：`RefactorOperationRunner`（全文）、`RewriteOperation`（全文）、`RenameOperation`（全文）、`RefactorVerifier`（全文）、`RefactorRuleGates`（全文）、`SymbolResolverAdapter`（契约面）
- **nop-refactor-java**：`JavaSymbolResolverAdapter` 结构（1619 行，方法面）+ 依赖面
- **nop-refactor-graphql**：`NopRefactorBizModel`（全文）、`app-refactor.beans.xml`
- **nop-jq**：`JqEngine`、`JqDirectQuery`（全文）、`JqRuntimeException`、`JqParser` 错误路径、测试全套（`TestJqOfficial` 全文、`JqOfficialCase` 全文、`jq-official.test` 2117 行）

### 基线校正（重要，修正主 agent 的 grep 线索）

1. **裸异常 103 处口径**：按 `throw new (RuntimeException|IllegalArgumentException|IllegalStateException|UnsupportedOperationException)` 重新统计，`main` 与 `test` 必须分开：
   - nop-lint main = **25**（全部集中在 nop-lint-core），test = **104**（core 72 + nop 17 + java 5 + 其他）
   - nop-refactor main = **0**；nop-jq main = **3**（`JqExecutor` 两处 unreachable-switch `IllegalStateException`、`JqValue` 一处工厂 IAE）
   - 即 main 代码合计 **28 处**，逐个判级见 [G10-09-01]；test 代码按口径不报或最多 P3，不单独立条。
2. **System.out/printStackTrace 129 处口径**：`main` 代码 **0 处** `System.out`/`System.err`；5 处 `printStackTrace` 中 3 处在 CLI/LSP launcher 的**系统边界**（`NopLintCli.java:145`、`NopLintLspLauncher.java:72`、`NopRefactorCli.java:98`，符合 error-handling.md 的边界 catch 豁免），另 2 处是 lint-nop 的**测试夹具文件**（`print-stack-trace/invalid/basic.java`，本身就是规则测试的违规样本）。129 处几乎全部位于 `src/test`（142 处 System.out 全在 test）——不构成立案对象。
3. **@Inject private：全仓 0 处**，本组核实无误（`NopRefactorBizModel.renameResolver` 为 `protected`，合规）。
4. **未提交改动线索勘误**：任务提示称 git 工作区有涉及 CompiledRule/Diagnostic/RuleSetRunner/TestFixEngineWiring 的未提交改动；`git status`/`git diff HEAD` 核实 **nop-lint/nop-refactor/nop-jq 下无任何未提交改动**（工作区改动全部位于 nop-stream/nop-code）。本审计对象为已提交的 live code。
5. **jq 430/430 语义基线核验**：`nop-jq/src/test/resources/io/nop/jq/jq-official.test`（2117 行）真实在仓。按 `JqOfficialCase.parse` 语义模拟计数：**421 个 ok 用例 + 9 个 %%FAIL 用例 = 430 个执行用例**，另有 17 个块被跳过（module-dependent `import/include/modulemeta` + `%%FAIL IGNORE`，解析器显式设计并有 javadoc 说明）。测试装配为 `@TestFactory` 动态测试，**失败路径真实断言**（编译失败/运行错误/输出不匹配均 fail，不多输出容忍仅限官方 runner 的前缀比较语义）。430/430 宣称与仓内事实一致。

### 零发现维度说明

- **维度 01（依赖图与模块边界）：依赖图全绿，无违规边**。完整依赖图：
  ```
  nop-jq ──────────────→ nop-core, nop-commons                       （独立，符合 module-groups 定位）
  nop-lint-core ───────→ nop-treesitter, nop-xlang
  nop-lint-java ───────→ nop-lint-core, nop-java-parser
  nop-lint-js ─────────→ nop-lint-core
  nop-lint-nop ────────→ nop-lint-core (main)；nop-lint-java 仅 test（规则库为纯 YAML，经 ServiceLoader 装配）
  nop-lint-graphql ────→ nop-lint-core, nop-graphql-core, nop-ioc（lint-java/lint-nop 仅 test）
  nop-lint-maven-plugin → lint-core + lint-java + lint-nop（显式装配三件套，与 CLI javadoc 的 classpath 契约一致）
  nop-refactor-core ───→ nop-lint-core（main）+ nop-lint-java（test）
  nop-refactor-java ───→ nop-refactor-core, nop-java-parser, nop-lint-java
  nop-refactor-graphql ─→ nop-refactor-core, nop-refactor-java(runtime), nop-graphql-core, nop-ioc
  ```
  无循环依赖；无 core→dao 类反向边；refactor 对 lint 公开 API 的消费边（`cli.RuleSetLoader/TargetScanner`、`engine.*`、`fix.*`、`suppress.ExemptionFilter`、`rule.RuleDslModel`）全部为 public 契约；`nop-refactor-graphql` 对 `nop-refactor-java` 的 runtime scope 与 beans.xml 按类名装配（`app-refactor.beans.xml`）匹配，BizModel 侧对未装配 bean 有 fail-closed 预检（`NopRefactorBizModel.java:203-208`）。分层规则 1-10 逐条对照无违规。仅存一条 P3 消费面观察（G10-01-01）。已核对无手写代码引用 `_` 前缀生成物（本组三模块族无 dao/meta 生成链路，`_vfs` 下为手写规则资源）。

- **维度 09 的正面结论**：`NopLintException`/`NopRefactorException` 均按两档策略的模式二提供 `(String)`/`(String,Throwable)` 双构造器并继承 `NopException`（`NopLintException.java`），main 代码 `throw new NopLintException` **300 处**；错误消息全部英文；SLF4J 全覆盖（main 零 System.out）；资源关闭走原子写 + finally 清理并 `LOG.warn(cleanupFailure)`（`EditPlanApplier.atomicWrite`），未发现吞异常点（catch 全部 rethrow-with-cause 或 LOG.warn(e) 末参）。bare-IAE/ISE 见 G10-09-01，属局部口径偏离而非系统性反模式。

### 执行说明

- 纯静态审计，未运行 mvn/test；机械基线由主 agent grep 提供，main/test 口径由本报告重新核实并校正（见上）。
- 遵守硬性约束：未读 ai-dev/audits、plans、bugs、lessons（方法论文件 deep-audit-prompts.md 与 unit-test-antipatterns.md 位于 ai-dev/skills/，属任务指定的必读方法论）。

## 发现

### [G10-15-01] 裸元变量 `$`/`$$` 在匹配内核中永远不匹配，与分类器的"非捕获可匹配"契约矛盾

- **文件**: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/pattern/PatternMatcher.java:83-90,164-183`（对照 `MetaVarSyntax.java:87-94`、`MetaVarEnv.java:31-44`）
- **证据片段**:
  ```java
  // PatternMatcher.matchRoot / step —— bare $ (SINGLE) 与 bare $$ (ANONYMOUS) 的 name 均为 null
  case SINGLE -> candidate.isNamed() && env.insert(metaVar.name(), candidate);
  ...
  case ANONYMOUS:
      return env.insert(metaVar.name(), candidate) ? Step.MATCHED : Step.FAIL;
  // MetaVarEnv.insert：name == null 直接返回 false
  public boolean insert(String name, LintNode node) {
      if (name == null || node == null) {
          return false;
      }
  ```
- **严重程度**: P2
- **现状**: `MetaVarSyntax.parse` 的 javadoc 明确声明"bare forms `$`、`$$`、`$$$`、`$_` are accepted as **non-capturing** meta-variables"（`MetaVarSyntax.java:19-22`，且 `SourcePatternCompilerTest.bareFormsAreNonCapturing` 断言了分类结果）。但匹配内核把"非捕获"实现为 `env.insert(null, node)`，而 `insert` 对 null 名一律返回 false——于是 bare `$`（SINGLE）与 bare `$$`（ANONYMOUS）在 matchRoot 与 step 两条路径上都必然返回"不匹配"。只有 bare `$$$`（MULTI，走 `insertMulti` 的 null-name 静默消费）和 `$_`（DROP，直接 `return true`）真正可匹配。
- **风险**: 含 bare `$`/`$$` token 的 pattern 可正常编译（`$`、`$$` 是合法 Java 标识符，tree-sitter 会产出对应叶子节点），但规则**静默地永远零命中**——这正是本模块在 regex matcher、stopBy 默认值等处反复用 fail-closed 拒绝规避的失效模式，属于规则库"永不报警且无任何可观测信号"的最坏失败形态。当前 70 条生产规则未使用 bare 形式，故暂无实际误报/漏报；风险随 DSL 用户面扩大而放大。
- **建议**: 二选一：(a) 在 `PatternMatcher` 中对 `name == null` 的 SINGLE/ANONYMOUS 走 DROP 语义（匹配不捕获）；(b) 在 `SourcePatternCompiler.convertNamed` 编译期拒绝 bare `$`/`$$`（与 `rejectReserved` 同法，fail-closed）。同时补一条端到端 matcher 级测试——现有测试只覆盖了分类器（`MetaVarSyntax.parse`），未覆盖匹配行为（命中 P-3/P-8：分类断言无法保护匹配语义）。
- **信心水平**: 确定（代码路径完整走查：MetaVarSyntax 产出 null name → MetaVarNode(shape, null, text) → PatternMatcher 两个消费点 → MetaVarEnv.insert null 守卫）
- **误报排除**: 不是"非捕获 = 不插环境"的有意设计——javadoc 与测试名 `bareFormsAreNonCapturing` 都声称"接受为非捕获元变量"，`$_`/`$$$` 两条 bare 路径也确实匹配，只有这两条断裂；也非 tree-sitter 层不可达（`$`/`$$` 为合法 Java 标识符 token）。
- **复核状态**: 未复核

### [G10-15-02] 规则级 `files:` include/exclude 与 `options:`/`settings:` 声明面被解析、被 xdef 声明，但引擎零消费——静默失效的 DSL 表面

- **文件**: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/rule/RuleDslModel.java:38-93,182,639-658`（解析：`RuleDslParser.java:175-176,907-913,915-930`；xdef 声明：`nop-lint/nop-lint-core/src/main/resources/_vfs/nop/lint/schema/lint-rule.xdef:165`）
- **证据片段**:
  ```xml
  <!-- lint-rule.xdef:165 —— 元模型把 files 声明为合法规则字段 -->
  <files include="csv-set" exclude="csv-set"/>
  ```
  ```java
  // RuleDslModel.Files javadoc："Include globs; never null, empty means include everything."
  // 全 main 代码 grep：getFiles()/getOptions()/getSettings() 除 RuleDslModel 自身外零调用点
  ```
- **严重程度**: P2
- **现状**: 规则作者可以写 `files: {include: [...], exclude: [...]}` 与 `options:`/`settings:` 块，xdef 结构校验通过、`RuleDslParser` 解析入模、`RuleDslModel` 携带——但 `LintEngine`/`RuleSetRunner`/`TargetScanner`/`CheckRunner` 无任何一处读取这些字段做文件过滤或参数注入。对比：同一引擎对"当前不支持的能力"的既定方针是**编译期拒绝**（regex matcher 直接 reject、XML 路径上的 fix/transform/constraints 逐项 fail-closed 拒绝，`CompiledRule.compile:194-215`），唯独这三个字段被接受却静默忽略。
- **风险**: 第三方规则（或未来生产规则）声明 `files: {include: "**/dao/**"}` 后，规则实际作用于**所有**扫描文件——声明与行为静默背离，产生跨文件误报且无任何告警；`options/settings` 同理是"看似可配置实则无效"的假配置面。与模块自身"an unsupported form fails loudly ... never being skipped silently"（`CompiledRule` javadoc）的既定纪律直接冲突。
- **建议**: 短期在 `CompiledRule.compileTreeSitter` 或 `RuleDslParser` 对非空 `files.include/exclude`、非空 `options/settings` 显式抛 `NopLintException("... not enforced by this engine version (fail-closed)")`（照抄 regexRejected 的措辞结构）；中期在 `RuleSetRunner`/`TargetScanner` 真正实现 per-rule 文件过滤。当前 70 条生产规则均未使用这三个字段（grep 零命中），故修复无迁移成本。
- **信心水平**: 确定（消费者搜索覆盖全部 main 代码：`getFiles|getOptions|getSettings` 除模型自身外零命中；生产规则库 grep `^files:|^options:|^settings:` 零命中）
- **误报排除**: 不是"预留字段"惯例——本模块对同类预留面（regex、`$@` 元变量、XML 路径 constraints）全部采用编译期显式拒绝而非静默携带；`RuleDslModel.Files` 的 javadoc 还写出了"empty means include everything"的过滤语义，说明它被当作将生效的契约在维护。
- **复核状态**: 未复核

### [G10-09-01] main 代码 28 处裸 IAE/ISE 绕过模块异常类，其中一部分位于规则编写者可触发的输入边界

- **文件**: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/pattern/AllMatcher.java:19`、`AnyMatcher.java:20`、`NotMatcher.java:18`、`fix/FixApplier.java:94`、`suppress/BaselineFile.java:44`、`node/LintTree.java:44-59`、`node/LineIndex.java:59,83`、`node/SourcePositions.java:23`、`xscript/SourceMap.java:27,60`、`constraint/ConstraintContext.java:32-34`、`cli/FileFindings.java:20`、`cli/FileDiff.java:15` 等（全量 25 处清单见审计过程；另 nop-jq 3 处）
- **证据片段**:
  ```java
  // AllMatcher.java:19 —— pattern 编译期可由 YAML 规则输入触发
  throw new IllegalArgumentException("all matcher requires at least one child");
  // FixApplier.java:94 —— --fix 调用方可触发
  throw new IllegalArgumentException("maxPasses must be at least 1: " + maxPasses);
  // BaselineFile.java:44
  throw new IllegalArgumentException("baseline count must be >= 1: " + count);
  ```
- **严重程度**: P3
- **现状**: 模块已按两档策略提供 `NopLintException extends NopException`（双构造器，main 300 处使用），但仍有 28 处 main 抛 JDK 裸异常。逐个判级：约半数是纯内部不变量/unreachable 分支（`RuleResultCache`/`BaselineEngine` 的 SHA-256 不可用、`JqExecutor:697,1016` 的 switch default、`XNodePatternMatcher:110`），可接受；但 `AllMatcher/AnyMatcher/NotMatcher`（编译 `all: []` 类规则的输入）、`FixApplier.maxPasses`（CLI 参数）、`BaselineFile` 计数（baseline 文件内容）、`LineIndex/SourcePositions/SourceMap` 的 byteOffset 越界（可由外部源码触发的计算路径）属于**规则编写者/使用者可触发的错误面**，会以裸 `IllegalArgumentException` 而非 `NopLintException` 抵达 CLI/GraphQL/LSP 边界。
- **风险**: 边界层（`NopLintBizModel` 测试按 message-mode 异常断言；LSP `onMessage` 虽有 `catch(Exception)` 兜底）仍能把错误转成响应，但错误类型面分裂：同样的"规则写错"有的报 `NopLintException`、有的报 IAE，妨碍程序化消费方按异常类型分流，也绕开了未来按 ErrorCode 演进的出口。
- **建议**: 把"规则/用户输入可触发"的 IAE 收敛为 `NopLintException`（消息原样保留）；纯 JVM 不变量类（SHA-256、unreachable default）可保留 ISE 但建议加注释标注口径。nop-jq 3 处维持现状即可（见 G10-09-02）。
- **信心水平**: 确定（28 处全部逐条人工看过上下文，非 grep 计数直接立案）
- **误报排除**: 不按"裸异常=违规"机械判级：JDK 标准前置条件异常在模块内部实现层是可辩护的惯例，这里立案的只是"输入边界 + 已有模块异常类却不用"的口径分裂部分；test 代码 104 处按审计口径不立案。
- **复核状态**: 未复核

### [G10-09-02] nop-jq 缺少模式二的模块异常族：编译期解析错误与运行期控制流共用裸 `RuntimeException` 子类

- **文件**: `nop-jq/src/main/java/io/nop/jq/runtime/JqRuntimeException.java:9`、`nop-jq/src/main/java/io/nop/jq/JqEngine.java:26-32`
- **证据片段**:
  ```java
  // JqRuntimeException —— 直接继承 RuntimeException，未继承 NopException
  public class JqRuntimeException extends RuntimeException {
      private final JqValue errorValue;
  ...
  // JqParser —— 编译期语法错误同样走 error(...) = JqRuntimeException
  throw error("Unexpected token: " + peek().getText());
  ```
- **严重程度**: P3
- **现状**: nop-jq 全模块没有任何继承 `NopException` 的异常类。`JqRuntimeException` 承担两种角色：(1) jq 语言级 error 值的控制流载体（`error()` 内建、try/catch 捕获——这是 jq 语义的一部分，消费方 `JqToolExecutor.java:61-67` 按具体类型捕获并转为工具结果，**这个用法是正确的领域设计**）；(2) 编译期语法错误（`JqEngine.compile` 失败）也走同一裸异常。运行期角色不应改，但编译期错误跨模块边界（nop-ai-toolkit → AI agent）传递时只能靠具体类型匹配。
- **风险**: 按 error-handling.md 模式二"每个模块定义一个继承 NopException 的异常类"，jq 的结构化错误响应/i18n 出口缺失；若未来其它消费方按 `NopException` 家族做统一兜底，jq 的编译错误会被归入"未知异常"分支。
- **建议**: 保持 `JqRuntimeException`（运行期控制流）不动；为编译期（`JqLexer`/`JqParser`/`JqEngine.compile`）引入 `NopJqException extends NopException`（或复用消息模式），仅迁移语法/编译错误。430/430 官方套件的 %%FAIL 断言按 `Exception` 粒度断言，不受影响。
- **信心水平**: 很可能（角色二的迁移价值明确；角色一保留的理由已写明，避免复核时误改成全量迁移）
- **误报排除**: 不是"所有 jq 错误都该继承 NopException"的机械适用——jq 的 try/catch 把 error 当**值**处理（`catch` 分支接收 `errorValue()`），把控制流异常改成 NopException 反而破坏领域语义；立案点仅在编译期错误的边界形态。
- **复核状态**: 未复核

### [G10-09-03] rename 面批量读文件的错误消息引用错变量：读的是 `moduleFile`，报的却是 rename 目标 `target`

- **文件**: `nop-refactor/nop-refactor-graphql/src/main/java/io/nop/refactor/graphql/NopRefactorBizModel.java:250-260`
- **证据片段**:
  ```java
  for (TargetFile moduleFile : targets) {
      byte[] original;
      try {
          long size = Files.size(moduleFile.path());
          checkPreReadCap(size, moduleFile.path().toString());
          original = Files.readAllBytes(moduleFile.path());
      } catch (IOException e) {
          throw new NopRefactorException("target '" + target.path()          // ← 应为 moduleFile.path()
                  + "' is not readable (fail-closed, the run aborts rather than "
                  + "silently skipping a rewrite target): " + e.getMessage(), e);
      }
  ```
- **严重程度**: P3
- **现状**: 从 `rewrite()` 的同构循环复制而来（rewrite 循环变量恰好叫 `target`），rename 循环里变量已改名 `moduleFile`，catch 消息未跟随。两个后果：offset 形式下，模块内**任意一个**其它文件读失败时，消息指向的是 rename 目标文件；FQN 形式下 `target.path()` 为 null，消息直接输出 `target 'null' is not readable`。同方法内紧随其后的 post-read cap 检查（第 262 行）用的是正确的 `moduleFile.path()`，可交叉印证这是漏改。
- **风险**: 运维/agent 按报错路径排查时会去检查一个本身可读的文件，真实的不可读文件反而被隐藏；`null` 消息则完全丢失定位信息。属诊断面缺陷，不影响 fail-closed 语义本身（仍然中止、不静默跳过）。
- **建议**: `target.path()` → `moduleFile.path()`；顺带把消息里的 "rewrite target" 措辞改为与 rename 上下文一致。补一条 FQN 形式 + 模块文件不可读的单测断言消息内容。
- **信心水平**: 确定
- **误报排除**: 不是编译错误（`SymbolTarget.path()` 存在，正常编译），纯粹是消息正确性缺陷；同文件 262 行证明作者知道正确变量名。
- **复核状态**: 未复核

### [G10-15-03] 规则 YAML 的 `metadata.source` 标量输入触发裸 `ClassCastException`，违背解析器自身的 fail-closed 条例

- **文件**: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/rule/RuleDslParser.java:893-898`
- **证据片段**:
  ```java
  Collection<?> source = (Collection<?>) metadata.get("source");   // 标量直接 CCE
  List<String> sources = new ArrayList<>();
  if (source != null) {
      for (Object item : source)
          sources.add(String.valueOf(item));
  }
  ```
- **严重程度**: P3
- **现状**: `RuleDslParser` 的类契约是"no validation failure is silently dropped"，全文件对每种畸形输入都抛携带规则 id 的 `NopLintException`。唯独 `metadata.source` 字段：规则作者写 `source: my-rule-id`（标量而非列表）时，强转在解析期抛裸 `ClassCastException`，不携带规则 id，也不走 `NopLintException`。
- **风险**: 规则加载失败的诊断质量骤降（无规则 id、无字段名、异常类型是实现细节），与本文件其余 60+ 处校验的报错口径不一致。
- **建议**: `instanceof Collection` 判定 + 非列表时抛 `NopLintException("Rule '...' declares metadata 'source' as a non-list value (each source is one list entry; fail-closed)")`，与 `parseConstraints:686-688` 对 `constraints` 非列表的处理完全同构。
- **信心水平**: 确定（同文件已有现成的同构处理可对照）
- **误报排除**: 不是"YAML 层会先拦"——xdef 对该字段的类型约束不足以拦截标量字符串；也不是不可达路径：`source` 是 metadata 的可选自由字段，第三方规则完全可能写标量。
- **复核状态**: 未复核

### [G10-01-01] nop-refactor-core 的 main 代码跨模块消费 nop-lint-core 的 `cli` 包，包名与契约定位不符

- **文件**: `nop-refactor/nop-refactor-core/src/main/java/io/nop/refactor/core/RefactorRuleGates.java:3`、`operation/RewriteOperation.java`（import `io.nop.lint.core.cli.RuleSetLoader/TargetScanner`）
- **证据片段**:
  ```java
  import io.nop.lint.core.cli.RuleSetLoader;   // RefactorRuleGates.verifyRewriteRuleset 的参数类型
  import io.nop.lint.core.cli.TargetScanner;   // NopLintBizModel/NopRefactorBizModel 同样消费
  ```
- **严重程度**: P3
- **现状**: `RuleSetLoader`/`TargetScanner` 名义上是 CLI 内部件（包名 `cli`），实际已成为跨模块公共 API：nop-refactor-core 的 operation 契约（`RefactorRuleGates.verifyRewriteRuleset(LoadedRuleSet,...)`）、nop-lint-graphql 与 nop-refactor-graphql 两个 BizModel 都以它们为类型面。依赖方向本身合规（无反向、无循环），问题是**契约定位**：`cli` 包内的类一旦为 refactor 面演进而改签名，波及面是三个模块而非一个 CLI。
- **风险**: 维护者按包名判断影响范围时低估破坏面；后续若重构 CLI（如替换 options 解析）容易连带破坏 refactor 契约。
- **建议**: 将 `RuleSetLoader`/`TargetScanner`/`LoadedRuleSet` 迁至 `io.nop.lint.core.rule` 或新增 `io.nop.lint.core.ruleset` 包（保留旧位置 thin delegate 一个版本周期）；或最低成本在两类 javadoc 标注"跨模块公共契约，变更需三模块联审"。
- **信心水平**: 很可能
- **误报排除**: 不是"Maven 依赖方向违规"（方向正确），也不违反"cli 不许被依赖"之类成文规则——立案点是命名揭示的契约错位，属真实维护成本（P3 上限）。
- **复核状态**: 未复核

### [G10-01-02] `LanguageRegistry` 以裸 `HashMap` 承载已发布读（GraphQL 逐请求 resolve），`register` 无任何并发防护

- **文件**: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/engine/LanguageRegistry.java:30,61-77`
- **证据片段**:
  ```java
  private final Map<String, LintLanguage> languages = new HashMap<>();
  ...
  public void register(LintLanguage language) {   // public 可变入口，无 synchronized/volatile
      ...
      languages.put(normalized, language);
  }
  ```
- **严重程度**: P3
- **现状**: `NopLintBizModel` 把 registry 构建后长期持有并逐请求 `resolve`（engine 路径读 HashMap）；`register` 是公开的写入口（javadoc 明确供 embedders/tests 使用）。构建后注册 → 并发读的场景下，HashMap 扩容/树化无 happens-before 保证。当前所有仓内调用点恰好是"先 build 后冻结"（`discoverDefaults()` 单线程构建、测试 `register` 在请求前），未触发。
- **风险**: 依赖隐式纪律而非类型系统保证的线程安全；任何"运行期补注册语言绑定"的合理演进（如热插拔 ts 桥接）都会在 `resolve` 读侧暴露为丢读/死循环级故障。
- **建议**: `HashMap` → `ConcurrentHashMap`（一处改动，零行为差异），或在 javadoc 写明 freeze-after-build 契约并校验。
- **信心水平**: 很可能（危害是条件性的，但修复成本近乎为零）
- **误报排除**: 不是"所有 HashMap 都要并发化"的机械适用——立案点是同一对象上公开写入口与跨线程读并存的事实组合，非纯风格。
- **复核状态**: 未复核

### [G10-01-03] rename/rewrite 变更面每个 GraphQL 请求重复执行 ServiceLoader 发现，与 Lint 面的一次装配契约不一致

- **文件**: `nop-refactor/nop-refactor-graphql/src/main/java/io/nop/refactor/graphql/NopRefactorBizModel.java:122,210`
- **证据片段**:
  ```java
  // rewrite(...) 与 rename(...) 每次请求：
  LanguageRegistry registry = LanguageRegistry.discoverDefaults();
  ```
- **严重程度**: P3
- **现状**: `NopLintBizModel` 用 double-checked `volatile Runtime` 把 registry + 编译产物冻结为单例（javadoc：plan Decision 6，逐请求 YAML 重扫会吃掉 fast profile 延迟预算）；`NopRefactorBizModel` 的两个 mutation 每请求调用 `discoverDefaults()`（ServiceLoader 全扫）+ `new LintEngine(...)`。ruleset 按请求重载是 refactor 面无状态契约的**有意设计**（支持任意 rulesetPrefix），但语言发现与之无关，属可冻结部分。
- **风险**: 高频 mutation 路径上每次请求付出 ServiceLoader 扫描 + 语言绑定重建；两个 GraphQL 面对同一"引擎装配"问题采用相反策略，后续维护者难以判断哪个是范本。
- **建议**: registry 发现结果按类加载器缓存（`LanguageRegistry` 自带按 loader 的 memo，或静态 volatile 持有）；engine 因携带 ruleset 无关配置，同样可单例化（`LintEngine(registry, STANDARD)` 无状态）。
- **信心水平**: 很可能
- **误报排除**: 不是"每请求重载规则集"本身——那是无状态契约的一部分；立案点仅是语言发现这一可缓存分量。
- **复核状态**: 未复核

### [G10-15-04] LSP/缓存/tsc 协议层对外部 JSON 输入的 unchecked 强转——现状全部被边界 catch 兜住，登记为受控观察项

- **文件**: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/lsp/NopLintLanguageServer.java:102-107,148-152,163-174`、`cli/RuleResultCache.java:95,125-132,210-212`、`nop-lint-js/src/main/java/io/nop/lint/js/tsc/TscProtocol.java:131-137`
- **证据片段**:
  ```java
  Map<String, Object> params = (Map<String, Object>) message.getOrDefault("params", Map.of());
  ...
  String uri = (String) ((Map<String, Object>) params.get("textDocument")).get("uri");
  ```
- **严重程度**: P3
- **现状**: 三处 `@SuppressWarnings("unchecked")` 均作用于外部输入（LSP JSON-RPC 帧、缓存 JSON 工件、tsc 线协议帧）。逐一核过逃逸路径：LSP 的 CCE 被 `onMessage` 的 `catch (Exception)` 捕获并转为 error response（notification 则 rethrow 由 transport 报 stderr，`NopLintLanguageServer.java:133-141`）；`RuleResultCache` 的强转对象来自 `JsonTool.parse` 后的 shape 校验链（version/runFingerprint 先行校验，畸形时 fail-closed `NopLintException`）；TscProtocol 有 `BAD_FRAME` 结构化包装。**当前无一可致进程级故障**。
- **风险**: 类型噪声集中在系统边界，后续在这些路径上新增字段访问时容易复制强转而不复制兜底；`RuleResultCache.get` 的 `files(artifact)` 内层结构强转若未来缓存由外部工具生成，可能漏出 `ClassCastException` 而非"corrupt cache"诊断。
- **建议**: 维持现状可接受；若触碰这些文件，优先把 `(Map<String,Object>)` 收敛为 `instanceof Map<?,?>` + 单点 `corrupt(...)` 工厂（`RuleResultCache` 已有该工厂，成本极低）。
- **信心水平**: 确定（逃逸路径逐条走过）
- **误报排除**: 不按"存在 unchecked cast 即立案"口径——三条路径的兜底边界均已核实存在，故只登记 P3 观察项而非缺陷条目。
- **复核状态**: 未复核

### [G10-21-01] rewrite 面目录展开在目标数上限检查之前无界进行，且 rewrite 不去重输入路径（rename 有去重，同一裁定两个面不一致）

- **文件**: `nop-refactor/nop-refactor-graphql/src/main/java/io/nop/refactor/graphql/NopRefactorBizModel.java:136-140,225-228,395-405`
- **证据片段**:
  ```java
  List<TargetFile> targets = collectTargets(input.getPaths(), nonApplies, null);
  if (targets.size() > CFG_MAX_TARGET_FILES.get()) {   // 上限在全量收集之后才检查
  ...
  // collectTargets 内：
  try (Stream<Path> walk = Files.walk(real)) {          // 目录递归无深度/数量界
      walk.filter(Files::isRegularFile).forEach(child ->
              classifyFile(child, targets, skipped, languageFilter));
  ```
- **严重程度**: P3
- **现状**: 写面的 512 文件上限（`nop.refactor.graphql.max-target-files`）在**收集完成后**才生效；传一个宽目录（如工作区根）会先把整棵树 walk 完并物化成 `TargetFile` 列表，再被 cap 拒绝——上限保护的是"读与改"，不保护"枚举"本身。另外 rename 面按 plan 12 裁定 5 对重复路径去重（225-228 行，javadoc 写明"double-indexed file would drift the symbol index"），rewrite 面对同一输入形状不去重——重复路径会重复 lint、重复进 plan，同一文件在 `landed`/`EditedFile`/stats 中双计（写结果幂等，无正确性损害，但 stats 与 nonApply 枚举失真）。
- **风险**: 认证用户传大目录导致瞬时内存/CPU 放大（Path 对象级，非文件内容）；rewrite 统计面重复计数误导 dryRun 审阅。
- **建议**: `collectTargets` 循环内每轮（及 walk 的 forEach 内）检查 `targets.size()` 超限即抛/短路；把 rename 的去重逻辑上提为两个面共用的收集后处理（javadoc 已给出同一裁定依据）。
- **信心水平**: 很可能
- **误报排除**: 不是"cap 应该前置到读之前"的重复立案——cap 对读确实已前置（`checkPreReadCap` 在 `Files.readAllBytes` 之前），立案点是枚举阶段的自身无界与两面的去重不一致。
- **复核状态**: 未复核

### [G10-21-02] `CompiledRule` 三处残留的双份 javadoc 块（编辑残留），同一方法/内部类挂着两个 `/** */`

- **文件**: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/engine/CompiledRule.java:160-179（compile 方法）、520-536（compileNodeMatcher）、659-668（flatBranchMatcher）`
- **证据片段**:
  ```java
      /**
       * Compiles a rule model for {@code language}.
       * @throws NopLintException when the rule uses a regex matcher ...
       */
      /**
       * Compiles a rule model for {@code language}. The compiled form is
       * file- and run-independent by construction (plan 10 compile-reuse) ...
       */
      public static CompiledRule compile(RuleDslModel model, LintLanguage language) {
  ```
- **严重程度**: P3
- **现状**: 三个声明各带两个连续 javadoc 块——旧版本描述与新版本描述并存（新块在前者之后），javadoc 工具只取最后一块，前一块成为死注释。内容上看新块是修订版，但旧块中仍有个别独有信息（如第一处的 `@throws` 全列表只存在于旧块）。
- **风险**: 读者无法判断哪份契约有效；后续修改若只改其一即产生文档漂移。纯文档问题，不影响行为。
- **建议**: 每处保留信息更全的一块（compile 建议合并旧块的 `@throws` 明细进新块），删除另一块。
- **信心水平**: 确定
- **误报排除**: 不是"注释多"的风格偏好——是同一声明的两个互斥版本并存的事实性编辑残留。
- **复核状态**: 未复核

## 维度正面的量化备注（非发现）

1. **测试有效性总体结论（维度 21）**：本组 145 个测试类整体保护力强。亮点：(a) jq 官方套件 430 用例真实在仓且断言语义严格（`TestJqOfficial` 无 lenient skip，`%%FAIL` 必须抛错）；(b) nop-lint-nop 的规则库采用"真实引擎 + valid/invalid 夹具 + `.expect` 逐条断言 + 套件集合 fail-closed 断言 + 70 规则 census 精确清单"四层防护，P-1~P-8 反模式总体检出率低（`assertNotNull` 密度：core 72/806、java 9/104、jq 0/131，未见纯 getter 往返测试与枚举计数测试）；(c) `TestProductionRuleCount` 的常量镜像属于 census 类测试的合法形态（清单漂移正是它要捕获的对象）。
2. **工程纪律亮点**：规则 DSL 解析器全字段 fail-closed 校验（含 util 引用图环检测、typeOf×L2 门）、`Fixer.merge` 单点冲突权威、`EditPlanApplier` 原子写 + 重解析守卫回滚、预算降梯与熔断全程可观测（每个被跳过的对象都有计数器与 ruleId 记录）。这类纪律水平在全仓属于第一梯队。

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| G10-15-01 | P2 | pattern/PatternMatcher.java | bare `$`/`$$` 元变量永远不匹配，与分类器契约矛盾且无 matcher 级测试 |
| G10-15-02 | P2 | rule/RuleDslModel.java | 规则级 `files:`/`options:`/`settings:` 被解析但引擎零消费，静默失效 DSL 面 |
| G10-09-01 | P3 | pattern/AllMatcher.java 等 25 处 | main 28 处裸 IAE/ISE 绕过 NopLintException，部分位于用户可触发输入边界 |
| G10-09-02 | P3 | jq/runtime/JqRuntimeException.java | nop-jq 缺模块异常族，编译期错误与运行期控制流共用裸 RuntimeException |
| G10-09-03 | P3 | refactor-graphql/NopRefactorBizModel.java:257 | rename 批量读错误消息引用错变量（`target.path()` 应为 `moduleFile.path()`） |
| G10-15-03 | P3 | rule/RuleDslParser.java:893 | metadata.source 标量输入触发裸 CCE，违背解析器 fail-closed 条例 |
| G10-01-01 | P3 | refactor-core/RefactorRuleGates.java:3 | refactor main 消费 lint 的 cli 包，包名与跨模块契约定位不符 |
| G10-01-02 | P3 | engine/LanguageRegistry.java:30 | 已发布读 + 公开写入口落在裸 HashMap 上，无并发防护 |
| G10-01-03 | P3 | refactor-graphql/NopRefactorBizModel.java:122,210 | 每请求 ServiceLoader 重扫，与 Lint 面一次装配契约不一致 |
| G10-15-04 | P3 | lsp/cli/tsc 三处 | 外部 JSON unchecked 强转（边界 catch 均已兜住），登记为受控观察项 |
| G10-21-01 | P3 | refactor-graphql/NopRefactorBizModel.java:136,395 | 目录展开先于文件数上限无界进行；rewrite 不去重与 rename 不一致 |
| G10-21-02 | P3 | engine/CompiledRule.java:160,520,659 | 三处双份 javadoc 编辑残留 |

**统计**: P0=0，P1=0，P2=2，P3=10（共 12 条）

## 子项复核结论

复核人：独立复核代理 R4（2026-09-30）

| 发现编号 | 判定 | 复核说明 |
|---|---|---|
| [G10-15-01] | 保留（维持 P2）| 全链路独立重推成立：`MetaVarSyntax.parse`（`MetaVarSyntax.java:69-106`）对 bare `$`/`$$`/`$$$` 返回 `Spec(shape, null)`（:87-91），javadoc :19-22 明文"bare forms are accepted as non-capturing meta-variables"；`SourcePatternCompiler.convertNamed`（`SourcePatternCompiler.java:159-163`）对 parse 非空的叶子生成 `MetaVarNode(shape, null, text)`——bare 形式编译为 null 名元变量而非字面量；`PatternMatcher` 两个消费点 `matchRoot`（:85-86：SINGLE 要求 `candidate.isNamed() && env.insert(name,...)`、ANONYMOUS 直接 `env.insert(name,...)`）与 step（:171-177）在 name==null 时全部 FAIL；`MetaVarEnv.insert`（`MetaVarEnv.java:31-34`）对 `name == null` 一律返回 false。对照路径核实：bare `$$$`（MULTI）在 `PatternMatcher.java:122-123` 被截流进 `matchEllipsis`，经 `env.insertMulti`（`MetaVarEnv.java:52-55`）对 null 名静默消费可匹配；`$_`（DROP）:87/:179 直接返回匹配——只有 `$`/`$$` 两条断裂，与报告完全一致。可达性核实：`$`/`$$` 是合法 Java 标识符，tree-sitter 层可产出具名叶子节点，路径可达；现有测试 `SourcePatternCompilerTest.bareFormsAreNonCapturing`（:58-64）只断言分类结果，未覆盖匹配行为，测试缺口说法属实。编译通过但规则静默零命中、无任何观测信号的失效形态，P2 恰当。|
| [G10-15-02] | 保留（维持 P2）| 双向独立 grep 验证：(a) 消费侧——`getFiles()/getOptions()/getSettings()` 在 nop-lint + nop-refactor 全部 main 代码除 `RuleDslModel.java` 自身（:167/:174/:182 getter，字段 :35-38，Files 内部类 :638）外零调用点（grep 命中的 `getFilesScanned`/`getFilesDegraded` 为 RunSummary 无关方法，refactor 侧 `plan.files()`/`input.files()` 亦无关）；`CompiledRule.java` 不触碰这三个字段。(b) 声明侧——`lint-rule.xdef:165` 确有 `<files include="csv-set" exclude="csv-set"/>`，`RuleDslParser.java:175-176` 解析 options/settings、:908-912 解析 files 入模。(c) 规则库侧——`^files:|^options:|^settings:` 在 nop-lint-nop 生产规则零命中，修复无迁移成本。"xdef 声明 + 解析入模 + 模型携带 + 引擎零消费"的静默失效链完整成立；与 `CompiledRule` javadoc 自述的 fail-closed 纪律（unsupported form 必须显式拒绝）相矛盾的说法与代码事实相符。当前无生产规则受影响的潜伏性缺陷，P2 恰当。|
