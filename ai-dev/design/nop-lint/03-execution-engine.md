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

### 1.2 多文件执行（增量模式）

> **API 约束**：nop-treesitter 的增量入口是 `TSParser.parseIncremental(language, oldTree, edits, newSource)`（`newSource` 为 `byte[]`）；`TSTree` 上没有 `edit()` 方法，编辑列表由调用方计算。

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
> 不静默跳过。`suppressed.java` 类抑制夹具不属于 RuleTester 契约（roadmap item 17）。

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
>   消息指向 autofix 能力（roadmap item 25），不静默忽略。suppression 类夹具不属于
>   RuleTester 契约（roadmap item 17 承接）。此外：缺 `diagnostics` 列表、空列表、
>   未知字段、非法行号（`<1`、`endLine < line`、非整数）均抛 `NopLintException`，
>   消息含夹具路径。

### 4.3 运行与 CI

- **JUnit 启动器（Phase 1 交付）**：`RuleTestRunner`（nop-lint-core 提供）随 `./mvnw test` 执行 fixtures：valid 文件零诊断、invalid 文件逐条断言
- `nop-lint test [--rules path]` CLI 入口（Phase 4）：同一逻辑的命令行包装
- Maven 集成：`nop-lint-maven-plugin` 的 `test-rules` goal 绑定 `test` phase（Phase 4）
- **验收规则**：08-migration §3 要求每 Phase 交付物有 fixture（Phase 1–3 经 `RuleTestRunner` 验证）；manifest（06 §7）中 tier 1–3 规则无 fixture 视为未完成
