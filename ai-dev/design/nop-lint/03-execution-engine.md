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
Constraint.evaluate(matchContext)                 // Phase 2
  ↓
xscript.executeMatch(matchContext)                // 可选，Phase 1
  ↓
抑制判定（注释/@SuppressWarnings/exemption/baseline，见 09）
  ↓
Diagnostic { ruleId, severity, message, range, fix? }
```

> **v1 落地裁定（2026-09-22，item 17）**：抑制判定实现为**管线尾部**（`LintEngine.lint` 在 `RuleSetRunner` 全部规则跑完之后、诊断出口之前构造 `SuppressionFilter` 一次性判定全部候选诊断）。与图示 per-match 位置语义等价：匹配内核对抑制无感知，判定单调（命中任一有效 span 即移除），前后置关系（xscript 后、输出前）保持。组合：内联注释扫描（`CommentSuppressionScanner`，恒开）+ 语言注解 provider（`LintLanguage.suppressionProvider()`，Java = `@SuppressWarnings`，缺省 null）。可观测性：`LintStats.suppressedDiagnostics` 计数被移除的候选诊断（不静默）；`diagnostics` 计数在抑制尾部调和为最终输出数（存活候选 + 元诊断）；FAST/STANDARD 两档一致生效（design 11 §2，抑制不按档位裁剪）。v1 落地层 = 注释 + 注解；exemption/baseline 归 item 27。

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

### 2.1 Maven 插件（统一名称：nop-lint-maven-plugin）

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
> - **console 行格式（默认输出，v1 唯一格式）**：诊断行 `<path>:<line>[-<endLine>]: <severity>: <ruleId>: <message>`——行号换算与 §4.2 RuleTester 同口径（`LineIndex`：startByte 与 endByte−1 各自落行，1-based；`endLine > line` 时以 `line-endLine` 区间形式呈现；文件按字典序、诊断按引擎序稳定输出）。汇总段：scanned/skipped 文件计数（skipped 按扩展名分桶）+ error/warning/info/hint/other 诊断计数 + `LintStats` 关键计数——`rules loaded`（规则集大小，报告一次）/ `executed` / `skippedByProfile`（含 ids）/ `kindFiltered`（跨文件求和，ids 取并集）、`suppressedDiagnostics`、`disabledRuleIds`、xscript 四计数（executed/failed/capped/timedOut）——延续"不静默跳过"硬约束的可观测口径。
> - **退出码三态（v1 落地）**：`0` 无 error 级诊断；`1` 存在 error 级诊断（`--max-warnings` 未进 v1）；`2` 内部错误——未知参数/缺目标/目标路径不存在/规则加载失败/规则语言未绑定/单文件读取或解析失败（消息含文件路径），不吞异常、不带病输出部分结果当作成功。

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
- `nop-lint test [--rules path]` CLI 入口（Phase 4）：同一逻辑的命令行包装
- Maven 集成：`nop-lint-maven-plugin` 的 `test-rules` goal 绑定 `test` phase（Phase 4）
- **验收规则**：08-migration §3 要求每 Phase 交付物有 fixture（Phase 1–3 经 `RuleTestRunner` 验证）；manifest（06 §7）中 tier 1–3 规则无 fixture 视为未完成
