# Nop Lint — 执行引擎与平台集成

> 日期: 2026-09-19（修订 2026-09-20）· 状态: 设计草案（索引见 [00-nop-lint-design.md](./00-nop-lint-design.md)）

## 1. 执行引擎

### 1.1 单文件执行流程

```
Source File
  ↓ (nop-treesitter TSParser.parse)
CST (Concrete Syntax Tree) → LintNode 门面
  ↓ (per rule)
CompiledRule.matcher.match(lintRoot, source)      // SourcePattern：语言在编译期绑定
  ↓
Match[] { node, captures, range }
  ↓ (per match)
Constraint.evaluate(matchContext)                 // item 22 已交付（见下方核对结论）
  ↓
xscript.executeMatch(matchContext)                // 可选，Phase 1
  ↓
抑制判定（注释/@SuppressWarnings/exemption/baseline，见 09）
  ↓
Diagnostic { ruleId, severity, message, range, fix? }
```

> **v1 落地裁定（2026-09-22，item 17）**：抑制判定实现为**管线尾部**（`LintEngine.lint` 在 `RuleSetRunner` 全部规则跑完之后、诊断出口之前构造 `SuppressionFilter` 一次性判定全部候选诊断）。与图示 per-match 位置语义等价：匹配内核对抑制无感知，判定单调（命中任一有效 span 即移除），前后置关系（xscript 后、输出前）保持。组合：内联注释扫描（`CommentSuppressionScanner`，恒开）+ 语言注解 provider（`LintLanguage.suppressionProvider()`，Java = `@SuppressWarnings`，缺省 null）。可观测性：`LintStats.suppressedDiagnostics` 计数被移除的候选诊断（不静默）；`diagnostics` 计数在抑制尾部调和为最终输出数（存活候选 + 元诊断）；FAST/STANDARD 两档一致生效（design 11 §2，抑制不按档位裁剪）。v1 落地层 = 注释 + 注解；exemption/baseline 归 item 27。

> **XML/XNode 路径接线裁定（2026-09-22，item 21，plan 2026-09-22-1045-2）**：`language: XML` 规则走**同一条执行管线**，注入点 = 规则编译入口的 binding 钩子 `LintLanguage.compileRule(RuleDslModel)`（default null → 既有 tree-sitter 编译矩阵；`XmlLanguage` 覆写返回 `XmlRuleCompiler` 的产物，经 `CompiledRule.precompiled` 装配）。裁定依据：(a) **零平台修改**——XNode 解析器只读消费（nop-core/nop-xlang/nop-treesitter 零改动）；(b) **计数零旁路**——下游（`KindIndex` kind 过滤 → 匹配 → xscript → 抑制尾 → `LintStats`）对 XML 规则零分支，每条规则恰一可观测出口（executed / skippedByProfile / kindFiltered）不变；`Diagnostic`/`LintStats`/`SuppressionFilter` 全量复用，无新计数口径；(c) XML 绑定无 tree-sitter 后端——`treeSitter()` 返回 null（接口契约增注），`parseIncremental` fail-closed，解析经 `XmlSourceParser`（facade 树 `LintTree.ofFacade`）；(d) xdef 零变更（language 枚举已含 XML）。XML 规则的 xscript 与内联注释抑制骑同一管线：注释经 facade `#comment` trivia 直接进入既有 `CommentSuppressionScanner`，注解 provider 恒 null（XML 无注解载体）。

> **约束求值管线序核对结论（2026-09-22，item 22，plan 2026-09-22-1045-3）**：上图 `Constraint.evaluate(matchContext)` 位置与 live 实现一致——约束过滤在 `RuleSetRunner.run` 内、**匹配之后、xscript 之前**（`applyConstraints`：per-match 独立求值，全部约束成立才保留；被过滤 match 计入 `LintStats.constraintFilteredMatches`，不静默丢弃，且绝不进入 xscript —— 测试断言 `xscriptMatchesExecuted=0` 钉死该序）。求值输入 = match 的 captures（`MetaVarEnv`）+ 匹配节点，即 `ConstraintContext`；约束编译期 capture 校验失败抛 `NopLintException`（含规则 id + 约束名 + capture 名）；求值期异常向上传播（过滤器结果 vs 求值缺陷分离，不吞异常）。约束失败的规则在 match 全被滤空时跳过后续诊断路径（`continue`），与 kind 过滤同形。约束对 xscript 规则同样生效（同位置，xscript 前）。

### 1.2 多文件执行（增量模式）

> **API 约束**：nop-treesitter 的增量入口是 `TSParser.parseIncremental(language, oldTree, edits, newSource)`（`newSource` 为 `byte[]`）；`TSTree` 上没有 `edit()` 方法，编辑列表由调用方计算。
>
> **EditCalculator 粒度裁定（2026-09-22，item 16）**：diff 输出为**最小化多 hunk 序列**（`io.nop.lint.core.lang.EditCalculator.diff`）。做法：字节级公共前后缀剥离 → 变更中段按行 Myers diff 分组 → 每组再收缩到字节级。**拒绝单一合并 hunk**：增量解析只能在编辑区域外复用旧叶节点，合并 hunk 会让相距较远的小改动强制重 lex 两者之间的整个 span，直接摧毁增量复用率（编辑器 keystroke 场景的常态，design 11 §6）；多 hunk 的代价仅是变更中段上的 O((N+M)·D) Myers 一趟。正确性与分解方式无关——后端保证任何合法、不重叠的编辑序列产物与全量 parse 逐字节一致。
>
> **TSInputEdit 坐标契约（2026-09-22，item 16 执行期裁定）**：`newEndByte` 采用 **锚定语义**（`startByte + 插入长度`；删除时 `newEndByte == startByte`），不是绝对 new 源偏移。依据：`TSInputEdit` record 的 javadoc 契约与构造校验（`newEndByte >= startByte`），以及后端 `ReuseCursor.mapOldToNew` 按每 hunk `newEndByte - oldEndByte` 折叠旧→新坐标位移——锚定语义下该差值恰等于 hunk 的局部 delta（插入 − 删除），绝对 new 坐标则会在净删除后的 hunk 上既违反构造校验又污染后续 hunk 的复用映射。当某 hunk 锚定后越出 new 源边界（仅在大额净删除之后发生）时，该 hunk **向后合并**进前一 hunk 直至合法：合并 span 的总 delta 与逐 hunk delta 之和精确相等，映射不差分毫，仅牺牲合并区间内的复用率；首个 hunk 起点处（公共前后缀剥离点）锚定终点恒等于绝对 new 终点，合并必然终止。

#### 增量解析集成落点（2026-09-22，item 16）

> **落点裁定**：增量入口挂在 `LintLanguage.parseIncremental(LintTree oldTree, byte[] newSource)`（`TreeSitterLanguageAdapter` 实现），与既有 `parse` 入口同层对称。理由：(a) adapter 持有 `parseIncremental` 要求的同一 `Language` 实例（后端按引用相等校验），调用方无需接触 Language 装配；(b) `EditCalculator.diff` 的调用被封在该入口内，编辑序列真实流经 `TSParser.parseIncremental`；(c) engine 层入口会把解析关注点混入规则执行层，`LintTree` 装配层则需要另持 Language——均拒绝。契约：`oldTree == null` 时**显式回退全量解析**（文档化分支，测试以 reuse 计数断言路径，非静默）；`oldTree` 与绑定实例不匹配、diff/解析失败一律 fail-closed 抛 `NopLintException`。等价 oracle：`TSTreeCursor` 全遍历比较 type/startByte/endByte 前序序列（测试域 `TreeEquivalenceOracle`），正确性门禁为真实 Java 语料 × 编辑矩阵 + 种子化随机编辑（≥100 实例）上增量产物 ≡ 全量产物。

```java
public class LintEngine {
    // 增量模式：复用已解析的 CST
    public LintReport lintFiles(FileFilter filter, boolean incremental) {
        Map<String, TSTree> cache = incremental ? loadCache() : emptyMap();

        return parallelFiles(filter, file -> {
            TSTree tree = cache.computeIfAbsent(file.path(), p -> parse(p));
            return lintTree(tree, loadedRules);
        });
    }

    // 增量解析：需要新增 diff → TSInputEdit 计算（交付项，见 08 Phase 1/4）
    private TSTree incrementalParse(String file, String oldSource, String newSource) {
        TSTree old = cache.get(file);
        if (old != null) {
            List<TSInputEdit> edits = EditCalculator.diff(oldSource, newSource);  // 新建组件
            return parser.parseIncremental(language, old, edits, newSource);
        }
        return parser.parse(newSource);
    }
}
```

### 1.3 性能优化

> 性能决策的权威来源是 `11-performance-profiles.md`（执行档位/成本模型/缓存/降级阶梯）；本表仅为索引。

| 策略 | 实现 | 收益 |
|------|------|------|
| **Kind 过滤** | 规则编译时提取目标 kind 集合（any→并集/all→交集），位图判断 | O(1) 过滤 |
| **增量解析** | TSParser.parseIncremental + EditCalculator | 编辑器集成场景 |
| **并行文件** | Java 21 VirtualThreads / CompletableFuture | 多核利用 |
| **规则排序** | 按选择性排序，高选择性规则先执行 | 减少无效匹配 |
| **缓存编译** | CompiledRule 序列化缓存 | 重复加载加速 |

## 2. 与 Nop 平台集成

> **LSP 编辑器面落地增注（2026-09-24，roadmap item 41，plan 2026-09-24-2330-1，live 以源码为准）**：LSP 服务器 v1 落地于 `nop-lint-core` `lsp` 包（`NopLintLanguageServer` 传输无关核心 + `NopLintLspLauncher` stdio Content-Length 帧入口）。能力面 v1：initialize（full-document sync）/ didOpen / didChange（全文本 resync 经 **parseIncremental 复用旧树**——item 16 面）/ didClose / shutdown+exit；诊断经 `textDocument/publishDiagnostics` 推送（LSP 0-based line + UTF-16 character——引擎 1-based line + UTF-8 byte 的双轴换算在 `position()`）。装配 = item 38 同款懒单例（discoverDefaults + 规则集一次加载 + **FAST 档钉死**，design 11 fast 预算）；未注册语言/未打开文档的 didChange 显式失败（fail-closed）。依赖形态：lsp4j 不在平台依赖树——**手写 Content-Length 帧循环 + 平台 JsonTool 序列化**（spike 裁定；协议面仅一种请求/响应 + 一种通知，手写面可控）。客户端对接：任意 LSP 客户端以 `java -cp <core+java+nop+treesitter> io.nop.lint.core.lsp.NopLintLspLauncher` 启动即可（VS Code LSP client / Neovim `lspconfig` 均按通用 LSP over stdio 配置）。range sync / codeAction / 插件打包留候选池。

### 2.1 Maven 插件（统一名称：nop-lint-maven-plugin）

> **v1 落地增注（2026-09-24，roadmap item 37，plan 2026-09-24-1500-1，live 以源码为准）**：`nop-lint-maven-plugin` 已落地（`nop-lint/nop-lint-maven-plugin`，goal `check`、@Mojo defaultPhase=VALIDATE、goalPrefix `nop-lint`）。与下方示例的差异裁定：(a) groupId 为 `io.github.entropy-cloud`（仓库惯例），goal 经 `<executions><execution><goals><goal>check</goal>` 声明绑定（无 execution 的裸 `<plugin>` 声明不触发）；(b) 规则面参数为 `rulesPrefix`（classpath-VFS 前缀扫描，默认 `/nop/lint/rules/`——示例的 `rulesPath` classpath 路径形态被 R1 1.10 拒绝，与 CLI rulesPrefix 参数对齐）；(c) 参数面 = targets（默认 `${project.basedir}/src`，多 target 列表）/ profile / failOnError / skip / fix / fixDryRun / baselineFile / baselineApply / baselineCheck / writeBaseline——**互斥与取值规则全部复用 `CliOptions.parse`（Mojo 只拼 CLI 参数语法，零新解析语义）**；(d) 失败映射矩阵：error 诊断或 baseline-check stale → failOnError=true 时 MojoFailureException（消息带 rule id 集与计数）/false 时 warn 日志；管线内部错误 → MojoExecutionException / error 日志；writeBaseline 恒成功（CLI exit-0 同义）；(e) 目标语义：显式 target 不存在 = MojoExecutionException（CLI exit-2 同义）；默认 src/ 缺失 = warn + 静默跳过（reactor 无源模块开箱即绿的 Mojo 适配，与示例"默认扫描"语义的显式偏离）；(f) 生命周期：每次 execute 一对 `CoreInitialization.initializeTo(REGISTER_COMPONENT)`/destroy（已初始化则跳过，CLI 同型）；(g) 依赖形态：插件 realm 类加载器看不到用户工程依赖——插件自身依赖 nop-lint-core/java/nop（R1 1.1 裁定，"用户工程自带"形态被拒）；(h) 输出：ConsoleReporter 经行缓冲桥接 Maven log（机器可读格式归 CLI 通道 item 39）。端到端实证：fixture 工程 `./mvnw validate`（违规→BUILD FAILURE 带 rule id/计数、干净→SUCCESS、`-Dnoplint.failOnError=false`→warn+SUCCESS）。

```xml
<plugin>
    <groupId>io.nop</groupId>
    <artifactId>nop-lint-maven-plugin</artifactId>
    <executions>
        <execution>
            <phase>validate</phase>
            <goals><goal>check</goal></goals>
        </execution>
    </executions>
    <configuration>
        <rulesPath>classpath:nop-lint-nop/rules/</rulesPath>
        <failOnError>true</failOnError>
    </configuration>
</plugin>
```

### 2.2 IoC Bean 注册

```xml
<bean id="nopLintEngine" class="io.nop.lint.core.LintEngine">
    <property name="ruleLoaders">
        <list>
            <ref bean="classpathRuleLoader"/>
            <ref bean="vfsRuleLoader"/>
        </list>
    </property>
</bean>

<bean id="nopLintService" class="io.nop.lint.nop.NopLintService">
    <property name="engine" ref="nopLintEngine"/>
</bean>
```

### 2.3 GraphQL API

> **v1 落地增注（2026-09-24，roadmap item 38，plan 2026-09-24-1530-1，live 以源码为准）**：`nop-lint-graphql` 模块落地（服务形态 = `TreeSitterBizModel` 全链路先例：`@BizModel("Lint")` + `@BizQuery` + `@Optional @Name` 可选参数 + beans 资源 `app-lint.beans.xml`（模块 resources 的 VFS 布局 `nop/lint/beans/` 目录）+ `_module` 空标记文件）。与下方草图的差异裁定：(a) 暴露名 = **`Lint__checkSource`/`Lint__checkFile`/`Lint__listRules`**（平台 `BizObj__action` 大写惯例，`GraphQLNameHelper` `OBJ_ACTION_SEPARATOR`；本节草图的 `lint__` 小写不成立）；(b) 返回面 = `LintCheckResult`（diagnostics + severity 计数）/`List<LintRuleView>`（id/severity/message/category/autoFixable）——**顶层 record DTO**（GraphQL 反射按 `g_<fqcn>` 派生对象类型名，嵌套 record 类型名推导不可用）；severity 取 RuleDslModel 顶层槽位（诊断载荷权威），metadata null 给安全默认；**无 fix 载体**（Decision 4）。(c) 安全边界 fail-closed：source 上限 `nop.lint.graphql.max-source-size`（AppConfig.varRef，默认 1MB，**checkFile 读入内容同受约束**）；未知规则 id 报错含 loaded 全集、未知 language 走 registry fail-closed；**checkFile 路径三分法**——namespace 前缀路径（首段含 `:`，如 `file:`/`cls:`）显式拒绝（`FileNamespaceHandler` 无默认 allowlist，放行 = 任意文件读）、`/` 开头纯 VFS 路径经 VFS 解析、其余磁盘路径 `toRealPath()` 后必须落在工作目录内。错误面 = message-mode `NopLintException`（GraphQL 响应把 message 映射到 `code` 槽位）。(d) 生命周期 = 懒初始化单例（registry + FAST 引擎 + 规则集启动后加载一次缓存；每次请求重扫 62 个 YAML 占主导 fast 档预算；规则变更需重启）。(e) 运行时模块集声明：应用需同时部署 nop-lint-java/nop-lint-nop（缺一 fail-closed）。端到端实证：GraphQLEngine RPC 真调三 query（容器装配 + 类型解析 + 字段选择 + 结构化错误全真）。

```graphql
type Query {
    lint__checkSource(
        source: String!
        language: String!
        rules: [String!]
    ): LintResult!

    lint__checkFile(
        path: String!
        rules: [String!]
    ): LintResult!

    lint__listRules(language: String): [LintRuleMeta!]!
}

type LintResult {
    diagnostics: [Diagnostic!]!
    fixes: [Fix!]!
    stats: LintStats!
}

type Diagnostic {
    ruleId: String!
    severity: DiagnosticSeverity!
    message: String!
    range: SourceRange!
    fix: Fix
}
```

**安全与资源边界**：
- 入参只接受**规则 id**（不接受裸 pattern 文本），可执行范围限于 VFS 内已部署的受信规则集；规则自身的防线见 11 §3/§5（xscript deadline、pattern 编译期复杂度检查）
- `lint__checkSource` 的 source 大小上限（默认 1MB，可配）与并发配额按档位（fast）约束；超限返回明确错误码而非超时
- 写类操作（baseline 生成等）不暴露 GraphQL，仅 CLI/Maven 通道

### 2.4 CI 集成与输出格式

```yaml
# GitHub Actions
- name: Nop Lint Check
  run: ./mvnw nop-lint:check -pl nop-auth,nop-purchase
```

**输出格式**（CLI `--format`，Phase 4）：

| 格式 | 用途 |
|------|------|
| `sarif` | GitHub Code Scanning / 主流平台 |
| `checkstyle-xml` | 迁移期对接现有 CI 面板 |
| `json` | 工具链集成 |
| `junit-xml` | CI 报表 |
| `console`（默认） | 人类可读 |

**退出码**：`0` 无违规；`1` 存在 error 级违规（或超过 `--max-warnings`）；`2` 内部错误（解析失败/规则崩溃）。

> **v1 最小 CLI 落地裁定（2026-09-22，item 18，plan 2026-09-22-0544-1）**：
>
> - **落点与发行形态**：CLI main 类 `io.nop.lint.core.cli.NopLintCli` 落在 `nop-lint-core`（不新建薄 launcher 模块；Maven 插件形态归 item 37，CLI 完整化归 item 39）。发行 = 普通 `java -cp` classpath 装配：运行 classpath 需同时含 nop-lint-java（ServiceLoader 语言绑定）、nop-lint-nop（`/nop/lint/rules/` 规则资源）与 nop-treesitter（native 库随 jar）。core → nop-lint-nop / nop-lint-java 保持**零编译期依赖**：语言经 ServiceLoader 发现（`LanguageRegistry.discoverDefaults`），规则经 classpath VFS 加载。
> - **v1 参数面**：`nop-lint check <path>... [--profile fast|standard]`（缺省 `standard`）。`--max-warnings`、`--rules` 覆盖、`--format`、match/test 子命令全部不在 v1——未知选项 fail-closed 报错退出 2，防手误参数静默落空。
> - **规则源**：默认 classpath VFS 前缀 `/nop/lint/rules/` 递归扫描 `*.rule.yml`（v1 无显式规则路径参数；内部 API 可注入前缀供测试）。前缀下零规则文件 = 部署错误退出 2；规则语言在 registry 查无绑定退出 2（消息含规则 id）——绝不以缩减规则集静默运行。
> - **扩展名绑定约定（v1）**：文件扩展名（去点、小写）== 注册语言 id（如 `.java` ↔ `java`）；无法绑定的文件（含无扩展名）按扩展名分桶显式计数进 skipped 汇总，不静默忽略。目标路径不存在 = 输入错误退出 2。多扩展名映射（ts/tsx）随 item 19 的语言模块引入。
> - **扩展名表落地形态（2026-09-22，item 19，plan 2026-09-22-1045-1，替换上行 v1 约定的前瞻注记）**：`TargetScanner` 持有显式扩展名→语言 id 表（`java→java`、`ts→typescript`、`tsx→tsx`；大小写不敏感——扩展名先小写再查表），替换 v1 的 `extension == language id` 约定。**表归属裁定：静态 map 于 `TargetScanner`**（扩展名绑定是 CLI 扫描关注点；`LanguageRegistry` 保持纯 id→绑定解析器），并暴露双向查询 `languageIdForExtension` / `extensionsForLanguage` 使两个消费方向永不漂移。文件 lintable 条件 = 扩展名命中表 **且** 映射 id 已注册；未命中表的扩展名（含 `.mts`/`.cts`——**v1 裁定不入表**，避免与 `.ts` 主表语义分叉，二者按扩展名分桶进 skipped summary 显式计数）与表内语言未注册的文件同样进 skipped 分桶。RuleTester 套件夹具扩展名同源于该表反查（java→`*.java`、typescript→`*.ts`、tsx→`*.tsx`）；规则语言无表项 = 套件显式失败（fail-closed，非静默空跑）。其余 v1 语义（目标路径不存在 = 退出 2、诊断行/汇总格式、退出码三态）不变。
> - **XML 家族扩展名落地（2026-09-22，item 21，plan 2026-09-22-1045-2，对上行表的追加条目）**：表追加 `xml→xml` 与 `xbiz→xml`（后者覆盖平台 XML 族模型 `.xbiz` 的主资源形态；`.orm.xml` 经 `.xml` 尾缀天然命中）。RuleTester 夹具扩展名反查随之（xml → `*.xbiz`/`*.xml`），`TestXmlCliWiring`/`TestTargetScanner` 双向断言钉死。
> - **console 行格式（默认输出，v1 唯一格式）**：诊断行 `<path>:<line>[-<endLine>]: <severity>: <ruleId>: <message>`——行号换算与 §4.2 RuleTester 同口径（`LineIndex`：startByte 与 endByte−1 各自落行，1-based；`endLine > line` 时以 `line-endLine` 区间形式呈现；文件按字典序、诊断按引擎序稳定输出）。汇总段：scanned/skipped 文件计数（skipped 按扩展名分桶）+ error/warning/info/hint/other 诊断计数 + `LintStats` 关键计数——`rules loaded`（规则集大小，报告一次）/ `executed` / `skippedByProfile`（含 ids）/ `kindFiltered`（跨文件求和，ids 取并集）、`suppressedDiagnostics`、`disabledRuleIds`、xscript 四计数（executed/failed/capped/timedOut）——延续"不静默跳过"硬约束的可观测口径。
> - **退出码三态（v1 落地）**：`0` 无 error 级诊断；`1` 存在 error 级诊断（`--max-warnings` 未进 v1）；`2` 内部错误——未知参数/缺目标/目标路径不存在/规则加载失败/规则语言未绑定/单文件读取或解析失败（消息含文件路径），不吞异常、不带病输出部分结果当作成功。
> - **autofix 参数面落地（2026-09-23，item 25，plan 2026-09-22-2225-1，live 以源码为准）**：`--fix` 与 `--fix-dry-run` 二开关互斥（同给 = 解析错误退出 2）。`--fix` 逐文件驱动 `FixApplier` multipass（每轮 merge → 原子写 → 重解析守卫，语法破坏回滚上一轮内容并计数），报告段 lint **最终落盘内容**——诊断与退出码描述的正是"不带 `--fix` 重跑"所见的残留面；`--fix-dry-run` 在内存走同一循环、不写盘，报告保持原内容并附 unified diff（`UnifiedDiff` 手写格式化器，3 行上下文，无第三方库）。汇总段新增 fix 行 `applied/conflicts/nonconvergentFiles/rollbacks`（dry-run 标注 "no files written"）；suggest-only 规则的诊断行追加 `(suggestion: <description>)` 标注，永不参与写盘。`--fix` 不改变退出码口径：残留 error → 1，全净 → 0。
> - **--cache 结果缓存落地（2026-09-24，item 43，plan 2026-09-25-0030-1，live 以源码为准）**：`nop-lint check --cache <file>`——增量结果缓存工件（单一 JSON，原子写）：指纹 = 文件内容 SHA-256 ⊕ run 指纹（规则集身份 + profile + fixMode + 生效 `--rules` 排序列表 + 缓存格式版本，任一变更全工件失效）；命中回放缓存诊断（诊断流保真，engine stats 命中文件为零，cacheHits 计入 RunSummary 并在命中>0 时渲染 "cache: N hit(s)" 行）；损坏缓存 fail-closed 退出 2；**与 --baseline 族、--fix/--fix-dry-run 互斥**（解析错误退出 2——回放不能驱动 fix multipass 与 baseline 流）。缓存文件为 CI 流水线工件（保存/恢复由流水线承担）。
> - **CLI 完整化落地（2026-09-24，item 39，plan 2026-09-24-1600-1，live 以源码为准）**：本节"输出格式（Phase 4）"表全量兑现——五种格式经 `Reporter` 接口统一（`render(CheckOutcome, Writer)`；`ConsoleReporter` 迁到 Writer 面，行结束符钉 `\n`，PrintStream 构造器委托，**golden 输出字节级不变**）。`--format console|sarif|checkstyle-xml|json|junit-xml`：机器格式只渲染诊断流（**无 dry-run diff 块、无 suggestion 标注**——与 fix 流正交的精确含义）；sarif = 2.1.0 最小合规面（version/runs/results + error→error/warning→warning/info+hint→note）；checkstyle-xml severity 映射 error→error/warning→warning/其余→info；junit-xml = 每文件一 testsuite、每诊断一 testcase（name=ruleId）。`--max-warnings N`：warning 级诊断 > N → 退出 1（error 语义不变；0 合法）；`--rules <id,id,...>`：实现位点 = `CheckRunner` 内 `loadRuleSet` 之后、`verifyRuleLanguages` 之前过滤（**显式收窄是有意裁定**：某语言的规则被全量过滤后不再触发未绑定语言检查——与 v1 防静默语义相反是 R1 3.2 的明示偏离），未知 id fail-closed 报错退出 2，`--rules` × `--fix` × baseline 组合合法（baseline fingerprint 消费跟随过滤后残差）。**match 子命令**：`nop-lint match <pattern> <file> [--language <id>]`——语言解析 = 扩展名 → `TargetScanner.languageIdForExtension` → registry.resolve（查无 → 退出 2），输出 `<file>:<line>:<col>: <首行>`（`LineIndex.lineOfByte/lineStartByte` 1-based 换算）；pattern 编译失败退出 2。**test 子命令**：`nop-lint test <suites-path>` 包装 `RuleTestRunner.runSuites`（默认 profile 构造器；deep 套件经显式 path + 自带 provider 的 harness 面运行），套件红 → 退出 1、加载错误 → 退出 2。子命令分发在 `NopLintCli` 初始化作用域内（match/test 与 check 共享 init/destroy 与退出码三态面），参数面各自独立 fail-closed。

Diagnostic → SARIF 映射：`ruleId→ruleId`、`severity(level)`→`error/warning/note`、`range`→`region`、`fix.description`→`help.text`。

## 3. 自动修复安全应用（Phase 2）

| 机制 | 说明 |
|------|------|
| **dry-run** | `--fix-dry-run`：只输出 diff，不写文件 |
| **原子写入** | 临时文件 + rename，失败不留半改状态 |
| **每 pass 重解析** | multipass（≤10）每轮 fix 后重新解析；语法破坏立即中止并回滚该文件 |
| **幂等收敛** | 每轮后诊断数必须下降，否则停止（防 fix 振荡） |
| **抑制交互** | 被抑制的诊断不产 fix；fix 产生的新诊断走正常抑制判定（09 §6） |
| **冲突检测** | range 重叠的 fix 只应用优先级最高者（04 §7 merge 算法） |

> **落地增注（2026-09-22，item 25，live 以源码为准）**：
> - **fix 载体**：fix 在 match 期渲染（env 唯一可用点）并随 `Diagnostic` 携带——suppression 对诊断整体过滤，"被抑制不产 fix" 天然成立；`$$$VAR` 序列渲染取首末捕获节点之间的原始源码切片（分隔符/注释逐字保留，空序列 = 空串），替换文本源码逐字，自动重缩进与 expand 同批 defer。
> - **冲突优先级**：跨规则按规则集声明序、同规则内按 match 生成序；merge = 按该优先级序贪心选非重叠集，选中集按 range[0] 排序应用（"range 互斥"前提不成立——嵌套同形匹配可重叠）。
> - **multipass 语义**：不收敛停止时保留最后成功轮的写盘；dry-run 在内存走完整 multipass、输出原始 vs 最终单一 unified diff；收敛计数以参与 fix 的候选诊断数为准（不含 suppression 元诊断——autofix 删除代码常使邻近抑制指令失效、元诊断反升，按全量计数会假性 nonconvergent）；每轮全量重编译规则可接受（≤10 轮，缓存归性能优化）。
> - **组合面 fail-closed**：`fix`+`xscript` parse 期拒绝、XML 语言路径声明 `fix` compile 期拒绝（模板会被静默丢弃的面，不允许存在）；`metadata.autoFixable` 与 `fix` 不做强一致校验；`--fix` 退出码沿用既有三态。

## 4. RuleTester（规则测试框架，Phase 1）

> 对标 ESLint RuleTester：YAML 规则必须可测试，否则 190 条 PMD/ErrorProne 移植（06 §7 manifest）无法验收。

### 4.1 目录布局

> **VFS 布局裁定（RuleTester，plan 2026-09-21-2137-1-rule-tester）**：规则文件必须位于
> `_vfs/` 之下才能经 VFS + xdef + x:extends 管线加载（与现存 `*.rule.yml` 夹具机制一致）。
> RuleTester 套件目录约定：
>
> ```
> <test-resources>/_vfs/test/lint/suites/<category>/<rule-name>/
> ├── <rule-name>.rule.yml        # 规则本体（文件名 = 套件目录名）
> ├── valid/
> │   └── basic.java              # 不应触发任何诊断的代码（*.java）
> └── invalid/
>     ├── runtime-exception.java  # 应触发诊断的代码（*.java）
>     └── runtime-exception.expect  # 期望文件：与夹具同名
> ```
>
> fail-closed 约定：invalid 夹具缺同名 `.expect`、`.expect` 的 `diagnostics` 为空、
> 套件无任何夹具、fixture 目录中的非 `*.java`/非 `.expect` 文件——全部显式失败，
> 不静默跳过。

> **抑制夹具契约（2026-09-22 裁定，item 17 承接）**：不新增 `.expect` 字段、不新增
> 夹具目录、不引入"关闭抑制"的引擎开关（三者皆拒绝：新 API 面且身份验证弱 / 契约
> 重复 / 生产线不当旋钮）。抑制场景以**普通 invalid/ 夹具的抑制后差分断言**表达：
> `.expect` 的 `diagnostics` 描述抑制生效后的可见面——同文件未抑制的对等违规（或同
> 套件其他夹具）证明规则在无抑制时确实命中，被抑制实例缺席 + 存活实例在场即构成差
> 分证明。元诊断以引擎固定 rule id（`unused-disable-directive` / `unpaired-disable`）
> 作为普通期望条目断言（注意：全被抑制且无元诊断的夹具期望列表为空 → 被既有
> fail-closed 规则拒绝，此类场景必须用差分对表达）。`LintStats` 计数断言不入
> `.expect`，由 Java 引擎级测试承担（`TestSuppressionEngineEndToEnd`）。样例套件：
> `nop-lint-nop` 测试资源的 `suites/suppression/no-suppress-demo/`（注释抑制、注解
> 抑制、unused-directive、unpaired-disable 四场景 + valid 干净夹具）。

### 4.2 期望文件格式（`*.expect`）

```yaml
diagnostics:
  - line: 12
    endLine: 12
    ruleId: nop-no-raw-exception
    messageContains: "RuntimeException"
    fix: "throw new NopException(ERR_CODE).param(args)"   # 可选：期望的 fix 文本
```

> **v1 裁定（RuleTester，plan 2026-09-21-2137-1-rule-tester）**：
>
> - **行号语义**：`line`/`endLine` 是 **1-based 源码行号**。`Diagnostic.range` 的
>   UTF-8 字节区间按「起止字节各自落行」换算——`startLine` = `startByte` 所在行，
>   `endLine` = `endByte - 1` 所在行（区间 end 为开边界）。跨行节点的起止行分别可断言；
>   期望中 `endLine` 缺省等于 `line`（单行断言）。
> - **v1 拒绝面（fail-closed，无静默容错）**：`.expect` 出现 `fix` 期望字段 → 抛错，
>   消息指向 autofix 能力（roadmap item 25），不静默忽略。抑制类夹具的契约表达见
>   §4.1 抑制夹具契约（item 17 已落地）。此外：缺 `diagnostics` 列表、空列表、
>   未知字段、非法行号（`<1`、`endLine < line`、非整数）均抛 `NopLintException`，
>   消息含夹具路径。

### 4.3 运行与 CI

- **JUnit 启动器（Phase 1 交付）**：`RuleTestRunner`（nop-lint-core 提供）随 `./mvnw test` 执行 fixtures：valid 文件零诊断、invalid 文件逐条断言
- `nop-lint test <suites-path>` CLI 入口（roadmap item 39 落地，plan 2026-09-24-1600-1）：同一逻辑（`RuleTestRunner.runSuites`）的命令行包装——**本节早期的 `--rules path` 形态被 item 39 的 `--rules <id,id,...>` 重定义**（后者 = check 的规则 id 白名单，见 §2.4 增注）；test 子命令的参数面 = 单一位置参数 `<suites-path>`，不携带 --rules
- Maven 集成：`nop-lint-maven-plugin` 的 `check` goal 落地（item 37）；`test-rules` goal 留候选池（未立项）
- **验收规则**：08-migration §3 要求每 Phase 交付物有 fixture（Phase 1–3 经 `RuleTestRunner` 验证）；manifest（06 §7）中 tier 1–3 规则无 fixture 视为未完成
